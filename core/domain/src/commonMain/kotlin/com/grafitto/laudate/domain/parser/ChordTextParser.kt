package com.grafitto.laudate.domain.parser

import com.grafitto.laudate.domain.model.music.Accidental
import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.PitchClass

/**
 * Errores controlados de parseo de acordes sin lanzar excepciones en tiempo de ejecución.
 */
sealed class ChordParseError {
    data class InvalidRootNote(val raw: String) : ChordParseError()
    data class InvalidBassNote(val raw: String) : ChordParseError()
    data class MalformedChord(val raw: String) : ChordParseError()
}

/**
 * Parser de texto de acordes individuales.
 * Soporta cifrado latino ("Do", "Lam7", "Sol/Si") y anglosajón ("C", "Am7", "G/B", "F#m7(b5)").
 */
object ChordTextParser {

    /** Nota fundamental: latina (Do-Re-Mi) o anglosajona (A-G). */
    private const val ROOT = "(?:Do|Re|Mi|Fa|Sol|La|Si|[A-G])"

    /** Alteración de la nota: sostenidos y bemoles (incluye dobles). */
    private const val ACCIDENTAL = "(?:#{1,2}|b{1,2})"

    /**
     * Calidades de acorde reconocidas (se pueden repetir: `m7b5`, `C7sus4`, `F#m7(b5)`…).
     *
     * Se enumeran a propósito en lugar de aceptar "cualquier cosa que no sea `/`": si la
     * calidad fuese libre, cualquier palabra que empiece por A-G se colaría como acorde
     * al parsear o transponer. Ese era el origen de corrupciones como
     * `[Estrofa]` → `[A#strofa]` o `[Asamblea]` → `[Esamblea]`.
     */
    private const val QUALITY_ATOMS =
        "(?:maj|min|sus|add|dim|aug|alt|omit|no|deg|m|M|\\+|-|°|ø|Δ|#|b|[0-9]{1,2}|\\([^)]*\\))"

    private val CHORD_REGEX = Regex(
        "^($ROOT)($ACCIDENTAL)?(${QUALITY_ATOMS}*)(?:/($ROOT)($ACCIDENTAL)?)?$",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): Result<DomainChord> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Acorde vacío"))
        }

        val match = CHORD_REGEX.matchEntire(trimmed)
            ?: return Result.failure(IllegalArgumentException("Formato de acorde irreconocible: $trimmed"))

        val rootName = match.groupValues[1]
        val rootAccSymbol = match.groupValues[2]
        val quality = match.groupValues[3]
        val bassName = match.groupValues[4]
        val bassAccSymbol = match.groupValues[5]

        val rootPitch = PitchClass.parse(rootName + rootAccSymbol)
            ?: return Result.failure(IllegalArgumentException("Nota fundamental inválida: $rootName$rootAccSymbol"))

        val rootAccidental = when (rootAccSymbol) {
            "#" -> Accidental.SHARP
            "b" -> Accidental.FLAT
            else -> Accidental.NATURAL
        }

        val bassPitch: PitchClass?
        val bassAccidental: Accidental?
        if (bassName.isNotEmpty()) {
            bassPitch = PitchClass.parse(bassName + bassAccSymbol)
                ?: return Result.failure(IllegalArgumentException("Nota de bajo inválida: $bassName$bassAccSymbol"))
            bassAccidental = when (bassAccSymbol) {
                "#" -> Accidental.SHARP
                "b" -> Accidental.FLAT
                else -> Accidental.NATURAL
            }
        } else {
            bassPitch = null
            bassAccidental = null
        }

        return Result.success(
            DomainChord(
                rootNote = rootPitch,
                rootAccidental = rootAccidental,
                quality = quality,
                bassNote = bassPitch,
                bassAccidental = bassAccidental,
                rawChordText = trimmed
            )
        )
    }
}
