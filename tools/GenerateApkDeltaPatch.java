import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Zero-dependency JDK 17 CLI tool generating and verifying Play Store-style
 * DPATCH1 binary APK delta patches between a previous APK and a new APK.
 *
 * Usage: java tools/GenerateApkDeltaPatch.java <old.apk> <new.apk> <out.dpatch>
 */
public class GenerateApkDeltaPatch {
    private static final byte[] MAGIC = new byte[]{'D', 'P', 'A', 'T', 'C', 'H', '1', 0};
    private static final int HEADER_SIZE = 80;
    private static final int OP_EOF = 0;
    private static final int OP_COPY = 1;
    private static final int OP_LITERAL = 2;
    private static final int MAX_LITERAL_CHUNK = 16 * 1024 * 1024;
    private static final int IO_BUF_SIZE = 65_536;

    static final class ZipSlice {
        final String name;
        final int method;
        final long crc32;
        final long compressedSize;
        final long uncompressedSize;
        final long spanStart;
        long spanEnd;
        long dataStart;
        long dataLength;
        String spanSha256Hex = "";
        String dataSha256Hex = "";

        ZipSlice(String name, int method, long crc32, long comp, long uncomp, long spanStart, long dataLen) {
            this.name = name;
            this.method = method;
            this.crc32 = crc32;
            this.compressedSize = comp;
            this.uncompressedSize = uncomp;
            this.spanStart = spanStart;
            this.dataLength = dataLen;
        }
    }

    static final class Op {
        final boolean isCopy;
        final long offset;
        final long length;

        Op(boolean isCopy, long offset, long length) {
            this.isCopy = isCopy;
            this.offset = offset;
            this.length = length;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java GenerateApkDeltaPatch.java <old.apk> <new.apk> <out.dpatch>");
            System.exit(2);
        }
        File baseFile = new File(args[0]);
        File targetFile = new File(args[1]);
        File patchFile = new File(args[2]);
        if (!baseFile.isFile() || !targetFile.isFile()) {
            throw new IllegalArgumentException("Base or target APK file does not exist");
        }

        byte[] baseSha = sha256File(baseFile);
        byte[] targetSha = sha256File(targetFile);
        long targetSize = targetFile.length();

        List<ZipSlice> baseSlices = indexZipSlices(baseFile);
        List<ZipSlice> targetSlices = indexZipSlices(targetFile);

        Map<String, ZipSlice> baseByName = new HashMap<>();
        Map<String, List<ZipSlice>> baseByPayloadKey = new HashMap<>();
        for (ZipSlice b : baseSlices) {
            baseByName.putIfAbsent(b.name, b);
            if (b.dataLength >= 128L) {
                String k = b.crc32 + ":" + b.dataLength;
                baseByPayloadKey.computeIfAbsent(k, ignored -> new ArrayList<>()).add(b);
            }
        }

        List<Op> rawOps = new ArrayList<>();
        long cursor = 0L;

        for (ZipSlice t : targetSlices) {
            if (t.spanStart > cursor) {
                rawOps.add(new Op(false, cursor, t.spanStart - cursor));
                cursor = t.spanStart;
            }
            if (t.spanStart < cursor) continue;

            long spanLen = t.spanEnd - t.spanStart;
            ZipSlice named = baseByName.get(t.name);
            if (named != null && (named.spanEnd - named.spanStart) == spanLen && named.spanSha256Hex.equals(t.spanSha256Hex)) {
                rawOps.add(new Op(true, named.spanStart, spanLen));
                cursor = t.spanEnd;
                continue;
            }

            ZipSlice payloadMatch = null;
            if (t.dataLength >= 128L) {
                List<ZipSlice> cands = baseByPayloadKey.get(t.crc32 + ":" + t.dataLength);
                if (cands != null) {
                    for (ZipSlice cand : cands) {
                        if (cand.dataSha256Hex.equals(t.dataSha256Hex)) {
                            payloadMatch = cand;
                            break;
                        }
                    }
                }
            }

            if (payloadMatch != null && t.dataStart >= t.spanStart && t.dataStart + t.dataLength <= t.spanEnd) {
                long headLen = t.dataStart - t.spanStart;
                if (headLen > 0L) rawOps.add(new Op(false, t.spanStart, headLen));
                rawOps.add(new Op(true, payloadMatch.dataStart, t.dataLength));
                long tailStart = t.dataStart + t.dataLength;
                long tailLen = t.spanEnd - tailStart;
                if (tailLen > 0L) rawOps.add(new Op(false, tailStart, tailLen));
                cursor = t.spanEnd;
            } else {
                rawOps.add(new Op(false, t.spanStart, spanLen));
                cursor = t.spanEnd;
            }
        }

        if (cursor < targetSize) {
            rawOps.add(new Op(false, cursor, targetSize - cursor));
        }

