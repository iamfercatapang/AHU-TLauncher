package com.ahu.tlauncher.update

/** `version.json` published next to TLauncher.apk on the "latest" GitHub release. Pure, unit tested. */
data class RemoteVersion(val versionCode: Int, val versionName: String) {
    fun isNewerThan(localCode: Int) = versionCode > localCode

    companion object {
        private val code = Regex("\"versionCode\"\\s*:\\s*(\\d+)")
        private val name = Regex("\"versionName\"\\s*:\\s*\"([^\"]*)\"")

        fun parse(json: String): RemoteVersion? {
            val c = code.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return RemoteVersion(c, name.find(json)?.groupValues?.get(1) ?: c.toString())
        }
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: RemoteVersion) : UpdateState
    data class Downloading(val version: RemoteVersion, val progress: Float) : UpdateState
    data class Installing(val version: RemoteVersion) : UpdateState
    data class Failed(val message: String, val version: RemoteVersion? = null) : UpdateState
}
