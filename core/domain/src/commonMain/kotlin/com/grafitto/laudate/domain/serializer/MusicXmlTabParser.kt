package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabFrettingFinger
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TuningString
import com.grafitto.laudate.domain.tablature.TabSymbolCompatibility

/**
 * Parser de **MusicXML** (`.musicxml`, `.xml`, `.mxl`) → [Tablature].
 *
 * MusicXML es el **puente universal**: MuseScore, Guitar Pro, TuxGuitar, Sibelius y Finale lo
 * exportan, así que este parser es la vía por la que entra cualquier partitura que no tenga un
 * parser propio. Lo que no se entiende —adornos, marcas de ensayo, símbolos de Guitar Pro que
 * MusicXML no estandariza— se **ignora en silencio**: la tablatura se importa igual y nunca se
 * muestra un error por un símbolo desconocido.
 *
 * La lectura es **por bloques** ([TabXml]), sin DOM: el Dominio es Kotlin puro y el formato es
 * plano y estable.
 *
 * Notas de formato que se tienen en cuenta:
 *  - `<string>` de MusicXML ya numera **1 = la cuerda más aguda**, igual que [TabPosition], así que
 *    no hay conversión (a diferencia del `.mscx` de MuseScore, que numera desde 0).
 *  - `<duration>` se mide en **divisiones** (`<divisions>`), y `<backup>`/`<forward>` mueven el cursor
 *    cuando hay varias voces; por eso las notas se agrupan por **posición temporal** y no por orden
 *    de aparición.
 */
object MusicXmlTabParser {

    /** Una nota ya leída de un compás, con su posición temporal en divisiones. */
    private data class Note(
        val start: Int,
        val figure: RhythmFigure?,
        val tuplet: TabTuplet?,
        val voice: Int,
        val isRest: Boolean,
        val plucks: List<TabPluck>
    )

    /**
     * Convierte el XML de una partitura en una [Tablature] del canto [songId].
     *
     * @param tuning afinación de reserva: si el fichero declara `<staff-tuning>` se usa la suya.
     */
    fun parse(
        xml: String,
        songId: String,
        title: String,
        tuning: Tuning = Tuning.GUITAR,
        resolution: TabResolution = TabResolution.SIXTEENTH
    ): Tablature {
        val part = partBlock(xml)
        var divisions = 1
        var currentTime = ScoreTimeSignature("4", 4)
        var currentTuning = tuning

        val measures = TabXml.blocks(part, "measure").mapIndexed { index, measureXml ->
            val previousTime = currentTime
            val attributes = TabXml.firstBlock(measureXml, "attributes")
            val declaredTime = attributes?.let(::parseTime)
            attributes?.let {
                TabXml.intChild(it, "divisions")?.takeIf { value -> value > 0 }?.let { value ->
                    divisions = value
                }
                declaredTime?.let { time -> currentTime = time }
                parseTuning(it)?.let { parsed -> currentTuning = parsed }
            }
            val notes = readNotes(measureXml, divisions)

            TabMeasure(
                slots = groupIntoSlots(notes),
                time = declaredTime?.takeIf { it != previousTime },
                repeat = readRepeat(measureXml),
                barline = readBarline(measureXml),
                dynamic = readDynamic(measureXml),
                tempo = readTempo(measureXml),
                tempoText = readTempoText(measureXml),
                text = readWords(measureXml),
                isPickup = isPickup(measureXml, index)
            )
        }.dropLastWhile { it.slots.isEmpty() }

        return Tablature(
            id = "musicxml",
            songId = songId,
            title = title,
            instrument = currentTuning.name,
            tuning = currentTuning,
            time = currentTime,
            resolution = resolution,
            measures = measures,
            origin = TabOrigin.IMPORTED_TEXT
        )
    }

    // ── Localización de la parte con tablatura ───────────────────────────────────────────────────

    /**
     * La `<part>` que **lleva tablatura** (la que tiene `<fret>` dentro de `<technical>`), o la
     * primera si ninguna lo declara. Una partitura orquestal trae varias partes y solo una suele
     * ser la del instrumento de cuerda.
     */
    private fun partBlock(xml: String): String {
        val parts = TabXml.blocks(xml, "part")
        if (parts.isEmpty()) return xml
        return parts.firstOrNull { it.contains("<fret>") } ?: parts.first()
    }

    // ── Notas ───────────────────────────────────────────────────────────────────────────────────

