package com.hitster.ui.layout

/**
 * Shared timeline spacing and compression math used by the main gameplay timeline for both normal placement and doubt placement.
 */

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class TimelineArrangement(
    val cardLefts: List<Float>,
    val cardWidth: Float,
    val gap: Float,
    val groupStartX: Float,
    val groupWidth: Float,
)

data class PendingTimelineArrangement(
    val committedCardLefts: List<Float>,
    val pendingCardLeft: Float,
    val cardWidth: Float,
    val gap: Float,
    val groupStartX: Float,
    val groupWidth: Float,
)

class TimelineLayoutCalculator(
    private val trackX: Float,
    private val trackWidth: Float,
    private val preferredCardWidth: Float = 150f,
    private val minCardWidth: Float = 92f,
    private val preferredGap: Float = 28f,
    private val minGap: Float = 12f,
) {
    /** Computes the centered layout for a fully revealed timeline with no active hidden card. */
    fun arrangement(cardCount: Int): TimelineArrangement {
        if (cardCount <= 0) {
            return TimelineArrangement(
                cardLefts = emptyList(),
                cardWidth = preferredCardWidth,
                gap = preferredGap,
                groupStartX = trackX + (trackWidth - preferredCardWidth) / 2f,
                groupWidth = 0f,
            )
        }

        val metrics = metricsFor(cardCount)
        val startX = trackX + (trackWidth - metrics.totalWidth) / 2f
        val lefts = List(cardCount) { index -> startX + index * metrics.step }
        return TimelineArrangement(lefts, metrics.cardWidth, metrics.step - metrics.cardWidth, startX, metrics.totalWidth)
    }

    /** Returns the horizontal centers of every legal insertion slot for the current timeline size. */
    fun insertionSlotCenters(existingCardCount: Int): List<Float> {
        return List(existingCardCount + 1) { slotIndex ->
            val arrangement = pendingArrangement(existingCardCount, slotIndex)
            arrangement.pendingCardLeft + arrangement.cardWidth / 2f
        }
    }

    /**
     * Computes layout while a hidden card is active.
     *
     * Committed cards may compress slightly, but the pending card always keeps full clearance from its neighbors.
     */
    fun pendingArrangement(existingCardCount: Int, pendingSlotIndex: Int): PendingTimelineArrangement {
        val totalCount = existingCardCount + 1
        val metrics = metricsFor(totalCount)
        val clampedSlot = pendingSlotIndex.coerceIn(0, existingCardCount)
        val leftCount = clampedSlot
        val rightCount = existingCardCount - clampedSlot
        val clearStep = max(metrics.step, metrics.cardWidth)
        val compressedStepCount = max(0, leftCount - 1) + max(0, rightCount - 1)
        val clearStepCount = (if (leftCount > 0) 1 else 0) + (if (rightCount > 0) 1 else 0)
        val compressedStep = when {
            compressedStepCount == 0 -> 0f
            else -> {
                val available = trackWidth - metrics.cardWidth - clearStep * clearStepCount
                min(metrics.step, available / compressedStepCount)
            }
        }
        val totalWidth = metrics.cardWidth + compressedStep * compressedStepCount + clearStep * clearStepCount
        val startX = trackX + (trackWidth - totalWidth) / 2f
        val committedLefts = MutableList(existingCardCount) { 0f }
        var cursor = startX

        for (index in 0 until leftCount) {
            committedLefts[index] = cursor
            cursor += if (index == leftCount - 1) clearStep else compressedStep
        }

        val pendingLeft = cursor
        if (rightCount > 0) {
            cursor = pendingLeft + clearStep
        }

        for (index in 0 until rightCount) {
            committedLefts[leftCount + index] = cursor
            if (index < rightCount - 1) {
                cursor += compressedStep
            }
        }

        return PendingTimelineArrangement(
            committedCardLefts = committedLefts,
            pendingCardLeft = pendingLeft,
            cardWidth = metrics.cardWidth,
            gap = compressedStep - metrics.cardWidth,
            groupStartX = startX,
            groupWidth = totalWidth,
        )
    }

    /** Finds the insertion slot whose visual center is nearest to the supplied drag position. */
    fun nearestSlotIndex(existingCardCount: Int, x: Float): Int {
        val centers = insertionSlotCenters(existingCardCount)
        if (centers.isEmpty()) {
            return 0
        }

        var closestIndex = 0
        var smallestDistance = abs(centers.first() - x)
        for (index in 1 until centers.size) {
            val distance = abs(centers[index] - x)
            if (distance < smallestDistance) {
                smallestDistance = distance
                closestIndex = index
            }
        }
        return closestIndex
    }

    private fun metricsFor(cardCount: Int): LayoutMetrics {
        if (cardCount == 1) {
            return LayoutMetrics(
                cardWidth = preferredCardWidth,
                step = preferredCardWidth + preferredGap,
                totalWidth = preferredCardWidth,
            )
        }

        val idealStep = preferredCardWidth + preferredGap
        val idealWidth = totalWidth(cardCount, preferredCardWidth, idealStep)
        if (idealWidth <= trackWidth) {
            return LayoutMetrics(preferredCardWidth, idealStep, idealWidth)
        }

        val minGapStep = preferredCardWidth + minGap
        val widthWithMinGap = totalWidth(cardCount, preferredCardWidth, minGapStep)
        if (widthWithMinGap <= trackWidth) {
            val step = (trackWidth - preferredCardWidth) / (cardCount - 1)
            return LayoutMetrics(preferredCardWidth, step, totalWidth(cardCount, preferredCardWidth, step))
        }

        val fittedCardWidth = (trackWidth - (cardCount - 1) * minGap) / cardCount
        if (fittedCardWidth >= minCardWidth) {
            val step = fittedCardWidth + minGap
            return LayoutMetrics(fittedCardWidth, step, totalWidth(cardCount, fittedCardWidth, step))
        }

        val overlapStep = (trackWidth - minCardWidth) / (cardCount - 1)
        return LayoutMetrics(minCardWidth, overlapStep, totalWidth(cardCount, minCardWidth, overlapStep))
    }

    private fun totalWidth(cardCount: Int, cardWidth: Float, step: Float): Float {
        return if (cardCount <= 0) 0f else cardWidth + (cardCount - 1) * step
    }

    private data class LayoutMetrics(
        val cardWidth: Float,
        val step: Float,
        val totalWidth: Float,
    )
}
