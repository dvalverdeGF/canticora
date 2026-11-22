package com.grafitto.laudate.domain.tablature

/**
 * **Geometría de las notas de adorno** (*grace notes*), tomada de MuseScore 4.
 *
 * En MuseScore la nota de adorno es un acorde propio que se cuelga del principal
 * (`Chord::graceNotesBefore()`) y se coloca **a la izquierda** de él:
 *
 * ```cpp
 * // src/engraving/rendering/score/chordlayout.cpp:621-635
 * double graceMag = ctx.conf().styleD(Sid::graceNoteMag);          // 0.7
 * double xl = -(item->spaceLw() + minNoteDistance);                // 0.35 sp
 * for (int i = nb - 1; i >= 0; --i) {
 *     Chord* c = graceNotesBefore.at(i);
 *     xl -= c->spaceRw();
 *     c->setPos(xl, 0);
 *     xl -= c->spaceLw() + minNoteDistance * graceMag;             // 0.245 sp
 * }
 * if (-xl > item->spaceLw()) item->setSpaceLw(item->spaceLw() - xl);
 * ```
 *
 * Es decir: el adorno ocupa su propio ancho **a 0,7** y se separa del siguiente
 * `minNoteDistance · graceNoteMag` = **0,245 espacios**. El acorde principal **no se mueve**: lo que
 * crece es su `spaceLw`, así que la columna se ensancha **hacia la izquierda**.
 *
 * **En tablatura** el adorno es una **cifra pequeña** en su cuerda, no un acorde con plica: la
 * tablatura es *stemless*, así que MuseScore **no dibuja la barra** de la acciaccatura
 * (`TLayout::layoutStemSlash` exige un asta, `tlayout.cpp:5378`). El tipo se guarda igual porque
 * cambia la **duración** al reproducir.
 */
object TabGraceGeometry {

    /**
     * **Tamaño** del adorno respecto al de la cifra normal: `graceNoteMag` de MuseScore
     * (`src/engraving/style/styledef.cpp:524`).
     */
    const val MAG: Float = 0.7f

    /**
     * **Aire mínimo** entre dos elementos de la misma voz: `minNoteDistance` de MuseScore
     * (`styledef.cpp:271`). Entre el adorno y la cifra principal se aplica **escalado** por [MAG].
     */
    const val MIN_DISTANCE_SPACES: Float = 0.35f

    /**
     * Media anchura de una **cifra de traste** en espacios. La cifra se pinta a `1,83` espacios de
     * alto (`TabCanvasTextStyles.fret`), y un dígito de una sans ocupa ~0,55 em de ancho → ~1,0
     * espacios. Se usa la mitad para separar centros.
     */
    const val FRET_HALF_SPACES: Float = 0.5f

    /** Media anchura de una cifra de adorno: la de la cifra normal escalada por [MAG]. */
    const val GRACE_HALF_SPACES: Float = FRET_HALF_SPACES * MAG

    /**
     * **Paso** entre dos adornos consecutivos (o entre el último adorno y la cifra principal), en
     * espacios: media cifra + el aire de MuseScore + media cifra de adorno.
     */
    const val STEP_SPACES: Float =
        FRET_HALF_SPACES + MIN_DISTANCE_SPACES * MAG + GRACE_HALF_SPACES

    /**
     * Ancho **extra** que la columna necesita para alojar [count] adornos a la izquierda de la cifra.
     *
     * La columna crece **hacia la izquierda** (como el `spaceLw` de MuseScore), así que quien dibuje
     * el contenido debe desplazarlo `extraWidth / 2` a la derecha para que la cifra quede centrada en
     * la parte de **duración** y no en la columna entera.
     */
    fun extraWidth(count: Int, staffSpace: Float): Float =
        if (count <= 0) 0f else count * STEP_SPACES * staffSpace

    /**
     * Desplazamiento horizontal del adorno [index] (0 = el **más cercano** a la cifra principal)
     * respecto al centro de la cifra principal, en espacios y **hacia la izquierda** (valor
     * positivo = a la izquierda).
     */
    fun offsetSpaces(index: Int): Float = STEP_SPACES * (index + 1)
}
