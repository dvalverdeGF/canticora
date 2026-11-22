package com.grafitto.laudate.domain.export

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.text.formatLongDateEs

/**
 * Un canto del repertorio, con el momento litúrgico y los ajustes propios de la celebración.
 */
data class CelebrationPdfSong(
    val song: Song,
    val moment: String,
    val overrideKey: String? = null
)

/**
 * Motor de maquetación del **PDF de una misa o evento completo**.
 *
 * Requisitos de diseño:
 *  - **Un canto por página**: cada canto arranca en página nueva, sin continuar en la página
 *    anterior. Si un canto ocupa más de una página, sus continuaciones llevan el título como
 *    encabezado vivo.
 *  - **Portada con el índice**: la primera página muestra el nombre de la celebración, sus datos
 *    (tipo, fecha, lugar) y la lista ordenada de cantos con su momento litúrgico.
 *  - **Tono de cada canto** ajustado al de la celebración (override) si lo hubiera.
 *
 * Lógica pura (sin Android) para poder probar la paginación en JVM.
 */
object CelebrationPdfLayout {

    fun layout(
        celebration: Celebration,
        songs: List<CelebrationPdfSong>,
        options: SongPdfOptions = SongPdfOptions()
    ): SongPdfDocument {
        // La portada (título, datos e índice) se pagina por su cuenta: si el repertorio tiene
        // muchos cantos, el índice puede ocupar más de una página sin mezclarse con el primero.
        val groups = mutableListOf<PageGroup>()

        groups += SongPdfLayout.paginateRows(coverRows(celebration, songs, options), options)
            .map { rows ->
                PageGroup(
                    rows = rows,
                    // La portada no lleva banda; su título es una fila dentro de la página.
                    header = null,
                    isTitlePage = true
                )
            }

        // Un canto por página: las filas de cada canto se paginan aisladas del resto. La banda de
        // cada página lleva el **título del canto** (no el de la celebración), de modo que quien
        // canta sepa siempre qué está cantando, igual que en un cancionero impreso.
        songs.forEach { entry ->
            val songForPdf = entry.song.copy(
                originalKey = entry.overrideKey?.takeIf { it.isNotBlank() } ?: entry.song.originalKey
            )
            val rows = buildList {
                entry.moment.takeIf { it.isNotBlank() }?.let { moment ->
                    add(
                        PdfRow(
                            kind = PdfRowKind.SECTION_HEADER,
                            text = moment.uppercase(),
                            heightPt = options.sectionFontSizePt * 1.6f,
                            leadingGapPt = 0f
                        )
                    )
                }
                addAll(SongPdfLayout.buildRows(songForPdf, options))
            }
            val songTitle = songForPdf.title.ifBlank { "Sin título" }
            groups += SongPdfLayout.paginateRows(rows, options).map { songRows ->
                PageGroup(rows = songRows, header = songTitle, isTitlePage = false)
            }
        }

        val total = groups.size
        return SongPdfDocument(
            title = celebration.name.ifBlank { "Repertorio" },
            pages = groups.mapIndexed { index, group ->
                PdfPage(
                    rows = group.rows,
                    index = index,
                    totalPages = total,
                    header = group.header,
                    isTitlePage = group.isTitlePage
                )
            },
            options = options
        )
    }

    /** Página ya paginada junto con los datos de su banda superior. */
    private data class PageGroup(
        val rows: List<PdfRow>,
        val header: String?,
        val isTitlePage: Boolean
    )

    /** Filas de la portada: título, datos de la celebración e índice de cantos. */
    private fun coverRows(
        celebration: Celebration,
        songs: List<CelebrationPdfSong>,
        options: SongPdfOptions
    ): List<PdfRow> = buildList {
        add(
            PdfRow(
                kind = PdfRowKind.TITLE,
                text = celebration.name.ifBlank { "Repertorio" },
                heightPt = options.titleFontSizePt * 1.5f,
                centered = true
            )
        )
        add(
            PdfRow(
                kind = PdfRowKind.SECTION_HEADER,
                text = celebration.celebrationType.ifBlank { "Misa" }.uppercase(),
                heightPt = options.sectionFontSizePt * 1.6f,
                leadingGapPt = 6f
            )
        )
        add(
            PdfRow(
                kind = PdfRowKind.METADATA,
                text = formatDate(celebration.dateTimestamp),
                heightPt = options.metaFontSizePt * 1.6f,
                leadingGapPt = 2f
            )
        )
        celebration.location?.takeIf { it.isNotBlank() }?.let { place ->
            add(
                PdfRow(
                    kind = PdfRowKind.METADATA,
                    text = place,
                    heightPt = options.metaFontSizePt * 1.6f,
                    leadingGapPt = 2f
                )
            )
        }
        celebration.description?.takeIf { it.isNotBlank() }?.lines()?.forEach { line ->
            add(
                PdfRow(
                    kind = PdfRowKind.COMMENT,
                    text = line,
                    heightPt = options.metaFontSizePt * 1.45f,
                    leadingGapPt = 2f
                )
            )
        }
        add(
            PdfRow(
                kind = PdfRowKind.SECTION_HEADER,
                text = "ÍNDICE DE CANTOS",
                heightPt = options.sectionFontSizePt * 1.6f,
                leadingGapPt = 18f
            )
        )
        songs.forEachIndexed { index, entry ->
            val numberLabel = entry.song.number?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            val moment = entry.moment.takeIf { it.isNotBlank() }?.let { "$it — " }.orEmpty()
            add(
                PdfRow(
                    kind = PdfRowKind.LYRICS,
                    text = "${index + 1}. $moment${entry.song.title}$numberLabel",
                    heightPt = options.lyricsFontSizePt * 1.45f,
                    leadingGapPt = 2f,
                    fontSizePt = options.lyricsFontSizePt
                )
            )
        }
        if (songs.isEmpty()) {
            add(
                PdfRow(
                    kind = PdfRowKind.COMMENT,
                    text = "Este repertorio no tiene cantos asignados.",
                    heightPt = options.metaFontSizePt * 1.45f,
                    leadingGapPt = 4f
                )
            )
        }
    }

    private fun formatDate(timestamp: Long): String = formatLongDateEs(timestamp)
}
