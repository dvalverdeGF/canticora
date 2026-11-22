package com.grafitto.laudate.domain.export

import com.grafitto.laudate.domain.model.music.NotationFormat
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.tablature.TabLayoutSettings

/**
 * (D-99) Colocación de la tablatura dentro del PDF del canto.
 */
enum class TablaturePlacement {
    /** Arriba, tras los datos del canto: se lee la música antes de cantar. */
    TOP,

    /** Al final del documento, como **apéndice**: primero toda la letra. */
    END;

    companion object {
        val DEFAULT = TOP
    }
}

/**
 * Opciones de maquetación del PDF de una canción (tamaños en puntos, 1 pt = 1/72").
 * Los valores por defecto dan una tipografía legible en A4 para leer en atril.
 */
data class SongPdfOptions(
    val titleFontSizePt: Float = 20f,
    val sectionFontSizePt: Float = 9.5f,
    val lyricsFontSizePt: Float = 11.5f,
    val chordsFontSizePt: Float = 9.5f,
    val metaFontSizePt: Float = 8f,
    /** Margen uniforme de la página. */
    val marginPt: Float = 42f,
    /** Ancho de la franja del margen izquierdo donde viven las etiquetas de sección rotadas. */
    val marginLabelWidthPt: Float = 18f,
    /**
     * Compresión horizontal del texto (1.0 = normal). Un valor < 1 «condensa» las letras y cabe
     * más texto por línea sin reducir el cuerpo, manteniendo la legibilidad musical.
     */
    val textScaleX: Float = 0.86f,
    /**
     * Nomenclatura de los acordes del PDF (Latina `Do`, Anglosajona `C`, o NNS). Es la preferencia
     * **global** de la app: el PDF debe imprimir los acordes en el mismo sistema que el visor, no
     * en el que viniera escrito el canto (el cancionero importado suele traer notación latina).
     */
    val notationSystem: NotationSystem = NotationSystem.ENGLISH,
    /** Separación mínima (pt) entre dos acordes consecutivos de la misma línea para que no se
     *  solapen: si sus anchos reales se pisan, el segundo se desplaza a la derecha y el primero
     *  un poco a la izquierda, repartiendo la holgura. */
    val minChordGapPt: Float = 3f,
    /** Separación entre las dos columnas de una página a dos columnas. */
    val columnGapPt: Float = 18f,
    /**
     * Separación vertical entre secciones consecutivas: da aire suficiente para distinguir el
     * estribillo de las estrofas sin abrir huecos desproporcionados. Se añade como espacio previo
     * a la **primera fila de contenido** de cada sección (salvo la primera del canto), de modo que
     * el bloque y su rótulo lateral sigan alineados por arriba.
     */
    val sectionGapPt: Float = 10f,
    /** Alto reservado por la cabecera de continuación en páginas > 1. */
    val runningHeaderHeightPt: Float = 24f,
    /** Alto reservado por el pie de página con el branding. */
    // `drawFooter` dibuja la línea de separación a `PAGE_HEIGHT - (baseline - metaFontSize - 6)` y
    // el texto en `PAGE_HEIGHT - margin * 0.55`. Con `marginPt = 42` eso son 36,2 pt por debajo del
    // área de contenido; reservar solo 26 dejaba al paginador contar 10 pt de más y el último
    // renglón de la letra se imprimía **encima** del pie («Página 1 de 1»).
    // (D-98) 46 pt: con 38 el rótulo de sección rotado («ESTROFA 2») se salía por el pie en
    // páginas largas, porque el rótulo se centra en su bloque y puede colgar más que el texto.
    val footerHeightPt: Float = 46f,
    /** Centra las líneas cantables, como en los cancioneros de coro. */
    val centerLyrics: Boolean = true,
    /**
     * Ampliación de la letra del estribillo respecto al cuerpo normal (1.0 = sin énfasis).
     * El estribillo se repite y se canta a coro, así que se destaca en negrita y algo mayor.
     */
    val chorusScale: Float = 1.08f,
    /**
     * Incluye los acordes sobre la letra. Con `false` el PDF sale **solo con la letra**, útil
     * para la asamblea; la elección se recuerda como preferencia del usuario.
     */
    val includeChords: Boolean = true,
    /**
     * Modo de visualización del PDF (mismas opciones que el visor, ADR-017): solo letra, letra y
     * acordes, letra y tablatura, letra + acordes + tablatura o solo tablatura. Gobierna qué capas
     * se imprimen; [includeChords] se conserva por compatibilidad pero [displayMode] manda.
     */
    val displayMode: SongDisplayMode = SongDisplayMode.CHORDS_ABOVE_TEXT,
    /**
     * Número de columnas de la página (1 o 2). El motor solo usa 2 columnas cuando la canción
     * **no cabe en una página a una columna**, para evitar partirla en dos hojas.
     */
    val columns: Int = 1,
    /**
     * **Escala de notación** de la tablatura imprimible (roadmap C.3): determina el alto de la
     * rejilla (cuánto ocupa cada sistema) y, con él, cuántos sistemas caben por página. Un valor
     * mayor imprime la tablatura más grande (menos sistemas por hoja); uno menor, más compacta.
     * Por defecto, la escala estándar (equivalentes de los antiguos valores fijos).
     */
    val tablatureScale: com.grafitto.laudate.domain.tablature.TabNotationScale =
        com.grafitto.laudate.domain.tablature.TabNotationScale.DEFAULT,
    /**
     * **Ajustes del pentagrama de tablatura** con los que se imprime
     * ([com.grafitto.laudate.domain.tablature.TabStaffOptions]).
     *
     * (D-46) Antes el PDF maquetaba **siempre** con los valores por defecto, así que si el usuario
     * elegía en el editor la blanca rayada, las cifras como letras o las líneas continuas, el papel
     * salía distinto de la pantalla. Ahora el exportador **puede** recibir los mismos ajustes que el
     * lienzo; quien llama decide si los pasa (hoy solo lo hacen las pruebas, porque
     * [com.grafitto.laudate.domain.tablature.TabStaffOptions] todavía no se persiste).
     */
    val tablatureStaff: com.grafitto.laudate.domain.tablature.TabStaffOptions =
        com.grafitto.laudate.domain.tablature.TabStaffOptions()
) {
    /**
     * Ancho útil de una **columna** de contenido.
     *
     * Reserva la franja de las etiquetas de sección en **ambos** lados (los márgenes exteriores):
     * la columna izquierda rotula a su izquierda y la derecha a su derecha, de modo que las
     * etiquetas nunca se solapan entre columnas. A una columna se usan las dos franjas, una a cada
     * lado del texto.
     */
    val contentWidthPt: Float
        get() = baseContentWidthPt + marginLabelWidthPt

    /** Ancho total disponible para el contenido, descontadas ambas franjas de etiqueta. */
    val baseContentWidthPt: Float
        get() = SongPdfLayout.PAGE_WIDTH_PT - marginPt * 2 - marginLabelWidthPt * 2

    /** Ancho de una columna en una página de [n] columnas. */
    fun columnWidthPt(n: Int): Float =
        if (n <= 1) contentWidthPt else (baseContentWidthPt - (n - 1) * columnGapPt) / n

    /** Alto útil de una página de contenido. */
    val contentHeightPt: Float
        get() = SongPdfLayout.PAGE_HEIGHT_PT - marginPt * 2 - footerHeightPt

    /** ¿Se imprimen los acordes? Deriva del modo (con compatibilidad hacia [includeChords]). */
    val pdfShowsChords: Boolean
        get() = if (displayMode == SongDisplayMode.CHORDS_ABOVE_TEXT) includeChords else displayMode.showsChords

    /** ¿Se imprime la letra? */
    val pdfShowsLyrics: Boolean
        get() = displayMode != SongDisplayMode.TABLATURE_ONLY

    /** ¿Se imprime la tablatura? */
    val pdfShowsTablature: Boolean
        get() = displayMode.showsTablature

    /**
     * (D-99) **Dónde** se imprime la tablatura en el papel.
     *
     * No todo el mundo quiere la música delante: para estudiar conviene arriba, y para cantar en
     * el atril, la letra primero y la tablatura como apéndice. Se decide en la exportación.
     */
    val tablaturePlacement: TablaturePlacement = TablaturePlacement.TOP
}

