package com.grafitto.laudate.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Instancia única de DataStore para las preferencias de lectura (no sensibles). */
private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "laudate_preferences"
)

/**
 * Implementación de [PreferencesRepository] sobre **DataStore Preferences**.
 *
 * Solo guarda preferencias **no sensibles** (nomenclatura, futuros ajustes de lectura):
 * nunca credenciales ni datos personales, por lo que no requiere cifrado
 * (ver `docs/SECURITY.md`).
 */
class PreferencesRepositoryImpl(context: Context) : PreferencesRepository {

    private val dataStore = context.applicationContext.preferencesDataStore

    override val notationSystem: Flow<NotationSystem> = dataStore.data.map { prefs ->
        prefs[KEY_NOTATION_SYSTEM]
            ?.let { stored -> NotationSystem.entries.firstOrNull { it.name == stored } }
            ?: DEFAULT_NOTATION_SYSTEM
    }

    override suspend fun setNotationSystem(system: NotationSystem) {
        dataStore.edit { prefs -> prefs[KEY_NOTATION_SYSTEM] = system.name }
    }

    override val songDisplayMode: Flow<SongDisplayMode> = dataStore.data.map { prefs ->
        prefs[KEY_SONG_DISPLAY_MODE]
            ?.let { stored -> SongDisplayMode.entries.firstOrNull { it.name == stored } }
            ?: DEFAULT_SONG_DISPLAY_MODE
    }

    override suspend fun setSongDisplayMode(mode: SongDisplayMode) {
        dataStore.edit { prefs -> prefs[KEY_SONG_DISPLAY_MODE] = mode.name }
    }

    override val tabDisplayPosition: Flow<TabDisplayPosition> = dataStore.data.map { prefs ->
        prefs[KEY_TAB_DISPLAY_POSITION]
            ?.let { stored -> TabDisplayPosition.entries.firstOrNull { it.name == stored } }
            ?: DEFAULT_TAB_DISPLAY_POSITION
    }

    override suspend fun setTabDisplayPosition(position: TabDisplayPosition) {
        dataStore.edit { prefs -> prefs[KEY_TAB_DISPLAY_POSITION] = position.name }
    }

    override val rhythmNotationMode: Flow<RhythmNotationMode> = dataStore.data.map { prefs ->
        prefs[KEY_RHYTHM_NOTATION_MODE]
            ?.let { stored -> RhythmNotationMode.entries.firstOrNull { it.name == stored } }
            ?: DEFAULT_RHYTHM_NOTATION_MODE
    }

    override suspend fun setRhythmNotationMode(mode: RhythmNotationMode) {
        dataStore.edit { prefs -> prefs[KEY_RHYTHM_NOTATION_MODE] = mode.name }
    }

