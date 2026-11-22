package com.grafitto.laudate.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Diálogos nativos de fichero para escritorio, sobre AWT (`FileDialog`).
 *
 * Son el equivalente de SAF (`CreateDocument`/`OpenDocument`) en Android: sustituyen al selector
 * del sistema. Ver ADR-013.
 *
 * ### Por qué son `suspend` (y por qué no se puede volver a hacerlos bloqueantes)
 *
 * `FileDialog` es **modal**: al mostrarse abre un bucle de eventos **anidado** en el hilo de la UI
 * (EDT). Si ese bucle anidado corre *dentro* de la continuación de una corrutina de Compose —el
 * colector de un `LaunchedEffect` o el `onClick` de un botón, que se despachan sobre el dispatcher
 * de Compose—, el bucle reentra el dispatcher y la maquinaria de corrutinas revienta con
 * `CoroutinesInternalError` («Fatal exception in coroutines machinery for DispatchedContinuation»).
 *
 * Por eso el diálogo se lanza **siempre** desde una tarea nueva del bucle de eventos
 * ([SwingUtilities.invokeLater]): la corrutina que lo invoca se suspende y devuelve el control al
 * EDT *antes* de que el diálogo se abra, de modo que el bucle anidado nunca se ejecuta dentro de una
 * continuación. Las llamadas deben hacerse desde un contexto `suspend` (colector de efectos o
 * `scope.launch { }`).
 */
object DesktopFileDialogs {

    /** Pide un fichero existente. Devuelve `null` si el usuario cancela. */
    suspend fun openFile(title: String, parent: Frame? = null): File? = runOnFreshEdtTask {
        val dialog = FileDialog(parent, title, FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory ?: return@runOnFreshEdtTask null
        val name = dialog.file ?: return@runOnFreshEdtTask null
        File(dir, name)
    }

    /** Pide uno o varios ficheros existentes. Devuelve una lista vacía si el usuario cancela. */
    suspend fun openFiles(title: String, parent: Frame? = null): List<File> = runOnFreshEdtTask {
        val dialog = FileDialog(parent, title, FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.isVisible = true
        val dir = dialog.directory ?: return@runOnFreshEdtTask emptyList()
        dialog.files.toList().map { File(dir, it.name) }
    }

    /** Pide un destino para guardar. Devuelve `null` si el usuario cancela. */
    suspend fun saveFile(title: String, suggestedName: String, parent: Frame? = null): File? = runOnFreshEdtTask {
        val dialog = FileDialog(parent, title, FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val dir = dialog.directory ?: return@runOnFreshEdtTask null
        val name = dialog.file ?: return@runOnFreshEdtTask null
        File(dir, name)
    }
}

/**
 * Ejecuta [block] —que muestra un diálogo modal— en una tarea **nueva** del bucle de eventos del
 * EDT, nunca dentro de la continuación actual. Ver la nota de [DesktopFileDialogs].
 *
 * Es `internal` para poder verificar en tests que el bloque no se ejecuta inline en la tarea del
 * llamante (que es justo lo que reventaba la maquinaria de corrutinas).
 */
internal suspend fun <T> runOnFreshEdtTask(block: () -> T): T = suspendCancellableCoroutine { cont ->
    SwingUtilities.invokeLater {
        if (cont.isActive) cont.resume(block())
    }
}
