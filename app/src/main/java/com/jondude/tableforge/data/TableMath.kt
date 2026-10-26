package com.jondude.tableforge.data

import kotlin.random.Random

object TableMath {
    /**
     * Splits 1..sides into [count] contiguous ranges as evenly as possible,
     * e.g. 4 entries on a d10 -> 1-3, 4-6, 7-8, 9-10.
     */
    fun ranges(sides: Int, count: Int): List<IntRange> {
        if (count <= 0) return emptyList()
        val n = count.coerceAtMost(sides)
        val base = sides / n
        var extra = sides % n
        var start = 1
        return List(n) {
            val size = base + if (extra-- > 0) 1 else 0
            val r = start until start + size
            start += size
            r
        }
    }

    fun entriesFor(tableId: Long, sides: Int, texts: List<String>): List<TableEntry> =
        ranges(sides, texts.size).mapIndexed { i, r -> TableEntry(tableId = tableId, minRoll = r.first, maxRoll = r.last, text = texts[i]) }

    fun roll(sides: Int, rng: Random = Random.Default): Int = rng.nextInt(1, sides + 1)

    fun lookup(entries: List<TableEntry>, roll: Int): TableEntry? =
        entries.firstOrNull { roll in it.minRoll..it.maxRoll }

    fun rangeLabel(e: TableEntry, sides: Int): String {
        fun fmt(v: Int) = if (sides == 100 && v == 100) "00" else v.toString()
        return if (e.minRoll == e.maxRoll) fmt(e.minRoll) else "${fmt(e.minRoll)}–${fmt(e.maxRoll)}"
    }

    /** Sensible default entry count for a die: one per face, except d100 which uses 20 bands of 5. */
    fun defaultCount(sides: Int) = if (sides == 100) 20 else sides
}
