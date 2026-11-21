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

internal data class AppUpdate(val version: String, val url: String, val size: Long, val sha256: String? = null)

private const val DOWNLOADS = "https://raw.githubusercontent.com/serotonin0228/pomodoro/apk-distribution"
private const val MAX_APK_SIZE = 150L * 1024 * 1024

internal object AppUpdates {
    suspend fun latest(installedVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        connect("$DOWNLOADS/latest.json") { connection ->
            if (connection.responseCode == 404) return@connect null
            check(connection.responseCode == 200)
            val manifest = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = manifest.getString("version")
            val size = manifest.getLong("size")
            if (!isNewerVersion(version, installedVersion) || size !in 1..MAX_APK_SIZE) return@connect null
            val checksum = manifest.optString("sha256").takeIf { it.isNotEmpty() }
            if (checksum != null && !checksum.matches(Regex("[a-fA-F0-9]{64}"))) return@connect null
            AppUpdate(version, "$DOWNLOADS/pomodoro-v$version-arm64-v8a.apk", size, checksum)
        }
    }

    suspend fun cachedApk(context: Context, update: AppUpdate): File? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "updates/pomodoro-v${update.version}.apk")
        if (!file.isFile || file.length() != update.size) return@withContext null
        runCatching {
            verifyChecksum(file, update.sha256)
            verifyApk(context, file, update.version)
            file
        }.getOrNull()
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
                verifyChecksum(partial, update.sha256)
                verifyApk(context, partial, update.version)
                check(partial.renameTo(apk))
                directory.listFiles()?.filter { it != apk && it.extension == "apk" }?.forEach { it.delete() }
                apk
            } finally {
                partial.delete()
            }
        }

    private fun verifyChecksum(file: File, expected: String?) {
        if (expected == null) return
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true))
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
