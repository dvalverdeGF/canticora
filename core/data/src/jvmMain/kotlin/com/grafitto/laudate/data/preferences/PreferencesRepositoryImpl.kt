package com.grafitto.laudate.data.preferences

import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

/**
 * Preferencias de Canticora en **escritorio (JVM)**.
 *
 * Persiste en un fichero `.properties` dentro de la carpeta de datos del usuario
 * (`~/.canticora/preferences.properties`). Es el equivalente de `DataStore` en Android: la
 * nomenclatura de acordes es global y se conserva entre sesiones. Ver ADR-013.
 */
class PreferencesRepositoryImpl(dataDirectory: File) : PreferencesRepository {

    private val file = File(dataDirectory, "preferences.properties").apply {
        parentFile?.mkdirs()
    }

    private val _notationSystem = MutableStateFlow(loadNotationSystem())
    override val notationSystem: Flow<NotationSystem> = _notationSystem.asStateFlow()

    private val _songDisplayMode = MutableStateFlow(loadSongDisplayMode())
    override val songDisplayMode: Flow<SongDisplayMode> = _songDisplayMode.asStateFlow()

    private val _tabDisplayPosition = MutableStateFlow(loadTabDisplayPosition())
    override val tabDisplayPosition: Flow<TabDisplayPosition> = _tabDisplayPosition.asStateFlow()

    private val _rhythmNotationMode = MutableStateFlow(loadRhythmNotationMode())
    override val rhythmNotationMode: Flow<RhythmNotationMode> = _rhythmNotationMode.asStateFlow()

    private val _songFontSizeSp = MutableStateFlow(loadSongFontSizeSp())
    override val songFontSizeSp: Flow<Float> = _songFontSizeSp.asStateFlow()

    private val _tablatureScale = MutableStateFlow(loadTablatureScale())
    override val tablatureScale: Flow<Float> = _tablatureScale.asStateFlow()

    private val _tablatureNumberScale = MutableStateFlow(loadTablatureNumberScale())
    override val tablatureNumberScale: Flow<Float> = _tablatureNumberScale.asStateFlow()

    private val _metronomeEnabled = MutableStateFlow(loadMetronomeEnabled())
    override val metronomeEnabled: Flow<Boolean> = _metronomeEnabled.asStateFlow()

    private val _tabMetronome = MutableStateFlow(loadTabMetronome())
    override val tabMetronome: Flow<com.grafitto.laudate.domain.tablature.TabMetronome> =
        _tabMetronome.asStateFlow()

    private val _tabMixer = MutableStateFlow(loadTabMixer())
    override val tabMixer: Flow<com.grafitto.laudate.domain.tablature.TabMixer> = _tabMixer.asStateFlow()

    private val _selectedTablatureBySong = MutableStateFlow(loadSelectedTablatureBySong())
    override val selectedTablatureBySong: Flow<Map<String, String>> = _selectedTablatureBySong.asStateFlow()

    private val _preferredTablatureInstrument = MutableStateFlow(read("preferred_tablature_instrument"))
    override val preferredTablatureInstrument: Flow<String?> = _preferredTablatureInstrument.asStateFlow()

    private val _pdfIncludeChords = MutableStateFlow(loadPdfIncludeChords())
    override val pdfIncludeChords: Flow<Boolean> = _pdfIncludeChords.asStateFlow()

    private val _tablatureStaffOptions = MutableStateFlow(loadTablatureStaffOptions())
    override val tablatureStaffOptions: Flow<com.grafitto.laudate.domain.tablature.TabStaffOptions> =
        _tablatureStaffOptions.asStateFlow()

    override suspend fun setNotationSystem(system: NotationSystem) {
        _notationSystem.value = system
        save("notation_system", system.name)
    }

    override suspend fun setSongDisplayMode(mode: SongDisplayMode) {
        _songDisplayMode.value = mode
        save("song_display_mode", mode.name)
    }

    override suspend fun setTabDisplayPosition(position: TabDisplayPosition) {
        _tabDisplayPosition.value = position
        save("tab_display_position", position.name)
    }

    override suspend fun setRhythmNotationMode(mode: RhythmNotationMode) {
        _rhythmNotationMode.value = mode
        save("rhythm_notation_mode", mode.name)
    }

    override suspend fun setSongFontSizeSp(sizeSp: Float) {
        val normalized = normalizeFontSize(sizeSp)
        _songFontSizeSp.value = normalized
        save("song_font_size_sp", normalized.toString())
    }

