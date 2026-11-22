package com.grafitto.laudate.web.data

import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.tablature.TabMetronome
import com.grafitto.laudate.domain.tablature.TabMixer
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import com.grafitto.laudate.domain.tablature.TabStaffOptionsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencias de Canticora en la **web** — ADR-023.
 *
 * Es el mismo contrato que `PreferencesRepositoryImpl` de Android (DataStore) y de escritorio
 * (`.properties`), pero sobre `localStorage`. Los valores por defecto y las validaciones son
 * idénticos a propósito: si se cambia un rango allí hay que cambiarlo aquí, y eso es justo lo que
 * el módulo común de ViewModels espera.
 */
class WebPreferencesRepository : PreferencesRepository {

    private val _notationSystem = MutableStateFlow(loadEnum(KEY_NOTATION, NotationSystem.entries, NotationSystem.LATIN))
    override val notationSystem: Flow<NotationSystem> = _notationSystem.asStateFlow()

    private val _songDisplayMode = MutableStateFlow(
        loadEnum(KEY_DISPLAY_MODE, SongDisplayMode.entries, SongDisplayMode.CHORDS_ABOVE_TEXT)
    )
    override val songDisplayMode: Flow<SongDisplayMode> = _songDisplayMode.asStateFlow()

    private val _tabDisplayPosition = MutableStateFlow(
        loadEnum(KEY_TAB_POSITION, TabDisplayPosition.entries, TabDisplayPosition.AFTER)
    )
    override val tabDisplayPosition: Flow<TabDisplayPosition> = _tabDisplayPosition.asStateFlow()

    private val _rhythmNotationMode = MutableStateFlow(
        loadEnum(KEY_RHYTHM_NOTATION, RhythmNotationMode.entries, RhythmNotationMode.ARROWS)
    )
    override val rhythmNotationMode: Flow<RhythmNotationMode> = _rhythmNotationMode.asStateFlow()

    private val _songFontSizeSp = MutableStateFlow(
        WebStorage.read(KEY_FONT_SIZE)?.toFloatOrNull()?.let(::normalizeFontSize)
            ?: DEFAULT_SONG_FONT_SIZE_SP
    )
    override val songFontSizeSp: Flow<Float> = _songFontSizeSp.asStateFlow()

    private val _tablatureScale = MutableStateFlow(
        WebStorage.read(KEY_TAB_SCALE)?.toFloatOrNull()?.let(::normalizeTablatureScale)
            ?: TabNotationScale.DEFAULT_ZOOM
    )
    override val tablatureScale: Flow<Float> = _tablatureScale.asStateFlow()

    private val _tablatureNumberScale = MutableStateFlow(
        WebStorage.read(KEY_TAB_NUMBER_SCALE)?.toFloatOrNull()?.let(::normalizeTablatureScale)
            ?: TabNotationScale.DEFAULT_ZOOM
    )
    override val tablatureNumberScale: Flow<Float> = _tablatureNumberScale.asStateFlow()

    private val _tablatureStaffOptions = MutableStateFlow(
        TabStaffOptionsCodec.decode(WebStorage.read(KEY_TAB_STAFF_OPTIONS))
    )
    override val tablatureStaffOptions: Flow<TabStaffOptions> =
        _tablatureStaffOptions.asStateFlow()

    private val _metronomeEnabled = MutableStateFlow(loadBoolean(KEY_METRONOME_ENABLED, false))
    override val metronomeEnabled: Flow<Boolean> = _metronomeEnabled.asStateFlow()

    private val _tabMetronome = MutableStateFlow(
        TabMetronome(
            enabled = loadBoolean(
                KEY_TAB_METRONOME_ENABLED,
                loadBoolean(KEY_METRONOME_ENABLED, false)
            ),
            volume = WebStorage.read(KEY_TAB_METRONOME_VOLUME)?.toFloatOrNull()
                ?: TabMetronome.DEFAULT_VOLUME,
            accentFirst = loadBoolean(KEY_TAB_METRONOME_ACCENT, true),
            subdivision = WebStorage.read(KEY_TAB_METRONOME_SUBDIVISION)?.toIntOrNull() ?: 1
        )
    )
    override val tabMetronome: Flow<TabMetronome> = _tabMetronome.asStateFlow()

