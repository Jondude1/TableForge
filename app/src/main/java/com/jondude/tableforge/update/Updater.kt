package com.jondude.tableforge.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jondude.tableforge.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer build published on GitHub Releases. */
data class Release(val build: Int, val name: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

/**
 * Self-updater. Every push to main builds an APK and publishes it as a GitHub release tagged
 * v1.0.<build>; the build number is also this app's versionCode, so a higher tag means newer.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Jondude1/TableForge/releases/latest"

    val currentBuild: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    /** Returns the newest release if it's newer than this build, null if up to date. Throws on network errors. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "TableForge-Android")
        }
        try {
            when (val code = conn.responseCode) {
                200 -> Unit
                404 -> throw IllegalStateException("No releases found. The TableForge repo may still be private.")
                else -> throw IllegalStateException("GitHub returned $code. Try again later.")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            val build = Regex("""(\d+)$""").find(tag)?.value?.toIntOrNull() ?: return@withContext null
            if (build <= currentBuild) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
            Release(build, json.optString("name", tag), json.optString("body"), apk.getString("browser_download_url"), apk.optLong("size"))
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK into the app cache, reporting progress 0..1. */
    suspend fun download(context: Context, r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "TableForge-${r.build}.apk")
        var url = URL(r.apkUrl)
        var conn: HttpURLConnection
        // GitHub redirects release downloads to a CDN; follow it manually across hosts.
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("User-Agent", "TableForge-Android")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
            if (code != 200) throw IllegalStateException("Download failed ($code).")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); done += n
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        out
    }

    /** True if Android will let this app open the installer (the user grants this once). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows installs from TableForge. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the APK to Android's installer, which shows its own confirm screen. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
