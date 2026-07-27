// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukesimmons.galleryvision.core.database.entity.NoteEntity

/** Flattened (note, depth) pairs in thread order. Orphans re-attach at the root. */
fun threadNotes(notes: List<NoteEntity>): List<Pair<NoteEntity, Int>> {
    val byParent = notes.groupBy { it.parentNoteId }
    val out = mutableListOf<Pair<NoteEntity, Int>>()

    fun walk(parentId: Long?, depth: Int) {
        byParent[parentId].orEmpty().sortedBy { it.id }.forEach { note ->
            out += note to depth
            walk(note.id, depth + 1)
        }
    }
    walk(null, 0)

    val seen = out.mapTo(HashSet()) { it.first.id }
    notes.filter { it.id !in seen }.sortedBy { it.id }.forEach { out += it to 0 }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesSheet(
    notes: List<NoteEntity>,
    onAdd: (body: String, parentNoteId: Long?) -> Unit,
    onDelete: (noteId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<NoteEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Notes", style = MaterialTheme.typography.titleMedium)

            val threaded = threadNotes(notes)
            if (threaded.isEmpty()) {
                Text(
                    "No notes yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    items(threaded, key = { it.first.id }) { (note, depth) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (depth * 16).dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                note.body,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { replyTo = note }) { Text("Reply") }
                            TextButton(onClick = { onDelete(note.id) }) { Text("Delete") }
                        }
                    }
                }
            }

            replyTo?.let { parent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Replying to: ${parent.body}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { replyTo = null }) { Text("X") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a note…") },
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        onAdd(draft, replyTo?.id)
                        draft = ""
                        replyTo = null
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Add") }
            }
        }
    }
}
