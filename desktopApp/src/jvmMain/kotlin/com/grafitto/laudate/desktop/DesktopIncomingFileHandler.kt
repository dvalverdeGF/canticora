package com.grafitto.laudate.desktop

import com.grafitto.laudate.domain.transfer.CelebrationBundleSerializer
import com.grafitto.laudate.domain.transfer.DetectedLaudateFile
import com.grafitto.laudate.domain.transfer.LaudateFileDetector
import com.grafitto.laudate.domain.transfer.LaudateFileKind
import com.grafitto.laudate.domain.usecase.ImportBackupUseCase
import com.grafitto.laudate.domain.usecase.ImportCatalogBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportCelebrationBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportChordProSongsUseCase
import com.grafitto.laudate.domain.usecase.ImportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.ChordProFile
import java.io.File

/**
 * Importa un fichero `.canticora` **recibido desde fuera** (doble clic, «Abrir con»): detecta qué
 * contiene con [LaudateFileDetector] y lo importa con el caso de uso correspondiente.
 *
 * Es el equivalente de escritorio del «archivo recibido» de Android (deep link `ACTION_VIEW`),
 * pero aquí el origen es una ruta de fichero normal. Ver ADR-013.
 */
class DesktopIncomingFileHandler(private val container: DesktopContainer) {

    /**
     * Analiza [file] (sin importarlo) para poder mostrar una confirmación.
     * Devuelve `null` si no se reconoce como archivo de Canticora.
     */
    fun detect(file: File): DetectedLaudateFile? {
        val head = runCatching { file.inputStream().use { it.readNBytes(HEAD_SAMPLE_SIZE) } }.getOrNull()
            ?: return null
        val isZip = LaudateFileDetector.looksLikeZip(head)
        // Un binario reconocido (vídeo, audio, imagen, PDF…) ni se lee como texto: ver bug del MP4
        // detectado como ChordPro. Solo se decodifica el archivo entero si la cabecera no es de un
        // binario conocido.
        val text = if (isZip || LaudateFileDetector.sniffMediaKind(head) != null) {
            null
        } else {
            runCatching { LaudateFileDetector.stripBom(file.readText()) }.getOrNull()
        }
        val detected = LaudateFileDetector.detect(
            text = text,
            mimeType = null,
            fileName = file.name,
            isZip = isZip,
            headBytes = head
        )
        return detected.takeIf { it.kind != LaudateFileKind.UNKNOWN }
    }

    /** Importa [file] y devuelve un mensaje legible para el usuario. */
    suspend fun import(file: File): String {
        val detected = detect(file) ?: return "El archivo no es de Canticora"

        return when (detected.kind) {
            LaudateFileKind.BACKUP -> {
                val result = container.importBackupUseCase(file.absolutePath)
                "Copia restaurada: ${result.songsImported} cantos, " +
                    "${result.celebrationsImported} repertorios"
            }

            LaudateFileKind.CELEBRATION -> {
                val bundle = CelebrationBundleSerializer.deserialize(content(file, detected))
                val result = container.importCelebrationBundleUseCase(bundle)
                "Repertorio importado: ${result.celebrationName}"
            }

            LaudateFileKind.CATALOG -> {
                val result = container.importCatalogBundleUseCase(content(file, detected))
                "Canciones importadas: ${result.songsImported}"
            }

            LaudateFileKind.RHYTHMS -> {
                // Acepta tanto el JSON de ritmos como la notación de texto pegable.
                val result = container.importRhythmPatternsUseCase.import(content(file, detected))
                if (result == null) "El archivo de ritmos no se pudo leer" else "Ritmos importados: ${result.created}"
            }

            LaudateFileKind.CHORDPRO -> {
                val result = container.importChordProSongsUseCase(
                    listOf(ChordProFile(content = content(file, detected), fileName = file.name))
                )
                "Canción importada: ${result.total} procesada(s)"
            }

            LaudateFileKind.SONG -> {
                val result = container.importSongPackageUseCase(file.absolutePath)
                val detail = buildList {
                    add(if (result.identical) "ya estaba" else if (result.created) "añadido" else "actualizado")
                    if (result.attachmentsImported > 0) add("${result.attachmentsImported} adjuntos")
                }
                "«${result.title}»: ${detail.joinToString(" · ")}"
            }

            LaudateFileKind.MEDIA -> {
                val description = detected.mediaDescription ?: "archivo"
                "Esto es un $description, no una canción. Añádelo como adjunto desde la pantalla " +
                    "de una canción."
            }

            LaudateFileKind.UNKNOWN -> "El archivo no es de Canticora"
        }
    }

    /** Contenido del fichero sin BOM (usa el detectado si lo hay). */
    private fun content(file: File, detected: DetectedLaudateFile): String =
        detected.rawText ?: LaudateFileDetector.stripBom(file.readText())

    private companion object {
        /** Bytes que se leen para reconocer el tipo de archivo, nunca el archivo entero. */
        const val HEAD_SAMPLE_SIZE = 4096
    }
}
