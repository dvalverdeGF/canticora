package com.grafitto.laudate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material Design 3 · Static color · Baseline color scheme.
 *
 * Valores oficiales del esquema *baseline* de Material 3 (tonal palette).
 * Referencia: https://m3.material.io/styles/color/static/baseline
 *
 * No se inventan colores: se usan los tonos canónicos del esquema baseline.
 */
object M3Baseline {

    // ─── Primary · P (violeta, tono semilla del baseline) ────────────────────
    val Primary0 = Color(0xFF000000)
    val Primary10 = Color(0xFF21005D)
    val Primary20 = Color(0xFF381E72)
    val Primary30 = Color(0xFF4F378B)
    val Primary40 = Color(0xFF6750A4)
    val Primary80 = Color(0xFFD0BCFF)
    val Primary90 = Color(0xFFEADDFF)
    val Primary95 = Color(0xFFF6EDFF)
    val Primary99 = Color(0xFFFFFBFE)
    val Primary100 = Color(0xFFFFFFFF)

    // ─── Secondary · S (gris-violeta) ────────────────────────────────────────
    val Secondary10 = Color(0xFF1D192B)
    val Secondary20 = Color(0xFF332D41)
    val Secondary30 = Color(0xFF4A4458)
    val Secondary40 = Color(0xFF625B71)
    val Secondary80 = Color(0xFFCCC2DC)
    val Secondary90 = Color(0xFFE8DEF8)

    // ─── Tertiary · T (rosa/malva) ───────────────────────────────────────────
    val Tertiary10 = Color(0xFF31111D)
    val Tertiary20 = Color(0xFF492532)
    val Tertiary30 = Color(0xFF633B48)
    val Tertiary40 = Color(0xFF7D5260)
    val Tertiary80 = Color(0xFFEFB8C8)
    val Tertiary90 = Color(0xFFFFD8E4)

    // ─── Error · E ───────────────────────────────────────────────────────────
    val Error10 = Color(0xFF410E0B)
    val Error20 = Color(0xFF601410)
    val Error30 = Color(0xFF8C1D18)
    val Error40 = Color(0xFFB3261E)
    val Error80 = Color(0xFFF2B8B5)
    val Error90 = Color(0xFFF9DEDC)
    val Error100 = Color(0xFFFFFFFF)

    // ─── Neutral · N ─────────────────────────────────────────────────────────
    val Neutral4 = Color(0xFF0F0D13)
    val Neutral6 = Color(0xFF141218)
    val Neutral10 = Color(0xFF1D1B20)
    val Neutral12 = Color(0xFF211F26)
    val Neutral17 = Color(0xFF2B2930)
    val Neutral20 = Color(0xFF322F35)
    val Neutral22 = Color(0xFF36343B)
    val Neutral24 = Color(0xFF3B383E)
    val Neutral30 = Color(0xFF48464C)
    val Neutral40 = Color(0xFF605D64)
    val Neutral50 = Color(0xFF787579)
    val Neutral80 = Color(0xFFCAC4D0)
    val Neutral87 = Color(0xFFDED8E1)
    val Neutral90 = Color(0xFFE6E0E9)
    val Neutral92 = Color(0xFFECE6F0)
    val Neutral94 = Color(0xFFF3EDF7)
    val Neutral95 = Color(0xFFF5EFF7)
    val Neutral96 = Color(0xFFF7F2FA)
    val Neutral98 = Color(0xFFFEF7FF)
    val Neutral99 = Color(0xFFFFFBFE)
    val Neutral100 = Color(0xFFFFFFFF)

    // ─── Neutral variant · NV ────────────────────────────────────────────────
    val NeutralVariant10 = Color(0xFF1D1A22)
    val NeutralVariant20 = Color(0xFF322F37)
    val NeutralVariant30 = Color(0xFF49454F)
    val NeutralVariant40 = Color(0xFF605D66)
    val NeutralVariant50 = Color(0xFF79747E)
    val NeutralVariant60 = Color(0xFF938F99)
    val NeutralVariant80 = Color(0xFFCAC4D0)
    val NeutralVariant90 = Color(0xFFE7E0EC)
}

