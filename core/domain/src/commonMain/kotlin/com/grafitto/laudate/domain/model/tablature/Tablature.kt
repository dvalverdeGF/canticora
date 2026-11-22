package com.grafitto.laudate.domain.model.tablature

import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.tablature.TabStringData

/**
 * Modelo de **tablatura** (ADR-017, *Feature T*). Kotlin puro: no conoce Android ni la UI.
 *
 * Una tablatura es un **documento propio** (se crea, se edita y se guarda), no una vista derivada
 * de la melodía (decisión T1). El vocabulario musical (altura, compás, tonalidad) se reutiliza del
 * paquete `model/score` para que las dos features compartan el mismo idioma.
 */

/**
 * **Eje 2 · Técnica** de interpretación (ADR-017 §4.7). La técnica es un **dato**, no un dibujo: el
 * modelo guarda su **significado** (ligado, bend…) y el visor decide cómo representarlo.
 *
 * En v1 el **trémolo** ([TREMOLO]) es requisito del cancionero de laúd (RF-127). El resto de técnicas
 * —ligados, expresión, mano derecha, duración— se modelan y **la UI ya las ofrece** en la paleta de
 * la casilla, siguiendo el catálogo de símbolos de los editores de referencia (§13.5.bis).
 */
enum class TabTechnique {
    NONE,
    TREMOLO,
    HAMMER,
    PULL,
    SLIDE,
    BEND,
    HARMONIC,
    PALM_MUTE,
    VIBRATO,
    /**
     * **Arco hacia abajo** (SMuFL `stringsDownBow`, U+E610). Es el signo que MuseScore dibuja sobre
     * la cuerda: un arco con la punta hacia abajo, no una flecha.
     */
    PICK_DOWN,
    /** **Arco hacia arriba** (SMuFL `stringsUpBow`, U+E612). */
    PICK_UP,
    /** Dejar sonar (`l.v.`, *let ring*). */
    LET_RING,
    /**
     * **Cuerda apagada** (*muted string*): se percute con la mano sin dejar sonar la altura, se
     * escribe `X` en lugar de la cifra. Es un sonido **percusivo**, no una nota afinada.
     */
    MUTED,
    /** **Nota fantasma** (*ghost note*): nota de adorno que se escribe entre paréntesis, `(5)`. */
    GHOST,
    /** **Tapping**: la nota se produce con la mano derecha sobre el mástil, se marca `T`. */
    TAPPING,
    /** **Bend descendente** (*bend down*): se estira la cuerda y se suelta, arco con flecha abajo. */
    BEND_DOWN,
    /** **Pre-bend**: se estira la cuerda **antes** de pulsarla, arco con barra vertical al inicio. */
    PRE_BEND,
    /** **Trino** (*trill*): alternancia rápida entre la nota y la de arriba; se marca `tr`. */
    TR,
    /** **Arpegio**: las notas del acorde se tocan una tras otra; línea ondulada vertical. */
    ARPEGGIO,
    /** **Rake**: se rasgan las cuerdas rápidamente antes de la nota; línea ondulada. */
    RAKE,
    /** **Armónico artificial** (*artificial harmonic*): se marca `A.H.`. */
    ARTIFICIAL_HARMONIC,
    /** **Armónico de pinza** (*pinch harmonic*): se marca `P.H.`. */
    PINCH_HARMONIC,
    /** **Armónico pulsado** (*tapped harmonic*): se marca `T.H.`. */
    TAPPED_HARMONIC,
    /** **Palanca — caída** (*whammy dive*): la nota baja de tono con la palanca. */
    WHAMMY_DIVE,
    /** **Palanca — recogida** (*whammy scoop*): la nota sube de tono y vuelve con la palanca. */
    WHAMMY_SCOOP,
    /** **Palanca — trémolo** (*tremolo bar*): ondulación del tono con la palanca. */
    TREMOLO_BAR,
    /** **Efecto violín**: la nota crece desde el silencio con la rueda de volumen; se marca `0<>`. */
    VIOLIN
}

/**
 * **Dedo de la mano izquierda** que pisa la nota (1 = índice … 4 = meñique). Es opcional: solo se
 * guarda si el usuario lo indica, para poder reproducir la digitación sugerida de una tablatura.
 */
enum class TabFrettingFinger(val number: Int) {
    INDEX(1), MIDDLE(2), RING(3), PINKY(4)
}

/**
 * **Dedo de la mano derecha** (o púa) que pulsa la nota, notación `p-i-m-a` (pulgar, índice, medio,
 * anular). Es opcional.
 */
enum class TabPickingFinger(val label: String) {
    THUMB("p"), INDEX("i"), MIDDLE("m"), RING("a")
}

/**
 * **Cantidad** de un *bend* (estiramiento), en tonos. Es un **dato** del modelo, no un dibujo: el
 * visor decide cómo rotularlo sobre el arco (`1/2`, `1`…). `null` en [TabPluck.bendAmount] = sin
 * indicar (el arco se dibuja sin cifra).
 */
enum class TabBendAmount(val label: String) {
    QUARTER("1/4"),
    HALF("1/2"),
    THREE_QUARTER("3/4"),
    FULL("1"),
    TWO("2")
}

/** Marca de **repetición** de un compás (barras `|:` `:|`, RF-127). */
enum class TabRepeatMark { START, END, BOTH }

/**
 * Tipo de **barra de compás** del final de un compás (grupo «Barras de compás» de la paleta):
 * la barra simple separa compases; la **doble** y la **final** (fina + gruesa) cierran secciones o
 * la pieza. Los signos `|:` `:|` de repetición van en [TabRepeatMark].
 */
enum class TabBarline {
    /** Barra simple `|` (por defecto). */
    SINGLE,

    /** Barra doble `||` (final de sección). */
    DOUBLE,

    /** Barra final `𝄂` (fina + gruesa): cierra la pieza. */
    FINAL
}

/**
 * **Signos de navegación musical** (grupo «Navegación» de la paleta, MuseScore *Repeats & jumps*):
 * indican por dónde se salta en la pieza. Se colocan **en un compás** y se dibujan en la franja de
 * marcas, por encima de la rejilla.
 *
 *  - [SEGNO] (`𝄋`) = punto de salto del *dal segno*.
 *  - [CODA] (`𝄌`) = inicio del fragmento de coda.
 *  - [FINE] = final de la pieza (donde para el *al Fine*).
 *  - [TO_CODA] = *To Coda*: salta a la coda.
 *
 * [symbol] es el glifo con el que se rotula en el visor y el botón de la paleta; [label] es su
 * nombre legible (accesibilidad y textos).
 */
enum class TabNavigationMark(val symbol: String, val label: String, val display: String) {
    /** Segno (SMuFL `segno`, U+E047). */
    SEGNO("\uE047", "Segno", "\uE047"),

    /** Coda (SMuFL `coda`, U+E048). */
    CODA("\uE048", "Coda", "\uE048"),

    /** *Fine*: es **texto**, no hay glifo. */
    FINE("Fine", "Fine", "Fine"),

    /** *To Coda*: es **texto**. */
    TO_CODA("To Coda", "To Coda", "To Coda")
}

/**
 * **Saltos** (*jumps*): indicaciones de la estructura de la pieza (grupo «Saltos» de la paleta).
 * Se escriben **al final del compás** desde el que se ordena el salto y se dibujan como rótulo.
 *
 *  - [DC] / [DC_AL_FINE] / [DC_AL_CODA]: *Da Capo* (volver al principio) y sus variantes.
 *  - [DS] / [DS_AL_FINE] / [DS_AL_CODA]: *Dal Segno* (volver al Segno) y sus variantes.
 *
 * [label] es el texto con el que se rotula (p. ej. `D.C. al Fine`).
 */
enum class TabJump(val label: String) {
    DC("D.C."),
    DC_AL_FINE("D.C. al Fine"),
    DC_AL_CODA("D.C. al Coda"),
    DS("D.S."),
    DS_AL_FINE("D.S. al Fine"),
    DS_AL_CODA("D.S. al Coda")
}

