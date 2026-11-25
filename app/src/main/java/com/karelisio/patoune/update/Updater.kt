package com.karelisio.patoune.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.karelisio.patoune.BuildConfig
import com.karelisio.patoune.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val changelog: String, val apkUrl: String, val sizeBytes: Long)

sealed interface DownloadState {
    data class Progress(val fraction: Float) : DownloadState
    data class Done(val file: File) : DownloadState
}

/** Mise à jour in-app depuis les GitHub Releases (au plus toutes les 6 h). */
class Updater(private val context: Context, private val settings: Settings) {

    suspend fun check(force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - settings.current().lastUpdateCheck < SIX_HOURS) return@withContext null
        runCatching {
            val conn = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000; conn.readTimeout = 10_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            settings.setUpdateCheck(now)
            val o = Json.parseToJsonElement(body).jsonObject
            val tag = o["tag_name"]!!.jsonPrimitive.content.removePrefix("v")
            val asset = o["assets"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["name"]!!.jsonPrimitive.content.endsWith(".apk") }
                ?: return@runCatching null
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@runCatching null
            Release(
                version = tag,
                changelog = o["body"]?.jsonPrimitive?.content.orEmpty(),
                apkUrl = asset["browser_download_url"]!!.jsonPrimitive.content,
                sizeBytes = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
        }.getOrNull()
    }

    fun download(release: Release): Flow<DownloadState> = flow {
        val out = File(context.cacheDir, "update.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var lastEmit = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    read += n
                    if (total > 0 && read - lastEmit > 256 * 1024) {
                        lastEmit = read
                        emit(DownloadState.Progress((read.toFloat() / total).coerceIn(0f, 1f)))
                    }
                }
            }
        }
        emit(DownloadState.Progress(1f))
        emit(DownloadState.Done(out))
    }.flowOn(Dispatchers.IO)

    /** Installation via PackageInstaller (une confirmation système est demandée). */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("patoune.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val intent = Intent(context, InstallReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        }
    }

    fun vitrineInstalled(): Boolean = runCatching { context.packageManager.getPackageInfo(VITRINE, 0); true }.getOrDefault(false)

    fun openInVitrine() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vitrine://app/${context.packageName.removeSuffix(".debug")}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    companion object {
        const val VITRINE = "com.karelisio.vitrine"
        private const val SIX_HOURS = 6 * 3_600_000L

        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
            val r = parts(remote); val l = parts(local)
            for (i in 0 until 3) if (r[i] != l[i]) return r[i] > l[i]
            return false
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
