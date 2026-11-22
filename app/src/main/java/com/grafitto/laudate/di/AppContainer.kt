package com.grafitto.laudate.di

import android.content.Context
import androidx.room.Room
import com.grafitto.laudate.BuildConfig
import com.grafitto.laudate.data.attachment.DataAttachmentStore
import com.grafitto.laudate.data.backup.AndroidBackupLocationProvider
import com.grafitto.laudate.data.backup.RoomLibraryWiper
import com.grafitto.laudate.data.backup.ZipBackupArchiveStore
import com.grafitto.laudate.data.export.SongPdfExporter
import com.grafitto.laudate.data.local.LaudateDatabase
import com.grafitto.laudate.data.local.dao.AttachmentDao
import com.grafitto.laudate.data.local.dao.CategoryDao
import com.grafitto.laudate.data.local.dao.CelebrationDao
import com.grafitto.laudate.data.local.dao.RhythmPatternDao
import com.grafitto.laudate.data.local.dao.SongDao
import com.grafitto.laudate.data.local.dao.TablatureDao
import com.grafitto.laudate.data.preferences.PreferencesRepositoryImpl
import com.grafitto.laudate.data.repository.AttachmentRepositoryImpl
import com.grafitto.laudate.data.repository.CategoryRepositoryImpl
import com.grafitto.laudate.data.repository.CelebrationRepositoryImpl
import com.grafitto.laudate.data.repository.RhythmPatternRepositoryImpl
import com.grafitto.laudate.data.repository.SongRepositoryImpl
import com.grafitto.laudate.data.repository.TablatureRepositoryImpl
import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.usecase.AddAttachmentToSongUseCase
import com.grafitto.laudate.domain.usecase.AddSongToCelebrationUseCase
import com.grafitto.laudate.domain.usecase.BackfillSongContentHashesUseCase
import com.grafitto.laudate.domain.usecase.CalculateCapoPositionUseCase
import com.grafitto.laudate.domain.usecase.CountSongsByCategoryUseCase
import com.grafitto.laudate.domain.usecase.DeleteSongAttachmentUseCase
import com.grafitto.laudate.domain.usecase.DetectDuplicateSongUseCase
import com.grafitto.laudate.domain.usecase.EnsureCategoriesForImportUseCase
import com.grafitto.laudate.domain.usecase.UpdateCategoryUseCase
import com.grafitto.laudate.domain.usecase.ExportBackupUseCase
import com.grafitto.laudate.domain.usecase.ExportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.ExportSongPackageUseCase
import com.grafitto.laudate.domain.usecase.ImportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.GetTransposedSongUseCase
import com.grafitto.laudate.domain.usecase.ImportBackupUseCase
import com.grafitto.laudate.domain.usecase.ImportCatalogBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportCelebrationBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportChordProSongsUseCase
import com.grafitto.laudate.domain.usecase.ImportTablatureFromFileUseCase
import com.grafitto.laudate.domain.usecase.ImportTablatureUseCase
import com.grafitto.laudate.domain.usecase.ImportSongPackageUseCase
import com.grafitto.laudate.domain.usecase.ParseChordProUseCase
import com.grafitto.laudate.domain.usecase.SearchSongsUseCase

/**
 * Contenedor de inyección de dependencias para la aplicación.
 * Provee instancias de base de datos, DAOs, repositorios y use cases.
 */
class AppContainer(context: Context) {

    /** Contexto de aplicación, para servicios de plataforma (SAF, share, PDF). */
    val appContext: Context = context.applicationContext

