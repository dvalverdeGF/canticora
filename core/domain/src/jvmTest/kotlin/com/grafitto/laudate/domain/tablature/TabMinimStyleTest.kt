package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry.StemDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Cómo se dibuja la blanca** en el pentagrama rítmico: el `TablatureMinimStyle` de MuseScore
 * (`src/engraving/dom/stafftype.h:87-91`), con sus tres valores.
 *
 * En una tablatura la plica no nace de una cabeza de nota, así que la blanca —la única figura con
 * plica y sin corchete— se confundiría con la negra. MuseScore lo resuelve de tres maneras y las tres
 * están en sus presets (`stafftype.cpp:946-967`): sin plica (`Simple`), con la plica a la mitad
 * (`Common`) o con dos barras inclinadas (`Full`).
 */
class TabMinimStyleTest {

    private val unit = 20f
    private val headY = 200f

    private fun primitivas(
        figure: RhythmFigure,
        minimStyle: MinimStyle,
        direction: StemDirection = StemDirection.DOWN
    ): List<TabRhythmPrimitive> = TabRhythmPrimitives.of(
        marks = listOf(TabRhythmMark(figure = figure, x = 100f, y = headY)),
        staffSpace = unit,
        stemDirection = direction,
        minimStyle = minimStyle
    )

    private fun plicas(primitivas: List<TabRhythmPrimitive>): List<TabRhythmPrimitive.Line> =
        primitivas.filterIsInstance<TabRhythmPrimitive.Line>()

    private fun barras(primitivas: List<TabRhythmPrimitive>): List<TabRhythmPrimitive.Slash> =
        primitivas.filterIsInstance<TabRhythmPrimitive.Slash>()

    @Test
    fun sinPlicaLaBlancaNoLlevaNingunTrazo() {
        // `Chord::shouldHaveStem()` (`chord.cpp:1074`) devuelve `false` para `V_HALF` en tablatura
        // con `minimStyle == NONE`: la figura se queda **sin símbolo** y solo la cifra dice la altura.
        assertTrue(
            "con `NONE` la blanca no puede llevar plica",
            plicas(primitivas(RhythmFigure.HALF, MinimStyle.NONE)).isEmpty()
        )
        assertTrue(
            "con `NONE` la blanca tampoco lleva barras inclinadas",
            barras(primitivas(RhythmFigure.HALF, MinimStyle.NONE)).isEmpty()
        )
    }

    @Test
    fun laPlicaCortaMideLaMitad() {
        // `StemLayout::tabStemLength` (`stemlayout.cpp:299-301`): `3,0 × STAFFTYPE_TAB_SHORTSTEMRATIO`.
        val plica = plicas(primitivas(RhythmFigure.HALF, MinimStyle.SHORTER)).single()
        val largo = plica.y2 - plica.y1
        assertEquals(
            "la plica de la blanca mide la mitad que la de una negra",
            unit * TabRhythmGeometry.STEM_LENGTH * TabRhythmGeometry.SHORT_STEM_RATIO,
            largo,
            0.01f
        )
    }

    @Test
    fun laPlicaCortaArrancaMediaPlicaMasAbajoYElExtremoLibreNoSeMueve() {
        // `StemLayout::tabRestStemPosY` (`stemlayout.cpp:266-274`): el arranque se desplaza lo mismo
        // que se acorta la plica, así que el **extremo libre** queda donde el de una plica normal.
        val negra = plicas(primitivas(RhythmFigure.QUARTER, MinimStyle.SHORTER)).single()
        val blanca = plicas(primitivas(RhythmFigure.HALF, MinimStyle.SHORTER)).single()
        assertEquals(
            "el extremo libre de la plica corta coincide con el de la plica normal",
            negra.y2,
            blanca.y2,
            0.01f
        )
        assertEquals(
            "el arranque baja media plica",
            negra.y1 + unit * TabRhythmGeometry.STEM_LENGTH * TabRhythmGeometry.SHORT_STEM_RATIO,
            blanca.y1,
            0.01f
        )
    }

