package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la lógica pura de los adjuntos: deducción de tipo y tamaño legible. */
class SongAttachmentTest {

    @Test
    fun `la deducción del tipo usa el MIME`() {
        assertEquals(AttachmentKind.AUDIO, AttachmentKind.fromMimeType("audio/mpeg"))
        assertEquals(AttachmentKind.VIDEO, AttachmentKind.fromMimeType("video/mp4"))
        assertEquals(AttachmentKind.IMAGE, AttachmentKind.fromMimeType("image/jpeg"))
        assertEquals(AttachmentKind.DOCUMENT, AttachmentKind.fromMimeType("application/pdf"))
        // Sin MIME conocido se trata como documento (lo más seguro: se copia el fichero).
        assertEquals(AttachmentKind.DOCUMENT, AttachmentKind.fromMimeType(null))
    }

    @Test
    fun `un enlace no tiene tamaño legible`() {
        val link = SongAttachment(
            id = "a1",
            songId = "s1",
            kind = AttachmentKind.LINK,
            displayName = "Ensayo en YouTube",
            uri = "https://youtube.com/watch?v=abc"
        )
        assertTrue(link.isLink)
        assertNull(link.readableSize())
    }

    @Test
    fun `el tamaño se muestra en unidades legibles`() {
        assertTrue(attachmentOf(512).readableSize()!!.endsWith("B"))
        assertTrue(attachmentOf(2048).readableSize()!!.contains("KB"))
        assertTrue(attachmentOf(3 * 1024 * 1024).readableSize()!!.contains("MB"))
    }

    private fun attachmentOf(bytes: Long) = SongAttachment(
        id = "a",
        songId = "s",
        kind = AttachmentKind.AUDIO,
        displayName = "pista.mp3",
        uri = "/tmp/pista.mp3",
        sizeBytes = bytes
    )
}
