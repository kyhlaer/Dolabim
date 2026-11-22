package com.dresskit.wardrobe.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Play Store-style ZIP-entry-aware binary delta patcher (`DPATCH1`) for Dolabım APKs.
 * Reconstructs target APKs strictly using identical byte spans & compressed payloads from the
 * currently installed base APK (`base.apk`) plus a small GZIP-compressed delta patch stream,
 * verifying both source SHA-256 and reconstructed target SHA-256 byte-for-byte.
 */
object ApkDeltaPatcher {

    private val MAGIC = byteArrayOf('D'.code.toByte(), 'P'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(), 'C'.code.toByte(), 'H'.code.toByte(), '1'.code.toByte(), 0)
    private const val HEADER_SIZE = 80
    private const val OP_EOF = 0
    private const val OP_COPY = 1
    private const val OP_LITERAL = 2
    private const val MAX_LITERAL_CHUNK = 16 * 1024 * 1024
    private const val DEFAULT_MAX_OUTPUT_BYTES = 260L * 1024L * 1024L
    private const val IO_BUF_SIZE = 65_536

    data class PatchStats(
        val baseSize: Long,
        val targetSize: Long,
        val patchSize: Long,
        val copiedBytes: Long,
        val literalBytes: Long,
        val baseSha256Hex: String,
        val targetSha256Hex: String
    )

    private data class ZipEntrySlice(
        val name: String,
        val method: Int,
        val crc32: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val spanStart: Long,
        var spanEnd: Long = 0L,
        var dataStart: Long = 0L,
        var dataLength: Long = 0L,
        var spanSha256Hex: String = "",
        var dataSha256Hex: String = ""
    )

    fun computeFileSha256Bytes(file: File): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(IO_BUF_SIZE)
        FileInputStream(file).use { fis ->
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest()
    }

    fun computeFileSha256Hex(file: File): String =
        computeFileSha256Bytes(file).joinToString("") { "%02x".format(it) }

