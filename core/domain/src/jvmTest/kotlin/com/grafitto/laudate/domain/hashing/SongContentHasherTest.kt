package com.grafitto.laudate.domain.hashing

import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.model.song.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la huella de contenido: es la base de la detección de duplicados, así que debe ser
 * **estable** (mismo contenido ⇒ misma huella) y **sensible** (cualquier cambio ⇒ otra huella).
 */
class SongContentHasherTest {

    private fun song(
        id: String = "s1",
        title: String = "Aleluya",
        key: String = "C",
        lyrics: String = "A[C]lé[G]lu[Am]ya",
        createdAt: Long = 1_000L
    ) = Song(
        id = id,
        title = title,
        originalKey = key,
        createdAt = createdAt,
        updatedAt = createdAt,
        sections = listOf(
            SongSection(
                id = "sec1",
                songId = id,
                sectionType = SectionType.CHORUS,
                sequenceOrder = 0,
                headerTitle = null,
                rawTextContent = lyrics,
                // El serializador canónico renderiza desde `lines`: hay que poblarlas para que
                // el texto ChordPro refleje la letra y los acordes.
                lines = parseLines(lyrics)
            )
        )
    )

    /** Descompone una línea ChordPro en letra limpia + anclas de acorde. */
    private fun parseLines(raw: String) = listOf(
        com.grafitto.laudate.domain.model.song.SongLine(
            lineIndex = 0,
            lyrics = raw.replace(Regex("\\[[^\\]]+\\]"), ""),
            chords = Regex("\\[([^\\]]+)\\]").findAll(raw).mapIndexed { index, match ->
                com.grafitto.laudate.domain.model.song.ChordAnchor(
                    id = "a$index",
                    sectionId = "sec1",
                    lineIndex = 0,
                    characterIndex = index,
                    chord = com.grafitto.laudate.domain.model.music.DomainChord(
                        rootNote = com.grafitto.laudate.domain.model.music.PitchClass.C
                    ),
                    rawChordText = match.groupValues[1]
                )
            }.toList()
        )
    )

    @Test
    fun `misma cancion produce la misma huella`() {
        assertEquals(SongContentHasher.hash(song()), SongContentHasher.hash(song()))
    }

    @Test
    fun `la huella no depende del id ni de las fechas`() {
        val a = SongContentHasher.hash(song(id = "uno", createdAt = 1L))
        val b = SongContentHasher.hash(song(id = "dos", createdAt = 999_999L))
        assertEquals(a, b)
    }

    @Test
    fun `cambiar la letra cambia la huella`() {
        assertNotEquals(
            SongContentHasher.hash(song(lyrics = "A[C]lé[G]lu[Am]ya")),
            SongContentHasher.hash(song(lyrics = "A[C]lé[G]lu[Am]ya!"))
        )
    }

    @Test
    fun `cambiar el titulo cambia la huella`() {
        assertNotEquals(
            SongContentHasher.hash(song(title = "Aleluya")),
            SongContentHasher.hash(song(title = "Aleluya (Pascua)"))
        )
    }

    @Test
    fun `cambiar la tonalidad cambia la huella`() {
        assertNotEquals(
            SongContentHasher.hash(song(key = "C")),
            SongContentHasher.hash(song(key = "D"))
        )
    }

    @Test
    fun `los saltos de linea CRLF y LF producen la misma huella`() {
        val lf = SongContentHasher.hashOfChordPro("{title: X}\n[A]Hola")
        val crlf = SongContentHasher.hashOfChordPro("{title: X}\r\n[A]Hola")
        assertEquals(lf, crlf)
    }

    @Test
    fun `los espacios finales no alteran la huella`() {
        val sinEspacios = SongContentHasher.hashOfChordPro("linea1\nlinea2")
        val conEspacios = SongContentHasher.hashOfChordPro("linea1   \nlinea2\t")
        assertEquals(sinEspacios, conEspacios)
    }

    @Test
    fun `la huella lleva el prefijo del algoritmo`() {
        assertTrue(SongContentHasher.hash(song()).startsWith(SongContentHasher.PREFIX))
    }

    @Test
    fun `matches compara contra el texto ChordPro`() {
        val chordPro = "{title: X}\n[A]Hola"
        val hash = SongContentHasher.hashOfChordPro(chordPro)
        assertTrue(SongContentHasher.matches(hash, chordPro))
        assertFalse(SongContentHasher.matches(hash, "{title: X}\n[A]Adios"))
        assertFalse(SongContentHasher.matches(null, chordPro))
    }

    @Test
    fun `hash de dos canciones distintas no colisiona`() {
        assertNotEquals(
            SongContentHasher.hash(song(title = "Uno", lyrics = "[C]Uno")),
            SongContentHasher.hash(song(title = "Dos", lyrics = "[C]Dos"))
        )
    }
}
