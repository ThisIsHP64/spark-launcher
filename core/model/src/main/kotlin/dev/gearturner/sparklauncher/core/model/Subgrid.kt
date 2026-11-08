package dev.gearturner.sparklauncher.core.model

/**
 * home placements are stored in half cells, so an icon is two units on each side. that lets items
 * sit exactly between grid cells when subgrid positioning is on, while every overlap and fit rule
 * keeps working unchanged on the finer grid. dock slots and folder ranks are not affected.
 */
const val SUBGRID = 2
