package com.grafitto.laudate.domain.model.rhythm

import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.platform.nowMillis
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compás musical expresado como fracción (numerador / denominador).
 * El valor temporal se normaliza a unidades de negra para poder validar patrones.
 */
data class TimeSignature(
    val numerator: Int,
    val denominator: Int
) {
    /** Duración total del compás expresada en negras (figuras de 1/4). */
    val totalQuarterBeats: Double
        get() = numerator * (4.0 / denominator)

    /**
     * Unidad de pulso del compás en negras.
     * - Compases simples (2/4, 3/4, 4/4): el pulso es 1 negra (1.0).
     * - Compases compuestos (6/8, 9/8, 12/8): el pulso es 1 negra con puntillo (1.5).
     */
    val beatUnitQuarterBeats: Double
        get() {
            val unit = 4.0 / denominator
            val isCompound = denominator >= 8 && numerator % 3 == 0 && numerator > 3
            return if (isCompound) unit * 3 else unit
        }

    /** Número de pulsos del compás (usado para generar las separaciones `|`). */
    val beatCount: Int
        get() = kotlin.math.round(totalQuarterBeats / beatUnitQuarterBeats).toInt().coerceAtLeast(1)

    override fun toString(): String = "$numerator/$denominator"

    companion object {
        val TWO_FOUR = TimeSignature(2, 4)
        val THREE_FOUR = TimeSignature(3, 4)
        val FOUR_FOUR = TimeSignature(4, 4)
        val SIX_EIGHT = TimeSignature(6, 8)
        val NINE_EIGHT = TimeSignature(9, 8)
        val TWELVE_EIGHT = TimeSignature(12, 8)

        /** Compases habituales en la música litúrgica y popular para guitarra. */
        val common: List<TimeSignature> =
            listOf(TWO_FOUR, THREE_FOUR, FOUR_FOUR, SIX_EIGHT, NINE_EIGHT, TWELVE_EIGHT)

        /**
         * Interpreta un texto tipo "4/4", "3/4", "6/8" devolviendo el compás correspondiente.
         * Devuelve null si el formato no es válido.
         */
        fun parse(raw: String): TimeSignature? {
            val cleaned = raw.trim()
            val parts = cleaned.split('/')
            if (parts.size != 2) return null
            val numerator = parts[0].trim().toIntOrNull() ?: return null
            val denominator = parts[1].trim().toIntOrNull() ?: return null
            if (numerator <= 0 || denominator <= 0) return null
            return TimeSignature(numerator, denominator)
        }

        /** Busca un compás conocido o crea uno nuevo a partir del texto. */
        fun fromNameOrDefault(name: String): TimeSignature =
            common.firstOrNull { it.toString() == name.trim() } ?: parse(name) ?: FOUR_FOUR
    }
}

/**
 * Figuras rítmicas y silencios con su valor en negras (1.0 = negra).
 *
 * Cada figura describe **cuándo y cuánto dura** un evento. La figura es independiente
 * de la dirección de rasgueo ([StrumDirection]).
 *
 * No existe la **semicorchea con puntillo** (0,375 negras) a propósito: es la única figura que no
 * cabe en ninguna subdivisión de la rejilla (negra, corchea, semicorchea) y rompería la conversión
 * exacta rejilla ↔ modelo. Las duraciones que no caben en una sola figura se representan con
 * **ligaduras** ([RhythmEvent.isTied]).
 */