    override val songFontSizeSp: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_SONG_FONT_SIZE_SP]?.let(::normalizeFontSize) ?: DEFAULT_SONG_FONT_SIZE_SP
    }

    override suspend fun setSongFontSizeSp(sizeSp: Float) {
        dataStore.edit { prefs -> prefs[KEY_SONG_FONT_SIZE_SP] = normalizeFontSize(sizeSp) }
    }

    override val tablatureScale: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TABLATURE_SCALE]?.let(::normalizeTablatureScale) ?: DEFAULT_TABLATURE_SCALE
    }

    override suspend fun setTablatureScale(scale: Float) {
        dataStore.edit { prefs -> prefs[KEY_TABLATURE_SCALE] = normalizeTablatureScale(scale) }
    }

    override val tablatureNumberScale: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TABLATURE_NUMBER_SCALE]?.let(::normalizeTablatureScale) ?: DEFAULT_TABLATURE_SCALE
    }

    override suspend fun setTablatureNumberScale(scale: Float) {
        dataStore.edit { prefs -> prefs[KEY_TABLATURE_NUMBER_SCALE] = normalizeTablatureScale(scale) }
    }

    override val metronomeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_METRONOME_ENABLED] ?: false
    }

    override suspend fun setMetronomeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_METRONOME_ENABLED] = enabled
            // (D-66) El interruptor histórico es el `enabled` del metrónomo configurable.
            prefs[KEY_TAB_METRONOME_ENABLED] = enabled
        }
    }

    override val tabMetronome: Flow<com.grafitto.laudate.domain.tablature.TabMetronome> =
        dataStore.data.map { prefs ->
            com.grafitto.laudate.domain.tablature.TabMetronome(
                enabled = prefs[KEY_TAB_METRONOME_ENABLED] ?: prefs[KEY_METRONOME_ENABLED] ?: false,
                volume = prefs[KEY_TAB_METRONOME_VOLUME]
                    ?: com.grafitto.laudate.domain.tablature.TabMetronome.DEFAULT_VOLUME,
                accentFirst = prefs[KEY_TAB_METRONOME_ACCENT] ?: true,
                subdivision = prefs[KEY_TAB_METRONOME_SUBDIVISION] ?: 1
            )
        }

    override suspend fun setTabMetronome(
        metronome: com.grafitto.laudate.domain.tablature.TabMetronome
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_TAB_METRONOME_ENABLED] = metronome.enabled
            prefs[KEY_TAB_METRONOME_VOLUME] = metronome.safeVolume
            prefs[KEY_TAB_METRONOME_ACCENT] = metronome.accentFirst
            prefs[KEY_TAB_METRONOME_SUBDIVISION] = metronome.safeSubdivision
            prefs[KEY_METRONOME_ENABLED] = metronome.enabled
        }
    }

    override val tabMixer: Flow<com.grafitto.laudate.domain.tablature.TabMixer> = dataStore.data.map { prefs ->
        com.grafitto.laudate.domain.tablature.TabMixer(
            volume = prefs[KEY_TAB_MIXER_VOLUME] ?: 1f,
            pan = prefs[KEY_TAB_MIXER_PAN] ?: 0f,
            muted = prefs[KEY_TAB_MIXER_MUTED] ?: false,
            solo = prefs[KEY_TAB_MIXER_SOLO] ?: false
        )
    }

    override suspend fun setTabMixer(mixer: com.grafitto.laudate.domain.tablature.TabMixer) {
        dataStore.edit { prefs ->
            prefs[KEY_TAB_MIXER_VOLUME] = mixer.safeVolume
            prefs[KEY_TAB_MIXER_PAN] = mixer.safePan
            prefs[KEY_TAB_MIXER_MUTED] = mixer.muted
            prefs[KEY_TAB_MIXER_SOLO] = mixer.solo
        }
    }

    override val selectedTablatureBySong: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        decodeTablatureSelection(prefs[KEY_SELECTED_TABLATURE])
    }

    override suspend fun setSelectedTablature(songId: String, tablatureId: String?) {
        dataStore.edit { prefs ->
            val current = decodeTablatureSelection(prefs[KEY_SELECTED_TABLATURE]).toMutableMap()
            if (tablatureId == null) current.remove(songId) else current[songId] = tablatureId
            prefs[KEY_SELECTED_TABLATURE] = encodeTablatureSelection(current)
        }
    }

    override val preferredTablatureInstrument: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_PREFERRED_TAB_INSTRUMENT]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setPreferredTablatureInstrument(instrument: String?) {
        dataStore.edit { prefs ->
            if (instrument.isNullOrBlank()) prefs.remove(KEY_PREFERRED_TAB_INSTRUMENT)
            else prefs[KEY_PREFERRED_TAB_INSTRUMENT] = instrument
        }
    }

    override val pdfIncludeChords: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PDF_INCLUDE_CHORDS] ?: DEFAULT_PDF_INCLUDE_CHORDS
    }

    override suspend fun setPdfIncludeChords(includeChords: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PDF_INCLUDE_CHORDS] = includeChords }
    }

    override val tablatureStaffOptions: Flow<com.grafitto.laudate.domain.tablature.TabStaffOptions> =
        dataStore.data.map { prefs ->
            com.grafitto.laudate.domain.tablature.TabStaffOptionsCodec.decode(
                prefs[KEY_TABLATURE_STAFF_OPTIONS]
            )
        }

    override suspend fun setTablatureStaffOptions(
        options: com.grafitto.laudate.domain.tablature.TabStaffOptions
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_TABLATURE_STAFF_OPTIONS] =
                com.grafitto.laudate.domain.tablature.TabStaffOptionsCodec.encode(options)
        }
    }

    private fun normalizeFontSize(sizeSp: Float): Float =
        if (sizeSp.isFinite()) sizeSp.coerceIn(MIN_SONG_FONT_SIZE_SP, MAX_SONG_FONT_SIZE_SP)
        else DEFAULT_SONG_FONT_SIZE_SP

    /** La escala de la tablatura se acota para que las cifras no se salgan ni desaparezcan. */
    private fun normalizeTablatureScale(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN_TABLATURE_SCALE, MAX_TABLATURE_SCALE)
        else DEFAULT_TABLATURE_SCALE

    /** Formato compacto `songId=tabId` por línea; los ids son UUID (sin `=`, `;` ni saltos). */
    private fun encodeTablatureSelection(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeTablatureSelection(raw: String?): Map<String, String> =
        raw.orEmpty().split(';')
            .mapNotNull { entry ->
                val parts = entry.split('=')
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
            }
            .toMap()

    private companion object {
        /** La notación latina es la preferida por el público litúrgico hispanohablante. */
        val DEFAULT_NOTATION_SYSTEM = NotationSystem.LATIN
        val KEY_NOTATION_SYSTEM = stringPreferencesKey("notation_system")

        val DEFAULT_SONG_DISPLAY_MODE = SongDisplayMode.CHORDS_ABOVE_TEXT
        val KEY_SONG_DISPLAY_MODE = stringPreferencesKey("song_display_mode")
        val DEFAULT_TAB_DISPLAY_POSITION = TabDisplayPosition.AFTER
        val KEY_TAB_DISPLAY_POSITION = stringPreferencesKey("tab_display_position")
        /** Tablatura elegida por canción, en formato `songId=tabId` separado por `;`. */
        val KEY_SELECTED_TABLATURE = stringPreferencesKey("selected_tablature_by_song")
        /** Instrumento preferido del usuario (nombre de la afinación), para elegir la tablatura. */
        val KEY_PREFERRED_TAB_INSTRUMENT = stringPreferencesKey("preferred_tablature_instrument")
        val DEFAULT_RHYTHM_NOTATION_MODE = RhythmNotationMode.ARROWS
        val KEY_RHYTHM_NOTATION_MODE = stringPreferencesKey("rhythm_notation_mode")
        const val DEFAULT_SONG_FONT_SIZE_SP = 16f
        const val MIN_SONG_FONT_SIZE_SP = 14f
        const val MAX_SONG_FONT_SIZE_SP = 32f
        val KEY_SONG_FONT_SIZE_SP = floatPreferencesKey("song_font_size_sp")

        /**
         * Escala base del dibujo de la tablatura y sus límites.
         *
         * (D-60) El mínimo baja de `0,7` a `0,35`: el usuario reportó que el 70 % seguía siendo
         * demasiado grande para leer una tablatura de 6 cuerdas en un móvil en vertical, y que
         * necesitaba poder encogerla más. El máximo no cambia.
         */
        const val DEFAULT_TABLATURE_SCALE = com.grafitto.laudate.domain.tablature.TabNotationScale.DEFAULT_ZOOM
        const val MIN_TABLATURE_SCALE = com.grafitto.laudate.domain.tablature.TabNotationScale.MIN_ZOOM
        const val MAX_TABLATURE_SCALE = com.grafitto.laudate.domain.tablature.TabNotationScale.MAX_ZOOM
        val KEY_TABLATURE_SCALE = floatPreferencesKey("tablature_scale")

        /** Escala solo de las cifras de traste, independiente de la rejilla. */
        val KEY_TABLATURE_NUMBER_SCALE = floatPreferencesKey("tablature_number_scale")

        /** Metrónomo del editor de tablaturas (D-23): apagado por defecto, como en MuseScore. */
        val KEY_METRONOME_ENABLED = booleanPreferencesKey("metronome_enabled")

        // Metrónomo configurable (D-66): volumen propio, acento y subdivisión.
        val KEY_TAB_METRONOME_ENABLED = booleanPreferencesKey("tab_metronome_enabled")
        val KEY_TAB_METRONOME_VOLUME = floatPreferencesKey("tab_metronome_volume")
        val KEY_TAB_METRONOME_ACCENT = booleanPreferencesKey("tab_metronome_accent")
        val KEY_TAB_METRONOME_SUBDIVISION = intPreferencesKey("tab_metronome_subdivision")

        // Mezclador de la reproducción de tablaturas (D-29).
        val KEY_TAB_MIXER_VOLUME = floatPreferencesKey("tab_mixer_volume")
        val KEY_TAB_MIXER_PAN = floatPreferencesKey("tab_mixer_pan")
        val KEY_TAB_MIXER_MUTED = booleanPreferencesKey("tab_mixer_muted")
        val KEY_TAB_MIXER_SOLO = booleanPreferencesKey("tab_mixer_solo")

        /** Por defecto el PDF sale con acordes: es lo que necesita quien acompaña. */
        const val DEFAULT_PDF_INCLUDE_CHORDS = true
        val KEY_PDF_INCLUDE_CHORDS = booleanPreferencesKey("pdf_include_chords")

        /**
         * Ajustes del pentagrama de tablatura (D-48), en el formato de
         * [com.grafitto.laudate.domain.tablature.TabStaffOptionsCodec].
         */
        val KEY_TABLATURE_STAFF_OPTIONS = stringPreferencesKey("tablature_staff_options")
    }
}
