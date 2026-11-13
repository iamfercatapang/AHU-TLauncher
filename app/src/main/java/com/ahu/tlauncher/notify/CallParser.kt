package com.ahu.tlauncher.notify

enum class CallPhase { RINGING, ACTIVE }

enum class CallAction { ANSWER, DECLINE, HANG_UP, MUTE, AUDIO_SWITCH }

/** A phone call seen in another app's notification (the head unit's Bluetooth phone app or a dialer). */
data class CallInfo(
    val packageName: String,
    val phase: CallPhase,
    val caller: String,
    /** Index of each recognised button in the notification's action list. */
    val actions: Map<CallAction, Int>,
    /** Call start (epoch ms) if the notification shows a running timer. */
    val chronometerBaseMs: Long? = null,
)

/**
 * Heuristic call-notification reader. Pure Kotlin, unit tested.
 * Head-unit phone apps differ, so we look at the notification category, the app's package name and
 * the button labels (English and Chinese, which most head-unit firmwares use).
 */
object CallParser {
    private val answer = Regex("""\b(answer|accept|pick ?up)\b|接听|接聽""", RegexOption.IGNORE_CASE)
    private val decline = Regex("""\b(decline|reject|refuse|ignore|dismiss)\b|拒接|拒绝|拒絕""", RegexOption.IGNORE_CASE)
    private val hangUp = Regex("""\b(hang ?up|end( call)?|disconnect)\b|挂断|掛斷|结束""", RegexOption.IGNORE_CASE)
    private val mute = Regex("""\b(un)?mute\b|静音|靜音""", RegexOption.IGNORE_CASE)
    private val audio = Regex("""\b(handset|private|phone audio|switch( audio)?|transfer|voice)\b|切换|私密""", RegexOption.IGNORE_CASE)
    private val phoneApp = Regex("""(bluetooth|btphone|bt\.phone|\.bt\b|dialer|incall|telecom|\.phone|carphone|hfp)""", RegexOption.IGNORE_CASE)
    private val generic = Regex("""^(incoming( call)?|ongoing call|calling|call|phone|bluetooth( phone)?|来电|來電|通话中|通話中)$""", RegexOption.IGNORE_CASE)

    fun parse(
        pkg: String,
        category: String?,
        title: CharSequence?,
        text: CharSequence?,
        actionTitles: List<CharSequence?>,
        ongoing: Boolean,
        usesChronometer: Boolean,
        whenMs: Long,
    ): CallInfo? {
        val actions = mutableMapOf<CallAction, Int>()
        actionTitles.forEachIndexed { i, raw ->
            val a = raw?.toString()?.trim().orEmpty()
            val kind = when {
                a.isEmpty() -> null
                answer.containsMatchIn(a) -> CallAction.ANSWER
                hangUp.containsMatchIn(a) -> CallAction.HANG_UP
                decline.containsMatchIn(a) -> CallAction.DECLINE
                mute.containsMatchIn(a) -> CallAction.MUTE
                audio.containsMatchIn(a) -> CallAction.AUDIO_SWITCH
                else -> null
            }
            if (kind != null && kind !in actions) actions[kind] = i
        }
        val isCallCategory = category == "call"
        val hasCallButton = actions.keys.any { it == CallAction.ANSWER || it == CallAction.DECLINE || it == CallAction.HANG_UP }
        // Without the "call" category, only trust phone-like apps that show real call buttons.
        val looksLikePhone = phoneApp.containsMatchIn(pkg) && hasCallButton
        if (!isCallCategory && !looksLikePhone) return null
        if (!hasCallButton && !ongoing) return null

        val phase = if (CallAction.ANSWER in actions) CallPhase.RINGING else CallPhase.ACTIVE
        // In a ringing notification the "end" button means decline.
        if (phase == CallPhase.RINGING && CallAction.DECLINE !in actions) actions.remove(CallAction.HANG_UP)?.let { actions[CallAction.DECLINE] = it }
        if (phase == CallPhase.ACTIVE && CallAction.HANG_UP !in actions) actions.remove(CallAction.DECLINE)?.let { actions[CallAction.HANG_UP] = it }

        val t = title?.toString()?.trim().orEmpty()
        val x = text?.toString()?.trim().orEmpty()
        val caller = when {
            t.isNotEmpty() && !generic.matches(t) -> t
            x.isNotEmpty() && !generic.matches(x) -> x
            else -> "Unknown caller"
        }
        return CallInfo(pkg, phase, caller, actions, if (usesChronometer && whenMs > 0 && phase == CallPhase.ACTIVE) whenMs else null)
    }
}
