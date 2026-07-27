// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lukesimmons.galleryvision.core.model.DenyKind

/** Settings: mpv.conf override, custom OCR dictionary, and the four deny lists. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val mpvConfig by viewModel.mpvConfig.collectAsStateWithLifecycle()
    val dictionary by viewModel.dictionary.collectAsStateWithLifecycle()
    val denyWords by viewModel.denyWords.collectAsStateWithLifecycle()
    val denyObjects by viewModel.denyObjects.collectAsStateWithLifecycle()
    val denyTags by viewModel.denyTags.collectAsStateWithLifecycle()
    val denyFaces by viewModel.denyFaces.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        }

        item {
            MpvConfigSection(
                saved = mpvConfig,
                onSave = viewModel::saveMpvConfig,
            )
        }

        item { HorizontalDivider() }
        item {
            Text("Custom dictionary", style = MaterialTheme.typography.titleMedium)
            Text(
                "OCR results snap to these words when close enough.",
                style = MaterialTheme.typography.labelMedium,
            )
            WordListEditor(
                words = dictionary,
                hint = "Add dictionary word",
                onAdd = viewModel::addDictionaryWord,
                onRemove = viewModel::removeDictionaryWord,
            )
        }

        item { HorizontalDivider() }
        item {
            Text("Deny lists", style = MaterialTheme.typography.titleMedium)
            Text(
                "Denied entries are hidden from detection displays and search.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        item {
            DenySection("Words", denyWords, "Add denied word") { value, remove ->
                if (remove) viewModel.removeDeny(DenyKind.WORD, value) else viewModel.addDeny(DenyKind.WORD, value)
            }
        }
        item {
            DenySection("Objects", denyObjects, "Add denied object") { value, remove ->
                if (remove) viewModel.removeDeny(DenyKind.OBJECT, value) else viewModel.addDeny(DenyKind.OBJECT, value)
            }
        }
        item {
            DenySection("Tags", denyTags, "Add denied tag") { value, remove ->
                if (remove) viewModel.removeDeny(DenyKind.TAG, value) else viewModel.addDeny(DenyKind.TAG, value)
            }
        }
        item {
            DenySection("Faces", denyFaces, "Add denied face name") { value, remove ->
                if (remove) viewModel.removeDeny(DenyKind.FACE, value) else viewModel.addDeny(DenyKind.FACE, value)
            }
        }
    }
}

@Composable
private fun MpvConfigSection(
    saved: String,
    onSave: (String) -> Unit,
) {
    var draft by remember { mutableStateOf<String?>(null) }
    Column {
        Text("Video player (mpv.conf)", style = MaterialTheme.typography.titleMedium)
        Text(
            "One key=value per line, applied when a video opens. Example: hwdec=auto",
            style = MaterialTheme.typography.labelMedium,
        )
        OutlinedTextField(
            value = draft ?: saved,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            minLines = 3,
            maxLines = 8,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (draft != null && draft != saved) {
                TextButton(onClick = { draft = null }) { Text("Discard") }
                Button(onClick = {
                    onSave(draft ?: "")
                    draft = null
                }) { Text("Save") }
            } else {
                Text(if (saved.isBlank()) "No overrides" else "Saved", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WordListEditor(
    words: List<String>,
    hint: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column {
        words.forEach { word ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onRemove(word) }) { Text("Remove") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(hint) },
                singleLine = true,
            )
            TextButton(
                onClick = {
                    onAdd(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
            ) { Text("Add") }
        }
    }
}

@Composable
private fun DenySection(
    title: String,
    values: List<String>,
    hint: String,
    onChange: (value: String, remove: Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        WordListEditor(
            words = values,
            hint = hint,
            onAdd = { onChange(it, false) },
            onRemove = { onChange(it, true) },
        )
    }
}
