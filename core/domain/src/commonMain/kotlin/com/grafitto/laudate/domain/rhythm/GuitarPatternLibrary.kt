package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature

/**
 * Biblioteca de patrones de rasgueo típicos de guitarra para los compases habituales.
 * Todos los patrones son estáticos, puros y de solo lectura.
 *
 * Invariantes:
 *  - Cada patrón integrado suma exactamente la duración de su compás.
 *  - Las direcciones de los golpes se calculan con [StrummingAutomation] (regla del péndulo),
 *    de modo que no hay flechas incoherentes escritas a mano.
 */
object GuitarPatternLibrary {

    private val DEFAULT_TIME_SIGNATURE = TimeSignature.FOUR_FOUR

    // ─── Constructores de figuras (dirección se calcula después) ─────────────
    private fun articulationOf(accent: Boolean) =
        if (accent) com.grafitto.laudate.domain.model.rhythm.StrumArticulation.ACCENT
        else com.grafitto.laudate.domain.model.rhythm.StrumArticulation.NONE

    private fun q(accent: Boolean = false) = RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, articulationOf(accent))
    private fun e(accent: Boolean = false) = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN, articulationOf(accent))
    private fun s(accent: Boolean = false) = RhythmEvent(RhythmFigure.SIXTEENTH, StrumDirection.DOWN, articulationOf(accent))
    private fun h(accent: Boolean = false) = RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN, articulationOf(accent))
    private fun qRest() = RhythmEvent(RhythmFigure.QUARTER_REST, StrumDirection.NONE)
    private fun eRest() = RhythmEvent(RhythmFigure.EIGHTH_REST, StrumDirection.NONE)
    private fun sRest() = RhythmEvent(RhythmFigure.SIXTEENTH_REST, StrumDirection.NONE)
    private fun qMute() = RhythmEvent(RhythmFigure.QUARTER, StrumDirection.MUTE)
    private fun eMute() = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.MUTE)
    private fun sMute() = RhythmEvent(RhythmFigure.SIXTEENTH, StrumDirection.MUTE)
    private fun dq(accent: Boolean = false) = RhythmEvent(RhythmFigure.DOTTED_QUARTER, StrumDirection.DOWN, articulationOf(accent))
    private fun de(accent: Boolean = false) = RhythmEvent(RhythmFigure.DOTTED_EIGHTH, StrumDirection.DOWN, articulationOf(accent))
    private fun dh(accent: Boolean = false) = RhythmEvent(RhythmFigure.DOTTED_HALF, StrumDirection.DOWN, articulationOf(accent))

    private fun pattern(
        id: String,
        name: String,
        timeSignature: TimeSignature,
        events: List<RhythmEvent>
    ) = RhythmPattern(
        id = id,
        name = name,
        timeSignature = timeSignature,
        isBuiltIn = true,
        events = StrummingAutomation.apply(events, timeSignature)
    )

    /** Patrones por defecto predefinidos. */
    val builtInPatterns: List<RhythmPattern> = listOf(
        // ══ 2/4 (2 negras = 2.0) ════════════════════════════════════════════
        pattern(
            id = "builtin_2_4_basico",
            name = "2/4 Básico",
            timeSignature = TimeSignature.TWO_FOUR,
            events = listOf(q(accent = true), q())
        ),
        pattern(
            id = "builtin_2_4_corcheas",
            name = "2/4 Corcheas",
            timeSignature = TimeSignature.TWO_FOUR,
            events = listOf(e(accent = true), e(), e(), e())
        ),
        pattern(
            id = "builtin_2_4_polca",
            name = "2/4 Polca",
            timeSignature = TimeSignature.TWO_FOUR,
            events = listOf(q(accent = true), e(), e())
        ),
        pattern(
            id = "builtin_2_4_marcha",
            name = "2/4 Marcha",
            timeSignature = TimeSignature.TWO_FOUR,
            events = listOf(q(accent = true), qMute())
        ),

        // ══ 3/4 (3 negras = 3.0) ════════════════════════════════════════════
        pattern(
            id = "builtin_3_4_vals",
            name = "3/4 Vals",
            timeSignature = TimeSignature.THREE_FOUR,
            events = listOf(q(accent = true), q(), q())
        ),
        pattern(
            id = "builtin_3_4_vals_corcheas",
            name = "3/4 Vals con corcheas",
            timeSignature = TimeSignature.THREE_FOUR,
            events = listOf(q(accent = true), e(), e(), e(), e())
        ),
        pattern(
            id = "builtin_3_4_balada",
            name = "3/4 Balada",
            timeSignature = TimeSignature.THREE_FOUR,
            events = listOf(q(accent = true), e(), e(), q())
        ),
        pattern(
            id = "builtin_3_4_arpegio",
            name = "3/4 Arpegio",
            timeSignature = TimeSignature.THREE_FOUR,
            events = listOf(q(accent = true), e(), e(), q())
        ),

        // ══ 4/4 (4 negras = 4.0) ════════════════════════════════════════════
        pattern(
            id = "builtin_4_4_basico",
            name = "4/4 Básico",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), q(), q(), q())
        ),
        pattern(
            id = "builtin_4_4_corcheas",
            name = "4/4 Corcheas constantes",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(e(accent = true), e(), e(), e(), e(), e(), e(), e())
        ),
        pattern(
            id = "builtin_4_4_pop",
            name = "4/4 Pop (D DU UDU)",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), e(), e(), e(), e(), q())
        ),
        pattern(
            id = "builtin_4_4_pop_balada",
            name = "4/4 Pop Balada",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), e(), e(), e(), e(), e(), e())
        ),
        pattern(
            id = "builtin_4_4_rock",
            name = "4/4 Rock (negras)",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), q(accent = true), q(accent = true), q(accent = true))
        ),
        pattern(
            id = "builtin_4_4_rock_corcheas",
            name = "4/4 Rock Corcheas",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(e(accent = true), e(), e(accent = true), e(), e(accent = true), e(), e(accent = true), e())
        ),
        pattern(
            id = "builtin_4_4_rock_palma",
            name = "4/4 Rock con acentos",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), q(), qMute(), q())
        ),
        pattern(
            id = "builtin_4_4_percusivo",
            name = "4/4 Percusivo",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), qMute(), q(), qMute())
        ),
        pattern(
            id = "builtin_4_4_balada",
            name = "4/4 Balada lenta",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(h(accent = true), e(), e(), q())
        ),
        pattern(
            id = "builtin_4_4_balada_silencio",
            name = "4/4 Balada con silencio",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(h(accent = true), e(), e(), qRest())
        ),
        pattern(
            id = "builtin_4_4_reggae",
            name = "4/4 Reggae",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(qRest(), e(accent = true), e(), e(), e(), e(), e())
        ),
        pattern(
            id = "builtin_4_4_funk",
            name = "4/4 Funk (semicorcheas)",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(
                s(accent = true), s(), s(), s(),
                s(), sMute(), s(), s(),
                s(accent = true), s(), s(), s(),
                s(), sMute(), s(), s()
            )
        ),
        pattern(
            id = "builtin_4_4_alabanza",
            name = "4/4 Alabanza",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), e(), e(), e(), e(), q())
        ),
        pattern(
            id = "builtin_4_4_mediodia",
            name = "4/4 Medio tiempo",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(h(accent = true), q(), q())
        ),
        pattern(
            id = "builtin_4_4_arpegio",
            name = "4/4 Arpegio (pulso)",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(q(accent = true), q(), e(), e(), q())
        ),
        pattern(
            id = "builtin_4_4_shuffle",
            name = "4/4 Shuffle",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(
                de(accent = true), s(), de(), s(),
                de(accent = true), s(), de(), s()
            )
        ),
        pattern(
            id = "builtin_4_4_balada_puntillo",
            name = "4/4 Ritmo con puntillos",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(dq(accent = true), e(), dq(), e())
        ),

        // ══ 6/8 (6 corcheas = 3.0) ══════════════════════════════════════════
        pattern(
            id = "builtin_6_8_basico",
            name = "6/8 Básico",
            timeSignature = TimeSignature.SIX_EIGHT,
            events = List(6) { e(accent = it == 0 || it == 3) }
        ),
        pattern(
            id = "builtin_6_8_percusivo",
            name = "6/8 Percusivo",
            timeSignature = TimeSignature.SIX_EIGHT,
            events = listOf(
                e(accent = true), eMute(), e(),
                e(accent = true), eMute(), e()
            )
        ),
        pattern(
            id = "builtin_6_8_balada",
            name = "6/8 Balada",
            timeSignature = TimeSignature.SIX_EIGHT,
            events = listOf(
                e(accent = true), eRest(), e(),
                e(accent = true), eRest(), e()
            )
        ),
        pattern(
            id = "builtin_6_8_vals",
            name = "6/8 Vals (negra-corchea)",
            timeSignature = TimeSignature.SIX_EIGHT,
            events = listOf(q(accent = true), e(), q(accent = true), e())
        ),
        pattern(
            id = "builtin_6_8_rock",
            name = "6/8 Rock",
            timeSignature = TimeSignature.SIX_EIGHT,
            events = List(6) { e(accent = it % 3 == 0) }
        ),

        // ══ 12/8 (12 corcheas = 6.0) ════════════════════════════════════════
        pattern(
            id = "builtin_12_8_basico",
            name = "12/8 Básico",
            timeSignature = TimeSignature.TWELVE_EIGHT,
            events = List(12) { e(accent = it % 3 == 0) }
        ),
        pattern(
            id = "builtin_12_8_balada",
            name = "12/8 Balada amplia",
            timeSignature = TimeSignature.TWELVE_EIGHT,
            events = listOf(dh(accent = true), dh())
        )
    )

    /** Filtra los patrones integrados por compás. */
    fun patternsFor(timeSignature: TimeSignature): List<RhythmPattern> =
        builtInPatterns.filter { it.timeSignature == timeSignature }

    /** Compás por defecto si el usuario no ha elegido ninguno. */
    fun defaultTimeSignature(): TimeSignature = DEFAULT_TIME_SIGNATURE
}
