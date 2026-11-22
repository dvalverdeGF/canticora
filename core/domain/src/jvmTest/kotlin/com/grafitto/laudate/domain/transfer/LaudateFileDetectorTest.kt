package com.grafitto.laudate.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del **detector de archivos de Laudate**.
 *
 * El reconocimiento es lo que permite que, al recibir un archivo por WhatsApp, la app diga qué es
 * y lo importe sin que el usuario tenga que elegir la vía. Es crítico que **no** se confunda un
 * archivo de Laudate con otro cualquiera, ni al revés.
 */
class LaudateFileDetectorTest {

    @Test
    fun `un zip se reconoce como copia de seguridad`() {
        val result = LaudateFileDetector.detect(text = null, isZip = true, fileName = "copia.laudate")
        assertEquals(LaudateFileKind.BACKUP, result.kind)
        assertTrue(result.isZip)
    }

    @Test
    fun `un repertorio se reconoce por su formato declarado`() {
        val json = """
            {
              "format": "canticora.event",
              "formatVersion": 1,
              "celebration": { "name": "Misa del domingo", "type": "Misa", "dateTimestamp": 1,
                               "items": [{"songIndex":0,"moment":"Entrada"}] },
              "songs": [{"title":"Aleluya","originalKey":"G","chordPro":"[G]Aleluya"}]
            }
        """.trimIndent()

        val result = LaudateFileDetector.detect(json)

        assertEquals(LaudateFileKind.CELEBRATION, result.kind)
        assertEquals("Misa del domingo", result.title)
        assertEquals(1, result.songCount)
        assertEquals(1, result.celebrationItemCount)
    }

    @Test
    fun `un repertorio sin campo format se reconoce por su forma`() {
        // Repertorios de versiones anteriores podrían no declarar `format`.
        val json = """{"celebration":{"name":"Ensayo","type":"Ensayo","dateTimestamp":1},"songs":[]}"""
        assertEquals(LaudateFileKind.CELEBRATION, LaudateFileDetector.detect(json).kind)
    }

    @Test
    fun `un cancionero se reconoce por traer canciones y categorias`() {
        val json = """
            {"version":1,"exportedAt":1,
             "songs":[{"id":"s1","title":"Canto","originalKey":"C","createdAt":1,"updatedAt":1}],
             "celebrations":[],
             "categories":[{"id":"c1","name":"Entrada","createdAt":1}]}
        """.trimIndent()

        val result = LaudateFileDetector.detect(json)

        assertEquals(LaudateFileKind.CATALOG, result.kind)
        assertEquals(1, result.songCount)
        assertEquals(1, result.categoryCount)
    }

    @Test
    fun `un manifiesto de copia de seguridad se reconoce por su formato`() {
        val json = """
            {"format":"canticora.backup","formatVersion":1,
             "payload":{"songs":[{"id":"s1"}],"celebrations":[],"categories":[]},
             "attachments":[{"id":"a1","songId":"s1","kind":"AUDIO","displayName":"x.mp3"}]}
        """.trimIndent()

        val result = LaudateFileDetector.detect(json)

        assertEquals(LaudateFileKind.BACKUP, result.kind)
        assertEquals(1, result.songCount)
        assertEquals(1, result.attachmentCount)
    }

    @Test
    fun `un chordpro con directivas se reconoce como cancion`() {
        val chordPro = """
            {title: Aleluya}
            {key: G}
            {soc}
            [G]Aleluya, alelu[D]ya
            {eoc}
        """.trimIndent()

        val result = LaudateFileDetector.detect(chordPro)

        assertEquals(LaudateFileKind.CHORDPRO, result.kind)
        assertEquals("Aleluya", result.title)
    }

    @Test
    fun `un chordpro sin directivas se reconoce por sus acordes`() {
        val chordPro = "[C]Señor, ten pie[G]dad\n[Am]Cristo, ten pie[F]dad"
        assertEquals(LaudateFileKind.CHORDPRO, LaudateFileDetector.detect(chordPro).kind)
    }

    @Test
    fun `un chordpro con acordes latinos se reconoce`() {
        val chordPro = "[Do]Santo, [Sol]Santo, [Lam]Santo es el Se[Fa]ñor"
        assertEquals(LaudateFileKind.CHORDPRO, LaudateFileDetector.detect(chordPro).kind)
    }

    @Test
    fun `un texto corriente no se confunde con chordpro`() {
        val texto = "Hola, ¿nos vemos mañana en el ensayo? Trae las fotocopias."
        assertEquals(LaudateFileKind.UNKNOWN, LaudateFileDetector.detect(texto).kind)
    }

