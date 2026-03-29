package com.hitster.ui.theme

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
                topColor = 0xF8E0CCFF,
                bottomColor = 0xD5A784FF,
                edgeColor = 0xF8EAD7FF,
            )

            1960 -> DecadeCardPalette(
                topColor = 0xE8DB91FF,
                bottomColor = 0xBC9348FF,
                edgeColor = 0xF5E9B8FF,
            )

            1970 -> DecadeCardPalette(
                topColor = 0xF5C97DFF,
                bottomColor = 0xD58D3DFF,
                edgeColor = 0xFFE1A8FF,
            )

            1980 -> DecadeCardPalette(
                topColor = 0xF5A672FF,
                bottomColor = 0xCD6D42FF,
                edgeColor = 0xFFD0A7FF,
            )

            1990 -> DecadeCardPalette(
                topColor = 0xE68EA5FF,
                bottomColor = 0xB75172FF,
                edgeColor = 0xF4C0CBFF,
            )

            2000 -> DecadeCardPalette(
                topColor = 0xD8A8F0FF,
                bottomColor = 0x9E63CBFF,
                edgeColor = 0xEACAF9FF,
            )

            2010 -> DecadeCardPalette(
                topColor = 0xAC9BECFF,
                bottomColor = 0x735CC8FF,
                edgeColor = 0xDDD2FFFF,
            )

            else -> DecadeCardPalette(
                topColor = 0x7FD8CAFF,
                bottomColor = 0x38A094FF,
                edgeColor = 0xCCF4EDFF,
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
