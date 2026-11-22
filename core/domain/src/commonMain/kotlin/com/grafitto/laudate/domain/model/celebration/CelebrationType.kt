package com.grafitto.laudate.domain.model.celebration

/**
 * Tipos de celebración o evento soportados por Canticora.
 *
 * Encapsula la semántica del tipo para que la UI no tenga que construir textos
 * ni decidir si el contexto es litúrgico (misa) o genérico (evento).
 */
enum class CelebrationType(
    val displayName: String,
    /** Indica si el contexto es una misa (con momentos litúrgicos predefinidos). */
    val isMass: Boolean,
    /** Nombre sugerido del campo cuando se crea uno nuevo de este tipo. */
    val defaultName: String,
    /** Etiqueta del campo de nombre, gramaticalmente correcta para cada tipo. */
    val nameFieldLabel: String,
    /** Texto de ejemplo para el campo de nombre. */
    val namePlaceholder: String
) {
    MISA(
        displayName = "Misa",
        isMass = true,
        defaultName = "Misa Dominical",
        nameFieldLabel = "Nombre de la misa",
        namePlaceholder = "Ej. Misa Dominical de 12:00"
    ),
    HORA_SANTA(
        displayName = "Hora Santa",
        isMass = false,
        defaultName = "Hora Santa",
        nameFieldLabel = "Nombre de la Hora Santa",
        namePlaceholder = "Ej. Hora Santa de Jueves Santo"
    ),
    RETIRO(
        displayName = "Retiro",
        isMass = false,
        defaultName = "Retiro",
        nameFieldLabel = "Nombre del retiro",
        namePlaceholder = "Ej. Retiro de Cuaresma"
    ),
    VIGILIA(
        displayName = "Vigilia",
        isMass = false,
        defaultName = "Vigilia",
        nameFieldLabel = "Nombre de la vigilia",
        namePlaceholder = "Ej. Vigilia de Pentecostés"
    ),
    CONCIERTO_ENSAYO(
        displayName = "Concierto / Ensayo",
        isMass = false,
        defaultName = "Concierto / Ensayo",
        nameFieldLabel = "Nombre del concierto o ensayo",
        namePlaceholder = "Ej. Concierto de Navidad"
    ),
    EVENTO_GENERAL(
        displayName = "Evento General",
        isMass = false,
        defaultName = "Evento",
        nameFieldLabel = "Nombre del evento",
        namePlaceholder = "Ej. Encuentro de coros"
    );

    /** Plantilla del título de la barra superior (Preparar / Editar). */
    fun editorTitle(isNew: Boolean): String {
        val verb = if (isNew) "Preparar" else "Editar"
        return "$verb $displayName"
    }

    /** Rótulo del encabezado del listado de cantos. */
    val songsSectionLabel: String
        get() = if (isMass) "Esquema de cantos" else "Cantos del evento"

    /** Rótulo de la etiqueta por defecto de un canto añadido sin momento litúrgico. */
    val defaultMomentLabel: String
        get() = if (isMass) "Entrada" else "Sin asignar"

    companion object {
        /** Opciones ofrecidas al usuario en el selector de tipo. */
        val selectable: List<CelebrationType> = entries.toList()

        /** Resuelve un tipo a partir de su nombre visible, con tolerancia a mayúsculas. */
        fun fromDisplayName(name: String): CelebrationType =
            entries.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) } ?: EVENTO_GENERAL

        /** Resuelve un tipo desde el valor persistido (nombre visible) con valor por defecto Misa. */
        fun fromStoredType(stored: String?): CelebrationType =
            entries.firstOrNull { it.displayName.equals(stored?.trim(), ignoreCase = true) } ?: MISA
    }
}
