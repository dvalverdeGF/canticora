package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabHairpinType
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.Tablature

/**
 * Distancia del **centro del pentagrama** a cada dígito del compás, en **separaciones de cuerda**.
 *
 * Medido del `salmo2.pdf`: el par `4/4` ocupa 3,94 espacios de pentagrama (como el propio
 * pentagrama) y cada dígito queda a **0,66 separaciones de cuerda** del centro. Antes se usaba 0,8 y
 * los dos dígitos salían demasiado separados.
 */
private const val TIME_SIG_CENTER_OFFSET: Float = 0.66f

/**
 * **Hueco de la línea de cuerda bajo una cifra**, en separaciones de cuerda, cuando el estilo de
 * líneas es [LineStyle.BROKEN_ON_MARKS].
 *
 * Medido del `salmo2.pdf` (300 dpi, cuerdas a 31 px): el corte mide **25 px** = 0,8 separaciones, y
 * es **el mismo** con un «1» (4 px de ancho), un «0» (17 px) y un «2» (5 px). Es decir: MuseScore no
 * ajusta el hueco al ancho del dígito, usa un valor fijo.
 */
private const val BROKEN_LINE_GAP_SPACINGS: Float = 0.8f

/**
 * **Maquetación pura** de una tablatura para su render **vectorial** (ADR-017). Produce
 * **primitivas** de dibujo (líneas y textos con su posición) que un `Canvas` (Compose) pinta sin
 * recurrir a caracteres `-` ni `|`. Kotlin puro: determinista y testeable al 100 %.
 *
 * Coordenadas en la **misma unidad** que [TabLayoutSettings] (la UI pasa valores ya en px).
 * El orden de las cuerdas es el de **dibujo** (la más aguda **arriba**).
 */
data class TabLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val isBar: Boolean = false,
    /**
     * Tipo de barra vertical (solo si [isBar]): permite dibujar la **doble** (`||`) y la **final**
     * (`𝄂`, fina + gruesa) como en MuseScore, no solo la simple.
     */
    val barline: com.grafitto.laudate.domain.model.tablature.TabBarline? = null,
    /**
     * **Signo de repetición** que lleva esta barra (D-65): `|:`, `:|` o los dos. Va **en la barra** y
     * no como texto encima de la rejilla, que es como lo dibuja MuseScore: el `|:` es una barra con
     * dos puntos, no un rótulo flotante. Antes se pintaba como texto `|:` / `:|` en la franja de
     * marcas, así que la repetición quedaba **despegada** del compás que abría o cerraba.
     */
    val repeat: com.grafitto.laudate.domain.model.tablature.TabRepeatMark? = null
)

/**
 * **Tramo de volta** dentro de un sistema (D-37): los compases **consecutivos** que comparten la
 * misma casilla (`ending`). El corchete se abre en [start] y se cierra en [end]; si la casilla
 * continúa en el sistema siguiente, [closes] es `false` y el corchete se corta en el borde derecho
 * (MuseScore hace lo mismo: el *spanner* se parte por sistema y cada trozo se dibuja abierto).
 */
internal data class TabVoltaSpan(
    /** Índice **local** (dentro del sistema) del primer compás del tramo. */
    val start: Int,
    /** Índice **local** del último compás del tramo. */
    val end: Int,
    /** Rótulo de la casilla: `1.`, `2.`, `1, 2.`… */
    val label: String,
    /** `true` si la casilla **empieza** aquí: se dibujan el gancho y el rótulo. */
    val opens: Boolean,
    /** `true` si la casilla **acaba** aquí: el corchete cierra en el borde derecho del compás. */
    val closes: Boolean
)

/** Tipo de texto dibujable, para que el pintor elija estilo (cifra, etiqueta, compás, prolongación). */
enum class TabTextKind {
    FRET, STRING_LABEL, HELD, MARK, MEASURE_NUMBER, DYNAMIC, ARTICULATION, OCTAVE_SHIFT, TEMPO, TEXT,

    /** **Clave de tablatura** (`TAB`) a la izquierda del sistema, como en MuseScore (R1). */
    TAB_CLEF,

    /** **Numerador** del compás (`4` de `4/4`), alineado con la caja del pentagrama. */
    TIME_NUMERATOR,

    /** **Denominador** del compás (`4` de `4/4`), bajo el numerador. */
    TIME_DENOMINATOR,

    /**
     * **Cifra de una nota de adorno** (*grace note*): se pinta a **0,7** del tamaño de una cifra
     * normal (`graceNoteMag` de MuseScore) y a la **izquierda** de la cifra principal.
     */
    GRACE,

    /**
     * **Rótulo de la volta** (`1.`, `2.`, `1, 2.`): en MuseScore es texto **negrita** de 11 pt
     * (`styleDef(voltaFontSize, 11.0)` y `styleDef(voltaFontStyle, Bold)`), no un glifo musical.
     */
    VOLTA
}

/**
 * **Estilo de las cifras** de traste (`Fret marks` en MuseScore): `Numbers` (0-9) o `Letters`
 * (a-k, sin la `i`), que es como se rotulaban las tablaturas antiguas.
 */
enum class FretMarkStyle { NUMBERS, LETTERS }

/**
 * **Colocación** de las cifras respecto a la línea de la cuerda (`On lines` / `Above lines` en
 * MuseScore). `ON_LINES` centra la cifra **sobre** la línea (lo habitual); `ABOVE_LINES` la deja
 * **encima**, sobre la línea, sin que la cruce.
 */
enum class MarksPlacement { ON_LINES, ABOVE_LINES }

/**
 * **Estilo de las líneas** de cuerda: `CONTINUOUS` (pasan por detrás de la cifra, como una cuerda) o
 * `BROKEN_ON_MARKS` (se **interrumpen** en cada cifra, como una tablatura «clásica»).
 */
enum class LineStyle { CONTINUOUS, BROKEN_ON_MARKS }

/**
 * **Valores de nota** del pentagrama rítmico (`Note values` en MuseScore):
 *
 *  - [NONE]: sin ritmo (solo cifras).
 *  - [SYMBOLS_ABOVE]: símbolos con forma de nota **encima** del pentagrama.
 *  - [STEMS_AND_BEAMS]: plicas, banderas y barras **como un pentagrama estándar** (el pentagrama
 *    rítmico propio bajo las cuerdas).
 */
enum class NoteValueStyle { NONE, SYMBOLS_ABOVE, STEMS_AND_BEAMS }

/**
 * **Cuándo se repite** el símbolo de duración: el `TablatureSymbolRepeat` de MuseScore
 * (`src/engraving/dom/stafftype.h:93-98`), con sus **cuatro** valores y no dos.
 *
 * Un pentagrama rítmico real **no** rotula la figura en cada columna: la rotula cuando cambia y,
 * según este ajuste, también al empezar algo. La regla de MuseScore vive en dos sitios, y hay que
 * reproducir **los dos**:
 *
 *  1. `ChordLayout::layoutChords` (`src/engraving/rendering/score/chordlayout.cpp:530-537`) decide si
 *     el `TabDurationSymbol` **existe**: se crea cuando cambia la figura, el puntillo, el grupo
 *     irregular, el modo de barra o el compás anterior era un silencio; y además cuando el ajuste es
 *     [ALWAYS], o [MEASURE]/[SYSTEM] y el compás cambió (`item->measure() != prevCR->measure()`).
 *  2. `TDraw::draw(const TabDurationSymbol*)` (`src/engraving/rendering/score/tdraw.cpp:3067-3074`)
 *     **filtra al dibujar** el caso [SYSTEM]: si el símbolo es de repetición y el acorde anterior
 *     está en el **mismo sistema**, no se pinta. Es decir, [SYSTEM] y [MEASURE] se comportan **igual**
 *     al maquetar y solo se distinguen al pintar.
 *
 * De ahí la traducción a la rejilla de Canticora, donde «el compás cambió» es «la columna es la
 * primera del compás» (`s == 0`) y «el sistema cambió» es «el compás es el primero del sistema»:
 *
 *  - [NEVER]: solo al cambiar de figura. Es el valor por defecto de MuseScore
 *    (`StaffType::m_symRepeat = TablatureSymbolRepeat::NEVER`, `stafftype.cpp:79`) y el de **todos**
 *    sus presets de tablatura (`stafftype.cpp:946-967`).
 *  - [SYSTEM]: al cambiar de figura y al abrir **sistema**.
 *  - [MEASURE]: al cambiar de figura y al abrir **compás**.
 *  - [ALWAYS]: en **todas** las columnas (útil en bocetos).
 */
enum class RhythmRepeat { NEVER, SYSTEM, MEASURE, ALWAYS }

/**
 * **Cómo se dibuja la blanca** en el pentagrama rítmico: el `TablatureMinimStyle` de MuseScore
 * (`src/engraving/dom/stafftype.h:87-91`).
 *
 * En una tablatura la plica no nace de una cabeza de nota, así que la blanca —la única figura con
 * plica y sin corchete— se confundiría con la negra. MuseScore lo resuelve de tres maneras, y las
 * tres están en sus presets (`stafftype.cpp:946-967`):
 *
 *  - [NONE]: la blanca **no lleva plica**. `Chord::shouldHaveStem()` (`chord.cpp:1074`) devuelve
 *    `false` para `V_HALF` en tablatura con este estilo, así que la figura queda **sin símbolo** y
 *    solo la cifra de traste dice la altura. Es el estilo de los presets `Simple` y de los históricos
 *    (`TAB_ITALIAN`, `TAB_FRENCH`).
 *  - [SHORTER]: la plica mide **la mitad** (`STAFFTYPE_TAB_SHORTSTEMRATIO = 0,5`,
 *    `stemlayout.h:48`) y su arranque se desplaza media plica, de modo que el **extremo libre queda
 *    donde el de una plica normal** (`StemLayout::tabStemLength` y `tabRestStemPosY`,
 *    `stemlayout.cpp:268-300`). Es el estilo de los presets `Common`.
 *  - [SLASHED]: la plica mide lo normal y lleva **dos barras inclinadas** cruzadas
 *    (`TDraw::draw(const Stem*)`, `tdraw.cpp:2879-2905`). Es el estilo de los presets `Full`.
 *
 * Solo afecta a la **blanca** (y a la blanca con puntillo): las demás figuras no cambian.
 */
enum class MinimStyle { NONE, SHORTER, SLASHED }

/**
 * **Qué se hace con la cifra de una nota que RECIBE una ligadura de prolongación**: el
 * `ShowTiedFret` de MuseScore (`src/engraving/dom/stafftype.h:116-120`).
 *
 * Una ligadura de prolongación une dos notas de la **misma altura**: la segunda no se vuelve a
 * atacar. En una tablatura la cifra de la segunda nota es redundante —dice lo mismo que la primera—
 * y por eso MuseScore permite quitarla. La decisión vive en `Note::shouldHideFret()`
 * (`src/engraving/dom/note.cpp:1527-1556`) y se consulta en **tres** sitios:
 *
 *  1. `Note::shouldHideFret()` decide si la cifra se pinta.
 *  2. `ChordLayout::layoutNote2` (`chordlayout.cpp:3166-3169`) decide si se le ponen **paréntesis**
 *     generados: `tieBackParen = isTabStaff && tieBack && showTiedFret != TIE_AND_FRET && !shouldHideFret()`.
 *  3. `SlurTieLayout::tieSegmentShouldBeSkipped` (`slurtielayout.cpp:1553-1563`) decide si el **arco**
 *     se dibuja: se omite con [NONE].
 *
 *  - [TIE_AND_FRET]: la cifra **siempre** se muestra, sin paréntesis. Es el valor por defecto de
 *    MuseScore (`styleDef(tabShowTiedFret, int(ShowTiedFret::TIE_AND_FRET))`, `styledef.cpp:2096`) y
 *    el que tenía Canticora hasta ahora.
 *  - [TIE]: la cifra se muestra **solo** donde la ligadura cruza una frontera —según
 *    [ParenthesizeTiedFret]— y **entre paréntesis**, para que se lea como recordatorio y no como una
 *    nota nueva. El arco se sigue dibujando.
 *  - [NONE]: la cifra **nunca** se muestra y el **arco tampoco**: la ligadura desaparece del grabado.
 */
enum class ShowTiedFret { TIE_AND_FRET, TIE, NONE }

/**
 * **Dónde se recuerda la cifra** de una nota ligada cuando [ShowTiedFret] es [ShowTiedFret.TIE]: el
 * `ParenthesizeTiedFret` de MuseScore (`src/engraving/dom/stafftype.h:122-126`).
 *
 * `Note::shouldHideFret()` (`note.cpp:1544-1556`) solo deja ver la cifra si la nota cae en el
 * **arranque de un compás** (`rtick().isZero()`); a partir de ahí este ajuste decide qué arranques
 * cuentan:
 *
 *  - [START_OF_SYSTEM]: solo el compás que **abre el sistema**. Es el valor por defecto de MuseScore
 *    (`styleDef(tabParenthesizeTiedFret, int(ParenthesizeTiedFret::START_OF_SYSTEM))`,
 *    `styledef.cpp:2097`): al pasar de línea el lector ha perdido la referencia y hay que recordarla.
 *  - [START_OF_MEASURE]: **cualquier** compás. Útil cuando la pieza se lee compás a compás.
 *  - [NEVER]: **ninguno**; la cifra ligada no se ve nunca (con [ShowTiedFret.TIE] equivale a
 *    [ShowTiedFret.NONE] salvo que el arco sí se dibuja).
 */
enum class ParenthesizeTiedFret { START_OF_SYSTEM, START_OF_MEASURE, NEVER }

/**
 * **Ajustes del pentagrama de tablatura**, espejo de los de `Staff/Part properties` de MuseScore
 * (`Simple` = solo cifras · `Common` = cifras + silencios + plicas · `Full` = además plicas de blanca).
 *
 * Los dos pintores (editor y visor) los reciben **iguales**: son la única fuente de verdad del
 * aspecto, de modo que no puede haber dos tablaturas distintas para el mismo documento.
 */
data class TabStaffOptions(
    /** Cifras como **números** (0-9) o **letras** (a-k, sin la `i`). */
    val fretMarks: FretMarkStyle = FretMarkStyle.NUMBERS,
    /** Cifra **sobre** la línea o **encima** de ella. */
    val marksPlacement: MarksPlacement = MarksPlacement.ON_LINES,
    /**
     * Líneas de cuerda **continuas** o **interrumpidas** en cada cifra.
     *
     * (2026-10-15) Por defecto **`BROKEN_ON_MARKS`**, que es lo que hace MuseScore con la tablatura
     * de referencia: el `.mscx` del `salmo2` lleva `<linesThrough>0</linesThrough>`, es decir «las
     * líneas **no** atraviesan las cifras». Medido del PDF: la línea se corta ~25 px (0,8
     * separaciones de cuerda) **solo** donde hay una cifra, y el corte es **independiente del ancho
     * del dígito**. El usuario lo pidió así: «la línea no debería verse detrás del número, dificulta
     * la lectura».
     */
    val lines: LineStyle = LineStyle.BROKEN_ON_MARKS,
    /**
     * Estilo del pentagrama rítmico. Por defecto **`STEMS_AND_BEAMS`**, que es el tipo `Common` de
     * MuseScore (`Common: fretmarks, rests, **stems and beams**`), el que usa la tablatura de
     * referencia (`<name>tab6StrCommon</name>`). Las **figuras van DEBAJO** de las cuerdas (U-5).
     *
     * OJO: el atributo `<durations>` del `.mscz` **no** es este modo (es un índice de variante de la
     * fuente `MuseScore Tab Modern`); y `stemsDown`/`minimStyle`/`stemsThrough` solo tienen sentido si
     * las plicas se muestran, lo que confirma que van activadas.
     */
    val noteValues: NoteValueStyle = NoteValueStyle.STEMS_AND_BEAMS,
    /** Dibujar también los silencios del pentagrama rítmico. */
    val showRests: Boolean = true,
    /**
     * Cuándo se repite el símbolo de duración (el `Repeat` de MuseScore).
     *
     * (D-45) Por defecto **[RhythmRepeat.MEASURE]**, no el `NEVER` de MuseScore (`stafftype.cpp:79`):
     * en un cancionero se espera que la figura se vuelva a rotular al abrir cada compás, y con
     * `NEVER` un compás de negras seguidas se queda sin ninguna referencia de duración. La desviación
     * es deliberada y se puede deshacer desde el panel del pentagrama.
     */
    val rhythmRepeat: RhythmRepeat = RhythmRepeat.MEASURE,
    /**
     * Cómo se dibuja la **blanca** ([MinimStyle]). Por defecto [MinimStyle.SHORTER], que es el
     * estilo del preset `Common` de MuseScore (`stafftype.cpp:947`) y el que usa la tablatura de
     * referencia (`<name>tab6StrCommon</name>`).
     */
    val minimStyle: MinimStyle = MinimStyle.SHORTER,
    /**
     * Qué se hace con la cifra de una nota que **recibe** una ligadura ([ShowTiedFret]). Por defecto
     * [ShowTiedFret.TIE_AND_FRET], el valor de MuseScore (`styledef.cpp:2096`).
     */
    val showTiedFret: ShowTiedFret = ShowTiedFret.TIE_AND_FRET,
    /**
     * Dónde se recuerda la cifra ligada cuando [showTiedFret] es [ShowTiedFret.TIE]
     * ([ParenthesizeTiedFret]). Por defecto [ParenthesizeTiedFret.START_OF_SYSTEM], el valor de
     * MuseScore (`styledef.cpp:2097`).
     */
    val parenthesizeTiedFret: ParenthesizeTiedFret = ParenthesizeTiedFret.START_OF_SYSTEM,
    /** Numerar los compases al inicio de cada compás. */
    val showMeasureNumbers: Boolean = true,
    /** Dibujar la clave de tablatura `TAB`. */
    val showClef: Boolean = true,
    /** Dibujar el compás (fracción) al inicio del sistema. */
    val showTimeSignature: Boolean = true,
    /**
     * Repetir la **clave** y el **compás** en **todos** los sistemas, no solo en el primero.
     *
     * MuseScore **no** lo hace en tablatura. `styleDef(hideTabClefAfterFirst, true)` en
     * `src/engraving/style/styledef.cpp` y `MeasureLayout::addHeaderClef()` en
     * `src/engraving/rendering/score/measurelayout.cpp` resuelven
     * `hideClef = staffType->isTabStaff() ? styleB(Sid::hideTabClefAfterFirst) : !styleB(Sid::genClef)`,
     * de modo que en un pentagrama de tablatura la clave se dibuja **solo en el primer sistema**
     * (`isFirstClef || !hideClef`). El `salmo2.pdf` lo confirma: el primer sistema arranca en
     * x = 281 px (clave + compás) y los otros tres en x = 177 px, **sin** ninguno de los dos. Por eso
     * el valor por defecto es `false`: repetirlos es lo que hacía que la tablatura «no pareciera
     * MuseScore» y además robaba 5 espacios de ancho a cada sistema.
     */
    val headerOnEverySystem: Boolean = false,
    /**
     * Rotular a la izquierda el **nombre de cada cuerda** (`E B G D A E`). MuseScore **no** las
     * dibuja (solo la clave `TAB`), así que el valor por defecto es `false` para que el pentagrama
     * no se distinga del suyo; la afinación ya se lee en la cabecera (`EADGBE`).
     */
    val showStringLabels: Boolean = false,
    /**
     * **Dirección de la plica** del pentagrama rítmico (`stemsDown` en MuseScore). Por defecto
     * **hacia abajo** (`true`), que es el valor del fichero del usuario (`stemsDown=1`) y lo que
     * encaja con que las figuras vivan **debajo** de la tablatura (U-5): la plica cuelga hacia el pie
     * de la página, no hacia las cuerdas.
     */
    val stemsDown: Boolean = true
) {
    companion object {
        /** Tipo `Simple` de MuseScore: solo cifras, sin pentagrama rítmico. */
        val SIMPLE = TabStaffOptions(noteValues = NoteValueStyle.NONE, minimStyle = MinimStyle.NONE)

        /** Tipo `Common` de MuseScore: cifras **y** ritmo con plicas y barras (el **defecto**). */
        val COMMON = TabStaffOptions()

        /**
         * Tipo `Full` de MuseScore (`stafftype.cpp:948`): como [COMMON] pero con la **blanca
         * rayada** ([MinimStyle.SLASHED]) en vez de con la plica corta.
         */
        val FULL = TabStaffOptions(minimStyle = MinimStyle.SLASHED)
    }
}

/**
 * **Caja de un compás** medida por la maquetación: dónde empieza y dónde acaba, para que el editor
 * pueda resaltarlo (selección) o colocar la columna fantasma de escritura sin volver a medir nada.
 */
data class TabMeasureBox(val measureIndex: Int, val left: Float, val right: Float)

/**
 * **Caja de una columna** (una nota o un acorde) medida por la maquetación: su centro en `x` y su
 * ancho en px. La usa el **editor** para el *hit testing* (dónde ha tocado el usuario) y para
 * superponer el cursor, la selección y el resaltado **sin volver a medir** el compás: así la
 * interacción cae exactamente donde el pintor dibujó la cifra.
 */
data class TabColumnBox(
    val measureIndex: Int,
    val slotIndex: Int,
    val centerX: Float,
    val left: Float,
    val width: Float,
    /**
     * **Voz** (1–4) de la columna. El editor la usa para pintar el resaltado con el color de la voz
     * de **cada** nota (no con el de la voz activa) y para el menú contextual de la casilla.
     */
    val voice: Int = 1
)

/**
 * **Rótulo del tempo** de una indicación de BPM (T5): `♩ = 92 · Andante`, como en una partitura. La
 * indicación italiana se deriva con [com.grafitto.laudate.domain.model.music.TempoMarking.fromBpm].
 *
 * Es el **recurso de reserva**: cuando la fuente trae el rótulo literal
 * ([com.grafitto.laudate.domain.model.tablature.TabMeasure.tempoText]) se usa ese, porque MuseScore
 * pinta el texto **tal cual** está escrito y no inventa la indicación italiana.
 */
fun tempoLabel(bpm: Int): String {
    val marking = com.grafitto.laudate.domain.model.music.TempoMarking.fromBpm(bpm)
    return "\u2669 = $bpm \u00b7 ${marking.italianName}"
}

/**
 * **Glifo vectorial** de técnica (§13.5.bis): se dibuja con `Canvas`, no con la cifra de texto, para
 * que los símbolos «de adorno» (ligadura, bend, vibrato, armónico…) se vean como en los editores de
 * referencia y no como caracteres sueltos que se recortan. El pintor decide las medidas; aquí solo
 * va **qué** símbolo y **dónde** (su centro).
 */
enum class TabGlyphKind {
    /** Trémolo: barras oblicuas sobre la cifra. */
    TREMOLO,

