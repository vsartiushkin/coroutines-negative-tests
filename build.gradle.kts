@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    jvm()

    js(IR) {
        nodejs()
    }

    wasmJs {
        nodejs()
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.coroutines.DelicateCoroutinesApi")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlin.js.ExperimentalWasmJsInterop")
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Several negative tests here deliberately mutate global JVM state (Thread.setDefaultUncaughtExceptionHandler)
// or leave permanently-running daemon threads behind (some busy-spinning) to lock in a genuine
// hang/deadlock. Running every test class in a fresh JVM keeps them from contaminating each other --
// without this, some tests (e.g. Gh2504Test) fail or behave differently only when run after others.
tasks.named<Test>("jvmTest") {
    forkEvery = 1
    maxParallelForks = 1
}