enum class RhythmFigure(
    val beatValue: Double,
    val symbol: String,
    val label: String,
    val isDotted: Boolean = false
) {
    WHOLE(4.0, "\uD834\uDD5D", "Redonda"),
    DOTTED_HALF(3.0, "\uD834\uDD5E\u00B7", "Blanca con puntillo", isDotted = true),
    HALF(2.0, "\uD834\uDD5E", "Blanca"),
    DOTTED_QUARTER(1.5, "\u2669\u00B7", "Negra con puntillo", isDotted = true),
    QUARTER(1.0, "\u2669", "Negra"),
    DOTTED_EIGHTH(0.75, "\u266A\u00B7", "Corchea con puntillo", isDotted = true),
    EIGHTH(0.5, "\u266A", "Corchea"),
    SIXTEENTH(0.25, "\uD834\uDD60", "Semicorchea"),

    WHOLE_REST(4.0, "\uD834\uDD3B", "Silencio de redonda"),
    DOTTED_HALF_REST(3.0, "\uD834\uDD3C\u00B7", "Silencio de blanca con puntillo", isDotted = true),
    HALF_REST(2.0, "\uD834\uDD3C", "Silencio de blanca"),
    DOTTED_QUARTER_REST(1.5, "\uD834\uDD3D\u00B7", "Silencio de negra con puntillo", isDotted = true),
    QUARTER_REST(1.0, "\uD834\uDD3D", "Silencio de negra"),
    DOTTED_EIGHTH_REST(0.75, "\uD834\uDD3E\u00B7", "Silencio de corchea con puntillo", isDotted = true),
    EIGHTH_REST(0.5, "\uD834\uDD3E", "Silencio de corchea"),
    SIXTEENTH_REST(0.25, "\uD834\uDD3F", "Silencio de semicorchea");

    val isRest: Boolean
        get() = name.endsWith("_REST")

    /** Nombre de la figura sin el sufijo de silencio. */
    val noteName: String
        get() = label.removePrefix("Silencio de ")

    companion object {
        /** Figuras sonoras ordenadas de mayor a menor duración. */
        val notes: List<RhythmFigure> = entries.filter { !it.isRest }

        /** Silencios ordenados de mayor a menor duración. */
        val rests: List<RhythmFigure> = entries.filter { it.isRest }

        /** Devuelve el silencio equivalente a una figura sonora (misma duración), si existe. */
        fun restEquivalentOf(figure: RhythmFigure): RhythmFigure? =
            entries.firstOrNull { it.isRest && it.beatValue == figure.beatValue }

        /** Devuelve la figura sonora equivalente a un silencio (misma duración), si existe. */
        fun noteEquivalentOf(figure: RhythmFigure): RhythmFigure? =
            entries.firstOrNull { !it.isRest && it.beatValue == figure.beatValue }
    }
}

/**
 * Dirección de un golpe de guitarra.
 *
 * La dirección describe **cómo se ejecuta** el rasgueo, no cuánto dura.
 * `NONE` se usa para silencios o posiciones sin golpe.
 */
enum class StrumDirection(val arrow: String) {
    DOWN("\u2193"),   // ↓ rasgueo hacia abajo
    UP("\u2191"),     // ↑ rasgueo hacia arriba
    MUTE("\u00D7"),   // × apagado / percusivo
    NONE("\u00B7");  // · pausa (no se toca)

    /** Direcciones que representan un golpe real. */
    val isStruck: Boolean get() = this == DOWN || this == UP || this == MUTE

    /** Nombre del símbolo ChordPro base de la dirección (`arrow-down`, `arrow-up`, `arrow-mute`). */
    val symbolBaseName: String
        get() = when (this) {
            DOWN -> "arrow-down"
            UP -> "arrow-up"
            MUTE -> "arrow-mute"
            NONE -> "bar"
        }

    /** Siguiente dirección al ciclar manualmente (↓ → ↑ → × → ↓). */
    fun cycled(): StrumDirection = when (this) {
        DOWN -> UP
        UP -> MUTE
        MUTE -> DOWN
        NONE -> DOWN
    }

    companion object {
        /** Opciones ofrecidas al usuario para editar un golpe. */
        val strikable: List<StrumDirection> = listOf(DOWN, UP, MUTE)
    }
}

/**
 * Articulación de un golpe de rasgueo, independiente de la dirección.
 *
 * Se corresponde 1:1 con los sufijos de los símbolos de rasgueo de ChordPro
 * (`arrow-down-with-accent` = [StrumDirection.DOWN] + [StrumArticulation.ACCENT]).
 */
enum class StrumArticulation {
    NONE,
    ACCENT,
    ARPEGGIO,
    STACCATO,
    ACCENT_ARPEGGIO,
    ACCENT_STACCATO;

    /** Indica si el golpe lleva acento. */
    val isAccent: Boolean
        get() = this == ACCENT || this == ACCENT_ARPEGGIO || this == ACCENT_STACCATO

