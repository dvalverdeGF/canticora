package com.grafitto.laudate.domain.tablature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **D-48** — El codec de los ajustes del pentagrama ([TabStaffOptionsCodec]).
 *
 * Lo que se protege aquí es la **compatibilidad hacia delante**: un texto guardado por una versión
 * anterior de la app debe seguir leyéndose cuando se añada un ajuste nuevo, y un texto corrupto no
 * debe impedir abrir el editor (se cae al valor por defecto, nunca a una excepción).
 */
class TabStaffOptionsCodecTest {

    @Test
    fun losQuinceAjustesSobrevivenAlViajeDeIdaYVuelta() {
        val original = TabStaffOptions(
            fretMarks = FretMarkStyle.LETTERS,
            marksPlacement = MarksPlacement.ABOVE_LINES,
            lines = LineStyle.CONTINUOUS,
            noteValues = NoteValueStyle.SYMBOLS_ABOVE,
            showRests = false,
            rhythmRepeat = RhythmRepeat.ALWAYS,
            minimStyle = MinimStyle.SLASHED,
            showTiedFret = ShowTiedFret.NONE,
            parenthesizeTiedFret = ParenthesizeTiedFret.NEVER,
            showMeasureNumbers = false,
            showClef = false,
            showTimeSignature = false,
            headerOnEverySystem = true,
            showStringLabels = true,
            stemsDown = false
        )

        assertEquals(original, TabStaffOptionsCodec.decode(TabStaffOptionsCodec.encode(original)))
    }

    @Test
    fun elTextoGuardadoLlevaLaMarcaDeVersionYLosQuinceCampos() {
        val text = TabStaffOptionsCodec.encode(TabStaffOptions())

        assertTrue(text.startsWith("v1;"))
        assertEquals(16, text.split(';').size)
        assertTrue(text.contains("showTiedFret=TIE_AND_FRET"))
        assertTrue(text.contains("parenthesizeTiedFret=START_OF_SYSTEM"))
    }

    @Test
    fun sinTextoGuardadoSeUsaElTipoCommonDeMuseScore() {
        assertEquals(TabStaffOptions(), TabStaffOptionsCodec.decode(null))
        assertEquals(TabStaffOptions(), TabStaffOptionsCodec.decode(""))
        assertEquals(TabStaffOptions(), TabStaffOptionsCodec.decode("   "))
    }

    @Test
    fun unTextoDeOtraVersionSeDescartaEntero() {
        // Sin la marca `v1` no se puede interpretar: mejor el defecto que adivinar.
        assertEquals(TabStaffOptions(), TabStaffOptionsCodec.decode("fretMarks=LETTERS;showClef=false"))
        assertEquals(TabStaffOptions(), TabStaffOptionsCodec.decode("v2;fretMarks=LETTERS"))
    }

    @Test
    fun unCampoDesconocidoNoRompeLaLectura() {
        // Es el caso de una versión futura que añada ajustes: los de hoy se leen igual.
        val text = "v1;fretMarks=LETTERS;campoDelFuturo=loQueSea;showClef=false"

        val decoded = TabStaffOptionsCodec.decode(text)

        assertEquals(FretMarkStyle.LETTERS, decoded.fretMarks)
        assertEquals(false, decoded.showClef)
    }

    @Test
    fun unValorInvalidoCaeAlDefectoDeEseCampoYNoAlDeLosDemas() {
        val text = "v1;fretMarks=INVENTADO;showClef=false;showRests=quizas"

        val decoded = TabStaffOptionsCodec.decode(text)

        assertEquals(FretMarkStyle.NUMBERS, decoded.fretMarks)
        assertEquals(true, decoded.showRests)
        // El resto del texto sí se respeta: un campo roto no invalida los demás.
        assertEquals(false, decoded.showClef)
    }

    @Test
    fun unTextoTruncadoConservaLosCamposQueSiLlegaron() {
        val text = "v1;fretMarks=LETTERS;minimStyle=SLASHED"

        val decoded = TabStaffOptionsCodec.decode(text)

        assertEquals(FretMarkStyle.LETTERS, decoded.fretMarks)
        assertEquals(MinimStyle.SLASHED, decoded.minimStyle)
        assertEquals(TabStaffOptions().rhythmRepeat, decoded.rhythmRepeat)
    }

    @Test
    fun losTokensSinSignoIgualSeIgnoranSinLanzar() {
        val decoded = TabStaffOptionsCodec.decode("v1;;basura;=sinClave;showClef=false")

        assertEquals(false, decoded.showClef)
        assertEquals(TabStaffOptions().fretMarks, decoded.fretMarks)
    }

    @Test
    fun losPresetsDeMuseScoreSeDistinguenAlCodificarse() {
        val simple = TabStaffOptionsCodec.encode(TabStaffOptions.SIMPLE)
        val common = TabStaffOptionsCodec.encode(TabStaffOptions.COMMON)
        val full = TabStaffOptionsCodec.encode(TabStaffOptions.FULL)

        assertNotEquals(simple, common)
        assertNotEquals(common, full)
        assertEquals(TabStaffOptions.SIMPLE, TabStaffOptionsCodec.decode(simple))
        assertEquals(TabStaffOptions.FULL, TabStaffOptionsCodec.decode(full))
    }
}
