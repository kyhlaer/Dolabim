package com.dresskit.wardrobe

import com.dresskit.wardrobe.model.DeepLinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityStateTest {

    @Test
    fun socialOverlayState_routesAllDeepLinkTargets() {
        val state = SocialOverlayState()

        state.handleIncomingTarget(DeepLinkTarget.SharedItem("item-123"))
        assertEquals("item-123", state.activeSharedItemId)

        state.handleIncomingTarget(DeepLinkTarget.SharedOutfit("outfit-456"))
        assertEquals("outfit-456", state.activeSharedOutfitId)

        state.handleIncomingTarget(DeepLinkTarget.UserProfile("kyhlaer", isUid = false))
        assertEquals("kyhlaer" to true, state.activeOtherProfile)

        state.handleIncomingTarget(DeepLinkTarget.UserProfile("uid-789", isUid = true))
        assertEquals("uid-789" to false, state.activeOtherProfile)

        state.handleIncomingTarget(DeepLinkTarget.SocialPost("post-1", "owner-2"))
        assertEquals("post-1" to "owner-2", state.activeSocialPostTarget)
    }

    @Test
    fun socialOverlayState_startsCleanWithZeroUnreadCounts() {
        val state = SocialOverlayState()
        assertEquals(0, state.pendingRequestsCount)
        assertEquals(0, state.unreadNotificationsCount)
        assertEquals(0, state.unreadDmCount)
        assertNull(state.activeSharedItemId)
        assertNull(state.activeSharedOutfitId)
        assertNull(state.activeOtherProfile)
        assertNull(state.activeSocialPostTarget)
    }
}
