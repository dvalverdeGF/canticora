package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import com.grafitto.laudate.domain.model.tablature.Tablature

/**
 * Traste máximo que admite el editor: una guitarra de 24 trastes. El traste se escribe con el
 * teclado o con el campo numérico, así que el rango es completo (no hay que "cambiar de octava"
 * como en los teclados virtuales que sólo muestran 0–12).
 */
const val MAX_FRET = 24

/**
 * Alcance máximo (en columnas) del **apagado de palma** (`P.M.`): más allá de un compás de 4/4 en
 * corcheas no tiene sentido y el control de la paleta se vuelve inmanejable.
 */
const val MAX_PALM_MUTE_SPAN = 8

/**
 * Utilidades **puras** de edición de la tablatura por **columnas** (ADR-017, *Feature T*).
 *
 * La unidad de edición es la **columna** (evento), no la casilla fija: escribir una nota **crea** una
 * columna nueva y desplaza lo que venga después, como en los editores de referencia (My Guitar
 * Tabs). Así «la nota siguiente» de un ligado es la columna contigua, sin casillas vacías de relleno.
 * [ScoreTimeSignature] y [TabResolution] quedan como **metadatos** de lectura.
 */
object TablatureEditing {

    /** Figuras que dura un tresillo (3:2): tres, en el tiempo de dos. */
    const val TRIPLET_GROUP_SIZE: Int = 3

    /**
     * Nº de columnas que **llenan** un compás para un compás y una resolución dados. Es una guía de
     * lectura (y para el PDF); la rejilla real tiene las columnas que el usuario haya escrito.
     */
    fun slotsPerMeasure(time: ScoreTimeSignature, resolution: TabResolution): Int {
        val perQuarter = when (resolution) {
            TabResolution.QUARTER -> 1.0
            TabResolution.EIGHTH -> 2.0
            TabResolution.SIXTEENTH -> 4.0
        }
        return kotlin.math.round(time.totalQuarterBeats * perQuarter).toInt().coerceAtLeast(1)
    }

    /**
     * Crea `measureCount` **compases vacíos**. Con el modelo de columnas un compás puede nacer **vacío**
     * (0 columnas): la primera nota se escribe insertando su columna. `withGuideColumns = true`
     * rellena con las columnas guía para documentos creados a partir de una plantilla.
     */
    fun emptyMeasures(
        time: ScoreTimeSignature,
        resolution: TabResolution,
        measureCount: Int = 1,
        withGuideColumns: Boolean = false
    ): List<TabMeasure> {
        val perMeasure = if (withGuideColumns) slotsPerMeasure(time, resolution) else 0
        return List(measureCount.coerceAtLeast(1)) { TabMeasure(List(perMeasure) { TabSlot() }) }
    }

    /**
     * Un compás **nuevo y completo**, con la **rejilla predefinida**: nace con **una columna por
     * unidad de la subdivisión** ([resolution]) y cada columna lleva el **silencio** de esa figura.
     *
     * (2026-10-15, punto 4 del usuario) Antes nacía con **una sola columna** que duraba el compás
     * entero (un silencio de redonda en `4/4`). Eso hacía que el compás tuviera **una única casilla**
     * —el recuadro del cursor abarcaba todo el compás— y que no se pudiera escribir «en el segundo
     * pulso»: al teclear, la nota se iba a la misma `x` que la anterior. El usuario lo describió como
     * «no están predefinidas las columnas del primer compás».
     *
     * Ahora un `4/4` en negras nace con **4 columnas** (4 silencios de negra), en corcheas con **8**,
     * en semicorcheas con **16**. Es la rejilla de trabajo: se ve dónde va cada pulso y se escribe
     * encima. Como en TuxGuitar, el compás **siempre está completo** (la suma de las columnas es la
     * duración del compás), así que no hay que «completar» nada.
     */
    fun emptyCompleteMeasure(
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): TabMeasure {
        val rest = restFigureForResolution(resolution)
        val count = slotsPerMeasure(time, resolution)
        return TabMeasure(slots = List(count) { TabSlot(rhythmicFigure = rest) })
    }

    /** **Silencio** que dura una unidad de la subdivisión [resolution] (negra, corchea, semicorchea). */
    fun restFigureForResolution(resolution: TabResolution): RhythmFigure = when (resolution) {
        TabResolution.QUARTER -> RhythmFigure.QUARTER_REST
        TabResolution.EIGHTH -> RhythmFigure.EIGHTH_REST
        TabResolution.SIXTEENTH -> RhythmFigure.SIXTEENTH_REST
    }

    /** **Figura sonora** que dura una unidad de la subdivisión [resolution]. */
    fun figureForResolution(resolution: TabResolution): RhythmFigure = when (resolution) {
        TabResolution.QUARTER -> RhythmFigure.QUARTER
        TabResolution.EIGHTH -> RhythmFigure.EIGHTH
        TabResolution.SIXTEENTH -> RhythmFigure.SIXTEENTH
    }

    /**
     * **Subdivisión** con la que está escrito un compás, **derivada** de sus columnas de silencio.
     *
     * No se guarda en el modelo: la subdivisión es la **rejilla de trabajo** y queda implícita en las
     * columnas. Se lee de la **primera columna de silencio** (la que define el paso de la rejilla); si
     * el compás no tiene silencios (está lleno de notas) se devuelve [fallback], que es la resolución
     * del documento.
     */
    fun subdivisionOf(measure: TabMeasure, fallback: TabResolution): TabResolution {
        val rest = measure.slots.firstOrNull { it.strings.isEmpty() && !it.isHeld }
            ?.rhythmicFigure ?: return fallback
        return when (rest.beatValue) {
            1.0 -> TabResolution.QUARTER
            0.5 -> TabResolution.EIGHTH
            0.25 -> TabResolution.SIXTEENTH
            else -> fallback
        }
    }

    /**
     * **Re-reparte los silencios** de un compás a la subdivisión [resolution], **conservando las
     * notas** (punto 5 del usuario: «no tengo como cambiar si el compás va a negras o a corcheas»).
     *
     * Recorre las **rachas contiguas de silencios** y sustituye cada una por tantas columnas de la
     * figura de la subdivisión como quepan; si la racha no es un múltiplo exacto, se parte con
     * [TabMeasureMeter.splitIntoRests] (la figura mayor que quepa) para no perder duración. Las
     * columnas con notas o notas mantenidas se dejan **intactas**.
     *
     * Ejemplo: un `4/4` con 2 negras y 2 silencios de negra, repartido en corcheas, queda con las 2
     * negras y **4 silencios de corchea**.
     */
    fun relayoutRests(
        measure: TabMeasure,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): TabMeasure {
        val unit = figureForResolution(resolution).beatValue
        val rest = restFigureForResolution(resolution)
        val out = mutableListOf<TabSlot>()
        var run = mutableListOf<TabSlot>()

        fun flush() {
            if (run.isEmpty()) return
            // Los silencios nuevos heredan la **voz** de la racha: re-repartir la rejilla no puede
            // cambiar de voz lo que el usuario escribió.
            val voice = run.first().voice
            val beats = run.sumOf { it.rhythmicFigure?.beatValue ?: unit }
            val exact = kotlin.math.round(beats / unit).toInt()
            if (exact >= 1 && kotlin.math.abs(exact * unit - beats) < 1e-6) {
                repeat(exact) { out += TabSlot(rhythmicFigure = rest, voice = voice) }
            } else {
                TabMeasureMeter.splitIntoRests(beats).forEach {
                    out += TabSlot(rhythmicFigure = it, voice = voice)
                }
            }
            run = mutableListOf()
        }

        measure.slots.forEach { slot ->
            if (slot.strings.isEmpty() && !slot.isHeld) run += slot else { flush(); out += slot }
        }
        flush()
        return rebalance(measure.copy(slots = out), time, resolution)
    }

