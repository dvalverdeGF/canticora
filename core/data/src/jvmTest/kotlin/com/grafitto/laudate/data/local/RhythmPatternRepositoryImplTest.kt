package com.grafitto.laudate.data.local

import com.grafitto.laudate.data.repository.RhythmPatternRepositoryImpl
import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Regresión: guardar un patrón rítmico debe persistirlo y aparecer en el listado.
 *
 * Cubre el fallo por el que `core:data` usaba `kotlinx.serialization` **sin** aplicar el plugin
 * compilador: `RhythmPatternMapper.toEntity` lanzaba `SerializationException` y el guardado
 * (tanto de un patrón nuevo como de uno editado) no llegaba a persistir.
 */
class RhythmPatternRepositoryImplTest {

    @Test
    fun `guardar un patron nuevo lo persiste y aparece en getAllPatterns`() = runTest {
        val file = Files.createTempFile("laudate-rhythm", ".db").toFile()
        file.delete()
        val db = buildDesktopDatabase(file.absolutePath)
        val repo = RhythmPatternRepositoryImpl(db.rhythmPatternDao())

        val pattern = RhythmPattern(
            id = "custom-1",
            name = "Mi ritmo",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP)
            )
        )
        repo.savePattern(pattern)

        val all = repo.getAllPatterns().first()
        val found = all.firstOrNull { it.id == "custom-1" }
        assertTrue("El patrón guardado debe aparecer en el listado", found != null)
        assertEquals(4, found!!.events.size)

        db.close()
    }

    @Test
    fun `eliminar un patron integrado lo oculta y deshacer lo restaura`() = runTest {
        val file = Files.createTempFile("laudate-rhythm-builtin", ".db").toFile()
        file.delete()
        val db = buildDesktopDatabase(file.absolutePath)
        val repo = RhythmPatternRepositoryImpl(db.rhythmPatternDao())

        val builtIn = repo.getAllPatterns().first().first { it.isBuiltIn }
        assertTrue("La biblioteca debe traer patrones integrados", repo.getAllPatterns().first().any { it.id == builtIn.id })

        repo.deletePattern(builtIn.id)
        val afterDelete = repo.getAllPatterns().first()
        assertTrue("El patrón integrado debe quedar oculto", afterDelete.none { it.id == builtIn.id })

        repo.restorePattern(builtIn)
        val afterUndo = repo.getAllPatterns().first()
        assertTrue("Deshacer debe volver a mostrar el integrado", afterUndo.any { it.id == builtIn.id })

        db.close()
    }

    @Test
    fun `eliminar un patron integrado persiste tras reabrir la base`() = runTest {
        val file = Files.createTempFile("laudate-rhythm-persist", ".db").toFile()
        file.delete()

        val firstDb = buildDesktopDatabase(file.absolutePath)
        val firstRepo = RhythmPatternRepositoryImpl(firstDb.rhythmPatternDao())
        val builtIn = firstRepo.getAllPatterns().first().first { it.isBuiltIn }
        firstRepo.deletePattern(builtIn.id)
        firstDb.close()

        // Reabrir la base: la ocultación debe seguir en vigor (no vuelve a aparecer).
        val secondDb = buildDesktopDatabase(file.absolutePath)
        val secondRepo = RhythmPatternRepositoryImpl(secondDb.rhythmPatternDao())
        val patterns = secondRepo.getAllPatterns().first()
        assertTrue("El integrado borrado no debe reaparecer al reabrir", patterns.none { it.id == builtIn.id })
        assertTrue("Los demás integrados siguen disponibles", patterns.count { it.isBuiltIn } > 0)
        secondDb.close()
    }
}
