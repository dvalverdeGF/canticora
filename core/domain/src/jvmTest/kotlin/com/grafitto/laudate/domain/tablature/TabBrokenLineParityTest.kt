package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.serializer.MsczParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

/**
 * **Regresión**: con `LineStyle.BROKEN_ON_MARKS` cada cifra debe tener su hueco **en la misma cuerda**
 * y **a la misma x**.
 *
 * El bug que cubre este test: `brokenSegments` acumulaba el origen de las columnas desde
 * `staffLeft` (el borde del pentagrama, debajo de la clave y el compás) en vez de desde `gridLeft`
 * (donde empieza la primera columna). El desfase era `clefSpace + timeSpace` (≈ 90 px), así que la
 * línea se veía **detrás** de las cifras y el hueco caía sobre la columna anterior.
 */
class TabBrokenLineParityTest {

    private fun mscx(): String? {
        val f = listOf(
            File("src/jvmTest/resources/salmo2.mscz"),
            File("core/domain/src/jvmTest/resources/salmo2.mscz")
        ).firstOrNull { it.exists() } ?: return null
        ZipInputStream(f.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".mscx")) return zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return null
    }

    /** Huecos de línea: `(y, centroX, ancho)` de cada interrupción de una cuerda. */
    private fun gapsOf(drawing: TablatureDrawing): List<Triple<Float, Float, Float>> {
        val out = mutableListOf<Triple<Float, Float, Float>>()
        drawing.lines.filter { !it.isBar }.groupBy { it.y1 }.forEach { (y, segs) ->
            val sorted = segs.sortedBy { it.x1 }
            for (i in 0 until sorted.size - 1) {
                val left = sorted[i].x2
                val right = sorted[i + 1].x1
                if (right - left > 1f) out += Triple(y, (left + right) / 2f, right - left)
            }
        }
        return out
    }

    @Test
    fun cadaCifraTieneSuHuecoEnLaMismaCuerdaYAlineado() {
        val xml = mscx()
        assumeTrue("falta salmo2.mscz", xml != null)
        val tab = MsczParser.parse(xml!!, songId = "salmo2")
        val settings = TabNotationScale(
            staffSpacePt = 34f / TabRhythmGeometry.STRING_SPACING_SPACES,
            systemGapPt = 14f * 1.7f
        ).toLayoutSettings(staff = TabStaffOptions())
        val systems = TablatureSystemsLayout.layout(tab, maxWidth = 2560f, settings = settings)

        var checked = 0
        systems.forEach { system ->
            val drawing = system.drawing
            val gaps = gapsOf(drawing)
            drawing.texts.filter { it.kind == TabTextKind.FRET }.forEach { digit ->
                val sameString = gaps.filter { kotlin.math.abs(it.first - digit.y) < 0.5f }
                val match = sameString.minByOrNull { kotlin.math.abs(it.second - digit.x) }
                assertTrue(
                    "la cifra '${digit.text}' (x=${digit.x}, y=${digit.y}) no tiene hueco en su cuerda",
                    match != null
                )
                assertEquals(
                    "el hueco de la cifra '${digit.text}' (y=${digit.y}) está desalineado",
                    digit.x,
                    match!!.second,
                    0.5f
                )
                checked++
            }
        }
        assertTrue("no se ha comprobado ninguna cifra", checked > 20)
    }
}