    /** Recorre `<note>`, `<backup>` y `<forward>` **en orden** para respetar las voces. */
    private fun readNotes(measureXml: String, divisions: Int): List<Note> {
        val events = Regex(
            "<(note|backup|forward)(?:\\s[^>]*)?>(.*?)</\\1>",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(measureXml).toList()

        val notes = mutableListOf<Note>()
        var cursor = 0
        events.forEach { event ->
            val body = event.groupValues[2]
            when (event.groupValues[1]) {
                "backup" -> cursor -= TabXml.intChild(body, "duration") ?: 0
                "forward" -> cursor += TabXml.intChild(body, "duration") ?: 0
                else -> {
                    val duration = TabXml.intChild(body, "duration") ?: 0
                    val isChord = TabXml.has(body, "chord")
                    val start = if (isChord) cursor - duration else cursor
                    if (!isChord) cursor += duration
                    notes += readNote(body, start)
                }
            }
        }
        return notes
    }

    /** Convierte un `<note>` en la nota de trabajo, con sus pulsaciones y técnicas. */
    private fun readNote(body: String, start: Int): Note {
        val dots = Regex("<dot\\s*/>").findAll(body).count()
        val figure = TabXml.figureOf(TabXml.child(body, "type"), dots)
        val technical = TabXml.firstBlock(body, "technical")

        val stringNumber = technical?.let { TabXml.intChild(it, "string") }
        val fret = technical?.let { TabXml.intChild(it, "fret") }
        val isRest = TabXml.has(body, "rest") || stringNumber == null || fret == null

        return Note(
            start = start,
            figure = figure,
            tuplet = readTuplet(body),
            voice = TabXml.intChild(body, "voice") ?: 1,
            isRest = isRest,
            plucks = if (isRest) {
                emptyList()
            } else {
                listOf(buildPluck(body, technical, stringNumber!!, fret!!))
            }
        )
    }

    /** Pulsación de una nota: posición, técnicas de `<technical>` y articulaciones. */
    private fun buildPluck(
        body: String,
        technical: String?,
        stringNumber: Int,
        fret: Int
    ): TabPluck {
        val techniques = mutableSetOf<TabTechnique>()
        if (technical != null) {
            if (TabXml.has(technical, "hammer-on")) techniques += TabTechnique.HAMMER
            if (TabXml.has(technical, "pull-off")) techniques += TabTechnique.PULL
            if (TabXml.has(technical, "slide")) techniques += TabTechnique.SLIDE
            if (TabXml.has(technical, "bend")) techniques += TabTechnique.BEND
            if (TabXml.has(technical, "harmonic")) techniques += TabTechnique.HARMONIC
            if (TabXml.has(technical, "tap")) techniques += TabTechnique.TAPPING
            if (TabXml.has(technical, "trill")) techniques += TabTechnique.TR
        }
        if (TabXml.has(body, "vibrato")) techniques += TabTechnique.VIBRATO
        if (TabXml.has(body, "let-ring")) techniques += TabTechnique.LET_RING

        val articulations = mutableSetOf<TabArticulation>()
        TabXml.firstBlock(body, "articulations")?.let { block ->
            if (TabXml.has(block, "accent")) articulations += TabArticulation.ACCENT
            if (TabXml.has(block, "strong-accent")) articulations += TabArticulation.MARCATO
            if (TabXml.has(block, "staccato")) articulations += TabArticulation.STACCATO
            if (TabXml.has(block, "tenuto")) articulations += TabArticulation.TENUTO
        }

        val fingering = technical?.let { TabXml.intChild(it, "fingering") }
        return TabPluck(
            position = TabPosition(stringNumber, fret),
            techniques = techniques,
            articulations = TabSymbolCompatibility.join(articulations),
            frettingFinger = when (fingering) {
                1 -> TabFrettingFinger.INDEX
                2 -> TabFrettingFinger.MIDDLE
                3 -> TabFrettingFinger.RING
                4 -> TabFrettingFinger.PINKY
                else -> null
            },
            tiedToNext = Regex("<tied\\s+type\\s*=\\s*\"start\"").containsMatchIn(body)
        )
    }

    /** `<time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes>`. */
    private fun readTuplet(body: String): TabTuplet? {
        val modification = TabXml.firstBlock(body, "time-modification") ?: return null
        val actual = TabXml.intChild(modification, "actual-notes") ?: return null
        val normal = TabXml.intChild(modification, "normal-notes") ?: return null
        if (actual <= 0 || normal <= 0) return null
        return TabTuplet(actual, normal)
    }

    /**
     * Agrupa las notas **por posición temporal**: las que empiezan a la vez forman un acorde y, por
     * tanto, **una sola casilla** de la rejilla.
     */
    private fun groupIntoSlots(notes: List<Note>): List<TabSlot> {
        if (notes.isEmpty()) return emptyList()
        return notes
            .groupBy { it.start }
            // `toSortedMap()` es de la JVM: se ordenan las entradas como lista (mismo resultado).
            .toList()
            .sortedBy { (start, _) -> start }
            .map { (_, group) ->
                val first = group.first()
                val plucks = group.flatMap { it.plucks }
                    .distinctBy { it.position.stringNumber }
                    .sortedBy { it.position.stringNumber }
                TabSlot(
                    strings = plucks,
                    rhythmicFigure = if (first.isRest) {
                        TabXml.restOf(first.figure ?: RhythmFigure.QUARTER)
                    } else {
                        first.figure
                    },
                    tuplet = first.tuplet,
                    voice = first.voice
                )
            }
    }

    // ── Metadatos del compás ────────────────────────────────────────────────────────────────────

    /** `<time><beats>4</beats><beat-type>4</beat-type></time>`. */
    private fun parseTime(xml: String): ScoreTimeSignature? {
        val time = TabXml.firstBlock(xml, "time") ?: return null
        val beats = TabXml.child(time, "beats") ?: return null
        val beatType = TabXml.intChild(time, "beat-type") ?: return null
        return ScoreTimeSignature(beats, beatType)
    }

    /**
     * Afinación declarada por la partitura (`<staff-tuning line="1">`). MusicXML numera las líneas
     * **de arriba abajo** (línea 1 = cuerda más aguda) y aquí se guardan de **grave a aguda**.
     */
    private fun parseTuning(xml: String): Tuning? {
        val details = TabXml.firstBlock(xml, "staff-details") ?: return null
        val lines = TabXml.blocks(details, "staff-tuning")
        if (lines.size < 4) return null
        val strings = lines.mapNotNull { line ->
            val step = TabXml.child(line, "tuning-step")?.firstOrNull() ?: return@mapNotNull null
            val alter = TabXml.intChild(line, "tuning-alter") ?: 0
            val octave = TabXml.intChild(line, "tuning-octave") ?: return@mapNotNull null
            TuningString(ScorePitch(step.uppercaseChar(), alter, octave))
        }
        if (strings.size < 4) return null
        val ordered = strings.reversed()
        // Si la afinación declarada es EXACTAMENTE uno de los presets (guitarra, bajo, ukelele,
        // laúd…), se usa el preset: así la tablatura importada no aparece como «Afinación de la
        // partitura» cuando en realidad es una guitarra estándar. Solo si no coincide con ninguno
        // se conserva una afinación propia con las cuerdas declaradas.
        return Tuning.presets.firstOrNull { preset ->
            preset.strings.size == ordered.size &&
                preset.strings.map { it.pitch } == ordered.map { it.pitch }
        } ?: Tuning(name = "Afinación de la partitura", strings = ordered)
    }

    /** Tempo del compás, por `<sound tempo="80"/>` o por `<metronome><per-minute>80</per-minute>`. */
    private fun readTempo(measureXml: String): Int? {
        TabXml.firstBlock(measureXml, "metronome")?.let { metronome ->
            TabXml.intChild(metronome, "per-minute")?.let { return it }
        }
        val sound = Regex("<sound\\s[^>]*tempo\\s*=\\s*\"([^\"]+)\"").find(measureXml)
        return sound?.groupValues?.get(1)?.toDoubleOrNull()?.let { kotlin.math.round(it).toInt() }
    }

    /** Texto de la indicación de tempo (`<words>` de la primera `<direction>`), si la hay. */
    private fun readTempoText(measureXml: String): String? {
        val direction = TabXml.firstBlock(measureXml, "direction") ?: return null
        if (!TabXml.has(direction, "metronome")) return null
        val words = TabXml.firstBlock(direction, "words") ?: return null
        return TabXml.textOf(words).takeIf { it.isNotBlank() }
    }

    /** Texto libre de una `<direction>` sin metrónomo (marcas de ensayo, «rit.», «solo»…). */
    private fun readWords(measureXml: String): String? {
        val direction = TabXml.firstBlock(measureXml, "direction") ?: return null
        if (TabXml.has(direction, "metronome")) return null
        val words = TabXml.firstBlock(direction, "words") ?: return null
        return TabXml.textOf(words).takeIf { it.isNotBlank() }
    }

    /** `<dynamics><mf/></dynamics>`, con el nombre del hijo como glifo. */
    private fun readDynamic(measureXml: String): TabDynamic? {
        val dynamics = TabXml.firstBlock(measureXml, "dynamics") ?: return null
        val glyph = Regex("<([a-z]+)\\s*/>").find(dynamics)?.groupValues?.get(1) ?: return null
        return TabDynamic.entries.firstOrNull { it.glyph == glyph }
    }

    /** Barras de repetición: `|:` en el compás (izquierda) y `:|` (derecha). */
    private fun readRepeat(measureXml: String): TabRepeatMark? {
        var start = false
        var end = false
        Regex("<repeat\\s[^>]*direction\\s*=\\s*\"(forward|backward)\"").findAll(measureXml).forEach {
            when (it.groupValues[1]) {
                "forward" -> start = true
                "backward" -> end = true
            }
        }
        return when {
            start && end -> TabRepeatMark.BOTH
            start -> TabRepeatMark.START
            end -> TabRepeatMark.END
            else -> null
        }
    }

    /** Barra final (`light-heavy`) o doble (`light-light`) al cierre del compás. */
    private fun readBarline(measureXml: String): TabBarline {
        val style = Regex("<bar-style>([^<]+)</bar-style>").find(measureXml)?.groupValues?.get(1)
        return when (style?.trim()) {
            "light-heavy", "heavy-heavy" -> TabBarline.FINAL
            "light-light" -> TabBarline.DOUBLE
            else -> TabBarline.SINGLE
        }
    }

    /** Anacrusa: MusicXML la marca con `number="0"` o `implicit="yes"` en el primer compás. */
    private fun isPickup(measureXml: String, index: Int): Boolean {
        if (index != 0) return false
        val opening = Regex("<measure(?:\\s[^>]*)?>").find(measureXml)?.value ?: return false
        return opening.contains("implicit=\"yes\"") || opening.contains("number=\"0\"")
    }
}
