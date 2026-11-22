package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.backup.BACKUP_DATA_ENTRY
import com.grafitto.laudate.domain.backup.BackupAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la serialización del manifiesto del paquete de canción (`canticora.song`, ADR-017). */
class SongPackageTest {

    private fun samplePackage(): SongPackage = SongPackage(
        exportedAt = 1L,
        appVersion = "1.0.24",
        song = SongPackageSong(
            title = "Aleluya",
            number = "402",
            author = "Kairoi",
            originalKey = "G",
            contentHash = "sha256:abc",
            defaultLiturgicalMoments = listOf("Aleluya / Aclamación"),
            chordPro = "{title: Aleluya}\n[G]Aleluya"
        ),
        attachments = listOf(
            BackupAttachment(
                id = "a1",
                songId = "s1",
                kind = "AUDIO",
                displayName = "pista.mp3",
                entryPath = "attachments/a1"
            ),
            BackupAttachment(
                id = "a2",
                songId = "s1",
                kind = "LINK",
                displayName = "Ensayo",
                linkUrl = "https://youtube.com/x"
            )
        )
    )

    @Test
    fun `el manifiesto sobrevive a un round trip`() {
        val original = samplePackage()
        val decoded = SongPackageSerializer.decode(SongPackageSerializer.encode(original))

        assertEquals(original, decoded)
        assertEquals(CANTICORA_SONG_FORMAT, decoded.format)
        assertEquals("Aleluya", decoded.song.title)
        assertEquals(2, decoded.attachments.size)
    }

    @Test
    fun `un formato ajeno no se acepta como paquete de cancion`() {
        val json = """{"format":"canticora.backup","song":{"title":"x","originalKey":"C","chordPro":""}}"""
        assertFalse(SongPackageSerializer.looksLikeSongPackage(json))
        val error = runCatching { SongPackageSerializer.decode(json) }.exceptionOrNull()
        assertEquals(true, error?.message?.contains("otro formato") == true)
    }

    @Test
    fun `una version de formato mas reciente se rechaza con un mensaje claro`() {
        val json = """
            {"format":"canticora.song","formatVersion":99,
             "song":{"title":"x","originalKey":"C","chordPro":"[C]x"}}
        """.trimIndent()
        val error = runCatching { SongPackageSerializer.decode(json) }.exceptionOrNull()
        assertEquals(true, error?.message?.contains("versión más reciente") == true)
    }

    @Test
    fun `un texto que no es json no se reconoce como paquete`() {
        assertFalse(SongPackageSerializer.looksLikeSongPackage("[C]Santo"))
        assertTrue(runCatching { SongPackageSerializer.decode("[C]x") }.isFailure)
    }

    @Test
    fun `el json crudo incluye el formato declarado`() {
        val raw = SongPackageSerializer.encode(samplePackage())
        assertTrue(raw.contains(CANTICORA_SONG_FORMAT))
        assertTrue(raw.contains("\"song\""))
    }

    @Test
    fun `la constante del manifiesto es distinta de la de la copia de seguridad`() {
        assertEquals("song.json", SONG_DATA_ENTRY)
        assertFalse(SONG_DATA_ENTRY == BACKUP_DATA_ENTRY)
    }
}