    /**
     * **Reequilibra** el compás para que dure **exactamente** lo que dura el compás ([time]).
     *
     * Conserva **todas** las columnas hasta la **última que aporta algo** —una nota, una nota
     * mantenida o un silencio con una figura **distinta** de la de la subdivisión (una elección
     * explícita del usuario)— y **descarta los silencios de relleno que van detrás**, volviéndolos a
     * repartir con la figura de la subdivisión [resolution] —o con [TabMeasureMeter.splitIntoRests]
     * si el hueco no es un múltiplo exacto—. Así el compás **nunca se queda corto ni se pasa** al
     * escribir: es la regla de TuxGuitar («el compás siempre está completo»).
     *
     * Ejemplo: en un `4/4` con rejilla de corcheas, escribir una **negra** en la primera columna deja
     * `[negra, silencio de negra ×3]` (4 columnas, 4 negras), no `[negra, 7 silencios de corchea]`
     * (4,5 negras, compás pasado).
     *
     * [fillFigure] es la **figura activa** de la barra de escritura: si el hueco que queda es un
     * múltiplo exacto de ella, los silencios de relleno la usan (escribir negras deja silencios de
     * negra). Si es `null` o no encaja, se usa la figura de la subdivisión [resolution].
     */
    fun rebalance(
        measure: TabMeasure,
        time: ScoreTimeSignature,
        resolution: TabResolution,
        fillFigure: RhythmFigure? = null,
        /**
         * `true` (por defecto) recorta las columnas que sobran para que el compás no pase de su
         * duración (D-91). Se pone `false` al **desmarcar un compás irregular**, donde D-73 fija que
         * el exceso se **reporte** para que el usuario lo arregle en vez de borrarle música.
         */
        trimExcess: Boolean = true
    ): TabMeasure {
        val target = time.totalQuarterBeats
        val unit = figureForResolution(resolution).beatValue
        val rest = restFigureForResolution(resolution)
        // Se conservan las columnas hasta la última que **aporta algo**: una nota, una nota mantenida
        // o un silencio con una figura **distinta** de la de la subdivisión (una elección explícita
        // del usuario, p. ej. al pulsar «negra» con el cursor sobre una casilla vacía). Los silencios
        // «de relleno» que van detrás se descartan y se vuelven a repartir.
        val lastContent = measure.slots.indexOfLast { it.strings.isNotEmpty() || it.isHeld }
        val lastExplicit = measure.slots.indexOfLast { it.rhythmicFigure != null && it.rhythmicFigure != rest }
        val last = maxOf(lastContent, lastExplicit)
        // La **voz** del compás: los silencios de relleno la heredan para no perder la polifonía al
        // escribir (escribir en la voz 2 no puede devolver el compás a la voz 1).
        val voice = voiceOf(measure)
        if (last < 0) {
            // Compás sin notas ni elecciones explícitas: la rejilla completa de la subdivisión.
            return measure.copy(
                slots = List(slotsPerMeasure(time, resolution)) {
                    TabSlot(rhythmicFigure = rest, voice = voice)
                }
            )
        }
        val kept = measure.slots.subList(0, last + 1).toMutableList()
        // (D-91) **El compás no puede pasarse de duración.** `rebalance` solo rellenaba el hueco que
        // faltaba: si lo escrito duraba **más** que el compás, lo dejaba tal cual, así que el compás
        // crecía sin límite y cabían decenas de negras en un `4/4` —«no veas qué cantidad de negras
        // caben en un compás 4/4». `diagnose` lo detectaba (`TOO_LONG`) pero nadie actuaba.
        //
        // Se recortan las columnas **sobrantes por la cola**. Una nota nunca se parte (no se puede
        // acortar sin cambiar lo que suena): se conserva mientras quepa y, si no cabe, se descarta
        // junto con lo que venga detrás, rellenando el hueco con silencios. La **anacrusa** y el
        // compás **irregular** duran lo que el usuario decidió y no se tocan.
        if (trimExcess && !measure.isPickup && !measure.isIrregular) {
            while (kept.size > 1) {
                val total = kept.sumOf { TabMeasureMeter.beatsOf(it, resolution) }
                if (total <= target + 1e-6) break
                kept.removeAt(kept.lastIndex)
            }
        }
        val used = kept.sumOf { TabMeasureMeter.beatsOf(it, resolution) }
        val missing = target - used
        if (missing > 1e-6) {
            // Particion **alineada al pulso**, como MuseScore (2026-10-27).
            TabMeasureMeter.splitIntoRests(missing, startBeats = used).forEach {
                kept += TabSlot(rhythmicFigure = it, voice = voice)
            }
        }
        return measure.copy(slots = kept)
    }

    /** [rebalance] sobre el compás [measureIndex] de un documento. */
    fun rebalanceMeasure(
        measures: List<TabMeasure>,
        measureIndex: Int,
        time: ScoreTimeSignature,
        resolution: TabResolution,
        fillFigure: RhythmFigure? = null
    ): List<TabMeasure> {
        val measure = measures.getOrNull(measureIndex) ?: return measures
        val balanced = rebalance(measure, time, resolution, fillFigure)
        if (balanced == measure) return measures
        return measures.toMutableList().also { it[measureIndex] = balanced }
    }

    /**
     * Cambia el compás o la resolución. Con el modelo de columnas **no** redimensiona la rejilla
     * (las columnas son libres); solo garantiza que exista al menos un compás.
     */
    fun resize(
        measures: List<TabMeasure>,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): List<TabMeasure> = measures.ifEmpty { listOf(TabMeasure()) }