/** Naturaleza de una fila del documento, para que el pintor elija fuente y color. */
enum class PdfRowKind {
    TITLE,
    METADATA,
    SECTION_HEADER,
    /** Fila con acordes encima de la letra. */
    CHORDS_OVER_LYRICS,
    /** Fila de **solo acordes** (intro/riff/outro), sin letra debajo. */
    CHORD_ONLY,
    /** Rejilla de rasgueo (notación de flechas). */
    RHYTHM,
    /** Tablatura dibujada vectorialmente (primitivas ya maquetadas). */
    TABLATURE,
    LYRICS,
    COMMENT,
    SPACER
}

/** Acorde anclado a una posición de la letra (dentro de su línea visual). */
data class PdfChordMark(
    val text: String,
    /** Posición en caracteres respecto al comienzo de la línea visual. */
    val characterIndex: Int,
    /** Ancho medido del acorde, para calcular el hueco mínimo entre acordes. */
    val widthPt: Float = 0f,
    /**
     * Desplazamiento horizontal **exacto** (pt) desde el inicio de la línea. Se usa en las filas
     * de solo acordes ([PdfRowKind.CHORD_ONLY]), donde no hay letra de la que derivar la posición.
     */
    val xOffsetPt: Float = 0f
)

/** Fila ya medida y lista para pintar. */
data class PdfRow(
    val kind: PdfRowKind,
    val text: String = "",
    val chords: List<PdfChordMark> = emptyList(),
    val heightPt: Float = 0f,
    /** Separación añadida respecto a la fila anterior. */
    val leadingGapPt: Float = 0f,
    /** Centra la fila en el ancho útil (letra de cancionero). */
    val centered: Boolean = false,
    /** El pintor la destaca con la variante de mayor peso (estribillos). */
    val emphasis: Boolean = false,
    /** Tamaño de fuente efectivo de la fila; 0 = el que corresponda por defecto a su tipo. */
    val fontSizePt: Float = 0f,
    /**
     * Etiqueta de la sección (p. ej. «ESTROFA», «ESTRIBILLO») que el pintor dibuja **rotada 90°**
     * en el margen izquierdo, a la altura de esta fila. Ahorra el renglón que antes ocupaba el
     * encabezado de sección. Solo se rellena en la **primera** fila de cada sección.
     */
    val marginLabel: String? = null,
    /**
     * Dibuja un tramo de **regla vertical** a la izquierda del contenido en esta fila. Al marcarse
     * en todas las filas de una sección, los tramos se encadenan y forman una línea continua que
     * recorre la sección de arriba abajo (como en una partitura guía).
     */
    val sectionRule: Boolean = false,
    /**
     * Altura total (pt) de la sección a la que pertenece la fila con [marginLabel]. Permite al
     * pintor **centrar verticalmente** la etiqueta dentro de la sección para que no se salga ni
     * pise el título ni las secciones vecinas.
     */
    val sectionHeightPt: Float = 0f,
    /**
     * Marca de repetición (**bis** / **×N**) que el pintor dibuja **pequeña** al inicio de la
     * sección (junto a la etiqueta). Solo se rellena en la **primera** fila de una sección
     * repetida, de modo que no alarga el rótulo de la sección.
     */
    val repeatMark: String? = null,
    /**
     * Primitivas de una **tablatura** ya maquetadas ([TablatureDrawing], en px de la rejilla). El
     * pintor las escala al ancho de la columna y las dibuja (líneas + cifras). Solo se rellena en
     * filas [PdfRowKind.TABLATURE].
     */
    val tablature: com.grafitto.laudate.domain.tablature.TablatureDrawing? = null,
    /**
     * `true` si esta fila **abre página**: el paginador cierra la hoja (o la columna) anterior aunque
     * quede sitio. Lo pone el **salto de página** de la tablatura (`Ctrl`+`Return` de MuseScore,
     * `TabMeasure.forcePageBreak`) en la primera fila del sistema que lo lleva.
     */
    val pageBreakBefore: Boolean = false,
    /**
     * (D-53) **Desplazamiento a la izquierda** (pt) con el que se dibuja la tablatura respecto a
     * `contentX`. La tablatura ocupa el **ancho de página** (margen a margen), como en MuseScore, y
     * no la columna de texto: así recupera la franja de etiquetas de sección, que en una partitura no
     * existe. Sin esto el sistema arrancaba 18 pt a la derecha del de MuseScore y el reparto en
     * líneas perdía un compás por sistema (4/10/10/8 en vez de 5/11/11/5 en el `salmo2`).
     */
    val tablatureInsetPt: Float = 0f,
    /**
     * (D-57) **Dónde cae la pauta dentro de la fila** (pt desde el techo de la fila): el borde
     * superior de la primera cuerda y el inferior de la última.
     *
     * MuseScore mide la distancia entre sistemas **de pauta a pauta**
     * (`PageLayout::distributeStaves`, `pagelayout.cpp:653`: `system->y() + sysStaff->bbox().y()`),
     * no de caja a caja. Lo que cuelga por debajo de la última cuerda —plicas, dinámica— vive dentro
     * del hueco y no empuja al sistema siguiente.
     */
    val tablatureGridTopPt: Float = 0f,
    /** (D-57) Borde inferior de la última cuerda, en pt desde el techo de la fila. */
    val tablatureGridBottomPt: Float = 0f
)

/** Una página del documento, con sus filas ya distribuidas. */
data class PdfPage(
    val rows: List<PdfRow>,
    val index: Int,
    val totalPages: Int,
    /** Texto de la banda superior; `null` deja la página sin banda (portada del repertorio). */
    val header: String? = null,
    /** La banda superior se pinta a tamaño de título en lugar de como encabezado vivo. */
    val isTitlePage: Boolean = false,
    /** Número de columnas de la página (1 o 2). */
    val columns: Int = 1,
    /** Índice de [rows] donde empieza la segunda columna; -1 si no hay corte. */
    val secondColumnStartIndex: Int = -1,
    /**
     * Filas de **cabecera a ancho completo** (datos del canto, notas, ritmo global) que se pintan
     * encima de las columnas, de lado a lado de la página, para que no invadan la columna derecha.
     */
    val headerRows: List<PdfRow> = emptyList()
)

/** Documento completo, listo para dibujar. */
data class SongPdfDocument(
    val title: String,
    val pages: List<PdfPage>,
    val options: SongPdfOptions
)

/**
 * Motor de maquetación del PDF de una canción, en **Kotlin puro**.
 *
 * Convierte un [Song] en una lista de páginas con filas ya medidas y ajustadas al ancho de
 * columna. Se separa del pintor (`SongPdfExporter`, en la capa de datos) para poder **probar
 * el reparto de líneas, el salto de palabra y la paginación sin Android ni emulador**.
 *
 * Las métricas de texto son aproximadas (Helvetica ≈ 0.5 em por carácter): son deterministas y
 * deliberadamente conservadoras, de modo que una línea nunca se sale del margen aunque la fuente
 * real sea algo más ancha.
 */
object SongPdfLayout {

    /** A4 vertical en puntos. */
    const val PAGE_WIDTH_PT = 595f
    const val PAGE_HEIGHT_PT = 842f

    /** Ancho medio de carácter como fracción del tamaño de fuente (Helvetica). */
    private const val CHAR_WIDTH_FACTOR = 0.5f

    /**
     * Ancho medio de carácter para los **acordes** (van en negrita, más ancha que la letra) como
     * fracción del tamaño de fuente. Algo mayor que [CHAR_WIDTH_FACTOR] para no subestimar el
     * solapamiento y que la separación inteligente actúe a tiempo.
     */
    private const val CHORD_WIDTH_FACTOR = 0.58f

    /**
     * Umbral de altura (pt) por debajo del cual una sección se considera **corta** y su rótulo
     * lateral se abrevia (p. ej. `ESTRIBILLO` → `CORO`). Con el cuerpo de letra por defecto,
     * ~3 líneas: a partir de ahí el rótulo completo ya cabe centrado sin dominar el bloque.
     */
    private const val SHORT_SECTION_HEIGHT_PT = 60f

    /** Factor de interlineado. */
    private const val LINE_HEIGHT_FACTOR = 1.32f

    /**
     * (D-98) Desplazamiento con el que los exportadores pintan el dibujo de la tablatura dentro de
     * su fila (`top + 4`). La fila tiene que **reservarlo**, o el sistema sobresale por abajo y
     * pisa el texto que viene detrás.
     */
    const val TABLATURE_TOP_INSET_PT = 4f

