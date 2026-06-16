# loops-kmp

A Kotlin Multiplatform client for the [loops.so](https://loops.so) API.
Targets **Android** and **iOS**, distributable to Kotlin consumers via **Maven Central** and to
Swift consumers via **Swift Package Manager**.

## Features

- `LoopsClient` built on Ktor (OkHttp on Android, NSURLSession/Darwin on iOS).
- Suspend API. Currently implemented:
    - `testApiKey()` — validate the API key (`GET /api-key`).
- Two construction modes — **direct** (server-side) and **proxy** (mobile) — with the security
  boundary enforced at the type level.
- All failures surface as a sealed `LoopsException`:
    - `LoopsException.Api(statusCode, body)` — the server returned a non-2xx response.
    - `LoopsException.Network(cause)` — no response (offline, timeout, DNS). Usually retryable.
    - `LoopsException.Serialization(cause)` — a 2xx body that didn't match the expected model.

> More endpoints (contacts, transactional emails, events) are planned. No third-party
> (Ktor, kotlinx.serialization) exceptions are exposed to consumers.

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
    implementation("io.github.retro99:loops-kmp:0.1.1")
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