    /** Vibrato: ondulación sobre la cifra. */
    VIBRATO,

    /** Bend: arco hacia arriba con su flecha. */
    BEND,

    /** Slide: línea oblicua. */
    SLIDE,

    /** Ligadura (*hammer-on* / *pull-off*): arco entre dos notas. */
    SLUR,

    /**
     * **Ligadura de prolongación** (*tie*, B-10): arco entre dos notas de la **misma altura** que
     * suenan como una sola. Se dibuja **pegado a la cuerda**, como en MuseScore, y no desplaza nada.
     */
    TIE,

    /** Armónico: rombo sobre la cifra. */
    HARMONIC,

    /** Apagado de palma: `P.M.` sobre la cifra. */
    PALM_MUTE,

    /** Dejar sonar: `l.v.` sobre la cifra. */
    LET_RING,

    /** Tapping: `T` sobre la cifra (la nota se produce con la mano derecha). */
    TAPPING,

    /** Bend descendente: arco con la flecha hacia abajo. */
    BEND_DOWN,

    /** Pre-bend: arco con una barra vertical al inicio (se estira antes de pulsar). */
    PRE_BEND,

    /** Trino (`tr`): alternancia rápida con la nota superior. */
    TRILL,

    /** Arpegio: línea ondulada vertical (el acorde se despliega). */
    ARPEGGIO,

    /** Rake: ondulación corta antes de la nota. */
    RAKE,

    /** Armónico de pinza (`P.H.`). */
    PINCH_HARMONIC,

    /** Armónico pulsado (`T.H.`). */
    TAPPED_HARMONIC,

    /** Palanca: caída de tono (whammy dive). */
    WHAMMY_DIVE,

    /** Palanca: recogida de tono (whammy scoop). */
    WHAMMY_SCOOP,

    /** Palanca: trémolo (ondulación del tono). */
    TREMOLO_BAR,

    /** Efecto violín (`0<>`): crescendo con la rueda de volumen. */
    VIOLIN,

    /** Dedo de la **mano izquierda** (pisa la nota): `1..4`. */
    FRETTING_FINGER,

    /** Dedo/púa de la **mano derecha** que pulsa: `p/i/m/a`. */
    PICKING_FINGER,

    /**
     * **Regulador de crescendo** (*hairpin*): la cuña que se **abre** hacia la derecha. Se dibuja en
     * la franja de las dinámicas, bajo la cuerda inferior, y abarca [TabGlyph.from]..[TabGlyph.to].
     */
    CRESCENDO,

    /** **Regulador de diminuendo**: la cuña que se **cierra** hacia la derecha. */
    DIMINUENDO
}

/**
 * Un **símbolo vectorial** en el dibujo: [kind] dice qué se pinta y ([x], [y]) es su centro.
 *
 * [from] y [to] (opcionales) son los extremos del arco de **ligadura**, que abarca dos notas
 * consecutivas: el pintor los usa para trazar el arco entre ambas.
 *
 * [label] es el **rótulo** que acompaña al símbolo cuando la técnica tiene una magnitud (la cifra
 * del bend, `1/2`); `null` si no lleva. [span] es el **alcance en columnas** de los símbolos que se
 * prolongan (`P.M.`), `1` = solo su columna.
 */
data class TabGlyph(
    val kind: TabGlyphKind,
    val x: Float,
    val y: Float,
    val from: Offset2 = Offset2(x, y),
    val to: Offset2 = Offset2(x, y),
    val label: String? = null,
    val span: Int = 1,
    /**
     * **Trazos** del trémolo (1–4). Solo lo lee [TabGlyphKind.TREMOLO]; el resto de glifos lo
     * ignoran. Se copia de [TabPluck.strokes] al emitir el glifo, para que el pintor no tenga que
     * volver a mirar la nota.
     */
    val strokes: Int = 1
)

/** Punto en el plano del dibujo (Kotlin puro, sin depender de la UI). */
data class Offset2(val x: Float, val y: Float)

/**
 * **Alineación horizontal** de un [TabText] respecto de su `x`.
 *
 * Casi todo va **centrado** en su columna o compás, pero las indicaciones de **tempo** van
 * **alineadas a la izquierda** en el primer elemento de su compás, como en MuseScore: el rótulo
 * nace donde nace la música y crece hacia la derecha, sin invadir la columna anterior.
 */
enum class TabTextAlign { CENTER, LEFT }

/** Texto con su **centro** en `(x, y)`. El pintor lo dibuja centrado. */
data class TabText(
    val text: String,
    val x: Float,
    val y: Float,
    val kind: TabTextKind,
    /**
     * Técnicas de la nota, para que el pintor **no** repita en el texto los símbolos que ya va a
     * dibujar vectorialmente ([TabGlyph]). Vacío cuando no aplica (etiqueta, marca…).
     */
    val techniques: Set<TabTechnique> = emptySet(),
    /**
     * `true` si el texto se dibuja **girado 90°** (sentido antihorario), como la clave `TAB` de la
     * tablatura, que se lee de abajo arriba (R1). El pintor decide cómo rotarlo.
     */
    val rotated: Boolean = false,
    /**
     * **Voz** (1–4) de la nota que representa el texto. El pintor colorea la cifra con el color de su
     * voz ([TabVoiceColors]) cuando la voz no es la 1, como la opción «colorear voces» de MuseScore.
     * `1` = tinta normal de la música.
     */
    val voice: Int = 1,
    /**
     * **Alineación horizontal** respecto de [x]. Por defecto **centrado**; el tempo va a la
     * izquierda ([TabTextAlign.LEFT]) para nacer en el primer elemento de su compás.
     */
    val align: TabTextAlign = TabTextAlign.CENTER
)

/** Resultado de la maquetación: líneas, textos, glifos y tamaño total. */
data class TablatureDrawing(
    val lines: List<TabLine>,
    val texts: List<TabText>,
    val width: Float,
    val height: Float,
    val glyphs: List<TabGlyph> = emptyList(),
    val rhythmMarks: List<TabRhythmMark> = emptyList(),
    /**
     * **Grupos de barras de unión** (beams), como **índices** sobre [rhythmMarks]. Los decide el
     * Dominio ([TabBeamGrouping]) porque dependen del **compás**: en 4/4 las corcheas se unen de
     * cuatro en cuatro, no todas en una sola barra. El pintor solo dibuja lo que se le dice.
     */
    val beamGroups: List<List<Int>> = emptyList(),
    /**
     * **Corchetes de tresillo** del sistema, ya resueltos ([TabRhythmGeometry.TupletBracket]): la
     * línea que agrupa las figuras del tresillo, sus ganchos y su sitio. Los decide el Dominio porque
     * dependen del **compás** y de la **primera cuerda**; el pintor solo traza lo que se le dice.
     */
    val tuplets: List<TabRhythmGeometry.TupletBracket> = emptyList(),
    /**
     * **Estilo de las figuras** con el que se maquetó el sistema ([TabStaffOptions.noteValues]).
     *
     * Lo lleva el dibujo porque los consumidores que no reciben los ajustes —los **exportadores de
     * PDF**— tienen que saber si pintar la **cabeza** de nota (`Note symbols`) o solo plicas y
     * barras (`Stems and beams`), y si la plica cuelga o sube.
     */
    val noteValues: NoteValueStyle = NoteValueStyle.STEMS_AND_BEAMS,
    /** Dirección de la plica con la que se maquetó ([TabStaffOptions.stemsDown]). */
    val stemsDown: Boolean = true,
    /**
     * **Cómo se dibuja la blanca** con la que se maquetó el sistema ([TabStaffOptions.minimStyle]).
     *
     * Lo lleva el dibujo por la misma razón que [noteValues] y [stemsDown]: los **exportadores de
     * PDF** no reciben los ajustes del pentagrama, y sin esto imprimirían la blanca con plica corta
     * aunque en pantalla se viera rayada.
     */
    val minimStyle: MinimStyle = MinimStyle.SHORTER,
    /**
     * **Rejilla de interacción**: las cajas de las columnas reales del sistema, indexadas por
     * compás local → columnas. El editor las usa para el *hit testing* y para superponer el cursor;
     * así no tiene que volver a medir la tablatura por su cuenta.
     */
    val columns: Map<Int, List<TabColumnBox>> = emptyMap(),
    /** Caja de cada compás del sistema (izquierda/derecha), para la selección y el cursor. */
    val measureBounds: List<TabMeasureBox> = emptyList(),
    /** Borde **superior** del pentagrama (la línea de la cuerda de arriba), en px. */
    val gridTop: Float = 0f,
    /** Borde **inferior** del pentagrama (la línea de la cuerda de abajo), en px. */
    val gridBottom: Float = 0f,
    /** Borde **izquierdo** de la caja del pentagrama (donde arranca el primer compás). */
    val gridLeft: Float = 0f,
    /**
     * Borde **izquierdo de las cuerdas**: donde empieza el pentagrama **antes** de la clave y el
     * compás. En MuseScore la clave `TAB` y el compás van **sobre** el pentagrama, así que las
     * cuerdas arrancan **antes** que el primer compás y pasan por debajo de la cabecera.
     */
    val staffLeft: Float = 0f,
    /** Borde **derecho** de la caja del pentagrama. */
    val gridRight: Float = 0f,
    /** Separación vertical entre cuerdas, para que el editor sepa el alto de cada cuerda. */
    val stringSpacing: Float = 0f,
    /**
     * (D-54) **Espacio de pentagrama** con el que se maquetó el sistema, en px.
     *
     * Lo lleva el dibujo porque desde D-54 **no** se puede derivar de [stringSpacing]: el zoom del
     * número abre las cuerdas sin que los grosores, las plicas ni las barras crezcan. `0` = derivarlo
     * de [stringSpacing] (el caso de siempre).
     */
    val staffSpace: Float = 0f
)

/** Figura rítmica vectorial asociada a una posición de tablatura. */
data class TabRhythmMark(
    val figure: RhythmFigure,
    val x: Float,
    /**
     * **Punto de anclaje vertical** de la figura, en px desde el borde superior del **sistema** (no
     * del pentagrama: por encima de las cuerdas hay franjas de símbolos, textos y dinámicas).
     *
     * No significa lo mismo para una nota que para un silencio, porque tampoco se anclan igual:
     *  - **nota**: la **cuerda inferior** (la plica nace ahí y cuelga hacia abajo);
     *  - **silencio**: la **línea base** del glifo, que es la línea del pentagrama que le toca según
     *    MuseScore ([TabRhythmGeometry.restLine]).
     */
    val y: Float,
    /**
     * **Grupo irregular** al que pertenece la figura ([TabTuplet]), o `null` si vale lo que dice su
     * dibujo. El corchete agrupa las figuras que comparten **la misma** proporción.
     */
    val tuplet: TabTuplet? = null,
    val measureIndex: Int = 0,
    /**
     * **Posición de la figura dentro de su compás**, en **negras** desde el inicio. Es lo que permite
     * cortar las barras de unión por **pulso** ([TabBeamGrouping]) en vez de unir todo el compás en una
     * sola barra. `0.0` en la primera columna.
     */
    val beatOffset: Double = 0.0
) {
    /** `true` si la figura forma parte de un **tresillo** (`3:2`). */
    val isTriplet: Boolean get() = tuplet == TabTuplet.TRIPLET
}

/**
 * Ancho de cada **columna** de un compás.
 *
 * El ancho sale de la **duración** de la columna (roadmap C.1, ver [TabSpacing]): una negra ocupa el
 * ancho base ([TabLayoutSettings.cellWidth]), una corchea `1/1,499` de eso y un compás con puntillos
 * lo que suman sus valores. Antes el paso era **uniforme** y todas las columnas medían lo mismo, que
 * es lo que hacía que un compás de 8 corcheas ocupara igual que uno de 2 negras (F-04).
 *
 * **Los símbolos NO ensanchan la columna** (corrección del usuario, 2026-10-14): las técnicas, las
 * articulaciones y los arcos de púa se **apilan en vertical** sobre la cifra ([TabSymbolPlacer]), así
 * que el ancho depende **solo de la duración** y del **texto de la cifra**. Antes se reservaba sitio
 * extra para repartirlos en fila y la tablatura **se movía** al añadir un símbolo.
 *
 * **La cifra sí ensancha** (D-50): `HorizontalSpacing::minHorizontalDistance`
 * (`horizontalspacing.cpp:1222-1290`) mide la **forma** de cada elemento, y la forma de una cifra de
 * tablatura es su texto. Sin esto una semicorchea (1,554 espacios) se comía una cifra de dos dígitos
 * (2,035 espacios) y los números se pisaban. Se toma la cifra **más ancha** de la columna, porque
 * todas las cuerdas comparten el ancho.
 *
 * **Y ensancha con el zoom del número** (D-52): [TabLayoutSettings.fretNumberScale] multiplica el
 * ancho de la cifra, igual que multiplica su tamaño al pintarla. Sin esto, subir el número al 200 %
 * en el atril dibujaba cifras del doble de tamaño sobre columnas del tamaño normal.
 */
private fun TabMeasure.columnWidths(
    resolution: TabResolution,
    settings: TabLayoutSettings
): List<Float> {
    if (slots.isEmpty()) return listOf(settings.cellWidth)
    return slots.map { slot ->
        val widestFret = slot.strings.maxOfOrNull { pluck ->
            TabSpacing.fretTextWidthSpaces(fretMarkText(pluck, settings.staff.fretMarks))
        } ?: 0f
        TabSpacing.columnWidth(
            beats = TabSpacing.beatsOf(slot.rhythmicFigure, slot.tuplet, resolution),
            cellWidth = settings.cellWidth,
            minContentWidth = (widestFret * settings.fretNumberScale +
                2f * TabSpacing.FRET_TEXT_PADDING_SPACES) * settings.staffSpace
        ) + TabGraceGeometry.extraWidth(slot.graces.size, settings.staffSpace)
    }
}

private fun TabResolution.defaultFigure(): RhythmFigure = TabSpacing.defaultFigure(this)

/** Un **sistema** es una fila de compases completa(s) que caben en el ancho disponible. */
data class TabSystem(
    val measureIndices: List<Int>,
    val topY: Float,
    val height: Float,
    val drawing: TablatureDrawing,
    /**
     * **Factor de justificación** con el que se estiraron las columnas de este sistema (`1` = sin
     * estirar). Lo decide [TablatureSystemsLayout] con la regla de MuseScore
     * (`SystemLayout::shouldBeJustified`).
     */
    val stretch: Float = 1f,
    /**
     * `true` si este sistema **abre página** en la exportación a PDF: su primer compás lleva
     * `forcePageBreak` (el `Ctrl`+`Return` de MuseScore). El editor de scroll continuo lo ignora.
     */
    val startsPage: Boolean = false
)

/**
 * Maquetación de una tablatura repartida en **sistemas** (filas) que caben en [maxWidth].
 *
 * La **unidad mínima es el compás**: un compás nunca se parte; si no cabe en el ancho restante,
 * cae entero a la línea siguiente. Así la tablatura se lee de arriba abajo sin scroll horizontal.
 */
/**
 * **Franjas verticales del pentagrama**, de arriba abajo (idea de `TGTrackSpacing` de TuxGuitar,
 * §0.3.bis del roadmap). Cada símbolo declara la suya y **no** se elige una `y` a mano: así ningún
 * símbolo pisa a otro y cada cosa cae donde le toca.
 *
 * El `order` fija el apilado: la `y` de una franja es la **suma de los altos de las anteriores**.
 */
enum class TabZone(val order: Int) {
    /** Aire superior. */
    TOP(0),

    /** Signos de **navegación** (Segno, Coda, Fine, To Coda) y **cambio de octava** (`8va`). */
    MARKER(1),

    /** **Texto** libre y **tempo** (`♩ = 80 · Andante`). */
    TEXT(2),

    /**
     * **Voltas** (`1.` `2.` `1, 2.`): el **corchete** que abraza los compases de la casilla y su
     * rótulo. Tiene franja propia (D-37) porque en MuseScore la volta es un *spanner* que va **por
     * encima** de las barras de repetición y necesita más alto que una línea de texto: la línea del
     * corchete cae a `voltaPosAbove` (3 espacios sobre la pauta) y el gancho baja `voltaHook`
     * (2,2 espacios). Compartir la franja `REPEAT` habría subido las barras `|:` `:|` y el `x2`.
     */
    VOLTA(3),

    /** **Barras de repetición** (`|:` `:|`), cifra de veces (`x2`) y **saltos** (`D.C.`). */
    REPEAT(4),

    /**
     * **Símbolos de técnica y articulación** de la nota (acento, armónico, bend, vibrato, `P.M.`…):
     * van **sobre la cuerda**, nunca dentro de la cifra (U-1). Los **arcos de púa** (`↑` `↓`) son la
     * técnica `PICK_UP`/`PICK_DOWN` y se apilan aquí, en la misma pila: no tienen franja propia.
     */
    SYMBOLS(5),

    /** **Las cuerdas y las cifras**: la ZONA B. Dentro, **solo la cifra** (U-1). */
    TABLATURE(6),

    /** **Figuras de duración** de cada columna: van **debajo** del pentagrama (U-5). */
    FIGURE(7),

    /** **Digitación**: mano izquierda (`1-4`) y derecha (`p i m a`), **debajo** (U-6). */
    FINGERING(8),

    /**
     * **Dinámicas** (`pp`, `mf`, `ppp`…): van **DEBAJO** del pentagrama, en la franja más exterior.
     *
     * (D-5) Esto **corrige U-4**, que las pedía encima. Manda MuseScore:
     * `styleDef(dynamicsPlacement, PlacementV::BELOW)` en `src/engraving/style/styledef.cpp`, y el
     * `salmo2.pdf` del usuario también las pone abajo. Se colocan **por fuera** de las figuras de
     * duración y de la digitación para que no se pisen con las plicas.
     */
    DYNAMIC(9),

    /** Aire inferior del sistema. */
    BOTTOM(10)
}

/**
 * **Franjas ya resueltas** de un sistema: la `y` de cada una, calculada **sumando los altos de las que
 * tiene encima** (como `TGSpacing.getPosition` de TuxGuitar), y **solo** de las franjas que la
 * tablatura **usa**: un pentagrama sin dinámicas no deja el hueco de las dinámicas.
 *
 * Kotlin puro y determinista. Es la pieza que evita el «todo a ojo» y garantiza que nada se solape.
 */
