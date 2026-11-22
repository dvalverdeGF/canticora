package com.grafitto.laudate.ui.tablature

import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.tablature.TabChordIssue
import com.grafitto.laudate.domain.tablature.TabMixer
import com.grafitto.laudate.domain.tablature.TabChordValidator
import com.grafitto.laudate.domain.tablature.TabNoteRef
import com.grafitto.laudate.domain.tablature.TabNoteSelection
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import com.grafitto.laudate.domain.tablature.TabTranspositionMode
import com.grafitto.laudate.domain.tablature.TablatureEditing
import com.grafitto.laudate.domain.tablature.TablatureSelection

/**
 * Contrato MVI del **editor de tablaturas** (ADR-017, *Feature T*).
 *
 * La pantalla lista las tablaturas de un canto y permite **crear/editar** una con la rejilla por
 * subdivisiones, reutilizando el patrón del editor de rasgueo (`BeatStrokeGrid`).
 */
data class TablatureUiState(
    val isLoading: Boolean = true,
    /**
     * Canto al que pertenecen las tablaturas en edición. El editor **siempre** se abre dentro de un
     * canto, así que nunca es nulo (cadena vacía = aún sin cargar). Una tablatura no existe suelta.
     */
    val songId: String = "",
    val songTitle: String = "",
    /** Tonalidad del canto, para precargar la **tonalidad escrita** de una tablatura nueva. */
    val songKey: String = "",
    /** Tablaturas existentes del canto. */
    val tablatures: List<Tablature> = emptyList(),
    /**
     * Tablatura **elegida** para verse (vista previa y panel de herramientas). `null` = la primera.
     * Se recuerda mientras dure la pantalla: es una decisión de lectura, no una preferencia.
     */
    val selectedTablatureId: String? = null,
    val isEditorOpen: Boolean = false,
    val editorIsNew: Boolean = true,
    /** Id de la tablatura en edición (null al crear). */
    val editingId: String? = null,
    val editorTitle: String = "",
    val editorTuning: Tuning = Tuning.GUITAR,
    val editorTime: ScoreTimeSignature = ScoreTimeSignature("4", 4),
    val editorResolution: TabResolution = TabResolution.EIGHTH,
    val editorMeasures: List<TabMeasure> = emptyList(),
    /** Cuerda seleccionada (1 = la más aguda) para escribir en la rejilla. */
    val selectedString: Int = 1,
    /** Traste seleccionado para escribir. */
    val selectedFret: Int = 0,
    /**
     * **Modo de entrada de notas** (paridad con MuseScore 4, ADR-020). Es el estado que muestra el
     * cursor **azul** sobre la cuerda activa y hace que **tocar una cuerda escriba un `0`**. Se
     * enciende con la tecla `N` o con el botón de la barra de entrada, y se apaga con `Esc`.
     *
     * **Desviación deliberada y firmada**: con el modo **apagado**, teclear una **cifra** de traste
     * entra en el modo **y escribe** (ahorra un toque por nota en tableta; en escritorio no pisa
     * ningún atajo real porque la duración en TAB se fija con `Shift`+`0`..`9`).
     */
    val isInputMode: Boolean = false,
    /**
     * **Voz activa** (1–4) de la entrada. MuseScore pinta el cursor y la nota seleccionada con el
     * color de su voz ([com.grafitto.laudate.domain.tablature.TabVoiceColors]).
     *
     * Desde la Fase 3 de paridad la voz **se guarda en la casilla** ([TabSlot.voice]): escribir con
     * la voz 2 crea notas de voz 2, y el lienzo pinta cada cifra con el color de **su** voz, no con
     * el de la voz activa. Es el modelo de polifonía de MuseScore (hasta 4 voces por pentagrama).
     */
    val activeVoice: Int = 1,
    /**
     * **Paletas visibles** (B-12/B-14 de la paridad con MuseScore 4). En MuseScore las paletas viven
     * en un panel **a la izquierda** que se muestra u oculta con `F9`; aquí igual. En pantallas
     * estrechas el panel se abre como hoja inferior en vez de robar ancho a la partitura.
     */
    val showPalettes: Boolean = true,
    /**
     * **Propiedades visibles** (B-11). En MuseScore el panel de propiedades es **contextual** y se
     * muestra u oculta con `F8`: solo enseña lo que se puede cambiar en la selección actual.
     */
    val showProperties: Boolean = true,
    /**
     * La partitura se está **reproduciendo** (B-9). Mientras dura, el cursor sigue la música y la
     * barra de escritura muestra el transporte en marcha; `Espacio` la para.
     */
    val isPlaying: Boolean = false,
    /**
     * Casilla que **suena** ahora mismo durante la reproducción, para que el cursor la siga. `null` =
     * parada. Es independiente de [selectedSlot]: al parar, el cursor vuelve donde estaba.
     */
    val playbackSlot: Pair<Int, Int>? = null,
    /**
     * **Metrónomo** encendido (D-23). Suena un clic en cada **pulsación** del compás (la unidad del
     * denominador: la negra en 4/4, la corchea en 6/8) y **acentúa** la primera —y en los compases
     * compuestos también la mitad—, como el metrónomo de MuseScore. Se guarda en las preferencias.
     */
    val metronomeEnabled: Boolean = false,
    /**
     * **Metrónomo configurable** (D-66): volumen propio, acento del primer tiempo y subdivisión de la
     * pulsación. [metronomeEnabled] se mantiene como espejo de `metronome.enabled` para no romper los
     * sitios que solo encienden y apagan.
     */
    val metronome: com.grafitto.laudate.domain.tablature.TabMetronome =
        com.grafitto.laudate.domain.tablature.TabMetronome.DEFAULT,
    /**
     * **Mezclador de la reproducción** (D-29): volumen, balance, silencio y solo. Se persiste como
     * preferencia porque el volumen del atril depende del sitio, no del canto.
     */
    val mixer: TabMixer = TabMixer.DEFAULT,
    /**
     * **Bucle de rango** (D-23): si no es `null`, la reproducción se limita a esos compases y se
     * repite indefinidamente hasta pararla. Es el «loop playback» de MuseScore, que allí se activa
     * sobre la **selección** de compases.
     */
    val loopRange: IntRange? = null,
    /**
     * **Figura activa** de la barra de escritura: la duración que reciben las notas que se escriban
     * a partir de ahora (y las columnas nuevas que cree el cursor). Es el «modo de duración» de
     * MuseScore/Guitar Pro: se elige la figura **una vez** y se teclean las notas seguidas, en vez de
     * elegir la figura casilla a casilla. `null` = heredar la subdivisión general del compás.
     */
    val selectedFigure: RhythmFigure? = null,
    /** Casilla seleccionada, para editar su traste/prolongación. */
    val selectedSlot: Pair<Int, Int>? = null,
    /**
     * **Búfer de dígitos** del teclado físico: al teclear «1» y «2» el traste es 12. Se vacía al
     * mover el cursor o al salir de la casilla. La UI lo refleja en el indicador del traste.
     */
    val fretBuffer: String = "",
    /** Tonalidad escrita de la tablatura (para cejilla/redigitado). */
    val editorWrittenKey: ScoreKey? = null,
    /** Rótulo libre de la tablatura (p. ej. «Por los niños»), RF-127. */
    val editorLabel: String = "",
    /** Momento litúrgico sugerido (p. ej. «Ofertorio»), RF-127. */
    val editorMoment: String = "",
    /** Nota libre del copista (año, indicaciones), RF-127. */
    val editorNote: String = "",
    /**
     * Secciones del canto a las que se puede asociar la tablatura (intro, estrofa, coro…).
     * Vacío si el editor no conoce al canto (p. ej. en pruebas).
     */
    val availableSections: List<TablatureSectionRef> = emptyList(),
    /** Sección elegida para la tablatura en edición (`null` = general). */
    val editorSectionId: String? = null,
    /**
     * Modo de transposición activo para la tablatura que se está viendo/editando. Por defecto se
     * decide con [com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.defaultMode].
     */
    val transpositionMode: TabTranspositionMode = TabTranspositionMode.CAPO,
    /**
     * Confirmación pendiente de redigitar una tablatura **escrita a mano**. El redigitado cambia
     * las posiciones del usuario, así que **nunca** se hace en silencio (regla de oro).
     */
    val pendingRefinger: PendingRefinger? = null,
    /** Escala del dibujo de las tablaturas (cifras y rejilla); preferencia global. */
    val tablatureScale: Float = 1f,
    /**
     * **Escala de las cifras de traste** dentro del editor, independiente de la rejilla.
     *
     * Es la misma preferencia global que usa el visor ([SongDetailUiState.tablatureNumberScale]),
     * pero el editor no la pasaba al lienzo: siempre dibujaba el número a `1f`, así que en modo
     * edición no había forma de **agrandar la cifra sin agrandar la rejilla** —justo lo que hace
     * falta para leer de lejos en el atril—. Ahora tiene su propio control en el panel de
     * herramientas.
     */
    val tablatureNumberScale: Float = 1f,
    /**
     * **Aspecto del pentagrama** en edición (los ajustes de MuseScore: cifras, líneas, valores de
     * nota, clave, compás). Editor y visor reciben **los mismos**, así que la tablatura que se
     * escribe y la que se lee no pueden diferir.
     */
    val editorStaffOptions: TabStaffOptions = TabStaffOptions(),
    /**
     * El diálogo **Datos de cuerda** está abierto (D-49). Es el «String data» de MuseScore: la tabla
     * de cuerdas con su altura y su marca «siempre al aire», y el número de trastes del instrumento.
     */
    val showStringDataDialog: Boolean = false,
    /**
     * Estado de la tablatura tal y como se abrió o guardó por última vez. Sirve para saber si hay
     * cambios sin guardar sin marcar el editor a mano en cada intent. `null` mientras no hay editor.
     */
    val baselineTablature: Tablature? = null,
    /** El diálogo «descartar cambios» está abierto, a la espera de confirmación. */
    val showDiscardDialog: Boolean = false,
    /**
     * **Selección de compases** (E1): un rango de índices. Con ella se borran/copian/duplican compases
     * en bloque, como en MuseScore. `null` = sin selección.
     */
    val selection: TablatureSelection? = null,
    /**
     * **Selección de notas** (Fase 2, ADR-020): una o varias cifras, para borrarlas, copiarlas o
     * marcarlas en bloque como hace MuseScore. Es **independiente** de [selection] (los compases).
     * `null` = sin selección de notas.
     */
    val noteSelection: TabNoteSelection? = null,
    /**
     * **Ancla** de la selección de notas: la nota donde empezó el gesto de `Mayús`. El rectángulo se
     * dibuja entre ella y [noteSelectionFocus], así que al mover el foco la selección crece y
     * decrece como una goma en vez de acumular notas sueltas.
     */
    val noteSelectionAnchor: TabNoteRef? = null,
    /** **Foco** de la selección de notas: el extremo que mueven `Mayús`+flecha. */
    val noteSelectionFocus: TabNoteRef? = null,
    /** Hay una operación que **deshacer** (pila de estados, E2). */
    val canUndo: Boolean = false,
    /** Hay una operación que **rehacer** (E2). */
    val canRedo: Boolean = false,
    /** Hay un bloque de compases en el **portapapeles** del editor (E3). */
    val hasClipboard: Boolean = false,
    /** Hay **notas** en el portapapeles de notas del editor (Fase 2, ADR-020). */
    val hasNoteClipboard: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Nº de columnas **de referencia** por compás (derivado del compás y la subdivisión). Es solo una
     * guía de lectura/maquetación: la rejilla real tiene las columnas que el usuario haya escrito.
     */
    val slotsPerMeasure: Int
        get() = TablatureEditing.slotsPerMeasure(editorTime, editorResolution)

    /** Columnas **reales** de cada compás (modelo de columnas). Un compás vacío devuelve 0. */
    val columnCountPerMeasure: List<Int>
        get() = editorMeasures.map { it.slots.size }

    /** Nº de compases del editor. */
    val editorMeasureCount: Int get() = editorMeasures.size

    /**
     * **Indicación de compás vigente** en [measureIndex]: la del último compás que la cambia, o la del
     * documento si ninguno lo hace. Es `Tablature.timeAt` aplicado a la lista **en edición**, que aún
     * no es una `Tablature` completa.
     */
    fun effectiveTimeAt(measureIndex: Int): ScoreTimeSignature =
        editorMeasures.take(measureIndex + 1).lastOrNull { it.time != null }?.time ?: editorTime

    /**
     * **Subdivisión** (la rejilla de trabajo) del compás donde está el cursor: negras, corcheas o
     * semicorcheas. Se **deriva** de las columnas de silencio del compás, así que no hay que
     * mantenerla sincronizada; si no hay cursor, es la resolución del documento.
     */
    val cursorSubdivision: TabResolution
        get() = selectedSlot?.first
            ?.let { editorMeasures.getOrNull(it) }
            ?.let { TablatureEditing.subdivisionOf(it, editorResolution) }
            ?: editorResolution

    /**
     * **Figura activa efectiva** (B-15): la que hay que resaltar en la barra de escritura.
     *
     * Es [selectedFigure] si el usuario eligió una duración; si no, la **figura de la casilla del
     * cursor**, que es lo que MuseScore muestra siempre en su barra de duraciones. Así el botón
     * encendido dice en todo momento **qué** se va a escribir, sin que el usuario tenga que recordar
     * si eligió una figura o está heredando la subdivisión del compás.
     */
    val activeFigure: RhythmFigure?
        get() {
            val slot = selectedSlot?.let { (measureIndex, slotIndex) ->
                editorMeasures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex)
            }
            // Con una **nota** bajo el cursor manda **su** duración, como la barra de MuseScore, que
            // siempre describe el elemento seleccionado. En una casilla **vacía** manda la figura
            // elegida, que es la que recibirá la nota que se escriba.
            val slotFigure = slot?.takeIf { !it.isEmpty || it.isHeld }?.rhythmicFigure
            return slotFigure ?: selectedFigure
        }

    /**
     * **Barra de estado** del cursor, con el vocabulario de MuseScore 4 (B-1):
     * `Nota; Altura: Sol3; Cuerda: 3; Traste: 0; Duración: Negra; Voz: 1; Compás: 2; Tiempo: 1;
     * Pentagrama 1`. Sin cursor devuelve [com.grafitto.laudate.domain.tablature.TabStatusBar.NO_SELECTION].
     */
    val statusBarText: String
        get() {
            val cursor = selectedSlot ?: return com.grafitto.laudate.domain.tablature.TabStatusBar.NO_SELECTION
            return com.grafitto.laudate.domain.tablature.TabStatusBar.text(
                measures = editorMeasures,
                measureIndex = cursor.first,
                slotIndex = cursor.second,
                stringNumber = selectedString,
                tuning = editorTuning,
                resolution = cursorSubdivision,
                voice = activeVoice
            )
        }

    /** Cuerdas en orden de dibujo (aguda arriba), con su número de lectura. */
    val drawnStrings: List<Pair<Int, com.grafitto.laudate.domain.model.tablature.TuningString>>
        get() = editorTuning.drawnStrings()

    /**
     * Progresión de dificultad de la tablatura en edición (orientativa, 1..10). Se calcula al
     * vuelo redigitando las alturas actuales con el motor de digitación, **sin** modificar la
     * tablatura del usuario: es solo un indicador.
     */
    val difficulty: Int
        get() {
            val pitches = editorAsTablature().soundedPitches()
            if (pitches.isEmpty()) return 1
            val result = com.grafitto.laudate.domain.tablature.FingeringEngine.assign(
                pitches,
                com.grafitto.laudate.domain.tablature.FingeringOptions(tuning = editorTuning)
            )
            return result.difficulty
        }

    /** Indica si la tablatura en edición es guardable (tiene al menos una nota). */
    val isEditorValid: Boolean
        get() = TablatureEditing.hasContent(editorMeasures)

    /**
     * Se puede **redigitar al tono del canto**: hay tablatura con notas y una tonalidad de canto
     * conocida distinta a la escrita (o sin tonalidad escrita declarada).
     */
    val canRefingerToSongKey: Boolean
        get() = songKey.isNotBlank() && TablatureEditing.hasContent(editorMeasures)

    /** Indica si hay cambios sin guardar respecto al estado de apertura o de la última guarda. */
    val hasUnsavedChanges: Boolean
        get() = baselineTablature != null && editorAsTablature() != baselineTablature

    /**
     * **Avisos de acorde** (F-08) de la tablatura en edición, indexados por
     * `measureIndex to slotIndex`.
     *
     * El editor **no bloquea** la escritura: deja poner cualquier combinación de cuerdas y trastes,
     * porque la tablatura es un lenguaje y hay música que usa posiciones raras. Lo que hace es
     * **avisar** de lo que no se puede tocar (dos notas en la misma cuerda, traste fuera del mástil,
     * palmo inalcanzable, `X` con traste) para que el lienzo pinte un punto y la barra de estado lo
     * cuente. Es la misma postura que MuseScore, que marca en rojo pero no corrige.
     *
     * Se memoiza con `lazy` porque el estado es inmutable: cada `copy()` recalcula una vez y el
     * lienzo y la barra de estado comparten el resultado.
     */
    val chordIssues: Map<Pair<Int, Int>, List<TabChordIssue>> by lazy {
        TabChordValidator.bySlot(editorAsTablature())
    }

    /** Nº de **columnas** con algún aviso de acorde (no de avisos: una columna puede tener varios). */
    val chordIssueCount: Int get() = chordIssues.size

    /** `true` si alguna columna de la tablatura en edición no se puede tocar. */
    val hasChordIssues: Boolean get() = chordIssues.isNotEmpty()

    /**
     * Resumen de una línea para la barra de estado: el primer aviso y, si hay más columnas, cuántas.
     * `null` cuando la tablatura es tocable, para no ocupar sitio con un mensaje vacío.
     */
    val chordIssueSummary: String?
        get() {
            val first = chordIssues.values.firstOrNull()?.firstOrNull() ?: return null
            val columns = chordIssues.size
            return if (columns == 1) {
                first.message
            } else {
                "$columns acordes con avisos · ${first.message}"
            }
        }

    /** Representación de la tablatura en edición, para previsualizar con el mismo visor. */
    fun editorAsTablature(): Tablature = Tablature(
        id = editingId ?: "editor",
        songId = songId,
        title = editorTitle.ifBlank { songTitle },
        instrument = editorTuning.name,
        tuning = editorTuning,
        time = editorTime,
        resolution = editorResolution,
        measures = editorMeasures,
        origin = TabOrigin.CREATED,
        writtenKey = editorWrittenKey,
        sectionId = editorSectionId,
        // Se persiste el modo elegido para que la transposición del canto lo respete después.
        transpositionMode = transpositionMode,
        label = editorLabel.trim().ifEmpty { null },
        moment = editorMoment.trim().ifEmpty { null },
        note = editorNote.trim().ifEmpty { null }
    )
}

