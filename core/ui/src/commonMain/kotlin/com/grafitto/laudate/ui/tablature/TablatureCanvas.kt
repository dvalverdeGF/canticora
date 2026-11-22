package com.grafitto.laudate.ui.tablature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.tablature.NoteValueStyle
import com.grafitto.laudate.domain.tablature.TabArticulationStack
import com.grafitto.laudate.domain.tablature.TabEngraving
import com.grafitto.laudate.domain.tablature.TabGraceGeometry
import com.grafitto.laudate.domain.tablature.TabGlyphs
import com.grafitto.laudate.domain.tablature.TabLayoutSettings
import com.grafitto.laudate.domain.tablature.TabNoteRef
import com.grafitto.laudate.domain.tablature.TabNoteSelection
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import com.grafitto.laudate.domain.tablature.TabSystem
import com.grafitto.laudate.domain.tablature.TabTextAlign
import com.grafitto.laudate.domain.tablature.TabTextKind
import com.grafitto.laudate.domain.tablature.TabTextMetrics
import com.grafitto.laudate.domain.tablature.TabVoiceColors
import com.grafitto.laudate.domain.tablature.TablatureDrawing
import com.grafitto.laudate.domain.tablature.TablatureSystemsLayout
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/**
 * **Tamaño de la clave `TAB`** en espacios de pentagrama (em de la fuente musical).
 *
 * La constante canónica vive en el Dominio ([TabGlyphs.TAB_CLEF_EM]) porque el **PDF** dibuja la
 * misma clave: aquí solo se aliasa para no repetir el número.
 */
private const val TAB_CLEF_EM: Float = TabGlyphs.TAB_CLEF_EM

/**
 * **Tamaño del compás** (`4/4`) en espacios de pentagrama.
 *
 * La constante canónica vive en el Dominio ([TabGlyphs.TIME_SIGNATURE_EM]) porque el **PDF** dibuja
 * la misma fracción.
 */
private const val TIME_SIGNATURE_EM: Float = TabGlyphs.TIME_SIGNATURE_EM

/**
 * `y` del **borde superior** de un glifo musical para que su **tinta** quede centrada en [centerY].
 *
 * Los glifos SMuFL se dibujan con la **línea base** en `topLeft.y + layout.getLineBaseline(0)`, y su
 * **caja de línea** (4,024 em en Bravura: `hhea.ascent = 2,012`, `descent = −2,012`) **no** coincide
 * con la **caja de tinta**. La clave `TAB`, por ejemplo, tiene su tinta centrada a **+0,008 em** de la
 * línea base, mientras que el centro de la caja de línea está a **−2,012 em**: anclar por
 * `size.height * 0.62f` la dejaba **0,49 em demasiado alta** (45 px a escala 1,7). MuseScore ancla por
 * **tinta**, y aquí se hace igual.
 *
 * Si no se conoce la tinta del glifo, se cae al centro de la caja de línea (comportamiento anterior).
 */
internal fun inkTopLeftY(
    layout: androidx.compose.ui.text.TextLayoutResult,
    centerY: Float,
    emPx: Float,
    ink: com.grafitto.laudate.domain.tablature.TabGlyphs.Ink?
): Float {
    val baseline = layout.getLineBaseline(0)
    // (D-77) Si el glifo no está tabulado se asume [TabGlyphs.FALLBACK_INK] (media em sobre la línea
    // base) en vez del centro de la **caja de línea**: en Bravura esa caja mide 4,024 em, así que el
    // glifo salía a la cuarta parte de su tamaño y descentrado.
    val centerFromBaseline = (ink ?: com.grafitto.laudate.domain.tablature.TabGlyphs.FALLBACK_INK)
        .centerFromBaseline
    return centerY - baseline + centerFromBaseline * emPx
}

/**
 * `y` del **borde superior** de un glifo musical para que su **línea base** caiga en [baselineY].
 *
 * Es el anclaje de los **corchetes**: en SMuFL el origen de `flag8thDown`/`flag16thDown` está en el
 * **punto de unión con la plica**, así que basta con poner la línea base en el extremo de la plica y
 * el corchete queda **pegado** a ella (medido del `salmo2.pdf`: la tinta del corchete arranca 0,24 pt
 * por debajo del extremo de la plica y sube 16,08 pt, exactamente `y1 = 0,812 em`).
 */
internal fun inkBaselineTopLeftY(
    layout: androidx.compose.ui.text.TextLayoutResult,
    baselineY: Float
): Float = baselineY - layout.getLineBaseline(0)

/**
 * Una **pulsacion** sobre el pentagrama, ya resuelta a una celda del modelo. La produce
 * [TablatureCanvas] a partir de su propia maquetacion, asi que **cae exactamente donde el pintor
 * dibujo la cifra**: el editor no vuelve a medir nada por su cuenta.
 *
 * [slotIndex] puede valer `slots.size` cuando el toque cae en el hueco del final del compas (la
 * **columna fantasma** donde se escribe la nota siguiente).
 */
data class TabCanvasHit(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int) {
    /**
     * `true` cuando la pulsación cayó **fuera de las cuerdas** (en el margen del compás). MuseScore
     * usa ese gesto para **seleccionar el compás entero** en vez de una nota, y es la única forma de
     * hacerlo con el ratón sin tocar el teclado.
     */
    val isMeasureLevel: Boolean get() = stringNumber <= 0
}

/**
 * **Resolvedor de posiciones** del lienzo: convierte una posición en **coordenadas raíz** (la de la
 * ventana) en la celda del modelo que hay debajo. Lo instala [TablatureCanvas] y lo consume el
 * **arrastre de celdas de paleta** (B-13): al soltar una alteración o una técnica sobre la partitura,
 * el símbolo se aplica a la nota que hay **debajo del dedo**, no a la que estuviera seleccionada.
 *
 * Es un objeto **estable** y sin estado propio: el lienzo lo rellena en cada composición con su
 * maquetación vigente, así que nunca resuelve con geometría vieja.
 */
@Stable
class TabCanvasHitTester {
    internal var resolve: ((Offset) -> TabCanvasHit?)? = null

    /** Celda bajo [rootPosition] (coordenadas raíz), o `null` si no hay ninguna. */
    operator fun invoke(rootPosition: Offset): TabCanvasHit? = resolve?.invoke(rootPosition)
}

/**
 * **Modificadores** con los que se ha pulsado el lienzo. MuseScore distingue el clic simple (mueve el
 * cursor), el `Mayús`+clic (extiende la selección de notas) y el `Ctrl`+clic (alterna una nota en la
 * selección). Se pasan **resueltos** a [TabCanvasHit] para que el editor no tenga que adivinar.
 */
data class TabCanvasModifiers(
    val shift: Boolean = false,
    val ctrl: Boolean = false
)

/**
 * Desenlace de una **pulsacion** sobre el lienzo. Se resuelve de forma **explicita** porque los
 * cuatro casos no son intercambiables: levantar el dedo es un clic, moverse es un arrastre, agotar
 * el tiempo es una pulsacion larga y cancelar no es nada.
 */
internal enum class TabTapOutcome { UP, MOVED, LONG_PRESS, CANCELLED }

/**
 * Espera al desenlace de la pulsacion iniciada en [down] y lo devuelve sin ambiguedad.
 *
 * **Por que no se usa `awaitLongPressOrCancellation`** (D-78): esa funcion delega en
 * `waitForUpOrCancellation`, que **consume** el evento de levantamiento antes de devolverlo. La
 * comprobacion posterior `up.isConsumed` daba por tanto **siempre** `true` y el clic no llegaba nunca
 * a `onHit`: pulsar una cifra no movia el cursor. Aqui se observan los eventos **sin consumirlos**,
 * de modo que el detector de arrastre sigue viendolos y el de pulsacion decide por su cuenta.
 */
internal suspend fun AwaitPointerEventScope.awaitTabTapOutcome(
    down: PointerInputChange
): TabTapOutcome {
    val slop = viewConfiguration.touchSlop
    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
                ?: return@withTimeoutOrNull TabTapOutcome.CANCELLED
            if (change.changedToUp()) return@withTimeoutOrNull TabTapOutcome.UP
            if ((change.position - down.position).getDistance() > slop) {
                return@withTimeoutOrNull TabTapOutcome.MOVED
            }
        }
        @Suppress("UNREACHABLE_CODE")
        TabTapOutcome.CANCELLED
    }
    return outcome ?: TabTapOutcome.LONG_PRESS
}

/**
 * **Capas de interaccion** que el editor superpone al pentagrama. Son **guias de interfaz** (cursor,
 * seleccion), no notacion, asi que van con el color de la interfaz y no con la tinta de la musica.
 * El visor de lectura las deja a `null` y el resultado es exactamente el mismo pentagrama.
 */
