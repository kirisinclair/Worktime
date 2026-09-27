package com.example.worktime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.worktime.ui.theme.*

@Composable
fun HelpScreen(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("How it works", style = MaterialTheme.typography.headlineMedium, color = TextHi)
            Spacer(Modifier.weight(1f))
            Ghost("Close", onClose)
        }
        Spacer(Modifier.height(20.dp))

        Group("Timer") {
            Line("Start and pause", "Tap the circle to start. Tap again to pause, once more to resume.")
            Line("Save a session", "Swipe down on the circle. Sessions under a minute are not recorded.")
            Line("Discard", "Swipe up on the circle to throw away the current run. Asks first.")
            Line("Session length", "Hold the circle to change it.")
            Line("Project", "Tap the label above the circle to pick one.")
        }

        Group("Breaks") {
            Line("Off", "The timer runs past the target into overwork; a swipe down saves target plus overwork. It stops itself at two hours.")
            Line("On", "The session ends on the target, saves on its own, and a break begins. A swipe down skips the break. Breaks never count as work.")
        }

        Group("Projects") {
            Line("Areas and projects", "An area sets a colour; its projects take shades of it, so each area reads as one colour family.")
            Line("Tasks", "An optional third level inside projects. Turn it on in More.")
            Line("Move", "A project can move to another area. Its sessions follow and it is recoloured to fit.")
            Line("Archive", "Finished areas, projects and tasks can be archived. They leave the pickers but keep counting in the stats.")
        }

        Group("Stats") {
            Line("Overview", "Totals for today, week, month and all time, then a history chart. Tap a bar to break that period down; tap beside the bars for the whole range.")
            Line("Details view", "Switch the breakdown between areas, projects and tasks.")
            Line("Absolute time", "Stretches the chart axis to the whole day, 24 hours per day, so work reads against all available time. A faint block at the top is sleep, eight hours a day; the space between it and the bars is free time.")
            Line("Sessions", "Every session listed. Tap to edit, swipe right to delete. The plus adds one by hand.")
            Line("Archive", "Archived items with their totals. Open one to see its sessions; restore it here.")
        }

        Group("Goal") {
            Line("Daily goal", "Set hours per day in More. A line on the history chart marks the target; week is x5, month x22, quarter x66, year x264.")
        }

        Group("Data") {
            Line("Local only", "Nothing leaves the phone. No account, no internet.")
            Line("Backup", "Uninstalling deletes everything. Backup writes it all to one file; restore reads it back.")
            Line("CSV", "Export for a spreadsheet, or import from another tracker.")
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 22.dp)) {
        Divider()
        Spacer(Modifier.height(16.dp))
        SectionLabel(title)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun Line(head: String, body: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(head, style = MaterialTheme.typography.bodyMedium, color = TextHi)
        Spacer(Modifier.height(3.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = TextMid)
    }
}