    /** Indica si el golpe se arpegia. */
    val isArpeggio: Boolean
        get() = this == ARPEGGIO || this == ACCENT_ARPEGGIO

    /** Indica si el golpe es staccato. */
    val isStaccato: Boolean
        get() = this == STACCATO || this == ACCENT_STACCATO

    /** Nombre canónico del símbolo ChordPro para una dirección y articulación. */
    fun symbolNameFor(direction: StrumDirection): String = when (this) {
        NONE -> direction.symbolBaseName
        ACCENT -> "${direction.symbolBaseName}-with-accent"
        ARPEGGIO -> "${direction.symbolBaseName}-with-arpeggio"
        STACCATO -> "${direction.symbolBaseName}-with-staccato"
        ACCENT_ARPEGGIO -> "${direction.symbolBaseName}-with-accent-and-arpeggio"
        ACCENT_STACCATO -> "${direction.symbolBaseName}-with-accent-and-staccato"
    }

    /** Activa o desactiva el acento conservando el resto de la articulación (arpegio/staccato). */
    fun toggleAccent(): StrumArticulation = when (this) {
        NONE -> ACCENT
        ACCENT -> NONE
        ARPEGGIO -> ACCENT_ARPEGGIO
        ACCENT_ARPEGGIO -> ARPEGGIO
        STACCATO -> ACCENT_STACCATO
        ACCENT_STACCATO -> STACCATO
    }

    companion object {
        /** Opciones ofrecidas al usuario para ciclar la articulación. */
        val all: List<StrumArticulation> =
            listOf(NONE, ACCENT, ARPEGGIO, STACCATO, ACCENT_ARPEGGIO, ACCENT_STACCATO)

        /** Siguiente articulación al ciclar (NONE → ACCENT → ARPEGGIO → … → NONE). */
        fun next(current: StrumArticulation): StrumArticulation =
            all[(all.indexOf(current) + 1) % all.size]
    }
}

/**
 * Registro de cuerdas de un golpe de rasgueo.
 *
 * En la notación de cancionero, el músico indica con la **longitud de la flecha** qué cuerdas
 * debe tocar: una flecha larga barre todas las cuerdas; una flecha corta ataca solo un registro.
 *  - [ALL]: flecha **larga** ↓ / ↑ (cuerdas graves y agudas).
 *  - [BASS]: flecha **corta** (solo cuerdas graves, 6.ª–4.ª).
 *  - [TREBLE]: flecha **corta** (solo cuerdas agudas, 3.ª–1.ª).
 *
 * Es una propiedad **ortogonal** a [StrumDirection] (cómo se ataca) y a [StrumArticulation]
 * (acento/arpegio/staccato): describe **qué cuerdas** suenan. Se compone con ambas sin
 * interferencias, igual que el resto del modelo rítmico.
 */
enum class StrumRange(val label: String) {
    ALL("Todas"),
    BASS("Graves"),
    TREBLE("Agudos");

    /** Indica si la flecha se dibuja corta (registro parcial). */
    val isShort: Boolean get() = this != ALL

    companion object {
        /** Registros ofrecidos en la paleta de trazos, en orden de uso. */
        val palette: List<StrumRange> = listOf(ALL, BASS, TREBLE)
    }
}

/**
 * Posición rítmica: **una unidad editable** del patrón.
 *
 * Modelo independiente y ortogonal:
 *  - [figure] representa CUÁNDO y CUÁNTO dura el evento.
 *  - [direction] representa CÓMO se ejecuta el rasgueo (↓ / ↑ / ×).
 *  - [range] representa QUÉ cuerdas suenan (todas / graves / agudos).
 *
 * Los tres campos son propiedades independientes de la misma posición:
 * cambiar la figura NO altera la dirección, cambiar la dirección NO altera la duración y
 * cambiar el registro NO altera ninguna de las dos anteriores.
 * Un silencio ([figure].isRest) no tiene dirección de rasgueo.
 */
