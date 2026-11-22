package com.grafitto.laudate.domain.hashing

import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.serializer.ChordProSerializer

/**
 * Calcula la **huella de contenido** (hash) de una canción.
 *
 * Objetivo: reconocer cuándo dos canciones son *la misma canción* aunque hayan viajado por
 * medios distintos (un archivo ChordPro reenviado por WhatsApp, un respaldo, el cancionero
 * semilla...). La huella se calcula sobre la **representación ChordPro canónica** —la salida
 * determinista de [ChordProSerializer]— y **no** sobre la identidad de la fila: dos canciones
 * con el mismo texto producen la misma huella aunque tengan `id`, `createdAt` o `updatedAt`
 * distintos.
 *
 * Propiedades:
 *  - **Estable**: mismo contenido ⇒ misma huella, en cualquier dispositivo.
 *  - **Sensible al contenido**: cambiar una sílaba, un acorde o el título cambia la huella.
 *  - **Insensible al formato**: se normalizan saltos de línea (CRLF/CR ⇒ LF) y espacios finales.
 *  - **No identifica**: la huella *no* sustituye al `id`. Es válida para detectar duplicados,
 *    no para fusionar automáticamente (dos versiones distintas pueden convivir si el usuario
 *    decide «crear nueva»).
 *
 * Kotlin puro: solo usa la JCA de la JVM, sin dependencias de Android.
 */
object SongContentHasher {

    /** Prefijo del algoritmo, para poder evolucionar el esquema de huella sin ambigüedad. */
    const val PREFIX: String = "sha256:"

    /**
     * Huella canónica de una canción completa (metadatos + secciones + acordes).
     *
     * Se serializa a ChordPro con [ChordProSerializer] porque su salida es determinista y ya
     * cubre todo el contenido musical del canto.
     */
    fun hash(song: Song): String = hashOfChordPro(ChordProSerializer.serialize(song))

    /**
     * Huella del texto ChordPro tal cual lo envió/recibió el usuario.
     * Útil para comparar contra un texto pegado sin necesidad de reconstruir el modelo.
     */
    fun hashOfChordPro(chordPro: String): String = PREFIX + sha256(canonicalize(chordPro))

    /** Comprueba si una huella almacenada corresponde al texto ChordPro dado. */
    fun matches(storedHash: String?, chordPro: String): Boolean =
        !storedHash.isNullOrBlank() && storedHash == hashOfChordPro(chordPro)

    /**
     * Normaliza el texto antes de calcular la huella: unifica saltos de línea y elimina los
     * espacios sobrantes al final de cada línea, que son invisibles para el usuario pero
     * romperían la comparación entre dos versiones del mismo canto.
     */
    private fun canonicalize(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trimEnd() }
            .joinToString("\n")
            .trim()

    private fun sha256(text: String): String = Sha256.hex(text)
}
