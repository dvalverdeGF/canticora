package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.harmonic.HarmonicTransposer
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.song.Song

/**
 * Caso de uso: Transponer una canción completa a una tonalidad destino dada por semitonos.
 * Cumple con el criterio RNF-102 (latencia ≤ 50 ms) y pureza de Dominio.
 */
class GetTransposedSongUseCase {

    operator fun invoke(song: Song, semitonesDelta: Int): Song {
        if (semitonesDelta == 0) return song
        val originalPitch = PitchClass.parse(song.originalKey) ?: PitchClass.C
        val targetPitch = originalPitch.transpose(semitonesDelta)
        return HarmonicTransposer.transposeSong(song, semitonesDelta, targetPitch)
    }
}
