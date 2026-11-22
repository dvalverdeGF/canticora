package com.grafitto.laudate.web

import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.LibraryWiper
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.rhythm.RhythmPlayer
import com.grafitto.laudate.domain.usecase.AddAttachmentToSongUseCase
import com.grafitto.laudate.domain.usecase.AddSongToCelebrationUseCase
import com.grafitto.laudate.domain.usecase.DeleteSongAttachmentUseCase
import com.grafitto.laudate.domain.usecase.DetectDuplicateSongUseCase
import com.grafitto.laudate.domain.usecase.EnsureCategoriesForImportUseCase
import com.grafitto.laudate.domain.usecase.ExportBackupUseCase
import com.grafitto.laudate.domain.usecase.ExportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.ExportSongPackageUseCase
import com.grafitto.laudate.domain.usecase.GetTransposedSongUseCase
import com.grafitto.laudate.domain.usecase.ImportBackupUseCase
import com.grafitto.laudate.domain.usecase.ImportCatalogBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportCelebrationBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportChordProSongsUseCase
import com.grafitto.laudate.domain.usecase.ImportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.ImportSongPackageUseCase
import com.grafitto.laudate.domain.usecase.ImportTablatureFromFileUseCase
import com.grafitto.laudate.domain.usecase.ImportTablatureUseCase
import com.grafitto.laudate.domain.usecase.ParseChordProUseCase
import com.grafitto.laudate.domain.usecase.SearchSongsUseCase
import com.grafitto.laudate.domain.usecase.UpdateCategoryUseCase
import com.grafitto.laudate.web.data.WebAttachmentRepository
import com.grafitto.laudate.web.data.WebAttachmentStore
import com.grafitto.laudate.web.data.WebCategoryRepository
import com.grafitto.laudate.web.data.WebCelebrationRepository
import com.grafitto.laudate.web.data.WebPreferencesRepository
import com.grafitto.laudate.web.data.WebRhythmPatternRepository
import com.grafitto.laudate.web.data.WebSongRepository
import com.grafitto.laudate.web.data.WebStore
import com.grafitto.laudate.web.data.WebTablatureRepository
import com.grafitto.laudate.web.platform.WebBackupArchiveStore
import com.grafitto.laudate.web.platform.WebBackupLocationProvider
import com.grafitto.laudate.web.platform.WebLibraryWiper
import com.grafitto.laudate.web.platform.WebRhythmPlayer
import com.grafitto.laudate.web.platform.WebScoreFileReader

/**
 * Contenedor de dependencias de la **web** — ADR-023.
 *
 * Es el gemelo de `AppContainer` (Android) y `DesktopContainer` (escritorio) sin `Context`, sin
 * Room y sin ficheros: los repositorios son las implementaciones web del módulo `web.data` sobre
 * [WebStore] (`localStorage`). Los casos de uso y los ViewModels son los mismos objetos que en las
 * otras dos plataformas, así que una pantalla nueva aparece aquí sin tocar nada.
 *
 * Lo único propio de la web es [hydrate]: el catálogo semilla se descarga y se parsea al arrancar.
 */
class WebContainer {

    private val store = WebStore()

    // ── Repositorios ─────────────────────────────────────────────────────────
    val songRepository: SongRepository = WebSongRepository(store)
    val categoryRepository: CategoryRepository = WebCategoryRepository(store)
    val celebrationRepository: CelebrationRepository = WebCelebrationRepository(store)
    val rhythmPatternRepository: RhythmPatternRepository = WebRhythmPatternRepository(store)
    val attachmentRepository: AttachmentRepository = WebAttachmentRepository(store)
    val tablatureRepository: TablatureRepository = WebTablatureRepository(store)
    val preferencesRepository: PreferencesRepository = WebPreferencesRepository()

    /** La web no tiene almacén de ficheros: se declara para satisfacer los casos de uso. */
    val attachmentStore = WebAttachmentStore()

