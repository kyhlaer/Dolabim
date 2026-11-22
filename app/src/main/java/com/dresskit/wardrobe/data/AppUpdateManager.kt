package com.dresskit.wardrobe.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val expectedSha256: String?,
    val deltaPatchUrl: String? = null,
    val deltaPatchFileName: String? = null,
    val deltaPatchSizeBytes: Long = 0L
)

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int
    ) : UpdateDownloadState()

    data object Verifying : UpdateDownloadState()
    data class NeedsInstallPermission(val apkFile: File) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

/**
 * Handles in-app GitHub Release update checks, custom snooze scheduling, bounded HTTPS APK
 * download, cryptographic SHA-256 & signing-certificate verification, and secure handoff to
 * Android's system package installer.
 */
object AppUpdateManager {

    private const val RELEASE_API_URL =
        "https://api.github.com/repos/kyhlaer/Dolabim/releases/latest"
    private val API_ALLOWED_HOSTS = setOf("api.github.com")
    private val APK_ALLOWED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )
    private const val UPDATES_DIR = "apk_updates"
    private const val APK_FILENAME = "dolabim_update.apk"
    private const val PATCH_FILENAME = "dolabim_update.dpatch"
    private const val MAX_JSON_CHARS = 128_000
    private const val MAX_APK_BYTES = 260L * 1024L * 1024L
    private const val PREFS_NAME = "dolabim_update_prefs"
    private const val KEY_SNOOZE_UNTIL_MS = "snooze_until_ms"
    private const val KEY_SNOOZED_VERSION = "snoozed_version"

    private val SHA256_REGEX = Regex("""(?i)SHA-?256\s*[:=]\s*([0-9a-f]{64})""")

    fun isVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val currentParts = parseVersionParts(currentVersion)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
        val len = max(remoteParts.size, currentParts.size)
        for (i in 0 until len) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (clean.isEmpty()) return emptyList()
        return clean.split('.').mapNotNull { seg ->
            seg.trim().toIntOrNull()
        }
    }

    fun extractSha256FromReleaseBody(body: String): String? {
        val match = SHA256_REGEX.find(body) ?: return null
        return match.groupValues.getOrNull(1)?.lowercase(Locale.ROOT)
    }

    fun getInstalledVersionName(context: Context): String {
        return runCatching {
            getInstalledPackageInfo(context, 0)?.versionName?.trim()?.ifEmpty { null }
        }.getOrNull() ?: "1.43.1"
    }

    fun getInstalledVersionCode(context: Context): Long {
        return runCatching {
            val info = getInstalledPackageInfo(context, 0)
            if (info != null) PackageInfoCompat.getLongVersionCode(info) else 1L
        }.getOrDefault(1L)
    }

    fun isSnoozedForVersion(context: Context, remoteVersion: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val snoozedVer = prefs.getString(KEY_SNOOZED_VERSION, null)?.trim() ?: return false
        val untilMs = prefs.getLong(KEY_SNOOZE_UNTIL_MS, 0L)
        return snoozedVer == remoteVersion.trim() && System.currentTimeMillis() < untilMs
    }

    fun snoozeUpdate(context: Context, remoteVersion: String, durationMillis: Long) {
        val clamped = durationMillis.coerceIn(60_000L, 90L * 86_400_000L)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNOOZED_VERSION, remoteVersion.trim())
            .putLong(KEY_SNOOZE_UNTIL_MS, System.currentTimeMillis() + clamped)
            .apply()
    }

    fun clearSnooze(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SNOOZED_VERSION)
            .remove(KEY_SNOOZE_UNTIL_MS)
            .apply()
    }

    private fun updatesDir(context: Context): File {
        val dir = File(context.cacheDir, UPDATES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cleanStaleUpdateFiles(context: Context, maxAgeMs: Long = 86_400_000L) {
        runCatching {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            updatesDir(context).listFiles()?.forEach { f ->
                if (f.isFile && (f.name.endsWith(".part") || f.name.endsWith(".tmp") || f.name.endsWith(".dpatch") || f.lastModified() < cutoff)) {
                    f.delete()
                }
            }
        }
    }

    suspend fun checkForUpdate(
        context: Context,
        ignoreSnooze: Boolean = false
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val installedVer = getInstalledVersionName(context)
        val conn = SecurityGuards.openSafeHttpsGet(
            initialUrl = RELEASE_API_URL,
            allowedHosts = API_ALLOWED_HOSTS,
            maxRedirects = 2,
            connectTimeoutMs = 5500,
            readTimeoutMs = 5500,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "Dolabim-Android/$installedVer"
            )
        ) ?: return@withContext null

        val rawJson = try {
            val buf = CharArray(MAX_JSON_CHARS)
            var total = 0
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (total < MAX_JSON_CHARS) {
                    val n = reader.read(buf, total, MAX_JSON_CHARS - total)
                    if (n <= 0) break
                    total += n
                }
            }
            if (total > 0) String(buf, 0, total) else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        } ?: return@withContext null

        return@withContext try {
            val obj = JSONObject(rawJson)
            if (obj.optBoolean("draft", false)) return@withContext null
            val tagName = obj.optString("tag_name", "").trim()
            val cleanVersion = tagName.removePrefix("v").removePrefix("V")
            if (cleanVersion.isEmpty() || !isVersionNewer(cleanVersion, installedVer)) {
                return@withContext null
            }
            if (!ignoreSnooze && isSnoozedForVersion(context, cleanVersion)) {
                return@withContext null
            }

            val assets = obj.optJSONArray("assets") ?: return@withContext null
            var bestApkUrl = ""
            var bestApkName = ""
            var bestApkSize = 0L
            val expectedDeltaName = "Dolabim-patch-v$installedVer-to-v$cleanVersion.dpatch"
            var bestDeltaUrl: String? = null
            var bestDeltaName: String? = null
            var bestDeltaSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "").trim()
                val downloadUrl = asset.optString("browser_download_url", "").trim()
                val size = asset.optLong("size", 0L)
                if (assetName.endsWith(".apk", ignoreCase = true) &&
                    SecurityGuards.isSafePublicHttpsUrl(downloadUrl, maxLen = 1000) &&
                    size in 1L..MAX_APK_BYTES
                ) {
                    if (bestApkUrl.isEmpty() || assetName.contains("Dolabim", ignoreCase = true)) {
                        bestApkUrl = downloadUrl
                        bestApkName = assetName
                        bestApkSize = size
                    }
                } else if (assetName.equals(expectedDeltaName, ignoreCase = true) &&
                    SecurityGuards.isSafePublicHttpsUrl(downloadUrl, maxLen = 1000) &&
                    size in 1L..MAX_APK_BYTES
                ) {
                    bestDeltaUrl = downloadUrl
                    bestDeltaName = assetName
                    bestDeltaSize = size
                }
            }

            if (bestApkUrl.isEmpty()) return@withContext null

            val baseReadable = runCatching {
                val src = context.applicationInfo?.sourceDir
                !src.isNullOrBlank() && File(src).let { it.isFile && it.canRead() }
            }.getOrDefault(false)
            val useDelta = baseReadable && bestDeltaUrl != null && bestDeltaSize in 1L until bestApkSize

            val bodyText = obj.optString("body", "").trim().take(4000)
            val titleText = obj.optString("name", "").trim().ifEmpty { "Dolabım $tagName" }.take(120)

            AppUpdateInfo(
                versionName = cleanVersion,
                tagName = tagName,
                releaseTitle = titleText,
                releaseNotes = bodyText,
                apkDownloadUrl = bestApkUrl,
                apkFileName = bestApkName,
                apkSizeBytes = bestApkSize,
                expectedSha256 = extractSha256FromReleaseBody(bodyText),
                deltaPatchUrl = if (useDelta) bestDeltaUrl else null,
                deltaPatchFileName = if (useDelta) bestDeltaName else null,
                deltaPatchSizeBytes = if (useDelta) bestDeltaSize else 0L
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadAndVerifyApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long, percent: Int) -> Unit,
        onVerifying: () -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = updatesDir(context)
        val partFile = File(dir, "$APK_FILENAME.part")
        val finalFile = File(dir, APK_FILENAME)
        partFile.delete()
        finalFile.delete()

        if (info.deltaPatchUrl != null && info.deltaPatchSizeBytes > 0L) {
            val deltaRes = downloadAndApplyDeltaPatch(context, dir, info, finalFile, onProgress, onVerifying)
            if (deltaRes.isSuccess) {
                clearSnooze(context)
                return@withContext deltaRes
            }
            partFile.delete()
            finalFile.delete()
            onProgress(0L, info.apkSizeBytes.coerceAtLeast(1L), 0)
        }

        val conn = SecurityGuards.openSafeHttpsGet(
            initialUrl = info.apkDownloadUrl,
            allowedHosts = APK_ALLOWED_HOSTS,
            maxRedirects = 5,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 30_000,
            headers = mapOf(
                "Accept" to "application/octet-stream",
                "User-Agent" to "Dolabim-Android/${getInstalledVersionName(context)}"
            )
        ) ?: return@withContext Result.failure(SecurityException("Güvenli indirme bağlantısı kurulamadı."))

        val totalExpected = conn.contentLengthLong.takeIf { it in 1L..MAX_APK_BYTES }
            ?: info.apkSizeBytes.coerceAtLeast(1L)
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = 0L

        try {
            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(16_384)
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            throw SecurityException("APK boyutu güvenlik sınırını aştı.")
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        val pct = ((downloaded * 100L) / totalExpected).toInt().coerceIn(0, 100)
                        if (pct != lastPercent) {
                            lastPercent = pct
                            onProgress(downloaded, totalExpected, pct)
                        }
                    }
                    output.flush()
                }
            }
        } catch (t: Throwable) {
            partFile.delete()
            return@withContext Result.failure(t)
        } finally {
            conn.disconnect()
        }

        if (downloaded < 1024L || !partFile.renameTo(finalFile)) {
            partFile.delete()
            return@withContext Result.failure(IllegalStateException("APK dosyası kaydedilemedi."))
        }

        onVerifying()

        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        val expectedSha = info.expectedSha256
        if (expectedSha != null && !SecurityGuards.constantTimeEqualsHex(actualSha, expectedSha)) {
            finalFile.delete()
            return@withContext Result.failure(SecurityException("SHA-256 bütünlük doğrulaması başarısız."))
        }

        val sigOk = verifyApkSignatureAndIdentity(context, finalFile)
        if (!sigOk) {
            finalFile.delete()
            return@withContext Result.failure(SecurityException("APK imza sertifikası veya paket kimliği eşleşmedi."))
        }

        clearSnooze(context)
        Result.success(finalFile)
    }

    private suspend fun downloadAndApplyDeltaPatch(
        context: Context,
        dir: File,
        info: AppUpdateInfo,
        finalFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, percent: Int) -> Unit,
        onVerifying: () -> Unit
    ): Result<File> {
        val deltaUrl = info.deltaPatchUrl ?: return Result.failure(IllegalArgumentException("No delta URL"))
        val basePath = context.applicationInfo?.sourceDir
            ?: return Result.failure(IllegalStateException("Base APK unavailable"))
        val baseFile = File(basePath)
        val patchPart = File(dir, "$PATCH_FILENAME.part")
        val patchFile = File(dir, PATCH_FILENAME)
        patchPart.delete()
        patchFile.delete()

        val conn = SecurityGuards.openSafeHttpsGet(
            initialUrl = deltaUrl,
            allowedHosts = APK_ALLOWED_HOSTS,
            maxRedirects = 5,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 30_000,
            headers = mapOf(
                "Accept" to "application/octet-stream",
                "User-Agent" to "Dolabim-Android/${getInstalledVersionName(context)}"
            )
        ) ?: return Result.failure(SecurityException("Delta indirme bağlantısı kurulamadı."))

        val totalExpected = conn.contentLengthLong.takeIf { it in 1L..MAX_APK_BYTES }
            ?: info.deltaPatchSizeBytes.coerceAtLeast(1L)
        var downloaded = 0L

        return try {
            conn.inputStream.use { input ->
                FileOutputStream(patchPart).use { output ->
                    val buffer = ByteArray(16_384)
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            throw SecurityException("Yama boyutu sınırı aşıldı.")
                        }
                        output.write(buffer, 0, read)
                        val pct = ((downloaded * 100L) / totalExpected).toInt().coerceIn(0, 100)
                        if (pct != lastPercent) {
                            lastPercent = pct
                            onProgress(downloaded, totalExpected, pct)
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded < 80L || !patchPart.renameTo(patchFile)) {
                throw IllegalStateException("Yama dosyası kaydedilemedi.")
            }

            onVerifying()

            val patchRes = ApkDeltaPatcher.applyPatch(
                baseApkFile = baseFile,
                patchFile = patchFile,
                outputFile = finalFile,
                expectedTargetSha256Hex = info.expectedSha256,
                maxOutputBytes = MAX_APK_BYTES
            )
            if (patchRes.isFailure) {
                throw patchRes.exceptionOrNull() ?: IllegalStateException("Yama uygulanamadı.")
            }

            if (!verifyApkSignatureAndIdentity(context, finalFile)) {
                finalFile.delete()
                throw SecurityException("Yamalı APK imza doğrulaması başarısız.")
            }

            Result.success(finalFile)
        } catch (t: Throwable) {
            patchPart.delete()
            patchFile.delete()
            finalFile.delete()
            Result.failure(t)
        } finally {
            patchPart.delete()
            patchFile.delete()
            conn.disconnect()
        }
    }

    private fun verifyApkSignatureAndIdentity(context: Context, apkFile: File): Boolean {
        return runCatching {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
            if (archiveInfo.packageName != context.packageName) return false

            val archiveVer = PackageInfoCompat.getLongVersionCode(archiveInfo)
            val installedVer = getInstalledVersionCode(context)
            if (archiveVer < installedVer) return false

            val installedInfo = getInstalledPackageInfo(context, flags) ?: return false
            val archiveCertHashes = extractSigningCertSha256s(archiveInfo)
            val installedCertHashes = extractSigningCertSha256s(installedInfo)

            if (archiveCertHashes.isEmpty() || installedCertHashes.isEmpty()) return false
            archiveCertHashes.any { cand ->
                installedCertHashes.any { inst -> MessageDigest.isEqual(cand, inst) }
            }
        }.getOrDefault(false)
    }

    private fun getInstalledPackageInfo(context: Context, flags: Int): PackageInfo? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, flags)
        }
    }

    private fun extractSigningCertSha256s(pkgInfo: PackageInfo): List<ByteArray> {
        val md = MessageDigest.getInstance("SHA-256")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            val signers = if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
            } else {
                null
            }
            if (!signers.isNullOrEmpty()) {
                return signers.map { sig -> md.digest(sig.toByteArray()) }
            }
        }
        @Suppress("DEPRECATION")
        val legacy = pkgInfo.signatures
        return legacy?.map { sig -> md.digest(sig.toByteArray()) } ?: emptyList()
    }

    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun createInstallPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        return runCatching {
            val updatesRoot = updatesDir(context).canonicalPath + File.separator
            if (!apkFile.exists() || !apkFile.canonicalPath.startsWith(updatesRoot)) return false
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