/**
 * **Dinámica** (matiz de intensidad) anclada a un compás (grupo «Dinámicas» de la paleta). Cubre los
 * **matices** (`ppp`…`fff`), los **acentos** (`sf`, `sfz`, `fp`) y los **reguladores** (`cresc.`,
 * `dim.`).
 *
 * [glyph] es el texto con el que se rotula bajo la cuerda inferior, como en una partitura. Los
 * reguladores (`cresc.`/`dim.`) en v1 se escriben **solo como texto**: el *hairpin* (la cuña que
 * crece) queda para una sub-tarea.
 */
enum class TabDynamic(val glyph: String) {
    // El glifo de una dinámica **es** su texto (`pp`, `mf`…): MuseScore lo rotula con la fuente de
    // texto en cursiva, no con un símbolo musical.

    PPP("ppp"),
    PP("pp"),
    P("p"),
    MP("mp"),
    MF("mf"),
    F("f"),
    FF("ff"),
    FFF("fff"),
    SF("sf"),
    SFZ("sfz"),
    FP("fp"),
    CRESCENDO("cresc."),
    DIMINUENDO("dim.");

    /** Indica si la dinámica es un **regulador** (crecimiento o disminución progresiva). */
    val isRegulator: Boolean get() = this == CRESCENDO || this == DIMINUENDO
}

/**
 * Tipo de **regulador** (*hairpin*): la **cuña** que indica un cambio progresivo de intensidad.
 *
 * En MuseScore el regulador **no** es un texto: es un elemento de *span* (`<HairPin>` con
 * `<subtype>crescendo</subtype>` / `diminuendo`) que se dibuja como dos líneas que se **abren**
 * (crescendo) o se **cierran** (diminuendo) bajo el pentagrama. El texto `cresc.`/`dim.` de
 * [TabDynamic] es otra cosa: la abreviatura escrita, que MuseScore también ofrece en su paleta.
 */
enum class TabHairpinType(val label: String) {
    /** La cuña se **abre** hacia la derecha: la intensidad crece. */
    CRESCENDO("Crescendo"),

    /** La cuña se **cierra** hacia la derecha: la intensidad disminuye. */
    DIMINUENDO("Diminuendo")
}

/**
 * **Regulador** (*hairpin*) que **empieza** en un compás y abarca [lengthMeasures] compases.
 *
 * Se guarda en el compás donde **nace** la cuña, igual que MuseScore ancla el `<HairPin>` a su
 * segmento inicial y guarda el final en el elemento siguiente. [lengthMeasures] es el alcance en
 * compases; la maquetación lo recorta al final del sistema, de modo que una cuña que cruza un salto
 * de línea se dibuja hasta el borde y **no** se reanuda (MuseScore sí la reanuda; queda como deuda
 * documentada en `docs/TABLATURE_UX_PARITY.md`).
 */
data class TabHairpin(
    val type: TabHairpinType,
    val lengthMeasures: Int = 1
) {
    /** Nº de compases que abarca la cuña, nunca menos de uno. */
    val measures: Int get() = lengthMeasures.coerceAtLeast(1)
}

/**
 * Tipo de **nota de adorno** (*grace note*), como `GraceType` de MuseScore.
 *
 * En MuseScore la nota de adorno es un **acorde aparte** que se cuelga del principal
 * (`Chord::graceNotesBefore()`), se dibuja a **0,7** del tamaño normal (`graceNoteMag`,
 * `styledef.cpp:524`) y se separa de la nota principal `minNoteDistance · 0,7` = **0,245 espacios**
 * (`chordlayout.cpp:621-635`).
 *
 * **En un pentagrama de tablatura las dos se ven igual**: la barra de la acciaccatura es el
 * `StemSlash` del **asta** (`TLayout::layoutStemSlash`, `tlayout.cpp:5378`) y la tablatura es
 * *stemless* (`StaffType` `TAB_6COMMON`, `stemless = false` pero sin plicas en las cifras), así que
 * MuseScore **no dibuja la barra**. El tipo se guarda igual porque cambia la **duración** al
 * reproducir: la acciaccatura no roba tiempo y la apoyatura se lleva la mitad del valor.
 */
enum class TabGraceType(val label: String) {
    /** **Acciaccatura**: se toca «aplastada» contra la nota principal, sin robarle tiempo. */
    ACCIACCATURA("Acciaccatura"),

    /** **Apoyatura**: se lleva la **mitad** del valor de la nota principal. */
    APPOGGIATURA("Apoyatura")
}

/**
 * Una **nota de adorno** (*grace note*) que se toca **antes** de la casilla que la contiene.
 *
 * Va en la **casilla** y no en la nota porque el adorno es un acorde propio que precede al acorde
 * principal: puede haber varios y pueden estar en cuerdas distintas. Se dibuja como una **cifra
 * pequeña** a la izquierda de la cifra principal, en la misma cuerda.
 */
data class TabGrace(
    /** Cuerda del adorno (1 = la más aguda), igual que [TabPosition.stringNumber]. */
    val stringNumber: Int,
    /** Traste del adorno. */
    val fret: Int,
    /** Tipo de adorno; por defecto la acciaccatura, que es la que pone MuseScore con `/`. */
    val type: TabGraceType = TabGraceType.ACCIACCATURA
)

/**
 * **Categoría** de una articulación: la **cualidad** que expresa, no el dibujo. MuseScore agrupa así
 * los signos (`Articulation::computeCategories`, `articulation.cpp:566`) porque una misma cualidad
 * puede dibujarse de dos maneras: **suelta** (`.`) o **combinada** con otra (`𝄰` = acento + picado).
 *
 * Sirve para dos cosas:
 *  - **Alternar por cualidad**: pulsar «picado» sobre una nota que lleva sforzato **quita solo el
 *    picado** y deja el acento, en vez de borrar el glifo entero.
 *  - **Combinar**: al añadir una cualidad a una nota que ya tiene otra combinable, se sustituyen los
 *    dos signos sueltos por el **glifo combinado** que MuseScore dibuja (`joinArticulations`).
 */
enum class TabArticulationCategory {
    /** Acento: ataque más fuerte. */
    ACCENT,

    /** Marcato: ataque más fuerte y más marcado. */
    MARCATO,

    /** Tenuto: la nota se sostiene todo su valor. */
    TENUTO,

    /** Picado: la nota se corta. */
    STACCATO,

    /** Picadísimo: se corta aún más. */
    STACCATISSIMO,

    /** Calderón. */
    FERMATA,

    /** Adorno (mordente, grupeto). */
    ORNAMENT,

    /** Arcada (arco arriba / abajo). */
    BOW,

    /** Armónico. */
    HARMONIC
}

/**
 * **Articulación** de una nota (grupo «Articulaciones» de la paleta, T3): cómo se ataca o se mantiene
 * el sonido. Se anotan en [TabPluck.articulations] y se dibujan **sobre la cifra**.
 *
 *  - [ACCENT] (`>`), [MARCATO] (`^`): más fuerte / más marcado.
 *  - [STACCATO] (`.`), [STACCATISSIMO] (más corto), [TENUTO] (`-`): duración del sonido.
 *  - [FERMATA] (`𝄐`): calderón, la nota se sostiene a gusto del intérprete.
 *  - [MORDENT], [TURN]: adornos (la nota se toca con una oscilación corta).
 *  - Las **combinadas** ([ACCENT_STACCATO], [MARCATO_STACCATO], [TENUTO_STACCATO], [TENUTO_ACCENT],
 *    [MARCATO_TENUTO]): un **solo glifo** SMuFL que expresa dos cualidades a la vez, como en
 *    MuseScore (`ARTICULATION_ABOVE_JOIN_GROUPS`, `articulation.cpp:749`). No se eligen a mano: las
 *    produce [TabSymbolCompatibility] al juntar dos sueltas.
 *
 * [glyph] es el símbolo con el que se rotula; [label] su nombre legible; [categories] las cualidades
 * que expresa (una, o dos si es combinada).
 */
