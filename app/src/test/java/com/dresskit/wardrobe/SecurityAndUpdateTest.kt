package com.dresskit.wardrobe

import com.dresskit.wardrobe.data.AiStudioService
import com.dresskit.wardrobe.data.ApkDeltaPatcher
import com.dresskit.wardrobe.data.AppUpdateManager
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.SecurityGuards
import com.dresskit.wardrobe.data.StoreSearchLinks
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.theme.STUDIO_ECOMMERCE_PROMPT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SecurityAndUpdateTest {

    @Test
    fun `isSafePublicHttpsUrl rejects insecure schemes, internal IPs, credentials, and non-443 ports`() {
        val unsafeUrls = listOf(
            "",
            "   ",
            "http://github.com/kyhlaer/Dolabim",
            "javascript:alert(1)",
            "file:///data/data/com.dresskit.wardrobe/files/wardrobe_data.json",
            "intent://scan/#Intent;scheme=zxing;end",
            "https://user:pass@github.com/kyhlaer/Dolabim",
            "https://localhost/update.apk",
            "https://api.localhost/v1",
            "https://printer.local/config",
            "https://metadata.internal/latest",
            "https://intranet/admin",
            "https://127.0.0.1/admin",
            "https://0.0.0.0/test",
            "https://10.0.0.5/secret",
            "https://172.16.4.12/secret",
            "https://172.31.255.1/secret",
            "https://192.168.1.1/router",
            "https://169.254.169.254/latest/meta-data/",
            "https://100.64.1.1/cgnat",
            "https://[::1]/loopback",
            "https://[fe80::1]/linklocal",
            "https://[fd12:3456:789a::1]/ula",
            "https://github.com:8443/kyhlaer/Dolabim",
            "https://github.com/path with spaces",
            "https://github.com/ctrl\nchar"
        )
        unsafeUrls.forEach { url ->
            assertFalse("Expected unsafe URL to be rejected: $url", SecurityGuards.isSafePublicHttpsUrl(url))
        }
    }

    @Test
    fun `isSafePublicHttpsUrl permits valid public https URLs`() {
        val safeUrls = listOf(
            "https://api.github.com/repos/kyhlaer/Dolabim/releases/latest",
            "https://raw.githubusercontent.com/kyhlaer/Dolabim/main/remote_config.json",
            "https://www.trendyol.com/sr?q=hoodie",
            "https://api.open-meteo.com/v1/forecast?latitude=41.0&longitude=28.9",
            "https://172.32.0.1/public",
            "https://8.8.8.8/dns-query"
        )
        safeUrls.forEach { url ->
            assertTrue("Expected safe URL to be allowed: $url", SecurityGuards.isSafePublicHttpsUrl(url))
        }
    }

    @Test
    fun `isVersionNewer accurately compares dotted version strings`() {
        assertTrue(AppUpdateManager.isVersionNewer("1.43.23", "1.43.22"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.22", "1.43.21"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.22", "1.43.2"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.21", "1.43.2"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.2", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("v1.43.2", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("V1.44", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("2.0", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.1.1", "1.43.1"))

        assertFalse(AppUpdateManager.isVersionNewer("1.43.22", "1.43.22"))
        assertFalse(AppUpdateManager.isVersionNewer("1.43.21", "1.43.21"))
        assertFalse(AppUpdateManager.isVersionNewer("1.43.1", "1.43.1"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.43.1", "1.43.1"))
        assertFalse(AppUpdateManager.isVersionNewer("1.43", "1.43.1"))
        assertFalse(AppUpdateManager.isVersionNewer("1.42.9", "1.43.1"))
        assertFalse(AppUpdateManager.isVersionNewer("", "1.43.1"))
    }

    @Test
    fun `extractSha256FromReleaseBody extracts lowercase 64-hex digest cleanly`() {
        val validHex = "A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F90"
        val body = "Release notes...\nSHA-256: $validHex\nEnjoy!"
        assertEquals(validHex.lowercase(), AppUpdateManager.extractSha256FromReleaseBody(body))

        assertNull(AppUpdateManager.extractSha256FromReleaseBody("SHA-256: nothex"))
        assertNull(AppUpdateManager.extractSha256FromReleaseBody("SHA-256: abcdef"))
        assertNull(AppUpdateManager.extractSha256FromReleaseBody("No checksum provided"))
    }

    @Test
    fun `sanitizeFileName blocks path traversal and unsafe filenames`() {
        assertNull(ImageStorage.sanitizeFileName(".."))
        assertNull(ImageStorage.sanitizeFileName("."))
        assertNull(ImageStorage.sanitizeFileName("../../../etc/passwd"))
        assertNull(ImageStorage.sanitizeFileName("..\\..\\secret.jpg"))
        assertNull(ImageStorage.sanitizeFileName("photo with space.jpg"))
        assertNull(ImageStorage.sanitizeFileName("bad;rm.jpg"))
        assertNull(ImageStorage.sanitizeFileName(""))
        assertNull(ImageStorage.sanitizeFileName("/nested/dir/safe.jpg"))

        assertEquals("garment_1234.png", ImageStorage.sanitizeFileName("garment_1234.png"))
        assertEquals("photo-1_2.jpg", ImageStorage.sanitizeFileName("  photo-1_2.jpg  "))
    }

    @Test
    fun `Chinese language support translates enums and localizedText`() {
        assertEquals(
            "你好",
            localizedText(AppLanguage.ZH, tr = "Merhaba", en = "Hello", zh = "你好")
        )
        assertEquals("黑色", GarmentColor.BLACK.display(AppLanguage.ZH))
        assertEquals("上装", ClothingCategory.TOP.display(AppLanguage.ZH))
        assertEquals("连帽卫衣", ClothingSubCategory.HOODIE.display(AppLanguage.ZH))
    }

    @Test
    fun `PriceTrackerService extracts prices across JSON-LD, Trendyol nested JSON, Inditex cents, and meta tags`() {
        val jsonLdHtml = """
            <html><head>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Boxy Hoodie","offers":{"@type":"Offer","price":1249.90,"priceCurrency":"TRY"}}
            </script>
            </head></html>
        """.trimIndent()
        assertEquals(1249.90, PriceTrackerService.extractPriceFromHtml(jsonLdHtml)!!, 0.01)

        val trendyolJson = """
            <script>window.__PRODUCT_DETAIL_APP_INITIAL_STATE__={"product":{"price":{"sellingPrice":{"value":899.95,"text":"899,95 TL"}}}};</script>
        """.trimIndent()
        assertEquals(899.95, PriceTrackerService.extractPriceFromHtml(trendyolJson, "https://www.trendyol.com/p-123")!!, 0.01)

        val bershkaCentsHtml = """
            <script>window.inditex={"product":{"price":129900,"oldPrice":159900}};</script>
        """.trimIndent()
        assertEquals(1299.0, PriceTrackerService.extractPriceFromHtml(bershkaCentsHtml, "https://www.bershka.com/tr/erkek-hoodie-c0p123.html")!!, 0.01)

        val metaHtml = """<meta property="product:price:amount" content="1.499,50" />"""
        assertEquals(1499.50, PriceTrackerService.extractPriceFromHtml(metaHtml)!!, 0.01)

        val visibleTlHtml = """<div>İndirimli Fiyat: 2.150,00 TL</div>"""
        assertEquals(2150.0, PriceTrackerService.extractPriceFromHtml(visibleTlHtml)!!, 0.01)
    }

    @Test
    fun `PriceTrackerService normalizes share-sheet links and encodes Turkish URLs safely`() {
        val shared = "Bershka Oversize Hoodie - Şahane ürün https://www.bershka.com/tr/siyah-şapka-p123.html,"
        val normalized = PriceTrackerService.normalizeProductUrl(shared)
        assertEquals("https://www.bershka.com/tr/siyah-%C5%9Fapka-p123.html", normalized)
        assertTrue(PriceTrackerService.isSafeHttpsUrl(normalized))
        assertFalse(PriceTrackerService.isSafeHttpsUrl("http://www.trendyol.com/p-123"))
        assertFalse(PriceTrackerService.isSafeHttpsUrl("https://localhost/product"))
    }

    @Test
    fun `StoreSearchLinks cleans fluff words and returns safe direct store URLs`() {
        val cleaned = StoreSearchLinks.cleanSearchQuery("erkek", "Ekru-Siyah Heavy Grafik Ceket veya Stili")
        assertEquals("erkek Ekru Siyah Ceket", cleaned)

        val links = StoreSearchLinks.buildStoreSearchLinks("kadın", "Siyah/Gri Boxy Hoodie")
        assertEquals(9, links.size)
        links.forEach { (store, url) ->
            assertTrue("Unsafe URL for $store: $url", SecurityGuards.isSafePublicHttpsUrl(url, maxLen = 1000))
        }
        val pullBearUrl = links.first { it.first == "Pull&Bear" }.second
        assertTrue(pullBearUrl.contains("Pull%26Bear"))
        val bershkaUrl = links.first { it.first == "Bershka" }.second
        assertTrue(bershkaUrl.startsWith("https://www.bershka.com/tr/q/"))
        assertFalse(bershkaUrl.contains("site:bershka.com"))
    }

    @Test
    fun `AiStudioService extracts base64 generated image payloads cleanly`() {
        val sampleB64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
        val directJson = """{"id":"int_1","output_image":{"mime_type":"image/png","data":"$sampleB64"}}"""
        assertEquals(sampleB64, AiStudioService.extractGeneratedImageBase64(directJson))

        val stepJson = """{"steps":[{"type":"model_output","content":[{"type":"image","mime_type":"image/jpeg","data":"$sampleB64"}]}]}"""
        assertEquals(sampleB64, AiStudioService.extractGeneratedImageBase64(stepJson))

        val vertexInlineJson = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"$sampleB64"}}]}}]}"""
        assertEquals(sampleB64, AiStudioService.extractGeneratedImageBase64(vertexInlineJson))

        val snakeInlineJson = """{"candidates":[{"content":{"parts":[{"inline_data":{"data":"$sampleB64","mime_type":"image/jpeg"}}]}}]}"""
        assertEquals(sampleB64, AiStudioService.extractGeneratedImageBase64(snakeInlineJson))

        val textThenImageJson = """{"candidates":[{"content":{"parts":[{"text":"Catalog image ready"},{"inlineData":{"mimeType":"image/webp","data":"$sampleB64"}}]}}]}"""
        assertEquals(sampleB64, AiStudioService.extractGeneratedImageBase64(textThenImageJson))

        assertNull(AiStudioService.extractGeneratedImageBase64("""{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/svg+xml","data":"$sampleB64"}}]}}]}"""))
        assertNull(AiStudioService.extractGeneratedImageBase64("""{"output_image":{"mime_type":"image/svg+xml","data":"$sampleB64"}}"""))
        assertNull(AiStudioService.extractGeneratedImageBase64("""{"steps":[{"type":"model_output","content":[{"type":"text","text":"ok"}]}]}"""))
        assertNull(AiStudioService.extractGeneratedImageBase64(""))
    }

    @Test
    fun `AiStudioService validates both AIza and AQ Express Gemini keys`() {
        assertTrue(AiStudioService.isValidGeminiApiKey("AIzaSyTestExampleKey1234567890"))
        assertTrue(AiStudioService.isValidGeminiApiKey("AQ.Ab8RN6TestExpressKey1234567890"))
        val longAqKey = "AQ.Ab8RN6" + "xY9_~=".repeat(40)
        assertTrue(AiStudioService.isValidGeminiApiKey(longAqKey))
        assertTrue(AiStudioService.isVertexExpressApiKey(longAqKey))
        assertFalse(AiStudioService.isVertexExpressApiKey("AIzaSyTestExampleKey1234567890"))

        assertFalse(AiStudioService.isValidGeminiApiKey("short"))
        assertFalse(AiStudioService.isValidGeminiApiKey("AQ.short"))
        assertFalse(AiStudioService.isValidGeminiApiKey("hf_12345678901234567890"))
        assertFalse(AiStudioService.isValidGeminiApiKey("AQ.bad key with spaces 123456"))
    }

    @Test
    fun `parseGradioSseOutput parses image URLs, inline base64, quota limits, and rejects foreign hosts`() {
        val sampleHost = "kulkas2pintu-qwen-edit-image.hf.space"
        val urlSse = """
            event: heartbeat
            data: null

            event: complete
            data: [{"path": "/tmp/gradio/abc/image.png", "url": "https://$sampleHost/gradio_api/file=/tmp/gradio/abc/image.png", "meta": {"_type": "gradio.FileData"}}, 42]
        """.trimIndent()
        assertEquals(
            AiStudioService.GradioSseOutcome.ImageUrl("https://$sampleHost/gradio_api/file=/tmp/gradio/abc/image.png"),
            AiStudioService.parseGradioSseOutput(urlSse, sampleHost)
        )

        val inlineB64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
        val fireredSse = """
            event: complete
            data: [{"image": "data:image/png;base64,$inlineB64", "seed": 1, "status": "success"}]
        """.trimIndent()
        assertEquals(
            AiStudioService.GradioSseOutcome.InlineBase64(inlineB64),
            AiStudioService.parseGradioSseOutput(fireredSse, "prithivmlmods-firered-image-edit-1-0-fast.hf.space")
        )

        val quotaSse = """
            event: error
            data: {"error": "You have exceeded your ZeroGPU runs limit. Authenticate with a Hugging Face token", "title": "ZeroGPU quota exceeded"}
        """.trimIndent()
        assertTrue(AiStudioService.parseGradioSseOutput(quotaSse, sampleHost) is AiStudioService.GradioSseOutcome.QuotaExceeded)

        val genericErrSse = "event: error\ndata: null\n"
        assertTrue(AiStudioService.parseGradioSseOutput(genericErrSse, sampleHost) is AiStudioService.GradioSseOutcome.SpaceError)

        val ssrfSse = """
            event: complete
            data: [{"url": "https://127.0.0.1/secret.png"}]
        """.trimIndent()
        val ssrfOutcome = AiStudioService.parseGradioSseOutput(ssrfSse, sampleHost)
        assertTrue(ssrfOutcome is AiStudioService.GradioSseOutcome.SpaceError)

        val foreignHostSse = """
            event: complete
            data: [{"url": "https://evil.example.com/payload.png"}]
        """.trimIndent()
        assertTrue(AiStudioService.parseGradioSseOutput(foreignHostSse, sampleHost) is AiStudioService.GradioSseOutcome.SpaceError)
    }

    @Test
    fun `FLAT_LAY_DIFFUSION_PROMPT matches exact STUDIO_ECOMMERCE_PROMPT`() {
        assertEquals(STUDIO_ECOMMERCE_PROMPT, AiStudioService.FLAT_LAY_DIFFUSION_PROMPT)
        assertTrue(AiStudioService.FLAT_LAY_DIFFUSION_PROMPT.contains("#FFFFFF"))
        assertTrue(AiStudioService.FLAT_LAY_DIFFUSION_PROMPT.contains("1:1 square frame"))
    }

    @Test
    fun `ApkDeltaPatcher creates tiny binary delta patch and reconstructs exact SHA-256 across shifted ZIP entries`() {
        val dir = Files.createTempDirectory("dpatch_test_").toFile()
        try {
            val sharedNative = ByteArray(512 * 1024) { i -> ((i * 31 + 7) and 0xFF).toByte() }
            val oldDex = ByteArray(4096) { i -> ((i + 11) and 0xFF).toByte() }
            val newDex = ByteArray(4096 + 217) { i -> ((i * 5 + 99) and 0xFF).toByte() }

            fun writeMockApk(outFile: File, dexBytes: ByteArray, nativeBytes: ByteArray) {
                ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                    val dexEntry = ZipEntry("classes.dex")
                    zos.putNextEntry(dexEntry)
                    zos.write(dexBytes)
                    zos.closeEntry()

                    val crc = CRC32().apply { update(nativeBytes) }.value
                    val soEntry = ZipEntry("lib/arm64-v8a/libonnxruntime.so").apply {
                        method = ZipEntry.STORED
                        size = nativeBytes.size.toLong()
                        compressedSize = nativeBytes.size.toLong()
                        this.crc = crc
                    }
                    zos.putNextEntry(soEntry)
                    zos.write(nativeBytes)
                    zos.closeEntry()
                }
            }

            val baseApk = File(dir, "base.apk")
            val targetApk = File(dir, "target.apk")
            val patchFile = File(dir, "update.dpatch")
            val rebuiltApk = File(dir, "rebuilt.apk")

            writeMockApk(baseApk, oldDex, sharedNative)
            writeMockApk(targetApk, newDex, sharedNative)

            val statsRes = ApkDeltaPatcher.createPatch(baseApk, targetApk, patchFile)
            assertTrue(statsRes.isSuccess)
            val stats = statsRes.getOrThrow()
            assertTrue("Expected delta patch much smaller than target APK: ${stats.patchSize} vs ${targetApk.length()}", stats.patchSize < targetApk.length() / 5)
            assertTrue("Expected >=500KB copied from base APK: ${stats.copiedBytes}", stats.copiedBytes >= 500_000L)

            val expectedSha = ApkDeltaPatcher.computeFileSha256Hex(targetApk)
            val applyRes = ApkDeltaPatcher.applyPatch(baseApk, patchFile, rebuiltApk, expectedTargetSha256Hex = expectedSha)
            assertTrue(applyRes.isSuccess)
            assertEquals(expectedSha, applyRes.getOrThrow())
            assertArrayEquals(targetApk.readBytes(), rebuiltApk.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ApkDeltaPatcher rejects mismatched base APK, tampered target SHA-256, and malicious copy bombs`() {
        val dir = Files.createTempDirectory("dpatch_sec_").toFile()
        try {
            val baseA = File(dir, "a.apk").apply { writeBytes("BASE_A_CONTENT".repeat(120).toByteArray()) }
            val baseB = File(dir, "b.apk").apply { writeBytes("BASE_B_DIFFERENT".repeat(120).toByteArray()) }
            val target = File(dir, "target.apk").apply { writeBytes("TARGET_CONTENT".repeat(120).toByteArray()) }
            val patch = File(dir, "p.dpatch")
            val out = File(dir, "out.apk")

            assertTrue(ApkDeltaPatcher.createPatch(baseA, target, patch).isSuccess)
            assertTrue(ApkDeltaPatcher.applyPatch(baseB, patch, out).isFailure)
            assertFalse(out.exists())

            assertTrue(ApkDeltaPatcher.applyPatch(baseA, patch, out, expectedTargetSha256Hex = "0".repeat(64)).isFailure)
            assertFalse(out.exists())

            // Craft malicious out-of-bounds OP_COPY patch claiming baseA's SHA-256
            val malPatch = File(dir, "mal.dpatch")
            val baseSha = ApkDeltaPatcher.computeFileSha256Bytes(baseA)
            val fakeTargetSha = ByteArray(32) { 0x11 }
            val bodyBaos = ByteArrayOutputStream()
            GZIPOutputStream(bodyBaos).use { gz ->
                DataOutputStream(gz).use { dos ->
                    dos.writeByte(1) // OP_COPY
                    dos.writeLong(baseA.length() - 4L)
                    dos.writeInt(512) // overflows baseA bounds!
                    dos.writeByte(0)
                }
            }
            DataOutputStream(FileOutputStream(malPatch)).use { hdr ->
                hdr.write(byteArrayOf('D'.code.toByte(), 'P'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(), 'C'.code.toByte(), 'H'.code.toByte(), '1'.code.toByte(), 0))
                hdr.write(baseSha)
                hdr.write(fakeTargetSha)
                hdr.writeLong(512L)
                hdr.write(bodyBaos.toByteArray())
            }
            assertTrue(ApkDeltaPatcher.applyPatch(baseA, malPatch, out).isFailure)
            assertFalse(out.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
