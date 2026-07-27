// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kover)
}

kover {
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                // Generated DI/DB code is not meaningful to cover.
                classes(
                    "*_Factory*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "*.dagger.*",
                    "*_Impl*",
                )
            }
        }
        verify {
            // Overall line coverage ratchet: measured baseline 15.9% (2026-07-27);
            // raise toward the R10 target (~90%) as tests grow.
            rule("overall line coverage") {
                minBound(15)
            }
        }
    }
}

// Aggregate per-module coverage into the root report (Kover merging model).
val coveredModules = listOf(
    ":app",
    ":core:model",
    ":core:common",
    ":core:database",
    ":core:datastore",
    ":core:ui",
    ":core:testing",
    ":data:mediastore",
    ":data:index",
    ":domain",
    ":inference",
    ":feature:library",
    ":feature:viewer",
    ":feature:faces",
    ":feature:video",
    ":feature:settings",
)
dependencies {
    coveredModules.forEach { kover(project(it)) }
}
