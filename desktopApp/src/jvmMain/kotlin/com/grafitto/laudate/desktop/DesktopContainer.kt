package com.grafitto.laudate.desktop

import com.grafitto.laudate.data.attachment.DesktopAttachmentStore
import com.grafitto.laudate.data.backup.DesktopBackupArchiveStore
import com.grafitto.laudate.data.backup.DesktopBackupLocationProvider
import com.grafitto.laudate.data.backup.RoomLibraryWiper
import com.grafitto.laudate.data.local.LaudateDatabase
import com.grafitto.laudate.data.local.buildDesktopDatabase
import com.grafitto.laudate.data.preferences.PreferencesRepositoryImpl
import com.grafitto.laudate.data.repository.AttachmentRepositoryImpl
import com.grafitto.laudate.data.repository.CategoryRepositoryImpl
import com.grafitto.laudate.data.repository.CelebrationRepositoryImpl
import com.grafitto.laudate.data.repository.RhythmPatternRepositoryImpl
import com.grafitto.laudate.data.repository.SongRepositoryImpl
import com.grafitto.laudate.data.repository.TablatureRepositoryImpl
import com.grafitto.laudate.data.rhythm.DesktopRhythmPlayer
import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.backup.BackupArchiveStore
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
import com.grafitto.laudate.domain.usecase.ParseChordProUseCase
import com.grafitto.laudate.domain.usecase.UpdateCategoryUseCase
import com.grafitto.laudate.domain.parser.ValidateChordProUseCase
import java.io.File

/**
 * Contenedor de dependencias de la app de escritorio.
 *
 * Es el equivalente de `AppContainer` en Android, pero **sin `Context`**: la base de datos y los
 * ficheros viven en la carpeta de datos del usuario (`~/.canticora/`). Monta todo el stack compartido
 * (Room KMP + repositorios + use cases) para reutilizar los MISMOS ViewModels y pantallas que
 * Android. Ver ADR-013.
 */