data class TabStaffZones(
    /** Altura de una franja de símbolos (una línea de texto o de símbolos). */
    val tier: Float,
    val padding: Float,
    val stringSpacing: Float,
    val stringCount: Int,
    /** Franjas que este sistema **usa** (las demás no reservan sitio). */
    val used: Set<TabZone>,
    /**
     * Nº máximo de **articulaciones apiladas** en una misma nota del sistema. La franja `SYMBOLS` se
     * dimensiona con él (una franja por símbolo), de modo que una nota con `^ > 𝄢` reserva sitio para
     * las **tres** y ninguna se sale por arriba.
     */
    val maxArticulationStack: Int = 1,
    /**
     * `true` si el sistema lleva alguna **ligadura de expresión** (`S` de MuseScore). El arco se
     * dibuja **por encima** de la pila de símbolos, así que la franja `SYMBOLS` reserva además
     * [SLUR_SPACES] para que la panza del arco no se salga por arriba del sistema (antes se salía y
     * el arco aparecía **cortado** en el borde del lienzo).
     */
    val hasSlur: Boolean = false,
    /**
     * `true` si el sistema lleva algún **grupo irregular** (tresillo, quintillo…). El corchete vuela
     * [TabRhythmGeometry.TUPLET_ABOVE_SPACES] sobre la primera cuerda y la cifra se centra **en la
     * línea**, así que su tinta sube además [TabRhythmGeometry.TUPLET_NUMBER_HALF_SPACES]: la franja
     * `TOP` tiene que reservar las dos cosas o la cifra se sale por arriba del lienzo.
     *
     * MuseScore lo resuelve con la *skyline*: el bbox del `Tuplet` incluye el de la cifra y
     * `Autoplace::autoplaceMeasureElement` (`tupletlayout.cpp:80-88`) lo añade al borde superior del
     * sistema. Aquí se reserva en la franja, que es el mecanismo equivalente.
     */
    val hasTuplet: Boolean = false,
    /**
     * `true` si este sistema **rotula su primer compás** con el número. MuseScore cuelga el número
     * `measureNumberPosAbove` (2 espacios) sobre la primera cuerda y el bbox del sistema crece para
     * contenerlo; aquí la franja `TOP` reserva ese aire (D-55).
     */
    val showMeasureNumber: Boolean = false,
    /**
     * (D-59) **Zoom del número de traste** ([TabLayoutSettings.fretNumberScale]). La cifra se centra
     * **en** la línea de la cuerda, así que su tinta sube media altura y la pila de símbolos tiene que
     * arrancar **por encima** de ella: al agrandar la cifra ese aire crece con ella.
     */
    val fretNumberScale: Float = 1f
) {
    /** Un **espacio de pentagrama** en px: la unidad de la notación. */
    private val staffSpace: Float
        get() = stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES

    /** Alto propio de cada franja, según lo que tiene que contener. */
    fun heightOf(zone: TabZone): Float = when (zone) {
        // (D-41) El **grupo irregular** vuela por encima de la primera cuerda: la línea del corchete a
        // `TUPLET_ABOVE_SPACES` y la cifra, centrada en ella, media altura más. La franja `TOP` es la
        // más alta del sistema, así que reserva el total (no hay nada encima de donde restar). Antes
        // valía `tier * 0.5` y la cifra del tresillo salía **cortada por la mitad** en el borde.
        TabZone.TOP -> maxOf(
            tier * 0.5f,
            if (hasTuplet) {
                (TabRhythmGeometry.TUPLET_ABOVE_SPACES +
                    TabRhythmGeometry.TUPLET_NUMBER_HALF_SPACES) * staffSpace
            } else {
                0f
            },
            // (D-55) El **número de compás** cuelga a `measureNumberPosAbove` (2 espacios) sobre la
            // primera cuerda y su tinta sube hasta 3,12 espacios: la franja `TOP` tiene que reservarlo
            // o el número se sale por arriba del lienzo. Antes no se reservaba nada y el número caía
            // **sobre la primera cuerda** (medido: centro a 0,6 pt de la pauta, cuando MuseScore lo
            // pone a 2,32 espacios).
            if (showMeasureNumber) MEASURE_NUMBER_INK_TOP_SPACES * staffSpace else 0f
        )
        TabZone.BOTTOM -> tier * 0.5f
        // (D-8) El **rótulo del tempo** no se apila encima de la pila de articulaciones: MuseScore lo
        // cuelga a una altura **fija** sobre la pauta y lo deja **convivir** con los arcos, que están a
        // otra `x`. Medido del `salmo2.pdf`: el tempo ocupa y = 470–494 con la pauta en y = 535, o sea
        // **1,32–2,10 espacios** sobre la primera cuerda, mientras la pila de articulaciones del compás
        // 4 llega a **3,2 espacios**. Antes la franja `TEXT` se sumaba **por encima** de `SYMBOLS`, así
        // que el tempo se iba a **5,8 espacios** y el sistema reservaba un aire que el PDF no tiene.
        // Ahora solo reserva lo que le **falte** para llegar a [TEXT_ANCHOR_SPACES].
        TabZone.TEXT -> (TEXT_ANCHOR_SPACES * staffSpace + staffSpace - symbolsHeight())
            .coerceAtLeast(0f)
        TabZone.MARKER, TabZone.DYNAMIC, TabZone.REPEAT -> tier
        // (D-37) La franja de la **volta** no se apila: la línea del corchete va a una altura **fija**
        // sobre la pauta (`voltaPosAbove` = 3,0 espacios), así que la franja solo reserva lo que le
        // **falte** para que la tinta del rótulo (1,6 espacios sobre la línea) quepa por encima de lo
        // que ya ocupan las franjas de debajo (`REPEAT`, `SYMBOLS`). Es la misma cuenta que hace
        // `TEXT` con el tempo.
        TabZone.VOLTA -> ((VOLTA_POS_ABOVE_SPACES + VOLTA_TEXT_ASCENT_SPACES) * staffSpace -
            zonesBetween(TabZone.VOLTA, TabZone.TABLATURE)).coerceAtLeast(0f)
        // (2026-10-12) La franja de símbolos mide **una franja por articulación apilada**: antes era
        // un `stringSpacing * 0.8f` fijo y una nota con tres signos se salía por arriba.
        // (D-59) Además reserva el **aire de base** ([symbolBaseClearance]): la cifra de la cuerda 1 se
        // centra en la línea y su tinta sube media altura, así que la primera franja de símbolos tiene
        // que empezar por encima de ella o el glifo la pisa (trémolo, palanca, armónicos, `l.v.`).
        TabZone.SYMBOLS -> stringSpacing * (TabArticulationStack.TIER_SPACES *
            maxArticulationStack.coerceAtLeast(1)) + symbolBaseClearance +
            if (hasSlur) SLUR_SPACES * staffSpace else 0f
        TabZone.TABLATURE -> (stringCount - 1).coerceAtLeast(0) * stringSpacing
        // (2026-10-12) La fila de figuras no lleva **cabezas** (la cifra es la cabeza) y la plica
        // cuelga desde la cuerda inferior: la franja mide el **hueco de arranque** más la **plica**.
        // (2026-10-15) El hueco es `STEM_TOP_GAP_SPACES` (1,0 espacio, medido de MuseScore) y la
        // plica `STEM_LENGTH` (3,0 espacios): antes se contaba solo la plica y, al alargarla a 3,99,
        // la franja se comía el hueco y la plica nacía pegada a la cuerda 6.
        // (D-91) La franja de las **figuras** mide lo que cuelga de verdad: la plica y su bandera.
        // Antes sumaba además el **hueco de arranque** (`STEM_TOP_GAP_SPACES`), que es el aire entre
        // la cuerda y el comienzo del trazo —no sitio extra—, así que la franja medía 4 espacios
        // (42,7 px con un pentagrama de 80) y el sistema quedaba **casi el doble de alto** que su
        // rejilla. Medido del `salmo2.pdf`: entre la última tinta y el sistema siguiente hay 1,39
        // espacios, no 4.
        TabZone.FIGURE -> stringSpacing * TabRhythmGeometry.STEM_LENGTH /
            TabRhythmGeometry.STRING_SPACING_SPACES
        TabZone.FINGERING -> stringSpacing * 1.0f
    }

    /** Borde **superior** de la franja: la suma de los altos de las de arriba que se usan. */
    fun top(zone: TabZone): Float {
        // (D-90) La franja de las **figuras** empieza donde acaba la rejilla, no encima de ella: la
        // plica cuelga de la cuerda inferior. Antes seguía la suma de franjas y su `top` coincidía
        // con el borde **superior** del pentagrama, así que el hueco de la plica se medía desde
        // arriba y la dejaba 38 px por debajo.
        if (zone.order > TabZone.TABLATURE.order) {
            var below = gridBottom
            TabZone.entries
                .filter { it.order in (TabZone.TABLATURE.order + 1) until zone.order }
                .filter { it in used }
                .forEach { below += heightOf(it) }
            return below
        }
        var y = padding
        TabZone.entries.filter { it.order < zone.order }.forEach { above ->
            if (above in used) y += heightOf(above)
        }
        // (D-20) `styleDef(dynamicsMinDistance, 0.5_sp)` (`styledef.cpp:822`): la dinámica no se pega
        // al elemento de encima. Medido del `salmo2.pdf`, las figuras acaban en 11,56 espacios y el
        // `mp` empieza en 12,05: **0,49 espacios** de aire. Sin él la dinámica quedaba pegada a las
        // plicas (0,09 espacios) y el sistema se veía más apretado que el de MuseScore.
        if (zone.order >= TabZone.DYNAMIC.order && TabZone.DYNAMIC in used) {
            y += staffSpace * DYNAMIC_MIN_DISTANCE_SPACES
        }
        return y
    }

    /** Borde **inferior** de la franja. */
    fun bottom(zone: TabZone) = top(zone) + heightOf(zone)

    /**
     * `y` a la que se dibuja el contenido de la franja:
     *  - símbolos y textos → **centro** de la banda;
     *  - figuras de duración → donde va la **cabeza** (media banda, para que la plica quepa debajo);
     *  - la tablatura → su **borde superior** (la primera cuerda).
     */
    fun anchor(zone: TabZone): Float = when (zone) {
        // (D-8) El tempo se ancla **respecto de la pauta**, no de la franja: así queda a la altura
        // medida del PDF aunque la pila de articulaciones sea más alta que él.
        TabZone.TEXT -> gridTop - TEXT_ANCHOR_SPACES * staffSpace
        TabZone.TABLATURE -> top(zone)
        // (D-90) La figura de duración **cuelga de la cuerda inferior**, y su ancla es la **línea de
        // esa cuerda**, no el borde superior de la franja. `top(zone)` ya incluye el alto de la
        // franja `TABLATURE` —la distancia entre la primera y la última cuerda—, así que devolvía
        // el borde **superior** del pentagrama y la plica salía **38 px** por debajo de la cuerda
        // (medido). En el `salmo2.pdf` la plica arranca **inmediatamente** bajo la línea (y = 692 en
        // un pentagrama que acaba en 691), que es lo que hace MuseScore con `stemPosY`:
        // `stemTop = gridBottom + STEM_TOP_GAP_SPACES · staffSpace`.
        TabZone.FIGURE -> gridBottom
        // (D-37) La línea del corchete de la volta va a `voltaPosAbove` (3,0 espacios) sobre la
        // primera cuerda, y el rótulo se apoya **en** ella (se pinta anclado por su línea base).
        TabZone.VOLTA -> gridTop - VOLTA_POS_ABOVE_SPACES * staffSpace
        else -> top(zone) + heightOf(zone) / 2f
    }

    /**
     * Suma de los altos de las franjas **usadas** que caen entre [from] y [to] (ambas exclusivas).
     * Sirve para las franjas que se colocan a una altura **absoluta** respecto de la pauta (el tempo
     * y la volta) y solo tienen que reservar el **déficit** que dejan las de debajo.
     */
    private fun zonesBetween(from: TabZone, to: TabZone): Float {
        var total = 0f
        TabZone.entries.forEach { zone ->
            if (zone.order > from.order && zone.order < to.order && zone in used) total += heightOf(zone)
        }
        return total
    }

    /** Borde superior de las cuerdas (la primera línea). */
    val gridTop: Float get() = top(TabZone.TABLATURE)

    /** Borde inferior de las cuerdas (la última línea). */
    val gridBottom: Float get() = gridTop + heightOf(TabZone.TABLATURE)

    /**
     * Alto total del sistema: su **caja de tinta**, como el `bbox()` de MuseScore.
     *
     * (D-57) Antes sumaba además una franja `BOTTOM` entera (`tier` = 0,9 espacios) de aire **por
     * debajo de la última cuerda**. MuseScore no tiene ese aire: el bbox del sistema acaba en la
     * última cuerda (o en la plica, si la hay) y el aire de separación lo aporta
     * `minSystemDistance`/`maxSystemDistance`, que se mide **desde** ese borde. Sumarlo dos veces
     * engordaba cada sistema 6,75 pt y, con el paso uniforme de D-56, el paso del `salmo2` salía
     * **135,1 pt** donde MuseScore gasta **129,1**. Medido del PDF: la fila de los sistemas 2-4 mide
     * 60,1 pt aquí y 52,8 en MuseScore; los 7,3 pt de diferencia son exactamente esa franja.
     */
    val height: Float get() = top(TabZone.BOTTOM)

    /**
     * `y` del **número de compás**: el centro de su caja de texto, colgado a
     * [MEASURE_NUMBER_ANCHOR_SPACES] sobre la primera cuerda.
     *
     * (D-55) Antes se dibujaba a `tier * 0,25` del borde de la franja `TOP`, que con `staffSpace = 5`
     * lo dejaba a **0,6 pt** sobre la pauta: el número **se montaba sobre la primera cuerda**. MuseScore
     * lo cuelga a `measureNumberPosAbove` (2 espacios) y el bbox del sistema crece para contenerlo.
     */
    fun measureNumberY(): Float = gridTop - MEASURE_NUMBER_ANCHOR_SPACES * staffSpace

    /** Alto de la franja `SYMBOLS` si el sistema la usa (`0` si no hay articulaciones). */
    private fun symbolsHeight(): Float =
        if (TabZone.SYMBOLS in used) heightOf(TabZone.SYMBOLS) else 0f

    /**
     * (D-59) **Aire de base de la pila de símbolos**, en px: lo que la franja `SYMBOLS` reserva por
     * debajo de su primera sub-franja para que el símbolo más bajo **no pise la cifra** de la cuerda 1.
     *
     * La cifra se centra **en** la línea de la cuerda, así que su tinta sube media altura; la pila
     * arranca justo por encima de esa tinta ([TabArticulationStack.baseClearanceSpaces]).
     */
    val symbolBaseClearance: Float
        get() = TabArticulationStack.baseClearanceSpaces(fretNumberScale) * staffSpace

    companion object {
        /**
         * **Altura de la línea base del rótulo del tempo** sobre la primera cuerda, en espacios de
         * pentagrama.
         *
         * MuseScore ancla el `<Tempo>` por su **línea base** (`styleDef(tempoPosAbove, PointF(0, -2.0))`
         * en `styledef.cpp`), no por el centro de la caja de texto. Medido del `salmo2.pdf` (pauta en
         * y = 535, espacio = 20,67 px): `Andante` ocupa y = 457–493 y no tiene descendentes, así que su
         * línea base cae a `(535 − 493) / 20,67 = 2,03` espacios; el `0` de `= 80` acaba en y = 487, es
         * decir `(535 − 487) / 20,67 = 2,32`. Se toma **2,32**, el del rótulo con cifras, que es el que
         * más se ve. Antes valía 2,48 y se interpretaba como el **centro** de la caja: con el `♩` de
         * Bravura la caja crecía y el texto quedaba 0,6 espacios alto respecto de `Andante`.
         */
        const val TEXT_ANCHOR_SPACES: Float = 2.32f

        /**
         * **Centro de la caja del número de compás** sobre la primera cuerda, en espacios.
         *
         * `styleDef(measureNumberPosAbove, PointF(0.0, -2.0))` (`styledef.cpp:1284`) pone la **línea
         * base** a 2 espacios sobre la primera cuerda. Medido del `salmo2.pdf`: la tinta del `6` va de
         * y = 1009 a 1031 px con la pauta en 1073, o sea **2,02 espacios** de base a primera cuerda.
         *
         * El pintor centra la **caja de línea**, no la tinta: `baseline = y + 0,35 em`, con
         * `em = 1,6` espacios ([TabTextMetrics.MEASURE_NUMBER_EM]). Para que la base caiga a 2,0
         * espacios el ancla de la caja va a `2,0 + 0,35 · 1,6 = 2,56`.
         */
        const val MEASURE_NUMBER_ANCHOR_SPACES: Float = 2.56f

        /**
         * **Tinta superior del número de compás** sobre la primera cuerda, en espacios: lo que la
         * franja `TOP` tiene que reservar para que el número no se salga por arriba del sistema.
         *
         * Base a 2,0 espacios + la tinta del dígito (0,744 em de alto con `em = 1,6`) = **3,19**.
         * Medido del `salmo2.pdf`: la tinta del `6` llega a 3,10 espacios sobre la pauta.
         */
        const val MEASURE_NUMBER_INK_TOP_SPACES: Float = 3.2f

        /**
         * Aire que la franja `SYMBOLS` reserva **por encima** de la pila para la **ligadura de
         * expresión**, en espacios de pentagrama.
         *
         * El arco se traza con `TabSlurGeometry.arc(..., over = true)`, cuya panza sube
         * `0,75 · alto` sobre la línea base, y el alto máximo es `1,8 · unidad` con
         * `unidad = glyphSize / 3,1 = 0,77` espacios → la panza sube hasta **1,04 espacios** más el
         * desplazamiento de la línea base (`0,10 · glyphSize` = 0,24 espacios). De ahí **1,4
         * espacios**: el arco más largo posible queda dentro de la franja y no se corta.
         */
        const val SLUR_SPACES: Float = 1.4f

        /**
         * `styleDef(voltaPosAbove, PointF(0, -3.0))` (`styledef.cpp`): la **línea** del corchete de la
         * volta va **3 espacios** sobre la primera cuerda, medida **desde la pauta** y no desde la
         * franja. MuseScore la coloca con un desplazamiento **absoluto** (`Volta` es un *spanner*
         * `TextLineBase` cuyo `posAbove` es fijo), así que no se apila sobre las articulaciones: si
         * una nota lleva `^` y hay volta, ambos conviven a distinta altura.
         */
        const val VOLTA_POS_ABOVE_SPACES: Float = 3.0f

        /**
         * **Tinta del rótulo** por encima de su línea base, en espacios: con la talla de 11 pt
         * (`styleDef(voltaFontSize, 11.0)`, ≈2,2 espacios) la mayúscula mide ~1,6 espacios. El texto
         * se apoya **en** la línea del corchete (`alignBaseLine` con `AlignV::BASELINE` en
         * `src/engraving/rendering/score/tlayout.cpp`), de modo que la franja tiene que reservar esa
         * tinta **por encima** de la línea.
         */
        const val VOLTA_TEXT_ASCENT_SPACES: Float = 1.6f

        /** `styleDef(voltaHook, 2.2_sp)`: alto del **gancho** de apertura del corchete. */
        const val VOLTA_HOOK_SPACES: Float = 2.2f

        /**
         * `styleDef(voltaOffset, PointF(0.6, 2.2))`: el rótulo arranca **0,6 espacios** a la derecha
         * del gancho, para que la cifra no se monte sobre la línea vertical.
         */
        const val VOLTA_TEXT_OFFSET_SPACES: Float = 0.6f

        /**
         * Aire entre la franja de las **figuras** y la de las **dinámicas**, en espacios de
         * pentagrama: el `dynamicsMinDistance` de MuseScore (`styleDef(dynamicsMinDistance, 0.5_sp)`,
         * `styledef.cpp:822`). Medido del `salmo2.pdf`: las plicas y barras acaban en 11,56 espacios
         * y el `mp` empieza en 12,05.
         */
        const val DYNAMIC_MIN_DISTANCE_SPACES: Float = 0.5f

        /**
         * Calcula las franjas de un sistema a partir de **lo que lleva**: marca solo las que tienen
         * contenido, para no dejar huecos vacíos.
         */
        fun of(
            settings: TabLayoutSettings,
            stringCount: Int,
            measures: List<TabMeasure>,
            showFigures: Boolean,
            /**
             * `true` si el sistema **anterior** terminó con una ligadura de expresión abierta: este
             * sistema dibuja el trozo de **continuación**, así que también tiene que reservar la
             * franja aunque ninguna de sus casillas esté ligada.
             */
            slurFromPreviousSystem: Boolean = false,
            /**
             * `true` si este sistema **rotula** su primer compás: la franja `TOP` reserva el aire del
             * número (D-55).
             */
            showMeasureNumber: Boolean = false
        ): TabStaffZones {
            val used = mutableSetOf(
                TabZone.TOP,
                TabZone.TABLATURE,
                TabZone.BOTTOM
            )
            if (measures.any { it.navigation != null || it.octaveShift != null }) used += TabZone.MARKER
            if (measures.any { it.text != null || it.tempo != null }) used += TabZone.TEXT
            // (D-5) Las dinámicas van DEBAJO del pentagrama, como en MuseScore.
            // La franja de las dinámicas la comparten el **matiz** escrito y el **regulador** (la
            // cuña): los dos viven bajo la cuerda inferior, en la misma línea, como en MuseScore.
            if (measures.any { it.dynamic != null || it.hairpin != null }) used += TabZone.DYNAMIC
            if (measures.any { it.repeat != null || it.ending != null || it.jump != null }) used += TabZone.REPEAT
            // (D-37) La **volta** tiene franja propia, por encima de las barras de repetición.
            if (measures.any { it.ending != null }) used += TabZone.VOLTA
            val hasSymbols = measures.any { m ->
                m.slots.any { sl ->
                    sl.strings.any { pl ->
                        pl.techniques.any { it.vectorialGlyph() != null } ||
                            pl.articulations.isNotEmpty() ||
                            // El arco de púa es la técnica `PICK_UP`/`PICK_DOWN`: reserva la franja de
                            // símbolos (ya no tiene la suya propia).
                            pl.has(TabTechnique.PICK_DOWN) || pl.has(TabTechnique.PICK_UP)
                    }
                }
            }
            // La **ligadura de expresión** vive en la franja de símbolos (por encima de la pila), así
            // que la reserva aunque no haya ninguna articulación.
            val hasSlur = slurFromPreviousSystem ||
                measures.any { m -> m.slots.any { it.slurToNext } }
            if (hasSymbols || hasSlur) used += TabZone.SYMBOLS
            // (D-41) El **grupo irregular** necesita aire por encima de la primera cuerda: la franja
            // `TOP` se dimensiona con él (ver `heightOf`).
            val hasTuplet = measures.any { m -> m.slots.any { it.tuplet != null } }
            // (2026-10-12) El **arco de púa** ya **no** reserva franja propia: es la **técnica**
            // `PICK_UP`/`PICK_DOWN` y se dibuja en la franja de símbolos, junto a las articulaciones.
            // Antes duplicaba el símbolo (técnica + articulación) y salían dos arcos.
            // (D-55) La franja de figuras solo se reserva si el sistema tiene **notas**. Los
            // **silencios** se dibujan **dentro** del pentagrama (`TabRhythmGeometry.restLine`), no
            // debajo, así que un sistema de compases vacíos no gasta plica. Antes se reservaba con
            // solo `showFigures` y dejaba **20 pt de aire muerto** por sistema. Medido del `salmo2`:
            // la fila de los sistemas 2-4 medía 67,6 pt donde MuseScore gasta 52,8, y ese aire
            // empujaba los sistemas hacia abajo.
            val hasNotes = measures.any { m -> m.slots.any { it.strings.isNotEmpty() || it.isHeld } }
            if (showFigures && hasNotes) used += TabZone.FIGURE
            // (U-6) La digitación va DEBAJO, en su franja.
            val hasFingers = measures.any { m ->
                m.slots.any { sl -> sl.strings.any { pl -> pl.frettingFinger != null || pl.pickingFinger != null } }
            }
            if (hasFingers) used += TabZone.FINGERING
            // Nº máximo de símbolos que van **apilados sobre la misma nota** (D-6): el arco de púa, las
            // articulaciones y los **glifos de técnica** (bend, vibrato, `P.M.`…). Todos comparten una
            // sola pila vertical, así que la franja `SYMBOLS` reserva una sub-franja por símbolo y
            // ninguno se sale por arriba — y, al no repartirse en fila, **no ensanchan la columna**.
            val maxStack = measures
                .flatMap { it.slots }
                .maxOfOrNull { TabSymbolPlacer.maxStackCount(it) }
                ?: 0
            return TabStaffZones(
                tier = settings.stringSpacing * 0.9f,
                padding = settings.padding,
                stringSpacing = settings.stringSpacing,
                stringCount = stringCount,
                used = used,
                maxArticulationStack = maxStack.coerceAtLeast(1),
                hasSlur = hasSlur,
                hasTuplet = hasTuplet,
                showMeasureNumber = showMeasureNumber,
                fretNumberScale = settings.fretNumberScale
            )
        }
    }
}

object TablatureSystemsLayout {

    /**
     * **Aire entre la barra y la primera nota**, en espacios de pentagrama: `styleDef(barNoteDistance,
     * 1.25_sp)` en `src/engraving/style/styledef.cpp` (línea 272).
     */
    const val BAR_NOTE_SPACES: Float = 1.25f

    /**
     * **Aire entre la última nota y la barra**, en espacios: `styleDef(noteBarDistance, 1.5_sp)`
     * (`styledef.cpp`, línea 275).
     */
    const val NOTE_BAR_SPACES: Float = 1.5f

    /**
     * Ancho (px) de un compás: sus columnas reales **más el aire de las dos barras**.
     *
     * MuseScore no pega la primera nota a la barra: reserva `barNoteDistance` (1,25 espacios) antes y
     * `noteBarDistance` (1,5) después. Sin ese aire el compás se veía **más apretado** que el del
     * `salmo2.pdf`, donde la primera cifra cae a **1,31–1,50 espacios** de la barra y en nuestro
     * render caía a **0,49**.
     */
    private fun measureWidth(
        measure: TabMeasure,
        resolution: TabResolution,
        settings: TabLayoutSettings,
        capacity: Double,
        stretch: Float = 1f,
        /**
         * Ancho que ocupa la **indicación de compás** dibujada **dentro** del sistema cuando este
         * compás **cambia** el compás. En MuseScore el `TimeSig` es un segmento al principio del
         * compás, así que **suma** a su ancho; sin reservarlo, la fracción se montaría sobre la
         * primera cifra.
         */
        timeChangeSpace: Float = 0f
    ): Float {
        // **Silencio de compás completo**: MuseScore lo **centra** en el hueco libre del compás
        // (`MeasureLayout::layoutMeasure`, rama «center full measure rest»), así que **no** suma el
        // aire de las dos barras, pero **sí** el de la barra izquierda (`barNoteDistance`, que
        // `HorizontalSpacing::getFirstSegmentXPos` aplica a **todo** primer segmento de compás,
        // `horizontalspacing.cpp:1200`), y **sí** lo mide por duración: el ancho natural es el de la
        // columna de la redonda y `minMeasureWidth` entra **después**, como **suelo**
        // (`HorizontalSpacing::enforceMinimumMeasureWidths`). Ver [MIN_MEASURE_SPACES].
        if (measure.isFullMeasureRest(resolution, capacity)) {
            val durationWidth = TabSpacing.columnWidth(capacity, settings.cellWidth)
            val natural = settings.staffSpace * BAR_NOTE_SPACES + durationWidth
            val floor = settings.staffSpace * MIN_MEASURE_SPACES
            return maxOf(natural, floor) * stretch + timeChangeSpace
        }
        val barGap = settings.staffSpace * (BAR_NOTE_SPACES + NOTE_BAR_SPACES)
        return (barGap + measure.columnWidths(resolution, settings).sum()) * stretch +
            timeChangeSpace
    }

