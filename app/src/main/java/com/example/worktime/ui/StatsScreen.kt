package com.example.worktime.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.worktime.data.*
import com.example.worktime.ui.theme.*
import java.time.LocalDate

@Composable
fun StatsScreen() {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stats", style = MaterialTheme.typography.headlineMedium, color = TextHi)
        }

        Segmented(
            listOf("Overview", "Sessions", "Archive"),
            tab,
            { tab = it },
            Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(10.dp))

        when (tab) {
            0 -> OverviewTab()
            1 -> TimelineTab()
            else -> ArchiveTab()
        }
    }
}

/* ------------------------------ overview ------------------------------ */

@Composable
private fun OverviewTab() {
    var period by remember { mutableStateOf(Period.DAY) }
    var fullView by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var showProjects by remember { mutableStateOf(true) }
    var showTasks by remember { mutableStateOf(false) }
    val buckets = remember(period, Store.sessions) { Stats.buckets(period) }
    // Start on the most recent bucket, which for Day means today.
    var selected by remember(period, buckets.size) { mutableStateOf(buckets.lastIndex) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Today", Fmt.minutes(Stats.today()), Modifier.weight(1f))
            Metric("Week", Fmt.minutes(Stats.thisWeek()), Modifier.weight(1f))
            Metric("Month", Fmt.minutes(Stats.thisMonth()), Modifier.weight(1f))
            Metric("Total", Fmt.hoursOnly(Stats.allTime()), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        Segmented(
            Period.values().map { it.label },
            Period.values().indexOf(period),
            { period = Period.values()[it] }
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clickable { fullView = !fullView },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Absolute time", style = MaterialTheme.typography.bodyMedium,
                color = if (fullView) accent() else TextMid)
            Spacer(Modifier.weight(1f))
            SmallSwitch(fullView) { fullView = it }
        }
        Spacer(Modifier.height(8.dp))
        HistoryChart(buckets, period, selected, fullView) { selected = it }

        Spacer(Modifier.height(24.dp))

        val chosen = buckets.getOrNull(selected)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { detailsOpen = !detailsOpen },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (detailsOpen) "\u2212" else "+",
                style = MaterialTheme.typography.titleMedium, color = TextMid)
            Spacer(Modifier.width(10.dp))
            SectionLabel("Details")
            Spacer(Modifier.width(10.dp))
            Text(
                if (chosen != null) Stats.longLabel(period, chosen.start) else "all",
                style = MaterialTheme.typography.titleMedium, color = TextMid
            )
            Spacer(Modifier.weight(1f))
            Text(
                Fmt.minutes(if (chosen != null) chosen.total else buckets.sumOf { it.total }),
                style = MaterialTheme.typography.titleMedium, color = TextHi
            )
        }

        if (detailsOpen) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckRow("Projects", showProjects) {
                    showProjects = !showProjects
                    if (!showProjects) showTasks = false  // tasks depend on projects
                }
                if (Store.settings.tasksEnabled) {
                    Spacer(Modifier.width(20.dp))
                    CheckRow("Tasks", showTasks, enabled = showProjects) {
                        if (showProjects) showTasks = !showTasks
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val sessions = if (chosen != null) chosen.sessions else buckets.flatMap { it.sessions }
        Breakdown(Stats.detailNested(sessions, showProjects, showTasks && Store.settings.tasksEnabled), 0)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(
        Modifier.clickable(enabled = enabled) { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val c = if (!enabled) TextLow else if (checked) accent() else TextMid
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val sw = size.width * 0.09f
            drawRect(c, style = Stroke(width = sw))
            if (checked) {
                drawLine(c, Offset(size.width * 0.24f, size.height * 0.52f),
                    Offset(size.width * 0.43f, size.height * 0.72f), sw * 1.4f, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.43f, size.height * 0.72f),
                    Offset(size.width * 0.78f, size.height * 0.30f), sw * 1.4f, StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) TextHi else TextLow)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = TextHi, maxLines = 1)
    }
}

/** Axis maximum and gridline step in hours, chosen so the labels stay round. */
private fun niceScale(maxHours: Double): Pair<Double, Double> {
    val steps = doubleArrayOf(
        0.25, 0.5, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 24.0,
        50.0, 100.0, 200.0, 400.0, 800.0, 2000.0
    )
    val target = maxHours / 4.0
    val step = steps.firstOrNull { it >= target } ?: steps.last()
    val axisMax = kotlin.math.ceil(maxHours / step) * step
    return (if (axisMax <= 0.0) step else axisMax) to step
}

