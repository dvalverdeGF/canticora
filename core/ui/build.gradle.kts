// -----------------------------------------------------------------------------
// :core:ui — UI compartida con Compose Multiplatform (Android + JVM/escritorio + web).
//
// Un solo juego de pantallas para las tres plataformas: lo único que cambia por target son
// los `actual` de `platform/` (¿es táctil?, mantener pantalla encendida, botón «atrás»).
// Ver ADR-013 (Compose Multiplatform) y ADR-023 (web/Wasm).
// -----------------------------------------------------------------------------
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.grafitto.laudate.ui"
        compileSdk = 36
        minSdk = 26
    }
    jvm()
    wasmJs {
        browser()
    }
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":core:presentation"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.ui)
        }
        getByName("androidMain").dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

// Paquete estable de la clase `Res` generada por los recursos de Compose.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.grafitto.laudate.ui.generated.resources"
}

// ── Parche: los recursos de Compose no llegaban a `assets/` ───────────────────────────────────────
//
// **Síntoma**: los glifos musicales (Bravura) y los iconos salían como **cajas vacías** en Android, y
// `painterResource` de los SVG lanzaba «Android platform doesn't support SVG format».
//
// **Causa**: con el plugin `com.android.kotlin.multiplatform.library` (AGP 9.4.1) el plugin de Compose
// 1.9.3 registra `copyXxxComposeResourcesToAndroidAssets` pero **no le asigna `outputDirectory`**, así
// que la tarea falla con «property 'outputDirectory' doesn't have a configured value» y el build la
// ignora. Los recursos quedaban solo en `java_res` (la **raíz** del APK, `composeResources/…`),
// mientras que el lector de Compose usa `AssetManager.open()`, que busca en `assets/`. El empate
// silencioso hacía que todo lo que no fuera texto saliera mal y nada avisara.
//
// **Arreglo**: se le da a la tarea su destino dentro de `assets/` —el mismo que usaría el plugin si lo
// configurara— y el módulo `app` declara ese directorio como fuente de assets (abajo, en
// `app/build.gradle.kts`, que es quien empaqueta). `CopyResourcesToAndroidAssetsTask` es `internal`
// en el plugin, así que se localiza por **nombre de tarea** en vez de por tipo: el nombre es estable
// (`copy<Fuente>ComposeResourcesToAndroidAssets`) y así el parche no depende de una API privada.
tasks.matching { it.name.endsWith("ComposeResourcesToAndroidAssets") }.configureEach {
    val outputDir = layout.buildDirectory.dir("generated/compose/resourceGenerator/assets")
    // `outputDirectory` es un `DirectoryProperty` de la tarea: se toca por reflexión porque el tipo
    // es interno. Si una versión futura del plugin ya lo configura, este `if` no pisa su valor.
    val prop = javaClass.methods
        .firstOrNull { it.name == "getOutputDirectory" && it.parameterCount == 0 }
        ?.invoke(this) as? org.gradle.api.file.DirectoryProperty
    if (prop != null && !prop.isPresent) {
        prop.set(outputDir)
    }
}

// Y la copia tiene que estar hecha antes de que `:app` empaquete: el módulo de UI no tiene `preBuild`
// propio (es una librería KMP), así que se engancha a su tarea de preparación de recursos.
tasks.matching { it.name.endsWith("ComposeResourcesToAndroidAssets") }.configureEach {
    // Sin dependencias cruzadas: el consumidor declara `dependsOn` sobre la tarea de copia desde su
    // propio build (ver `app/build.gradle.kts`), que es donde se conoce el nombre de la variante.
}