    /** (D-98) Aire entre el último sistema de tablatura y el texto que le sigue. */
    const val TABLATURE_TEXT_GAP_PT = 14f

    fun layout(
        song: Song,
        options: SongPdfOptions = SongPdfOptions(),
        /** Tablaturas del canto, para imprimirlas cuando el modo las incluye. */
        tablatures: List<com.grafitto.laudate.domain.model.tablature.Tablature> = emptyList()
    ): SongPdfDocument {
        val title = song.title.ifBlank { "Sin título" }

        // A una columna el contenido cabe en una sola hoja: se queda así. El título se pinta en la
        // banda superior (igual que a dos columnas) para que el estilo sea consistente.
        val singleRows = buildRows(
            song,
            options,
            tablatures = tablatures,
            tablatureInsetPt = options.marginLabelWidthPt
        ).filterNot { it.kind == PdfRowKind.TITLE }
        val singleGroups = paginateRows(singleRows, options)
        if (singleGroups.size <= 1) {
            return SongPdfDocument(
                title,
                singleColumnPages(singleGroups, title, options),
                options.copy(columns = 1)
            )
        }

        // (D-99) El canto no cabe en una hoja. Se prueba primero el reparto a **dos columnas de
        // texto**, que es el que aprovecha el papel. La **tablatura no entra en la columna**: va en
        // la banda superior a **ancho completo** (`tablatureWidthPt`), así que sigue teniendo los
        // 493 pt útiles y ningún sistema se estrecha. Solo si las dos columnas no ahorran hojas se
        // vuelve a una columna.

        // La canción no cabe en una hoja: se maqueta a **dos columnas** (con el título en la banda
        // a ancho completo) para reducir el número de hojas.
        val columnWidth = options.columnWidthPt(2)
        val allRows = buildRows(
            song,
            options,
            contentWidthPt = columnWidth,
            tablatures = tablatures,
            // La música se maqueta al ancho de la **banda**, no al de la columna.
            tablatureWidthPt = options.contentWidthPt
        )
            // El título lo dibuja la banda (a ancho completo), no como fila de columna.
            .filterNot { it.kind == PdfRowKind.TITLE }
        // Las filas anteriores a la primera sección (datos del canto, notas, ritmo global) van a
        // **ancho completo** encima de las columnas, para no invadir la columna derecha.
        // (D-99) La banda superior a ancho completo es **metadatos + tablatura**. Corta en la
        // primera fila que ya no sea ni lo uno ni lo otro (la primera sección), para que el texto
        // empiece ya en columnas y no se lleve media banda.
        val firstSection = allRows.indexOfFirst { it.marginLabel != null }
        // (D-99) La banda solo lleva la tablatura si va ARRIBA. Al final, la banda es solo la
        // cabecera de datos (y si no hay datos, no hay banda).
        val headerRows = if (firstSection > 0) allRows.take(firstSection) else emptyList()
        val columnRows = allRows.drop(headerRows.size)
        val headerBlockHeight = headerRows.sumOf { (it.leadingGapPt + it.heightPt).toDouble() }.toFloat()
        // (D-96) El dibujante empieza la cabecera en `headerHeight + 12` y las columnas en
        // `headerY + 4`, así que la capacidad real de la primera columna es la del papel menos el
        // bloque de cabecera **y esos 16 pt de separación**. Antes no se descontaban y la última
        // fila de la página se imprimía por debajo del pie.
        // (D-99) 22 pt: los 12 de aire bajo la banda más los 4 de separación a las columnas, con
        // margen para que la última fila de la columna derecha no llegue al pie.
        val headerReservePt = 22f
        val firstCapacity = options.contentHeightPt - headerBlockHeight - headerReservePt
        // (D-99) Si la **banda** (metadatos + tablatura) no cabe en una hoja, el reparto a dos
        // columnas la desbordaría por el pie. En ese caso se vuelve a una columna, que pagina la
        // tablatura sistema a sistema como siempre.
        // La banda tiene que caber en la **página** (no en lo que sobra para las columnas, que es
        // otra cosa): si no, se sale por el pie.
        // (D-99) La banda **no se puede partir**. Si la tablatura trae un salto de página explícito
        // (`Ctrl`+`Return` de MuseScore) o no cabe en una hoja, no puede ir en la banda: se rehace
        // el documento a una columna, donde el paginador sí respeta los saltos sistema a sistema.
        val tabInBand = headerRows.filter { it.kind == PdfRowKind.TABLATURE }
        val bandTabBreaks = tabInBand.drop(1).any { it.pageBreakBefore }
        if (tabInBand.isNotEmpty() && (bandTabBreaks || headerBlockHeight > options.contentHeightPt)) {
            return SongPdfDocument(
                title,
                singleColumnPages(singleGroups, title, options),
                options.copy(columns = 1)
            )
        }
        val groups = twoColumnPaginate(
            columnRows, options,
            firstCapacity = firstCapacity
        )
        // (D-99) **Sin retroceder de página.** Si el reparto a dos columnas no ahorra ninguna hoja
        // respecto a una columna, no merece la pena el papel estrecho y se rehace a una columna
        // con las filas de ancho completo. Nunca se mezclan ambos repartos en el mismo documento:
        // así una página posterior jamás queda antes que una de una maqueta distinta.
        if (groups.size >= singleGroups.size) {
            return SongPdfDocument(
                title,
                singleColumnPages(singleGroups, title, options),
                options.copy(columns = 1)
            )
        }
        return SongPdfDocument(
            title,
            twoColumnPages(groups, title, headerRows, options, firstCapacity = firstCapacity),
            options.copy(columns = 2)
        )
    }

    /**
     * Página a dos columnas de un documento: [split] es el índice de fila donde empieza la segunda
     * columna.
     */
    private data class TwoColumnPage(val rows: List<PdfRow>, val split: Int)

    /**
     * Reparte las filas en páginas de dos columnas. Se respeta la **integridad de las secciones**
     * (una sección no se parte entre columnas si cabe entera), se **balancean** ambas columnas y la
     * primera página deja sitio para la cabecera a ancho completo ([firstCapacity]).
     */
    private fun twoColumnPaginate(
        rows: List<PdfRow>,
        options: SongPdfOptions,
        firstCapacity: Float
    ): List<TwoColumnPage> {
        val h = options.contentHeightPt
        val pages = mutableListOf<TwoColumnPage>()
        var index = 0
        var firstPage = true
        while (index < rows.size) {
            val start = index
            val leftCap = if (firstPage) firstCapacity else h

            // Un **salto de página** explícito (`Ctrl`+`Return` de MuseScore) cierra la **hoja**
            // entera, no solo la columna: la página no puede pasar de él.
            val pageLimit = firstPageBreakBetween(rows, start, rows.size) ?: rows.size

            // (D-100) **Aprovechamiento de la hoja.** Antes el corte de columna era un llenado
            // voraz y después se movía **una** sección, así que la derecha quedaba medio vacía y
            // a veces sobraba una hoja entera. Ahora se prueban **todos los cortes válidos** —los
            // que caen en un inicio de sección, para no partir una sección por la mitad, más el
            // llenado máximo— y se elige el que **llena más la hoja**; a igualdad de filas
            // consumidas, el que **equilibra** las dos columnas.
            //
            // Nunca se retrocede: el reparto es un barrido creciente de `index`, y cada página
            // consume al menos una fila más que la anterior.
            val greedyEnd = greedyColumnEnd(rows, start, leftCap).coerceAtMost(pageLimit)
            val candidates = buildList {
                add(greedyEnd)
                addAll(sectionStartsBetween(rows, start, greedyEnd))
            }.filter { it > start }.distinct().sorted()

            var split = greedyEnd
            var rightEnd = greedyColumnEnd(rows, split, leftCap).coerceAtMost(pageLimit)
            // (D-100) Si **todo** el resto cabe en la columna izquierda, la página va a **una
            // columna**: no hay nada que poner en la derecha y el corte `split` dejaría una
            // segunda columna vacía. Se marca con `rightEnd == rows.size` y `split` al final.
            var bestConsumed = -1
            var bestImbalance = Float.MAX_VALUE
            candidates.forEach { candidate ->
                val candidateRight = greedyColumnEnd(rows, candidate, leftCap).coerceAtMost(pageLimit)
                // (D-100) La **segunda columna tiene que llevar algo**: si el candidato la deja
                // vacía, la página tendría una sola columna y el reparto «a dos» mentiría (el
                // dibujante incluso la pintaría sin corte). Por eso se exige `>` y no `>=`.
                if (candidateRight <= candidate) return@forEach
                val leftHeight = heightOf(rows, start, candidate)
                val rightHeight = heightOf(rows, candidate, candidateRight)
                if (leftHeight > leftCap + 0.5f || rightHeight > leftCap + 0.5f) return@forEach
                val imbalance = kotlin.math.abs(leftHeight - rightHeight)
                if (candidateRight > bestConsumed ||
                    (candidateRight == bestConsumed && imbalance < bestImbalance)
                ) {
                    split = candidate
                    rightEnd = candidateRight
                    bestConsumed = candidateRight
                    bestImbalance = imbalance
                }
            }

            // Si la izquierda se lo comió todo, la página se dibuja a una sola columna.
            val finalSplit = if (rightEnd == split) rightEnd - start else split - start
            pages += TwoColumnPage(rows.subList(start, rightEnd), finalSplit)
            index = rightEnd
            firstPage = false
        }
        if (pages.isEmpty()) pages += TwoColumnPage(emptyList(), 0)
        return pages
    }

