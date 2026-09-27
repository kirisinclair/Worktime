package com.example.worktime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.worktime.data.Area
import com.example.worktime.data.Palette
import com.example.worktime.data.Project
import com.example.worktime.data.Store
import com.example.worktime.ui.theme.*

@Composable
fun ProjectsScreen() {
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editArea by remember { mutableStateOf<Area?>(null) }
    var newAreaOpen by remember { mutableStateOf(false) }
    var editProject by remember { mutableStateOf<Project?>(null) }
    var newProjectFor by remember { mutableStateOf<Area?>(null) }
    var newTaskFor by remember { mutableStateOf<Project?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Projects", style = MaterialTheme.typography.headlineMedium, color = TextHi)
            Spacer(Modifier.weight(1f))
            Ghost("+ area", { newAreaOpen = true })
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Store.areas.sortedBy { it.order }, key = { it.id }) { a ->
                val open = a.id in expanded
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(0.dp))
                        .background(Surface2)
                        .padding(14.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = if (open) expanded - a.id else expanded + a.id
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Dot(a.colorArgb, 12)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            a.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (a.archived) TextLow else TextHi
                        )
                        if (a.archived) {
                            Spacer(Modifier.width(8.dp))
                            Text("archived", style = MaterialTheme.typography.bodySmall, color = TextLow)
                        }
                        Spacer(Modifier.weight(1f))
                        EditIcon(
                            modifier = Modifier
                                .clickable { editArea = a }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (open) "−" else "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMid
                        )
                    }

                    if (open) {
                        Spacer(Modifier.height(10.dp))
                        Store.projectsOf(a.id).forEach { p ->
                            Column(Modifier.padding(start = 24.dp)) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { editProject = p }
                                        .padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Dot(p.colorArgb, 10)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (p.archived) TextLow else TextHi
                                    )
                                    Spacer(Modifier.weight(1f))
                                    EditIcon(
                                        modifier = Modifier
                                            .clickable { editProject = p }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (Store.settings.tasksEnabled) {
                                    Store.tasksOf(p.id).filter { !it.archived }.forEach { tk ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 22.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("·", color = TextLow)
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                tk.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextMid,
                                                modifier = Modifier.weight(1f)
                                            )
                                            ArchiveIcon(
                                                size = 16,
                                                modifier = Modifier
                                                    .clickable { Store.updateTask(tk.id, archived = true) }
                                                    .padding(6.dp)
                                            )
                                            DeleteIcon(
                                                size = 16,
                                                modifier = Modifier
                                                    .clickable { Store.deleteTask(tk.id) }
                                                    .padding(6.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "+ task",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextLow,
                                        modifier = Modifier
                                            .padding(start = 22.dp)
                                            .clickable { newTaskFor = p }
                                            .padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Ghost("+ project", { newProjectFor = a })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (newAreaOpen) {
        AreaDialog(null, { newAreaOpen = false })
    }
    editArea?.let { a ->
        AreaDialog(a, { editArea = null })
    }
    newProjectFor?.let { a ->
        ProjectDialog(a, null, { newProjectFor = null })
    }
    editProject?.let { p ->
        val a = Store.area(p.areaId)
        if (a != null) ProjectDialog(a, p, { editProject = null }) else editProject = null
    }
    newTaskFor?.let { p ->
        var name by remember(p.id) { mutableStateOf("") }
        Sheet({ newTaskFor = null }) {
            SectionLabel("New task")
            Spacer(Modifier.height(12.dp))
            Field(name, { name = it }, "name")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Filled("Create", {
                    if (name.isNotBlank()) Store.addTask(p.id, name.trim())
                    newTaskFor = null
                })
                Ghost("Cancel", { newTaskFor = null })
            }
        }
    }
}

@Composable
private fun AreaDialog(existing: Area?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.colorArgb ?: Palette.base[0]) }
    var confirmDelete by remember { mutableStateOf(false) }

    Sheet(onDismiss) {
        SectionLabel(if (existing == null) "New area" else "Area")
        Spacer(Modifier.height(12.dp))
        Field(name, { name = it }, "name")
        Spacer(Modifier.height(18.dp))
        SectionLabel("Color")
        Spacer(Modifier.height(10.dp))
        ColorGrid(Palette.base, color) { color = it }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Filled(if (existing == null) "Create" else "Save", {
                if (name.isNotBlank()) {
                    if (existing == null) Store.addArea(name.trim(), color)
                    else Store.updateArea(existing.id, name.trim(), color)
                }
                onDismiss()
            })
            Ghost("Cancel", onDismiss)
        }
        if (existing != null) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ghost(if (existing.archived) "Unarchive" else "Archive", {
                    Store.updateArea(existing.id, archived = !existing.archived)
                    onDismiss()
                })
                Ghost("Delete", { confirmDelete = true })
            }
        }
    }

    if (confirmDelete && existing != null) {
        val n = Store.sessions.count { it.areaId == existing.id }
        ConfirmDialog(
            title = "Delete area",
            body = "Its projects, tasks and $n sessions will be removed too. This cannot be undone.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { Store.deleteArea(existing.id); onDismiss() }
        )
    }
}