    /**
     * Ancho de la **indicación de compás** que se rotula **dentro** del sistema, en píxeles: el mismo
     * que reserva la cabecera para la fracción (`labelWidth · 0,95`). `0` si el compás no cambia el
     * compás o si las indicaciones están ocultas.
     */
    private fun timeChangeSpaceOf(
        tablature: Tablature,
        globalIndex: Int,
        settings: TabLayoutSettings
    ): Float = if (settings.staff.showTimeSignature && tablature.changesTimeAt(globalIndex)) {
        settings.labelWidth * 0.95f
    } else {
        0f
    }

    /**
     * Ancho de la **indicación de cortesía** que anuncia, al **final** de un sistema, el compás que
     * va a regir en el sistema siguiente, en píxeles. `0` si el compás [nextGlobalIndex] no cambia
     * el compás o si las indicaciones están ocultas.
     *
     * Es el comportamiento real de MuseScore 4: `MeasureLayout::addSystemTrailer`
     * (`src/engraving/rendering/score/measurelayout.cpp`, línea 1981) llama a `setCourtesyTimeSig`
     * con `SegmentType::TimeSigAnnounce` **solo para el último compás del sistema**
     * (`SystemLayout::layoutSystem`, `systemlayout.cpp`, línea 434), y `setCourtesyTimeSig`
     * (línea 1022) la crea cuando `genCourtesyTimesig` —`true` por defecto, `styledef.cpp:533`— y el
     * compás siguiente tiene indicación propia. La cortesía se dibuja a **tamaño completo** (no se
     * le aplica `setSmall`, a diferencia de la clave de cortesía), así que reserva el mismo ancho
     * que la fracción de la cabecera.
     *
     * **Ojo**: MuseScore **no** repite la indicación al **abrir** sistema (`addSystemHeader`,
     * línea 1887, solo añade barline de inicio, clave y armadura); lo que hace es **anunciarla** al
     * cerrar el anterior. Ver D-44 en `docs/TABLATURE_UX_PARITY.md`.
     */
    private fun courtesyTimeSpaceOf(
        tablature: Tablature,
        nextGlobalIndex: Int,
        settings: TabLayoutSettings
    ): Float = if (settings.staff.showTimeSignature && tablature.changesTimeAt(nextGlobalIndex)) {
        settings.labelWidth * 0.95f
    } else {
        0f
    }

    /**
     * **Ancho mínimo de un compás**, en espacios: `styleDef(minMeasureWidth, 8.0_sp)` en
     * `src/engraving/style/styledef.cpp` (línea 180), aplicado por
     * `HorizontalSpacing::computeMinMeasureWidth` (`src/engraving/rendering/score/horizontalspacing.cpp`,
     * línea 1018) y `enforceMinimumMeasureWidths` (línea 1054).
     *
     * **Es un SUELO, no el ancho.** MuseScore decide el corte de línea con el ancho **natural** del
     * compás y solo después sube a `minMeasureWidth` si se quedó corto. Aplicar el mínimo como ancho
     * daba **12** compases vacíos por sistema en vez de los **11** del referente.
     *
     * **Ancho natural de un compás vacío** (F-04.quater): `barNoteDistance` (1,25 espacios, que
     * `HorizontalSpacing::getFirstSegmentXPos` aplica a **todo** primer segmento de compás,
     * `horizontalspacing.cpp:1200`) **más** la columna de la **redonda**
     * (`TabSpacing.columnWidth(capacity, cellWidth)` = `3,5 · 4^0,585` = **7,875 espacios** en 4/4),
     * o sea **9,125 espacios**. El silencio se **centra** en ese hueco
     * (`MeasureLayout::layoutMeasure`, rama «center full measure rest», `measurelayout.cpp:2212`), así
     * que no se suma el aire de la barra derecha.
     *
     * **Comprobado contra el `salmo2.pdf`** (compases 6–32, todos silencio de compás completo): con
     * `102,89 / 9,125 = 11,28` caben **11 por sistema** —los sistemas 2 y 3 tienen 11 cada uno y el
     * 4.º, con 5, se justifica porque llena más del 30 %— y justificados quedan en
     * `102,89 / 11 = 9,35 espacios`, que es exactamente lo medido en el PDF (**193 px / 20,67 px =
     * 9,34**). Con el ancho de antes (8,585) cabían 11 por casualidad, pero el reparto de los
     * compases **con contenido** seguía sin coincidir.
     */
    const val MIN_MEASURE_SPACES: Float = 8.0f

    /**
     * ¿El compás es un **silencio de compás completo**? Es decir: o no tiene columnas, o tiene una
     * sola que es un silencio cuya duración cubre el compás entero ([capacity] en negras).
     *
     * MuseScore distingue el **silencio de compás** (`<durationType>measure</durationType>`, que se
     * centra y no ocupa ancho) de los **silencios sueltos** (una blanca, una negra…), que sí se
     * espacian por duración. Un compás de cuatro silencios de negra **no** es un compás vacío.
     */
    private fun TabMeasure.isFullMeasureRest(resolution: TabResolution, capacity: Double): Boolean {
        if (slots.isEmpty()) return true
        // (2026-10-27) La **rejilla de trabajo** del editor (ocho silencios de corchea en un
        // `4/4`) es, para el lector, un compas **entero en silencio**: se mide como **un**
        // silencio de compas, igual que `slots.isEmpty()`.
        if (TabMeasureMeter.isMeasureRest(this, resolution)) return true
        if (slots.size != 1) return false
        val slot = slots[0]
        if (slot.strings.isNotEmpty() || slot.isHeld) return false
        val beats = TabSpacing.beatsOf(slot.rhythmicFigure, slot.tuplet, resolution)
        return beats >= capacity - 1e-6
    }

    /**
     * **Umbral de llenado del último sistema** (`lastSystemFillLimit` de MuseScore, por defecto
     * `0,3`): un sistema que no llega a esta fracción del ancho disponible **no se justifica**.
     * MuseScore lo usa para no estirar un último sistema casi vacío
     * (`SystemLayout::shouldBeJustified`).
     */
    const val LAST_SYSTEM_FILL_LIMIT: Float = 0.3f

    /**
     * **Tope de justificación** de un sistema (D-76): un sistema nunca se estira más de **×1,5**.
     *
     * MuseScore justifica todo lo que no sea el último sistema, y aquí se hacía igual sin límite. El
     * problema aparece al **reducir el zoom**: los anchos naturales se encogen, el sistema entra
     * entero en el ancho disponible y el factor de estiramiento se dispara —con una tablatura corta
     * al 35 % llegaba a **×2,5**—, así que las cifras se separaban horizontalmente mientras las
     * cuerdas seguían juntas. El usuario lo describió como «cuando reduzco el tamaño de la tablatura,
     * ¿por qué se ensancha?»: encoger el zoom **encogía** la tablatura en vertical y la **estiraba**
     * en horizontal.
     *
     * Con el tope, un sistema que no llena el ancho se queda a su tamaño natural y deja aire a la
     * derecha, que es lo que se espera al reducir el zoom. Los sistemas que sí llenan el ancho (el
     * caso normal de una pieza larga) se justifican como siempre.
     */
    const val MAX_JUSTIFY_STRETCH: Float = 1.5f

    /**
     * **Aire mínimo entre el borde izquierdo del sistema y la primera nota**, en espacios:
     * `styleDef(systemHeaderMinStartOfSystemDistance, 1.25_sp)` en
     * `src/engraving/style/styledef.cpp`, aplicado por `HorizontalSpacing::getFirstSegmentXPos`
     * (`src/engraving/rendering/score/horizontalspacing.cpp`, línea 1200) con
     * `x = max(x, styleAbsolute(systemHeaderMinStartOfSystemDistance))`.
     *
     * Es la pieza que faltaba del **margen izquierdo** del sistema: MuseScore lo compone como
     * `clave + compás + 1,25 espacios`, y el `salmo2.pdf` lo confirma — el pentagrama arranca en
     * x = 281 px y la primera cifra en x ≈ 405 px, o sea **6,0 espacios**, de los que 4,0 son la
     * clave y el compás. Sin este aire, el primer compás se quedaba 1,25 espacios más ancho que el
     * del PDF y el reparto de los compases con contenido no cuadraba.
     */
    const val SYSTEM_HEADER_MIN_START_SPACES: Float = 1.25f

    /**
     * **Ancho natural** de la partitura entera (px) con las medidas dadas: la cabecera izquierda más
     * la suma de los compases, **sin** justificar y **sin** márgenes de página.
     *
     * Es lo que hace falta para el «ajustar al ancho» de MuseScore (D-14): la escala que llena el
     * lienzo es la que iguala este ancho al **ancho imprimible** del lienzo. Como todas las medidas
     * de [TabLayoutSettings] son proporcionales al espacio de pentagrama, el ancho natural escala
     * linealmente con el zoom, así que basta con medirlo **una vez** a la escala base.
     */
    fun naturalWidth(tablature: Tablature, settings: TabLayoutSettings): Float {
        val labelSpace = if (settings.staff.showStringLabels) settings.labelWidth else 0f
        val clefSpace = if (settings.staff.showClef) settings.labelWidth * 1.05f else 0f
        val timeSpace = if (settings.staff.showTimeSignature) settings.labelWidth * 0.95f else 0f
        val headerGap = if (clefSpace > 0f || timeSpace > 0f) {
            settings.staffSpace * SYSTEM_HEADER_MIN_START_SPACES
        } else {
            0f
        }
        val measures = tablature.measures.ifEmpty { listOf(TabMeasure()) }
        var total = labelSpace + clefSpace + timeSpace + headerGap
        measures.forEachIndexed { index, measure ->
            total += measureWidth(
                measure,
                tablature.resolution,
                settings,
                tablature.capacityAt(index),
                timeChangeSpace = timeChangeSpaceOf(tablature, index, settings)
            )
        }
        return total
    }

    /**
     * **Escala de «ajustar al ancho»** (D-14): el zoom al que la partitura ocupa exactamente el
     * **ancho imprimible** del lienzo (el lienzo menos los dos márgenes de página de MuseScore).
     *
     * MuseScore tiene el mismo comando (`Ctrl`+`Shift`+`J` en la versión 3, el botón de la barra de
     * estado en la 4) y sirve para lo mismo: ver la página entera de un vistazo sin adivinar el
     * porcentaje. Devuelve `1` si la partitura no tiene ancho medible.
     */
    fun fitToWidthScale(
        tablature: Tablature,
        viewportWidth: Float,
        settings: TabLayoutSettings
    ): Float {
        val natural = naturalWidth(tablature, settings)
        if (natural <= 0f || viewportWidth <= 0f) return 1f
        val printable = viewportWidth * (1f - 2f * TabLayoutSettings.PAGE_MARGIN_RATIO)
        return printable / natural
    }

    /**
     * **Reparto equilibrado del último sistema.** MuseScore es **greedy** (`curSysWidth >
     * targetSystemWidth` → corta) y **no** reequilibra: si el último sistema se queda con un solo
     * compás, lo deja así. Aquí sí se reequilibra, pero **atado al propio umbral de MuseScore**
     * ([LAST_SYSTEM_FILL_LIMIT]): mientras el último sistema no llegue a ese llenado se le pasa el
     * último compás del sistema anterior, de modo que el último sistema siempre sea justificable y
     * no quede un compás suelto en una línea.
     *
     * Se detiene si el sistema anterior ya tiene un solo compás o si el compás que se movería no
     * cabe en el último sistema.
     */
    private fun balanceLastLine(
        lines: MutableList<List<Int>>,
        widths: List<Float>,
        available: Float,
        measures: List<TabMeasure>
    ) {
        while (lines.size > 1) {
            val last = lines.last()
            // Un sistema que **empieza** con un salto forzado no se toca: el usuario decidió dónde
            // arranca (`forceSystemBreak` o `forcePageBreak`), y reequilibrar lo desharía.
            if (measures.getOrNull(last.first())?.startsSystem == true) return
            val lastWidth = last.fold(0f) { acc, i -> acc + widths[i] }
            if (lastWidth / available >= LAST_SYSTEM_FILL_LIMIT) return
            val previous = lines[lines.size - 2]
            if (previous.size <= 1) return
            val moved = previous.last()
            if (lastWidth + widths[moved] > available) return
            lines[lines.size - 2] = previous.dropLast(1)
            lines[lines.size - 1] = listOf(moved) + last
        }
    }

    /**
     * **Factor de justificación** de un sistema (MuseScore, `SystemLayout::shouldBeJustified`): el
     * sistema se estira hasta llenar [available] **salvo** que sea el último y no llegue a
     * [LAST_SYSTEM_FILL_LIMIT]. Nunca **encoge** (`coerceAtLeast(1f)`): un compás más ancho que el
     * disponible se sale, como en MuseScore, en vez de comprimirse y solapar las cifras.
     */
    private fun stretchOf(
        indices: List<Int>,
        widths: List<Float>,
        available: Float,
        isLast: Boolean
    ): Float {
        val natural = indices.fold(0f) { acc, i -> acc + widths[i] }
        if (natural <= 0f) return 1f
        // (D-92) **Todos los sistemas se justifican salvo el último**, que es lo que hace MuseScore
        // en `SystemLayout::shouldBeJustified` (`systemlayout.cpp:488-516`): el umbral
        // `lastSystemFillLimit` **solo** se consulta para el sistema que cierra la pieza (o el que
        // precede a un salto de sección). El resto se estira **siempre** hasta el ancho, que es lo
        // que hace que los bordes derechos coincidan y la tablatura se lea como una partitura.
        //
        // Antes (D-91) se aplicaba el umbral a todos: los sistemas cortos se quedaban a su tamaño
        // natural y el conjunto parecía descuadrado, con cada renglón acabando en un sitio distinto.
        if (isLast && natural / available < LAST_SYSTEM_FILL_LIMIT) return 1f
        // (D-76) El estiramiento tiene **tope**: sin él, reducir el zoom disparaba el factor y la
        // tablatura se «ensanchaba» en horizontal mientras se encogía en vertical.
        return (available / natural).coerceIn(1f, MAX_JUSTIFY_STRETCH)
    }

    /**
     * Reparte los compases en filas que caben en [maxWidth]. La **unidad mínima es el compás**: un
     * compás nunca se parte; si no cabe, cae entero a la línea siguiente. El ancho de cada compás
     * sale de sus **columnas reales** (modelo de columnas), no de un nº fijo.
     *
     * Después del reparto **greedy** (el de MuseScore) se aplican dos correcciones: el **reparto
     * equilibrado** ([balanceLastLine]) y la **justificación** ([stretchOf]).
     */
    fun layout(
        tablature: Tablature,
        maxWidth: Float,
        settings: TabLayoutSettings = TabLayoutSettings()
    ): List<TabSystem> {
        // (D-54) El **zoom del número** abre las cuerdas lo justo para que la cifra quepa, sin que el
        // resto del grabado engorde. Por debajo del 171 % esto devuelve los mismos ajustes.
        val settings = settings.withFretNumberSpacing()
        val strings = tablature.tuning.drawnStrings()
        val stringCount = strings.size
        val measures = tablature.measures.ifEmpty { listOf(TabMeasure()) }
        // Se descuenta lo que ocupa la **cabecera izquierda** del pentagrama (margen, etiquetas de
        // cuerda si se piden, clave `TAB` y compás), para que el reparto en líneas mida lo mismo que
        // luego se dibuja. El **número de compás** no reserva nada: va **encima** de la rejilla.
        val labelSpace = if (settings.staff.showStringLabels) settings.labelWidth else 0f
        val clefSpace = if (settings.staff.showClef) settings.labelWidth * 1.05f else 0f
        val timeSpace = if (settings.staff.showTimeSignature) settings.labelWidth * 0.95f else 0f
        // El reparto en líneas se hace con el ancho **más estrecho** (el del primer sistema, que sí
        // lleva clave y compás): así ningún sistema se sale del lienzo. Después, cada sistema se
        // justifica a **su** ancho real, que en los sistemas 2+ es mayor porque no llevan cabecera.
        // (D-13) El **margen de página** va a los **dos** lados: si solo se descuenta el de la
        // izquierda, la justificación estira el sistema hasta el borde derecho de la hoja y el margen
        // derecho desaparece.
        // (F-04.quater) El **margen izquierdo** del sistema es `clave + compás + 1,25 espacios`
        // (`systemHeaderMinStartOfSystemDistance`), como en MuseScore: sin ese aire el primer
        // compás salía 1,25 espacios más ancho que el del `salmo2.pdf`.
        val headerGap = if (clefSpace > 0f || timeSpace > 0f) {
            settings.staffSpace * SYSTEM_HEADER_MIN_START_SPACES
        } else {
            0f
        }
        // (F-04.quater) El **aire de arranque** del sistema (`clave + compás + 1,25 espacios`) forma
        // parte del **primer compás**, no del margen: MuseScore lo mete en el `minLeft` del primer
        // segmento, así que las cuerdas arrancan **después** de la cabecera y la cabecera se dibuja
        // **encima** del pentagrama. Medido del `salmo2.pdf`: las cuerdas empiezan en x = 281 px
        // (13,6 espacios = margen de 8,56 + 5,03 de cabecera) y la clave `TAB` cae en 296–331.
        val headerSpace = clefSpace + timeSpace + headerGap
        val reserved = settings.pageMargin * 2f + labelSpace + headerSpace
        val available = (maxWidth - reserved).coerceAtLeast(settings.cellWidth)
        // Con `headerOnEverySystem` cada línea lleva cabecera, así que también hay que descontarla.
        val availableWithoutHeader = (
            maxWidth - settings.pageMargin * 2f - labelSpace -
                if (settings.staff.headerOnEverySystem) headerSpace else 0f
            ).coerceAtLeast(settings.cellWidth)

        // Reparto de compases en líneas: se van acumulando mientras quepan. Cada compás se mide con
        // **su** indicación de compás (puede cambiar a mitad de pieza) y reserva el ancho de la
        // fracción cuando es él quien la cambia.
        val widths = measures.mapIndexed { index, measure ->
            measureWidth(
                measure,
                tablature.resolution,
                settings,
                tablature.capacityAt(index),
                timeChangeSpace = timeChangeSpaceOf(tablature, index, settings)
            )
        }
        // Anchos **para el reparto**: el compás 0 arrastra el aire de la cabecera, porque la cabecera
        // ocupa sitio **dentro** del sistema. La justificación, en cambio, se calcula **sin** ella
        // (no se estira) y con el ancho disponible ya descontado.
        val packWidths = widths.toMutableList().also { if (it.isNotEmpty()) it[0] += headerSpace }
        val lines = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var used = 0f
        measures.forEachIndexed { index, measure ->
            val w = packWidths[index]
            // (D-9) El **primer** sistema lleva clave y compás; los demás no, así que disponen de más
            // ancho. Empaquetar todos con el ancho estrecho dejaba los sistemas 2+ medio vacíos y
            // luego los estiraba en exceso.
            val lineAvailable = if (lines.isEmpty()) available else availableWithoutHeader
            if (current.isNotEmpty() && (used + w > lineAvailable || measure.startsSystem)) {
                lines += current
                current = mutableListOf()
                used = 0f
            }
            current += index
            used += w
        }
        if (current.isNotEmpty()) lines += current

        // Reparto **equilibrado**: el último sistema no se queda con un compás suelto. El último
        // sistema nunca es el primero, así que se mide con el ancho **sin** cabecera.
        balanceLastLine(lines, packWidths, availableWithoutHeader, measures)

        // (D-47) Notas que **reciben** una ligadura de prolongación, en una sola pasada. Se calcula
        // aquí, una vez por documento, y no dentro de `drawSystem`: la ligadura puede cruzar de
        // sistema, así que cada sistema necesita saber lo que dejó abierto el anterior.
        val tiedFromPrevious = TabTiedFret.tiedFromPrevious(tablature.measures)
        val result = mutableListOf<TabSystem>()
        var y = 0f
        // `true` si el sistema anterior terminó con una ligadura de expresión abierta: el siguiente
        // dibuja el trozo de continuación.
        var slurOpen = false
        lines.forEachIndexed { lineIndex, indices ->
            // **Justificación**: el sistema se estira hasta llenar el ancho disponible (salvo el
            // último, si no llega al umbral de MuseScore). El primer sistema —o todos, si se pide
            // repetir la cabecera— lleva clave y compás, así que dispone de menos ancho.
            val hasHeader = lineIndex == 0 || settings.staff.headerOnEverySystem
            // (D-44) **Indicación de cortesía**: si el compás que **abre el sistema siguiente**
            // cambia el compás, MuseScore anuncia la fracción nueva al **final** de este sistema
            // (`MeasureLayout::addSystemTrailer` -> `setCourtesyTimeSig`). Su franja se reserva a la
            // derecha del sistema y se **descuenta** del ancho justificable.
            val courtesy = courtesyTimeSpaceOf(
                tablature,
                (indices.lastOrNull() ?: 0) + 1,
                settings
            )
            val stretch = stretchOf(
                indices = indices,
                widths = widths,
                available = (if (hasHeader) {
                    available - headerSpace
                } else {
                    availableWithoutHeader
                }) - courtesy,
                isLast = lineIndex == lines.lastIndex
            )
            val drawing = drawSystem(
                tablature = tablature,
                strings = strings,
                stringCount = stringCount,
                measureIndices = indices,
                settings = settings,
                // (U-3) El número de compás se rotula solo **desde el segundo sistema**.
                systemIndex = result.size,
                systemCount = lines.size,
                stretch = stretch,
                slurFromPreviousSystem = slurOpen,
                courtesyTimeSpace = courtesy,
                tiedFromPrevious = tiedFromPrevious
            )
            result += TabSystem(
                measureIndices = indices,
                topY = y,
                height = drawing.height,
                drawing = drawing,
                stretch = stretch,
                // **Salto de página** (`Ctrl`+`Return`): el sistema abre hoja en el PDF.
                startsPage = indices.firstOrNull()
                    ?.let { measures.getOrNull(it)?.forcePageBreak } == true
            )
            y += drawing.height + settings.systemGap
            // La cadena queda abierta si la **última** casilla del sistema está ligada.
            slurOpen = indices.lastOrNull()
                ?.let { measures[it].slots.lastOrNull()?.slurToNext }
                ?: false
        }
        return result
    }

