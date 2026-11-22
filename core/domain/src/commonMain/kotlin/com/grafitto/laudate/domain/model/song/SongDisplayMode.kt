package com.grafitto.laudate.domain.model.song

/**
 * Modos de visualización de un canto en el visor, la vista previa y el modo Celebración.
 *
 * Combina tres capas independientes: **letra**, **acordes** y **tablatura**. El nombre histórico
 * [CHORDS_ABOVE_TEXT] se conserva (la preferencia se guarda por `name`) y equivale a «Letra y acordes».
 */
enum class SongDisplayMode(val label: String) {
    /** Solo la letra. */
    LYRICS_ONLY("Solo letra"),

    /** Letra con los acordes encima (modo clásico). */
    CHORDS_ABOVE_TEXT("Letra y acordes"),

    /** Letra con la tablatura asociada. */
    LYRICS_AND_TABLATURE("Letra y tablatura"),

    /** Letra, acordes y tablatura. */
    LYRICS_CHORDS_AND_TABLATURE("Letra, acordes y tablatura"),

    /** Solo la tablatura. */
    TABLATURE_ONLY("Solo tablatura");

    /** ¿Muestra la letra? */
    val showsLyrics: Boolean get() = this != TABLATURE_ONLY

    /** ¿Muestra los acordes sobre la letra? */
    val showsChords: Boolean get() = this == CHORDS_ABOVE_TEXT || this == LYRICS_CHORDS_AND_TABLATURE

    /** ¿Muestra la tablatura? */
    val showsTablature: Boolean
        get() = this == LYRICS_AND_TABLATURE || this == LYRICS_CHORDS_AND_TABLATURE || this == TABLATURE_ONLY

    companion object {
        /** Valor por defecto si la preferencia guardada no existe o no es reconocible. */
        val DEFAULT = CHORDS_ABOVE_TEXT

        /** Resuelve un nombre guardado tolerando valores desconocidos. */
        fun fromName(name: String?): SongDisplayMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
