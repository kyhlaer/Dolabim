package com.dresskit.wardrobe

import com.dresskit.wardrobe.data.AppUpdateManager
import com.dresskit.wardrobe.data.InspirationCategory
import com.dresskit.wardrobe.data.LookbookCatalog
import com.dresskit.wardrobe.data.LookbookGender
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.data.WeatherCondition
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.GenderPreference
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.StyleTag
import com.dresskit.wardrobe.ui.components.BooleanPrefStore
import com.dresskit.wardrobe.ui.components.DockSnowSimulation
import com.dresskit.wardrobe.ui.components.WinterThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookbookMoodWinterUnitTest {

    private class InMemoryBooleanPrefStore : BooleanPrefStore {
        private val map = mutableMapOf<String, Boolean>()
        override fun getBoolean(key: String, default: Boolean): Boolean = map[key] ?: default
        override fun putBoolean(key: String, value: Boolean) {
            map[key] = value
        }
    }

    private fun buildSampleCloset(): List<ClothingItem> = listOf(
        ClothingItem(
            id = "top_hoodie",
            name = "Ekru Hoodie",
            category = ClothingCategory.TOP,
            subCategory = ClothingSubCategory.HOODIE,
            styleTag = StyleTag.CASUAL,
            seasons = listOf(Season.AUTUMN, Season.WINTER),
            colors = listOf(GarmentColor.ECRU),
            photoFileNames = listOf("h.jpg")
        ),
        ClothingItem(
            id = "top_shirt",
            name = "Beyaz Poplin Gömlek",
            category = ClothingCategory.TOP,
            subCategory = ClothingSubCategory.SHIRT,
            styleTag = StyleTag.SMART,
            seasons = listOf(Season.ALL),
            colors = listOf(GarmentColor.WHITE),
            photoFileNames = listOf("s.jpg")
        ),
        ClothingItem(
            id = "top_tee_bright",
            name = "Sarı Tişört",
            category = ClothingCategory.TOP,
            subCategory = ClothingSubCategory.TSHIRT,
            styleTag = StyleTag.SPORT,
            seasons = listOf(Season.SPRING, Season.SUMMER),
            colors = listOf(GarmentColor.YELLOW),
            photoFileNames = listOf("y.jpg")
        ),
        ClothingItem(
            id = "top_black_knit",
            name = "Siyah Triko",
            category = ClothingCategory.TOP,
            subCategory = ClothingSubCategory.SWEATER,
            styleTag = StyleTag.CASUAL,
            seasons = listOf(Season.AUTUMN, Season.WINTER),
            colors = listOf(GarmentColor.BLACK),
            photoFileNames = listOf("bk.jpg")
        ),
        ClothingItem(
            id = "bot_sweats",
            name = "Açık Gri Eşofman",
            category = ClothingCategory.BOTTOM,
            subCategory = ClothingSubCategory.SWEATPANTS,
            styleTag = StyleTag.SPORT,
            seasons = listOf(Season.ALL),
            colors = listOf(GarmentColor.LIGHT_GRAY),
            photoFileNames = listOf("sw.jpg")
        ),
        ClothingItem(
            id = "bot_chino",
            name = "Bej Chino",
            category = ClothingCategory.BOTTOM,
            subCategory = ClothingSubCategory.CHINO,
            styleTag = StyleTag.SMART,
            seasons = listOf(Season.ALL),
            colors = listOf(GarmentColor.BEIGE),
            photoFileNames = listOf("ch.jpg")
        ),
        ClothingItem(
            id = "bot_black_pants",
            name = "Siyah Pantolon",
            category = ClothingCategory.BOTTOM,
            subCategory = ClothingSubCategory.PANTS,
            styleTag = StyleTag.FORMAL,
            seasons = listOf(Season.ALL),
            colors = listOf(GarmentColor.BLACK),
            photoFileNames = listOf("bp.jpg")
        ),
        ClothingItem(
            id = "out_blazer",
            name = "Bordo Blazer",
            category = ClothingCategory.OUTERWEAR,
            subCategory = ClothingSubCategory.BLAZER,
            styleTag = StyleTag.SMART,
            seasons = listOf(Season.AUTUMN, Season.SPRING),
            colors = listOf(GarmentColor.BURGUNDY),
            photoFileNames = listOf("bl.jpg")
        ),
        ClothingItem(
            id = "out_black_jacket",
            name = "Antrasit Ceket",
            category = ClothingCategory.OUTERWEAR,
            subCategory = ClothingSubCategory.JACKET,
            styleTag = StyleTag.CASUAL,
            seasons = listOf(Season.AUTUMN, Season.WINTER),
            colors = listOf(GarmentColor.CHARCOAL),
            photoFileNames = listOf("aj.jpg")
        ),
        ClothingItem(
            id = "shoe_sneaker",
            name = "Beyaz Sneaker",
            category = ClothingCategory.SHOES,
            subCategory = ClothingSubCategory.SNEAKER,
            styleTag = StyleTag.CASUAL,
            seasons = listOf(Season.ALL),
            colors = listOf(GarmentColor.WHITE),
            photoFileNames = listOf("sn.jpg")
        ),
        ClothingItem(
            id = "shoe_loafer",
            name = "Siyah Loafer",
            category = ClothingCategory.SHOES,
            subCategory = ClothingSubCategory.LOAFER,
            styleTag = StyleTag.FORMAL,
            seasons = listOf(Season.ALL),
            colors = listOf(GarmentColor.BLACK),
            photoFileNames = listOf("lf.jpg")
        )
    )

    @Test
    fun lookbookCatalog_hasRestoredOutfitsBalancedSixStylesAndNoUnisex() {
        val men = LookbookCatalog.MEN_LOOKS
        val women = LookbookCatalog.WOMEN_LOOKS
        val all = LookbookCatalog.ALL_LOOKS

        // Only MEN and WOMEN gender tabs exist (Unisex removed)
        assertEquals(listOf(LookbookGender.MEN, LookbookGender.WOMEN), LookbookGender.entries)

        // All 82 original outfits (41 men + 41 women) restored + 30 verified new outfits = 112
        assertEquals(56, men.size)
        assertEquals(56, women.size)
        assertEquals(112, all.size)

        // Unique IDs across the whole catalog
        assertEquals(112, all.map { it.id }.toSet().size)

        // Verify original restored look IDs are present
        val idSet = all.map { it.id }.toSet()
        assertTrue(idSet.contains("ys_1") && idSet.contains("ys_46"))
        assertTrue(idSet.contains("ys_w1") && idSet.contains("ys_w41"))

        // All 6 non-ALL styles well populated for both Men and Women
        val styles = InspirationCategory.entries.filter { it != InspirationCategory.ALL }
        assertEquals(6, styles.size)
        styles.forEach { cat ->
            assertTrue("Men should have >= 6 looks in $cat", men.count { it.category == cat } >= 6)
            assertTrue("Women should have >= 6 looks in $cat", women.count { it.category == cat } >= 6)
        }

        // Required fields and safe URLs
        all.forEach { look ->
            assertTrue("${look.id} invalid imageUrl", look.imageUrl.startsWith("https://"))
            assertTrue("${look.id} missing TR title", look.trTitle.isNotBlank())
            assertTrue("${look.id} missing EN title", look.enTitle.isNotBlank())
            assertTrue("${look.id} missing pieces", look.trPieces.size >= 3 && look.enPieces.size >= 3)
            assertTrue("${look.id} missing tips", look.trTip.isNotBlank() && look.enTip.isNotBlank())
            assertTrue("${look.id} missing colorDotHexes", look.colorDotHexes.isNotEmpty())
            assertTrue("${look.id} missing paletteColors", look.paletteColors.isNotEmpty())
            assertTrue("${look.id} harmony out of bounds", look.harmonyPercent in 80..99)
        }

        // Style & season filtering: null = Tümü (all), Season.ALL = 4 Mevsim only, specific season = match or ALL
        val allMenViaTumu = LookbookCatalog.filterLooks(men, InspirationCategory.ALL, null)
        assertEquals(men.size, allMenViaTumu.size)

        val fourSeasonMen = LookbookCatalog.filterLooks(men, InspirationCategory.ALL, Season.ALL)
        assertTrue(fourSeasonMen.isNotEmpty())
        assertTrue(fourSeasonMen.all { it.season == Season.ALL })
        assertTrue(fourSeasonMen.size < men.size)

        val smartAutumn = LookbookCatalog.filterLooks(men, InspirationCategory.SMART_CASUAL, Season.AUTUMN)
        assertTrue(smartAutumn.isNotEmpty())
        assertTrue(smartAutumn.all { it.category == InspirationCategory.SMART_CASUAL })
        assertTrue(smartAutumn.all { it.season == Season.AUTUMN || it.season == Season.ALL })

        val menWinter = LookbookCatalog.filterLooks(men, InspirationCategory.ALL, Season.WINTER)
        assertTrue(menWinter.isNotEmpty())
        assertTrue(menWinter.all { it.season == Season.WINTER || it.season == Season.ALL })

        assertEquals(men, LookbookCatalog.looksForGender(LookbookGender.MEN))
        assertEquals(women, LookbookCatalog.looksForGender(LookbookGender.WOMEN))
    }

    @Test
    fun dockSnowSimulation_accumulatesSmoothlyTapersAtEdgesAndClearsOnShake() {
        val sim = DockSnowSimulation()
        val initialPeak = sim.peakHeightDp()
        assertTrue(initialPeak > 0f && initialPeak < sim.maxHeightDp)
        assertEquals(0f, sim.heightsDp.first(), 0.001f)
        assertEquals(0f, sim.heightsDp.last(), 0.001f)

        // Simulate repeated snowflake hits near the center
        repeat(40) {
            sim.deposit(0.5f, 2.8f)
            sim.relaxAvalanche()
        }
        val builtPeak = sim.peakHeightDp()
        assertTrue("Snow should accumulate higher than initial dusting", builtPeak > initialPeak)
        assertTrue("Snow height must obey tasteful cap", builtPeak <= sim.maxHeightDp + 0.01f)
        // Rounded dock ends stay cleanly tapered to 0
        assertEquals(0f, sim.heightsDp.first(), 0.001f)
        assertEquals(0f, sim.heightsDp.last(), 0.001f)

        // Tab switch shake emits clumps and resets dock snow to 0
        val clumps = sim.shakeAndClear()
        assertTrue("Shaking accumulated snow must emit falling clumps", clumps.isNotEmpty())
        assertEquals(0f, sim.peakHeightDp(), 0.001f)
        assertEquals(0f, sim.displayPeakHeightDp(), 0.001f)

        // Single flake deposit after shake must ease smoothly from 0 without sudden full-height jump
        sim.deposit(0.5f, 2.2f)
        assertEquals(0f, sim.displayPeakHeightDp(), 0.001f)
        sim.stepSmooth(0.016f)
        val afterOneFrame = sim.displayPeakHeightDp()
        assertTrue("First frame should be tiny gradual frost ($afterOneFrame)", afterOneFrame > 0f && afterOneFrame < 0.25f)
        repeat(45) { sim.stepSmooth(0.033f) }
        assertTrue("Display height should smoothly catch up to target", sim.displayPeakHeightDp() > afterOneFrame)

        // Bridge must subtract overlay root origin (e.g. statusBarsPadding) so snow sits flush on dock top
        val bridge = com.dresskit.wardrobe.ui.components.WinterDockSnowBridge()
        bridge.updateDockBounds(leftPx = 44f, topPx = 2120f, rightPx = 1036f)
        bridge.updateOverlayOrigin(xPx = 0f, yPx = 128f)
        assertEquals(44f, bridge.localDockLeftPx(), 0.01f)
        assertEquals(1036f, bridge.localDockRightPx(), 0.01f)
        assertEquals(1992f, bridge.localDockTopPx(), 0.01f)
    }

    @Test
    fun whatsNewAutoShow_triggersOnMissingOrOlderVersionAndSuppressesSameVersion() {
        assertTrue(AppUpdateManager.shouldShowWhatsNewOnLaunch(null, "1.43.284"))
        assertTrue(AppUpdateManager.shouldShowWhatsNewOnLaunch("", "1.43.284"))
        assertTrue(AppUpdateManager.shouldShowWhatsNewOnLaunch("1.43.283", "1.43.284"))
        assertFalse(AppUpdateManager.shouldShowWhatsNewOnLaunch("1.43.284", "1.43.284"))
        assertFalse(AppUpdateManager.shouldShowWhatsNewOnLaunch("v1.43.284", "1.43.284"))
        assertTrue(AppUpdateManager.shouldShowWhatsNewOnLaunch("1.43.284", "1.43.285"))
        assertFalse(AppUpdateManager.shouldShowWhatsNewOnLaunch("1.43.284", ""))
    }

    @Test
    fun releaseNotesParser_stripsMarkdownFiltersChecksumsAndUsesFallback() {
        val raw = """
            ## Yenilikler
            - **112 Kombinlik** yeni ilham kataloğu eklendi.
            * _Moduna Göre Kombin_ artık en üstte katlanabilir.
            SHA-256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
            Dolabim-patch-v1.43.281-to-v1.43.282.dpatch
            ---
            - Kış & Yılbaşı teması eklendi.
        """.trimIndent()
        val parsed = AppUpdateManager.parseReleaseNotesHighlights(raw)
        assertEquals(3, parsed.size)
        assertTrue(parsed[0].contains("112 Kombinlik") && !parsed[0].contains("**"))
        assertTrue(parsed[1].contains("Moduna Göre Kombin") && !parsed[1].contains("_"))
        assertTrue(parsed.none { it.contains("SHA-256", ignoreCase = true) || it.contains("dpatch") })

        // Empty or checksum-only notes fall back cleanly
        val fallback = AppUpdateManager.parseReleaseNotesHighlights(
            "SHA-256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AppUpdateManager.DEFAULT_WHATS_NEW_EN
        )
        assertEquals(AppUpdateManager.DEFAULT_WHATS_NEW_EN, fallback)
    }

    @Test
    fun lookbookSkinToneMatch_boostsFlatteringAndPenalizesAvoid() {
        val neutralScore = LookbookCatalog.computeLookSkinToneMatch(
            paletteColors = listOf(GarmentColor.BEIGE, GarmentColor.NAVY),
            flatteringColors = emptySet(),
            avoidColors = emptySet()
        )
        assertNull(neutralScore)

        val highScore = LookbookCatalog.computeLookSkinToneMatch(
            paletteColors = listOf(GarmentColor.ECRU, GarmentColor.CAMEL, GarmentColor.NAVY),
            flatteringColors = setOf(GarmentColor.ECRU, GarmentColor.CAMEL),
            avoidColors = setOf(GarmentColor.BLACK)
        )
        val lowScore = LookbookCatalog.computeLookSkinToneMatch(
            paletteColors = listOf(GarmentColor.BLACK, GarmentColor.CHARCOAL),
            flatteringColors = setOf(GarmentColor.ECRU, GarmentColor.CAMEL),
            avoidColors = setOf(GarmentColor.BLACK, GarmentColor.CHARCOAL)
        )
        assertNotNull(highScore)
        assertNotNull(lowScore)
        assertTrue(highScore!! > lowScore!!)
        assertTrue(highScore in 45..99)
        assertTrue(lowScore in 45..99)
    }

    @Test
    fun moodEngine_generatesNonRepeatingMoodOutfitsAndHandlesSmallCloset() {
        val closet = buildSampleCloset()

        OutfitRecommendationEngine.OutfitMood.entries.forEach { mood ->
            val first = OutfitRecommendationEngine.suggestMoodOutfit(
                items = closet,
                mood = mood,
                weather = WeatherCondition.COOL,
                lang = AppLanguage.TR,
                genderPref = GenderPreference.UNISEX
            )
            assertNotNull("Expected suggestion for mood $mood", first)
            assertTrue(first!!.items.size >= 2)
            assertTrue(first.matchScore in 48..99)
            assertTrue(first.reasonNote.isNotBlank())

            // Pressing "Başka Öner" must not immediately repeat the active outfit
            val second = OutfitRecommendationEngine.suggestMoodOutfit(
                items = closet,
                mood = mood,
                weather = WeatherCondition.COOL,
                excludeSignature = first.signature,
                recentSignatures = listOf(first.signature),
                lang = AppLanguage.TR,
                genderPref = GenderPreference.UNISEX
            )
            assertNotNull("Expected alternate suggestion for mood $mood", second)
            assertNotEquals("Mood $mood repeated immediately", first.signature, second!!.signature)
        }

        // Dark & Minimal should prioritize dark pieces
        val darkPick = OutfitRecommendationEngine.suggestMoodOutfit(
            items = closet,
            mood = OutfitRecommendationEngine.OutfitMood.DARK_MINIMAL,
            weather = WeatherCondition.COOL
        )!!
        assertTrue(darkPick.items.any {
            it.colors.firstOrNull() == GarmentColor.BLACK || it.colors.firstOrNull() == GarmentColor.CHARCOAL
        })

        // Empty or 1-item closet returns null without crashing
        assertNull(
            OutfitRecommendationEngine.suggestMoodOutfit(
                items = emptyList(),
                mood = OutfitRecommendationEngine.OutfitMood.CHILL
            )
        )
        assertNull(
            OutfitRecommendationEngine.suggestMoodOutfit(
                items = listOf(closet.first()),
                mood = OutfitRecommendationEngine.OutfitMood.DATE
            )
        )
    }

    @Test
    fun winterThemePreferences_defaultsTrueAndPersistsToggle() {
        val store = InMemoryBooleanPrefStore()
        assertTrue(WinterThemePreferences.isEnabled(store))

        WinterThemePreferences.setEnabled(store, false)
        assertFalse(WinterThemePreferences.isEnabled(store))

        WinterThemePreferences.setEnabled(store, true)
        assertTrue(WinterThemePreferences.isEnabled(store))
    }
}
