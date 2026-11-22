package com.grafitto.laudate.domain.tablature

/**
 * **Escala de notación** de la tablatura (roadmap **C.3**, arregla F-05).
 *
 * MuseScore no mide «a ojo»: tiene una escala de notación (`staffSpace`, el **espacio de
 * pentagrama**) de la que salen **todos** los tamaños. Aquí se replica esa idea: la maquetación de la
 * rejilla deriva de una **única unidad** (el espacio de pentagrama, en puntos), de modo que:
 *
 *  - la **separación entre cuerdas** es un espacio;
 *  - el **ancho base de una columna** (una negra) es `1.8` espacios, como en la notación grabada;
 *  - la **cabecera** del sistema (clave + compás) y el **aire** entre sistemas también son
 *    proporcionales.
 *
 * Con eso, cambiar la escala **reescala la tablatura entera** y, como cada sistema del motor de PDF es
 * una **fila** ([com.grafitto.laudate.domain.export.SongPdfLayout]), el alto de cada sistema cambia y
 * con él cuántos caben por página: la paginación **nunca parte un sistema por la mitad** ni separa un
 * compás de su barra (el sistema viaja entero como una fila).
 *
 * Kotlin puro y determinista: lo comparten el motor de PDF y sus pruebas.
 */
data class TabNotationScale(
    /**
     * **Un espacio de pentagrama** en puntos (la distancia entre dos cuerdas contiguas de la
     * tablatura). Es la unidad de medida de toda la notación, como en SMuFL.
     *
     * (D-85) Vale **5,0 pt**, no 6,0. El PDF de referencia (`salmo2.pdf`) tiene las cuerdas a
     * **7,44 pt** —medido línea a línea en `salmo2-lines.csv`—, y como van a **1,5 espacios**
     * (`<lineDistance>1.5</lineDistance>` del `.mscz`), el espacio vale `7,44 / 1,5 = 4,96 pt`.
     * Con 6,0 pt las cuerdas quedaban a **9 pt**: un **21 % más de aire** del que tiene la
     * partitura de referencia, que es exactamente lo que el usuario veía como «demasiado espacio
     * entre las líneas». El propio KDoc del proyecto ya citaba «espacio = 5,0 pt» al describir las
     * tallas de las cifras; el valor por defecto se había quedado en 6,0 y contradecía al
     * documento.
     */
    val staffSpacePt: Float = 5.0f,
    /**
     * Separación vertical (pt) entre dos sistemas consecutivos de la misma página.
     *
     * Por defecto es el **mínimo de MuseScore**, `styleDef(minSystemDistance, 8.5_sp)`
     * (`src/engraving/style/styledef.cpp`, línea 58), expresado en puntos: **8,5 espacios**. Antes
     * valía `8.0f` fijos, que con un espacio de 5 pt eran **1,6 espacios** y dejaba los sistemas
     * pegados (D-15).
     */
    val systemGapPt: Float =
        staffSpacePt * TabLayoutSettings.Companion.SYSTEM_GAP_BETWEEN_INK_SPACES
) {
    /** **Separación entre cuerdas** de la rejilla, en pt: **[STRING_SPACING_SPACES] espacios** (1,5),
     *  como el `<lineDistance>1.5</lineDistance>` del fichero de MuseScore. */
    val stringSpacingPt: Float get() = staffSpacePt * TabRhythmGeometry.STRING_SPACING_SPACES

    /**
     * **Ancho base de una negra**, en pt: **[TabSpacing.QUARTER_WIDTH_SPACES] espacios** (3,5), el
     * `DEFAULT_QUARTER_NOTE_SPACE` de MuseScore. Es el `cellWidth` que se pasa a la maquetación para
     * que el reparto de compases coincida con MuseScore.
     */
    val cellWidthPt: Float get() = TabSpacing.quarterWidth(staffSpacePt)

    /** Ancho reservado a la **cabecera** del sistema (clave y compás): `3,6` espacios. */
    val headerWidthPt: Float get() = staffSpacePt * 3.6f

    /**
     * **Medidas de maquetación** ([TabLayoutSettings]) derivadas de **esta** escala, para que el
     * pintor (editor y visor) use **exactamente** las mismas proporciones que el PDF.
     *
     * Es la pieza que cierra D-7: la proporción clave es **`ancho de negra / separación de cuerdas`**.
     * La de MuseScore es `3,5 / 1,5 = 2,333`; con el `cellWidth` fijo de antes (22 vs 20) salía
     * **`1,1`**, es decir la tablatura **2,1× más estrecha** de lo que debe. Aquí sale la razón buena
     * por construcción.
     *
     * [labelWidthPt] y [paddingPt] se pueden ajustar (el ancho de cabecera lo decide la plataforma),
     * pero por defecto salen de la propia escala para que **todo** case con el PDF.
     *
     * [fretNumberScale] es el zoom del número del atril (D-52): entra en la maquetación para que la
     * columna reserve el ancho de la cifra **ya escalada**, no el de la cifra normal.
     */
    fun toLayoutSettings(
        staff: TabStaffOptions = TabStaffOptions(),
        labelWidthPt: Float = headerWidthPt * 0.55f,
        paddingPt: Float = headerWidthPt * 0.18f,
        fretNumberScale: Float = 1f
    ): TabLayoutSettings = TabLayoutSettings(
        cellWidth = cellWidthPt,
        stringSpacing = stringSpacingPt,
        labelWidth = labelWidthPt,
        padding = paddingPt,
        systemGap = systemGapPt,
        fretNumberScale = fretNumberScale,
        staff = staff
    )

    companion object {
        /**
         * Escala por defecto: **5,0 pt de espacio**, que es el valor **medido del `salmo2.pdf`**
         * (cuerdas a 7,42 pt = 1,485 espacios). Con esto, una negra mide 19,1 pt, como MuseScore.
         */
        val DEFAULT = TabNotationScale(staffSpacePt = TabSpacing.SPACE_PT)

        /**
         * **Rango del zoom de la tablatura** que ofrecen los deslizadores de la UI (D-60).
         *
         * Vive en el Dominio —y no en cada pantalla— para que el editor, el visor de canción y la
         * persistencia no puedan discrepar: antes el mínimo estaba escrito a mano en tres sitios
         * (`0,7`) y el usuario se quedó sin poder encoger más la tablatura en el móvil.
         *
         * El mínimo baja a **`0,35`** (la mitad del anterior): a 5 pt de espacio son 1,75 pt entre
         * cuerdas, que sigue siendo legible en una pantalla de 6" y cabe una tablatura de 6 cuerdas
         * en vertical. El máximo se queda en `2,0` (el doble del tamaño de grabación).
         */
        const val MIN_ZOOM: Float = 0.35f

        /** Zoom máximo de la tablatura (200 % del tamaño de grabación). */
        const val MAX_ZOOM: Float = 2.0f

        /** Zoom de grabación: el tamaño con el que se mide la paridad con MuseScore. */
        const val DEFAULT_ZOOM: Float = 1.0f
    }
}
