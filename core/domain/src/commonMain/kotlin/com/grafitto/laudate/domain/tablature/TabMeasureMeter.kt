package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot

/**
 * **Métrica y validación de compases de tablatura** — port fiel de la *inteligencia de negocio* de
 * **TuxGuitar** (`app.tuxguitar.song.helpers.TGMeasureError` +
 * `TGMeasureManager.getMeasureErrors` + `TGDuration.splitPreciseDuration`), reimplementada en
 * Kotlin puro.
 *
 * La idea, que es la que faltaba en Canticora: **un compás es una duración que hay que completar**.
 * Un compás `4/4` dura 4 negras; si el usuario escribe 3, **falta 1** y hay que **rellenarla con
 * silencios** (no dejar el compás «corto»). Y un compás puede ser **inválido** de cinco maneras
 * distintas, exactamente las de TuxGuitar:
 *
 * | Código (TuxGuitar) | Significado | Aquí |
 * | :--- | :--- | :--- |
 * | `VOICE_DISCONTINUOUS` | hay un **hueco** entre notas/silencios | `DISCONTINUOUS` |
 * | `VOICE_OVERLAP` | dos notas se **solapan** | `OVERLAP` |
 * | `VOICE_EARLY_START` | la primera empieza **antes** del compás | `EARLY_START` |
 * | `VOICE_TOO_SHORT` | el compás **acaba antes** de su final | `TOO_SHORT` |
 * | `VOICE_TOO_LONG` | el compás **se pasa** de su final | `TOO_LONG` |
 *
 * Además del diagnóstico, porta dos operaciones de TuxGuitar:
 *  - **`splitPreciseDuration`** → [splitIntoRests]: parte un hueco en las figuras que lo llenan
 *    («pocas y grandes»: 2,5 negras = blanca + corchea, no cinco corcheas).
 *  - **`TGMeasureManager.addNote`** → [fillWithRests]: rellena el hueco con los silencios correctos.
 *
 * Kotlin puro y determinista: lo comparten el editor, el visor y el PDF.
 */
object TabMeasureMeter {

    /** Tolerancia al comparar duraciones (negras), para no fallar por coma flotante. */
    private const val EPSILON = 1e-6

    /** Silencios disponibles, de **mayor a menor** duración (como `durationWidths` de TuxGuitar). */
    private val REST_CHOICES: List<RhythmFigure> = listOf(
        RhythmFigure.WHOLE_REST,
        RhythmFigure.DOTTED_HALF_REST,
        RhythmFigure.HALF_REST,
        RhythmFigure.DOTTED_QUARTER_REST,
        RhythmFigure.QUARTER_REST,
        RhythmFigure.DOTTED_EIGHTH_REST,
        RhythmFigure.EIGHTH_REST,
        RhythmFigure.SIXTEENTH_REST
    )

    // ── Códigos de error: port 1:1 de `TGMeasureError` ────────────────────────────────────────────
    /**
     * **Errores de duración** de un compás, como los `int` de `TGMeasureError`. Se combinan con `or`
     * (es un campo de bits en TuxGuitar y aquí también, para conservar la semántica).
     */
    enum class Error(val code: Int) {
        OK(0),

        /** Hay un **hueco** entre dos notas/silencios de la voz (`VOICE_DISCONTINUOUS`). */
        DISCONTINUOUS(0x01),

        /** Dos notas se **solapan** (`VOICE_OVERLAP`). */
        OVERLAP(0x02),

        /** La primera nota empieza **antes** del compás (`VOICE_EARLY_START`). */
        EARLY_START(0x04),

        /** El compás **acaba antes** de su final (`VOICE_TOO_SHORT`). */
        TOO_SHORT(0x08),

        /** El compás **se pasa** de su final (`VOICE_TOO_LONG`). */
        TOO_LONG(0x10);

        companion object {
            /** Todos los errores presentes en una máscara de bits. */
            fun of(mask: Int): Set<Error> =
                entries.filter { it.code != 0 && (mask and it.code) != 0 }.toSet()
        }
    }

