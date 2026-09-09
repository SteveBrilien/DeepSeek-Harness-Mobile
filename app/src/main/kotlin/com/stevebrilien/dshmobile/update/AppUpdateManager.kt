package com.stevebrilien.dshmobile.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.stevebrilien.dshmobile.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdateManager(private val context: Context) {
    companion object {
        const val UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/SteveBrilien/DeepSeek-Harness-Mobile/main/release/update.json"
    }

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val notes: String,
    )

    data class CheckResult(val release: Release?, val updateAvailable: Boolean, val message: String)

    fun check(): CheckResult {
        val connection = open(UPDATE_MANIFEST_URL)
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        check(connection.responseCode in 200..299) { "更新清单 HTTP ${connection.responseCode}" }
        val json = JSONObject(text)
        val release = Release(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            apkUrl = json.getString("apkUrl"),
            sha256 = json.getString("sha256").lowercase(),
            notes = json.optString("notes"),
        )
        require(release.apkUrl.startsWith("https://")) { "APK URL 必须使用 HTTPS" }
        require(release.sha256.matches(Regex("[0-9a-f]{64}"))) { "更新清单 SHA-256 无效" }
        val available = release.versionCode > BuildConfig.VERSION_CODE
        return CheckResult(
            release = release,
            updateAvailable = available,
            message = if (available) "发现 ${release.versionName}" else "当前已是最新版本（${BuildConfig.VERSION_NAME}）",
        )
    }

    fun downloadAndVerify(release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { check(exists() || mkdirs()) }
        val partial = File(dir, ".dsh-mobile-${release.versionCode}.apk.partial")
        val final = File(dir, "dsh-mobile-${release.versionCode}.apk")
        partial.delete()
        val connection = open(release.apkUrl)
        check(connection.responseCode in 200..299) { "APK 下载 HTTP ${connection.responseCode}" }
        connection.inputStream.use { input -> partial.outputStream().buffered().use { output -> input.copyTo(output) } }
        val actual = sha256(partial)
        check(actual == release.sha256) { "APK SHA-256 校验失败" }
        final.delete()
        check(partial.renameTo(final)) { "无法发布已校验安装包" }
        return final
    }

    fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "DSH-Mobile/${BuildConfig.VERSION_NAME}")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