    private fun drawSystem(
        tablature: Tablature,
        strings: List<Pair<Int, com.grafitto.laudate.domain.model.tablature.TuningString>>,
        stringCount: Int,
        measureIndices: List<Int>,
        settings: TabLayoutSettings,
        /** Índice de este sistema (0 = el primero). */
        systemIndex: Int,
        /** Número total de sistemas del documento. */
        systemCount: Int,
        /** **Factor de justificación** de las columnas (`1` = sin estirar). */
        stretch: Float = 1f,
        /** `true` si el sistema anterior dejó una ligadura de expresión abierta. */
        slurFromPreviousSystem: Boolean = false,
        /**
         * (D-44) Ancho (px) de la **indicación de cortesía** que anuncia el compás del sistema
         * siguiente. Se dibuja **después** de la barra final del sistema, como el
         * `SegmentType::TimeSigAnnounce` de MuseScore.
         */
        courtesyTimeSpace: Float = 0f,
        /**
         * (D-47) Notas que **reciben** una ligadura de prolongación ([TabTiedFret.tiedFromPrevious]).
         * Se pasa desde [layout] porque la ligadura puede cruzar de sistema.
         */
        tiedFromPrevious: Set<TabTieKey> = emptySet()
    ): TablatureDrawing {
        // Orden a la izquierda del pentagrama, como en MuseScore: barra del sistema → clave `TAB` →
        // compás (fracción) → primer compás. Las **etiquetas de cuerda** solo ocupan franja si se
        // piden (MuseScore no las dibuja) y los **números de compás** van **encima**, sin franja.
        val labelSpace = if (settings.staff.showStringLabels) settings.labelWidth else 0f
        // (D-9) La **clave** y el **compás** solo se **dibujan** en el **primer** sistema, como
        // MuseScore en tablatura (`hideTabClefAfterFirst = true`). Repetirlos en todos era una de las
        // razones por las que la tablatura no se parecía a la suya, y además robaba ancho a cada
        // sistema.
        val drawsHeader = systemIndex == 0 || settings.staff.headerOnEverySystem
        val clefSpace = if (settings.staff.showClef && drawsHeader) settings.labelWidth * 1.05f else 0f
        val timeSpace = if (settings.staff.showTimeSignature && drawsHeader) {
            settings.labelWidth * 0.95f
        } else {
            0f
        }
        // (F-04.quater) El **aire mínimo** de MuseScore entre el borde del sistema y la primera
        // nota (`systemHeaderMinStartOfSystemDistance`, 1,25 espacios) forma parte del margen
        // izquierdo: sin él el primer compás se dibujaba 1,25 espacios más ancho que el del PDF.
        val headerGap = if (clefSpace > 0f || timeSpace > 0f) {
            settings.staffSpace * SYSTEM_HEADER_MIN_START_SPACES
        } else {
            0f
        }
        // **Aire de la cabecera**: clave + compás + el aire de arranque. En MuseScore la cabecera va
        // **dentro** del sistema (es el `minLeft` del primer segmento del compás 1), así que las
        // cuerdas **arrancan después** de ella y la clave y el compás se dibujan **encima** del
        // pentagrama. Medido del `salmo2.pdf`: cuerdas en x = 281 px (13,6 espacios = margen 8,56 +
        // 5,03 de cabecera), clave `TAB` en 296–331, `4/4` en 352–387 y primera cifra en 433.
        val headerInset = clefSpace + timeSpace + headerGap
        // **Borde izquierdo del pentagrama**: donde **arrancan las cuerdas**.
        val staffLeft = settings.pageMargin + labelSpace + headerInset
        val contentLeft = staffLeft
        val measures = measureIndices.mapNotNull { tablature.measures.getOrNull(it) }
        // **Justificación**: todas las columnas del sistema se estiran por el **mismo** factor, así
        // que todo lo que cuelga de ellas (cifras, barras, plicas, corchetes, arcos) se recoloca
        // solo y el sistema llena el ancho disponible sin tocar nada más.
        //
        // El ancho del compás es el de sus columnas **más el aire de las dos barras**
        // (`barNoteDistance` + `noteBarDistance` de MuseScore), igual que en [measureWidth]: si aquí
        // se midiera solo la suma de columnas, el reparto en líneas y el dibujo no coincidirían.
        // El aire se estira con el **mismo** factor que las columnas: [stretchOf] reparte el ancho
        // disponible entre el compás **entero** (columnas + aire), así que si el aire no se estirara
        // el sistema se quedaría corto y no llenaría el ancho, que es justo lo que hace MuseScore.
        val barGap = settings.staffSpace *
            (TablatureSystemsLayout.BAR_NOTE_SPACES + TablatureSystemsLayout.NOTE_BAR_SPACES) * stretch
        // Aire de la barra que abre el compás (`barNoteDistance` de MuseScore): la primera columna
        // **no** empieza pegada a la barra. Se estira con el sistema, como [barGap].
        val barNote = settings.staffSpace * TablatureSystemsLayout.BAR_NOTE_SPACES * stretch
        // (F-04.bis) El ancho sale de la **misma** función que el reparto en líneas, para que un
        // compás con **silencio de compás completo** mida el mínimo de MuseScore y no una redonda.
        // **Indicación de compás vigente** de cada compás del sistema: puede cambiar a mitad de
        // pieza, así que el ancho, el silencio de compás completo y la rejilla se miden con la suya.
        val capacities = measures.indices.map { local ->
            tablature.capacityAt(measureIndices.getOrNull(local) ?: local)
        }
        // Ancho de la fracción que se rotula **dentro** del sistema cuando el compás cambia el compás.
        val timeChangeSpaces = measures.indices.map { local ->
            timeChangeSpaceOf(tablature, measureIndices.getOrNull(local) ?: local, settings)
        }
        val widths = measures.mapIndexed { index, measure ->
            TablatureSystemsLayout.measureWidth(
                measure, tablature.resolution, settings, capacities[index], stretch,
                timeChangeSpaces[index]
            ) + if (index == 0) headerInset else 0f
        }
        val columnWidths = measures.mapIndexed { index, measure ->
            if (measure.isFullMeasureRest(tablature.resolution, capacities[index])) {
                // El silencio de compás se **centra** en el hueco libre (MuseScore): la columna ocupa
                // el compás entero, así que el glifo cae en su centro exacto. Se descuentan **dos**
                // veces el aire de la barra de apertura porque la columna empieza en
                // `measureLeft + barNote` y su centro es `barNote + ancho/2`.
                val inset = if (index == 0) headerInset else 0f
                listOf(
                    (widths[index] - inset - timeChangeSpaces[index] - 2f * barNote)
                        .coerceAtLeast(0f)
                )
            } else {
                measure.columnWidths(tablature.resolution, settings).map { it * stretch }
            }
        }
        // El final del sistema puede cerrar con repetición (`:| x2`), que se dibuja **más allá** de la
        // barra: se reserva una franja a la derecha para que no se salga del lienzo ni del sistema.
        val endMarkSpace = if (measures.lastOrNull()?.repeat == TabRepeatMark.END ||
            measures.lastOrNull()?.repeat == TabRepeatMark.BOTH
        ) {
            settings.cellWidth * 1.2f
        } else {
            0f
        }
        // El **número de compás** va *encima* de la rejilla (como MuseScore), así que **no** reserva
        // franja horizontal: el pentagrama empieza justo tras el compás.
        val gridLeft = contentLeft
        val contentRight = gridLeft + widths.sum()
        // (D-44) La **indicación de cortesía** va **más allá** de la barra final del sistema, así que
        // su franja se suma al ancho dibujado (y no al del último compás, que acabaría metiéndola
        // dentro del compás, antes de la barra).
        val drawingRight = contentRight + endMarkSpace + courtesyTimeSpace
        // ══ **Franjas del pentagrama** (idea de `TGTrackSpacing` de TuxGuitar, §0.3.bis) ═══════════
        // Cada símbolo tiene su franja **con nombre** y la `y` sale de **sumar los altos de las franjas
        // de arriba**. Nada de `stringSpacing * 1.4f` a ojo: eso era lo que hacía que los símbolos se
        // pisaran (U-1, U-4, U-5, U-6).
        val showFigures = settings.staff.noteValues != NoteValueStyle.NONE
        // (D-55) El número de compás solo se rotula al abrir sistema (y nunca en el primero), así que
        // solo ese sistema reserva su aire.
        val showsMeasureNumber = settings.staff.showMeasureNumbers && systemCount > 1 && systemIndex > 0
        val zones = TabStaffZones.of(
            settings, stringCount, measures, showFigures, slurFromPreviousSystem, showsMeasureNumber
        )
        val tier = zones.tier
        val contentTop = zones.gridTop
        val contentBottom = zones.gridBottom
        // `y` de cada zona, ya resuelta.
        val markerY = zones.anchor(TabZone.MARKER)
        val textY = zones.anchor(TabZone.TEXT)
        val dynamicY = zones.anchor(TabZone.DYNAMIC)
        val repeatY = zones.anchor(TabZone.REPEAT)
        // **Base de la pila de símbolos**: la pila crece **hacia arriba** desde la primera cuerda, así
        // que su base es el **borde inferior** de la franja `SYMBOLS` menos media franja, para que el
        // símbolo más bajo quede **centrado** en su sub-franja y no se salga por abajo. Antes se usaba
        // el **borde superior** de la franja y la pila subía **fuera** de ella, pisando las franjas de
        // arriba (dinámicas, repeticiones…).
        // (D-59) El borde inferior de la franja **coincide con la línea de la cuerda 1** (`bottom` de
        // `SYMBOLS` es `gridTop`, porque `TABLATURE` empieza donde acaba `SYMBOLS`), así que el **aire
        // de base** se descuenta aquí: la franja lo reserva en su alto y la pila arranca por encima de
        // la tinta de la cifra. Antes la pila arrancaba a media franja de la línea y el glifo
        // (2,4 espacios de caja con la franja en 1,65) bajaba 0,375 espacios **sobre el número**.
        val tierPx = settings.stringSpacing * TabArticulationStack.TIER_SPACES
        val symbolsY = zones.bottom(TabZone.SYMBOLS) - zones.symbolBaseClearance - tierPx * 0.5f
        val figureY = zones.anchor(TabZone.FIGURE)
        val fingerY = zones.anchor(TabZone.FINGERING)

        val lines = mutableListOf<TabLine>()
        val texts = mutableListOf<TabText>()
        val glyphs = mutableListOf<TabGlyph>()
        val rhythmMarks = mutableListOf<TabRhythmMark>()

        strings.forEachIndexed { index, (stringNumber, _) ->
            val y = contentTop + index * settings.stringSpacing
            // **Líneas interrumpidas** (`Broken` en MuseScore): la cuerda se traza por tramos, dejando
            // un hueco en cada cifra. Se calcula aquí, una sola vez, con los centros reales.
            // (D-22) Las cuerdas arrancan en `staffLeft`, que **ya incluye** la cabecera: la clave y el
            // compás ocupan el principio del compás 1 y se dibujan **encima** de las cuerdas, como en
            // MuseScore (`minLeft` del primer segmento). Por eso `staffLeft == gridLeft`.
            if (settings.staff.lines == LineStyle.BROKEN_ON_MARKS) {
                lines += brokenSegments(
                    staffLeft, gridLeft, contentRight, y, stringNumber, measures, columnWidths,
                    barNote, barGap, settings, headerInset
                )
            } else {
                lines += TabLine(staffLeft, y, contentRight, y)
            }
            // La **etiqueta de la cuerda** (opcional): solo se rotula si se ha pedido.
            if (settings.staff.showStringLabels) {
                val label = tablature.tuning.pitchOf(stringNumber)?.step?.uppercaseChar()?.toString() ?: "?"
                texts += TabText(label, settings.pageMargin + settings.labelWidth / 2f, y, TabTextKind.STRING_LABEL)
            }
        }
        // **Clave de tablatura** `TAB` (R1): al principio del **compás 1**, sobre el pentagrama y
        // centrada en la altura de la rejilla, con las cuerdas pasando por debajo (D-22; medido del
        // `salmo2.pdf`: cuerdas en x = 281 px, clave en 296–330).
        if (settings.staff.showClef && drawsHeader) {
            // (D-55) El texto es el **punto de código SMuFL** de la clave (`6stringTabClef` /
            // `4stringTabClef`), no la cadena `TAB`: así el lienzo y los dos PDF dibujan el mismo
            // glifo sin tener que adivinar el número de cuerdas cada uno por su cuenta. Antes el PDF
            // de escritorio imprimía el texto literal `TAB` en Helvetica.
            texts += TabText(
                TabGlyphs.tabClef(stringCount),
                staffLeft + clefSpace / 2f,
                (contentTop + contentBottom) / 2f,
                TabTextKind.TAB_CLEF
            )
        }
        // **Compás** (fracción): al inicio del **sistema**, a la derecha de la clave y **sobre** el
        // pentagrama. El numerador y el denominador van **apilados** y centrados verticalmente en la
        // caja del pentagrama, como en una partitura (antes el editor lo pintaba aparte, con su propio
        // ancho, y no coincidían).
        if (settings.staff.showTimeSignature && drawsHeader) {
            val timeX = staffLeft + clefSpace + timeSpace / 2f
            val timeCenterY = (contentTop + contentBottom) / 2f
            // La fracción de la cabecera es la **vigente en el primer compás del sistema**, no la del
            // documento: con cambio de compás a mitad de pieza, el sistema que empieza después del
            // cambio tiene que rotular la nueva (MuseScore repite la indicación al abrir sistema).
            val headerTime = tablature.timeAt(measureIndices.firstOrNull() ?: 0)
            // Los dos dígitos se **apilan** a la altura del pentagrama. Medido del `salmo2.pdf`: cada
            // dígito queda a **0,66 separaciones de cuerda** del centro (el par ocupa 3,94 esp, como el
            // propio pentagrama). Antes iban a 0,8 y salían demasiado separados.
            texts += TabText(
                headerTime.beats.toString(),
                timeX,
                timeCenterY - settings.stringSpacing * TIME_SIG_CENTER_OFFSET,
                TabTextKind.TIME_NUMERATOR
            )
            texts += TabText(
                headerTime.beatType.toString(),
                timeX,
                timeCenterY + settings.stringSpacing * TIME_SIG_CENTER_OFFSET,
                TabTextKind.TIME_DENOMINATOR
            )
        }

        // Barras verticales: una al final de cada compás, con el **tipo** de ese compás (doble/final),
        // como en MuseScore. **OJO** (D-4, medido del `salmo2.pdf`): MuseScore **no** traza ninguna
        // barra de **apertura** de sistema; las cuerdas arrancan directamente tras la clave (1.er
        // sistema) o en el margen (los demás). Antes se dibujaba una barra `isSystemStart` a la
        // izquierda que el PDF **no** tiene.
        var x = gridLeft
        // (D-53) La barra **fina + gruesa** (`𝄂`) cierra la **pieza**, no cada línea: en MuseScore
        // solo el último compás del **último** sistema lleva `endBarType = END`; los sistemas
        // intermedios cierran con la barra **simple** de su compás. Antes se forzaba `FINAL` en el
        // último compás de **todos** los sistemas, así que el `salmo2.pdf` salía con cuatro barras
        // finales donde MuseScore tiene una (medido: el PDF de referencia cierra los sistemas 1-3
        // con un solo trazo en x = 2302 y el 4 con dos, en 2282 y 2298).
        val isLastSystem = systemIndex == systemCount - 1
        // (D-65) **Repeticiones en la barra**. El `|:` de un compás cae en su **borde izquierdo**, que
        // es la misma `x` que la barra derecha del compás anterior: por eso se resuelven **por
        // frontera** y no por compás. Si el compás que cierra pide `:|` y el que abre pide `|:`, la
        // frontera lleva los dos (`BOTH`), que es lo que hace MuseScore con `… :| |: …`.
        //
        // La frontera **izquierda del sistema** solo se traza si el primer compás abre repetición:
        // MuseScore no dibuja barra de apertura de sistema (D-4), pero el `|:` **sí** es una barra y
        // sin ella la repetición se quedaría sin su signo al empezar una línea.
        val firstRepeat = measures.firstOrNull()?.repeat
        if (firstRepeat == TabRepeatMark.START || firstRepeat == TabRepeatMark.BOTH) {
            lines += TabLine(
                gridLeft, contentTop, gridLeft, contentBottom,
                isBar = true,
                barline = TabBarline.SINGLE,
                repeat = TabRepeatMark.START
            )
        }
        measures.forEachIndexed { m, measure ->
            x += widths[m]
            val isLastOfPiece = m == measures.lastIndex && isLastSystem
            // Un `DOUBLE`/`FINAL` **explícito** del compás manda siempre; el cierre de la pieza
            // completa el `SINGLE` por defecto con la barra final.
            val barline = if (isLastOfPiece && measure.barline == TabBarline.SINGLE) {
                TabBarline.FINAL
            } else {
                measure.barline
            }
            val closes = measure.repeat == TabRepeatMark.END || measure.repeat == TabRepeatMark.BOTH
            val nextRepeat = measures.getOrNull(m + 1)?.repeat
            val opens = nextRepeat == TabRepeatMark.START || nextRepeat == TabRepeatMark.BOTH
            val repeat = when {
                closes && opens -> TabRepeatMark.BOTH
                closes -> TabRepeatMark.END
                opens -> TabRepeatMark.START
                else -> null
            }
            lines += TabLine(
                x, contentTop, x, contentBottom,
                isBar = true,
                barline = barline,
                repeat = repeat
            )
        }

        // (D-44) **Indicación de cortesía** al final del sistema: anuncia la fracción que regirá en el
        // sistema siguiente, como el `TimeSigAnnounce` de MuseScore. Va **después** de la barra final
        // del sistema, centrada en su franja, y a **tamaño completo** (MuseScore no le aplica
        // `setSmall`). Sin ella, el lector se encontraba el compás nuevo sin aviso al pasar de línea.
        if (courtesyTimeSpace > 0f) {
            val courtesyX = contentRight + endMarkSpace + courtesyTimeSpace / 2f
            val courtesyCenterY = (contentTop + contentBottom) / 2f
            val courtesyTime = tablature.timeAt((measureIndices.lastOrNull() ?: 0) + 1)
            texts += TabText(
                courtesyTime.beats.toString(),
                courtesyX,
                courtesyCenterY - settings.stringSpacing * TIME_SIG_CENTER_OFFSET,
                TabTextKind.TIME_NUMERATOR
            )
            texts += TabText(
                courtesyTime.beatType.toString(),
                courtesyX,
                courtesyCenterY + settings.stringSpacing * TIME_SIG_CENTER_OFFSET,
                TabTextKind.TIME_DENOMINATOR
            )
        }

        // Columnas: cada compás se dibuja a partir de su desfase acumulado, con una columna por
        // slot (no hay casillas vacías de relleno: solo lo que el usuario escribió).
        val columnBoxes = mutableMapOf<Int, List<TabColumnBox>>()
        val measureBoxes = mutableListOf<TabMeasureBox>()
        // Figura de la ultima columna rotulada: con `MEASURE` el simbolo de duracion solo se
        // repite cuando **cambia** (o al empezar compas), como el `Repeat` de MuseScore.
        var lastRhythmFigure: RhythmFigure? = null
        // **Izquierda de cada compás** del sistema, precalculada: la necesitan los arcos que cruzan
        // compases (la ligadura de expresión) para saber dónde cae una casilla de otro compás.
        val measureLefts = ArrayList<Float>(widths.size).apply {
            var left = gridLeft
            widths.forEach { add(left); left += it }
        }
        // **Continuación** de una ligadura de expresión que venía del sistema anterior: el arco se
        // reanuda en el borde izquierdo de la rejilla y acaba donde acaba la cadena en este sistema,
        // como el `startOfSystem` de MuseScore (`src/engraving/rendering/score/slurtielayout.cpp`).
        // Antes el arco se **cortaba** en el borde derecho y no volvía a aparecer.
        if (slurFromPreviousSystem) {
            var endMeasure = 0
            var endSlot = 0
            var ranOut = false
            while (true) {
                val slots = measures.getOrNull(endMeasure)?.slots.orEmpty()
                if (endSlot !in slots.indices) { ranOut = true; break }
                if (!slots[endSlot].slurToNext) break
                var nm = endMeasure
                var ns = endSlot + 1
                while (nm <= measures.lastIndex && ns > measures[nm].slots.lastIndex) {
                    nm++
                    ns = 0
                }
                if (nm > measures.lastIndex) { ranOut = true; break }
                endMeasure = nm
                endSlot = ns
            }
            val endColumns = columnWidths.getOrNull(endMeasure).orEmpty()
            val endX = if (!ranOut && endSlot in endColumns.indices) {
                measureLefts.getOrElse(endMeasure) { gridLeft } +
                    (if (endMeasure == 0) headerInset else 0f) + barNote +
                    endColumns.take(endSlot).sum() + endColumns[endSlot] / 2f
            } else {
                measureLefts.getOrElse(endMeasure) { gridLeft } +
                    (widths.getOrNull(endMeasure) ?: 0f)
            }
            val startX = gridLeft
            val slurY = zones.top(TabZone.SYMBOLS) +
                (if (zones.hasSlur) TabStaffZones.SLUR_SPACES * settings.staffSpace else 0f)
            glyphs += TabGlyph(
                kind = TabGlyphKind.SLUR,
                x = (startX + endX) / 2f,
                y = slurY,
                from = Offset2(startX, slurY),
                to = Offset2(endX, slurY)
            )
        }
        var measureLeft = gridLeft
        measures.forEachIndexed { localMeasure, measure ->
            // **Compas de silencio** (sin musica o solo con la rejilla de trabajo): se mide y se
            // pinta como **una** columna —un unico silencio de compas centrado—, pero el modelo
            // conserva la rejilla (ocho corcheas en un `4/4`). Su ancho se reparte **en partes
            // iguales** entre las columnas del modelo para que tocar el tercer pulso de un compas
            // vacio siga llevando el cursor al tercer pulso.
            val restOnly = measure.slots.isEmpty() ||
                TabMeasureMeter.isMeasureRest(measure, tablature.resolution)
            val columns = if (restOnly && measure.slots.isNotEmpty()) {
                val step = columnWidths[localMeasure].sum() / measure.slots.size
                List(measure.slots.size) { step }
            } else {
                columnWidths[localMeasure]
            }
            // **Origen de las columnas** del compás: la cabecera del sistema ocupa el principio del
            // **primer** compás (MuseScore la mete en su `minLeft`), así que sus columnas empiezan
            // después de ella. En los demás compases el origen es la barra + `barNoteDistance`.
            val columnOrigin = measureLeft + (if (localMeasure == 0) headerInset else 0f) +
                timeChangeSpaces[localMeasure] + barNote
            // **Cambio de compás dentro del sistema**: la fracción nueva se rotula al principio del
            // compás que la introduce, en la franja que [timeChangeSpaces] le ha reservado. Es el
            // `TimeSig` que la paleta *Indicaciones de compás* de MuseScore inserta anclado al compás;
            // sin él, el lector no sabría por qué el compás siguiente tiene otras columnas.
            if (timeChangeSpaces[localMeasure] > 0f) {
                // (D-92) La fracción del **cambio de compás** va **dentro** del compás que cambia,
                // a su izquierda, y las columnas empiezan **después** de ella. Antes se centraba en
                // la franja reservada, pero `columnWidths` no descontaba esa franja en la rama
                // normal (solo en la del silencio de compás completo), así que la primera cifra caía
                // **encima** del `8/4` y se leía como un amasijo.
                val changeX = measureLeft + (if (localMeasure == 0) headerInset else 0f) +
                    timeChangeSpaces[localMeasure] / 2f
                val changeCenterY = (contentTop + contentBottom) / 2f
                val changeTime = tablature.timeAt(
                    measureIndices.getOrNull(localMeasure) ?: localMeasure
                )
                texts += TabText(
                    changeTime.beats.toString(),
                    changeX,
                    changeCenterY - settings.stringSpacing * TIME_SIG_CENTER_OFFSET,
                    TabTextKind.TIME_NUMERATOR
                )
                texts += TabText(
                    changeTime.beatType.toString(),
                    changeX,
                    changeCenterY + settings.stringSpacing * TIME_SIG_CENTER_OFFSET,
                    TabTextKind.TIME_DENOMINATOR
                )
            }
            // **Posición en negras** de cada columna dentro del compás: la necesita el agrupamiento de
            // barras de unión ([TabBeamGrouping]) para cortar por pulso y no unir el compás entero.
            val beatOffsets = measure.slots.runningFold(0.0) { acc, slot ->
                acc + TabSpacing.beatsOf(slot.rhythmicFigure, slot.tuplet, tablature.resolution)
            }
            // Número de compás (1-based, global del documento): en MuseScore va **encima** de la
            // rejilla, alineado con **su barra** (no en una franja a la izquierda). Con **anacrusa**
            // el primer compás es el `0` y no se rotula, así que el segundo pasa a ser el `1`
            // (`Measure::measureNumber()` de MuseScore).
            val globalNumber = tablature.measureNumber(
                measureIndices.getOrNull(localMeasure) ?: localMeasure
            )
            // (U-3) Como en MuseScore: el número se pone **solo al principio de cada sistema**, y
            // **solo a partir del segundo** (el primero no se rotula). Si el documento cabe en una
            // sola línea, no hay ningún número. La anacrusa (`0`) nunca se numera.
            val showsNumber = settings.staff.showMeasureNumbers &&
                systemCount > 1 && systemIndex > 0 && localMeasure == 0 && globalNumber > 0
            if (showsNumber) {
                // (C.6) El número va **encima de la barra** que abre el compás, con un pequeño
                // desplazo a la derecha para no pisar la barra, y se garantiza que queda a la
                // **derecha del área de clave y compás** (`gridLeft`): nunca se monta sobre la clave
                // `TAB` ni sobre la fracción, como en MuseScore.
                val numberX = (measureLeft + settings.cellWidth * settings.measureNumberOffset)
                    .coerceAtLeast(gridLeft)
                texts += TabText(
                    globalNumber.toString(),
                    numberX,
                    // (D-55) El número se ancla **respecto de la pauta** (`measureNumberPosAbove`),
                    // no al borde de la franja: así queda a 2,32 espacios sobre la primera cuerda
                    // aunque la franja `TOP` crezca por un tresillo.
                    zones.measureNumberY(),
                    TabTextKind.MEASURE_NUMBER
                )
            }
            // Las marcas del compás van en su franja: **repetición/volta/salto** abajo (pegadas a la
            // rejilla) y **navegación/8va** más arriba.
            addMeasureMarks(
                texts,
                measure,
                measureLeft,
                repeatY,
                markerY,
                widths[localMeasure],
                settings
            )
            // (D-5) La **dinámica** va **DEBAJO** del pentagrama, en su franja propia, y **centrada en
            // la primera columna** del compás: MuseScore la ancla al pulso donde se escribió y el
            // modelo guarda una por compás, así que el primer pulso es el ancla fiel. Centrada en el
            // compás entero caía a media casilla de sitio (medido en el `salmo2.pdf`: el `mp` está a
            // 1,16 espacios de la barra, justo sobre la primera nota).
            addDynamicMark(
                texts,
                measure,
                columnOrigin + settings.cellWidth * 0.5f,
                dynamicY
            )
            // **Regulador** (*hairpin*): la cuña de crescendo/diminuendo, en la **misma franja** que
            // la dinámica y a su derecha. MuseScore la dibuja como dos líneas que se abren o se
            // cierran; el alcance es de compases, así que se recorta al final del sistema.
            measure.hairpin?.let { hairpin ->
                val lastMeasure = (localMeasure + hairpin.measures - 1).coerceAtMost(measures.lastIndex)
                // Si el compás lleva además un matiz escrito, la cuña **arranca después** de él para
                // no pisarlo (MuseScore coloca el `HairPin` a continuación del `Dynamic`).
                val startX = columnOrigin +
                    if (measure.dynamic != null) settings.cellWidth else settings.cellWidth * 0.5f
                val endX = measureLefts.getOrElse(lastMeasure) { measureLeft } + widths[lastMeasure]
                if (endX > startX) {
                    glyphs += TabGlyph(
                        kind = if (hairpin.type == TabHairpinType.CRESCENDO) {
                            TabGlyphKind.CRESCENDO
                        } else {
                            TabGlyphKind.DIMINUENDO
                        },
                        x = (startX + endX) / 2f,
                        y = dynamicY,
                        from = Offset2(startX, dynamicY),
                        to = Offset2(endX, dynamicY)
                    )
                }
            }
            // **Tempo** (T5): se rotula en **su** compás (donde está la indicación), en la franja de
            // texto. (D-8) Va **alineado a la izquierda**, como en MuseScore: centrado en el compás,
            // el rótulo se metía bajo el arco de púa de la primera columna. Y se usa el **rótulo
            // literal** de la fuente cuando existe (`♩ = 80`, `Andante`), porque MuseScore no inventa
            // la indicación italiana.
            //
            // (D-26) **Dónde empieza** el rótulo. MuseScore lo alinea sobre el **compás** cuando el
            // tempo cae en el primer tiempo de un compás que abre sistema (`TLayout::layoutTempoText`,
            // `tlayout.cpp:5766-5775`):
            //
            // ```cpp
            // // tempo text on first chordrest of measure should align over time sig if present,
            // // unless time sig is above staff
            // if (s->rtick().isZero()) {
            //     Segment* p = item->segment()->prev(SegmentType::TimeSig);
            //     if (p && !p->allElementsInvisible()) {
            //         ldata->moveX(-(s->x() - p->x()));
            //         EngravingItem* e = p->element(item->staffIdx() * VOICES);
            //         if (e) ldata->moveX(e->x());
            //     }
            // }
            // ```
            //
            // O sea: el rótulo **retrocede** hasta el borde izquierdo de la **fracción de compás**.
            // Medido del `salmo2.pdf`: la fracción `4/4` ocupa x 352–387 y el `♩` empieza en **351**,
            // un píxel a su izquierda. Antes lo poníamos en el centro de la primera columna (x 460 en
            // nuestro render), 5,3 espacios más a la derecha que MuseScore, y un rótulo largo se
            // metía sobre las cifras. **No** hace falta que el tempo aporte su ancho al compás: en el
            // PDF el `80` acaba en x 487 y la primera cifra está en 434, así que MuseScore **deja**
            // que el rótulo se extienda por encima de las columnas —vive en su propia franja
            // vertical—; lo que hace es empezar más a la izquierda para tener sitio.
            val tempoX = if (localMeasure == 0 && drawsHeader && settings.staff.showTimeSignature) {
                staffLeft + clefSpace
            } else {
                columnOrigin
            }
            measure.tempo?.let { bpm ->
                texts += TabText(
                    measure.tempoText ?: tempoLabel(bpm),
                    tempoX,
                    textY,
                    TabTextKind.TEMPO,
                    align = TabTextAlign.LEFT
                )
            }
            // **Texto libre** (T6): indicación del copista, centrada sobre el compás, en su franja.
            measure.text?.let { freeText ->
                texts += TabText(
                    freeText,
                    measureLeft + widths[localMeasure] / 2f,
                    textY,
                    TabTextKind.TEXT
                )
            }
            measureBoxes += TabMeasureBox(globalNumber - 1, measureLeft, measureLeft + widths[localMeasure])
            columnBoxes[globalNumber - 1] = measure.slots.mapIndexed { s, slot ->
                val left = columnOrigin + columns.take(s).sum()
                TabColumnBox(
                    measureIndex = globalNumber - 1,
                    slotIndex = s,
                    centerX = left + columns[s] / 2f,
                    left = left,
                    width = columns[s],
                    voice = slot.voice
                )
            }
            // **Silencio de compas**: un compas sin musica se dibuja con **un unico**
            // silencio de compas centrado, nunca con una fila de silencios. Cubre los dos
            // casos que son el mismo para el lector: el compas **sin columnas** (importado o
            // vaciado a mano) y el compas que solo tiene la **rejilla de trabajo** del editor
            // (ocho silencios de corchea en un `4/4` que nadie ha escrito). La rejilla hace
            // falta para saber donde cae un clic, pero **no es musica**.
            if (restOnly &&
                settings.staff.noteValues != NoteValueStyle.NONE &&
                settings.staff.showRests
            ) {
                rhythmMarks += TabRhythmMark(
                    figure = RhythmFigure.WHOLE_REST,
                    x = measureLeft + widths[localMeasure] / 2f,
                    y = contentTop + TabRhythmGeometry.restLine(
                        stringCount,
                        isWholeRest = true
                    ) * settings.stringSpacing,
                    measureIndex = globalNumber - 1
                )
            }
            measure.slots.forEachIndexed { s, slot ->
                // **Notas de adorno** (MuseScore, `Chord::graceNotesBefore`): la columna crece
                // **hacia la izquierda** para alojarlas, así que el contenido de la columna se
                // desplaza media anchura extra a la derecha y la cifra queda centrada en la parte de
                // **duración**. Todo lo que cuelga de la columna (cifra, figura, articulaciones,
                // técnicas, arcos) usa este mismo eje, así que se mueve junto.
                val graceExtra = TabGraceGeometry.extraWidth(slot.graces.size, settings.staffSpace)
                val cellCenterX = columnOrigin + columns.take(s).sum() + columns[s] / 2f +
                    graceExtra / 2f
                // (D-45/D-47) `MEASURE` y `SYSTEM` comparten el disparo de maquetación —el compás
                // cambió— y solo se distinguen al pintar: `TDraw::draw` omite el símbolo de `SYSTEM`
                // cuando el acorde anterior está en el **mismo** sistema (`tdraw.cpp:3067-3074`).
                // Aquí el sistema ya está partido, así que «el compás cambió de sistema» es «este
                // compás abre el sistema». Lo mismo necesita la cifra de la nota ligada (D-47).
                val opensMeasure = s == 0
                val opensSystem = opensMeasure && globalNumber - 1 == measureIndices.firstOrNull()
                // **Ligadura de expresión** (`S` de MuseScore): un **único** arco por cadena, no uno
                // por par. Se emite solo en la casilla que **abre** la cadena (la anterior no está
                // ligada) y se extiende hasta la primera casilla **después** del final de la cadena,
                // que es donde MuseScore ancla el extremo derecho del arco.
                if (slot.slurToNext && (s == 0 || !measure.slots[s - 1].slurToNext)) {
                    val startX = cellCenterX
                    var endMeasure = localMeasure
                    var endSlot = s
                    var ranOut = false
                    // Avanzar mientras la casilla actual esté ligada.
                    while (true) {
                        val slots = measures.getOrNull(endMeasure)?.slots.orEmpty()
                        if (endSlot !in slots.indices || !slots[endSlot].slurToNext) break
                        var nm = endMeasure
                        var ns = endSlot + 1
                        while (nm <= measures.lastIndex && ns > measures[nm].slots.lastIndex) {
                            nm++
                            ns = 0
                        }
                        if (nm > measures.lastIndex) {
                            ranOut = true
                            break
                        }
                        endMeasure = nm
                        endSlot = ns
                    }
                    val endColumns = columnWidths.getOrNull(endMeasure).orEmpty()
                    val endX = if (!ranOut && endSlot in endColumns.indices) {
                        measureLefts.getOrElse(endMeasure) { measureLeft } +
                            (if (endMeasure == 0) headerInset else 0f) + barNote +
                            endColumns.take(endSlot).sum() + endColumns[endSlot] / 2f
                    } else {
                        // La cadena llega al final del sistema: el arco se corta en el borde derecho,
                        // como MuseScore, que continúa la ligadura al principio del sistema siguiente.
                        measureLefts.getOrElse(endMeasure) { measureLeft } +
                            (widths.getOrNull(endMeasure) ?: 0f)
                    }
                    // El arco va **por encima de todo** lo que cuelga de la cuerda superior: por
                    // encima de la pila de articulaciones y técnicas, no dentro de ella.
                    // `heightOf(SYMBOLS)` nunca baja de una franja (`coerceAtLeast(1)`), así que
                    // `symbolsY` queda **media franja por debajo** del borde superior de la banda
                    // cuando no hay articulaciones; se toma el más alto de los dos y se sube media
                    // franja para que el arco quede **fuera** de la pila en todos los casos.
                    val slurY = zones.top(TabZone.SYMBOLS) +
                        (if (zones.hasSlur) TabStaffZones.SLUR_SPACES * settings.staffSpace else 0f)
                    glyphs += TabGlyph(
                        kind = TabGlyphKind.SLUR,
                        x = (startX + endX) / 2f,
                        y = slurY,
                        from = Offset2(startX, slurY),
                        to = Offset2(endX, slurY)
                    )
                }
                // Con el silencio de compas ya pintado arriba, las columnas de la rejilla no
                // vuelven a rotular su figura: se dibujaria ocho veces lo que el lector tiene
                // que ver una sola.
                if (!restOnly &&
                    (slot.strings.isNotEmpty() || slot.isHeld || slot.rhythmicFigure != null)
                ) {
                    val figure = slot.rhythmicFigure ?: tablature.resolution.defaultFigure()
                    val hasSound = slot.strings.isNotEmpty() || slot.isHeld
                    // `Show rests`: un silencio explícito (columna sin notas) se dibuja salvo que se
                    // haya desactivado. `Note values`: `None` desactiva todo el pentagrama rítmico.
                    val visible = settings.staff.noteValues != NoteValueStyle.NONE &&
                        (hasSound || settings.staff.showRests)
                    // `Repeat` (`TablatureSymbolRepeat` de MuseScore): el **símbolo** de duración no
                    // se repite en cada columna, solo al cambiar de figura y, según el ajuste, al
                    // abrir compás o sistema. Aplica a `Note symbols`; con `Stems and beams` la
                    // duración ya va en las plicas y barras, así que se dibujan **todas** las notas
                    // (como un pentagrama).
                    val symbolsAbove = settings.staff.noteValues == NoteValueStyle.SYMBOLS_ABOVE
                    val repeats = !symbolsAbove || when (settings.staff.rhythmRepeat) {
                        RhythmRepeat.ALWAYS -> true
                        RhythmRepeat.MEASURE -> opensMeasure || figure != lastRhythmFigure
                        RhythmRepeat.SYSTEM -> opensSystem || figure != lastRhythmFigure
                        RhythmRepeat.NEVER -> figure != lastRhythmFigure
                    }
                    if (visible && repeats) {
                        // (U-5) La figura va **DEBAJO** de las cuerdas, en su franja. Nunca encima.
                        // (2026-10-14) Los **silencios** se anclan a la **línea** que les toca según
                        // MuseScore ([TabRhythmGeometry.restLine]): la blanca y las menores apoyan su
                        // origen en la línea «natural» del pentagrama y la redonda pende de la de
                        // arriba. Antes se centraban en el pentagrama, que en 6 cuerdas cae **entre**
                        // dos líneas. Las **notas** sí cuelgan de la cuerda inferior (la plica nace ahí).
                        val isRest = figure.isRest
                        rhythmMarks += TabRhythmMark(
                            figure = figure,
                            x = cellCenterX,
                            y = if (isRest) {
                                contentTop + TabRhythmGeometry.restLine(
                                    stringCount,
                                    isWholeRest = figure == RhythmFigure.WHOLE_REST
                                ) * settings.stringSpacing
                            } else {
                                figureY
                            },
                            tuplet = slot.tuplet,
                            measureIndex = globalNumber - 1,
                            beatOffset = beatOffsets[s]
                        )
                    }
                    if (visible) lastRhythmFigure = figure
                }
                // **Notas de adorno** (*grace notes*): cifras **pequeñas** a la izquierda de la
                // cifra principal, en su cuerda. MuseScore las cuelga del acorde principal
                // (`Chord::graceNotesBefore`) y las separa `minNoteDistance · 0,7` = 0,245 espacios;
                // el **último** de la lista es el que queda pegado a la nota principal, así que se
                // dibuja de derecha a izquierda. En tablatura **no** llevan barra de acciaccatura:
                // la tablatura es *stemless* y MuseScore no dibuja el `StemSlash` sin asta.
                slot.graces.forEachIndexed { g, grace ->
                    val gi = strings.indexOfFirst { it.first == grace.stringNumber }
                    if (gi < 0) return@forEachIndexed
                    val gy = contentTop + gi * settings.stringSpacing
                    texts += TabText(
                        text = fretMarkText(grace.fret, settings.staff.fretMarks),
                        x = cellCenterX - TabGraceGeometry.offsetSpaces(g) * settings.staffSpace,
                        y = if (settings.staff.marksPlacement == MarksPlacement.ABOVE_LINES) {
                            gy - settings.stringSpacing * 0.42f
                        } else {
                            gy
                        },
                        kind = TabTextKind.GRACE,
                        voice = slot.voice
                    )
                }
                if (slot.isHeld && slot.strings.isEmpty()) {
                    strings.forEachIndexed { i, _ ->
                        texts += TabText(
                            "~",
                            cellCenterX,
                            contentTop + i * settings.stringSpacing,
                            TabTextKind.HELD,
                            voice = slot.voice
                        )
                    }
                } else {
                    slot.strings.forEach { pluck ->
                        val i = strings.indexOfFirst { it.first == pluck.stringNumber }
                        if (i < 0) return@forEach
                        val y = contentTop + i * settings.stringSpacing
                        // Cifra **encima** de la línea (`Above lines`) o centrada en ella (`On lines`).
                        val markY = if (settings.staff.marksPlacement == MarksPlacement.ABOVE_LINES) {
                            y - settings.stringSpacing * 0.42f
                        } else {
                            y
                        }
                        // (D-47) **Cifra de una nota ligada**: `Note::shouldHideFret()`
                        // (`note.cpp:1527-1556`). Con `TIE_AND_FRET` (el defecto) se pinta siempre;
                        // con `TIE` solo al abrir compás o sistema y **entre paréntesis**; con `NONE`
                        // no se pinta nunca. El arco lo decide `shouldDrawTie`.
                        val tiedFrom = TabTieKey(globalNumber - 1, s, pluck.stringNumber) in
                            tiedFromPrevious
                        val hideFret = TabTiedFret.shouldHideFret(
                            tiedFromPrevious = tiedFrom,
                            atMeasureStart = s == 0,
                            opensSystem = opensSystem,
                            showTiedFret = settings.staff.showTiedFret,
                            parenthesize = settings.staff.parenthesizeTiedFret
                        )
                        val parenthesizeFret = TabTiedFret.shouldParenthesize(
                            tiedFromPrevious = tiedFrom,
                            atMeasureStart = s == 0,
                            opensSystem = opensSystem,
                            showTiedFret = settings.staff.showTiedFret,
                            parenthesize = settings.staff.parenthesizeTiedFret
                        )
                        if (!hideFret) {
                            val mark = fretMarkText(pluck, settings.staff.fretMarks)
                            texts += TabText(
                                if (parenthesizeFret) "($mark)" else mark,
                                cellCenterX,
                                markY,
                                TabTextKind.FRET,
                                techniques = pluck.techniques,
                                voice = slot.voice
                            )
                        }
                        // (U-1) Las **articulaciones** se apilan **en VERTICAL** sobre la cuerda, una
                        // por franja (corrección del usuario). La pila arranca en `symbolsY` y sube.
                        val articulationTiers = addArticulationMarks(
                            texts,
                            pluck,
                            cellCenterX,
                            symbolsY,
                            tierPx = tierPx
                        )
                        // Un **símbolo por cada técnica** de la nota (se combinan: ligado + slide…).
                        // Se **apilan en VERTICAL** encima de las articulaciones, uno por franja, con
                        // el eje `x` **siempre** el de la cifra: así el ancho de la columna depende
                        // **solo de la duración** y la tablatura **no se mueve** al añadir un símbolo
                        // (corrección del usuario, 2026-10-14). Antes se repartían en fila y ensanchaban
                        // la columna.
                        val previousX = previousNoteX(
                            measures,
                            columnWidths,
                            localMeasure,
                            s,
                            pluck.stringNumber,
                            columnOrigin - barNote,
                            barNote,
                            barGap
                        )
                        // (D-89) **Dos grupos de símbolos**, como en MuseScore:
                        //  * los que van en la **pila superior** (`Articulation`/`Ornament` de
                        //    MuseScore): articulaciones y púa;
                        //  * los que van **pegados a la cifra** (bend, slide, trino, vibrato), que en
                        //    MuseScore son `SLine`/`Glissando` anclados a la **nota** y no a una franja
                        //    del sistema. Ver [goesNextToFret].
                        val (fretAdjacent, stacked) = pluck.techniques
                            .mapNotNull { technique -> technique.vectorialGlyph()?.let { technique to it } }
                            .filter { it.second != TabGlyphKind.SLUR }
                            .partition { it.first.goesNextToFret }
                        // (D-89) Los que van junto a la cifra **no se apilan con las articulaciones**:
                        // se dibujan uno detrás de otro a la derecha del número, que es como los sitúa
                        // MuseScore (`guitarbendlayout.cpp` usa `startNote->width() + 0.25·spatium`).
                        fretAdjacent.forEachIndexed { index, (_, kind) ->
                            val glyphX = cellCenterX + settings.cellWidth * (0.30f + 0.22f * index)
                            glyphs += TabGlyph(
                                kind = kind,
                                x = glyphX,
                                y = y,
                                from = Offset2(cellCenterX, y),
                                to = Offset2(glyphX, y),
                                label = pluck.glyphLabel(kind),
                                span = pluck.glyphSpan(kind),
                                strokes = pluck.strokes
                            )
                        }
                        val aboveKinds = stacked
                            .map { it.second }
                            .sortedBy { TabSymbolPlacer.aboveOrderIndex(it) }
                        aboveKinds.forEachIndexed { index, kind ->
                            val glyphY = symbolsY - (articulationTiers + index) * tierPx
                            glyphs += TabGlyph(
                                kind = kind,
                                x = cellCenterX,
                                y = glyphY,
                                from = Offset2(previousX ?: (cellCenterX - settings.cellWidth * 0.5f), glyphY),
                                to = Offset2(cellCenterX, glyphY),
                                label = pluck.glyphLabel(kind),
                                span = pluck.glyphSpan(kind),
                                strokes = pluck.strokes
                            )
                        }
                        // La **ligadura** (`SLUR`) se une a la nota anterior: un arco largo, aparte.
                        // **No** entra en la pila: va pegado a la cuerda, sin desplazar nada.
                        if (pluck.has(TabTechnique.HAMMER) || pluck.has(TabTechnique.PULL)) {
                            val fromX = previousX ?: (cellCenterX - settings.cellWidth * 0.5f)
                            glyphs += TabGlyph(
                                kind = TabGlyphKind.SLUR,
                                x = cellCenterX,
                                y = symbolsY,
                                from = Offset2(fromX, symbolsY),
                                to = Offset2(cellCenterX, symbolsY)
                            )
                        }
                        // **Ligadura de prolongación** (B-10): arco de esta cifra a la **siguiente** de
                        // la misma cuerda, pegado a la línea de la cuerda. Si la siguiente nota cae en
                        // otro sistema, el arco se corta en el borde derecho (como MuseScore, que
                        // continúa la ligadura al principio del sistema siguiente).
                        if (pluck.tiedToNext && TabTiedFret.shouldDrawTie(settings.staff.showTiedFret)) {
                            val nextX = nextNoteX(
                                measures,
                                columnWidths,
                                localMeasure,
                                s,
                                pluck.stringNumber,
                                columnOrigin - barNote,
                                barNote,
                                barGap
                            )
                            val toX = nextX ?: (
                                columnOrigin - barNote +
                                    (columnWidths.getOrNull(localMeasure)?.sum() ?: 0f) + barGap
                                )
                            glyphs += TabGlyph(
                                kind = TabGlyphKind.TIE,
                                x = (cellCenterX + toX) / 2f,
                                y = y,
                                from = Offset2(cellCenterX, y),
                                to = Offset2(toX, y)
                            )
                        }
                        // (U-6) La **digitación** va **DEBAJO de la tablatura**, en su franja, nunca
                        // dentro de la cifra. La mano **izquierda** (`1-4`) a la izquierda del centro y
                        // la **derecha** (`p i m a`) a la derecha, para que las dos no se pisen.
                        pluck.frettingFinger?.let { finger ->
                            glyphs += TabGlyph(
                                kind = TabGlyphKind.FRETTING_FINGER,
                                x = cellCenterX - settings.cellWidth * 0.20f,
                                y = fingerY,
                                label = pluck.glyphLabel(TabGlyphKind.FRETTING_FINGER)
                            )
                        }
                        pluck.pickingFinger?.let { finger ->
                            glyphs += TabGlyph(
                                kind = TabGlyphKind.PICKING_FINGER,
                                x = cellCenterX + settings.cellWidth * 0.20f,
                                y = fingerY,
                                label = pluck.glyphLabel(TabGlyphKind.PICKING_FINGER)
                            )
                        }
                    }
                }
            }
            measureLeft += widths[localMeasure]
        }

        // ══ **Voltas** (D-37) ═════════════════════════════════════════════════════════════════════
        // El corchete se dibuja **una vez por tramo** (compases consecutivos con la misma casilla),
        // no una vez por compás: es el `Volta` de MuseScore, un *spanner* `TextLineBase` que abraza
        // el rango de compases de la casilla.
        addVoltaBrackets(
            lines = lines,
            texts = texts,
            spans = voltaSpansOf(tablature.measures, measureIndices),
            measureLefts = measureLefts,
            widths = widths,
            zones = zones,
            settings = settings
        )

        // El alto sale de las **franjas**: incluye las de arriba (marcas, texto, dinámicas) y las de
        // abajo (figuras y digitación), sin números a ojo.
        return TablatureDrawing(
            lines = lines,
            texts = texts,
            width = drawingRight + settings.pageMargin,
            height = zones.height,
            glyphs = glyphs,
            rhythmMarks = rhythmMarks,
            beamGroups = TabBeamGrouping.groupsOf(
                rhythmMarks,
                tablature.time,
                timeAt = { tablature.timeAt(it) }
            ),
            tuplets = TabRhythmGeometry.tupletBracketsOf(
                marks = rhythmMarks,
                unit = settings.stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES,
                staffTop = contentTop,
                // Los grupos de barra hacen falta para la regla `AUTO_BRACKET`: un grupo irregular
                // que ocupa exactamente una barra de unión se rotula **sin** corchete.
                beamGroups = TabBeamGrouping.groupsOf(
                    rhythmMarks,
                    tablature.time,
                    timeAt = { tablature.timeAt(it) }
                )
            ),
            columns = columnBoxes,
            measureBounds = measureBoxes,
            noteValues = settings.staff.noteValues,
            stemsDown = settings.staff.stemsDown,
            minimStyle = settings.staff.minimStyle,
            gridTop = contentTop,
            gridBottom = contentBottom,
            gridLeft = gridLeft,
            staffLeft = staffLeft,
            gridRight = contentRight,
            stringSpacing = settings.stringSpacing,
            staffSpace = settings.staffSpace
        )
    }

