package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature

/**
 * Escribe una [Tablature] como **MusicXML** (`score-partwise`), el formato que abren MuseScore,
 * Guitar Pro, TuxGuitar, Sibelius, Finale y la mayoría de visores de partituras.
 *
 * Es la contrapartida de [MusicXmlTabParser] y el formato que se comparte al pulsar «Compartir
 * tablatura»: un `.musicxml` de texto plano que **cualquiera** puede abrir, no un formato cerrado de
 * Canticora. El viaje de ida y vuelta (escribir → leer) es una prueba de que no se pierde nada.
 *
 * Decisiones de escritura:
 *  - `divisions = 24`: da enteros exactos para la negra (24), la corchea (12), la semicorchea (6),
 *    el puntillo (×1,5) y el **tresillo** (×2/3 → 8). Con `divisions = 1` los grupos irregulares
 *    quedarían con duraciones truncadas.
 *  - Las columnas se escriben **en una sola voz**, en el orden del editor: el modelo es una rejilla
 *    de columnas, no una partitura polifónica, y así el resultado se lee igual que en Canticora.
 *  - Cada nota lleva `<pitch>` **y** `<technical><string>/<fret>`, porque algunos lectores exigen la
 *    altura aunque el pentagrama sea de tablatura.
 */
object MusicXmlTabWriter {

    /** Divisiones por negra: admite puntillos y tresillos con números enteros. */
    private const val DIVISIONS = 24

    /** Convierte la tablatura en el texto de un `.musicxml` completo. */
    fun write(tablature: Tablature): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine(
            "<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" " +
                "\"http://www.musicxml.org/dtds/partwise.dtd\">"
        )
        appendLine("<score-partwise version=\"4.0\">")
        appendLine("  <work><work-title>${escape(tablature.title)}</work-title></work>")
        appendLine("  <identification>")
        appendLine("    <encoding><software>Canticora</software></encoding>")
        appendLine("  </identification>")
        appendLine("  <part-list>")
        appendLine("    <score-part id=\"P1\">")
        appendLine("      <part-name>${escape(tablature.instrument)}</part-name>")
        appendLine("      <score-instrument id=\"P1-I1\">")
        appendLine("        <instrument-name>${escape(tablature.instrument)}</instrument-name>")
        appendLine("      </score-instrument>")
        appendLine("      <midi-instrument id=\"P1-I1\">")
        appendLine("        <midi-channel>1</midi-channel>")
        appendLine("        <midi-program>25</midi-program>")
        appendLine("      </midi-instrument>")
        appendLine("    </score-part>")
        appendLine("  </part-list>")
        appendLine("  <part id=\"P1\">")

        tablature.measures.forEachIndexed { index, measure ->
            writeMeasure(
                builder = this,
                measure = measure,
                number = if (index == 0 && tablature.startsWithPickup) "0" else "${index + 1}",
                isFirst = index == 0,
                tablature = tablature
            )
        }

