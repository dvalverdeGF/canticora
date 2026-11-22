package com.grafitto.laudate.domain.tablature

/**
 * Colores de la **voz activa** del editor de tablatura, medidos del **código fuente de MuseScore 4**
 * (`src/engraving/internal/engravingconfiguration.cpp`, función `EngravingConfiguration::init`):
 *
 * ```cpp
 * static const Color DEFAULT_VOICE_COLORS[VOICES + 1] {
 *     "#0065BF",   // voz 1
 *     "#007F00",   // voz 2
 *     "#C53F00",   // voz 3
 *     "#C31989",   // voz 4
 *     "#6038FC",   // «todas las voces»
 * };
 * ```
 *
 * En MuseScore, el **cursor de entrada** y el **resaltado de una nota seleccionada** llevan el color
 * de su voz (`NotationConfiguration::selectionColor(voiceIndex)` → `EngravingConfiguration::voiceColor`).
 * Aquí se guardan como `Int` **ARGB** (`0xFFRRGGBB`) para que el **Dominio siga siendo Kotlin puro**
 * (sin `android.graphics.Color` ni `androidx.compose.ui.graphics.Color`): la UI los convierte al
 * pintar. El valor de «todas las voces» se conserva para cuando exista la asignación de voz.
 *
 * Los colores **heredados de MuseScore 3** que circulaban por la documentación (azul / verde / naranja
 * / morado) eran **cercanos pero no exactos**: el morado es `#C31989` (magenta) y el verde es oscuro
 * (`#007F00`), no los tonos brillantes que se solían citar. Estos son los del código.
 */
object TabVoiceColors {

    /** Color de la **voz 1** (`#0065BF`): azul. Es la voz por defecto al escribir. */
    const val VOICE_1: Int = 0xFF0065BF.toInt()

    /** Color de la **voz 2** (`#007F00`): verde. */
    const val VOICE_2: Int = 0xFF007F00.toInt()

    /** Color de la **voz 3** (`#C53F00`): naranja quemado. */
    const val VOICE_3: Int = 0xFFC53F00.toInt()

    /** Color de la **voz 4** (`#C31989`): magenta. */
    const val VOICE_4: Int = 0xFFC31989.toInt()

    /** Color de la asignación «**todas las voces**» (`#6038FC`): violeta. */
    const val ALL_VOICES: Int = 0xFF6038FC.toInt()

    /**
     * Color de la voz [voice] (1–4). Una voz fuera de rango cae en la 1, que es la de escritura por
     * defecto; así el cursor nunca se queda sin color.
     */
    fun of(voice: Int): Int = when (voice) {
        2 -> VOICE_2
        3 -> VOICE_3
        4 -> VOICE_4
        else -> VOICE_1
    }

    /** Número de voces que soporta MuseScore en un pentagrama (y Canticora desde la Fase 5). */
    const val VOICE_COUNT: Int = 4

    /**
     * Opacidad del **cursor de entrada**, en `0f..1f`: MuseScore pinta el rectángulo del cursor con el
     * color de la voz a **media tinta** para que la cifra se lea por debajo. El valor exacto del código
     * de MuseScore es `dropRectColor` con `alpha = 80` sobre 255 ≈ **0,31**.
     */
    const val CURSOR_ALPHA: Float = 80f / 255f
}