enum class TabArticulation(
    val glyph: String,
    val label: String,
    val display: String,
    val categories: Set<TabArticulationCategory>
) {
    /** Acento (SMuFL `articAccentAbove`, U+E4A0). */
    ACCENT("\uE4A0", "Acento", "\uE4A0", setOf(TabArticulationCategory.ACCENT)),

    /** Picado (SMuFL `articStaccatoAbove`, U+E4A2). */
    STACCATO("\uE4A2", "Picado (staccato)", "\uE4A2", setOf(TabArticulationCategory.STACCATO)),

    /** Marcato (SMuFL `articMarcatoAbove`, U+E4AC). */
    MARCATO("\uE4AC", "Marcato", "\uE4AC", setOf(TabArticulationCategory.MARCATO)),

    /** Tenuto (SMuFL `articTenutoAbove`, U+E4A4). */
    TENUTO("\uE4A4", "Tenuto", "\uE4A4", setOf(TabArticulationCategory.TENUTO)),

    /** Calderón (SMuFL `fermataAbove`, U+E4C0). */
    FERMATA("\uE4C0", "Calderón (fermata)", "\uE4C0", setOf(TabArticulationCategory.FERMATA)),

    /** Picado extremo (SMuFL `articStaccatissimoAbove`, U+E4A6). */
    STACCATISSIMO(
        "\uE4A6",
        "Picado extremo (staccatissimo)",
        "\uE4A6",
        setOf(TabArticulationCategory.STACCATISSIMO)
    ),

    /** Mordente (SMuFL `ornamentMordent`, U+E56D). */
    MORDENT("\uE56D", "Mordente", "\uE56D", setOf(TabArticulationCategory.ORNAMENT)),

    /** Grupeto (SMuFL `ornamentTurn`, U+E567). */
    TURN("\uE567", "Grupeto (turn)", "\uE567", setOf(TabArticulationCategory.ORNAMENT)),

    /** **Arco hacia arriba** sobre la cuerda (SMuFL `stringsUpBow`, U+E612). */
    UP_BOW("\uE612", "Arco hacia arriba", "\uE612", setOf(TabArticulationCategory.BOW)),

    /** **Arco hacia abajo** sobre la cuerda (SMuFL `stringsDownBow`, U+E610). */
    DOWN_BOW("\uE610", "Arco hacia abajo", "\uE610", setOf(TabArticulationCategory.BOW)),

    /** **Armónico** (SMuFL `stringsHarmonic`, U+E614). */
    HARMONIC("\uE614", "Armónico", "\uE614", setOf(TabArticulationCategory.HARMONIC)),

    /**
     * **Sforzato**: acento **y** picado en un solo glifo (SMuFL `articAccentStaccatoAbove`, U+E4B0).
     * Es el `Shift`+`V` de MuseScore (`shortcuts.json:138`, `TOGGLE_SFORZATO_COMMAND`).
     */
    ACCENT_STACCATO(
        "\uE4B0",
        "Sforzato (acento y picado)",
        "\uE4B0",
        setOf(TabArticulationCategory.ACCENT, TabArticulationCategory.STACCATO)
    ),

    /** **Marcato y picado** (SMuFL `articMarcatoStaccatoAbove`, U+E4AE). */
    MARCATO_STACCATO(
        "\uE4AE",
        "Marcato y picado",
        "\uE4AE",
        setOf(TabArticulationCategory.MARCATO, TabArticulationCategory.STACCATO)
    ),

    /** **Portato**: tenuto **y** picado (SMuFL `articTenutoStaccatoAbove`, U+E4B2). */
    TENUTO_STACCATO(
        "\uE4B2",
        "Portato (tenuto y picado)",
        "\uE4B2",
        setOf(TabArticulationCategory.TENUTO, TabArticulationCategory.STACCATO)
    ),

    /** **Sforzato y tenuto**: acento **y** tenuto (SMuFL `articTenutoAccentAbove`, U+E4B4). */
    TENUTO_ACCENT(
        "\uE4B4",
        "Acento y tenuto",
        "\uE4B4",
        setOf(TabArticulationCategory.TENUTO, TabArticulationCategory.ACCENT)
    ),

    /** **Marcato y tenuto** (SMuFL `articMarcatoTenutoAbove`, U+E4BC). */
    MARCATO_TENUTO(
        "\uE4BC",
        "Marcato y tenuto",
        "\uE4BC",
        setOf(TabArticulationCategory.MARCATO, TabArticulationCategory.TENUTO)
    );

    /**
     * `true` si la articulación se **apila en vertical** sobre la nota (como hace MuseScore: `^`, `>`,
     * `𝄢` uno encima de otro) en vez de colocarse en la propia cifra.
     *
     * Quedan **fuera** de la pila los símbolos que **no** son un adorno de articulación:
     *  - el **armónico** ([HARMONIC]) se pinta **en** la cifra (un diamante);
     *  - los **arcos de púa** ([UP_BOW]/[DOWN_BOW]) son la **técnica** `PICK_UP`/`PICK_DOWN` (que se
     *    dibuja en su propia franja sobre la cuerda): tenerlos **también** como articulación era una
     *    **duplicación** que permitía marcar arco arriba y abajo a la vez (corrección del usuario).
     */
    val stacks: Boolean
        get() = this != HARMONIC && this != UP_BOW && this != DOWN_BOW

    /**
     * `true` si el glifo **combina dos cualidades** (sforzato, portato…). MuseScore los llama
     * *double* (`Articulation::isDouble`): no se eligen de la paleta, los produce la combinación.
     */
    val isCombined: Boolean
        get() = categories.size > 1

    /**
     * Las articulaciones **sueltas** que este glifo representa: él mismo si es simple, o las dos que
     * combina. Es la inversa de [TabSymbolCompatibility.join] y la que usa el **desglose** al alternar
     * (MuseScore: `splitArticulations`, `articulation.cpp:772`).
     */
    val components: Set<TabArticulation>
        get() = when (this) {
            ACCENT_STACCATO -> setOf(ACCENT, STACCATO)
            MARCATO_STACCATO -> setOf(MARCATO, STACCATO)
            TENUTO_STACCATO -> setOf(TENUTO, STACCATO)
            TENUTO_ACCENT -> setOf(TENUTO, ACCENT)
            MARCATO_TENUTO -> setOf(MARCATO, TENUTO)
            else -> setOf(this)
        }
}

/**
 * **Alteración** escrita junto a la cifra de una nota (grupo «Alteraciones», T4). Sirve para las
 * tablaturas del cancionero que anotan la alteración de la nota **en la propia cuerda** (música con
 * armaduras o notas alteradas sueltas). Es un **dato**: no cambia el traste, aclara la altura leída.
 *
 * [glyph] es el símbolo con el que se rotula; [label] su nombre legible.
 */
enum class TabAccidental(val glyph: String, val label: String) {
    FLAT("♭", "Bemol"),
    NATURAL("♮", "Becuadro"),
    SHARP("♯", "Sostenido"),
    DOUBLE_SHARP("𝄪", "Doble sostenido"),
    DOUBLE_FLAT("𝄫", "Doble bemol")
}

/**
 * **Cambio de octava** de un pasaje (grupo «Altura» de la paleta, T4): la música se lee una octava
 * (u octava y quinta) por encima o por debajo de lo escrito. Se anota en el **compás** donde empieza
 * el pasaje.
 *
 *  - [OTTAVA] (`8va`): una octava **arriba**.
 *  - [QUINDICESIMA] (`15ma`): dos octavas **arriba**.
 *  - [OTTAVA_BASSA] (`8vb`): una octava **abajo**.
 *
 * [label] es el rótulo con el que se dibuja sobre el sistema.
 */
enum class TabOctaveShift(val label: String) {
    /** Una octava arriba: SMuFL `ottavaAlta` (U+E510). */
    OTTAVA("8va"),

    /** Dos octavas arriba: SMuFL `quindicesimaAlta` (U+E512). */
    QUINDICESIMA("15ma"),

    /** Una octava abajo: SMuFL `ottavaBassa` (U+E511). */
    OTTAVA_BASSA("8vb");

    /** **Glifo** con el que se dibuja (el rótulo `8va` solo es para leerlo en un listado). */
    val display: String
        get() = when (this) {
            OTTAVA -> "\uE510"
            QUINDICESIMA -> "\uE512"
            OTTAVA_BASSA -> "\uE511"
        }
}

/**
 * **Eje 1 · Posición** (ADR-017 §4.7): dónde se pisa la nota.
 *
 *  - [stringNumber] usa **1 = la cuerda más aguda** (como se lee en cualquier tablatura). En el
 *    modelo interno de la afinación el orden va **al revés** (grave → aguda); el conversor
 *    `Tuning.pitchOf(stringNumber)` hace el puente.
 *  - [fret] `0` = cuerda al aire.
 *
 * La **afinación** del instrumento completa la posición: `altura = afinación[cuerda] + fret`.
 */
