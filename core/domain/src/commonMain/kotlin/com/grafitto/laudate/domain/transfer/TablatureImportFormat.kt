package com.grafitto.laudate.domain.transfer

/**
 * Formatos de **partitura** que Canticora sabe convertir en una tablatura propia.
 *
 * El usuario elige un fichero en el selector del sistema y el dominio decide **qué es** por la
 * extensión (y, si falta, por el contenido). Todos los parsers son **tolerantes**: lo que no
 * entienden —símbolos de Guitar Pro, adornos de MuseScore, dinámicas raras— se **ignora en
 * silencio**, nunca lanza una excepción ni deja el XML a la vista.
 *
 * Guitar Pro 3/4/5 (`.gp3`, `.gp4`, `.gp5`) es un formato **binario propietario** sin
 * especificación pública: se reconoce para poder dar un mensaje claro («expórtalo como MusicXML o
 * `.gpx`») en vez de un error de parseo.
 */
enum class TablatureImportFormat(
    /** Nombre que se muestra al usuario. */
    val label: String,
    /** Extensiones aceptadas, en minúsculas y sin punto. */
    val extensions: List<String>,
    /** Tipos MIME que declara el selector del sistema. */
    val mimeTypes: List<String>,
    /**
     * Extensiones cuyo contenido es un **contenedor ZIP** (hay que abrir una entrada interna).
     * MusicXML solo lo es en `.mxl`; el `.musicxml`/`.xml` va en texto plano.
     */
    val archiveExtensions: List<String> = emptyList(),
    /** `false` para los formatos que solo se **reconocen** para avisar de que no se pueden leer. */
    val isSupported: Boolean = true
) {
    /** **MusicXML**: el idioma franco de MuseScore, Guitar Pro, TuxGuitar, Sibelius y Finale. */
    MUSICXML(
        label = "MusicXML",
        extensions = listOf("musicxml", "xml", "mxl"),
        mimeTypes = listOf(
            "application/vnd.recordare.musicxml",
            "application/vnd.recordare.musicxml+xml",
            "application/xml",
            "text/xml"
        ),
        archiveExtensions = listOf("mxl")
    ),

    /** **MuseScore** (`.mscz`): ZIP con el XML `.mscx` dentro. */
    MSCZ(
        label = "MuseScore",
        extensions = listOf("mscz"),
        mimeTypes = listOf("application/zip", "application/x-mscz"),
        archiveExtensions = listOf("mscz")
    ),

    /** **Guitar Pro 7 y 8** (`.gp`, `.gpx`): ZIP con `Content/score.gpif` (XML). */
    GPIF(
        label = "Guitar Pro 7/8",
        extensions = listOf("gp", "gpx"),
        mimeTypes = listOf("application/zip"),
        archiveExtensions = listOf("gp", "gpx")
    ),

    /** **TuxGuitar** (`.tg`): ZIP con `song.tg` (XML). */
    TUXGUITAR(
        label = "TuxGuitar",
        extensions = listOf("tg"),
        mimeTypes = listOf("application/zip"),
        archiveExtensions = listOf("tg")
    ),

    /** **Guitar Pro 3/4/5**: binario propietario, solo se reconoce para guiar al usuario. */
    GP_LEGACY(
        label = "Guitar Pro 3/4/5",
        extensions = listOf("gp3", "gp4", "gp5"),
        mimeTypes = listOf("application/octet-stream"),
        isSupported = false
    );

    /** `true` si el fichero [fileName] viaja dentro de un ZIP y hay que abrir una entrada. */
    fun isArchiveFile(fileName: String): Boolean =
        extensionOf(fileName) in archiveExtensions

    /** Extensión normalizada de un nombre de fichero (sin punto, en minúsculas). */
    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").trim().lowercase()

    /** Descripción para el mensaje de error cuando el formato no se puede leer. */
    val unsupportedHint: String?
        get() = if (isSupported) {
            null
        } else {
            "Canticora no puede leer $label: es un formato binario cerrado. " +
                "Ábrelo en $label y expórtalo como MusicXML (o como .gpx) para importarlo."
        }

    companion object {

        /** Formatos que el usuario puede importar de verdad. */
        val supported: List<TablatureImportFormat> = entries.filter { it.isSupported }

        /** Todas las extensiones aceptadas por el selector, con punto (`".mscz"`). */
        val pickerExtensions: List<String> = entries.flatMap { it.extensions }.map { ".$it" }

        /** Tipos MIME que se pasan al selector del sistema (sin repetir). */
        val pickerMimeTypes: List<String> = entries.flatMap { it.mimeTypes }.distinct()

        /** Formato reconocido por el **nombre** del fichero, o `null` si la extensión no es de partitura. */
        fun fromFileName(fileName: String?): TablatureImportFormat? {
            val extension = fileName?.substringAfterLast('.', "")?.trim()?.lowercase()
            if (extension.isNullOrBlank()) return null
            return entries.firstOrNull { extension in it.extensions }
        }

        /**
         * Formato deducido del **contenido** cuando el nombre no ayuda (un archivo compartido sin
         * extensión, por ejemplo). No abre ZIP: eso lo resuelve el lector de ficheros.
         */
        fun fromContent(text: String): TablatureImportFormat? = when {
            text.contains("<score-partwise") || text.contains("<score-timewise") -> MUSICXML
            text.contains("<GPIF") -> GPIF
            text.contains("<tuxguitar") -> TUXGUITAR
            text.contains("<museScore") -> MSCZ
            else -> null
        }
    }
}