/** Referencia ligera a una sección del canto, para el desplegable de asociación de la tablatura. */
data class TablatureSectionRef(val id: String, val label: String)

sealed interface TablatureUiIntent {
    /** Carga las tablaturas de un canto. */
    data class LoadForSong(val songId: String, val songTitle: String = "", val songKey: String = "") : TablatureUiIntent

    /**
     * **Suelta el canto abierto** porque la pantalla ya no tiene ninguno (por ejemplo, un canto
     * nuevo todavía sin guardar). Sin esto el ViewModel conservaba el `songId` del canto anterior y
     * una tablatura escrita en el canto nuevo acababa guardándose en el viejo.
     */
    data object ClearSong : TablatureUiIntent

    /** Abre el editor para crear una tablatura nueva. */
    data object OpenNewEditor : TablatureUiIntent
    /**
     * **Importa una partitura** (MusicXML, MuseScore `.mscz`, Guitar Pro `.gp`/`.gpx` o TuxGuitar
     * `.tg`) como tablatura del canto, **sustituyendo** la que hubiera. La plataforma abre el
     * selector de archivo y devuelve la ruta en [TablatureUiIntent.ImportTablatureFromFile].
     */
    data object RequestImportTablature : TablatureUiIntent
    /**
     * Importación real de un fichero ya elegido: [fileUri] es su ruta y [fileName] su nombre (de la
     * extensión sale el formato). El dominio lo parsea y reemplaza la tablatura del canto.
     */
    data class ImportTablatureFromFile(val fileUri: String, val fileName: String) : TablatureUiIntent
    /**
     * **Comparte una tablatura** como **MusicXML** (`.musicxml`): el formato que abren MuseScore,
     * Guitar Pro, TuxGuitar y Finale. La plataforma abre la hoja de compartir.
     */
    data class ShareTablature(val tablatureId: String) : TablatureUiIntent
    /** Abre el editor para editar una tablatura existente. */
    data class OpenEditor(val tablatureId: String) : TablatureUiIntent
    /** Duplica una tablatura existente (abre el editor como copia nueva). */
    data class DuplicateTablature(val tablatureId: String) : TablatureUiIntent
    /** Cambia el título de la tablatura en edición. */
    data class UpdateEditorTitle(val title: String) : TablatureUiIntent
    /** Asocia la tablatura en edición a una sección del canto (`null` = general). */
    data class SetEditorSection(val sectionId: String?) : TablatureUiIntent
    /** Mueve una tablatura **guardada** a otra sección del canto (`null` = bloque general). */
    data class MoveTablatureToSection(val tablatureId: String, val sectionId: String?) : TablatureUiIntent
    /** Sube o baja una tablatura **guardada** dentro de su grupo (negativo = subir). */
    data class MoveTablatureOrder(val tablatureId: String, val delta: Int) : TablatureUiIntent
    /** Elige qué tablatura se ve (vista previa y panel de herramientas). `null` = la primera. */
    data class SelectTablature(val tablatureId: String?) : TablatureUiIntent
    /** Fija la **tonalidad escrita** de la tablatura (cejilla/redigitado), RF-127. */
    data class SetEditorWrittenKey(val key: ScoreKey?) : TablatureUiIntent
    /** Rótulo libre de la tablatura, RF-127. */
    data class UpdateEditorLabel(val label: String) : TablatureUiIntent
    /** Momento litúrgico sugerido, RF-127. */
    data class UpdateEditorMoment(val moment: String) : TablatureUiIntent
    /** Nota libre del copista, RF-127. */
    data class UpdateEditorNote(val note: String) : TablatureUiIntent
    /** Fija la escala del dibujo de las tablaturas (preferencia global). */
    data class SetTablatureScale(val scale: Float) : TablatureUiIntent

