package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabJump
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabNavigationMark
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.Tablature

/**
 * Un **ataque** de la reproducción: qué suena, cuándo y cuánto dura.
 *
 * [atMs] y [durationMs] están en milisegundos desde el **principio de la pieza**, ya con el tempo
 * aplicado. [midiNotes] son las alturas simultáneas del acorde (una por cuerda pulsada); las cuerdas
 * apagadas (`X`) no entran porque no tienen altura definida.
 */
data class TabPlaybackEvent(
    val measureIndex: Int,
    val slotIndex: Int,
    val atMs: Long,
    val durationMs: Long,
    val midiNotes: List<Int>
)

/**
 * Un **clic del metrónomo**: cuándo suena y si es el **acento** del compás.
 *
 * MuseScore hace sonar el metrónomo en cada **pulsación** del compás (la unidad del denominador: la
 * negra en 4/4, la corchea en 6/8) y **acentúa** la primera. En los compases **compuestos** (6/8, 9/8,
 * 12/8) acentúa también la mitad del compás, que es donde empieza el segundo grupo de tres.
 *
 * [beat] es la pulsación **dentro del compás**, empezando en 1.
 */
data class TabMetronomeClick(
    val measureIndex: Int,
    val beat: Int,
    val atMs: Long,
    val isAccent: Boolean
)

/**
 * **Línea de tiempo** de la reproducción de una tablatura: los ataques en orden y la duración total.
 *
 * Es el resultado de [TabPlayback.timeline] y es **puro**: no sabe de audio ni de coroutines. El
 * ViewModel la recorre con `delay()` y llama a [TabNotePlayer]; así el motor de audio de cada
 * plataforma (ADR-012/ADR-013) queda intacto y la lógica musical se puede probar sin sonido.
 *
 * [clicks] son los clics del **metrónomo**, ya sincronizados con [events] porque se calculan en la
 * misma pasada: así el clic y la nota de la misma pulsación caen en el mismo milisegundo.
 */
data class TabPlaybackTimeline(
    val events: List<TabPlaybackEvent>,
    val totalMs: Long,
    val clicks: List<TabMetronomeClick> = emptyList()
) {
    val isEmpty: Boolean get() = events.isEmpty()

    /** Índice del evento que suena en [positionMs], o `-1` si ya ha terminado. */
    fun eventIndexAt(positionMs: Long): Int =
        events.indexOfLast { it.atMs <= positionMs }
}

/**
 * **Motor de reproducción** de la tablatura (B-9), en Kotlin puro.
 *
 * Convierte la partitura en una [TabPlaybackTimeline] aplicando las mismas reglas que MuseScore:
 *
 *  - **Tempo**: cada compás puede traer su `♩ = N` ([TabMeasure.tempo]); si no, se hereda el
 *    anterior y, al principio, se usa [defaultBpm].
 *  - **Duración**: la figura de la casilla ([com.grafitto.laudate.domain.model.tablature.TabSlot.rhythmicFigure])
 *    o, si no la tiene, la de la rejilla ([Tablature.resolution]). El **tresillo** dura 2/3.
 *  - **Ligadura de prolongación** (`tiedToNext`, B-10): la nota ligada **no se vuelve a atacar** y su
 *    sonido se **suma** a la duración de la nota de destino, cruzando compases si hace falta.
 *  - **Prolongación** ([com.grafitto.laudate.domain.model.tablature.TabSlot.isHeld]): la casilla no
 *    ataca; alarga el sonido que ya estaba sonando.
 *  - **Repeticiones**: `|:` … `:|` con [TabMeasure.repeatCount] se expanden, como al reproducir en
 *    MuseScore. Las casillas de *volta* ([TabMeasure.ending]) se respetan: la 1.ª vuelta solo suena
 *    en la primera pasada, y una casilla de **varias pasadas** (`1, 2.`, [TabMeasure.endingTo]) suena
 *    en todas las suyas.
 *  - **Saltos** (`D.C.`, `D.S.`, `al Fine`, `al Coda`): se resuelven con [playOrder] siguiendo las
 *    reglas de `RepeatList::unwind` de MuseScore — el salto se toma **una sola vez** y **solo en la
 *    última pasada** del compás que lo lleva, el compás del salto **suena**, el *Fine* / *To Coda*
 *    **suena** y después se salta a la *Coda* o se termina.
 *
 * Lo que **no** hace (a propósito, para no mentir): solo se resuelve **un** salto por pieza (el
 * primero que aparezca). Encadenar varios saltos queda para cuando haga falta.
 */