data class TabCanvasOverlay(
    /** Compases **seleccionados** (se resaltan con un tinte muy leve). */
    val selection: Set<Int> = emptySet(),
    /** Celda con el **cursor** (marco fino): par (compas, columna). */
    val cursor: Pair<Int, Int>? = null,
    /** Cuerda del cursor (para dibujarlo solo en su fila). `null` = no dibujar el cursor. */
    val cursorString: Int? = null,
    /** **Columna fantasma** del compas donde se va a escribir la primera nota. */
    val ghostMeasure: Int? = null,
    /**
     * **Modo de entrada de notas** (ADR-020): dibuja el cursor como el **rectangulo azul** solido de
     * MuseScore (del color de la voz) en vez del marco fino gris, para que se vea donde se escribe.
     */
    val inputMode: Boolean = false,
    /**
     * **Voz activa** (1–4): el color del cursor es el de su voz ([TabVoiceColors.of]). `0` o fuera de
     * rango cae en la voz 1.
     */
    val voice: Int = 1,
    /**
     * **Notas seleccionadas** (Fase 2, ADR-020): cada una se resalta con el rectangulo del color de su
     * voz, como hace MuseScore al seleccionar una cifra. Son guias de interfaz, no notacion.
     */
    val selectedNotes: Set<TabNoteRef> = emptySet(),
    /**
     * **Cursor de reproducción** (B-9): la columna que está sonando. Se pinta como una banda tenue
     * sobre **todas** las cuerdas, del color de la reproducción, para seguir la música con la vista
     * sin confundirla con el cursor de escritura (que es de la voz activa).
     */
    val playbackCursor: Pair<Int, Int>? = null,
    /**
     * **Bucle de rango** (D-23): compases que se repiten. Se dibuja un **corchete** por encima del
     * pentagrama, del color de la reproducción, con las dos puntas hacia abajo en los extremos — el
     * mismo recurso que MuseScore para marcar el «loop playback».
     */
    val loopRange: IntRange? = null,
    /**
     * **Columnas con avisos de acorde** (F-08), como pares `(compás, columna)`. Se marca cada una con
     * un **punto rojo** por encima del pentagrama: el editor no impide escribir la combinación, solo
     * avisa de que no se puede tocar (dos notas en la misma cuerda, traste fuera del mástil, palmo
     * inalcanzable, `X` con traste).
     */
    val chordIssues: Set<Pair<Int, Int>> = emptySet()
)

/**
 * **Pintor unico de la tablatura** (S1 del plan de paridad visual).
 *
 * Dibuja **todas** las primitivas que produce la maquetacion de dominio ([TablatureSystemsLayout]):
 * lineas y barras de compas, clave `TAB`, compas, numeros de compas, cifras, simbolos de tecnica y
 * figuras de ritmo. **El editor y el visor usan este mismo composable**, de modo que no puede haber
 * dos tablaturas distintas para el mismo documento: era la causa raiz de que no coincidieran.
 *
 * Toda la **notacion** va en una tinta unica (el color de la musica) y sobre el fondo del soporte; el
 * color de interfaz queda para los overlays ([TabCanvasOverlay]).
 */
