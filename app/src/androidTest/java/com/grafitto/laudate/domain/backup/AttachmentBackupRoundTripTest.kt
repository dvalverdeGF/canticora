package com.grafitto.laudate.domain.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica que los **adjuntos** de un canto entran en la copia de seguridad completa y que los
 * enlaces se reconstruyen a partir de su URL.
 *
 * Es la garantía que pidió el usuario: «añadir audios a mis canciones aunque no se puedan compartir
 * pero sí guardar en mis copias de seguridad».
 */
@RunWith(AndroidJUnit4::class)
class AttachmentBackupRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun fileAttachmentIsStoredInTheManifestWithItsEntryPath() {
        val payload = BackupPayload(
            version = 1,
            exportedAt = 1L,
            songs = emptyList(),
            celebrations = emptyList(),
            categories = emptyList()
        )
        val entries = listOf(
            BackupAttachment(
                id = "a1",
                songId = "s1",
                kind = AttachmentKind.AUDIO.name,
                displayName = "ensayo.mp3",
                mimeType = "audio/mpeg",
                sizeBytes = 2048L,
                createdAt = 1L,
                entryPath = backupAttachmentEntryPath("a1"),
                linkUrl = null
            )
        )
        val backup = FullBackup(
            format = CANTICORA_BACKUP_FORMAT,
            payload = payload,
            attachments = entries
        )

        val encoded = FullBackupSerializer.encode(backup)
        val decoded = FullBackupSerializer.decode(encoded)

        assertEquals(CANTICORA_BACKUP_FORMAT, decoded.format)
        assertEquals(1, decoded.attachments.size)
        val restored = decoded.attachments.first()
        assertEquals("ensayo.mp3", restored.displayName)
        assertEquals("a1", restored.id)
        assertEquals("s1", restored.songId)
        assertTrue(restored.entryPath!!.startsWith("attachments/"))
    }

    @Test
    fun linkIsStoredWithItsUrlAndWithoutFileInTheZip() {
        val payload = BackupPayload(
            version = 1,
            exportedAt = 1L,
            songs = emptyList(),
            celebrations = emptyList(),
            categories = emptyList()
        )
        val backup = FullBackup(
            format = CANTICORA_BACKUP_FORMAT,
            payload = payload,
            attachments = listOf(
                BackupAttachment(
                    id = "l1",
                    songId = "s1",
                    kind = AttachmentKind.LINK.name,
                    displayName = "Ensayo en YouTube",
                    mimeType = "text/uri-list",
                    sizeBytes = null,
                    createdAt = 2L,
                    entryPath = null,
                    linkUrl = "https://youtu.be/ensayo-demo"
                )
            )
        )

        val decoded = FullBackupSerializer.decode(FullBackupSerializer.encode(backup))
        val link = decoded.attachments.single()

        // Un enlace no viaja como fichero: solo se conserva la URL para recrearlo.
        assertEquals(null, link.entryPath)
        assertEquals("https://youtu.be/ensayo-demo", link.linkUrl)
        assertEquals(AttachmentKind.LINK, AttachmentKind.fromName(link.kind))
    }

    @Test
    fun domainModelKnowsALinkHasNoLocalFile() {
        val link = SongAttachment(
            id = "l1",
            songId = "s1",
            kind = AttachmentKind.LINK,
            displayName = "Ensayo",
            uri = "https://youtu.be/x",
            mimeType = "text/uri-list"
        )
        assertTrue(link.isLink)
        assertEquals(null, link.readableSize())
    }
}
