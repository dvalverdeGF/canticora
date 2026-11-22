package com.grafitto.laudate.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grafitto.laudate.data.local.dao.SongDao
import com.grafitto.laudate.data.local.entity.CelebrationEntity
import com.grafitto.laudate.data.local.entity.CelebrationItemEntity
import com.grafitto.laudate.data.local.entity.ChordAnchorEntity
import com.grafitto.laudate.data.local.entity.SongAttachmentEntity
import com.grafitto.laudate.data.local.entity.SongEntity
import com.grafitto.laudate.data.local.entity.SongSectionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Regresión del error «Foreign key constraint failed (code 1811)»** al restaurar una copia.
 *
 * Reproduce contra **Room real** (SQLite en el dispositivo) el escenario que fallaba:
 * un canto guardado que forma parte de un repertorio y que vuelve a llegar en un lote
 * (restauración de copia o reimportación de un cancionero).
 *
 * El error lo provocaba `@Insert(onConflict = REPLACE)` en `songs`: SQLite implementa el `REPLACE`
 * como `DELETE` + `INSERT`, y ese borrado dispara las claves foráneas de los hijos:
 *  - `celebration_items.song_id` es `ON DELETE RESTRICT`, así que SQLite **aborta** el borrado con
 *    el trigger interno del código 1811 (`SQLITE_CONSTRAINT_TRIGGER`);
 *  - `song_attachments.song_id` es `ON DELETE CASCADE`, así que además **borraba en silencio los
 *    adjuntos del usuario**.
 *
 * Estos tests fijan el comportamiento correcto: los adjuntos y los vínculos con repertorios deben
 * sobrevivir, y un lote con canciones ya existentes no debe fallar ni duplicar nada.
 */
@RunWith(AndroidJUnit4::class)
class SongUpsertForeignKeyTest {

    private lateinit var database: LaudateDatabase
    private lateinit var songDao: SongDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LaudateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoringABatchWithASongAlreadyInACelebrationDoesNotFail() = runBlocking {
        // 1) Un canto guardado que ya forma parte de un repertorio (esto es lo que rompía el REPLACE).
        songDao.upsertFullSong(song("s1"), sections("s1"), anchors("s1"))
        database.celebrationDao().insertFullCelebration(
        CelebrationEntity(
            id = "c1",
            name = "Misa del domingo",
            dateTimestamp = 1L,
            description = null,
            location = null,
            celebrationType = "Misa",
            createdAt = 1L,
            updatedAt = 1L
        ),
            listOf(
                CelebrationItemEntity(
                    id = "ci1",
                    celebrationId = "c1",
                    songId = "s1",
                    liturgicalMoment = "Entrada",
                    displayOrder = 0,
                    overrideKey = null,
                    overrideCapo = null,
                    customNotes = null
                )
            )
        )

        // 2) Llega el mismo canto otra vez (restauración de una copia que lo incluye).
        //    Antes lanzaba «Foreign key constraint failed (code 1811 sqlite_constraint_trigger)».
        songDao.insertFullSongs(listOf(song("s1")), sections("s1"), anchors("s1"))

        // 3) El vínculo con el repertorio sigue intacto y no se ha duplicado el canto.
        assertEquals(1, songDao.getSongCount())
        val items = database.query("SELECT COUNT(*) FROM celebration_items", null).use { cursor ->
            cursor.moveToFirst(); cursor.getInt(0)
        }
        assertEquals(1, items)
    }

    @Test
    fun updatingASongKeepsItsAttachments() = runBlocking {
        songDao.upsertFullSong(song("s1"), sections("s1"), anchors("s1"))
        database.attachmentDao().insertAttachment(
            SongAttachmentEntity(
                id = "a1",
                songId = "s1",
                kind = "AUDIO",
                displayName = "ensayo.mp3",
                uri = "/data/attachments/s1/ensayo.mp3",
                sourceUri = null,
                mimeType = "audio/mpeg",
                sizeBytes = 1234L,
                createdAt = 1L
            )
        )
        assertEquals(1, database.attachmentDao().countForSong("s1"))

        // Editar el canto (mismo id) no debe llevarse por delante sus adjuntos.
        songDao.upsertFullSong(
            song("s1").copy(title = "Título editado"),
            sections("s1"),
            anchors("s1")
        )

        assertEquals(1, database.attachmentDao().countForSong("s1"))
        assertEquals("Título editado", songDao.getSongWithDetailsById("s1")?.song?.title)
    }

    @Test
    fun reimportingTheSameBatchDoesNotDuplicateSongsNorSections() = runBlocking {
        songDao.insertFullSongs(listOf(song("s1"), song("s2")), sections("s1", "s2"), anchors("s1", "s2"))
        songDao.insertFullSongs(listOf(song("s1"), song("s2")), sections("s1", "s2"), anchors("s1", "s2"))

        assertEquals(2, songDao.getSongCount())
        val sectionsCount = database.query("SELECT COUNT(*) FROM song_sections", null).use {
            it.moveToFirst(); it.getInt(0)
        }
        assertEquals(2, sectionsCount)
    }

    @Test
    fun editedSectionsOfAnExistingSongAreUpdated() = runBlocking {
        songDao.upsertFullSong(song("s1"), sections("s1"), anchors("s1"))

        // Nueva versión de las secciones para el mismo canto (contenido distinto, mismo id).
        songDao.upsertFullSong(
            song("s1"),
            listOf(
                SongSectionEntity(
                    id = "s1-sec-2",
                    songId = "s1",
                    sectionType = "VERSE",
                    sequenceOrder = 0,
                    rawTextContent = "Letra nueva",
                    headerTitle = null
                )
            ),
            emptyList()
        )

        val sections = database.query("SELECT raw_text_content FROM song_sections WHERE song_id = 's1'", null).use {
            it.moveToFirst(); it.getString(0)
        }
        assertEquals("Letra nueva", sections)
        assertEquals(1, songDao.getSongCount())
    }

    private fun song(id: String) = SongEntity(
        id = id,
        title = "Canto $id",
        originalKey = "C",
        number = null,
        author = null,
        rhythm = null,
        rhythmPatternId = null,
        tempoBpm = null,
        tempoMarking = null,
        defaultLiturgicalMomentsJson = "[]",
        defaultLiturgicalTimesJson = "[]",
        createdAt = 1L,
        updatedAt = 1L,
        isFavorite = false,
        capo = 0,
        notes = null,
        contentHash = null
    )

    private fun sections(vararg songIds: String) = songIds.map { songId ->
        SongSectionEntity(
            id = "$songId-sec-1",
            songId = songId,
            sectionType = "VERSE",
            sequenceOrder = 0,
            rawTextContent = "Letra de $songId",
            headerTitle = null
        )
    }

    private fun anchors(vararg songIds: String) = songIds.map { songId ->
        ChordAnchorEntity(
            id = "$songId-anchor-1",
            sectionId = "$songId-sec-1",
            lineIndex = 0,
            characterIndex = 0,
            chordRoot = "C",
            chordQuality = "",
            chordBass = null,
            rawChordText = "C"
        )
    }
}