@Composable
fun TablatureCanvas(
    tablature: Tablature,
    modifier: Modifier = Modifier,
    /** Escala del dibujo (rejilla, barras y simbolos). */
    scale: Float = 1f,
    /**
     * **Multiplicador** del tamaño de las cifras de traste sobre el que ya les da la rejilla. `1f`
     * = el número escala **con** la tablatura (como MuseScore); el usuario puede subirlo o bajarlo
     * para leer de lejos en el atril sin agrandar la rejilla.
     */
    numberScale: Float = 1f,
    /** Ajustes del pentagrama (los de MuseScore): cifras, lineas, valores de nota, clave, compas... */
    options: TabStaffOptions = TabStaffOptions(),
    /** Capas de interaccion del editor (cursor, seleccion...). `null` en el visor de lectura. */
    overlay: TabCanvasOverlay? = null,
    /** Si se pasa, el pentagrama es **tocable**: cada pulsacion se resuelve a una celda del modelo. */
    onHit: ((TabCanvasHit, TabCanvasModifiers) -> Unit)? = null,
    /**
     * **Pulsacion larga** sobre una celda (raton: clic derecho; dedo: mantener pulsado). En MuseScore
     * abre el **menu contextual** de la seleccion (`Cortar`, `Copiar`, `Pegar`, `Propiedades de
     * compas`...). Con `null`, la pulsacion larga no hace nada.
     */
    onLongPress: ((TabCanvasHit) -> Unit)? = null,
    /**
     * **Arrastrar una cifra a la cuerda adyacente** sin cambiar su altura (`Ctrl`+`flecha` con el
     * raton). Recibe la celda de origen y cuantas **cuerdas** se ha movido (positivo = hacia la mas
     * grave). Es el gesto que documenta MuseScore: «Drag a fret mark up or down to an adjacent string».
     */
    onDragStrings: ((TabCanvasHit, Int) -> Unit)? = null,
    /**
     * **Selección por rectángulo** (*rubber band*): arrastrar sobre un **hueco vacío** del lienzo
     * selecciona las notas que toca el rectángulo, como en MuseScore. Arrastrar **sobre una cifra**
     * sigue moviendo la nota de cuerda ([onDragStrings]); el lienzo decide cuál de los dos gestos es
     * según dónde empieza el arrastre. Recibe la selección ya resuelta contra la geometría.
     */
    onRubberBand: ((TabNoteSelection) -> Unit)? = null,
    /**
     * **Zoom** con el pellizco (tactil) o con `Ctrl`+rueda (raton), como MuseScore (`Ctrl`+`+`/`-` y
     * `Ctrl`+rueda). Recibe el **factor** a aplicar a la escala actual (p. ej. `1.1` para acercar).
     */
    onZoom: ((Float) -> Unit)? = null,
    /** Color de la **tinta** de la musica (por defecto, el del tema). */
    inkColor: Color = MaterialTheme.colorScheme.onSurface,
    /**
     * **Resolvedor de posiciones** para el arrastre de celdas de paleta (B-13). Si se pasa, el lienzo
     * lo rellena con su maquetación vigente y el editor puede preguntar «¿qué celda hay bajo este
     * punto?» al soltar un símbolo de la paleta.
     */
    hitTester: TabCanvasHitTester? = null,
    /**
     * **Escala de «ajustar al ancho»** (D-14): el lienzo la calcula con su ancho real y la comunica
     * al editor, que la ofrece en el desplegable de zoom de la barra de estado. Es la misma cuenta
     * que hace MuseScore al pulsar el botón de ajustar: la escala que iguala el ancho natural de la
     * partitura al ancho imprimible del lienzo.
     */
    onFitScale: ((Float) -> Unit)? = null
) {
    val ink = inkColor
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availablePx = with(density) { maxWidth.toPx() }
        val settings = remember(scale, options, availablePx, numberScale) {
            // **Proporciones del PDF** (D-7): la clave es la razón `ancho de negra / separación de
            // cuerdas`, que en MuseScore vale `3,815 / 1,5 = 2,543`. Antes el canvas usaba números
            // sueltos (`cellWidth = 22·scale` contra `stringSpacing = 20·scale`, razón **1,1**) y la
            // tablatura salía **2,3× más estrecha** que el PDF. Aquí la separación de cuerdas sigue
            // siendo la legible de pantalla y el ancho de negra sale de la **misma escala de
            // notación** que usa el motor de PDF ([TabNotationScale]).
            //
            // (D-85) **16 dp en vez de 20.** Medido del `salmo2.pdf`, las cuerdas van a **1,5
            // espacios** con un espacio de **4,96 pt**, y la cifra ocupa el **0,887** de esa
            // separación. Con 20 dp la rejilla tenía un 25 % más de aire que la referencia y el
            // usuario lo veía como «demasiado espacio entre las líneas». 16 dp la deja en la misma
            // proporción que el PDF y, de paso, caben más compases por pantalla sin tocar el zoom.
            val stringSpacing = 16f * scale
            // (D-15) La separación entre sistemas **no** se pasa a mano: sale de la escala de
            // notación como `minSystemDistance` (8,5 espacios, `styledef.cpp:58`). Antes se forzaba a
            // `14f * scale`, que con un espacio de 13,33 px eran **1,05 espacios** y dejaba los
            // sistemas pegados unos a otros.
            TabNotationScale(
                staffSpacePt = stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES
            ).toLayoutSettings(
                staff = options,
                // (D-52) El zoom del número entra en la maquetación: la columna reserva el ancho de
                // la cifra **ya escalada**. Antes solo lo sabía el pintor y las cifras se pisaban.
                fretNumberScale = numberScale
            ).copy(
                // (D-13) **Ancho imprimible**: MuseScore deja `pageWidth - pagePrintableWidth` de
                // margen a cada lado de la hoja, que en el `salmo2.pdf` son **7,1 %** por la
                // izquierda y **7,3 %** por la derecha. Sin esto la tablatura iba de borde a borde y
                // parecía un formulario en vez de una partitura.
                pageMargin = availablePx * TabLayoutSettings.PAGE_MARGIN_RATIO
            )
        }
        val systems = remember(tablature, availablePx, settings) {
            TablatureSystemsLayout.layout(tablature, maxWidth = availablePx, settings = settings)
        }
        // (D-14) **Ajustar al ancho**: la escala que llena el ancho imprimible del lienzo. Se calcula
        // aquí porque es el único sitio que conoce el ancho real, y se comunica al editor para el
        // desplegable de zoom de la barra de estado.
        //
        // (D-52) La clave es **`settings`**, no `options`: el ancho natural depende de la escala y del
        // zoom del número, así que con `options` la escala de ajuste se quedaba **congelada** al
        // cambiar cualquiera de los dos y el desplegable de zoom mentía.
        val fitScale = remember(tablature, availablePx, settings) {
            TablatureSystemsLayout.fitToWidthScale(tablature, availablePx, settings)
        }
        LaunchedEffect(fitScale) { onFitScale?.invoke(fitScale) }
        // (D-57) El alto del sistema es su **caja de tinta** (como el `bbox()` de MuseScore), así que
        // la última cuerda cae justo en el borde inferior: el lienzo reserva medio espacio para que
        // el trazo no se recorte. El aire entre sistemas ya lo pone `systemGap`.
        val totalHeight = systems.lastOrNull()?.let {
            it.topY + it.height + settings.stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES * 0.5f
        } ?: 0f

        val measurer = rememberTextMeasurer()
        // **Fuente musical real** (Bravura, OFL): la misma que usa MuseScore para los glifos.
        val musicFamily = MusicFont.family()
        // Fuente de **texto** de MuseScore (Edwin, OFL): tempo, dinámicas, textos y números.
        val textFamily = MusicFont.textFamily()
        // **Un espacio de pentagrama** en px: la unidad de la que salen TODAS las tallas (tipografía
        // del PDF). Es la separación de cuerdas partida por 1,5 (el `lineDistance` de MuseScore).
        // (D-54) El zoom del número abre las cuerdas sin que las tallas del grabado crezcan con ellas:
        // el espacio de pentagrama sale de los ajustes **ya corregidos**, no de la separación de
        // cuerdas.
        val staffSpacePx = settings.withFretNumberSpacing().staffSpace
        // La fuente y el peso de MuseScore (`Tablature fret number`), con las medidas del PDF.
        val styles = remember(ink, numberScale, scale, musicFamily, textFamily, staffSpacePx, density) {
            TabCanvasTextStyles(
                ink = ink,
                staffSpace = staffSpacePx,
                numberScale = numberScale,
                density = density,
                music = musicFamily,
                text = textFamily
            )
        }
        // (D-59) Los símbolos de técnica **caben en su franja**: la pila sube una franja por símbolo
        // (`TabArticulationStack.TIER_SPACES`), así que el glifo se dibuja exactamente a esa franja.
        // Antes medía **2,4 espacios** con la franja en **1,65**: su caja bajaba 0,375 espacios por
        // debajo de la primera cuerda y **tapaba la mitad superior de la cifra** (trémolo, palanca
        // caída, armónicos, `l.v.`). El tamaño sale de la separación de cuerdas **ya corregida** por
        // el zoom del número (D-54), de modo que al agrandar la cifra el símbolo crece con ella.
        val tierPx = settings.withFretNumberSpacing().stringSpacing *
            TabArticulationStack.TIER_SPACES
        val glyphSize = tierPx
        // Colores de las **guias de interfaz** (cursor, seleccion, fantasma). Se leen aqui, en el
        // composable, porque dentro de `DrawScope` no hay acceso al tema.
        val overlayColors = TabOverlayColors(
            cursor = MaterialTheme.colorScheme.outline,
            selection = MaterialTheme.colorScheme.primaryContainer,
            ghost = MaterialTheme.colorScheme.outline
        )
        // Posición del lienzo en coordenadas **raíz**: el arrastre de paletas llega en coordenadas de
        // ventana y hay que restarlas para resolver la celda (B-13).
        val canvasRoot = remember { mutableStateOf(Offset.Zero) }
        // **Rectangulo de seleccion** (*rubber band*) mientras se arrastra sobre un hueco vacio. Vive
        // en el lienzo (no en el ViewModel) porque es puro estado de gesto: al soltar se resuelve a
        // una seleccion de notas y se emite.
        var rubberBand by remember { mutableStateOf<Rect?>(null) }
        // **Punto de pulsacion** del arrastre. `detectDragGestures` entrega en `onDragStart` la
        // posicion donde se **supero el umbral** de arrastre (~18 px), no donde se pulso, y MuseScore
        // decide el gesto por el punto de **pulsacion**: arrastrar sobre una cifra mueve la nota y
        // arrastrar sobre un hueco abre el rectangulo. Sin esto, un arrastre que empieza en el hueco
        // pero cruza el umbral ya dentro del pentagrama se interpretaba como arrastre de nota.
        var downPosition by remember { mutableStateOf(Offset.Zero) }
        SideEffect {
            hitTester?.resolve = { rootPosition ->
                hitTest(systems, tablature, rootPosition - canvasRoot.value, settings)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeight.toDp() })
                .onGloballyPositioned { canvasRoot.value = it.positionInRoot() }
                .then(
                    if (onHit == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(systems, tablature, settings) {
                            // **Pulsacion** (mueve el cursor; en modo entrada escribe un `0`), con los
                            // **modificadores** resueltos (`Mayus` extiende la seleccion de notas,
                            // `Ctrl` la alterna) y **pulsacion larga** (menu contextual de la celda, como
                            // el clic derecho de MuseScore y la pulsacion larga en tableta).
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val modifiers = TabCanvasModifiers(
                                    shift = currentEvent.keyboardModifiers.isShiftPressed,
                                    ctrl = currentEvent.keyboardModifiers.isCtrlPressed
                                )
                                val hit = hitTest(systems, tablature, down.position, settings)
                                // (D-71) **Clic derecho** (escritorio): menu contextual directo, como
                                // en MuseScore. No se espera ningun tiempo.
                                if (currentEvent.buttons.isSecondaryPressed) {
                                    if (hit != null) onLongPress?.invoke(hit)
                                    waitForUpOrCancellation()
                                    return@awaitEachGesture
                                }
                                // (D-71) **Solo** una pulsacion larga de verdad abre el menu.
                                // (D-78) El desenlace se resuelve con [awaitTabTapOutcome] y **no** con
                                // `awaitLongPressOrCancellation`: esa funcion **consume** el evento de
                                // levantamiento, asi que la guarda `up.isConsumed` daba siempre `true` y
                                // el clic **nunca** llegaba a `onHit`. El usuario lo describio como «la
                                // pulsacion no hace nada»: se podia escribir en la primera casilla (la
                                // que deja el cursor al abrir) pero pulsar otra cifra no movia el cursor.
                                when (awaitTabTapOutcome(down)) {
                                    TabTapOutcome.LONG_PRESS -> {
                                        if (hit != null) onLongPress?.invoke(hit)
                                        waitForUpOrCancellation()
                                    }
                                    TabTapOutcome.UP -> if (hit != null) onHit(hit, modifiers)
                                    // Movido mas alla del umbral o gesto cancelado: lo resuelve el
                                    // detector de arrastre, no el de pulsacion.
                                    TabTapOutcome.MOVED, TabTapOutcome.CANCELLED -> Unit
                                }
                            }
                        }
                    }
                )
                .then(
                    // **Arrastrar** en el lienzo. MuseScore distingue dos gestos por **donde empieza**
                    // el arrastre: sobre una **cifra** mueve la nota a la cuerda adyacente sin cambiar
                    // su altura; sobre un **hueco vacio** abre un **rectangulo de seleccion**
                    // (*rubber band*) que selecciona las notas que toca. El de cuerda se resuelve al
                    // **soltar**, con el numero de cuerdas recorridas, para no reescribir la partitura
                    // en cada frame.
                    if ((onDragStrings == null && onRubberBand == null) || onHit == null) {
                        Modifier
                    } else {
                        Modifier
                            // Observador **pasivo** del `down`: solo apunta donde se pulso, no
                            // consume nada. Va **antes** que el detector de arrastre para verlo en la
                            // pasada `Initial`.
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes
                                            .firstOrNull { it.pressed && !it.previousPressed }
                                            ?.let { downPosition = it.position }
                                    }
                                }
                            }
                            .pointerInput(systems, tablature, settings) {
                            var origin: TabCanvasHit? = null
                            var fromString = 0
                            var start = Offset.Zero
                            var lastOffset = Offset.Zero
                            var banding = false
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // El gesto se decide por el punto de **pulsacion**, no por el de
                                    // superacion del umbral (ver `downPosition`).
                                    val pressed = downPosition
                                    val hit = hitTest(systems, tablature, pressed, settings)
                                    origin = hit
                                    fromString = hit?.stringNumber ?: 0
                                    start = pressed
                                    lastOffset = offset
                                    banding = hit == null && onRubberBand != null
                                    rubberBand = if (banding) {
                                        Rect(pressed.x, pressed.y, pressed.x, pressed.y)
                                    } else {
                                        null
                                    }
                                },
                                onDragEnd = {
                                    val from = origin
                                    val band = rubberBand
                                    rubberBand = null
                                    if (banding) {
                                        if (band != null && onRubberBand != null) {
                                            onRubberBand(
                                                TabNoteSelection.rect(
                                                    systems = systems,
                                                    measures = tablature.measures,
                                                    left = band.left,
                                                    top = band.top,
                                                    right = band.right,
                                                    bottom = band.bottom
                                                )
                                            )
                                        }
                                    } else if (from != null && fromString != 0 && onDragStrings != null) {
                                        val to = hitTest(systems, tablature, lastOffset, settings)
                                        val delta = (to?.stringNumber ?: fromString) - fromString
                                        if (delta != 0) onDragStrings(from, delta)
                                    }
                                    origin = null
                                    banding = false
                                },
                                onDragCancel = {
                                    rubberBand = null
                                    origin = null
                                    banding = false
                                },
                                onDrag = { change, _ ->
                                    lastOffset = change.position
                                    if (banding) {
                                        rubberBand = Rect(
                                            left = minOf(start.x, change.position.x),
                                            top = minOf(start.y, change.position.y),
                                            right = maxOf(start.x, change.position.x),
                                            bottom = maxOf(start.y, change.position.y)
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
                .then(
                    // **Zoom** con el pellizco (tactil) o con `Ctrl`+rueda (raton), como MuseScore
                    // (`Ctrl`+`+`/`-` y `Ctrl`+rueda). El pellizco se lee con `detectTransformGestures`;
                    // la rueda, con el `scrollDelta` del evento de puntero.
                    if (onZoom == null) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1f) onZoom(zoom)
                                }
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        // Solo con `Ctrl` pulsado, como MuseScore: sin modificador, la rueda
                                        // desplaza el lienzo (lo hace el contenedor con scroll).
                                        if (scroll != 0f) {
                                            val ctrl = event.keyboardModifiers.isCtrlPressed
                                            if (ctrl) {
                                                onZoom(if (scroll < 0f) 1.1f else 1f / 1.1f)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                )
        ) {
            systems.forEach { system ->
                drawTabDrawing(
                    drawing = system.drawing,
                    dy = system.topY,
                    settings = settings,
                    styles = styles,
                    measurer = measurer,
                    glyphSize = glyphSize,
                    ink = ink,
                    overlay = overlay,
                    overlayColors = overlayColors,
                    options = options,
                    scale = scale,
                    // (2026-10-30) El **espacio de pentagrama del lienzo**, no el del dibujo.
                    // El del dibujo lo fija el Dominio **antes** de aplicar el zoom del número, así
                    // que al agrandar la tablatura las plicas, los silencios y los grosores se
                    // quedaban clavados en el tamaño viejo mientras las cifras y las cuerdas sí
                    // crecían: eso era el «las plicas y los silencios no se escalan» del usuario.
                    staffSpacePx = staffSpacePx,
                    stringCount = tablature.tuning.stringCount
                )
            }
            // **Rectangulo de seleccion** encima de todo: tinte suave y marco de 1 dp, como el
            // *rubber band* de MuseScore. Se dibuja al final para que no lo tape ningun sistema.
            rubberBand?.let { band ->
                if (band.width > 0f && band.height > 0f) {
                    drawRect(
                        color = overlayColors.selection.copy(alpha = 0.14f),
                        topLeft = Offset(band.left, band.top),
                        size = Size(band.width, band.height)
                    )
                    drawRect(
                        color = overlayColors.selection,
                        topLeft = Offset(band.left, band.top),
                        size = Size(band.width, band.height),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Texto con los **símbolos musicales** (SMuFL) resueltos con la fuente musical.
 *
 * MuseScore escribe el tempo como **texto enriquecido**: `<sym>metNoteQuarterUp</sym> = 80`. Aquí el
 * símbolo llega como punto de código SMuFL (`\uECA5`) dentro de la cadena, y la fuente de texto
 * (Edwin) no lo tiene: sin este paso el `♩` **no se dibujaba** y el rótulo salía `= 80`.
 *
 * El símbolo se dibuja a **su propio cuerpo** ([symbolSize]), no al del texto: medido del
 * `salmo2.pdf` (pauta en y = 535, espacio = 20,67 px), el `♩` ocupa y = 440–491 (**2,52 espacios**)
 * mientras que la tinta del texto mide 2,03, es decir el símbolo es **1,24×** el texto. Con el cuerpo
 * del texto el `♩` salía diminuto y descolgado de la línea base, como un subíndice.
 */
private fun annotatedText(
    text: String,
    music: FontFamily,
    symbolSize: TextUnit
): AnnotatedString {
    if (text.none { it in '\uE000'..'\uF8FF' }) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var i = 0
        while (i < text.length) {
            if (text[i] in '\uE000'..'\uF8FF') {
                val start = i
                while (i < text.length && text[i] in '\uE000'..'\uF8FF') i++
                addStyle(
                    SpanStyle(fontFamily = music, fontSize = symbolSize),
                    start,
                    i
                )
            } else {
                i++
            }
        }
    }
}

/**
 * **Resuelve** una pulsacion (en px del lienzo) a la celda del modelo sobre la que ha caido: primero
 * el **sistema** por la altura, luego el **compas** por la x (de sus cajas reales) y la **columna**
 * mas cercana. El hueco del final del compas cuenta como la **columna fantasma** de escritura.
 */
private fun hitTest(
    systems: List<TabSystem>,
    tablature: Tablature,
    offset: Offset,
    settings: TabLayoutSettings
): TabCanvasHit? {
    val system = systems.firstOrNull { offset.y >= it.topY && offset.y <= it.topY + it.height } ?: return null
    val drawing = system.drawing
    if (drawing.stringSpacing <= 0f) return null
    val y = offset.y - system.topY
    // Cuerda: la fila mas cercana a la altura del toque (la aguda va arriba, indice 0).
    val stringCount = tablature.tuning.stringCount.coerceAtLeast(1)
    // Fuera de las cuerdas (margen superior o inferior del compás) la pulsación es **de compás**, no
    // de nota: se marca con `stringNumber = 0` y el editor la trata como selección de compás.
    val outsideStrings = y < drawing.gridTop - drawing.stringSpacing * 0.5f ||
        y > drawing.gridBottom + drawing.stringSpacing * 0.5f
    val stringIndex = ((y - drawing.gridTop) / drawing.stringSpacing)
        .roundToInt()
        .coerceIn(0, stringCount - 1)
    val stringNumber = if (outsideStrings) 0 else stringIndex + 1
    // Compas: la caja cuya x contiene el toque; si cae en el hueco de la derecha, la ultima anterior.
    val measureBox = drawing.measureBounds.firstOrNull { offset.x >= it.left && offset.x <= it.right }
        ?: drawing.measureBounds.lastOrNull { it.right <= offset.x }
        ?: return null
    val measureIndex = measureBox.measureIndex
    val slotCount = tablature.measures.getOrNull(measureIndex)?.slots?.size ?: 0
    // Columna: la caja que contiene el toque; detras de la ultima es la columna fantasma.
    val column = drawing.columns[measureIndex]
        ?.firstOrNull { offset.x >= it.left - it.width * 0.2f && offset.x <= it.left + it.width }
    val slotIndex = column?.slotIndex ?: slotCount
    return TabCanvasHit(measureIndex, slotIndex, stringNumber)
}

/**
 * Estilos de texto del pentagrama, en un solo sitio para que editor y visor midan igual.
 *
 * **Tipografía con las medidas REALES del PDF** (2026-10-12). MuseScore no elige las tallas «a ojo»:
 * las deriva del **espacio de pentagrama** (la distancia entre cuerdas), que es la unidad de toda la
 * notación. Aquí se hace **igual**: cada talla es una **fracción del espacio** ([staffSpace]) medida
 * del `salmo2.pdf`, no un número en `sp` suelto. Antes eran valores fijos (`fret = 12`, `tabClef =
 * 22`…) que, con el espacio real (~22,7 px), salían **2–5× más pequeños** que en el PDF.
 *
 * Fracciones medidas del `salmo2.pdf` (en **espacios de pentagrama**, con espacio = 5,0 pt):
 *  - **cifra** de traste: tinta alta **0,887 de la separación de cuerdas** → `em ≈ 1,83` esp.
 *  - **clave `TAB`**: glifo de **5,86** esp de alto; Bravura `6stringTabClef` = 1,512 em → `em ≈ 3,87`.
 *  - **compás `4/4`**: el par mide **3,94** esp; Bravura `timeSig` = 0,501 em → `em ≈ 3,87`.
 *  - **número de compás**: ~2,0 esp (pequeño, sobre la barra).
 */
private class TabCanvasTextStyles(
    ink: Color,
    /** **Un espacio de pentagrama** en px: la unidad de la que salen TODAS las tallas. */
    private val staffSpace: Float,
    /** Escala propia de las cifras de traste (el usuario puede agrandarlas sin tocar la rejilla). */
    numberScale: Float,
    /** Densidad para convertir px → `sp` (las tallas se miden en px del pentagrama). */
    private val density: androidx.compose.ui.unit.Density,
    /** Fuente **musical** (Bravura): glifos del compás, de las figuras y del puntillo. */
    private val music: FontFamily,
    /** Fuente de **texto** (Edwin, la de MuseScore): tempo, dinámicas, textos y números. */
    private val text: FontFamily
) {
    /** Fuente musical, para los pintores que la necesiten por separado (las figuras de ritmo). */
    val musicFamily: FontFamily get() = music

    /** Fuente de **texto** (Edwin), para los pintores que la necesiten por separado. */
    val textFamily: FontFamily get() = text

    /** **Un espacio de pentagrama** en px: la unidad de la que salen TODAS las tallas. */
    val staffSpacePx: Float get() = staffSpace

    /** Px → `sp` con la densidad de la pantalla (una talla en espacios se convierte aquí). */
    private fun spaces(em: Float): TextUnit =
        with(density) { (staffSpace * em).toSp() }

    // Cifra de traste: **sans**, peso normal, tamaño derivado del espacio. MuseScore usa **FreeSans**
    // (`styleDef(tabFretNumberFontFace, "FreeSans")`), una sans de métrica Helvetica; como FreeSans es
    // GPLv3 y pesa 1,5 MB, se usa la **sans del sistema**, que es la misma familia métrica. Antes era
    // **serif**, y esa era la diferencia de forma más visible con MuseScore.
    // Medido del `salmo2.pdf`: la tinta de la cifra mide **0,887 de la separación de cuerdas** (27,5 px
    // con cuerdas a 31 px). En el render la tinta de la sans mide **0,728 em**, así que el em que la
    // clava es `0,887 / 0,728 = 1,22` separaciones de cuerda = **1,83 espacios**. Antes era 2,0 y las
    // cifras salían **~10 % demasiado grandes** y se solapaban entre cuerdas contiguas.
    val fret: TextStyle = TextStyle(
        color = ink,
        fontSize = with(density) {
            (staffSpace * TabTextMetrics.emOf(TabTextKind.FRET, numberScale)).toSp()
        },
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif
    )

    /**
     * **Cifra de adorno** (*grace note*): la misma tipografía que [fret] a **0,7** del tamaño
     * (`graceNoteMag` de MuseScore, `styledef.cpp:524`). En tablatura no lleva barra de acciaccatura
     * porque la tablatura es *stemless* y MuseScore no dibuja el `StemSlash` sin asta.
     */
    val grace: TextStyle = fret.copy(
        fontSize = with(density) {
            (staffSpace * TabTextMetrics.emOf(TabTextKind.GRACE, numberScale)).toSp()
        }
    )
    val stringLabel: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.STRING_LABEL_EM),
        fontFamily = text
    )
    // Número de compás: serif, pequeño y sin negrita (sobre la barra del compás).
    val measureNumber: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.MEASURE_NUMBER_EM),
        fontWeight = FontWeight.Normal,
        fontFamily = text
    )
    // Compás: **glifos de la fuente musical** (SMuFL), apilados, ocupando la altura del pentagrama.
    // Medido del `salmo2.pdf`: el par `4/4` mide **3,94 esp** de alto y `timeSig` = 0,501 em
    // ⇒ **em ≈ 3,87**. Antes era 4,2 y salía ~8 % demasiado grande (punto 2 del usuario).
    val timeSignature: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.emOf(TabTextKind.TIME_NUMERATOR)),
        fontWeight = FontWeight.Normal,
        fontFamily = music
    )
    // Clave de tablatura: el glifo SMuFL de la propia fuente musical, de 5,90 esp de alto.
    val tabClef: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.emOf(TabTextKind.TAB_CLEF)),
        fontWeight = FontWeight.Normal,
        fontFamily = music
    )
    // Dinámica: en MuseScore es cursiva y negrita, con la fuente de texto.
    val dynamic: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.DYNAMIC_EM),
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontFamily = text
    )
    // Articulaciones: **glifos SMuFL** (acento, arco de púa…), del tamaño del pentagrama.
    val articulation: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.MUSIC_MARK_EM),
        fontWeight = FontWeight.Normal,
        fontFamily = music
    )
    /** Signos musicales sobre la rejilla (navegación, **8va**, marcas): fuente musical. */
    val musicMark: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.MUSIC_MARK_EM),
        fontWeight = FontWeight.Normal,
        fontFamily = music
    )
    val glyphLabel: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.GLYPH_LABEL_EM),
        fontWeight = FontWeight.Normal,
        fontFamily = text
    )
    // **Rótulo del tempo** (D-8). MuseScore le da talla y peso **propios**: `styleDef(tempoFontSize,
    // 12.0)` y `styleDef(tempoFontStyle, int(FontStyle::Bold))` en `styledef.cpp`. Con 1 espacio =
    // 5 pt, 12 pt son **2,4 espacios**. Antes usaba el `mark` genérico (2,0 y normal) y el rótulo
    // salía ~15 % más pequeño que el del `salmo2.pdf` (tinta de `Andante` 1,49 esp frente a 1,70).
    val tempo: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.TEMPO_EM),
        fontWeight = FontWeight.Bold,
        fontFamily = text
    )
    // **Texto libre** y marcas de la rejilla. Medido del `salmo2.pdf` (espacio de pentagrama =
    // 20,67 px a 300 dpi): la tinta de las cifras del tempo mide **42 px = 2,03 esp**. En el render,
    // con `em = 2,0` esp la tinta de Edwin mide **2,03 esp** (verificado por píxeles), así que **2,0**
    // es la talla correcta. Se probó **2,9** (deducida de una medición previa equivocada) y la tinta
    // salía **2,95 esp**, un 45 % de más: el rótulo invadía la franja de las articulaciones.
    val mark: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.TEXT_EM),
        fontWeight = FontWeight.Normal,
        fontFamily = text
    )
    val held: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.HELD_EM),
        fontWeight = FontWeight.Normal,
        fontFamily = text
    )
    // (D-37) **Rótulo de la volta** (`1.`, `2.`, `1, 2.`). MuseScore le da talla y peso propios:
    // `styleDef(voltaFontSize, 11.0)` y `styleDef(voltaFontStyle, int(FontStyle::Bold))` en
    // `src/engraving/style/styledef.cpp`. Con 1 espacio = 5 pt, 11 pt son **2,2 espacios**.
    val volta: TextStyle = TextStyle(
        color = ink,
        fontSize = spaces(TabTextMetrics.VOLTA_EM),
        fontWeight = FontWeight.Bold,
        fontFamily = text
    )

    /**
     * Cuerpo del **símbolo SMuFL** incrustado en un texto (el `♩` del tempo), en `sp`.
     *
     * Medido del `salmo2.pdf`: el `♩` ocupa y = 440–491, **2,52 espacios**, mientras que la tinta
     * del texto del tempo mide 2,03 (el símbolo es **1,24×** el texto). La tinta de
     * `metNoteQuarterUp` en Bravura mide **0,86 em** de alto (verificado por píxeles: con
     * `em = 3,2` espacios el glifo mide 1,82 espacios), así que el cuerpo del símbolo es
     * `2,52 / 0,86 ≈ 2,9` espacios; se usa **3,2** para que la tinta llegue a 1,82 y quede en la
     * proporción del PDF. Con el cuerpo del texto (2,4) el `♩` medía 1,37 espacios y quedaba
     * diminuto, descolgado de la línea base.
     */
    val musicSymbol: TextUnit = spaces(TabTextMetrics.MUSIC_SYMBOL_EM)
}