data class TabPosition(val stringNumber: Int, val fret: Int)

/**
 * **Eje 3 · Ejecución** (ADR-017 §4.7): cómo **suena** la nota (velocidad, dinámica, MIDI). En v1
 * **no hay reproducción** (ADR-012), así que queda `null`; se modela ya para que una partitura
 * importada o un MusicXML no **pierdan** estos datos al cruzar de formato.
 */
data class TabExecution(
    val velocity: Int? = null,
    val dynamics: String? = null,
    val midiNote: Int? = null
)

/**
 * **Qué se rotula** sobre el corchete de un grupo irregular. Es el `TupletNumberType` de MuseScore
 * (`src/engraving/dom/tuplet.h:34`), con sus tres valores y su valor por defecto.
 *
 * MuseScore lo expone en *Propiedades ▸ Grupo irregular ▸ Número*; aquí se guarda en el propio
 * [TabTuplet] para que el ajuste viaje con el grupo y sobreviva a guardar y reabrir.
 */
enum class TabTupletNumberType(val label: String) {
    /** Solo el número de figuras: `3`. Es el valor por defecto de MuseScore. */
    SHOW_NUMBER("Número"),

    /** La proporción completa: `3:2`. */
    SHOW_RELATION("Proporción"),

    /** Sin cifra: el corchete queda mudo. */
    NO_TEXT("Sin texto")
}

/**
 * **Si se dibuja el corchete** de un grupo irregular. Es el `TupletBracketType` de MuseScore
 * (`src/engraving/dom/tuplet.h:60`).
 *
 * El valor por defecto es [AUTO_BRACKET], y **no** significa «siempre corchete»: MuseScore lo omite
 * cuando el grupo ocupa exactamente una barra de unión y la cifra cae del lado de la plica
 * (`Tuplet::calcHasBracket`, `tuplet.cpp:203-279`). Un tresillo de corcheas dentro de una barra se
 * rotula con el `3` **sin** corchete; uno que cruza un cambio de barra sí lo lleva.
 */
enum class TabTupletBracketType(val label: String) {
    /** Automático: corchete solo cuando hace falta (regla de MuseScore). */
    AUTO_BRACKET("Automático"),

    /** Siempre corchete. */
    SHOW_BRACKET("Con corchete"),

    /** Nunca corchete. */
    SHOW_NO_BRACKET("Sin corchete")
}

/**
 * **Grupo de valor especial** — el `Tuplet` de MuseScore Studio 4.
 *
 * MuseScore no modela el tresillo como una marca booleana sino como un **grupo con proporción**
 * `numNotes : normalNotes` (`Tuplet::ratio()`, `src/engraving/dom/tuplet.h`): `3:2` es el tresillo,
 * `5:4` el quintillo, `6:4` el seisillo, `7:4` el septillo y `9:8` el nonillo. La duración efectiva de
 * cada figura del grupo es `normalNotes / numNotes` de la escrita, y el **número** que se rotula sobre
 * el corchete es `numNotes` (`TupletNumberType::SHOW_NUMBER`, el valor por defecto de
 * `styleDef(tupletNumberType)` en `src/engraving/style/styledef.cpp`).
 *
 * Antes el modelo solo tenía un `isTriplet: Boolean`, así que un quintillo o un septillo —que
 * MuseScore crea con *Crear grupo irregular*— no se podían ni escribir ni importar: se perdían al
 * cruzar de formato. Con la proporción explícita se conservan todos.
 *
 * [numNotes] y [normalNotes] son **positivos** por contrato: un grupo de `0` figuras no existe y
 * `ratio` dividiría por cero.
 */
data class TabTuplet(
    val numNotes: Int = 3,
    val normalNotes: Int = 2,
    /**
     * **Qué se rotula** sobre el corchete (`TupletNumberType` de MuseScore, `tuplet.h:34`). Por
     * defecto solo el número de figuras, que es lo que hace MuseScore
     * (`m_numberType = TupletNumberType::SHOW_NUMBER`, `tuplet.cpp:70`).
     */
    val numberType: TabTupletNumberType = TabTupletNumberType.SHOW_NUMBER,
    /**
     * **Si se dibuja el corchete** (`TupletBracketType` de MuseScore, `tuplet.h:60`). Por defecto
     * `AUTO_BRACKET`, que **omite** el corchete cuando el grupo ocupa exactamente una barra de unión
     * y la cifra cae del lado de la plica (`Tuplet::calcHasBracket`, `tuplet.cpp:203`).
     */
    val bracketType: TabTupletBracketType = TabTupletBracketType.AUTO_BRACKET
) {
    init {
        require(numNotes > 0) { "numNotes debe ser positivo (era $numNotes)" }
        require(normalNotes > 0) { "normalNotes debe ser positivo (era $normalNotes)" }
    }

    /** **Factor de duración** de cada figura del grupo: `normalNotes / numNotes` (tresillo = 2/3). */
    val ratio: Double get() = normalNotes.toDouble() / numNotes

    /**
     * Rótulo del corchete según [numberType]: `3` con `SHOW_NUMBER`, `3:2` con `SHOW_RELATION` y
     * **vacío** con `NO_TEXT` (MuseScore deja el corchete sin cifra).
     */
    val label: String
        get() = when (numberType) {
            TabTupletNumberType.SHOW_NUMBER -> numNotes.toString()
            TabTupletNumberType.SHOW_RELATION -> relationLabel
            TabTupletNumberType.NO_TEXT -> ""
        }

    /** Rótulo con `TupletNumberType::SHOW_RELATION`: la proporción completa (`3:2`). */
    val relationLabel: String get() = "$numNotes:$normalNotes"

    /** `true` si el corchete lleva **cifra** que rotular. */
    val hasLabel: Boolean get() = label.isNotEmpty()

    companion object {
        /** Tresillo: tres figuras en el tiempo de dos. */
        val TRIPLET: TabTuplet = TabTuplet(3, 2)

        /** Quintillo: cinco figuras en el tiempo de cuatro. */
        val QUINTUPLET: TabTuplet = TabTuplet(5, 4)

        /** Seisillo: seis figuras en el tiempo de cuatro. */
        val SEXTUPLET: TabTuplet = TabTuplet(6, 4)

        /** Septillo: siete figuras en el tiempo de cuatro. */
        val SEPTUPLET: TabTuplet = TabTuplet(7, 4)

        /** Nonillo: nueve figuras en el tiempo de ocho. */
        val NONUPLET: TabTuplet = TabTuplet(9, 8)

        /**
         * Las proporciones que ofrece el diálogo *Crear grupo irregular* de MuseScore, en el orden en
         * que las presenta. Es la lista que consume la paleta «Grupos irregulares».
         */
        val PRESETS: List<TabTuplet> = listOf(TRIPLET, QUINTUPLET, SEXTUPLET, SEPTUPLET, NONUPLET)
    }
}