object TabPlayback {

    /** Tempo de partida si la pieza no indica ninguno: negra = 90, un *Andante* cómodo. */
    const val DEFAULT_BPM: Int = 90

    /** Tope de compases expandidos, para que una repetición mal puesta no cuelgue la reproducción. */
    private const val MAX_EXPANDED_MEASURES = 2_000

    /**
     * Alturas del **clic del metrónomo**: `C6` para el acento del compás y `E5` para el resto, que es
     * la relación de MuseScore (el acento suena más agudo y más fuerte). Se piden al reproductor como
     * notas MIDI cortas, así no hace falta un canal de audio aparte.
     */
    const val METRONOME_ACCENT_MIDI: Int = 84
    const val METRONOME_CLICK_MIDI: Int = 76

    /** Duración del clic: corta, para que no se solape con la pulsación siguiente. */
    const val METRONOME_CLICK_MS: Int = 40

    /** Figura por defecto de una rejilla, cuando la casilla no declara la suya. */
    fun defaultFigure(resolution: TabResolution): RhythmFigure = when (resolution) {
        TabResolution.QUARTER -> RhythmFigure.QUARTER
        TabResolution.EIGHTH -> RhythmFigure.EIGHTH
        TabResolution.SIXTEENTH -> RhythmFigure.SIXTEENTH
    }

    /**
     * Duración en **milisegundos** de una figura a un tempo dado, con la proporción de su **grupo
     * irregular** aplicada (`Tuplet::ratio()`: 2/3 en un tresillo, 4/5 en un quintillo).
     */
    fun durationMs(figure: RhythmFigure, bpm: Int, tuplet: TabTuplet? = null): Long {
        val safeBpm = bpm.coerceIn(20, 400)
        val beats = figure.beatValue * (tuplet?.ratio ?: 1.0)
        return (beats * 60_000.0 / safeBpm).toLong().coerceAtLeast(1L)
    }