private fun hourLabel(h: Double): String = when {
    h <= 0.0 -> "0"
    h < 1.0 -> "" + kotlin.math.round(h * 60).toInt() + "m"
    h == kotlin.math.floor(h) -> "" + h.toInt() + "h"
    else -> String.format(java.util.Locale.US, "%.1fh", h)
}

@Composable
private fun HistoryChart(
    buckets: List<Bucket>,
    period: Period,
    selected: Int,
    fullView: Boolean,
    onSelect: (Int) -> Unit
) {
    val maxTotal = buckets.maxOfOrNull { it.total } ?: 0L
    if (maxTotal == 0L && !fullView) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No sessions in this range",
                style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
        return
    }

    val maxHours = maxTotal / 3_600_000.0
    val goalMs = Stats.goalMsFor(period)
    val goalHours = goalMs?.let { it / 3_600_000.0 } ?: 0.0
    val sleepHours = Stats.sleepHours(period)

    // Full view pins the axis to the whole 24h/day; otherwise it auto-scales.
    val axisMax: Double
    val stepH: Double
    if (fullView) {
        axisMax = Stats.fullDayHours(period)
        stepH = axisMax / 4.0
    } else {
        val scale = niceScale(maxOf(maxHours, goalHours))
        axisMax = scale.first
        stepH = scale.second
    }

    val gridArgb = Line.toArgb()
    val labelArgb = TextLow.toArgb()
    val hiArgb = TextHi.toArgb()
    val accArgb = Store.settings.accentArgb
    val bubbleArgb = Surface2.toArgb()
    // Sleep block distinct from the (now brighter) button surface: lean toward TextLow.
    val sleepArgb = androidx.compose.ui.graphics.lerp(Surface2, TextLow, 0.28f).toArgb()

    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.SANS_SERIF
        }
    }

    val plotH = 187.dp
    val xLabelH = 18.dp
    val bubbleH = 38.dp   // headroom above the plot; keeps the callout off the top line
    val gutter = 40.dp    // fixed left column for the Y-axis labels
    val pitchDp = 34.dp   // fixed width per bucket, so the plot can be scrolled

    val scroll = rememberScrollState()
    // Jump to the newest bucket once the row is measured. If everything fits the
    // viewport, maxValue stays 0 and the right-anchored drawing keeps it at the edge.
    LaunchedEffect(buckets.size, period) {
        repeat(120) {
            if (scroll.maxValue > 0) {
                scroll.scrollTo(scroll.maxValue)
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(16)
        }
    }

    val plotWidthDp = pitchDp * buckets.size

    Row(Modifier.fillMaxWidth().height(bubbleH + plotH + xLabelH)) {

        // Fixed Y axis: gridline stubs and hour labels that never scroll.
        Canvas(Modifier.width(gutter).fillMaxHeight()) {
            val top = bubbleH.toPx()
            val plotHpx = plotH.toPx()
            val baseY = top + plotHpx
            paint.textSize = 9.sp.toPx()
            paint.color = labelArgb
            paint.textAlign = android.graphics.Paint.Align.LEFT
            var v = 0.0
            while (v <= axisMax + 1e-9) {
                val y = baseY - (v / axisMax).toFloat() * plotHpx
                if (v > 0.0) {
                    drawContext.canvas.nativeCanvas.drawText(hourLabel(v), 0f, y - 3.dp.toPx(), paint)
                }
                v += stepH
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // maxWidth here is the real viewport (this Box is not scrollable).
            val viewportW = maxWidth
            val contentW = maxOf(plotWidthDp, viewportW)
            Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                Canvas(
                    Modifier
                        .width(contentW)
                        .fillMaxHeight()
                        .pointerInput(buckets, contentW) {
                            detectTapGestures { off ->
                                if (buckets.isEmpty()) return@detectTapGestures
                                val pitch = pitchDp.toPx()
                                val originX = size.width - pitch * buckets.size
                                val idx = ((off.x - originX) / pitch).toInt()
                                onSelect(if (idx in buckets.indices) idx else -1)
                            }
                        }
                ) {
                    val top = bubbleH.toPx()
                val plotHpx = plotH.toPx()
                val baseY = top + plotHpx
            val pitch = pitchDp.toPx()
            val fullW = size.width
            // Anchor the newest bucket to the right edge. When content overflows the
            // viewport, size.width already equals the content width so this is 0.
            val originX = (size.width - pitch * buckets.size).coerceAtLeast(0f)
            val barW = (pitch * 0.5f).coerceIn(2f, 22.dp.toPx())

            paint.textSize = 9.sp.toPx()

            // Full view: a faint block at the top for sleep hours.
            if (fullView && sleepHours > 0.0) {
                val sleepFrac = (sleepHours / axisMax).toFloat().coerceIn(0f, 1f)
                val sleepBottom = baseY - (1f - sleepFrac) * plotHpx
                drawRect(
                    color = Color(sleepArgb),
                    topLeft = Offset(0f, top),
                    size = Size(fullW, sleepBottom - top)
                )
            }

            // gridlines across the whole scrollable width
            var v = 0.0
            while (v <= axisMax + 1e-9) {
                val y = baseY - (v / axisMax).toFloat() * plotHpx
                drawLine(Color(gridArgb), Offset(0f, y), Offset(fullW, y), 1f)
                v += stepH
            }

            // bars, anchored from the right
            val hasSel = selected in buckets.indices
            buckets.forEachIndexed { i, b ->
                if (b.total == 0L) return@forEachIndexed
                var yBottom = baseY
                val x = originX + i * pitch + (pitch - barW) / 2f
                b.slices.reversed().forEach { sl ->
                    val hpx = (sl.ms / 3_600_000.0 / axisMax).toFloat() * plotHpx
                    if (hpx <= 0f) return@forEach
                    drawRect(Color(sl.colorArgb), Offset(x, yBottom - hpx), Size(barW, hpx))
                    yBottom -= hpx
                }
            }

            // goal line
            if (goalMs != null) {
                val gy = baseY - (goalHours / axisMax).toFloat() * plotHpx
                val dash = 6.dp.toPx()
                var gx = 0f
                while (gx < fullW) {
                    drawLine(Color(accArgb), Offset(gx, gy),
                        Offset(minOf(gx + dash, fullW), gy), 1.5.dp.toPx())
                    gx += dash * 2
                }
            }

            // selected bar outline + callout
            if (hasSel) {
                val b = buckets[selected]
                val totalH = (b.total / 3_600_000.0 / axisMax).toFloat() * plotHpx
                val x = originX + selected * pitch + (pitch - barW) / 2f
                val barTop = baseY - totalH
                if (totalH > 0f) {
                    drawRect(
                        color = Color(accArgb),
                        topLeft = Offset(x - 2.5f, barTop - 2.5f),
                        size = Size(barW + 5f, totalH + 5f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                val text = Fmt.minutes(b.total)
                paint.textSize = 11.sp.toPx()
                paint.textAlign = android.graphics.Paint.Align.LEFT
                val textW = paint.measureText(text)
                val padH = 10.dp.toPx()
                val bw = textW + padH * 2
                val bh = 26.dp.toPx()
                var bx = x + barW / 2f - bw / 2f
                bx = bx.coerceIn(0f, (fullW - bw).coerceAtLeast(0f))
                val by = 2.dp.toPx()
                val cx = x + barW / 2f
                drawLine(Color(accArgb), Offset(cx, by + bh),
                    Offset(cx, (barTop - 3.dp.toPx()).coerceAtLeast(by + bh)), 1.5.dp.toPx())
                drawRect(Color(bubbleArgb), Offset(bx, by), Size(bw, bh))
                drawRect(Color(gridArgb), Offset(bx, by), Size(bw, bh), style = Stroke(width = 1f))
                paint.color = hiArgb
                drawContext.canvas.nativeCanvas.drawText(text, bx + padH, by + bh / 2f + 4.dp.toPx(), paint)
                paint.textSize = 9.sp.toPx()
            }

            // x labels under every bar
            paint.textAlign = android.graphics.Paint.Align.CENTER
            buckets.forEachIndexed { i, b ->
                paint.color = if (hasSel && i == selected) accArgb else labelArgb
                drawContext.canvas.nativeCanvas.drawText(
                    b.label, originX + i * pitch + pitch / 2f, baseY + 13.dp.toPx(), paint
                )
            }
            }
            }
        }
    }
}

@Composable
private fun Breakdown(rows: List<DetailRow>, depth: Int) {
    if (rows.isEmpty()) {
        if (depth == 0) Text("Nothing here",
            style = MaterialTheme.typography.bodyMedium, color = TextLow)
        return
    }
    rows.forEachIndexed { i, r ->
        if (depth == 0 && i > 0) Spacer(Modifier.height(14.dp))
        val big = depth == 0
        Row(
            Modifier.padding(start = (depth * 20).dp, top = if (big) 0.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dot(r.colorArgb, if (big) 10 else 8)
            Spacer(Modifier.width(10.dp))
            Text(r.name,
                style = if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (big) TextHi else TextMid)
            Spacer(Modifier.weight(1f))
            Text(Fmt.minutes(r.ms),
                style = if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (big) TextHi else TextMid)
        }
        Breakdown(r.children, depth + 1)
    }
}


/* ------------------------------ archive ------------------------------ */

private sealed interface ArchiveSel {
    data class A(val id: String) : ArchiveSel
    data class P(val id: String) : ArchiveSel
    data class T(val id: String) : ArchiveSel
}

@Composable
private fun ArchiveTab() {
    var open by remember { mutableStateOf<ArchiveSel?>(null) }

    val sel = open
    if (sel != null) {
        ArchiveDetail(sel, onBack = { open = null })
        return
    }

    val areas = Store.areas.filter { it.archived }
    val projects = Store.projects.filter { it.archived }
    val tasks = Store.tasks.filter { it.archived }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        if (areas.isEmpty() && projects.isEmpty() && tasks.isEmpty()) {
            Text(
                "Nothing archived. Archive a finished area, project or task to keep it out of the " +
                        "pickers while its time still counts in the totals.",
                style = MaterialTheme.typography.bodyMedium, color = TextMid,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(40.dp))
            return@Column
        }

        if (areas.isNotEmpty()) {
            SectionLabel("Areas")
            Spacer(Modifier.height(6.dp))
            areas.forEach { a ->
                ArchiveRow(a.name, a.colorArgb, Stats.msForArea(a.id),
                    onOpen = { open = ArchiveSel.A(a.id) },
                    onRestore = { Store.updateArea(a.id, archived = false) })
            }
            Spacer(Modifier.height(18.dp))
        }
        if (projects.isNotEmpty()) {
            SectionLabel("Projects")
            Spacer(Modifier.height(6.dp))
            projects.forEach { p ->
                val area = Store.area(p.areaId)?.name ?: ""
                ArchiveRow(area + "  \u00B7  " + p.name, p.colorArgb, Stats.msForProject(p.id),
                    onOpen = { open = ArchiveSel.P(p.id) },
                    onRestore = { Store.updateProject(p.id, archived = false) })
            }
            Spacer(Modifier.height(18.dp))
        }
        if (tasks.isNotEmpty()) {
            SectionLabel("Tasks")
            Spacer(Modifier.height(6.dp))
            tasks.forEach { tk ->
                val proj = Store.project(tk.projectId)
                ArchiveRow(
                    (Store.area(proj?.areaId)?.name ?: "") + "  \u00B7  " +
                            (proj?.name ?: "") + "  \u00B7  " + tk.name,
                    proj?.colorArgb ?: 0xFF888888.toInt(),
                    Stats.msForTask(tk.id),
                    onOpen = { open = ArchiveSel.T(tk.id) },
                    onRestore = { Store.updateTask(tk.id, archived = false) })
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ArchiveRow(
    label: String, argb: Int, totalMs: Long,
    onOpen: () -> Unit, onRestore: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(argb, 10)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextHi)
            Spacer(Modifier.height(2.dp))
            Text(Fmt.minutes(totalMs), style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
        Text(
            "Unarchive",
            style = MaterialTheme.typography.bodyMedium,
            color = accent(),
            modifier = Modifier.clickable { onRestore() }.padding(start = 10.dp)
        )
    }
}

@Composable
private fun ArchiveDetail(sel: ArchiveSel, onBack: () -> Unit) {
    val title: String
    val argb: Int
    val sessions: List<Session>
    when (sel) {
        is ArchiveSel.A -> {
            val a = Store.area(sel.id)
            title = a?.name ?: "Area"
            argb = a?.colorArgb ?: 0xFF888888.toInt()
            sessions = Stats.sessionsForArea(sel.id)
        }
        is ArchiveSel.P -> {
            val p = Store.project(sel.id)
            title = (Store.area(p?.areaId)?.name ?: "") + "  \u00B7  " + (p?.name ?: "Project")
            argb = p?.colorArgb ?: 0xFF888888.toInt()
            sessions = Stats.sessionsForProject(sel.id)
        }
        is ArchiveSel.T -> {
            val tk = Store.task(sel.id)
            title = tk?.name ?: "Task"
            argb = Store.project(tk?.projectId)?.colorArgb ?: 0xFF888888.toInt()
            sessions = Stats.sessionsForTask(sel.id)
        }
    }
    val total = sessions.sumOf { it.durationMillis }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ghost("Back", onBack)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(argb, 12)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi,
                modifier = Modifier.weight(1f))
            Text(Fmt.minutes(total), style = MaterialTheme.typography.titleMedium, color = TextHi)
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("" + sessions.size + " sessions")
        Spacer(Modifier.height(10.dp))

        if (sessions.isEmpty()) {
            Text("No sessions recorded", style = MaterialTheme.typography.bodyMedium, color = TextLow)
        } else {
            sessions.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.fullDate(Fmt.localDate(s.startMillis)),
                            style = MaterialTheme.typography.bodyMedium, color = TextHi)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            Fmt.clockTime(s.startMillis) + " \u2013 " + Fmt.clockTime(s.endMillis),
                            style = MaterialTheme.typography.bodySmall, color = TextLow
                        )
                    }
                    Text(Fmt.exact(s.durationMillis),
                        style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/* ------------------------------ timeline ------------------------------ */

@Composable
private fun TimelineTab() {
    var editing by remember { mutableStateOf<Session?>(null) }
    var pendingDelete by remember { mutableStateOf<Session?>(null) }
    val grouped = remember(Store.sessions) {
        Store.sessions.sortedByDescending { it.startMillis }
            .groupBy { Fmt.localDate(it.startMillis) }
            .toList()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (grouped.isEmpty()) {
            item {
                Text("Nothing yet", style = MaterialTheme.typography.bodyMedium, color = TextLow)
            }
        }
        grouped.forEach { (date, list) ->
            item(key = "h-" + date) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Fmt.fullDate(date),
                        style = MaterialTheme.typography.labelSmall, color = TextLow)
                    Spacer(Modifier.weight(1f))
                    Text(Fmt.minutes(list.sumOf { it.durationMillis }),
                        style = MaterialTheme.typography.labelSmall, color = TextMid)
                }
            }
            for (s in list) {
                item(key = s.id) {
                    SwipeToDelete(
                        open = pendingDelete?.id == s.id,
                        onArm = { pendingDelete = s }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Bg)
                                .clickable { editing = s }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Dot(Store.colorFor(s), 10)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Store.labelFor(s),
                                    style = MaterialTheme.typography.bodyMedium, color = TextHi)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    Fmt.clockTime(s.startMillis) + " \u2013 " + Fmt.clockTime(s.endMillis) +
                                            (if (s.manual) "  \u00B7  manual" else ""),
                                    style = MaterialTheme.typography.bodySmall, color = TextLow
                                )
                            }
                            Text(Fmt.exact(s.durationMillis),
                                style = MaterialTheme.typography.bodyMedium, color = TextMid)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    editing?.let { s ->
        SessionDialog(existing = s, onDismiss = { editing = null })
    }

    pendingDelete?.let { s ->
        ConfirmDialog(
            title = "Delete session",
            body = Store.labelFor(s) + ", " + Fmt.exact(s.durationMillis) +
                    " on " + Fmt.isoDate(s.startMillis) + ". This cannot be undone.",
            confirm = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = { Store.deleteSession(s.id); pendingDelete = null }
        )
    }
}

/**
 * Drag a row to the right to uncover a red bin. Past about a third of the width
 * the row springs back and onDelete fires, which opens the confirmation.
 */
@Composable
private fun SwipeToDelete(
    open: Boolean,
    onArm: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(1f) }
    val trigger = 0.33f

    // Parent controls the latch: when open, snap to the right and stay; when the
    // parent clears it (cancel), animate back.
    LaunchedEffect(open, widthPx) {
        if (open) offset.animateTo(widthPx * 0.6f) else offset.animateTo(0f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        if (offset.value > 1f) {
            val progress = (offset.value / (widthPx * trigger)).coerceIn(0f, 1f)
            val full = open || progress >= 1f
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        if (full) Danger else Danger.copy(alpha = 0.20f + 0.65f * progress)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                TrashIcon(
                    tint = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(open) {
                    if (open) return@pointerInput   // latched: ignore further drags
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offset.value > widthPx * trigger) {
                                onArm()   // parent will set open=true and keep it latched
                            } else {
                                scope.launch { offset.animateTo(0f) }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } }
                    ) { change, dx ->
                        change.consume()
                        scope.launch {
                            offset.snapTo((offset.value + dx).coerceIn(0f, widthPx * 0.6f))
                        }
                    }
                }
        ) { content() }
    }
}

@Composable
private fun TrashIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, k: Float = 1f) =
            drawLine(
                color = tint,
                start = Offset(w * x1, h * y1),
                end = Offset(w * x2, h * y2),
                strokeWidth = sw * k,
                cap = StrokeCap.Round
            )
        line(0.10f, 0.24f, 0.90f, 0.24f)          // lid
        line(0.38f, 0.24f, 0.38f, 0.11f)          // handle
        line(0.62f, 0.24f, 0.62f, 0.11f)
        line(0.38f, 0.11f, 0.62f, 0.11f)
        line(0.21f, 0.28f, 0.27f, 0.88f)          // body
        line(0.79f, 0.28f, 0.73f, 0.88f)
        line(0.27f, 0.88f, 0.73f, 0.88f)
        line(0.43f, 0.40f, 0.45f, 0.75f, 0.8f)    // ribs
        line(0.57f, 0.40f, 0.55f, 0.75f, 0.8f)
    }
}