data class RhythmEvent(
    val figure: RhythmFigure,
    val direction: StrumDirection = StrumDirection.DOWN,
    val articulation: StrumArticulation = StrumArticulation.NONE,
    val range: StrumRange = StrumRange.ALL,
    /**
     * Indica que esta figura **continúa** la nota anterior (ligadura) en lugar de iniciar un
     * ataque nuevo.
     *
     * Es lo que permite representar duraciones que no caben en una sola figura sin perder tiempo:
     * una nota de 2,5 negras se guarda como `blanca` + `corchea ligada`, y la rejilla la vuelve a
     * ver como un único golpe prolongado. Sin ligaduras, la conversión rejilla ↔ modelo sería
     * **con pérdida** y el patrón guardado no coincidiría con lo dibujado.
     */
    val isTied: Boolean = false
) {
    /** Valor temporal del evento en negras. */
    val beatValue: Double get() = figure.beatValue

    /** Dirección efectiva: los silencios nunca tienen golpe. */
    val effectiveDirection: StrumDirection
        get() = if (figure.isRest) StrumDirection.NONE else direction

    /** Indica si la posición representa un golpe sonoro (incluidas las ligaduras). */
    val isStruck: Boolean get() = !figure.isRest && direction.isStruck

    /**
     * Indica si la posición **inicia** un ataque: suena y no es continuación de otra.
     *
     * Es la propiedad que decide si hay que **volver a golpear** (reproducción) o si hay que
     * dibujar una flecha nueva (rejilla). Una ligadura suena, pero no se ataca.
     */
    val isAttack: Boolean get() = isStruck && !isTied

    /**
     * Indica si el golpe lleva acento. Propiedad derivada de [articulation]:
     * la articulación es la **única fuente de verdad**.
     */
    val isAccent: Boolean get() = articulation.isAccent

    /** Articulación efectiva: un silencio nunca se articula. */
    val effectiveArticulation: StrumArticulation
        get() = if (figure.isRest) StrumArticulation.NONE else articulation

    /** Registro efectivo: un silencio no ataca ninguna cuerda. */
    val effectiveRange: StrumRange
        get() = if (figure.isRest) StrumRange.ALL else range

    /** Nombre canónico del símbolo ChordPro equivalente a este golpe. */
    val symbolName: String
        get() = effectiveArticulation.symbolNameFor(effectiveDirection)

    /** Glifo Unicode equivalente (aproximación de la fuente ChordPro). */
    val symbolGlyph: String
        get() = com.grafitto.laudate.domain.model.chordpro.ChordProSymbol
            .fromName(symbolName)?.glyph
            ?: effectiveDirection.arrow
}

/**
 * Patrón de rasgueo completo asociado a un compás.
 *
 * El patrón es una **secuencia plana de posiciones rítmicas**. Las separaciones `|`
 * de la representación visual NO se almacenan: se derivan del compás mediante
 * [metricGroups] / [beatBoundaryIndices].
 */
