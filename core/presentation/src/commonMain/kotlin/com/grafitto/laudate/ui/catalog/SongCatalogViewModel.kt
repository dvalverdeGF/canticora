package com.grafitto.laudate.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.model.song.SearchQueryPolicy
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSearchCriteria
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.usecase.AddSongToCelebrationUseCase
import com.grafitto.laudate.domain.usecase.CountSongsByCategoryUseCase
import com.grafitto.laudate.domain.usecase.EnsureCategoriesForImportUseCase
import com.grafitto.laudate.domain.serializer.ChordProSerializer
import com.grafitto.laudate.domain.usecase.SearchSongsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del catálogo (MVI).
 *
 * **Rendimiento (RNF-102/RNF-103).** Con ~1.000 cantos, buscar en la **letra** en cada pulsación
 * bloquea la escritura. Para evitarlo:
 *
 *  1. **Búsqueda ligera.** Mientras el texto no llega a [SearchQueryPolicy.MIN_QUERY_LENGTH]
 *     caracteres no se carga la letra: se usa `getAllSongs()` (sin secciones) y se busca sobre
 *     título/autor/número. En cuanto el texto es significativo (o hay filtros), se cambia a
 *     `getAllSongsWithLyrics()`.
 *  2. **Debounce** de 200 ms: no se recalcula en cada tecla intermedia.
 *  3. **Muestra acotada** (máx. [SearchQueryPolicy.MAX_RESULTS]) hasta que la consulta es
 *     significativa; `resultCount` e `isShowingSample` informan a la UI del total real.
 *  4. Todo el filtrado/ordenado se ejecuta en [computeDispatcher], nunca en el hilo principal.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SongCatalogViewModel(
    private val songRepository: SongRepository,
    private val categoryRepository: CategoryRepository,
    private val celebrationRepository: CelebrationRepository? = null,
    private val addSongToCelebrationUseCase: AddSongToCelebrationUseCase? = null,
    private val searchSongsUseCase: SearchSongsUseCase = SearchSongsUseCase(),
    private val countSongsByCategoryUseCase: CountSongsByCategoryUseCase = CountSongsByCategoryUseCase(),
    private val ensureCategoriesForImportUseCase: EnsureCategoriesForImportUseCase? = null,
    /** Dispatcher para el filtrado en memoria; nunca debe ser Main. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Preferencias globales: recuerda si el PDF se exporta con acordes o solo con la letra. */
    private val preferencesRepository: PreferencesRepository? = null,
    /** Adjuntos (para el icono del catálogo). Opcional por retrocompatibilidad. */
    private val attachmentRepository: AttachmentRepository? = null,
    /** Tablaturas (para el icono del catálogo). Opcional por retrocompatibilidad. */
    private val tablatureRepository: TablatureRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongCatalogUiState())
    val uiState: StateFlow<SongCatalogUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<SongCatalogSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    /** Catálogo completo con letra; se usa para acciones (añadir a misa) y el contador total. */
    private val catalog = MutableStateFlow<List<Song>>(emptyList())

    /** Criterios vigentes: entrada del flujo de resultados. */
    private val criteria = MutableStateFlow(SongSearchCriteria())

    private val celebrationsFlow = celebrationRepository?.getAllCelebrations()
        ?: kotlinx.coroutines.flow.flowOf(emptyList())

    init {
        observeCatalog()
        observeResults()
        observePdfPreference()
        observeContentBadges()
        processIntent(SongCatalogUiIntent.LoadCatalog)
    }

    /**
     * Observa qué cantos tienen adjuntos o tablaturas para pintar los iconos del listado
     * **sin** una consulta por tarjeta (RNF-103).
     */
    private fun observeContentBadges() {
        val attachments = attachmentRepository?.observeSongIdsWithAttachments()
        val tablatures = tablatureRepository?.observeSongIdsWithTablatures()
        val attachmentsFlow = attachments ?: kotlinx.coroutines.flow.flowOf(emptySet())
        val tablaturesFlow = tablatures ?: kotlinx.coroutines.flow.flowOf(emptySet())
        viewModelScope.launch {
            combine(attachmentsFlow, tablaturesFlow) { withAtt, withTab -> withAtt to withTab }
                .collect { (withAtt, withTab) ->
                    _uiState.update {
                        it.copy(songIdsWithAttachments = withAtt, songIdsWithTablatures = withTab)
                    }
                }
        }
    }

    /** La preferencia «PDF con acordes» es global y se recuerda entre sesiones. */
    private fun observePdfPreference() {
        val prefs = preferencesRepository ?: return
        viewModelScope.launch {
            prefs.songDisplayMode.collect { mode ->
                _uiState.update { it.copy(pdfDisplayMode = mode) }
            }
        }
    }

    fun processIntent(intent: SongCatalogUiIntent) {
        when (intent) {
            is SongCatalogUiIntent.LoadCatalog -> loadCatalog()
            is SongCatalogUiIntent.UpdateSearchCriteria -> {
                // Solo se publican los criterios: `observeResults` recalcula con debounce.
                criteria.value = intent.criteria
                _uiState.update { it.copy(searchCriteria = intent.criteria) }
            }
            is SongCatalogUiIntent.ToggleSongFavorite -> {
                viewModelScope.launch {
                    songRepository.setFavorite(intent.songId, !intent.currentFavorite)
                }
            }
            is SongCatalogUiIntent.DeleteSong -> {
                viewModelScope.launch {
                    songRepository.deleteSong(intent.songId)
                    _sideEffect.send(SongCatalogSideEffect.ShowMessage("Canción eliminada"))
                }
            }
            is SongCatalogUiIntent.ShareSong -> shareSong(intent.songId)
            is SongCatalogUiIntent.ShareSongWithAttachments -> shareSongWithAttachments(intent.songId)
            is SongCatalogUiIntent.ExportSongPdf -> exportSongPdf(intent.songId, intent.mode)
            is SongCatalogUiIntent.AddSongToCelebration -> addSongToCelebration(intent.celebrationId, intent.songId)
            is SongCatalogUiIntent.UndoCelebrationAdd -> undoCelebrationAdd()
            is SongCatalogUiIntent.DismissPendingAction -> _uiState.update { it.copy(pendingAction = null) }
            is SongCatalogUiIntent.ImportSongs -> importSongs(intent.songs)
        }
    }

    /** Comparte el canto como ChordPro por la hoja nativa del sistema. */
    private fun shareSong(songId: String) {
        val song = catalog.value.firstOrNull { it.id == songId } ?: return
        viewModelScope.launch {
            _sideEffect.send(
                SongCatalogSideEffect.ShareSong(
                    title = song.title,
                    chordPro = ChordProSerializer.serialize(song)
                )
            )
        }
    }

    /**
     * Empaqueta el canto con sus adjuntos (`canticora.song`) y lo comparte.
     * La UI resuelve el destino del ZIP (caché interna) y ejecuta [ExportSongPackageUseCase].
     */
    private fun shareSongWithAttachments(songId: String) {
        val song = catalog.value.firstOrNull { it.id == songId } ?: return
        viewModelScope.launch {
            _sideEffect.send(
                SongCatalogSideEffect.ShareSongPackage(
                    songId = song.id,
                    title = song.title,
                    suggestedFileName = buildPackageFileName(song)
                )
            )
        }
    }

    /** Nombre de archivo seguro (sin caracteres prohibidos) para el paquete del canto. */
    private fun buildPackageFileName(song: Song): String {
        val safeTitle = song.title
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifBlank { "canto" }
        return "$safeTitle.canticora"
    }

    /** Prepara el PDF del canto y pide el destino (SAF); recuerda el modo de vista elegido. */
    private fun exportSongPdf(songId: String, mode: com.grafitto.laudate.domain.model.song.SongDisplayMode) {
        val song = catalog.value.firstOrNull { it.id == songId } ?: return
        viewModelScope.launch {
            preferencesRepository?.setSongDisplayMode(mode)
            _sideEffect.send(
                SongCatalogSideEffect.ExportSongPdf(
                    song = song,
                    suggestedFileName = buildPdfFileName(song),
                    mode = mode
                )
            )
        }
    }

    /** Nombre de archivo seguro (sin caracteres prohibidos) para el PDF exportado. */
    private fun buildPdfFileName(song: Song): String {
        val safeTitle = song.title
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifBlank { "canto" }
        return "$safeTitle.pdf"
    }

    /**
     * Añade un canto a una celebración. El momento litúrgico se sugiere a partir del propio
     * canto (primer momento por defecto); nunca se duplica el canto en la misma lista.
     */
    private fun addSongToCelebration(celebrationId: String, songId: String) {
        val useCase = addSongToCelebrationUseCase ?: return
        viewModelScope.launch {
            val song = catalog.value.firstOrNull { it.id == songId } ?: return@launch
            val celebrationName = _uiState.value.celebrations
                .firstOrNull { it.id == celebrationId }?.name
                ?: "la celebración"

            val result = useCase(celebrationId, song)
            if (result == null) {
                _sideEffect.send(SongCatalogSideEffect.ShowMessage("No se encontró la celebración"))
                return@launch
            }

            if (result.alreadyPresent) {
                _uiState.update { it.copy(pendingAction = null) }
                _sideEffect.send(
                    SongCatalogSideEffect.ShowMessage("«${song.title}» ya estaba en $celebrationName")
                )
                return@launch
            }

            // La confirmación se ofrece como Snackbar con «Deshacer» desde la pantalla.
            _uiState.update {
                it.copy(
                    pendingAction = PendingCelebrationAction(
                        celebrationId = celebrationId,
                        celebrationName = celebrationName,
                        song = song,
                        alreadyPresent = false
                    )
                )
            }
        }
    }

    private fun undoCelebrationAdd() {
        val pending = _uiState.value.pendingAction ?: return
        viewModelScope.launch {
            val repo = celebrationRepository
            if (repo != null) {
                val celebration = repo.getCelebrationById(pending.celebrationId).firstOrNull()
                if (celebration != null) {
                    val remaining = celebration.items
                        .filterNot { it.songId == pending.song.id }
                        .mapIndexed { index, item -> item.copy(displayOrder = index) }
                    repo.saveCelebration(celebration.copy(items = remaining))
                }
            }
            _uiState.update { it.copy(pendingAction = null) }
        }
    }

    /**
     * Importa un lote de canciones y garantiza que existan las categorías referenciadas.
     * Las categorías nuevas se clasifican por naturaleza (momento de misa, tiempo litúrgico
     * o categoría adicional) para no mezclar las partes de la liturgia con el resto.
     */
    private fun importSongs(songs: List<com.grafitto.laudate.domain.model.song.Song>) {        if (songs.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }

            val referencedCategories = songs
                .flatMap { it.defaultLiturgicalMoments + it.defaultLiturgicalTimes }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val result = ensureCategoriesForImportUseCase?.invoke(referencedCategories)

            songs.forEach { songRepository.saveSong(it) }

            _uiState.update { it.copy(isImporting = false) }
            val created = result?.totalCreated ?: 0
            val message = if (created > 0) {
                "Importadas ${songs.size} canciones · $created categorías nuevas registradas"
            } else {
                "Importadas ${songs.size} canciones"
            }
            _sideEffect.send(SongCatalogSideEffect.ShowMessage(message))
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            // El catálogo se carga de forma reactiva en `observeCatalog`; aquí solo se
            // garantiza que existan las categorías canónicas (idempotente).
            categoryRepository.seedStandardCategoriesIfEmpty()
        }
    }

    /** Catálogo completo con letra: alimenta el contador total y las acciones por id. */
    private fun observeCatalog() {
        viewModelScope.launch {
            combine(
                songRepository.getAllSongsWithLyrics(),
                categoryRepository.getAllCategories(),
                celebrationsFlow
            ) { songs, allCategories, celebrations ->
                Triple(songs, allCategories, celebrations)
            }.collect { (songs, allCategories, celebrations) ->
                catalog.value = songs
                val counts = countSongsByCategoryUseCase(songs, allCategories)
                _uiState.update { current ->
                    current.copy(
                        categories = allCategories,
                        categorySongCounts = counts,
                        celebrations = celebrations,
                        totalSongCount = songs.size,
                        errorMessage = null
                    )
                }
            }
        }
    }

    /**
     * Recalcula los resultados ante cada cambio de criterios.
     *
     * `criteria → debounce → flatMapLatest(búsqueda)`: el debounce evita cálculos en teclas
     * intermedias y el `flatMapLatest` cancela la búsqueda en curso si llega una consulta más
     * nueva. El cálculo se ejecuta en [computeDispatcher].
     */
    private fun observeResults() {
        viewModelScope.launch {
            criteria
                .debounce(RESULTS_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { current -> searchResultsFlow(current) }
                .flowOn(computeDispatcher)
                .collect { results ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            songs = results.songs,
                            resultCount = results.totalCount,
                            isShowingSample = results.isSample
                        )
                    }
                }
        }
    }

    /**
     * Resultados para unos criterios. Solo se carga la letra cuando el texto es significativo;
     * con pocos caracteres se busca sobre metadatos (título/autor/número), que es instantáneo.
     */
    private fun searchResultsFlow(current: SongSearchCriteria) =
        (if (SearchQueryPolicy.isSearchQueryValid(current.query)) {
            songRepository.getAllSongsWithLyrics()
        } else {
            songRepository.getAllSongs()
        }).map { songs ->
            val filtered = searchSongsUseCase(songs, current)
            val limited = SearchQueryPolicy.limited(filtered, current)
            SearchResults(
                songs = limited,
                totalCount = filtered.size,
                isSample = limited.size < filtered.size
            )
        }

    /** Resultado interno de una búsqueda: la muestra visible y los totales para la UI. */
    private data class SearchResults(
        val songs: List<Song>,
        val totalCount: Int,
        val isSample: Boolean
    )

    private companion object {
        /** Espera antes de recalcular: evita buscar en cada pulsación intermedia. */
        const val RESULTS_DEBOUNCE_MS = 200L
    }
}
