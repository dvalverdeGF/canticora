// -----------------------------------------------------------------------------
// :webApp — Web app / PWA de Canticora (Kotlin/Wasm + Compose Multiplatform).
//
// Tercera plataforma, junto a Android (:app) y escritorio (:desktopApp). Comparte el 100 %
// de las pantallas (:core:ui), los ViewModels (:core:presentation) y el motor musical
// (:core:domain); lo único propio es el "cascarón": contenedor de dependencias sin Room
// (datos en memoria + `localStorage`), arranque en el navegador y empaquetado PWA.
//
// NO depende de :core:data: Room 2.8.5 no publica variante `wasm`. La web implementa las
// interfaces de repositorio del Dominio sobre un almacén en memoria hidratado con el
// catálogo semilla. Ver ADR-023.
// -----------------------------------------------------------------------------
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // Metadatos de adjuntos y respaldos: JSON con tipos seguros en `localStorage`.
    alias(libs.plugins.kotlin.serialization)
}

// La misma versión que Android y escritorio (fuente única en `gradle.properties`, ADR-022).
val appVersion: String = providers.gradleProperty("canticora.version").get()

/**
 * Genera `WebVersion.kt` con la versión, para que el código no la replique a mano. Mismo
 * criterio que `:desktopApp`: `gradle.properties` es la única fuente de verdad (ADR-022).
 */
val generateVersionSource = tasks.register("generateVersionSource") {
    val outDir = layout.buildDirectory.dir("generated/version")
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("com/grafitto/laudate/web")
        dir.mkdirs()
        dir.resolve("CanticoraVersion.kt").writeText(
            """
            |// GENERADO POR GRADLE desde gradle.properties (ADR-022). NO EDITAR A MANO.
            |package com.grafitto.laudate.web
            |
            |/** Versión de la aplicación web; fuente única en `gradle.properties`. */
            |object CanticoraVersion {
            |    /** Versión semver («1.1.4»). */
            |    const val NAME: String = "$version"
            |    /** Versión anotada en repertorios y copias exportadas desde la web. */
            |    const val WEB_NAME: String = "$version-web"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    wasmJs {
        // La app se sirve como estáticos (GitHub Pages): un único `.js` de entrada.
        browser {
            commonWebpackConfig {
                outputFileName = "canticora.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        // El plugin de recursos de Compose decide si genera la clase `Res` mirando las
        // dependencias de `commonMain`: la librería tiene que estar aquí, no en `wasmJsMain`.
        getByName("commonMain").dependencies {
            implementation(compose.components.resources)
        }
        getByName("wasmJsMain").apply {
            kotlin.srcDir(generateVersionSource)
        }
        getByName("wasmJsMain").dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            implementation(project(":core:ui"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

/**
 * Catálogo inicial → recurso de Compose.
 *
 * **La web se publica solo con el catálogo de ejemplo.** El cancionero completo es contenido
 * privado del autor y no viaja en el binario que se sirve en Internet; lo que sí se distribuye es
 * la **semillita** de 55 cantos tradicionales que el sitio del producto ofrece como muestra
 * (`webApp/demo/canticora_semillita.json`). Se ve la app con contenido real desde el primer
 * arranque y el cancionero propio se añade desde «Importar».
 *
 * Para una compilación **personal** que lleve el cancionero completo (nunca una publicación) se
 * indica el fichero explícitamente y de forma efímera:
 *
 * ```
 * ./gradlew :webApp:wasmJsBrowserDistribution -Pcanticora.catalog=<ruta-al-catalogo.json>
 * ```
 *
 * La ruta generada (`src/commonMain/composeResources/files/catalog.json`) está en `.gitignore`: el
 * fichero existe solo durante la compilación y nunca se versiona.
 */
val emptyCatalogJson = """
    {"version":1,"exportedAt":0,"songs":[],"celebrations":[],"categories":[]}
""".trimIndent()

/**
 * Escribe el catálogo que viajará con la web.
 *
 * Es una tarea tipada (no un `doLast` en línea) para ser compatible con la caché de configuración
 * de Gradle y para que el contenido del catálogo quede como entrada declarada.
 */
abstract class PrepareWebCatalogTask : DefaultTask() {

    /** Catálogo **personal** indicado por el usuario; vacío = usar el de ejemplo. */
    @get:Input
    abstract val catalogPath: Property<String>

    /** Semillita versionada que se publica con la app. */
    @get:InputFile
    @get:Optional
    abstract val demoCatalog: RegularFileProperty

    /** JSON de catálogo vacío, usado si no hay ni catálogo personal ni semillita. */
    @get:Input
    abstract val emptyCatalog: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        val requested = catalogPath.get().trim()
        val personal = requested.takeIf { it.isNotEmpty() }?.let(::File)
        val demo = demoCatalog.orNull?.asFile
        when {
            personal != null && personal.isFile -> {
                logger.lifecycle("Catálogo personal empaquetado en la web: ${personal.absolutePath}")
                personal.copyTo(target, overwrite = true)
            }
            personal != null -> {
                logger.warn("No existe el catálogo indicado: ${personal.absolutePath}")
                target.writeText(emptyCatalog.get() + "\n")
            }
            demo != null && demo.isFile -> {
                logger.lifecycle("Catálogo de ejemplo empaquetado en la web: ${demo.name}")
                demo.copyTo(target, overwrite = true)
            }
            else -> target.writeText(emptyCatalog.get() + "\n")
        }
    }
}

val prepareWebCatalog = tasks.register<PrepareWebCatalogTask>("prepareWebCatalog") {
    val requested = providers.gradleProperty("canticora.catalog")
        .orElse(providers.environmentVariable("CANTICORA_CATALOG"))
        .orNull
        ?.trim()
        .orEmpty()
    catalogPath.set(if (requested.isEmpty()) "" else rootProject.file(requested).absolutePath)
    demoCatalog.set(layout.projectDirectory.file("demo/canticora_semillita.json"))
    emptyCatalog.set(emptyCatalogJson)
    outputFile.set(layout.projectDirectory.file("src/commonMain/composeResources/files/catalog.json"))
}

// Los recursos de Compose se empaquetan en tareas que no conocen este fichero: se encadenan para
// que el catálogo esté en su sitio antes de generar la clase `Res` y de empaquetar la distribución.
tasks.matching {
    it.name.contains("ComposeResources") ||
        it.name.startsWith("copyNonXmlValueResources") ||
        it.name == "generateComposeResClass"
}.configureEach {
    dependsOn(prepareWebCatalog)
}

// Paquete estable de la clase `Res` generada por los recursos de Compose de ESTE módulo.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.grafitto.laudate.web.generated.resources"
}
