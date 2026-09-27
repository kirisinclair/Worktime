package com.example.worktime.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.worktime.data.CsvImport
import com.example.worktime.data.Fmt
import com.example.worktime.data.Store
import com.example.worktime.ui.theme.*

@Composable
fun ImportScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var preview by remember { mutableStateOf<CsvImport.Preview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = null
        try {
            val text = ctx.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: throw Exception("cannot open")
            preview = CsvImport.parse(text)
        } catch (e: Exception) {
            error = e.message ?: "Could not read the file"
            preview = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Import CSV", style = MaterialTheme.typography.headlineMedium, color = TextHi)
            Spacer(Modifier.weight(1f))
            Ghost("Close", onClose)
        }
        Spacer(Modifier.height(16.dp))

        val p = preview
        if (p == null) {
            Text(
                "Most time trackers can export a CSV. Column order does not matter and the " +
                        "header is matched automatically.",
                style = MaterialTheme.typography.bodyMedium, color = TextMid
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SectionLabel("Recognised columns")
                Spacer(Modifier.height(10.dp))
                listOf(
                    "project, label, tag, activity" to "required",
                    "duration, minutes, hours, time" to "required unless start and end are present",
                    "date, start date, day" to "today if missing",
                    "start, end, from, to" to "optional",
                    "area, client, group, category" to "Imported if missing",
                    "task, description, notes" to "optional"
                ).forEach { (names, note) ->
                    Text(names, style = MaterialTheme.typography.bodyMedium, color = TextHi)
                    Text(note, style = MaterialTheme.typography.bodySmall, color = TextLow)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Durations may be 90, 1.5, 1:30 or 01:30:00. A bare number is read as minutes.",
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
            Spacer(Modifier.height(16.dp))
            Ghost("Choose CSV file", { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text(
                "" + p.rows.size + " entries, " + Fmt.minutes(p.totalMs) + " total" +
                        (if (p.skipped > 0) ", " + p.skipped + " rows skipped" else ""),
                style = MaterialTheme.typography.bodyLarge, color = TextHi
            )
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SectionLabel("Areas to create or reuse")
                Spacer(Modifier.height(8.dp))
                Text(p.areas.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium, color = TextHi)
                Spacer(Modifier.height(14.dp))
                SectionLabel("Projects")
                Spacer(Modifier.height(8.dp))
                Text(p.projects.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium, color = TextHi)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Existing areas and projects with the same name are reused. Re-importing the " +
                        "same file adds nothing, duplicates are detected.",
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Filled("Import", {
                    val added = CsvImport.apply(p, Store.settings.tasksEnabled)
                    Toast.makeText(
                        ctx,
                        "Imported " + added + ", skipped " + (p.rows.size - added) + " duplicates",
                        Toast.LENGTH_LONG
                    ).show()
                    onClose()
                })
                Ghost("Back", { preview = null })
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
