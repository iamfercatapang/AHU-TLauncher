package com.ahu.tlauncher

import com.ahu.tlauncher.system.CellType
import com.ahu.tlauncher.ui.home.ago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StatusIconsTest {
    @Test fun cellLabels() {
        assertEquals("4G", CellType.label(13))
        assertEquals("5G", CellType.label(20))
        assertEquals("3G", CellType.label(15))
        assertEquals("2G", CellType.label(2))
        assertNull(CellType.label(0))
    }

    @Test fun timeAgo() {
        val now = LocalDateTime.of(2026, 11, 14, 12, 0)
        val ms = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("now", ago(now, ms - 20_000))
        assertEquals("5 min", ago(now, ms - 5 * 60_000))
        assertEquals("2 h", ago(now, ms - 2 * 3_600_000))
        assertEquals("3 d", ago(now, ms - 3 * 86_400_000L))
    }
}
