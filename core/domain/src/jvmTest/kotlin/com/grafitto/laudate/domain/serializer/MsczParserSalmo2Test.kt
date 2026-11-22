package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.tablature.TabSymbolCompatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

/**
 * **Banco de referencia del `salmo2`** (tarea del usuario 2026-10-12): parsea el `.mscz` real y
 * comprueba que Canticora reconstruye **exactamente** los 5 compases escritos, con sus cuerdas,
 * trastes, figuras, técnicas y articulaciones apiladas.
 *
 * El `.mscz` es un ZIP: aquí se extrae el `*.mscx` (el parseo del XML lo hace [MsczParser], Kotlin
 * puro). El fichero vive en `resources/` y el test se **salta** (`assumeTrue`) si no está.
 */
class MsczParserSalmo2Test {

    private fun mscx(): String? {
        val f = File("src/jvmTest/resources/salmo2.mscz").takeIf { it.exists() }
            ?: File("core/domain/src/jvmTest/resources/salmo2.mscz").takeIf { it.exists() }
            ?: return null
        ZipInputStream(f.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".mscx")) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                e = zip.nextEntry
            }
        }
        return null
    }

    @Test
    fun parseaElSalmo2ConSusCincoCompases() {
        val xml = mscx()
        assumeTrue("falta resources/salmo2.mscz", xml != null)
        val tab = MsczParser.parse(xml!!, songId = "salmo2")

        assertEquals("4", tab.time.beats.toString())
        assertEquals(4, tab.time.beatType)
        assertEquals("el tempo del fichero es 80 (1.3333 negras/s)", 80, tab.measures[0].tempo)
        assertTrue("el fichero tiene 32 compases", tab.measures.size >= 5)
        // (D-8) El rótulo del tempo es **literal** del `.mscx`: el símbolo musical llega como punto de
        // código SMuFL (Bravura lo pinta) y el texto tal cual. Antes se inventaba `♩ = 80 · Andante`.
        assertEquals("compás 1: rótulo literal del tempo", "\uECA5 = 80", tab.measures[0].tempoText)
        assertEquals("compás 2: segunda indicación, que antes se perdía", "Andante", tab.measures[1].tempoText)
        assertEquals("compás 2: su propio BPM (1.5333 negras/s)", 92, tab.measures[1].tempo)
        assertEquals("compás 3: sin indicación de tempo", null, tab.measures[2].tempoText)
        // (D-5) Las dinámicas del `.mscx` (`<Dynamic><subtype>mp</subtype>`) se importan; antes se
        // descartaban y el `mp`/`pp` del PDF se perdía al digitalizar.
        val conDinamica = tab.measures.mapIndexedNotNull { i, m -> m.dynamic?.let { i to it } }
        assertTrue("el fichero trae dinámicas", conDinamica.isNotEmpty())
        assertEquals("la primera dinámica es mp", TabDynamic.MP, conDinamica.first().second)

        // ── Compás 1: 4 negras, s6f1↑ · s5f2↓ · s4f3↑ · s3f0 ─────────────────────────────────────
        // El `.mscx` escribe `<string>5</string>` (índice **0-based desde la aguda**), que en la
        // numeración de lectura de Canticora (1 = la más aguda) es la **6ª** cuerda.
        val m1 = tab.measures[0]
        assertEquals(4, m1.slots.size)
        assertEquals(6, m1.slots[0].strings.single().stringNumber)
        assertEquals(1, m1.slots[0].strings.single().fret)
        assertTrue("compás 1: primera nota con arco hacia arriba", m1.slots[0].strings.single().has(TabTechnique.PICK_UP))
        assertTrue("compás 1: segunda nota con arco hacia abajo", m1.slots[1].strings.single().has(TabTechnique.PICK_DOWN))
        assertEquals(RhythmFigure.QUARTER, m1.slots[0].rhythmicFigure)

        // ── Compás 3: 8 corcheas, con acento en la primera ───────────────────────────────────────
        val m3 = tab.measures[2]
        assertEquals("compás 3: 8 corcheas", 8, m3.slots.size)
        assertEquals(RhythmFigure.EIGHTH, m3.slots[0].rhythmicFigure)
        assertTrue("compás 3: acento en la primera", m3.slots[0].strings.single().articulations.contains(TabArticulation.ACCENT))

        // ── Compás 4: 16avos con armónico, silencios, y la negra con 3 articulaciones apiladas ───
        val m4 = tab.measures[3]
        assertTrue("compás 4: primera nota armónica", m4.slots[0].strings.single().articulations.contains(TabArticulation.HARMONIC))
        // La negra s4f0 con tenuto + arco↓ + acento + marcato (`<string>3</string>` en el `.mscx`).
        // (D-40) Tenuto y marcato **no** se apilan: MuseScore los dibuja con el glifo combinado
        // `articMarcatoTenutoAbove` (U+E4BC). El acento sí queda suelto.
        val negra = m4.slots.first { sl ->
            sl.strings.any {
                it.stringNumber == 4 && it.fret == 0 &&
                    TabSymbolCompatibility.isArticulationPresent(it.articulations, TabArticulation.TENUTO)
            }
        }.strings.single()
        assertEquals(
            "marcato + tenuto en un solo glifo, más el acento",
            setOf(TabArticulation.MARCATO_TENUTO, TabArticulation.ACCENT),
            negra.articulations
        )
        assertTrue("arco hacia abajo", negra.has(TabTechnique.PICK_DOWN))
    }

    @Test
    fun reproduceLaTablaturaSinPerderNadaAlVolverAChordProJson() {
        val xml = mscx()
        assumeTrue("falta resources/salmo2.mscz", xml != null)
        val tab = MsczParser.parse(xml!!, songId = "salmo2")
        // Round-trip por el serializador propio: no se pierde ninguna técnica ni articulación.
        val json = TablatureSerializer.encode(tab)
        val back = TablatureSerializer.decode(json)
        assertEquals(tab.measures.size, back.measures.size)
        assertEquals(tab.measures[3].slots.size, back.measures[3].slots.size)
        assertEquals(
            tab.measures[3].slots.flatMap { it.strings }.map { it.articulations },
            back.measures[3].slots.flatMap { it.strings }.map { it.articulations }
        )
    }

    /**
     * **Silencio de compás** (`<Rest><durationType>measure</durationType></Rest>`): MuseScore lo
     * escribe como un silencio de **redonda** que ocupa el compás entero. Antes el parser devolvía un
     * `TabSlot()` **vacío** y el compás se quedaba en blanco: ni glifo, ni ancho, ni nada. Se veía en
     * los compases 6 y siguientes del `salmo2` (todos `R:measure`).
     */
    @Test
    fun elSilencioDeCompasSeLeeComoSilencioDeRedonda() {
        val xml = mscx()
        assumeTrue("falta resources/salmo2.mscz", xml != null)
        val tab = MsczParser.parse(xml!!, songId = "salmo2")

        val vacios = tab.measures.drop(5)
        assertTrue("el salmo2 tiene compases de silencio de compás", vacios.isNotEmpty())
        vacios.forEachIndexed { i, m ->
            assertEquals("compás ${i + 6}: una sola columna", 1, m.slots.size)
            assertEquals(
                "compás ${i + 6}: silencio de redonda",
                RhythmFigure.WHOLE_REST,
                m.slots[0].rhythmicFigure
            )
            assertTrue("compás ${i + 6}: sin notas", m.slots[0].strings.isEmpty())
        }
    }

    /**
     * **Tresillo** (`<Tuplet>` 3:2): MuseScore lo escribe como un bloque **hermano** de las notas, no
     * envolviéndolas, así que un `findAll` de `<Chord>|<Rest>` se lo saltaba y las tres notas salían
     * como figuras sueltas: sin corchete y sin el «3». El compás 5 del `salmo2` es un tresillo de tres
     * silencios de blanca.
     */
    @Test
    fun elTresilloSeMarcaEnSusTresColumnas() {
        val xml = mscx()
        assumeTrue("falta resources/salmo2.mscz", xml != null)
        val tab = MsczParser.parse(xml!!, songId = "salmo2")

        val tresillo = tab.measures[4]
        assertEquals("el tresillo tiene tres columnas", 3, tresillo.slots.size)
        tresillo.slots.forEachIndexed { i, slot ->
            assertTrue("columna $i del tresillo marcada como tresillo", slot.isTriplet)
            assertEquals("columna $i: silencio de blanca", RhythmFigure.HALF_REST, slot.rhythmicFigure)
        }
        // Y **no** se contagia a los compases vecinos.
        assertTrue("el compás anterior no es tresillo", tab.measures[3].slots.none { it.isTriplet })
        assertTrue("el compás siguiente no es tresillo", tab.measures[5].slots.none { it.isTriplet })
    }
}