    /**
     * Construye la línea de tiempo de [tablature]. [defaultBpm] es el tempo de arranque cuando la
     * pieza no indica ninguno en su primer compás.
     *
     * [range] acota la reproducción a unos compases (el **bucle de rango** de MuseScore): se descartan
     * las ocurrencias fuera del rango **después** de expandir repeticiones y saltos, así que las
     * repeticiones que caen dentro del rango se siguen respetando y el tiempo se **rebasea a cero**.
     * `null` = la pieza entera.
     */
    fun timeline(
        tablature: Tablature,
        defaultBpm: Int = DEFAULT_BPM,
        range: IntRange? = null,
        /**
         * **Metrónomo** (D-66): su subdivisión y su acento. `null` = un clic por pulsación con acento,
         * que es el comportamiento histórico; así las llamadas que no lo pasan no cambian.
         */
        metronome: TabMetronome? = null
    ): TabPlaybackTimeline {
        val subdivision = metronome?.safeSubdivision ?: 1
        val accentFirst = metronome?.accentFirst ?: true
        val order = playOrder(tablature.measures)
            .filter { range == null || it.first in range }
        val events = mutableListOf<TabPlaybackEvent>()
        val clicks = mutableListOf<TabMetronomeClick>()
        var cursorMs = 0L
        var bpm = defaultBpm.coerceIn(20, 400)
        order.forEach { (measureIndex, pass) ->
            val measure = tablature.measures.getOrNull(measureIndex) ?: return@forEach
            // La *volta* solo suena en sus pasadas: la 1.ª vuelta en la primera, la 2.ª en la segunda…
            // y la casilla `1, 2.` en **las dos**, como el `Volta::hasEnding()` de MuseScore.
            if (measure.ending != null && !measure.hasEnding(pass)) {
                return@forEach
            }
            measure.tempo?.let { bpm = it.coerceIn(20, 400) }
            // Pulsaciones del compás: la unidad del **denominador** (negra en 4/4, corchea en 6/8), que
            // es donde MuseScore hace sonar el metrónomo. Se resuelve **por compás** porque la
            // indicación puede cambiar a mitad de pieza: el metrónomo tiene que seguir el compás que
            // suena, no el del encabezado.
            val measureTime = tablature.timeAt(measureIndex)
            val beatUnit = 4.0 / measureTime.beatType.coerceAtLeast(1)
            val beatsPerMeasure = (measureTime.totalQuarterBeats / beatUnit).coerceAtLeast(1.0)
            // Compás **compuesto** (6/8, 9/8, 12/8): el acento cae también en la mitad del compás.
            val compound = measureTime.beatType == 8 &&
                beatsPerMeasure >= 6.0 &&
                beatsPerMeasure % 3.0 == 0.0
            val fallback = defaultFigure(tablature.resolution)
            // Pulsaciones transcurridas **dentro** de este compás, para numerar los clics. Se cuenta en
            // **pulsaciones**, no en negras: en 6/8 una pulsación es una corchea (0,5 negras), y contar
            // en negras solo daba 3 clics donde MuseScore da 6.
            var beatInMeasure = 0.0

            measure.slots.forEachIndexed { slotIndex, slot ->
                val figure = slot.rhythmicFigure ?: fallback
                val slotMs = durationMs(figure, bpm, slot.tuplet)
                val slotBeats = (figure.beatValue * (slot.tuplet?.ratio ?: 1.0)) / beatUnit

                // **Metrónomo**: un clic por cada frontera de **subdivisión** que caiga dentro de la
                // casilla. Se calcula aquí, en la misma pasada que los ataques, para que clic y nota
                // de la misma pulsación compartan milisegundo.
                //
                // (D-66) Con `subdivision > 1` se avanza en fracciones de pulsación (`1/subdivision`),
                // así que en 4/4 con corcheas salen ocho clics por compás. El **acento** solo lo lleva
                // la pulsación entera: si lo llevaran también las subdivisiones, el pulso dejaría de
                // oírse, que es justo lo que el metrónomo tiene que marcar.
                val step = 1.0 / subdivision
                // Se recorre por **índice entero** de subdivisión, no acumulando `beat += step`: con
                // tresillos el acumulado daba `0,9999999…` y el pulso entero dejaba de reconocerse
                // como acento. Con el índice, `index % subdivision == 0` es exacto.
                var index = kotlin.math.ceil(beatInMeasure / step - 1e-9).toInt()
                while (index * step < beatInMeasure + slotBeats - 1e-9) {
                    val beat = index * step
                    val offsetBeats = beat - beatInMeasure
                    val atMs = cursorMs + (offsetBeats / slotBeats * slotMs).toLong()
                    val number = index / subdivision + 1
                    val onBeat = index % subdivision == 0
                    clicks += TabMetronomeClick(
                        measureIndex = measureIndex,
                        beat = number,
                        atMs = atMs,
                        isAccent = accentFirst && onBeat && (
                            number == 1 ||
                                (compound && number == (beatsPerMeasure / 2.0).toInt() + 1)
                            )
                    )
                    index++
                }
                beatInMeasure += slotBeats

                if (slot.isHeld) {
                    // Prolongación: no ataca, alarga lo que ya sonaba.
                    val last = events.lastOrNull()
                    if (last != null) {
                        events[events.lastIndex] = last.copy(durationMs = last.durationMs + slotMs)
                    }
                } else {
                    val notes = slot.strings
                        .filter { !it.isMuted }
                        .filter {
                            !TablatureEditing.isTiedFromPrevious(
                                tablature.measures, measureIndex, slotIndex, it.stringNumber
                            )
                        }
                        .mapNotNull { pluck ->
                            tablature.tuning.pitchOf(pluck.stringNumber)?.midi?.plus(pluck.fret)
                        }
                    if (notes.isNotEmpty()) {
                        events += TabPlaybackEvent(
                            measureIndex = measureIndex,
                            slotIndex = slotIndex,
                            atMs = cursorMs,
                            durationMs = soundLengthMs(
                                tablature.measures, measureIndex, slotIndex, slotMs, bpm, fallback
                            ),
                            midiNotes = notes.distinct()
                        )
                    }
                }
                cursorMs += slotMs
            }
        }
        return TabPlaybackTimeline(events = events, totalMs = cursorMs, clicks = clicks)
    }

