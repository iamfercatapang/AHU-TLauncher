package com.ahu.tlauncher

import com.ahu.tlauncher.update.RemoteVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteVersionTest {
    @Test fun parsesCiJson() {
        val v = RemoteVersion.parse("""{"versionCode": 12, "versionName": "1.0.12", "apk": "TLauncher.apk"}""")!!
        assertEquals(12, v.versionCode)
        assertEquals("1.0.12", v.versionName)
    }
    @Test fun newerOnlyWhenCodeHigher() {
        val v = RemoteVersion(12, "1.0.12")
        assertTrue(v.isNewerThan(11))
        assertFalse(v.isNewerThan(12))
        assertFalse(v.isNewerThan(13))
    }
    @Test fun garbageIsNull() = assertNull(RemoteVersion.parse("<html>Not Found</html>"))
    @Test fun missingNameFallsBackToCode() = assertEquals("7", RemoteVersion.parse("""{"versionCode":7}""")!!.versionName)
}
