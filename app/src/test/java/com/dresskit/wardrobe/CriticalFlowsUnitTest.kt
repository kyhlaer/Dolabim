package com.dresskit.wardrobe

import com.dresskit.wardrobe.data.BackupManager
import com.dresskit.wardrobe.data.PersonalColorAnalyzer
import com.dresskit.wardrobe.data.SocialNotificationService
import com.dresskit.wardrobe.data.WardrobeRepository
import com.dresskit.wardrobe.data.requireSocialRead
import com.dresskit.wardrobe.data.socialQueryRows
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.SocialNotificationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CriticalFlowsUnitTest {

    private fun filledArgbPixels(w: Int, h: Int, r: Int, g: Int, b: Int, a: Int = 255): IntArray {
        val px = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        return IntArray(w * h) { px }
    }

    @Test
    fun skinExtraction_handlesWarmCoolDeepShadowAndExtremeInputs() {
        val scenarios = listOf(
            "warm_cast" to Triple(245, 195, 120),
            "cool_cast" to Triple(140, 145, 180),
            "deep_skin" to Triple(72, 46, 34),
            "shadowed_dark" to Triple(24, 18, 14),
            "overexposed_bright" to Triple(250, 245, 240)
        )

        scenarios.forEach { (label, rgb) ->
            val pixels = filledArgbPixels(40, 40, rgb.first, rgb.second, rgb.third)
            val samples = PersonalColorAnalyzer.extractSkinSamplesFromPixels(
                width = 40,
                height = 40,
                pixels = pixels
            )
            assertTrue("$label should extract non-empty skin samples", samples.isNotEmpty())
            val profile = PersonalColorAnalyzer.analyzeSkinSamples(
                skinRgb = samples,
                ambientRgb = rgb,
                lang = AppLanguage.TR
            )
            assertTrue("$label flattering colors empty", profile.flatteringColors.isNotEmpty())
            assertTrue("$label avoid colors empty", profile.avoidColors.isNotEmpty())
            assertTrue("$label summary blank", profile.summaryText.isNotBlank())
        }
    }

    @Test
    fun backupHelpers_parseAndroidAndWebHexes_andRejectPathTraversal() {
        // Hex parsing handles Android numeric Long and Web "#RRGGBB" / "#AARRGGBB" strings
        assertEquals(0xFF1A2B3CL, WardrobeRepository.parseExtractedHex(0xFF1A2B3CL))
        assertEquals(0xFF1A2B3CL, WardrobeRepository.parseExtractedHex("#1A2B3C"))
        assertEquals(0xFF1A2B3CL, WardrobeRepository.parseExtractedHex("1a2b3c"))
        assertEquals(0x801A2B3CL, WardrobeRepository.parseExtractedHex("#801A2B3C"))
        assertNull(WardrobeRepository.parseExtractedHex("not_a_hex"))
        assertNull(WardrobeRepository.parseExtractedHex(null))

        // Restore photo file resolution allows safe keys and blocks traversal
        val tempRoot = Files.createTempDirectory("dresskit_backup_test").toFile()
        try {
            val photosDir = File(tempRoot, "photos").apply { mkdirs() }
            val safeJpg = BackupManager.resolveSafeRestorePhotoFile(photosDir, "orig_item_1.jpg")
            assertNotNull(safeJpg)
            assertEquals("orig_item_1.jpg", safeJpg?.name)

            val safeNoExt = BackupManager.resolveSafeRestorePhotoFile(photosDir, "bgrm_ph_42")
            assertNotNull(safeNoExt)
            assertEquals("bgrm_ph_42", safeNoExt?.name)

            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, "../wardrobe_data.json"))
            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, "..\\wardrobe_data.json"))
            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, "sub/file.jpg"))
            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, "sub\\file.jpg"))
            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, "/etc/passwd"))
            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, "C:\\Windows\\System32\\cmd.exe"))
            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, ".hidden"))
            assertNull(BackupManager.resolveSafeRestorePhotoFile(photosDir, "   "))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun notificationCursor_filtersReadOlderEqualAndAlreadyShown() {
        val baseNotif = SocialNotificationData(
            notifId = "n_200",
            targetUid = "user1",
            type = "post_like",
            fromUid = "user2",
            fromUsername = "ali",
            fromDisplayName = "Ali",
            fromAvatar = "",
            postId = "post1",
            postOwnerUid = "user1",
            previewText = "Beğendi",
            read = false,
            createdAtMs = 1_764_000_000_000L
        )
        val rows = listOf(
            baseNotif.copy(notifId = "n_read", read = true, createdAtMs = 1_764_000_010_000L),
            baseNotif.copy(notifId = "n_old", createdAtMs = 1_763_999_999_000L),
            baseNotif.copy(notifId = "n_100", createdAtMs = 1_764_000_000_000L), // <= lastId "n_150"
            baseNotif.copy(notifId = "n_200", createdAtMs = 1_764_000_000_000L), // > lastId "n_150"
            baseNotif.copy(notifId = "n_shown", createdAtMs = 1_764_000_020_000L),
            baseNotif.copy(notifId = "n_new", createdAtMs = 1_764_000_030_000L)
        )

        val fresh = SocialNotificationService.filterFreshNotifications(
            rows = rows,
            baselineMs = 1_764_000_000_000L,
            lastId = "n_150",
            shownIds = setOf("n_shown")
        )
        val ids = fresh.map { it.notifId }
        assertEquals(listOf("n_200", "n_new"), ids)
        assertFalse(ids.contains("n_read"))
        assertFalse(ids.contains("n_old"))
        assertFalse(ids.contains("n_100"))
        assertFalse(ids.contains("n_shown"))
    }

    @Test
    fun socialReadGuards_handleQuota429ServerErrorsAndStrictFallback() {
        requireSocialRead(200)
        requireSocialRead(204)

        try {
            requireSocialRead(429)
            fail("Expected 429 to throw IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("429"))
        }

        try {
            requireSocialRead(0)
            fail("Expected 0 network failure to throw IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("0"))
        }

        try {
            requireSocialRead(503)
            fail("Expected 503 to throw IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("503"))
        }

        // strict = true propagates HTTP error; strict = false degrades cleanly without throwing
        assertNotNull(socialQueryRows(429, "{}", strict = false))
        assertNotNull(socialQueryRows(503, "not-json", strict = false))
        try {
            socialQueryRows(429, "{}", strict = true)
            fail("Expected strict 429 query to throw")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("429"))
        }
        try {
            socialQueryRows(503, "not-json", strict = true)
            fail("Expected strict 503 query to throw")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("503"))
        }
    }
}
