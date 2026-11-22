package com.grafitto.laudate.domain.model.liturgy

import com.grafitto.laudate.domain.platform.nowMillis

/**
 * Naturaleza de una categoría del catálogo.
 *
 * Permite no mezclar conceptos distintos: las partes de la misa son una cosa,
 * los tiempos litúrgicos otra, y las clasificaciones propias del repertorio
 * del coro (colecciones, estilos, advocaciones...) son categorías adicionales.
 */
enum class LiturgicalCategoryKind(
    val displayName: String,
    /** Indica si el tiempo litúrgico aplica como «cuándo» más que como «qué momento». */
    val isSeasonal: Boolean = false
) {
    /** Parte de la liturgia de la misa (Entrada, Gloria, Ofertorio, Comunión...). */
    MASS_MOMENT("Momento de la misa", isSeasonal = false),

    /** Tiempo litúrgico (Adviento, Navidad, Cuaresma, Pascua, Tiempo Ordinario...). */
    LITURGICAL_SEASON("Tiempo litúrgico", isSeasonal = true),

    /** Categoría propia del repertorio del coro (franciscanas, juveniles, marianas...). */
    ADDITIONAL("Categoría adicional", isSeasonal = false);

    companion object {
        fun fromName(name: String?): LiturgicalCategoryKind =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: ADDITIONAL
    }
}

/**
 * Representa una categoría o momento litúrgico en el Dominio.
 * Puede ser una categoría estándar del rito canónico o personalizada por el usuario.
 */
data class LiturgicalCategory(
    val id: String,
    val name: String,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isSystemStandard: Boolean = false,
    val kind: LiturgicalCategoryKind = LiturgicalCategoryKind.ADDITIONAL,
    val createdAt: Long = nowMillis()
)
