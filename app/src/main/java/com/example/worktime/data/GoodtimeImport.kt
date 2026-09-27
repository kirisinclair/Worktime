package com.example.worktime.data

import android.database.sqlite.SQLiteDatabase

/**
 * Reads a Goodtime backup (.db). Only work sessions are taken; breaks are dropped.
 *
 * Goodtime stores `timestamp` as the moment the session ENDED and `duration` in
 * whole minutes, so the start is timestamp minus duration.
 */
object GoodtimeImport {

    const val DEFAULT_LABEL = "PRODUCTIVITY_DEFAULT_LABEL"

    data class GtSession(
        val gtId: Long,
        val startMillis: Long,
        val durationMs: Long,
        val label: String
    )

    data class GtLabel(val name: String, val count: Int, val totalMs: Long) {
        val display: String get() = if (name == DEFAULT_LABEL) "Default (no label)" else name
    }

    data class Parsed(val labels: List<GtLabel>, val sessions: List<GtSession>)

    class ParseError(message: String) : Exception(message)

    fun parse(path: String): Parsed {
        val db = try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw ParseError("Not a readable SQLite file")
        }

        try {
            val sessions = ArrayList<GtSession>()
            val c = try {
                db.rawQuery(
                    "SELECT id, timestamp, duration, labelName FROM localSession WHERE isWork = 1",
                    null
                )
            } catch (e: Exception) {
                throw ParseError("No Goodtime session table in this file")
            }

            c.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val endMillis = it.getLong(1)
                    val durMin = it.getLong(2)
                    val label = it.getString(3) ?: DEFAULT_LABEL
                    if (durMin <= 0L) continue
                    val durMs = durMin * 60_000L
                    sessions.add(GtSession(id, endMillis - durMs, durMs, label))
                }
            }

            val labels = sessions.groupBy { it.label }
                .map { (name, list) -> GtLabel(name, list.size, list.sumOf { s -> s.durationMs }) }
                .sortedByDescending { it.totalMs }

            return Parsed(labels, sessions)
        } finally {
            try { db.close() } catch (_: Exception) {}
        }
    }
}