        List<Op> ops = new ArrayList<>();
        for (Op op : rawOps) {
            if (!ops.isEmpty()) {
                Op prev = ops.get(ops.size() - 1);
                if (prev.isCopy == op.isCopy && prev.offset + prev.length == op.offset) {
                    ops.set(ops.size() - 1, new Op(op.isCopy, prev.offset, prev.length + op.length));
                    continue;
                }
            }
            ops.add(op);
        }

        File parent = patchFile.getParentFile();
        if (parent != null) parent.mkdirs();

        byte[] ioBuf = new byte[IO_BUF_SIZE];
        long copiedBytes = 0L;
        long literalBytes = 0L;

        try (RandomAccessFile targetRaf = new RandomAccessFile(targetFile, "r");
             BufferedOutputStream rawOut = new BufferedOutputStream(new FileOutputStream(patchFile), IO_BUF_SIZE)) {
            DataOutputStream hdrOut = new DataOutputStream(rawOut);
            hdrOut.write(MAGIC);
            hdrOut.write(baseSha);
            hdrOut.write(targetSha);
            hdrOut.writeLong(targetSize);
            hdrOut.flush();

            try (GZIPOutputStream gzOut = new GZIPOutputStream(rawOut, IO_BUF_SIZE);
                 DataOutputStream bodyOut = new DataOutputStream(gzOut)) {
                for (Op op : ops) {
                    long rem = op.length;
                    long off = op.offset;
                    if (op.isCopy) {
                        while (rem > 0L) {
                            int chunk = (int) Math.min(rem, Integer.MAX_VALUE);
                            bodyOut.writeByte(OP_COPY);
                            bodyOut.writeLong(off);
                            bodyOut.writeInt(chunk);
                            copiedBytes += chunk;
                            off += chunk;
                            rem -= chunk;
                        }
                    } else {
                        targetRaf.seek(off);
                        while (rem > 0L) {
                            int chunk = (int) Math.min(rem, MAX_LITERAL_CHUNK);
                            bodyOut.writeByte(OP_LITERAL);
                            bodyOut.writeInt(chunk);
                            int left = chunk;
                            while (left > 0) {
                                int n = Math.min(left, ioBuf.length);
                                targetRaf.readFully(ioBuf, 0, n);
                                bodyOut.write(ioBuf, 0, n);
                                left -= n;
                            }
                            literalBytes += chunk;
                            off += chunk;
                            rem -= chunk;
                        }
                    }
                }
                bodyOut.writeByte(OP_EOF);
                bodyOut.flush();
            }
        }

        // Strict roundtrip verification in streaming digest pass
        String verifiedHex = verifyRoundtripPatch(baseFile, patchFile);
        String expectedHex = toHex(targetSha);
        if (!verifiedHex.equals(expectedHex)) {
            patchFile.delete();
            throw new SecurityException("Roundtrip verification failed: " + verifiedHex + " != " + expectedHex);
        }

        System.out.printf(
            "DPATCH1_OK base=%d target=%d patch=%d copied=%d literal=%d sha256=%s%n",
            baseFile.length(), targetSize, patchFile.length(), copiedBytes, literalBytes, verifiedHex
        );
    }

