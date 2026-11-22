package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.serializer.ChordProSerializer
import com.grafitto.laudate.domain.usecase.ImportCatalogBundleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fidelidad del cancionero real (5ª ed.) al importarlo como bundle `.laudate`.
 *
 * Cubre los casos límite que el cancionero trae de verdad: filas de acordes sin letra,
 * bises, cejilla, voces, coro tras bloque de acordes, varias canciones por página,
 * numeración de 2–4 dígitos y naturaleza de las categorías.
 *
 * El bundle es la fuente de verdad: estos tests **no** modifican su formato, verifican
 * que la app lo conserva; si algo se pierde, se señala para ajustar la herramienta de importación.
 */
class BundleImportFidelityTest {

    @Test
    fun chordOnlyLineSurvivesImportAndChordProRoundTrip() = runTest {
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-intro",
                    title = "Canto con intro",
                    number = "55",
                    sections = listOf(
                        sectionJson(
                            id = "sec-i",
                            songId = "s-intro",
                            sectionType = "INTRO",
                            sequenceOrder = 0,
                            lines = listOf(
                                // Fila de acordes de la intro: sin letra, solo acordes.
                                lineJson(0, lyrics = "", chords = listOf("G" to 0, "C" to 5))
                            )
                        )
                    )
                )
            )
        )

        val songs = importSongs(json)
        val song = songs.single()
        val chordOnlyLine = song.sections.single().lines.single()

        assertEquals("", chordOnlyLine.lyrics)
        assertEquals(listOf("G", "C"), chordOnlyLine.chords.map { it.rawChordText })

        // El texto reconstruido conserva la fila de acordes aunque no haya letra.
        val chordPro = ChordProSerializer.renderLine(chordOnlyLine.lyrics, chordOnlyLine.chords)
        assertEquals("[G][C]", chordPro)

        // Y sobrevive a un ida y vuelta ChordPro ↔ AST.
        val reparsed = ChordProParser.parse("{key: G}\n{sov}\n$chordPro\n{eov}")
        val reparsedLine = reparsed.sections.single().lines.first { it.chords.isNotEmpty() }
        assertEquals(listOf("G", "C"), reparsedLine.chords.map { it.rawChordText })
    }

    @Test
    fun emptyLineWithoutLyricsOrChordsDoesNotBreakSerialization() = runTest {
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-blank",
                    title = "Canto con línea vacía",
                    sections = listOf(
                        sectionJson(
                            id = "sec-b",
                            songId = "s-blank",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            lines = listOf(
                                lineJson(0, lyrics = "", chords = emptyList()),
                                lineJson(1, lyrics = "Letra", chords = listOf("D" to 0))
                            )
                        )
                    )
                )
            )
        )

        val song = importSongs(json).single()

        assertEquals("", song.sections.single().lines[0].lyrics)
        assertTrue(song.sections.single().lines[0].chords.isEmpty())
        // Serializar no revienta con líneas vacías.
        assertNotNull(ChordProSerializer.serialize(song))
    }

    @Test
    fun repeatMarkersSurviveRoundTrip() = runTest {
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-bis",
                    title = "Canto con bises",
                    sections = listOf(
                        sectionJson(
                            id = "sec-r1",
                            songId = "s-bis",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            isRepeated = true,
                            repeatCount = 2,
                            lines = listOf(lineJson(0, lyrics = "Se repite dos veces", chords = emptyList()))
                        ),
                        sectionJson(
                            id = "sec-r2",
                            songId = "s-bis",
                            sectionType = "CHORUS",
                            sequenceOrder = 1,
                            isRepeated = true,
                            repeatCount = null,
                            lines = listOf(lineJson(0, lyrics = "BIS", chords = emptyList()))
                        )
                    )
                )
            )
        )

        val song = importSongs(json).single()

        assertTrue(song.sections[0].isRepeated)
        assertEquals(2, song.sections[0].repeatCount)
        assertTrue(song.sections[1].isRepeated)
        assertNull(song.sections[1].repeatCount)

        // Round-trip: `{repeat: 2}` + `{repeat_end}` se reconstruyen y se vuelven a leer.
        val serialized = ChordProSerializer.serialize(song)
        assertTrue(serialized.contains("{repeat: 2}"))
        assertTrue(serialized.contains("{repeat}"))
        assertTrue(serialized.contains("{repeat_end}"))

        val reparsed = ChordProParser.parse(serialized)
        assertTrue(reparsed.sections[0].isRepeated)
        assertEquals(2, reparsed.sections[0].repeatCount)
        assertTrue(reparsed.sections[1].isRepeated)
        assertNull(reparsed.sections[1].repeatCount)
    }

    @Test
    fun capoIsPreservedAndSerialized() = runTest {
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-capo",
                    title = "Canto con cejilla",
                    capo = 2,
                    sections = listOf(
                        sectionJson(
                            id = "sec-c",
                            songId = "s-capo",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            lines = listOf(lineJson(0, lyrics = "Con cejilla", chords = listOf("A" to 0)))
                        )
                    )
                )
            )
        )

        val song = importSongs(json).single()
        assertEquals(2, song.capo)

        val serialized = ChordProSerializer.serialize(song)
        assertTrue(serialized.contains("{capo: 2}"))

        // Y el parser lo vuelve a leer (caso `[Cejilla 2]` del cancionero → `{capo: 2}`).
        assertEquals(2, ChordProParser.parse(serialized).capo)
    }

    @Test
    fun sectionAndCommentVoicesSurviveRoundTrip() = runTest {
        // `BackupLine` no tiene campo de tipo ni de voz: en el bundle real el comentario
        // (`{comment-todos: todos fuerte}`) viaja dentro de `rawTextContent` de la sección.
        val commentRaw = "Cantan sopranos\n{comment-todos: todos fuerte}"
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-voces",
                    title = "Canto con voces",
                    sections = listOf(
                        sectionJson(
                            id = "sec-v",
                            songId = "s-voces",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            voice = "Sopranos",
                            label = "Primera vez",
                            rawTextContent = commentRaw,
                            lines = listOf(
                                lineJson(0, lyrics = "Cantan sopranos", chords = listOf("G" to 0)),
                                commentLineJson(1, comment = "{comment-todos: todos fuerte}", voice = "todos")
                            )
                        ),
                        sectionJson(
                            id = "sec-may",
                            songId = "s-voces",
                            sectionType = "CHORUS",
                            sequenceOrder = 1,
                            lines = listOf(lineJson(0, lyrics = "TODOS", chords = emptyList()))
                        )
                    )
                )
            )
        )

        val song = importSongs(json).single()
        val verse = song.sections[0]

        assertEquals("Sopranos", verse.voice)
        assertEquals("Primera vez", verse.label)
        assertEquals(LineType.COMMENT, verse.lines[1].type)
        assertEquals("todos", verse.lines[1].voice)
        assertEquals("todos fuerte", verse.lines[1].comment)

        val serialized = ChordProSerializer.serialize(song)
        assertTrue(serialized.contains("{start_of_verse-sopranos Primera vez}"))
        assertTrue(serialized.contains("{comment-todos: todos fuerte}"))

        val reparsed = ChordProParser.parse(serialized)
        val reparsedVerse = reparsed.sections[0]
        // La directiva ChordPro `{start_of_verse-voz}` se emite en minúsculas (convención del parser),
        // así que el round-trip normaliza la caja del nombre de la voz. La BD conserva la original.
        assertEquals("sopranos", reparsedVerse.voice)
        assertEquals("Sopranos", verse.voice)
        assertEquals("Primera vez", reparsedVerse.label)
        val reparsedComment = reparsedVerse.lines.first { it.type == LineType.COMMENT }
        assertEquals("todos", reparsedComment.voice)
        assertEquals("todos fuerte", reparsedComment.comment)
    }

    @Test
    fun commentInsideRawTextContentIsRebuiltWhenBundleOmitsLines() = runTest {
        // Variante defensiva: si el bundle solo trae `rawTextContent` (sin `lines` resueltas),
        // los comentarios y las filas de acordes se reconstruyen igualmente.
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-raw",
                    title = "Solo rawTextContent",
                    sections = listOf(
                        sectionJson(
                            id = "sec-raw",
                            songId = "s-raw",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            rawTextContent = "[G]Cantad\n{comment-todos: fuerte}",
                            lines = emptyList()
                        )
                    )
                )
            )
        )

        val section = importSongs(json).single().sections.single()

        assertEquals(2, section.lines.size)
        assertEquals("Cantad", section.lines[0].lyrics)
        assertEquals(listOf("G"), section.lines[0].chords.map { it.rawChordText })
        assertEquals(LineType.COMMENT, section.lines[1].type)
        assertEquals("fuerte", section.lines[1].comment)
        assertEquals("todos", section.lines[1].voice)
    }

    @Test
    fun chorusAfterChordBlockRemainsAnIndependentSection() = runTest {
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-coro",
                    title = "Con coro en mayúsculas",
                    sections = listOf(
                        sectionJson(
                            id = "sec-1",
                            songId = "s-coro",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            lines = listOf(
                                lineJson(0, lyrics = "", chords = listOf("G" to 0, "D" to 4)),
                                lineJson(1, lyrics = "Estrofa en minúsculas", chords = emptyList())
                            )
                        ),
                        sectionJson(
                            id = "sec-2",
                            songId = "s-coro",
                            sectionType = "CHORUS",
                            sequenceOrder = 1,
                            lines = listOf(
                                lineJson(0, lyrics = "", chords = listOf("C" to 0)),
                                lineJson(1, lyrics = "CORO EN MAYÚSCULAS", chords = emptyList())
                            )
                        )
                    )
                )
            )
        )

        val song = importSongs(json).single()

        assertEquals(2, song.sections.size)
        assertEquals(SectionType.VERSE, song.sections[0].sectionType)
        assertEquals(SectionType.CHORUS, song.sections[1].sectionType)
        assertTrue(song.sections[0].id != song.sections[1].id)
        assertEquals(listOf("C"), song.sections[1].lines[0].chords.map { it.rawChordText })
        assertEquals("CORO EN MAYÚSCULAS", song.sections[1].lines[1].lyrics)
    }

    @Test
    fun adjacentSongsFromSamePageStayIndependent() = runTest {
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-a",
                    title = "Primer canto",
                    number = "1149",
                    sections = listOf(
                        sectionJson(
                            id = "sec-a1",
                            songId = "s-a",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            lines = listOf(lineJson(0, lyrics = "Uno", chords = emptyList()))
                        )
                    )
                ),
                songJson(
                    id = "s-b",
                    title = "Segundo canto",
                    number = "1150",
                    sections = listOf(
                        sectionJson(
                            id = "sec-b1",
                            songId = "s-b",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            lines = listOf(lineJson(0, lyrics = "Dos", chords = emptyList()))
                        )
                    )
                ),
                songJson(
                    id = "s-c",
                    title = "Tercer canto",
                    sections = listOf(
                        sectionJson(
                            id = "sec-c1",
                            songId = "s-c",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            lines = listOf(lineJson(0, lyrics = "Tres", chords = emptyList()))
                        )
                    )
                )
            )
        )

        val songs = importSongs(json)

        assertEquals(3, songs.size)
        assertEquals(listOf("Primer canto", "Segundo canto", "Tercer canto"), songs.map { it.title })
        assertEquals(listOf("1149", "1150", null), songs.map { it.number })
        // Ningún canto absorbe las secciones del contiguo.
        songs.forEach { song ->
            assertEquals(1, song.sections.size)
            assertTrue(song.sections.all { it.songId == song.id })
            assertFalse(song.title.contains("canto\n"))
        }
    }

    @Test
    fun numbersOfTwoToFourDigitsAreSearchableAndNotUnique() = runTest {
        val json = bundleOf(
            songs = listOf(
                songJson(id = "s-1", title = "Aleluya", number = "402"),
                songJson(id = "s-2", title = "Otro Aleluya", number = "402"),
                songJson(id = "s-3", title = "Sin número", number = null)
            )
        )

        val songs = importSongs(json)

        // El número NO es clave: dos canciones pueden compartirlo.
        assertEquals(2, songs.count { it.number == "402" })
        assertNull(songs.single { it.id == "s-3" }.number)

        // La búsqueda del catálogo (título o número, como en SongCatalogScreen) lo encuentra.
        val query = "402"
        val matches = songs.filter { song ->
            song.title.lowercase().contains(query) ||
                (song.number?.lowercase()?.contains(query) == true)
        }
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.title.contains("Aleluya") })

        // Y no se confunde con el título.
        assertFalse(songs.any { it.title.contains("402") })
    }

    @Test
    fun categoryKindsOfRealSongbookAreDistinguishedAndNotDuplicated() = runTest {
        val json = bundleOf(
            categories = listOf(
                categoryJson("c-1", "Entrada", "MASS_MOMENT", 0),
                categoryJson("c-2", "Paz - Cordero de Dios", "MASS_MOMENT", 1),
                categoryJson("c-3", "Cuaresma", "LITURGICAL_SEASON", 20),
                categoryJson("c-4", "María", "ADDITIONAL", 40),
                categoryJson("c-5", "Franciscanos", "ADDITIONAL", 41),
                categoryJson("c-6", "Himnos y Cánones", "ADDITIONAL", 42),
                categoryJson("c-7", "Espíritu Santo", "ADDITIONAL", 43)
            ),
            songs = listOf(songJson(id = "s-1", title = "Cualquier canto", number = "1"))
        )

        val categories = importCategories(json)

        assertEquals(7, categories.size)
        assertEquals(2, categories.count { it.kind == LiturgicalCategoryKind.MASS_MOMENT })
        assertEquals(1, categories.count { it.kind == LiturgicalCategoryKind.LITURGICAL_SEASON })
        assertEquals(4, categories.count { it.kind == LiturgicalCategoryKind.ADDITIONAL })
        // Los momentos de la misa no se mezclan con las colecciones del coro.
        assertEquals(
            LiturgicalCategoryKind.ADDITIONAL,
            categories.single { it.name == "Franciscanos" }.kind
        )

        // Reimportar el bundle completo no duplica nada (nombre único, sin distinguir mayúsculas).
        val second = importCategories(json)
        assertEquals(7, second.size)
        assertEquals(7, second.map { it.name.lowercase() }.distinct().size)
    }

    @Test
    fun minimalSongWithoutMetadataImportsWithSaneDefaults() = runTest {
        // Canto del cancionero sin autor, sin capo y sin secciones.
        val json = bundleOf(songs = listOf(songJson(id = "s-min", title = "Canto mínimo", number = "1149")))

        val song = importSongs(json).single()

        assertEquals("Canto mínimo", song.title)
        assertEquals("1149", song.number)
        assertNull(song.author)
        assertEquals("4/4", song.rhythm)
        assertEquals(0, song.capo)
        assertTrue(song.sections.isEmpty())
        assertFalse(song.isFavorite)
        assertTrue(song.defaultLiturgicalMoments.isEmpty())
        assertTrue(song.defaultLiturgicalTimes.isEmpty())
    }

    @Test
    fun storedLyricsAreCleanAndAnchorsAreNotDuplicated() = runTest {
        // `rawTextContent` conserva las etiquetas [Acorde] (lo exige el mapper de recarga),
        // pero las líneas de dominio llevan la letra ya limpia y los anclajes del bundle,
        // sin volver a parsear el ChordPro.
        val json = bundleOf(
            songs = listOf(
                songJson(
                    id = "s-clean",
                    title = "Con etiquetas",
                    sections = listOf(
                        sectionJson(
                            id = "sec-x",
                            songId = "s-clean",
                            sectionType = "VERSE",
                            sequenceOrder = 0,
                            rawTextContent = "[G]Cantad al Se[C]ñor",
                            lines = listOf(lineJson(0, lyrics = "Cantad al Señor", chords = listOf("G" to 0, "C" to 11)))
                        )
                    )
                )
            )
        )

        val section = importSongs(json).single().sections.single()

        assertEquals("[G]Cantad al Se[C]ñor", section.rawTextContent)
        assertEquals("Cantad al Señor", section.lines.single().lyrics)
        assertFalse(section.lines.single().lyrics.contains("["))
        assertEquals(listOf("G", "C"), section.lines.single().chords.map { it.rawChordText })
        assertEquals(listOf(0, 11), section.lines.single().chords.map { it.characterIndex })
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private suspend fun importSongs(json: String): List<Song> {
        val songs = FakeSongRepository()
        importCatalog(json, songs, FakeCategoryRepository())
        return songs.songs
    }

    private suspend fun importCategories(json: String) =
        FakeCategoryRepository().also { importCatalog(json, FakeSongRepository(), it) }.categories

    private suspend fun importCatalog(
        json: String,
        songs: SongRepository,
        categories: CategoryRepository
    ) = ImportCatalogBundleUseCase(
        songRepository = songs,
        categoryRepository = categories,
        computeDispatcher = Dispatchers.Unconfined
    ).invoke(json)

    private fun bundleOf(
        songs: List<String> = emptyList(),
        categories: List<String> = emptyList()
    ) = """
        {
          "version": 1,
          "exportedAt": 0,
          "categories": [${categories.joinToString(",")}],
          "songs": [${songs.joinToString(",")}],
          "celebrations": []
        }
    """.trimIndent()

    private fun categoryJson(id: String, name: String, kind: String, order: Int) =
        """{ "id": "$id", "name": "$name", "displayOrder": $order,
             "isSystemStandard": false, "kind": "$kind", "createdAt": 1 }"""

    private fun songJson(
        id: String,
        title: String,
        number: String? = null,
        capo: Int = 0,
        sections: List<String> = emptyList()
    ): String {
        val numberField = number?.let { "\"$it\"" } ?: "null"
        return """
            {
              "id": "$id", "title": "$title", "number": $numberField, "originalKey": "G",
              "author": null, "rhythm": "4/4", "capo": $capo,
              "defaultLiturgicalMoments": [], "defaultLiturgicalTimes": [],
              "isFavorite": false, "createdAt": 1, "updatedAt": 1,
              "sections": [${sections.joinToString(",")}]
            }
        """
    }

    private fun sectionJson(
        id: String,
        songId: String,
        sectionType: String,
        sequenceOrder: Int,
        lines: List<String>,
        rawTextContent: String = "Letra",
        label: String? = null,
        voice: String? = null,
        isRepeated: Boolean = false,
        repeatCount: Int? = null
    ): String {
        val labelField = label?.let { "\"$it\"" } ?: "null"
        val voiceField = voice?.let { "\"$it\"" } ?: "null"
        val repeatField = repeatCount?.toString() ?: "null"
        return """
            {
              "id": "$id", "songId": "$songId", "sectionType": "$sectionType",
              "sequenceOrder": $sequenceOrder, "headerTitle": null,
              "rawTextContent": "$rawTextContent", "label": $labelField, "voice": $voiceField,
              "isRepeated": $isRepeated, "repeatCount": $repeatField,
              "lines": [${lines.joinToString(",")}]
            }
        """
    }

    private fun lineJson(lineIndex: Int, lyrics: String, chords: List<Pair<String, Int>>): String {
        val anchors = chords.joinToString(",") { (chord, index) ->
            """{ "id": "anc-$lineIndex-$index", "sectionId": "sec", "lineIndex": $lineIndex,
                 "characterIndex": $index, "chordRoot": "$chord", "chordQuality": "",
                 "chordBass": null, "rawChordText": "$chord" }"""
        }
        return """{ "lineIndex": $lineIndex, "lyrics": "$lyrics", "chords": [$anchors] }"""
    }

    private fun commentLineJson(lineIndex: Int, comment: String, voice: String): String =
        """{ "lineIndex": $lineIndex, "lyrics": "$comment", "chords": [] }"""

    private class FakeSongRepository : SongRepository {
        val songs = mutableListOf<Song>()
        override fun getAllSongs(): Flow<List<Song>> = flowOf(songs.toList())
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(songs.toList())
        override suspend fun getSongCount(): Int = songs.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(songs.filter { it.isFavorite })
        override fun getSongById(id: String): Flow<Song?> = flowOf(songs.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> =
            songs.filter { it.id in ids }

        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override suspend fun overwriteSong(song: Song) = saveSong(song)

        override fun searchSongs(query: String): Flow<List<Song>> =
            flowOf(songs.filter { it.title.contains(query, ignoreCase = true) })
        override suspend fun saveSong(song: Song) {
            songs.removeAll { it.id == song.id }
            songs.add(song)
        }
        override suspend fun saveSongs(songs: List<Song>) {
            songs.forEach { saveSong(it) }
        }
        override suspend fun setFavorite(songId: String, isFavorite: Boolean) {
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) songs[index] = songs[index].copy(isFavorite = isFavorite)
        }
        override suspend fun deleteSong(songId: String) {
            songs.removeAll { it.id == songId }
        }

        override suspend fun findSongByContentHash(hash: String): Song? =
            songs.firstOrNull { it.contentHash == hash }

        override suspend fun findSongByContentSignature(song: Song): Song? = null

        override suspend fun getSongsMissingContentHash(): List<Song> =
            songs.filter { it.contentHash.isNullOrBlank() }

        override suspend fun updateContentHash(songId: String, hash: String) {
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) songs[index] = songs[index].copy(contentHash = hash)
        }

        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categories = mutableListOf<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>()

        override fun getAllCategories(): Flow<List<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>> =
            flowOf(categories.toList())

        override suspend fun getAllCategoriesSnapshot() = categories.toList()
        override suspend fun getCategoryById(id: String) = categories.firstOrNull { it.id == id }
        override suspend fun saveCategory(category: com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory) {
            saveCategories(listOf(category))
        }
        override suspend fun updateCategory(category: com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory) = Unit
        override suspend fun saveCategories(
            categories: List<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>
        ): Int {
            val existing = this.categories.map { it.name.trim().lowercase() }.toMutableSet()
            var created = 0
            categories.forEach { category ->
                if (existing.add(category.name.trim().lowercase())) {
                    this.categories.add(category)
                    created++
                }
            }
            return created
        }
        override suspend fun deleteCategory(categoryId: String) {
            categories.removeAll { it.id == categoryId }
        }
        override suspend fun seedStandardCategoriesIfEmpty() = Unit
        override suspend fun ensureCategories(names: Collection<String>, kind: LiturgicalCategoryKind) =
            names.toList()
    }
}