    fun applyPatch(
        baseApkFile: File,
        patchFile: File,
        outputFile: File,
        expectedTargetSha256Hex: String? = null,
        maxOutputBytes: Long = DEFAULT_MAX_OUTPUT_BYTES
    ): Result<String> {
        if (!baseApkFile.isFile || !baseApkFile.canRead()) {
            return Result.failure(IllegalStateException("Base APK unreadable"))
        }
        if (!patchFile.isFile || patchFile.length() < HEADER_SIZE) {
            return Result.failure(IllegalStateException("Delta patch truncated or missing"))
        }
        val baseLength = baseApkFile.length()
        if (baseLength <= 0L || baseLength > maxOutputBytes) {
            return Result.failure(SecurityException("Base APK size out of bounds"))
        }

        val tempOut = File(
            outputFile.parentFile ?: File("."),
            "${outputFile.name}.delta.tmp"
        )
        tempOut.delete()

        return try {
            val actualBaseSha = computeFileSha256Bytes(baseApkFile)
            val baseShaExpected = ByteArray(32)
            val targetShaExpected = ByteArray(32)
            val targetSize: Long

            FileInputStream(patchFile).use { rawFis ->
                val bis = BufferedInputStream(rawFis, IO_BUF_SIZE)
                val hdrDis = DataInputStream(bis)
                val magicBuf = ByteArray(MAGIC.size)
                hdrDis.readFully(magicBuf)
                if (!magicBuf.contentEquals(MAGIC)) {
                    throw SecurityException("Invalid delta patch magic")
                }
                hdrDis.readFully(baseShaExpected)
                hdrDis.readFully(targetShaExpected)
                targetSize = hdrDis.readLong()

                if (!MessageDigest.isEqual(actualBaseSha, baseShaExpected)) {
                    throw SecurityException("Base APK SHA-256 mismatch")
                }
                if (targetSize <= 0L || targetSize > maxOutputBytes) {
                    throw SecurityException("Target APK size out of bounds: $targetSize")
                }

                val targetShaHexHeader = targetShaExpected.joinToString("") { "%02x".format(it) }
                if (!expectedTargetSha256Hex.isNullOrBlank() &&
                    !SecurityGuards.constantTimeEqualsHex(targetShaHexHeader, expectedTargetSha256Hex.trim())
                ) {
                    throw SecurityException("Delta target SHA-256 does not match release digest")
                }

                val outDigest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                val ioBuf = ByteArray(IO_BUF_SIZE)

                RandomAccessFile(baseApkFile, "r").use { raf ->
                    BufferedOutputStream(FileOutputStream(tempOut), IO_BUF_SIZE).use { bos ->
                        GZIPInputStream(bis, IO_BUF_SIZE).use { gzis ->
                            val opIn = DataInputStream(gzis)
                            var sawEof = false
                            while (true) {
                                val op = opIn.read()
                                if (op < 0) break
                                if (op == OP_EOF) {
                                    sawEof = true
                                    break
                                }
                                when (op) {
                                    OP_COPY -> {
                                        val oldOffset = opIn.readLong()
                                        val length = opIn.readInt()
                                        if (oldOffset < 0L || length <= 0 || oldOffset > baseLength - length.toLong()) {
                                            throw SecurityException("Out-of-bounds COPY operation")
                                        }
                                        if (written + length.toLong() > targetSize) {
                                            throw SecurityException("Delta COPY exceeds declared target size")
                                        }
                                        raf.seek(oldOffset)
                                        var rem = length
                                        while (rem > 0) {
                                            val chunk = min(rem, ioBuf.size)
                                            raf.readFully(ioBuf, 0, chunk)
                                            bos.write(ioBuf, 0, chunk)
                                            outDigest.update(ioBuf, 0, chunk)
                                            rem -= chunk
                                            written += chunk.toLong()
                                        }
                                    }

                                    OP_LITERAL -> {
                                        val length = opIn.readInt()
                                        if (length <= 0 || length > MAX_LITERAL_CHUNK) {
                                            throw SecurityException("Invalid LITERAL length: $length")
                                        }
                                        if (written + length.toLong() > targetSize) {
                                            throw SecurityException("Delta LITERAL exceeds declared target size")
                                        }
                                        var rem = length
                                        while (rem > 0) {
                                            val chunk = min(rem, ioBuf.size)
                                            opIn.readFully(ioBuf, 0, chunk)
                                            bos.write(ioBuf, 0, chunk)
                                            outDigest.update(ioBuf, 0, chunk)
                                            rem -= chunk
                                            written += chunk.toLong()
                                        }
                                    }

                                    else -> throw SecurityException("Unknown delta opcode: $op")
                                }
                            }
                            if (!sawEof) {
                                throw EOFException("Missing DPATCH1 EOF marker")
                            }
                        }
                        bos.flush()
                    }
                }

                if (written != targetSize) {
                    throw SecurityException("Reconstructed size mismatch ($written vs $targetSize)")
                }

                val actualTargetSha = outDigest.digest()
                if (!MessageDigest.isEqual(actualTargetSha, targetShaExpected)) {
                    throw SecurityException("Reconstructed target SHA-256 mismatch")
                }
                val actualTargetHex = actualTargetSha.joinToString("") { "%02x".format(it) }
                if (!expectedTargetSha256Hex.isNullOrBlank() &&
                    !SecurityGuards.constantTimeEqualsHex(actualTargetHex, expectedTargetSha256Hex.trim())
                ) {
                    throw SecurityException("Reconstructed target SHA-256 does not match release digest")
                }

                outputFile.delete()
                if (!tempOut.renameTo(outputFile)) {
                    throw IllegalStateException("Could not finalize patched APK")
                }
                Result.success(actualTargetHex)
            }
        } catch (t: Throwable) {
            tempOut.delete()
            outputFile.delete()
            Result.failure(t)
        }
    }

