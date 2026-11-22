package com.grafitto.laudate.domain.transfer

/**
 * Política de resolución cuando algo que se importa **coincide** con algo que ya existe en la
 * biblioteca (un canto, un repertorio o un ritmo que te han compartido y que ya tienes).
 *
 * Es la misma decisión en los tres casos, por lo que se comparte aquí: el dominio no conoce a la UI
 * y la UI no necesita tres contratos distintos.
 */
enum class ImportConflictPolicy {
    /** Conserva lo local y descarta lo que llega. */
    SKIP,

    /** Sustituye lo local por la versión que llega (conservando su identidad). */
    OVERWRITE,

    /** Añade lo que llega como un elemento **nuevo**, sin tocar lo local. */
    KEEP_BOTH
}

/**
 * Un elemento entrante que ya existe localmente pero con **contenido distinto**.
 *
 * [key] lo identifica de forma estable entre la inspección y la aplicación (huella de contenido o
 * firma del ritmo), de modo que la decisión del usuario se pueda correlacionar.
 */
data class ImportConflict(
    val key: String,
    val title: String,
    /** Texto corto para la UI: p. ej. «Canto», «Ritmo» o el momento litúrgico. */
    val detail: String? = null
)