    /** Índices de inicio de sección (fila con etiqueta) dentro de `[from, to)`, excluyendo `from`. */
    private fun sectionStartsBetween(rows: List<PdfRow>, from: Int, to: Int): List<Int> =
        (from + 1 until to).filter { rows[it].marginLabel != null }

    /**
     * Índice de la primera fila con **salto de página** dentro de `[from, to)`, excluyendo `from`
     * (que ya abre columna). `null` si no hay ninguna.
     */
    private fun firstPageBreakBetween(rows: List<PdfRow>, from: Int, to: Int): Int? =
        (from + 1 until to).firstOrNull { rows[it].pageBreakBefore }

    /**
     * Fin de columna al llenar desde [from] sin superar [cap] (al menos una fila).
     *
     * Un **salto de página** explícito (`pageBreakBefore`) corta la columna aunque quede sitio: es el
     * `Ctrl`+`Return` de MuseScore, que obliga a empezar hoja.
     */
    private fun greedyColumnEnd(rows: List<PdfRow>, from: Int, cap: Float): Int {
        var used = 0f
        var i = from
        while (i < rows.size) {
            if (i > from && rows[i].pageBreakBefore) break
            val need = rows[i].leadingGapPt + rows[i].heightPt
            if (used + need > cap && i > from) break
            used += need
            i++
        }
        return i
    }

    /**
     * (D-98) Alto del **bloque de sección** que empieza en `from`: desde su fila rotulada hasta
     * la siguiente fila con rótulo (o el final). Es lo que hay que dejar caber entero para que el
     * rótulo rotado no se salga por el pie.
     */
    private fun sectionBlockHeight(rows: List<PdfRow>, from: Int): Float {
        var i = from + 1
        while (i < rows.size && rows[i].marginLabel == null) i++
        return heightOf(rows, from, i)
    }

    /** Altura total de las filas en `[from, to)`. */
    private fun heightOf(rows: List<PdfRow>, from: Int, to: Int): Float {
        var sum = 0f
        for (i in from until to) sum += rows[i].leadingGapPt + rows[i].heightPt
        return sum
    }

    /** Construye las páginas (con sus bandas) a partir de los grupos de dos columnas. */
    private fun twoColumnPages(
        pages: List<TwoColumnPage>,
        title: String,
        headerRows: List<PdfRow>,
        options: SongPdfOptions,
        firstCapacity: Float
    ): List<PdfPage> {
        val total = pages.size
        return pages.mapIndexed { index, page ->
            // (D-17) Cada **columna** se justifica por separado: la izquierda de la primera página
            // tiene menos alto porque encima van las filas de cabecera a ancho completo.
            val split = page.split.coerceIn(0, page.rows.size)
            val leftCapacity = if (index == 0) firstCapacity else options.contentHeightPt
            PdfPage(
                rows = justifyTablatureSystems(page.rows.subList(0, split), options, leftCapacity) +
                    justifyTablatureSystems(page.rows.subList(split, page.rows.size), options, options.contentHeightPt),
                index = index,
                totalPages = total,
                // Todas las páginas de dos columnas llevan el título en la banda (a ancho completo).
                header = title,
                isTitlePage = index == 0,
                columns = 2,
                secondColumnStartIndex = page.split,
                // La cabecera a ancho completo solo va en la primera página.
                headerRows = if (index == 0) headerRows else emptyList()
            )
        }
    }

    /** Páginas de una columna a partir de los grupos ya paginados. */
    private fun singleColumnPages(
        groups: List<List<PdfRow>>,
        title: String,
        options: SongPdfOptions
    ): List<PdfPage> {
        val total = groups.size
        return groups.mapIndexed { index, rows ->
            PdfPage(
                rows = justifyTablatureSystems(rows, options, options.contentHeightPt),
                index = index,
                totalPages = total,
                // La banda superior lleva el título en la primera página (a tamaño de título) y en
                // las siguientes, como encabezado vivo.
                header = title,
                isTitlePage = index == 0
            )
        }
    }

    // ── Construcción de filas ──────────────────────────────────────────────────

    /**
     * Filas de una canción (título, metadatos, notas y secciones), sin paginar.
     * Expuesto para que la maquetación del repertorio pueda encadenar varios cantos.
     */
    fun buildRows(
        song: Song,
        options: SongPdfOptions,
        contentWidthPt: Float = options.contentWidthPt,
        /** Tablaturas del canto; solo se imprimen si el modo las incluye (`pdfShowsTablature`). */
        tablatures: List<com.grafitto.laudate.domain.model.tablature.Tablature> = emptyList(),
        /**
         * (D-53) Franja que la tablatura **recupera** a su izquierda para ocupar el ancho de página.
         * Es [SongPdfOptions.marginLabelWidthPt] a una columna y `0` a dos (ahí la tablatura no puede
         * salirse de su columna).
         */
        tablatureInsetPt: Float = 0f,
        /**
         * (D-99) Ancho con el que se maqueta la **tablatura**. A dos columnas el texto va estrecho,
         * pero la música se dibuja en la **banda superior a ancho completo**; sin este parámetro se
         * repartiría en media página. Por defecto, el mismo ancho que el texto.
         */
        tablatureWidthPt: Float = contentWidthPt
    ): List<PdfRow> {
        val rows = mutableListOf<PdfRow>()

        rows += PdfRow(
            kind = PdfRowKind.TITLE,
            text = song.title.ifBlank { "Sin título" },
            heightPt = options.titleFontSizePt * 1.5f
        )

        // Datos del canto + rasgueo global, todo en **una sola línea** (se recorta si no cabe).
        // (D-98) Si la tablatura va a rotular el tempo en su primer compás (como MuseScore), el
        // metadato NO lo repite: salía «Negra = 112 (Moderato)» arriba y «Negra = 112 · Moderato»
        // sobre la partitura.
        val gridLabelsTempo = options.pdfShowsTablature && tablatures.any { tab ->
            tab.measures.any { it.tempo != null || it.tempoText != null }
        }
        val metaSong = if (gridLabelsTempo) song.copy(tempoBpm = null) else song
        buildMetaAndRhythmLine(metaSong, options.notationSystem)?.let { line ->
            rows += PdfRow(
                kind = PdfRowKind.METADATA,
                text = fitToWidth(line, options.contentWidthPt, options.metaFontSizePt),
                heightPt = options.metaFontSizePt * 1.5f,
                leadingGapPt = 1f
            )
        }

        song.notes?.takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() }?.forEach { note ->
            rows += PdfRow(
                kind = PdfRowKind.COMMENT,
                text = note.trim(),
                heightPt = options.metaFontSizePt * LINE_HEIGHT_FACTOR,
                leadingGapPt = 1f
            )
        }

