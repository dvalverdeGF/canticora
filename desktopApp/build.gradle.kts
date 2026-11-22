// -----------------------------------------------------------------------------
// :desktopApp — Aplicación de escritorio (Windows/JVM) de LaudateApp.
//
// Consume los módulos compartidos :core:* (dominio, datos, presentación y UI con
// Compose Multiplatform) y aporta el "cascarón" de plataforma: ventana, diálogos de
// fichero, PDF, asociación de ficheros .laudate y empaquetado (jpackage → MSIX).
// Ver ADR-013 y docs/APPLE_KMP_PLAN.md §17.
// -----------------------------------------------------------------------------
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// ── Versión: fuente única en `gradle.properties` (ADR-022) ───────────────────
// El MSI/EXE y el MSIX usan este número, y la app lo muestra en «Acerca de».
// Antes el literal de escritorio vivía aparte y se quedó obsoleto («1.0.24-desktop»)
// cuando la Store ya servía la 1.1.3.
val appVersion: String = providers.gradleProperty("canticora.version").get()
val appVersionCode: String = providers.gradleProperty("canticora.versionCode").get()

/**
 * Genera `CanticoraVersion.kt` con la versión, para que el código no la replique a mano.
 * Se regenera en cada build, así que cambiar `gradle.properties` basta.
 */
val generateVersionSource = tasks.register("generateVersionSource") {
    val outDir = layout.buildDirectory.dir("generated/version")
    val version = appVersion
    val versionCode = appVersionCode
    inputs.property("version", version)
    inputs.property("versionCode", versionCode)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("com/grafitto/laudate/desktop")
        dir.mkdirs()
        dir.resolve("CanticoraVersion.kt").writeText(
            """
            |// GENERADO POR GRADLE desde gradle.properties (ADR-022). NO EDITAR A MANO.
            |package com.grafitto.laudate.desktop
            |
            |/** Versión de la aplicación; fuente única en `gradle.properties`. */
            |object CanticoraVersion {
            |    /** Versión semver («1.1.3»). */
            |    const val NAME: String = "$version"
            |    /** Código entero monótono (lo exige Google Play). */
            |    const val CODE: String = "$versionCode"
            |    /** Versión anotada en repertorios y copias exportadas desde escritorio. */
            |    const val DESKTOP_NAME: String = "$version-desktop"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    jvm()
    jvmToolchain(17)

    sourceSets {
        getByName("jvmMain").apply {
            kotlin.srcDir(generateVersionSource)
        }
        getByName("jvmMain").dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
            implementation(project(":core:presentation"))
            implementation(project(":core:ui"))
            implementation(compose.desktop.currentOs)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            // Recursos compartidos de Compose (icono de la ventana: `Res.drawable.app_logo`).
            implementation(compose.components.resources)
            // Dispatcher Main de escritorio (Swing/AWT): sin él, `viewModelScope` falla con
            // «Module with the Main dispatcher is missing» (en Android lo aporta
            // kotlinx-coroutines-android). Misma versión que coroutines-core.
            implementation(libs.kotlinx.coroutines.swing)
            // PDF de escritorio: sustituye a `android.graphics.pdf` reutilizando los layouts puros.
            implementation(libs.pdfbox)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            // `Dispatchers.Swing` (el `Dispatchers.Main` de escritorio) para reproducir en tests el
            // patrón que rompía los diálogos modales: invocarlos desde una corrutina del EDT.
            implementation(libs.kotlinx.coroutines.swing)
            // Render de composables a imagen (verificación visual de la tablatura).
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.grafitto.laudate.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Canticora"
            // Leída de `gradle.properties` (fuente única, ADR-018).
            packageVersion = appVersion
            description = "Canticora — Crea tu repertorio. Interpreta tu música"
            vendor = "Grafitto"

            // Runtime JVM RECORTADO (RNF-504): solo los módulos que la app necesita, en lugar
            // del JDK completo (~110 MB → ~55 MB). Los sugiere `:desktopApp:suggestRuntimeModules`.
            modules("java.instrument", "jdk.unsupported")

            windows {
                // La Microsoft Store (MSIX) la aporta `jpackage` + WiX; nombre del menú inicio.
                menuGroup = "Canticora"
                // Icono del ejecutable (barra de tareas, escritorio, Inicio). Con fondo crema, para
                // que Windows no pinte el azul por defecto. Se genera con packaging/msix/make_icon.py.
                iconFile.set(project.file("../packaging/msix/laudate.ico"))
                // Asociación de ficheros `.canticora` (sustituye al deep link ACTION_VIEW de Android).
                // El `extension` debe ir con punto.
                fileAssociation(
                    mimeType = "application/vnd.canticora.event+json",
                    extension = ".canticora",
                    description = "Repertorio o copia de seguridad de Canticora"
                )
            }
        }
    }
}