    /** Cambia el **tamaño de las cifras** sin tocar el de la rejilla (editor de tablatura). */
    data class SetTablatureNumberScale(val scale: Float) : TablatureUiIntent
    /** Cambia el **aspecto del pentagrama** (cifras, líneas, valores de nota, clave, compás). */
    data class SetStaffOptions(val options: TabStaffOptions) : TablatureUiIntent
    /** Cambia la afinación (instrumento) de la tablatura en edición. */
    data class SetTuning(val tuning: Tuning) : TablatureUiIntent

    // ── Datos de cuerda (D-49, el «String data» de MuseScore) ────────────────────────────────────

    /** Abre el diálogo **Datos de cuerda** (cuerdas, alturas, «siempre al aire» y nº de trastes). */
    data object OpenStringDataDialog : TablatureUiIntent

    /** Cierra el diálogo **Datos de cuerda** sin tocar nada más. */
    data object CloseStringDataDialog : TablatureUiIntent

    /** Cambia el **número de trastes** del instrumento (el `m_frets` de MuseScore). */
    data class SetTuningFrets(val frets: Int) : TablatureUiIntent

    /** Cambia la **altura al aire** de una cuerda, con su grafía (sostenidos o bemoles). */
    data class SetStringPitch(
        val stringNumber: Int,
        val pitch: ScorePitch,
        val useFlat: Boolean = false
    ) : TablatureUiIntent