@Composable
private fun ProjectDialog(area: Area, existing: Project?, onDismiss: () -> Unit) {
    val shades = remember(area.colorArgb) { Palette.shadesArgb(area.colorArgb) }
    val used = remember(existing?.id) {
        Store.projectsOf(area.id).filter { it.id != existing?.id }.map { it.colorArgb }
    }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember {
        mutableStateOf(existing?.colorArgb ?: Palette.nextFreeShade(area.colorArgb, used))
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    Sheet(onDismiss) {
        SectionLabel(area.name)
        Spacer(Modifier.height(12.dp))
        Field(name, { name = it }, "project name")
        Spacer(Modifier.height(18.dp))
        SectionLabel("Shade")
        Spacer(Modifier.height(10.dp))
        ColorGrid(shades, color, columns = 10) { color = it }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Filled(if (existing == null) "Create" else "Save", {
                if (name.isNotBlank()) {
                    if (existing == null) Store.addProject(area.id, name.trim(), color)
                    else Store.updateProject(existing.id, name.trim(), color)
                }
                onDismiss()
            })
            Ghost("Cancel", onDismiss)
        }
        if (existing != null) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ghost(if (existing.archived) "Unarchive" else "Archive", {
                    Store.updateProject(existing.id, archived = !existing.archived)
                    onDismiss()
                })
                Ghost("Delete", { confirmDelete = true })
            }
            val others = Store.areas.filter { it.id != area.id && !it.archived }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Ghost("Move to another area", { moveOpen = true }, Modifier.fillMaxWidth())
            }
        }
    }

    if (moveOpen && existing != null) {
        Sheet({ moveOpen = false }) {
            SectionLabel("Move " + existing.name + " to")
            Spacer(Modifier.height(10.dp))
            Text(
                "Its sessions move with it and it gets a shade of the new area's colour.",
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
            Spacer(Modifier.height(12.dp))
            Store.areas.filter { it.id != area.id && !it.archived }.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Store.moveProject(existing.id, a.id)
                            moveOpen = false
                            onDismiss()
                        }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Dot(a.colorArgb)
                    Spacer(Modifier.width(12.dp))
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, color = TextHi)
                }
            }
            Spacer(Modifier.height(10.dp))
            Ghost("Cancel", { moveOpen = false })
        }
    }

    if (confirmDelete && existing != null) {
        val n = Store.sessions.count { it.projectId == existing.id }
        ConfirmDialog(
            title = "Delete project",
            body = "Its tasks and $n sessions will be removed too.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { Store.deleteProject(existing.id); onDismiss() }
        )
    }
}
