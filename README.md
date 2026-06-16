# loops-kmp

A Kotlin Multiplatform client for the [loops.so](https://loops.so) API.
Targets **Android** and **iOS**, distributable to Kotlin consumers via **JitPack** and to
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

## Add to a Kotlin / Android / KMP project (JitPack)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.retro99:loops-kmp:0.1.0")
}
```

> The version is any git tag (e.g. `0.1.0`) or commit hash. JitPack builds it on first request.

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

Tag and push — the `Release` GitHub Action builds the XCFramework, attaches it to a
GitHub Release, and updates `Package.swift`:

```bash
git tag 0.1.0
git push origin 0.1.0
```

JitPack needs nothing extra; it builds the Kotlin artifact on demand from the same tag.

## Building locally

```bash
./gradlew build                                # compile + run tests for all targets
./gradlew iosSimulatorArm64Test                # run common tests on the iOS simulator
./gradlew testAndroidHostTest                  # run common tests on the Android host
./gradlew assembleLoopsSdkReleaseXCFramework   # produces build/XCFrameworks/release/LoopsSdk.xcframework
```
