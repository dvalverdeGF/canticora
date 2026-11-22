package com.grafitto.laudate.domain.model.music

/**
 * Formateo de **tonalidades** (no de acordes) según la nomenclatura elegida.
 *
 * El visor ya renderizaba los acordes con `DomainChord.toFormattedString`, pero la
 * tonalidad («Tono: C», «Original: F#») se mostraba siempre tal cual venía del dato
 * (en notación anglosajona). Este utilitario cierra esa fuga de notación.
 *
 * Es lógica pura (JVM) y no depende de Android.
 */
object NotationFormat {

    private val ROOT_REGEX = Regex("^(Do|Re|Mi|Fa|Sol|La|Si|[A-Ga-g])([#b]?)(.*)$")

    /**
     * Devuelve una tonalidad o **acorde** (`C`, `F#`, `Am`, `Bb`, `Do`, `Lam`, `C/G`, `Do/Mi`…)
     * en el sistema indicado. Si no se reconoce la nota base se devuelve el texto original
     * sin tocar.
     *
     * También convierte la nota de bajo de los acordes con barra (`C/G` → `Do/Sol`).
     */
    fun key(name: String?, system: NotationSystem): String {
        if (name.isNullOrBlank()) return name.orEmpty()
        val trimmed = name.trim()

        // Acorde con bajo: se convierte cada lado por separado (`C/G`, `Do/Sol`).
        val slashIndex = trimmed.indexOf('/')
        if (slashIndex > 0 && slashIndex < trimmed.lastIndex) {
            val head = trimmed.substring(0, slashIndex)
            val bass = trimmed.substring(slashIndex + 1)
            return convertRoot(head, system) + "/" + convertRoot(bass, system)
        }

        return convertRoot(trimmed, system)
    }

    /** Convierte la nota base (con alteración) de un acorde o tonalidad. */
    private fun convertRoot(name: String, system: NotationSystem): String {
        val match = ROOT_REGEX.find(name) ?: return name
        val rootToken = match.groupValues[1]
        val accidentalToken = match.groupValues[2]
        val rest = match.groupValues[3]

        val pitch = PitchClass.parse(rootToken + accidentalToken) ?: return name
        // La notación numérica (NNS) necesita una tonalidad de referencia que aquí no existe:
        // se deja en anglosajona para no mentir sobre los grados.
        if (system == NotationSystem.NNS) return name

        val flat = accidentalToken == "b"
        return formatPitch(pitch, flat, system) + rest
    }

    private fun formatPitch(pitch: PitchClass, flat: Boolean, system: NotationSystem): String {
        val isLatin = system == NotationSystem.LATIN
        return when (pitch) {
            PitchClass.C -> if (isLatin) "Do" else "C"
            PitchClass.C_SHARP -> if (flat) (if (isLatin) "Reb" else "Db") else (if (isLatin) "Do#" else "C#")
            PitchClass.D -> if (isLatin) "Re" else "D"
            PitchClass.D_SHARP -> if (flat) (if (isLatin) "Mib" else "Eb") else (if (isLatin) "Re#" else "D#")
            PitchClass.E -> if (isLatin) "Mi" else "E"
            PitchClass.F -> if (isLatin) "Fa" else "F"
            PitchClass.F_SHARP -> if (flat) (if (isLatin) "Solb" else "Gb") else (if (isLatin) "Fa#" else "F#")
            PitchClass.G -> if (isLatin) "Sol" else "G"
            PitchClass.G_SHARP -> if (flat) (if (isLatin) "Lab" else "Ab") else (if (isLatin) "Sol#" else "G#")
            PitchClass.A -> if (isLatin) "La" else "A"
            PitchClass.A_SHARP -> if (flat) (if (isLatin) "Sib" else "Bb") else (if (isLatin) "La#" else "A#")
            PitchClass.B -> if (isLatin) "Si" else "B"
        }
    }
}