/** Colores de las guias de interfaz que el editor superpone al pentagrama. */
private data class TabOverlayColors(
    val cursor: Color,
    val selection: Color,
    val ghost: Color,
    /** Color del **cursor de reproducción** (B-9): distinto del de escritura a propósito. */
    val playback: Color = Color(0xFF1B7F4B),
    /** Color del **aviso de acorde** (F-08): el rojo de error de Material 3. */
    val warning: Color = Color(0xFFB3261E)
)
/**
 * Dibuja **un sistema** de la tablatura (lineas, barras, glifos, ritmo, textos y overlays). Es la
 * rutina compartida por editor y visor: cualquier cambio de aspecto se hace **aqui** y afecta a los
 * dos a la vez.
 */
private fun DrawScope.drawTabDrawing(
    drawing: TablatureDrawing,
    dy: Float,
    settings: TabLayoutSettings,
    styles: TabCanvasTextStyles,
    measurer: TextMeasurer,
    glyphSize: Float,
    ink: Color,
    overlay: TabCanvasOverlay?,
    overlayColors: TabOverlayColors,
    options: TabStaffOptions,
    scale: Float,
    /**
     * **Un espacio de pentagrama** en px, ya con el zoom del número aplicado
     * ([TabLayoutSettings.withFretNumberSpacing]). Es la unidad de la que salen los grosores, las
     * plicas, los silencios y las barras de unión.
     */
    staffSpacePx: Float,
    /** Numero de cuerdas del instrumento: elige la clave de tablatura (`4string`/`6string`). */
    stringCount: Int
) {
    val cursorColor = overlayColors.cursor
    val selectionColor = overlayColors.selection
    val ghostColor = overlayColors.ghost
    val playbackColor = overlayColors.playback
    // Grosores de grabado **en espacios de pentagrama** (D-39): igual que MuseScore, escalan con el
    // zoom y con el tamano del pentagrama. Antes eran pixeles fijos (1f y 1,6f) y salian un 32 % mas
    // finos de lo que pide la hoja de estilos.
    //
    // (2026-10-30) Se usa **el espacio del lienzo**, no el que el Dominio guardó en `drawing`:
    // aquel se calculó **antes** de aplicar el zoom del número, así que al agrandar la tablatura los
    // trazos, las plicas y los silencios se quedaban pequeños mientras las cifras y las cuerdas
    // crecían. El lienzo ya tiene el valor corregido (lo pasa en [staffSpacePx]).
    val staffSpace = staffSpacePx.takeIf { it > 0f }
        ?: drawing.staffSpace.takeIf { it > 0f }
        ?: TabEngraving.staffSpaceOf(settings.stringSpacing)
    val staffLineStroke = TabEngraving.STAFF_LINE_WIDTH * staffSpace
    val barStroke = TabEngraving.BAR_WIDTH * staffSpace
    val doubleBarStroke = TabEngraving.DOUBLE_BAR_WIDTH * staffSpace
    val doubleBarGap = TabEngraving.DOUBLE_BAR_DISTANCE * staffSpace
    val endBarStroke = TabEngraving.END_BAR_WIDTH * staffSpace
    val endBarGap = TabEngraving.END_BAR_DISTANCE * staffSpace

    // ── Overlays de fondo: la seleccion de compases (tinte muy leve, nunca sobre la notacion) ────
    overlay?.selection?.forEach { measureIndex ->
        val box = drawing.measureBounds.firstOrNull { it.measureIndex == measureIndex } ?: return@forEach
        drawRect(
            color = selectionColor.copy(alpha = 0.22f),
            topLeft = Offset(box.left, drawing.gridTop - settings.stringSpacing * 0.6f + dy),
            size = Size(
                box.right - box.left,
                drawing.gridBottom - drawing.gridTop + settings.stringSpacing * 1.2f
            )
        )
    }

    // ── Bucle de rango (D-23): corchete por encima del pentagrama ────────────────────────────────
    overlay?.loopRange?.let { range ->
        val boxes = drawing.measureBounds.filter { it.measureIndex in range }
        if (boxes.isNotEmpty()) {
            val left = boxes.minOf { it.left }
            val right = boxes.maxOf { it.right }
            // La franja `TOP` de `TabStaffZones` ya reserva `tier * 0,5` = 0,45 espacios de
            // separación por encima de la rejilla: el corchete se dibuja **dentro** de ella para no
            // salirse del lienzo (antes iba a 0,9 espacios y quedaba recortado en el borde).
            val y = drawing.gridTop - settings.stringSpacing * 0.45f + dy
            val tick = settings.stringSpacing * 0.3f
            drawLine(
                color = playbackColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = barStroke
            )
            // Puntas hacia abajo: el corchete «abraza» los compases que se repiten.
            drawLine(playbackColor, Offset(left, y), Offset(left, y + tick), barStroke)
            drawLine(playbackColor, Offset(right, y), Offset(right, y + tick), barStroke)
        }
    }

    // ── Avisos de acorde (F-08): un punto rojo sobre la columna que no se puede tocar ────────────
    // Va **dentro** de la franja `TOP` (que reserva `tier · 0,5` = 0,45 espacios) y por debajo del
    // corchete del bucle, para que los dos quepan sin pisarse. El editor no bloquea la escritura: el
    // punto es un aviso, no un error que impida seguir.
    overlay?.chordIssues?.forEach { (measureIndex, slotIndex) ->
        val box = drawing.columns[measureIndex]?.firstOrNull { it.slotIndex == slotIndex }
            ?: return@forEach
        drawCircle(
            color = overlayColors.warning,
            radius = settings.stringSpacing * 0.15f,
            center = Offset(
                box.left + box.width / 2f,
                drawing.gridTop - settings.stringSpacing * 0.28f + dy
            )
        )
    }

    // ── Notas seleccionadas (Fase 2, ADR-020): el rectangulo del color de su voz, como MuseScore ──
    overlay?.selectedNotes?.forEach { ref ->
        val box = drawing.columns[ref.measureIndex]?.firstOrNull { it.slotIndex == ref.slotIndex }
            ?: return@forEach
        val y = stringY(drawing, ref.stringNumber) ?: return@forEach
        drawRect(
            // El color es el de la **voz de la nota** (`box.voice`), no el de la voz activa: una nota
            // de la voz 2 se resalta en verde aunque se esté escribiendo en la voz 1 (B-3).
            color = voiceColorOf(box.voice).copy(alpha = 0.30f),
            topLeft = Offset(box.left + 1f, y - settings.stringSpacing * 0.5f + dy),
            size = Size(box.width - 2f, settings.stringSpacing)
        )
    }

    // ── Columna fantasma (marco discontinuo donde se escribe la primera nota del compas) ────────
    overlay?.ghostMeasure?.let { ghost ->
        // Solo se dibuja cuando el compás **está vacío** (no hay ninguna columna): es la posición donde
        // se escribirá su primera nota. Si ya tiene columnas, no hay fantasma que mostrar.
        val hasColumns = drawing.columns[ghost]?.isNotEmpty() == true
        val box = drawing.measureBounds.firstOrNull { it.measureIndex == ghost }
        if (box != null && !hasColumns) {
            drawGhostFrame(box.left + settings.cellWidth / 2f, drawing, ghostColor, scale)
        }
    }

    // ── Lineas de cuerda y barras de compas ───────────────────────────────────────────────────────
    drawing.lines.forEach { line ->
        if (!line.isBar) {
            drawLine(
                color = ink,
                start = Offset(line.x1, snapLine(line.y1 + dy, staffLineStroke)),
                end = Offset(line.x2, snapLine(line.y2 + dy, staffLineStroke)),
                strokeWidth = staffLineStroke,
                // MuseScore usa **extremos planos** (`PenCapStyle::FlatCap`): con extremos redondos
                // la linea se prolongaria medio grosor por cada lado y se comeria la barra.
                cap = StrokeCap.Butt
            )
            return@forEach
        }
        val top = line.y1 + dy
        val bottom = line.y2 + dy
        // (D-65) **Barra de repetición**: `|:` / `:|` son una barra **fina + gruesa** con dos puntos,
        // no un rótulo. Se dibuja aquí, en la propia barra, para que la repetición quede pegada al
        // compás que abre o cierra. La gruesa va **por fuera** (a la izquierda en `|:`, a la derecha
        // en `:|`) y los puntos **por dentro**, mirando al pasaje repetido: es la convención de
        // Gould (*Behind Bars*) y la de MuseScore, y es la misma relación que hay entre la barra
        // final `𝄂` (fina + gruesa) y su espejo.
        val repeatMark = line.repeat
        if (repeatMark != null) {
            drawRepeatBarline(
                x = line.x1,
                top = top,
                bottom = bottom,
                mark = repeatMark,
                ink = ink,
                thinStroke = barStroke,
                thickStroke = endBarStroke,
                gap = endBarGap,
                dotRadius = staffSpace * 0.22f
            )
            return@forEach
        }
        // Las barras son **verticales**: se ajusta la `x` (no la `y`) para que salgan nitidas.
        // MuseScore **no** dibuja barra de apertura de sistema (D-4, medido del PDF): la barra solo
        // cierra compases, con el tipo del compas (final o doble).
        when (line.barline) {
            TabBarline.FINAL -> {
                // Trazo fino + trazo grueso separados `endBarDistance` (0,37 sp) **de borde a borde**,
                // como MuseScore (`tdraw.cpp`: `lw/2 + endBarDistance + lw2/2`). Solo se ajusta a la
                // rejilla el **ancla**; el segundo trazo se desplaza la distancia exacta, porque
                // ajustar los dos por separado deformaba la separacion.
                val thickX = snapLine(line.x1, endBarStroke)
                val thinX = thickX - (endBarStroke * 0.5f + endBarGap + barStroke * 0.5f)
                drawLine(ink, Offset(thinX, top), Offset(thinX, bottom), barStroke)
                drawLine(ink, Offset(thickX, top), Offset(thickX, bottom), endBarStroke)
            }
            TabBarline.DOUBLE -> {
                val rightX = snapLine(line.x1, doubleBarStroke)
                val leftX = rightX - (doubleBarStroke + doubleBarGap)
                drawLine(ink, Offset(leftX, top), Offset(leftX, bottom), doubleBarStroke)
                drawLine(ink, Offset(rightX, top), Offset(rightX, bottom), doubleBarStroke)
            }
            else -> {
                val x = snapLine(line.x1, barStroke)
                drawLine(
                    color = ink,
                    start = Offset(x, top),
                    end = Offset(x, bottom),
                    strokeWidth = barStroke,
                    cap = StrokeCap.Butt
                )
            }
        }
    }

    // ── Simbolos vectoriales de tecnica (tremolo, ligadura, bend, vibrato, armonico, P.M.) ────────
    with(TabTechniquePainter) {
        drawing.glyphs.forEach { glyph ->
            drawGlyph(
                glyph = glyph.copy(
                    y = glyph.y + dy,
                    from = glyph.from.copy(y = glyph.from.y + dy),
                    to = glyph.to.copy(y = glyph.to.y + dy)
                ),
                color = ink,
                size = glyphSize,
                cellWidth = settings.cellWidth
            ) { text, x, y, _ ->
                val measured = measurer.measure(text, styles.glyphLabel)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f)
                )
            }
        }
    }

    // ── Pentagrama ritmico (segun `Note values`: nada / encima / debajo con plicas y barras) ──────
    if (options.noteValues != NoteValueStyle.NONE && drawing.rhythmMarks.isNotEmpty()) {
        // `Stems and beams` (MuseScore): **sin cabezas**; la cifra es la cabeza y debajo solo van las
        // plicas y barras (corrección del usuario: los ovalos eran los «puntos sin sentido»).
        val showNoteheads = options.noteValues == NoteValueStyle.SYMBOLS_ABOVE
        with(TabRhythmPainter) {
            drawRhythmFigures(
                marks = drawing.rhythmMarks,
                offsetY = dy,
                color = ink,
                // **Un espacio de pentagrama** en px, el del **lienzo** (con el zoom ya aplicado):
                // el paso de las cuerdas es 1,5 espacios, así que la plica (3,5 espacios) queda con la
                // proporcion de MuseScore, no deformada. Antes se tomaba el del `drawing`, que el
                // Dominio calculó antes del zoom: por eso las plicas no escalaban.
                staffSpace = staffSpacePx,
                measurer = measurer,
                labelStyle = styles.glyphLabel,
                music = styles.musicFamily,
                stemDirection = if (options.stemsDown) {
                    com.grafitto.laudate.domain.tablature.TabRhythmGeometry.StemDirection.DOWN
                } else {
                    com.grafitto.laudate.domain.tablature.TabRhythmGeometry.StemDirection.UP
                },
                showNoteheads = showNoteheads,
                // **Dónde se corta cada barra de unión** lo decide el Dominio (depende del compás):
                // en 4/4 las corcheas van de cuatro en cuatro, no todas en una sola barra.
                beamGroups = drawing.beamGroups,
                // Los **corchetes de tresillo** (línea, ganchos y cifra SMuFL) también vienen resueltos
                // del Dominio: dependen del compás y de la primera cuerda.
                tuplets = drawing.tuplets,
                // **Cómo se dibuja la blanca** (`TablatureMinimStyle` de MuseScore): sin plica, con
                // plica corta o rayada. Viene del dibujo para que el PDF pinte lo mismo.
                minimStyle = drawing.minimStyle
            )
        }
    }

    // ── Textos (cifras, etiquetas, compas, numeros de compas, marcas, dinamicas...) ───────────────
    drawing.texts.forEach { t ->
        // La clave de tablatura es un **glifo de la fuente musical** (SMuFL `6stringTabClef`), el
        // mismo que usa MuseScore. Nada de dibujarla a mano.
        if (t.kind == TabTextKind.TAB_CLEF) {
            // (D-55) El glifo viene **resuelto del Dominio** (`TabGlyphs.tabClef`), no se adivina aquí
            // a partir del número de cuerdas: el PDF de escritorio y el de Android leen el mismo
            // punto de código y los tres dibujan la misma clave.
            val glyph = t.text
            val clef = measurer.measure(glyph, styles.tabClef)
            // **Anclaje por TINTA**, no por caja de línea: la caja de línea de Bravura mide 4,024 em
            // (línea base a 2,012 em del borde superior) y la tinta de la clave está centrada a
            // +0,008 em de la línea base. Anclar por `size.height * 0.62f` dejaba la clave **0,49 em
            // demasiado alta** (45 px a escala 1,7): ese era el bug del punto 2 del usuario.
            drawText(
                textLayoutResult = clef,
                topLeft = Offset(
                    t.x - clef.size.width / 2f,
                    inkTopLeftY(clef, t.y + dy, styles.staffSpacePx * TAB_CLEF_EM, MusicFont.inkOf(glyph))
                )
            )
            return@forEach
        }
        // El **compas** tambien son glifos SMuFL (`timeSigN`), no cifras de texto.
        if (t.kind == TabTextKind.TIME_NUMERATOR || t.kind == TabTextKind.TIME_DENOMINATOR) {
            val glyph = t.text.toIntOrNull()?.let { MusicFont.timeSignatureGlyph(it) } ?: t.text
            val measured = measurer.measure(glyph, styles.timeSignature)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    t.x - measured.size.width / 2f,
                    inkTopLeftY(
                        measured,
                        t.y + dy,
                        styles.staffSpacePx * TIME_SIGNATURE_EM,
                        MusicFont.inkOf(glyph)
                    )
                )
            )
            return@forEach
        }
        val baseStyle = when (t.kind) {
            TabTextKind.FRET -> styles.fret
            TabTextKind.GRACE -> styles.grace
            TabTextKind.STRING_LABEL -> styles.stringLabel
            TabTextKind.MEASURE_NUMBER -> styles.measureNumber
            TabTextKind.DYNAMIC -> styles.dynamic
            // El **rótulo del tempo** tiene talla y peso propios en MuseScore: `tempoFontSize = 12.0`
            // y `tempoFontStyle = Bold` (`styledef.cpp`), es decir **2,4 espacios** y negrita, no el
            // `mark` genérico. Con `mark` (2,0 y normal) el rótulo salía ~15 % más pequeño que el PDF.
            TabTextKind.TEMPO -> styles.tempo
            // Las articulaciones (y los arcos de púa) y los signos de navegación/**8va** son **glifos
            // de la fuente musical**; el texto libre y los rótulos van con la de texto.
            TabTextKind.ARTICULATION, TabTextKind.MARK, TabTextKind.OCTAVE_SHIFT -> styles.musicMark
            // (D-37) El rótulo de la **volta** es texto **negrita** de 11 pt en MuseScore
            // (`voltaFontSize`/`voltaFontStyle`), no un glifo musical.
            TabTextKind.VOLTA -> styles.volta
            TabTextKind.HELD -> styles.held
            else -> styles.mark
        }
        // **Voz de la nota** (B-3): MuseScore pinta cada cifra con el color de **su** voz, no con el de
        // la voz activa. La voz 1 va en tinta (es la voz por defecto y no se colorea), como en
        // MuseScore, donde la voz 1 es negra y las demás llevan color.
        val style = if (t.voice != 1 && (t.kind == TabTextKind.FRET || t.kind == TabTextKind.HELD)) {
            baseStyle.copy(color = voiceColorOf(t.voice))
        } else {
            baseStyle
        }
        val measured = measurer.measure(annotatedText(t.text, styles.musicFamily, styles.musicSymbol), style)
        // (D-8) El tempo va **alineado a la izquierda** en el primer elemento de su compás; el resto,
        // centrado en su columna o compás.
        val left = if (t.align == TabTextAlign.LEFT) t.x else t.x - measured.size.width / 2f
        // (D-10) El **tempo** se ancla por su **línea base**, como MuseScore (`tempoPosAbove`), no por
        // el centro de la caja. El rótulo mezcla la fuente musical (`♩`, Bravura) con la de texto
        // (Edwin), y Bravura tiene una ascendente mucho mayor: la caja de línea crece, la línea base
        // baja dentro de ella y, al centrar la caja, el texto quedaba **0,6 espacios demasiado alto**
        // respecto de `Andante` (que es solo Edwin). Anclando por la base, los dos coinciden.
        val top = if (t.kind == TabTextKind.TEMPO || t.kind == TabTextKind.VOLTA) {
            t.y + dy - measured.firstBaseline
        } else {
            t.y + dy - measured.size.height / 2f
        }
        if (t.rotated) {
            rotate(degrees = -90f, pivot = Offset(t.x, t.y + dy)) {
                drawText(textLayoutResult = measured, topLeft = Offset(left, top))
            }
        } else {
            drawText(textLayoutResult = measured, topLeft = Offset(left, top))
        }
    }

    // ── Cursor de entrada (ADR-020), al frente para que se vea sobre la cifra ────────────────────
    // Con el **modo de entrada** activo es el **rectángulo azul** de MuseScore: relleno translúcido
    // del **color de la voz activa**, del alto de un espacio de cuerda, sobre la cuerda activa. Con
    // el modo apagado se mantiene el marco fino, que es solo una guía de posición.
    // **Cursor de reproducción** (B-9): banda tenue sobre la columna que suena, por delante del
    // cursor de escritura para que se vea aunque el cursor esté en otra casilla.
    overlay?.playbackCursor?.let { (measureIndex, slotIndex) ->
        val box = drawing.columns[measureIndex]?.firstOrNull { it.slotIndex == slotIndex }
        if (box != null) {
            val top = stringY(drawing, 1)?.minus(settings.stringSpacing * 0.5f)?.plus(dy)
            val bottom = stringY(drawing, stringCount)?.plus(settings.stringSpacing * 0.5f)?.plus(dy)
            if (top != null && bottom != null) {
                drawRect(
                    color = playbackColor.copy(alpha = 0.18f),
                    topLeft = Offset(box.left + 1f, top),
                    size = Size(box.width - 2f, bottom - top)
                )
                drawRect(
                    color = playbackColor.copy(alpha = 0.55f),
                    topLeft = Offset(box.left + 1f, top),
                    size = Size(box.width - 2f, bottom - top),
                    style = Stroke(width = 1.2f)
                )
            }
        }
    }

    overlay?.cursor?.let { (measureIndex, slotIndex) ->
        val stringNumber = overlay.cursorString ?: 1
        val box = drawing.columns[measureIndex]?.firstOrNull { it.slotIndex == slotIndex }
        val y = stringY(drawing, stringNumber)
        if (box != null && y != null) {
            val top = y - settings.stringSpacing * 0.5f + dy
            if (overlay.inputMode) {
                // El color de la **voz activa**: los mismos valores que MuseScore 4 (ver TabVoiceColors).
                val voiceColor = voiceColorOf(overlay.voice)
                drawRect(
                    color = voiceColor.copy(alpha = TabVoiceColors.CURSOR_ALPHA),
                    topLeft = Offset(box.left + 1f, top),
                    size = Size(box.width - 2f, settings.stringSpacing)
                )
                // Un borde del mismo color, opaco, marca con precision donde cae la celda.
                drawRect(
                    color = voiceColor,
                    topLeft = Offset(box.left + 1f, top),
                    size = Size(box.width - 2f, settings.stringSpacing),
                    style = Stroke(width = 1.4f)
                )
            } else {
                drawRoundRect(
                    color = cursorColor,
                    topLeft = Offset(box.left + 1f, top),
                    size = Size(box.width - 2f, settings.stringSpacing),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

/**
 * **Barra de repetición** `|:` / `:|` (D-65).
 *
 * Se compone de un trazo **grueso** y uno **fino** separados [gap] de borde a borde —la misma
 * separación que la barra final `𝄂`, porque en MuseScore las dos son la misma pareja de trazos— y
 * **dos puntos** a un tercio y dos tercios del alto de la rejilla, que es donde caen en un
 * pentagrama de cinco líneas (2.º y 4.º espacio). En tablatura el alto es el de las cuerdas, así
 * que los puntos se reparten proporcionalmente: con 6 cuerdas quedan entre la 2.ª y la 3.ª y
 * entre la 4.ª y la 5.ª, que es lo que se ve en una tablatura de MuseScore.
 *
 * [mark] decide de qué lado va cada cosa: en `START` la gruesa queda a la **izquierda** (fuera del
 * pasaje) y los puntos a la **derecha**; en `END` al revés. En `BOTH` —un compás que cierra una
 * repetición y abre la siguiente— se dibujan **las dos** parejas, con los puntos en medio.
 */
private fun DrawScope.drawRepeatBarline(
    x: Float,
    top: Float,
    bottom: Float,
    mark: com.grafitto.laudate.domain.model.tablature.TabRepeatMark,
    ink: Color,
    thinStroke: Float,
    thickStroke: Float,
    gap: Float,
    dotRadius: Float
) {
    val dotY1 = top + (bottom - top) / 3f
    val dotY2 = top + (bottom - top) * 2f / 3f
    // Distancia del **centro** de los puntos al trazo fino: un punto de diámetro y medio, que es
    // la separación de MuseScore (`BarLine::drawDots`).
    val dotGap = dotRadius * 2.6f

    fun drawPair(anchorX: Float, opens: Boolean) {
        // `opens` = el `|:`: gruesa a la izquierda, fina a la derecha, puntos a la derecha.
        val thickX = snapLine(anchorX, thickStroke)
        val thinX = if (opens) {
            thickX + thickStroke * 0.5f + gap + thinStroke * 0.5f
        } else {
            thickX - thickStroke * 0.5f - gap - thinStroke * 0.5f
        }
        drawLine(ink, Offset(thinX, top), Offset(thinX, bottom), thinStroke, StrokeCap.Butt)
        drawLine(ink, Offset(thickX, top), Offset(thickX, bottom), thickStroke, StrokeCap.Butt)
        val dotsX = if (opens) thinX + dotGap else thinX - dotGap
        drawCircle(ink, dotRadius, Offset(dotsX, dotY1))
        drawCircle(ink, dotRadius, Offset(dotsX, dotY2))
    }

    when (mark) {
        com.grafitto.laudate.domain.model.tablature.TabRepeatMark.START -> drawPair(x, opens = true)
        com.grafitto.laudate.domain.model.tablature.TabRepeatMark.END -> drawPair(x, opens = false)
        com.grafitto.laudate.domain.model.tablature.TabRepeatMark.BOTH -> {
            // Cierra a la izquierda y abre a la derecha: dos parejas espejadas con los puntos
            // entre ellas. Se separan lo justo para que no se toquen.
            val half = thickStroke * 0.5f + gap + thinStroke + dotGap * 2f
            drawPair(x - half, opens = false)
            drawPair(x + half, opens = true)
        }
    }
}

/**
 * Color de la **voz** en `androidx.compose.ui.graphics.Color`, a partir del valor **ARGB** que define
 * el Dominio ([TabVoiceColors], medido del codigo de MuseScore 4). Se hace aqui, en la capa de UI,
 * para que el Dominio siga sin depender de Compose.
 */
private fun voiceColorOf(voice: Int): Color = Color(TabVoiceColors.of(voice))

/**
 * **Ajuste a la rejilla de píxeles** de la coordenada de una línea: un trazo solo sale nítido si su
 * **centro** cae en el centro de una fila/columna. La regla depende de la **paridad del grosor**:
 * un trazo de grosor **impar** (1 px, 3 px…) se centra en `n + 0,5`; uno de grosor **par** (2 px,
 * 4 px…) se centra en `n` entero. Es la misma regla que aplica MuseScore al rasterizar.
 *
 * Sin esto, el **segundo sistema** (que arranca en una `y` fraccionaria, porque la altura de un
 * sistema no es entera) salía con las cuerdas **desvaídas**: gris al 50 % repartido en dos filas, en
 * vez de una fila casi negra como el primero. MuseScore también ajusta los sistemas a píxeles.
 *
 * [width] es el grosor del trazo en píxeles; por omisión 1 px (el caso histórico).
 */
private fun snapLine(v: Float, width: Float = 1f): Float =
    if (width.roundToInt() % 2 == 0) floor(v + 0.5f) else floor(v) + 0.5f

/**
 * Altura (y) de una **cuerda** dentro del dibujo. Se calcula de la **rejilla** (`gridTop` + el paso),
 * no de las etiquetas de cuerda: MuseScore no las pinta, así que no se puede depender de ellas.
 */
private fun stringY(drawing: TablatureDrawing, stringNumber: Int): Float? {
    if (stringNumber <= 0 || drawing.stringSpacing <= 0f) return null
    return drawing.gridTop + (stringNumber - 1) * drawing.stringSpacing
}

/** Marco **discontinuo** de la columna fantasma (donde se escribira la primera nota de un compas). */
private fun DrawScope.drawGhostFrame(
    centerX: Float,
    drawing: TablatureDrawing,
    color: Color,
    scale: Float
) {
    val half = 11f * scale
    val top = drawing.gridTop - 2f * scale
    val bottom = drawing.gridBottom + 2f * scale
    val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
    drawLine(color, Offset(centerX - half, top), Offset(centerX + half, top), 1f, pathEffect = dash)
    drawLine(color, Offset(centerX - half, bottom), Offset(centerX + half, bottom), 1f, pathEffect = dash)
    drawLine(color, Offset(centerX - half, top), Offset(centerX - half, bottom), 1f, pathEffect = dash)
    drawLine(color, Offset(centerX + half, top), Offset(centerX + half, bottom), 1f, pathEffect = dash)
}