        // (D-97/D-99) La tablatura va **entera en un solo bloque**, nunca intercalada entre
        // secciones: ni al final de cada estrofa (lo que partía la música con el texto) ni
        // repartida. **Dónde** se coloca lo decide [SongPdfOptions.tablaturePlacement]: arriba,
        // para leer la música antes de cantar, o al final, como apéndice para el atril.
        val tablatureBlock: List<PdfRow> = if (options.pdfShowsTablature) {
            buildList {
                tablatures.forEach { tab ->
                    addAll(tablatureRows(tab, options, tablatureWidthPt, tablatureInsetPt))
                }
                // (D-98) Aire tras la música: la fila de ritmo (plicas y corchetes) baja por debajo
                // de la rejilla y sin este respiro la primera línea de acordes la rozaba.
                if (isNotEmpty() && last().kind == PdfRowKind.TABLATURE) {
                    add(PdfRow(kind = PdfRowKind.SPACER, heightPt = TABLATURE_TEXT_GAP_PT))
                }
            }
        } else {
            emptyList()
        }
        if (options.tablaturePlacement == TablaturePlacement.TOP) {
            rows += tablatureBlock
        }

        // La primera sección del canto no lleva aire previo: no hay nada antes que separar.
        var firstSectionPlaced = false
        song.sections.forEach { section ->
            // El estribillo (y el coro) se imprime centrado, en negrita y algo mayor: es la parte
            // que canta toda la asamblea y conviene localizar de un vistazo en el atril.
            val isChorus = section.sectionType == SectionType.CHORUS ||
                section.sectionType == SectionType.ACCLAMATION
            val bodyFontSize = if (isChorus) {
                options.lyricsFontSizePt * options.chorusScale
            } else {
                options.lyricsFontSizePt
            }
            val centered = isChorus || options.centerLyrics

            // Filas de la sección (se recogen aparte para etiquetar y reglar la sección entera).
            val sectionRows = mutableListOf<PdfRow>()

            // Cambio dinámico de rasgueo declarado con `{x_rhythm: Nombre}` para ESTA sección.
            section.rhythmPattern?.let { pattern ->
                rhythmGridOf(pattern, options)?.let { sectionRows += it }
            }

            section.lines.forEach { line ->
                // En «Solo tablatura» no se imprime texto (ni letra, ni acordes, ni comentarios):
                // `pdfShowsLyrics`/`pdfShowsChords` son ambos falsos. Antes se ignoraba
                // `pdfShowsLyrics` (definido pero sin usar) y el PDF salía con la letra igualmente.
                val showText = options.pdfShowsLyrics || options.pdfShowsChords
                if (!showText) return@forEach
                when (line.type) {
                    LineType.COMMENT, LineType.HEADER -> {
                        val text = line.comment ?: line.lyrics
                        if (text.isNotBlank()) {
                            val prefix = line.voice?.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
                            sectionRows += PdfRow(
                                kind = PdfRowKind.COMMENT,
                                text = prefix + text,
                                heightPt = options.metaFontSizePt * LINE_HEIGHT_FACTOR,
                                leadingGapPt = 1f,
                                centered = centered,
                                fontSizePt = options.metaFontSizePt
                            )
                        }
                    }

                    LineType.LYRICS, LineType.CHORD_ONLY -> {
                        val chordOnly = line.type == LineType.CHORD_ONLY
                        if (line.chords.isEmpty() || !options.pdfShowsChords) {
                            if (line.lyrics.isBlank()) {
                                // Una fila solo-acordes sin letra no debe reservar un renglón vacío
                                // cuando los acordes están desactivados (no hay nada que imprimir).
                                if (!chordOnly) {
                                    sectionRows += PdfRow(kind = PdfRowKind.SPACER, heightPt = bodyFontSize * 0.5f)
                                }
                            } else {
                                wrapText(line.lyrics, contentWidthPt, bodyFontSize, options.textScaleX)
                                    .forEach { chunk ->
                                        sectionRows += PdfRow(
                                            kind = PdfRowKind.LYRICS,
                                            text = chunk,
                                            heightPt = bodyFontSize * LINE_HEIGHT_FACTOR,
                                            centered = centered,
                                            emphasis = isChorus,
                                            fontSizePt = bodyFontSize
                                        )
                                    }
                            }
                        } else if (chordOnly) {
                            // Fila de solo acordes (intro/riff/outro): se anclan al margen izquierdo
                            // y se separan por el **ancho real del acorde** (no por un espacio de
                            // letra), para que no se solapen.
                            sectionRows += buildChordOnlyRows(line.chords, options)
                        } else {
                            sectionRows += buildChordRows(
                                lyrics = line.lyrics,
                                chords = line.chords,
                                options = options,
                                fontSizePt = bodyFontSize,
                                centered = centered,
                                emphasis = isChorus,
                                contentWidthPt = contentWidthPt
                            )
                        }
                    }
                }
            }


            // Etiqueta de sección en el margen (rotada) + regla vertical (solo para el estribillo).
            // La altura total se adjunta a la fila rotulada para centrarla: es la **caja
            // delimitadora real** del contenido, SIN el aire externo entre secciones.
            val label = sectionDisplayTitle(section)
            val repeatMark = section.repeatCount?.let { "×$it" } ?: if (section.isRepeated) "bis" else null
            val sectionHeight = sectionRows.sumOf { (it.leadingGapPt + it.heightPt).toDouble() }.toFloat()
            // La etiqueta va rotada (su longitud ocupa altura): en un bloque corto, un rótulo largo
            // lo dominaría o desbordaría. Por eso en secciones de pocas líneas se usa la abreviatura.
            val shortLabel = sectionShortTitle(section)
            val chosenLabel = if (sectionHeight < SHORT_SECTION_HEIGHT_PT) shortLabel else label
            // Aire entre secciones: se añade como espacio previo a la PRIMERA fila de contenido (la
            // que lleva el rótulo y, si procede, el «bis»), así la separación depende de las
            // dimensiones reales del bloque y no de un salto fijo. Se aplica tras el primer bloque
            // para no dejar un hueco huérfano al principio del canto.
            val startGap = if (firstSectionPlaced) options.sectionGapPt else 0f
            sectionRows.forEachIndexed { index, row ->
                rows += row.copy(
                    leadingGapPt = if (index == 0) row.leadingGapPt + startGap else row.leadingGapPt,
                    marginLabel = if (index == 0) chosenLabel else null,
                    // La línea vertical solo acompaña al estribillo (y aclamaciones): así destaca la
                    // parte que canta la asamblea, sin recargar las estrofas.
                    sectionRule = isChorus,
                    sectionHeightPt = sectionHeight,
                    repeatMark = if (index == 0) repeatMark else null
                )
            }
            if (sectionRows.isNotEmpty()) firstSectionPlaced = true
        }

        // (D-99) Apéndice: la tablatura al final, después de toda la letra. Abre página propia
        // para que no se mezcle con la última estrofa.
        if (options.tablaturePlacement == TablaturePlacement.END && tablatureBlock.isNotEmpty()) {
            rows += tablatureBlock.mapIndexed { index, row ->
                if (index == 0) row.copy(pageBreakBefore = true, leadingGapPt = 0f) else row
            }
        }