    /**
     * **Líneas interrumpidas** (`Broken` en MuseScore, `<linesThrough>0</linesThrough>`): en vez de
     * una línea de cuerda de lado a lado, devuelve los tramos que quedan **entre** las cifras de
     * [measures], dejando un hueco centrado en cada columna **que tenga una cifra en esa cuerda**.
     *
     * (2026-10-15) Dos correcciones medidas del `salmo2.pdf`:
     *  - El hueco es de **0,8 separaciones de cuerda** (25 px con las cuerdas a 31 px), **independiente
     *    del ancho del dígito**: medido en «1», «0» y «2» da 25, 25 y 24 px. Antes era
     *    `cellWidth * 0,62` (≈ 31 px con la celda por defecto), que no escalaba con el pentagrama.
     *  - Solo se rompe donde **esa** cuerda lleva cifra. Antes se rompía en **todas** las columnas,
     *    incluidas las de silencio, y la línea salía troceada sin motivo.
     */
    private fun brokenSegments(
        lineLeft: Float,
        columnsLeft: Float,
        gridRight: Float,
        y: Float,
        stringNumber: Int,
        measures: List<TabMeasure>,
        columnWidths: List<List<Float>>,
        barNote: Float,
        barGap: Float,
        settings: TabLayoutSettings,
        headerInset: Float = 0f
    ): List<TabLine> {
        val gap = settings.stringSpacing * BROKEN_LINE_GAP_SPACINGS
        val segments = mutableListOf<TabLine>()
        var cursor = lineLeft
        // **Origen de las columnas**: `gridLeft`, no `staffLeft`. Las cuerdas arrancan en el borde del
        // pentagrama (debajo de la clave y el compás), pero la **primera columna** empieza tras la
        // cabecera. Usar `staffLeft` aquí desplazaba **todos** los huecos `clefSpace + timeSpace`
        // (≈ 90 px) a la izquierda, así que la línea se veía **detrás** de las cifras.
        // El **aire de las barras** (`barNote`/`barGap`) se suma igual que en el dibujo de las cifras:
        // si aquí no se sumara, cada hueco caería `barNote` px a la izquierda de su cifra.
        var measureLeft = columnsLeft
        measures.forEachIndexed { m, measure ->
            val columns = columnWidths.getOrNull(m).orEmpty()
            measure.slots.forEachIndexed { s, slot ->
                val hasMark = slot.strings.any { it.stringNumber == stringNumber }
                if (hasMark) {
                    val inset = if (m == 0) headerInset else 0f
                    val center = measureLeft + inset + barNote + columns.take(s).sum() +
                        columns.getOrElse(s) { 0f } / 2f
                    val gapLeft = (center - gap / 2f).coerceAtLeast(cursor)
                    if (gapLeft > cursor) segments += TabLine(cursor, y, gapLeft, y)
                    cursor = center + gap / 2f
                }
            }
            measureLeft += columns.sum() + barGap + (if (m == 0) headerInset else 0f)
        }
        if (cursor < gridRight) segments += TabLine(cursor, y, gridRight, y)
        return segments
    }

