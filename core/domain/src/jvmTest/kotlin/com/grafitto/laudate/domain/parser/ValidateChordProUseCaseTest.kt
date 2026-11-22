package com.grafitto.laudate.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateChordProUseCaseTest {

    private val useCase = ValidateChordProUseCase()

    @Test
    fun emptyContent_isInvalid() {
        val result = useCase("   ")
        assertFalse(result.isValid)
        assertNull(result.song)
        assertTrue(result.errors.any { it.message.contains("vacío") })
    }

    @Test
    fun lyricsWithoutChords_isValidWithWarning() {
        val result = useCase(
            """
            {title: Solo letra}
            {sov}
            Cantamos al Señor
            {eov}
            """.trimIndent()
        )

        assertTrue(result.isValid)
        assertNotNull(result.song)
        assertTrue(result.warnings.any { it.message.contains("acordes") })
    }

    @Test
    fun validChordPro_parsesSong() {
        val result = useCase(
            """
            {title: Juntos como hermanos}
            {key: G}
            {soc}
            [G]Juntos como her[C]manos
            {eoc}
            """.trimIndent()
        )

        assertTrue(result.isValid)
        assertEquals("Juntos como hermanos", result.song?.title)
        assertEquals("G", result.song?.originalKey)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun unbalancedClose_isReportedAsError() {
        val result = useCase(
            """
            {title: Desbalanceado}
            {eov}
            [C]Texto suelto
            """.trimIndent()
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("sin apertura") })
    }

    @Test
    fun unclosedSection_isWarning() {
        val result = useCase(
            """
            {title: Sin cerrar}
            {sov}
            [C]Texto
            """.trimIndent()
        )

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.message.contains("sin cerrar") })
    }

    @Test
    fun reportLineNumbersForErrors() {
        val result = useCase(
            """
            {title: Líneas}

            {eoc}
            [C]Texto
            """.trimIndent()
        )

        assertFalse(result.isValid)
        val issue = result.errors.first()
        assertEquals(3, issue.lineNumber)
    }
}
