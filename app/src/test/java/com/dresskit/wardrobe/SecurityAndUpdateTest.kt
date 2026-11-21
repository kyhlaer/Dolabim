package com.dresskit.wardrobe

import com.dresskit.wardrobe.data.AiStudioService
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(AppUpdateManager.isVersionNewer("1.43.2", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("v1.43.2", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("V1.44", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("2.0", "1.43.1"))
        assertTrue(AppUpdateManager.isVersionNewer("1.43.1.1", "1.43.1"))

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

        assertNull(AiStudioService.extractGeneratedImageBase64("""{"steps":[{"type":"model_output","content":[{"type":"text","text":"ok"}]}]}"""))
        assertNull(AiStudioService.extractGeneratedImageBase64(""))
    }
}
