package com.grafitto.laudate

/**
 * Convierte la pantalla actual en una **cadena estable** y viceversa.
 *
 * Existe por un fallo real: al **girar la tableta**, la app volvía al catálogo desde cualquier
 * pantalla —incluido el Modo Celebración—. La causa era que la pantalla actual se guardaba con un
 * `remember` a secas, que **no sobrevive a la recreación de la Activity** que Android hace al rotar
 * el dispositivo. `rememberSaveable` sí, pero necesita un `Saver`, y este es ese `Saver`: la pantalla
 * viaja como texto dentro del `Bundle` de estado.
 *
 * El formato es `tipo` o `tipo:id` (y `tipo:id:titulo` para el listado de tablaturas, que necesita el
 * título para su cabecera). Es deliberadamente **plano y tolerante**:
 *
 * - Un código **desconocido** cae al catálogo en vez de romper el arranque. Si algún día se elimina
 *   una pantalla, una sesión guardada con ella se recupera en el catálogo, no con un fallo.
 * - Un **identificador vacío** también cae a una pantalla válida: `SongDetail("")` o
 *   `LiveCelebration("")` dejarían la UI en un estado sin salida, así que se degradan a la lista.
 * - El título de la tablatura se parte con `limit = 3`, para que un canto con `:` en el nombre no
 *   rompa la reconstrucción.
 */
internal fun appScreenToRouteCode(screen: AppScreen): String = when (screen) {
    is AppScreen.Catalog -> "catalog"
    is AppScreen.SongDetail -> "song:${screen.songId}"
    is AppScreen.SongEditor -> "editor:${screen.songId.orEmpty()}"
    is AppScreen.CelebrationList -> "celebrations"
    is AppScreen.CelebrationEditor -> "celebrationEditor:${screen.celebrationId.orEmpty()}"
    is AppScreen.LiveCelebration -> "live:${screen.celebrationId}"
    is AppScreen.RhythmPatterns -> "rhythms"
    is AppScreen.RhythmPatternEditor -> "rhythmEditor"
    is AppScreen.Tablatures -> "tablatures:${screen.songId}:${screen.songTitle}"
    is AppScreen.TablatureEditor -> "tablatureEditor"
    is AppScreen.Backup -> "backup"
    is AppScreen.Import -> "import"
    is AppScreen.Manual -> "manual"
}

/** Reconstruye la pantalla desde [appScreenToRouteCode]. Un código desconocido cae al catálogo. */
internal fun appScreenFromRouteCode(code: String?): AppScreen {
    if (code.isNullOrBlank()) return AppScreen.Catalog
    val parts = code.split(':', limit = 3)
    val kind = parts[0]
    val a = parts.getOrNull(1).orEmpty()
    val b = parts.getOrNull(2).orEmpty()
    return when (kind) {
        "catalog" -> AppScreen.Catalog
        "song" -> a.takeIf { it.isNotBlank() }?.let(AppScreen::SongDetail) ?: AppScreen.Catalog
        "editor" -> AppScreen.SongEditor(a.takeIf { it.isNotBlank() })
        "celebrations" -> AppScreen.CelebrationList
        "celebrationEditor" -> AppScreen.CelebrationEditor(a.takeIf { it.isNotBlank() })
        "live" -> a.takeIf { it.isNotBlank() }?.let(AppScreen::LiveCelebration)
            ?: AppScreen.CelebrationList
        "rhythms" -> AppScreen.RhythmPatterns
        "rhythmEditor" -> AppScreen.RhythmPatternEditor
        "tablatures" -> a.takeIf { it.isNotBlank() }?.let { AppScreen.Tablatures(it, b) }
            ?: AppScreen.Catalog
        "tablatureEditor" -> AppScreen.TablatureEditor
        "backup" -> AppScreen.Backup
        "import" -> AppScreen.Import
        "manual" -> AppScreen.Manual
        else -> AppScreen.Catalog
    }
}
