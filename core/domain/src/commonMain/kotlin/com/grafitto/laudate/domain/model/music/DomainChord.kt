package com.grafitto.laudate.domain.model.music

/**
 * Representa un acorde parsed en el dominio musical.
 * Desacoplado de la notación de presentación.
 */
data class DomainChord(
    val rootNote: PitchClass,
    val rootAccidental: Accidental = Accidental.NATURAL,
    val quality: String = "", // ej: "", "m", "7", "maj7", "m7", "sus4", "add9", "dim"
    val bassNote: PitchClass? = null,
    val bassAccidental: Accidental? = null,
    val rawChordText: String = ""
) {
    /**
     * Renderiza el acorde a texto según el sistema de notación solicitado y la tonalidad referencial.
     */
    fun toFormattedString(system: NotationSystem, referenceKey: PitchClass = rootNote): String {
        return when (system) {
            NotationSystem.LATIN -> {
                val rootStr = formatPitch(rootNote, rootAccidental, NotationSystem.LATIN)
                val bassStr = bassNote?.let { "/" + formatPitch(it, bassAccidental ?: Accidental.NATURAL, NotationSystem.LATIN) } ?: ""
                "$rootStr$quality$bassStr"
            }
            NotationSystem.ENGLISH -> {
                val rootStr = formatPitch(rootNote, rootAccidental, NotationSystem.ENGLISH)
                val bassStr = bassNote?.let { "/" + formatPitch(it, bassAccidental ?: Accidental.NATURAL, NotationSystem.ENGLISH) } ?: ""
                "$rootStr$quality$bassStr"
            }
            NotationSystem.NNS -> {
                val degree = calculateNnsDegree(rootNote, referenceKey)
                val bassDegree = bassNote?.let { "/" + calculateNnsDegree(it, referenceKey) } ?: ""
                "$degree$quality$bassDegree"
            }
        }
    }

    private fun formatPitch(pitch: PitchClass, accidental: Accidental, system: NotationSystem): String {
        val isLatin = system == NotationSystem.LATIN
        return when (pitch) {
            PitchClass.C -> if (isLatin) "Do" else "C"
            PitchClass.C_SHARP -> if (accidental == Accidental.FLAT) (if (isLatin) "Reb" else "Db") else (if (isLatin) "Do#" else "C#")
            PitchClass.D -> if (isLatin) "Re" else "D"
            PitchClass.D_SHARP -> if (accidental == Accidental.FLAT) (if (isLatin) "Mib" else "Eb") else (if (isLatin) "Re#" else "D#")
            PitchClass.E -> if (isLatin) "Mi" else "E"
            PitchClass.F -> if (isLatin) "Fa" else "F"
            PitchClass.F_SHARP -> if (accidental == Accidental.FLAT) (if (isLatin) "Solb" else "Gb") else (if (isLatin) "Fa#" else "F#")
            PitchClass.G -> if (isLatin) "Sol" else "G"
            PitchClass.G_SHARP -> if (accidental == Accidental.FLAT) (if (isLatin) "Lab" else "Ab") else (if (isLatin) "Sol#" else "G#")
            PitchClass.A -> if (isLatin) "La" else "A"
            PitchClass.A_SHARP -> if (accidental == Accidental.FLAT) (if (isLatin) "Sib" else "Bb") else (if (isLatin) "La#" else "A#")
            PitchClass.B -> if (isLatin) "Si" else "B"
        }
    }

    private fun calculateNnsDegree(pitch: PitchClass, referenceKey: PitchClass): String {
        val semitones = ((pitch.chromaticIndex - referenceKey.chromaticIndex) % 12 + 12) % 12
        return when (semitones) {
            0 -> "1"
            1 -> "b2"
            2 -> "2"
            3 -> "b3"
            4 -> "3"
            5 -> "4"
            6 -> "b5"
            7 -> "5"
            8 -> "b6"
            9 -> "6"
            10 -> "b7"
            11 -> "7"
            else -> "1"
        }
    }
}