class DesktopContainer(
    /**
     * Carpeta de datos (base de datos, adjuntos, preferencias). Por defecto `~/.canticora`; se puede
     * inyectar otra (p. ej. una temporal) para aislar pruebas.
     *
     * ⚠️ `~/.canticora` y `canticora.db` son **identidad técnica interna** (como el paquete
     * `com.grafitto.laudate`): NO se renombran con la marca para no perder los datos de los
     * usuarios existentes. Ver ADR-015.
     */
    val dataDirectory: File = File(System.getProperty("user.home"), ".canticora").apply { mkdirs() }
) {

    private val database: LaudateDatabase =
        buildDesktopDatabase(File(dataDirectory, "canticora.db").absolutePath)

    // ── Repositorios ─────────────────────────────────────────────────────────
    val songRepository: SongRepository = SongRepositoryImpl(database.songDao())
    val categoryRepository: CategoryRepository = CategoryRepositoryImpl(database.categoryDao())
    val celebrationRepository: CelebrationRepository = CelebrationRepositoryImpl(database.celebrationDao())
    val rhythmPatternRepository: RhythmPatternRepository = RhythmPatternRepositoryImpl(database.rhythmPatternDao())
    val attachmentRepository: AttachmentRepository = AttachmentRepositoryImpl(database.attachmentDao())
    val tablatureRepository: TablatureRepository = TablatureRepositoryImpl(database.tablatureDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepositoryImpl(dataDirectory)

    // ── Servicios de plataforma ──────────────────────────────────────────────
    private val desktopAttachmentStore = DesktopAttachmentStore(dataDirectory)
    val attachmentStore: AttachmentStore = desktopAttachmentStore
    val backupArchiveStore: BackupArchiveStore = DesktopBackupArchiveStore(desktopAttachmentStore)
    val rhythmPlayer: RhythmPlayer = DesktopRhythmPlayer()

    /**
     * Versión de la aplicación (se anota en repertorios y copias exportadas, y la muestra
     * «Acerca de Canticora»).
     *
     * Se genera desde `gradle.properties` en build time (`CanticoraVersion.kt`, ADR-022):
     * antes era un literal aquí y se quedó en «1.0.24-desktop» aunque la Store ya servía
     * la 1.1.3, con lo que la app parecía no haberse actualizado.
     */
    val appVersionName: String = CanticoraVersion.DESKTOP_NAME

    /**
     * Fichero `.canticora` abierto con la app (doble clic / «Abrir con»), si lo hay.
     * Se consume desde la UI para ofrecer importarlo (asociación de ficheros de escritorio).
     */
    var incomingFile: File? = null

    // ── Casos de uso (dominio puro) ──────────────────────────────────────────
    val ensureCategoriesForImportUseCase = EnsureCategoriesForImportUseCase(categoryRepository)
    val addSongToCelebrationUseCase = AddSongToCelebrationUseCase(celebrationRepository)
    val updateCategoryUseCase = UpdateCategoryUseCase(categoryRepository, songRepository, celebrationRepository)
    val detectDuplicateSongUseCase = DetectDuplicateSongUseCase(songRepository)
    val getTransposedSongUseCase = GetTransposedSongUseCase()
    val parseChordProUseCase = ParseChordProUseCase()
    val validateChordProUseCase = ValidateChordProUseCase()
    val addAttachmentToSongUseCase = AddAttachmentToSongUseCase(attachmentStore, attachmentRepository)
    val deleteSongAttachmentUseCase = DeleteSongAttachmentUseCase(attachmentStore, attachmentRepository)
    val importCatalogBundleUseCase = ImportCatalogBundleUseCase(songRepository, categoryRepository)
    val importCelebrationBundleUseCase = ImportCelebrationBundleUseCase(songRepository, celebrationRepository, tablatureRepository)
    val importChordProSongsUseCase = ImportChordProSongsUseCase(songRepository, ensureCategoriesForImportUseCase)
    val importRhythmPatternsUseCase = ImportRhythmPatternsUseCase(rhythmPatternRepository)
    val exportRhythmPatternsUseCase = ExportRhythmPatternsUseCase(rhythmPatternRepository, appVersionName)
    val exportBackupUseCase = ExportBackupUseCase(
        songRepository, celebrationRepository, categoryRepository,
        attachmentRepository, rhythmPatternRepository, backupArchiveStore, appVersionName,
        tablatureRepository = tablatureRepository
    )
    val importBackupUseCase = ImportBackupUseCase(
        songRepository, celebrationRepository, categoryRepository,
        attachmentRepository, rhythmPatternRepository, backupArchiveStore,
        libraryWiper = RoomLibraryWiper(
            database.songDao(), database.celebrationDao(), database.categoryDao(),
            database.rhythmPatternDao(), database.attachmentDao(), attachmentStore
        ),
        tablatureRepository = tablatureRepository,
        backupLocationProvider = DesktopBackupLocationProvider(dataDirectory)
    )

    // ── Paquete de canción con adjuntos (canticora.song, ADR-017) ───────────────
    val exportSongPackageUseCase = ExportSongPackageUseCase(
        songRepository, attachmentRepository, tablatureRepository, backupArchiveStore, appVersionName
    )
    val importSongPackageUseCase = ImportSongPackageUseCase(
        songRepository, attachmentRepository, tablatureRepository, backupArchiveStore,
        ensureCategoriesForImportUseCase
    )

    // ── Importar una partitura como tablatura del canto ─────────────────────────
    private val scoreFileReader = com.grafitto.laudate.data.transfer.DesktopScoreFileReader(
        backupArchiveStore
    )
    val importTablatureUseCase = com.grafitto.laudate.domain.usecase.ImportTablatureFromFileUseCase(
        scoreFileReader,
        com.grafitto.laudate.domain.usecase.ImportTablatureUseCase(tablatureRepository)
    )

    /** Precarga las categorías litúrgicas canónicas si la base está vacía (primera ejecución). */
    suspend fun seedCategories() = categoryRepository.seedStandardCategoriesIfEmpty()

    fun close() {
        rhythmPlayer.stop()
        database.close()
    }
}
