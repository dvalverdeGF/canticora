package com.grafitto.laudate.ui.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.rhythm.RhythmGrid
import com.grafitto.laudate.domain.rhythm.RhythmGridCell
import com.grafitto.laudate.domain.rhythm.RhythmGridMapping
import com.grafitto.laudate.domain.rhythm.RhythmGridResolution
import com.grafitto.laudate.domain.rhythm.RhythmPlaybackScheduler
import com.grafitto.laudate.domain.rhythm.StrummingAutomation

/**
 * Modo de edición del patrón. Ambos modos operan sobre **el mismo modelo**: son dos formas
 * de la misma información, no dos patrones distintos.
 *
 *  - [GRID]: rejilla «Beat & Stroke» (subdivisiones tocables). Es la vía rápida.
 *  - [STRUCTURED]: editor por posiciones con figuras, articulaciones y símbolos ChordPro.
 */
enum class RhythmEditorMode(val label: String) {
    GRID("Rejilla"),
    STRUCTURED("Avanzado")
}

data class RhythmPatternUiState(
    val isLoading: Boolean = true,
    val allPatterns: List<RhythmPattern> = emptyList(),
    val notationMode: RhythmNotationMode = RhythmNotationMode.COMBINED,
    /** Posición seleccionada en el editor, cuya articulación se está editando. */
    val selectedEventIndex: Int? = null,
    val selectedTimeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val editorEvents: List<RhythmEvent> = emptyList(),
    val editorName: String = "",
    val editingPatternId: String? = null,
    /**
     * Id del patrón sobre el que actúan los botones del editor (compartir, copiar, duplicar,
     * eliminar). Coincide con [editingPatternId] salvo en las **copias** sin guardar, que aún no
     * tienen id propio pero sí un origen sobre el que operar.
     */
    val actionPatternId: String? = null,
    /**
     * Indica si el editor está creando un patrón **nuevo** (o una copia) y no editando uno
     * existente. No se puede derivar de [editingPatternId]: una copia de un patrón existente se
     * abre con `editingPatternId == null` (se guardará con un id nuevo), pero **no** es nueva.
     */
    val editorIsNew: Boolean = true,
    val isEditorOpen: Boolean = false,
    /** Modo de edición activo: rejilla rápida o editor avanzado por posiciones. */
    val editorMode: RhythmEditorMode = RhythmEditorMode.GRID,
    /** Subdivisión elegida para la rejilla «Beat & Stroke». */
    val gridResolution: RhythmGridResolution = RhythmGridResolution.EIGHTH,
    /**
     * Trazo activo en la paleta: el estado (dirección + registro de cuerdas) que se aplica
     * al tocar una casilla. Permite escribir flechas largas (todas) y cortas (graves/agudos)
     * de forma directa, sin ciclar.
     */
    val paletteCell: RhythmGridCell = RhythmGridCell.Down,
    val previewBpm: Int = RhythmPlaybackScheduler.DEFAULT_BPM,
    /** Indica si el patrón se está reproduciendo ahora mismo. */
    val isPlaying: Boolean = false,
    /** Posición que está sonando durante la reproducción, para resaltarla (RF-307). */
    val activeEventIndex: Int? = null,
    val errorMessage: String? = null
) {
    /** Patrones compatibles con el compás seleccionado. */
    val visiblePatterns: List<RhythmPattern>
        get() = allPatterns.filter { it.timeSignature == selectedTimeSignature }

    /** Suma temporal del patrón en construcción. */
    val editorTotalBeats: Double
        get() = editorEvents.sumOf { it.beatValue }

    /** Número de compases que ocupa el patrón en construcción (mínimo uno). */
    val editorBarCount: Int
        get() = editorAsPattern().barCount

    /** Duración objetivo del patrón: [editorBarCount] compases completos. */
    val targetBeats: Double
        get() = editorBarCount * selectedTimeSignature.totalQuarterBeats

    /**
     * Indica si el patrón en construcción es **guardable**: ocupa compases completos y contiene
     * al menos un golpe. Es la misma regla que aplica el Dominio ([RhythmPattern.isValid]), de modo
     * que la UI nunca bloquea un patrón que el modelo considera correcto.
     */
    val isEditorValid: Boolean
        get() = editorAsPattern().isValid

    /** Dirección que le correspondería al próximo golpe añadido al final. */
    val nextStrumDirection: StrumDirection
        get() = StrummingAutomation.nextDirection(editorEvents, selectedTimeSignature)

    /** Representación del editor como patrón, para reutilizar la misma visualización. */
    fun editorAsPattern(): RhythmPattern = RhythmPattern(
        id = editingPatternId ?: "editor",
        name = editorName,
        timeSignature = selectedTimeSignature,
        events = editorEvents,
        isBuiltIn = false
    )

    /**
     * Resolución **efectiva** de la rejilla: la elegida por el usuario si representa el patrón
     * sin pérdida; en caso contrario, la más gruesa que sí lo hace. Así la rejilla nunca
     * desentona del modelo aunque el patrón se edite en el modo avanzado.
     *
     * OJO: estas propiedades derivadas deben ser `get()` (calculadas), NUNCA propiedades de
     * constructor. Si se declararan en el constructor, `copy()` las arrastraría **obsoletas**
     * sin recalcularlas, y la rejilla dejaría de reflejar el modelo.
     */
    val effectiveGridResolution: RhythmGridResolution
        get() = RhythmGridMapping.effectiveResolution(editorAsPattern(), gridResolution)

    /**
     * Rejilla «Beat & Stroke» **derivada** del modelo: no es estado duplicado, de modo que
     * cualquier edición (en la rejilla o en el editor avanzado) se refleja en ambas vistas
     * de forma automática y sin posibilidad de desincronizarse.
     *
     * Siempre muestra **compases completos** ([editorBarCount]), que es lo que garantiza que
     * tocar una casilla nunca altere la duración del patrón.
     */
    val gridCells: List<RhythmGridCell>
        get() = RhythmGridMapping.toGrid(editorAsPattern(), effectiveGridResolution).cells

    /**
     * Índices de casilla que inician una línea de tiempo, para los separadores `|`.
     * Se derivan del **mismo** criterio que la fila de etiquetas (`slotsPerBeat`), de modo que
     * el separador siempre cae bajo la cifra del pulso y nunca bajo un `&`.
     */
    val gridBeatBoundaries: Set<Int>
        get() {
            val perBeat = effectiveGridResolution.slotsPerBeat(selectedTimeSignature)
            return if (perBeat <= 1) emptySet() else gridCells.indices.filter { it % perBeat == 0 }.toSet()
        }

    /** Índices de casilla que inician un compás, para los separadores `‖` (patrones de 2+ compases). */
    val gridBarBoundaries: Set<Int>
        get() {
            val perBar = effectiveGridResolution.slotCount(selectedTimeSignature)
            return if (perBar <= 0) emptySet() else gridCells.indices.filter { it % perBar == 0 }.toSet()
        }

    /** Índice de casilla donde empieza cada evento, para resaltar el golpe activo. */
    val gridSlotStarts: List<Int>
        get() = RhythmGridMapping.slotStarts(editorAsPattern(), effectiveGridResolution)

    /** Casilla que está sonando (RF-307), traducida desde el índice de evento activo. */
    val activeGridSlot: Int?
        get() = activeEventIndex?.let { gridSlotStarts.getOrNull(it) }
}