    @Test
    fun laBlancaLlevaDosBarrasInclinadas() {
        // `TDraw::draw(const Stem*)` (`tdraw.cpp:2887-2905`): dos paralelogramos, no uno.
        val barras = barras(primitivas(RhythmFigure.HALF, MinimStyle.SLASHED))
        assertEquals("la blanca rayada lleva exactamente dos barras", 2, barras.size)
        barras.forEach { barra ->
            assertEquals(unit * TabRhythmGeometry.SLASH_WIDTH, barra.width, 0.01f)
            assertEquals(unit * TabRhythmGeometry.SLASH_THICKNESS, barra.thickness, 0.01f)
            assertEquals(unit * TabRhythmGeometry.SLASH_SLANT, barra.slant, 0.01f)
        }
        assertEquals(
            "la segunda barra va `SLASH_DISPL` espacios más abajo",
            unit * TabRhythmGeometry.SLASH_DISPLACEMENT,
            barras[1].y - barras[0].y,
            0.01f
        )
    }

    @Test
    fun lasBarrasSeCentranEnLaPlica() {
        // La pareja mide `SLASH_PAIR_HEIGHT = 2,0` espacios y la plica 3,0: arranca 0,5 espacios por
        // debajo del arranque (plica hacia abajo) y su centro cae en la mitad de la plica.
        val plica = plicas(primitivas(RhythmFigure.HALF, MinimStyle.SLASHED)).single()
        val barras = barras(primitivas(RhythmFigure.HALF, MinimStyle.SLASHED))
        val centroPareja = (barras.first().y + barras.last().y +
            unit * TabRhythmGeometry.SLASH_THICKNESS + unit * TabRhythmGeometry.SLASH_SLANT) / 2f
        val centroPlica = (plica.y1 + plica.y2) / 2f
        assertEquals("la pareja de barras se centra en la plica", centroPlica, centroPareja, 0.01f)
    }

    @Test
    fun laPlicaHaciaArribaTambienSeAcortaYSeRaya() {
        val negra = plicas(
            primitivas(RhythmFigure.QUARTER, MinimStyle.SHORTER, StemDirection.UP)
        ).single()
        val blanca = plicas(
            primitivas(RhythmFigure.HALF, MinimStyle.SHORTER, StemDirection.UP)
        ).single()
        assertEquals(
            "con la plica hacia arriba el extremo libre tampoco se mueve",
            negra.y2,
            blanca.y2,
            0.01f
        )
        assertEquals(
            "la blanca rayada lleva sus dos barras también con la plica hacia arriba",
            2,
            barras(primitivas(RhythmFigure.HALF, MinimStyle.SLASHED, StemDirection.UP)).size
        )
    }

    @Test
    fun laNegraNoCambiaConNingunEstilo() {
        // `minimStyle` solo afecta a la **blanca** (`stafftype.h:352`: «stem-and-beam durations only»).
        val referencias = plicas(primitivas(RhythmFigure.QUARTER, MinimStyle.SHORTER)).single()
        MinimStyle.entries.forEach { estilo ->
            val plica = plicas(primitivas(RhythmFigure.QUARTER, estilo)).single()
            assertEquals("la negra no cambia con `$estilo`", referencias, plica)
            assertTrue(
                "la negra nunca lleva barras inclinadas (`$estilo`)",
                barras(primitivas(RhythmFigure.QUARTER, estilo)).isEmpty()
            )
        }
    }

    @Test
    fun elSilencioDeBlancaNoCambia() {
        // El silencio no tiene plica: `minimStyle` no lo toca (MuseScore solo desplaza su plica
        // cuando la tiene, y en tablatura el silencio se dibuja con el glifo de la fuente).
        val referencias = primitivas(RhythmFigure.HALF_REST, MinimStyle.SHORTER)
        MinimStyle.entries.forEach { estilo ->
            assertEquals(
                "el silencio de blanca no cambia con `$estilo`",
                referencias,
                primitivas(RhythmFigure.HALF_REST, estilo)
            )
        }
    }

    @Test
    fun laBlancaConPuntilloSeComportaComoLaBlanca() {
        // `DOTTED_HALF` es una blanca: MuseScore compara `durationType().type() == V_HALF`, y el
        // puntillo no cambia el tipo.
        assertTrue(
            "la blanca con puntillo tampoco lleva plica con `NONE`",
            plicas(primitivas(RhythmFigure.DOTTED_HALF, MinimStyle.NONE)).isEmpty()
        )
        assertEquals(
            "la blanca con puntillo también se raya",
            2,
            barras(primitivas(RhythmFigure.DOTTED_HALF, MinimStyle.SLASHED)).size
        )
    }
}
