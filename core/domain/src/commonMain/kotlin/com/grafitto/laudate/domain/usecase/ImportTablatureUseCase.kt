package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.serializer.GpifTabParser
import com.grafitto.laudate.domain.serializer.MsczParser
import com.grafitto.laudate.domain.serializer.MusicXmlTabParser
import com.grafitto.laudate.domain.serializer.TabDocumentTitle
import com.grafitto.laudate.domain.serializer.TuxGuitarTabParser
import com.grafitto.laudate.domain.transfer.TablatureImportFormat

/**
 * Resultado de importar una **partitura** como tablatura de un canto.
 */
data class TablatureImportResult(
    /** Formato del que se ha importado. */
    val format: TablatureImportFormat,
    /** Título de la tablatura importada (el del fichero o el del canto). */
    val title: String,
    /** Nº de compases de la tablatura importada. */
    val measureCount: Int,
    /** Nº de notas (pulsaciones) de la tablatura importada. */
    val noteCount: Int,
    /** **Tablaturas anteriores del canto** que se han eliminado al reemplazar. */
    val replaced: Int
)

/**
 * Caso de uso: convertir el **XML de una partitura** en la tablatura del canto, sustituyendo la que
 * hubiera.
 *
 * Es el **único** camino de importación de tablaturas: la plataforma se limita a abrir el fichero y
 * a entregar aquí el **texto** del documento (ya extraído del ZIP si era un contenedor). El dominio
 * decide qué parser usar según [format]:
 *
 * | Formato | Parser |
 * | :--- | :--- |
 * | [TablatureImportFormat.MUSICXML] | [MusicXmlTabParser] |
 * | [TablatureImportFormat.MSCZ] | [MsczParser] |
 * | [TablatureImportFormat.GPIF] | [GpifTabParser] |
 * | [TablatureImportFormat.TUXGUITAR] | [TuxGuitarTabParser] |
 *
 * Los parsers son **tolerantes**: los símbolos que Canticora no modela se **ignoran en silencio**,
 * así que un fichero de Guitar Pro lleno de efectos importa igual sin dar error.
 */
class ImportTablatureUseCase(
    private val tablatureRepository: TablatureRepository,
    /** Generador de ids (inyectable para pruebas deterministas). */
    private val generateId: () -> String = { newId() }
) {

    /**
     * Parsea [content] y **reemplaza** la tablatura del canto [songId].
     *
     * @param content el XML de la partitura (nunca el ZIP: eso lo abre la plataforma).
     * @param title título de la tablatura; si viene vacío se usa el que traiga el documento.
     * @throws IllegalArgumentException si el formato no se puede leer o el documento no tiene música.
     */
    suspend operator fun invoke(
        format: TablatureImportFormat,
        content: String,
        songId: String,
        title: String,
        tuning: Tuning = Tuning.GUITAR
    ): TablatureImportResult {
        require(songId.isNotBlank()) { "Una tablatura siempre pertenece a un canto" }
        require(content.isNotBlank()) { "El archivo está vacío" }
        format.unsupportedHint?.let { throw IllegalArgumentException(it) }

        // El título que escribe la plataforma es el **nombre del fichero**, que no dice nada de la
        // obra («partitura.gp», «score.musicxml»). Si el usuario no ha escrito uno, manda el que
        // declara el documento.
        val effectiveTitle = title.trim().ifBlank {
            TabDocumentTitle.resolve(format, content) ?: DEFAULT_TITLE
        }

        val parsed = when (format) {
            TablatureImportFormat.MUSICXML ->
                MusicXmlTabParser.parse(content, songId = songId, title = effectiveTitle, tuning = tuning)

            TablatureImportFormat.MSCZ ->
                MsczParser.parse(content, songId = songId, title = effectiveTitle, tuning = tuning)

            TablatureImportFormat.GPIF ->
                GpifTabParser.parse(content, songId = songId, title = effectiveTitle, tuning = tuning)

            TablatureImportFormat.TUXGUITAR ->
                TuxGuitarTabParser.parse(content, songId = songId, title = effectiveTitle, tuning = tuning)

            TablatureImportFormat.GP_LEGACY ->
                throw IllegalArgumentException(format.unsupportedHint ?: "Formato no soportado")
        }

        // Los compases **finales** vacíos que añaden los editores al cerrar el documento no son
        // música; los vacíos intermedios sí (un silencio de compás entre dos frases).
        val measures = parsed.measures.dropLastWhile { it.slots.isEmpty() }
        require(measures.isNotEmpty()) { "El archivo no contiene compases con música" }

        val imported = parsed.copy(
            id = generateId(),
            songId = songId,
            title = effectiveTitle,
            measures = measures,
            origin = TabOrigin.IMPORTED_TEXT
        )

        val replaced = tablatureRepository.replaceTablaturesOf(imported)
        return TablatureImportResult(
            format = format,
            title = imported.title,
            measureCount = measures.size,
            noteCount = measures.sumOf { measure -> measure.slots.sumOf { it.strings.size } },
            replaced = replaced
        )
    }

    private companion object {
        /** Título de última instancia cuando ni el usuario ni el documento dicen nada. */
        const val DEFAULT_TITLE = "Partitura importada"
    }
}
