package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.serializer.MsczParser

/**
 * Resultado de importar una **partitura de MuseScore** (`.mscz`) como tablatura de un canto.
 */
data class MsczImportResult(
    /** Título de la tablatura importada (el del fichero o el del canto). */
    val title: String,
    /** Nº de compases de la tablatura importada. */
    val measureCount: Int,
    /** Nº de notas (pulsaciones) de la tablatura importada. */
    val noteCount: Int,
    /** **Tablaturas anteriores del canto** que se han eliminado al reemplazar. */
    val replaced: Int
)

/**
 * Caso de uso: **importar una partitura `.mscz` de MuseScore** como tablatura del canto, **sustituyendo**
 * la tablatura que hubiera.
 *
 * Flujo (Kotlin puro, sin Android):
 *  1. La **plataforma** extrae el `*.mscx` (XML) del ZIP `.mscz` —un `.mscz` es un ZIP— y lo entrega
 *     como texto (**[mscxXml]**): el dominio nunca toca ficheros ni ZIP.
 *  2. [MsczParser] lo convierte en una [Tablature].
 *  3. Se **borran todas las tablaturas previas del canto** y se guarda la nueva. Es lo que pidió el
 *     usuario: «**borrar la que hay e importarla**», porque una tablatura pertenece al canto y
 *     reimportar la partitura debe **reemplazar**, no acumular copias.
 *
 * El dominio describe *qué* se importa; la lectura del ZIP vive tras [BackupArchiveStore] en la capa
 * de datos (ver `ImportTablatureFromFileUseCase`).
 */
class ImportMsczTablatureUseCase(
    private val tablatureRepository: TablatureRepository,
    /** Generador de ids (inyectable para pruebas deterministas). */
    private val generateId: () -> String = { newId() }
) {

    /**
     * Parsea [mscxXml] y **reemplaza** la tablatura del canto [songId].
     *
     * @param title título de la tablatura (el nombre del fichero `.mscz`, o el del canto).
     * @param tuning afinación presupuesta (una tablatura `.mscz` de guitarra usa la estándar).
     * @throws IllegalArgumentException si el XML no contiene una tablatura legible.
     */
    suspend operator fun invoke(
        mscxXml: String,
        songId: String,
        title: String,
        tuning: Tuning = Tuning.GUITAR
    ): MsczImportResult {
        require(songId.isNotBlank()) { "Una tablatura siempre pertenece a un canto" }
        require(mscxXml.isNotBlank()) { "El archivo de MuseScore está vacío" }

        val parsed = MsczParser.parse(mscxXml, songId = songId, title = title, tuning = tuning)
        // El parser devuelve `measures` (todas, con silencios de compás incluidos). Se descartan los
        // compases **finales** completamente vacíos que MuseScore añade al final del documento, pero
        // no los vacíos intermedios: un silencio de compás entre dos frases es música.
        val measures = parsed.measures.dropLastWhile { it.slots.isEmpty() }
        require(measures.isNotEmpty()) { "El archivo no contiene compases con música" }

        val imported = parsed.copy(
            id = generateId(),
            songId = songId,
            title = title.ifBlank { parsed.title },
            measures = measures
        )

        // **Reemplazo**: se borran las tablaturas previas del canto y se guarda la nueva. Así la
        // partitura recién importada es **la** tablatura del canto, no una más acumulada.
        val replaced = tablatureRepository.replaceTablaturesOf(imported)

        return MsczImportResult(
            title = imported.title,
            measureCount = measures.size,
            noteCount = measures.sumOf { m -> m.slots.sumOf { it.strings.size } },
            replaced = replaced
        )
    }
}
