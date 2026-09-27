package com.example.worktime.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Imports time entries from a CSV exported by another tracker.
 *
 * Nothing is assumed about column order. The header is matched against the names
 * the common trackers use, so exports from Toggl, Clockify, Timeular and our own
 * CSV all land without the user editing the file first.
 *
 * The minimum a row needs is a date, a duration (or a start and end time) and a
 * project name. An area column is used when present; otherwise everything goes
 * into one area named Imported.
 */
object CsvImport {

    class ParseError(message: String) : Exception(message)

    data class Row(
        val startMillis: Long,
        val durationMs: Long,
        val area: String,
        val project: String,
        val task: String?,
        val srcKey: String
    )

    data class Preview(
        val rows: List<Row>,
        val skipped: Int,
        val areas: List<String>,
        val projects: List<String>,
        val totalMs: Long
    )

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd", "dd.MM.yyyy", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd"
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    private val ALIASES = mapOf(
        "date" to listOf("date", "start date", "start_date", "day", "startdate"),
        "start" to listOf("start", "start time", "start_time", "from", "starttime", "begin"),
        "end" to listOf("end", "end time", "end_time", "to", "endtime", "stop", "finish"),
        "duration" to listOf("duration", "minutes", "min", "hours", "time", "length", "hhmm", "duration (h)", "duration (min)"),
        "area" to listOf("area", "client", "group", "category", "workspace", "folder"),
        "project" to listOf("project", "project name", "label", "tag", "activity"),
        "task" to listOf("task", "description", "notes", "subproject", "title")
    )

    fun parse(text: String): Preview {
        val lines = text.replace("\uFEFF", "").lineSequence()
            .filter { it.isNotBlank() }.toList()
        if (lines.size < 2) throw ParseError("File has no rows")

        val delim = pickDelimiter(lines[0])
        val header = splitLine(lines[0], delim).map { it.trim().lowercase(Locale.US) }
        val idx = HashMap<String, Int>()
        ALIASES.forEach { (key, names) ->
            val i = header.indexOfFirst { h -> names.any { it == h } }
            if (i >= 0) idx[key] = i
        }

        if (!idx.containsKey("project"))
            throw ParseError("No project column found. Expected one of: project, label, tag, activity")
        if (!idx.containsKey("duration") && !(idx.containsKey("start") && idx.containsKey("end")))
            throw ParseError("No duration column, and no start plus end pair either")

        val rows = ArrayList<Row>()
        var skipped = 0

        for (n in 1 until lines.size) {
            val cells = splitLine(lines[n], delim)
            fun cell(k: String): String? =
                idx[k]?.let { cells.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

            val project = cell("project")
            if (project == null) { skipped++; continue }

            val date = cell("date")?.let { parseDate(it) }
            val startT = cell("start")
            val endT = cell("end")

            var durMs = cell("duration")?.let { parseDuration(it) } ?: 0L
            if (durMs <= 0L && startT != null && endT != null) {
                val a = parseTimeMinutes(startT)
                val b = parseTimeMinutes(endT)
                if (a != null && b != null) {
                    val diff = if (b >= a) b - a else b + 1440 - a
                    durMs = diff * 60_000L
                }
            }
            if (durMs < Store.MIN_SESSION_MS) { skipped++; continue }

            val day = date ?: LocalDate.now()
            val startMin = startT?.let { parseTimeMinutes(it) } ?: 9 * 60
            val startMillis = Fmt.millisOf(day, startMin / 60, startMin % 60)

            rows.add(
                Row(
                    startMillis = startMillis,
                    durationMs = durMs,
                    area = cell("area") ?: "Imported",
                    project = project,
                    task = cell("task"),
                    srcKey = "csv:" + day + ":" + startMin + ":" + durMs + ":" + project.hashCode()
                )
            )
        }

        if (rows.isEmpty()) throw ParseError("No usable rows found")

        return Preview(
            rows = rows,
            skipped = skipped,
            areas = rows.map { it.area }.distinct().sorted(),
            projects = rows.map { it.project }.distinct().sorted(),
            totalMs = rows.sumOf { it.durationMs }
        )
    }

    /** Creates any missing areas, projects and tasks, then inserts the sessions. */
    fun apply(preview: Preview, withTasks: Boolean): Int {
        preview.areas.forEachIndexed { i, name ->
            if (Store.areas.none { it.name.equals(name, true) }) {
                Store.addArea(name, Palette.base[(Store.areas.size + i) % Palette.base.size])
            }
        }
        val sessions = ArrayList<Session>()
        preview.rows.forEach { r ->
            val area = Store.areas.firstOrNull { it.name.equals(r.area, true) } ?: return@forEach
            val project = Store.projectsOf(area.id).firstOrNull { it.name.equals(r.project, true) }
                ?: Store.addProject(
                    area.id, r.project,
                    Palette.nextFreeShade(area.colorArgb, Store.projectsOf(area.id).map { it.colorArgb })
                )
            val taskId = if (withTasks && r.task != null) {
                (Store.tasksOf(project.id).firstOrNull { it.name.equals(r.task, true)
                } ?: Store.addTask(project.id, r.task)).id
            } else null
            sessions.add(
                Session(
                    areaId = area.id, projectId = project.id, taskId = taskId,
                    startMillis = r.startMillis, durationMillis = r.durationMs,
                    manual = true, srcKey = r.srcKey
                )
            )
        }
        return Store.importSessions(sessions)
    }

    // ---------- parsing helpers ----------

    private fun pickDelimiter(headerLine: String): Char {
        val counts = listOf(',', ';', '\t').map { it to headerLine.count { c -> c == it } }
        return counts.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first ?: ','
    }

    /** Minimal RFC-4180 split: honours double quotes and doubled quotes inside them. */
    private fun splitLine(line: String, delim: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delim && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseDate(v: String): LocalDate? {
        val head = v.trim().split(' ', 'T').first()
        DATE_FORMATS.forEach { f ->
            try { return LocalDate.parse(head, f) } catch (_: Exception) {}
        }
        try { return LocalDateTime.parse(v.trim()).toLocalDate() } catch (_: Exception) {}
        return null
    }

    /** Accepts 09:30, 9:30 AM, or a full timestamp. Returns minutes since midnight. */
    private fun parseTimeMinutes(v: String): Int? {
        val s = v.trim()
        val pm = s.contains("PM", true)
        val am = s.contains("AM", true)
        val m = Regex("(\\d{1,2}):(\\d{2})").find(s) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val mi = m.groupValues[2].toIntOrNull() ?: return null
        if (pm && h < 12) h += 12
        if (am && h == 12) h = 0
        if (h > 23 || mi > 59) return null
        return h * 60 + mi
    }

    /** Accepts 90, 1.5, 1:30, 01:30:00. Bare numbers are read as minutes. */
    private fun parseDuration(v: String): Long {
        val s = v.trim()
        if (s.contains(':')) {
            val parts = s.split(':').mapNotNull { it.trim().toIntOrNull() }
            return when (parts.size) {
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                2 -> (parts[0] * 3600L + parts[1] * 60L) * 1000L
                else -> 0L
            }
        }
        val num = s.replace(',', '.').toDoubleOrNull() ?: return 0L
        return Math.round(num * 60_000.0)
    }
}
