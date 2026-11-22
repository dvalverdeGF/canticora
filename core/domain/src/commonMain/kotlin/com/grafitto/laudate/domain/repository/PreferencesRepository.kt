package com.grafitto.laudate.domain.repository

import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import kotlinx.coroutines.flow.Flow

/**
 * Preferencias de lectura del usuario que **trascienden a una pantalla concreta**.
 *
 * A diferencia de los ajustes de una sola vista, la nomenclatura de acordes
 * (`Do-Re-Mi` / `C-D-E`) es una preferencia **global**: el usuario la cambia donde
 * quiera (visor, modo celebración, editor de canción) y debe conservarse en toda la
 * aplicación hasta que vuelva a modificarla.
 */
interface PreferencesRepository {

    /** Nomenclatura activa; se emite de nuevo cada vez que el usuario la cambia. */
    val notationSystem: Flow<NotationSystem>

    /** Fija la nomenclatura activa en toda la aplicación. */
    suspend fun setNotationSystem(system: NotationSystem)

    /** Modo de representación de la letra y los acordes en la vista de canción. */
    val songDisplayMode: Flow<SongDisplayMode>

    /** Fija el modo de representación de la canción para toda la aplicación. */
    suspend fun setSongDisplayMode(mode: SongDisplayMode)

    /**
     * Posición de la tablatura respecto a la letra cuando el modo muestra **ambas**
     * (arriba / abajo / a la derecha). Es una preferencia de lectura persistente.
     */
    val tabDisplayPosition: Flow<TabDisplayPosition>

    /** Fija la posición de la tablatura en la vista mixta. */
    suspend fun setTabDisplayPosition(position: TabDisplayPosition)

    /** Notación usada para representar el ritmo en la vista de canción. */
    val rhythmNotationMode: Flow<RhythmNotationMode>

    /** Fija la notación del ritmo para toda la aplicación. */
    suspend fun setRhythmNotationMode(mode: RhythmNotationMode)

    /** Tamaño de letra elegido para leer canciones, en sp. */
    val songFontSizeSp: Flow<Float>

    /** Fija el tamaño de letra de la vista de canción, en sp. */
    suspend fun setSongFontSizeSp(sizeSp: Float)

    /**
     * Escala del dibujo de las **tablaturas** (1.0 = tamaño base; mayor = cifras y rejilla más
     * grandes). Es una preferencia global de lectura para que las cifras de traste puedan
     * agrandarse en atril.
     */
    val tablatureScale: Flow<Float>

    /** Fija la escala del dibujo de las tablaturas. */
    suspend fun setTablatureScale(scale: Float)

    /**
     * Escala **solo de las cifras de traste** de la tablatura (1.0 = tamaño base). Es independiente
     * de [tablatureScale]: permite agrandar el número sin agrandar la rejilla, o al revés, para
     * leerla de lejos en el atril.
     */
    val tablatureNumberScale: Flow<Float>

    /** Fija la escala de las cifras de traste de la tablatura. */
    suspend fun setTablatureNumberScale(scale: Float)

    /**
     * **Ajustes del pentagrama de tablatura** ([com.grafitto.laudate.domain.tablature.TabStaffOptions]):
     * cifras, líneas, pentagrama rítmico, blanca, cifra de la nota ligada y cabecera.
     *
     * Es una preferencia **global** y persistente: el usuario los elige una vez en el panel del
     * pentagrama del editor y deben valer para **todo** lo que dibuje tablatura —el editor, el visor
     * de canción y el **PDF** exportado—, no solo para la sesión en curso.
     */
    val tablatureStaffOptions: Flow<com.grafitto.laudate.domain.tablature.TabStaffOptions>

    /** Guarda los ajustes del pentagrama de tablatura. */
    suspend fun setTablatureStaffOptions(
        options: com.grafitto.laudate.domain.tablature.TabStaffOptions
    )

    /**
     * **Metrónomo** encendido en el editor de tablaturas (D-23). Es una preferencia persistente: quien
     * ensaya con clic lo quiere encendido la próxima vez que abra un canto.
     */
    val metronomeEnabled: Flow<Boolean>

    /** Enciende o apaga el metrónomo del editor de tablaturas. */
    suspend fun setMetronomeEnabled(enabled: Boolean)

    /**
     * **Metrónomo configurable** (D-66): encendido, volumen propio, acento del primer tiempo y
     * subdivisión de la pulsación. Sustituye al antiguo interruptor [metronomeEnabled], que se
     * conserva como atajo de compatibilidad (`enabled` del modelo).
     */
    val tabMetronome: Flow<com.grafitto.laudate.domain.tablature.TabMetronome>

    /** Guarda la configuración del metrónomo. */
    suspend fun setTabMetronome(metronome: com.grafitto.laudate.domain.tablature.TabMetronome)

    /**
     * **Mezclador de la reproducción** de tablaturas (D-29): volumen, balance, silencio y solo. Es una
     * preferencia persistente porque el volumen del atril depende del sitio (un aula, una iglesia, un
     * ensayo con auriculares) y no del canto.
     */
    val tabMixer: Flow<com.grafitto.laudate.domain.tablature.TabMixer>

    /** Guarda el mezclador de la reproducción de tablaturas. */
    suspend fun setTabMixer(mixer: com.grafitto.laudate.domain.tablature.TabMixer)

    /**
     * Tablatura elegida para **mostrar** en cada canción cuando tiene varias (mapa `songId → id`).
     * Es una preferencia persistente: al volver a abrir el canto se recuerda cuál se estaba viendo.
     */
    val selectedTablatureBySong: Flow<Map<String, String>>

    /** Recuerda (o borra, con `null`) la tablatura elegida para un canción. */
    suspend fun setSelectedTablature(songId: String, tablatureId: String?)

    /**
     * **Instrumento preferido** del usuario (`nombre` de la afinación, p. ej. «Guitarra»), el que
     * toca por defecto. En el visor de canción se elige la tablatura de ese instrumento cuando el
     * canto tiene varias, sin tener que seleccionarla cada vez. `null` = sin preferencia (se usa la
     * primera tablatura).
     */
    val preferredTablatureInstrument: Flow<String?>

    /** Fija el instrumento preferido (o lo borra con `null`). */
    suspend fun setPreferredTablatureInstrument(instrument: String?)

    /**
     * Preferencia de exportación a PDF: `true` incluye los acordes sobre la letra.
     * Se recuerda entre sesiones para no volver a preguntar lo mismo cada vez.
     */
    val pdfIncludeChords: Flow<Boolean>

    /** Fija la preferencia de exportación a PDF (con o sin acordes). */
    suspend fun setPdfIncludeChords(includeChords: Boolean)
}
