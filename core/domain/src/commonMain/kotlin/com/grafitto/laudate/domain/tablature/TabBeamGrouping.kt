package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **Agrupamiento de corchetes** (barras de unión) por **pulso**, con la regla de MuseScore.
 *
 * Hasta ahora el pintor unía **todas** las corcheas contiguas de un compás en **una sola** barra.
 * Medido del `salmo2.pdf` (2026-10-14), eso **no** es lo que hace MuseScore: el compás 3 del primer
 * sistema tiene 8 corcheas y el PDF las une en **dos** barras de 4 (x = 1126..1285 y 1336..1495),
 * separadas justo en la mitad del compás. Una barra de compás entero es un error de grabado: la barra
 * debe **respetar el pulso** para que se lea dónde cae cada tiempo.
 *
 * La regla no es inventada: es la tabla `noteGroups` de MuseScore 4
 * (`src/engraving/dom/groups.cpp`), que a su vez recoge la convención de grabado. Cada entrada de la
 * tabla es un **nodo** `(posición, máscara)`:
 *
 *  - la **posición** va en **treintaidosavos** (1/8 de negra) desde el inicio del compás;
 *  - la **máscara** tiene tres campos de 4 bits, uno por **nivel de barra**: bits 0-3 para las
 *    corcheas, 4-7 para las semicorcheas y 8-11 para las figuras más cortas. El valor `1` significa
 *    «aquí **arranca** una barra nueva» y `0`, «la barra **continúa**».
 *
 * De ahí salen los cortes que se ven en el PDF: en 4/4 las corcheas se cortan en la **mitad** del
 * compás (4+4) y las semicorcheas en **cada negra** (4+4+4+4); en 6/8 las corcheas se cortan en
 * 3+3; en 12/8, en 3+3+3+3.
 *
 * Kotlin puro y determinista: lo comparten el editor, el visor y el PDF.
 */
object TabBeamGrouping {

    /** La barra **arranca** aquí: la figura no se une a la anterior. */
    private const val BEGIN = 1

    /**
     * **Tabla de nodos** de MuseScore 4 (`noteGroups` de `groups.cpp`), copiada literalmente.
     *
     * La clave es el compás `(pulsos, unidad)`; el valor, la lista de nodos `(posición en
     * treintaidosavos, máscara)`. Solo están los compases que MuseScore tabula; el resto usa la
     * **reserva** de [nodesOf].
     */
    private val TABLE: Map<Pair<Int, Int>, List<Pair<Int, Int>>> = mapOf(
        (2 to 2) to listOf(
            4 to 0x200, 8 to 0x110, 12 to 0x200, 16 to 0x111, 20 to 0x200, 24 to 0x110, 28 to 0x200
        ),
        (3 to 2) to listOf(
            4 to 0x200, 8 to 0x110, 12 to 0x200, 16 to 0x111, 20 to 0x200, 24 to 0x110, 28 to 0x200,
            32 to 0x111, 36 to 0x200, 40 to 0x110, 44 to 0x200
        ),
        (4 to 2) to listOf(
            4 to 0x200, 8 to 0x110, 12 to 0x200, 16 to 0x111, 20 to 0x200, 24 to 0x110, 28 to 0x200,
            32 to 0x111, 36 to 0x200, 40 to 0x110, 44 to 0x200, 48 to 0x111, 52 to 0x200, 56 to 0x110,
            60 to 0x200
        ),
        (2 to 4) to listOf(4 to 0x200, 8 to 0x111, 12 to 0x200),
        (3 to 4) to listOf(4 to 0x200, 8 to 0x111, 12 to 0x200, 16 to 0x111, 20 to 0x200),
        (4 to 4) to listOf(
            4 to 0x200, 8 to 0x110, 12 to 0x200, 16 to 0x111, 20 to 0x200, 24 to 0x110, 28 to 0x200
        ),
        (5 to 4) to listOf(
            4 to 0x200, 8 to 0x110, 12 to 0x200, 16 to 0x110, 20 to 0x200, 24 to 0x111, 28 to 0x200,
            32 to 0x110, 36 to 0x200
        ),
        (6 to 4) to listOf(
            4 to 0x200, 8 to 0x110, 12 to 0x200, 16 to 0x110, 20 to 0x200, 24 to 0x111, 28 to 0x200,
            32 to 0x110, 36 to 0x200, 40 to 0x110, 44 to 0x200
        ),
        (3 to 8) to listOf(4 to 0x200, 8 to 0x200),
        (5 to 8) to listOf(4 to 0x200, 8 to 0x200, 12 to 0x111, 16 to 0x200),
        (6 to 8) to listOf(4 to 0x200, 8 to 0x200, 12 to 0x111, 16 to 0x200, 20 to 0x200),
        (7 to 8) to listOf(4 to 0x200, 8 to 0x200, 12 to 0x111, 16 to 0x200, 20 to 0x111, 24 to 0x200),
        (9 to 8) to listOf(
            4 to 0x200, 8 to 0x200, 12 to 0x111, 16 to 0x200, 20 to 0x200, 24 to 0x111, 28 to 0x200,
            32 to 0x200
        ),
        (12 to 8) to listOf(
            4 to 0x200, 8 to 0x200, 12 to 0x111, 16 to 0x200, 20 to 0x200, 24 to 0x111, 28 to 0x200,
            32 to 0x200, 36 to 0x111, 40 to 0x200, 44 to 0x200
        )
    )

