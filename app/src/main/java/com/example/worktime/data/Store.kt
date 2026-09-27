package com.example.worktime.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Store {

    /** Anything shorter than this is dropped rather than written as a session. */
    const val MIN_SESSION_MS = 60_000L

    /** A running timer stops itself here so a forgotten session cannot run all night. */
    const val MAX_SESSION_MS = 2 * 60 * 60 * 1000L

    private lateinit var file: File
    private var loaded = false

    var areas by mutableStateOf<List<Area>>(emptyList()); private set
    var projects by mutableStateOf<List<Project>>(emptyList()); private set
    var tasks by mutableStateOf<List<TaskItem>>(emptyList()); private set
    var sessions by mutableStateOf<List<Session>>(emptyList()); private set
    var settings by mutableStateOf(Settings()); private set
    var timer by mutableStateOf(ActiveTimer()); private set

    fun init(ctx: Context) {
        if (loaded) return
        file = File(ctx.applicationContext.filesDir, "worktime.json")
        load()
        loaded = true
        reconcileTimer()
    }

    // ---------- lookups ----------

    fun area(id: String?): Area? = areas.firstOrNull { it.id == id }
    fun project(id: String?): Project? = projects.firstOrNull { it.id == id }
    fun task(id: String?): TaskItem? = tasks.firstOrNull { it.id == id }
    fun projectsOf(areaId: String): List<Project> =
        projects.filter { it.areaId == areaId }.sortedBy { it.order }
    fun tasksOf(projectId: String): List<TaskItem> =
        tasks.filter { it.projectId == projectId }.sortedBy { it.order }

    fun labelFor(s: Session): String {
        val a = area(s.areaId)?.name ?: "?"
        val p = project(s.projectId)?.name ?: "?"
        val t = task(s.taskId)?.name
        return if (t != null) "$a / $p / $t" else "$a / $p"
    }

    fun colorFor(s: Session): Int =
        project(s.projectId)?.colorArgb ?: area(s.areaId)?.colorArgb ?: 0xFF888888.toInt()

    // ---------- mutations: areas ----------

    fun addArea(name: String, colorArgb: Int): Area {
        val a = Area(name = name, colorArgb = colorArgb, order = areas.size)
        areas = areas + a
        save()
        return a
    }

    fun updateArea(id: String, name: String? = null, colorArgb: Int? = null, archived: Boolean? = null) {
        areas = areas.map {
            if (it.id != id) it
            else it.copy(
                name = name ?: it.name,
                colorArgb = colorArgb ?: it.colorArgb,
                archived = archived ?: it.archived
            )
        }
        save()
    }

    fun deleteArea(id: String) {
        val pids = projects.filter { it.areaId == id }.map { it.id }.toSet()
        tasks = tasks.filterNot { it.projectId in pids }
        projects = projects.filterNot { it.areaId == id }
        sessions = sessions.filterNot { it.areaId == id }
        areas = areas.filterNot { it.id == id }
        if (settings.lastAreaId == id) settings = settings.copy(lastAreaId = null, lastProjectId = null, lastTaskId = null)
        if (timer.areaId == id) timer = timer.copy(areaId = null, projectId = null, taskId = null)
        save()
    }

    // ---------- mutations: projects ----------

    fun addProject(areaId: String, name: String, colorArgb: Int): Project {
        val p = Project(areaId = areaId, name = name, colorArgb = colorArgb, order = projectsOf(areaId).size)
        projects = projects + p
        save()
        return p
    }

    fun updateProject(id: String, name: String? = null, colorArgb: Int? = null, archived: Boolean? = null) {
        projects = projects.map {
            if (it.id != id) it
            else it.copy(
                name = name ?: it.name,
                colorArgb = colorArgb ?: it.colorArgb,
                archived = archived ?: it.archived
            )
        }
        save()
    }

    /**
     * Re-parents a project. Its sessions follow, and it is recoloured from the new
     * area's shade family so the colour still reads as belonging there.
     */
    fun moveProject(projectId: String, newAreaId: String) {
        val p = project(projectId) ?: return
        if (p.areaId == newAreaId) return
        val newArea = area(newAreaId) ?: return
        val used = projectsOf(newAreaId).map { it.colorArgb }
        val shade = Palette.nextFreeShade(newArea.colorArgb, used)
        projects = projects.map {
            if (it.id == projectId)
                it.copy(areaId = newAreaId, colorArgb = shade, order = projectsOf(newAreaId).size)
            else it
        }
        sessions = sessions.map {
            if (it.projectId == projectId) it.copy(areaId = newAreaId) else it
        }
        if (timer.projectId == projectId) timer = timer.copy(areaId = newAreaId)
        if (settings.lastProjectId == projectId) settings = settings.copy(lastAreaId = newAreaId)
        save()
    }

    fun deleteProject(id: String) {
        tasks = tasks.filterNot { it.projectId == id }
        sessions = sessions.filterNot { it.projectId == id }
        projects = projects.filterNot { it.id == id }
        if (settings.lastProjectId == id) settings = settings.copy(lastProjectId = null, lastTaskId = null)
        if (timer.projectId == id) timer = timer.copy(projectId = null, taskId = null)
        save()
    }

    // ---------- mutations: tasks ----------

    fun addTask(projectId: String, name: String): TaskItem {
        val t = TaskItem(projectId = projectId, name = name, order = tasksOf(projectId).size)
        tasks = tasks + t
        save()
        return t
    }

    fun updateTask(id: String, name: String? = null, archived: Boolean? = null) {
        tasks = tasks.map {
            if (it.id != id) it else it.copy(name = name ?: it.name, archived = archived ?: it.archived)
        }
        save()
    }

    fun deleteTask(id: String) {
        sessions = sessions.map { if (it.taskId == id) it.copy(taskId = null) else it }
        tasks = tasks.filterNot { it.id == id }
        if (settings.lastTaskId == id) settings = settings.copy(lastTaskId = null)
        if (timer.taskId == id) timer = timer.copy(taskId = null)
        save()
    }

    // ---------- sessions ----------

    fun addSession(areaId: String, projectId: String, taskId: String?, startMillis: Long, durationMillis: Long, manual: Boolean) {
        if (durationMillis < MIN_SESSION_MS) return
        sessions = sessions + Session(
            areaId = areaId, projectId = projectId, taskId = taskId,
            startMillis = startMillis, durationMillis = durationMillis, manual = manual
        )
        save()
    }

    /** Bulk insert. Rows whose srcKey is already present are skipped. One save at the end. */
    fun importSessions(incoming: List<Session>): Int {
        val known = sessions.mapNotNull { it.srcKey }.toHashSet()
        val fresh = incoming.filter { it.srcKey == null || known.add(it.srcKey) }
        if (fresh.isEmpty()) return 0
        sessions = sessions + fresh
        save()
        return fresh.size
    }

    fun updateSession(s: Session) {
        sessions = sessions.map { if (it.id == s.id) s else it }
        save()
    }

    fun deleteSession(id: String) {
        sessions = sessions.filterNot { it.id == id }
        save()
    }

    // ---------- settings ----------

    fun setDefaultDuration(min: Int) {
        settings = settings.copy(defaultDurationMin = min.coerceIn(1, 600))
        if (!timer.running && timer.accumulatedMs == 0L) {
            timer = timer.copy(targetMs = settings.defaultDurationMin * 60_000L)
        }
        save()
    }

    fun setAccent(argb: Int) {
        settings = settings.copy(accentArgb = argb)
        save()
    }

    fun setDarkTheme(v: Boolean) {
        settings = settings.copy(darkTheme = v)
        save()
    }

    fun setGoal(enabled: Boolean? = null, dailyMin: Int? = null) {
        settings = settings.copy(
            goalEnabled = enabled ?: settings.goalEnabled,
            goalDailyMin = (dailyMin ?: settings.goalDailyMin).coerceIn(15, 24 * 60)
        )
        save()
    }

    fun setBreaks(
        enabled: Boolean? = null,
        breakMin: Int? = null,
        autoStart: Boolean? = null,
        longEnabled: Boolean? = null,
        longMin: Int? = null,
        beforeLong: Int? = null
    ) {
        settings = settings.copy(
            breaksEnabled = enabled ?: settings.breaksEnabled,
            breakMin = (breakMin ?: settings.breakMin).coerceIn(1, 120),
            autoStartBreak = autoStart ?: settings.autoStartBreak,
            longBreakEnabled = longEnabled ?: settings.longBreakEnabled,
            longBreakMin = (longMin ?: settings.longBreakMin).coerceIn(1, 180),
            sessionsBeforeLongBreak = (beforeLong ?: settings.sessionsBeforeLongBreak).coerceIn(2, 12)
        )
        save()
    }

    /** Length of the break that follows the session just finished. */
    fun nextBreakMs(): Long =
        (if (isLongBreakNext()) settings.longBreakMin else settings.breakMin) * 60_000L

    fun isLongBreakNext(): Boolean =
        settings.longBreakEnabled &&
                settings.cycleCount > 0 &&
                settings.cycleCount % settings.sessionsBeforeLongBreak == 0

    /**
     * Called when a work timer reaches its target and breaks are on. Records the
     * session exactly at the target and leaves the timer idle in break mode.
     * Idempotent: a second call while already in break mode does nothing.
     */
    fun completeWork(): Boolean {
        if (timer.isBreak) return false
        val aId = timer.areaId
        val pId = timer.projectId
        val target = timer.targetMs
        if (aId != null && pId != null) {
            val start = if (timer.firstStartedAt > 0L) timer.firstStartedAt
                        else System.currentTimeMillis() - target
            addSession(aId, pId, timer.taskId, start, target, manual = false)
        }
        settings = settings.copy(cycleCount = settings.cycleCount + 1)
        timer = timer.copy(
            running = false, startedAt = 0L, firstStartedAt = 0L, accumulatedMs = 0L,
            isBreak = true, targetMs = nextBreakMs()
        )
        save()
        return true
    }

    fun startBreak(now: Long) {
        if (!timer.isBreak) return
        timer = timer.copy(running = true, startedAt = now, firstStartedAt = now)
        save()
    }

    /** Break over, or skipped. Returns to a fresh work timer. Nothing is recorded. */
    fun endBreak() {
        if (!timer.isBreak) return
        timer = timer.copy(
            running = false, startedAt = 0L, firstStartedAt = 0L, accumulatedMs = 0L,
            isBreak = false, targetMs = settings.defaultDurationMin * 60_000L
        )
        save()
    }

    fun setTasksEnabled(v: Boolean) {
        settings = settings.copy(tasksEnabled = v)
        save()
    }

    fun setSelection(areaId: String?, projectId: String?, taskId: String?) {
        settings = settings.copy(lastAreaId = areaId, lastProjectId = projectId, lastTaskId = taskId)
        timer = timer.copy(areaId = areaId, projectId = projectId, taskId = taskId)
        save()
    }

    // ---------- timer ----------

    fun setTarget(minutes: Int) {
        timer = timer.copy(targetMs = minutes.coerceIn(1, 600) * 60_000L)
        save()
    }

    fun start(now: Long) {
        if (timer.running) return
        val first = if (timer.accumulatedMs == 0L) now else timer.firstStartedAt
        timer = timer.copy(running = true, startedAt = now, firstStartedAt = first)
        save()
    }

    fun pause(now: Long) {
        if (!timer.running) return
        val acc = timer.accumulatedMs + (now - timer.startedAt).coerceAtLeast(0L)
        timer = timer.copy(running = false, accumulatedMs = acc, startedAt = 0L)
        save()
    }

    /** Hard stop at the cap. Nothing is written; the time waits to be saved or dropped. */
    fun autoStop() {
        if (!timer.running) return
        timer = timer.copy(running = false, startedAt = 0L, accumulatedMs = MAX_SESSION_MS)
        save()
    }

    /** Writes the accumulated time as a session and clears the timer. */
    fun finish(now: Long) {
        val elapsed = timer.elapsed(now)
        val aId = timer.areaId
        val pId = timer.projectId
        if (elapsed >= MIN_SESSION_MS && aId != null && pId != null) {
            val start = if (timer.firstStartedAt > 0L) timer.firstStartedAt else now - elapsed
            addSession(aId, pId, timer.taskId, start, elapsed, manual = false)
        }
        resetTimer()
    }

    fun resetTimer() {
        timer = timer.copy(
            running = false,
            startedAt = 0L,
            firstStartedAt = 0L,
            accumulatedMs = 0L,
            isBreak = false,
            targetMs = settings.defaultDurationMin * 60_000L
        )
        save()
    }

    /**
     * If the process was killed while running, clamp to the cap and stop. The time is
     * kept, not written, so it is still there to save or drop on the next launch.
     */
    private fun reconcileTimer() {
        if (!timer.running) return
        if (timer.elapsed(System.currentTimeMillis()) >= MAX_SESSION_MS) autoStop()
    }

    // ---------- csv ----------

    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("date,start,end,minutes,hhmm,area,project,task,source\n")
        sessions.sortedBy { it.startMillis }.forEach { s ->
            val minutes = s.durationMillis / 60000.0
            sb.append(Fmt.isoDate(s.startMillis)).append(',')
            sb.append(Fmt.clockTime(s.startMillis)).append(',')
            sb.append(Fmt.clockTime(s.endMillis)).append(',')
            sb.append(String.format(java.util.Locale.US, "%.2f", minutes)).append(',')
            sb.append(Fmt.hhmm(s.durationMillis)).append(',')
            sb.append(csv(area(s.areaId)?.name ?: "")).append(',')
            sb.append(csv(project(s.projectId)?.name ?: "")).append(',')
            sb.append(csv(task(s.taskId)?.name ?: "")).append(',')
            sb.append(if (s.manual) "manual" else "timer").append('\n')
        }
        return sb.toString()
    }

    private fun csv(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n'))
            "\"" + v.replace("\"", "\"\"") + "\""
        else v

    // ---------- persistence ----------

    private fun save() {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(toJson().toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (_: Exception) {
        }
    }

    /** Pretty-printed full backup: structure, sessions and settings. */
    fun exportJson(): String = toJson().put("exportedAt", System.currentTimeMillis()).toString(2)

    private fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("version", 1)

            root.put("areas", JSONArray().also { arr ->
                areas.forEach {
                    arr.put(JSONObject().apply {
                        put("id", it.id); put("name", it.name); put("color", it.colorArgb)
                        put("archived", it.archived); put("order", it.order)
                    })
                }
            })

            root.put("projects", JSONArray().also { arr ->
                projects.forEach {
                    arr.put(JSONObject().apply {
                        put("id", it.id); put("areaId", it.areaId); put("name", it.name)
                        put("color", it.colorArgb); put("archived", it.archived); put("order", it.order)
                    })
                }
            })

            root.put("tasks", JSONArray().also { arr ->
                tasks.forEach {
                    arr.put(JSONObject().apply {
                        put("id", it.id); put("projectId", it.projectId); put("name", it.name)
                        put("archived", it.archived); put("order", it.order)
                    })
                }
            })

            root.put("sessions", JSONArray().also { arr ->
                sessions.forEach {
                    arr.put(JSONObject().apply {
                        put("id", it.id); put("areaId", it.areaId); put("projectId", it.projectId)
                        put("taskId", it.taskId ?: JSONObject.NULL)
                        put("start", it.startMillis); put("duration", it.durationMillis)
                        put("manual", it.manual); put("note", it.note)
                        put("srcKey", it.srcKey ?: JSONObject.NULL)
                    })
                }
            })

            root.put("settings", JSONObject().apply {
                put("defaultDurationMin", settings.defaultDurationMin)
                put("accentArgb", settings.accentArgb)
                put("darkTheme", settings.darkTheme)
                put("goalEnabled", settings.goalEnabled)
                put("goalDailyMin", settings.goalDailyMin)
                put("breaksEnabled", settings.breaksEnabled)
                put("breakMin", settings.breakMin)
                put("autoStartBreak", settings.autoStartBreak)
                put("longBreakEnabled", settings.longBreakEnabled)
                put("longBreakMin", settings.longBreakMin)
                put("sessionsBeforeLongBreak", settings.sessionsBeforeLongBreak)
                put("cycleCount", settings.cycleCount)
                put("tasksEnabled", settings.tasksEnabled)
                put("lastAreaId", settings.lastAreaId ?: JSONObject.NULL)
                put("lastProjectId", settings.lastProjectId ?: JSONObject.NULL)
                put("lastTaskId", settings.lastTaskId ?: JSONObject.NULL)
            })

            root.put("timer", JSONObject().apply {
                put("running", timer.running)
                put("startedAt", timer.startedAt)
                put("firstStartedAt", timer.firstStartedAt)
                put("accumulatedMs", timer.accumulatedMs)
                put("targetMs", timer.targetMs)
                put("isBreak", timer.isBreak)
                put("areaId", timer.areaId ?: JSONObject.NULL)
                put("projectId", timer.projectId ?: JSONObject.NULL)
                put("taskId", timer.taskId ?: JSONObject.NULL)
            })

        return root
    }

    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifEmpty { null }

    private fun load() {
        if (!file.exists()) {
            seedDefaults()
            return
        }
        try {
            applyJson(JSONObject(file.readText()))
        } catch (_: Exception) {
            seedDefaults()
        }
    }

    private fun applyJson(root: JSONObject) {
            areas = root.optJSONArray("areas").toList { o ->
                Area(o.getString("id"), o.getString("name"), o.getInt("color"),
                    o.optBoolean("archived", false), o.optInt("order", 0))
            }
            projects = root.optJSONArray("projects").toList { o ->
                Project(o.getString("id"), o.getString("areaId"), o.getString("name"), o.getInt("color"),
                    o.optBoolean("archived", false), o.optInt("order", 0))
            }
            tasks = root.optJSONArray("tasks").toList { o ->
                TaskItem(o.getString("id"), o.getString("projectId"), o.getString("name"),
                    o.optBoolean("archived", false), o.optInt("order", 0))
            }
            sessions = root.optJSONArray("sessions").toList { o ->
                Session(o.getString("id"), o.getString("areaId"), o.getString("projectId"),
                    o.strOrNull("taskId"), o.getLong("start"), o.getLong("duration"),
                    o.optBoolean("manual", false), o.optString("note", ""),
                    o.strOrNull("srcKey"))
            }
            root.optJSONObject("settings")?.let { o ->
                settings = Settings(
                    o.optInt("defaultDurationMin", 50),
                    o.optInt("accentArgb", 0xFFEDEDF0.toInt()),
                    o.optBoolean("darkTheme", true),
                    o.optBoolean("goalEnabled", false),
                    o.optInt("goalDailyMin", 480),
                    o.optBoolean("breaksEnabled", false),
                    o.optInt("breakMin", 5),
                    o.optBoolean("autoStartBreak", true),
                    o.optBoolean("longBreakEnabled", false),
                    o.optInt("longBreakMin", 15),
                    o.optInt("sessionsBeforeLongBreak", 4),
                    o.optInt("cycleCount", 0),
                    o.optBoolean("tasksEnabled", false),
                    o.strOrNull("lastAreaId"), o.strOrNull("lastProjectId"), o.strOrNull("lastTaskId")
                )
            }
            root.optJSONObject("timer")?.let { o ->
                timer = ActiveTimer(
                    o.optBoolean("running", false),
                    o.optLong("startedAt", 0L),
                    o.optLong("firstStartedAt", 0L),
                    o.optLong("accumulatedMs", 0L),
                    o.optLong("targetMs", settings.defaultDurationMin * 60_000L),
                    o.optBoolean("isBreak", false),
                    o.strOrNull("areaId"), o.strOrNull("projectId"), o.strOrNull("taskId")
                )
            }
    }

    // ---------- backup ----------

    class BackupInfo(
        val areas: Int, val projects: Int, val tasks: Int, val sessions: Int,
        val exportedAt: Long
    )

    /** Reads the counts out of a backup without touching anything. Throws if unusable. */
    fun peekBackup(text: String): BackupInfo {
        val root = JSONObject(text)
        if (!root.has("areas") || !root.has("sessions"))
            throw IllegalArgumentException("Not a Worktime backup")
        return BackupInfo(
            root.optJSONArray("areas")?.length() ?: 0,
            root.optJSONArray("projects")?.length() ?: 0,
            root.optJSONArray("tasks")?.length() ?: 0,
            root.optJSONArray("sessions")?.length() ?: 0,
            root.optLong("exportedAt", 0L)
        )
    }

    /**
     * Replaces everything with the backup's contents. The running timer is not
     * restored, since a timer from another moment in time is meaningless here.
     */
    fun importBackup(text: String) {
        applyJson(JSONObject(text))
        timer = ActiveTimer(
            targetMs = settings.defaultDurationMin * 60_000L,
            areaId = settings.lastAreaId,
            projectId = settings.lastProjectId,
            taskId = settings.lastTaskId
        )
        save()
    }

    private fun <T> JSONArray?.toList(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) out.add(f(getJSONObject(i)))
        return out
    }

    private fun seedDefaults() {
        val a = Area(name = "Area 1", colorArgb = Palette.base[0], order = 0)
        areas = listOf(a)
        projects = listOf(
            Project(areaId = a.id, name = "Project 1", colorArgb = Palette.shadesArgb(a.colorArgb)[6], order = 0)
        )
        settings = settings.copy(lastAreaId = a.id, lastProjectId = projects[0].id)
        timer = timer.copy(
            areaId = a.id, projectId = projects[0].id,
            targetMs = settings.defaultDurationMin * 60_000L
        )
        save()
    }
}
