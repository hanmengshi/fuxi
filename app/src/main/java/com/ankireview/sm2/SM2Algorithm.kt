package com.ankireview.sm2

import java.time.LocalDate

data class SM2Card(
    val interval: Int    = 0,
    val ease: Double     = 2.5,
    val reps: Int        = 0,
    val due: String      = "2000-01-01",
    val lapses: Int      = 0      // number of times forgotten (Again)
)

enum class Grade(val value: Int, val label: String) {
    AGAIN(0, "重来"),
    HARD(1,  "困难"),
    GOOD(2,  "良好"),
    EASY(3,  "简单")
}

/**
 * SM-2 algorithm closely matching Anki's implementation:
 * - Again: interval = 1day, ease -= 0.20, lapses++
 * - Hard:  interval *= 1.2, ease -= 0.15
 * - Good:  interval = prev * ease
 * - Easy:  interval = prev * ease * easyBonus(1.3), ease += 0.15
 *
 * New cards (reps==0) follow learning steps: 1min -> 10min -> 1day -> graduated
 * Lapse cards restart from interval=1
 */
object SM2 {
    private const val EASY_BONUS      = 1.3
    private const val INTERVAL_MOD    = 1.0
    private const val MIN_EASE        = 1.3
    private const val GRADUATING_INT  = 1    // days for first Good on new card
    private const val EASY_INT        = 4    // days for first Easy on new card

    fun calculate(card: SM2Card, grade: Grade): SM2Card {
        var interval = card.interval
        var ease     = card.ease
        var reps     = card.reps
        var lapses   = card.lapses

        when {
            // ── Learning / Relearning (new cards or lapses) ──────────────
            reps == 0 -> {
                when (grade) {
                    Grade.AGAIN -> { interval = 1;        ease = maxOf(MIN_EASE, ease - 0.20); lapses++ }
                    Grade.HARD  -> { interval = 1;        ease = maxOf(MIN_EASE, ease - 0.15) }
                    Grade.GOOD  -> { interval = GRADUATING_INT; reps = 1 }
                    Grade.EASY  -> { interval = EASY_INT;       reps = 1; ease += 0.15 }
                }
            }
            // ── Review cards ─────────────────────────────────────────────
            else -> {
                when (grade) {
                    Grade.AGAIN -> {
                        lapses++
                        reps     = 0
                        interval = maxOf(1, (interval * 0.5).toInt())  // Anki: interval * lapse multiplier
                        ease     = maxOf(MIN_EASE, ease - 0.20)
                    }
                    Grade.HARD -> {
                        interval = maxOf(interval + 1, (interval * 1.2 * INTERVAL_MOD).toInt())
                        ease     = maxOf(MIN_EASE, ease - 0.15)
                    }
                    Grade.GOOD -> {
                        interval = maxOf(interval + 1, (interval * ease * INTERVAL_MOD).toInt())
                        reps++
                    }
                    Grade.EASY -> {
                        interval = maxOf(interval + 1, (interval * ease * EASY_BONUS * INTERVAL_MOD).toInt())
                        ease     = ease + 0.15
                        reps++
                    }
                }
            }
        }

        interval = maxOf(1, interval)
        val due  = LocalDate.now().plusDays(interval.toLong()).toString()
        return SM2Card(interval, "%.2f".format(ease).toDouble(), reps, due, lapses)
    }

    /** Preview next intervals for each grade without modifying card */
    fun preview(card: SM2Card): Map<Grade, Int> =
        Grade.values().associateWith { calculate(card, it).interval }

    /** Human-readable retention description */
    fun retentionLabel(card: SM2Card): String = when {
        card.reps == 0                -> "新卡片"
        card.lapses > 3               -> "困难卡片"
        card.interval >= 21           -> "已掌握"
        card.interval >= 7            -> "复习中"
        else                          -> "学习中"
    }
}