/** Lo que se toca en una **casilla** de la rejilla: puede haber varias cuerdas a la vez. */
data class TabSlot(
    val strings: List<TabPluck> = emptyList(),
    /** `true` si la casilla **prolonga** el sonido de la anterior (no se vuelve a pulsar). */
    val isHeld: Boolean = false,
    /**
     * Figura rítmica de la posición (compartida por todas las cuerdas del acorde). `null` conserva
     * la compatibilidad: se interpreta según [Tablature.resolution].
     */
    val rhythmicFigure: RhythmFigure? = null,
    /**
     * **Grupo de valor especial** al que pertenece la casilla ([TabTuplet]), o `null` si la figura vale
     * lo que dice su dibujo. Sustituye al antiguo `isTriplet: Boolean`, que solo sabía expresar `3:2`.
     */
    val tuplet: TabTuplet? = null,
    /**
     * **Voz** a la que pertenece la posición (1–4), como en MuseScore 4. Al compartir la figura
     * rítmica todas las cuerdas de la posición comparten voz: es la voz del **acorde**, no de cada
     * nota suelta. `1` = voz 1 (la de escritura por defecto).
     *
     * La voz se conserva al serializar para que un archivo guardado y reabierto mantenga el color
     * con el que se pintó; si no se guardara, el color se perdería al cerrar la app.
     */
    val voice: Int = 1,
    /**
     * `true` si la casilla lleva una **ligadura de expresión** (*slur*) hacia la **siguiente** casilla
     * con sonido, como la tecla `S` de MuseScore 4.
     *
     * Se guarda en la **casilla** y no en la nota porque la ligadura de expresión es una indicación de
     * **fraseo**, no de duración: une dos posiciones cualesquiera, aunque sus alturas no coincidan. La
     * ligadura de **prolongación** (*tie*) es lo contrario — une dos notas de la **misma** altura y por
     * eso vive en la nota ([TabPluck.tiedToNext]).
     *
     * Varias casillas seguidas con la marca forman **una sola** ligadura larga (MuseScore dibuja un
     * único arco, no uno por par).
     */
    val slurToNext: Boolean = false,
    /**
     * **Notas de adorno** (*grace notes*) que se tocan **antes** de esta casilla, como
     * `Chord::graceNotesBefore()` de MuseScore. Se dibujan como cifras **pequeñas** (0,7 del tamaño)
     * a la izquierda de la cifra principal y **ensanchan la columna** lo justo para no pisarla.
     *
     * El orden de la lista es el de **ejecución**: el primero se toca primero, así que el **último**
     * es el que queda pegado a la nota principal.
     */
    val graces: List<TabGrace> = emptyList()
) {
    /** Indica si la casilla no contiene ninguna pulsación (pausa). */
    val isEmpty: Boolean get() = strings.isEmpty()

    /** `true` si la casilla lleva alguna **nota de adorno**. */
    val hasGraces: Boolean get() = graces.isNotEmpty()

    /**
     * `true` si la casilla forma parte de un **tresillo** (`3:2`). Se conserva como atajo de lectura
     * porque es el grupo irregular más frecuente; el resto de proporciones se consultan en [tuplet].
     */
    val isTriplet: Boolean get() = tuplet == TabTuplet.TRIPLET
}

/**
 * Una cuerda pulsada = **Posición** + **Técnica** + **Ejecución** opcional (ADR-017 §4.7).
 *
 * Los ejes se mantienen **separados** a propósito: la posición dice *dónde*, la técnica *cómo se
 * articula* y la ejecución *cómo suena*. El modelo no se limita a lo que el visor dibuja ni a lo
 * que MusicXML sabe representar. [stringNumber] y [fret] se exponen como **derivados** de la
 * posición para comodidad de lectura.
 *
 * [techniques] es un **conjunto**: los marcados se **combinan** (un ligado puede llevar además un
 * slide, un bend puede llevar vibrato…), como en My Guitar Tabs. La propiedad [technique] devuelve el
 * principal para los sitios que solo esperan uno.
 *
 * [bendAmount] y [palmMuteSpan] son **parámetros** de dos de esas técnicas (cuánto se estira el bend y
 * cuántas columnas abarca el apagado de palma). Se guardan aparte de [techniques] porque son
 * **magnitudes**, no marcas: la técnica dice *qué* se hace y estos campos *cuánto*.
 */
data class TabPluck(
    val position: TabPosition,
    val techniques: Set<TabTechnique> = emptySet(),
    val execution: TabExecution? = null,
    /** Cantidad del *bend* (`1/2`, `1`…); `null` = sin indicar. */
    val bendAmount: TabBendAmount? = null,
    /** Nº de **columnas** que abarca el `P.M.` desde esta nota; `null` = solo su columna. */
    val palmMuteSpan: Int? = null,
    /** Dedo de la **mano izquierda** que pisa la nota (opcional). */
    val frettingFinger: TabFrettingFinger? = null,
    /** Dedo/púa de la **mano derecha** que pulsa la nota (opcional). */
    val pickingFinger: TabPickingFinger? = null,
    /**
     * **Articulaciones** de la nota (acento, picado, calderón…), T3. Se **combinan**: un acento con un
     * picado o un `tenuto` con calderón son posibles, como en una partitura.
     */
    val articulations: Set<TabArticulation> = emptySet(),
    /**
     * **Alteración** escrita junto a la cifra (bemol, becuadro, sostenido…), T4. `null` = sin alteración
     * (la nota se lee por su traste). Es una aclaración de la altura, no cambia el traste.
     */
    val accidental: TabAccidental? = null,
    /**
     * **Ligadura de prolongación** (*tie*, B-10): la nota **sigue sonando** en la siguiente nota de la
     * misma cuerda y la misma altura, y las dos se escriben unidas por un arco. Es lo que en MuseScore
     * hace `T` (`tie` en `shortcuts.xml`).
     *
     * No confundir con [TabSlot.isHeld] (la nota se mantiene **dentro** de la misma columna) ni con
     * el **ligado de expresión** (*slur*, `S` en MuseScore), que une notas de **distinta** altura: aquí
     * solo se modela la prolongación, que es la que cambia la **duración** del sonido.
     */
    val tiedToNext: Boolean = false,
    /**
     * **Número de trazos** del trémolo (1–4), como la paleta «Trémolos» de MuseScore, que ofrece
     * `tremolo1`…`tremolo4` (`Tremolo::setTremoloType` en `src/engraving/libmscore/tremolo.cpp`).
     * Un trazo es una **corchea** de repetición, dos una **semicorchea**, tres una **fusa** y cuatro
     * una **semifusa**: cuantos más trazos, más rápida la alternancia.
     *
     * Solo tiene sentido con [TabTechnique.TREMOLO]; en las demás técnicas se ignora. Se guarda
     * siempre (aunque no haya trémolo) para que activar y desactivar la técnica **no pierda** el
     * número de trazos que el usuario había elegido.
     */
    val tremoloStrokes: Int = 1
) {
    /** Cuerda (1 = la más aguda). Derivado de [position]. */
    val stringNumber: Int get() = position.stringNumber

    /** Trazos del trémolo **acotados** a `1..`[MAX_TREMOLO_STROKES]; nunca deja el dibujo sin trazos. */
    val strokes: Int get() = tremoloStrokes.coerceIn(1, MAX_TREMOLO_STROKES)

    /** Traste (0 = al aire). Derivado de [position]. */
    val fret: Int get() = position.fret

    /** Técnica **principal** (la primera declarada) para los sitios que solo admiten una. */
    val technique: TabTechnique get() = techniques.firstOrNull() ?: TabTechnique.NONE

    /** Indica si la nota lleva la técnica [technique]. */
    fun has(technique: TabTechnique): Boolean = technique in techniques

    /** Indica si la cuerda está **apagada** (`X`): suena como percusión, sin altura definida. */
    val isMuted: Boolean get() = has(TabTechnique.MUTED)

    /** Indica si la nota lleva algún tipo de **estiramiento** (bend, bend down o pre-bend). */
    val hasBend: Boolean
        get() = has(TabTechnique.BEND) || has(TabTechnique.BEND_DOWN) || has(TabTechnique.PRE_BEND)

    companion object {
        /**
         * **Trazos máximos** de un trémolo. MuseScore llega a cuatro (`tremolo4`), que es una
         * semifusa: más trazos no se leen y ensucian la cifra.
         */
        const val MAX_TREMOLO_STROKES = 4

        /** Azúcar de construcción: `TabPluck(6, 0)` equivale a `TabPluck(TabPosition(6, 0))`. */
        operator fun invoke(
            stringNumber: Int,
            fret: Int,
            technique: TabTechnique = TabTechnique.NONE,
            execution: TabExecution? = null
        ): TabPluck = TabPluck(
            position = TabPosition(stringNumber, fret),
            techniques = if (technique == TabTechnique.NONE) emptySet() else setOf(technique),
            execution = execution
        )
    }
}

/**
 * Un compás de tablatura: una secuencia de **columnas** (eventos) y las **marcas del manuscrito**.
 *
 * La unidad de edición es la **columna**, no la casilla fija: escribir una nota crea una columna
 * nueva (como en My Guitar Tabs), de modo que «la nota siguiente» de un ligado es la columna
 * contigua y no hay casillas vacías de relleno. El número de columnas es libre; [TabResolution] y
 * [ScoreTimeSignature] quedan como **metadatos** de lectura (qué figura representa cada columna).
 *
 *  - [repeat]: barras de repetición `|:` `:|`.
 *  - [ending]: casilla de **volta** (1ª vez = 1, 2ª vez = 2…); `null` = sin casilla.
 *  - [endingTo]: última casilla cuando la volta cubre **varias pasadas** (`1, 2.`).
 */