    /**
     * X de la **nota anterior** de la misma cuerda, para el arco de ligadura: mira primero las
     * columnas previas del mismo compás y, si no hay, los compases anteriores del sistema. `null` si
     * esa cuerda no tenía nota antes (el pintor abre el arco desde media columna a la izquierda).
     */
    private fun previousNoteX(
        measures: List<TabMeasure>,
        columnWidths: List<List<Float>>,
        localMeasure: Int,
        slotIndex: Int,
        stringNumber: Int,
        measureLeft: Float,
        barNote: Float,
        barGap: Float
    ): Float? {
        fun slotCenter(measure: Int, slot: Int, left: Float): Float {
            val columns = columnWidths.getOrNull(measure).orEmpty()
            return left + barNote + columns.take(slot).sum() + (columns.getOrNull(slot) ?: 0f) / 2f
        }
        for (s in slotIndex - 1 downTo 0) {
            val hasPluck = measures.getOrNull(localMeasure)?.slots?.getOrNull(s)?.strings
                ?.any { it.stringNumber == stringNumber } == true
            if (hasPluck) return slotCenter(localMeasure, s, measureLeft)
        }
        var left = measureLeft
        for (m in localMeasure - 1 downTo 0) {
            left -= (columnWidths.getOrNull(m)?.sum() ?: 0f) + barGap
            val slots = measures.getOrNull(m)?.slots ?: continue
            val last = (slots.lastIndex downTo 0).firstOrNull { s ->
                slots[s].strings.any { it.stringNumber == stringNumber }
            } ?: -1
            if (last >= 0) return slotCenter(m, last, left)
        }
        return null
    }

    /**
     * `x` del **centro** de la siguiente nota de [stringNumber] **después** de `[localMeasure, slotIndex]`,
     * o `null` si no hay ninguna en este sistema. Es el extremo derecho de la ligadura de prolongación
     * (B-10).
     */
    private fun nextNoteX(
        measures: List<TabMeasure>,
        columnWidths: List<List<Float>>,
        localMeasure: Int,
        slotIndex: Int,
        stringNumber: Int,
        measureLeft: Float,
        barNote: Float,
        barGap: Float
    ): Float? {
        fun slotCenter(measure: Int, slot: Int, left: Float): Float {
            val columns = columnWidths.getOrNull(measure).orEmpty()
            return left + barNote + columns.take(slot).sum() + (columns.getOrNull(slot) ?: 0f) / 2f
        }
        val slots = measures.getOrNull(localMeasure)?.slots.orEmpty()
        for (s in slotIndex + 1..slots.lastIndex) {
            if (slots[s].strings.any { it.stringNumber == stringNumber }) {
                return slotCenter(localMeasure, s, measureLeft)
            }
        }
        var left = measureLeft + (columnWidths.getOrNull(localMeasure)?.sum() ?: 0f) + barGap
        for (m in localMeasure + 1..measures.lastIndex) {
            val next = measures.getOrNull(m)?.slots.orEmpty()
            val first = next.indexOfFirst { slot -> slot.strings.any { it.stringNumber == stringNumber } }
            if (first >= 0) return slotCenter(m, first, left)
            left += (columnWidths.getOrNull(m)?.sum() ?: 0f) + barGap
        }
        return null
    }

    /**
     * Añade las marcas del manuscrito (RF-127) de un compás: barras de repetición (`|:` `:|`) en los
     * extremos y la **volta** (`1.` `2.`…) sobre el centro. Se dibujan por encima de la rejilla.
     */
}

/**
 * **Tramos de volta** de un sistema (D-37).
 *
 * Agrupa los compases **consecutivos** que comparten la misma casilla (`ending`). [TabVoltaSpan.opens]
 * es `true` cuando el compás anterior del documento **no** lleva la misma casilla (así una volta que
 * cruza un salto de sistema se dibuja abierta en el sistema siguiente, como el *spanner* partido de
 * MuseScore) y [TabVoltaSpan.closes] cuando el siguiente tampoco la lleva.
 */
internal fun voltaSpansOf(
    all: List<TabMeasure>,
    measureIndices: List<Int>
): List<TabVoltaSpan> {
    val spans = mutableListOf<TabVoltaSpan>()
    var i = 0
    while (i < measureIndices.size) {
        val global = measureIndices[i]
        val ending = all.getOrNull(global)?.ending
        if (ending == null) {
            i++
            continue
        }
        var j = i
        while (j + 1 < measureIndices.size &&
            measureIndices[j + 1] == measureIndices[j] + 1 &&
            all.getOrNull(measureIndices[j + 1])?.ending == ending
        ) {
            j++
        }
        spans += TabVoltaSpan(
            start = i,
            end = j,
            // (D-43) El rótulo es el **texto propio** de la casilla si alguno de sus compases lo trae
            // (el `beginText` del `Volta` de MuseScore, que vive en el *spanner* y no en el compás);
            // si no, el derivado de las pasadas (`1.`, `1, 2.`). Se busca en todo el tramo para que
            // escribirlo en cualquier compás de la casilla surta efecto.
            label = (i..j)
                .mapNotNull { all.getOrNull(measureIndices[it])?.endingText?.takeIf { t -> t.isNotBlank() } }
                .firstOrNull()
                ?: all[global].endingLabel
                ?: "$ending.",
            opens = all.getOrNull(global - 1)?.ending != ending,
            closes = all.getOrNull(measureIndices[j] + 1)?.ending != ending
        )
        i = j + 1
    }
    return spans
}

/**
 * Dibuja los **corchetes de volta** (D-37) en la franja [TabZone.VOLTA].
 *
 * Reproduce el `Volta` de MuseScore: una línea horizontal de `voltaLineWidth` (0,11 espacios, que en
 * píxeles es el trazo de 1 px de las líneas no-barra) a la **línea base** del rótulo, un **gancho**
 * descendente de `voltaHook` (2,2 espacios) al principio de la casilla y el rótulo (`1.`, `1, 2.`)
 * **una sola vez**, al principio del tramo. Si la casilla continúa en el sistema siguiente, el
 * corchete se corta en el borde derecho y el rótulo **no** se repite (MuseScore usa `continueText`,
 * que por defecto está vacío).
 */
private fun addVoltaBrackets(
    lines: MutableList<TabLine>,
    texts: MutableList<TabText>,
    spans: List<TabVoltaSpan>,
    measureLefts: List<Float>,
    widths: List<Float>,
    zones: TabStaffZones,
    settings: TabLayoutSettings
) {
    if (spans.isEmpty()) return
    val staffSpace = settings.staffSpace
    // La línea del corchete **es** la línea base del rótulo (`alignBaseLine` de MuseScore): el
    // ancla de la franja ya apunta ahí, así que el texto se pinta con anclaje por línea base.
    val lineY = zones.anchor(TabZone.VOLTA)
    val hook = TabStaffZones.VOLTA_HOOK_SPACES * staffSpace
    spans.forEach { span ->
        val left = measureLefts.getOrElse(span.start) { 0f }
        val right = measureLefts.getOrElse(span.end) { left } + widths.getOrElse(span.end) { 0f }
        lines += TabLine(left, lineY, right, lineY)
        if (span.opens) {
            lines += TabLine(left, lineY, left, lineY + hook)
            texts += TabText(
                span.label,
                // El rótulo **nace** a `voltaOffset.x` (0,6 espacios) del gancho y crece hacia la
                // derecha: en MuseScore el texto de la volta es `beginText` de un `TextLineBase` y
                // se alinea a la **izquierda** del gancho, no centrado en el compás. Centrarlo lo
                // montaba sobre la línea vertical del gancho.
                left + TabStaffZones.VOLTA_TEXT_OFFSET_SPACES * staffSpace,
                lineY,
                TabTextKind.VOLTA,
                align = TabTextAlign.LEFT
            )
        }
    }
}

/**
 * Añade las marcas del manuscrito (RF-127) de un compás: barras de repetición (`|:` `:|`) en los
 * extremos, la **volta** (`1.` `2.`…), el **signo de navegación** (Segno/Coda/Fine/To Coda) y el
 * **salto** (D.C./D.S.). Se dibujan por encima de la rejilla en dos franjas: [lowY] para las marcas
 * del compás y [navY] para la navegación/8va.
 */
private fun addMeasureMarks(
    texts: MutableList<TabText>,
    measure: TabMeasure,
    measureLeft: Float,
    lowY: Float,
    navY: Float,
    measureWidth: Float,
    settings: TabLayoutSettings
) {
    val y = lowY
    // (C.5) Las marcas de la franja **baja** (repetición, cifra de veces, volta y salto) pueden
    // coincidir en el mismo compás y antes se pisaban (`:| x2` con la volta `1.`, o el `D.C.` encima
    // del cierre). Se recogen **todas** y se reparten con el resolvedor de colisiones, que nunca
    // solapa dos textos. El rótulo va anclado a su **centro**.
    val lowMarks = mutableListOf<Pair<Float, String>>()
    // (D-65) El `|:` / `:|` **ya no se rotula aquí**: es la propia barra la que lleva los dos puntos
    // (ver `TabLine.repeat`). Solo queda el **número de veces** (`x2`), que en MuseScore es un texto
    // pequeño junto a la barra de cierre y no tiene sitio en el trazo.
    measure.repeat?.let { repeat ->
        if (repeat == TabRepeatMark.END || repeat == TabRepeatMark.BOTH) {
            lowMarks += (measureLeft + measureWidth + settings.cellWidth * 0.55f) to
                "x${measure.repeatCount.coerceIn(2, 8)}"
        }
    }
    // (D-37) La **volta** ya **no** se rotula aquí: el corchete y su rótulo se dibujan **una sola vez
    // por tramo** en [drawSystem] (`addVoltaBrackets`), no una vez por compás. Antes una casilla de
    // dos compases salía como `1.` `1.` y sin corchete.
    // **Salto** (D.C. / D.S. y variantes) al **final** del compás, donde se lee.
    measure.jump?.let { jump ->
        lowMarks += (measureLeft + measureWidth - settings.cellWidth * 0.30f) to jump.label
    }
    val solvedLow = TabMarkCollisions.resolve(
        lowMarks,
        cellWidth = settings.cellWidth,
        leftBound = measureLeft,
        rightBound = measureLeft + measureWidth
    )
    lowMarks.forEachIndexed { index, (_, text) ->
        texts += TabText(text, solvedLow[index], y, TabTextKind.MARK)
    }

    // **Signo de navegación** (Segno / Coda / Fine / To Coda) anclado al inicio del compás, en la
    // franja de arriba.
    measure.navigation?.let { navigation ->
        texts += TabText(
            navigation.display,
            measureLeft + settings.cellWidth * 0.55f,
            navY,
            TabTextKind.MARK
        )
    }
    // **Cambio de octava** (T4): rótulo al inicio del compás donde empieza el pasaje, en la franja
    // de navegación (la línea discontinua queda para una sub-tarea). Se desplaza al centro-derecha
    // del compás para no pisar el signo de navegación, que va a la izquierda.
    measure.octaveShift?.let { shift ->
        texts += TabText(
            shift.display,
            measureLeft + measureWidth * 0.62f,
            navY,
            TabTextKind.OCTAVE_SHIFT
        )
    }
}

/**
 * Añade la **dinámica** (matiz/regulador) de un compás: se escribe **debajo** de la cuerda inferior,
 * centrada en el compás, como en una partitura (T2).
 */
private fun addDynamicMark(
    texts: MutableList<TabText>,
    measure: TabMeasure,
    centerX: Float,
    y: Float
) {
    measure.dynamic?.let { dynamic ->
        texts += TabText(dynamic.glyph, centerX, y, TabTextKind.DYNAMIC)
    }
}

/**
 * **Articulaciones** de una nota (T3): se **apilan en VERTICAL** sobre la cifra, una por franja (como
 * MuseScore, corrección del usuario 2026-10-12). Antes se repartían **en fila** a los lados de la
 * cifra, que es lo que hacía que salieran «distintas articulaciones en la misma nota» amontonadas.
 *
 * El **armónico** no entra en la pila: se pinta **en** la cifra (diamante), así que se omite aquí.
 *
 * @return el nº de **franjas** que ha ocupado la pila (arco de púa incluido). Los glifos de técnica se
 *   apilan **encima** de ellas, así que necesitan saber dónde acaba.
 */
private fun addArticulationMarks(
    texts: MutableList<TabText>,
    pluck: TabPluck,
    centerX: Float,
    /** Franja de las **articulaciones** (borde inferior de la pila, sobre la cuerda). */
    symbolsY: Float,
    /** Altura de la pila: cada articulación sube [tierPx]. */
    tierPx: Float
): Int {
    // (D-6, medido del `salmo2.pdf`) **Todos** los símbolos que van sobre la cuerda forman **una sola
    // pila**, de abajo arriba: primero el **arco de púa** (la *técnica* `PICK_*`, que en MuseScore es el
    // `𝄢`/`V` pegado a la cuerda), después las **articulaciones** (`>` acento, `^` marcato, tenuto…).
    // El `.mscz` pone `stringsDownBow`+acento+*marcato* en la **misma** nota, así que comparten pila.
    val pick = when {
        TabTechnique.PICK_DOWN in pluck.techniques -> TabArticulation.DOWN_BOW.display
        TabTechnique.PICK_UP in pluck.techniques -> TabArticulation.UP_BOW.display
        else -> null
    }
    var tier = 0
    if (pick != null) {
        texts += TabText(pick, centerX, symbolsY - tier * tierPx, TabTextKind.ARTICULATION)
        tier++
    }
    // Las articulaciones que **se apilan** suben una franja por símbolo, **encima** del arco de púa.
    val stacked = TabArticulationStack.stack(pluck.articulations.filter { it.stacks }.toSet())
    stacked.forEach { item ->
        texts += TabText(
            item.articulation.display,
            centerX,
            symbolsY - (tier + item.tier) * tierPx,
            TabTextKind.ARTICULATION
        )
    }
    return tier + stacked.size
}