    // ── Servicios de plataforma (equivalentes web de los de escritorio) ─────
    /** ZIP de copias y paquetes: se descarga o se guarda en `localStorage`/IndexedDB. */
    val backupArchiveStore: BackupArchiveStore = WebBackupArchiveStore()
    val rhythmPlayer: RhythmPlayer = WebRhythmPlayer()
    private val libraryWiper: LibraryWiper = WebLibraryWiper(store)
    private val backupLocationProvider = WebBackupLocationProvider()
    private val scoreFileReader = WebScoreFileReader()

    /**
     * Versión que se anota en los repertorios exportados y que muestra «Acerca de Canticora».
     * Se genera desde `gradle.properties` (ADR-022) para no repetir el número a mano.
     */
    val appVersionName: String = CanticoraVersion.WEB_NAME

    // ── Casos de uso compartidos (Dominio puro) ──────────────────────────────
    val searchSongsUseCase = SearchSongsUseCase()
    val addSongToCelebrationUseCase = AddSongToCelebrationUseCase(celebrationRepository)
    val ensureCategoriesForImportUseCase = EnsureCategoriesForImportUseCase(categoryRepository)
    val getTransposedSongUseCase = GetTransposedSongUseCase()
    val parseChordProUseCase = ParseChordProUseCase()
    val detectDuplicateSongUseCase = DetectDuplicateSongUseCase(songRepository)
    val importCelebrationBundleUseCase = ImportCelebrationBundleUseCase(
        songRepository,
        celebrationRepository,
        tablatureRepository
    )
    val importCatalogBundleUseCase = ImportCatalogBundleUseCase(songRepository, categoryRepository)
    val importChordProSongsUseCase =
        ImportChordProSongsUseCase(songRepository, ensureCategoriesForImportUseCase)
    val importRhythmPatternsUseCase = ImportRhythmPatternsUseCase(rhythmPatternRepository)
    val addAttachmentToSongUseCase = AddAttachmentToSongUseCase(attachmentStore, attachmentRepository)
    val deleteSongAttachmentUseCase =
        DeleteSongAttachmentUseCase(attachmentStore, attachmentRepository)
    val updateCategoryUseCase =
        UpdateCategoryUseCase(categoryRepository, songRepository, celebrationRepository)

    // ── Ritmos, copias de seguridad y paquetes con adjuntos ──────────────────
    val exportRhythmPatternsUseCase =
        ExportRhythmPatternsUseCase(rhythmPatternRepository, appVersionName)
    val exportBackupUseCase = ExportBackupUseCase(
        songRepository, celebrationRepository, categoryRepository,
        attachmentRepository, rhythmPatternRepository, backupArchiveStore, appVersionName,
        tablatureRepository = tablatureRepository
    )
    val importBackupUseCase = ImportBackupUseCase(
        songRepository, celebrationRepository, categoryRepository,
        attachmentRepository, rhythmPatternRepository, backupArchiveStore,
        libraryWiper = libraryWiper,
        tablatureRepository = tablatureRepository,
        backupLocationProvider = backupLocationProvider
    )
    val exportSongPackageUseCase = ExportSongPackageUseCase(
        songRepository, attachmentRepository, tablatureRepository, backupArchiveStore, appVersionName
    )
    val importSongPackageUseCase = ImportSongPackageUseCase(
        songRepository, attachmentRepository, tablatureRepository, backupArchiveStore,
        ensureCategoriesForImportUseCase
    )
    val importTablatureUseCase = ImportTablatureFromFileUseCase(
        scoreFileReader,
        ImportTablatureUseCase(tablatureRepository)
    )

    /** Carga favoritos, repertorios y ajustes guardados en el navegador (síncrono, kilobytes). */
    fun restoreUserData() = store.restore()

    /** Incorpora el catálogo semilla ya descargado y parseado. */
    fun hydrate(songs: List<Song>, categories: List<LiturgicalCategory>) =
        store.hydrate(songs, categories)

    /** Copia completa de la biblioteca en JSON (`BackupPayload`), para descargarla. */
    fun exportLibraryJson(): String = store.exportLibraryJson()

    /** Guarda un repertorio (lo usa la navegación al volver del modo en vivo, si hiciera falta). */
    suspend fun saveCelebration(celebration: Celebration) =
        celebrationRepository.saveCelebration(celebration)
}
