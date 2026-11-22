package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.transfer.ScoreFileReader
import com.grafitto.laudate.domain.transfer.TablatureImportFormat

/**
 * Caso de uso: importar una **partitura desde un fichero** y convertirla en la tablatura del canto.
 *
 * Resuelve las dos incógnitas que la plataforma no debe conocer:
 *  - **qué formato es** — por la extensión y, si no basta, por el contenido;
 *  - **dónde está el XML** — en un fichero de texto (MusicXML) o dentro de un ZIP (`.mscz`, `.gp`,
 *    `.gpx`, `.tg`, `.mxl`).
 *
 * Una vez tiene el texto, delega en [ImportTablatureUseCase], que es quien parsea y sustituye la
 * tablatura del canto.
 *
 * Guitar Pro 3/4/5 (`.gp3`, `.gp4`, `.gp5`) es un binario propietario: se **reconoce** para
 * devolver un mensaje que explica cómo exportarlo, no para intentar leerlo.
 */
class ImportTablatureFromFileUseCase(
    private val fileReader: ScoreFileReader,
    private val importTablature: ImportTablatureUseCase
) {

    /**
     * Lee [fileUri] y **reemplaza** la tablatura del canto [songId].
     *
     * @param fileName nombre visible del fichero (el que da el selector); de él sale el formato.
     * @throws IllegalArgumentException si el formato no se puede leer o el fichero está vacío.
     */
    suspend operator fun invoke(
        fileUri: String,
        fileName: String,
        songId: String,
        title: String,
        tuning: Tuning = Tuning.GUITAR
    ): TablatureImportResult {
        require(songId.isNotBlank()) { "Una tablatura siempre pertenece a un canto" }

        val byName = TablatureImportFormat.fromFileName(fileName)
        byName?.unsupportedHint?.let { throw IllegalArgumentException(it) }

        val resolved = resolve(fileUri, fileName, byName)
            ?: throw IllegalArgumentException(
                "No se pudo leer «$fileName». Comprueba que es un archivo de partitura " +
                    "(MusicXML, MuseScore, Guitar Pro o TuxGuitar)."
            )

        return importTablature(
            format = resolved.first,
            content = resolved.second,
            songId = songId,
            title = title,
            tuning = tuning
        )
    }

    /** Formato y XML del fichero, o `null` si no hay nada legible. */
    private suspend fun resolve(
        fileUri: String,
        fileName: String,
        byName: TablatureImportFormat?
    ): Pair<TablatureImportFormat, String>? {
        // 1. La extensión ya dice que el documento va dentro de un ZIP.
        if (byName != null && byName.isArchiveFile(fileName)) {
            return readArchiveContent(fileUri, byName)?.let { byName to it }
        }

        // 2. Texto plano: MusicXML, o cualquier XML reconocible por su contenido.
        val text = fileReader.readText(fileUri)
        if (text != null && text.isNotBlank()) {
            val format = byName ?: TablatureImportFormat.fromContent(text)
            if (format != null) return format to text
        }

        // 3. ZIP sin extensión útil (un `.gpx` renombrado, un archivo compartido sin nombre).
        val preferred = byName ?: TablatureImportFormat.MSCZ
        readArchiveContent(fileUri, preferred)?.let { return preferred to it }
        TablatureImportFormat.entries
            .filter { it != preferred && it.isSupported && it.archiveExtensions.isNotEmpty() }
            .forEach { format ->
                readArchiveContent(fileUri, format)?.let { return format to it }
            }
        return null
    }

    /**
     * Abre el ZIP de [fileUri] y devuelve su XML, o `null` si no hay ninguna entrada legible.
     *
     * Cada formato guarda su XML en un sitio distinto y algunos con nombre variable, así que se
     * prueban en orden: el nombre conocido y, si falla, la extensión interna.
     */
    private suspend fun readArchiveContent(
        fileUri: String,
        format: TablatureImportFormat
    ): String? = when (format) {
        TablatureImportFormat.MSCZ ->
            fileReader.readArchiveEntryEndingWith(fileUri, ".mscx")

        TablatureImportFormat.GPIF ->
            fileReader.readArchiveEntry(fileUri, "Content/score.gpif")
                ?: fileReader.readArchiveEntryEndingWith(fileUri, ".gpif")

        TablatureImportFormat.TUXGUITAR ->
            fileReader.readArchiveEntry(fileUri, "song.tg")
                ?: fileReader.readArchiveEntryEndingWith(fileUri, "song.tg")

        TablatureImportFormat.MUSICXML -> readCompressedMusicXml(fileUri)

        TablatureImportFormat.GP_LEGACY -> null
    }

    /**
     * `.mxl`: un ZIP que declara en `META-INF/container.xml` **cuál** es su MusicXML
     * (`<rootfile full-path="…">`). Si el manifiesto falta o no se puede leer, se cae a la primera
     * entrada con extensión de MusicXML.
     */
    private suspend fun readCompressedMusicXml(fileUri: String): String? {
        val manifest = fileReader.readArchiveEntry(fileUri, "META-INF/container.xml")
        val path = manifest?.let {
            Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1)
        }
        if (path != null) {
            fileReader.readArchiveEntry(fileUri, path)?.let { return it }
        }
        return fileReader.readArchiveEntryEndingWith(fileUri, ".musicxml")
            ?: fileReader.readArchiveEntryEndingWith(fileUri, ".xml")
    }
}