    override suspend fun setTablatureScale(scale: Float) {
        val normalized = normalizeTablatureScale(scale)
        _tablatureScale.value = normalized
        save("tablature_scale", normalized.toString())
    }

    override suspend fun setTablatureNumberScale(scale: Float) {
        val normalized = normalizeTablatureScale(scale)
        _tablatureNumberScale.value = normalized
        save("tablature_number_scale", normalized.toString())
    }

    override suspend fun setMetronomeEnabled(enabled: Boolean) {
        _metronomeEnabled.value = enabled
        save("metronome_enabled", enabled.toString())
        // (D-66) El interruptor histórico es el `enabled` del metrónomo configurable: se mantienen
        // sincronizados para que encender por un sitio y apagar por otro no deje dos verdades.
        _tabMetronome.value = _tabMetronome.value.copy(enabled = enabled)
        save("tab_metronome_enabled", enabled.toString())
    }

    override suspend fun setTabMetronome(
        metronome: com.grafitto.laudate.domain.tablature.TabMetronome
    ) {
        _tabMetronome.value = metronome
        _metronomeEnabled.value = metronome.enabled
        save("tab_metronome_enabled", metronome.enabled.toString())
        save("tab_metronome_volume", metronome.safeVolume.toString())
        save("tab_metronome_accent", metronome.accentFirst.toString())
        save("tab_metronome_subdivision", metronome.safeSubdivision.toString())
        save("metronome_enabled", metronome.enabled.toString())
    }

    override suspend fun setTabMixer(mixer: com.grafitto.laudate.domain.tablature.TabMixer) {
        _tabMixer.value = mixer
        save("tab_mixer_volume", mixer.safeVolume.toString())
        save("tab_mixer_pan", mixer.safePan.toString())
        save("tab_mixer_muted", mixer.muted.toString())
        save("tab_mixer_solo", mixer.solo.toString())
    }

    override suspend fun setSelectedTablature(songId: String, tablatureId: String?) {
        val updated = _selectedTablatureBySong.value.toMutableMap()
        if (tablatureId == null) updated.remove(songId) else updated[songId] = tablatureId
        _selectedTablatureBySong.value = updated
        save("selected_tablature_by_song", encodeTablatureSelection(updated))
    }

    override suspend fun setPreferredTablatureInstrument(instrument: String?) {
        _preferredTablatureInstrument.value = instrument
        if (instrument.isNullOrBlank()) clear("preferred_tablature_instrument")
        else save("preferred_tablature_instrument", instrument)
    }

    override suspend fun setPdfIncludeChords(includeChords: Boolean) {
        _pdfIncludeChords.value = includeChords
        save("pdf_include_chords", includeChords.toString())
    }

    override suspend fun setTablatureStaffOptions(
        options: com.grafitto.laudate.domain.tablature.TabStaffOptions
    ) {
        _tablatureStaffOptions.value = options
        save("tablature_staff_options", com.grafitto.laudate.domain.tablature.TabStaffOptionsCodec.encode(options))
    }

    private fun loadNotationSystem(): NotationSystem {
        val raw = read("notation_system") ?: return NotationSystem.LATIN
        return NotationSystem.entries.firstOrNull { it.name == raw } ?: NotationSystem.LATIN
    }

    private fun loadSongDisplayMode(): SongDisplayMode {
        val raw = read("song_display_mode") ?: return SongDisplayMode.CHORDS_ABOVE_TEXT
        return SongDisplayMode.entries.firstOrNull { it.name == raw } ?: SongDisplayMode.CHORDS_ABOVE_TEXT
    }

    private fun loadTabDisplayPosition(): TabDisplayPosition {
        val raw = read("tab_display_position") ?: return TabDisplayPosition.AFTER
        return TabDisplayPosition.entries.firstOrNull { it.name == raw } ?: TabDisplayPosition.AFTER
    }

    private fun loadRhythmNotationMode(): RhythmNotationMode {
        val raw = read("rhythm_notation_mode") ?: return RhythmNotationMode.ARROWS
        return RhythmNotationMode.entries.firstOrNull { it.name == raw } ?: RhythmNotationMode.ARROWS
    }

    private fun loadSongFontSizeSp(): Float =
        read("song_font_size_sp")?.toFloatOrNull()?.let(::normalizeFontSize) ?: DEFAULT_SONG_FONT_SIZE_SP

