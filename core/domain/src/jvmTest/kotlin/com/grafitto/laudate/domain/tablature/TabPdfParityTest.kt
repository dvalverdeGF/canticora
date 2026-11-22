package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.serializer.MsczParser
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.abs

/**
 * **Arnés de comparación Canticora ↔ `salmo2.pdf`** (tarea 3.1 del `TABLATURE_CONTINUIDAD.md`).
 *
 * Hasta ahora la paridad se comprobaba «a ojo»; esto la convierte en un **test con números**. El
 * fixture `resources/salmo2/salmo2-frets.csv` lo genera `tools/salmo2_reference.py` extrayendo del
 * PDF real (`pdftotext -bbox` + detección de líneas por píxeles del PNG a 300 dpi) la **x** de cada
 * cifra medida en **espacios de pentagrama**.
 *
 * El test **no** compara la `x` absoluta (el arranque del pentagrama —clave/compás— es un tema aparte,
 * V-2), sino el **paso entre cifras consecutivas** (`dx`): es lo que fija la **ley de espaciado** de
 * C.1 y el **salto entre compases**. Si `TabSpacing` se toca sin querer, aquí salta.
 *
 * Se **salta** (`assumeTrue`) si faltan el `.mscz` o el fixture, para no romper la batería en un
 * clon sin los binarios de referencia.
 */
class TabPdfParityTest {

    // ── Medidas REALES del `salmo2.pdf` (extraídas del PDF, no estimadas) ────────────────────────
    /** Separación entre cuerdas, en **pt**: 7,44 (`<lineDistance>1.5</lineDistance>`). */
    private val stringSpacingPt = 7.44f

    /** **Espacio de pentagrama** en pt = separación / 1,5 = **4,96 pt**. */
    private val spacePt get() = stringSpacingPt / 1.5f

    /**
     * **Tolerancia** en espacios de pentagrama para el paso entre cifras. El PDF mide a 300 dpi
     * (0,24 pt/píxel ≈ 0,05 espacios), pero se deja holgura para el redondeo de la detección de
     * líneas; el objetivo de la tarea es **≤ 1 px ≈ 0,05 espacios**, y aquí se comprueba con 0,35.
     */
    private val toleranceSpaces = 0.35f