data class TabMeasure(
    val slots: List<TabSlot> = emptyList(),
    val repeat: TabRepeatMark? = null,
    val ending: Int? = null,
    /**
     * **Última casilla** de la volta cuando cubre un **rango de pasadas**: `ending = 1` con
     * `endingTo = 2` es la casilla `1, 2.` de MuseScore, que suena en la primera **y** en la segunda
     * vuelta. `null` = la volta es de una sola pasada ([ending]).
     *
     * MuseScore lo modela como un `std::vector<int> endings` en el `Volta`
     * (`src/engraving/dom/volta.h`), que además admite listas **no contiguas** (`1, 3`). Aquí se
     * modela como **rango** porque es lo que la UI ofrece y lo que se lee en una partitura; una lista
     * suelta obligaría a un `List<Int>` en cada compás para un caso que no aparece en el repertorio.
     */
    val endingTo: Int? = null,
    /**
     * **Rótulo propio** de la volta, o `null` para usar el que se deriva de las pasadas (`1.`,
     * `1, 2.`).
     *
     * Es el `beginText` del `Volta` de MuseScore: allí la casilla es un `TextLineBase`
     * (`src/engraving/dom/volta.h:60`) y su rótulo es **texto editable**
     * (`M_PROPERTY(String, beginText, setBeginText)`, `src/engraving/dom/textlinebase.h:109`), no algo
     * que se calcule de `endings()`. Por eso MuseScore permite escribir «1ª vez», «Todos», «Al final»
     * o «Dal segno» sobre el corchete sin tocar las pasadas, que siguen mandando en la **reproducción**
     * ([hasEnding]).
     *
     * Se guarda aparte de [ending]/[endingTo] a propósito: el rótulo es **presentación** y las
     * pasadas son **semántica**. Si el rótulo sustituyera a las pasadas, escribir «Todos» borraría la
     * casilla y la pieza dejaría de repetir.
     */
    val endingText: String? = null,
    /** Veces que se repite el compás al cerrar la repetición; el valor convencional es 2. */
    val repeatCount: Int = 2,
    /** Tipo de **barra de compás** al final de este compás (`simple`, `doble`, `final`). */
    val barline: TabBarline = TabBarline.SINGLE,
    /** Signo de **navegación** (Segno / Coda / Fine / To Coda) anclado a este compás, RF-127. */
    val navigation: TabNavigationMark? = null,
    /** **Salto** (D.C. / D.S. y variantes) escrito al final de este compás, RF-127. */
    val jump: TabJump? = null,
    /** **Dinámica** (matiz/regulador) escrita bajo la cuerda inferior de este compás, RF-127. */
    val dynamic: TabDynamic? = null,
    /**
     * **Regulador** (*hairpin*) que **empieza** en este compás: la cuña de crescendo o diminuendo.
     * Es un elemento distinto de [dynamic]: la dinámica es el matiz escrito (`mp`, `cresc.`) y el
     * regulador es la cuña que lo desarrolla.
     */
    val hairpin: TabHairpin? = null,
    /** **Cambio de octava** (8va / 15ma / 8vb) que **empieza** en este compás, T4. */
    val octaveShift: TabOctaveShift? = null,
    /**
     * **Tempo** (BPM) indicado en este compás, T5. Se rotula `♩ = N` y, si corresponde, con la
     * indicación italiana (`Andante`, `Allegro`…) derivada con `TempoMarking.fromBpm`. `null` = sin
     * indicación (se hereda el tempo anterior). Se muestra sobre el **primer compás del sistema**.
     */
    val tempo: Int? = null,
    /**
     * **Rótulo literal** de la indicación de tempo tal como está escrita en la fuente (`♩ = 80`,
     * `Andante`, `Allegro ma non troppo`…). MuseScore guarda el texto **tal cual** en
     * `<Tempo><text>` y lo pinta sin inventar nada; cuando existe, la maquetación lo usa **en vez**
     * de la etiqueta derivada de [tempo] ([tempoLabel]). `null` = derivar de [tempo].
     *
     * Los símbolos musicales del texto llegan como **puntos de código SMuFL** (por ejemplo
     * `\uECA5` para `metNoteQuarterUp`), que el pintor resuelve con la fuente musical.
     */
    val tempoText: String? = null,
    /**
     * **Texto libre** anclado a este compás (T6): una indicación del copista («ritardando», «solo
     * guitarra», «D.C.», un comentario…). Se rotula sobre el sistema, centrado en el compás.
     */
    val text: String? = null,
    /**
     * `true` si este compás **empieza un sistema nuevo** (salto de línea forzado), como el comando
     * «Crear sistema» de MuseScore 4. `false` = el sistema lo decide la maquetación por ancho.
     */
    val forceSystemBreak: Boolean = false,
    /**
     * `true` si este compás **empieza una página nueva** en la exportación a PDF, como el
     * `Ctrl`+`Return` de MuseScore (`LayoutBreak::Type::PAGE`).
     *
     * Un salto de página **implica** salto de sistema: en MuseScore el `LayoutBreak` de página
     * también cierra la línea (`MeasureLayout` mira `isPageBreak()` antes que `isLineBreak()`), así
     * que la maquetación lo trata como [forceSystemBreak] **y además** marca el sistema como inicio
     * de hoja. En el editor de scroll continuo no cambia nada; solo afecta al PDF.
     */
    val forcePageBreak: Boolean = false,
    /**
     * `true` si este compás es una **anacrusa** (*pickup*): el compás **incompleto** con el que
     * arranca la pieza, como el `Measure::isAnacrusis()` de MuseScore
     * (`src/engraving/dom/measure.cpp:1968`).
     *
     * La anacrusa **no** se rellena con silencios hasta la duración nominal —eso es justo lo que la
     * distingue de un compás corto por error—, **no** se numera (MuseScore la llama compás `0`, así
     * que el siguiente es el `1`) y **no** la tocan las operaciones de «completar compás».
     *
     * MuseScore guarda la longitud **real** (`len`) y la compara con la del compás (`timesig`); aquí
     * la longitud real ya es **derivable** de las columnas (cada una lleva su figura), así que basta
     * con la marca. Solo tiene sentido en el **primer** compás: una anacrusa en medio sería un
     * compás irregular, que es otra cosa.
     */
    val isPickup: Boolean = false,
    /**
     * **Indicación de compás** que **empieza** en este compás, o `null` si hereda la del compás
     * anterior (y, en el primero, la del documento, [Tablature.time]).
     *
     * Es el «cambio de compás» de MuseScore: la paleta *Indicaciones de compás* inserta un
     * `TimeSig` **anclado a un compás**, y a partir de ahí todo se mide con él —la duración del
     * compás, la rejilla de columnas, el metrónomo y el agrupamiento de barras de unión—. Sin esto,
     * una pieza que pasa de 4/4 a 3/4 en la segunda parte no se podía escribir: el compás de 3/4
     * salía marcado como «corto» y «Completar compases» le añadía una negra de silencio.
     *
     * Se guarda la indicación **nueva**, no la vigente: así el documento no arrastra copias y
     * cambiar la del principio propaga el cambio a todos los compases que no lo declaren.
     */
    val time: ScoreTimeSignature? = null,
    /**
     * `true` si este compás es **irregular**: su duración **real** difiere a propósito de la nominal
     * de su indicación, como el `Measure::isIrregular()` de MuseScore
     * (`src/engraving/dom/measure.h:202`, `m_timesig != m_len`).
     *
     * Es la marca que distingue un compás **corto o largo a propósito** —un compás de 3 negras en
     * medio de una pieza 4/4, un compás de 5 para un efecto— de un compás **a medio escribir**. Sin
     * ella, «Completar compases» rellenaría el compás deliberado y la barra de estado lo marcaría en
     * rojo para siempre.
     *
     * En MuseScore la irregularidad se **deriva** de comparar `m_len` con `m_timesig`; aquí la
     * duración real ya es derivable de las columnas, así que la marca tiene que ser **explícita**:
     * si se derivara, todo compás a medio escribir sería «irregular» y nunca se avisaría de que le
     * faltan silencios.
     *
     * La **anacrusa** ([isPickup]) es un caso particular: un compás irregular que además es el
     * primero y se numera como `0`. Se guardan por separado porque la anacrusa solo puede ser corta
     * (pasarse sigue siendo un error) y la irregularidad admite las dos direcciones.
     */
    val isIrregular: Boolean = false
) {
    /** `true` si este compás **abre un sistema** por cualquier motivo (línea o página). */
    val startsSystem: Boolean get() = forceSystemBreak || forcePageBreak

    /**
     * `true` si el compás **no** debe medirse contra la duración nominal: la anacrusa y el compás
     * irregular son duraciones **elegidas**, no compases a medio escribir.
     */
    val hasFreeDuration: Boolean get() = isPickup || isIrregular

    /**
     * **Casillas de la volta** como lista ordenada: `[1]`, `[1, 2]`, `[2, 3]`… Vacía si el compás no
     * lleva casilla. Es la lectura de [ending] y [endingTo] en la forma en que MuseScore la guarda
     * (`Volta::endings()`), para que el resto del código no tenga que saber que aquí es un rango.
     */
    val endings: List<Int>
        get() {
            val first = ending ?: return emptyList()
            val last = endingTo ?: return listOf(first)
            return if (last <= first) listOf(first) else (first..last).toList()
        }

    /**
     * `true` si la volta **suena en la pasada** [pass] (1 = primera vuelta). Es el `hasEnding()` de
     * MuseScore (`src/engraving/dom/volta.cpp:169`): la casilla `1, 2.` suena en las dos primeras
     * vueltas, no solo en la primera.
     */
    fun hasEnding(pass: Int): Boolean = pass in endings

    /**
     * **Rótulo** de la volta tal como se escribe sobre el corchete: `1.`, `1, 2.`, `2, 3.`. `null` si
     * el compás no lleva casilla.
     *
     * Si el compás trae [endingText] se usa **ese** texto tal cual (el `beginText` de MuseScore); si
     * no, se deriva de las pasadas. Un texto en blanco se ignora: dejaría el corchete mudo sin que el
     * usuario lo pretendiera.
     */
    val endingLabel: String?
        get() {
            // Sin casilla no hay corchete, así que tampoco hay rótulo: un texto propio huérfano no se
            // dibuja en ninguna parte y devolverlo aquí haría creer que sí.
            if (endings.isEmpty()) return null
            return endingText?.takeIf { it.isNotBlank() }
                ?: endings.joinToString(", ", postfix = ".")
        }
}

