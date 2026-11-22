package com.grafitto.laudate.domain.model.celebration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationTypeTest {

    @Test
    fun onlyMisaIsMarkedAsMass() {
        assertTrue(CelebrationType.MISA.isMass)
        CelebrationType.entries.filter { it != CelebrationType.MISA }.forEach { type ->
            assertFalse("${type.displayName} no debería ser una misa", type.isMass)
        }
    }

    @Test
    fun eachTypeHasDistinctDefaultName() {
        // El nombre por defecto debe cambiar con el tipo; nunca quedarse "Misa Dominical".
        val defaults = CelebrationType.entries.map { it.defaultName }
        assertEquals(defaults.size, defaults.distinct().size)
        assertTrue(CelebrationType.VIGILIA.defaultName != "Misa Dominical")
        assertTrue(CelebrationType.RETIRO.defaultName != "Misa Dominical")
    }

    @Test
    fun nameFieldLabelsAreGrammaticallyCorrect() {
        // Evita "Nombre de la retiro", "Nombre de la Concierto / Ensayo".
        CelebrationType.entries.forEach { type ->
            val label = type.nameFieldLabel
            assertFalse("Etiqueta incorrecta: $label", label.contains("de la Retiro"))
            assertFalse("Etiqueta incorrecta: $label", label.contains("de la Concierto"))
            assertFalse("Etiqueta incorrecta: $label", label.contains("Nombre de la Vigilia"))
            assertTrue("Etiqueta debe empezar por 'Nombre del/ de la': $label", label.startsWith("Nombre"))
        }
    }

    @Test
    fun fromStoredTypeResolvesKnownTypes() {
        assertEquals(CelebrationType.MISA, CelebrationType.fromStoredType("Misa"))
        assertEquals(CelebrationType.VIGILIA, CelebrationType.fromStoredType("vigilia"))
        assertEquals(CelebrationType.RETIRO, CelebrationType.fromStoredType("Retiro"))
        assertEquals(CelebrationType.CONCIERTO_ENSAYO, CelebrationType.fromStoredType("Concierto / Ensayo"))
    }

    @Test
    fun fromStoredTypeDefaultsToMisaForUnknownValue() {
        assertEquals(CelebrationType.MISA, CelebrationType.fromStoredType("Desconocido"))
        assertEquals(CelebrationType.MISA, CelebrationType.fromStoredType(null))
    }

    @Test
    fun fromDisplayNameDefaultsToGenericEvent() {
        assertEquals(CelebrationType.VIGILIA, CelebrationType.fromDisplayName("Vigilia"))
        assertEquals(CelebrationType.EVENTO_GENERAL, CelebrationType.fromDisplayName("Cualquiera"))
    }

    @Test
    fun editorTitleUsesDisplayNameVerbatim() {
        assertEquals("Preparar Misa", CelebrationType.MISA.editorTitle(isNew = true))
        assertEquals("Editar Vigilia", CelebrationType.VIGILIA.editorTitle(isNew = false))
        assertEquals("Preparar Hora Santa", CelebrationType.HORA_SANTA.editorTitle(isNew = true))
    }

    @Test
    fun massAndEventUseDifferentSectionLabels() {
        assertEquals("Esquema de cantos", CelebrationType.MISA.songsSectionLabel)
        assertEquals("Cantos del evento", CelebrationType.VIGILIA.songsSectionLabel)
    }

    @Test
    fun defaultMomentLabelDiffersForMassAndEvents() {
        assertEquals("Entrada", CelebrationType.MISA.defaultMomentLabel)
        assertEquals("Sin asignar", CelebrationType.VIGILIA.defaultMomentLabel)
    }
}