    /**
     * **Duración en negras** de una columna: su figura (o la de la resolución) escalada por la
     * proporción de su **grupo irregular** (`Tuplet::ratio()`: 2/3 en un tresillo, 4/5 en un quintillo).
     */
    fun beatsOf(slot: TabSlot, resolution: TabResolution): Double {
        val base = slot.rhythmicFigure?.beatValue ?: TabSpacing.defaultFigure(resolution).beatValue
        return base * (slot.tuplet?.ratio ?: 1.0)
    }

    /** **Duración real en negras** de un compás (suma de las duraciones de sus columnas). */
    fun durationOf(measure: TabMeasure, resolution: TabResolution): Double =
        measure.slots.sumOf { beatsOf(it, resolution) }

    /** Duración **objetivo** del compás, en negras (`4/4` = 4,0). */
    fun expectedBeats(time: ScoreTimeSignature): Double = time.totalQuarterBeats

    /**
     * **Diagnóstico completo** de un compás: los errores de `TGMeasureError` **y** su duración.
     *
     * En Canticora la voz es **única** (un `strings` por columna) y las columnas van en secuencia, así
     * que `DISCONTINUOUS`/`OVERLAP`/`EARLY_START` **no pueden** darse hoy (se dejan modelados por
     * fidelidad al port). Los que importan son `TOO_SHORT` (falta rellenar) y `TOO_LONG` (se pasa).
     */
    data class Diagnosis(
        val actualBeats: Double,
        val expectedBeats: Double,
        val errors: Set<Error>
    ) {
        val isOk: Boolean get() = errors.isEmpty()

        /** **Falta** duración (acaba antes del final del compás): hay que rellenar con silencios. */
        val isTooShort: Boolean get() = Error.TOO_SHORT in errors

        /** **Se pasa** de duración: error estructural, no se toca. */
        val isTooLong: Boolean get() = Error.TOO_LONG in errors

        /** Hueco por rellenar (0 si no falta). */
        val missingBeats: Double get() = (expectedBeats - actualBeats).coerceAtLeast(0.0)

        /** Exceso de duración (0 si no sobra). */
        val excessBeats: Double get() = (actualBeats - expectedBeats).coerceAtLeast(0.0)

        /** El compás **está vacío**: se interpreta como un silencio completo (válido). */
        val isEmpty: Boolean get() = actualBeats < EPSILON
    }

    /** Diagnóstico de un compás (port de `TGMeasureManager.getMeasureErrors`). */
    fun diagnose(
        measure: TabMeasure,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): Diagnosis {
        val expected = expectedBeats(time)
        val actual = durationOf(measure, resolution)
        var mask = 0
        // Una sola voz contigua: no hay huecos ni solapes; solo importa el final. Las columnas van en
        // secuencia, así que `EARLY_START`, `DISCONTINUOUS` y `OVERLAP` quedan a 0 (como en TuxGuitar
        // cuando la voz es contigua).
        when {
            // La **anacrusa** es corta **a propósito**: no es un compás al que le falte relleno, es el
            // compás incompleto con el que arranca la pieza. Marcarla como `TOO_SHORT` llenaría la
            // barra de estado de avisos rojos en un documento correcto. Pasarse de largo **sí** es un
            // error, también en la anacrusa.
            // El compás **irregular** dura lo que el usuario decidió: ni corto ni largo son errores.
            measure.isIrregular -> Unit
            actual > expected + EPSILON -> mask = mask or Error.TOO_LONG.code
            measure.isPickup -> Unit
            actual < expected - EPSILON -> mask = mask or Error.TOO_SHORT.code
        }
        return Diagnosis(actual, expected, Error.of(mask))
    }

    /** `true` si la **duración** del compás es válida (`TGMeasureManager.isMeasureDurationValid`). */
    fun isDurationValid(
        measure: TabMeasure,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): Boolean = !diagnose(measure, time, resolution).let { it.isTooShort || it.isTooLong }

