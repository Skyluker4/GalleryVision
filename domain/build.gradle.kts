// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.lukesimmons.galleryvision.domain"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(project(":core:database"))
    api(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test.junit)
}

kover {
    reports {
        verify {
            // Search engine is the correctness core; its bar is high by design (R10).
            rule("domain line coverage") {
                minBound(75)
            }
        }
    }
}
