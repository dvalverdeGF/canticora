package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.transfer.RhythmBundleSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportRhythmPatternsUseCaseTest {

    private class FakeRepository : RhythmPatternRepository {
        val stored = mutableListOf<RhythmPattern>()
        override fun getAllPatterns(): Flow<List<RhythmPattern>> = flowOf(stored.toList())
        override fun getCustomPatterns(): Flow<List<RhythmPattern>> = flowOf(stored.toList())
        override suspend fun savePattern(pattern: RhythmPattern) {
            stored.removeAll { it.id == pattern.id }
            stored += pattern
        }
        override suspend fun deletePattern(patternId: String) { stored.removeAll { it.id == patternId } }
        override suspend fun restorePattern(pattern: RhythmPattern) { stored += pattern }
    }

    private fun exportedJson(vararg patterns: RhythmPattern): String =
        RhythmBundleSerializer.encode(RhythmBundleSerializer.toBundle(patterns.toList(), "test"))

    @Test
    fun `importa los patrones de un archivo`() = runBlocking {
        val repository = FakeRepository()
        val useCase = ImportRhythmPatternsUseCase(repository)
        val source = RhythmPattern(id = "a", name = "Balada", timeSignature = com.grafitto.laudate.domain.model.rhythm.TimeSignature.FOUR_FOUR)

        val result = useCase(exportedJson(source))

        assertEquals(1, result.created)
        assertEquals(0, result.skipped)
        assertEquals(1, repository.stored.size)
        // El patrón importado recibe un identificador propio, nunca el del emisor.
        assertEquals(false, repository.stored.first().id == "a")
    }

    @Test
    fun `reimportar el mismo archivo no duplica`() = runBlocking {
        val repository = FakeRepository()
        val useCase = ImportRhythmPatternsUseCase(repository)
        val json = exportedJson(
            RhythmPattern(id = "a", name = "Balada", timeSignature = com.grafitto.laudate.domain.model.rhythm.TimeSignature.FOUR_FOUR)
        )

        useCase(json)
        val second = useCase(json)

        assertEquals(0, second.created)
        assertEquals(1, second.skipped)
        assertEquals(1, repository.stored.size)
    }

    @Test
    fun `importa un ritmo pegado como texto`() = runBlocking {
        val repository = FakeRepository()
        val useCase = ImportRhythmPatternsUseCase(repository)
        val pasted = "Compás 4/4\n↓ ↑ ↓ ↑ ↓ ↑ ↓ ↑"

        val result = useCase.import(pasted)

        assertEquals(1, result?.created)
        assertEquals(4.0, repository.stored.first().totalBeatValue, 1e-6)
    }

    @Test
    fun `un texto sin ritmo devuelve null`() = runBlocking {
        val repository = FakeRepository()
        val useCase = ImportRhythmPatternsUseCase(repository)

        assertEquals(null, useCase.import("Hola, ¿qué tal?"))
    }

    @Test
    fun `un archivo vacío no crea nada`() = runBlocking {
        val repository = FakeRepository()
        val useCase = ImportRhythmPatternsUseCase(repository)
        val json = exportedJson()

        val result = useCase(json)

        assertEquals(0, result.created)
        assertEquals(0, repository.stored.size)
    }

    @Test
    fun `sobrescribe un ritmo personalizado con el mismo nombre si se permite`() = runBlocking {
        val repository = FakeRepository()
        val useCase = ImportRhythmPatternsUseCase(repository)
        // Ya existe un ritmo personalizado «Balada» con un patrón distinto.
        val local = RhythmPattern(
            id = "local-1",
            name = "Balada",
            timeSignature = com.grafitto.laudate.domain.model.rhythm.TimeSignature.FOUR_FOUR,
            events = listOf(
                com.grafitto.laudate.domain.model.rhythm.RhythmEvent(
                    figure = com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER,
                    direction = com.grafitto.laudate.domain.model.rhythm.StrumDirection.DOWN
                )
            )
        )
        repository.savePattern(local)
        val incoming = RhythmPattern(
            id = "incoming",
            name = "Balada",
            timeSignature = com.grafitto.laudate.domain.model.rhythm.TimeSignature.FOUR_FOUR,
            events = listOf(
                com.grafitto.laudate.domain.model.rhythm.RhythmEvent(
                    figure = com.grafitto.laudate.domain.model.rhythm.RhythmFigure.EIGHTH,
                    direction = com.grafitto.laudate.domain.model.rhythm.StrumDirection.DOWN
                )
            )
        )
        val json = exportedJson(incoming)

        // Primero se inspecciona: es un conflicto (mismo nombre, distinto contenido).
        val preview = useCase.inspect(json)
        assertEquals(1, preview.conflicts.size)

        // Y al aplicar, se sobrescribe el mismo id (no se crea otro).
        val result = useCase(json)
        assertEquals(1, result.overwritten)
        assertEquals(1, repository.stored.size)
        assertEquals("local-1", repository.stored.single().id)
    }
}
