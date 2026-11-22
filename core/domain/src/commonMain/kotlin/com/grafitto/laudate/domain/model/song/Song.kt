package com.grafitto.laudate.domain.model.song

import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.platform.nowMillis

/**
 * Anclaje preciso de un acorde sobre una posición de línea y carácter.
 */
data class ChordAnchor(
    val id: String,
    val sectionId: String,
    val lineIndex: Int,
    val characterIndex: Int,
    val chord: DomainChord,
    val rawChordText: String
)

/**
 * Naturaleza de una línea dentro de una sección.
 */
enum class LineType {
    /** Línea de letra (puede llevar acordes anclados). */
    LYRICS,
    /**
     * Línea **solo de acordes** (intro, riff, outro, pasaje instrumental): tiene acordes anclados
     * pero ninguna letra debajo. El visor la pinta como una fila de acordes propia —bloque de
     * «Solo»/«Intro»/«Instrumental»— y la exportación al PDF la respeta sin reservar un renglón
     * de letra vacío.
     */
    CHORD_ONLY,
    /** Comentario del canto (p. ej. indicación de ensayo o dinámica). Se renderiza tal cual. */
    COMMENT,
    /** Encabezado de navegación (p. ej. el rótulo de un salto). Se renderiza como comentario destacado. */
    HEADER
}

/**
 * Línea de texto con acordes superpuestos.
 *
 * [type] distingue la letra real del canto ([LineType.LYRICS]) de las **filas solo de acordes**
 * ([LineType.CHORD_ONLY], típicas de intro/riff/outro) y de los comentarios y encabezados
 * ([LineType.COMMENT] / [LineType.HEADER]), que se muestran como texto destacado sin acordes.
 * [comment] conserva el texto del comentario y [voice] la voz destinataria
 * (`{comment-alto: ...}` → voice = "alto"), o `null` si es un comentario general.
 */
data class SongLine(
    val lineIndex: Int,
    val lyrics: String,
    val chords: List<ChordAnchor> = emptyList(),
    val type: LineType = LineType.LYRICS,
    val comment: String? = null,
    val voice: String? = null
)

/**
 * Tipos de secciones de un canto litúrgico.
 */
enum class SectionType {
    INTRO,
    VERSE,
    PRE_CHORUS,
    CHORUS,
    BRIDGE,
    INTERLUDE,
    OUTRO,
    ACCLAMATION
}

/**
 * Sección de una canción con sus líneas y acordes asociados.
 *
 * [label] es la etiqueta declarada por el usuario (p. ej. "Verse 1", "Estribillo"),
 * independiente del [sectionType] inferido. [voice] identifica la voz o grupo que la canta
 * (soprano, tenor, asamblea...) cuando el canto es polifónico o dialogado.
 *
 * [isRepeated] indica que la sección se repite (bises). [repeatCount] es el número total de
 * ejecuciones cuando se conoce (`{repeat: 2}` → 2), o `null` si solo se sabe que se repite.
 *
 * [rhythmPatternName] es el nombre del patrón rítmico declarado con la directiva ChordPro
 * `{x_rhythm: Nombre}` para **esta** sección; permite que el canto cambie de rasgueo al pasar
 * de sección (p. ej. verso en «Rock» y estribillo en «Semicorcheas funk»). `null` = sin cambio.
 *
 * [rhythmPattern] es el patrón **resuelto** (por nombre) para `[rhythmPatternName]`. Es un valor
 * **transitorio de presentación** (no se persiste ni se serializa): lo puebla la capa que ya tiene
 * acceso a la biblioteca de ritmos (visor, exportador) para poder dibujar la rejilla en el PDF.
 */
data class SongSection(
    val id: String,
    val songId: String,
    val sectionType: SectionType,
    val sequenceOrder: Int,
    val headerTitle: String?,
    val rawTextContent: String,
    val label: String? = null,
    val voice: String? = null,
    val isRepeated: Boolean = false,
    val repeatCount: Int? = null,
    val rhythmPatternName: String? = null,
    val rhythmPattern: RhythmPattern? = null,
    val lines: List<SongLine> = emptyList()
)

/**
 * Modelo inmutable de Canción en el Dominio.
 */
data class Song(
    val id: String,
    val title: String,
    val originalKey: String,
    /**
     * Número de la canción dentro del cancionero de origen (opcional).
     *
     * No es único ni está restringido a una colección: es habitual que distintos cancioneros
     * reutilicen el mismo número (varios «Gloria» o «Padre Nuestro»), por lo que puede repetirse
     * libremente entre canciones. Sirve para referirse a la canción en los ensayos («el Aleluya 402»).
     */
    val number: String? = null,
    val author: String? = null,
    val rhythm: String? = null,
    val rhythmPatternId: String? = null,
    /** Patrón rítmico global **resuelto** (transitorio de presentación, no persistido). */
    val rhythmPattern: RhythmPattern? = null,
    val tempoBpm: Int? = null,
    val tempoMarking: String? = null,
    val defaultLiturgicalMoments: List<String> = emptyList(),
    val defaultLiturgicalTimes: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val capo: Int = 0,
    val notes: String? = null,
    /**
     * Huella de contenido ([com.grafitto.laudate.domain.hashing.SongContentHasher]), derivada del
     * ChordPro canónico del canto.
     *
     * Se usa para detectar duplicados cuando una canción llega desde fuera (ChordPro compartido,
     * respaldo, cancionero semilla). Es un valor **derivado**: la capa de datos lo recalcula al
     * guardar y lo expone al leer, por lo que nunca debe fijarse a mano.
     */
    val contentHash: String? = null,
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis(),
    val sections: List<SongSection> = emptyList()
)
