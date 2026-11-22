package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TuningString

/**
 * Parser de **Guitar Pro 7 y 8** (`.gp`, `.gpx`) → [Tablature].
 *
 * Un `.gp`/`.gpx` es un ZIP cuyo `Content/score.gpif` es un XML con toda la partitura. Aquí se
 * parsea el **XML ya extraído** (la extracción del ZIP la hace la capa de datos), así que el parser
 * es Kotlin puro.
 *
 * Es un parser **tolerante por contrato**: Guitar Pro tiene cientos de propiedades (`<Property
 * name="Slap">`, articulaciones de bajo, percusión, letras, automatizaciones…) y las que Canticora no
 * modela se **ignoran en silencio**. Nunca lanza por un símbolo desconocido.
 *
 * Lo que sí se lee:
 *  - la **afinación** declarada (`<Property name="Tuning"><Pitches>…`), en MIDI;
 *  - los **compases** (`<Bar>`), sus **tiempos** (`<Rhythm ref>`), las **notas** (`<String>`,
 *    `<Fret>`) y las **técnicas** que tienen equivalente (`HopoOrigin`, `Slide`, `Bend`…);
 *  - el **compás** y el **tempo** del `MasterBar`.
 */
object GpifTabParser {

    /** Convierte el `score.gpif` en una [Tablature] del canto [songId]. */
    fun parse(
        gpif: String,
        songId: String,
        title: String,
        tuning: Tuning = Tuning.GUITAR,
        resolution: TabResolution = TabResolution.SIXTEENTH
    ): Tablature {
        val rhythms = readRhythms(gpif)
        val track = trackBlock(gpif)
        val parsedTuning = parseTuning(track) ?: tuning
        val time = parseTime(gpif) ?: ScoreTimeSignature("4", 4)
        val tempo = TabXml.firstBlock(gpif, "MasterBars")
            ?.let { TabXml.blocks(it, "MasterBar") }
            ?.firstNotNullOfOrNull { TabXml.intChild(it, "Tempo") }
            ?: TabXml.intChild(gpif, "Tempo")

        val measures = TabXml.blocks(track, "Bar").mapIndexed { index, bar ->
            TabMeasure(
                slots = readSlots(bar, rhythms),
                tempo = if (index == 0) tempo else null
            )
        }.dropLastWhile { it.slots.isEmpty() }

        return Tablature(
            id = "gpif",
            songId = songId,
            title = title,
            instrument = parsedTuning.name,
            tuning = parsedTuning,
            time = time,
            resolution = resolution,
            measures = measures,
            origin = TabOrigin.IMPORTED_TEXT
        )
    }

    // ── Pistas y afinación ──────────────────────────────────────────────────────────────────────

    /** La pista que lleva tablatura: la que declara `<Property name="Tuning">`. */
    private fun trackBlock(gpif: String): String {
        val tracks = TabXml.blocks(gpif, "Track")
        if (tracks.isEmpty()) return gpif
        return tracks.firstOrNull { it.contains("name=\"Tuning\"") } ?: tracks.first()
    }

    /**
     * Afinación de la pista: `<Property name="Tuning"><Pitches>40 45 50 55 59 64</Pitches>`.
     *
     * Los valores son **notas MIDI** y se ordenan de **grave a aguda**, que es el orden en que
     * [Tuning] guarda las cuerdas, sin depender de cómo las escriba el fichero.
     */
    private fun parseTuning(track: String): Tuning? {
        val pitches = TabXml.firstBlock(track, "Pitches") ?: return null
        val midi = pitches.split(' ', '\n', '\r', '\t')
            .mapNotNull { it.trim().toIntOrNull() }
            .sorted()
        if (midi.size < 4) return null
        return Tuning(
            name = TabXml.child(track, "Name")?.takeIf { it.isNotBlank() } ?: "Guitarra",
            strings = midi.map { TuningString(ScorePitch.fromMidi(it)) }
        )
    }

    /** `<Time>4/4</Time>` del `MasterBar`. */
    private fun parseTime(gpif: String): ScoreTimeSignature? {
        val raw = TabXml.child(gpif, "Time") ?: return null
        val beats = raw.substringBefore('/').trim().toIntOrNull() ?: return null
        val beatType = raw.substringAfter('/', "4").trim().toIntOrNull() ?: return null
        return ScoreTimeSignature(beats.toString(), beatType)
    }

    // ── Ritmos ──────────────────────────────────────────────────────────────────────────────────

    /** Un ritmo de GPIF ya resuelto: su nombre de figura y su grupo irregular. */
    private data class Rhythm(val noteValue: String?, val dots: Int, val tuplet: TabTuplet?)

    /**
     * Los `<Rhythm id="N">` de la partitura, indexados por id. Los `<Beat>` los referencian con
     * `<Rhythm ref="N"/>`, así que hay que resolverlos **antes** de recorrer los compases.
     */
    private fun readRhythms(gpif: String): Map<String, Rhythm> {
        val container = TabXml.firstBlock(gpif, "Rhythms") ?: return emptyMap()
        return Regex("<Rhythm\\s+id\\s*=\\s*\"(\\d+)\"[^>]*>(.*?)</Rhythm>", RegexOption.DOT_MATCHES_ALL)
            .findAll(container)
            .associate { match ->
                val body = match.groupValues[2]
                match.groupValues[1] to Rhythm(
                    noteValue = TabXml.child(body, "NoteValue"),
                    dots = TabXml.intChild(body, "AugmentationDot") ?: 0,
                    tuplet = readTuplet(body)
                )
            }
    }

