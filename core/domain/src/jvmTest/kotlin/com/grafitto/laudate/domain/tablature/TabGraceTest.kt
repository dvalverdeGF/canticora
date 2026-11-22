package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabGrace
import com.grafitto.laudate.domain.model.tablature.TabGraceType
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Notas de adorno** (*grace notes*): la cifra **pequeña** que se toca antes de la principal, como
 * la tecla `/` de MuseScore.
 *
 * En MuseScore el adorno es un acorde propio colgado del principal (`Chord::graceNotesBefore()`),
 * dibujado a `graceNoteMag = 0,7` (`styledef.cpp:524`) y separado `minNoteDistance · 0,7` = 0,245
 * espacios (`chordlayout.cpp:621-635`). En tablatura **no** lleva barra de acciaccatura: la tablatura
 * es *stemless* y `TLayout::layoutStemSlash` exige un asta.
 */
class TabGraceTest {

    private fun base(measureCount: Int = 2): List<TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
        measures = measures
    )

    /** Casilla 0 del compás 0 con una nota en la cuerda 3, traste 5. */
    private fun withNote(): List<TabMeasure> = TablatureEditing.setPluck(
        base(), 0, 0, TabPluck(TabPosition(3, 5))
    )

    // ── Modelo y edición ────────────────────────────────────────────────────────────────────────

    @Test
    fun addGrace_clonaLaNotaMasAgudaDeLaCasilla() {
        val updated = TablatureEditing.addGrace(withNote(), 0, 0)
        val grace = updated[0].slots[0].graces.single()
        assertEquals(3, grace.stringNumber)
        assertEquals(5, grace.fret)
        assertEquals(TabGraceType.ACCIACCATURA, grace.type)
    }

    @Test
    fun addGrace_enUnaCasillaVaciaUsaLaCuerda1YElTraste0() {
        val updated = TablatureEditing.addGrace(base(), 0, 0)
        val grace = updated[0].slots[0].graces.single()
        assertEquals(1, grace.stringNumber)
        assertEquals(0, grace.fret)
    }

    @Test
    fun addGrace_dosVecesAnadeDosAdornosYElNuevoQuedaElUltimo() {
        val updated = TablatureEditing.addGrace(
            TablatureEditing.addGrace(withNote(), 0, 0), 0, 0, TabGraceType.APPOGGIATURA
        )
        val graces = updated[0].slots[0].graces
        assertEquals(2, graces.size)
        // El **último** de la lista es el que se toca pegado a la nota principal.
        assertEquals(TabGraceType.APPOGGIATURA, graces.last().type)
    }

    @Test
    fun removeLastGrace_quitaSoloElUltimo() {
        val two = TablatureEditing.addGrace(
            TablatureEditing.addGrace(withNote(), 0, 0), 0, 0, TabGraceType.APPOGGIATURA
        )
        val one = TablatureEditing.removeLastGrace(two, 0, 0)
        assertEquals(1, one[0].slots[0].graces.size)
        assertEquals(TabGraceType.ACCIACCATURA, one[0].slots[0].graces.single().type)
    }

    @Test
    fun removeLastGrace_sinAdornosNoCambiaNada() {
        val measures = withNote()
        assertEquals(measures, TablatureEditing.removeLastGrace(measures, 0, 0))
    }

    @Test
    fun clearGraces_losQuitaTodos() {
        val two = TablatureEditing.addGrace(
            TablatureEditing.addGrace(withNote(), 0, 0), 0, 0
        )
        assertTrue(TablatureEditing.clearGraces(two, 0, 0)[0].slots[0].graces.isEmpty())
    }

    @Test
    fun addGrace_enUnaCasillaQueNoExisteNoRompe() {
        val measures = base()
        assertEquals(measures, TablatureEditing.addGrace(measures, 9, 9))
    }

    // ── Geometría (los números de MuseScore) ────────────────────────────────────────────────────

    @Test
    fun elTamanoDelAdornoEsElDeMuseScore() {
        assertEquals(0.7f, TabGraceGeometry.MAG, 0.0001f)
        assertEquals(0.35f, TabGraceGeometry.MIN_DISTANCE_SPACES, 0.0001f)
    }

    @Test
    fun elPasoEntreAdornosEsMediaCifraMasElAireDeMuseScoreMasMediaCifraPequena() {
        // 0,5 + 0,35 · 0,7 + 0,5 · 0,7 = 0,5 + 0,245 + 0,35 = 1,095 espacios.
        assertEquals(1.095f, TabGraceGeometry.STEP_SPACES, 0.0001f)
    }

    @Test
    fun elAnchoExtraCreceLinealmenteConElNumeroDeAdornos() {
        assertEquals(0f, TabGraceGeometry.extraWidth(0, 20f), 0.0001f)
        assertEquals(1.095f * 20f, TabGraceGeometry.extraWidth(1, 20f), 0.01f)
        assertEquals(2f * 1.095f * 20f, TabGraceGeometry.extraWidth(2, 20f), 0.01f)
    }

    @Test
    fun elPrimerAdornoQuedaAPasoYMedioDeLaCifraPrincipal() {
        assertEquals(TabGraceGeometry.STEP_SPACES, TabGraceGeometry.offsetSpaces(0), 0.0001f)
        assertEquals(2f * TabGraceGeometry.STEP_SPACES, TabGraceGeometry.offsetSpaces(1), 0.0001f)
    }

    // ── Maquetación ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun elAdornoEnsanchaLaColumnaYSeDibujaComoCifraPequena() {
        val settings = TabLayoutSettings(cellWidth = 20f, stringSpacing = 20f, labelWidth = 20f)
        val plain = tablature(withNote())
        val graced = tablature(TablatureEditing.addGrace(withNote(), 0, 0))
        val plainWidth = TablatureSystemsLayout.layout(plain, 2000f, settings)
            .first().drawing.measureBounds.first().let { it.right - it.left }
        val gracedWidth = TablatureSystemsLayout.layout(graced, 2000f, settings)
            .first().drawing.measureBounds.first().let { it.right - it.left }
        // La columna crece exactamente el ancho extra del adorno (1,095 espacios = 14,6 px).
        assertEquals(TabGraceGeometry.extraWidth(1, settings.staffSpace), gracedWidth - plainWidth, 0.5f)
    }

    @Test
    fun elAdornoSeEmiteComoTextoDeTipoGraceALaIzquierdaDeLaCifra() {
        val settings = TabLayoutSettings(cellWidth = 20f, stringSpacing = 20f, labelWidth = 20f)
        val drawing = TablatureSystemsLayout.layout(
            tablature(TablatureEditing.addGrace(withNote(), 0, 0)), 2000f, settings
        ).first().drawing
        val grace = drawing.texts.single { it.kind == TabTextKind.GRACE }
        val fret = drawing.texts.single { it.kind == TabTextKind.FRET }
        assertEquals("5", grace.text)
        // A la izquierda, y a `STEP_SPACES` del centro de la cifra principal.
        assertEquals(
            TabGraceGeometry.STEP_SPACES * settings.staffSpace,
            fret.x - grace.x,
            0.5f
        )
    }

    @Test
    fun elAdornoVaEnLaCuerdaQueDeclara() {
        val settings = TabLayoutSettings(cellWidth = 20f, stringSpacing = 20f, labelWidth = 20f)
        val measures = TablatureEditing.addGrace(withNote(), 0, 0)
        val drawing = TablatureSystemsLayout.layout(tablature(measures), 2000f, settings)
            .first().drawing
        val grace = drawing.texts.single { it.kind == TabTextKind.GRACE }
        val fret = drawing.texts.single { it.kind == TabTextKind.FRET }
        // Misma cuerda ⇒ misma `y` (la cifra principal está en la cuerda 3).
        assertEquals(fret.y, grace.y, 0.01f)
    }

    @Test
    fun sinAdornosNoSeEmiteNingunTextoGrace() {
        val settings = TabLayoutSettings(cellWidth = 20f, stringSpacing = 20f, labelWidth = 20f)
        val drawing = TablatureSystemsLayout.layout(tablature(withNote()), 2000f, settings)
            .first().drawing
        assertTrue(drawing.texts.none { it.kind == TabTextKind.GRACE })
    }

    @Test
    fun elAdornoNoSeSaleDeSuColumna() {
        val settings = TabLayoutSettings(cellWidth = 20f, stringSpacing = 20f, labelWidth = 20f)
        val measures = TablatureEditing.addGrace(
            TablatureEditing.addGrace(withNote(), 0, 0), 0, 0
        )
        val drawing = TablatureSystemsLayout.layout(tablature(measures), 2000f, settings)
            .first().drawing
        val box = drawing.measureBounds.first()
        val graces = drawing.texts.filter { it.kind == TabTextKind.GRACE }
        assertEquals(2, graces.size)
        // Media cifra de adorno a cada lado del centro: el más a la izquierda no cruza la barra.
        val half = TabGraceGeometry.GRACE_HALF_SPACES * settings.staffSpace
        assertTrue(graces.minOf { it.x } - half >= box.left)
    }

    // ── Serialización ───────────────────────────────────────────────────────────────────────────

    @Test
    fun losAdornosSobrevivenAlGuardarYReabrir() {
        val measures = TablatureEditing.addGrace(
            TablatureEditing.addGrace(withNote(), 0, 0), 0, 0, TabGraceType.APPOGGIATURA
        )
        val json = TablatureSerializer.encode(tablature(measures))
        val back = TablatureSerializer.decode(json)
        assertNotNull(back)
        val graces = back.measures[0].slots[0].graces
        assertEquals(2, graces.size)
        assertEquals(TabGraceType.ACCIACCATURA, graces[0].type)
        assertEquals(TabGraceType.APPOGGIATURA, graces[1].type)
        assertEquals(3, graces[0].stringNumber)
        assertEquals(5, graces[0].fret)
    }

    @Test
    fun unJsonSinElCampoGracesSigueLeyendose() {
        // Compatibilidad: el campo es opcional, así que un archivo anterior no lo lleva. Se simula
        // quitándolo del JSON que produce el serializador actual.
        val json = TablatureSerializer.encode(tablature(withNote()))
            .replace(Regex(",\\s*\"graces\": \\[\\]"), "")
        assertFalse(json.contains("\"graces\""))
        val back = TablatureSerializer.decode(json)
        assertTrue(back.measures[0].slots[0].graces.isEmpty())
    }

    @Test
    fun unTipoDeAdornoDesconocidoCaeEnAcciaccatura() {
        val json = TablatureSerializer.encode(tablature(withNote()))
            .replace(
                Regex("\"graces\": \\[\\]"),
                "\"graces\": [{\"stringNumber\": 2, \"fret\": 7, \"type\": \"RARO\"}]"
            )
        val back = TablatureSerializer.decode(json)
        val grace = back.measures[0].slots[0].graces.single()
        assertEquals(TabGraceType.ACCIACCATURA, grace.type)
        assertEquals(2, grace.stringNumber)
        assertEquals(7, grace.fret)
    }

    @Test
    fun elModeloExponeSiLaCasillaLlevaAdornos() {
        assertFalse(base()[0].slots[0].hasGraces)
        assertTrue(TablatureEditing.addGrace(base(), 0, 0)[0].slots[0].hasGraces)
    }

    // ── Edición del adorno (D-63) ────────────────────────────────────────────────────────────────

    @Test
    fun addGrace_enLaCuerdaActivaNoClonaLaMasAguda() {
        // La casilla tiene notas en la 1 y en la 5; el cursor está en la 5. El adorno debe nacer en
        // la 5, no en la 1: antes siempre se clonaba la más aguda y había que moverlo a mano.
        val measures = TablatureEditing.setPluck(
            TablatureEditing.setPluck(base(), 0, 0, TabPluck(TabPosition(1, 3))),
            0, 0, TabPluck(TabPosition(5, 7))
        )
        val grace = TablatureEditing.addGrace(measures, 0, 0, stringNumber = 5)
            .let { it[0].slots[0].graces.single() }
        assertEquals(5, grace.stringNumber)
        assertEquals(7, grace.fret)
    }

    @Test
    fun addGrace_sinCuerdaActivaSigueClonandoLaMasAguda() {
        val measures = TablatureEditing.setPluck(
            TablatureEditing.setPluck(base(), 0, 0, TabPluck(TabPosition(1, 3))),
            0, 0, TabPluck(TabPosition(5, 7))
        )
        val grace = TablatureEditing.addGrace(measures, 0, 0)[0].slots[0].graces.single()
        assertEquals(1, grace.stringNumber)
        assertEquals(3, grace.fret)
    }

    @Test
    fun addGrace_enCasillaVaciaUsaLaCuerdaActivaConTrasteCero() {
        val grace = TablatureEditing.addGrace(base(), 0, 0, stringNumber = 4)
            .let { it[0].slots[0].graces.single() }
        assertEquals(4, grace.stringNumber)
        assertEquals(0, grace.fret)
    }

    @Test
    fun setGraceFret_cambiaElTrasteDelAdorno() {
        val withGrace = TablatureEditing.addGrace(withNote(), 0, 0)
        val updated = TablatureEditing.setGraceFret(withGrace, 0, 0, 0, 9)
        assertEquals(9, updated[0].slots[0].graces.single().fret)
        // La nota principal no se toca: el adorno es una nota aparte.
        assertEquals(5, updated[0].slots[0].strings.single().fret)
    }

    @Test
    fun setGraceFret_acotaAlRangoDeTrastes() {
        val withGrace = TablatureEditing.addGrace(withNote(), 0, 0)
        assertEquals(
            MAX_FRET,
            TablatureEditing.setGraceFret(withGrace, 0, 0, 0, 99)[0].slots[0].graces.single().fret
        )
        assertEquals(
            0,
            TablatureEditing.setGraceFret(withGrace, 0, 0, 0, -3)[0].slots[0].graces.single().fret
        )
    }

    @Test
    fun setGraceString_mueveElAdornoDeCuerda() {
        val withGrace = TablatureEditing.addGrace(withNote(), 0, 0)
        val updated = TablatureEditing.setGraceString(withGrace, 0, 0, 0, 6)
        assertEquals(6, updated[0].slots[0].graces.single().stringNumber)
    }

    @Test
    fun setGraceString_acotaALaPrimeraCuerda() {
        val withGrace = TablatureEditing.addGrace(withNote(), 0, 0)
        assertEquals(
            1,
            TablatureEditing.setGraceString(withGrace, 0, 0, 0, 0)[0].slots[0].graces.single().stringNumber
        )
    }

    @Test
    fun editarUnAdornoInexistenteNoRompeNada() {
        val measures = withNote()
        assertEquals(measures, TablatureEditing.setGraceFret(measures, 0, 0, 3, 7))
        assertEquals(measures, TablatureEditing.setGraceString(measures, 0, 0, 3, 2))
    }

    @Test
    fun tabGracePorDefectoEsAcciaccatura() {
        assertEquals(TabGraceType.ACCIACCATURA, TabGrace(1, 0).type)
    }
}