sealed interface RhythmPatternUiIntent {
    data object LoadPatterns : RhythmPatternUiIntent
    data class SetNotationMode(val mode: RhythmNotationMode) : RhythmPatternUiIntent
    data class SelectTimeSignature(val timeSignature: TimeSignature) : RhythmPatternUiIntent
    data object OpenNewPatternEditor : RhythmPatternUiIntent
    data class OpenEditPattern(val patternId: String) : RhythmPatternUiIntent
    data class DuplicatePattern(val patternId: String) : RhythmPatternUiIntent
    data class UpdateEditorName(val name: String) : RhythmPatternUiIntent
    data class SetEditorTimeSignature(val timeSignature: TimeSignature) : RhythmPatternUiIntent
    /** Cambia entre la rejilla rápida y el editor avanzado (mismo modelo en ambos). */
    data class SetEditorMode(val mode: RhythmEditorMode) : RhythmPatternUiIntent

    // ── Rejilla «Beat & Stroke» (subdivisiones tocables) ──────────────────────
    /** Cambia la subdivisión de la rejilla (negras, corcheas, semicorcheas). */
    data class SetGridResolution(val resolution: RhythmGridResolution) : RhythmPatternUiIntent
    /** Selecciona el trazo de la paleta (↓ / ↑ / × / graves / agudos / aire / prolongar). */
    data class SetPaletteCell(val cell: RhythmGridCell) : RhythmPatternUiIntent
    /** Aplica el trazo activo de la paleta a una casilla (toque corto). */
    data class SetGridCell(val slot: Int) : RhythmPatternUiIntent
    /** Cicla el estado de una casilla: ↓ → ↑ → aire → prolongar → × → ↓. */
    data class CycleGridCell(val slot: Int) : RhythmPatternUiIntent
    /** Deja una casilla en aire (toque largo). */
    data class ClearGridCell(val slot: Int) : RhythmPatternUiIntent
    /** Añade un compás al final del patrón (se rellena con silencios). */
    data object AddBar : RhythmPatternUiIntent
    /** Quita el último compás del patrón. */
    data object RemoveBar : RhythmPatternUiIntent

