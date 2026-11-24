package com.dresskit.wardrobe.data

import com.dresskit.wardrobe.model.GifPreset
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates 24 crisp offline animated GIF89a reaction stickers and validates external Tenor/Giphy GIF URLs.
 */
object AnimatedGifGenerator {

    private val ALLOWED_GIF_HOSTS = setOf(
        "media.tenor.com",
        "c.tenor.com",
        "media.giphy.com",
        "media0.giphy.com",
        "media1.giphy.com",
        "media2.giphy.com",
        "media3.giphy.com",
        "media4.giphy.com",
        "i.giphy.com"
    )

    private data class GifSpec(
        val id: String,
        val labelTr: String,
        val labelEn: String,
        val bgRgb: Int,
        val cardRgb: Int,
        val fgRgb: Int,
        val accentRgb: Int,
        val glyph: String,
        val badgeText: String
    )

    private val SPECS = listOf(
        GifSpec("heart_pop", "Bayıldım", "Love", 0x1A0F14, 0x2E1521, 0xF43F5E, 0xFDA4AF, "heart", "LOVE"),
        GifSpec("fire_look", "Çok İyi", "Fire", 0x1A110B, 0x2F1B10, 0xF97316, 0xFACC15, "flame", "FIRE"),
        GifSpec("star_drip", "Yıldız Kombin", "Iconic", 0x111119, 0x1F2032, 0xFACC15, 0xFEF08A, "star", "10/10"),
        GifSpec("sparkle_chic", "Çok Şık", "Chic", 0x101418, 0x1A242D, 0xE2E8F0, 0x38BDF8, "sparkle", "CHIC"),
        GifSpec("crown_fit", "Kral/Kraliçe", "Slay", 0x16120B, 0x292011, 0xF59E0B, 0xFDE68A, "crown", "SLAY"),
        GifSpec("check_ok", "Onaylandı", "Approved", 0x0B1612, 0x132A21, 0x10B981, 0x6EE7B7, "check", "YES!"),
        GifSpec("flash_cam", "Paparazzi", "Snap", 0x121214, 0x222228, 0xFAFAFA, 0x34D399, "camera", "SNAP"),
        GifSpec("drip_bag", "Sepete At", "Cop It", 0x131118, 0x241F30, 0xA855F7, 0xE9D5FF, "bag", "COP"),
        GifSpec("vibe_sun", "Cool Vibe", "Cool", 0x0D141C, 0x162436, 0x38BDF8, 0xBAE6FD, "shades", "COOL"),
        GifSpec("party_win", "Kutlama", "Party", 0x181018, 0x2D192D, 0xEC4899, 0xFDE047, "confetti", "VIBE"),
        GifSpec("wow_shock", "Efsane", "Whoa", 0x141410, 0x262619, 0xEAB308, 0xFFFFFF, "bolt", "WOW"),
        GifSpec("mood_cozy", "Sıcak Vibe", "Cozy", 0x18130E, 0x2A2018, 0xD97706, 0xFCD34D, "coffee", "COZY"),
        GifSpec("hanger_fit", "Tam Kombin", "Fit", 0x101012, 0x1F1F24, 0xFAFAFA, 0xD4A373, "hanger", "FIT"),
        GifSpec("diamond_lux", "Lüks", "Luxe", 0x0B1519, 0x132830, 0x22D3EE, 0xCFFAFE, "diamond", "LUXE"),
        GifSpec("eye_stare", "Göz Kamaştırdı", "Serve", 0x141016, 0x251B2B, 0xC084FC, 0xF5D0FE, "sparkle", "SERVE"),
        GifSpec("note_vibe", "Ritim", "Mood", 0x101614, 0x1A2824, 0x34D399, 0xA7F3D0, "pulse", "MOOD"),
        GifSpec("kiss_blush", "Muhteşem", "Chef's Kiss", 0x1B0F13, 0x311820, 0xFB7185, 0xFECDD3, "heart", "MUAH"),
        GifSpec("moon_night", "Gece Kombini", "Night", 0x0B0C14, 0x151829, 0x93C5FD, 0xFDE047, "moon", "NIGHT"),
        GifSpec("sun_day", "Gündüz Stili", "Sunny", 0x19150A, 0x2C2410, 0xFBBF24, 0xFEF3C7, "sun", "DAY"),
        GifSpec("ribbon_cute", "Tatlı", "Cute", 0x191015, 0x2F1B26, 0xF472B6, 0xFBCFE8, "star", "CUTE"),
        GifSpec("street_tag", "Streetwear", "Street", 0x0E0E10, 0x1D1D22, 0xFAFAFA, 0xF97316, "bolt", "STREET"),
        GifSpec("vintage_sepia", "Retro", "Vintage", 0x171410, 0x2B241B, 0xD4A373, 0xFAEDCD, "hanger", "RETRO"),
        GifSpec("clean_mono", "Minimal", "Clean", 0x09090B, 0x18181C, 0xFAFAFA, 0x94A3B8, "check", "CLEAN"),
        GifSpec("plus_one", "Destek", "Boost", 0x0F1512, 0x1A2922, 0x4ADE80, 0xBBF7D0, "confetti", "+100")
    )

