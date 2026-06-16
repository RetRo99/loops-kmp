# loops-kmp

A Kotlin Multiplatform client for the [loops.so](https://loops.so) API.
Targets **Android** and **iOS**, distributable to Kotlin consumers via **Maven Central** and to
Swift consumers via **Swift Package Manager**.

## Features

- `LoopsClient` built on Ktor (OkHttp on Android, NSURLSession/Darwin on iOS).
- Suspend API. Currently implemented:
    - `testApiKey()` — validate the API key (`GET /api-key`).
- All failures surface as a sealed `LoopsException`:
    - `LoopsException.Api(statusCode, body)` — the server returned a non-2xx response.
    - `LoopsException.Network(cause)` — no response (offline, timeout, DNS). Usually retryable.
    - `LoopsException.Serialization(cause)` — a 2xx body that didn't match the expected model.

> More endpoints (contacts, transactional emails, events) are planned. No third-party
> (Ktor, kotlinx.serialization) exceptions are exposed to consumers.

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
    implementation("io.github.retro99:loops-kmp:0.1.0")
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

## Usage (Kotlin)

```kotlin
val client = LoopsClient(apiKey = "YOUR_API_KEY")

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

## Releasing

Tag and push — the `Release` GitHub Action (on a macOS runner) publishes the full
multiplatform artifact to Maven Central, builds the iOS XCFramework, attaches it to a
GitHub Release, and updates `Package.swift`:

```bash
git tag 0.1.0
git push origin 0.1.0
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
