package com.grafitto.laudate.domain.tablature

/**
 * **Serialización de los ajustes del pentagrama** ([TabStaffOptions]) a una cadena de texto plana.
 *
 * Los ajustes son una preferencia **global** del usuario: se eligen una vez en el panel del
 * pentagrama y deben sobrevivir al cierre de la app y llegar al **PDF** exportado. Ni DataStore ni
 * el fichero `.properties` del escritorio guardan objetos, así que hace falta un codec.
 *
 * El formato es una lista de pares `clave=valor` separados por `;`, con un token de versión
 * delante:
 *
 * ```
 * v1;fretMarks=NUMBERS;marksPlacement=ON_LINES;lines=BROKEN_ON_MARKS;…
 * ```
 *
 * **Por qué texto y no JSON**: el Dominio no debe arrastrar `kotlinx.serialization` a la capa de
 * preferencias (el proyecto serializa con DTOs, no anotando modelos de Dominio), y un formato
 * `clave=valor` es **tolerante por construcción**: cada campo que falte o traiga un valor
 * desconocido cae a su valor por defecto, de modo que **añadir un ajuste nuevo en el futuro no
 * invalida lo ya guardado** ni obliga a migrar nada.
 *
 * El codec es **puro** (sin Android, sin ficheros): se prueba en JVM y lo usan las dos
 * implementaciones de [com.grafitto.laudate.domain.repository.PreferencesRepository].
 */
object TabStaffOptionsCodec {

    /** Marca de versión del formato. Un texto sin ella se considera ilegible y se ignora. */
    const val VERSION = "v1"

    private const val SEPARATOR = ';'
    private const val ASSIGN = '='

    private const val KEY_FRET_MARKS = "fretMarks"
    private const val KEY_MARKS_PLACEMENT = "marksPlacement"
    private const val KEY_LINES = "lines"
    private const val KEY_NOTE_VALUES = "noteValues"
    private const val KEY_SHOW_RESTS = "showRests"
    private const val KEY_RHYTHM_REPEAT = "rhythmRepeat"
    private const val KEY_MINIM_STYLE = "minimStyle"
    private const val KEY_SHOW_TIED_FRET = "showTiedFret"
    private const val KEY_PARENTHESIZE_TIED_FRET = "parenthesizeTiedFret"
    private const val KEY_SHOW_MEASURE_NUMBERS = "showMeasureNumbers"
    private const val KEY_SHOW_CLEF = "showClef"
    private const val KEY_SHOW_TIME_SIGNATURE = "showTimeSignature"
    private const val KEY_HEADER_ON_EVERY_SYSTEM = "headerOnEverySystem"
    private const val KEY_SHOW_STRING_LABELS = "showStringLabels"
    private const val KEY_STEMS_DOWN = "stemsDown"

    /**
     * Convierte los ajustes en su representación textual. **Siempre** escribe los quince campos,
     * para que el texto guardado sea legible y comparable en los tests.
     */
    fun encode(options: TabStaffOptions): String = buildString {
        append(VERSION)
        appendField(KEY_FRET_MARKS, options.fretMarks.name)
        appendField(KEY_MARKS_PLACEMENT, options.marksPlacement.name)
        appendField(KEY_LINES, options.lines.name)
        appendField(KEY_NOTE_VALUES, options.noteValues.name)
        appendField(KEY_SHOW_RESTS, options.showRests.toString())
        appendField(KEY_RHYTHM_REPEAT, options.rhythmRepeat.name)
        appendField(KEY_MINIM_STYLE, options.minimStyle.name)
        appendField(KEY_SHOW_TIED_FRET, options.showTiedFret.name)
        appendField(KEY_PARENTHESIZE_TIED_FRET, options.parenthesizeTiedFret.name)
        appendField(KEY_SHOW_MEASURE_NUMBERS, options.showMeasureNumbers.toString())
        appendField(KEY_SHOW_CLEF, options.showClef.toString())
        appendField(KEY_SHOW_TIME_SIGNATURE, options.showTimeSignature.toString())
        appendField(KEY_HEADER_ON_EVERY_SYSTEM, options.headerOnEverySystem.toString())
        appendField(KEY_SHOW_STRING_LABELS, options.showStringLabels.toString())
        appendField(KEY_STEMS_DOWN, options.stemsDown.toString())
    }

    /**
     * Reconstruye los ajustes desde [text]. Devuelve [TabStaffOptions] por defecto (el tipo `Common`
     * de MuseScore) si el texto es `null`, está vacío o no lleva la marca de versión; y deja en su
     * valor por defecto **cada** campo ausente o con un valor no reconocido.
     */
    fun decode(text: String?): TabStaffOptions {
        val defaults = TabStaffOptions()
        if (text.isNullOrBlank()) return defaults
        val tokens = text.split(SEPARATOR)
        if (tokens.firstOrNull()?.trim() != VERSION) return defaults
        val values = HashMap<String, String>(tokens.size)
        for (token in tokens.drop(1)) {
            val at = token.indexOf(ASSIGN)
            if (at <= 0) continue
            values[token.substring(0, at).trim()] = token.substring(at + 1).trim()
        }
        return TabStaffOptions(
            fretMarks = values.enumOr(KEY_FRET_MARKS, defaults.fretMarks),
            marksPlacement = values.enumOr(KEY_MARKS_PLACEMENT, defaults.marksPlacement),
            lines = values.enumOr(KEY_LINES, defaults.lines),
            noteValues = values.enumOr(KEY_NOTE_VALUES, defaults.noteValues),
            showRests = values.booleanOr(KEY_SHOW_RESTS, defaults.showRests),
            rhythmRepeat = values.enumOr(KEY_RHYTHM_REPEAT, defaults.rhythmRepeat),
            minimStyle = values.enumOr(KEY_MINIM_STYLE, defaults.minimStyle),
            showTiedFret = values.enumOr(KEY_SHOW_TIED_FRET, defaults.showTiedFret),
            parenthesizeTiedFret = values.enumOr(
                KEY_PARENTHESIZE_TIED_FRET,
                defaults.parenthesizeTiedFret
            ),
            showMeasureNumbers = values.booleanOr(
                KEY_SHOW_MEASURE_NUMBERS,
                defaults.showMeasureNumbers
            ),
            showClef = values.booleanOr(KEY_SHOW_CLEF, defaults.showClef),
            showTimeSignature = values.booleanOr(
                KEY_SHOW_TIME_SIGNATURE,
                defaults.showTimeSignature
            ),
            headerOnEverySystem = values.booleanOr(
                KEY_HEADER_ON_EVERY_SYSTEM,
                defaults.headerOnEverySystem
            ),
            showStringLabels = values.booleanOr(KEY_SHOW_STRING_LABELS, defaults.showStringLabels),
            stemsDown = values.booleanOr(KEY_STEMS_DOWN, defaults.stemsDown)
        )
    }

    private fun StringBuilder.appendField(key: String, value: String) {
        append(SEPARATOR)
        append(key)
        append(ASSIGN)
        append(value)
    }

    private inline fun <reified T : Enum<T>> Map<String, String>.enumOr(
        key: String,
        fallback: T
    ): T {
        val raw = this[key] ?: return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }

    private fun Map<String, String>.booleanOr(key: String, fallback: Boolean): Boolean =
        this[key]?.toBooleanStrictOrNull() ?: fallback
}