    private val _tabMixer = MutableStateFlow(
        TabMixer(
            volume = WebStorage.read(KEY_TAB_MIXER_VOLUME)?.toFloatOrNull() ?: 1f,
            pan = WebStorage.read(KEY_TAB_MIXER_PAN)?.toFloatOrNull() ?: 0f,
            muted = loadBoolean(KEY_TAB_MIXER_MUTED, false),
            solo = loadBoolean(KEY_TAB_MIXER_SOLO, false)
        )
    )
    override val tabMixer: Flow<TabMixer> = _tabMixer.asStateFlow()

    private val _selectedTablatureBySong = MutableStateFlow(loadTablatureSelection())
    override val selectedTablatureBySong: Flow<Map<String, String>> =
        _selectedTablatureBySong.asStateFlow()

    private val _preferredTablatureInstrument =
        MutableStateFlow(WebStorage.read(KEY_PREFERRED_INSTRUMENT))
    override val preferredTablatureInstrument: Flow<String?> =
        _preferredTablatureInstrument.asStateFlow()

    private val _pdfIncludeChords = MutableStateFlow(loadBoolean(KEY_PDF_INCLUDE_CHORDS, true))
    override val pdfIncludeChords: Flow<Boolean> = _pdfIncludeChords.asStateFlow()

    override suspend fun setNotationSystem(system: NotationSystem) {
        _notationSystem.value = system
        WebStorage.write(KEY_NOTATION, system.name)
    }

    override suspend fun setSongDisplayMode(mode: SongDisplayMode) {
        _songDisplayMode.value = mode
        WebStorage.write(KEY_DISPLAY_MODE, mode.name)
    }

    override suspend fun setTabDisplayPosition(position: TabDisplayPosition) {
        _tabDisplayPosition.value = position
        WebStorage.write(KEY_TAB_POSITION, position.name)
    }

    override suspend fun setRhythmNotationMode(mode: RhythmNotationMode) {
        _rhythmNotationMode.value = mode
        WebStorage.write(KEY_RHYTHM_NOTATION, mode.name)
    }

    override suspend fun setSongFontSizeSp(sizeSp: Float) {
        val normalized = normalizeFontSize(sizeSp)
        _songFontSizeSp.value = normalized
        WebStorage.write(KEY_FONT_SIZE, normalized.toString())
    }

    override suspend fun setTablatureScale(scale: Float) {
        val normalized = normalizeTablatureScale(scale)
        _tablatureScale.value = normalized
        WebStorage.write(KEY_TAB_SCALE, normalized.toString())
    }

    override suspend fun setTablatureNumberScale(scale: Float) {
        val normalized = normalizeTablatureScale(scale)
        _tablatureNumberScale.value = normalized
        WebStorage.write(KEY_TAB_NUMBER_SCALE, normalized.toString())
    }

    override suspend fun setTablatureStaffOptions(options: TabStaffOptions) {
        _tablatureStaffOptions.value = options
        WebStorage.write(KEY_TAB_STAFF_OPTIONS, TabStaffOptionsCodec.encode(options))
    }

    override suspend fun setMetronomeEnabled(enabled: Boolean) {
        _metronomeEnabled.value = enabled
        WebStorage.write(KEY_METRONOME_ENABLED, enabled.toString())
        // Mismo contrato que Android/escritorio: el interruptor histórico manda sobre el `enabled`
        // del metrónomo configurable.
        _tabMetronome.value = _tabMetronome.value.copy(enabled = enabled)
        WebStorage.write(KEY_TAB_METRONOME_ENABLED, enabled.toString())
    }

    override suspend fun setTabMetronome(metronome: TabMetronome) {
        _tabMetronome.value = metronome
        _metronomeEnabled.value = metronome.enabled
        WebStorage.write(KEY_TAB_METRONOME_ENABLED, metronome.enabled.toString())
        WebStorage.write(KEY_TAB_METRONOME_VOLUME, metronome.safeVolume.toString())
        WebStorage.write(KEY_TAB_METRONOME_ACCENT, metronome.accentFirst.toString())
        WebStorage.write(KEY_TAB_METRONOME_SUBDIVISION, metronome.safeSubdivision.toString())
        WebStorage.write(KEY_METRONOME_ENABLED, metronome.enabled.toString())
    }