        return rows
    }

    /**
     * Convierte una tablatura en una fila de PDF: maqueta la rejilla al **ancho de columna** (en
     * puntos), de modo que encaje sin deformarse. La unidad mínima es el compás, que puede caer a
     * la fila siguiente; cada sistema de la rejilla se imprime como una sub-fila.
     */
    private fun tablatureRows(
        tablature: com.grafitto.laudate.domain.model.tablature.Tablature,
        options: SongPdfOptions,
        contentWidthPt: Float,
        tablatureInsetPt: Float = 0f
    ): List<PdfRow> {
        // (C.3) La tablatura se maqueta con la **escala de notación** elegida: la unidad de la
        // rejilla sale de ella, así que el alto de cada sistema (y por tanto cuántos caben por
        // página) se escala con la notación. La unidad mínima es el compás.
        val scale = options.tablatureScale
        val base = com.grafitto.laudate.domain.tablature.TabLayoutSettings(
            cellWidth = scale.cellWidthPt,
            stringSpacing = scale.stringSpacingPt,
            labelWidth = scale.headerWidthPt * 0.55f,
            // (D-53) El **margen de página ya lo aporta el paginador** del PDF (`marginPt`): sumarlo
            // otra vez aquí metía 3,24 pt de más a la izquierda de cada sistema.
            padding = 0f,
            systemGap = scale.systemGapPt,
            staff = options.tablatureStaff
        )
        // (D-53) La tablatura se maqueta al **ancho de página** (columna + la franja que recupera a
        // su izquierda), no al de la columna de texto: es lo que hace MuseScore y lo que hace que el
        // reparto en líneas coincida con el suyo.
        //
        // (D-85) Pero se **dibuja alineada a la izquierda con el texto** y con el **ancho de la
        // columna**: la franja de etiquetas que se recuperaba a la izquierda desalineaba la
        // partitura 18 pt respecto a la letra, que es lo que el usuario veía como que «no tiene
        // alineación completa a la izquierda». MuseScore imprime la tablatura en el mismo margen
        // que el resto del contenido.
        //
        // (D-96) El ancho de maquetación es **exactamente** el de la columna: se dibuja desde
        // `contentX` (que ya está a la derecha de la franja de etiquetas) y sin desplazamiento
        // hacia atrás, así que sumarle el inset lo sacaba 18 pt por el margen derecho.
        val systems = com.grafitto.laudate.domain.tablature.TablatureSystemsLayout.layout(
            tablature, maxWidth = contentWidthPt, settings = base
        )
        // Cada **sistema** es una **fila** del PDF, de modo que el paginador de filas NUNCA puede
        // partir un sistema por la mitad ni separar un compás de su barra (el sistema viaja entero).
        // (D-15) El aire entre sistemas es el **mínimo de MuseScore**
        // (`styleDef(minSystemDistance, 8.5_sp)`, `styledef.cpp:58`), que ya viene en
        // `scale.systemGapPt`. Antes la fila sumaba `8f` de alto y `6f` de aire — **2,8 espacios** con
        // un espacio de 5 pt —, así que en el PDF los sistemas salían mucho más pegados que en
        // MuseScore y que en el propio editor.
        return systems.mapIndexed { index, system ->
            PdfRow(
                kind = PdfRowKind.TABLATURE,
                // (D-98) El alto reservado es el del **dibujo** más el desplazamiento con el que se
                // pinta (`top + 4` en los dos exportadores). Antes se reservaba solo `system.height`
                // y la fila crecía 4 pt por abajo: la cola de las plicas y los corchetes caía
                // sobre los acordes de la primera línea de letra, que empiezan justo debajo.
                heightPt = system.height + TABLATURE_TOP_INSET_PT,
                // El aire va **delante** de cada sistema salvo el primero: así el paginador lo cuenta
                // como separación y la justificación vertical (D-17) puede **estirarlo** sin tocar el
                // alto de la rejilla.
                // Aire antes del sistema (`systemGap` de la escala). El último sistema lleva
                // además 8 pt extra para despegarlo de la primera línea de letra: la fila de ritmo
                // (plicas y corchetes) termina muy cerca del borde inferior de la rejilla.
                leadingGapPt = if (index == 0) 0f else scale.systemGapPt,
                tablature = system.drawing,
                // **Salto de página** (`Ctrl`+`Return`): el sistema abre hoja. El primero no, porque
                // ya empieza página.
                pageBreakBefore = index > 0 && system.startsPage,
                // (D-85) **Sin desplazamiento**: la tablatura se alinea con el texto. El inset se
                // usa solo para calcular el ancho (ya aplicado arriba en `maxWidth`).
                tablatureInsetPt = 0f,
                // (D-57) La distancia entre sistemas se mide **de pauta a pauta**, como MuseScore.
                tablatureGridTopPt = system.drawing.gridTop,
                tablatureGridBottomPt = system.drawing.gridBottom
            )
        }
    }

    /**
     * Divide una línea con acordes en filas visuales, repartiendo cada acorde en la visual que
     * contiene su carácter. Así los acordes **no se desalinean** cuando la letra ocupa más de
     * un renglón en el PDF.
     */
    private fun buildChordRows(
        lyrics: String,
        chords: List<com.grafitto.laudate.domain.model.song.ChordAnchor>,
        options: SongPdfOptions,
        fontSizePt: Float = options.lyricsFontSizePt,
        centered: Boolean = options.centerLyrics,
        emphasis: Boolean = false,
        contentWidthPt: Float = options.contentWidthPt
    ): List<PdfRow> {
        val maxChars = maxCharsForWidth(contentWidthPt, fontSizePt, options.textScaleX)
        val ranges = visualLineRanges(lyrics, maxChars)
        val sorted = chords.sortedBy { it.characterIndex }

        return ranges.map { range ->
            val marks = sorted.mapNotNull { anchor ->
                val idx = anchor.characterIndex
                val inRange = idx in range.first..range.last ||
                    (idx >= lyrics.length && range.last == ranges.last().last)
                if (!inRange) return@mapNotNull null
                val text = chordText(anchor, options.notationSystem)
                PdfChordMark(
                    text = text,
                    characterIndex = (idx - range.first).coerceAtLeast(0),
                    widthPt = approxChordWidthPt(text, options.chordsFontSizePt, options.textScaleX)
                )
            }
            // En una fila solo-acordes la letra son los espacios entre corchetes: NO se recortan,
            // porque son los que separan visualmente los acordes (si se recortan, todos caen en
            // la misma x y se superponen).
            val rawChunk = lyrics.substring(range.first, range.last)
            val chunkText = if (rawChunk.isBlank()) rawChunk else rawChunk.trim()
            val hasChords = marks.isNotEmpty()
            if (hasChords) {
                PdfRow(
                    kind = PdfRowKind.CHORDS_OVER_LYRICS,
                    text = chunkText.trimEnd(),
                    chords = marks,
                    heightPt = options.chordsFontSizePt * 1.2f + fontSizePt * LINE_HEIGHT_FACTOR,
                    leadingGapPt = 2f,
                    centered = centered,
                    emphasis = emphasis,
                    fontSizePt = fontSizePt
                )
            } else {
                // Continuación de la línea visual: NO lleva acordes, así que se emite como letra
                // normal (alto y baseline de letra). Antes se emitía como fila de acordes vacía y
                // su texto se salía del renglón y chocaba con lo de abajo.
                PdfRow(
                    kind = PdfRowKind.LYRICS,
                    text = chunkText,
                    heightPt = fontSizePt * LINE_HEIGHT_FACTOR,
                    leadingGapPt = 0f,
                    centered = centered,
                    emphasis = emphasis,
                    fontSizePt = fontSizePt
                )
            }
        }
    }

    /**
     * Fila de **solo acordes** (intro/riff/outro): acordes alineados horizontalmente, separados
     * por su ancho real (más una pequeña holgura), sin letra debajo.
     */
    private fun buildChordOnlyRows(
        chords: List<com.grafitto.laudate.domain.model.song.ChordAnchor>,
        options: SongPdfOptions
    ): List<PdfRow> {
        if (chords.isEmpty()) return emptyList()
        val sorted = chords.sortedBy { it.characterIndex }
        var cursor = 0f
        val marks = sorted.map { anchor ->
            val text = chordText(anchor, options.notationSystem)
            val width = approxChordWidthPt(text, options.chordsFontSizePt, options.textScaleX)
            val mark = PdfChordMark(
                text = text,
                characterIndex = 0,
                widthPt = width,
                xOffsetPt = cursor
            )
            cursor += width + options.minChordGapPt
            mark
        }
        return listOf(
            PdfRow(
                kind = PdfRowKind.CHORD_ONLY,
                text = "",
                chords = marks,
                heightPt = options.chordsFontSizePt * 1.6f,
                leadingGapPt = 2f,
                centered = false,
                fontSizePt = options.chordsFontSizePt
            )
        )
    }

    /**
     * Texto del acorde según la nomenclatura del PDF. Se re-renderiza desde el `DomainChord`
     * (raíz, calidad y bajo estructurados), igual que el visor, para respetar la preferencia
     * global. Solo si el acorde no tiene datos (texto vacío) se recurre al original.
     */
    fun chordText(anchor: ChordAnchor, system: NotationSystem): String =
        anchor.chord.toFormattedString(system).ifBlank { anchor.rawChordText }

    /**
     * Reparte los acordes de una línea para que **ninguna caja se solape** con la siguiente,
     * respetando un hueco mínimo ([minGapPt]) y repartiendo la separación de forma **simétrica**.
     *
     * El llamante mide las posiciones naturales (`xOffsetPt`, según el carácter que ancla cada
     * acorde) y los anchos reales (`widthPt`) con las métricas de su fuente. Esta función solo
     * decide los huecos:
     *  1. **Sin solapes**: cada acorde empieza, como mínimo, `widthPt + minGapPt` después del
     *     anterior (empuje hacia la derecha).
     *  2. **Reparto simétrico**: si hubo que empujar, todo el grupo recula la **mitad** del mayor
     *     desplazamiento, de modo que unos acordes quedan algo a la derecha y otros algo a la
     *     izquierda, y el conjunto no se desborda. El primer acorde nunca se lleva antes del origen.
     */
    fun distributeChordGaps(marks: List<PdfChordMark>, minGapPt: Float): List<PdfChordMark> {
        if (marks.size < 2) return marks
        val n = marks.size
        val y = FloatArray(n)
        y[0] = marks[0].xOffsetPt
        for (i in 1 until n) {
            y[i] = maxOf(marks[i].xOffsetPt, y[i - 1] + marks[i - 1].widthPt + minGapPt)
        }
        val maxShift = (0 until n).maxOf { y[it] - marks[it].xOffsetPt }
        if (maxShift > 0f) {
            val back = minOf(maxShift / 2f, (0 until n).minOf { y[it] })
            for (i in 0 until n) y[i] -= back
        }
        return marks.mapIndexed { i, mark -> mark.copy(xOffsetPt = y[i]) }
    }

    /**
     * Datos del canto **y** rasgueo global, todo en **una sola línea** para ahorrar renglones:
     * `Autor · Tono · Compás · ♩ = ... · Rasgueo: Nombre ↓ ↑ × ·`.
     */
    private fun buildMetaAndRhythmLine(song: Song, system: NotationSystem): String? {
        val parts = buildList {
            song.number?.takeIf { it.isNotBlank() }?.let { add("Nº $it") }
            song.author?.takeIf { it.isNotBlank() }?.let { add(it) }
            // La tonalidad se imprime en la nomenclatura elegida (igual que el visor).
            add("Tono: ${NotationFormat.key(song.originalKey, system)}")
            if (song.capo > 0) add("Capo: traste ${song.capo}")
            song.rhythm?.takeIf { it.isNotBlank() }?.let { add("Compás: $it") }
            song.tempoBpm?.let { bpm ->
                val marking = song.tempoMarking?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                // «Negra = N» en vez del glifo ♩: el PDF solo embebe Helvetica y Times (Standard 14),
                // así que cualquier carácter fuera de Latin-1 se imprime como `?` y la línea salía
                // como «? = 112». La negra se escribe con todas las letras: se lee igual y no depende
                // de que haya una fuente musical cargada.
                add("Negra = $bpm$marking")
            }
            song.rhythmPattern?.takeIf { it.events.isNotEmpty() }?.let { pattern ->
                add("Rasgueo: ${pattern.name} ${rhythmGrid(pattern)}")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }

    /** Rejilla de flechas de un patrón, agrupada por pulso con separadores `|`. */
    private fun rhythmGrid(pattern: RhythmPattern): String =
        pattern.metricGroups().joinToString(" | ") { group ->
            group.joinToString(" ") { it.effectiveDirection.arrow }
        }

    /**
     * Fila de rejilla de rasgueo (nombre + flechas) para un patrón de sección. `null` si no hay.
     */
    private fun rhythmGridOf(pattern: RhythmPattern?, options: SongPdfOptions): PdfRow? {
        if (pattern == null || pattern.events.isEmpty()) return null
        return PdfRow(
            kind = PdfRowKind.RHYTHM,
            text = "Rasgueo: ${pattern.name} ${rhythmGrid(pattern)}",
            heightPt = options.metaFontSizePt * LINE_HEIGHT_FACTOR,
            leadingGapPt = 1f,
            fontSizePt = options.metaFontSizePt
        )
    }

    /** Recorta un texto con elipsis si excede el ancho dado (métricas aproximadas). */
    fun fitToWidth(text: String, widthPt: Float, fontSizePt: Float): String {
        val maxChars = maxCharsForWidth(widthPt, fontSizePt)
        return if (text.length <= maxChars || maxChars < 2) text else text.take(maxChars - 1).trimEnd() + "…"
    }

    /** Título legible de la sección, con su etiqueta o voz si las tiene. */
    fun sectionDisplayTitle(section: SongSection): String {
        val base = section.label?.takeIf { it.isNotBlank() }
            ?: when (section.sectionType) {
                SectionType.INTRO -> "Intro"
                SectionType.VERSE -> "Estrofa"
                SectionType.PRE_CHORUS -> "Pre-estribillo"
                SectionType.CHORUS -> "Estribillo"
                SectionType.BRIDGE -> "Puente"
                SectionType.INTERLUDE -> "Interludio"
                SectionType.OUTRO -> "Final"
                SectionType.ACCLAMATION -> "Aclamación"
            }
        val voice = section.voice?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        // La repetición (bis) NO se añade al rótulo: se dibuja aparte, pequeña, junto a la etiqueta.
        return (base + voice).uppercase()
    }

    /**
     * Rótulo **abreviado** de la sección, para cajas pequeñas (secciones de pocas líneas): la
     * etiqueta va **rotada**, así que su longitud ocupa altura; en un bloque corto, un rótulo largo
     * lo dominaría o desbordaría. Reconoce los tipos habituales conservando el número y la voz
     * (`ESTROFA 1` → `EST. 1`, `ESTRIBILLO` → `CORO`). Si no reconoce la etiqueta, la devuelve igual.
     */
    fun sectionShortTitle(section: SongSection): String {
        val full = sectionDisplayTitle(section)
        // Se separa la voz (« · SOPRANO») para abreviar solo el rótulo, no el nombre de la voz.
        val sep = full.indexOf(" · ")
        val base = if (sep >= 0) full.substring(0, sep) else full
        val voice = if (sep >= 0) full.substring(sep) else ""
        return abbreviateSectionLabel(base) + voice
    }

    /** Abreviatura del rótulo base (ya en mayúsculas) para los tipos de sección reconocidos. */
    private fun abbreviateSectionLabel(base: String): String {
        val b = base.trim()
        return when {
            b == "ESTROFA" || b.startsWith("ESTROFA ") -> {
                val rest = b.removePrefix("ESTROFA").trim()
                // Solo se abrevia si lo que sigue es el número («ESTROFA 2» → «EST. 2»); una
                // etiqueta descriptiva («ESTROFA DEL PUEBLO») se conserva entera.
                if (rest.isEmpty() || rest.all { it.isDigit() }) {
                    if (rest.isEmpty()) "EST." else "EST. $rest"
                } else b
            }
            b == "ESTRIBILLO" || b.startsWith("ESTRIBILLO ") -> "CORO" + b.removePrefix("ESTRIBILLO")
            b == "PRE-ESTRIBILLO" -> "PRE-ESTR."
            b == "PUENTE" || b == "BRIDGE" -> "PTE."
            b == "FINAL" || b == "OUTRO" -> "FIN"
            b == "INTERLUDIO" -> "INTERL."
            b == "ACLAMACIÓN" || b == "ACLAMACION" -> "ACLAM."
            else -> b
        }
    }

    /** Número máximo de caracteres que caben en un ancho dado para un tamaño de fuente. */
    fun maxCharsForWidth(widthPt: Float, fontSizePt: Float, scaleX: Float = 1f): Int {
        val charWidth = (fontSizePt * CHAR_WIDTH_FACTOR * scaleX).coerceAtLeast(1f)
        return (widthPt / charWidth).toInt().coerceAtLeast(1)
    }

    /** Ancho aproximado de un texto, en puntos (ya comprimido por [scaleX]). */
    fun approxTextWidthPt(text: String, fontSizePt: Float, scaleX: Float = 1f): Float =
        text.length * fontSizePt * CHAR_WIDTH_FACTOR * scaleX

    /** Ancho aproximado de un **acorde** (en negrita), en puntos. */
    private fun approxChordWidthPt(text: String, fontSizePt: Float, scaleX: Float = 1f): Float =
        text.length * fontSizePt * CHORD_WIDTH_FACTOR * scaleX

    /**
     * Reparte un texto en líneas visuales que quepan en [maxChars], sin partir palabras
     * (salvo que una sola palabra exceda la línea).
     */
    fun wrapText(text: String, widthPt: Float, fontSizePt: Float, scaleX: Float = 1f): List<String> {
        val maxChars = maxCharsForWidth(widthPt, fontSizePt, scaleX)
        return text.split('\n').flatMap { paragraph ->
            val wrapped = wrapParagraph(paragraph, maxChars)
            if (wrapped.isEmpty()) listOf("") else wrapped
        }
    }

    private fun wrapParagraph(paragraph: String, maxChars: Int): List<String> {
        if (paragraph.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var current = StringBuilder()

        paragraph.split(' ').forEach { word ->
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxChars) {
                current.append(' ').append(word)
            } else {
                result += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    /**
     * Índices (inclusivos) de cada línea visual del texto, respetando límites de palabra.
     * Un acorde se asigna a la visual cuyo rango contiene su carácter.
     */
    private fun visualLineRanges(text: String, maxChars: Int): List<IntRange> {
        if (text.isEmpty()) return listOf(0..0)

        val ranges = mutableListOf<IntRange>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end - 1)
                if (lastSpace > start) end = lastSpace
            }
            ranges += start..end

            var next = end
            while (next < text.length && text[next] == ' ') next++
            if (next <= start) next = minOf(start + 1, text.length)
            start = next
        }
        return ranges
    }

    // ── Paginación ─────────────────────────────────────────────────────────────

    /**
     * Reparte filas en páginas sin exceder el alto útil y evitando encabezados huérfanos.
     * Devuelve los grupos de filas ya paginados.
     */
    fun paginateRows(rows: List<PdfRow>, options: SongPdfOptions): List<List<PdfRow>> {
        val usableHeight = options.contentHeightPt
        val pages = mutableListOf<MutableList<PdfRow>>()
        var current = mutableListOf<PdfRow>()
        var used = 0f

        rows.forEachIndexed { index, row ->
            val needed = row.leadingGapPt + row.heightPt
            // (D-98) Una **sección con rótulo** no se parte entre páginas si cabe entera en una:
            // el rótulo va rotado y centrado en el bloque completo, así que la fracción que caía al
            // pie lo pintaba fuera de la hoja («ESTROFA 2» cortada por abajo). Se abre página
            // nueva antes de empezar la sección.
            if (row.marginLabel != null && current.isNotEmpty()) {
                val blockHeight = sectionBlockHeight(rows, index)
                if (blockHeight <= usableHeight && used + blockHeight > usableHeight) {
                    pages += current
                    current = mutableListOf()
                    used = 0f
                }
            }
            val isSectionHeader = row.kind == PdfRowKind.SECTION_HEADER
            // Evita dejar un encabezado de sección huérfano al final de la página:
            // se exige que quepa también la primera fila de su contenido.
            val required = if (isSectionHeader) needed + lookAheadHeight(rows, index) else needed

            // **Salto de página** explícito (`Ctrl`+`Return`): cierra la hoja aunque quede sitio.
            val forced = row.pageBreakBefore && current.isNotEmpty()
            if ((used + required > usableHeight || forced) && current.isNotEmpty()) {
                pages += current
                current = mutableListOf()
                used = 0f
            }
            current += row
            used += needed
        }
        if (current.isNotEmpty()) pages += current
        if (pages.isEmpty()) pages.add(mutableListOf())
        return pages
    }

    private fun lookAheadHeight(rows: List<PdfRow>, index: Int): Float =
        rows.getOrNull(index + 1)?.let { it.leadingGapPt + it.heightPt } ?: 0f

    /**
     * **Justificación vertical** de los sistemas de tablatura de una columna, como
     * `PageLayout::justifySystems()` de MuseScore
     * (`src/engraving/rendering/score/pagelayout.cpp`).
     *
     * MuseScore coloca los sistemas a un **hueco de pauta a pauta** —del borde inferior de la última
     * cuerda de un sistema al borde superior de la primera del siguiente— acotado por
     * `styleDef(minSystemDistance, 8.5_sp)` (`styledef.cpp:58`) y
     * `styleDef(maxSystemDistance, 15.0_sp)` (`styledef.cpp:59`), y reparte el alto que sobra en la
     * página **a partes iguales** entre esos huecos.
     *
     * (D-56) Lo que se iguala es el hueco **de pauta a pauta**, no el de fila a fila. En el
     * `salmo2.pdf` los techos de los sistemas caen a 129,1 / 128,9 / 128,9 pt y los huecos entre
     * tintas son 34,8 / 76,1 / 76,1: el primero mide la mitad que los otros dos porque el primer
     * sistema es mucho más alto (lleva plicas y dinámica). Repartir el sobrante entre los huecos de
     * **fila** —lo que hacíamos— dejaba el primer sistema 35 pt más abajo de donde MuseScore lo
     * pone, porque su fila es 44 pt más alta que su pauta.
     *
     * Solo estira los huecos **entre sistemas consecutivos** ([PdfRowKind.TABLATURE] seguido de
     * [PdfRowKind.TABLATURE]): el interlineado de la letra y de los acordes ya viene medido por la
     * tipografía y no se toca.
     *
     * @param capacityPt alto útil de la columna; el sobrante se mide contra él, no contra la página.
     */
    private fun justifyTablatureSystems(
        rows: List<PdfRow>,
        options: SongPdfOptions,
        capacityPt: Float
    ): List<PdfRow> {
        if (rows.size < 2) return rows
        val gaps = (1 until rows.size).filter {
            rows[it].kind == PdfRowKind.TABLATURE && rows[it - 1].kind == PdfRowKind.TABLATURE
        }
        if (gaps.isEmpty()) return rows
        val staffSpace = options.tablatureScale.staffSpacePt
        val minGapPt = staffSpace * TabLayoutSettings.MIN_SYSTEM_DISTANCE_SPACES
        val maxGapPt = staffSpace * TabLayoutSettings.MAX_SYSTEM_DISTANCE_SPACES
        val used = rows.sumOf { (it.leadingGapPt + it.heightPt).toDouble() }.toFloat()
        val remaining = capacityPt - used
        if (remaining <= 0.5f) return rows

        // (D-57) MuseScore iguala el hueco **de pauta a pauta**, no el de caja a caja
        // (`PageLayout::distributeStaves`, `pagelayout.cpp:653`: `system->y() + sysStaff->bbox().y()`
        // menos el borde inferior de la pauta anterior). En el `salmo2.pdf` los huecos de pauta son
        // 18,38 / 18,34 / 18,34 espacios —uniformes— mientras los de caja son 9,2 / 15,2 / 15,2: el
        // primero mide casi la mitad porque el primer sistema cuelga 27 pt de dinámica por debajo de
        // su última cuerda. Igualar el hueco de caja dejaba el primer sistema 35 pt más abajo de
        // donde MuseScore lo pone.
        val current = gaps.map { i ->
            rows[i - 1].heightPt + rows[i].leadingGapPt +
                rows[i].tablatureGridTopPt - rows[i - 1].tablatureGridBottomPt
        }
        val uniform = (current.sumOf { it.toDouble() }.toFloat() + remaining) / gaps.size
        // El tope de `styleDef(maxSystemDistance, 15.0_sp)` (`styledef.cpp:59`) acota el hueco de
        // **caja**, no el de pauta: el hueco de pauta máximo es el que deja el hueco de caja en el
        // tope, y el que muerde es el par con menos vuelo por encima y por debajo. En el `salmo2`
        // muerde —el hueco de pauta se queda en 18,1 espacios en vez de los 37 que pediría el
        // papel— y por eso la hoja **no se llena**, igual que en MuseScore, que deja 154 pt en
        // blanco al pie.
        val cap = gaps.minOf { i ->
            maxGapPt + rows[i].tablatureGridTopPt +
                (rows[i - 1].heightPt - rows[i - 1].tablatureGridBottomPt)
        }
        val staffGap = minOf(uniform, cap).coerceAtLeast(minGapPt)
        val extra = FloatArray(rows.size)
        gaps.forEachIndexed { k, i ->
            val wanted = (staffGap - current[k] + rows[i].leadingGapPt).coerceAtLeast(0f)
            extra[i] = (wanted - rows[i].leadingGapPt).coerceAtLeast(0f)
        }
        if (extra.none { it > 0f }) return rows
        return rows.mapIndexed { index, row ->
            if (extra[index] > 0f) row.copy(leadingGapPt = row.leadingGapPt + extra[index]) else row
        }
    }
}
