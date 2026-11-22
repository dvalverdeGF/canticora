package com.grafitto.laudate.data.preferences

import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.tablature.FretMarkStyle
import com.grafitto.laudate.domain.tablature.MinimStyle
import com.grafitto.laudate.domain.tablature.ParenthesizeTiedFret
import com.grafitto.laudate.domain.tablature.ShowTiedFret
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class PreferencesRepositoryImplTest {

    @Test
    fun readingPreferencesPersistAcrossRepositoryInstances() = runBlocking {
        val dataDirectory = Files.createTempDirectory("laudate-preferences-test").toFile()
        try {
            val repository = PreferencesRepositoryImpl(dataDirectory)
            repository.setSongDisplayMode(SongDisplayMode.LYRICS_ONLY)
            repository.setRhythmNotationMode(RhythmNotationMode.CHORDPRO_SYMBOLS)
            repository.setSongFontSizeSp(24f)

            val restored = PreferencesRepositoryImpl(dataDirectory)

            assertEquals(SongDisplayMode.LYRICS_ONLY, restored.songDisplayMode.first())
            assertEquals(RhythmNotationMode.CHORDPRO_SYMBOLS, restored.rhythmNotationMode.first())
            assertEquals(24f, restored.songFontSizeSp.first(), 0f)
        } finally {
            dataDirectory.deleteRecursively()
        }
    }

    @Test
    fun losAjustesDelPentagramaSeRecuerdanEntreSesiones() = runBlocking {
        val dataDirectory = Files.createTempDirectory("laudate-staff-options-test").toFile()
        try {
            val options = TabStaffOptions(
                fretMarks = FretMarkStyle.LETTERS,
                minimStyle = MinimStyle.SLASHED,
                showTiedFret = ShowTiedFret.TIE,
                parenthesizeTiedFret = ParenthesizeTiedFret.START_OF_MEASURE,
                showClef = false
            )
            PreferencesRepositoryImpl(dataDirectory).setTablatureStaffOptions(options)

            // D-48: al reabrir la app el pentagrama se dibuja como lo dejó el usuario.
            val restored = PreferencesRepositoryImpl(dataDirectory)

            assertEquals(options, restored.tablatureStaffOptions.first())
        } finally {
            dataDirectory.deleteRecursively()
        }
    }

    @Test
    fun sinAjustesGuardadosElPentagramaUsaElTipoCommon() = runBlocking {
        val dataDirectory = Files.createTempDirectory("laudate-staff-default-test").toFile()
        try {
            assertEquals(
                TabStaffOptions(),
                PreferencesRepositoryImpl(dataDirectory).tablatureStaffOptions.first()
            )
        } finally {
            dataDirectory.deleteRecursively()
        }
    }

    @Test
    fun songFontSizeIsClampedToTheSupportedRange() = runBlocking {
        val dataDirectory = Files.createTempDirectory("laudate-font-size-test").toFile()
        try {
            val repository = PreferencesRepositoryImpl(dataDirectory)
            repository.setSongFontSizeSp(50f)

            assertEquals(32f, repository.songFontSizeSp.first(), 0f)
        } finally {
            dataDirectory.deleteRecursively()
        }
    }
}
