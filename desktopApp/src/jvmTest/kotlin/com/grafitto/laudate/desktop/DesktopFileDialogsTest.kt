package com.grafitto.laudate.desktop

import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión de la exportación a PDF en escritorio.
 *
 * Los diálogos nativos (`FileDialog`) son **modales**: al mostrarse abren un bucle de eventos
 * anidado en el EDT. Si eso ocurre dentro de la continuación de una corrutina de Compose, el bucle
 * reentra el dispatcher y la app muere con `CoroutinesInternalError` («Fatal exception in coroutines
 * machinery for DispatchedContinuation»). Estos tests fijan el contrato que lo evita: el bloque del
 * diálogo se ejecuta en una tarea **nueva** del bucle de eventos, nunca inline en la tarea del
 * llamante.
 */
class DesktopFileDialogsTest {

    @Test
    fun `el dialogo se difiere a una tarea posterior del bucle de eventos`() = runBlocking {
        val order = mutableListOf<String>()

        val job = launch(Dispatchers.Swing) {
            order += "corrutina"
            // Tarea encolada ANTES de pedir el diálogo: si el diálogo se ejecutase inline (el bug),
            // aparecería antes que este marcador.
            SwingUtilities.invokeLater { order += "marcador" }
            runOnFreshEdtTask { order += "dialogo" }
            order += "fin"
        }
        job.join()

        assertEquals(listOf("corrutina", "marcador", "dialogo", "fin"), order)
    }

    @Test
    fun `el bloque se ejecuta en el hilo de la UI y devuelve su resultado`() = runBlocking {
        var ranOnEdt = false
        val result = runOnFreshEdtTask {
            ranOnEdt = SwingUtilities.isEventDispatchThread()
            "resultado"
        }

        assertTrue("el diálogo debe mostrarse en el EDT", ranOnEdt)
        assertEquals("resultado", result)
    }
}
