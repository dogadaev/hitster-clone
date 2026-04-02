package com.hitster.ui.screen

import com.badlogic.gdx.math.Rectangle
import com.hitster.core.model.PlayerId
import com.hitster.core.model.TurnPhase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchScreenTurnPhaseTest {
    @Test
    fun `pending placement phase includes the initial drawn slot and moved slot`() {
        assertTrue(isPendingPlacementPhase(TurnPhase.AWAITING_PLACEMENT))
        assertTrue(isPendingPlacementPhase(TurnPhase.CARD_POSITIONED))
        assertFalse(isPendingPlacementPhase(TurnPhase.WAITING_FOR_DRAW))
        assertFalse(isPendingPlacementPhase(TurnPhase.AWAITING_DOUBT_WINDOW))
    }

    @Test
    fun `doubt placement phase only includes active doubt positioning states`() {
        assertTrue(isDoubtPlacementPhase(TurnPhase.AWAITING_DOUBT_PLACEMENT))
        assertTrue(isDoubtPlacementPhase(TurnPhase.DOUBT_POSITIONED))
        assertFalse(isDoubtPlacementPhase(TurnPhase.CARD_POSITIONED))
        assertFalse(isDoubtPlacementPhase(TurnPhase.COMPLETE))
    }

    @Test
    fun `resolution reveal overlay stays active only during the hold window`() {
        assertTrue(isResolutionRevealOverlayActive(0f))
        assertTrue(isResolutionRevealOverlayActive(0.40f))
        assertFalse(isResolutionRevealOverlayActive(0.58f))
        assertFalse(isResolutionRevealOverlayActive(0.75f))
    }

    @Test
    fun `stale resolution is not re-presented after its overlay was already shown`() {
        assertTrue(
            shouldCreateResolutionPresentation(
                resolutionCardId = "card-1",
                currentPresentationCardId = null,
                lastPresentedResolutionCardId = null,
            ),
        )
        assertFalse(
            shouldCreateResolutionPresentation(
                resolutionCardId = "card-1",
                currentPresentationCardId = null,
                lastPresentedResolutionCardId = "card-1",
            ),
        )
        assertTrue(
            shouldCreateResolutionPresentation(
                resolutionCardId = "card-2",
                currentPresentationCardId = "card-1",
                lastPresentedResolutionCardId = "card-1",
            ),
        )
    }

    @Test
    fun `pending card animation resets when the timeline owner or card changes`() {
        val first = PlayerId("first")
        val second = PlayerId("second")

        assertFalse(
            shouldResetPendingCardAnimation(
                previousTimelinePlayerId = first,
                currentTimelinePlayerId = first,
                previousPendingCardId = "card-1",
                currentPendingCardId = "card-1",
            ),
        )
        assertTrue(
            shouldResetPendingCardAnimation(
                previousTimelinePlayerId = first,
                currentTimelinePlayerId = second,
                previousPendingCardId = "card-1",
                currentPendingCardId = "card-1",
            ),
        )
        assertTrue(
            shouldResetPendingCardAnimation(
                previousTimelinePlayerId = first,
                currentTimelinePlayerId = first,
                previousPendingCardId = "card-1",
                currentPendingCardId = "card-2",
            ),
        )
    }

    @Test
    fun `timeline focus toggle only shows when current and local timelines differ`() {
        val local = PlayerId("local")
        val other = PlayerId("other")

        assertTrue(shouldShowTimelineFocusToggle(other, local, forcedCurrent = false))
        assertFalse(shouldShowTimelineFocusToggle(local, local, forcedCurrent = false))
        assertFalse(shouldShowTimelineFocusToggle(other, local, forcedCurrent = true))
        assertFalse(shouldShowTimelineFocusToggle(null, local, forcedCurrent = false))
    }

    @Test
    fun `resolution freeze only applies to the shared overlay timeline`() {
        val local = PlayerId("local")
        val other = PlayerId("other")

        assertTrue(
            shouldFreezeResolutionBaseTimeline(
                displayedTimelinePlayerId = other,
                overlayPlayerId = other,
                localPlayerId = local,
                showingLocalTimeline = false,
                overlayActive = true,
            ),
        )
        assertFalse(
            shouldFreezeResolutionBaseTimeline(
                displayedTimelinePlayerId = local,
                overlayPlayerId = other,
                localPlayerId = local,
                showingLocalTimeline = true,
                overlayActive = true,
            ),
        )
        assertTrue(
            shouldFreezeResolutionBaseTimeline(
                displayedTimelinePlayerId = local,
                overlayPlayerId = local,
                localPlayerId = local,
                showingLocalTimeline = true,
                overlayActive = true,
            ),
        )
    }

    @Test
    fun `shared timeline stays on resolved player until the next draw begins`() {
        val resolved = PlayerId("resolved")
        val next = PlayerId("next")

        assertTrue(
            sharedTimelinePlayerIdForMatchSurface(
                activePlayerId = next,
                resolutionPlayerId = resolved,
                doubtTargetPlayerId = null,
                phase = TurnPhase.WAITING_FOR_DRAW,
            ) == resolved,
        )
        assertTrue(
            sharedTimelinePlayerIdForMatchSurface(
                activePlayerId = next,
                resolutionPlayerId = resolved,
                doubtTargetPlayerId = null,
                phase = TurnPhase.AWAITING_PLACEMENT,
            ) == next,
        )
        assertTrue(
            sharedTimelinePlayerIdForMatchSurface(
                activePlayerId = next,
                resolutionPlayerId = null,
                doubtTargetPlayerId = null,
                phase = TurnPhase.AWAITING_PLACEMENT,
                overlayPlayerId = resolved,
                overlayActive = true,
            ) == resolved,
        )
    }

    @Test
    fun `hidden card playback touch zone covers the top interaction lane without swallowing the full card`() {
        val cardRect = Rectangle(100f, 40f, 180f, 300f)
        val controlRect = Rectangle(146f, 244f, 88f, 88f)

        assertTrue(hiddenCardPlaybackTouchZoneContains(cardRect, controlRect, 190f, 288f))
        assertTrue(hiddenCardPlaybackTouchZoneContains(cardRect, controlRect, 122f, 252f))
        assertFalse(hiddenCardPlaybackTouchZoneContains(cardRect, controlRect, 190f, 180f))
        assertFalse(hiddenCardPlaybackTouchZoneContains(cardRect, controlRect, 120f, 120f))
    }
}
