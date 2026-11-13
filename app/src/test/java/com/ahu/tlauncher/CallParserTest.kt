package com.ahu.tlauncher

import com.ahu.tlauncher.notify.CallAction
import com.ahu.tlauncher.notify.CallParser
import com.ahu.tlauncher.notify.CallPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallParserTest {
    private fun p(pkg: String, cat: String?, title: String?, text: String?, acts: List<String>, ongoing: Boolean = true, chrono: Boolean = false, whenMs: Long = 0) =
        CallParser.parse(pkg, cat, title, text, acts, ongoing, chrono, whenMs)

    @Test fun dialerIncoming() {
        val c = p("com.google.android.dialer", "call", "Maria Santos", "Incoming call", listOf("Decline", "Answer"))!!
        assertEquals(CallPhase.RINGING, c.phase)
        assertEquals("Maria Santos", c.caller)
        assertEquals(mapOf(CallAction.DECLINE to 0, CallAction.ANSWER to 1), c.actions)
    }

    @Test fun dialerOngoing() {
        val c = p("com.google.android.dialer", "call", "Maria Santos", "Ongoing call", listOf("Speaker", "Mute", "Hang up"), chrono = true, whenMs = 1000)!!
        assertEquals(CallPhase.ACTIVE, c.phase)
        assertEquals(2, c.actions[CallAction.HANG_UP])
        assertEquals(1, c.actions[CallAction.MUTE])
        assertEquals(1000L, c.chronometerBaseMs)
    }

    @Test fun headUnitBluetoothAppChinese() {
        val c = p("com.syu.bt", null, "来电", "+63 917 123 4567", listOf("接听", "挂断"))!!
        assertEquals(CallPhase.RINGING, c.phase)
        assertEquals("+63 917 123 4567", c.caller)
        // "hang up" on a ringing call means decline
        assertEquals(mapOf(CallAction.ANSWER to 0, CallAction.DECLINE to 1), c.actions)
    }

    @Test fun activeWithPrivateMode() {
        val c = p("com.example.btphone", null, "Juan", "00:12", listOf("Private", "End"))!!
        assertEquals(CallPhase.ACTIVE, c.phase)
        assertEquals(0, c.actions[CallAction.AUDIO_SWITCH])
        assertEquals(1, c.actions[CallAction.HANG_UP])
    }

    @Test fun ignoresMusicAndPhoneStatus() {
        assertNull(p("com.spotify.music", "transport", "Song", "Artist", listOf("Previous", "Pause", "Next")))
        assertNull(p("com.android.phone", null, "SIM card", "No service", emptyList()))
        assertNull(p("com.whatsapp", "msg", "Juan", "Answer me!", listOf("Reply", "Mark as read"), ongoing = false))
    }

    @Test fun genericTitleFallsBack() {
        assertEquals("Unknown caller", p("com.x.dialer", "call", "Incoming call", "", listOf("Answer", "Decline"))!!.caller)
    }
}