/** Medidas del dibujo (en px, ya resueltas por la plataforma). */
data class TabLayoutSettings(
    val cellWidth: Float = 22f,
    val stringSpacing: Float = 20f,
    val labelWidth: Float = 26f,
    val padding: Float = 8f,
    /**
     * **Margen de página** horizontal (px), a izquierda y a derecha del sistema (D-13).
     *
     * MuseScore no dibuja la música de borde a borde: la hoja tiene un **ancho imprimible**
     * (`styleDef(pagePrintableWidth, 7.0567 in)` frente a `styleDef(pageWidth, 8.2677 in)` en
     * `src/engraving/style/styledef.cpp`), es decir deja **7,1 %** de margen a cada lado. Medido del
     * `salmo2.pdf` (2480 px de ancho): las cuerdas arrancan en x = 177 px y acaban en x = 2303, o sea
     * **7,1 %** por la izquierda y **7,3 %** por la derecha — simétrico.
     *
     * Es **independiente** de [padding], que es el aire **vertical** de la primera franja del sistema:
     * mezclarlos metía 180 px de aire encima de cada pentagrama. Por defecto vale lo mismo que
     * [padding] para no cambiar el motor de PDF, que ya paginaba bien.
     */
    val pageMargin: Float = padding,
    /**
     * **Separación vertical entre sistemas** (filas de compases), en px (D-15).
     *
     * MuseScore no la mide a ojo: `styleDef(minSystemDistance, 8.5_sp)` y
     * `styleDef(maxSystemDistance, 15.0_sp)` (`src/engraving/style/styledef.cpp`, líneas 58 y 59)
     * fijan el **mínimo** y el **máximo** entre los que el paginador reparte el aire sobrante de la
     * página. Aquí se usa el **mínimo** ([MIN_SYSTEM_DISTANCE_SPACES]) porque el lienzo del editor es
     * un scroll continuo, no una página: no hay aire sobrante que repartir.
     *
     * Antes valía `14f` **fijos**, que con un espacio de pentagrama de 13,33 px eran **1,05 espacios**
     * — ocho veces menos que MuseScore, y de ahí que los sistemas salieran pegados unos a otros.
     * Ahora se deriva del espacio de pentagrama, como el resto de distancias.
     */
    val systemGap: Float = stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES *
        SYSTEM_GAP_BETWEEN_INK_SPACES,
    /**
     * (C.6) Desplazamiento del **número de compás** respecto a la barra que abre el compás, en
     * fracciones del ancho de columna. Pequeño, para que el número quede **sobre la barra** sin
     * pisarla.
     */
    val measureNumberOffset: Float = 0.30f,
    /**
     * **Multiplicador del tamaño de la cifra** sobre el que le da la rejilla (D-52).
     *
     * El visor deja al usuario agrandar el número de traste para leer de lejos en el atril sin
     * agrandar la rejilla (`TablatureCanvas.numberScale`, de 0,7 a 2,0). Hasta D-52 ese factor solo
     * llegaba al **pintor** (`TabCanvasTextStyles.fret`), no a la **maquetación**: con `2,0` la cifra
     * se dibujaba al doble pero la columna seguía reservando el ancho de la cifra normal, así que los
     * números **se pisaban**. Aquí entra en el ancho mínimo de la columna, que es lo que hace
     * MuseScore: la `Shape` de un `TextBase` se mide con la fuente **ya escalada**.
     *
     * `1f` = el tamaño de la rejilla (lo que hace el PDF y el editor).
     */
    val fretNumberScale: Float = 1f,
    /**
     * **Aspecto del pentagrama** (los ajustes de MuseScore). Editor y visor reciben los **mismos**:
     * de aquí sale la única verdad de cómo se ve la tablatura.
     */
    val staff: TabStaffOptions = TabStaffOptions(),
    /**
     * (D-54) **Espacio de pentagrama fijo**, cuando la separación de cuerdas ya **no** lo determina.
     *
     * Lo pone [withFretNumberSpacing] al agrandar la cifra: las cuerdas se abren para que el número
     * quepa, pero los grosores, las plicas, las barras y los huecos del grabado **no** crecen con
     * ellas. `null` (lo normal) deja que el espacio salga de la separación de cuerdas.
     */
    val staffSpaceOverride: Float? = null
) {
    /**
     * **Un espacio de pentagrama** en px: la unidad de la que salen todas las distancias de MuseScore
     * ([TablatureSystemsLayout.BAR_NOTE_SPACES], [TablatureSystemsLayout.NOTE_BAR_SPACES],
     * [TabStaffZones.TEXT_ANCHOR_SPACES]…). Es la separación de cuerdas dividida por
     * [TabRhythmGeometry.STRING_SPACING_SPACES], porque en tablatura las cuerdas van a **1,5 espacios**.
     *
     * (D-54) Si [staffSpaceOverride] está puesto, manda él: es el caso del **zoom del número**, donde
     * las cuerdas se abren sin que el resto del grabado engorde.
     */
    val staffSpace: Float
        get() = staffSpaceOverride ?: (stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES)

    /**
     * (D-54) **Abre las cuerdas** lo justo para que la cifra agrandada no se pise, dejando el resto
     * del grabado intacto.
     *
     * Devuelve `this` sin tocar nada si el zoom no pasa del **171 %** (el punto en que la tinta de la
     * cifra agotaría el aire entre cuerdas), así que el grabado normal no se mueve ni un píxel.
     */
    fun withFretNumberSpacing(): TabLayoutSettings {
        val base = staffSpace
        val spacing = TabSpacing.stringSpacingFor(stringSpacing, base, fretNumberScale)
        if (spacing == stringSpacing) return this
        return copy(stringSpacing = spacing, staffSpaceOverride = base)
    }

    companion object {
        /**
         * **Margen de página** de MuseScore, en fracción del ancho de la hoja (D-13).
         *
         * Sale de `styleDef(pageWidth, 8.2677 in)` y `styleDef(pagePrintableWidth, 7.0567 in)`
         * (`src/engraving/style/styledef.cpp`): `(8,2677 − 7,0567) / 2 / 8,2677 = 0,0732`. Medido del
         * `salmo2.pdf` da **0,071** por la izquierda y **0,073** por la derecha; se toma el menor para
         * que nada se salga.
         */
        const val PAGE_MARGIN_RATIO: Float = 0.071f

        /**
         * **Separación mínima entre sistemas**, en espacios de pentagrama (D-15).
         *
         * `styleDef(minSystemDistance, 8.5_sp)` en `src/engraving/style/styledef.cpp` (línea 58). El
         * máximo, `styleDef(maxSystemDistance, 15.0_sp)` (línea 59), es hasta donde MuseScore **estira**
         * la separación al justificar verticalmente una página; el lienzo continuo del editor no
         * justifica, así que usa el mínimo.
         */
        const val MIN_SYSTEM_DISTANCE_SPACES: Float = 8.5f

        /**
         * **Aire entre sistemas**, en espacios de pentagrama, medido de la **tinta** de uno al
         * borde del siguiente (D-91).
         *
         * `minSystemDistance` de MuseScore **no** es esto: es la distancia entre la **última cuerda
         * de un sistema** y la **primera del siguiente**, y en él la plica y las figuras cuelgan
         * *dentro* de esa distancia. Aquí el bloque de cada sistema ya incluye plicas, figuras y
         * digitación —su «caja de tinta»—, así que sumarle 8,5 espacios más **encima** duplicaba el
         * aire: medido en el render, entre la última tinta de un sistema y la primera del siguiente
         * había **490 px** donde el `salmo2.pdf` deja **43 px** (1,39 espacios).
         *
         * Por eso se usa la medida **real del PDF**: 1,39 espacios entre tintas.
         */
        const val SYSTEM_GAP_BETWEEN_INK_SPACES: Float = 1.4f

        /**
         * **Separación máxima entre sistemas**, en espacios de pentagrama (D-17).
         *
         * `styleDef(maxSystemDistance, 15.0_sp)` en `src/engraving/style/styledef.cpp` (línea 59). Es
         * el tope que `PageLayout::justifySystems()` respeta al repartir el alto sobrante de una
         * página impresa entre los huecos entre sistemas
         * (`src/engraving/rendering/score/pagelayout.cpp`). El lienzo del editor no justifica, así
         * que solo lo usa el motor de PDF.
         */
        const val MAX_SYSTEM_DISTANCE_SPACES: Float = 15.0f
    }
}

/**
 * Texto de la cifra. Las técnicas que tienen **glifo vectorial** ([TabGlyphKind]) **no** se escriben
 * aquí: se dibujan aparte (arco de ligadura, bend, vibrato, `P.M.`…), porque un `P.M.` pegado al
 * número ensanchaba la casilla hasta recortarse y «no se veía». Solo la **púa** (`↓`/`↑`) va como
 * prefijo, porque es parte de cómo se lee el ataque y ocupa un solo carácter.
 *
 * Dos técnicas **sí** cambian el texto, porque son la propia cifra:
 *  - **Cuerda apagada** ([TabTechnique.MUTED]): se escribe `X` en vez del traste (no hay altura).
 *  - **Nota fantasma** ([TabTechnique.GHOST]): la cifra va entre paréntesis, `(5)`.
 */
fun fretText(fret: Int, techniques: Set<TabTechnique>): String {
    // La cifra es **solo** el número (o la `X` del apagado, o la nota fantasma entre paréntesis). La
    // púa **no** se escribe aquí: es un **símbolo aparte** que se dibuja sobre la cuerda, como en
    // MuseScore (un arco, no una flecha pegada al número).
    val base = if (TabTechnique.MUTED in techniques) "X" else fret.toString()
    return if (TabTechnique.GHOST in techniques) "($base)" else base
}

/**
 * Texto de la cifra **con alteración** (T4): la alteración se antepone a la cifra (`♯3`, `♭5`), como
 * en la tablatura escrita a mano. `null` deja la cifra tal cual.
 */
fun fretText(fret: Int, techniques: Set<TabTechnique>, accidental: com.grafitto.laudate.domain.model.tablature.TabAccidental?): String {
    val base = fretText(fret, techniques)
    return if (accidental == null) base else "${accidental.glyph}$base"
}

/** Texto de la cifra para una **sola** técnica (comodidad para importadores y tests). */
fun fretText(fret: Int, technique: TabTechnique): String =
    fretText(fret, if (technique == TabTechnique.NONE) emptySet() else setOf(technique))

/**
 * Cifra de la nota **según el estilo** de MuseScore ([FretMarkStyle]): en `NUMBERS` la cifra es el
 * traste (`0`-`9`, `12`…); en `LETTERS` se rotula con la letra correspondiente (`a`-`k`, **sin la `i`**,
 * que es la convención histórica de las tablaturas de laúd y vihuela). Las marcas que **no** son una
 * cifra (cuerda apagada `X`, nota fantasma `(5)`, la púa `↓`) se mantienen igual en los dos estilos.
 */
fun fretMarkText(pluck: TabPluck, style: FretMarkStyle): String {
    if (style == FretMarkStyle.NUMBERS) {
        return fretText(pluck.fret, pluck.techniques, pluck.accidental)
    }
    // Letras: `a`-`k` saltando la `i` (0=a, 1=b, … 8=j, 9=k), como una tablatura antigua.
    val letters = "abcdefghjklmnopqrstuvwxyz"
    val base = if (pluck.isMuted) "X" else {
        letters.getOrNull(pluck.fret)?.toString() ?: pluck.fret.toString()
    }
    val ghosted = if (TabTechnique.GHOST in pluck.techniques) "($base)" else base
    return pluck.accidental?.let { "${it.glyph}$ghosted" } ?: ghosted
}

/**
 * **Rótulo de una cifra de adorno**: solo el traste, sin técnicas ni alteraciones (un adorno no las
 * lleva). Respeta el estilo de cifras (`Numbers` / `Letters`) igual que la cifra principal.
 */
fun fretMarkText(fret: Int, style: FretMarkStyle): String {
    if (style == FretMarkStyle.NUMBERS) return fret.toString()
    val letters = "abcdefghjklmnopqrstuvwxyz"
    return letters.getOrNull(fret)?.toString() ?: fret.toString()
}

/**
 * **Rótulo** que acompaña a un símbolo cuando la técnica tiene una **magnitud**: hoy solo la cifra
 * del bend (`1/2`, `1`…). `null` si el símbolo no se rotula.
 */
fun TabPluck.glyphLabel(kind: TabGlyphKind): String? = when (kind) {
    TabGlyphKind.BEND, TabGlyphKind.BEND_DOWN, TabGlyphKind.PRE_BEND -> bendAmount?.label
    TabGlyphKind.HARMONIC -> if (has(TabTechnique.ARTIFICIAL_HARMONIC)) "A.H." else null
    TabGlyphKind.TRILL -> "tr"
    TabGlyphKind.PINCH_HARMONIC -> "P.H."
    TabGlyphKind.TAPPED_HARMONIC -> "T.H."
    TabGlyphKind.VIOLIN -> "0<>"
    TabGlyphKind.FRETTING_FINGER -> frettingFinger?.number?.toString()
    TabGlyphKind.PICKING_FINGER -> pickingFinger?.label
    else -> null
}

/** **Alcance en columnas** de un símbolo que se prolonga: hoy solo el `P.M.` (`1` = su columna). */
fun TabPluck.glyphSpan(kind: TabGlyphKind): Int =
    if (kind == TabGlyphKind.PALM_MUTE) (palmMuteSpan ?: 1).coerceAtLeast(1) else 1

/**
 * Glifo vectorial de una técnica (`null` si no tiene símbolo propio y se representa con texto).
 * Los símbolos se dibujan con `Canvas` para que se lean como en los editores de referencia y no se
 * recorten al ancho de la casilla.
 */
fun TabTechnique.vectorialGlyph(): TabGlyphKind? = when (this) {
    TabTechnique.TREMOLO -> TabGlyphKind.TREMOLO
    TabTechnique.VIBRATO -> TabGlyphKind.VIBRATO
    TabTechnique.BEND -> TabGlyphKind.BEND
    TabTechnique.BEND_DOWN -> TabGlyphKind.BEND_DOWN
    TabTechnique.PRE_BEND -> TabGlyphKind.PRE_BEND
    TabTechnique.SLIDE -> TabGlyphKind.SLIDE
    TabTechnique.HAMMER, TabTechnique.PULL -> TabGlyphKind.SLUR
    TabTechnique.HARMONIC, TabTechnique.ARTIFICIAL_HARMONIC -> TabGlyphKind.HARMONIC
    TabTechnique.PINCH_HARMONIC -> TabGlyphKind.PINCH_HARMONIC
    TabTechnique.TAPPED_HARMONIC -> TabGlyphKind.TAPPED_HARMONIC
    TabTechnique.PALM_MUTE -> TabGlyphKind.PALM_MUTE
    TabTechnique.LET_RING -> TabGlyphKind.LET_RING
    TabTechnique.TAPPING -> TabGlyphKind.TAPPING
    TabTechnique.TR -> TabGlyphKind.TRILL
    TabTechnique.ARPEGGIO -> TabGlyphKind.ARPEGGIO
    TabTechnique.RAKE -> TabGlyphKind.RAKE
    TabTechnique.WHAMMY_DIVE -> TabGlyphKind.WHAMMY_DIVE
    TabTechnique.WHAMMY_SCOOP -> TabGlyphKind.WHAMMY_SCOOP
    TabTechnique.TREMOLO_BAR -> TabGlyphKind.TREMOLO_BAR
    TabTechnique.VIOLIN -> TabGlyphKind.VIOLIN
    // `MUTED` y `GHOST` **son** la cifra (`X`, `(5)`): no llevan símbolo aparte.
    TabTechnique.NONE, TabTechnique.PICK_DOWN, TabTechnique.PICK_UP,
    TabTechnique.MUTED, TabTechnique.GHOST -> null
}

/**
 * **Símbolos que van PEGADOS A LA CIFRA**, no en la pila de arriba (D-89).
 *
 * Comprobado en el código fuente de MuseScore:
 *
 * - **Bend / pre-bend / bend down**: son un `GuitarBend`, un `SLine` que **nace en la nota**. Su cifra
 *   (`bendDigit`) se pega a `arrow.front()` —el extremo de la flecha sobre el propio traste— y el arco
 *   arranca en `startNote->pos() + 0.25·spatium` a la derecha de la cifra
 *   (`guitarbendlayout.cpp`: `computeStartPos` y `layoutBendTabStaff`). No vive en una franja superior.
 * - **Slide**: es un `Glissando` `STRAIGHT`, una línea que une **dos notas**; su texto va `Sl` junto al
 *   inicio, **inmediatamente después de la cifra** (`src/importexport/tabledit/internal/importtef.cpp`
 *   crea el glissando con `setText(u"Sl")`).
 * - **Trino (`tr`)**: `Trill` es un `LineSegment` `ON_STAFF` cuyo texto se apoya sobre la cabeza, pegado
 *   a ella, no en la nube superior del sistema.
 * - **Vibrato**: `Vibrato` es un `SLine` `ON_STAFF` que ondula sobre la cuerda de la nota
 *   (`vibratoPosAbove = (0, -1)` en `styledef.cpp` y en las plantillas `score_style.mss`): un espacio
 *   por encima de **su** cuerda.
 *
 * Los que **sí** van arriba son las `Articulation`/`Ornament` de MuseScore (acento, picado, calderón,
 * mordente, grupeto) y los arcos de púa, que también son `Articulation`.
 */
val TabTechnique.goesNextToFret: Boolean
    get() = when (this) {
        TabTechnique.BEND, TabTechnique.BEND_DOWN, TabTechnique.PRE_BEND,
        TabTechnique.SLIDE, TabTechnique.TR, TabTechnique.VIBRATO -> true
        else -> false
    }

/** Nombre legible de una **técnica** de tablatura, en español (RF-127). */
fun techniqueLabel(technique: TabTechnique): String = when (technique) {
    TabTechnique.NONE -> "Sin técnica"
    TabTechnique.TREMOLO -> "Trémolo"
    TabTechnique.HAMMER -> "Ligado ascendente (hammer-on)"
    TabTechnique.PULL -> "Ligado descendente (pull-off)"
    TabTechnique.SLIDE -> "Deslizamiento (slide)"
    TabTechnique.BEND -> "Estiramiento (bend)"
    TabTechnique.HARMONIC -> "Armónico"
    TabTechnique.PALM_MUTE -> "Apagado de palma (palm mute)"
    TabTechnique.VIBRATO -> "Vibrato"
    TabTechnique.PICK_DOWN -> "Púa hacia abajo"
    TabTechnique.PICK_UP -> "Púa hacia arriba"
    TabTechnique.LET_RING -> "Dejar sonar (let ring)"
    TabTechnique.MUTED -> "Cuerda apagada (muted string)"
    TabTechnique.GHOST -> "Nota fantasma (ghost note)"
    TabTechnique.TAPPING -> "Tapping"
    TabTechnique.BEND_DOWN -> "Estiramiento descendente (bend down)"
    TabTechnique.PRE_BEND -> "Pre-estiramiento (pre-bend)"
    TabTechnique.TR -> "Trino (trill)"
    TabTechnique.ARPEGGIO -> "Arpegio"
    TabTechnique.RAKE -> "Rake (rasgueo previo)"
    TabTechnique.ARTIFICIAL_HARMONIC -> "Armónico artificial (A.H.)"
    TabTechnique.PINCH_HARMONIC -> "Armónico de pinza (P.H.)"
    TabTechnique.TAPPED_HARMONIC -> "Armónico pulsado (T.H.)"
    TabTechnique.WHAMMY_DIVE -> "Palanca: caída (whammy dive)"
    TabTechnique.WHAMMY_SCOOP -> "Palanca: recogida (whammy scoop)"
    TabTechnique.TREMOLO_BAR -> "Palanca: trémolo (tremolo bar)"
    TabTechnique.VIOLIN -> "Efecto violín (volumen)"
}

/**
 * **Familias de técnicas** para la paleta del editor: agrupa los símbolos por lo que hacen, de modo
 * que cada grupo vaya en su propia sección y no todos revueltos. Es la taxonomía de los editores de
 * referencia (Guitar Pro): expresión, ligados, mano derecha y duración.
 */
enum class TabTechniqueFamily(val label: String) {
    EXPRESSION("Expresión"),
    SLURS("Ligados"),
    PICKING("Mano derecha"),
    SUSTAIN("Duración"),
    HARMONICS("Armónicos"),
    WHAMMY("Palanca");

    /** Técnicas de la familia, en el orden en que se muestran. */
    val techniques: List<TabTechnique>
        get() = when (this) {
            EXPRESSION -> listOf(
                TabTechnique.TREMOLO,
                TabTechnique.VIBRATO,
                TabTechnique.BEND,
                TabTechnique.BEND_DOWN,
                TabTechnique.PRE_BEND,
                TabTechnique.SLIDE,
                TabTechnique.TR,
                TabTechnique.VIOLIN
            )
            SLURS -> listOf(
                TabTechnique.HAMMER,
                TabTechnique.PULL,
                TabTechnique.HARMONIC,
                TabTechnique.TAPPING
            )
            PICKING -> listOf(
                TabTechnique.PICK_DOWN,
                TabTechnique.PICK_UP,
                TabTechnique.PALM_MUTE,
                TabTechnique.MUTED,
                TabTechnique.ARPEGGIO,
                TabTechnique.RAKE
            )
            SUSTAIN -> listOf(TabTechnique.LET_RING, TabTechnique.GHOST)
            HARMONICS -> listOf(
                TabTechnique.ARTIFICIAL_HARMONIC,
                TabTechnique.PINCH_HARMONIC,
                TabTechnique.TAPPED_HARMONIC
            )
            WHAMMY -> listOf(
                TabTechnique.WHAMMY_DIVE,
                TabTechnique.WHAMMY_SCOOP,
                TabTechnique.TREMOLO_BAR
            )
        }
}

/**
 * **Maquetación de una sola fila** (sin cortar en sistemas) — se conserva **solo para los tests** que
 * comprueban la geometría de un único sistema.
 *
 * **No** vuelve a calcular nada: **delega** en [TablatureSystemsLayout], que es la **única** fuente
 * de verdad de la maquetación (franjas, símbolos, figuras y digitación). Antes había aquí una
 * **segunda implementación** con sus propias franjas a mano, y era el motivo de que el editor y el
 * visor no coincidieran.
 */
object TablatureLayout {
    fun layout(tablature: Tablature, settings: TabLayoutSettings = TabLayoutSettings()): TablatureDrawing =
        TablatureSystemsLayout
            .layout(tablature, maxWidth = Float.MAX_VALUE, settings = settings)
            .firstOrNull()
            ?.drawing
            ?: TablatureDrawing(lines = emptyList(), texts = emptyList(), width = 0f, height = 0f)
}