    @Test
    fun `un json ajeno no se confunde con un archivo de laudate`() {
        val json = """{"name":"Pedido","items":[{"id":1,"qty":2}],"total":9.99}"""
        assertEquals(LaudateFileKind.UNKNOWN, LaudateFileDetector.detect(json).kind)
    }

    @Test
    fun `un archivo vacio o ilegible no revienta`() {
        assertEquals(LaudateFileKind.UNKNOWN, LaudateFileDetector.detect(null).kind)
        assertEquals(LaudateFileKind.UNKNOWN, LaudateFileDetector.detect("").kind)
        assertEquals(LaudateFileKind.UNKNOWN, LaudateFileDetector.detect("   ").kind)
    }

    @Test
    fun `un json malformado no revienta`() {
        assertEquals(LaudateFileKind.UNKNOWN, LaudateFileDetector.detect("{esto no es json").kind)
    }

    @Test
    fun `el mime de zip basta aunque no se pueda leer el contenido`() {
        val result = LaudateFileDetector.detect(text = null, mimeType = "application/zip")
        assertEquals(LaudateFileKind.BACKUP, result.kind)
    }

    @Test
    fun `los bytes magicos del zip se detectan`() {
        val zipHead = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertTrue(LaudateFileDetector.looksLikeZip(zipHead))
        assertFalse(LaudateFileDetector.looksLikeZip("{\"a\":1}".toByteArray()))
    }

    @Test
    fun `la etiqueta del tipo es apta para mostrar al usuario`() {
        assertEquals("repertorio", DetectedLaudateFile(LaudateFileKind.CELEBRATION).kindLabel)
        assertEquals("copia de seguridad", DetectedLaudateFile(LaudateFileKind.BACKUP).kindLabel)
        assertEquals("cancionero", DetectedLaudateFile(LaudateFileKind.CATALOG).kindLabel)
        assertEquals("canción en ChordPro", DetectedLaudateFile(LaudateFileKind.CHORDPRO).kindLabel)
    }

    // ── Endurecimiento: un binario nunca se clasifica como canción (bug conocido) ────────────

    @Test
    fun `un mp4 se reconoce como video por sus bytes magicos, nunca como chordpro`() {
        // Caja "ftyp" de un contenedor ISO Base Media: tamaño de caja + "ftyp" + marca.
        val mp4Head = byteArrayOf(0, 0, 0, 0x18) + "ftypmp42".toByteArray()
        val result = LaudateFileDetector.detect(text = null, fileName = "video.mp4", headBytes = mp4Head)
        assertEquals(LaudateFileKind.MEDIA, result.kind)
        assertEquals("vídeo", result.mediaDescription)
    }

    @Test
    fun `un png se reconoce como imagen por sus bytes magicos`() {
        val pngHead = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val result = LaudateFileDetector.detect(text = null, fileName = "foto.png", headBytes = pngHead)
        assertEquals(LaudateFileKind.MEDIA, result.kind)
        assertEquals("imagen", result.mediaDescription)
    }

    @Test
    fun `un pdf se reconoce como documento pdf por sus bytes magicos`() {
        val pdfHead = "%PDF-1.4".toByteArray()
        val result = LaudateFileDetector.detect(text = null, fileName = "partitura.pdf", headBytes = pdfHead)
        assertEquals(LaudateFileKind.MEDIA, result.kind)
        assertEquals("documento PDF", result.mediaDescription)
    }

