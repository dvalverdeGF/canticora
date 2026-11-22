package com.grafitto.laudate.data.local

import com.grafitto.laudate.data.local.entity.SongEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * Verifica que la **base de datos de escritorio** (JVM) abre con el esquema real de Room KMP y
 * acepta operaciones. Es la prueba de que el stack de datos compartido funciona fuera de Android,
 * habilitando `:desktopApp`. Ver ADR-013.
 */
class DesktopDatabaseTest {

    @Test
    fun `abre la base de escritorio con el esquema real e inserta un canto`() = runTest {
        val file = Files.createTempFile("laudate-desktop", ".db").toFile()
        file.delete() // crear la base desde cero (instalación nueva)

        val db = buildDesktopDatabase(file.absolutePath)
        val dao = db.songDao()

        assertEquals(0, dao.getSongCount())

        dao.insertSong(
            SongEntity(id = "1", title = "Aleluya", originalKey = "D", author = "Haendel", rhythm = null)
        )
        assertEquals(1, dao.getSongCount())

        db.close()
    }
}
