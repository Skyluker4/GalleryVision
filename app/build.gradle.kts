plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    signingConfigs {
        create("release") {
        }
    }
    namespace = "com.lukesimmons.galleryvision"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lukesimmons.galleryvision"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        signingConfig = signingConfigs.getByName("debug")
        multiDexEnabled = false
        testFunctionalTest = true
        testHandleProfiling = true
        testApplicationId = "com.lukesimmons.galleryvisiontest"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            isJniDebuggable = false
            renderscriptOptimLevel = 3
            signingConfig = signingConfigs.getByName("release")
            multiDexEnabled = false
        }
        getByName("debug") {
            isDebuggable = true
            isJniDebuggable = true
            renderscriptOptimLevel = 0
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "dbg"
            multiDexEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }

    buildToolsVersion = "36.0.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Feature + layers
    implementation(project(":feature:library"))
    implementation(project(":feature:viewer"))
    implementation(project(":feature:faces"))
    implementation(project(":feature:video"))
    implementation(project(":feature:settings"))
    implementation(project(":domain"))
    implementation(project(":data:index"))
    implementation(project(":data:mediastore"))
    implementation(project(":inference"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))
    implementation(project(":core:testing"))

    // Dependency injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    // Force a Kotlin-2.4-capable metadata reader for Hilt's processor (its bundled
    // kotlin-metadata-jvm tops out at 2.3.0 and chokes on Kotlin-2.4 libraries).
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")
    annotationProcessor("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")

    // Database (Room builder used by DI)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // Inference (kept for the on-device OCR spike test)
    implementation(libs.onnxruntime.android)

    // App + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.kotlin.test.junit)
}