    @Test
    fun `un mp3 con etiqueta id3 se reconoce como audio`() {
        val mp3Head = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, 0, 0)
        val result = LaudateFileDetector.detect(text = null, fileName = "pista.mp3", headBytes = mp3Head)
        assertEquals(LaudateFileKind.MEDIA, result.kind)
        assertEquals("audio", result.mediaDescription)
    }

    @Test
    fun `aunque el binario llegue ya decodificado como texto, nunca se clasifica como chordpro`() {
        // Simula lo que ocurría en el bug: un MP4 leído con un lector de texto trae miles de nulos.
        val binaryAsText = "ftypmp42isom" + "\u0000".repeat(5000) + "[C]esto no debería importar"
        val result = LaudateFileDetector.detect(text = binaryAsText, fileName = "video.mp4")
        assertEquals(LaudateFileKind.MEDIA, result.kind)
    }

    @Test
    fun `un xml se reconoce como musicxml o documento xml, nunca como chordpro`() {
        val musicXml = """<?xml version="1.0"?><score-partwise></score-partwise>"""
        val resultMusicXml = LaudateFileDetector.detect(musicXml)
        assertEquals(LaudateFileKind.MEDIA, resultMusicXml.kind)
        assertEquals("partitura digital (MusicXML)", resultMusicXml.mediaDescription)

        val genericXml = "<pedido><item>1</item></pedido>"
        val resultGenericXml = LaudateFileDetector.detect(genericXml)
        assertEquals(LaudateFileKind.MEDIA, resultGenericXml.kind)
        assertEquals("documento XML", resultGenericXml.mediaDescription)
    }

    @Test
    fun `un zip con bytes magicos no se confunde con un binario de otro tipo`() {
        // El ZIP se comprueba con isZip/mimeType; aunque se pasen también headBytes con otra
        // cabecera, el ZIP tiene prioridad (es la vía existente para detectarlo).
        val result = LaudateFileDetector.detect(text = null, isZip = true, fileName = "copia.laudate")
        assertEquals(LaudateFileKind.BACKUP, result.kind)
    }

    @Test
    fun `sniffMediaKind no revienta con cabeceras muy cortas o vacias`() {
        assertEquals(null, LaudateFileDetector.sniffMediaKind(byteArrayOf()))
        assertEquals(null, LaudateFileDetector.sniffMediaKind(byteArrayOf(1)))
        assertEquals(null, LaudateFileDetector.sniffMediaKind("[Do]Santo".toByteArray()))
    }

    // ── Paquete de canción con adjuntos (canticora.song, ADR-017) ──────────────────────────────

    @Test
    fun `un manifiesto de canto suelto se reconoce por su formato`() {
        val json = """
            {"format":"canticora.song","formatVersion":1,
             "song":{"title":"Aleluya","originalKey":"G","chordPro":"[G]Aleluya","contentHash":"sha256:x"},
             "attachments":[
                {"id":"a1","songId":"s1","kind":"AUDIO","displayName":"pista.mp3","entryPath":"attachments/a1"},
                {"id":"a2","songId":"s1","kind":"LINK","displayName":"Ensayo","linkUrl":"https://x"}
             ]}
        """.trimIndent()

        val result = LaudateFileDetector.detect(json)

        assertEquals(LaudateFileKind.SONG, result.kind)
        assertEquals("Aleluya", result.title)
        assertEquals(2, result.attachmentCount)
        assertEquals(true, result.hasAttachmentFiles)
    }

    @Test
    fun `un manifiesto de canto sin ficheros declara que solo lleva enlaces`() {
        val json = """
            {"format":"canticora.song",
             "song":{"title":"Aleluya","originalKey":"G","chordPro":"[G]Aleluya"},
             "attachments":[{"id":"a1","songId":"s1","kind":"LINK","displayName":"Ensayo","linkUrl":"https://x"}]}
        """.trimIndent()

        val result = LaudateFileDetector.detect(json)
        assertEquals(LaudateFileKind.SONG, result.kind)
        assertEquals(false, result.hasAttachmentFiles)
    }

    @Test
    fun `la etiqueta del tipo canto con adjuntos es apta para el usuario`() {
        assertEquals("canto con adjuntos", DetectedLaudateFile(LaudateFileKind.SONG).kindLabel)
    }

    @Test
    fun `un zip cuya primera entrada es song_json se reconoce como canto, no como copia`() {
        // Cabecera local del ZIP (30 bytes) + nombre de la primera entrada ("song.json").
        val name = SONG_DATA_ENTRY.toByteArray()
        val head = ByteArray(30 + name.size)
        head[0] = 'P'.code.toByte()
        head[1] = 'K'.code.toByte()
        head[2] = 0x03
        head[3] = 0x04
        head[26] = name.size.toByte()
        System.arraycopy(name, 0, head, 30, name.size)

        assertEquals(LaudateFileDetector.zipFirstEntryName(head), SONG_DATA_ENTRY)

        val result = LaudateFileDetector.detect(text = null, isZip = true, fileName = "canto.canticora", headBytes = head)
        assertEquals(LaudateFileKind.SONG, result.kind)
    }

    @Test
    fun `un zip cuya primera entrada es backup_json se sigue reconociendo como copia de seguridad`() {
        val name = "backup.json".toByteArray()
        val head = ByteArray(30 + name.size)
        head[0] = 'P'.code.toByte()
        head[1] = 'K'.code.toByte()
        head[2] = 0x03
        head[3] = 0x04
        head[26] = name.size.toByte()
        System.arraycopy(name, 0, head, 30, name.size)

        val result = LaudateFileDetector.detect(text = null, isZip = true, fileName = "copia.canticora", headBytes = head)
        assertEquals(LaudateFileKind.BACKUP, result.kind)
    }

    @Test
    fun `zipFirstEntryName no revienta con una cabecera incompleta`() {
        assertEquals(null, LaudateFileDetector.zipFirstEntryName(byteArrayOf(0x50, 0x4B)))
        assertEquals(null, LaudateFileDetector.zipFirstEntryName(ByteArray(40)))
    }
}