/** Resolución de la rejilla, equivalente a la del editor de rasgueo. */
enum class TabResolution { QUARTER, EIGHTH, SIXTEENTH }

/** Origen de la tablatura: escrita por el usuario, generada desde una partitura o importada. */
enum class TabOrigin { CREATED, GENERATED_FROM_SCORE, IMPORTED_TEXT }

/**
 * Una cuerda (o curso) de un instrumento: altura al aire y si es un **curso doble** (laúd/bandurria).
 *
 * Los dos últimos campos son los de `instrString` de MuseScore (`stringdata.h:42-53`) que Canticora
 * implementa (D-49):
 *
 *  - [alwaysOpen] es el `open` de MuseScore, la columna «Always open» del diálogo «String data»: la
 *    cuerda **no está sobre el mástil** (tiorba, la 5.ª del banjo de cinco cuerdas), así que en una
 *    tablatura solo admite el traste `0`. `StringData::fret()` lo comprueba con
 *    `(fret > 0 && strg.open)` (`stringdata.cpp:524`).
 *  - [useFlat] es el `useFlat` de MuseScore: escribir la altura de esta cuerda con **bemoles** en vez
 *    de sostenidos.
 *
 * El `startFret` de MuseScore **no** se modela: solo existe para la 5.ª cuerda del banjo de cinco
 * cuerdas, MuseScore lo calcula solo en `StringData::configBanjo5thString()` (`stringdata.cpp:807-823`)
 * y no lo expone en el diálogo.
 */
data class TuningString(
    val pitch: ScorePitch,
    val isCourse: Boolean = false,
    val alwaysOpen: Boolean = false,
    val useFlat: Boolean = false
)

/**
 * Afinación de un instrumento, ordenada de **grave a aguda** (NO como se dibuja: en pantalla la más
 * aguda va ARRIBA). Ver [drawnStrings] para obtener el orden de dibujo.
 *
 * [frets] es el `m_frets` de `StringData` (`stringdata.h:100`): el **número de trastes** del
 * instrumento. No es decorativo — `StringData::fret()` rechaza los trastes fuera de `0..frets` y
 * `convertPitch()` lo usa para decidir si una altura cabe en el mástil. El valor por defecto, **19**,
 * es el de la guitarra en `instruments.xml` (`<frets>19</frets>`).
 */
data class Tuning(
    val name: String,
    val strings: List<TuningString>,
    val frets: Int = TabStringData.DEFAULT_FRETS
) {
    val stringCount: Int get() = strings.size

    /** Altura al aire de una cuerda por su número de lectura (1 = la más aguda). */
    fun pitchOf(stringNumber: Int): ScorePitch? =
        strings.getOrNull(strings.size - stringNumber)?.pitch

    /**
     * Cuerdas en **orden de dibujo** (la más aguda arriba), con su número de lectura.
     * Devuelve pares `(stringNumber, TuningString)` listos para pintar de arriba abajo.
     */
    fun drawnStrings(): List<Pair<Int, TuningString>> =
        strings.reversed().mapIndexed { index, string -> (index + 1) to string }

    /** Indica si el instrumento admite cejilla (los instrumentos sin trastes no). */
    val supportsCapo: Boolean get() = !isCourseInstrument()

    /** Indica si alguna cuerda es un curso doble (laúd/bandurria). */
    fun isCourseInstrument(): Boolean = strings.any { it.isCourse }

    companion object {
        // -----------------------------------------------------------------------------------------
        // Afinaciones de fábrica. Orden SIEMPRE de grave a aguda.
        // -----------------------------------------------------------------------------------------

        /** Guitarra española estándar (Mi grave → Mi agudo). */
        val GUITAR = Tuning(
            name = "Guitarra",
            strings = listOf(
                TuningString(ScorePitch('E', 0, 2)), // 6ª Mi2
                TuningString(ScorePitch('A', 0, 2)), // 5ª La2
                TuningString(ScorePitch('D', 0, 3)), // 4ª Re3
                TuningString(ScorePitch('G', 0, 3)), // 3ª Sol3
                TuningString(ScorePitch('B', 0, 3)), // 2ª Si3
                TuningString(ScorePitch('E', 0, 4))  // 1ª Mi4
            )
        )

        /** Guitarra en *drop D*: la 6ª baja de Mi2 a Re2. */
        val GUITAR_DROP_D = Tuning(
            name = "Guitarra (Drop D)",
            strings = listOf(
                TuningString(ScorePitch('D', 0, 2)),
                TuningString(ScorePitch('A', 0, 2)),
                TuningString(ScorePitch('D', 0, 3)),
                TuningString(ScorePitch('G', 0, 3)),
                TuningString(ScorePitch('B', 0, 3)),
                TuningString(ScorePitch('E', 0, 4))
            )
        )

        /** Bajo de 4 cuerdas (Mi1 → Sol2). */
        val BASS_4 = Tuning(
            name = "Bajo (4 cuerdas)",
            strings = listOf(
                TuningString(ScorePitch('E', 0, 1)),
                TuningString(ScorePitch('A', 0, 1)),
                TuningString(ScorePitch('D', 0, 2)),
                TuningString(ScorePitch('G', 0, 2))
            )
        )

        /** Bajo de 5 cuerdas (Si0 grave → Sol2). */
        val BASS_5 = Tuning(
            name = "Bajo (5 cuerdas)",
            strings = listOf(
                TuningString(ScorePitch('B', 0, 0)),
                TuningString(ScorePitch('E', 0, 1)),
                TuningString(ScorePitch('A', 0, 1)),
                TuningString(ScorePitch('D', 0, 2)),
                TuningString(ScorePitch('G', 0, 2))
            )
        )

        /** Bajo de 6 cuerdas (Si0 grave → Do3 agudo). */
        val BASS_6 = Tuning(
            name = "Bajo (6 cuerdas)",
            strings = listOf(
                TuningString(ScorePitch('B', 0, 0)),
                TuningString(ScorePitch('E', 0, 1)),
                TuningString(ScorePitch('A', 0, 1)),
                TuningString(ScorePitch('D', 0, 2)),
                TuningString(ScorePitch('G', 0, 2)),
                TuningString(ScorePitch('C', 0, 3))
            )
        )

        /** Ukelele soprano: Sol4 → Do4 → Mi4 → La4 (**reentrante**, la 1ª no es la más grave). */
        val UKULELE = Tuning(
            name = "Ukelele",
            strings = listOf(
                TuningString(ScorePitch('G', 0, 4)),
                TuningString(ScorePitch('C', 0, 4)),
                TuningString(ScorePitch('E', 0, 4)),
                TuningString(ScorePitch('A', 0, 4))
            )
        )

        /**
         * Laúd / bandurria de 6 **cursos dobles**. La afinación concreta es un **preset editable**
         * (varía por instrumento y escuela): aquí se parte de un temple por cuartas habitual
         * (La2–Re3–Sol3–Do4–Mi4–La4) que el usuario puede ajustar.
         */
        val LUTE = Tuning(
            name = "Laúd / Bandurria (6 cursos)",
            strings = listOf(
                TuningString(ScorePitch('A', 0, 2), isCourse = true),
                TuningString(ScorePitch('D', 0, 3), isCourse = true),
                TuningString(ScorePitch('G', 0, 3), isCourse = true),
                TuningString(ScorePitch('C', 0, 4), isCourse = true),
                TuningString(ScorePitch('E', 0, 4), isCourse = true),
                TuningString(ScorePitch('A', 0, 4), isCourse = true)
            )
        )

        /** Todas las afinaciones de fábrica, para el selector del editor. */
        val presets: List<Tuning> = listOf(GUITAR, GUITAR_DROP_D, BASS_4, BASS_5, BASS_6, UKULELE, LUTE)

        /** Afinación personalizada de `stringCount` cuerdas al aire. */
        fun custom(stringCount: Int): Tuning {
            val count = stringCount.coerceIn(1, 8)
            return Tuning(
                name = "Personalizada ($count)",
                strings = List(count) { TuningString(ScorePitch('E', 0, 2)) }
            )
        }
    }
}