data class RhythmPattern(
    val id: String,
    val name: String,
    val timeSignature: TimeSignature,
    val events: List<RhythmEvent> = emptyList(),
    val isBuiltIn: Boolean = false,
    val createdAt: Long = nowMillis()
) {
    companion object {
        /**
         * Rellena el patrón **global** del canto y el de cada sección a partir de la lista de
         * patrones disponibles. Los patrones se resuelven por `id` (global) y por **nombre** (sección,
         * `{x_rhythm: Nombre}`). Es un paso de **presentación** previo a la maquetación del PDF y al
         * pintado del visor: los campos [Song.rhythmPattern] / [SongSection.rhythmPattern] no se
         * persisten. Devuelve el canto sin cambios si no se aporta ningún patrón.
         */
        fun resolveFor(song: Song, patterns: List<RhythmPattern>): Song {
            if (patterns.isEmpty()) return song
            val global = song.rhythmPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
            val byName = patterns.associateBy { it.name.lowercase() }
            val sections = song.sections.map { section ->
                val named = section.rhythmPatternName?.let { byName[it.lowercase()] }
                if (named == null) section else section.copy(rhythmPattern = named)
            }
            return song.copy(rhythmPattern = global, sections = sections)
        }
    }

    /** Suma temporal del patrón en negras. */
    val totalBeatValue: Double
        get() = events.sumOf { it.beatValue }

    /** Duración de un compás del patrón, en negras. */
    val barBeatValue: Double
        get() = timeSignature.totalQuarterBeats

    /**
     * Número de compases que ocupa el patrón.
     *
     * Un patrón puede ocupar **varios compases** (p. ej. un ritmo de dos compases). Se deriva del
     * total, de modo que nunca puede quedar desincronizado con [events]: un patrón parcial cuenta
     * como el compás que está empezando a llenar.
     */
    val barCount: Int
        get() {
            val bar = barBeatValue
            if (bar <= 0.0) return 1
            val bars = totalBeatValue / bar
            return kotlin.math.ceil(bars - 1e-6).toInt().coerceAtLeast(1)
        }

    /** Duración objetivo del patrón completo: [barCount] compases. */
    val targetBeatValue: Double
        get() = barCount * barBeatValue

    /**
     * Indica si el patrón está **completo y es guardable**: ocupa un número entero de compases y
     * contiene al menos un golpe.
     *
     * Un patrón sin ningún golpe (todo silencios) no es un ritmo y no se puede guardar.
     */
    val isValid: Boolean
        get() {
            if (events.isEmpty()) return false
            if (events.none { it.isAttack }) return false
            val bar = barBeatValue
            if (bar <= 0.0) return false
            val bars = totalBeatValue / bar
            return abs(bars - bars.roundToInt()) < 1e-3
        }

    /** Diferencia temporal respecto al final del último compás empezado (positiva = falta). */
    val remainingBeatValue: Double
        get() = targetBeatValue - totalBeatValue

    /**
     * Agrupa las posiciones por pulso del compás para la representación visual.
     *
     * La separación `|` se genera a partir de la estructura métrica real del compás
     * ([TimeSignature.beatUnitQuarterBeats]) y nunca se almacena en el patrón.
     */
    fun metricGroups(): List<List<RhythmEvent>> {
        if (events.isEmpty()) return emptyList()

        val beatUnit = timeSignature.beatUnitQuarterBeats
        val groups = mutableListOf<List<RhythmEvent>>()
        val current = mutableListOf<RhythmEvent>()
        var accumulated = 0.0

        events.forEach { event ->
            current.add(event)
            accumulated += event.beatValue

            // Cierra el grupo al alcanzar (o superar) el siguiente límite de pulso.
            if (accumulated >= beatUnit - 1e-6) {
                groups.add(current.toList())
                current.clear()
                accumulated -= beatUnit
                if (accumulated < 1e-6) accumulated = 0.0
            }
        }
        if (current.isNotEmpty()) groups.add(current.toList())
        return groups
    }

    /** Índices de inicio de cada pulso dentro de [events], útil para insertar separadores. */
    fun beatBoundaryIndices(): Set<Int> {
        if (events.isEmpty()) return emptySet()
        val beatUnit = timeSignature.beatUnitQuarterBeats
        val boundaries = mutableSetOf(0)
        var accumulated = 0.0
        events.forEachIndexed { index, event ->
            accumulated += event.beatValue
            if (accumulated >= beatUnit - 1e-6) {
                accumulated -= beatUnit
                if (accumulated < 1e-6) accumulated = 0.0
                if (index + 1 < events.size) boundaries.add(index + 1)
            }
        }
        return boundaries
    }

    /** Posición (en negras) a la que empieza una posición rítmica. */
    fun startBeatOf(index: Int): Double =
        events.take(index.coerceIn(0, events.size)).sumOf { it.beatValue }
}

/**
 * Modo global de representación de patrones rítmicos para el usuario.
 * Todas las vistas son representaciones del MISMO modelo de datos.
 */
enum class RhythmNotationMode(
    val displayName: String
) {
    ARROWS("Flechas"),
    MUSICAL_SYMBOLS("Figuras"),
    COMBINED("Combinada"),

    /**
     * Símbolos oficiales de ChordPro (`<sym arrow-down-with-accent/>`): incluye la articulación
     * (acento, arpegio, staccato) del golpe, no solo su dirección.
     */
    CHORDPRO_SYMBOLS("Símbolos ChordPro")
}
