package com.example.worktime.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.worktime.data.Fmt
import com.example.worktime.data.Store
import com.example.worktime.timer.Notifs
import com.example.worktime.timer.TimerService
import com.example.worktime.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    val t = Store.timer
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPicker by remember { mutableStateOf(false) }
    var showDuration by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }

    LaunchedEffect(t.running) {
        if (t.running) TimerService.start(ctx) else TimerService.stop(ctx)
    }

    LaunchedEffect(t.running, t.startedAt, t.isBreak) {
        while (t.running) {
            val ts = System.currentTimeMillis()
            now = ts
            val e = t.elapsed(ts)
            if (t.isBreak) {
                if (e >= t.targetMs) {
                    Store.endBreak()
                    Notifs.alert(ctx, "Break over", "Back to work")
                    break
                }
            } else if (Store.settings.breaksEnabled) {
                if (e >= t.targetMs) {
                    val long = Store.isLongBreakNext()
                    if (Store.completeWork()) {
                        Notifs.alert(
                            ctx, "Session done",
                            (if (long) "Long break" else "Break") + " " +
                                    (Store.timer.targetMs / 60000) + " min"
                        )
                    }
                    if (Store.settings.autoStartBreak) Store.startBreak(System.currentTimeMillis())
                    break
                }
            } else if (e >= Store.MAX_SESSION_MS) {
                Store.autoStop()
                break
            }
            delay(200)
        }
        now = System.currentTimeMillis()
    }

    val elapsed = t.elapsed(now)
    val target = t.targetMs
    val overwork = if (t.isBreak || Store.settings.breaksEnabled) 0L
    else (elapsed - target).coerceAtLeast(0L)
    val progress = if (target > 0) (elapsed.toFloat() / target).coerceIn(0f, 1f) else 0f
    val overRange = (Store.MAX_SESSION_MS - target).coerceAtLeast(1L)
    val overProgress = (overwork.toFloat() / overRange).coerceIn(0f, 1f)
    val stopped = !t.running && elapsed >= Store.MAX_SESSION_MS

    val ready = t.isBreak || (t.areaId != null && t.projectId != null)
    val accent = if (t.isBreak) 0xFF63636E.toInt()
    else Store.project(t.projectId)?.colorArgb
        ?: Store.area(t.areaId)?.colorArgb
        ?: 0xFFEDEDF0.toInt()


    fun save() {
        val cur = Store.timer
        val banked = cur.elapsed(System.currentTimeMillis())
        if (banked < Store.MIN_SESSION_MS) return
        val ts = System.currentTimeMillis()
        Store.finish(ts)
        now = ts
        Toast.makeText(ctx, "+" + Fmt.exact(banked) + " recorded", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onVerticalDrag = { _, delta -> dy += delta },
                    onDragEnd = {
                        // Everything read fresh here so the very first gesture is not
                        // stale: pointerInput(Unit) captures its closure once.
                        val cur = Store.timer
                        val e = cur.elapsed(System.currentTimeMillis())
                        val threshold = 48.dp.toPx()
                        val hasSomething = e > 0L || cur.accumulatedMs > 0L || cur.running
                        when {
                            // Down.
                            dy > threshold -> when {
                                cur.isBreak -> { Store.endBreak(); now = System.currentTimeMillis() }
                                e >= Store.MIN_SESSION_MS -> save()
                                // Under a minute: a downward swipe resets, no record.
                                hasSomething -> { Store.resetTimer(); now = System.currentTimeMillis() }
                            }
                            // Up: discard with confirmation, any time there is something.
                            dy < -threshold && !cur.isBreak && hasSomething -> {
                                confirmDiscard = true
                            }
                        }
                    }
                )
            }
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))

        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .clickable(enabled = !t.isBreak) { showPicker = true }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (t.isBreak) {
                    Box(Modifier.size(9.dp).background(Color(accent)))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (Store.settings.longBreakEnabled &&
                            Store.settings.cycleCount % Store.settings.sessionsBeforeLongBreak == 0
                        ) "Long break" else "Break",
                        style = MaterialTheme.typography.bodyLarge, color = TextHi
                    )
                } else if (ready) {
                    Box(Modifier.size(9.dp).background(Color(accent)))
                    Spacer(Modifier.width(9.dp))
                    val label = buildString {
                        append(Store.area(t.areaId)?.name ?: "")
                        append("  \u00B7  ")
                        append(Store.project(t.projectId)?.name ?: "")
                        Store.task(t.taskId)?.let { append("  \u00B7  "); append(it.name) }
                    }
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = TextHi)
                } else {
                    Text("Select project", style = MaterialTheme.typography.bodyLarge, color = TextMid)
                }
            }
            Ghost(
                "+",
                { manualOpen = true },
                Modifier.align(Alignment.CenterEnd)
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(288.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { if (!Store.timer.running) showDuration = true },
                        onTap = {
                            val cur = Store.timer
                            val ready = cur.isBreak || (cur.areaId != null && cur.projectId != null)
                            if (!ready) return@detectTapGestures
                            val ts = System.currentTimeMillis()
                            if (cur.running) Store.pause(ts) else Store.start(ts)
                            now = ts
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Line,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                if (progress > 0f) {
                    drawArc(
                        color = Color(accent),
                        startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                // Overwork rides on a thinner inner ring so the two never read as one.
                if (overwork > 0L) {
                    val inner = stroke * 2.2f
                    drawArc(
                        color = TextHi,
                        startAngle = -90f, sweepAngle = 360f * overProgress, useCenter = false,
                        topLeft = Offset(inner, inner),
                        size = Size(size.width - inner * 2, size.height - inner * 2),
                        style = Stroke(width = stroke * 0.5f, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                if (overwork > 0L) Fmt.countdown(overwork) else Fmt.countdown(target - elapsed),
                style = MaterialTheme.typography.displayLarge,
                color = TextHi
            )
        }

        // Fixed-height slot so the circle never moves when the label appears.
        Box(
            Modifier
                .padding(top = 12.dp)
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!t.running && !t.isBreak && elapsed > 0L && elapsed < Store.MAX_SESSION_MS) {
                Text("paused", style = MaterialTheme.typography.bodyMedium, color = Color(accent))
            }
        }

        Spacer(Modifier.height(20.dp))

        if (t.isBreak && !t.running) {
            Ghost("Start break", { Store.startBreak(System.currentTimeMillis()) })
            Spacer(Modifier.height(10.dp))
            Ghost("Skip", { Store.endBreak() })
        } else if (stopped) {
            Text(
                "session stopped at " + Fmt.exact(elapsed),
                style = MaterialTheme.typography.bodyMedium,
                color = Danger
            )
        } else if (overwork > 0L) {
            Text(
                "overwork +" + Fmt.exact(overwork),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Today")
            Spacer(Modifier.weight(1f))
            Text(
                Fmt.minutes(com.example.worktime.data.Stats.today()),
                style = MaterialTheme.typography.titleMedium,
                color = TextHi
            )
        }
    }

    if (manualOpen) {
        SessionDialog(existing = null, onDismiss = { manualOpen = false })
    }

    if (showPicker) {
        SelectionDialog(
            onDismiss = { showPicker = false },
            onPick = { a, p, task ->
                Store.setSelection(a, p, task)
                showPicker = false
            }
        )
    }

    if (showDuration) {
        DurationDialog(
            currentMin = (target / 60000).toInt(),
            onDismiss = { showDuration = false },
            onPick = { m -> Store.setTarget(m); Store.setDefaultDuration(m); showDuration = false }
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard",
            body = "Throw away " + Fmt.exact(elapsed) + " without recording it.",
            confirm = "Discard",
            onDismiss = { confirmDiscard = false },
            onConfirm = { Store.resetTimer(); now = System.currentTimeMillis() }
        )
    }
}
