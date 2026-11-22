package com.grafitto.laudate.domain.transfer

/**
 * Lectura del **contenido** de un fichero que el usuario ha elegido en el selector del sistema.
 *
 * El Dominio no puede abrir ficheros (Kotlin puro), pero sí decidir **qué** hay que leer: este
 * contrato le da lo justo para importar una partitura sin conocer `ContentResolver`, `File` ni
 * `ZipInputStream`.
 *
 * Las tres operaciones existen porque los formatos de partitura viajan de dos maneras:
 *  - **texto plano** — MusicXML (`.musicxml`, `.xml`), que se lee entero con [readText];
 *  - **contenedor ZIP** — `.mscz` de MuseScore, `.gp`/`.gpx` de Guitar Pro, `.tg` de TuxGuitar y
 *    `.mxl` de MusicXML comprimido, donde hay que abrir una **entrada interna** cuyo nombre puede
 *    ser fijo ([readArchiveEntry]) o variable ([readArchiveEntryEndingWith]).
 */
interface ScoreFileReader {

    /** Texto completo del fichero, o `null` si no se puede abrir. */
    suspend fun readText(fileUri: String): String?

    /** Texto de la entrada [entryName] del ZIP, o `null` si no existe o no es un ZIP. */
    suspend fun readArchiveEntry(fileUri: String, entryName: String): String?

    /**
     * Texto de la **primera** entrada cuyo nombre **termine** en [suffix] (sin distinguir
     * mayúsculas), o `null`. Es lo que resuelve los ZIP cuyo nombre de entrada lleva dentro el
     * nombre del documento (`.mscz` → `<nombre>.mscx`).
     */
    suspend fun readArchiveEntryEndingWith(fileUri: String, suffix: String): String?
}
