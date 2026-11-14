package com.ahu.tlauncher.route

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ApiException(message: String) : Exception(message)

/**
 * Calls Google's Places (New) and Routes web APIs with the app's Maps key. The key is restricted to
 * Android apps, so each request carries the package name and signing-certificate SHA-1 that Google checks.
 */
class GoogleWeb(private val context: Context) {
    private val key: String by lazy {
        runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("com.google.android.geo.API_KEY")
        }.getOrNull().orEmpty()
    }

    private val certSha1: String by lazy {
        runCatching {
            val sig = if (Build.VERSION.SDK_INT >= 28) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            }
            MessageDigest.getInstance("SHA-1").digest(sig!!.toByteArray()).joinToString("") { "%02X".format(it) }
        }.getOrDefault("")
    }

    val hasKey get() = key.isNotBlank()

    suspend fun post(url: String, body: String, fieldMask: String, api: String): String = call(url, "POST", body, fieldMask, api)
    suspend fun get(url: String, fieldMask: String, api: String): String = call(url, "GET", null, fieldMask, api)

    private suspend fun call(url: String, method: String, body: String?, fieldMask: String, api: String): String = withContext(Dispatchers.IO) {
        if (!hasKey) throw ApiException("No Google API key in this build")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("X-Goog-Api-Key", key)
            c.setRequestProperty("X-Goog-FieldMask", fieldMask)
            c.setRequestProperty("X-Android-Package", context.packageName)
            c.setRequestProperty("X-Android-Cert", certSha1)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw ApiException(RouteMath.apiError(code, text, api))
            text
        } catch (e: java.io.IOException) {
            throw ApiException("No internet connection")
        } finally { c.disconnect() }
    }
}
