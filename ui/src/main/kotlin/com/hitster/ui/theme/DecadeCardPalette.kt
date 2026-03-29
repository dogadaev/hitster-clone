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
                topColor = 0xF7DDC6FF,
                bottomColor = 0xD39B79FF,
                edgeColor = 0xFCEEDFFF,
            )

            1960 -> DecadeCardPalette(
                topColor = 0xE8D18CFF,
                bottomColor = 0xB18447FF,
                edgeColor = 0xFBE9C6FF,
            )

            1970 -> DecadeCardPalette(
                topColor = 0xF1B96FFF,
                bottomColor = 0xCE7E37FF,
                edgeColor = 0xFDE0A7FF,
            )

            1980 -> DecadeCardPalette(
                topColor = 0xEC8E62FF,
                bottomColor = 0xC25833FF,
                edgeColor = 0xF8C4A4FF,
            )

            1990 -> DecadeCardPalette(
                topColor = 0xD87D90FF,
                bottomColor = 0xA54161FF,
                edgeColor = 0xF0B8C5FF,
            )

            2000 -> DecadeCardPalette(
                topColor = 0xC58FE0FF,
                bottomColor = 0x864CADFF,
                edgeColor = 0xE7CDF7FF,
            )

            2010 -> DecadeCardPalette(
                topColor = 0x8D74D5FF,
                bottomColor = 0x5A3E98FF,
                edgeColor = 0xDCCCFBFF,
            )

            else -> DecadeCardPalette(
                topColor = 0x63BFA8FF,
                bottomColor = 0x2E8275FF,
                edgeColor = 0xC8EEE5FF,
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
