package com.example.worktime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.worktime.data.Palette
import com.example.worktime.data.Store
import com.example.worktime.ui.theme.*

@Composable
fun Dot(argb: Int, size: Int = 10, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .background(Color(argb))
    )
}

@Composable
fun Ghost(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .background(Surface2)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (enabled) TextHi else TextLow)
    }
}

/** The accent the user picked in More. */
@Composable
fun accent(): Color = Color(Store.settings.accentArgb)

@Composable
fun Filled(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = Store.settings.accentArgb
    Box(
        modifier
            .clip(RoundedCornerShape(0.dp))
            .background(Color(bg))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(Palette.onColor(bg))
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = accent(),
        modifier = modifier
    )
}

@Composable
fun Field(value: String, onValue: (String) -> Unit, placeholder: String = "", numeric: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(Surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TextLow)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextHi),
            cursorBrush = SolidColor(TextHi),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .background(Surface1)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

@Composable
fun ColorGrid(colors: List<Int>, selected: Int?, columns: Int = 7, onPick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.heightIn(max = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(colors) { c ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .background(Color(c))
                    .then(
                        if (c == selected) Modifier.border(2.dp, TextHi, RoundedCornerShape(0.dp))
                        else Modifier
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

/**
 * Flat one-tap list: every area with its projects underneath, and tasks too when
 * the third level is on. No drilling down.
 */
@Composable
fun SelectionDialog(
    onDismiss: () -> Unit,
    onPick: (areaId: String, projectId: String, taskId: String?) -> Unit
) {
    val tasksEnabled = Store.settings.tasksEnabled
    val areas = Store.areas.filter { !it.archived }.sortedBy { it.order }

    Sheet(onDismiss) {
        if (areas.isEmpty()) {
            Text(
                "Create an area on the Projects tab first",
                style = MaterialTheme.typography.bodyMedium, color = TextMid
            )
            return@Sheet
        }
        areas.forEachIndexed { ai, a ->
            if (ai > 0) Spacer(Modifier.height(16.dp))
            SectionLabel(a.name)
            Spacer(Modifier.height(6.dp))
            val ps = Store.projectsOf(a.id).filter { !it.archived }
            if (ps.isEmpty()) {
                Text(
                    "no projects",
                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            ps.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(a.id, p.id, null) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Dot(p.colorArgb)
                    Spacer(Modifier.width(12.dp))
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, color = TextHi)
                }
                if (tasksEnabled) {
                    Store.tasksOf(p.id).filter { !it.archived }.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(a.id, p.id, t.id) }
                                .padding(start = 24.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\u00B7", color = TextLow)
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DurationDialog(currentMin: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var custom by remember { mutableStateOf(currentMin.toString()) }
    Sheet(onDismiss) {
        SectionLabel("Duration, minutes")
        Spacer(Modifier.height(14.dp))
        FlowRowSimple(listOf(15, 25, 30, 45, 50, 60, 90, 120)) { m ->
            Ghost(m.toString(), { onPick(m) })
        }
        Spacer(Modifier.height(16.dp))
        Field(custom, { custom = it.filter { c -> c.isDigit() }.take(3) }, "custom", numeric = true)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Filled("OK", { custom.toIntOrNull()?.let(onPick) })
            Ghost("Cancel", onDismiss)
        }
    }
}

/** Minimal wrapping row so no experimental FlowRow is needed. */
@Composable
fun <T> FlowRowSimple(items: List<T>, perRow: Int = 4, item: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item(it) }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Sheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMid,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledWide(confirm, { onConfirm(); onDismiss() }, Modifier.weight(1f))
            GhostWide("Cancel", onDismiss, Modifier.weight(1f))
        }
    }
}

@Composable
fun ChipLabel(text: String, argb: Int, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .clip(RoundedCornerShape(0.dp))
            .background(Color(argb).copy(alpha = 0.18f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(argb, 10)
            Spacer(Modifier.width(9.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = TextHi)
        }
    }
}


@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line)
    )
}

/**
 * Full-width pill selector. The active segment is filled with the accent colour,
 * its label flipping to black or white depending on how bright that colour is.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val acc = Store.settings.accentArgb
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(Surface2)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, name ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .background(if (active) Color(acc) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) Color(Palette.onColor(acc)) else TextMid,
                    maxLines = 1
                )
            }
        }
    }
}

/** Left label in small caps, optional strong value on the right. */
@Composable
fun HeadRow(label: String, value: String? = null, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(label)
        if (sub != null) {
            Spacer(Modifier.width(8.dp))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = TextLow)
        }
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextHi)
        }
    }
}