    val database: LaudateDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            LaudateDatabase::class.java,
            "laudate.db"
        )
        .addMigrations(
            LaudateDatabase.MIGRATION_1_2,
            LaudateDatabase.MIGRATION_2_3,
            LaudateDatabase.MIGRATION_3_4,
            LaudateDatabase.MIGRATION_4_5,
            LaudateDatabase.MIGRATION_5_6,
            LaudateDatabase.MIGRATION_6_7,
            LaudateDatabase.MIGRATION_7_8,
            LaudateDatabase.MIGRATION_8_9,
            LaudateDatabase.MIGRATION_9_10,
            LaudateDatabase.MIGRATION_10_11,
            LaudateDatabase.MIGRATION_11_12,
            LaudateDatabase.MIGRATION_12_13,
            LaudateDatabase.MIGRATION_13_14
        )
        // En release NUNCA se destruye la base del usuario: si falta una migración, se falla
        // ruidosamente en lugar de borrar sus datos. Solo en DEBUG se tolera un esquema roto.
        .apply {
            if (BuildConfig.DEBUG) {
                fallbackToDestructiveMigration()
            }
        }
        .build()
    }

    val songDao: SongDao by lazy { database.songDao() }
    val celebrationDao: CelebrationDao by lazy { database.celebrationDao() }
    val categoryDao: CategoryDao by lazy { database.categoryDao() }
    val rhythmPatternDao: RhythmPatternDao by lazy { database.rhythmPatternDao() }
    val attachmentDao: AttachmentDao by lazy { database.attachmentDao() }
    val tablatureDao: TablatureDao by lazy { database.tablatureDao() }

    val songRepository: SongRepository by lazy {
        SongRepositoryImpl(songDao)
    }

    /**
     * Preferencias de lectura persistentes (nomenclatura de acordes, …).
     * Son globales a la app: un cambio aquí se refleja en todas las pantallas.
     */
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepositoryImpl(appContext)
    }

    val attachmentRepository: AttachmentRepository by lazy {
        AttachmentRepositoryImpl(attachmentDao)
    }

    /** Almacén de ficheros adjuntos en el área privada de la app. */
    val attachmentStore: AttachmentStore by lazy {
        DataAttachmentStore(appContext)
    }

    val addAttachmentToSongUseCase: AddAttachmentToSongUseCase by lazy {
        AddAttachmentToSongUseCase(attachmentStore, attachmentRepository)
    }

    val deleteSongAttachmentUseCase: DeleteSongAttachmentUseCase by lazy {
        DeleteSongAttachmentUseCase(attachmentStore, attachmentRepository)
    }

    // ── Copia de seguridad completa (ZIP con adjuntos) ─────────────────────────

    val backupArchiveStore: BackupArchiveStore by lazy {
        ZipBackupArchiveStore(appContext)
    }

    val exportBackupUseCase: ExportBackupUseCase by lazy {
        ExportBackupUseCase(
            songRepository = songRepository,
            celebrationRepository = celebrationRepository,
            categoryRepository = categoryRepository,
            attachmentRepository = attachmentRepository,
            rhythmPatternRepository = rhythmPatternRepository,
            archiveStore = backupArchiveStore,
            appVersion = appVersionName,
            tablatureRepository = tablatureRepository
        )
    }

    val importBackupUseCase: ImportBackupUseCase by lazy {
        ImportBackupUseCase(
            songRepository = songRepository,
            celebrationRepository = celebrationRepository,
            categoryRepository = categoryRepository,
            attachmentRepository = attachmentRepository,
            rhythmPatternRepository = rhythmPatternRepository,
            archiveStore = backupArchiveStore,
            libraryWiper = RoomLibraryWiper(
                songDao, celebrationDao, categoryDao, rhythmPatternDao, attachmentDao, attachmentStore
            ),
            tablatureRepository = tablatureRepository,
            backupLocationProvider = AndroidBackupLocationProvider(appContext)
        )
    }

    // ── Paquete de canción con adjuntos (canticora.song, ADR-017) ───────────────

    /** Empaqueta **un canto con sus adjuntos** para compartirlo por WhatsApp, correo, etc. */
    val exportSongPackageUseCase: ExportSongPackageUseCase by lazy {
        ExportSongPackageUseCase(
            songRepository = songRepository,
            attachmentRepository = attachmentRepository,
            tablatureRepository = tablatureRepository,
            archiveStore = backupArchiveStore,
            appVersion = appVersionName
        )
    }

    /** Importa un canto suelto con sus adjuntos, con resolución de conflictos. */
    val importSongPackageUseCase: ImportSongPackageUseCase by lazy {
        ImportSongPackageUseCase(
            songRepository = songRepository,
            attachmentRepository = attachmentRepository,
            tablatureRepository = tablatureRepository,
            archiveStore = backupArchiveStore,
            ensureCategoriesForImportUseCase = ensureCategoriesForImportUseCase
        )
    }

    /**
     * Lector de ficheros de partitura: texto plano (MusicXML) y entradas de ZIP (`.mscz`, `.gp`,
     * `.gpx`, `.tg`, `.mxl`). Reutiliza el lector de ZIP de la copia de seguridad.
     */
    val scoreFileReader: com.grafitto.laudate.domain.transfer.ScoreFileReader by lazy {
        com.grafitto.laudate.data.transfer.AndroidScoreFileReader(
            context = context,
            archiveStore = backupArchiveStore
        )
    }

    /**
     * Importa una partitura (MusicXML, MuseScore, Guitar Pro 7/8, TuxGuitar) como tablatura del
     * canto, reemplazando la previa.
     */
    val importTablatureUseCase: ImportTablatureFromFileUseCase by lazy {
        ImportTablatureFromFileUseCase(
            fileReader = scoreFileReader,
            importTablature = ImportTablatureUseCase(tablatureRepository)
        )
    }

    // ── Intercambio de patrones rítmicos (RF-310) ──────────────────────────────

    /** Construye el archivo/paquete de ritmos para compartir o exportar. */
    val exportRhythmPatternsUseCase: ExportRhythmPatternsUseCase by lazy {
        ExportRhythmPatternsUseCase(rhythmPatternRepository, appVersionName)
    }

    /** Importa ritmos desde un archivo o desde texto pegado, sin duplicar los ya existentes. */
    val importRhythmPatternsUseCase: ImportRhythmPatternsUseCase by lazy {
        ImportRhythmPatternsUseCase(rhythmPatternRepository)
    }

    val celebrationRepository: CelebrationRepository by lazy {
        CelebrationRepositoryImpl(celebrationDao)
    }

    val rhythmPatternRepository: RhythmPatternRepository by lazy {
        RhythmPatternRepositoryImpl(rhythmPatternDao)
    }

    val tablatureRepository: TablatureRepository by lazy {
        TablatureRepositoryImpl(tablatureDao)
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(categoryDao)
    }

    val ensureCategoriesForImportUseCase: EnsureCategoriesForImportUseCase by lazy {
        EnsureCategoriesForImportUseCase(categoryRepository)
    }

    /**
     * Renombra una categoría/momento propagando el cambio a canciones y repertorios.
     *
     * Las categorías canónicas también pueden renombrarse: `is_system_standard` describe su
     * origen, no las hace inmutables (RF-103).
     */
    val updateCategoryUseCase: UpdateCategoryUseCase by lazy {
        UpdateCategoryUseCase(categoryRepository, songRepository, celebrationRepository)
    }

    val searchSongsUseCase: SearchSongsUseCase by lazy {
        SearchSongsUseCase()
    }

    /** Cuenta los cantos de cada categoría para mostrar el número entre paréntesis (RF-103). */
    val countSongsByCategoryUseCase: CountSongsByCategoryUseCase by lazy {
        CountSongsByCategoryUseCase()
    }

    val addSongToCelebrationUseCase: AddSongToCelebrationUseCase by lazy {
        AddSongToCelebrationUseCase(celebrationRepository)
    }

    val getTransposedSongUseCase: GetTransposedSongUseCase by lazy {
        GetTransposedSongUseCase()
    }

    val calculateCapoPositionUseCase: CalculateCapoPositionUseCase by lazy {
        CalculateCapoPositionUseCase()
    }

    val parseChordProUseCase: ParseChordProUseCase by lazy {
        ParseChordProUseCase()
    }

    // ── Huella de contenido y duplicados ───────────────────────────────────────

    val detectDuplicateSongUseCase: DetectDuplicateSongUseCase by lazy {
        DetectDuplicateSongUseCase(songRepository)
    }

    /** Importa repertorios compartidos (`.canticora`) sin sobrescribir canciones existentes. */
    val importCelebrationBundleUseCase: ImportCelebrationBundleUseCase by lazy {
        ImportCelebrationBundleUseCase(songRepository, celebrationRepository, tablatureRepository)
    }

    // ── Importación de contenido (pantalla «Importar») ─────────────────────────

    /**
     * Importa un **catálogo/cancionero completo** en JSON (esquema `BackupPayload`).
     *
     * Es la vía para incorporar la semilla del cancionero, que ya **no** se embarca en el APK:
     * se distribuye aparte y el usuario la importa cuando quiere.
     */
    val importCatalogBundleUseCase: ImportCatalogBundleUseCase by lazy {
        ImportCatalogBundleUseCase(
            songRepository = songRepository,
            categoryRepository = categoryRepository
        )
    }

    /** Importa canciones sueltas en ChordPro, sin sobrescribir las que ya existen. */
    val importChordProSongsUseCase: ImportChordProSongsUseCase by lazy {
        ImportChordProSongsUseCase(
            songRepository = songRepository,
            ensureCategoriesForImportUseCase = ensureCategoriesForImportUseCase
        )
    }

    /** Rellena las huellas de canciones antiguas; se ejecuta en segundo plano al arrancar. */
    val backfillSongContentHashesUseCase: BackfillSongContentHashesUseCase by lazy {
        BackfillSongContentHashesUseCase(songRepository)
    }

    // ── Exportación (PDF) ─────────────────────────────────────────────────────

    val songPdfExporter: SongPdfExporter by lazy {
        SongPdfExporter(appContext.contentResolver, musicTypeface)
    }

    /**
     * **Bravura**, la fuente musical SMuFL con la que el PDF imprime las figuras de ritmo, los
     * silencios y las cifras de los grupos irregulares.
     *
     * Compose Multiplatform empaqueta los recursos de `core:ui` en `assets/composeResources/…`, así
     * que la ruta es la del paquete de su clase `Res`. Se carga con `runCatching` porque un fallo
     * aquí **no** debe impedir exportar: sin fuente, la tablatura sale sin la fila de ritmo, pero
     * con sus cuerdas, barras y cifras de traste.
     */
    private val musicTypeface: android.graphics.Typeface? by lazy {
        runCatching {
            android.graphics.Typeface.createFromAsset(
                appContext.assets,
                "composeResources/com.grafitto.laudate.ui.generated.resources/font/bravura.otf"
            )
        }.getOrNull()
    }

    /** Versión de la app anotada en los repertorios exportados. */
    val appVersionName: String get() = BuildConfig.VERSION_NAME
}
