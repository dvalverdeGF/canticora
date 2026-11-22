package com.grafitto.laudate.data.local

import com.grafitto.laudate.data.repository.TablatureRepositoryImpl
import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Pruebas del repositorio de tablaturas sobre el esquema real de Room (JVM). */
class TablatureRepositoryImplTest {

    private fun tempDb(): LaudateDatabase {
        val file = Files.createTempFile("laudate-tab", ".db").toFile()
        file.delete()
        return buildDesktopDatabase(file.absolutePath)
    }

    /** Inserta el canto al que pertenecen las tablaturas (FK CASCADE). */
    private suspend fun seedSong(db: LaudateDatabase, id: String) {
        db.songDao().insertSong(
            com.grafitto.laudate.data.local.entity.SongEntity(
                id = id, title = "Canto $id", originalKey = "C", author = null, rhythm = null
            )
        )
    }

    private fun tab(id: String, songId: String, instrument: String = "Guitarra"): Tablature = Tablature(
        id = id,
        songId = songId,
        title = "Riff $id",
        instrument = instrument,
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.EIGHTH,
        origin = TabOrigin.CREATED,
        writtenKey = ScoreKey(0, ScoreKey.MAJOR),
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(6, 0))),
                    TabSlot(strings = listOf(TabPluck(5, 2)))
                )
            )
        )
    )

    @Test
    fun `guardar una tablatura la persiste con todos sus campos`() = runTest {
        val db = tempDb()
        seedSong(db, "s1")
        val repo = TablatureRepositoryImpl(db.tablatureDao())

        repo.saveTablature(tab("t1", "s1"))

        val found = repo.getTablatureById("t1")
        assertNotNull(found)
        assertEquals("Riff t1", found!!.title)
        assertEquals(Tuning.GUITAR, found.tuning)
        assertEquals(1, found.measures.size)
        assertEquals(2, found.measures[0].slots.size)
        assertEquals(0, found.measures[0].slots[0].strings[0].fret)

        db.close()
    }

    @Test
    fun `un canto puede tener varias tablaturas`() = runTest {
        val db = tempDb()
        seedSong(db, "s1")
        val repo = TablatureRepositoryImpl(db.tablatureDao())

        repo.saveTablature(tab("t1", "s1", instrument = "Guitarra"))
        repo.saveTablature(tab("t2", "s1", instrument = "Bajo (4 cuerdas)"))

        val forSong = repo.observeTablaturesForSong("s1").first()
        assertEquals(2, forSong.size)
        assertTrue(forSong.any { it.instrument == "Guitarra" })
        assertTrue(forSong.any { it.instrument == "Bajo (4 cuerdas)" })

        db.close()
    }

    @Test
    fun `actualizar conserva la fecha de creacion`() = runTest {
        val db = tempDb()
        seedSong(db, "s1")
        val repo = TablatureRepositoryImpl(db.tablatureDao())

        repo.saveTablature(tab("t1", "s1"))
        val createdAt = db.tablatureDao().getTablatureById("t1")!!.createdAt

        repo.saveTablature(tab("t1", "s1").copy(title = "Otro título"))
        val after = db.tablatureDao().getTablatureById("t1")!!
        assertEquals(createdAt, after.createdAt)

        db.close()
    }

    @Test
    fun `borrar una tablatura la elimina`() = runTest {
        val db = tempDb()
        seedSong(db, "s1")
        val repo = TablatureRepositoryImpl(db.tablatureDao())

        repo.saveTablature(tab("t1", "s1"))
        repo.deleteTablature("t1")
        assertNull(repo.getTablatureById("t1"))

        db.close()
    }

    @Test
    fun `borrar el canto borra sus tablaturas por cascada`() = runTest {
        val db = tempDb()
        seedSong(db, "s1")
        val repo = TablatureRepositoryImpl(db.tablatureDao())

        repo.saveTablature(tab("t1", "s1"))
        db.songDao().deleteAllSongs()

        assertNull(repo.getTablatureById("t1"))

        db.close()
    }
}
