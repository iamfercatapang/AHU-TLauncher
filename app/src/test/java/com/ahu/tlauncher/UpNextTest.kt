package com.ahu.tlauncher

import com.ahu.tlauncher.notify.QueueEntry
import com.ahu.tlauncher.ui.home.UpNext
import org.junit.Assert.assertEquals
import org.junit.Test

class UpNextTest {
    private val q = (1L..5L).map { QueueEntry(it, "Song $it", "Artist", null, null, null) }

    @Test fun startsAtPlayingSong() {
        assertEquals(listOf(3L, 4L, 5L), UpNext.rows(q, 3).map { it.id })
        assertEquals(2, UpNext.remaining(q, 3))
    }

    @Test fun unknownActiveShowsAll() {
        assertEquals(5, UpNext.rows(q, -1).size)
        assertEquals(5, UpNext.remaining(q, -1))
    }

    @Test fun lastSong() = assertEquals(0, UpNext.remaining(q, 5))
}
