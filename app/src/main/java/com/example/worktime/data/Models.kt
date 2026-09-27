package com.example.worktime.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Area(
    val id: String = newId(),
    val name: String,
    val colorArgb: Int,
    val archived: Boolean = false,
    val order: Int = 0
)

data class Project(
    val id: String = newId(),
    val areaId: String,
    val name: String,
    val colorArgb: Int,
    val archived: Boolean = false,
    val order: Int = 0
)

data class TaskItem(
    val id: String = newId(),
    val projectId: String,
    val name: String,
    val archived: Boolean = false,
    val order: Int = 0
)

data class Session(
    val id: String = newId(),
    val areaId: String,
    val projectId: String,
    val taskId: String? = null,
    val startMillis: Long,
    val durationMillis: Long,
    val manual: Boolean = false,
    val note: String = "",
    val srcKey: String? = null
) {
    val endMillis: Long get() = startMillis + durationMillis
}

data class Settings(
    val defaultDurationMin: Int = 50,
    val accentArgb: Int = 0xFFEDEDF0.toInt(),
    val darkTheme: Boolean = true,
    val goalEnabled: Boolean = false,
    val goalDailyMin: Int = 480,
    val breaksEnabled: Boolean = false,
    val breakMin: Int = 5,
    val autoStartBreak: Boolean = true,
    val longBreakEnabled: Boolean = false,
    val longBreakMin: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
    val cycleCount: Int = 0,
    val tasksEnabled: Boolean = false,
    val lastAreaId: String? = null,
    val lastProjectId: String? = null,
    val lastTaskId: String? = null
)

data class ActiveTimer(
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val firstStartedAt: Long = 0L,
    val accumulatedMs: Long = 0L,
    val targetMs: Long = 50 * 60_000L,
    val isBreak: Boolean = false,
    val areaId: String? = null,
    val projectId: String? = null,
    val taskId: String? = null
) {
    fun elapsed(now: Long): Long =
        accumulatedMs + if (running) (now - startedAt).coerceAtLeast(0L) else 0L
}
