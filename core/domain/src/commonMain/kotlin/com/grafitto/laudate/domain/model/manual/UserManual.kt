package com.grafitto.laudate.domain.model.manual

/**
 * Modelo de contenido del **manual de usuario** de Canticora (Kotlin puro, sin Android).
 *
 * El manual vive **en el propio código** (no en un asset) para no añadir dependencias ni
 * coste de APK, y se estructura en capítulos y secciones para poder construir un índice
 * navegable, ofrecer secciones plegables y buscar por texto.
 *
 * El contenido canónico (fuente de verdad) es [UserManualContent]; este archivo solo define
 * la forma de los datos.
 */
data class ManualChapter(
    /** Identificador estable y único (usado como clave de lista y para navegar desde el índice). */
    val id: String,
    val title: String,
    /** Icono temático del capítulo. */
    val icon: ManualChapterIcon,
    val sections: List<ManualSection>
)

/**
 * Icono temático de un capítulo del manual.
 *
 * Es una **identidad de contenido**, no un dibujo: el Dominio dice *qué* representa el icono y cada
 * plataforma elige con qué lo pinta (`Icons.Filled.*` en Compose). Antes se guardaba un emoji y la
 * web lo dibujaba como un hueco vacío, porque el navegador no siempre tiene fuente de emoji y el
 * lienzo de Compose/Wasm no la sustituye.
 */
enum class ManualChapterIcon {
    /** Primeros pasos: despegue. */
    START,

    /** El cancionero. */
    CATALOG,

    /** Buscar cantos. */
    SEARCH,

    /** Leer un canto. */
    READING,

    /** Transponer y el capo. */
    TRANSPOSE,

    /** Tablaturas. */
    TABLATURE,

    /** Patrones rítmicos. */
    RHYTHM,

    /** Misas, eventos y Modo Celebración. */
    CELEBRATION,

    /** Categorías y etiquetas. */
    CATEGORIES,

    /** Editar una canción. */
    EDITOR,

    /** Datos y copias de seguridad. */
    BACKUP,

    /** Importar contenido. */
    IMPORT,

    /** Apéndice de ChordPro. */
    CHORDPRO,

    /** Consejos y solución de problemas. */
    TIPS
}

/** Sección de un capítulo: un bloque plegable con párrafos, pasos y consejos. */
data class ManualSection(
    /** Identificador estable y único dentro del manual. */
    val id: String,
    val title: String,
    val blocks: List<ManualBlock>
) {
    /**
     * Texto plano de la sección, listo para el buscador interno del manual:
     * concatena el título, los párrafos, los pasos, las notas y la tabla.
     */
    val searchableText: String
        get() = buildString {
            append(title).append('\n')
            blocks.forEach { block ->
                when (block) {
                    is ManualBlock.Paragraph -> append(block.text).append('\n')
                    is ManualBlock.Steps -> block.items.forEach { append(it).append('\n') }
                    is ManualBlock.Note -> append(block.text).append('\n')
                    is ManualBlock.Reference -> {
                        block.caption?.let { append(it).append('\n') }
                        block.headers.forEach { append(it).append(' ') }
                        append('\n')
                        block.rows.forEach { row -> append(row.joinToString(" ")).append('\n') }
                    }
                }
            }
        }
}

/** Bloque de contenido de una sección. */
sealed interface ManualBlock {

    /** Párrafo de texto corriente. */
    data class Paragraph(val text: String) : ManualBlock

    /** Lista numerada de pasos (se muestra con numeración automática). */
    data class Steps(val items: List<String>) : ManualBlock

    /** Aviso o consejo breve, destacado visualmente. */
    data class Note(val text: String) : ManualBlock

    /**
     * Tabla de referencia (p. ej. directivas ChordPro).
     *
     * @param caption Título opcional de la tabla.
     * @param headers Cabeceras de columna; si está vacía, la tabla se dibuja sin cabecera.
     */
    data class Reference(
        val caption: String? = null,
        val headers: List<String> = emptyList(),
        val rows: List<List<String>>
    ) : ManualBlock
}
