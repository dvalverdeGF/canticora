package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
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
 * Parser de **TuxGuitar** (`.tg`) → [Tablature].
 *
 * Un `.tg` es un ZIP con `song.tg` dentro, un XML con la pista, sus cuerdas y sus compases. Aquí se
 * parsea el **XML ya extraído**; el ZIP lo abre la capa de datos.
 *
 * Como los demás parsers de partituras, es **tolerante**: TuxGuitar guarda efectos, letras,
 * marcadores, cambios de instrumento y percusión que Canticora no modela, y todos ellos se
 * **ignoran en silencio**.
 *
 * Detalles del formato que se tienen en cuenta:
 *  - `<strings><string><number>1</number><value>64</value></string>`: el `value` es la nota MIDI al
 *    aire y `number` numera **1 = la cuerda más aguda**, igual que [TabPosition].
 *  - `<duration>` no son divisiones sino el **denominador** de la figura (`1` redonda, `2` blanca,
 *    `4` negra, `8` corchea, `16` semicorchea).
 *  - Las notas de un acorde viajan como varios `<note>` dentro de `<notes>`; las versiones antiguas
 *    del formato las escriben como `<string>`/`<value>` sueltos, y también se aceptan.
 */
object TuxGuitarTabParser {

    /** Convierte el `song.tg` en una [Tablature] del canto [songId]. */
    fun parse(
        xml: String,
        songId: String,
        title: String,
        tuning: Tuning = Tuning.GUITAR,
        resolution: TabResolution = TabResolution.SIXTEENTH
    ): Tablature {
        val track = trackBlock(xml)
        val parsedTuning = parseTuning(track) ?: tuning
        val time = parseTime(track) ?: ScoreTimeSignature("4", 4)
        val measures = TabXml.blocks(track, "measure").mapIndexed { index, measureXml ->
            val header = TabXml.firstBlock(measureXml, "header")
            TabMeasure(
                slots = readSlots(measureXml),
                tempo = if (index == 0) header?.let { TabXml.intChild(it, "tempo") } else null,
                time = header?.let(::parseTime)?.takeIf { it != time }
            )
        }.dropLastWhile { it.slots.isEmpty() }

        return Tablature(
            id = "tuxguitar",
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

    /** La pista que declara cuerdas; la primera si ninguna lo hace. */
    private fun trackBlock(xml: String): String {
        val tracks = TabXml.blocks(xml, "track")
        if (tracks.isEmpty()) return xml
        return tracks.firstOrNull { it.contains("<strings>") } ?: tracks.first()
    }

    /**
     * Afinación de la pista. Las cuerdas se ordenan **de grave a aguda** por su nota MIDI, sin
     * depender del orden en que las escriba el fichero.
     */
    private fun parseTuning(track: String): Tuning? {
        val container = TabXml.firstBlock(track, "strings") ?: return null
        val midi = TabXml.blocks(container, "string")
            .mapNotNull { TabXml.intChild(it, "value") }
            .sorted()
        if (midi.size < 4) return null
        return Tuning(
            name = TabXml.child(track, "name")?.takeIf { it.isNotBlank() } ?: "Guitarra",
            strings = midi.map { TuningString(ScorePitch.fromMidi(it)) }
        )
    }

    /** `<time-signature><numerator>4</numerator><denominator>4</denominator></time-signature>`. */
    private fun parseTime(xml: String): ScoreTimeSignature? {
        val block = TabXml.firstBlock(xml, "time-signature") ?: return null
        val numerator = TabXml.intChild(block, "numerator") ?: return null
        val denominator = TabXml.intChild(block, "denominator") ?: return null
        if (numerator <= 0 || denominator <= 0) return null
        return ScoreTimeSignature(numerator.toString(), denominator)
    }

    /** Columnas de un `<measure>`: los `<beat>` de su primera `<voice>`. */
    private fun readSlots(measureXml: String): List<TabSlot> {
        val voice = TabXml.firstBlock(measureXml, "voices")?.let { TabXml.firstBlock(it, "voice") }
            ?: return emptyList()
        val beats = TabXml.firstBlock(voice, "beats")?.let { TabXml.blocks(it, "beat") }
            ?: return emptyList()

        return beats.map { beat ->
            val figure = figureOf(TabXml.intChild(beat, "duration"))
            val dotted = TabXml.child(beat, "dotted")?.equals("true", ignoreCase = true) == true
            val tuplet = TabXml.intChild(beat, "tuplet")
                ?.takeIf { it > 2 }
                ?.let { TabTuplet(it, 2) }
            val plucks = readPlucks(beat)
            if (plucks.isEmpty()) {
                TabSlot(
                    rhythmicFigure = TabXml.restOf(figure ?: RhythmFigure.QUARTER),
                    tuplet = tuplet
                )
            } else {
                TabSlot(
                    strings = plucks,
                    rhythmicFigure = if (dotted) dottedOf(figure) else figure,
                    tuplet = tuplet
                )
            }
        }
    }

    /** Las notas de un `<beat>`: acorde en `<notes>` o, en formatos antiguos, sueltas. */
    private fun readPlucks(beat: String): List<TabPluck> {
        val notes = TabXml.firstBlock(beat, "notes")?.let { TabXml.blocks(it, "note") }.orEmpty()
        val plucks = if (notes.isNotEmpty()) {
            notes.mapNotNull { note ->
                val stringNumber = TabXml.intChild(note, "string") ?: return@mapNotNull null
                val fret = TabXml.intChild(note, "value") ?: return@mapNotNull null
                buildPluck(note, stringNumber, fret)
            }
        } else {
            val stringNumber = TabXml.intChild(beat, "string") ?: return emptyList()
            val fret = TabXml.intChild(beat, "value") ?: return emptyList()
            listOf(buildPluck(beat, stringNumber, fret))
        }
        return plucks
            .filter { it.position.stringNumber >= 1 }
            .distinctBy { it.position.stringNumber }
            .sortedBy { it.position.stringNumber }
    }

    /** Técnicas que tengan equivalente en el modelo; el resto se ignora. */
    private fun buildPluck(xml: String, stringNumber: Int, fret: Int): TabPluck {
        val techniques = mutableSetOf<TabTechnique>()
        if (TabXml.has(xml, "hammer")) techniques += TabTechnique.HAMMER
        if (TabXml.has(xml, "slide")) techniques += TabTechnique.SLIDE
        if (TabXml.has(xml, "bend")) techniques += TabTechnique.BEND
        if (TabXml.has(xml, "harmonic")) techniques += TabTechnique.HARMONIC
        if (TabXml.has(xml, "vibrato")) techniques += TabTechnique.VIBRATO
        if (TabXml.has(xml, "palm-mute")) techniques += TabTechnique.PALM_MUTE
        if (TabXml.has(xml, "trill")) techniques += TabTechnique.TR
        if (TabXml.has(xml, "tapping")) techniques += TabTechnique.TAPPING
        return TabPluck(
            position = TabPosition(stringNumber, fret.coerceAtLeast(0)),
            techniques = techniques,
            tiedToNext = TabXml.child(xml, "tied")?.equals("true", ignoreCase = true) == true
        )
    }

    /** `<duration>` de TuxGuitar → figura (el valor es el **denominador**: 4 = negra). */
    private fun figureOf(duration: Int?): RhythmFigure? = when (duration) {
        1 -> RhythmFigure.WHOLE
        2 -> RhythmFigure.HALF
        4 -> RhythmFigure.QUARTER
        8 -> RhythmFigure.EIGHTH
        16 -> RhythmFigure.SIXTEENTH
        else -> null
    }

    /** Versión con puntillo de una figura, si el modelo la tiene. */
    private fun dottedOf(figure: RhythmFigure?): RhythmFigure? = when (figure) {
        RhythmFigure.HALF -> RhythmFigure.DOTTED_HALF
        RhythmFigure.QUARTER -> RhythmFigure.DOTTED_QUARTER
        RhythmFigure.EIGHTH -> RhythmFigure.DOTTED_EIGHTH
        else -> figure
    }
}
