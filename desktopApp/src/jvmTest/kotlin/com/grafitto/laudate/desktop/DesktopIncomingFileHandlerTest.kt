package com.grafitto.laudate.desktop

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.transfer.CelebrationBundleSerializer
import com.grafitto.laudate.domain.transfer.LaudateFileKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

/**
 * Verifica la **asociación de ficheros `.laudate`** en escritorio: un fichero de repertorio recibido
 * (doble clic / «Abrir con») se detecta y se importa de verdad. Ver ADR-013.
 */
class DesktopIncomingFileHandlerTest {

    @Test
    fun `detecta e importa un repertorio laudate`() = runTest {
        val song = ChordProParser.parse(
            chordProContent = "{title: Aleluya}\n{key: D}\n{soc}\n[D]Aleluya\n{eoc}",
            songId = "s1",
            fallbackTitle = "Aleluya"
        )
        val celebration = Celebration(
            id = "c1",
            name = "Misa del domingo",
            dateTimestamp = 0L,
            items = listOf(
                CelebrationItem(
                    id = "i1",
                    celebrationId = "c1",
                    songId = song.id,
                    liturgicalMoment = "Entrada",
                    displayOrder = 0
                )
            )
        )
        val bundle = CelebrationBundleSerializer.toBundle(
            celebration = celebration,
            songsById = mapOf(song.id to song),
            appVersion = "test"
        )
        val file = Files.createTempFile("laudate-test", ".laudate").toFile()
        file.writeText(CelebrationBundleSerializer.serialize(bundle))

        val container = DesktopContainer(dataDirectory = Files.createTempDirectory("laudate-test-db").toFile())
        try {
            val handler = DesktopIncomingFileHandler(container)

            // Comprobación de aislamiento: la base temporal debe empezar vacía.
            assertEquals("La BD temporal empieza vacía", 0, container.songRepository.getSongCount())

            val detected = handler.detect(file)
            assertNotNull("Se detecta el fichero", detected)
            assertEquals(LaudateFileKind.CELEBRATION, detected!!.kind)
            assertEquals(true, (detected.celebrationItemCount ?: 0) >= 1)

            val message = handler.import(file)
            assertEquals(
                "La BD temporal debe estar aislada",
                1,
                container.songRepository.getSongCount()
            )
            assertEquals(1, container.celebrationRepository.getAllCelebrations().first().size)
            assert(message.contains("Misa del domingo"))
        } finally {
            container.close()
            file.delete()
            container.dataDirectory.deleteRecursively()
        }
    }

    @Test
    fun `un video recibido se reconoce como media, nunca se importa como cancion`() = runTest {
        val file = Files.createTempFile("laudate-test", ".mp4").toFile()
        // Caja "ftyp" de un MP4 real, seguida de relleno binario con nulos (como el bug original).
        file.writeBytes(byteArrayOf(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(2000))

        val container = DesktopContainer(dataDirectory = Files.createTempDirectory("laudate-test-db").toFile())
        try {
            val handler = DesktopIncomingFileHandler(container)

            val detected = handler.detect(file)
            assertNotNull("Se detecta el fichero como multimedia", detected)
            assertEquals(LaudateFileKind.MEDIA, detected!!.kind)
            assertEquals("vídeo", detected.mediaDescription)

            val message = handler.import(file)
            assert(message.contains("vídeo"))
            assertEquals(
                "Un vídeo nunca debe crear una canción",
                0,
                container.songRepository.getSongCount()
            )
        } finally {
            container.close()
            file.delete()
            container.dataDirectory.deleteRecursively()
        }
    }

    @Test
    fun `detecta e importa un canto con adjuntos y lo distingue de una copia`() = runTest {
        val container = DesktopContainer(dataDirectory = Files.createTempDirectory("laudate-test-db").toFile())
        try {
            val song = ChordProParser.parse(
                chordProContent = "{title: Aleluya}\n{key: D}\n[D]Aleluya",
                songId = "s1",
                fallbackTitle = "Aleluya"
            )
            container.songRepository.saveSong(song)

            val dir = Files.createTempDirectory("laudate-test-pkg").toFile()
            val file = java.io.File(dir, "aleluya.canticora")
            // Se empaqueta el canto recién guardado; la detección debe reconocerlo como paquete
            // (`song.json`) y no como copia de seguridad (`backup.json`).
            container.exportSongPackageUseCase(
                destinationUri = file.absolutePath,
                songId = song.id
            )

            val handler = DesktopIncomingFileHandler(container)
            val detected = handler.detect(file)
            assertNotNull("Se detecta el paquete del canto", detected)
            assertEquals(LaudateFileKind.SONG, detected!!.kind)

            val message = handler.import(file)
            assert(message.contains("Aleluya"))
        } finally {
            container.close()
            container.dataDirectory.deleteRecursively()
        }
    }
}