    /**
     * **Parte un hueco en las figuras que lo llenan** (port simplificado de
     * `TGDuration.splitPreciseDuration` con `preferredDuration = null`): usa siempre la **figura más
     * grande que quepa**, que es lo que hace TuxGuitar al rellenar («pocas y grandes»).
     *
     * Ejemplo: 2,5 negras → `[blanca (2), corchea (0,5)]`, nunca cinco corcheas.
     */
    fun splitIntoRests(beats: Double, startBeats: Double = 0.0): List<RhythmFigure> {
        if (beats <= EPSILON) return emptyList()
        val result = mutableListOf<RhythmFigure>()
        var remaining = beats
        var position = startBeats
        var guard = 0
        while (remaining > EPSILON && guard++ < 64) {
            val pick = REST_CHOICES.firstOrNull {
                it.beatValue <= remaining + EPSILON && isAlignedToBeat(position, it.beatValue)
            } ?: REST_CHOICES.firstOrNull { it.beatValue <= remaining + EPSILON } ?: break
            result += pick
            position += pick.beatValue
            remaining -= pick.beatValue
        }
        return result
    }

    /**
     * `true` si un silencio de [length] negras puede **empezar** en [position]: su duración divide
     * a la posición.
     *
     * Es la agrupación de silencios de MuseScore: un silencio de blanca (2) empieza en 0, 2, 4…,
     * nunca en 1; uno de negra (1) en cualquier pulso; uno de corchea (0,5) en cualquier mitad.
     *
     * (2026-10-27) Antes se elegía siempre la figura **mayor** que cupiera, y un hueco de 3 negras
     * detrás de una negra en el pulso 1 salía como `blanca con puntillo + corchea`: un silencio que
     * **cruza el pulso 2** y que así no se escribe. El usuario lo describió como «salen silencios a
     * lo loco».
     */
    private fun isAlignedToBeat(position: Double, length: Double): Boolean {
        if (length <= EPSILON) return false
        val steps = position / length
        return kotlin.math.abs(steps - kotlin.math.round(steps)) < 1e-6
    }

    /**
     * **Rellena con silencios** el hueco que falta hasta completar el compás, **al final** (port de
     * `TGMeasureManager.addNote`: se añade al final de la voz, `start = último final`).
     *
     * Si el compás está completo, se pasa (`TOO_LONG`) o está vacío, devuelve el compás **sin tocar**.
     */
    fun fillWithRests(
        measure: TabMeasure,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): TabMeasure {
        // La **anacrusa** y el compás **irregular** no se rellenan: su duración es una decisión, no un
        // compás a medias.
        if (measure.hasFreeDuration) return measure
        val diagnosis = diagnose(measure, time, resolution)
        if (!diagnosis.isTooShort) return measure
        val rests = splitIntoRests(diagnosis.missingBeats)
        if (rests.isEmpty()) return measure
        return measure.copy(slots = measure.slots + rests.map { TabSlot(rhythmicFigure = it) })
    }

    /**
     * **Completa todos los compases** de un documento (lo que se ejecuta al cambiar el compás, al
     * añadir compases o al pedirlo el usuario). Devuelve la lista ya completada.
     */
    fun fillAll(
        measures: List<TabMeasure>,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): List<TabMeasure> = measures.map { fillWithRests(it, time, resolution) }

    /**
     * **Compás nuevo completo**: un compás recién creado **nunca** debe quedar «corto», así que se
     * rellena con su silencio de compás (la figura mayor que quepa). Port de `TGMeasureManager`
     * cuando se añade un compás.
     */
    fun emptyCompleteMeasure(time: ScoreTimeSignature, resolution: TabResolution): TabMeasure =
        TabMeasure(slots = splitIntoRests(expectedBeats(time)).map { TabSlot(rhythmicFigure = it) })