/**
 * Tablatura completa. **Documento propio**, no una vista derivada de la melodía (T1).
 */
data class Tablature(
    val id: String,
    /**
     * **Canto al que pertenece** (clave foránea, obligatoria). Una tablatura **siempre** es de un
     * canto: no existe una tablatura suelta. La columna `numeric_tablatures.song_id` ya era `NOT
     * NULL`; antes el modelo de dominio la dejaba nullable «mientras se crea», pero eso permitía
     * estados imposibles (una tablatura sin dueño) que luego había que rescatar con `?:`. Se unifica
     * con la base de datos: **no nulo**.
     */
    val songId: String,
    val title: String,
    val instrument: String,
    val tuning: Tuning,
    val time: ScoreTimeSignature,
    val resolution: TabResolution = TabResolution.EIGHTH,
    val measures: List<TabMeasure> = emptyList(),
    val origin: TabOrigin = TabOrigin.CREATED,
    /** Tonalidad **en la que está escrita** (para la cejilla y el redigitado). */
    val writtenKey: ScoreKey? = null,
    /**
     * Sección del canto junto a la que se muestra esta tablatura (intercalado). `null` = **general**:
     * se muestra en el bloque de tablaturas al final. Permite tener la tablatura de la intro, la de
     * la estrofa, la del coro… y que aparezcan junto a su parte (ADR-017).
     */
    val sectionId: String? = null,
    /**
     * **Orden** de la tablatura dentro de su grupo (mismo [sectionId], o el bloque general). El
     * usuario puede subirla o bajarla para decidir cuál se lee primero. Se guarda en el propio
     * documento (viaja en el JSON), así que no necesita columna nueva en la base de datos.
     */
    val order: Int = 0,
    /**
     * Modo con el que la tablatura sigue la transposición del canto (`null` = automático según el
     * instrumento y el origen, vía `TabTranspositionPolicy.defaultMode`). Se persiste para que el
     * usuario pueda forzar cejilla, redigitado o bloqueo.
     */
    val transpositionMode: com.grafitto.laudate.domain.tablature.TabTranspositionMode? = null,
    /** Rótulo libre propio de la tablatura (p. ej. «Por los niños»), RF-127. */
    val label: String? = null,
    /** Momento litúrgico sugerido (p. ej. «Ofertorio»), RF-127. */
    val moment: String? = null,
    /** Nota libre del copista (año, indicaciones), RF-127. */
    val note: String? = null
) {
    val stringCount: Int get() = tuning.stringCount

    /**
     * Columnas **de referencia** por compás para la lectura, **derivadas** de [time] y [resolution].
     *
     * Ya **no** impone el tamaño de la rejilla: desde el modelo de columnas cada compás tiene las
     * columnas que el usuario ha escrito. Este número solo sirve de guía (cuántas columnas
     * «llenan» el compás) para la maquetación y para el PDF.
     */
    val slotsPerMeasure: Int
        get() {
            val slotsPerQuarter = when (resolution) {
                TabResolution.QUARTER -> 1.0
                TabResolution.EIGHTH -> 2.0
                TabResolution.SIXTEENTH -> 4.0
            }
            return kotlin.math.round(time.totalQuarterBeats * slotsPerQuarter).toInt().coerceAtLeast(1)
        }

    /**
     * `true` si la pieza **arranca con anacrusa**: el primer compás está marcado como *pickup*.
     *
     * Es la condición que MuseScore comprueba en `Measure::isAnacrusis()`
     * (`src/engraving/dom/measure.cpp:1968`): un compás **más corto** que su compás nominal y en la
     * **primera** posición. Aquí la posición ya la da el índice, así que basta con mirar la marca.
     */
    val startsWithPickup: Boolean get() = measures.firstOrNull()?.isPickup == true

    /**
     * **Número de compás** que se imprime para el compás [index] (1-based), como
     * `Measure::measureNumber()` de MuseScore.
     *
     * Con anacrusa el primer compás es el **0** y no se rotula, así que el segundo es el **1**: sin
     * este desplazamiento todos los números saldrían uno por encima y el director contaría un compás
     * de más. Devuelve `0` para la anacrusa, que es la señal de «no numerar».
     */
    fun measureNumber(index: Int): Int = index + 1 - if (startsWithPickup) 1 else 0

    /**
     * **Indicación de compás vigente** en el compás [index]: la del último compás que la declare, o
     * la del documento ([time]) si ninguno lo hace.
     *
     * Es la resolución que MuseScore hace al recorrer la partitura (`Measure::timesig()` devuelve la
     * del propio compás, que el motor ya ha propagado al crearlo); aquí se resuelve **al leer** para
     * que el modelo no tenga que mantener copias sincronizadas.
     */
    fun timeAt(index: Int): ScoreTimeSignature {
        for (i in index.coerceAtMost(measures.lastIndex) downTo 0) {
            measures[i].time?.let { return it }
        }
        return time
    }

    /**
     * **Duración nominal** del compás [index] en negras: la de su indicación vigente. Es la
     * `capacity` con la que se mide el compás, se reparte la rejilla y se cuenta el metrónomo.
     */
    fun capacityAt(index: Int): Double = timeAt(index).totalQuarterBeats

    /**
     * `true` si el compás [index] **cambia** la indicación de compás respecto del anterior, que es
     * cuando hay que **rotular** la fracción dentro del sistema (en el primer compás la rotula la
     * cabecera, así que no cuenta).
     */
    fun changesTimeAt(index: Int): Boolean =
        index > 0 && index <= measures.lastIndex && measures[index].time != null

    /** Notas (alturas con octava) de la tablatura que no son pausas, en orden temporal. */
    fun soundedPitches(): List<ScorePitch> = measures.flatMap { measure ->
        measure.slots.flatMap { slot ->
            slot.strings.mapNotNull { pluck ->
                // Una cuerda **apagada** (`X`) suena como percusión: no aporta altura.
                if (pluck.isMuted) return@mapNotNull null
                tuning.pitchOf(pluck.stringNumber)?.let { open ->
                    if (pluck.fret == 0) open
                    else ScorePitch.fromMidi(open.midi + pluck.fret)
                }
            }
        }
    }
}
