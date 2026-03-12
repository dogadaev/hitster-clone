package com.hitster.ui

import kotlin.math.abs
import kotlin.math.max

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
        val step = metrics.cardWidth + metrics.gap
        val lefts = List(cardCount) { index -> startX + index * step }
        return TimelineArrangement(lefts, metrics.cardWidth, metrics.gap, startX, metrics.totalWidth)
    }

    fun insertionSlotCenters(existingCardCount: Int): List<Float> {
        val arrangement = arrangement(existingCardCount + 1)
        return arrangement.cardLefts.map { left -> left + arrangement.cardWidth / 2f }
    }

    fun pendingArrangement(existingCardCount: Int, pendingSlotIndex: Int): PendingTimelineArrangement {
        val totalCount = existingCardCount + 1
        val arrangement = arrangement(totalCount)
        val clampedSlot = pendingSlotIndex.coerceIn(0, existingCardCount)
        val committedLefts = List(existingCardCount) { index ->
            val arrangedIndex = if (index < clampedSlot) index else index + 1
            arrangement.cardLefts[arrangedIndex]
        }

        return PendingTimelineArrangement(
            committedCardLefts = committedLefts,
            pendingCardLeft = arrangement.cardLefts[clampedSlot],
            cardWidth = arrangement.cardWidth,
            gap = arrangement.gap,
            groupStartX = arrangement.groupStartX,
            groupWidth = arrangement.groupWidth,
        )
    }

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
            return LayoutMetrics(preferredCardWidth, preferredGap, preferredCardWidth)
        }

        val idealWidth = totalWidth(cardCount, preferredCardWidth, preferredGap)
        if (idealWidth <= trackWidth) {
            return LayoutMetrics(preferredCardWidth, preferredGap, idealWidth)
        }

        val widthWithMinGap = totalWidth(cardCount, preferredCardWidth, minGap)
        if (widthWithMinGap <= trackWidth) {
            val gap = (trackWidth - cardCount * preferredCardWidth) / (cardCount - 1)
            return LayoutMetrics(preferredCardWidth, gap, totalWidth(cardCount, preferredCardWidth, gap))
        }

        val widthWithMinCard = totalWidth(cardCount, minCardWidth, minGap)
        if (widthWithMinCard >= trackWidth) {
            val gap = max(4f, (trackWidth - cardCount * minCardWidth) / (cardCount - 1))
            return LayoutMetrics(minCardWidth, gap, totalWidth(cardCount, minCardWidth, gap))
        }

        val cardWidth = (trackWidth - (cardCount - 1) * minGap) / cardCount
        return LayoutMetrics(cardWidth, minGap, totalWidth(cardCount, cardWidth, minGap))
    }

    private fun totalWidth(cardCount: Int, cardWidth: Float, gap: Float): Float {
        return cardCount * cardWidth + (cardCount - 1) * gap
    }

    private data class LayoutMetrics(
        val cardWidth: Float,
        val gap: Float,
        val totalWidth: Float,
    )
}