    /**
     * Duración **real** del sonido de la casilla `[measureIndex, slotIndex]`: su figura más la de
     * todas las notas ligadas que la prolongan (`tiedToNext`), cruzando compases.
     */
    private fun soundLengthMs(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        slotMs: Long,
        bpm: Int,
        fallback: RhythmFigure
    ): Long {
        var total = slotMs
        var current = measureIndex to slotIndex
        var guard = 0
        while (guard++ < MAX_EXPANDED_MEASURES) {
            val (m, s) = current
            val slot = measures.getOrNull(m)?.slots?.getOrNull(s) ?: break
            val tied = slot.strings.any { it.tiedToNext }
            if (!tied) break
            val target = slot.strings
                .firstOrNull { it.tiedToNext }
                ?.let { TablatureEditing.tieTarget(measures, m, s, it.stringNumber) }
                ?: break
            val targetSlot = measures.getOrNull(target.first)?.slots?.getOrNull(target.second) ?: break
            total += durationMs(targetSlot.rhythmicFigure ?: fallback, bpm, targetSlot.tuplet)
            current = target
        }
        return total
    }

    /**
     * **Orden real de ejecución** de la pieza: repeticiones ([expandRepeats]) **más** los saltos
     * (`D.C.`, `D.S.`, `al Fine`, `al Coda`).
     *
     * Replica las reglas de `RepeatList::unwind` de MuseScore
     * (`src/engraving/dom/repeatlist.cpp`):
     *
     *  - Un salto se honra **solo en la última pasada** del compás que lo lleva
     *    (`playbackCount >= startRepeatReference->getRepeatCount()`), es decir, después de agotar sus
     *    repeticiones.
     *  - Cada salto se toma **una sola vez** (`m_jumpsTaken`), pero se **encadenan**: si la pasada
     *    del salto encuentra otro salto distinto (por ejemplo un `D.C.` dentro de la `Coda`), también
     *    se honra. Antes se resolvía **un** salto por pieza y el resto se ignoraba.
     *  - El compás del salto **suena** antes de saltar: el `JUMP` se procesa después de añadir el
     *    compás al segmento.
     *  - El marcador de *playUntil* (`Fine` / `To Coda`) **suena** y después se salta a *continueAt*
     *    (la `Coda`) o se termina la pieza.
     *  - En la pasada del salto las repeticiones **no** se repiten (`forceFinalRepeat`): se toca la
     *    **última** casilla de *volta*, que es lo que consigue emitir la pasada máxima.
     *
     * Si no hay saltos, devuelve exactamente [expandRepeats].
     */
    fun playOrder(measures: List<TabMeasure>): List<Pair<Int, Int>> {
        val base = expandRepeats(measures)
        if (base.isEmpty()) return base
        // En la pasada del salto las repeticiones no se repiten (`forceFinalRepeat`): se emite la
        // pasada **máxima**, que es la que hace sonar la última casilla de *volta*.
        val finalPass = base.maxOf { it.second }
        // Compases cuyo salto ya se ha tomado: MuseScore no los repite (`m_jumpsTaken`).
        val taken = mutableSetOf<Int>()
        val result = mutableListOf<Pair<Int, Int>>()
        var segment = base
        var guard = 0
        while (guard++ < MAX_EXPANDED_MEASURES) {
            // Primer salto **sin tomar**, en orden de recorrido.
            val jumpIndex = segment
                .firstOrNull { (m, _) -> measures[m].jump != null && m !in taken }
                ?.first
            if (jumpIndex == null) {
                result += segment
                break
            }
            // Se toma en la **última** pasada del compás, no en la primera: si el compás está dentro
            // de una repetición, la ocurrencia que salta es la de la pasada final.
            val at = segment.indexOfLast { it.first == jumpIndex }
            val jump = measures[jumpIndex].jump ?: break
            taken += jumpIndex
            val target = jumpTarget(measures, jump)
            if (target < 0) continue // destino inexistente (p. ej. `D.S.` sin `Segno`): se ignora
            // El compás del salto **suena** antes de saltar.
            result += segment.subList(0, at + 1)
            val stopAt = playUntilOf(measures, jump)
            val continueAt = continueAtOf(measures, jump)
            val end = if (stopAt >= target) stopAt else measures.lastIndex
            val next = mutableListOf<Pair<Int, Int>>()
            for (i in target..end) next += i to finalPass
            if (continueAt >= 0) {
                for (i in continueAt..measures.lastIndex) next += i to finalPass
            }
            segment = next
        }
        return result
    }

