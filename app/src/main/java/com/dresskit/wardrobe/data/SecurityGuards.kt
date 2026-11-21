package com.dresskit.wardrobe.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Shared network, URL, and cryptographic security guards for Dolabım.
 * Protects against SSRF, cleartext downgrade, open redirects, unsafe external schemes,
 * and timing side-channels.
 */
object SecurityGuards {

    private val ALLOWED_REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    fun isSafePublicHttpsUrl(raw: String, maxLen: Int = 600): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > maxLen) return false
        for (ch in trimmed) {
            if (ch.isWhitespace() || ch.code < 0x20 || ch.code == 0x7F) return false
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        val port = uri.port
        if (port != -1 && port != 443) return false

        val host = uri.host?.trim()?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (host.isEmpty() || isDisallowedHostLiteral(host)) return false
        return true
    }

    private fun isDisallowedHostLiteral(host: String): Boolean {
        if (host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".home.arpa")
        ) {
            return true
        }

        val cleanV6 = host.removePrefix("[").removeSuffix("]")
        if (cleanV6.contains(':')) {
            val lower = cleanV6.lowercase(Locale.ROOT)
            if (lower == "::" ||
                lower == "::1" ||
                lower.startsWith("fe80:") ||
                lower.startsWith("fc") ||
                lower.startsWith("fd") ||
                lower.startsWith("ff")
            ) {
                return true
            }
            return runCatching {
                val addr = InetAddress.getByName(cleanV6)
                !isPublicInetAddress(addr)
            }.getOrDefault(true)
        }

        val ipv4Parts = host.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { part -> part.toIntOrNull() in 0..255 }) {
            val b0 = ipv4Parts[0].toInt()
            val b1 = ipv4Parts[1].toInt()
            if (b0 == 0 || b0 == 10 || b0 == 127) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 172 && b1 in 16..31) return true
            if (b0 == 192 && b1 == 168) return true
            if (b0 == 100 && b1 in 64..127) return true
            if (b0 >= 224) return true
            return false
        }

        // Reject single-label intranet hostnames without a TLD dot
        return !host.contains('.')
    }

    fun isPublicHostResolved(host: String): Boolean {
        val clean = host.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (clean.isEmpty() || isDisallowedHostLiteral(clean)) return false
        return runCatching {
            val addrs = InetAddress.getAllByName(clean)
            addrs.isNotEmpty() && addrs.all { isPublicInetAddress(it) }
        }.getOrDefault(false)
    }

    private fun isPublicInetAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress
        ) {
            return false
        }
        val bytes = addr.address
        if (addr is Inet4Address && bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0 || b0 == 10 || b0 == 127) return false
            if (b0 == 169 && b1 == 254) return false
            if (b0 == 172 && b1 in 16..31) return false
            if (b0 == 192 && b1 == 168) return false
            if (b0 == 100 && b1 in 64..127) return false
            if (b0 >= 224) return false
        } else if (addr is Inet6Address && bytes.size == 16) {
            val firstByte = bytes[0].toInt() and 0xFF
            // Unique Local Address (fc00::/7)
            if ((firstByte and 0xFE) == 0xFC) return false
        }
        return true
    }

    fun openSafeHttpsGet(
        initialUrl: String,
        allowedHosts: Set<String>? = null,
        maxRedirects: Int = 4,
        connectTimeoutMs: Int = 7000,
        readTimeoutMs: Int = 7000,
        headers: Map<String, String> = emptyMap()
    ): HttpsURLConnection? {
        var currentUrl = initialUrl.trim()
        for (hop in 0..maxRedirects) {
            if (!isSafePublicHttpsUrl(currentUrl, maxLen = 2048)) return null
            val uri = runCatching { URI(currentUrl) }.getOrNull() ?: return null
            val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
            if (allowedHosts != null && host !in allowedHosts) return null
            if (!isPublicHostResolved(host)) return null

            var conn: HttpsURLConnection? = null
            try {
                conn = (URL(currentUrl).openConnection() as? HttpsURLConnection)?.apply {
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                } ?: return null

                val code = conn.responseCode
                if (code in ALLOWED_REDIRECT_CODES && hop < maxRedirects) {
                    val location = conn.getHeaderField("Location")?.trim()
                    conn.disconnect()
                    if (location.isNullOrEmpty()) return null
                    currentUrl = runCatching { uri.resolve(location).toString() }.getOrNull() ?: return null
                    continue
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    return null
                }
                return conn
            } catch (_: Exception) {
                conn?.disconnect()
                return null
            }
        }
        return null
    }

    fun openExternalUrlSafely(context: Context, rawUrl: String): Boolean {
        val clean = rawUrl.trim()
        if (!isSafePublicHttpsUrl(clean, maxLen = 1000)) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun constantTimeEqualsHex(a: String, b: String): Boolean {
        val left = a.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
        val right = b.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(left, right)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
