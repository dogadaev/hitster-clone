package com.hitster.ui

/**
 * Maps release decades to card color treatments so revealed cards visually resemble the tabletop game.
 */

internal data class DecadeCardPalette(
    val topColor: Long,
    val bottomColor: Long,
    val edgeColor: Long,
)

internal object DecadeCardPalettes {
    fun forYear(releaseYear: Int): DecadeCardPalette {
        val decade = normalizeDecade(releaseYear)
        return when (decade) {
            1950 -> DecadeCardPalette(
                topColor = 0xBFE4D7FF,
                bottomColor = 0x8CC6B6FF,
                edgeColor = 0xE7F7F1FF,
            )

            1960 -> DecadeCardPalette(
                topColor = 0xC9E4A8FF,
                bottomColor = 0x99C873FF,
                edgeColor = 0xEDF7D5FF,
            )

            1970 -> DecadeCardPalette(
                topColor = 0xF0D67CFF,
                bottomColor = 0xD8B24FFF,
                edgeColor = 0xFAEAB3FF,
            )

            1980 -> DecadeCardPalette(
                topColor = 0xF3B56FFF,
                bottomColor = 0xD98B43FF,
                edgeColor = 0xFFE1B0FF,
            )

            1990 -> DecadeCardPalette(
                topColor = 0xE99297FF,
                bottomColor = 0xCC6672FF,
                edgeColor = 0xF8C3C8FF,
            )

            2000 -> DecadeCardPalette(
                topColor = 0xD59CEBFF,
                bottomColor = 0xB070D6FF,
                edgeColor = 0xEFD3FBFF,
            )

            2010 -> DecadeCardPalette(
                topColor = 0x8EB7F7FF,
                bottomColor = 0x5E88D8FF,
                edgeColor = 0xD2E4FFFF,
            )

            else -> DecadeCardPalette(
                topColor = 0x7FD8D4FF,
                bottomColor = 0x47ACA7FF,
                edgeColor = 0xC5F4F1FF,
            )
        }
    }

    private fun normalizeDecade(releaseYear: Int): Int {
        if (releaseYear <= 1959) {
            return 1950
        }
        if (releaseYear >= 2020) {
            return 2020
        }
        return (releaseYear / 10) * 10
    }
}
