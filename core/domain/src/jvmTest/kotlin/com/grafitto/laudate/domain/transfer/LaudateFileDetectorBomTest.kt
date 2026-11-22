package com.grafitto.laudate.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regresión: muchos programas de Windows (PowerShell `Set-Content -Encoding UTF8`, Notepad…)
 * escriben un **BOM** `\uFEFF` al inicio del fichero. Sin quitarlo, un repertorio `.laudate` válido
 * no empezaba por `{` y el detector lo confundía con ChordPro. Ver ADR-013.
 */
class LaudateFileDetectorBomTest {

    private val celebrationJson =
        """{"format":"canticora.event","celebration":{"name":"Misa","items":[{}]},"songs":[]}"""

    @Test
    fun `una copia con BOM se detecta como repertorio`() {
        val detected = LaudateFileDetector.detect(text = "\uFEFF$celebrationJson", fileName = "misa.laudate")
        assertEquals(LaudateFileKind.CELEBRATION, detected.kind)
        assertEquals("Misa", detected.title)
    }

    @Test
    fun `sin BOM tambien se detecta (control)`() {
        val detected = LaudateFileDetector.detect(text = celebrationJson, fileName = "misa.laudate")
        assertEquals(LaudateFileKind.CELEBRATION, detected.kind)
    }

    @Test
    fun `stripBom solo quita el caracter inicial`() {
        assertEquals("abc", LaudateFileDetector.stripBom("\uFEFFabc"))
        assertEquals("abc", LaudateFileDetector.stripBom("abc"))
        assertEquals("", LaudateFileDetector.stripBom(""))
    }
}
