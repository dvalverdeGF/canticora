package com.grafitto.laudate.domain.parser

import com.grafitto.laudate.domain.model.song.Song

/**
 * Severidad de un problema detectado al validar un texto ChordPro.
 */
enum class ChordProValidationSeverity { ERROR, WARNING }

/**
 * Incidencia concreta de la validación.
 */
data class ChordProValidationIssue(
    val severity: ChordProValidationSeverity,
    val message: String,
    val lineNumber: Int? = null
)

/**
 * Resultado de validar un texto ChordPro.
 */
data class ChordProValidationResult(
    val isValid: Boolean,
    val issues: List<ChordProValidationIssue> = emptyList(),
    val song: Song? = null
) {
    val errors: List<ChordProValidationIssue>
        get() = issues.filter { it.severity == ChordProValidationSeverity.ERROR }

    val warnings: List<ChordProValidationIssue>
        get() = issues.filter { it.severity == ChordProValidationSeverity.WARNING }
}

/**
 * Caso de uso: valida un texto ChordPro antes de importarlo.
 *
 * Comprueba estructura, directivas balanceadas y contenido mínimo, y devuelve el
 * [Song] parseado cuando el texto es aceptable. Dominio puro, sin dependencias de Android.
 */
class ValidateChordProUseCase {

    private val CHORD_TAG_REGEX = Regex("\\[[^\\]]+\\]")

    private val knownSectionStart = setOf(
        "start_of_verse", "sov",
        "start_of_chorus", "soc",
        "start_of_bridge", "sob",
        "start_of_intro", "start_of_outro", "start_of_interlude"
    )
    private val knownSectionEnd = setOf(
        "end_of_verse", "eov",
        "end_of_chorus", "eoc",
        "end_of_bridge", "eob",
        "end_of_intro", "end_of_outro", "end_of_interlude"
    )

    operator fun invoke(rawContent: String): ChordProValidationResult {
        val issues = mutableListOf<ChordProValidationIssue>()

        if (rawContent.isBlank()) {
            issues.add(
                ChordProValidationIssue(
                    ChordProValidationSeverity.ERROR,
                    "El contenido ChordPro está vacío."
                )
            )
            return ChordProValidationResult(isValid = false, issues = issues)
        }

        val lines = rawContent.lines()
        var openSections = 0
        var hasLyrics = false
        var hasChords = false

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmed = line.trim()

            // La letra es el texto que queda tras quitar las etiquetas de acorde.
            val lyricsOnly = trimmed.replace(CHORD_TAG_REGEX, "")
            if (!trimmed.startsWith("{") && lyricsOnly.isNotBlank()) {
                hasLyrics = true
            }
            if (CHORD_TAG_REGEX.containsMatchIn(trimmed) && !ChordProParser.isSectionHeaderLine(trimmed)) {
                hasChords = true
            }

            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val directive = ChordProParser.parseDirective(trimmed)
                when (directive.tag) {
                    in knownSectionStart -> openSections++
                    in knownSectionEnd -> {
                        openSections--
                        if (openSections < 0) {
                            issues.add(
                                ChordProValidationIssue(
                                    ChordProValidationSeverity.ERROR,
                                    "Cierre de sección «${directive.tag}» sin apertura previa.",
                                    lineNumber
                                )
                            )
                            openSections = 0
                        }
                    }
                }
            }
        }

        if (openSections > 0) {
            issues.add(
                ChordProValidationIssue(
                    ChordProValidationSeverity.WARNING,
                    "Hay $openSections sección(es) sin cerrar. Se cerrarán automáticamente al final."
                )
            )
        }

        if (!hasLyrics) {
            issues.add(
                ChordProValidationIssue(
                    ChordProValidationSeverity.ERROR,
                    "No se ha encontrado ningún texto de letra."
                )
            )
        }

        if (!hasChords) {
            issues.add(
                ChordProValidationIssue(
                    ChordProValidationSeverity.WARNING,
                    "No se han detectado acordes. La canción se importará solo con letra."
                )
            )
        }

        val hasErrors = issues.any { it.severity == ChordProValidationSeverity.ERROR }
        val song = if (hasErrors) null else runCatching { ChordProParser.parse(rawContent) }.getOrElse { error ->
            issues.add(
                ChordProValidationIssue(
                    ChordProValidationSeverity.ERROR,
                    "No se pudo interpretar el contenido: ${error.message ?: "formato no válido"}"
                )
            )
            null
        }

        return ChordProValidationResult(
            isValid = !hasErrors,
            issues = issues,
            song = song
        )
    }
}
