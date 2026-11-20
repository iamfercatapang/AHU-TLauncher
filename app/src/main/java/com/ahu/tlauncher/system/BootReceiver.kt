package com.ahu.tlauncher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiving the boot broadcast starts TLauncher's process with the head unit. [com.ahu.tlauncher.LauncherApp] then
 * creates [ResumeOnStart], which counts that as a car start (it's right after boot) and brings the dashboard up.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