    override suspend fun setTabMixer(mixer: TabMixer) {
        _tabMixer.value = mixer
        WebStorage.write(KEY_TAB_MIXER_VOLUME, mixer.safeVolume.toString())
        WebStorage.write(KEY_TAB_MIXER_PAN, mixer.safePan.toString())
        WebStorage.write(KEY_TAB_MIXER_MUTED, mixer.muted.toString())
        WebStorage.write(KEY_TAB_MIXER_SOLO, mixer.solo.toString())
    }

    override suspend fun setSelectedTablature(songId: String, tablatureId: String?) {
        val updated = _selectedTablatureBySong.value.toMutableMap()
        if (tablatureId == null) updated.remove(songId) else updated[songId] = tablatureId
        _selectedTablatureBySong.value = updated
        WebStorage.write(
            KEY_SELECTED_TABLATURE,
            updated.entries.joinToString(";") { "${it.key}=${it.value}" }
        )
    }

    override suspend fun setPreferredTablatureInstrument(instrument: String?) {
        _preferredTablatureInstrument.value = instrument
        if (instrument.isNullOrBlank()) WebStorage.remove(KEY_PREFERRED_INSTRUMENT)
        else WebStorage.write(KEY_PREFERRED_INSTRUMENT, instrument)
    }

    override suspend fun setPdfIncludeChords(includeChords: Boolean) {
        _pdfIncludeChords.value = includeChords
        WebStorage.write(KEY_PDF_INCLUDE_CHORDS, includeChords.toString())
    }

    private fun normalizeFontSize(sizeSp: Float): Float =
        if (sizeSp.isFinite()) sizeSp.coerceIn(MIN_SONG_FONT_SIZE_SP, MAX_SONG_FONT_SIZE_SP)
        else DEFAULT_SONG_FONT_SIZE_SP

    private fun normalizeTablatureScale(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(TabNotationScale.MIN_ZOOM, TabNotationScale.MAX_ZOOM)
        else TabNotationScale.DEFAULT_ZOOM

    private fun loadBoolean(key: String, fallback: Boolean): Boolean =
        WebStorage.read(key)?.toBooleanStrictOrNull() ?: fallback

    private fun <T : Enum<T>> loadEnum(
        key: String,
        entries: List<T>,
        fallback: T
    ): T {
        val raw = WebStorage.read(key) ?: return fallback
        return entries.firstOrNull { it.name == raw } ?: fallback
    }

    private fun loadTablatureSelection(): Map<String, String> =
        WebStorage.read(KEY_SELECTED_TABLATURE).orEmpty().split(';')
            .mapNotNull { entry ->
                val parts = entry.split('=')
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .toMap()

    private companion object {
        const val DEFAULT_SONG_FONT_SIZE_SP = 16f
        const val MIN_SONG_FONT_SIZE_SP = 14f
        const val MAX_SONG_FONT_SIZE_SP = 32f

        const val KEY_NOTATION = "notation_system"
        const val KEY_DISPLAY_MODE = "song_display_mode"
        const val KEY_TAB_POSITION = "tab_display_position"
        const val KEY_RHYTHM_NOTATION = "rhythm_notation_mode"
        const val KEY_FONT_SIZE = "song_font_size_sp"
        const val KEY_TAB_SCALE = "tablature_scale"
        const val KEY_TAB_NUMBER_SCALE = "tablature_number_scale"
        const val KEY_TAB_STAFF_OPTIONS = "tablature_staff_options"
        const val KEY_METRONOME_ENABLED = "metronome_enabled"
        const val KEY_TAB_METRONOME_ENABLED = "tab_metronome_enabled"
        const val KEY_TAB_METRONOME_VOLUME = "tab_metronome_volume"
        const val KEY_TAB_METRONOME_ACCENT = "tab_metronome_accent"
        const val KEY_TAB_METRONOME_SUBDIVISION = "tab_metronome_subdivision"
        const val KEY_TAB_MIXER_VOLUME = "tab_mixer_volume"
        const val KEY_TAB_MIXER_PAN = "tab_mixer_pan"
        const val KEY_TAB_MIXER_MUTED = "tab_mixer_muted"
        const val KEY_TAB_MIXER_SOLO = "tab_mixer_solo"
        const val KEY_SELECTED_TABLATURE = "selected_tablature_by_song"
        const val KEY_PREFERRED_INSTRUMENT = "preferred_tablature_instrument"
        const val KEY_PDF_INCLUDE_CHORDS = "pdf_include_chords"
    }
}
