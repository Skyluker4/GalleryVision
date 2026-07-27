// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.faces

import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** People screen: face clusters with thumbnails, rename, and contact linking. */
@Composable
fun PeopleScreen(
    modifier: Modifier = Modifier,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val clusters by viewModel.clusters.collectAsStateWithLifecycle()
    val reclustering by viewModel.reclustering.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<PeopleViewModel.ClusterUi?>(null) }
    var editText by remember { mutableStateOf("") }

    val pickContact =
        rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
            val cluster = editing ?: return@rememberLauncherForActivityResult
            if (uri != null) {
                val (lookupKey, displayName) = queryContact(context, uri)
                viewModel.linkContact(cluster.info.id, lookupKey)
                if (displayName != null) viewModel.rename(cluster.info.id, displayName)
            }
            editing = null
        }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("People", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { viewModel.recluster() }, enabled = !reclustering) {
                Text(if (reclustering) "Clustering…" else "Recluster")
            }
        }

        if (clusters.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No faces found yet. Faces are detected and clustered in the background.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(clusters, key = { it.info.id }) { cluster ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editText = cluster.info.name ?: ""
                                    editing = cluster
                                },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (cluster.face != null) {
                                Image(
                                    bitmap = cluster.face.asImageBitmap(),
                                    contentDescription = cluster.info.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Text(
                                text = cluster.info.name ?: "Person ${cluster.info.id}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                text = "${cluster.info.memberCount} photos",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { cluster ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(cluster.info.name ?: "Person ${cluster.info.id}") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank()) viewModel.rename(cluster.info.id, editText.trim())
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    cluster.info.name?.let { name ->
                        TextButton(onClick = {
                            viewModel.denyCluster(name)
                            editing = null
                        }) { Text("Deny") }
                    }
                    TextButton(onClick = { pickContact.launch(null) }) { Text("Link contact") }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

private fun queryContact(
    context: android.content.Context,
    uri: Uri,
): Pair<String?, String?> {
    var lookupKey: String? = null
    var displayName: String? = null
    runCatching {
        context.contentResolver
            .query(
                uri,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    lookupKey = cursor.getString(0)
                    displayName = cursor.getString(1)
                }
            }
    }
    return lookupKey to displayName
}
