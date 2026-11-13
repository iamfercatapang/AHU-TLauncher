package com.ahu.tlauncher.obd

import kotlinx.coroutines.flow.Flow

interface ObdSource {
    /** Cold flow: collecting it connects and polls; cancelling it disconnects. Serves [link] commands between polls. */
    fun stream(link: ObdLink): Flow<ObdState>
}