    /**
     * **Inserta una columna** (evento) en el compás [measureIndex] a la altura [slotIndex]. Si el
     * índice está al final, la añade; si está en medio, **desplaza** lo que venga después. Es lo que
     * hace `Intro` en My Guitar Tabs: crear el «acorde» donde irá la nota.
     *
     * Si la columna se inserta **más allá del final** del compás, se añaden columnas vacías de
     * relleno hasta esa posición (así se puede escribir «al final» sin contar).
     */
    fun insertColumn(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int
    ): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val measure = measures[measureIndex]
        val slots = measure.slots.toMutableList()
        // El índice puede estar **más allá del final** (escribir «al final» sin contar columnas):
        // se rellena con columnas vacías hasta esa posición y luego se inserta la nueva.
        val at = slotIndex.coerceAtLeast(0)
        while (slots.size < at) slots += TabSlot()
        slots.add(at.coerceAtMost(slots.size), TabSlot())
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /**
     * **Parte en dos** la columna [slotIndex] del compás [measureIndex] para abrir un hueco donde
     * escribir **sin que el compás se pase** de su duración.
     *
     * Es la respuesta de MuseScore a «quiero dos notas donde había una»: la columna se divide en dos
     * de la mitad y el compás sigue durando exactamente lo mismo. Si la columna lleva una nota, la
     * nota **se acorta** a la mitad y el hueco que deja es un silencio (una negra pasa a corchea +
     * silencio de corchea); si era un silencio, quedan dos silencios de la mitad.
     *
     * Devuelve `null` cuando la columna **no se puede partir**: ya es la figura más corta, pertenece
     * a un grupo irregular o es una prolongación (`isHeld`). En ese caso el llamante decide qué hacer
     * (saltar al compás siguiente).
     */
    fun splitSlot(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        resolution: TabResolution
    ): List<TabMeasure>? {
        val measure = measures.getOrNull(measureIndex) ?: return null
        val slot = measure.slots.getOrNull(slotIndex) ?: return null
        if (slot.isHeld || slot.tuplet != null) return null
        val figure = slot.rhythmicFigure ?: restFigureForResolution(resolution)
        val half = figure.beatValue / 2.0
        val halfRest = RhythmFigure.rests.firstOrNull { kotlin.math.abs(it.beatValue - half) < 1e-6 }
            ?: return null
        val slots = measure.slots.toMutableList()
        slots[slotIndex] = slot.copy(rhythmicFigure = halfRest)
        slots.add(slotIndex + 1, TabSlot(rhythmicFigure = halfRest, voice = slot.voice))
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /**
     * **Parte una columna de silencio** para que la nota que se escribe ocupe **su propia duración**
     * (D-93).
     *
     * Un compás `4/4` recién creado nace con **una** columna de silencio de redonda
     * ([TabMeasureMeter.emptyCompleteMeasure] → `splitIntoRests(4.0)` da `[WHOLE_REST]`). Al escribir
     * una negra encima, `setFret` metía la nota **en esa misma columna**, que seguía siendo una
     * redonda: el compás duraba lo mismo pero la negra **duraba cuatro pulsos**, y el silencio que
     * «sobraba» no existía como columna. El usuario lo describió como «vas desplazando los silencios
     * en lugar de sustituirlos».
     *
     * Lo correcto —y lo que hace MuseScore— es: la columna del cursor **se queda con la figura de la
     * nota** ([figure]) y su **resto** (la redonda menos la negra) pasa a **una columna nueva**
     * detrás, repartido en las figuras de silencio mayores que quepan. Si la nota dura más que la
     * columna, no se parte (no cabe) y se devuelve `null`.
     *
     * @return el documento con la columna partida, o `null` si no hace falta o no se puede.
     */
    fun splitSlotForFigure(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        figure: RhythmFigure
    ): List<TabMeasure>? {
        val measure = measures.getOrNull(measureIndex) ?: return null
        val slot = measure.slots.getOrNull(slotIndex) ?: return null
        // Solo se parte un **silencio** (o una casilla con una figura más larga); si ya tiene la
        // figura que se va a escribir, no hay nada que hacer.
        val current = slot.rhythmicFigure ?: return null
        if (current.isRest.not() && slot.strings.isEmpty()) return null
        if (kotlin.math.abs(current.beatValue - figure.beatValue) < 1e-6) return null
        val leftover = current.beatValue - figure.beatValue
        val slots = measure.slots.toMutableList()
        if (leftover < -1e-6) {
            // (D-93) **La nota es MÁS LARGA que la casilla.** En una rejilla de corcheas, elegir
            // «negra» no puede meter una negra en media negra de sitio: la nota **absorbe** las
            // casillas siguientes hasta completar su duración, que es lo que hace MuseScore al
            // escribir una figura más larga. Antes se ponía la figura a la fuerza y el compás se
            // desbordaba (ocho negras en un 4/4 otra vez, por la puerta de atrás).
            var need = figure.beatValue - current.beatValue
            var index = slotIndex + 1
            val victims = mutableListOf<Int>()
            while (need > 1e-6 && index < slots.size) {
                val next = slots[index]
                // No se absorbe una casilla con **nota**: se perdería música.
                if (next.strings.isNotEmpty() || next.isHeld || next.tuplet != null) return null
                need -= (next.rhythmicFigure?.beatValue ?: current.beatValue)
                victims += index
                index++
            }
            if (need > 1e-6) return null
            victims.asReversed().forEach { slots.removeAt(it) }
            slots[slotIndex] = slot.copy(rhythmicFigure = figure)
            return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
        }
        slots[slotIndex] = slot.copy(rhythmicFigure = figure)
        if (leftover > 1e-6) {
            var index = slotIndex + 1
            TabMeasureMeter.splitIntoRests(leftover, startBeats = figure.beatValue).forEach { rest ->
                slots.add(index, TabSlot(rhythmicFigure = rest, voice = slot.voice))
                index++
            }
        }
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /**
     * **Pone la figura [figure] en la casilla**, partiendo el silencio si es más larga que la nota o
     * **absorbiendo** las casillas siguientes si la nota es más larga que el silencio (D-93).
     *
     * Es el gesto único de escribir una nota con una duración elegida: en una rejilla de corcheas,
     * una negra ocupa **dos** casillas, no media. Devuelve el documento tal cual si no se puede (la
     * casilla siguiente ya tiene una nota, o el compás no llega).
     */
    fun setFigureAbsorbing(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        figure: RhythmFigure
    ): List<TabMeasure> {
        val measure = measures.getOrNull(measureIndex) ?: return measures
        val slot = measure.slots.getOrNull(slotIndex) ?: return measures
        val current = slot.rhythmicFigure ?: return measures
        // Casilla con nota y ya de la figura pedida: nada que hacer.
        if (kotlin.math.abs(current.beatValue - figure.beatValue) < 1e-6) {
            return setRhythmicFigure(measures, measureIndex, slotIndex, figure)
        }
        return splitSlotForFigure(measures, measureIndex, slotIndex, figure)
            ?: setRhythmicFigure(measures, measureIndex, slotIndex, figure)
    }

    /**
     * **Inserta una barra de compás** después de la columna [slotIndex] del compás [measureIndex]:
     * parte el compás en dos. El segundo recibe las columnas desde la posición de corte (o queda
     * vacío si el corte es al final). Las **marcas del documento** (repetición/volta) se quedan en el
     * primero, salvo la repetición de **cierre**, que se traslada al segundo (es su final).
     */
    fun insertBar(measures: List<TabMeasure>, measureIndex: Int, slotIndex: Int): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val measure = measures[measureIndex]
        val at = (slotIndex + 1).coerceIn(0, measure.slots.size)
        val first = measure.copy(slots = measure.slots.take(at))
        val second = TabMeasure(
            slots = measure.slots.drop(at),
            // La repetición de cierre (`:|`) marca el final del compás: viaja al compás nuevo.
            repeat = measure.repeat?.let {
                when (it) {
                    TabRepeatMark.END -> TabRepeatMark.END
                    TabRepeatMark.BOTH -> TabRepeatMark.BOTH
                    TabRepeatMark.START -> null
                }
            },
            repeatCount = if (measure.repeat == TabRepeatMark.END || measure.repeat == TabRepeatMark.BOTH) {
                measure.repeatCount
            } else {
                2
            }
        )
        val firstFixed = if (measure.repeat == TabRepeatMark.END || measure.repeat == TabRepeatMark.BOTH) {
            first.copy(repeat = null)
        } else {
            first
        }
        return measures.toMutableList().also {
            it[measureIndex] = firstFixed
            it.add(measureIndex + 1, second)
        }
    }

    /** Añade una casilla (o vacía) en una posición, **sin** desplazar las demás. */
    fun setSlot(measures: List<TabMeasure>, measureIndex: Int, slotIndex: Int, slot: TabSlot): List<TabMeasure> =
        updateSlot(measures, measureIndex, slotIndex) { slot }

    /**
     * Alterna una cuerda en una casilla: si ya estaba pulsada en ese traste, la quita; si no, la
     * añade. Mantiene ordenadas las pulsaciones por número de cuerda.
     */
    fun togglePluck(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        fret: Int
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        val existing = slot.strings.firstOrNull { it.stringNumber == stringNumber }
        val strings = when {
            existing == null -> slot.strings + TabPluck(stringNumber, fret, TabTechnique.NONE)
            existing.fret == fret -> slot.strings.filterNot { it.stringNumber == stringNumber } // quitar
            // Cambiar el traste CONSERVA la técnica de la nota (no se pierde el trémolo, RF-127).
            else -> slot.strings.map {
                if (it.stringNumber == stringNumber) it.copy(position = it.position.copy(fret = fret)) else it
            }
        }.sortedBy { it.stringNumber }
        slot.copy(strings = strings)
    }

    /**
     * Escribe el traste de una cuerda en una casilla. Si la cuerda **no** tenía nota, la añade: es
     * el gesto de escritura del editor (tocar la casilla y teclear la cifra), así que no puede
     * exigir que la nota exista antes. El traste se acota a `0..MAX_FRET`.
     */
    /**
     * Escribe el traste de una cuerda en una casilla. Si la cuerda **no** tenía nota, la añade: es
     * el gesto de escritura del editor (tocar la casilla y teclear la cifra), así que no puede
     * exigir que la nota exista antes. El traste se acota a `0..MAX_FRET`.
     *
     * Con el modelo de **columnas**, si [slotIndex] está **más allá del final** del compás, se crean
     * las columnas de relleno necesarias: tocar la «columna fantasma» de un compás vacío escribe la
     * primera nota y crea su columna.
     */
    fun setFret(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        fret: Int
    ): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val safeFret = fret.coerceIn(0, MAX_FRET)
        val measure = measures[measureIndex]
        val slots = measure.slots.toMutableList()
        // Se rellena hasta la columna pedida (si el cursor está «tras el final», crea la columna).
        while (slots.size <= slotIndex) slots += TabSlot()
        if (slotIndex < 0) return measures
        val slot = slots[slotIndex]
        val existing = slot.strings.firstOrNull { it.stringNumber == stringNumber }
        val strings = when (existing) {
            null -> slot.strings + TabPluck(stringNumber, safeFret, TabTechnique.NONE)
            else -> slot.strings.map {
                if (it.stringNumber == stringNumber) it.copy(position = it.position.copy(fret = safeFret)) else it
            }
        }.sortedBy { it.stringNumber }
        // (D-87) Una casilla es **un** evento: si estaba marcada como **nota mantenida** y se le
        // escribe una nota, deja de serlo. Sin esto quedaban la `~` de todas las cuerdas y la cifra
        // nueva superpuestas en la misma casilla.
        slots[slotIndex] = slot.copy(strings = strings, isHeld = false)
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /**
     * **Alterna** una técnica de una cuerda pulsada. Las técnicas se **combinan** (un ligado puede
     * llevar además un slide, un bend un vibrato…), como en My Guitar Tabs: pulsar el marcado lo
     * **añade** si no estaba y lo **quita** si ya estaba. Es una marca **del documento**, así que el
     * redigitado nunca la borra (RF-127, ADR-017 §4.7).
     *
     * [TabTechnique.NONE] **limpia** todas las técnicas de esa nota.
     */
    fun toggleTechnique(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        technique: TabTechnique
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        val strings = slot.strings.map { pluck ->
            when {
                pluck.stringNumber != stringNumber -> pluck
                technique == TabTechnique.NONE -> pluck.copy(techniques = emptySet())
                // Volver a pulsar el símbolo lo **quita**.
                pluck.has(technique) -> pluck.copy(techniques = pluck.techniques - technique)
                // Al **añadirlo**, la matriz (U-2) retira los incompatibles: no se pueden tener a la
                // vez arco arriba y abajo, picado y tenuto, armónico y apagada…
                else -> pluck.copy(
                    techniques = TabSymbolCompatibility.withTechnique(pluck.techniques, technique)
                )
            }
        }
        slot.copy(strings = strings)
    }

    /**
     * Fija la **cantidad del bend** (`1/2`, `1`…) de una cuerda pulsada; `null` la deja sin indicar.
     * Es una **magnitud** de la técnica de estiramiento, no una técnica en sí: se guarda aparte para
     * que el arco pueda rotularse sin duplicar símbolos.
     */
    fun setBendAmount(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        amount: com.grafitto.laudate.domain.model.tablature.TabBendAmount?
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        slot.copy(
            strings = slot.strings.map {
                if (it.stringNumber == stringNumber) it.copy(bendAmount = amount) else it
            }
        )
    }

    /**
     * Fija el **alcance del `P.M.`** (apagado de palma) de una cuerda pulsada: cuántas **columnas**
     * abarca desde esta nota. `null` = solo su columna. Se acota a `1..MAX_PALM_MUTE_SPAN`.
     */
    fun setPalmMuteSpan(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        span: Int?
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        slot.copy(
            strings = slot.strings.map {
                if (it.stringNumber == stringNumber) {
                    it.copy(palmMuteSpan = span?.coerceIn(1, MAX_PALM_MUTE_SPAN))
                } else {
                    it
                }
            }
        )
    }

    /**
     * Fija el **dedo de la mano izquierda** (1–4) que pisa la nota; `null` lo deja sin indicar. Es
     * una anotación de **digitación**, no una técnica de interpretación.
     */
    fun setFrettingFinger(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        finger: com.grafitto.laudate.domain.model.tablature.TabFrettingFinger?
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        slot.copy(
            strings = slot.strings.map {
                if (it.stringNumber == stringNumber) it.copy(frettingFinger = finger) else it
            }
        )
    }

    /** Fija el **dedo/púa de la mano derecha** (`p`/`i`/`m`/`a`) que pulsa la nota; `null` lo quita. */
    fun setPickingFinger(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        finger: com.grafitto.laudate.domain.model.tablature.TabPickingFinger?
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        slot.copy(
            strings = slot.strings.map {
                if (it.stringNumber == stringNumber) it.copy(pickingFinger = finger) else it
            }
        )
    }

    /**
     * Fija la **técnica principal** de una cuerda pulsada (la sustituye). Se conserva para el
     * redigitado y los importadores, que conocen una sola técnica por nota.
     */
    fun setTechnique(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        technique: TabTechnique
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        val strings = slot.strings.map {
            if (it.stringNumber == stringNumber) {
                it.copy(techniques = if (technique == TabTechnique.NONE) emptySet() else setOf(technique))
            } else {
                it
            }
        }
        slot.copy(strings = strings)
    }

    /** Alterna el **trémolo** (`*`) de todas las cuerdas pulsadas de una casilla. */
    fun toggleTremolo(measures: List<TabMeasure>, measureIndex: Int, slotIndex: Int): List<TabMeasure> =
        updateSlot(measures, measureIndex, slotIndex) { slot ->
            val turnOn = slot.strings.any { !it.has(TabTechnique.TREMOLO) }
            slot.copy(
                strings = slot.strings.map {
                    if (turnOn) it.copy(techniques = it.techniques + TabTechnique.TREMOLO)
                    else it.copy(techniques = it.techniques - TabTechnique.TREMOLO)
                }
            )
        }

    /**
     * Fija el **número de trazos** del trémolo de una nota (D-28), como la paleta «Trémolos» de
     * MuseScore (`tremolo1`…`tremolo4`). Se acota a `1..`[TabPluck.MAX_TREMOLO_STROKES].
     *
     * **No** activa el trémolo: solo cambia cuántos trazos dibuja. Si la nota no lo lleva, el valor
     * queda guardado para cuando se active, de modo que encender y apagar la técnica no pierde la
     * elección del usuario.
     */
    fun setTremoloStrokes(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        strokes: Int
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        val value = strokes.coerceIn(1, TabPluck.MAX_TREMOLO_STROKES)
        slot.copy(
            strings = slot.strings.map {
                if (it.stringNumber == stringNumber) it.copy(tremoloStrokes = value) else it
            }
        )
    }

    /**
     * Avanza la marca de **repetición** de un compás de forma cíclica:
     * `null → START → END → BOTH → null`.
     */
    fun cycleRepeat(measures: List<TabMeasure>, measureIndex: Int): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { measure ->
            val next = when (measure.repeat) {
                null -> TabRepeatMark.START
                TabRepeatMark.START -> TabRepeatMark.END
                TabRepeatMark.END -> TabRepeatMark.BOTH
                TabRepeatMark.BOTH -> null
            }
            measure.copy(repeat = next)
        }

    /**
     * Fija la marca de **repetición** de un compás a un valor concreto ([mark]; `null` la quita). Es
     * el gesto de la **paleta de símbolos**: un botón por símbolo, que fija *ese* símbolo en vez de
     * ir rotando por todos.
     */
    fun setRepeat(measures: List<TabMeasure>, measureIndex: Int, mark: TabRepeatMark?): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { it.copy(repeat = mark) }

    /** Avanza la **volta** (casilla) de un compás: `null → 1 → 2 → 3 → null`. */
    fun cycleEnding(measures: List<TabMeasure>, measureIndex: Int): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { measure ->
            val next = when (measure.ending) {
                null -> 1
                1 -> 2
                2 -> 3
                else -> null
            }
            measure.copy(ending = next, endingTo = null)
        }

    /**
     * Fija la **volta** (casilla de 1ª/2ª vez) de un compás a un valor concreto ([ending]; `null` la
     * quita). Un botón por casilla, como en la paleta de símbolos.
     *
     * Deja la casilla en **una sola pasada**: para la casilla de varias pasadas (`1, 2.`) está
     * [setEndingRange].
     */
    fun setEnding(measures: List<TabMeasure>, measureIndex: Int, ending: Int?): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { it.copy(ending = ending, endingTo = null) }

    /**
     * Fija la volta a un **rango de pasadas** `[from]..[to]`: la casilla `1, 2.` de MuseScore, que
     * suena en la primera **y** en la segunda vuelta.
     *
     * Es el `Volta::setEndings()` (`src/engraving/dom/volta.cpp:125`), que ordena la lista y deja que
     * `hasEnding()` decida pasada a pasada. Aquí el rango se guarda como [TabMeasure.ending] +
     * [TabMeasure.endingTo]; si `to <= from` se guarda una casilla simple, porque `1, 1.` no existe.
     */
    fun setEndingRange(
        measures: List<TabMeasure>,
        measureIndex: Int,
        from: Int?,
        to: Int? = null
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { measure ->
        if (from == null) {
            measure.copy(ending = null, endingTo = null)
        } else {
            val first = from.coerceIn(1, 16)
            val last = (to ?: first).coerceIn(1, 16)
            measure.copy(
                ending = first,
                endingTo = if (last > first) last else null
            )
        }
    }

    /** Fija el número de repeticiones de un compás (entre 2 y 8). */
    fun setRepeatCount(measures: List<TabMeasure>, measureIndex: Int, count: Int): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { it.copy(repeatCount = count.coerceIn(2, 8)) }

    /**
     * Fija el **rótulo propio** de la volta ([text]); `null` o en blanco lo devuelve al derivado de
     * las pasadas (`1.`, `1, 2.`).
     *
     * Es el `beginText` del `Volta` de MuseScore (`src/engraving/dom/textlinebase.h:109`): el rótulo
     * es texto libre y **no** toca las pasadas, que siguen mandando en la reproducción. Solo tiene
     * efecto si el compás **lleva casilla** ([TabMeasure.ending]): sin volta no hay corchete donde
     * escribir, así que el texto se descarta en vez de quedar guardado e invisible.
     */
    fun setEndingText(measures: List<TabMeasure>, measureIndex: Int, text: String?): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { measure ->
            if (measure.ending == null) {
                measure
            } else {
                measure.copy(endingText = text?.takeIf { it.isNotBlank() })
            }
        }

    /**
     * Fija el tipo de **barra de compás** (simple/doble/final) del final de un compás.
     */
    fun setBarline(
        measures: List<TabMeasure>,
        measureIndex: Int,
        barline: com.grafitto.laudate.domain.model.tablature.TabBarline
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { it.copy(barline = barline) }

    // ── Navegación y saltos (T1) ────────────────────────────────────────────────

    /**
     * Fija el **signo de navegación** (Segno / Coda / Fine / To Coda) de un compás; `null` lo quita.
     * Un botón por signo en la paleta, como en las marcas de repetición.
     */
    fun setNavigation(
        measures: List<TabMeasure>,
        measureIndex: Int,
        mark: com.grafitto.laudate.domain.model.tablature.TabNavigationMark?
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { it.copy(navigation = mark) }

    /**
     * Fija el **salto** (D.C. / D.S. y variantes) de un compás; `null` lo quita. Es un texto
     * estructurado, no una cadena libre: la pieza sabe leerlo.
     */
    fun setJump(
        measures: List<TabMeasure>,
        measureIndex: Int,
        jump: com.grafitto.laudate.domain.model.tablature.TabJump?
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { it.copy(jump = jump) }

    // ── Dinámicas (T2) ──────────────────────────────────────────────────────────

    /**
     * Fija la **dinámica** (matiz/regulador) de un compás; `null` la quita. Un botón por matiz en la
     * paleta (grupo «Dinámicas»).
     */
    fun setDynamic(
        measures: List<TabMeasure>,
        measureIndex: Int,
        dynamic: com.grafitto.laudate.domain.model.tablature.TabDynamic?
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { it.copy(dynamic = dynamic) }

    /**
     * Fija el **regulador** (*hairpin*) que **empieza** en un compás; `null` lo quita. Es la cuña de
     * crescendo/diminuendo, distinta de la dinámica escrita ([setDynamic]).
     */
    fun setHairpin(
        measures: List<TabMeasure>,
        measureIndex: Int,
        hairpin: com.grafitto.laudate.domain.model.tablature.TabHairpin?
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { it.copy(hairpin = hairpin) }

    // ── Notas de adorno (*grace notes*) ─────────────────────────────────────────

    /**
     * **Añade una nota de adorno** a una casilla, como la tecla `/` de MuseScore.
     *
     * El adorno se coloca en la **cuerda de la nota más aguda** de la casilla (o en la cuerda 1 si la
     * casilla está vacía) y con el **mismo traste** que esa nota: es lo que hace MuseScore al pulsar
     * `/` sobre un acorde — clona el acorde y lo cuelga como adorno. Después se puede cambiar de
     * cuerda o de traste con los mismos gestos que una nota normal.
     *
     * Si la casilla ya tiene adornos se añade **otro** (MuseScore admite varios); el nuevo queda el
     * **último**, que es el que se toca más cerca de la nota principal.
     */
    fun addGrace(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        type: com.grafitto.laudate.domain.model.tablature.TabGraceType =
            com.grafitto.laudate.domain.model.tablature.TabGraceType.ACCIACCATURA,
        stringNumber: Int? = null
    ): List<TabMeasure> {
        val slot = measures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex) ?: return measures
        // (D-63) El adorno nace en la **cuerda activa** si esa cuerda tiene nota en la casilla: es la
        // que el usuario está escribiendo. Antes se clonaba siempre la **más aguda**, así que al
        // añadir un adorno sobre la cuerda 5 aparecía en la 1 y había que moverlo. Si la cuerda
        // activa no tiene nota se cae a la más aguda, y si la casilla está vacía, a la cuerda activa
        // con traste 0 (el usuario lo corrige con [setGraceFret]).
        val source = stringNumber?.let { wanted -> slot.strings.lastOrNull { it.stringNumber == wanted } }
            ?: slot.strings.minByOrNull { it.stringNumber }
        val grace = com.grafitto.laudate.domain.model.tablature.TabGrace(
            stringNumber = source?.stringNumber ?: stringNumber ?: 1,
            fret = source?.fret ?: 0,
            type = type
        )
        return updateSlot(measures, measureIndex, slotIndex) { it.copy(graces = it.graces + grace) }
    }

    /**
     * **Cambia el traste de un adorno** (D-63).
     *
     * El usuario reportó que los adornos «siempre salen como 0 pequeñito»: al añadirlos sobre una
     * casilla vacía el traste nace en `0` y **no había forma de corregirlo** —el control de traste
     * del panel edita la nota principal, no el adorno—. Con esto el adorno es tan editable como la
     * nota, que es lo que hace MuseScore: un adorno es una nota de verdad.
     *
     * [graceIndex] es la posición dentro de [TabMeasure.slots] → `graces`; un índice fuera de rango
     * no hace nada (el adorno se pudo borrar entre el clic y el intent).
     */
    fun setGraceFret(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        graceIndex: Int,
        fret: Int
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        if (graceIndex !in slot.graces.indices) return@updateSlot slot
        val graces = slot.graces.toMutableList()
        graces[graceIndex] = graces[graceIndex].copy(fret = fret.coerceIn(0, MAX_FRET))
        slot.copy(graces = graces)
    }

    /**
     * **Cambia la cuerda de un adorno** (D-63): el adorno se dibuja en la línea de su cuerda, así que
     * moverlo de cuerda lo mueve de línea, igual que en MuseScore.
     */
    fun setGraceString(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        graceIndex: Int,
        stringNumber: Int
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        if (graceIndex !in slot.graces.indices) return@updateSlot slot
        val graces = slot.graces.toMutableList()
        graces[graceIndex] = graces[graceIndex].copy(stringNumber = stringNumber.coerceAtLeast(1))
        slot.copy(graces = graces)
    }

    /**
     * **Quita el último** adorno de una casilla. Es el deshacer natural de [addGrace]: pulsar `/`
     * sobre una casilla que ya tiene adornos añade otro, así que el borrado va por el menú o por
     * [clearGraces].
     */
    fun removeLastGrace(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int
    ): List<TabMeasure> {
        val slot = measures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex) ?: return measures
        if (slot.graces.isEmpty()) return measures
        return updateSlot(measures, measureIndex, slotIndex) {
            it.copy(graces = it.graces.dropLast(1))
        }
    }

    /** **Quita todos** los adornos de una casilla. */
    fun clearGraces(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) {
        if (it.graces.isEmpty()) it else it.copy(graces = emptyList())
    }

    // ── Articulaciones (T3) ─────────────────────────────────────────────────────

    /**
     * **Alterna** una **articulación** de una cuerda pulsada (T3). Las articulaciones se **combinan**
     * (un acento con un picado se dibujan como el **sforzato** `𝄰`), así que la alternancia es por
     * **cualidad**: pulsar «picado» sobre una nota que lleva sforzato quita solo el picado y deja el
     * acento suelto, como `Chord::updateArticulations` en MuseScore.
     */
    fun toggleArticulation(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        articulation: com.grafitto.laudate.domain.model.tablature.TabArticulation
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        slot.copy(
            strings = slot.strings.map { pluck ->
                if (pluck.stringNumber != stringNumber) {
                    pluck
                } else {
                    // (U-2) La matriz retira lo que choca y **combina** las parejas de MuseScore.
                    pluck.copy(
                        articulations = TabSymbolCompatibility.toggleArticulation(
                            pluck.articulations,
                            articulation
                        )
                    )
                }
            }
        )
    }

    // ── Alteraciones y altura (T4) ───────────────────────────────────────────────

    /**
     * Fija la **alteración** escrita junto a la cifra de una cuerda pulsada; `null` la quita. No
     * cambia el traste: solo aclara la altura leída.
     */
    fun setAccidental(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        accidental: com.grafitto.laudate.domain.model.tablature.TabAccidental?
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        slot.copy(
            strings = slot.strings.map {
                if (it.stringNumber == stringNumber) it.copy(accidental = accidental) else it
            }
        )
    }

    // ── Ligadura de prolongación (B-10) ──────────────────────────────────────────

    /**
     * **Alterna la ligadura de prolongación** (*tie*) de la nota [stringNumber] de la casilla
     * `[measureIndex, slotIndex]`.
     *
     * Una ligadura solo tiene sentido si **existe una nota siguiente** en la misma cuerda y con la
     * **misma altura** (mismo traste): es lo que hace MuseScore, que rechaza ligar notas distintas.
     * Por eso, si no hay destino válido, la operación **no cambia nada** y devuelve la lista tal cual.
     *
     * @return la lista de compases con la ligadura alternada, o la misma lista si no se puede ligar.
     */
    fun toggleTie(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int
    ): List<TabMeasure> {
        val pluck = measures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex)
            ?.strings?.firstOrNull { it.stringNumber == stringNumber } ?: return measures
        // (D-90) **Si no hay nota a la que ligar, se crea.** Es lo que hace MuseScore: `EditTie`
        // llama a `cmdAddTie` cuando `searchTieNote` no encuentra destino
        // (`edittie.cpp:253-260`, `searchTieNote` en `dom/utils.cpp`), y la nota creada lleva el
        // **mismo traste** en la misma cuerda. Sin esto el botón «no hacía nada» —que es lo que el
        // usuario reportó— porque la mayoría de las notas que se escriben no tienen todavía una
        // siguiente con el mismo traste.
        val target = tieTarget(measures, measureIndex, slotIndex, stringNumber)
        if (!pluck.tiedToNext && target == null) {
            val created = createTieTarget(measures, measureIndex, slotIndex, stringNumber)
                ?: return measures
            return updateSlot(created, measureIndex, slotIndex) { slot ->
                slot.copy(
                    strings = slot.strings.map {
                        if (it.stringNumber == stringNumber) it.copy(tiedToNext = true) else it
                    }
                )
            }
        }
        return updateSlot(measures, measureIndex, slotIndex) { slot ->
            slot.copy(
                strings = slot.strings.map {
                    if (it.stringNumber == stringNumber) it.copy(tiedToNext = !it.tiedToNext) else it
                }
            )
        }
    }

    /**
     * **Crea la nota destino** de una ligadura cuando no existe (D-90): añade una columna detrás de
     * la del cursor —o reutiliza la siguiente si está vacía— y escribe en ella la **misma nota**
     * (misma cuerda y mismo traste), que es lo que hace `EditTie::cmdAddTie` en MuseScore.
     *
     * La columna nueva dura lo mismo que la nota ligada: una ligadura **no cambia la duración**, solo
     * reparte el mismo sonido entre dos columnas. Si la rejilla no admite otra columna de esa figura,
     * la nota se liga a la columna siguiente y se deja que el rebalanceo del compás la absorba.
     *
     * @return el documento nuevo, o `null` si no se puede crear.
     */
    private fun createTieTarget(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int
    ): List<TabMeasure>? {
        val origin = measures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex)
            ?.strings?.firstOrNull { it.stringNumber == stringNumber } ?: return null
        val measure = measures[measureIndex]
        val nextIndex = slotIndex + 1
        // Si la columna siguiente está **vacía** (un silencio de relleno), se aprovecha: se le pone
        // la nota y no se añade nada. Es el caso normal de una rejilla predefinida.
        if (nextIndex <= measure.slots.lastIndex) {
            val next = measure.slots[nextIndex]
            if (next.strings.isEmpty() && !next.isHeld) {
                return setFret(measures, measureIndex, nextIndex, stringNumber, origin.fret)
            }
        }
        val inserted = insertColumn(measures, measureIndex, nextIndex)
        return setFret(inserted, measureIndex, nextIndex, stringNumber, origin.fret)
    }

    // ── Ligadura de expresión (S) ────────────────────────────────────────────────

    /**
     * **Alterna la ligadura de expresión** (*slur*) de la casilla `[measureIndex, slotIndex]`, que es
     * el gesto de `S` en MuseScore 4.
     *
     * A diferencia de la ligadura de **prolongación** ([toggleTie]), que une dos notas de la **misma
     * altura** en la misma cuerda, la de expresión une **dos casillas cualesquiera**: es una
     * indicación de **fraseo**, no de duración. Por eso se guarda en la casilla
     * ([TabSlot.slurToNext]) y no exige que las alturas coincidan.
     *
     * Solo se puede ligar si **hay una casilla siguiente con sonido**: una ligadura al vacío no
     * significa nada. Si no la hay, la operación **no cambia nada** y devuelve la lista tal cual.
     *
     * @return la lista de compases con la ligadura alternada, o la misma lista si no se puede ligar.
     */
    fun toggleSlur(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int
    ): List<TabMeasure> {
        val slot = measures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex) ?: return measures
        if (!slot.slurToNext && nextSoundingSlot(measures, measureIndex, slotIndex) == null) {
            return measures
        }
        return updateSlot(measures, measureIndex, slotIndex) { it.copy(slurToNext = !it.slurToNext) }
    }

    /**
     * **Primera casilla con sonido** después de `[measureIndex, slotIndex]`, cruzando compases.
     * `null` si no hay ninguna. Es el destino de la ligadura de expresión.
     */
    fun nextSoundingSlot(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int
    ): Pair<Int, Int>? {
        for (m in measureIndex until measures.size) {
            val slots = measures[m].slots
            val from = if (m == measureIndex) slotIndex + 1 else 0
            for (s in from until slots.size) {
                if (slots[s].strings.isNotEmpty() || slots[s].isHeld) return m to s
            }
        }
        return null
    }

    /**
     * **Destino** de la ligadura que empieza en `[measureIndex, slotIndex]` sobre [stringNumber]: la
     * primera nota de esa cuerda **después** de esa casilla, cruzando compases. `null` si no hay
     * ninguna o si su traste **no coincide** (una ligadura entre alturas distintas no es válida).
     */
    fun tieTarget(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int
    ): Pair<Int, Int>? {
        val origin = measures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex)
            ?.strings?.firstOrNull { it.stringNumber == stringNumber } ?: return null
        for (m in measureIndex until measures.size) {
            val slots = measures[m].slots
            val from = if (m == measureIndex) slotIndex + 1 else 0
            for (s in from until slots.size) {
                val candidate = slots[s].strings.firstOrNull { it.stringNumber == stringNumber }
                if (candidate != null) {
                    return if (candidate.fret == origin.fret) m to s else null
                }
            }
        }
        return null
    }

    /**
     * Indica si la nota `[measureIndex, slotIndex]` de [stringNumber] **recibe** una ligadura, es
     * decir, si la nota anterior de esa misma cuerda la dejó ligada. Sirve para dibujar el arco y para
     * no volver a atacarla al reproducir.
     */
    fun isTiedFromPrevious(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int
    ): Boolean {
        for (m in measureIndex downTo 0) {
            val slots = measures[m].slots
            val from = if (m == measureIndex) slotIndex - 1 else slots.lastIndex
            for (s in from downTo 0) {
                val candidate = slots[s].strings.firstOrNull { it.stringNumber == stringNumber }
                if (candidate != null) return candidate.tiedToNext
            }
        }
        return false
    }

    /**
     * Fija el **cambio de octava** de un compás (8va / 15ma / 8vb); `null` lo quita. Marca el inicio
     * del pasaje desplazado.
     */
    fun setOctaveShift(
        measures: List<TabMeasure>,
        measureIndex: Int,
        shift: com.grafitto.laudate.domain.model.tablature.TabOctaveShift?
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { it.copy(octaveShift = shift) }

    // ── Tempo (T5) ───────────────────────────────────────────────────────────────

    /** BPM mínimo y máximo admitidos en la indicación de tempo de un compás. */
    const val MIN_TEMPO_BPM = 30
    const val MAX_TEMPO_BPM = 300

    /**
     * Fija el **tempo** (BPM) de un compás; `null` quita la indicación. Se acota a
     * `MIN_TEMPO_BPM..MAX_TEMPO_BPM` para que un valor absurdo no rompa la maquetación.
     */
    fun setTempo(measures: List<TabMeasure>, measureIndex: Int, bpm: Int?): List<TabMeasure> =
        updateMeasure(measures, measureIndex) {
            it.copy(tempo = bpm?.coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM))
        }

    // ── Texto (T6) ───────────────────────────────────────────────────────────────

    /**
     * Fija el **texto libre** anclado a un compás (indicación del copista, comentario…); `null` o en
     * blanco lo quita. Se recorta el espacio sobrante para no dibujar rótulos vacíos.
     */
    fun setText(measures: List<TabMeasure>, measureIndex: Int, text: String?): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { it.copy(text = text?.trim()?.ifEmpty { null }) }

    /** Fija la figura rítmica de una posición; `null` vuelve a heredar la resolución general. */
    fun setRhythmicFigure(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        figure: RhythmFigure?
    ): List<TabMeasure> =
        updateSlot(measures, measureIndex, slotIndex) { it.copy(rhythmicFigure = figure) }

    /**
     * **Tresillo** (3:2) sobre la posición [slotIndex]: marca el **grupo entero** de [groupSize]
     * columnas consecutivas, no una sola.
     *
     * Un tresillo es un **grupo**, no una casilla: en MuseScore `Ctrl+3` convierte la figura del
     * cursor en un grupo de tres figuras que duran dos tercios cada una, y el corchete abarca las
     * tres. Marcar una sola casilla producía un corchete de una figura —un «tresillo» de una nota—
     * que no existe en notación. Aquí se replica el gesto: se marcan [groupSize] columnas seguidas
     * (creando las que falten) y, si el cursor ya estaba dentro de un tresillo, se **deshace el
     * grupo completo** al que pertenece.
     */
    fun toggleTripletGroup(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        groupSize: Int = TRIPLET_GROUP_SIZE
    ): List<TabMeasure> = toggleTupletGroup(measures, measureIndex, slotIndex, TabTuplet.TRIPLET, groupSize)

    /**
     * **Grupo irregular** de proporción [tuplet] sobre la posición [slotIndex], generalizando el
     * tresillo a cualquier proporción (`Tuplet` de MuseScore: `3:2`, `5:4`, `6:4`, `7:4`, `9:8`…).
     *
     * Igual que [toggleTripletGroup], marca el **grupo entero** de `tuplet.numNotes` columnas
     * consecutivas —un grupo irregular es un grupo, no una casilla— y, si el cursor ya estaba dentro
     * de un grupo **de la misma proporción**, lo deshace completo. Si estaba dentro de otro grupo
     * distinto, lo **sustituye**: en MuseScore cambiar el tipo de grupo reescribe el grupo, no lo
     * anida.
     */
    fun toggleTupletGroup(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        tuplet: TabTuplet,
        groupSize: Int = tuplet.numNotes
    ): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val measure = measures[measureIndex]
        if (slotIndex !in measure.slots.indices) return measures
        val slots = measure.slots.toMutableList()
        if (slots[slotIndex].tuplet == tuplet) {
            // Deshacer: se limpia la **racha contigua** del mismo grupo que contiene al cursor.
            var from = slotIndex
            while (from > 0 && slots[from - 1].tuplet == tuplet) from--
            var to = slotIndex
            while (to + 1 < slots.size && slots[to + 1].tuplet == tuplet) to++
            for (i in from..to) slots[i] = slots[i].copy(tuplet = null)
        } else {
            // Marcar: el grupo necesita [groupSize] columnas; las que falten nacen vacías.
            val size = groupSize.coerceAtLeast(1)
            while (slots.size < slotIndex + size) slots += TabSlot()
            for (i in slotIndex until slotIndex + size) {
                slots[i] = slots[i].copy(tuplet = tuplet)
            }
        }
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /**
     * Cambia **cómo se rotula** el grupo irregular que contiene al cursor (`TupletNumberType` de
     * MuseScore): solo el número (`3`), la proporción (`3:2`) o nada.
     *
     * Se aplica a **toda la racha contigua** del mismo grupo, no solo a la casilla del cursor: el
     * corchete es uno y su cifra también, así que dejar media racha con un ajuste y media con otro
     * partiría el grupo en dos. Si la casilla no está en un grupo, no se toca nada.
     */
    fun setTupletNumberType(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        numberType: TabTupletNumberType
    ): List<TabMeasure> = updateTupletGroup(measures, measureIndex, slotIndex) {
        it.copy(numberType = numberType)
    }

    /**
     * Cambia **si se dibuja el corchete** del grupo irregular que contiene al cursor
     * (`TupletBracketType` de MuseScore). Igual que [setTupletNumberType], se aplica a toda la racha.
     */
    fun setTupletBracketType(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        bracketType: TabTupletBracketType
    ): List<TabMeasure> = updateTupletGroup(measures, measureIndex, slotIndex) {
        it.copy(bracketType = bracketType)
    }

    /** Aplica [transform] al [TabTuplet] de la **racha contigua** que contiene la casilla. */
    private inline fun updateTupletGroup(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        transform: (TabTuplet) -> TabTuplet
    ): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val measure = measures[measureIndex]
        if (slotIndex !in measure.slots.indices) return measures
        val tuplet = measure.slots[slotIndex].tuplet ?: return measures
        val updated = transform(tuplet)
        if (updated == tuplet) return measures
        val slots = measure.slots.toMutableList()
        var from = slotIndex
        while (from > 0 && slots[from - 1].tuplet == tuplet) from--
        var to = slotIndex
        while (to + 1 < slots.size && slots[to + 1].tuplet == tuplet) to++
        for (i in from..to) slots[i] = slots[i].copy(tuplet = updated)
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /** Quita una cuerda de una casilla. */
    fun removePluck(measures: List<TabMeasure>, measureIndex: Int, slotIndex: Int, stringNumber: Int): List<TabMeasure> =
        updateSlot(measures, measureIndex, slotIndex) { slot ->
            slot.copy(strings = slot.strings.filterNot { it.stringNumber == stringNumber })
        }

    /** Vacía una casilla (pausa). */
    fun clearSlot(measures: List<TabMeasure>, measureIndex: Int, slotIndex: Int): List<TabMeasure> =
        updateSlot(measures, measureIndex, slotIndex) { TabSlot() }

    /** Alterna la prolongación ([TabSlot.isHeld]) de una casilla. */
    /**
     * **Alterna la nota mantenida** de una casilla.
     *
     * (D-87) Una casilla **no puede** ser «nota mantenida» y tener notas a la vez: el pintor dibuja
     * las dos cosas —una `~` por cuerda y, encima, la cifra de cada nota— y salen superpuestas, que es
     * el amasijo ilegible que se veía en la partitura. Antes `toggleHeld` se limitaba a invertir el
     * flag, así que:
     *
     *  - marcar la mantenida sobre una casilla **con notas** dejaba las cifras **debajo** de la `~`;
     *  - y escribir una nota en una casilla **mantenida** dejaba la `~` ocupando todas las cuerdas,
     *    incluidas las que no tienen nota.
     *
     * Ahora la operación es **excluyente**: al marcar la mantenida se **vacían** las notas de la
     * casilla (la casilla pasa a ser una prolongación de lo anterior, como un `Tie` de MuseScore sin
     * nota que lo origine, que tampoco existe), y al escribir una nota en una casilla mantenida se
     * **desmarca** la mantenida. Es la misma regla que aplica `setFret` al sustituir la nota de la
     * misma cuerda: una casilla es **un** evento, no una mezcla.
     */
    fun toggleHeld(measures: List<TabMeasure>, measureIndex: Int, slotIndex: Int): List<TabMeasure> =
        updateSlot(measures, measureIndex, slotIndex) { slot ->
            if (slot.isHeld) {
                slot.copy(isHeld = false)
            } else {
                // Al marcarla, la casilla pasa a ser solo la prolongación: sin notas.
                slot.copy(isHeld = true, strings = emptyList())
            }
        }

    // ── Entrada de notas: por altura (método 2) y por pulsación (Ctrl+↑/↓) ─────────

    /**
     * Escribe una **[TabPluck] completa** (cuerda, traste y técnicas) en una casilla, **creando** la
     * columna si hace falta y **sustituyendo** la nota de esa cuerda si ya existía. Es el gesto de la
     * entrada por nombre de nota (método 2): la pulsación ya viene resuelta por el motor de digitación,
     * así que no se pasa por [setFret] (que solo guarda el traste de la cuerda activa).
     */
    fun setPluck(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        pluck: TabPluck
    ): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val measure = measures[measureIndex]
        val slots = measure.slots.toMutableList()
        while (slots.size <= slotIndex) slots += TabSlot()
        if (slotIndex < 0) return measures
        val slot = slots[slotIndex]
        val strings = (slot.strings.filterNot { it.stringNumber == pluck.stringNumber } + pluck)
            .sortedBy { it.stringNumber }
        // (D-87) Igual que en [setFret]: escribir una nota **desmarca** la nota mantenida.
        slots[slotIndex] = slot.copy(strings = strings, isHeld = false)
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /**
     * **Mueve una nota a la cuerda contigua** sin cambiar su altura (el `Ctrl+↑`/`Ctrl+↓` de
     * MuseScore): recalcula el traste para que suene igual en la cuerda vecina. Si la nota no cabe
     * en esa cuerda, se queda como estaba.
     */
    fun movePluckToAdjacentString(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        tuning: com.grafitto.laudate.domain.model.tablature.Tuning,
        direction: Int
    ): List<TabMeasure> = updateSlot(measures, measureIndex, slotIndex) { slot ->
        val pluck = slot.strings.firstOrNull { it.stringNumber == stringNumber } ?: return@updateSlot slot
        val moved = TabNoteInput.moveToAdjacentString(pluck, tuning, direction) ?: return@updateSlot slot
        slot.copy(strings = (slot.strings.filterNot { it.stringNumber == stringNumber } + moved).sortedBy { it.stringNumber })
    }

    /**
     * Añade un compás al final **ya completo y con rejilla** (Fase B.1): nace con sus columnas de
     * silencio (una por unidad de la subdivisión), de modo que nunca queda «corto» y se puede
     * escribir en cualquier pulso. Es lo que hace MuseScore/TuxGuitar al insertar un compás. Si
     * [time] o [resolution] no se pasan, se crea vacío (comportamiento antiguo).
     */
    fun addMeasure(
        measures: List<TabMeasure>,
        time: ScoreTimeSignature? = null,
        resolution: TabResolution? = null
    ): List<TabMeasure> = measures + if (time != null && resolution != null) {
        emptyCompleteMeasure(time, resolution)
    } else {
        TabMeasure()
    }

    /** Quita el último compás (nunca deja la tablatura sin compases). */
    fun removeMeasure(measures: List<TabMeasure>): List<TabMeasure> =
        if (measures.size <= 1) measures else measures.dropLast(1)

    // ── Bloque: borrar / insertar / duplicar (E1-E3) ───────────────────────────────

    /**
     * **Elimina** los compases del rango [range] (E1). Nunca deja la tablatura vacía: si el rango
     * abarca todo, se conserva **un** compás vacío (como MuseScore, que una partitura sin compases no
     * existe).
     */
    fun removeMeasures(measures: List<TabMeasure>, range: IntRange): List<TabMeasure> {
        if (measures.isEmpty()) return measures
        val selection = TablatureSelection(range).clamped(measures.size) ?: return measures
        val from = selection.range.first
        val to = selection.range.last
        val remaining = measures.take(from) + measures.drop(to + 1)
        return normalizePickup(remaining.ifEmpty { listOf(TabMeasure()) })
    }

    /**
     * **Inserta** [block] en la posición [at] (índice del compás ante el que se pega). Si [at] está al
     * final, se **añaden** al final. Es el «pegar» del portapapeles (E3).
     */
    fun insertMeasures(measures: List<TabMeasure>, at: Int, block: List<TabMeasure>): List<TabMeasure> {
        if (block.isEmpty()) return measures
        val position = at.coerceIn(0, measures.size)
        return normalizePickup(measures.take(position) + block + measures.drop(position))
    }

    /**
     * **Intercambia** los compases del rango [range] con el bloque [block] del portapapeles: el bloque
     * ocupa el sitio de la selección y lo que había en la selección pasa a ser el contenido nuevo del
     * portapapeles. Es el «Intercambiar con el portapapeles» de MuseScore (`Ctrl+Mayús+X`,
     * `edit-swap`), que sirve para **mover** un pasaje sin pasar por cortar y pegar.
     *
     * Si el bloque y la selección **no** miden lo mismo, el documento cambia de tamaño: el bloque
     * sustituye a la selección tal cual (como hace MuseScore, que no exige igual longitud). El
     * llamante recibe el bloque **desplazado** para poder guardarlo en el portapapeles.
     */
    fun swapMeasures(
        measures: List<TabMeasure>,
        range: IntRange,
        block: List<TabMeasure>
    ): List<TabMeasure> {
        if (measures.isEmpty() || block.isEmpty()) return measures
        val selection = TablatureSelection(range).clamped(measures.size) ?: return measures
        val from = selection.range.first
        val to = selection.range.last
        return normalizePickup(measures.take(from) + block + measures.drop(to + 1))
    }

    /**
     * **Duplica** los compases del rango [range] justo detrás de la selección (E3). Devuelve el
     * documento nuevo; el llamante puede querer mover el cursor a la copia.
     */
    fun duplicateMeasures(measures: List<TabMeasure>, range: IntRange): List<TabMeasure> {
        if (measures.isEmpty()) return measures
        val selection = TablatureSelection(range).clamped(measures.size) ?: return measures
        val block = measures.subList(selection.range.first, selection.range.last + 1).toList()
        return insertMeasures(measures, selection.range.last + 1, block)
    }

    /**
     * **Vacía** los compases del rango [range] conservando los compases y sus **propiedades**
     * (repeticiones, casillas, tempo, texto, salto de sistema): solo se borra el **contenido** —notas
     * y silencios—, que se rehace con la rejilla de la **subdivisión del propio compás**.
     *
     * Es el «Vaciar compases» de MuseScore, distinto de «Eliminar compases» ([removeMeasures]), que
     * quita los compases del documento.
     */
    fun clearMeasures(
        measures: List<TabMeasure>,
        range: IntRange,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): List<TabMeasure> {
        if (measures.isEmpty()) return measures
        val selection = TablatureSelection(range).clamped(measures.size) ?: return measures
        val out = measures.toMutableList()
        for (index in selection.range) {
            val measure = out[index]
            val subdivision = subdivisionOf(measure, resolution)
            out[index] = measure.copy(
                slots = List(slotsPerMeasure(time, subdivision)) {
                    TabSlot(rhythmicFigure = restFigureForResolution(subdivision))
                }
            )
        }
        return out
    }

    /**
     * **Anacrusa** (*pickup*): marca o desmarca el **primer** compás como el compás **incompleto** con
     * el que arranca la pieza, como el `Measure::isAnacrusis()` de MuseScore
     * (`src/engraving/dom/measure.cpp:1968`).
     *
     * Al **ponerla** se recortan los **silencios del final**: son justo el relleno que la anacrusa no
     * debe llevar, y sin recortarlos el compás seguiría durando lo nominal y la marca no se notaría.
     * Si el compás no tiene ninguna nota no se recorta nada (un compás vacío no tiene «final de la
     * música» del que tirar) y solo se marca.
     *
     * Al **quitarla** el compás vuelve a durar lo nominal: se rellena con silencios, que es la
     * operación inversa y deja el documento como estaba.
     *
     * Solo se toca el **primer** compás: una anacrusa en medio sería un compás irregular, que es otra
     * cosa y no se modela aquí.
     */
    fun togglePickup(
        measures: List<TabMeasure>,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): List<TabMeasure> {
        val first = measures.firstOrNull() ?: return measures
        val updated = if (first.isPickup) {
            TabMeasureMeter.fillWithRests(first.copy(isPickup = false), time, resolution)
        } else {
            val marked = first.copy(isPickup = true)
            val end = marked.slots.indexOfLast { it.strings.isNotEmpty() || it.isHeld }
            if (end < 0) marked else marked.copy(slots = marked.slots.take(end + 1))
        }
        return listOf(updated) + measures.drop(1)
    }

    /**
     * **Compás irregular**: marca o desmarca el compás [index] como de **duración libre**, como el
     * `Measure::isIrregular()` de MuseScore (`m_timesig != m_len`).
     *
     * Al **marcarlo** no se toca el contenido: la duración que tiene **pasa a ser** la querida, y
     * desde ese momento «Completar compases» lo deja en paz y la barra de estado no lo marca en rojo.
     * Al **desmarcarlo** se reequilibra a la duración nominal ([time]), que es la operación inversa.
     *
     * Es lo que permite escribir un compás de 3 negras en medio de una pieza 4/4 —o uno de 5 para un
     * efecto— sin que el editor lo tome por un compás a medio escribir.
     */
    fun toggleIrregular(
        measures: List<TabMeasure>,
        index: Int,
        time: ScoreTimeSignature,
        resolution: TabResolution
    ): List<TabMeasure> {
        val measure = measures.getOrNull(index) ?: return measures
        val updated = if (measure.isIrregular) {
            // (D-73) Al desmarcar, el exceso **se reporta**, no se recorta: borrar música por detrás
            // para «cuadrar» el compás destruiría lo que el usuario escribió.
            rebalance(measure.copy(isIrregular = false), time, resolution, trimExcess = false)
        } else {
            measure.copy(isIrregular = true)
        }
        return measures.toMutableList().also { it[index] = updated }
    }

    /**
     * **Normaliza la anacrusa**: solo el **primer** compás puede serlo. Copiar, mover o pegar un
     * bloque que contenía la anacrusa dejaría la marca en un compás del medio, y entonces
     * «Completar compases» lo dejaría corto para siempre y la numeración se desplazaría dos veces.
     *
     * Es la misma regla que `Measure::isAnacrusis()` de MuseScore, que exige ser el primer compás del
     * sistema o de la sección: la marca no viaja con el contenido, es una propiedad de la **posición**.
     */
    fun normalizePickup(measures: List<TabMeasure>): List<TabMeasure> =
        if (measures.size <= 1 || measures.drop(1).none { it.isPickup }) {
            measures
        } else {
            listOf(measures.first()) + measures.drop(1).map { it.copy(isPickup = false) }
        }

    /**
     * **Cambio de compás a mitad de pieza**: fija la indicación **vigente a partir de** [index], o la
     * quita con `null` para que el compás vuelva a la del documento ([documentTime]).
     *
     * Es el `TimeSig` que MuseScore inserta **anclado a un compás** (`Measure::timesig()`): la
     * indicación no es una propiedad global del documento sino del compás, y rige hasta la siguiente.
     * Por eso el modelo guarda el **override** en [TabMeasure.time] y lo resuelve con
     * `Tablature.timeAt`.
     *
     * Al cambiar la indicación el compás **cambia de capacidad**, así que se **reequilibra** con la
     * nueva: un `3/4` dentro de una pieza `4/4` tiene que durar 3 negras, no 4. Sin este paso el
     * compás quedaría «largo» y «Completar compases» le añadiría un silencio de más.
     *
     * Si la indicación pedida **coincide** con la del documento se guarda `null`: un override que no
     * cambia nada es ruido en el modelo y en el JSON.
     */
    fun setTime(
        measures: List<TabMeasure>,
        index: Int,
        time: ScoreTimeSignature?,
        documentTime: ScoreTimeSignature,
        resolution: TabResolution
    ): List<TabMeasure> {
        val measure = measures.getOrNull(index) ?: return measures
        val effective = time ?: documentTime
        val stored = if (effective == documentTime) null else effective
        // Si la indicación nueva es **más corta**, se recortan los **silencios del final**: son el
        // relleno que ya no cabe. Las **notas no se tocan** —si el contenido no cabe, el compás queda
        // «largo» y el aviso lo dice, que es más honesto que borrar música del usuario—.
        var trimmed = measure.copy(time = stored)
        var guard = 0
        while (guard++ < 512 &&
            TabMeasureMeter.durationOf(trimmed, resolution) > effective.totalQuarterBeats + 1e-6
        ) {
            val last = trimmed.slots.lastOrNull() ?: break
            if (last.strings.isNotEmpty() || last.isHeld) break
            trimmed = trimmed.copy(slots = trimmed.slots.dropLast(1))
        }
        val updated = rebalance(trimmed, effective, resolution)
        return measures.toMutableList().also { it[index] = updated }
    }

    /**
     * **Mueve** el bloque de compases [range] a la posición [to], que es el índice que ocupa en la
     * lista **ya sin el bloque** (así el destino no depende de si se mueve a izquierda o a derecha).
     * Es el «Mover compases» de MuseScore: reordena, no copia ni borra.
     */
    fun moveMeasures(measures: List<TabMeasure>, range: IntRange, to: Int): List<TabMeasure> {
        if (measures.isEmpty()) return measures
        val selection = TablatureSelection(range).clamped(measures.size) ?: return measures
        val from = selection.range.first
        val last = selection.range.last
        val block = measures.subList(from, last + 1).toList()
        val rest = measures.take(from) + measures.drop(last + 1)
        val position = to.coerceIn(0, rest.size)
        return normalizePickup(rest.take(position) + block + rest.drop(position))
    }

    /**
     * Asigna la **voz** [voice] (1–4) a la casilla [slotIndex] del compás [measureIndex]. La voz es
     * una propiedad de la **posición** (el acorde), como en MuseScore: todas las notas de la casilla
     * suenan en la misma voz.
     */
    fun setSlotVoice(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        voice: Int
    ): List<TabMeasure> {
        val safe = voice.coerceIn(1, TabVoiceColors.VOICE_COUNT)
        return updateSlot(measures, measureIndex, slotIndex) { it.copy(voice = safe) }
    }

    /** Asigna la **voz** [voice] a todas las casillas de los compases del rango [range]. */
    fun setVoiceInRange(measures: List<TabMeasure>, range: IntRange, voice: Int): List<TabMeasure> {
        if (measures.isEmpty()) return measures
        val selection = TablatureSelection(range).clamped(measures.size) ?: return measures
        val safe = voice.coerceIn(1, TabVoiceColors.VOICE_COUNT)
        val out = measures.toMutableList()
        for (index in selection.range) {
            val measure = out[index]
            if (measure.slots.all { it.voice == safe }) continue
            out[index] = measure.copy(slots = measure.slots.map { it.copy(voice = safe) })
        }
        return out
    }

    /**
     * Marca (`true`) o desmarca (`false`) el compás [measureIndex] como **inicio de sistema**
     * (`forceSystemBreak`): la maquetación ([TablatureSystemsLayout]) empieza un sistema nuevo en él.
     * Es el «Añadir salto de sistema» de MuseScore (`Return`).
     */
    fun setForceSystemBreak(
        measures: List<TabMeasure>,
        measureIndex: Int,
        force: Boolean
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) { it.copy(forceSystemBreak = force) }

    /** Alterna el **salto de sistema** del compás [measureIndex] (`Return` de MuseScore). */
    fun toggleForceSystemBreak(measures: List<TabMeasure>, measureIndex: Int): List<TabMeasure> =
        updateMeasure(measures, measureIndex) { it.copy(forceSystemBreak = !it.forceSystemBreak) }

    /**
     * Marca (`true`) o desmarca (`false`) el compás [measureIndex] como **inicio de página**
     * (`forcePageBreak`): la exportación a PDF empieza hoja en él. Es el `Ctrl`+`Return` de MuseScore.
     *
     * Un salto de página **implica** salto de sistema, así que al marcarlo se marca también
     * [TabMeasure.forceSystemBreak] y al desmarcarlo se desmarcan los dos: en MuseScore el
     * `LayoutBreak` de página también cierra la línea, y dejar el de línea suelto tras quitar el de
     * página daría un salto de sistema que el usuario no pidió.
     */
    fun setForcePageBreak(
        measures: List<TabMeasure>,
        measureIndex: Int,
        force: Boolean
    ): List<TabMeasure> = updateMeasure(measures, measureIndex) {
        it.copy(forcePageBreak = force, forceSystemBreak = if (force) true else false)
    }

    /** Alterna el **salto de página** del compás [measureIndex] (`Ctrl`+`Return` de MuseScore). */
    fun toggleForcePageBreak(measures: List<TabMeasure>, measureIndex: Int): List<TabMeasure> {
        val measure = measures.getOrNull(measureIndex) ?: return measures
        return setForcePageBreak(measures, measureIndex, !measure.forcePageBreak)
    }

    /**
     * Quita la **columna** (evento) [slotIndex] del compás [measureIndex]. Si el compás se queda sin
     * columnas, se conserva (un compás vacío es válido: es un silencio).
     */
    fun removeColumn(measures: List<TabMeasure>, measureIndex: Int, slotIndex: Int): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val measure = measures[measureIndex]
        if (slotIndex !in measure.slots.indices) return measures
        val slots = measure.slots.toMutableList().also { it.removeAt(slotIndex) }
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = slots) }
    }

    /** Indica si la tablatura tiene al menos una nota sonora. */
    fun hasContent(measures: List<TabMeasure>): Boolean = measures.any { measure ->
        measure.slots.any { it.strings.isNotEmpty() }
    }

    // ── Selección de notas (Fase 2, ADR-020) ───────────────────────────────────────

    /**
     * **Borra** las notas de la selección [selection], dejando las columnas (vacías o con las otras
     * cuerdas del acorde). Es el `Supr` de MuseScore cuando hay notas seleccionadas: se borra **solo lo
     * seleccionado**, no el compás entero.
     */
    fun removeSelectedNotes(
        measures: List<TabMeasure>,
        selection: TabNoteSelection
    ): List<TabMeasure> {
        if (selection.isEmpty) return measures
        // Se agrupa por compás y columna para hacer una sola pasada por cada casilla.
        val bySlot = selection.notes.groupBy { it.measureIndex to it.slotIndex }
        var result = measures
        bySlot.forEach { (position, refs) ->
            val (measureIndex, slotIndex) = position
            val strings = refs.map { it.stringNumber }.toSet()
            result = updateSlot(result, measureIndex, slotIndex) { slot ->
                slot.copy(strings = slot.strings.filterNot { it.stringNumber in strings })
            }
        }
        return result
    }

    /**
     * **Copia** las notas de la selección [selection] al portapapeles de notas: guarda la pulsación
     * completa (con técnicas, digitación y figura) y su **desplazamiento** desde la nota de anclaje,
     * para poder pegarlas en otra posición conservando la forma.
     */
    fun copySelectedNotes(
        measures: List<TabMeasure>,
        selection: TabNoteSelection
    ): TabNoteClipboard {
        if (selection.isEmpty) return TabNoteClipboard(emptyList(), TabNoteRef(0, 0, 1))
        // Anclaje: la nota más arriba y a la izquierda de la selección (orden de lectura).
        val anchor = selection.notes.minWithOrNull(
            compareBy({ it.measureIndex }, { it.slotIndex }, { it.stringNumber })
        ) ?: return TabNoteClipboard(emptyList(), TabNoteRef(0, 0, 1))
        val entries = selection.notes.mapNotNull { ref ->
            val pluck = measures.getOrNull(ref.measureIndex)?.slots?.getOrNull(ref.slotIndex)
                ?.strings?.firstOrNull { it.stringNumber == ref.stringNumber } ?: return@mapNotNull null
            val figure = measures.getOrNull(ref.measureIndex)?.slots?.getOrNull(ref.slotIndex)?.rhythmicFigure
            TabNoteClipboardEntry(
                measureOffset = ref.measureIndex - anchor.measureIndex,
                slotOffset = ref.slotIndex - anchor.slotIndex,
                pluck = pluck,
                rhythmicFigure = figure
            )
        }.sortedWith(compareBy({ it.measureOffset }, { it.slotOffset }, { it.pluck.stringNumber }))
        return TabNoteClipboard(entries, anchor)
    }

    /**
     * **Pega** un portapapeles de notas en la posición [target], conservando los desplazamientos
     * relativos a su anclaje. Cada nota se escribe con [setPluck], así que **crea** las columnas que
     * hagan falta y respeta las técnicas y la digitación copiadas.
     */
    fun pasteNotes(
        measures: List<TabMeasure>,
        clipboard: TabNoteClipboard,
        target: TabNoteRef
    ): List<TabMeasure> {
        if (clipboard.isEmpty) return measures
        var result = measures
        clipboard.notes.forEach { entry ->
            val measureIndex = target.measureIndex + entry.measureOffset
            val slotIndex = target.slotIndex + entry.slotOffset
            if (measureIndex in result.indices) {
                result = setPluck(result, measureIndex, slotIndex, entry.pluck)
                entry.rhythmicFigure?.let { figure ->
                    result = setRhythmicFigure(result, measureIndex, slotIndex, figure)
                }
            }
        }
        return result
    }

    /**
     * **Voz** de un compás: la de su primera casilla **con contenido** (o la de la primera casilla, o
     * la 1). Los silencios de relleno que crea [rebalance] heredan esta voz, de modo que escribir en
     * la voz 2 no devuelve el compás a la voz 1.
     */
    private fun voiceOf(measure: TabMeasure): Int =
        measure.slots.firstOrNull { it.strings.isNotEmpty() || it.isHeld }?.voice
            ?: measure.slots.firstOrNull()?.voice
            ?: 1

    private inline fun updateSlot(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        transform: (TabSlot) -> TabSlot
    ): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        val measure = measures[measureIndex]
        if (slotIndex !in measure.slots.indices) return measures
        val newSlots = measure.slots.toMutableList()
        newSlots[slotIndex] = transform(newSlots[slotIndex])
        // `measure.copy` conserva repeat/ending: editar una casilla nunca borra las marcas.
        return measures.toMutableList().also { it[measureIndex] = measure.copy(slots = newSlots) }
    }

    /** Aplica una transformación a un compás entero, conservando el resto. */
    private inline fun updateMeasure(
        measures: List<TabMeasure>,
        measureIndex: Int,
        transform: (TabMeasure) -> TabMeasure
    ): List<TabMeasure> {
        if (measureIndex !in measures.indices) return measures
        return measures.toMutableList().also { it[measureIndex] = transform(measures[measureIndex]) }
    }
}
