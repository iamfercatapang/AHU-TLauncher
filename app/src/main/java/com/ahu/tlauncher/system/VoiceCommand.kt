package com.ahu.tlauncher.system

/** What a spoken command asks for. */
sealed interface VoiceCommand {
    data class Navigate(val query: String) : VoiceCommand
    data object Home : VoiceCommand
    data object Work : VoiceCommand
    data object FindFuel : VoiceCommand
    data object Play : VoiceCommand
    data object Pause : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object VolumeUp : VoiceCommand
    data object VolumeDown : VoiceCommand
    data object Mute : VoiceCommand
    data class Call(val who: String) : VoiceCommand
    data class Open(val app: String) : VoiceCommand
    data object StopNavigation : VoiceCommand
    data class Unknown(val text: String) : VoiceCommand

    companion object {
        private val NAV = Regex("^(?:navigate|drive|take me|go|directions|route|bring me|get me)(?: me)?(?: to| towards)? (.+)$")

        /** Reads what the speech recogniser heard (English). Pure, unit tested. */
        fun parse(heard: String): VoiceCommand {
            val t = heard.lowercase().trim().trimEnd('.', '!', '?').replace(Regex("^(?:hey |ok |okay )?(?:please |can you |could you )?"), "")
                .replace(Regex(" please$"), "").trim()
            return when {
                t.isEmpty() -> Unknown(heard)
                Regex("^(?:navigate|drive|take me|go|directions|bring me|get me)(?: me)?(?: to)? home$|^home$").matches(t) -> Home
                Regex("^(?:navigate|drive|take me|go|directions|bring me|get me)(?: me)?(?: to)? (?:work|the office|office)$").matches(t) -> Work
                Regex(".*\\b(?:gas|fuel|petrol|gasoline)(?: station| stations| stop)?\\b.*").matches(t) &&
                    Regex(".*\\b(?:find|nearest|nearby|where|need|look|search|show|station|stop|low)\\b.*").matches(t) -> FindFuel
                Regex("^(?:stop|end|cancel|exit)(?: the)? (?:navigation|navigating|route|directions|trip)$").matches(t) -> StopNavigation
                Regex("^(?:next|skip)(?: song| track| one)?$|^(?:play )?(?:the )?next (?:song|track)$").matches(t) -> Next
                Regex("^(?:previous|back|go back)(?: song| track)?$|^(?:play )?(?:the )?previous (?:song|track)$|^last (?:song|track)$").matches(t) -> Previous
                Regex("^(?:pause|stop)(?: the)?(?: music| song| playing| audio)?$").matches(t) -> Pause
                Regex("^(?:play|resume|continue|start)(?: the)?(?: music| song| playing| audio)?$").matches(t) -> Play
                Regex("^(?:volume up|turn (?:it )?up|louder|increase (?:the )?volume|raise (?:the )?volume)$").matches(t) -> VolumeUp
                Regex("^(?:volume down|turn (?:it )?down|quieter|softer|lower (?:the )?volume|decrease (?:the )?volume)$").matches(t) -> VolumeDown
                Regex("^(?:mute|unmute|silence)(?: the)?(?: sound| audio| volume)?$").matches(t) -> Mute
                t.startsWith("call ") || t.startsWith("dial ") || t.startsWith("phone ") -> Call(t.substringAfter(' ').trim())
                t.startsWith("open ") || t.startsWith("launch ") || t.startsWith("start ") -> Open(t.substringAfter(' ').removePrefix("the ").removeSuffix(" app").trim())
                else -> NAV.find(t)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { Navigate(it) } ?: Unknown(heard)
            }
        }

        /** "0917 123 4567" heard as a number to dial: digits only (and a leading +), or null for a name. */
        fun phoneNumber(who: String): String? {
            val digits = who.replace(Regex("[\\s()-]"), "")
            return digits.takeIf { Regex("^\\+?\\d{3,15}$").matches(it) }
        }
    }
}