    private fun normalizeFontSize(sizeSp: Float): Float =
        if (sizeSp.isFinite()) sizeSp.coerceIn(MIN_SONG_FONT_SIZE_SP, MAX_SONG_FONT_SIZE_SP)
        else DEFAULT_SONG_FONT_SIZE_SP

    private fun normalizeTablatureScale(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN_TABLATURE_SCALE, MAX_TABLATURE_SCALE)
        else DEFAULT_TABLATURE_SCALE

    private fun loadTablatureScale(): Float =
        read("tablature_scale")?.toFloatOrNull()?.let(::normalizeTablatureScale) ?: DEFAULT_TABLATURE_SCALE

    private fun loadTablatureNumberScale(): Float =
        read("tablature_number_scale")?.toFloatOrNull()?.let(::normalizeTablatureScale)
            ?: DEFAULT_TABLATURE_SCALE

    /** Formato compacto `songId=tabId` separado por `;`. Los ids son UUID (sin `=`, `;` ni saltos). */
    private fun encodeTablatureSelection(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun loadSelectedTablatureBySong(): Map<String, String> =
        read("selected_tablature_by_song").orEmpty().split(';')
            .mapNotNull { entry ->
                val parts = entry.split('=')
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
            }
            .toMap()

    private fun loadMetronomeEnabled(): Boolean =
        read("metronome_enabled")?.toBooleanStrictOrNull() ?: false

    private fun loadTabMetronome(): com.grafitto.laudate.domain.tablature.TabMetronome =
        com.grafitto.laudate.domain.tablature.TabMetronome(
            enabled = read("tab_metronome_enabled")?.toBooleanStrictOrNull()
                ?: read("metronome_enabled")?.toBooleanStrictOrNull()
                ?: false,
            volume = read("tab_metronome_volume")?.toFloatOrNull()
                ?: com.grafitto.laudate.domain.tablature.TabMetronome.DEFAULT_VOLUME,
            accentFirst = read("tab_metronome_accent")?.toBooleanStrictOrNull() ?: true,
            subdivision = read("tab_metronome_subdivision")?.toIntOrNull() ?: 1
        )

    private fun loadTabMixer(): com.grafitto.laudate.domain.tablature.TabMixer =
        com.grafitto.laudate.domain.tablature.TabMixer(
            volume = read("tab_mixer_volume")?.toFloatOrNull() ?: 1f,
            pan = read("tab_mixer_pan")?.toFloatOrNull() ?: 0f,
            muted = read("tab_mixer_muted")?.toBooleanStrictOrNull() ?: false,
            solo = read("tab_mixer_solo")?.toBooleanStrictOrNull() ?: false
        )

    private fun loadPdfIncludeChords(): Boolean =
        read("pdf_include_chords")?.toBooleanStrictOrNull() ?: true

    private fun loadTablatureStaffOptions(): com.grafitto.laudate.domain.tablature.TabStaffOptions =
        com.grafitto.laudate.domain.tablature.TabStaffOptionsCodec.decode(read("tablature_staff_options"))

    private fun read(key: String): String? = runCatching {
        if (!file.exists()) return null
        Properties().apply { file.inputStream().use { load(it) } }.getProperty(key)
    }.getOrNull()

    private fun save(key: String, value: String) = runCatching {
        val props = Properties().apply {
            if (file.exists()) file.inputStream().use { load(it) }
            setProperty(key, value)
        }
        file.outputStream().use { props.store(it, "Canticora — preferencias de escritorio") }
    }.getOrNull()

    private fun clear(key: String) = runCatching {
        val props = Properties().apply {
            if (file.exists()) file.inputStream().use { load(it) }
            remove(key)
        }
        file.outputStream().use { props.store(it, "Canticora — preferencias de escritorio") }
    }.getOrNull()

    private companion object {
        const val DEFAULT_SONG_FONT_SIZE_SP = 16f
        const val MIN_SONG_FONT_SIZE_SP = 14f
        const val MAX_SONG_FONT_SIZE_SP = 32f
        const val DEFAULT_TABLATURE_SCALE = com.grafitto.laudate.domain.tablature.TabNotationScale.DEFAULT_ZOOM
        /** (D-60) El mínimo baja a `0,35`: el 70 % seguía siendo demasiado grande en móvil. */
        const val MIN_TABLATURE_SCALE = com.grafitto.laudate.domain.tablature.TabNotationScale.MIN_ZOOM
        const val MAX_TABLATURE_SCALE = com.grafitto.laudate.domain.tablature.TabNotationScale.MAX_ZOOM
    }
}
