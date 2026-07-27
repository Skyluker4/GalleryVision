// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.model.SearchField
import com.lukesimmons.galleryvision.core.model.SortDirection
import com.lukesimmons.galleryvision.core.model.SortSpec

/** Media grid with a search bar (R8 boolean/wildcard/regex/field search) and sort control. */
@Composable
fun LibraryScreen(
    onMediaClick: (MediaEntity) -> Unit,
    onPeopleClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val count by viewModel.mediaCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.rescan() }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            onSearch = viewModel::search,
            onClear = viewModel::clearSearch,
            sort = sort,
            onSort = viewModel::setSort,
            onPeopleClick = onPeopleClick,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                searching -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                results != null -> {
                    val list = results!!
                    if (list.isEmpty()) {
                        Text(
                            text = "No matches for \"$query\"",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        MediaGrid(list, onMediaClick)
                        Text(
                            text = "${list.size} matches",
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                items.itemCount == 0 && items.loadState.refresh is androidx.paging.LoadState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                items.itemCount == 0 -> {
                    Text(
                        text = "No media yet. Grant access and rescan.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items.itemCount) { index ->
                            val media = items[index]
                            if (media != null) {
                                MediaCard(media, onMediaClick)
                            }
                        }
                    }
                    Text(
                        text = "$count items",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    sort: SortSpec?,
    onSort: (SortSpec) -> Unit,
    onPeopleClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search: path:name* taken:>2024-01-01 tag:x AND NOT …") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            trailingIcon = {
                if (query.isNotBlank()) {
                    TextButton(onClick = onClear) { Text("X") }
                }
            },
        )
        SortMenu(sort, onSort)
        TextButton(onClick = onPeopleClick) { Text("People") }
    }
}

@Composable
private fun SortMenu(sort: SortSpec?, onSort: (SortSpec) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(sortLabel(sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sortOptions().forEach { (label, spec) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSort(spec)
                        open = false
                    },
                )
            }
        }
    }
}

private fun sortOptions(): List<Pair<String, SortSpec>> = listOf(
    "Newest" to SortSpec(SearchField.TAKEN, SortDirection.DESC),
    "Oldest" to SortSpec(SearchField.TAKEN, SortDirection.ASC),
    "Name A-Z" to SortSpec(SearchField.PATH, SortDirection.ASC),
    "Name Z-A" to SortSpec(SearchField.PATH, SortDirection.DESC),
    "Added (new)" to SortSpec(SearchField.ADDED, SortDirection.DESC),
    "Modified (new)" to SortSpec(SearchField.MODIFIED, SortDirection.DESC),
)

private fun sortLabel(sort: SortSpec?): String =
    if (sort == null) "Sort" else sortOptions().firstOrNull { it.second == sort }?.first ?: "Sort"

@Composable
private fun MediaGrid(media: List<MediaEntity>, onMediaClick: (MediaEntity) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(media, key = { it.id }) { item ->
            MediaCard(item, onMediaClick)
        }
    }
}

@Composable
private fun MediaCard(media: MediaEntity, onMediaClick: (MediaEntity) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onMediaClick(media) },
    ) {
        AsyncImage(
            model = media.sourceUri,
            contentDescription = media.path,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
