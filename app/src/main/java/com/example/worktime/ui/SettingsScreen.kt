package com.example.worktime.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.worktime.data.Fmt
import com.example.worktime.data.Palette
import com.example.worktime.data.Store
import com.example.worktime.ui.theme.*

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var showDuration by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showBreakLen by remember { mutableStateOf(false) }
    var showLongLen by remember { mutableStateOf(false) }
    var showBeforeLong by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<String, Store.BackupInfo>?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }

    if (importOpen) {
        ImportScreen(onClose = { importOpen = false })
        return
    }

    if (helpOpen) {
        HelpScreen(onClose = { helpOpen = false })
        return
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(Store.exportCsv().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(ctx, "Exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not write the file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(Store.exportJson().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not write the file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreError = null
            try {
                val text = ctx.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: throw Exception("cannot open")
                pendingRestore = text to Store.peekBackup(text)
            } catch (e: Exception) {
                restoreError = e.message ?: "Could not read the file"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextHi)
        Spacer(Modifier.height(20.dp))

        // How it works, a plain tappable row, no card
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { helpOpen = true }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("How it works", style = MaterialTheme.typography.bodyLarge, color = TextHi)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Swipe down on the timer to record a session, swipe up to throw it away.",
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("More", style = MaterialTheme.typography.bodyMedium, color = accent())
        }

        Divider()

        // Block 2: appearance
        ValueDotRow("Accent color", Store.settings.accentArgb) { showAccent = true }
        Toggle(
            title = "Dark theme",
            subtitle = null,
            checked = Store.settings.darkTheme
        ) { Store.setDarkTheme(it) }
        Toggle(
            title = "Third level: tasks",
            subtitle = "Tasks inside projects",
            checked = Store.settings.tasksEnabled
        ) { Store.setTasksEnabled(it) }

        Divider()

        // Block 3: goal, then duration, then breaks and everything breaks reveals
        Toggle(
            title = "Daily goal",
            subtitle = "Marks a line on the history chart. Week is x5, month x22, quarter x66, year x264",
            checked = Store.settings.goalEnabled
        ) { Store.setGoal(enabled = it) }

        if (Store.settings.goalEnabled) {
            ValueRow(
                "Hours per day",
                Fmt.minutes(Store.settings.goalDailyMin * 60_000L)
            ) { showGoal = true }
        }

        ValueRow("Default duration", "${Store.settings.defaultDurationMin} min") {
            showDuration = true
        }
        Toggle(
            title = "Breaks",
            subtitle = null,
            checked = Store.settings.breaksEnabled
        ) { Store.setBreaks(enabled = it) }

        if (Store.settings.breaksEnabled) {
            ValueRow("Break length", "" + Store.settings.breakMin + " min") { showBreakLen = true }
            Toggle(
                title = "Start break automatically",
                subtitle = "Otherwise the break waits for a tap",
                checked = Store.settings.autoStartBreak
            ) { Store.setBreaks(autoStart = it) }
            Toggle(
                title = "Long break",
                subtitle = "A longer rest every few sessions",
                checked = Store.settings.longBreakEnabled
            ) { Store.setBreaks(longEnabled = it) }
            if (Store.settings.longBreakEnabled) {
                ValueRow("Long break length", "" + Store.settings.longBreakMin + " min") {
                    showLongLen = true
                }
                ValueRow("After", "" + Store.settings.sessionsBeforeLongBreak + " sessions") {
                    showBeforeLong = true
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Divider()
        Spacer(Modifier.height(18.dp))
        SectionLabel("Data")
        Spacer(Modifier.height(4.dp))

        LinkRow("Backup to file") {
            val name = "worktime-backup-" + Fmt.isoDate(System.currentTimeMillis()) + ".json"
            backupSaver.launch(name)
        }
        LinkRow("Restore from backup") { backupPicker.launch(arrayOf("*/*")) }
        LinkRow("Export CSV") {
            val name = "worktime_" + Fmt.isoDate(System.currentTimeMillis()) + ".csv"
            exporter.launch(name)
        }
        LinkRow("Import CSV from another tracker") { importOpen = true }

        restoreError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(22.dp))
        Divider()
        Spacer(Modifier.height(18.dp))
        SectionLabel("About")
        Spacer(Modifier.height(4.dp))

        LinkRow("Privacy policy") { /* TODO: open privacy URL */ }
        LinkRow("Website") { /* TODO: open site URL */ }
        LinkRow("Contact support") { /* TODO: open mailto */ }

        Spacer(Modifier.height(40.dp))
    }

    pendingRestore?.let { pair ->
        val info = pair.second
        ConfirmDialog(
            title = "Restore backup",
            body = "" + info.sessions + " sessions, " + info.projects + " projects, " +
                    info.areas + " areas" +
                    (if (info.exportedAt > 0L) ", saved " + Fmt.isoDate(info.exportedAt) else "") +
                    ". Everything currently in the app is replaced, including " +
                    Store.sessions.size + " sessions you have now.",
            confirm = "Replace",
            onDismiss = { pendingRestore = null },
            onConfirm = {
                try {
                    Store.importBackup(pair.first)
                    Toast.makeText(ctx, "Restored", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Restore failed", Toast.LENGTH_LONG).show()
                }
                pendingRestore = null
            }
        )
    }

    if (showBreakLen) {
        DurationDialog(Store.settings.breakMin, { showBreakLen = false }) {
            Store.setBreaks(breakMin = it); showBreakLen = false
        }
    }
    if (showLongLen) {
        DurationDialog(Store.settings.longBreakMin, { showLongLen = false }) {
            Store.setBreaks(longMin = it); showLongLen = false
        }
    }
    if (showGoal) {
        var custom by remember {
            mutableStateOf((Store.settings.goalDailyMin / 60.0).let {
                if (it == it.toLong().toDouble()) it.toLong().toString()
                else it.toString()
            })
        }
        Sheet({ showGoal = false }) {
            SectionLabel("Hours per day")
            Spacer(Modifier.height(14.dp))
            FlowRowSimple(listOf(3, 4, 5, 6, 7, 8)) { h ->
                Ghost(h.toString() + "h", { Store.setGoal(dailyMin = h * 60); showGoal = false })
            }
            Spacer(Modifier.height(16.dp))
            Field(
                custom,
                { custom = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(4) },
                "custom, e.g. 7.5",
                numeric = true
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Filled("OK", {
                    val hrs = custom.replace(',', '.').toDoubleOrNull()
                    if (hrs != null) {
                        Store.setGoal(dailyMin = (hrs * 60).toInt())
                        showGoal = false
                    }
                })
                Ghost("Cancel", { showGoal = false })
            }
        }
    }

    if (showBeforeLong) {
        Sheet({ showBeforeLong = false }) {
            SectionLabel("Long break after")
            Spacer(Modifier.height(14.dp))
            FlowRowSimple(listOf(2, 3, 4, 5, 6, 8)) { n ->
                Ghost("" + n, { Store.setBreaks(beforeLong = n); showBeforeLong = false })
            }
            Spacer(Modifier.height(14.dp))
            Ghost("Cancel", { showBeforeLong = false })
        }
    }

    if (showAccent) {
        val choices = Palette.accents
        Sheet({ showAccent = false }) {
            SectionLabel("Accent color")
            Spacer(Modifier.height(12.dp))
            ColorGrid(choices, Store.settings.accentArgb, columns = 5) {
                Store.setAccent(it)
                showAccent = false
            }
            Spacer(Modifier.height(16.dp))
            Ghost("Cancel", { showAccent = false })
        }
    }

    if (showDuration) {
        DurationDialog(
            currentMin = Store.settings.defaultDurationMin,
            onDismiss = { showDuration = false },
            onPick = { m -> Store.setDefaultDuration(m); showDuration = false }
        )
    }
}

@Composable
private fun Toggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHi)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLow)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg,
                checkedTrackColor = TextHi,
                uncheckedThumbColor = TextLow,
                uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Line
            )
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHi)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = TextMid)
    }
}

@Composable
private fun ValueDotRow(title: String, argb: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHi)
        Spacer(Modifier.weight(1f))
        Dot(argb, 16)
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHi)
    }
}
