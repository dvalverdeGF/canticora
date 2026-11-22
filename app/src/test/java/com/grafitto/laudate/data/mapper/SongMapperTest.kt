package com.grafitto.laudate.data.mapper

import com.grafitto.laudate.data.local.entity.ChordAnchorEntity
import com.grafitto.laudate.data.local.entity.SongEntity
import com.grafitto.laudate.data.local.entity.SongSectionEntity
import com.grafitto.laudate.data.local.relation.SectionWithAnchorsRelation
import com.grafitto.laudate.data.local.relation.SongWithDetailsRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del mapeo `Entity ↔ Dominio`.
 *
 * Cubren una regresión real: la consulta que lee solo la tabla `songs` no rellena la relación de
 * secciones, de modo que el modelo de dominio sale **sin letra**. Un repertorio impreso o
 * compartido con esas canciones aparecía con los títulos pero sin el texto. Estas pruebas fijan
 * que la única vía que trae la letra es la que parte de [SongWithDetailsRelation].
 */
class SongMapperTest {

    private fun songEntity() = SongEntity(
        id = "s1",
        title = "A Dios den gracias",
        originalKey = "C",
        number = "312",
        author = null,
        rhythm = null,
        rhythmPatternId = null,
        tempoBpm = null,
        tempoMarking = null,
        defaultLiturgicalMomentsJson = "[]",
        defaultLiturgicalTimesJson = "[]",
        isFavorite = false,
        capo = 0,
        notes = null,
        contentHash = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun sectionRelation() = SectionWithAnchorsRelation(
        section = SongSectionEntity(
            id = "sec1",
            songId = "s1",
            sectionType = "VERSE",
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "Den gracias al Señor, invoquen su nombre",
            label = null,
            voice = null,
            isRepeated = false,
            repeatCount = null
        ),
        anchors = listOf(
            ChordAnchorEntity(
                id = "a1",
                sectionId = "sec1",
                lineIndex = 0,
                characterIndex = 0,
                chordRoot = "C",
                chordQuality = "",
                chordBass = null,
                rawChordText = "C"
            )
        )
    )

    @Test
    fun `el mapeo desde la relacion conserva la letra de las secciones`() {
        val song = SongMapper.toDomain(
            SongWithDetailsRelation(
                song = songEntity(),
                sections = listOf(sectionRelation()),
                arrangements = emptyList(),
                annotations = emptyList()
            )
        )

        assertEquals(1, song.sections.size)
        assertEquals(
            "Den gracias al Señor, invoquen su nombre",
            song.sections.first().lines.first().lyrics
        )
    }

    @Test
    fun `el mapeo desde la relacion conserva los acordes anclados`() {
        val song = SongMapper.toDomain(
            SongWithDetailsRelation(
                song = songEntity(),
                sections = listOf(sectionRelation()),
                arrangements = emptyList(),
                annotations = emptyList()
            )
        )

        val anchor = song.sections.first().lines.first().chords.single()
        assertEquals("C", anchor.rawChordText)
        assertEquals(0, anchor.characterIndex)
    }

    @Test
    fun `el mapeo de la entidad suelta NO trae letra`() {
        // Documenta la trampa: esta vía es válida para listados, nunca para exportar.
        val song = SongMapper.toDomain(songEntity())

        assertTrue(song.sections.isEmpty())
        assertEquals("A Dios den gracias", song.title)
    }
}
