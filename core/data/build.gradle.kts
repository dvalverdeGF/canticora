// -----------------------------------------------------------------------------
// :core:data — Persistencia (Room KMP) + repositorios + mappers.
//
// Targets: Android (para :app) y JVM (para :desktopApp, futuro).
// El ESQUEMA (entidades, DAOs, @Database, migraciones, relaciones, repos, mappers)
// vive en `commonMain`; solo el driver/almacenamiento específico de plataforma
// vive en `androidMain` / `jvmMain`. Ver ADR-013 y el spike (spike/kmp-persistence).
// -----------------------------------------------------------------------------
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    // Necesario para que los `@Serializable` de los mappers (p. ej. RhythmPatternMapper)
    // generen su serializador: sin el plugin, `encodeToString` falla en tiempo de ejecución.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.grafitto.laudate.data"
        compileSdk = 36
        minSdk = 26
    }
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            // `api`: la superficie del módulo incluye `LaudateDatabase : RoomDatabase`, que los
            // consumidores (p. ej. :desktopApp) necesitan en su classpath para tipar la base.
            // `room-runtime` trae el soporte de coroutines/Flow (room-ktx NO es KMP).
            api(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("jvmMain").dependencies {
            // Driver SQLite empaquetado para escritorio.
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("androidMain").dependencies {
            implementation(libs.androidx.datastore.preferences)
            // FileProvider, para exponer las copias automáticas (almacén privado) al compartirlas.
            implementation(libs.androidx.core.ktx)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}
