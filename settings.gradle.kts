pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // `PREFER_SETTINGS` en lugar de `FAIL_ON_PROJECT_REPOS`: el plugin de Kotlin/Wasm registra
    // por su cuenta el repositorio de la distribución de Node y, con la política estricta, el
    // build abortaba («repository ... was added by unknown code»). Con esta política los
    // repositorios de proyecto se ignoran (con aviso) y mandan SIEMPRE los de aquí abajo, donde
    // además está declarado el de Node. Ningún módulo declara repositorios propios.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // ── Herramientas del empaquetado Kotlin/Wasm ────────────────────────────────────────
        // El plugin de Kotlin descarga Node (si no está), Yarn y `binaryen` (el optimizador del
        // `.wasm`) desde SUS repositorios, no desde Maven Central ni Google. Se declaran aquí
        // —y no por proyecto— para que el build funciona con la política de repositorios fijada
        // arriba. Cada uno sirve UN artefacto (`content { includeModule(...) }`).
        ivy("https://nodejs.org/dist") {
            name = "NodeDistributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "YarnDistributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "BinaryenDistributions"
            patternLayout {
                artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "Canticora"
include(":app")
include(":core:domain")
include(":core:presentation")
include(":core:data")
include(":core:ui")
include(":desktopApp")
include(":webApp")
