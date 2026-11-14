package com.ahu.tlauncher

import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.home.musicSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastMediaTest {
    private val song = MediaInfo("com.google.android.apps.youtube.music", "Midnight Drive", "The Night Lines", null, false, 95_000, 214_000, 0, 1f)

    @Test fun liveShowsArtist() = assertEquals("The Night Lines", HomeUiState(media = song.copy(isPlaying = true)).musicSubtitle("YouTube Music"))
    @Test fun lastPlayed() = assertEquals("Last played · The Night Lines", HomeUiState(lastMedia = song).musicSubtitle("YouTube Music"))
    @Test fun resuming() = assertEquals("Starting YouTube Music…", HomeUiState(lastMedia = song, resumingMedia = true).musicSubtitle("YouTube Music"))
    @Test fun nothing() = assertNull(HomeUiState().musicSubtitle(null))
}