    fun createPatch(
        baseApkFile: File,
        targetApkFile: File,
        patchFile: File
    ): Result<PatchStats> {
        if (!baseApkFile.isFile || !targetApkFile.isFile) {
            return Result.failure(IllegalArgumentException("Base or target file missing"))
        }
        return try {
            val baseSha = computeFileSha256Bytes(baseApkFile)
            val targetSha = computeFileSha256Bytes(targetApkFile)
            val targetSize = targetApkFile.length()

            val baseParsed = indexZipSlices(baseApkFile)
            val targetParsed = indexZipSlices(targetApkFile)

            val baseByName = HashMap<String, ZipEntrySlice>(baseParsed.size * 2 + 1)
            val baseByPayloadKey = HashMap<String, MutableList<ZipEntrySlice>>(baseParsed.size * 2 + 1)
            for (b in baseParsed) {
                baseByName.putIfAbsent(b.name, b)
                if (b.dataLength >= 128L) {
                    val key = "${b.crc32}:${b.dataLength}"
                    baseByPayloadKey.getOrPut(key) { ArrayList(2) }.add(b)
                }
            }

            // Build sorted non-overlapping Copy/Literal instructions covering [0, targetSize)
            val ops = ArrayList<RawOp>(targetParsed.size * 2 + 16)
            var cursor = 0L

            for (t in targetParsed) {
                if (t.spanStart > cursor) {
                    ops.add(RawOp.Literal(cursor, t.spanStart - cursor))
                    cursor = t.spanStart
                }
                if (t.spanStart < cursor) continue // Defensive guard for overlapping offsets

                val spanLen = t.spanEnd - t.spanStart
                val namedBase = baseByName[t.name]
                if (namedBase != null &&
                    (namedBase.spanEnd - namedBase.spanStart) == spanLen &&
                    namedBase.spanSha256Hex == t.spanSha256Hex
                ) {
                    ops.add(RawOp.Copy(namedBase.spanStart, spanLen))
                    cursor = t.spanEnd
                    continue
                }

                // Match identical payload slice even when ZIP alignment padding / timestamps shifted headers
                var payloadMatch: ZipEntrySlice? = null
                if (t.dataLength >= 128L) {
                    val pKey = "${t.crc32}:${t.dataLength}"
                    val candidates = baseByPayloadKey[pKey]
                    if (candidates != null) {
                        for (cand in candidates) {
                            if (cand.dataSha256Hex == t.dataSha256Hex) {
                                payloadMatch = cand
                                break
                            }
                        }
                    }
                }

                if (payloadMatch != null && t.dataStart >= t.spanStart && t.dataStart + t.dataLength <= t.spanEnd) {
                    val headLen = t.dataStart - t.spanStart
                    if (headLen > 0L) {
                        ops.add(RawOp.Literal(t.spanStart, headLen))
                    }
                    ops.add(RawOp.Copy(payloadMatch.dataStart, t.dataLength))
                    val tailStart = t.dataStart + t.dataLength
                    val tailLen = t.spanEnd - tailStart
                    if (tailLen > 0L) {
                        ops.add(RawOp.Literal(tailStart, tailLen))
                    }
                    cursor = t.spanEnd
                } else {
                    ops.add(RawOp.Literal(t.spanStart, spanLen))
                    cursor = t.spanEnd
                }
            }

            if (cursor < targetSize) {
                ops.add(RawOp.Literal(cursor, targetSize - cursor))
            }

            // Coalesce adjacent Copy or adjacent Literal instructions
            val coalesced = ArrayList<RawOp>(ops.size)
            for (op in ops) {
                val prev = coalesced.lastOrNull()
                if (op is RawOp.Copy && prev is RawOp.Copy && prev.oldOffset + prev.length == op.oldOffset) {
                    coalesced[coalesced.lastIndex] = RawOp.Copy(prev.oldOffset, prev.length + op.length)
                } else if (op is RawOp.Literal && prev is RawOp.Literal && prev.targetOffset + prev.length == op.targetOffset) {
                    coalesced[coalesced.lastIndex] = RawOp.Literal(prev.targetOffset, prev.length + op.length)
                } else {
                    coalesced.add(op)
                }
            }

            patchFile.parentFile?.mkdirs()
            val tempPatch = File(patchFile.parentFile ?: File("."), "${patchFile.name}.tmp")
            tempPatch.delete()

            var copiedBytes = 0L
            var literalBytes = 0L
            val ioBuf = ByteArray(IO_BUF_SIZE)

            RandomAccessFile(targetApkFile, "r").use { targetRaf ->
                BufferedOutputStream(FileOutputStream(tempPatch), IO_BUF_SIZE).use { rawOut ->
                    val hdrOut = DataOutputStream(rawOut)
                    hdrOut.write(MAGIC)
                    hdrOut.write(baseSha)
                    hdrOut.write(targetSha)
                    hdrOut.writeLong(targetSize)
                    hdrOut.flush()

                    GZIPOutputStream(rawOut, IO_BUF_SIZE).use { gzOut ->
                        val bodyOut = DataOutputStream(gzOut)
                        for (op in coalesced) {
                            when (op) {
                                is RawOp.Copy -> {
                                    var rem = op.length
                                    var off = op.oldOffset
                                    while (rem > 0L) {
                                        val chunk = min(rem, Int.MAX_VALUE.toLong()).toInt()
                                        bodyOut.writeByte(OP_COPY)
                                        bodyOut.writeLong(off)
                                        bodyOut.writeInt(chunk)
                                        copiedBytes += chunk.toLong()
                                        off += chunk.toLong()
                                        rem -= chunk.toLong()
                                    }
                                }

                                is RawOp.Literal -> {
                                    var rem = op.length
                                    var off = op.targetOffset
                                    targetRaf.seek(off)
                                    while (rem > 0L) {
                                        val chunk = min(rem, MAX_LITERAL_CHUNK.toLong()).toInt()
                                        bodyOut.writeByte(OP_LITERAL)
                                        bodyOut.writeInt(chunk)
                                        var left = chunk
                                        while (left > 0) {
                                            val n = min(left, ioBuf.size)
                                            targetRaf.readFully(ioBuf, 0, n)
                                            bodyOut.write(ioBuf, 0, n)
                                            left -= n
                                        }
                                        literalBytes += chunk.toLong()
                                        off += chunk.toLong()
                                        rem -= chunk.toLong()
                                    }
                                }
                            }
                        }
                        bodyOut.writeByte(OP_EOF)
                        bodyOut.flush()
                    }
                }
            }

            patchFile.delete()
            if (!tempPatch.renameTo(patchFile)) {
                tempPatch.delete()
                throw IllegalStateException("Could not write final patch file")
            }

            Result.success(
                PatchStats(
                    baseSize = baseApkFile.length(),
                    targetSize = targetSize,
                    patchSize = patchFile.length(),
                    copiedBytes = copiedBytes,
                    literalBytes = literalBytes,
                    baseSha256Hex = baseSha.joinToString("") { "%02x".format(it) },
                    targetSha256Hex = targetSha.joinToString("") { "%02x".format(it) }
                )
            )
        } catch (t: Throwable) {
            patchFile.delete()
            Result.failure(t)
        }
    }

