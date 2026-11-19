package com.pomodorotimer.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdate(val version: String, val url: String, val size: Long)

private const val PROJECT = "serotonin0228/pomodoro"
private const val MAX_APK_SIZE = 150L * 1024 * 1024

internal object AppUpdates {
    suspend fun latest(installedVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        connect("https://api.github.com/repos/$PROJECT/releases/latest") { connection ->
            if (connection.responseCode == 404) return@connect null
            check(connection.responseCode == 200)
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@connect null
            val version = release.optString("tag_name").removePrefix("v")
            if (!isNewerVersion(version, installedVersion)) return@connect null
            val filename = "pomodoro-v$version-arm64-v8a.apk"
            val downloadUrl = "https://github.com/$PROJECT/releases/download/v$version/$filename"
            val assets = release.getJSONArray("assets")
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val size = asset.optLong("size")
                if (asset.optString("name") == filename && asset.optString("state") == "uploaded" &&
                    asset.optString("browser_download_url") == downloadUrl && size in 1..MAX_APK_SIZE
                ) return@connect AppUpdate(version, downloadUrl, size)
            }
            null
        }
    }

    suspend fun download(context: Context, update: AppUpdate, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").also { it.mkdirs() }
            val apk = File(directory, "pomodoro-v${update.version}.apk")
            val partial = File(directory, "download.partial.apk")
            check(!apk.exists() || apk.delete())
            try {
                connect(update.url) { connection ->
                    check(connection.responseCode == 200)
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= update.size)
                                output.write(buffer, 0, count)
                                onProgress(total.toFloat() / update.size)
                            }
                            check(total == update.size)
                        }
                    }
                }
                verifyApk(context, partial, update.version)
                check(partial.renameTo(apk))
                directory.listFiles()?.filter { it != apk && it.extension == "apk" }?.forEach { it.delete() }
                apk
            } finally {
                partial.delete()
            }
        }

    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, file: File, version: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, flags)
        val archive = checkNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags))
        check(archive.packageName == installed.packageName && archive.versionName == version)
        check(PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed))
        val signers = installed.signers()
        check(signers.isNotEmpty() && signers == archive.signers())
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
        return certificates.orEmpty().map { it.toCharsString() }.toSet()
    }

    private fun <T> connect(url: String, action: (HttpURLConnection) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Pomodoro-Android")
            action(connection)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    fun parse(value: String): List<Int>? {
        val parts = value.split('.')
        if (parts.size != 3 || parts.any { it.isEmpty() || it.any { char -> char !in '0'..'9' } }) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    val next = parse(candidate) ?: return false
    val current = parse(installed) ?: return false
    for (index in next.indices) {
        if (next[index] != current[index]) return next[index] > current[index]
    }
    return false
}
