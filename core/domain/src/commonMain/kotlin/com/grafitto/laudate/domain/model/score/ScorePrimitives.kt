package com.grafitto.laudate.domain.model.score

import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.rhythm.TimeSignature

/**
 * Primitivas musicales compartidas por el **motor de partituras** (*Feature S*) y el **motor de
 * tablaturas** (*Feature T*). Son **Kotlin puro**: no conocen Android ni la UI.
 *
 * Viven en el paquete `model/score` porque nacieron con las partituras; la tablatura las reutiliza
 * (afinaciones, compás y tonalidad) para no duplicar el vocabulario musical. Ver ADR-017.
 */

/** Clave de un pentagrama. v1 dibuja [TREBLE] y [BASS]; [ALTO]/[TENOR] producen error legible. */
enum class Clef(val sign: Char, val line: Int) {
    TREBLE('G', 2),
    BASS('F', 4),
    ALTO('C', 3),
    TENOR('C', 4);

    companion object {
        /** Claves que el motor de render de v1 sabe dibujar. */
        val renderable: Set<Clef> = setOf(TREBLE, BASS)
    }
}

/**
 * Armadura de una tonalidad: número de alteraciones (positivo = sostenidos, negativo = bemoles)
 * y modo. `fifths` se mueve por el Círculo de Quintas al transponer.
 */
data class ScoreKey(val fifths: Int, val mode: String = MAJOR) {
    /** Indica si la tonalidad está en modo menor (la armadura se recalcula igual, la tónica no cambia). */
    val isMinor: Boolean get() = mode.equals(MINOR, ignoreCase = true)

    companion object {
        const val MAJOR = "major"
        const val MINOR = "minor"
    }
}

/**
 * Compás. `beats` es **texto** para admitir compases compuestos («4», «3+2», «2+2+3»); el pulso
 * simple habitual se obtiene con [beatsAsInt].
 */
data class ScoreTimeSignature(val beats: String, val beatType: Int) {
    /** Número de pulsos si el compás es simple; `null` en un compás compuesto aditivo («3+2»). */
    val beatsAsInt: Int? get() = beats.trim().toIntOrNull()

    /** Duración total del compás en negras deduciendo el resultado de los pulsos aditivos. */
    val totalQuarterBeats: Double
        get() {
            val units = beats.split('+').sumOf { it.trim().toIntOrNull() ?: 0 }
            val effective = if (units > 0) units else (beatsAsInt ?: 0)
            return effective * (4.0 / beatType)
        }

    /**
     * Convierte al compás del motor de ritmos cuando es representable (simple o compuesto
     * uniforme). Devuelve `null` en compases aditivos que [TimeSignature] no modela.
     */
    fun toTimeSignature(): TimeSignature? {
        val numerator = beatsAsInt ?: return null
        return TimeSignature(numerator, beatType)
    }

    override fun toString(): String = "$beats/$beatType"

    companion object {
        /** Crea un compás a partir de un texto «4/4». Devuelve `null` si no es válido. */
        fun parse(raw: String): ScoreTimeSignature? {
            val parts = raw.trim().split('/')
            if (parts.size != 2) return null
            val denominator = parts[1].trim().toIntOrNull() ?: return null
            if (denominator <= 0) return null
            val beats = parts[0].trim()
            if (beats.isEmpty()) return null
            return ScoreTimeSignature(beats, denominator)
        }
    }
}

/** Compás del motor de ritmos → compás del modelo de partituras. */
fun TimeSignature.toScoreTimeSignature(): ScoreTimeSignature = ScoreTimeSignature("$numerator", denominator)

/**
 * Construye la [ScoreKey] (mayor por defecto) cuya **tónica** es el índice cromático dado.
 *
 * La armadura avanza de quinta en quinta (7 semitonos por alteración); la inversa de `×7` en
 * módulo 12 es `×7`, así que `fifths = (índice × 7) mod 12`, normalizado a −6..5. Sirve para
 * fijar la **tonalidad escrita** de una tablatura a partir del tono del canto.
 */
fun scoreKeyOfTonic(chromaticIndex: Int, minor: Boolean = false): ScoreKey {
    val fifthsRaw = ((chromaticIndex * 7) % 12 + 12) % 12
    val majorFifths = if (fifthsRaw > 5) fifthsRaw - 12 else fifthsRaw
    // En modo menor, la tónica está una tercera menor por debajo: la armadura sube 3 quintas.
    val fifths = if (minor) majorFifths - 3 else majorFifths
    val normalized = ((fifths + 6) % 12 + 12) % 12 - 6
    return ScoreKey(normalized, if (minor) ScoreKey.MINOR else ScoreKey.MAJOR)
}

/**
 * Altura musical concreta: nombre (A–G), alteración en semitonos y octava (C4 = Do central = MIDI 60).
 *
 * Es el tipo con el que la tablatura conoce la afinación de cada cuerda y con el que la partitura
 * representa cada nota. El conversor [toPitchClass] tiende el puente con el **motor armónico**
 * existente para que la transposición use el mismo Círculo de Quintas que los acordes.
 */
data class ScorePitch(val step: Char, val alter: Int, val octave: Int) {

    /** Índice cromático 0..11 desde Do (Do=0, Re=2, …, Si=11). */
    val chromaticIndex: Int
        get() {
            val base = when (step.uppercaseChar()) {
                'C' -> 0
                'D' -> 2
                'E' -> 4
                'F' -> 5
                'G' -> 7
                'A' -> 9
                'B' -> 11
                else -> 0
            }
            return (((base + alter) % 12) + 12) % 12
        }

    /** Número MIDI (C4 = 60). Permite comparar alturas y calcular intervalos. */
    val midi: Int
        get() {
            val base = when (step.uppercaseChar()) {
                'C' -> 0
                'D' -> 2
                'E' -> 4
                'F' -> 5
                'G' -> 7
                'A' -> 9
                'B' -> 11
                else -> 0
            }
            return (octave + 1) * 12 + base + alter
        }

    /** Nota del motor armónico (sin octava) equivalente a esta altura. */
    fun toPitchClass(): PitchClass = PitchClass.fromIndex(chromaticIndex)

    override fun toString(): String {
        val accidental = when (alter) {
            1 -> "#"
            2 -> "##"
            -1 -> "b"
            -2 -> "bb"
            else -> ""
        }
        return "${step.uppercaseChar()}$accidental$octave"
    }

    companion object {
        /** Crea una altura a partir de un número MIDI (C4 = 60). La alteración sale enarmónica. */
        fun fromMidi(midi: Int): ScorePitch {
            val octave = midi / 12 - 1
            return when (((midi % 12) + 12) % 12) {
                0 -> ScorePitch('C', 0, octave)
                1 -> ScorePitch('C', 1, octave)
                2 -> ScorePitch('D', 0, octave)
                3 -> ScorePitch('D', 1, octave)
                4 -> ScorePitch('E', 0, octave)
                5 -> ScorePitch('F', 0, octave)
                6 -> ScorePitch('F', 1, octave)
                7 -> ScorePitch('G', 0, octave)
                8 -> ScorePitch('G', 1, octave)
                9 -> ScorePitch('A', 0, octave)
                10 -> ScorePitch('A', 1, octave)
                else -> ScorePitch('B', 0, octave)
            }
        }
    }
}
