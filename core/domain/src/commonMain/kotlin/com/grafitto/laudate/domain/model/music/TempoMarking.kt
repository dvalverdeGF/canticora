package com.grafitto.laudate.domain.model.music

/**
 * Indicación de tempo tradicional italiana, con su rango aproximado de BPM.
 * Puro Dominio: sin dependencias de Android.
 */
enum class TempoMarking(
    val italianName: String,
    val minBpm: Int,
    val maxBpm: Int,
    val description: String
) {
    LARGO("Largo", 40, 60, "Muy lento y solemne"),
    ADAGIO("Adagio", 60, 76, "Lento, tranquilo"),
    ANDANTE("Andante", 76, 108, "A paso de caminar"),
    MODERATO("Moderato", 108, 120, "Moderado"),
    ALLEGRETTO("Allegretto", 120, 132, "Algo animado"),
    ALLEGRO("Allegro", 132, 168, "Rápido y animado"),
    VIVACE("Vivace", 168, 200, "Vivo, con energía"),
    PRESTO("Presto", 200, 240, "Muy rápido");

    /** BPM central sugerido para esta indicación. */
    val suggestedBpm: Int
        get() = (minBpm + maxBpm) / 2

    companion object {
        /**
         * Devuelve la indicación italiana que corresponde a un BPM dado.
         * Si el BPM queda fuera de todos los rangos, devuelve la más cercana.
         */
        fun fromBpm(bpm: Int): TempoMarking {
            return entries.firstOrNull { bpm in it.minBpm..it.maxBpm }
                ?: entries.minByOrNull { candidate ->
                    when {
                        bpm < candidate.minBpm -> candidate.minBpm - bpm
                        bpm > candidate.maxBpm -> bpm - candidate.maxBpm
                        else -> 0
                    }
                }
                ?: ANDANTE
        }

        /** Busca una indicación por su nombre italiano (tolerante a mayúsculas). */
        fun fromName(name: String): TempoMarking? =
            entries.firstOrNull { it.italianName.equals(name.trim(), ignoreCase = true) }

        /** Todas las indicaciones ordenadas, útil para menús. */
        val all: List<TempoMarking> = entries.toList()
    }
}