/**
 * Colores de acento semántico por tipo de sección musical.
 *
 * Se derivan **exclusivamente** de la paleta tonal baseline (Primary/Secondary/Tertiary/Neutral),
 * de modo que todo el conjunto permanece armónico y conforme al esquema Material 3.
 * Se ofrecen dos tonos por sección para garantizar contraste en tema claro y oscuro.
 */
object SectionColors {

    private data class Accent(val light: Color, val dark: Color)

    private val defaultAccent = Accent(M3Baseline.Primary40, M3Baseline.Primary80)

    private val accents: Map<String, Accent> = mapOf(
        "INTRO" to Accent(M3Baseline.Primary40, M3Baseline.Primary80),
        "VERSE" to Accent(M3Baseline.Secondary40, M3Baseline.Secondary80),
        "PRE_CHORUS" to Accent(M3Baseline.Primary30, M3Baseline.Primary80),
        "CHORUS" to Accent(M3Baseline.Tertiary40, M3Baseline.Tertiary80),
        "BRIDGE" to Accent(M3Baseline.Secondary30, M3Baseline.Secondary80),
        "INTERLUDE" to Accent(M3Baseline.NeutralVariant40, M3Baseline.NeutralVariant80),
        "OUTRO" to Accent(M3Baseline.Tertiary30, M3Baseline.Tertiary80),
        "ACCLAMATION" to Accent(M3Baseline.Primary20, M3Baseline.Primary80)
    )

    /** Paleta cíclica de acentos baseline para etiquetas libres (momentos litúrgicos, etc.). */
    private val palette: List<Accent> = listOf(
        Accent(M3Baseline.Primary40, M3Baseline.Primary80),
        Accent(M3Baseline.Tertiary40, M3Baseline.Tertiary80),
        Accent(M3Baseline.Secondary40, M3Baseline.Secondary80),
        Accent(M3Baseline.Primary30, M3Baseline.Primary80),
        Accent(M3Baseline.Tertiary30, M3Baseline.Tertiary80),
        Accent(M3Baseline.Secondary30, M3Baseline.Secondary80),
        Accent(M3Baseline.NeutralVariant40, M3Baseline.NeutralVariant80)
    )

    /** Resuelve el acento según el tema activo. */
    @Composable
    fun forSectionType(sectionTypeName: String): Color {
        val accent = accents[sectionTypeName.uppercase()] ?: defaultAccent
        return if (isSystemInDarkTheme()) accent.dark else accent.light
    }

    /**
     * Resuelve un acento estable a partir de una etiqueta libre (p. ej. "Entrada", "Ofertorio").
     * Usa la paleta baseline de forma determinista para mantener la coherencia cromática.
     */
    @Composable
    fun forLabel(label: String): Color {
        val accent = accents[label.uppercase()]
            ?: palette[(label.hashCode().let { if (it < 0) -it else it }) % palette.size]
        return if (isSystemInDarkTheme()) accent.dark else accent.light
    }

    /** Etiqueta legible en español para un tipo de sección. */
    fun labelFor(sectionTypeName: String): String {
        return when (sectionTypeName.uppercase()) {
            "INTRO" -> "Intro"
            "VERSE" -> "Estrofa"
            "PRE_CHORUS" -> "Pre-coro"
            "CHORUS" -> "Coro"
            "BRIDGE" -> "Puente"
            "INTERLUDE" -> "Interludio"
            "OUTRO" -> "Final"
            "ACCLAMATION" -> "Aclamación"
            else -> "Sección"
        }
    }

    /**
     * Etiqueta **abreviada** para el mapa de estructura del visor (`Int`, `Est`, `Coro`, `Puen`,
     * `Fin`…). El mapa es una línea de tiempo de badges diminutos: el nombre completo no cabe y
     * tampoco hace falta, porque el color ya identifica la parte.
     */
    fun shortLabelFor(sectionTypeName: String): String {
        return when (sectionTypeName.uppercase()) {
            "INTRO" -> "Int"
            "VERSE" -> "Est"
            "PRE_CHORUS" -> "Pre"
            "CHORUS" -> "Coro"
            "BRIDGE" -> "Puen"
            "INTERLUDE" -> "Intl"
            "OUTRO" -> "Fin"
            "ACCLAMATION" -> "Acl"
            else -> "Sec"
        }
    }
}
