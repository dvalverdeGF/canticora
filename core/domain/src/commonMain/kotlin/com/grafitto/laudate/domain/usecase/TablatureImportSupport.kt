package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.repository.TablatureRepository

/**
 * Guarda [imported] como **la** tablatura del canto, sustituyendo las que hubiera.
 *
 * Una tablatura pertenece al canto y reimportar la partitura debe **reemplazar**, no acumular
 * copias; por eso todos los importadores (MuseScore, MusicXML, Guitar Pro, TuxGuitar) comparten este
 * paso en vez de repetirlo cada uno.
 *
 * @return cuántas tablaturas anteriores del canto se han eliminado.
 */
internal suspend fun TablatureRepository.replaceTablaturesOf(imported: Tablature): Int {
    val previous = getTablaturesForSong(imported.songId)
    previous.forEach { deleteTablature(it.id) }
    saveTablature(imported)
    return previous.size
}