    /** Compás al que salta [jump]; `-1` si el destino no existe (p. ej. un `D.S.` sin `Segno`). */
    private fun jumpTarget(measures: List<TabMeasure>, jump: TabJump): Int = when (jump) {
        TabJump.DC, TabJump.DC_AL_FINE, TabJump.DC_AL_CODA -> 0
        TabJump.DS, TabJump.DS_AL_FINE, TabJump.DS_AL_CODA ->
            measures.indexOfFirst { it.navigation == TabNavigationMark.SEGNO }
    }

    /** Compás en el que **termina** la pasada del salto (`Fine` / `To Coda`); `-1` si no hay. */
    private fun playUntilOf(measures: List<TabMeasure>, jump: TabJump): Int {
        val mark = when (jump) {
            TabJump.DC_AL_FINE, TabJump.DS_AL_FINE -> TabNavigationMark.FINE
            TabJump.DC_AL_CODA, TabJump.DS_AL_CODA -> TabNavigationMark.TO_CODA
            TabJump.DC, TabJump.DS -> return -1
        }
        return measures.indexOfFirst { it.navigation == mark }
    }

    /** Compás al que se salta desde el `To Coda` (la `Coda`); `-1` si no hay. */
    private fun continueAtOf(measures: List<TabMeasure>, jump: TabJump): Int = when (jump) {
        TabJump.DC_AL_CODA, TabJump.DS_AL_CODA ->
            measures.indexOfFirst { it.navigation == TabNavigationMark.CODA }
        else -> -1
    }

    /**
     * Expande las **repeticiones** en el orden real de ejecución: una lista de
     * `(índiceDeCompás, númeroDePasada)`. La pasada empieza en 1 y sube en cada vuelta, que es lo que
     * permite decidir qué *volta* suena.
     */
    fun expandRepeats(measures: List<TabMeasure>): List<Pair<Int, Int>> {
        if (measures.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Int, Int>>()
        var pass = 1
        var index = 0
        var sectionStart = 0
        var guard = 0
        while (index < measures.size && guard++ < MAX_EXPANDED_MEASURES) {
            val measure = measures[index]
            if (measure.repeat == TabRepeatMark.START || measure.repeat == TabRepeatMark.BOTH) {
                sectionStart = index
            }
            result += index to pass
            val closes = measure.repeat == TabRepeatMark.END || measure.repeat == TabRepeatMark.BOTH
            if (closes) {
                val times = (measure.repeatCount ?: 2).coerceIn(1, 16)
                if (pass < times) {
                    pass += 1
                    index = sectionStart
                    continue
                }
                // Sección agotada: la siguiente empieza después y vuelve a la pasada 1.
                pass = 1
                sectionStart = index + 1
            }
            index += 1
        }
        return result
    }
}