    private sealed class RawOp {
        data class Copy(val oldOffset: Long, val length: Long) : RawOp()
        data class Literal(val targetOffset: Long, val length: Long) : RawOp()
    }

    private fun indexZipSlices(zipFile: File): List<ZipEntrySlice> {
        val len = zipFile.length()
        if (len < 22L) return emptyList()
        return runCatching {
            RandomAccessFile(zipFile, "r").use { raf ->
                val searchWindow = min(len, 65_557L).toInt()
                val tailStart = len - searchWindow
                val tailBuf = ByteArray(searchWindow)
                raf.seek(tailStart)
                raf.readFully(tailBuf)

                var eocdIdx = -1
                for (i in searchWindow - 22 downTo 0) {
                    if (tailBuf[i] == 0x50.toByte() &&
                        tailBuf[i + 1] == 0x4B.toByte() &&
                        tailBuf[i + 2] == 0x05.toByte() &&
                        tailBuf[i + 3] == 0x06.toByte()
                    ) {
                        eocdIdx = i
                        break
                    }
                }
                if (eocdIdx < 0) return emptyList()

                val cdSize = uint32LE(tailBuf, eocdIdx + 12)
                val cdOffset = uint32LE(tailBuf, eocdIdx + 16)
                if (cdOffset < 0L || cdSize <= 0L || cdOffset + cdSize > tailStart + eocdIdx) {
                    return emptyList()
                }
                if (cdSize > 32L * 1024L * 1024L) return emptyList()

                val cdBytes = ByteArray(cdSize.toInt())
                raf.seek(cdOffset)
                raf.readFully(cdBytes)

                val entries = ArrayList<ZipEntrySlice>(256)
                var pos = 0
                while (pos + 46 <= cdBytes.size) {
                    if (cdBytes[pos] != 0x50.toByte() ||
                        cdBytes[pos + 1] != 0x4B.toByte() ||
                        cdBytes[pos + 2] != 0x01.toByte() ||
                        cdBytes[pos + 3] != 0x02.toByte()
                    ) {
                        return emptyList()
                    }
                    val method = uint16LE(cdBytes, pos + 10)
                    val crc32 = uint32LE(cdBytes, pos + 16)
                    val compSize = uint32LE(cdBytes, pos + 20)
                    val uncompSize = uint32LE(cdBytes, pos + 24)
                    val fnLen = uint16LE(cdBytes, pos + 28)
                    val extraLen = uint16LE(cdBytes, pos + 30)
                    val commentLen = uint16LE(cdBytes, pos + 32)
                    val localOff = uint32LE(cdBytes, pos + 42)
                    if (pos + 46 + fnLen + extraLen + commentLen > cdBytes.size) break

                    val name = String(cdBytes, pos + 46, fnLen, StandardCharsets.UTF_8)
                    val rawLen = if (method == 0) max(compSize, uncompSize) else compSize
                    entries.add(
                        ZipEntrySlice(
                            name = name,
                            method = method,
                            crc32 = crc32,
                            compressedSize = compSize,
                            uncompressedSize = uncompSize,
                            spanStart = localOff,
                            dataLength = rawLen
                        )
                    )
                    pos += 46 + fnLen + extraLen + commentLen
                }

                entries.sortBy { it.spanStart }
                val lhHdr = ByteArray(30)
                val hashBuf = ByteArray(IO_BUF_SIZE)
                for (i in entries.indices) {
                    val cur = entries[i]
                    val nextStart = if (i + 1 < entries.size) entries[i + 1].spanStart else cdOffset
                    if (cur.spanStart < 0L || nextStart <= cur.spanStart || nextStart > cdOffset) {
                        return emptyList()
                    }
                    cur.spanEnd = nextStart

                    if (cur.spanStart + 30L <= cur.spanEnd) {
                        raf.seek(cur.spanStart)
                        raf.readFully(lhHdr)
                        if (lhHdr[0] == 0x50.toByte() && lhHdr[1] == 0x4B.toByte() &&
                            lhHdr[2] == 0x03.toByte() && lhHdr[3] == 0x04.toByte()
                        ) {
                            val lhFnLen = uint16LE(lhHdr, 26)
                            val lhExtraLen = uint16LE(lhHdr, 28)
                            val dStart = cur.spanStart + 30L + lhFnLen + lhExtraLen
                            if (dStart + cur.dataLength <= cur.spanEnd) {
                                cur.dataStart = dStart
                            }
                        }
                    }

                    cur.spanSha256Hex = hashRange(raf, cur.spanStart, cur.spanEnd - cur.spanStart, hashBuf)
                    if (cur.dataLength >= 128L && cur.dataStart > 0L) {
                        cur.dataSha256Hex = hashRange(raf, cur.dataStart, cur.dataLength, hashBuf)
                    }
                }
                entries
            }
        }.getOrElse { emptyList() }
    }

    private fun hashRange(raf: RandomAccessFile, offset: Long, length: Long, buf: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        raf.seek(offset)
        var rem = length
        while (rem > 0L) {
            val chunk = min(rem, buf.size.toLong()).toInt()
            raf.readFully(buf, 0, chunk)
            md.update(buf, 0, chunk)
            rem -= chunk.toLong()
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun uint16LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)

    private fun uint32LE(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFFL)) or
            ((buf[off + 1].toLong() and 0xFFL) shl 8) or
            ((buf[off + 2].toLong() and 0xFFL) shl 16) or
            ((buf[off + 3].toLong() and 0xFFL) shl 24)
}