    /**
     * **Posiciones donde arranca una barra nueva** para figuras de duración [figure] en [time],
     * en **negras** desde el inicio del compás. La primera es siempre `0.0` (el compás abre barra).
     *
     * Es la lectura directa de la tabla: se queda con los nodos cuyo campo de 4 bits del **nivel de
     * barra** de [figure] vale `1`. En 4/4 devuelve `[0.0, 2.0]` para las corcheas (4+4) y
     * `[0.0, 1.0, 2.0, 3.0]` para las semicorcheas (4+4+4+4).
     *
     * Una figura **sin barra** (negra o más larga) no se une nunca: devuelve solo `[0.0]`.
     */
    fun breakPositions(time: ScoreTimeSignature, figure: RhythmFigure): List<Double> {
        val shift = shiftOf(figure) ?: return listOf(0.0)
        val positions = mutableListOf(0.0)
        nodesOf(time).forEach { (pos, action) ->
            if (((action shr shift) and 0xf) == BEGIN) positions += pos / 8.0
        }
        return positions
    }

    /**
     * `true` si la figura que empieza en [beatOffset] (**negras** desde el inicio del compás)
     * **arranca** una barra nueva en vez de unirse a la anterior.
     *
     * Es la regla de `Groups::beamMode` de MuseScore: se busca el nodo de la **posición exacta** y se
     * mira el campo de 4 bits del nivel de barra de [figure]. Si la posición **no** cae en un nodo
     * (una figura a contratiempo, un tresillo), la barra **continúa**, que es lo que hace MuseScore
     * (`beamMode` devuelve `AUTO` y el llamante lo convierte en `MID`).
     */
    fun startsBeam(beatOffset: Double, figure: RhythmFigure, time: ScoreTimeSignature): Boolean {
        if (beatOffset <= 1e-6) return true
        val shift = shiftOf(figure) ?: return false
        val exact = beatOffset * 8.0
        val pos = exact.roundToInt()
        if (abs(exact - pos) > 1e-6) return false
        val action = nodesOf(time).firstOrNull { it.first == pos }?.second ?: return false
        return ((action shr shift) and 0xf) == BEGIN
    }

    /**
     * **Grupos de barras de unión** de [marks], como **índices** sobre la propia lista.
     *
     * Un grupo es una **racha contigua** de figuras con corchete ([TabRhythmGeometry.flagCount] > 0)
     * dentro del **mismo compás** y del **mismo pulso** ([startsBeam]). Un silencio o una figura sin
     * corchete **corta** la racha, y una racha de una sola figura no se agrupa (se dibuja con su
     * **bandera**, no con barra).
     *
     * Los índices apuntan a [marks] tal cual, así que el pintor no necesita volver a decidir nada: la
     * decisión musical (dónde se corta la barra) se toma **una vez**, en el Dominio, donde se conoce el
     * compás. [marks] puede venir desordenada: el orden de lectura se reconstruye aquí.
     */
    fun groupsOf(
        marks: List<TabRhythmMark>,
        time: ScoreTimeSignature,
        /**
         * Indicación de compás **vigente** en cada compás. Por defecto la misma para todos; con
         * **cambio de compás** a mitad de pieza cada compás corta por **su** pulso, que es lo que hace
         * MuseScore al agrupar las barras de unión.
         */
        timeAt: (Int) -> ScoreTimeSignature = { time }
    ): List<List<Int>> {
        if (marks.isEmpty()) return emptyList()
        val result = mutableListOf<List<Int>>()
        var run = mutableListOf<Int>()
        var measure = Int.MIN_VALUE

        fun flush() {
            if (run.size > 1) result += run
            run = mutableListOf()
        }

        val readingOrder = marks.indices.sortedWith(
            compareBy({ marks[it].measureIndex }, { marks[it].beatOffset })
        )
        readingOrder.forEach { index ->
            val mark = marks[index]
            if (mark.measureIndex != measure) {
                flush()
                measure = mark.measureIndex
            }
            if (TabRhythmGeometry.flagCount(mark.figure) == 0) {
                flush()
            } else {
                if (startsBeam(mark.beatOffset, mark.figure, timeAt(mark.measureIndex))) flush()
                run += index
            }
        }
        flush()
        return result
    }

    /**
     * **Nivel de barra** de [figure] dentro de la máscara: `0` corcheas, `4` semicorcheas, `8` las
     * más cortas. `null` si la figura **no lleva barra** (negra o más larga, o un silencio).
     */
    private fun shiftOf(figure: RhythmFigure): Int? = when (figure) {
        RhythmFigure.EIGHTH, RhythmFigure.DOTTED_EIGHTH -> 0
        RhythmFigure.SIXTEENTH -> 4
        else -> null
    }

    /**
     * **Nodos** de [time]: la tabla de MuseScore o, si el compás no está tabulado, su **reserva**
     * (`Groups::endings`): un corte por **pulso de la unidad del denominador**, con la máscara
     * `0x111` (corta en todos los niveles).
     *
     * Un compás **aditivo** («3+2») no tiene entrada propia, pero sí un total de negras: se deduce su
     * numerador equivalente (`5` para «3+2»/8) para poder aplicar la reserva.
     */
    private fun nodesOf(time: ScoreTimeSignature): List<Pair<Int, Int>> {
        val denominator = time.beatType
        if (denominator <= 0) return emptyList()
        val numerator = time.beatsAsInt
            ?: (time.totalQuarterBeats * denominator / 4.0).roundToInt()
        if (numerator <= 0) return emptyList()
        TABLE[numerator to denominator]?.let { return it }
        val unit = when (denominator) {
            2 -> 16
            4 -> 8
            8 -> 4
            16 -> 2
            32 -> 1
            else -> return emptyList()
        }
        return (1 until numerator).map { it * unit to 0x111 }
    }
}