    /**
     * **Inserta un silencio explícito** de la figura indicada en el compás, en la posición de columna
     * indicada (o al final si [slotIndex] es `null`).
     *
     * (D-83) **Sustituye, no acumula.** Antes el silencio se *añadía* a la lista de columnas, así que
     * poner un silencio de redonda en un compás `4/4` —que ya nace con cuatro silencios de negra, una
     * columna por pulso— dejaba **cinco** columnas: los cuatro silencios predefinidos seguían ahí y el
     * compás se pasaba de duración. En MuseScore un silencio es un **elemento de tiempo**, igual que
     * una nota: ocupa el pulso del cursor y desplaza a los que vienen después.
     *
     * Ahora se quitan las columnas que caen dentro de la duración del silencio —solo si están **vacías**
     * (sin notas ni silencios explícitos del usuario), para no borrar música por accidente— y las que
     * se pisan parcialmente se recortan con un silencio de la figura que sí quepa. Así la suma de
     * columnas sigue siendo la del compás y no aparecen silencios de más.
     */
    fun insertRest(
        measure: TabMeasure,
        figure: RhythmFigure,
        slotIndex: Int? = null,
        resolution: TabResolution? = null
    ): TabMeasure {
        val rest = TabSlot(
            rhythmicFigure = if (figure.isRest) figure
            else (RhythmFigure.restEquivalentOf(figure) ?: RhythmFigure.QUARTER_REST)
        )
        val duration = rest.rhythmicFigure?.beatValue ?: RhythmFigure.QUARTER_REST.beatValue
        val at = (slotIndex ?: measure.slots.size).coerceIn(0, measure.slots.size)
        // Se recorren las columnas desde [at] consumiendo la duración del silencio. Las que caen dentro
        // y están **vacías** se retiran; la que se pisa a medias se sustituye por el silencio que
        // complete lo que falte, y el resto del compás se queda como estaba.
        val slots = measure.slots.toMutableList()
        var remaining = duration
        var index = at
        while (remaining > EPSILON && index < slots.size) {
            val slot = slots[index]
            val slotBeats = beatsOf(slot, resolution ?: TabResolution.EIGHTH)
            val hasContent = slot.strings.isNotEmpty() || slot.isHeld
            if (hasContent) break
            if (slotBeats <= remaining + EPSILON) {
                slots.removeAt(index)
                remaining -= slotBeats
            } else {
                // La columna dura más que lo que falta: se deja un silencio con el hueco sobrante.
                val leftover = slotBeats - remaining
                slots[index] = TabSlot(rhythmicFigure = largestRestFor(leftover))
                remaining = 0.0
            }
        }
        slots.add(at.coerceAtMost(slots.size), rest)
        return measure.copy(slots = slots)
    }

    /** La **figura de silencio mayor** que cabe en [beats] (al menos una semicorchea). */
    private fun largestRestFor(beats: Double): RhythmFigure =
        REST_CHOICES.firstOrNull { it.beatValue <= beats + EPSILON } ?: RhythmFigure.SIXTEENTH_REST

    /** `true` si el compás **solo tiene silencios** (no hay ninguna nota ni nota mantenida). */
    fun isAllRests(measure: TabMeasure): Boolean = measure.slots.isNotEmpty() &&
        measure.slots.all { it.strings.isEmpty() && !it.isHeld }

    /**
     * `true` si el compás es un **silencio de compás**: no tiene ninguna nota y **todas** sus
     * columnas son el silencio de relleno de la subdivisión [resolution] —la rejilla de trabajo que
     * crea el editor—, sin tresillos ni figuras elegidas a mano.
     *
     * Es el caso que MuseScore pinta con **un único** silencio de compás centrado (una redonda),
     * no con una fila de silencios: un `4/4` vacío muestra **una** redonda, no ocho corcheas. El
     * editor necesita la rejilla para saber **dónde** cae un clic
     * ([TablatureEditing.slotsPerMeasure]), pero eso es una ayuda de escritura, no música. Antes se
     * dibujaba tal cual y al abrir el editor aparecían **dieciséis** silencios que nadie había
     * escrito («salen silencios a lo loco», 2026-10-27).
     */
    fun isMeasureRest(measure: TabMeasure, resolution: TabResolution): Boolean {
        if (measure.slots.isEmpty()) return true
        val filler = TablatureEditing.restFigureForResolution(resolution)
        return measure.slots.all { slot ->
            slot.strings.isEmpty() && !slot.isHeld && slot.tuplet == null &&
                slot.rhythmicFigure == filler
        }
    }
}
