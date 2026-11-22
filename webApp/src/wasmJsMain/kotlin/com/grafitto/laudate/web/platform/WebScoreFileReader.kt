package com.grafitto.laudate.web.platform

import com.grafitto.laudate.domain.transfer.ScoreFileReader

/**
 * Lectura de **partituras** en el navegador: MusicXML en texto y los contenedores ZIP
 * (`.mxl`, `.mscz`, `.gp`/`.gpx`, `.tg`).
 *
 * El fichero elegido en el selector se queda en memoria con un URI `mem://…`; aquí se abre y se
 * busca la entrada interna que pide el importador de tablaturas.
 */
class WebScoreFileReader : ScoreFileReader {

    override suspend fun readText(fileUri: String): String? =
        WebArchiveRegistry.read(fileUri)?.text

    override suspend fun readArchiveEntry(fileUri: String, entryName: String): String? =
        WebArchiveRegistry.read(fileUri)?.textOf(entryName)

    override suspend fun readArchiveEntryEndingWith(fileUri: String, suffix: String): String? =
        WebArchiveRegistry.read(fileUri)?.textEndingWith(suffix)
}
