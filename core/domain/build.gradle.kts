// -----------------------------------------------------------------------------
// :core:domain — Núcleo de Laudate en Kotlin Multiplatform (Kotlin puro).
//
// Regla dura (ver .github/instructions/domain-pure-kotlin.instructions.md):
// PROHIBIDO `android.*`, `Context`, Room, Compose. Debe compilar como JVM puro.
// Por eso NO aplica el plugin de Compose ni el de Android aquí.
//
// De momento solo target `jvm()` (lo consumen :app y, más adelante, :desktopApp).
// Los targets de Apple (`iosX64`/`iosArm64`/`iosSimulatorArm64`) se añadirán en
// la fase de iOS, cuando el dominio esté verificado en JVM.
// -----------------------------------------------------------------------------
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    // Web/PWA (ver ADR-023): el dominio es Kotlin puro y debe compilar también en Wasm.
    // `browser()` además del target: sin declarar un entorno de ejecución, Gradle rechaza
    // este módulo como dependencia de un proyecto JS/Wasm («is not configured for JS usage»).
    wasmJs {
        browser()
    }
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        // Los tests del dominio se heredaron de :app y usan la API de JUnit4.
        // Se ejecutan en el target JVM (jvmTest). La conversión a `kotlin.test`
        // (para poder correrlos también en los targets de Apple) se hará en la
        // fase de iOS. Ver ADR-013.
        getByName("jvmTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
