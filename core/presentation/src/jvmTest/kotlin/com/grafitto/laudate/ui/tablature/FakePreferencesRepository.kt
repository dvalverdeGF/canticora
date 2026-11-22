package com.grafitto.laudate.ui.tablature

import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.tablature.TabMetronome
import com.grafitto.laudate.domain.tablature.TabMixer
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **Preferencias en memoria** para los tests de ViewModel (D-48).
 *
 * Guarda el valor en un `MutableStateFlow` y lo emite igual que las implementaciones reales, de modo
 * que un test puede comprobar dos cosas: que el ViewModel **lee** la preferencia al arrancar y que
 * la **escribe** cuando el usuario cambia un ajuste.
 */
class FakePreferencesRepository : PreferencesRepository {

    private val _notationSystem = MutableStateFlow(NotationSystem.LATIN)
    override val notationSystem: Flow<NotationSystem> = _notationSystem.asStateFlow()

    private val _songDisplayMode = MutableStateFlow(SongDisplayMode.CHORDS_ABOVE_TEXT)
    override val songDisplayMode: Flow<SongDisplayMode> = _songDisplayMode.asStateFlow()

    private val _tabDisplayPosition = MutableStateFlow(TabDisplayPosition.AFTER)
    override val tabDisplayPosition: Flow<TabDisplayPosition> = _tabDisplayPosition.asStateFlow()

    private val _rhythmNotationMode = MutableStateFlow(RhythmNotationMode.ARROWS)
    override val rhythmNotationMode: Flow<RhythmNotationMode> = _rhythmNotationMode.asStateFlow()

    private val _songFontSizeSp = MutableStateFlow(16f)
    override val songFontSizeSp: Flow<Float> = _songFontSizeSp.asStateFlow()

    private val _tablatureScale = MutableStateFlow(1f)
    override val tablatureScale: Flow<Float> = _tablatureScale.asStateFlow()

    private val _tablatureNumberScale = MutableStateFlow(1f)
    override val tablatureNumberScale: Flow<Float> = _tablatureNumberScale.asStateFlow()

    private val _metronomeEnabled = MutableStateFlow(false)
    override val metronomeEnabled: Flow<Boolean> = _metronomeEnabled.asStateFlow()

    private val _tabMixer = MutableStateFlow(TabMixer())
    override val tabMixer: Flow<TabMixer> = _tabMixer.asStateFlow()

    private val _tabMetronome = MutableStateFlow(TabMetronome.DEFAULT)
    override val tabMetronome: Flow<TabMetronome> = _tabMetronome.asStateFlow()

    private val _selectedTablatureBySong = MutableStateFlow<Map<String, String>>(emptyMap())
    override val selectedTablatureBySong: Flow<Map<String, String>> =
        _selectedTablatureBySong.asStateFlow()

    private val _preferredTablatureInstrument = MutableStateFlow<String?>(null)
    override val preferredTablatureInstrument: Flow<String?> =
        _preferredTablatureInstrument.asStateFlow()

    private val _pdfIncludeChords = MutableStateFlow(true)
    override val pdfIncludeChords: Flow<Boolean> = _pdfIncludeChords.asStateFlow()

    private val _tablatureStaffOptions = MutableStateFlow(TabStaffOptions())
    override val tablatureStaffOptions: Flow<TabStaffOptions> =
        _tablatureStaffOptions.asStateFlow()

    /** Último valor guardado de los ajustes del pentagrama, para aserciones directas. */
    val savedStaffOptions: TabStaffOptions get() = _tablatureStaffOptions.value

    override suspend fun setNotationSystem(system: NotationSystem) {
        _notationSystem.value = system
    }

    override suspend fun setSongDisplayMode(mode: SongDisplayMode) {
        _songDisplayMode.value = mode
    }

    override suspend fun setTabDisplayPosition(position: TabDisplayPosition) {
        _tabDisplayPosition.value = position
    }

    override suspend fun setRhythmNotationMode(mode: RhythmNotationMode) {
        _rhythmNotationMode.value = mode
    }

    override suspend fun setSongFontSizeSp(sizeSp: Float) {
        _songFontSizeSp.value = sizeSp
    }

    override suspend fun setTablatureScale(scale: Float) {
        _tablatureScale.value = scale
    }

    override suspend fun setTablatureNumberScale(scale: Float) {
        _tablatureNumberScale.value = scale
    }

    override suspend fun setMetronomeEnabled(enabled: Boolean) {
        _metronomeEnabled.value = enabled
        _tabMetronome.value = _tabMetronome.value.copy(enabled = enabled)
    }

    override suspend fun setTabMetronome(metronome: TabMetronome) {
        _tabMetronome.value = metronome
        _metronomeEnabled.value = metronome.enabled
    }

    override suspend fun setTabMixer(mixer: TabMixer) {
        _tabMixer.value = mixer
    }

    override suspend fun setSelectedTablature(songId: String, tablatureId: String?) {
        val updated = _selectedTablatureBySong.value.toMutableMap()
        if (tablatureId == null) updated.remove(songId) else updated[songId] = tablatureId
        _selectedTablatureBySong.value = updated
    }

    override suspend fun setPreferredTablatureInstrument(instrument: String?) {
        _preferredTablatureInstrument.value = instrument
    }

    override suspend fun setPdfIncludeChords(includeChords: Boolean) {
        _pdfIncludeChords.value = includeChords
    }

    override suspend fun setTablatureStaffOptions(options: TabStaffOptions) {
        _tablatureStaffOptions.value = options
    }
}
