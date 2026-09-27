package com.example.worktime.data

import java.time.LocalDate
import java.time.temporal.WeekFields

enum class Period(val label: String, val buckets: Int) {
    DAY("Day", 14),
    WEEK("Week", 12),
    MONTH("Month", 12),
    QUARTER("Quarter", 8),
    YEAR("Year", 6)
}

enum class DetailDim(val label: String) { AREA("Areas"), PROJECT("Projects"), TASK("Tasks") }

data class DetailRow(
    val name: String,
    val colorArgb: Int,
    val ms: Long,
    val children: List<DetailRow> = emptyList()
)

data class Slice(val projectId: String, val areaId: String, val colorArgb: Int, val ms: Long)

data class Bucket(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val label: String,
    val slices: List<Slice>,
    val sessions: List<Session>
) {
    val total: Long get() = slices.sumOf { it.ms }
}

object Stats {

    private fun startOfWeek(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
    private fun startOfMonth(d: LocalDate): LocalDate = d.withDayOfMonth(1)
    private fun startOfQuarter(d: LocalDate): LocalDate =
        LocalDate.of(d.year, ((d.monthValue - 1) / 3) * 3 + 1, 1)
    private fun startOfYear(d: LocalDate): LocalDate = LocalDate.of(d.year, 1, 1)

    /** How many buckets to span, from the earliest session up to now. */
    private fun bucketCount(period: Period, today: LocalDate): Int {
        val earliest = Store.sessions.minOfOrNull { it.startMillis }
            ?: return period.buckets
        val first = Fmt.localDate(earliest)
        val span = when (period) {
            Period.DAY -> java.time.temporal.ChronoUnit.DAYS.between(first, today).toInt() + 1
            Period.WEEK -> java.time.temporal.ChronoUnit.WEEKS.between(startOfWeek(first), startOfWeek(today)).toInt() + 1
            Period.MONTH -> java.time.temporal.ChronoUnit.MONTHS.between(startOfMonth(first), startOfMonth(today)).toInt() + 1
            Period.QUARTER -> (java.time.temporal.ChronoUnit.MONTHS.between(startOfQuarter(first), startOfQuarter(today)).toInt() / 3) + 1
            Period.YEAR -> (today.year - first.year) + 1
        }
        // At least the default window, and a hard ceiling so the canvas stays sane.
        return span.coerceIn(period.buckets, 400)
    }

    fun buckets(period: Period, today: LocalDate = LocalDate.now()): List<Bucket> {
        val n = bucketCount(period, today)
        val starts = ArrayList<Pair<LocalDate, LocalDate>>()
        when (period) {
            Period.DAY -> for (i in n - 1 downTo 0) {
                val s = today.minusDays(i.toLong()); starts.add(s to s.plusDays(1))
            }
            Period.WEEK -> {
                val cur = startOfWeek(today)
                for (i in n - 1 downTo 0) {
                    val s = cur.minusWeeks(i.toLong()); starts.add(s to s.plusWeeks(1))
                }
            }
            Period.MONTH -> {
                val cur = startOfMonth(today)
                for (i in n - 1 downTo 0) {
                    val s = cur.minusMonths(i.toLong()); starts.add(s to s.plusMonths(1))
                }
            }
            Period.QUARTER -> {
                val cur = startOfQuarter(today)
                for (i in n - 1 downTo 0) {
                    val s = cur.minusMonths(3L * i); starts.add(s to s.plusMonths(3))
                }
            }
            Period.YEAR -> {
                val cur = startOfYear(today)
                for (i in n - 1 downTo 0) {
                    val s = cur.minusYears(i.toLong()); starts.add(s to s.plusYears(1))
                }
            }
        }

        return starts.map { (s, e) ->
            val from = Fmt.millisOf(s, 0, 0)
            val to = Fmt.millisOf(e, 0, 0)
            val inRange = Store.sessions.filter { it.startMillis in from until to }
            Bucket(s, e, label(period, s), stack(inRange), inRange)
        }
    }

    /** ISO week number, 1..52 or 53 in long years. Weeks start on Monday. */
    fun isoWeek(d: LocalDate): Int = d.get(WeekFields.ISO.weekOfWeekBasedYear())

    private fun isoWeekYear(d: LocalDate): Int = d.get(WeekFields.ISO.weekBasedYear())

    /** Short label drawn under every bar. */
    private fun label(p: Period, s: LocalDate): String = when (p) {
        Period.DAY -> s.dayOfMonth.toString()
        Period.WEEK -> isoWeek(s).toString()
        Period.MONTH -> String.format("%02d", s.monthValue)
        Period.QUARTER -> "Q" + ((s.monthValue - 1) / 3 + 1)
        Period.YEAR -> s.year.toString().takeLast(2)
    }

    /** Full label for the bubble and the Details header. */
    fun longLabel(p: Period, s: LocalDate): String = when (p) {
        Period.DAY -> Fmt.dayLabel(s)
        Period.WEEK -> "week " + isoWeek(s) + ", " + isoWeekYear(s)
        Period.MONTH -> String.format("%02d.%s", s.monthValue, s.year.toString().takeLast(2))
        Period.QUARTER -> "Q" + ((s.monthValue - 1) / 3 + 1) + " " + s.year
        Period.YEAR -> s.year.toString()
    }

    /**
     * Slices ordered so the biggest area sits at the bottom of the bar and its
     * projects stay adjacent, biggest project first.
     */
    fun stack(sessions: List<Session>): List<Slice> {
        val byProject = sessions.groupBy { it.projectId }
            .map { (pid, list) ->
                Slice(
                    projectId = pid,
                    areaId = list.first().areaId,
                    colorArgb = Store.project(pid)?.colorArgb
                        ?: Store.area(list.first().areaId)?.colorArgb ?: 0xFF888888.toInt(),
                    ms = list.sumOf { it.durationMillis }
                )
            }
        val areaTotals = byProject.groupBy { it.areaId }.mapValues { e -> e.value.sumOf { it.ms } }
        return byProject.sortedWith(
            compareByDescending<Slice> { areaTotals[it.areaId] ?: 0L }
                .thenByDescending { it.ms }
        )
    }

    fun totalBetween(from: LocalDate, toExclusive: LocalDate): Long {
        val a = Fmt.millisOf(from, 0, 0)
        val b = Fmt.millisOf(toExclusive, 0, 0)
        return Store.sessions.filter { it.startMillis in a until b }.sumOf { it.durationMillis }
    }

    /**
     * Nested rollup for the Details panel: areas always, projects when showProjects,
     * tasks under each project when showTasks. Every level shown at once.
     */
    fun detailNested(sessions: List<Session>, showProjects: Boolean, showTasks: Boolean): List<DetailRow> {
        if (sessions.isEmpty()) return emptyList()
        return sessions.groupBy { it.areaId }.map { (aid, aList) ->
            val a = Store.area(aid)
            val projectRows = if (showProjects) {
                aList.groupBy { it.projectId }.map { (pid, pList) ->
                    val taskRows = if (showTasks) {
                        pList.filter { it.taskId != null }.groupBy { it.taskId }
                            .map { (tid, tList) ->
                                DetailRow(
                                    Store.task(tid)?.name ?: "Deleted",
                                    Store.project(pid)?.colorArgb ?: 0xFF888888.toInt(),
                                    tList.sumOf { it.durationMillis }
                                )
                            }.sortedByDescending { it.ms }
                    } else emptyList()
                    DetailRow(
                        Store.project(pid)?.name ?: "Deleted",
                        Store.project(pid)?.colorArgb ?: 0xFF888888.toInt(),
                        pList.sumOf { it.durationMillis },
                        taskRows
                    )
                }.sortedByDescending { it.ms }
            } else emptyList()
            DetailRow(a?.name ?: "Deleted", a?.colorArgb ?: 0xFF888888.toInt(),
                aList.sumOf { it.durationMillis }, projectRows)
        }.sortedByDescending { it.ms }
    }

    /** Rolls a set of sessions up by the chosen dimension for the Details panel. */
    fun detail(sessions: List<Session>, dim: DetailDim): List<DetailRow> {
        if (sessions.isEmpty()) return emptyList()
        return when (dim) {
            DetailDim.AREA -> sessions.groupBy { it.areaId }
                .map { (aid, list) ->
                    val a = Store.area(aid)
                    val kids = list.groupBy { it.projectId }
                        .map { (pid, l) ->
                            DetailRow(
                                Store.project(pid)?.name ?: "Deleted",
                                Store.project(pid)?.colorArgb ?: 0xFF888888.toInt(),
                                l.sumOf { it.durationMillis }
                            )
                        }.sortedByDescending { it.ms }
                    DetailRow(a?.name ?: "Deleted", a?.colorArgb ?: 0xFF888888.toInt(),
                        list.sumOf { it.durationMillis }, kids)
                }.sortedByDescending { it.ms }

            DetailDim.PROJECT -> sessions.groupBy { it.projectId }
                .map { (pid, list) ->
                    DetailRow(
                        Store.project(pid)?.name ?: "Deleted",
                        Store.project(pid)?.colorArgb ?: 0xFF888888.toInt(),
                        list.sumOf { it.durationMillis }
                    )
                }.sortedByDescending { it.ms }

            DetailDim.TASK -> sessions.filter { it.taskId != null }.groupBy { it.taskId }
                .map { (tid, list) ->
                    DetailRow(
                        Store.task(tid)?.name ?: "Deleted",
                        Store.project(list.first().projectId)?.colorArgb ?: 0xFF888888.toInt(),
                        list.sumOf { it.durationMillis }
                    )
                }.sortedByDescending { it.ms }
        }
    }

    /** Target minutes for one bucket of the given period, or null if goals are off. */
    /** Calendar days in one bucket of a period, for the full-day axis. */
    fun daysPerBucket(period: Period): Int = when (period) {
        Period.DAY -> 1
        Period.WEEK -> 7
        Period.MONTH -> 30
        Period.QUARTER -> 90
        Period.YEAR -> 365
    }

    /** Total hours in a bucket at 24h/day. */
    fun fullDayHours(period: Period): Double = daysPerBucket(period) * 24.0

    /** Sleep hours in a bucket at 8h/day. */
    fun sleepHours(period: Period): Double = daysPerBucket(period) * 8.0

    fun goalMsFor(period: Period): Long? {
        if (!Store.settings.goalEnabled) return null
        val daily = Store.settings.goalDailyMin.toLong()
        val days = when (period) {
            Period.DAY -> 1L
            Period.WEEK -> 5L
            Period.MONTH -> 22L
            Period.QUARTER -> 66L
            Period.YEAR -> 264L
        }
        return daily * days * 60_000L
    }

    fun msForArea(areaId: String): Long =
        Store.sessions.filter { it.areaId == areaId }.sumOf { it.durationMillis }

    fun msForProject(projectId: String): Long =
        Store.sessions.filter { it.projectId == projectId }.sumOf { it.durationMillis }

    fun msForTask(taskId: String): Long =
        Store.sessions.filter { it.taskId == taskId }.sumOf { it.durationMillis }

    fun sessionsForArea(areaId: String): List<Session> =
        Store.sessions.filter { it.areaId == areaId }.sortedByDescending { it.startMillis }

    fun sessionsForProject(projectId: String): List<Session> =
        Store.sessions.filter { it.projectId == projectId }.sortedByDescending { it.startMillis }

    fun sessionsForTask(taskId: String): List<Session> =
        Store.sessions.filter { it.taskId == taskId }.sortedByDescending { it.startMillis }

    fun today(): Long = LocalDate.now().let { totalBetween(it, it.plusDays(1)) }
    fun thisWeek(): Long = LocalDate.now().let { totalBetween(startOfWeek(it), it.plusDays(1)) }
    fun thisMonth(): Long = LocalDate.now().let { totalBetween(startOfMonth(it), it.plusDays(1)) }
    fun allTime(): Long = Store.sessions.sumOf { it.durationMillis }
}