    /** `<PrimaryTuplet num="3" den="2"/>` (también admite `<SecondaryTuplet>`). */
    private fun readTuplet(body: String): TabTuplet? {
        val match = Regex("<(?:Primary|Secondary)Tuplet\\s+num\\s*=\\s*\"(\\d+)\"\\s+den\\s*=\\s*\"(\\d+)\"")
            .find(body) ?: return null
        val num = match.groupValues[1].toIntOrNull() ?: return null
        val den = match.groupValues[2].toIntOrNull() ?: return null
        if (num <= 0 || den <= 0) return null
        return TabTuplet(num, den)
    }

    // ── Compases ───────────────────────────────────────────────────────────────────────────────

    /** Las columnas de un `<Bar>`: un `<Beat>` con notas es un acorde; sin notas, una pausa. */
    private fun readSlots(bar: String, rhythms: Map<String, Rhythm>): List<TabSlot> {
        val voice = TabXml.firstBlock(bar, "Voices")?.let { TabXml.firstBlock(it, "Voice") }
            ?: return emptyList()
        val beats = TabXml.firstBlock(voice, "Beats")?.let { TabXml.blocks(it, "Beat") }
            ?: return emptyList()

        return beats.map { beat ->
            val rhythm = readBeatRhythm(beat, rhythms)
            val notes = TabXml.firstBlock(beat, "Notes")?.let { TabXml.blocks(it, "Note") }.orEmpty()
            val plucks = notes.mapNotNull { readPluck(it) }.sortedBy { it.position.stringNumber }
            if (plucks.isEmpty()) {
                TabSlot(
                    rhythmicFigure = TabXml.restOf(
                        TabXml.figureOf(rhythm?.noteValue, rhythm?.dots ?: 0)
                            ?: com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER
                    ),
                    tuplet = rhythm?.tuplet
                )
            } else {
                TabSlot(
                    strings = plucks,
                    rhythmicFigure = TabXml.figureOf(rhythm?.noteValue, rhythm?.dots ?: 0),
                    tuplet = rhythm?.tuplet
                )
            }
        }
    }

    /** El ritmo de un `<Beat>`: por referencia (`<Rhythm ref="N"/>`) o escrito en línea. */
    private fun readBeatRhythm(beat: String, rhythms: Map<String, Rhythm>): Rhythm? {
        Regex("<Rhythm\\s+ref\\s*=\\s*\"(\\d+)\"")
            .find(beat)
            ?.groupValues
            ?.get(1)
            ?.let { ref -> rhythms[ref]?.let { return it } }

        val inline = TabXml.firstBlock(beat, "Rhythm") ?: return null
        return Rhythm(
            noteValue = TabXml.child(inline, "NoteValue"),
            dots = TabXml.intChild(inline, "AugmentationDot") ?: 0,
            tuplet = readTuplet(inline)
        )
    }

    /** Una `<Note>` de GPIF: su cuerda, su traste y las técnicas que tengan equivalente. */
    private fun readPluck(note: String): TabPluck? {
        val properties = TabXml.firstBlock(note, "Properties") ?: note
        val stringNumber = numberProperty(properties, "String") ?: return null
        val fret = numberProperty(properties, "Fret") ?: return null
        if (stringNumber < 1) return null

        val techniques = mutableSetOf<TabTechnique>()
        if (properties.contains("name=\"HopoOrigin\"") || properties.contains("name=\"HopoDestination\"")) {
            techniques += TabTechnique.HAMMER
        }
        if (properties.contains("name=\"Slide\"")) techniques += TabTechnique.SLIDE
        if (properties.contains("name=\"Bend\"")) techniques += TabTechnique.BEND
        if (properties.contains("name=\"Harmonic\"")) techniques += TabTechnique.HARMONIC
        if (properties.contains("name=\"Vibrato\"")) techniques += TabTechnique.VIBRATO
        if (properties.contains("name=\"PalmMute\"") || properties.contains("name=\"PalmMuteOrigin\"")) {
            techniques += TabTechnique.PALM_MUTE
        }
        if (properties.contains("name=\"TieOrigin\"")) techniques += TabTechnique.LET_RING

        return TabPluck(
            position = TabPosition(stringNumber, fret.coerceAtLeast(0)),
            techniques = techniques,
            tiedToNext = properties.contains("name=\"TieOrigin\"")
        )
    }

    /** `<Property name="X"><Number>N</Number></Property>` → N. */
    private fun numberProperty(properties: String, name: String): Int? {
        val match = Regex(
            "<Property\\s+name\\s*=\\s*\"$name\"\\s*>(.*?)</Property>",
            RegexOption.DOT_MATCHES_ALL
        ).find(properties) ?: return null
        return TabXml.intChild(match.groupValues[1], "Number")
    }
}
