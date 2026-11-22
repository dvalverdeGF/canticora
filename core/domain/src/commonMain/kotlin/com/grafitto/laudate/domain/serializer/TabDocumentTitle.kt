package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.transfer.TablatureImportFormat

/**
 * Título que **declara el propio fichero** de partitura.
 *
 * Cuando el usuario importa una partitura desde un fichero, lo natural es quedarse con el título que
 * el documento trae escrito (`<work-title>` en MusicXML, `<Title>` en Guitar Pro, `<title>` en
 * TuxGuitar, `<workTitle>` en MuseScore). La plataforma solo conoce el **nombre del fichero**, que
 * casi nunca coincide, así que esta lectura se hace en el dominio y con el mismo criterio para todos
 * los formatos.
 *
 * Es deliberadamente tolerante: si el documento no declara título devuelve `null` y quien llama
 * decide el sustituto.
 */
internal object TabDocumentTitle {

    /** Primer título declarado por el documento de [format], o `null` si no trae ninguno. */
    fun resolve(format: TablatureImportFormat, xml: String): String? = when (format) {
        TablatureImportFormat.MUSICXML -> firstOf(xml, "work-title", "movement-title", "credit-words")
        TablatureImportFormat.MSCZ -> firstOf(xml, "workTitle", "movementTitle")
            ?: metaTagTitle(xml)
        TablatureImportFormat.GPIF -> firstOf(xml, "Title", "SubTitle")
        TablatureImportFormat.TUXGUITAR -> firstOf(xml, "title", "artist")
        TablatureImportFormat.GP_LEGACY -> null
    }

    private fun firstOf(xml: String, vararg tags: String): String? = tags.firstNotNullOfOrNull { tag ->
        Regex("<$tag\\b[^>]*>([^<]*)</$tag>")
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** MuseScore 3 guarda el título en `<metaTag name="title">valor</metaTag>`. */
    private fun metaTagTitle(xml: String): String? =
        Regex("<metaTag\\s+name=\"(?:workTitle|movementTitle|title)\"[^>]*>([^<]*)</metaTag>")
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