    /** Marca o desmarca una cuerda como **siempre al aire** (no pasa por el mástil). */
    data class SetStringAlwaysOpen(val stringNumber: Int, val alwaysOpen: Boolean) : TablatureUiIntent

    /** Añade una cuerda **debajo** de la indicada (`0` = al final de la tabla, la más grave). */
    data class AddTuningString(val afterStringNumber: Int, val pitch: ScorePitch) : TablatureUiIntent

    /** Quita una cuerda de la afinación. */
    data class RemoveTuningString(val stringNumber: Int) : TablatureUiIntent
    /** Cambia el compás; redimensiona la rejilla. */
    data class SetTimeSignature(val time: ScoreTimeSignature) : TablatureUiIntent
    /** Cambia la resolución de la rejilla; redimensiona. */
    data class SetResolution(val resolution: TabResolution) : TablatureUiIntent

    /** Selecciona una casilla (compás, casilla) para editarla. */
    data class SelectSlot(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent

    // ── Modo de entrada de notas (paridad con MuseScore 4, ADR-020) ────────────────

    /**
     * **`N`**: entra o sale del **modo de entrada de notas**. Con el modo activo el cursor se pinta
     * como el rectángulo **azul** de MuseScore (del color de la voz) y tocar una cuerda **escribe un
     * `0`**; con el modo apagado, tocar **solo selecciona**.
     */
    data object ToggleInputMode : TablatureUiIntent

    /** **`Esc`**: sale del modo de entrada y deselecciona, sin tocar la música. */
    data object LeaveInputMode : TablatureUiIntent

    /**
     * **`Espacio`**: reproduce o pausa la partitura, como MuseScore. En esta fase es un **aviso**: la
     * reproducción del documento entero llega en la Fase 5 (hoy solo suena la nota suelta al escribir).
     */
    data object PlaybackToggle : TablatureUiIntent

    /**
     * **Enciende o apaga el metrónomo** (D-23). No interrumpe la reproducción: el cambio se aplica en
     * la siguiente pasada, porque los clics se calculan al construir la línea de tiempo.
     */
    data object ToggleMetronome : TablatureUiIntent
    /** **Volumen del metrónomo** (D-66), `0..1`, independiente del de la tablatura. */
    data class SetMetronomeVolume(val volume: Float) : TablatureUiIntent
    /** Enciende o apaga el **acento** del primer tiempo del compás (D-66). */
    data object ToggleMetronomeAccent : TablatureUiIntent
    /** **Subdivisión** de la pulsación del metrónomo (D-66): `1` pulsación, `2` corcheas, `3` tresillos, `4` semicorcheas. */
    data class SetMetronomeSubdivision(val subdivision: Int) : TablatureUiIntent
    /** Fija el **volumen** del mezclador (`0..1`), acotado por el ViewModel. */
    data class SetMixerVolume(val volume: Float) : TablatureUiIntent
    /** Fija el **balance** del mezclador (`-1` izquierda … `1` derecha). */
    data class SetMixerPan(val pan: Float) : TablatureUiIntent
    /** Conmuta el **silencio** (`M`) del mezclador. */
    data object ToggleMixerMute : TablatureUiIntent
    /** Conmuta el **solo** (`S`) del mezclador. */
    data object ToggleMixerSolo : TablatureUiIntent

    /**
     * **Activa o desactiva el bucle de rango** (D-23) sobre la **selección de compases** actual. Sin
     * selección no hay bucle: se emite un aviso en vez de inventar un rango. Si ya había bucle, lo
     * quita (es un interruptor, como el botón de MuseScore).
     */
    data object ToggleLoopRange : TablatureUiIntent

    /**
     * Fija la **voz activa** (1–4): cambia el color del cursor (Fase 5: polifonía real).
     *
     * Una voz **fuera de rango cae en la voz 1** (la de escritura por defecto), no en la última:
     * es el mismo criterio que `TabVoiceColors.of()`, y así el cursor nunca se queda sin color.
     */
    data class SetActiveVoice(val voice: Int) : TablatureUiIntent

    /**
     * Asigna la **voz** [voice] a la casilla [measureIndex]/[slotIndex] (menú contextual de la
     * casilla, como «Voice 1..4» de MuseScore). Fuera de rango se acota a 1..4.
     */
    data class SetSlotVoice(val measureIndex: Int, val slotIndex: Int, val voice: Int) : TablatureUiIntent

    /**
     * Asigna la **voz** [voice] a **todas** las casillas de los compases seleccionados: es el
     * `Ctrl+Alt+1..4` de MuseScore aplicado a una selección de compases.
     */
    data class SetVoiceInRange(val voice: Int) : TablatureUiIntent

    /**
     * **Teclado físico**: escribe un dígito de traste en la casilla seleccionada acumulándolo en el
     * búfer (teclear «1» y «2» da el traste 12). Si la combinación no es un traste válido, el
     * dígito empieza un búfer nuevo. Tras la pausa de la cifra el cursor **avanza** una columna.
     *
     * Con el **modo de entrada apagado** (desviación firmada del ADR-020) el primer dígito **entra**
     * en el modo y escribe.
     */
    data class TypeFretDigit(val digit: Int) : TablatureUiIntent
    /**
     * **Teclado en pantalla**: escribe el traste [fret] en la casilla y cuerda activas y **avanza**
     * una columna. Es el gesto del teclado numérico: sin búfer de dos cifras (cada toque es una cifra
     * completa) y sin cambiar de cuerda.
     */
    data class WriteFretAndAdvance(val fret: Int) : TablatureUiIntent
    /** **Teclado físico**: mueve el cursor (←/→ casillas, ↑/↓ cuerdas) sin escribir. */
    data class MoveCursor(val measureDelta: Int, val slotDelta: Int, val stringDelta: Int) : TablatureUiIntent

    /**
     * **Lleva el cursor** a la primera casilla del compás [measureIndex] **sin** tocar la selección de
     * compases. Es el `Ctrl+Inicio` / `Ctrl+Fin` de MuseScore («ir al principio / al final de la
     * partitura»), que mueven el cursor, no la selección.
     */
    data class GoToMeasure(val measureIndex: Int) : TablatureUiIntent
    /**
     * **Teclado físico**: retrocede una casilla (Retroceso). Si la casilla tenía una nota en la
     * cuerda activa la borra; si estaba vacía, mueve el cursor hacia atrás.
     */
    data object BackspaceCursor : TablatureUiIntent
    /**
     * **Teclado físico**: borra la nota de la cuerda activa en la casilla (Suprimir). No mueve el
     * cursor (a diferencia de Retroceso), como en un editor de texto.
     */
    data object DeleteAtCursor : TablatureUiIntent
    /** Vacía el **búfer** de dígitos del traste (p. ej. al hacer clic en otra casilla o al guardar). */
    data object ClearFretBuffer : TablatureUiIntent

    // ── Edición de la nota por teclado (paridad con MuseScore 4, ADR-020) ───────────

    /**
     * **`↑`/`↓` en modo normal**: sube o baja el **traste** de la nota del cursor **sin cambiar de
     * cuerda**. Es lo que hace MuseScore («To increment or decrement, without changing the string»),
     * frente a las flechas en modo entrada, que mueven el cursor entre cuerdas.
     */
    data class NudgeFret(val delta: Int) : TablatureUiIntent
    /**
     * **`Alt`+`Shift`+`↑`/`↓`**: sube o baja el traste **cambiando de cuerda** si hace falta, para
     * dejar el traste **más bajo posible** (como MuseScore).
     */
    data class NudgeFretAcrossStrings(val delta: Int) : TablatureUiIntent
    /** **`Q`/`W`**: acorta o alarga la figura de la nota del cursor (`null` = no cambia). */
    data class SetFigureForCursor(val figure: RhythmFigure?) : TablatureUiIntent
    /**
     * **`Q`/`W`** con la figura resultante ya calculada por el Dominio (`TabShortcuts`): acorta
     * (`delta = -1`) o alarga (`delta = +1`) la duración de la nota del cursor.
     */
    data class AdjustFigure(val delta: Int) : TablatureUiIntent
    /** **`.`**: alterna el **puntillo** de la figura de la nota del cursor. */
    data object ToggleDotAtCursor : TablatureUiIntent
    /** **`;`**: inserta un **silencio** de la figura activa en el cursor (como MuseScore). */
    data object InsertRestAtCursorShortcut : TablatureUiIntent
    /**
     * **`Shift`+`X`**: alterna la **nota fantasma** (*crosshead/ghost*) de la nota del cursor. Se
     * corresponde con `TabTechnique.GHOST`.
     */
    data object ToggleGhostNoteAtCursor : TablatureUiIntent
    /** **`Shift`+`V`/`O`/`S`/`N`**: aplica la **articulación** [articulation] a la nota del cursor. */
    data class ApplyArticulationAtCursor(
        val articulation: com.grafitto.laudate.domain.model.tablature.TabArticulation
    ) : TablatureUiIntent

    /**
     * **Alterna** la **técnica** [technique] de la cuerda activa en la casilla del cursor. Las
     * técnicas se **combinan** (ligado + slide…), así que el mismo botón la pone y la quita; `NONE`
     * las limpia todas.
     *
     * Es el gesto de la **paleta de técnicas** (un botón por símbolo). No tiene atajo de teclado:
     * MuseScore **no** asigna teclas a las técnicas (ADR-020).
     */
    data class ToggleTechnique(
        val technique: com.grafitto.laudate.domain.model.tablature.TabTechnique
    ) : TablatureUiIntent

    /**
     * Toca una casilla de la rejilla. La fila tocada pasa a ser la **cuerda activa** y el cursor se
     * coloca en esa casilla; **nunca** escribe. Como en MuseScore y Guitar Pro, pulsar la partitura
     * solo **señala dónde** se va a escribir: el traste se escribe después (teclado numérico o
     * teclado físico). Antes, tocar una casilla vacía escribía el traste activo y un roce podía
     * estropear lo escrito. [stringNumber] `null` = la cuerda ya activa.
     */
    data class TapSlot(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int? = null
    ) : TablatureUiIntent
    /** Aplica el traste seleccionado a una cuerda concreta de la casilla seleccionada. */
    data class SetSlotFret(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int, val fret: Int) : TablatureUiIntent
    /** Quita una cuerda de una casilla. */
    data class RemovePluck(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int) : TablatureUiIntent
    /** Vacía una casilla. */
    data class ClearSlot(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent
    /** Alterna la prolongación de una casilla. */
    data class ToggleHeld(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent
    /** Alterna el **trémolo** (`*`) de una casilla (RF-127). */
    data class ToggleTremolo(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent
    /**
     * Fija la **técnica** de una cuerda pulsada (trémolo, ligado, bend, armónico, palm mute, vibrato,
     * púa, *let ring*…). Un botón por símbolo en la paleta de la casilla (§13.5.bis). Si la cuerda no
     * tenía nota, aplicar una técnica **no** la crea: la técnica es una marca de una nota existente.
     */
    data class SetTechnique(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val technique: com.grafitto.laudate.domain.model.tablature.TabTechnique
    ) : TablatureUiIntent
    /**
     * Fija el **número de trazos** del trémolo de una nota (1–4), como la paleta «Trémolos» de
     * MuseScore (`tremolo1`…`tremolo4`). **No** activa el trémolo: solo cambia cuántos trazos dibuja.
     */
    data class SetTremoloStrokes(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val strokes: Int
    ) : TablatureUiIntent
    /**
     * Fija la **cantidad del bend** (`1/2`, `1`…) de una cuerda pulsada; `null` la deja sin indicar.
     * Es la **magnitud** de la técnica de estiramiento, no una técnica en sí.
     */
    data class SetBendAmount(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val amount: com.grafitto.laudate.domain.model.tablature.TabBendAmount?
    ) : TablatureUiIntent
    /**
     * Fija el **alcance del `P.M.`** (apagado de palma) de una cuerda pulsada: cuántas columnas
     * abarca desde esta nota. `null` = solo su columna.
     */
    data class SetPalmMuteSpan(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val span: Int?
    ) : TablatureUiIntent
    /**
     * Fija el **dedo de la mano izquierda** (1–4) que pisa la nota; `null` lo deja sin indicar.
     * Es una anotación de **digitación**, no una técnica de interpretación.
     */
    data class SetFrettingFinger(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val finger: com.grafitto.laudate.domain.model.tablature.TabFrettingFinger?
    ) : TablatureUiIntent
    /** Fija el **dedo/púa de la mano derecha** (`p`/`i`/`m`/`a`) que pulsa la nota; `null` lo quita. */
    data class SetPickingFinger(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val finger: com.grafitto.laudate.domain.model.tablature.TabPickingFinger?
    ) : TablatureUiIntent
    /** Fija la figura rítmica de la posición activa; `null` hereda la subdivisión del compás. */
    data class SetRhythmicFigureAtCursor(val figure: RhythmFigure?) : TablatureUiIntent
    /** Alterna el marcador de tresillo de la posición activa. */
    data object ToggleTripletAtCursor : TablatureUiIntent
    /**
     * Marca (o deshace) un **grupo irregular** de proporción [numNotes]:[normalNotes] en la posición
     * activa, como el *Crear grupo irregular* de MuseScore.
     *
     * Si la casilla ya pertenece a un grupo de esa misma proporción, se deshace; si pertenece a otro,
     * se sustituye. Se marcan `numNotes` columnas contiguas (creando las que falten).
     */
    data class SetTupletGroupAtCursor(
        val numNotes: Int,
        val normalNotes: Int
    ) : TablatureUiIntent
    /**
     * Cambia **qué se rotula** sobre el corchete del grupo irregular del cursor
     * (`TupletNumberType` de MuseScore): `3`, `3:2` o nada.
     */
    data class SetTupletNumberTypeAtCursor(
        val numberType: com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
    ) : TablatureUiIntent
    /**
     * Cambia **si se dibuja el corchete** del grupo irregular del cursor
     * (`TupletBracketType` de MuseScore): automático, siempre o nunca.
     */
    data class SetTupletBracketTypeAtCursor(
        val bracketType: com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
    ) : TablatureUiIntent
    /** Avanza la marca de **repetición** de un compás (`|:` `:|`), RF-127. */
    data class CycleRepeat(val measureIndex: Int) : TablatureUiIntent
    /**
     * Fija la marca de **repetición** de un compás a un valor concreto (`null` la quita). Un botón
     * por símbolo de la paleta, en vez de ir rotando por todos.
     */
    data class SetRepeatMark(
        val measureIndex: Int,
        val mark: com.grafitto.laudate.domain.model.tablature.TabRepeatMark?
    ) : TablatureUiIntent
    /** Cambia el número de ejecuciones de la repetición seleccionada (2..8). */
    data class SetRepeatCount(val measureIndex: Int, val count: Int) : TablatureUiIntent
    /** Fija el **tipo de barra de compás** (simple/doble/final) del compás, un botón por tipo. */
    data class SetBarline(
        val measureIndex: Int,
        val barline: com.grafitto.laudate.domain.model.tablature.TabBarline
    ) : TablatureUiIntent
    /** Avanza la **volta** (casilla de 1ª/2ª vez) de un compás, RF-127. */
    data class CycleEnding(val measureIndex: Int) : TablatureUiIntent
    /** Fija la **volta** de un compás a un valor concreto (`null` la quita), un botón por casilla. */
    data class SetEnding(val measureIndex: Int, val ending: Int?) : TablatureUiIntent
    /**
     * Fija la **casilla de varias pasadas** de un compás (D-37): la volta suena en las pasadas
     * `from..to` (`1, 2.` en MuseScore). Con `to <= from` queda una casilla simple; con `from = null`
     * se quita la volta.
     */
    data class SetEndingRange(val measureIndex: Int, val from: Int?, val to: Int?) : TablatureUiIntent
    /**
     * Fija el **rótulo propio** de la volta de un compás (D-43); `null` o en blanco lo devuelve al
     * derivado de las pasadas (`1.`, `1, 2.`).
     *
     * Es el `beginText` del `Volta` de MuseScore: el texto del corchete es **libre** y no toca las
     * pasadas, que siguen mandando en la reproducción. Solo tiene efecto si el compás lleva casilla.
     */
    data class SetEndingText(val measureIndex: Int, val text: String?) : TablatureUiIntent
    /**
     * Fija el **signo de navegación** (Segno / Coda / Fine / To Coda) de un compás; `null` lo quita.
     * Un botón por signo en la paleta (grupo «Navegación»), T1.
     */
    data class SetNavigation(
        val measureIndex: Int,
        val mark: com.grafitto.laudate.domain.model.tablature.TabNavigationMark?
    ) : TablatureUiIntent
    /** Fija el **salto** (D.C. / D.S. y variantes) de un compás; `null` lo quita (grupo «Saltos»), T1. */
    data class SetJump(
        val measureIndex: Int,
        val jump: com.grafitto.laudate.domain.model.tablature.TabJump?
    ) : TablatureUiIntent
    /**
     * Fija la **dinámica** (matiz/regulador) de un compás; `null` la quita. Un botón por matiz en la
     * paleta (grupo «Dinámicas»), T2.
     */
    data class SetDynamic(
        val measureIndex: Int,
        val dynamic: com.grafitto.laudate.domain.model.tablature.TabDynamic?
    ) : TablatureUiIntent
    /**
     * Fija el **regulador** (*hairpin*) que empieza en un compás; `null` lo quita. Es la **cuña** de
     * crescendo/diminuendo, un elemento distinto de la dinámica escrita ([SetDynamic]).
     */
    data class SetHairpin(
        val measureIndex: Int,
        val hairpin: com.grafitto.laudate.domain.model.tablature.TabHairpin?
    ) : TablatureUiIntent
    /**
     * **Añade una nota de adorno** (*grace note*) a la casilla del cursor, como la tecla `/` de
     * MuseScore. El adorno se clona de la nota más aguda de la casilla (misma cuerda y traste) y se
     * dibuja como una cifra **pequeña** a su izquierda.
     */
    data class AddGrace(
        val measureIndex: Int,
        val slotIndex: Int,
        val type: com.grafitto.laudate.domain.model.tablature.TabGraceType =
            com.grafitto.laudate.domain.model.tablature.TabGraceType.ACCIACCATURA,
        /**
         * Cuerda donde nace el adorno (D-63). `null` = la más aguda de la casilla, que es el
         * comportamiento histórico. La UI pasa la **cuerda activa** para que el adorno aparezca donde
         * el usuario está escribiendo.
         */
        val stringNumber: Int? = null
    ) : TablatureUiIntent
    /** **Quita el último** adorno de una casilla. */
    data class RemoveGrace(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent
    /** **Quita todos** los adornos de una casilla. */
    data class ClearGraces(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent
    /**
     * **Cambia el traste de un adorno** (D-63). Sin esto, un adorno añadido sobre una casilla vacía
     * se quedaba en `0` para siempre: el control de traste del panel edita la nota principal.
     */
    data class SetGraceFret(
        val measureIndex: Int,
        val slotIndex: Int,
        val graceIndex: Int,
        val fret: Int
    ) : TablatureUiIntent
    /** **Cambia la cuerda de un adorno** (D-63): lo mueve de línea en la rejilla. */
    data class SetGraceString(
        val measureIndex: Int,
        val slotIndex: Int,
        val graceIndex: Int,
        val stringNumber: Int
    ) : TablatureUiIntent
    /**
     * **Alterna** una **articulación** (acento, picado, calderón…) de la cuerda activa en la casilla
     * actual (grupo «Articulaciones»), T3. Se combinan como las técnicas.
     */
    data class ToggleArticulation(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val articulation: com.grafitto.laudate.domain.model.tablature.TabArticulation
    ) : TablatureUiIntent
    /** Fija la **alteración** de la cuerda activa en la casilla actual (grupo «Alteraciones»), T4. */
    data class SetAccidental(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int,
        val accidental: com.grafitto.laudate.domain.model.tablature.TabAccidental?
    ) : TablatureUiIntent
    /**
     * **Alterna la ligadura de prolongación** (*tie*, B-10) de una cuerda pulsada: une esta nota con
     * la **siguiente de la misma cuerda** para que suenen como una sola. Es el gesto de `T` en
     * MuseScore. Solo se acepta si la nota siguiente tiene el **mismo traste**; si no, no hace nada
     * (una ligadura entre alturas distintas es un *slur*, no un *tie*).
     */
    data class ToggleTie(
        val measureIndex: Int,
        val slotIndex: Int,
        val stringNumber: Int
    ) : TablatureUiIntent
    /**
     * **Alterna la ligadura de expresión** (*slur*) de una casilla: une esta posición con la
     * **siguiente con sonido** para indicar fraseo. Es el gesto de `S` en MuseScore 4.
     *
     * No exige que las alturas coincidan (eso es la ligadura de prolongación, [ToggleTie]); solo que
     * exista una casilla siguiente con sonido.
     */
    data class ToggleSlur(
        val measureIndex: Int,
        val slotIndex: Int
    ) : TablatureUiIntent
    /** Fija el **cambio de octava** de un compás (8va / 15ma / 8vb); `null` lo quita (T4). */
    data class SetOctaveShift(
        val measureIndex: Int,
        val shift: com.grafitto.laudate.domain.model.tablature.TabOctaveShift?
    ) : TablatureUiIntent
    /** Fija el **tempo** (BPM) de un compás; `null` quita la indicación (grupo «Tempo»), T5. */
    data class SetTempo(val measureIndex: Int, val bpm: Int?) : TablatureUiIntent
    /** Fija el **texto libre** anclado a un compás (indicación del copista); `null` lo quita (T6). */
    data class SetMeasureText(val measureIndex: Int, val text: String?) : TablatureUiIntent
    /** Añade un compás **al final** del documento. */
    data object AddMeasure : TablatureUiIntent
    /**
     * Añade [count] compases **al final** del documento (`Alt+Mayús+B` de MuseScore, «añadir
     * compases»). MuseScore abre un diálogo para pedir la cantidad; aquí la cantidad la decide el
     * llamante, que es lo que permite usarlo también desde el cromo sin un modal.
     */
    data class AddMeasures(val count: Int) : TablatureUiIntent
    /** Quita el último compás. */
    data object RemoveMeasure : TablatureUiIntent
    /**
     * **Inserta un compás vacío (con su silencio de compás) delante** del compás [measureIndex],
     * desplazándolo. Es el «insertar compás» de MuseScore/Guitar Pro: un compás se añade **donde
     * hace falta**, no solo al final.
     */
    data class InsertMeasureBefore(val measureIndex: Int) : TablatureUiIntent
    /** **Inserta un compás vacío detrás** del compás [measureIndex]. */
    data class InsertMeasureAfter(val measureIndex: Int) : TablatureUiIntent
    /**
     * **Inserta [count] compases vacíos delante** del compás [measureIndex]. Es el `Ctrl`+`Ins` de
     * MuseScore (`insert-measures`, `shortcuts.json:197`): a diferencia de `Ins` (`insert-measure`,
     * que inserta **uno**), este inserta **tantos como compases haya en la selección**. Antes
     * `Ctrl`+`Ins` insertaba **detrás** del cursor, que no es lo que hace MuseScore.
     */
    data class InsertMeasuresBefore(val measureIndex: Int, val count: Int) : TablatureUiIntent
    /**
     * **Intercambia** la selección con el portapapeles (`Ctrl+Mayús+X` de MuseScore, `edit-swap`): el
     * portapapeles ocupa el sitio de la selección y lo que había en ella pasa al portapapeles. Sirve
     * para **mover** un pasaje sin cortar y pegar.
     */
    data object SwapSelection : TablatureUiIntent
    /** **Duplica** el compás [measureIndex] justo detrás (con sus notas y marcas). */
    data class DuplicateMeasure(val measureIndex: Int) : TablatureUiIntent
    /** **Elimina** el compás [measureIndex]. Nunca deja el documento sin compases. */
    data class DeleteMeasure(val measureIndex: Int) : TablatureUiIntent

    /**
     * **Elimina** los compases del rango [range] (`Ctrl+Shift+Supr` de MuseScore sobre una selección).
     * Si el rango abarca todo el documento se conserva **un** compás vacío: una tablatura sin compases
     * no existe.
     */
    data class DeleteMeasures(val range: IntRange) : TablatureUiIntent

    /**
     * **Vacía** los compases del rango [range] (`Ctrl+Shift+Del` de MuseScore): quita las notas y
     * deja el compás completo con su silencio, **conservando** las marcas (repetición, casilla,
     * tempo, texto, salto de sistema). No es lo mismo que eliminar: el compás sigue ahí.
     */
    data class ClearMeasures(val range: IntRange) : TablatureUiIntent

    /**
     * **Mueve** el bloque de compases [range] a la posición [to], que es el índice que ocupa en la
     * lista **ya sin el bloque** (así el destino no depende de la dirección del movimiento).
     */
    data class MoveMeasures(val range: IntRange, val to: Int) : TablatureUiIntent

    /**
     * **Salto de sistema** del compás [measureIndex] (`Return` de MuseScore): el compás empieza una
     * fila nueva aunque quepa en la anterior. Alterna.
     */
    data class ToggleSystemBreak(val measureIndex: Int) : TablatureUiIntent

    /** Fija el **salto de sistema** del compás [measureIndex] a un valor concreto. */
    data class SetSystemBreak(val measureIndex: Int, val force: Boolean) : TablatureUiIntent

    /**
     * **Salto de página** del compás [measureIndex] (`Ctrl`+`Return` de MuseScore): en la exportación
     * a PDF el compás empieza hoja nueva. Implica salto de sistema. Alterna.
     */
    data class TogglePageBreak(val measureIndex: Int) : TablatureUiIntent

    /** Fija el **salto de página** del compás [measureIndex] a un valor concreto. */
    data class SetPageBreak(val measureIndex: Int, val force: Boolean) : TablatureUiIntent

    /**
     * **Cambia la subdivisión** (la rejilla de trabajo) del compás [measureIndex] a [resolution]:
     * negras, corcheas o semicorcheas.
     *
     * Es el control que faltaba (punto 5 del usuario: «no tengo como cambiar si el compás va a negras
     * o a corcheas»). Re-reparte las **columnas de silencio** del compás a la nueva figura y deja
     * **intactas** las columnas con notas, así que se puede cambiar la rejilla de un compás a medio
     * escribir sin perder nada.
     */
    data class SetMeasureSubdivision(
        val measureIndex: Int,
        val resolution: TabResolution
    ) : TablatureUiIntent

    /**
     * **Anacrusa** (*pickup*): marca o desmarca el **primer** compás como el compás incompleto con el
     * que arranca la pieza, como el `Measure::isAnacrusis()` de MuseScore.
     *
     * Al ponerla se recortan los silencios del final (el relleno que la anacrusa no debe llevar) y al
     * quitarla se rellenan, así que el gesto es **reversible** y no hay que ajustar la duración a mano.
     * La anacrusa **no** se numera —el siguiente compás pasa a ser el `1`— y «Completar compases» la
     * deja en paz.
     */
    data object TogglePickup : TablatureUiIntent

    /**
     * **Cambio de compás a mitad de pieza**: fija la indicación vigente **a partir de** [measureIndex],
     * o la quita con `null` para que el compás vuelva a la del documento.
     *
     * Es el `TimeSig` que MuseScore inserta anclado a un compás desde la paleta *Indicaciones de
     * compás*: la indicación rige desde ese compás hasta el siguiente cambio, y el compás se
     * **reequilibra** a su nueva capacidad (un `3/4` dentro de una pieza `4/4` dura 3 negras).
     */
    data class SetMeasureTime(
        val measureIndex: Int,
        val time: ScoreTimeSignature?
    ) : TablatureUiIntent

    /**
     * **Compás irregular**: marca o desmarca el compás [measureIndex] como de **duración libre**, como
     * el `Measure::isIrregular()` de MuseScore.
     *
     * Al marcarlo, la duración que tiene **pasa a ser** la querida: «Completar compases» lo deja en
     * paz y la barra de estado no lo marca en rojo. Al desmarcarlo se reequilibra a la nominal.
     */
    data class ToggleIrregular(val measureIndex: Int) : TablatureUiIntent

    // ── Selección de rango, portapapeles y deshacer/rehacer (E1-E3) ────────────────

    /** Inicia/actualiza la **selección de compases** desde [anchor] hasta [to] (E1). */
    data class SelectMeasureRange(val anchor: Int, val to: Int) : TablatureUiIntent
    /** Limpia la selección de compases. */
    data object ClearSelection : TablatureUiIntent

    /**
     * **Selecciona un compás** entero (clic en su número o `Ctrl+Shift+←/→`). Sustituye la selección
     * anterior y deja el cursor en su primera casilla.
     */
    data class SelectMeasureAt(val measureIndex: Int) : TablatureUiIntent

    /**
     * **Extiende la selección de compases** [delta] compases a partir del ancla actual
     * (`Shift+clic` y `Shift+←/→` de MuseScore). Sin selección previa, selecciona el compás del
     * cursor.
     */
    data class ExtendMeasureSelection(val delta: Int) : TablatureUiIntent

    // ── Zoom del lienzo (B-6) ───────────────────────────────────────────────────────

    /** Acerca el dibujo un paso (`Ctrl` + `+`). */
    data object ZoomIn : TablatureUiIntent
    /** Aleja el dibujo un paso (`Ctrl` + `-`). */
    data object ZoomOut : TablatureUiIntent
    /** Vuelve al **100 %** (`Ctrl` + `0`). */
    data object ZoomReset : TablatureUiIntent

    // ── Paneles laterales (B-11, B-12, B-14) ────────────────────────────────────────

    /** Muestra u oculta el panel de **paletas** (`F9` de MuseScore). */
    data object TogglePalettes : TablatureUiIntent
    /** Muestra u oculta el panel de **propiedades** (`F8` de MuseScore). */
    data object ToggleProperties : TablatureUiIntent

    // ── Selección de notas (Fase 2, ADR-020) ────────────────────────────────────────

    /**
     * **Selecciona una nota** (clic simple sobre la cifra, o el cursor en modo normal). Sustituye la
     * selección anterior.
     */
    data class SelectNote(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int) : TablatureUiIntent {
        /** Referencia a la nota de este intent, para el Dominio. */
        val ref: TabNoteRef get() = TabNoteRef(measureIndex, slotIndex, stringNumber)
    }
    /** Añade o quita una nota de la selección (el **`Ctrl`+clic** de MuseScore). */
    data class ToggleNoteSelection(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int) : TablatureUiIntent {
        /** Referencia a la nota de este intent, para el Dominio. */
        val ref: TabNoteRef get() = TabNoteRef(measureIndex, slotIndex, stringNumber)
    }
    /**
     * **`Mayús`+clic/flecha**: extiende la selección en un **rango de columnas** de la misma cuerda
     * desde la nota ya seleccionada hasta [measureIndex]/[slotIndex].
     */
    data class ExtendNoteSelection(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int) : TablatureUiIntent {
        /** Referencia a la nota de este intent, para el Dominio. */
        val ref: TabNoteRef get() = TabNoteRef(measureIndex, slotIndex, stringNumber)
    }
    /** Selecciona **todas las notas** del compás [measureIndex]. */
    data class SelectNotesInMeasure(val measureIndex: Int) : TablatureUiIntent

    /**
     * **`Mayús`+`←`/`→`**: extiende la selección de notas [delta] columnas desde el borde actual (en
     * el mismo compás o cruzando al contiguo), como en MuseScore.
     */
    data class ExtendNoteSelectionRelative(val delta: Int) : TablatureUiIntent
    /**
     * **`Mayús`+flecha en las dos dimensiones**: mueve el **foco** de la selección [deltaSlot]
     * columnas y [deltaString] cuerdas desde donde esté, y recalcula el rectángulo entre el ancla y
     * el foco.
     *
     * Es el *rubber band* de MuseScore con el teclado: `Mayús`+`←`/`→` recorre el tiempo y
     * `Mayús`+`↑`/`↓` añade o quita cuerdas, de modo que se puede seleccionar un acorde entero o un
     * pasaje de varias cuerdas sin tocar el ratón. La selección **decrece** al volver sobre los
     * pasos, porque el rectángulo se recalcula y no se acumula.
     */
    data class ExtendNoteSelectionRect(val deltaSlot: Int, val deltaString: Int) : TablatureUiIntent
    /** Selecciona **todas las notas** del documento (`Ctrl`+`A`). */
    data object SelectAllNotes : TablatureUiIntent
    /**
     * **Selección por rectángulo** (*rubber band*): sustituye la selección por [notes]. La UI resuelve
     * el rectángulo contra la geometría del lienzo ([TabNoteSelection.rect]) porque el ViewModel no
     * conoce el layout; aquí solo se aplica.
     */
    data class SetNoteSelection(val notes: Set<TabNoteRef>) : TablatureUiIntent
    /** Limpia la selección de notas. */
    data object ClearNoteSelection : TablatureUiIntent
    /** **Borra** las notas seleccionadas, dejando las columnas (el `Supr` con notas seleccionadas). */
    data object DeleteSelectedNotes : TablatureUiIntent
    /** **Copia** las notas seleccionadas al portapapeles de notas. */
    data object CopySelectedNotes : TablatureUiIntent
    /** **Corta** las notas seleccionadas (copia + borra). */
    data object CutSelectedNotes : TablatureUiIntent
    /** **Pega** el portapapeles de notas en el cursor. */
    data object PasteNotes : TablatureUiIntent
    /** **Elimina** los compases seleccionados (E1). */
    data object DeleteSelection : TablatureUiIntent
    /** **Copia** los compases seleccionados al portapapeles del editor (E3). */
    data object CopySelection : TablatureUiIntent
    /** **Corta** los compases seleccionados (copia + elimina), E3. */
    data object CutSelection : TablatureUiIntent
    /** **Duplica** los compases seleccionados justo detrás (E3). */
    data object DuplicateSelection : TablatureUiIntent
    /** **Pega** el portapapeles en la posición del cursor (E3). */
    data object PasteClipboard : TablatureUiIntent
    /** **Deshace** el último cambio del editor (E2). */
    data object Undo : TablatureUiIntent
    /** **Rehace** el último cambio deshecho (E2). */
    data object Redo : TablatureUiIntent

    /**
     * **Inserta una columna** (evento) en la posición del cursor y desplaza lo que venga después,
     * como `Intro` en My Guitar Tabs. Si el cursor está al final, la añade; si no, abre hueco.
     */
    data class InsertColumnAtCursor(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent
    /**
     * **Inserta una columna** en la posición del **cursor**. Se dispara desde el menú «Columna ▾»
     * (el `Intro` de MuseScore es un **salto de sistema**, así que **no** se le asigna atajo).
     */
    data object InsertColumnAtCursorNow : TablatureUiIntent
    /** Quita la columna (evento) indicada; el compás puede quedarse vacío (silencio). */
    data class RemoveColumnAt(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent
    /** Quita la columna (evento) de la **posición del cursor** (atajo `Ctrl+Supr`). */
    data object RemoveColumnAtCursor : TablatureUiIntent
    /**
     * **Inserta una barra de compás** tras la columna indicada, partiendo el compás en dos. Se
     * corresponde con `Shift+Intro` en My Guitar Tabs; en MuseScore no tiene atajo y se ofrece desde
     * el menú contextual.
     */
    data class InsertBarAfter(val measureIndex: Int, val slotIndex: Int) : TablatureUiIntent

    /**
     * **Inserta un silencio** de la figura [figure] (una figura sonora o directamente un silencio) en
     * la columna del **cursor**, desplazando lo que venga después. Es la operación que faltaba para
     * **escribir silencios** (Fase B.1).
     */
    data class InsertRestAtCursor(val figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure) : TablatureUiIntent

    /**
     * **Completa el compás** de la columna del cursor rellenando con los **silencios** que falten
     * hasta su duración (Fase B.1): un `4/4` con 3 negras se completa con una negra de silencio. Es la
     * inteligencia de TuxGuitar (`TGMeasureManager`), y no destruye nada si ya está completo.
     */
    data class CompleteMeasureAt(val measureIndex: Int) : TablatureUiIntent

    /** **Completa con silencios TODOS los compases** que estén «cortos» (Fase B.1). */
    data object CompleteAllMeasures : TablatureUiIntent

    // ── Entrada de notas al estilo MuseScore (ADR-020) ─────────────────────────────

    /** **Ctrl+↑/↓**: mueve la nota del cursor a la cuerda contigua sin cambiar su altura. */
    data class MoveNoteString(val direction: Int) : TablatureUiIntent

    /**
     * **Arrastrar la cifra a la cuerda adyacente** con el ratón o el dedo (MuseScore: «Drag a fret mark
     * up or down to an adjacent string»). [stringDelta] es cuántas cuerdas se ha movido (positivo =
     * hacia la más grave), sin cambiar la altura de la nota.
     */
    data class DragNoteToAdjacentString(val stringDelta: Int) : TablatureUiIntent

    /** Guarda la tablatura en edición. */
    data object SaveTablature : TablatureUiIntent
    /** Elimina una tablatura. */
    data class DeleteTablature(val tablatureId: String) : TablatureUiIntent
    /** Elimina la tablatura en edición y cierra el editor. */
    data object DeleteEditingTablature : TablatureUiIntent
    data object CloseEditor : TablatureUiIntent

    /** Aplica un modo de transposición a una tablatura existente. */
    data class ApplyTransposition(val tablatureId: String, val mode: TabTranspositionMode) : TablatureUiIntent

    /** Fija el modo de transposición activo (para el selector de la cabecera). */
    data class SetTranspositionMode(val mode: TabTranspositionMode) : TablatureUiIntent
    /** Confirma el redigitado de una tablatura escrita a mano (aviso aceptado). */
    data object ConfirmRefinger : TablatureUiIntent
    /** Cancela el redigitado pendiente. */
    data object CancelRefinger : TablatureUiIntent

    /**
     * Redigita la tablatura en edición **al tono actual del canto**: transporta sus alturas el
     * intervalo entre la tonalidad escrita y la del canto, y recalcula cuerdas y trastes. Es la
     * forma de hacer que una tablatura siga el cambio de tonalidad guardado del canto desde aquí.
     */
    data object RefingerToSongKey : TablatureUiIntent

    /**
     * Deriva una tablatura **equivalente** para **otro instrumento/afinación** (p. ej. laúd ↔
     * guitarra) desde la existente, redigitando sus alturas con el [FingeringEngine]. Se guarda como
     * una tablatura **nueva** (la original se conserva) para poder elegir cuál se muestra.
     */
    data class RefingerToTuning(val tablatureId: String, val tuning: Tuning) : TablatureUiIntent

    /** Solicita salir del editor; si hay cambios sin guardar, pide confirmación antes de cerrar. */
    data object RequestExit : TablatureUiIntent
    /** Confirma descartar los cambios sin guardar y cierra el editor. */
    data object ConfirmDiscard : TablatureUiIntent
    /** Cancela el descarte y permanece en el editor. */
    data object DismissDiscard : TablatureUiIntent
}

/**
 * Redigitado pendiente de confirmación: cambiar las posiciones de una tablatura **escrita a mano**
 * altera la digitación del usuario. La UI muestra un aviso antes de aplicarlo (regla de oro, §4.2).
 */
data class PendingRefinger(val tablatureId: String)

sealed interface TablatureSideEffect {
    data class ShowMessage(val message: String) : TablatureSideEffect
    /** La raíz ofrece «Deshacer» en el snackbar. */
    data class ShowUndoMessage(val message: String) : TablatureSideEffect
    /** Abre la hoja de compartir con el contenido ya preparado. */
    data class ShareContent(val content: String, val fileName: String, val mimeType: String) : TablatureSideEffect
    /**
     * La plataforma abre el **selector de archivo** para elegir una partitura (MusicXML, MuseScore,
     * Guitar Pro o TuxGuitar).
     */
    data object RequestTablatureFile : TablatureSideEffect
}
