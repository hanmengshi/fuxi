package com.ankireview.sm2

import java.time.LocalDate

data class SM2Card(val interval: Int = 0, val ease: Double = 2.5, val reps: Int = 0, val due: String = "2000-01-01")

enum class Grade(val value: Int, val label: String) {
    AGAIN(0, "重来"), HARD(1, "困难"), GOOD(2, "良好"), EASY(3, "简单")
}

object SM2 {
    fun calculate(card: SM2Card, grade: Grade): SM2Card {
        var (interval, ease, reps, _) = card
        when (grade) {
            Grade.AGAIN -> { reps = 0; interval = 1;          ease = maxOf(1.3, ease - 0.20) }
            Grade.HARD  -> { reps = 0; interval = if (interval < 1) 2 else maxOf(1, (interval * 1.2).toInt())
                             ease = maxOf(1.3, ease - 0.15) }
            Grade.GOOD  -> { reps++; interval = when(reps){1->4;2->7;else->(interval*ease).toInt()} }
            Grade.EASY  -> { reps++; interval = if(reps==1) 7 else (interval*ease*1.3).toInt(); ease += 0.15 }
        }
        interval = maxOf(1, interval)
        return SM2Card(interval, "%.2f".format(ease).toDouble(), reps,
            LocalDate.now().plusDays(interval.toLong()).toString())
    }
    fun preview(card: SM2Card) = Grade.values().associateWith { calculate(card, it).interval }
}
