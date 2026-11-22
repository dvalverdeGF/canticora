package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProParser

/**
 * Caso de uso: Parsear texto en formato ChordPro a la representación canónica estructurada AST.
 */
class ParseChordProUseCase {

    operator fun invoke(rawChordPro: String, fallbackTitle: String = "Sin título"): Song {
        return ChordProParser.parse(chordProContent = rawChordPro, fallbackTitle = fallbackTitle)
    }
}