        appendLine("  </part>")
        appendLine("</score-partwise>")
    }

    private fun writeMeasure(
        builder: StringBuilder,
        measure: TabMeasure,
        number: String,
        isFirst: Boolean,
        tablature: Tablature
    ) {
        val implicit = if (number == "0") " implicit=\"yes\"" else ""
        builder.appendLine("    <measure number=\"$number\"$implicit>")

        if (isFirst) {
            builder.appendLine("      <attributes>")
            builder.appendLine("        <divisions>$DIVISIONS</divisions>")
            builder.appendLine("        <key><fifths>0</fifths></key>")
            builder.appendLine(
                "        <time><beats>${tablature.time.beats}</beats>" +
                    "<beat-type>${tablature.time.beatType}</beat-type></time>"
            )
            builder.appendLine("        <clef><sign>TAB</sign><line>5</line></clef>")
            builder.appendLine("        <staff-details>")
            builder.appendLine("          <staff-lines>${tablature.tuning.stringCount}</staff-lines>")
            tablature.tuning.strings.reversed().forEachIndexed { line, string ->
                builder.appendLine("          <staff-tuning line=\"${line + 1}\">")
                builder.appendLine(
                    "            <tuning-step>${string.pitch.step}</tuning-step>"
                )
                if (string.pitch.alter != 0) {
                    builder.appendLine(
                        "            <tuning-alter>${string.pitch.alter}</tuning-alter>"
                    )
                }
                builder.appendLine(
                    "            <tuning-octave>${string.pitch.octave}</tuning-octave>"
                )
                builder.appendLine("          </staff-tuning>")
            }
            builder.appendLine("        </staff-details>")
            builder.appendLine("      </attributes>")
        } else {
            measure.time?.let { time ->
                builder.appendLine("      <attributes>")
                builder.appendLine(
                    "        <time><beats>${time.beats}</beats>" +
                        "<beat-type>${time.beatType}</beat-type></time>"
                )
                builder.appendLine("      </attributes>")
            }
        }

        measure.repeat?.let { repeat ->
            if (repeat == TabRepeatMark.START || repeat == TabRepeatMark.BOTH) {
                builder.appendLine("      <barline location=\"left\">")
                builder.appendLine("        <bar-style>heavy-light</bar-style>")
                builder.appendLine("        <repeat direction=\"forward\"/>")
                builder.appendLine("      </barline>")
            }
        }

        measure.tempo?.let { bpm ->
            builder.appendLine("      <direction placement=\"above\">")
            builder.appendLine("        <direction-type>")
            builder.appendLine("          <metronome>")
            builder.appendLine("            <beat-unit>quarter</beat-unit>")
            builder.appendLine("            <per-minute>$bpm</per-minute>")
            builder.appendLine("          </metronome>")
            builder.appendLine("        </direction-type>")
            builder.appendLine("        <sound tempo=\"$bpm\"/>")
            builder.appendLine("      </direction>")
        }

        measure.dynamic?.let { dynamic ->
            builder.appendLine("      <direction placement=\"below\">")
            builder.appendLine("        <direction-type>")
            builder.appendLine("          <dynamics><${dynamic.glyph}/></dynamics>")
            builder.appendLine("        </direction-type>")
            builder.appendLine("      </direction>")
        }

        measure.slots.forEach { slot ->
            val figure = slot.rhythmicFigure ?: RhythmFigure.QUARTER
            val type = typeNameOf(figure)
            val duration = durationOf(figure, slot.tuplet)

            if (slot.strings.isEmpty()) {
                builder.appendLine("      <note>")
                builder.appendLine("        <rest/>")
                builder.appendLine("        <duration>$duration</duration>")
                builder.appendLine("        <voice>1</voice>")
                builder.appendLine("        <type>$type</type>")
                if (figure.isDotted) builder.appendLine("        <dot/>")
                writeTuplet(builder, slot.tuplet)
                builder.appendLine("      </note>")
            } else {
                slot.strings.forEachIndexed { noteIndex, pluck ->
                    writeNote(
                        builder = builder,
                        pluck = pluck,
                        figure = figure,
                        type = type,
                        duration = duration,
                        isChordMember = noteIndex > 0,
                        tablature = tablature,
                        tuplet = slot.tuplet
                    )
                }
            }
        }

        measure.repeat?.let { repeat ->
            if (repeat == TabRepeatMark.END || repeat == TabRepeatMark.BOTH) {
                builder.appendLine("      <barline location=\"right\">")
                builder.appendLine("        <bar-style>light-heavy</bar-style>")
                builder.appendLine("        <repeat direction=\"backward\"/>")
                builder.appendLine("      </barline>")
            }
        }

        if (measure.barline != TabBarline.SINGLE && measure.repeat == null) {
            val style = if (measure.barline == TabBarline.FINAL) "light-heavy" else "light-light"
            builder.appendLine("      <barline location=\"right\">")
            builder.appendLine("        <bar-style>$style</bar-style>")
            builder.appendLine("      </barline>")
        }

        builder.appendLine("    </measure>")
    }

    private fun writeNote(
        builder: StringBuilder,
        pluck: TabPluck,
        figure: RhythmFigure,
        type: String,
        duration: Int,
        isChordMember: Boolean,
        tablature: Tablature,
        tuplet: com.grafitto.laudate.domain.model.tablature.TabTuplet?
    ) {
        val pitch = pitchOf(pluck, tablature)
        builder.appendLine("      <note>")
        if (isChordMember) builder.appendLine("        <chord/>")
        if (pitch != null) {
            builder.appendLine("        <pitch>")
            builder.appendLine("          <step>${pitch.step}</step>")
            if (pitch.alter != 0) builder.appendLine("          <alter>${pitch.alter}</alter>")
            builder.appendLine("          <octave>${pitch.octave}</octave>")
            builder.appendLine("        </pitch>")
        }
        builder.appendLine("        <duration>$duration</duration>")
        if (pluck.tiedToNext) builder.appendLine("        <tie type=\"start\"/>")
        builder.appendLine("        <voice>1</voice>")
        builder.appendLine("        <type>$type</type>")
        if (figure.isDotted) builder.appendLine("        <dot/>")
        writeTuplet(builder, tuplet)

        builder.appendLine("        <notations>")
        if (pluck.tiedToNext) builder.appendLine("          <tied type=\"start\"/>")
        builder.appendLine("          <technical>")
        builder.appendLine("            <string>${pluck.stringNumber}</string>")
        builder.appendLine("            <fret>${pluck.fret}</fret>")
        when {
            TabTechnique.HAMMER in pluck.techniques -> {
                builder.appendLine("            <hammer-on type=\"start\">H</hammer-on>")
            }
            TabTechnique.PULL in pluck.techniques -> {
                builder.appendLine("            <pull-off type=\"start\">P</pull-off>")
            }
            TabTechnique.SLIDE in pluck.techniques -> {
                builder.appendLine("            <slide type=\"start\" number=\"1\">S</slide>")
            }
            TabTechnique.HARMONIC in pluck.techniques -> builder.appendLine("            <harmonic/>")
            TabTechnique.TAPPING in pluck.techniques -> builder.appendLine("            <tap hand=\"right\"/>")
            TabTechnique.TR in pluck.techniques -> builder.appendLine("            <trill-mark/>")
            else -> Unit
        }
        pluck.frettingFinger?.let {
            builder.appendLine("            <fingering>${it.number}</fingering>")
        }
        builder.appendLine("          </technical>")
        builder.appendLine("        </notations>")
        builder.appendLine("      </note>")
    }

    private fun writeTuplet(
        builder: StringBuilder,
        tuplet: com.grafitto.laudate.domain.model.tablature.TabTuplet?
    ) {
        if (tuplet == null) return
        builder.appendLine("        <time-modification>")
        builder.appendLine("          <actual-notes>${tuplet.numNotes}</actual-notes>")
        builder.appendLine("          <normal-notes>${tuplet.normalNotes}</normal-notes>")
        builder.appendLine("        </time-modification>")
    }

    /** Altura que suena al pisar [pluck] en la afinación de [tablature]. */
    private fun pitchOf(pluck: TabPluck, tablature: Tablature): ScorePitch? {
        if (pluck.isMuted) return null
        val open = tablature.tuning.pitchOf(pluck.stringNumber) ?: return null
        return if (pluck.fret == 0) open else ScorePitch.fromMidi(open.midi + pluck.fret)
    }

    /** Nombre de `<type>` de MusicXML para una figura (por duración, así sirve también los silencios). */
    private fun typeNameOf(figure: RhythmFigure): String = when {
        figure.beatValue >= 4.0 -> "whole"
        figure.beatValue >= 2.0 -> "half"
        figure.beatValue >= 1.0 -> "quarter"
        figure.beatValue >= 0.5 -> "eighth"
        else -> "16th"
    }

    /** Duración en divisiones, ajustada al grupo irregular si lo hay. */
    private fun durationOf(
        figure: RhythmFigure,
        tuplet: com.grafitto.laudate.domain.model.tablature.TabTuplet?
    ): Int {
        val base = (figure.beatValue * DIVISIONS).toInt().coerceAtLeast(1)
        if (tuplet == null || tuplet.numNotes <= 0) return base
        return (base * tuplet.normalNotes / tuplet.numNotes).coerceAtLeast(1)
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
