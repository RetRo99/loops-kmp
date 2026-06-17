import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ksp)
    alias(libs.plugins.skie)
}

group = "io.github.retro99"
version = "1.0.0"

kotlin {
    android {
        namespace = "com.retro99.loops.sdk"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        withHostTestBuilder {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    val xcf = XCFramework("LoopsSdk")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "LoopsSdk"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.coroutines.jdk8)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.cio)
        }
    }
}

dependencies {
    add("kspJvm", project(":ksp-processor"))
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    // Sign only when a key is configured (CI release). Allows unsigned local/maven-local builds.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
    ) {
        signAllPublications()
    }

    coordinates(group.toString(), "loops-kmp", version.toString())

    pom {
        name.set("loops-kmp")
        description.set("Kotlin Multiplatform client for the loops.so API (Android, iOS, JVM).")
        inceptionYear.set("2026")
        url.set("https://github.com/retro99/loops-kmp")

        licenses {
            license {
                name.set("GNU General Public License v3.0")
                url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                distribution.set("https://www.gnu.org/licenses/gpl-3.0.txt")
            }
        }

        developers {
            developer {
                id.set("retro99")
                name.set("Rok Retar")
                url.set("https://github.com/retro99")
            }
        }

        scm {
            url.set("https://github.com/retro99/loops-kmp")
            connection.set("scm:git:git://github.com/retro99/loops-kmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/retro99/loops-kmp.git")
        }
    }
}
