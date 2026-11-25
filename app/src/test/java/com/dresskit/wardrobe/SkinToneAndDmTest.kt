package com.dresskit.wardrobe

import com.dresskit.wardrobe.data.ColorSeason
import com.dresskit.wardrobe.data.FacialContrast
import com.dresskit.wardrobe.data.PersonalColorAnalyzer
import com.dresskit.wardrobe.data.SkinUndertone
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.DmMessage
import com.dresskit.wardrobe.model.pairConvId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinToneAndDmTest {

    private fun solidRgb(r: Int, g: Int, b: Int, count: Int = 240): List<Int> {
        val px = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        return List(count) { px }
    }

    @Test
    fun skinToneAnalyzer_separatesCoolNeutralAndWarm_andPopulatesAll12Seasons() {
        // Cool rosy fair skin -> must never collapse to WARM
        val coolFair = PersonalColorAnalyzer.analyzeSkinSamples(
            skinRgb = solidRgb(235, 206, 212),
            darkFeatureL = 66f,
            lang = AppLanguage.TR
        )
        assertEquals(SkinUndertone.COOL, coolFair.undertone)
        assertTrue(coolFair.season == ColorSeason.LIGHT_SUMMER || coolFair.season == ColorSeason.TRUE_SUMMER)
        assertTrue(coolFair.flatteringColors.isNotEmpty())
        assertTrue(coolFair.avoidColors.isNotEmpty())

        // High-contrast cool deeper skin -> Winter season (never WARM)
        val coolWinter = PersonalColorAnalyzer.analyzeSkinSamples(
            skinRgb = solidRgb(166, 134, 142),
            darkFeatureL = 16f,
            lang = AppLanguage.TR
        )
        assertEquals(SkinUndertone.COOL, coolWinter.undertone)
        assertTrue(
            coolWinter.season == ColorSeason.DEEP_WINTER ||
                coolWinter.season == ColorSeason.TRUE_WINTER ||
                coolWinter.season == ColorSeason.BRIGHT_WINTER
        )

        // Neutral medium skin (balanced a* and b*) -> not collapsed into WARM
        val neutralSkin = PersonalColorAnalyzer.analyzeSkinSamples(
            skinRgb = solidRgb(210, 184, 174),
            darkFeatureL = 64f,
            lang = AppLanguage.EN
        )
        assertNotEquals(SkinUndertone.WARM, neutralSkin.undertone)

        // Medium olive skin (low a*, elevated b*) -> NEUTRAL + SOFT_AUTUMN
        val oliveSkin = PersonalColorAnalyzer.analyzeSkinSamples(
            skinRgb = solidRgb(170, 158, 124),
            darkFeatureL = 42f,
            lang = AppLanguage.TR
        )
        assertEquals(SkinUndertone.NEUTRAL, oliveSkin.undertone)
        assertEquals(ColorSeason.SOFT_AUTUMN, oliveSkin.season)

        // Warm golden fair skin -> Spring family
        val warmFair = PersonalColorAnalyzer.analyzeSkinSamples(
            skinRgb = solidRgb(236, 198, 154),
            darkFeatureL = 64f,
            lang = AppLanguage.TR
        )
        assertEquals(SkinUndertone.WARM, warmFair.undertone)
        assertTrue(
            warmFair.season == ColorSeason.LIGHT_SPRING ||
                warmFair.season == ColorSeason.TRUE_SPRING ||
                warmFair.season == ColorSeason.BRIGHT_SPRING
        )

        // Warm deep bronze skin -> DEEP_AUTUMN
        val warmDeep = PersonalColorAnalyzer.analyzeSkinSamples(
            skinRgb = solidRgb(145, 102, 70),
            darkFeatureL = 14f,
            lang = AppLanguage.TR
        )
        assertEquals(SkinUndertone.WARM, warmDeep.undertone)
        assertEquals(ColorSeason.DEEP_AUTUMN, warmDeep.season)

        // All 12 seasons return complete palettes and localized summaries
        assertEquals(12, ColorSeason.entries.size)
        ColorSeason.entries.forEach { season ->
            val prof = PersonalColorAnalyzer.profileForSeason(
                season = season,
                averageSkinHex = 0xFFCDB99CL,
                faceDetected = true,
                contrast = FacialContrast.MEDIUM,
                lang = AppLanguage.TR
            )
            assertEquals(season, prof.season)
            assertTrue("${season.name} flattering empty", prof.flatteringColors.isNotEmpty())
            assertTrue("${season.name} avoid empty", prof.avoidColors.isNotEmpty())
            assertTrue("${season.name} summary blank", prof.summaryText.isNotBlank())
        }
    }

    @Test
    fun dmHelpers_deterministicPairConvId_and15MinEditWindow() {
        assertEquals("abc_xyz", pairConvId("xyz", "abc"))
        assertEquals("abc_xyz", pairConvId("abc", "xyz"))
        assertEquals(pairConvId("uid_B", "uid_A"), pairConvId("uid_A", "uid_B"))

        val created = 1_764_000_000_000L
        val textMsg = DmMessage(
            msgId = "m_test_1234",
            convId = "a_b",
            senderUid = "a",
            msgType = "text",
            text = "Merhaba",
            createdAtMs = created
        )
        assertTrue(textMsg.canEdit(created + 14L * 60_000L + 59_000L))
        assertFalse(textMsg.canEdit(created + 15L * 60_000L + 1_000L))

        val deletedMsg = textMsg.copy(isDeleted = true)
        assertFalse(deletedMsg.canEdit(created + 5_000L))

        val photoMsg = textMsg.copy(msgType = "photo", mediaBase64 = "data:image/jpeg;base64,abc")
        assertFalse(photoMsg.canEdit(created + 5_000L))

        val replyMsg = textMsg.copy(msgType = "post_reply", sharedPostId = "post_12345")
        assertTrue(replyMsg.canEdit(created + 60_000L))
    }
}
