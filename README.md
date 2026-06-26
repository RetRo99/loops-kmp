# loops-kmp

A Kotlin Multiplatform client for the [loops.so](https://loops.so) API.
Targets **Android**, **JVM** (server-side), and **iOS** (arm64 device + Apple Silicon simulator) — distributable to Kotlin/Java consumers
via **Maven Central** and to Swift consumers via **Swift Package Manager**.

## Features

- `LoopsClient` built on Ktor (OkHttp on Android, CIO on JVM, NSURLSession/Darwin on iOS).
- **Full coverage of the Loops API**, organized into resource groups on the client. Suspend API
  on Kotlin; `CompletableFuture` wrappers on JVM; `async`/`await` on Swift via SKIE.
- `testApiKey()` lives directly on the client (`GET /api-key`); everything else is grouped:

  | Group | Endpoints |
  |---|---|
  | `client.contacts` | find, create, update, delete, suppression status / removal |
  | `client.contactProperties` | list, create |
  | `client.events` | send (with idempotency key) |
  | `client.transactional` | send, listEmails, plus email management (create / get / update / draft / publish) |
  | `client.lists` | list mailing lists |
  | `client.campaigns` | list, get, create, update |
  | `client.emailMessages` | get, update |
  | `client.themes` | list, get |
  | `client.components` | list, get |
  | `client.sendingIps` | list dedicated sending IPs |
  | `client.uploads` | create (presigned URL), complete |

- Two construction modes — **direct** (server-side) and **proxy** (mobile) — with the security
  boundary enforced at the type level.
- **Automatic retry on HTTP 429**, configurable network **timeouts**, and opt-in request
  **logging** — see [Resilience & configuration](#resilience--configuration).
- All failures surface as a sealed `LoopsException`:
    - `LoopsException.Api(statusCode, body)` — the server returned a non-2xx response.
    - `LoopsException.RateLimit(limit, remaining)` — HTTP 429, with the `x-ratelimit-*` headers
      parsed. Thrown only after retries are exhausted (see [Resilience & configuration](#resilience--configuration)).
    - `LoopsException.Network(cause)` — no response (offline, timeout, DNS). Usually retryable.
    - `LoopsException.Serialization(cause)` — a 2xx body that didn't match the expected model.

> No third-party (Ktor, kotlinx.serialization) exceptions are exposed to consumers.

---

## Security: never ship the Loops API key in a mobile app

Loops states explicitly:
> *"Your Loops API key should never be used client side or exposed to your end users."*

A mobile app binary is not a safe place for a secret. R8/ProGuard obfuscates code symbols —
not string values. Any key compiled into an APK or IPA can be extracted with standard tooling
in minutes. The Loops API key is a **full-access account credential**: it can send email as
your brand, read and delete your entire contact list, and run campaigns.

This library enforces the rule at the type level:

- `LoopsClient.direct(apiKey)` — for **server-side** use only. Has an `apiKey` parameter
  because the key is safe on a trusted server.
- `LoopsClient.proxy(proxyUrl)` — for **mobile** use. **Has no `apiKey` parameter**, so it is
  structurally impossible to embed a Loops key in a client binary. Your app talks to your own
  backend; your backend holds the key and forwards to Loops.

```
Mobile app  ──▶  your backend proxy  ──key──▶  api.loops.so
```

---

## Add to a Kotlin / Android / KMP project (Maven Central)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.retar:loops-kmp:1.0.0")
}
```

> For a KMP module, add it to `commonMain`; Gradle resolves the correct Android/iOS
> variant per target automatically.

## Add to an iOS / Swift project (Swift Package Manager)

In Xcode: **File → Add Package Dependencies…** and enter:

```
https://github.com/retro99/loops-kmp
```

Pick the version, then:

```swift
import LoopsSdk
```

(Swift calls suspend functions via the generated completion-handler/async bridge.)

## Usage

### Server-side (`direct` mode)

Use this on a trusted backend (Ktor, Spring, etc.) where the API key is safe:

```kotlin
val client = LoopsClient.direct(apiKey = "YOUR_API_KEY")

try {
    val result = client.testApiKey()
    println("Key valid for team: ${result.teamName}")
} catch (error: LoopsException.Api) {
    println("API rejected the request (${error.statusCode}): ${error.body}")
} catch (error: LoopsException.Network) {
    println("Could not reach Loops: ${error.message}")
} finally {
    client.close()
}
```

Override `baseUrl` only for Loops staging or EU data-residency endpoints:

```kotlin
val client = LoopsClient.direct(
    apiKey = "YOUR_API_KEY",
    baseUrl = "https://eu.loops.so/api/v1/",
)
```

### Working with resources

Each resource group hangs off the client. A few representative calls:

```kotlin
// Contacts — custom properties are flattened to top-level JSON automatically.
client.contacts.create(
    CreateContactRequest(
        email = "a@b.com",
        firstName = "Ada",
        customProperties = mapOf(
            "plan" to LoopsValue.of("pro"),
            "signupDate" to LoopsValue.ofDateMillis(1_705_486_871_000),
        ),
    ),
)

// ...and read them back: unknown top-level keys are collected into customProperties.
val contact = client.contacts.find(ContactIdentifier.ByEmail("a@b.com")).single()
println(contact.customProperties["plan"]) // StringValue(pro)

// Events — eventProperties stay nested; idempotencyKey is optional.
client.events.send(
    EventRequest(eventName = "signup", email = "a@b.com"),
    idempotencyKey = "evt-123",
)

// Transactional email management (alpha on the Loops side).
val email = client.transactional.createEmail(NameRequest("Welcome"))
client.transactional.ensureEmailDraft(email.id)
client.transactional.publishEmail(email.id)
val emails = client.transactional.listEmails(perPage = 20) // Page<TransactionalEmail>

// Uploads — the SDK only fetches the presigned URL; you PUT the bytes yourself.
val upload = client.uploads.create(CreateUploadRequest("image/png", bytes.size))
// httpClient.put(upload.presignedUrl) { setBody(bytes) }  // your own PUT
val asset = client.uploads.complete(upload.emailAssetId)
println(asset.finalUrl)
```

> **JVM (Java) callers:** every suspend method has a generated `*Async()` twin returning a
> `CompletableFuture` (e.g. `client.contacts.createAsync(...)`). *(JVM target only.)*
> **Swift callers:** call the same methods with `try await` thanks to SKIE.

### Resilience & configuration

`direct` and `proxy` accept three optional configuration objects. Each has a sensible default,
so you only pass what you want to change.

| Config | Default | What it does |
|---|---|---|
| `RetryConfig` | `RetryConfig.DEFAULT` (3 retries, 500 ms → 10 s backoff) | Retries HTTP 429 with exponential backoff, honouring a `Retry-After` header. `RetryConfig.NONE` disables it. |
| `TimeoutConfig` | `TimeoutConfig.NONE` (engine defaults) | Request / connect / socket timeouts. `TimeoutConfig.DEFAULT` applies 30 s / 10 s / 30 s. |
| `LoggingConfig` | `LoggingConfig.none()` (off) | Request/response logging at a chosen `LogLevel`. Off by default because header/body logs can leak the API key and PII; on the JVM it bridges to SLF4J. |

```kotlin
val client = LoopsClient.direct(
    apiKey = "YOUR_API_KEY",
    baseUrl = LoopsClient.LOOPS_BASE_URL,
    retry = RetryConfig(maxRetries = 5),
    timeout = TimeoutConfig.DEFAULT,
    logging = LoggingConfig.of(LogLevel.INFO),
)
```

Swift sees the same factory (Kotlin default arguments don't cross the Swift boundary, so an
engine-free overload is provided):

```swift
let client = LoopsClient.companion.direct(
    apiKey: "YOUR_API_KEY",
    baseUrl: LoopsClient.companion.LOOPS_BASE_URL,
    retry: RetryConfig.companion.DEFAULT,
    timeout: TimeoutConfig.companion.DEFAULT,
    logging: LoggingConfig.companion.of(level: .info, logger: nil)
)
```

### Mobile (`proxy` mode)

Use this inside an Android or iOS app. Your app talks to **your own backend**, which holds
the Loops key server-side. The `proxyUrl` points at your backend — never at `app.loops.so`.

**No app auth (proxy is public or uses cookies/mTLS):**

```kotlin
val client = LoopsClient.proxy(proxyUrl = "https://your-backend.com/loops/")
```

**With a rotating session token** (re-evaluated per request — no client rebuild on refresh):

```kotlin
val client = LoopsClient.proxy(
    proxyUrl = "https://your-backend.com/loops/",
    auth = ProxyAuth.BearerToken { sessionStore.currentToken() },
)
```

**With arbitrary headers:**

```kotlin
val client = LoopsClient.proxy(
    proxyUrl = "https://your-backend.com/loops/",
    auth = ProxyAuth.Headers {
        mapOf("X-App-User-Id" to userId, "X-App-Session" to sessionId)
    },
)
```

### `ProxyAuth` options

| Type | Description |
|---|---|
| `ProxyAuth.None` | No extra auth. Default. |
| `ProxyAuth.BearerToken { token() }` | Adds `Authorization: Bearer <token>`. The lambda runs on every request — return `null` to omit the header. |
| `ProxyAuth.Headers { headers() }` | Adds arbitrary headers. The lambda runs on every request. |

Both lambda types are `suspend`, so token refresh / async lookups work without extra wiring.
The consumer owns caching; the library just calls the lambda.

---

## Migration from 0.0.x

```kotlin
// before
val client = LoopsClient(apiKey = "YOUR_API_KEY")

// after
val client = LoopsClient.direct(apiKey = "YOUR_API_KEY")
```

---

## Releasing

Tag and push — the `Release` GitHub Action (on a macOS runner) publishes the full
multiplatform artifact to Maven Central, builds the iOS XCFramework, attaches it to a
GitHub Release, and updates `Package.swift`:

```bash
git tag 0.1.1
git push origin 0.1.1
```

Requires these repository secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
`SIGNING_KEY` (ASCII-armored GPG private key), `SIGNING_KEY_PASSWORD`.

## License

Licensed under the [GNU General Public License v3.0](LICENSE).

## Building locally

```bash
./gradlew build                                # compile + run tests for all targets
./gradlew iosSimulatorArm64Test                # run common tests on the iOS simulator
./gradlew testAndroidHostTest                  # run common tests on the Android host
./gradlew assembleLoopsSdkReleaseXCFramework   # produces build/XCFrameworks/release/LoopsSdk.xcframework
```
