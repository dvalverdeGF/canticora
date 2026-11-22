package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del manifiesto de copia de seguridad completa (Kotlin puro, sin Android). */
class FullBackupTest {

    @Test
    fun `roundtrip del manifiesto conserva canciones, repertorios y adjuntos`() {
        val song = songOf("Aleluya", "[C]Aleluya")
        val backup = FullBackup(
            appVersion = "1.0.4",
            payload = BackupSerializer.toPayload(listOf(song), emptyList(), emptyList()),
            attachments = listOf(
                BackupAttachment(
                    id = "att-1",
                    songId = song.id,
                    kind = "AUDIO",
                    displayName = "ensayo.mp3",
                    mimeType = "audio/mpeg",
                    sizeBytes = 1024,
                    entryPath = backupAttachmentEntryPath("att-1")
                )
            )
        )

        val decoded = FullBackupSerializer.decode(FullBackupSerializer.encode(backup))

        assertEquals("canticora.backup", decoded.format)
        assertEquals(1, decoded.payload.songs.size)
        assertEquals("Aleluya", decoded.payload.songs.single().title)
        assertEquals(1, decoded.attachments.size)
        assertEquals("attachments/att-1", decoded.attachments.single().entryPath)
    }

    @Test
    fun `un texto que no es copia de seguridad no se reconoce`() {
        assertFalse(FullBackupSerializer.looksLikeBackup("{\"format\":\"otra.cosa\"}"))
        assertFalse(FullBackupSerializer.looksLikeBackup("no es json"))
    }

    @Test
    fun `el reconocimiento acepta una copia válida`() {
        val backup = FullBackup(
            payload = BackupSerializer.toPayload(emptyList(), emptyList(), emptyList())
        )
        assertTrue(FullBackupSerializer.looksLikeBackup(FullBackupSerializer.encode(backup)))
    }

    @Test
    fun `el mapa de celebraciones reconstruye sus elementos`() {
        val song = songOf("Gloria", "[G]Gloria")
        val payload = BackupSerializer.toPayload(
            songs = listOf(song),
            celebrations = listOf(
                Celebration(
                    id = "cel-1",
                    name = "Misa",
                    dateTimestamp = 10L,
                    celebrationType = "Misa",
                    items = listOf(
                        com.grafitto.laudate.domain.model.celebration.CelebrationItem(
                            id = "i1",
                            celebrationId = "cel-1",
                            songId = song.id,
                            liturgicalMoment = "Gloria",
                            displayOrder = 0
                        )
                    )
                )
            ),
            categories = listOf(LiturgicalCategory(id = "c1", name = "Gloria", kind = LiturgicalCategoryKind.MASS_MOMENT))
        )

        val celebration = payload.celebrations.single().toDomain()
        assertEquals("Misa", celebration.celebrationType)
        assertEquals("Gloria", celebration.items.single().liturgicalMoment)
        assertEquals(LiturgicalCategoryKind.MASS_MOMENT, payload.categories.single().toDomain().kind)
    }

    private fun songOf(title: String, lyrics: String): Song = ChordProParser.parse(
        chordProContent = "{title: $title}\n$lyrics",
        songId = "song-$title",
        fallbackTitle = title
    )
}

/**
 * El **nombre de archivo** de la copia de seguridad es **uno solo** para todas las plataformas.
 *
 * Android y escritorio lo componían por su cuenta y se desincronizaron: la app pedía
 * `canticora_copia_<fecha>.canticora` y el escritorio seguía con el literal
 * `laudate_copia.laudate`, así que los ficheros de Windows aparecían con la marca y la extensión
 * **antiguas**. Esta prueba fija el formato para que la duplicación no vuelva.
 */
class BackupFileNameTest {

    @Test
    fun `el nombre usa la marca, la fecha y la extension de Canticora`() {
        assertEquals(
            "canticora_copia_2026-10-30.canticora",
            canticoraBackupFileName("2026-10-30")
        )
    }

    @Test
    fun `no queda rastro de la marca anterior`() {
        val name = canticoraBackupFileName("2026-10-30")
        assertFalse("El nombre no debe contener «laudate»: $name", name.contains("laudate"))
        assertFalse("El nombre no debe acabar en «.laudate»: $name", name.endsWith(".laudate"))
    }

    @Test
    fun `la extension coincide con la constante del formato`() {
        assertTrue(canticoraBackupFileName("2026-01-01").endsWith(".$CANTICORA_BACKUP_EXTENSION"))
    }
}