    private fun mscx(): String? {
        val candidates = listOf(
            File("src/jvmTest/resources/salmo2.mscz"),
            File("core/domain/src/jvmTest/resources/salmo2.mscz")
        )
        val f = candidates.firstOrNull { it.exists() } ?: return null
        ZipInputStream(f.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".mscx")) return zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return null
    }

    private fun fixture(): File? = listOf(
        File("src/jvmTest/resources/salmo2/salmo2-frets.csv"),
        File("core/domain/src/jvmTest/resources/salmo2/salmo2-frets.csv")
    ).firstOrNull { it.exists() }

    /** Cifras del fixture, ordenadas por `x`, en **espacios** (relativas al borde de la rejilla). */
    private data class Ref(val text: String, val system: Int, val string: Int, val xSpaces: Float)

    private fun reference(): List<Ref> {
        val f = fixture() ?: return emptyList()
        return f.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val c = line.split(",")
            // text,system,string,x_spaces,y_spaces,x_pt
            Ref(c[0], c[1].toInt(), c[2].toInt(), c[3].toFloat())
        }
    }

    /**
     * Ajustes de la maquetación **a la escala del PDF**: espacio de pentagrama 4,96 pt (⇒ separación
     * de cuerdas 7,44 pt) y `cellWidth` = una negra = **3,5 espacios** (el ancho **natural**; el PDF
     * está justificado y sus cifras salen un 7,8 % más separadas). Sin etiquetas de cuerda
     * (MuseScore no las dibuja) y con clave/compás activos, como la referencia.
     */
    private fun settings() = TabLayoutSettings(
        stringSpacing = stringSpacingPt,
        cellWidth = TabSpacing.quarterWidth(spacePt),
        staff = TabStaffOptions(
            showStringLabels = false,
            showClef = true,
            showTimeSignature = true,
            noteValues = NoteValueStyle.STEMS_AND_BEAMS,
            showMeasureNumbers = true
        )
    )

    /**
     * `x` de cada **cifra** del primer sistema, en **espacios**, ordenadas. Se maqueta con un ancho
     * enorme para que **todo** caiga en un solo sistema: así el arnés mide solo la **ley de
     * espaciado**, sin depender de dónde rompa MuseScore los sistemas.
     */
    private fun canticoraFretXs(): List<Float> {
        val xml = mscx() ?: return emptyList()
        val tab = MsczParser.parse(xml, songId = "salmo2")
        val st = settings()
        val systems = TablatureSystemsLayout.layout(tab, maxWidth = 1_000_000f, settings = st)
        val drawing = systems.firstOrNull()?.drawing ?: return emptyList()
        return drawing.texts
            .filter { it.kind == TabTextKind.FRET }
            .sortedBy { it.x }
            .map { (it.x - drawing.gridLeft) / spacePt }
    }

    /**
     * **Pasos intra-compás** de los **3 primeros compases** (los «limpios»: 4 negras + 4 negras + 8
     * corcheas), en espacios. Se excluye el salto **entre compases** (la barra añade aire en
     * MuseScore, y el ancho de la barra es un tema de maquetación aparte, no de la ley de espaciado).
     *
     * Devuelve la lista `dx` de cada compás concatenada: 3 + 3 + 7 = **13** pasos.
     */
    private fun intraMeasureSteps(xs: List<Float>, counts: List<Int>): List<Float> {
        val steps = mutableListOf<Float>()
        var offset = 0
        counts.forEach { count ->
            for (i in offset + 1 until offset + count) {
                if (i < xs.size && i - 1 < xs.size) steps += xs[i] - xs[i - 1]
            }
            offset += count
        }
        return steps
    }

    /** Nº de columnas **con notas** de los 3 primeros compases del `.mscz` (4 + 4 + 8). */
    private fun firstThreeMeasureNoteCounts(): List<Int> {
        val xml = mscx() ?: return emptyList()
        return MsczParser.parse(xml, songId = "salmo2").measures.take(3).map { m -> m.slots.count { it.strings.isNotEmpty() } }
    }

    /** `x` (en espacios) de las cifras de los 3 primeros compases, en orden de lectura. */
    private fun canticoraFretXsFirstThree(): List<Float> {
        val xml = mscx() ?: return emptyList()
        val tab = MsczParser.parse(xml, songId = "salmo2")
        val st = settings()
        val systems = TablatureSystemsLayout.layout(tab, maxWidth = 1_000_000f, settings = st)
        val drawing = systems.firstOrNull()?.drawing ?: return emptyList()
        // Se ordenan por (x, cuerda) para que un acorde no desordene la secuencia.
        val wanted = firstThreeMeasureNoteCounts().sum()
        return drawing.texts
            .filter { it.kind == TabTextKind.FRET }
            .sortedWith(compareBy({ it.x }, { it.y }))
            .take(wanted)
            .map { (it.x - drawing.gridLeft) / spacePt }
    }

    @Test
    fun losPasosIntraCompasCoincidenConElPdfDentroDeTolerancia() {
        val xml = mscx()
        assumeTrue("falta resources/salmo2.mscz", xml != null)
        val ref = reference().filter { it.system == 0 }
        assumeTrue("falta resources/salmo2/salmo2-frets.csv", ref.isNotEmpty())

        val counts = firstThreeMeasureNoteCounts()
        assumeTrue("no se pudo leer la estructura del .mscz", counts.size == 3)

        val refXs = ref.map { it.xSpaces }
        val mineXs = canticoraFretXsFirstThree()
        assumeTrue("la maquetación no produjo cifras", mineXs.isNotEmpty())
        assumeTrue("el PDF no trae las ${counts.sum()} cifras esperadas", refXs.size >= counts.sum())

        val refSteps = intraMeasureSteps(refXs, counts)
        val mineSteps = intraMeasureSteps(mineXs, counts)

        val deviations = refSteps.indices.map { abs(refSteps[it] - mineSteps[it]) }
        val worst = deviations.maxOrNull() ?: 0f
        val worstIndex = deviations.indexOf(worst)

        println("Arnés salmo2 · pasos intra-compás (${refSteps.size})")
        println("  PDF   = ${refSteps.map { "%.3f".format(it) }}")
        println("  Cant. = ${mineSteps.map { "%.3f".format(it) }}")
        println("  peor desviación = %.3f espacios (paso ${worstIndex + 1})".format(worst))

        assertTrue(
            "el paso entre cifras difiere del PDF en %.3f espacios (límite %.2f) en el paso %d"
                .format(worst, toleranceSpaces, worstIndex + 1),
            worst <= toleranceSpaces
        )
    }

    @Test
    fun elNumeroDeCifrasDelTramoDeMusicaCoincide() {
        val xml = mscx()
        assumeTrue("falta resources/salmo2.mscz", xml != null)
        val ref = reference().filter { it.system == 0 }
        assumeTrue("falta resources/salmo2/salmo2-frets.csv", ref.isNotEmpty())

        val mine = canticoraFretXs()
        assumeTrue("la maquetación no produjo cifras", mine.isNotEmpty())

        // Las cifras de la música del PDF (4 compases) tienen que estar **todas** en Canticora.
        assertTrue(
            "el PDF trae ${ref.size} cifras y Canticora dibuja ${mine.size} (faltan ${ref.size - mine.size})",
            mine.size >= ref.size
        )
    }

    @Test
    fun lasConstantesMedidasDelPdfSiguenEnElMotor() {
        // Guardarraíl: si alguien cambia la ley, este test lo dice aunque el `.mscz` no esté.
        org.junit.Assert.assertEquals(
            "la negra mide 3,5 espacios (DEFAULT_QUARTER_NOTE_SPACE)",
            3.5f, TabSpacing.QUARTER_WIDTH_SPACES, 0.001f
        )
        org.junit.Assert.assertEquals(
            "el exponente medido es 0,585",
            0.585f, TabSpacing.SPACING_EXPONENT, 0.001f
        )
        // La corchea y la semicorchea salen de la ley: 2,333 y 1,556 espacios.
        val quarter = TabSpacing.quarterWidth(spacePt)
        org.junit.Assert.assertEquals(
            "la corchea mide 2,333 espacios",
            2.333f, TabSpacing.columnWidth(0.5, quarter) / spacePt, 0.01f
        )
        org.junit.Assert.assertEquals(
            "la semicorchea mide 1,556 espacios",
            1.556f, TabSpacing.columnWidth(0.25, quarter) / spacePt, 0.01f
        )
    }

    /**
     * **Reparto en líneas del `salmo2` real** (F-04.quater): el PDF reparte sus 32 compases en
     * **5 / 11 / 11 / 5**. Se reproduce con la geometría de página de MuseScore —120 espacios de
     * ancho (210 mm / 1,75 mm de spatium) y margen del 7,1 % por lado (D-13)— y el ancho **natural**
     * de la negra (`DEFAULT_QUARTER_NOTE_SPACE = 3,5_sp`).
     *
     * Es el test que cierra el último desajuste de reparto: con el ancho **justificado** (3,815) los
     * compases salían un 9 % más anchos y el reparto era **4 / 10 / 11 / 7**.
     */
    @Test
    fun elRepartoEnLineasDelSalmo2EsElDelPdf() {
        val xml = mscx()
        assumeTrue("falta core/domain/src/jvmTest/resources/salmo2.mscz", xml != null)
        val tablature = MsczParser.parse(xml!!, songId = "salmo2")
        val staffSpace = 20f
        val pageWidth = staffSpace * 120f
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(staffSpace),
            stringSpacing = staffSpace * TabRhythmGeometry.STRING_SPACING_SPACES,
            labelWidth = staffSpace * 3.6f * 0.55f,
            padding = staffSpace * 3.6f * 0.18f,
            pageMargin = pageWidth * TabLayoutSettings.PAGE_MARGIN_RATIO,
            staff = TabStaffOptions(showStringLabels = false)
        )
        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = pageWidth, settings = settings)
        org.junit.Assert.assertEquals(
            "el PDF reparte los 32 compases del salmo2 en 5 / 11 / 11 / 5",
            listOf(5, 11, 11, 5),
            systems.map { it.measureIndices.size }
        )
    }

    /**
     * **Reparto en líneas del `salmo2` = 5 / 11 / 11 / 5**, el del PDF.
     *
     * Es la comprobación de extremo a extremo de la ley de espaciado: se maqueta el `.mscz` real con
     * la **geometría de página de MuseScore** (120 espacios de ancho = 210 mm / 1,75 mm de `spatium`,
     * margen del 7,1 % por lado ⇒ **102,96 espacios imprimibles**, frente a los 102,89 del `.mscz`) y
     * se cuentan los compases de cada sistema.
     *
     * Los números salen de la ley, no de un ajuste: los compases 1–5 (4 negras, 4 negras, 8 corcheas,
     * 6 semicorcheas + 2 negras + 1 corchea, tresillo de blancas) suman **91,52 espacios** más la
     * cabecera, y el 6.º (silencio de compás, **9,125**) ya no cabe; los vacíos entran **11** por
     * sistema (`102,96 / 9,125 = 11,28`) y los 5 que quedan cierran la página.
     *
     * Si alguien vuelve a meter el ancho **justificado** (3,815) como natural, el reparto se
     * descuadra a 4 / 10 / 11 / 7 y este test lo dice.
     */
    @Test
    fun elSalmo2SeReparteEnSistemasComoElPdf() {
        val xml = mscx() ?: return
        val tab = MsczParser.parse(xml, songId = "salmo2")
        assumeTrue("el .mscz no trajo compases", tab.measures.size == 32)
        val staffSpace = 20f
        val pageWidth = staffSpace * 120f
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(staffSpace),
            stringSpacing = staffSpace * TabRhythmGeometry.STRING_SPACING_SPACES,
            labelWidth = staffSpace * 3.6f * 0.55f,
            padding = staffSpace * 3.6f * 0.18f,
            pageMargin = pageWidth * TabLayoutSettings.PAGE_MARGIN_RATIO,
            staff = TabStaffOptions(
                showStringLabels = false,
                showClef = true,
                showTimeSignature = true
            )
        )
        val systems = TablatureSystemsLayout.layout(tab, maxWidth = pageWidth, settings = settings)
        org.junit.Assert.assertEquals(
            "el reparto en líneas del PDF es 5 / 11 / 11 / 5",
            listOf(5, 11, 11, 5),
            systems.map { it.measureIndices.size }
        )
    }

    @Test
    fun elNumeroDeSistemasDetectadoDelPdfSonCuatro() {
        // Confirma que el fixture recoge los 4 sistemas del PDF (6 líneas cada uno).
        val lines = listOf(
            File("src/jvmTest/resources/salmo2/salmo2-lines.csv"),
            File("core/domain/src/jvmTest/resources/salmo2/salmo2-lines.csv")
        ).firstOrNull { it.exists() } ?: return
        val values = lines.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { it.toFloatOrNull() }
        assumeTrue("fixture sin líneas", values.isNotEmpty())
        val systems = values.count { it > 0f && it < 800f } / 6
        println("líneas de cuerda detectadas: ${values.size} ⇒ ~$systems sistemas")
        assertTrue("el PDF tiene 4 sistemas de 6 cuerdas", values.size >= 24)
    }

    /**
     * (D-53) **El PDF impreso reparte el `salmo2` en 5 / 11 / 11 / 5**, igual que MuseScore.
     *
     * Los dos tests de reparto anteriores maquetan con la geometría del **lienzo** (120 espacios de
     * ancho y margen del 7,1 % por lado). El **PDF** usaba otra: `contentWidthPt = 493 pt` (que ya
     * había descontado **dos veces** la franja de etiquetas de 18 pt) y además `pageMargin = 3,24 pt`
     * dentro del contenido, así que el sistema arrancaba en **89,3 pt** en vez de los **67,4 pt** del
     * PDF de MuseScore y disponía de **460,7 pt** en vez de **485,2**. Con 460,7 pt los cinco primeros
     * compases (478,2 pt) no cabían y el reparto salía **4 / 10 / 10 / 8**.
     *
     * Este test fija la geometría **real del papel**: A4 (595 pt) con 42 pt de margen por lado, la
     * tablatura ocupando el ancho de página completo y el margen aportado por el paginador
     * (`pageMargin = 0`). Si alguien vuelve a sumar la franja de etiquetas o el margen, aquí salta.
     */
    @Test
    fun elPdfDelSalmo2SeReparteComoElDeMuseScore() {
        val xml = mscx() ?: return
        val tab = MsczParser.parse(xml, songId = "salmo2")
        assumeTrue("el .mscz no trajo compases", tab.measures.size == 32)
        // Geometría del papel: `SongPdfLayout.PAGE_WIDTH_PT - 2 · marginPt`.
        val pageWidthPt = 595f - 2f * 42f
        val staffSpacePt = 5f
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(staffSpacePt),
            stringSpacing = staffSpacePt * TabRhythmGeometry.STRING_SPACING_SPACES,
            labelWidth = staffSpacePt * 3.6f * 0.55f,
            // (D-53) El margen de página ya lo aporta el paginador del PDF.
            padding = 0f,
            pageMargin = 0f,
            staff = TabStaffOptions(
                showStringLabels = false,
                showClef = true,
                showTimeSignature = true
            )
        )
        val systems = TablatureSystemsLayout.layout(tab, maxWidth = pageWidthPt, settings = settings)
        org.junit.Assert.assertEquals(
            "el PDF de MuseScore reparte los 32 compases en 5 / 11 / 11 / 5",
            listOf(5, 11, 11, 5),
            systems.map { it.measureIndices.size }
        )
        // El sistema arranca en el **margen de página** (42 pt, que aporta el paginador del PDF y no
        // el layout) más la cabecera (clave + compás + 1,25 espacios ≈ 26 pt): **68 pt**. El PDF de
        // MuseScore mide 67,4 pt (281 px a 300 dpi). Antes de D-53 el layout sumaba además la franja
        // de etiquetas (18 pt) y su propio `pageMargin` (3,24 pt), y el sistema arrancaba en 89,3 pt.
        val first = systems.first().drawing
        org.junit.Assert.assertEquals(
            "el primer sistema arranca en el margen de página, como en MuseScore",
            68.0f,
            42f + first.staffLeft,
            1.0f
        )
    }

    /** Maqueta el `salmo2` con la geometría **del papel** (A4, 42 pt de margen, espacio de 5 pt). */
    private fun salmo2Systems(): List<TabSystem>? {
        val xml = mscx() ?: return null
        val tab = MsczParser.parse(xml, songId = "salmo2")
        assumeTrue("el .mscz no trajo compases", tab.measures.size == 32)
        val staffSpacePt = 5f
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(staffSpacePt),
            stringSpacing = staffSpacePt * TabRhythmGeometry.STRING_SPACING_SPACES,
            labelWidth = staffSpacePt * 3.6f * 0.55f,
            padding = 0f,
            pageMargin = 0f,
            staff = TabStaffOptions(
                showStringLabels = false,
                showClef = true,
                showTimeSignature = true
            )
        )
        return TablatureSystemsLayout.layout(tab, maxWidth = 595f - 2f * 42f, settings = settings)
    }

    /**
     * (D-59) **El aire de base de la pila de símbolos no mueve el paso entre sistemas.**
     *
     * D-59 engordó la franja `SYMBOLS` en `baseClearanceSpaces` (0,756 espacios) para que el trémolo,
     * la palanca, los armónicos y el `l.v.` dejen de pisar la cifra de la cuerda 1. Como esa franja
     * entra en `height` **y** en `gridTop` a la vez, el **hueco de pauta** de los pares afectados no
     * cambia; y el tope de `maxSystemDistance` (D-57) lo fijan los pares **2-3 y 3-4**, que no llevan
     * articulaciones. Resultado: el paso sigue en **128,5 pt** frente a los **129,1 / 128,9 / 128,9**
     * del `salmo2.pdf` (0,5 % de desviación, la misma que dejó D-57).
     *
     * Si alguien vuelve a sumar el aire de base **también** en `gridTop` sin restarlo en `symbolsY`,
     * o lo mete en una franja que sí acota el tope, este test lo dice.
     */
    @Test
    fun elAireDeBaseDeLosSimbolosNoMueveElPasoEntreSistemas() {
        val systems = salmo2Systems() ?: return
        org.junit.Assert.assertEquals(
            "el reparto en líneas del PDF es 5 / 11 / 11 / 5",
            listOf(5, 11, 11, 5),
            systems.map { it.measureIndices.size }
        )
        val staffSpacePt = 5f
        val maxGapPt = staffSpacePt * TabLayoutSettings.MAX_SYSTEM_DISTANCE_SPACES
        // Tope del hueco de pauta: `maxSystemDistance` más lo que vuela el sistema de arriba por
        // debajo de su última cuerda y el de abajo por encima de su primera cuerda.
        val cap = (1 until systems.size).minOf { i ->
            maxGapPt + systems[i].drawing.gridTop +
                (systems[i - 1].height - systems[i - 1].drawing.gridBottom)
        }
        // Los pares 2-3 y 3-4 no llevan articulaciones: su `gridTop` es solo la franja del número de
        // compás (3,2 espacios) y no vuela nada por debajo de la última cuerda.
        org.junit.Assert.assertEquals(
            "el tope lo fijan los sistemas sin articulaciones",
            maxGapPt + 3.2f * staffSpacePt,
            cap,
            0.01f
        )
        val stringSpan = systems[0].drawing.gridBottom - systems[0].drawing.gridTop
        val step = cap + stringSpan
        org.junit.Assert.assertEquals(
            "el paso entre sistemas es el del PDF (129,1 pt)",
            129.1f,
            step,
            1.0f
        )
    }

    /**
     * (D-59) **Solo los sistemas con articulaciones reservan la franja `SYMBOLS`.**
     *
     * La franja se suma a `height` únicamente si el sistema la **usa** (`TabStaffZones.used`), así que
     * un sistema sin trémolos ni palancas no paga el aire de base. En el `salmo2` solo el primero
     * lleva articulaciones: los otros tres arrancan su caja en la franja del número de compás.
     */
    @Test
    fun soloLosSistemasConArticulacionesReservanLaFranjaDeSimbolos() {
        val systems = salmo2Systems() ?: return
        val staffSpacePt = 5f
        val measureNumberBand = 3.2f * staffSpacePt
        assertTrue(
            "el primer sistema lleva articulaciones y su caja sube por encima del número de compás",
            systems.first().drawing.gridTop > measureNumberBand + 0.5f
        )
        systems.drop(1).forEachIndexed { index, system ->
            org.junit.Assert.assertEquals(
                "el sistema ${index + 2} no lleva articulaciones: no reserva la franja de símbolos",
                measureNumberBand,
                system.drawing.gridTop,
                0.01f
            )
        }
    }
}