/* --------------------------- add / edit session --------------------------- */

@Composable
fun SessionDialog(existing: Session?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val initial = existing?.startMillis ?: System.currentTimeMillis()
    var date by remember { mutableStateOf(Fmt.localDate(initial)) }
    var hour by remember { mutableStateOf(Fmt.localDateTime(initial).hour) }
    var minute by remember { mutableStateOf(Fmt.localDateTime(initial).minute) }
    // For a new entry the user usually types only minutes and leaves the time alone.
    // In that case the session is treated as ending now, so it lands in the past.
    var timeEdited by remember { mutableStateOf(existing != null) }
    var minutes by remember {
        mutableStateOf(((existing?.durationMillis ?: 0L) / 60000L).toString().let {
            if (it == "0") "" else it
        })
    }
    var areaId by remember { mutableStateOf(existing?.areaId ?: Store.settings.lastAreaId) }
    var projectId by remember { mutableStateOf(existing?.projectId ?: Store.settings.lastProjectId) }
    var taskId by remember { mutableStateOf(existing?.taskId) }
    var pickOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Sheet(onDismiss) {
        SectionLabel(if (existing == null) "Add manually" else "Session")
        Spacer(Modifier.height(14.dp))

        val label = if (areaId != null && projectId != null)
            (Store.area(areaId)?.name ?: "") + "  \u00B7  " + (Store.project(projectId)?.name ?: "") +
                    (Store.task(taskId)?.let { "  \u00B7  " + it.name } ?: "")
        else "Select project"
        Ghost(label, { pickOpen = true }, Modifier.fillMaxWidth())

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(Fmt.dayLabel(date) + "." + date.year, {
                DatePickerDialog(
                    ctx,
                    { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                    date.year, date.monthValue - 1, date.dayOfMonth
                ).show()
            })
            Ghost(String.format("%02d:%02d", hour, minute), {
                TimePickerDialog(
                    ctx,
                    { _, h, mi -> hour = h; minute = mi; timeEdited = true },
                    hour, minute, true
                ).show()
            })
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Minutes")
        Spacer(Modifier.height(8.dp))
        Field(minutes, { minutes = it.filter { c -> c.isDigit() }.take(4) }, "e.g. 50", numeric = true)

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Filled("Save", {
                val mins = minutes.toLongOrNull() ?: 0L
                val a = areaId
                val p = projectId
                if (mins > 0 && a != null && p != null) {
                    val durMs = mins * 60000L
                    // Time untouched on a new entry: end now, start durMs earlier.
                    val start = if (!timeEdited) System.currentTimeMillis() - durMs
                                else Fmt.millisOf(date, hour, minute)
                    if (existing == null) {
                        Store.addSession(a, p, taskId, start, durMs, manual = true)
                    } else {
                        Store.updateSession(
                            existing.copy(
                                areaId = a, projectId = p, taskId = taskId,
                                startMillis = start, durationMillis = durMs
                            )
                        )
                    }
                }
                onDismiss()
            })
            Ghost("Cancel", onDismiss)
            if (existing != null) Ghost("Delete", { confirmDelete = true })
        }
    }

    if (pickOpen) {
        SelectionDialog(
            onDismiss = { pickOpen = false },
            onPick = { a, p, t -> areaId = a; projectId = p; taskId = t; pickOpen = false }
        )
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete session",
            body = "This record will be removed permanently.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { Store.deleteSession(existing.id); onDismiss() }
        )
    }
}
