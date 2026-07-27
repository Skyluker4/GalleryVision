// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.lukesimmons.galleryvision.core.model.FolderPolicyMode

/** Folder list with per-folder visibility policy and the global allow-list-only switch. */
@Composable
fun FoldersScreen(
    onFolderClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoldersViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val policies by viewModel.policies.collectAsStateWithLifecycle()
    val allowListOnly by viewModel.allowListOnly.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Folders", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (allowListOnly) "Showing only allowed folders" else "Hiding denied folders",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text("Allow-list only", style = MaterialTheme.typography.labelMedium)
            Switch(checked = allowListOnly, onCheckedChange = viewModel::setAllowListOnly)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(folders, key = { it.id }) { folder ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onFolderClick(folder.id) }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            folder.path.substringAfterLast('/').ifEmpty { folder.path },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${folder.mediaCount} items · ${folder.path}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    PolicyMenu(
                        mode = policies[folder.id],
                        onSelect = { viewModel.setPolicy(folder.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyMenu(
    mode: FolderPolicyMode?,
    onSelect: (FolderPolicyMode?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(
                when (mode) {
                    FolderPolicyMode.DENY -> "Denied"
                    FolderPolicyMode.ALLOW -> "Allowed"
                    null -> "Default"
                },
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Default") }, onClick = {
                onSelect(null)
                open = false
            })
            DropdownMenuItem(
                text = { Text("Deny (hide)") },
                onClick = {
                    onSelect(FolderPolicyMode.DENY)
                    open = false
                },
            )
            DropdownMenuItem(
                text = { Text("Allow (keep in allow-list)") },
                onClick = {
                    onSelect(FolderPolicyMode.ALLOW)
                    open = false
                },
            )
        }
    }
}
