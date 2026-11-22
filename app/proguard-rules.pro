# Reglas ProGuard/R8 para la build de release de Laudate.
#
# La app NO usa Hilt en runtime (DI manual con AppContainer), pero las dependencias de Hilt
# siguen declaradas en el classpath, así que sus reglas las aporta la propia librería.
# Aquí van solo las reglas necesarias para que R8 no rompa el comportamiento en runtime.

# ── Room ────────────────────────────────────────────────────────────────────────
# Room genera implementaciones en tiempo de compilación y usa reflexión para los
# TypeConverters y las entidades. Hay que conservar las clases anotadas.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ── kotlinx.serialization ───────────────────────────────────────────────────────
# Los @Serializable se resuelven por reflexión sobre el companion/serializer generado.
# Sin estas reglas falla al importar catálogo, repertorio, ritmos y copia de seguridad.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.grafitto.laudate.**$$serializer { *; }
-keepclassmembers class com.grafitto.laudate.** {
    *** Companion;
}
-keepclasseswithmembers class com.grafitto.laudate.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Modelos de dominio serializables ────────────────────────────────────────────
# Los DTO de transferencia/backup se reconstruyen por reflexión: no se pueden ofuscar
# los nombres de sus campos porque viajan en el JSON de los archivos.
-keepclassmembers class com.grafitto.laudate.domain.transfer.** { *; }
-keepclassmembers class com.grafitto.laudate.domain.backup.** { *; }

# ── Compose ─────────────────────────────────────────────────────────────────────
# Compose usa un compilador propio; sus lambdas composables no deben romperse.
-dontwarn androidx.compose.**

# ── kotlinx.coroutines ──────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Android / ViewModel ─────────────────────────────────────────────────────────
# Los ViewModel se instancian por reflexión desde `viewModel {}` en Compose.
-keep class * extends androidx.lifecycle.ViewModel { <init>(); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }

# ── Enumerados y anotaciones ────────────────────────────────────────────────────
# La app persiste nombres de enum (secciones, tipos, notación) en Room y DataStore:
# conservarlos evita corromper datos al deserializar.
-keepclassmembers enum com.grafitto.laudate.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Datos de stack para informes ─────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
