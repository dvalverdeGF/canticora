package com.grafitto.laudate.domain.model.tablature

/**
 * Posición de la tablatura respecto a la letra cuando el modo de visualización muestra **ambas**
 * (p. ej. «Letra y tablatura»). Es una preferencia de lectura persistente.
 */
enum class TabDisplayPosition(val label: String) {
    /** La tablatura **arriba**, la letra debajo. */
    BEFORE("Arriba"),

    /** La letra **arriba**, la tablatura debajo. */
    AFTER("Abajo"),

    /** Letra y tablatura **lado a lado** (la tablatura a la derecha). */
    RIGHT("A la derecha");

    companion object {
        val DEFAULT = AFTER

        /** Resuelve un nombre guardado tolerando valores desconocidos. */
        fun fromName(name: String?): TabDisplayPosition =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
