package com.pandagallery.app.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extra grid columns to add on top of a screen's chosen density, based on how much width is
 * actually available: a phone in portrait needs none, a small tablet or landscape phone benefits
 * from a couple more, and a large or unfolded tablet from a couple more again.
 *
 * Replaces what used to be a single hardcoded 600dp cutoff copy-pasted at two call sites (the
 * Photos grids), and several other grids (People, Collections, Settings, Private Vault) that had
 * no width awareness at all and stayed a fixed column count on any screen size.
 */
fun adaptiveColumnBonus(maxWidth: Dp): Int = when {
    maxWidth >= EXPANDED_WIDTH -> 4
    maxWidth >= MEDIUM_WIDTH -> 2
    else -> 0
}

/** Total column count for a grid whose normal-density column count is [baseColumns]. */
fun adaptiveColumnCount(baseColumns: Int, maxWidth: Dp): Int = baseColumns + adaptiveColumnBonus(maxWidth)

private val MEDIUM_WIDTH = 600.dp
private val EXPANDED_WIDTH = 900.dp