    // ── Edición de posiciones rítmicas (mismo modelo, distintas representaciones) ──
    /** Añade una posición al final (figura sonora o silencio). */
    data class AddEvent(val figure: RhythmFigure) : RhythmPatternUiIntent
    /** Añade un golpe percusivo (×) al final. */
    data class AddMuteEvent(val figure: RhythmFigure = RhythmFigure.QUARTER) : RhythmPatternUiIntent
    /** Sustituye la figura de una posición conservando su dirección. */
    data class SetEventFigure(val index: Int, val figure: RhythmFigure) : RhythmPatternUiIntent
    /** Sustituye la dirección de una posición conservando su figura. */
    data class SetEventDirection(val index: Int, val direction: StrumDirection) : RhythmPatternUiIntent
    /** Cicla la dirección de una posición (↓ → ↑ → × → ↓). */
    data class CycleEventDirection(val index: Int) : RhythmPatternUiIntent
    /** Rota la articulación del golpe: normal → acento → arpegio → staccato → … */
    data class CycleEventArticulation(val index: Int) : RhythmPatternUiIntent
    /** Selecciona la posición activa para editar su articulación. */
    data class SelectEvent(val index: Int?) : RhythmPatternUiIntent
    /** Fija la articulación de una posición concreta. */
    data class SetEventArticulation(val index: Int, val articulation: StrumArticulation) : RhythmPatternUiIntent
    /** Fija el registro de cuerdas (todas/graves/agudos) de una posición concreta. */
    data class SetEventRange(val index: Int, val range: StrumRange) : RhythmPatternUiIntent
    /** Alterna el acento de una posición. */
    data class ToggleEventAccent(val index: Int) : RhythmPatternUiIntent
    /** Convierte una posición en silencio (o de silencio a golpe). */
    data class ToggleEventRest(val index: Int) : RhythmPatternUiIntent
    /** Elimina una posición. */
    data class RemoveEvent(val index: Int) : RhythmPatternUiIntent
    /** Mueve una posición a la izquierda. */
    data class MoveEventLeft(val index: Int) : RhythmPatternUiIntent
    /** Mueve una posición a la derecha. */
    data class MoveEventRight(val index: Int) : RhythmPatternUiIntent
    /** Redistribuye las direcciones aplicando la regla del péndulo. */
    data object AutoAssignDirections : RhythmPatternUiIntent
    /** Regenera las direcciones ↓/↑ de la rejilla con la regla del péndulo. */
    data object AutoAssignGridDirections : RhythmPatternUiIntent
    data object RemoveLastEvent : RhythmPatternUiIntent
    data object ClearEditor : RhythmPatternUiIntent

    data object SavePattern : RhythmPatternUiIntent
    data class DeletePattern(val patternId: String) : RhythmPatternUiIntent
    /** Elimina el patrón que se está editando y cierra el editor. */
    data object DeleteEditingPattern : RhythmPatternUiIntent
    /** Restaura el último patrón eliminado (deshacer desde el snackbar). */
    data object UndoDeletePattern : RhythmPatternUiIntent
    data object CloseEditor : RhythmPatternUiIntent

    // ── Previsualización sonora (RF-306 / RF-307) ──────────────────────────────
    /** Fija el BPM de la previsualización (acotado al rango válido). */
    data class SetPreviewBpm(val bpm: Int) : RhythmPatternUiIntent
    /** Ajusta el BPM sumando [delta] (para los botones − / +). */
    data class AdjustPreviewBpm(val delta: Int) : RhythmPatternUiIntent
    /** Inicia o detiene la reproducción del patrón en construcción. */
    data object TogglePreviewPlayback : RhythmPatternUiIntent
    /** Detiene la reproducción (p. ej. al salir de la pantalla). */
    data object StopPreview : RhythmPatternUiIntent

    // ── Intercambio de ritmos (RF-310) ──────────────────────────────────────
    /** Comparte un patrón por la hoja del sistema (archivo `.canticora-rhythm`). */
    data class SharePattern(val patternId: String) : RhythmPatternUiIntent
    /** Copia un patrón al portapapeles como texto pegable. */
    data class CopyPatternAsText(val patternId: String) : RhythmPatternUiIntent
    /** Comparte la colección completa de ritmos propios. */
    data object ShareAllPatterns : RhythmPatternUiIntent
    /** Importa ritmos desde un texto pegado. */
    data class ImportPatternsFromText(val text: String) : RhythmPatternUiIntent

    data class ShowError(val message: String) : RhythmPatternUiIntent
}
sealed interface RhythmPatternSideEffect {
    data class ShowMessage(val message: String) : RhythmPatternSideEffect
    /** Se ha eliminado un patrón: la raíz ofrece «Deshacer» en el snackbar. */
    data class ShowUndoMessage(val message: String) : RhythmPatternSideEffect
    /** Abre la hoja para compartir con el contenido ya preparado. */
    data class ShareContent(val content: String, val fileName: String, val mimeType: String) : RhythmPatternSideEffect
    /** Copia texto al portapapeles. */
    data class CopyToClipboard(val text: String) : RhythmPatternSideEffect
}