/** Small pencil, drawn so no icon dependency is needed. */
@Composable
fun EditIcon(tint: Color = TextLow, size: Int = 18, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size.dp)) {
        val w = this.size.width
        val sw = w * 0.09f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, androidx.compose.ui.geometry.Offset(w * x1, w * y1),
                androidx.compose.ui.geometry.Offset(w * x2, w * y2), sw,
                androidx.compose.ui.graphics.StrokeCap.Round)
        // pencil body
        line(0.24f, 0.76f, 0.72f, 0.28f)
        line(0.38f, 0.90f, 0.86f, 0.42f)
        line(0.24f, 0.76f, 0.38f, 0.90f)   // nib base
        line(0.72f, 0.28f, 0.86f, 0.42f)   // eraser end
        // tip
        line(0.18f, 0.82f, 0.30f, 0.84f)
    }
}

/** Archive box: a tray with a lid. */
@Composable
fun ArchiveIcon(tint: Color = TextLow, size: Int = 18, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size.dp)) {
        val w = this.size.width
        val sw = w * 0.09f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, androidx.compose.ui.geometry.Offset(w * x1, w * y1),
                androidx.compose.ui.geometry.Offset(w * x2, w * y2), sw,
                androidx.compose.ui.graphics.StrokeCap.Round)
        // lid
        line(0.14f, 0.26f, 0.86f, 0.26f)
        // body
        line(0.20f, 0.30f, 0.24f, 0.82f)
        line(0.80f, 0.30f, 0.76f, 0.82f)
        line(0.24f, 0.82f, 0.76f, 0.82f)
        // handle slot
        line(0.42f, 0.46f, 0.58f, 0.46f)
    }
}

/** Trash can, shared bin icon. */
@Composable
fun DeleteIcon(tint: Color = TextLow, size: Int = 18, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size.dp)) {
        val w = this.size.width
        val sw = w * 0.09f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, androidx.compose.ui.geometry.Offset(w * x1, w * y1),
                androidx.compose.ui.geometry.Offset(w * x2, w * y2), sw,
                androidx.compose.ui.graphics.StrokeCap.Round)
        line(0.14f, 0.26f, 0.86f, 0.26f)   // lid
        line(0.40f, 0.26f, 0.42f, 0.14f)   // handle
        line(0.60f, 0.26f, 0.58f, 0.14f)
        line(0.42f, 0.14f, 0.58f, 0.14f)
        line(0.24f, 0.30f, 0.30f, 0.84f)   // body
        line(0.76f, 0.30f, 0.70f, 0.84f)
        line(0.30f, 0.84f, 0.70f, 0.84f)
    }
}

@Composable
fun FilledWide(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = Store.settings.accentArgb
    Box(
        modifier
            .background(Color(bg))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(Palette.onColor(bg)))
    }
}

@Composable
fun GhostWide(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Surface2)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextHi)
    }
}

/** Compact custom switch, accent when on. */
@Composable
fun SmallSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val acc = Store.settings.accentArgb
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val trackH = size.height
            val r = trackH / 2f
            drawRoundRect(
                color = if (checked) Color(acc) else Surface2,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
            val knobR = r - 3.dp.toPx()
            val cx = if (checked) size.width - r else r
            drawCircle(
                color = if (checked) Color(Palette.onColor(acc)) else TextLow,
                radius = knobR,
                center = androidx.compose.ui.geometry.Offset(cx, size.height / 2f)
            )
        }
    }
}
