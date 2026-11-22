package com.grafitto.laudate.ui.song

import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.model.tablature.Tablature

data class SongDetailUiState(
    val isLoading: Boolean = false,
    val song: Song? = null,
    val originalKey: String = "C",
    val currentKey: String = "C",
    val transpositionSemitones: Int = 0,
    val capoPosition: Int = 0,
    val notationSystem: NotationSystem = NotationSystem.LATIN,
    val displayMode: SongDisplayMode = SongDisplayMode.CHORDS_ABOVE_TEXT,
    val rhythmNotationMode: RhythmNotationMode = RhythmNotationMode.ARROWS,
    /** Tablaturas del canto (ADR-017). Se muestran según [displayMode]. */
    val tablatures: List<Tablature> = emptyList(),
    /** Tablatura elegida cuando hay varias; `null` usa la primera. */
    val selectedTablatureId: String? = null,
    /**
     * **Instrumento preferido** del usuario (nombre de la afinación, p. ej. «Guitarra»). Cuando está
     * fijado, el visor muestra **solo** las tablaturas de ese instrumento, para no mezclar en
     * pantalla la de guitarra con la de bajo. Es una preferencia **global** (se recuerda para todas
     * las canciones) que el usuario puede cambiar desde el propio visor.
     */
    val preferredTablatureInstrument: String? = null,
    /** Posición de la tablatura respecto a la letra en el modo mixto (preferencia persistente). */
    val tabDisplayPosition: TabDisplayPosition = TabDisplayPosition.AFTER,
    /** Escala del dibujo de las tablaturas (preferencia global, para cifras más grandes en atril). */
    val tablatureScale: Float = 1f,
    /** Escala **solo de las cifras de traste**, independiente de la rejilla (preferencia global). */
    val tablatureNumberScale: Float = 1f,
    /**
     * **Ajustes del pentagrama** de tablatura (preferencia global, D-48): cifras, líneas, pentagrama
     * rítmico, blanca, cifra de la nota ligada y cabecera. El visor dibuja con los **mismos** ajustes
     * con que se editó, de modo que lo que se ve en pantalla es lo que sale en el PDF.
     */
    val tablatureStaffOptions: com.grafitto.laudate.domain.tablature.TabStaffOptions =
        com.grafitto.laudate.domain.tablature.TabStaffOptions(),
    val rhythmPattern: RhythmPattern? = null,
    /**
     * Patrón rítmico por sección, resuelto desde las directivas `{x_rhythm: Nombre}` del canto.
     * La clave es el `sequenceOrder` de la sección. Permite que cada sección muestre su propio
     * rasgueo (verso, coro, intro) sin salir del visor.
     */
    val sectionRhythmPatterns: Map<Int, RhythmPattern> = emptyMap(),
    val isControlsExpanded: Boolean = false,
    /**
     * Cabecero de metadatos plegado por defecto: plegado muestra una sola línea con lo que
     * quepa; desplegado, todos los metadatos (autor, número, capo, compás, tempo, notas…).
     */
    val isMetadataExpanded: Boolean = false,
    /**
     * Modo lectura sin distracciones: oculta barra superior, metadatos y controles de lectura
     * para dejar en pantalla solo el título y la canción (útil en ensayos y celebraciones).
     */
    val isFocusMode: Boolean = false,
    val fontSizeSp: Float = 16f,
    val isAutoScrolling: Boolean = false,
    /** Adjuntos del canto (audio, vídeo, documentos, imágenes y enlaces). */
    val attachments: List<SongAttachment> = emptyList(),
    val errorMessage: String? = null
) {
    /**
     * Tablaturas **ya transpuestas para su visualización**: siguen la transposición del canto según
     * el modo de cada tablatura (cejilla / redigitado / bloqueado). Es **temporal** (no se guarda).
     *
     * Los semitonos incluyen, además de la transposición **en vivo**, el **cambio de tonalidad
     * guardado** del canto respecto a la tonalidad **escrita** de cada tablatura ([Tablature.writtenKey]).
     */
    val displayTablatures: List<Tablature>
        get() = tablatures.map { tab ->
            val semitones = transpositionSemitones +
                com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.semitonesFromWrittenKey(tab, songKey)
            if (semitones == 0) tab
            else com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.transposeForDisplay(tab, semitones)
        }

    /** Instrumentos (afinaciones) que el canto tiene en sus tablaturas, sin repetir y en orden. */
    val tablatureInstruments: List<String>
        get() = tablatures.map { it.tuning.name }.distinct()

    /**
     * Tablaturas **que se muestran**, filtradas por el [preferredTablatureInstrument]: si hay una
     * preferencia y el canto tiene tablaturas de ese instrumento, se muestran solo esas; si no,
     * todas. Así, en una canción con guitarra y bajo, quien toca la guitarra no ve el bajo de más.
     */
    val visibleTablatures: List<Tablature>
        get() {
            val preferred = preferredTablatureInstrument
            val filtered = if (preferred != null) displayTablatures.filter { it.tuning.name == preferred } else emptyList()
            return filtered.ifEmpty { displayTablatures }
        }

    /** Tablatura que se debe mostrar (la elegida o, si no hay elección, la primera visible). */
    val visibleTablature: Tablature?
        get() = visibleTablatures.firstOrNull { it.id == selectedTablatureId } ?: visibleTablatures.firstOrNull()

    /**
     * Tablaturas **generales**: las que no están atadas a una sección concreta y se muestran en el
     * bloque común del final.
     *
     * Incluye también las **huérfanas**: tablaturas cuya sección ya no existe (se borró la sección,
     * se reescribió el ChordPro o la vista previa del editor regenera las secciones con identificadores
     * nuevos). Antes se quedaban fuera de las dos listas y **desaparecían de la pantalla**.
     */
    val generalTablatures: List<Tablature>
        get() {
            val sectionIds = song?.sections?.mapTo(mutableSetOf()) { it.id }.orEmpty()
            return visibleTablatures.filter { it.sectionId == null || it.sectionId !in sectionIds }
        }

    /** Tablaturas asociadas a una sección concreta, para intercalarlas junto a su parte. */
    fun tablaturesForSection(sectionId: String): List<Tablature> =
        visibleTablatures.filter { it.sectionId == sectionId }

    /**
     * Modo de visualización **efectivo**: si el modo elegido muestra tablatura pero el canto no
     * tiene ninguna, se cae a «Letra y acordes» para no dejar la pantalla vacía.
     */
    val effectiveDisplayMode: SongDisplayMode
        get() = if (displayMode.showsTablature && tablatures.isEmpty()) SongDisplayMode.CHORDS_ABOVE_TEXT
        else displayMode

    /** Tonalidad vigente del canto en el visor (la que sirve de destino a la tablatura). */
    private val songKey: String get() = currentKey.ifBlank { originalKey }
}

