package com.example.worktime.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

object Fmt {

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun localDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun millisOf(d: LocalDate, hour: Int, minute: Int): Long =
        d.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DAY_LABEL = DateTimeFormatter.ofPattern("dd.MM", Locale.US)
    private val FULL = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    fun isoDate(millis: Long): String = localDate(millis).format(ISO)
    fun clockTime(millis: Long): String = localDateTime(millis).format(HM)
    fun dayLabel(d: LocalDate): String = d.format(DAY_LABEL)
    fun fullDate(d: LocalDate): String = d.format(FULL)

    /** mm:ss for a running timer, h:mm:ss past an hour. */
    fun countdown(ms: Long): String {
        val total = (ms.coerceAtLeast(0L)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Exact duration as 2:07 hours:minutes. */
    fun hhmm(ms: Long): String {
        val m = (ms / 60000L)
        return String.format(Locale.US, "%d:%02d", m / 60, m % 60)
    }

    /** Minutes rounded to the nearest 15. */
    fun roundedMinutes(ms: Long): Long {
        val minutes = ms / 60000.0
        return (minutes / 15.0).roundToLong() * 15L
    }

    /** Rounded to the nearest quarter hour, rendered as 3h 15m. */
    fun rounded(ms: Long): String {
        val m = roundedMinutes(ms)
        val h = m / 60
        val rem = m % 60
        return when {
            h == 0L && rem == 0L -> "0"
            h == 0L -> "${rem}m"
            rem == 0L -> "${h}h"
            else -> "${h}h ${rem}m"
        }
    }

    /** Whole hours only, rounded to nearest: 1349h. For the grand Total. */
    fun hoursOnly(ms: Long): String {
        val h = Math.round(ms / 3_600_000.0)
        return "${h}h"
    }

    /** To-the-minute total: 2h 7m, 45m, or 0m. Never seconds, for stats and totals. */
    fun minutes(ms: Long): String {
        val m = ms / 60000L
        val h = m / 60
        val rem = m % 60
        return when {
            h > 0 && rem > 0 -> "${h}h ${rem}m"
            h > 0 -> "${h}h"
            else -> "${rem}m"
        }
    }

    /** Exact duration as 2h 07m, used in the timeline. */
    fun exact(ms: Long): String {
        val m = ms / 60000L
        val s = (ms / 1000L) % 60
        val h = m / 60
        val rem = m % 60
        return when {
            h > 0 -> "${h}h ${rem}m"
            rem > 0 -> "${rem}m"
            else -> "${s}s"
        }
    }
}
