package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    @Test
    fun exportAndImportJson_preservesDataIntegrity() {
        val song = Song(
            id = "song-1",
            title = "Juntos como Hermanos",
            originalKey = "C",
            author = "Cesáreo Gabaráin",
            rhythm = "4/4",
            defaultLiturgicalMoments = listOf("Entrada"),
            isFavorite = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            sections = listOf(
                SongSection(
                    id = "sec-1",
                    songId = "song-1",
                    sectionType = SectionType.CHORUS,
                    sequenceOrder = 0,
                    headerTitle = "Coro",
                    rawTextContent = "Juntos como hermanos",
                    lines = listOf(
                        SongLine(
                            lineIndex = 0,
                            lyrics = "Juntos como hermanos",
                            chords = listOf(
                                ChordAnchor(
                                    id = "anc-1",
                                    sectionId = "sec-1",
                                    lineIndex = 0,
                                    characterIndex = 0,
                                    chord = DomainChord(rootNote = PitchClass.C),
                                    rawChordText = "C"
                                )
                            )
                        )
                    )
                )
            )
        )

        val celebration = Celebration(
            id = "cel-1",
            name = "Misa Pascual",
            dateTimestamp = 5000L,
            items = listOf(
                CelebrationItem(
                    id = "item-1",
                    celebrationId = "cel-1",
                    songId = "song-1",
                    liturgicalMoment = "Entrada",
                    displayOrder = 0
                )
            )
        )

        val category = LiturgicalCategory(
            id = "cat-1",
            name = "Entrada",
            isSystemStandard = true
        )

        val json = BackupSerializer.exportToJson(
            songs = listOf(song),
            celebrations = listOf(celebration),
            categories = listOf(category)
        )

        assertTrue(json.contains("Juntos como Hermanos"))
        assertTrue(json.contains("Misa Pascual"))
        assertTrue(json.contains("Entrada"))

        val payload = BackupSerializer.importFromJson(json)
        assertEquals(1, payload.songs.size)
        assertEquals("Juntos como Hermanos", payload.songs[0].title)
        assertEquals(1, payload.celebrations.size)
        assertEquals("Misa Pascual", payload.celebrations[0].name)
        assertEquals(1, payload.categories.size)
        assertEquals("Entrada", payload.categories[0].name)
    }
}
