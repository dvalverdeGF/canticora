// -----------------------------------------------------------------------------
// :core:presentation — Contratos MVI, ViewModels y lógica de presentación pura.
//
// Targets: `jvm()` (Android + escritorio) y `wasmJs()` (web/PWA). Ver ADR-023.
//
// Los ViewModels viven en `commonMain` y extienden el **ViewModel multiplataforma de
// JetBrains** (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`), que publica
// variantes `jvm`, `js`, `wasm` y `native`. El artefacto de Google no tiene variante
// `wasm`, por eso se usa el de JetBrains: el **paquete Kotlin es el mismo**
// (`androidx.lifecycle`), así que ningún import cambia. En Android, la variante
// `androidJvm` de JetBrains delega en Google 2.9.2, la versión fijada en el catálogo.
// -----------------------------------------------------------------------------
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    wasmJs {
        browser()
    }
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            // `api`: la API pública del módulo son los ViewModels, que extienden ViewModel.
            api(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
