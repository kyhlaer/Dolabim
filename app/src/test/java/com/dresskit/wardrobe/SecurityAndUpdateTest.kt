package com.dresskit.wardrobe

import com.dresskit.wardrobe.data.AiStudioService
import com.dresskit.wardrobe.data.AnalyticsService
import com.dresskit.wardrobe.data.AnimatedGifGenerator
import com.dresskit.wardrobe.data.ApkDeltaPatcher
import com.dresskit.wardrobe.data.AppUpdateManager
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.SecurityGuards
import com.dresskit.wardrobe.data.SocialProfileService
import com.dresskit.wardrobe.data.StoreSearchLinks
import com.dresskit.wardrobe.data.WeatherCondition
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.DeepLinkTarget
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.SocialPostData
import com.dresskit.wardrobe.model.StatsVisibilityConfig
import com.dresskit.wardrobe.model.STYLE_VIBE_PRESETS
import com.dresskit.wardrobe.model.StyleTag
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.theme.STUDIO_ECOMMERCE_PROMPT
import org.json.JSONArray
import org.json.JSONObject
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
            "https://[::ffff:127.0.0.1]/mapped-loopback",
            "https://[::ffff:7f00:1]/mapped-hex",
            "https://[64:ff9b::127.0.0.1]/nat64",
            "https://[100::1]/discard",
            "https://[2001::1]/teredo",
            "https://[2001:db8::1]/docs",
            "https://[2002:7f00:1::]/6to4",
            "https://0177.0.0.1/octal",
            "https://0x7f.0.0.1/hex",
            "https://2130706433/int",
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
        assertTrue(AppUpdateManager.isVersionNewer("1.43.261", "1.43.26"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.26", "1.43.25"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.25", "1.43.24"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.24", "1.43.23"))
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
    fun `ImageStorage calculateInSampleSize downsamples massive photos without overflow`() {
        assertEquals(1, ImageStorage.calculateInSampleSize(1080, 1440, 1440))
        // 200MP camera photo (16320 x 12240)
        val sample200Mp = ImageStorage.calculateInSampleSize(16320, 12240, 1440)
        assertTrue(sample200Mp >= 8)
        assertTrue(16320 / sample200Mp <= 2880)
        // Extreme panorama (24000 x 18000)
        val sampleExtreme = ImageStorage.calculateInSampleSize(24000, 18000, 1440)
        assertTrue(sampleExtreme >= 16)
        assertTrue(24000 / sampleExtreme <= 2880)
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
        assertEquals("Lacivert Kruvaze Takım", StoreSearchLinks.cleanItemOnlyQuery("Lacivert Kruvaze Takım, Ekru Gömlek, Makosen"))
        assertEquals("Siyah Deri Ceket", StoreSearchLinks.cleanItemOnlyQuery("Erkek Siyah Deri Ceket"))

        val links = StoreSearchLinks.buildStoreSearchLinks("kadın", "Siyah/Gri Boxy Hoodie")
        assertEquals(10, links.size)
        assertTrue(links.none { it.first == "Pull&Bear" })
        links.forEach { (store, url) ->
            assertTrue("Unsafe URL for $store: $url", SecurityGuards.isSafePublicHttpsUrl(url, maxLen = 1000))
        }
        val zaraUrl = links.first { it.first == "Zara" }.second
        assertEquals("https://www.zara.com/tr/tr/search?searchTerm=Siyah+Gri+Hoodie&section=WOMAN", zaraUrl)
        val bershkaUrl = links.first { it.first == "Bershka" }.second
        assertEquals("https://www.bershka.com/tr/q/Siyah%20Gri%20Hoodie?gender=woman", bershkaUrl)
        assertFalse(bershkaUrl.contains("site:bershka.com"))
        assertTrue(links.first { it.first == "Hepsiburada" }.second.startsWith("https://www.hepsiburada.com/ara?q="))
        assertTrue(links.first { it.first == "Boyner" }.second.startsWith("https://www.boyner.com.tr/search?q="))

        val menLinks = StoreSearchLinks.buildStoreSearchLinks("erkek", "Bej Trençkot & Siyah Kazak")
        assertEquals("https://www.zara.com/tr/tr/search?searchTerm=Bej+Tren%C3%A7kot&section=MAN", menLinks.first { it.first == "Zara" }.second)
        assertEquals("https://www.bershka.com/tr/q/Bej%20Tren%C3%A7kot?gender=man", menLinks.first { it.first == "Bershka" }.second)
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
    fun `parseGradioSseOutput parses image URLs, inline base64, quota limits, and rejects foreign or legacy hosts`() {
        val sampleHost = "black-forest-labs-flux-2-dev.hf.space"
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
        val qwenSse = """
            event: complete
            data: [{"image": "data:image/png;base64,$inlineB64", "seed": 1, "status": "success"}]
        """.trimIndent()
        assertEquals(
            AiStudioService.GradioSseOutcome.InlineBase64(inlineB64),
            AiStudioService.parseGradioSseOutput(qwenSse, "qwen-qwen-image-edit-2511.hf.space")
        )

        val quotaSse = """
            event: error
            data: {"error": "You have exceeded your ZeroGPU runs limit. Authenticate with a Hugging Face token", "title": "ZeroGPU quota exceeded"}
        """.trimIndent()
        assertTrue(AiStudioService.parseGradioSseOutput(quotaSse, sampleHost) is AiStudioService.GradioSseOutcome.QuotaExceeded)

        val genericErrSse = "event: error\ndata: null\n"
        assertTrue(AiStudioService.parseGradioSseOutput(genericErrSse, sampleHost) is AiStudioService.GradioSseOutcome.SpaceError)

        val legacyHostOutcome = AiStudioService.parseGradioSseOutput(urlSse, "kulkas2pintu-qwen-edit-image.hf.space")
        assertTrue(legacyHostOutcome is AiStudioService.GradioSseOutcome.SpaceError)

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
    fun `FLAT_LAY_DIFFUSION_PROMPT and official HF allowlist enforce strict garment fidelity`() {
        assertEquals(STUDIO_ECOMMERCE_PROMPT, AiStudioService.FLAT_LAY_DIFFUSION_PROMPT)
        assertFalse(AiStudioService.FLAT_LAY_DIFFUSION_PROMPT.startsWith("Transform"))
        assertTrue(AiStudioService.FLAT_LAY_DIFFUSION_PROMPT.contains("#FFFFFF"))
        assertTrue(AiStudioService.FLAT_LAY_DIFFUSION_PROMPT.contains("1:1 square frame"))
        assertTrue(AiStudioService.FLAT_LAY_DIFFUSION_PROMPT.contains("back view stays back view"))
        assertTrue(AiStudioService.FLAT_LAY_DIFFUSION_PROMPT.contains("exact full size"))
        assertEquals(
            setOf(
                "black-forest-labs-flux-2-dev.hf.space",
                "black-forest-labs-flux-2-klein-9b.hf.space",
                "qwen-qwen-image-edit-2511.hf.space",
                "fireredteam-firered-image-edit-1-1.hf.space",
                "black-forest-labs-flux-1-kontext-dev.hf.space"
            ),
            AiStudioService.ALLOWED_HF_HOSTS
        )
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

    @Test
    fun `ClothingItem activePhotos separates Remove BG, Original, and Catalog with per-slot fallback`() {
        val mixedItem = ClothingItem(
            id = "item_test_1",
            name = "Back Graphic Hoodie",
            category = ClothingCategory.TOP,
            subCategory = ClothingSubCategory.HOODIE,
            colors = listOf(GarmentColor.ECRU),
            styleTag = StyleTag.CASUAL,
            seasons = listOf(Season.ALL),
            photoFileNames = listOf("bgrm_1.jpg", "bgrm_2.jpg"),
            originalPhotoFileNames = listOf("orig_1.jpg", "orig_2.jpg"),
            catalogPhotoFileNames = listOf("catalog_1.jpg", ""),
            useOriginalPhotos = false,
            useCatalogPhotos = false
        )
        assertEquals(listOf("bgrm_1.jpg", "bgrm_2.jpg"), mixedItem.activePhotos)
        assertEquals(listOf("orig_1.jpg", "orig_2.jpg"), mixedItem.copy(useOriginalPhotos = true).activePhotos)
        assertEquals(
            listOf("catalog_1.jpg", "bgrm_2.jpg"),
            mixedItem.copy(useOriginalPhotos = false, useCatalogPhotos = true).activePhotos
        )

        assertTrue(ImageStorage.isOfflineCutoutFileName("bgrm_123.jpg"))
        assertFalse(ImageStorage.isOfflineCutoutFileName("studio_legacy.jpg"))
        assertFalse(ImageStorage.isOfflineCutoutFileName("catalog_123.jpg"))
        assertFalse(ImageStorage.isOfflineCutoutFileName("orig_123.jpg"))
        assertFalse(ImageStorage.isOfflineCutoutFileName(""))
        assertFalse(ImageStorage.isOfflineCutoutFileName(null))
    }

    @Test
    fun `v143262 version ordering and error exception classes behave as expected`() {
        assertTrue(AppUpdateManager.isVersionNewer("1.43.262", "1.43.261"))
        val busy = AiStudioService.StudioServiceBusyException("busy")
        val quota = AiStudioService.StudioQuotaExceededException("quota")
        assertEquals("busy", busy.message)
        assertEquals("quota", quota.message)
    }

    @Test
    fun `telemetry schema maps match Firestore rules field counts, monotonic bounds, and session ID rules`() {
        val uid = "user_Alpha-01"
        val validSessionId = "${uid}_1764000000000"
        assertTrue(AnalyticsService.isValidSessionDocId(validSessionId, uid))
        assertFalse(AnalyticsService.isValidSessionDocId("other_${1764000000000}", uid))
        assertFalse(AnalyticsService.isValidSessionDocId("${uid}_123", uid))

        val deviceMap = AnalyticsService.buildDeviceSchemaMap(
            uid = uid,
            anonTag = "",
            platform = "unexpected_platform",
            appVersion = "1.43.262",
            deviceModel = "Pixel 9",
            osInfo = "Android 15",
            screenSize = "1080x2424",
            language = "TR",
            theme = "MODERN",
            timezone = "Europe/Istanbul",
            country = "Turkey",
            city = "Istanbul",
            region = "Marmara",
            firstSeenAtMs = 1764000005000L,
            lastSeenAtMs = 1764000000000L, // deliberately earlier than firstSeenAtMs to verify monotonic clamp
            sessionCount = 0, // below min 1
            totalDurationSec = -50L,
            activeSessionId = validSessionId,
            lastTab = "wardrobe",
            isOnline = true,
            wardrobeItemCount = 12,
            wishlistCount = -3,
            outfitCount = 4,
            lifetimeFeatureCounts = mapOf("add_item" to 5, "neg_val" to -2),
            lifetimeTabSeconds = mapOf("wardrobe" to 120),
            lifetimeFeatureSeconds = mapOf("catalog_ai" to 14),
            errorCount = 1,
            lastError = "busy"
        )
        assertEquals(28, deviceMap.size)
        assertEquals("android", deviceMap["platform"])
        assertEquals("A-USER", deviceMap["anonTag"])
        assertEquals(1764000005000L, deviceMap["firstSeenAtMs"])
        assertEquals(1764000005000L, deviceMap["lastSeenAtMs"])
        assertEquals(1L, deviceMap["sessionCount"])
        assertEquals(0L, deviceMap["totalDurationSec"])
        assertEquals(0L, deviceMap["wishlistCount"])
        assertFalse(deviceMap.containsKey("profileUsername"))
        @Suppress("UNCHECKED_CAST")
        val lifeCounts = deviceMap["lifetimeFeatureCounts"] as Map<String, Int>
        assertEquals(0, lifeCounts["neg_val"])

        val profileDeviceMap = AnalyticsService.buildDeviceSchemaMap(
            uid = uid,
            anonTag = "A-TEST",
            platform = "android",
            appVersion = "1.43.276",
            deviceModel = "Pixel",
            osInfo = "Android 15",
            screenSize = "1080x2400",
            language = "TR",
            theme = "MODERN",
            timezone = "Europe/Istanbul",
            country = "Turkey",
            city = "Istanbul",
            region = "Marmara",
            firstSeenAtMs = 1764000005000L,
            lastSeenAtMs = 1764000005000L,
            sessionCount = 1,
            totalDurationSec = 10L,
            activeSessionId = validSessionId,
            lastTab = "feed",
            isOnline = true,
            wardrobeItemCount = 3,
            wishlistCount = 1,
            outfitCount = 1,
            lifetimeFeatureCounts = emptyMap(),
            lifetimeTabSeconds = emptyMap(),
            lifetimeFeatureSeconds = emptyMap(),
            errorCount = 0,
            lastError = "",
            profileUsername = "kerem",
            profileDisplayName = "Kerem",
            profileAvatar = "data:image/jpeg;base64,AAAA"
        )
        assertEquals(31, profileDeviceMap.size)

        val sessionMap = AnalyticsService.buildSessionSchemaMap(
            uid = uid,
            sessionId = validSessionId,
            anonTag = "A-TEST",
            platform = "web_pwa",
            appVersion = "1.43.270",
            deviceModel = "Chrome",
            osInfo = "Android",
            screenSize = "412x915",
            language = "EN",
            theme = "PASTEL",
            timezone = "Europe/Istanbul",
            country = "Turkey",
            city = "Ankara",
            region = "Central Anatolia",
            entrySource = "",
            networkType = "wifi",
            startedAtMs = 1764000010000L,
            lastActiveAtMs = 1764000000000L,
            durationSec = 999999, // over 172800 limit
            activeTab = "outfits",
            isOnline = false,
            tabSeconds = mapOf("outfits" to 45),
            featureCounts = mapOf("tryon_3d" to 2),
            featureSeconds = mapOf("catalog_ai" to 9),
            recentEvents = (1..60).map { "1764000000000|feature|f$it|" },
            wardrobeStats = mapOf("items" to 12)
        )
        assertEquals(26, sessionMap.size)
        assertEquals("web_pwa", sessionMap["platform"])
        assertEquals("cold_start", sessionMap["entrySource"])
        assertEquals(1764000010000L, sessionMap["startedAtMs"])
        assertEquals(1764000010000L, sessionMap["lastActiveAtMs"])
        assertEquals(172800L, sessionMap["durationSec"])
        @Suppress("UNCHECKED_CAST")
        val events = sessionMap["recentEvents"] as List<String>
        assertEquals(50, events.size)
    }

    @Test
    fun `v143270 version ordering is strictly newer than v143263`() {
        assertTrue(AppUpdateManager.isVersionNewer("1.43.270", "1.43.263"))
        assertFalse(AppUpdateManager.isVersionNewer("1.43.263", "1.43.270"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.263", "1.43.262"))
    }

    @Test
    fun `social username normalization, validation, and deep link parsing work across formats`() {
        assertEquals("kerem_style", SocialProfileService.normalizeUsername("  @Kerem_Style "))
        assertEquals("sahin_irem", SocialProfileService.normalizeUsername("  @Şahin İrem "))
        assertTrue(SocialProfileService.isValidUsername("kerem_style.17"))
        assertTrue(SocialProfileService.isValidUsername("kyhlaer"))
        assertTrue(SocialProfileService.isValidUsername("Şahin İrem"))
        assertFalse(SocialProfileService.isValidUsername("ab"))
        assertFalse(SocialProfileService.isValidUsername("kerem..style"))
        assertFalse(SocialProfileService.isValidUsername(".kerem"))
        assertFalse(SocialProfileService.isValidUsername("kerem."))
        assertFalse(SocialProfileService.isValidUsername("!!!"))

        assertEquals(
            DeepLinkTarget.SharedItem("AbCdEf12_34"),
            SocialProfileService.parseDeepLink("https://kyhlaer.github.io/Dolabim/?item=AbCdEf12_34")
        )
        assertEquals(
            DeepLinkTarget.UserProfile("kerem_style", isUid = false),
            SocialProfileService.parseDeepLink("https://kyhlaer.github.io/Dolabim/?u=Kerem_Style")
        )
        assertEquals(
            DeepLinkTarget.UserProfile("abcUID_12345", isUid = true),
            SocialProfileService.parseDeepLink("https://kyhlaer.github.io/Dolabim/?uid=abcUID_12345")
        )
        assertEquals(
            DeepLinkTarget.SharedItem("ZzYyXx98"),
            SocialProfileService.parseDeepLink("dolabim://item/ZzYyXx98")
        )
        assertNull(SocialProfileService.parseDeepLink("https://kyhlaer.github.io/Dolabim/?item=bad"))
    }

    @Test
    fun `filtered wardrobe summary hides stats toggled off by owner`() {
        val sample = listOf(
            ClothingItem(
                id = "1",
                name = "Black Hoodie",
                category = ClothingCategory.TOP,
                subCategory = ClothingSubCategory.HOODIE,
                colors = listOf(GarmentColor.BLACK),
                seasons = listOf(Season.AUTUMN),
                photoFileNames = listOf("a.jpg"),
                isFavorite = true,
                priceText = "1200 TL"
            ),
            ClothingItem(
                id = "2",
                name = "Wish Coat",
                category = ClothingCategory.OUTERWEAR,
                subCategory = ClothingSubCategory.COAT,
                colors = listOf(GarmentColor.BEIGE),
                seasons = listOf(Season.WINTER),
                photoFileNames = listOf("b.jpg"),
                isWishlist = true,
                priceText = "3500 TL"
            )
        )
        val hiddenVis = StatsVisibilityConfig(
            showTotalItems = true,
            showCategoryBreakdown = false,
            showFavoriteCount = false,
            showWishlistCount = true,
            showOutfitCount = false,
            showTopColors = false,
            showTopSubCategories = false,
            showEstimatedValue = false
        )
        val summary = SocialProfileService.computeFilteredWardrobeSummary(sample, emptyList(), hiddenVis)
        assertEquals(1, summary["totalItems"])
        assertEquals(1, summary["wishlistCount"])
        assertFalse(summary.containsKey("favoriteCount"))
        assertFalse(summary.containsKey("categoryCounts"))
        assertFalse(summary.containsKey("outfitCount"))
        assertFalse(summary.containsKey("estimatedWishlistValue"))
    }

    @Test
    fun `hourly weather parser builds rolling 24 hours starting at current hour`() {
        val dateIso = "2026-11-24"
        val nextDateIso = "2026-11-25"
        val times = mutableListOf<String>()
        val temps = mutableListOf<Double>()
        val feels = mutableListOf<Double>()
        val rain = mutableListOf<Int>()
        val codes = mutableListOf<Int>()
        val winds = mutableListOf<Double>()
        for (dIso in listOf(dateIso, nextDateIso)) {
            for (h in 0..23) {
                times.add("${dIso}T%02d:00".format(h))
                temps.add(10.0 + (h % 12))
                feels.add(9.0 + (h % 12))
                rain.add(if (dIso == dateIso && h == 14) 75 else 10)
                codes.add(if (dIso == dateIso && h == 14) 61 else 1)
                winds.add(12.5)
            }
        }

        val parsed = OutfitRecommendationEngine.parseHourlyArrays(
            times = times,
            temps = temps,
            apparent = feels,
            rainProbs = rain,
            codes = codes,
            winds = winds,
            dateIso = dateIso,
            isToday = true,
            currentHour = 14
        )
        assertEquals(24, parsed.size)
        assertEquals("14:00", parsed[0].hourLabel)
        assertTrue(parsed[0].isCurrentHour)
        assertFalse(parsed[0].isNextDay)
        assertEquals(75, parsed[0].precipitationProb)
        assertEquals(13, parsed[0].windKmh)
        assertEquals("23:00", parsed[9].hourLabel)
        assertFalse(parsed[9].isNextDay)
        assertEquals("00:00", parsed[10].hourLabel)
        assertTrue(parsed[10].isNextDay)
        assertFalse(parsed[10].isCurrentHour)
        assertEquals("13:00", parsed[23].hourLabel)
        assertTrue(parsed[23].isNextDay)

        val fallback = OutfitRecommendationEngine.buildFallbackHourly(
            dayIndex = 0,
            maxTempC = 20,
            minTempC = 11,
            condition = WeatherCondition.RAINY,
            currentHour = 14
        )
        assertEquals(24, fallback.size)
        assertEquals("14:00", fallback[0].hourLabel)
        assertTrue(fallback[0].isCurrentHour)
        assertFalse(fallback[0].isNextDay)
        assertEquals("00:00", fallback[10].hourLabel)
        assertTrue(fallback[10].isNextDay)
        assertEquals("13:00", fallback[23].hourLabel)
        assertTrue(fallback[23].isNextDay)
    }

    @Test
    fun `shared outfit deep links and clean firestore error formatting work`() {
        val webOutfit = SocialProfileService.parseDeepLink("https://kyhlaer.github.io/Dolabim/?outfit=AbCd1234EfGh")
        assertEquals(DeepLinkTarget.SharedOutfit("AbCd1234EfGh"), webOutfit)

        val schemeOutfit = SocialProfileService.parseDeepLink("dolabim://outfit/AbCd1234EfGh")
        assertEquals(DeepLinkTarget.SharedOutfit("AbCd1234EfGh"), schemeOutfit)

        val rawJson = """{"error":{"code":403,"message":"Missing or insufficient permissions.","status":"PERMISSION_DENIED"}}"""
        val cleanErr = SocialProfileService.formatFsError(403, rawJson, "Profil kaydedilemedi")
        assertTrue(cleanErr.startsWith("Profil kaydedilemedi:"))
        assertTrue(cleanErr.contains("403"))
        assertFalse(cleanErr.contains("\"error\""))
    }

    @Test
    fun `social feed gif generator, 15 minute caption window, and post deep links work reliably`() {
        val presets = AnimatedGifGenerator.presets
        assertEquals(24, presets.size)
        assertTrue(presets.all { it.dataUrl.startsWith("data:image/gif;base64,R0lGOD") })
        assertTrue(presets.all { AnimatedGifGenerator.isSafeGifDataUrl(it.dataUrl) })

        assertTrue(AnimatedGifGenerator.isSafeExternalGifUrl("https://media.tenor.com/abc/look.gif"))
        assertTrue(AnimatedGifGenerator.isSafeExternalGifUrl("https://media.giphy.com/media/xyz/giphy.gif"))
        assertFalse(AnimatedGifGenerator.isSafeExternalGifUrl("http://media.tenor.com/insecure.gif"))
        assertFalse(AnimatedGifGenerator.isSafeExternalGifUrl("https://evil.example.com/bad.gif"))

        val createdMs = 1_764_000_000_000L
        val post = SocialPostData(
            postId = "p_abc123456789",
            ownerUid = "uid_12345",
            ownerUsername = "kerem",
            ownerDisplayName = "Kerem",
            ownerAvatar = "",
            postKind = "outfit",
            caption = "Sokak stili denemesi",
            mediaType = "text",
            photos = emptyList(),
            posterBase64 = "",
            chunkCount = 0,
            videoMime = "",
            videoDurationSec = 0,
            attachedItemName = "",
            attachedCategory = "",
            attachedPriceText = "",
            attachedProductUrl = "",
            createdAtMs = createdMs,
            editedAtMs = 0L,
            isPrivatePost = true
        )
        assertTrue(post.canEditCaption(createdMs + 899_999L))
        assertTrue(post.canEditCaption(createdMs + 900_000L))
        assertFalse(post.canEditCaption(createdMs + 900_001L))
        assertEquals(0, post.editRemainingMinutes(createdMs + 900_001L))
        assertTrue(post.shareUrl.contains("?post=p_abc123456789&owner=uid_12345"))

        val parsedWebPost = SocialProfileService.parseDeepLink("https://kyhlaer.github.io/Dolabim/?post=p_abc123456789&owner=uid_12345")
        assertEquals(DeepLinkTarget.SocialPost("p_abc123456789", "uid_12345"), parsedWebPost)
        val parsedSchemePost = SocialProfileService.parseDeepLink("dolabim://post/p_abc123456789")
        assertEquals(DeepLinkTarget.SocialPost("p_abc123456789", ""), parsedSchemePost)
    }

    @Test
    fun `v1_43_273 unified style presets and wardrobe color palette summary stats work cleanly`() {
        assertEquals(20, STYLE_VIBE_PRESETS.size)

        val defVis = StatsVisibilityConfig()
        assertTrue(defVis.showColorPalette)
        assertTrue(StatsVisibilityConfig.fromJson(null).showColorPalette)
        val hiddenVis = defVis.copy(showColorPalette = false)
        assertFalse(hiddenVis.showColorPalette)

        val sampleItems = listOf(
            ClothingItem(
                id = "c1",
                name = "Siyah Hoodie",
                category = ClothingCategory.TOP,
                subCategory = ClothingSubCategory.HOODIE,
                colors = listOf(GarmentColor.BLACK),
                seasons = listOf(Season.AUTUMN),
                photoFileNames = listOf("a.jpg"),
                isFavorite = true
            ),
            ClothingItem(
                id = "c2",
                name = "Beyaz Tişört",
                category = ClothingCategory.TOP,
                subCategory = ClothingSubCategory.TSHIRT,
                colors = listOf(GarmentColor.WHITE),
                seasons = listOf(Season.SUMMER),
                photoFileNames = listOf("b.jpg")
            )
        )
        val withPalette = SocialProfileService.computeFilteredWardrobeSummary(sampleItems, emptyList<Outfit>(), defVis)
        assertTrue(withPalette.containsKey("colorPalette"))
        assertTrue(withPalette["colorPalette"].toString().contains("BLACK:1"))
        assertTrue(withPalette["colorPalette"].toString().contains("WHITE:1"))

        val withoutPalette = SocialProfileService.computeFilteredWardrobeSummary(sampleItems, emptyList<Outfit>(), hiddenVis)
        assertFalse(withoutPalette.containsKey("colorPalette"))
    }
}