sealed interface SongDetailUiIntent {
    data class LoadSong(val songId: String) : SongDetailUiIntent
    data class LoadChordPro(val chordPro: String) : SongDetailUiIntent
    data class TransposeKey(val semitonesDelta: Int) : SongDetailUiIntent
    data object ResetTransposition : SongDetailUiIntent
    data class SetCapo(val capo: Int) : SongDetailUiIntent
    data class ToggleNotationSystem(val system: NotationSystem) : SongDetailUiIntent
    data class ChangeDisplayMode(val mode: SongDisplayMode) : SongDetailUiIntent
    data class ChangeRhythmNotationMode(val mode: RhythmNotationMode) : SongDetailUiIntent
    /** Elige la tablatura que se muestra cuando el canto tiene varias (`null` = la primera). */
    data class SelectTablature(val tablatureId: String?) : SongDetailUiIntent
    /**
     * Fija el **instrumento preferido** (tablatura) que se muestra en todas las canciones.
     * `null` = sin preferencia (se muestran todas las tablaturas).
     */
    data class SelectTablatureInstrument(val instrument: String?) : SongDetailUiIntent
    /** Cambia la posición de la tablatura en la vista mixta (arriba / abajo / a la derecha). */
    data class ChangeTabDisplayPosition(val position: TabDisplayPosition) : SongDetailUiIntent
    /**
     * **Mueve** la tablatura a otra sección del canto (`null` = bloque general del final). Es la
     * forma de colocarla junto a la intro, la estrofa o el coro sin salir del visor.
     */
    data class MoveTablatureToSection(val tablatureId: String, val sectionId: String?) : SongDetailUiIntent
    /** **Reordena** la tablatura dentro de su grupo (`delta` = -1 la sube, +1 la baja). */
    data class MoveTablatureOrder(val tablatureId: String, val delta: Int) : SongDetailUiIntent
    data object ToggleControls : SongDetailUiIntent
    /** Pliega o despliega el cabecero de metadatos del canto. */
    data object ToggleMetadata : SongDetailUiIntent
    /** Muestra u oculta el modo lectura sin distracciones. */
    data object ToggleFocusMode : SongDetailUiIntent
    data class SetAutoScroll(val active: Boolean) : SongDetailUiIntent
    data class UpdateFontSize(val newSizeSp: Float) : SongDetailUiIntent
    data object SaveFontSize : SongDetailUiIntent
    /** Cambia el tamaño del dibujo de la tablatura (rejilla y cifras). */
    data class UpdateTablatureScale(val newScale: Float) : SongDetailUiIntent
    data object SaveTablatureScale : SongDetailUiIntent
    /** Cambia el tamaño **solo de las cifras de traste** de la tablatura. */
    data class UpdateTablatureNumberScale(val newScale: Float) : SongDetailUiIntent
    data object SaveTablatureNumberScale : SongDetailUiIntent
    /** Añade un fichero adjunto elegido con el selector del sistema. */
    data class AddAttachmentFromUri(
        val sourceUri: String,
        val displayName: String,
        val mimeType: String?
    ) : SongDetailUiIntent
    /** Añade un enlace externo (YouTube, Drive, web…). */
    data class AddAttachmentLink(val url: String, val title: String? = null) : SongDetailUiIntent
    /** Elimina un adjunto (fichero y registro). */
    data class DeleteAttachment(val attachment: SongAttachment) : SongDetailUiIntent
    /** Descarga/exporta el fichero local de un adjunto al destino elegido con el selector. */
    data class ExportAttachment(val attachment: SongAttachment, val destinationUri: String) : SongDetailUiIntent
    data class DeleteSong(val songId: String) : SongDetailUiIntent
}

sealed interface SongDetailSideEffect {
    data class ShowMessage(val message: String) : SongDetailSideEffect
    data class NavigateBack(val dummy: Unit = Unit) : SongDetailSideEffect
}