    val presets: List<GifPreset> by lazy {
        SPECS.map { spec ->
            GifPreset(
                id = spec.id,
                labelTr = spec.labelTr,
                labelEn = spec.labelEn,
                dataUrl = encodeSpecGif(spec)
            )
        }
    }

    fun isSafeExternalGifUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 600) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host in ALLOWED_GIF_HOSTS
    }

    fun isSafeGifDataUrl(raw: String, maxLen: Int = 260_000): Boolean {
        if (!raw.startsWith("data:image/gif;base64,") || raw.length > maxLen) return false
        val body = raw.substringAfter("base64,", "")
        return body.startsWith("R0lGOD") && body.length <= maxLen
    }

    private fun encodeSpecGif(spec: GifSpec): String {
        val w = 48
        val h = 48
        val frames = 6
        val palette = intArrayOf(
            spec.bgRgb,
            spec.cardRgb,
            0x2B2B34,
            spec.fgRgb,
            spec.accentRgb,
            0xFFFFFF,
            0x18181D,
            0x09090B,
            0xD4D4D8,
            0xA1A1AA,
            spec.bgRgb,
            spec.cardRgb,
            spec.fgRgb,
            spec.accentRgb,
            0xFFFFFF,
            0x18181D
        )

        val baos = ByteArrayOutputStream(8192)
        // Header: GIF89a
        baos.write("GIF89a".toByteArray(Charsets.US_ASCII))
        // Logical Screen Descriptor
        writeShortLe(baos, w)
        writeShortLe(baos, h)
        baos.write(0xB3) // GCT=1, colorRes=3 (101), sort=0, size=3 (2^(3+1)=16 colors)
        baos.write(0x00) // bg color index
        baos.write(0x00) // pixel aspect ratio
        // Global Color Table (16 * 3 = 48 bytes)
        for (c in palette) {
            baos.write((c ushr 16) and 0xFF)
            baos.write((c ushr 8) and 0xFF)
            baos.write(c and 0xFF)
        }
        // Netscape 2.0 infinite loop application extension
        baos.write(0x21)
        baos.write(0xFF)
        baos.write(0x0B)
        baos.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        baos.write(0x03)
        baos.write(0x01)
        writeShortLe(baos, 0)
        baos.write(0x00)

        for (f in 0 until frames) {
            // Graphics Control Extension
            baos.write(0x21)
            baos.write(0xF9)
            baos.write(0x04)
            baos.write(0x04) // disposal = 1
            writeShortLe(baos, 13) // 130ms per frame
            baos.write(0x00)
            baos.write(0x00)

            // Image Descriptor
            baos.write(0x2C)
            writeShortLe(baos, 0)
            writeShortLe(baos, 0)
            writeShortLe(baos, w)
            writeShortLe(baos, h)
            baos.write(0x00) // No local color table

            val pixels = renderFramePixels(w, h, f, frames, spec)
            writeLzwPixels(baos, pixels, minCodeSize = 4)
        }

        // Trailer
        baos.write(0x3B)
        val b64 = encodeBase64(baos.toByteArray())
        return "data:image/gif;base64,$b64"
    }

    private fun encodeBase64(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val hasB2 = i + 2 < data.size
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0
            sb.append(alphabet[b0 ushr 2])
            sb.append(alphabet[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            sb.append(if (hasB1) alphabet[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            sb.append(if (hasB2) alphabet[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    private fun renderFramePixels(w: Int, h: Int, frame: Int, totalFrames: Int, spec: GifSpec): ByteArray {
        val px = ByteArray(w * h)
        val phase = (frame.toDouble() / totalFrames.toDouble()) * Math.PI * 2.0
        val bobY = (sin(phase) * 2.2).toFloat()
        val pulseR = 1.0f + 0.14f * cos(phase).toFloat()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val dxBorder = minOf(x, w - 1 - x, y, h - 1 - y)
                var color = 0
                if (dxBorder >= 2 && dxBorder <= 3) color = 2
                else if (dxBorder > 3) color = 1

                val cx = x - 24f
                val cy = y - (21f + bobY)

                when (spec.glyph) {
                    "heart" -> {
                        val sx = (cx / (9.5f * pulseR))
                        val sy = (-cy / (9.5f * pulseR))
                        val eq = (sx * sx + sy * sy - 1f)
                        val v = eq * eq * eq - sx * sx * sy * sy * sy
                        if (v <= 0f) color = if (v > -0.22f) 4 else 3
                    }
                    "star" -> {
                        val r = sqrt(cx * cx + cy * cy)
                        val ang = kotlin.math.atan2(cy.toDouble(), cx.toDouble()) + phase * 0.2
                        val edge = (9.5 + 4.8 * cos(5.0 * ang)) * pulseR
                        if (r <= edge) color = if (r <= edge * 0.45) 5 else 3
                    }
                    "flame" -> {
                        val sway = sin(phase + y * 0.25) * 1.8f
                        val fx = (cx - sway.toFloat())
                        val fy = y - (25f + bobY)
                        if (fy in -13f..10f) {
                            val halfW = ((10f - fy) * 0.45f) * (1f - (fy + 13f) / 30f) * pulseR * 1.4f
                            if (abs(fx) <= halfW) {
                                color = if (abs(fx) <= halfW * 0.48f && fy > -5f) 4 else 3
                            }
                        }
                    }
                    "crown" -> {
                        val rx = cx
                        val ry = cy
                        if (ry in 2f..8f && abs(rx) <= 11f) color = 3
                        else if (ry in -8f..2f && abs(rx) <= 11f) {
                            val col = ((abs(rx) / 5.5f)).toInt()
                            val tip = if (abs(rx) <= 2f) -8f else if (abs(rx) > 7f) -7f else 1f
                            if (ry >= tip || col == 2) color = if (ry < -5f) 4 else 3
                        }
                    }
                    "check" -> {
                        val tx = cx
                        val ty = cy
                        val leg1 = abs((tx + 3f) - (ty - 4f)) < 2.2f && tx in -9f..-1f
                        val leg2 = abs((tx + 1f) + (ty - 4f) * 1.0f) < 2.2f && tx in -1f..10f
                        val ring = abs(sqrt(tx * tx + ty * ty) - 12f * pulseR) < 1.8f
                        if (leg1 || leg2) color = 5 else if (ring) color = 3
                    }
                    "hanger" -> {
                        val hookR = sqrt(cx * cx + (cy + 7f) * (cy + 7f))
                        if (hookR in 1.6f..3.8f && cy < -5f) color = 4
                        val bar = abs(cy - 6f) < 1.3f && abs(cx) <= 12f
                        val leftSlope = abs(cy - (-2f + abs(cx) * 0.65f)) < 1.3f && abs(cx) <= 12f
                        if (bar || leftSlope) color = 3
                    }
                    else -> {
                        val r = sqrt(cx * cx + cy * cy)
                        if (r <= 10.5f * pulseR) {
                            color = if (r <= 5.5f * pulseR) 4 else 3
                        }
                    }
                }

                // Animated corner sparkles rotating around perimeter
                for (s in 0 until 3) {
                    val a = phase + s * 2.094
                    val sx = 24 + (cos(a) * 17.5).toInt()
                    val sy = 23 + (sin(a) * 17.5).toInt()
                    if (abs(x - sx) + abs(y - sy) <= 1) color = 5
                }

                // Bottom pill ribbon indicator (pulsing width across frames)
                if (y in 38..42 && abs(x - 24) <= (12 + (frame % 3) * 2)) {
                    color = if (y == 40 && (x % 3 != 0)) 5 else 4
                }

                px[y * w + x] = color.toByte()
            }
        }
        return px
    }

    private fun writeShortLe(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun writeLzwPixels(out: ByteArrayOutputStream, pixels: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)
        val clearCode = 1 shl minCodeSize // 16
        val eoiCode = clearCode + 1 // 17
        val codeBits = minCodeSize + 1 // 5

        val lzwBytes = ByteArrayOutputStream(pixels.size * 2)
        var bitBuf = 0
        var bitCount = 0

        fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += codeBits
            while (bitCount >= 8) {
                lzwBytes.write(bitBuf and 0xFF)
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        // Flush clearCode every 10 pixels so the decoder string table never overflows 5 bits
        emit(clearCode)
        var countSinceClear = 0
        for (p in pixels) {
            emit(p.toInt() and 0x0F)
            countSinceClear++
            if (countSinceClear >= 10) {
                emit(clearCode)
                countSinceClear = 0
            }
        }
        emit(eoiCode)
        if (bitCount > 0) {
            lzwBytes.write(bitBuf and 0xFF)
        }

        val raw = lzwBytes.toByteArray()
        var pos = 0
        while (pos < raw.size) {
            val len = minOf(255, raw.size - pos)
            out.write(len)
            out.write(raw, pos, len)
            pos += len
        }
        out.write(0x00) // block terminator
    }
}
