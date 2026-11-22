package com.grafitto.laudate.domain.model.music

/**
 * Representa los 12 tonos de la escala cromática occidental (módulo 12).
 * 0 = Do / C
 */
enum class PitchClass(
    val chromaticIndex: Int,
    val preferredLatinName: String,
    val preferredEnglishName: String
) {
    C(0, "Do", "C"),
    C_SHARP(1, "Do#", "C#"),
    D(2, "Re", "D"),
    D_SHARP(3, "Re#", "D#"),
    E(4, "Mi", "E"),
    F(5, "Fa", "F"),
    F_SHARP(6, "Fa#", "F#"),
    G(7, "Sol", "G"),
    G_SHARP(8, "Sol#", "G#"),
    A(9, "La", "A"),
    A_SHARP(10, "La#", "A#"),
    B(11, "Si", "B");

    /**
     * Transpone la nota un número determinado de semitonos con aritmética módulo 12 normalizada.
     */
    fun transpose(semitones: Int): PitchClass {
        val normalized = (chromaticIndex + semitones) % 12
        val positive = if (normalized < 0) normalized + 12 else normalized
        return fromIndex(positive)
    }

    companion object {
        fun fromIndex(index: Int): PitchClass {
            val normalized = ((index % 12) + 12) % 12
            return entries.first { it.chromaticIndex == normalized }
        }

        /**
         * Parsea una nota base textual en notación Latina o Anglosajona.
         */
        fun parse(name: String): PitchClass? {
            val trimmed = name.trim()
            return entries.firstOrNull {
                it.preferredLatinName.equals(trimmed, ignoreCase = true) ||
                it.preferredEnglishName.equals(trimmed, ignoreCase = true)
            } ?: when (trimmed.lowercase()) {
                "reb", "db" -> C_SHARP
                "mib", "eb" -> D_SHARP
                "solb", "gb" -> F_SHARP
                "lab", "ab" -> G_SHARP
                "sib", "bb" -> A_SHARP
                else -> null
            }
        }
    }
}

/**
 * Alteraciones musicales.
 */
enum class Accidental(val symbol: String) {
    NATURAL(""),
    SHARP("#"),
    FLAT("b"),
    DOUBLE_SHARP("##"),
    DOUBLE_FLAT("bb")
}

/**
 * Sistemas de nomenclatura soportados en la visualización.
 */
enum class NotationSystem {
    LATIN,
    ENGLISH,
    NNS
}