    private static String verifyRoundtripPatch(File baseFile, File patchFile) throws Exception {
        if (patchFile.length() < HEADER_SIZE) throw new IllegalStateException("Patch too small");
        long baseLen = baseFile.length();
        byte[] actualBaseSha = sha256File(baseFile);
        MessageDigest outMd = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[IO_BUF_SIZE];

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(patchFile), IO_BUF_SIZE));
             RandomAccessFile baseRaf = new RandomAccessFile(baseFile, "r")) {
            byte[] magic = new byte[8];
            dis.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new SecurityException("Bad magic");
            byte[] baseExpected = new byte[32];
            byte[] targetExpected = new byte[32];
            dis.readFully(baseExpected);
            dis.readFully(targetExpected);
            if (!MessageDigest.isEqual(actualBaseSha, baseExpected)) throw new SecurityException("Base SHA mismatch");
            long targetSize = dis.readLong();
            long written = 0L;

            try (GZIPInputStream gzis = new GZIPInputStream(dis, IO_BUF_SIZE);
                 DataInputStream opIn = new DataInputStream(gzis)) {
                boolean sawEof = false;
                while (true) {
                    int op = opIn.read();
                    if (op < 0) break;
                    if (op == OP_EOF) {
                        sawEof = true;
                        break;
                    }
                    if (op == OP_COPY) {
                        long off = opIn.readLong();
                        int len = opIn.readInt();
                        if (off < 0 || len <= 0 || off > baseLen - len) throw new SecurityException("Bad COPY");
                        baseRaf.seek(off);
                        int rem = len;
                        while (rem > 0) {
                            int c = Math.min(rem, buf.length);
                            baseRaf.readFully(buf, 0, c);
                            outMd.update(buf, 0, c);
                            rem -= c;
                            written += c;
                        }
                    } else if (op == OP_LITERAL) {
                        int len = opIn.readInt();
                        if (len <= 0 || len > MAX_LITERAL_CHUNK) throw new SecurityException("Bad LITERAL");
                        int rem = len;
                        while (rem > 0) {
                            int c = Math.min(rem, buf.length);
                            opIn.readFully(buf, 0, c);
                            outMd.update(buf, 0, c);
                            rem -= c;
                            written += c;
                        }
                    } else {
                        throw new SecurityException("Bad op: " + op);
                    }
                }
                if (!sawEof) throw new EOFException("Missing EOF");
            }
            if (written != targetSize) throw new SecurityException("Written mismatch");
            byte[] actualTarget = outMd.digest();
            if (!MessageDigest.isEqual(actualTarget, targetExpected)) throw new SecurityException("Target SHA mismatch");
            return toHex(actualTarget);
        }
    }

    private static List<ZipSlice> indexZipSlices(File file) throws Exception {
        long len = file.length();
        List<ZipSlice> empty = new ArrayList<>();
        if (len < 22L) return empty;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int win = (int) Math.min(len, 65_557L);
            long tailStart = len - win;
            byte[] tail = new byte[win];
            raf.seek(tailStart);
            raf.readFully(tail);
            int eocd = -1;
            for (int i = win - 22; i >= 0; i--) {
                if (tail[i] == 0x50 && tail[i + 1] == 0x4B && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) return empty;
            long cdSize = uint32LE(tail, eocd + 12);
            long cdOff = uint32LE(tail, eocd + 16);
            if (cdOff < 0 || cdSize <= 0 || cdOff + cdSize > tailStart + eocd || cdSize > 32L * 1024L * 1024L) {
                return empty;
            }
            byte[] cd = new byte[(int) cdSize];
            raf.seek(cdOff);
            raf.readFully(cd);

            List<ZipSlice> list = new ArrayList<>();
            int pos = 0;
            while (pos + 46 <= cd.length) {
                if (cd[pos] != 0x50 || cd[pos + 1] != 0x4B || cd[pos + 2] != 0x01 || cd[pos + 3] != 0x02) return empty;
                int method = uint16LE(cd, pos + 10);
                long crc = uint32LE(cd, pos + 16);
                long comp = uint32LE(cd, pos + 20);
                long uncomp = uint32LE(cd, pos + 24);
                int fnLen = uint16LE(cd, pos + 28);
                int exLen = uint16LE(cd, pos + 30);
                int cmLen = uint16LE(cd, pos + 32);
                long lhOff = uint32LE(cd, pos + 42);
                if (pos + 46 + fnLen + exLen + cmLen > cd.length) break;
                String name = new String(cd, pos + 46, fnLen, StandardCharsets.UTF_8);
                long dataLen = (method == 0) ? Math.max(comp, uncomp) : comp;
                list.add(new ZipSlice(name, method, crc, comp, uncomp, lhOff, dataLen));
                pos += 46 + fnLen + exLen + cmLen;
            }
            list.sort(Comparator.comparingLong(s -> s.spanStart));
            byte[] lh = new byte[30];
            byte[] hbuf = new byte[IO_BUF_SIZE];
            for (int i = 0; i < list.size(); i++) {
                ZipSlice cur = list.get(i);
                long next = (i + 1 < list.size()) ? list.get(i + 1).spanStart : cdOff;
                if (cur.spanStart < 0 || next <= cur.spanStart || next > cdOff) return empty;
                cur.spanEnd = next;
                if (cur.spanStart + 30 <= cur.spanEnd) {
                    raf.seek(cur.spanStart);
                    raf.readFully(lh);
                    if (lh[0] == 0x50 && lh[1] == 0x4B && lh[2] == 0x03 && lh[3] == 0x04) {
                        int fLen = uint16LE(lh, 26);
                        int eLen = uint16LE(lh, 28);
                        long dStart = cur.spanStart + 30L + fLen + eLen;
                        if (dStart + cur.dataLength <= cur.spanEnd) cur.dataStart = dStart;
                    }
                }
                cur.spanSha256Hex = hashRange(raf, cur.spanStart, cur.spanEnd - cur.spanStart, hbuf);
                if (cur.dataLength >= 128L && cur.dataStart > 0L) {
                    cur.dataSha256Hex = hashRange(raf, cur.dataStart, cur.dataLength, hbuf);
                }
            }
            return list;
        }
    }

    private static String hashRange(RandomAccessFile raf, long off, long len, byte[] buf) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        raf.seek(off);
        long rem = len;
        while (rem > 0L) {
            int chunk = (int) Math.min(rem, buf.length);
            raf.readFully(buf, 0, chunk);
            md.update(buf, 0, chunk);
            rem -= chunk;
        }
        return toHex(md.digest());
    }

    private static byte[] sha256File(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[IO_BUF_SIZE];
        try (FileInputStream fis = new FileInputStream(f)) {
            int n;
            while ((n = fis.read(buf)) > 0) md.update(buf, 0, n);
        }
        return md.digest();
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    private static int uint16LE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long uint32LE(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8) | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }
}
