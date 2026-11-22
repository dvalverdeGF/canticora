import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Credenciales de firma de release. El fichero NO se versiona (ver .gitignore).
// Si no existe (p. ej. otro desarrollador o CI sin secretos), la compilación de
// debug sigue funcionando y la de release simplemente queda sin firmar.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropsFile.exists()

// Versión de la app: fuente única en `gradle.properties` (ver comentario allí y ADR-022).
val appVersionName: String = providers.gradleProperty("canticora.version").get()
val appVersionCode: Int = providers.gradleProperty("canticora.versionCode").get().toInt()

android {
    namespace = "com.grafitto.laudate"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.grafitto.laudate"
        minSdk = 26
        targetSdk = 36
        // Leídas de `gradle.properties` (fuente única, ADR-022). No escribir números aquí.
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Optimización (R8): reduce y ofusca el código, y elimina recursos no usados.
            // Sin esto el AAB llevaba ~49 MB de dex sin reducir (RNF-105). Las reglas de keep
            // (Room, kotlinx.serialization, reflexión de Compose) viven en proguard-rules.pro.
            optimization {
                enable = true
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Necesario para `BuildConfig.DEBUG` (tolerar esquemas rotos solo en desarrollo).
        buildConfig = true
    }

    // ── Assets de Compose Multiplatform (parche) ───────────────────────────────────────────────
    //
    // **Problema**: con el plugin `com.android.kotlin.multiplatform.library` (AGP 9.x) y Compose
    // 1.9.3, los recursos de `:core:ui` **no** llegaban a `assets/` del APK. Aterrizaban en `java_res`
    // (la raíz del APK, `composeResources/…`), que `painterResource` sí alcanza por classloader —por
    // eso los iconos y el logo funcionaban— pero **`Font()` no**: carga las tipografías con
    // `AssetManager.open()`, que solo mira en `assets/`. El resultado era que Bravura y Edwin no se
    // encontraban y todos los glifos musicales (clave TAB, figuras, silencios) salían como **cajas**,
    // sin ningún error en el log.
    //
    // **Arreglo**: `:core:ui` deja los recursos ya preparados en `build/generated/compose/
    // resourceGenerator/assets` (su tarea de copia se remienda en su propio `build.gradle.kts`) y
    // aquí se **copian** al directorio de assets de este módulo con una tarea propia, declarada con
    // entradas y salidas explícitas. No se puede usar `assets.srcDir(…)` apuntando al `build/` de
    // otro módulo: Gradle lo rechaza por «implicit dependency» (un módulo no debe leer el resultado
    // de otro sin declararlo), y con razón.
    val composeAssetsSource = rootProject.layout.projectDirectory.dir(
        "core/ui/build/generated/compose/resourceGenerator/assets"
    )
    val composeAssetsTarget = layout.buildDirectory.dir("generated/composeAssets")
    val syncComposeAssets = tasks.register<Sync>("syncComposeAssets") {
        description = "Copia los recursos de Compose de :core:ui a los assets de la app."
        from(project(":core:ui").tasks.matching {
            it.name.endsWith("ComposeResourcesToAndroidAssets")
        })
        from(composeAssetsSource)
        into(composeAssetsTarget)
    }
    // El directorio **sí** se declara como assets, pero es propiedad de este módulo (está bajo su
    // propio `build/`), así que Gradle puede validarlo. La API de `sourceSets` de Android no acepta
    // `Provider`, así que se pasa la **ruta** ya resuelta.
    sourceSets.getByName("main").assets.srcDir(
        composeAssetsTarget.get().asFile.absolutePath
    )
    // Y la copia tiene que estar hecha **antes** de cualquier tarea que lea los assets: fusionar
    // assets, lint (que construye su modelo leyéndolos) y las de empaquetado. `matching` con
    // `dependsOn` es perezoso, así que vale aunque la tarea se registre después; se cubren las
    // familias de nombres que usa AGP (`lintVital…`, `generate…Lint…`, `lintAnalyze…`, `merge…Assets`,
    // `package…`, `bundle…`) porque no hay una única tarea «de lint».
    tasks.matching {
        it.name.startsWith("lint") ||
            it.name.contains("Lint") ||
            (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
            (it.name.startsWith("package") && it.name.endsWith("Bundle")) ||
            it.name.startsWith("bundle")
    }.configureEach {
        dependsOn(syncComposeAssets)
    }
}

dependencies {
    // Núcleo compartido (Kotlin Multiplatform, Kotlin puro). Ver ADR-013.
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // DataStore (preferencias no sensibles)
    implementation(libs.androidx.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
