package com.yenaly.han1meviewer.MissAV.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.MissAV.common.MissAvGroup
import com.yenaly.han1meviewer.MissAV.common.MissAvOptions
import com.yenaly.han1meviewer.MissAV.ui.components.MissAvGenreSheet
import com.yenaly.han1meviewer.MissAV.ui.components.MissAvSortFilterSheet
import com.yenaly.han1meviewer.MissAV.viewmodel.MissAvViewModel
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import kotlinx.coroutines.launch

private data class ActiveChip(
    val label: String,
    val onClear: () -> Unit,
)

@Composable
fun MissAvSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    viewModel: MissAvViewModel,
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val searchState by viewModel.searchFlow.collectAsStateWithLifecycle()
    val allVideos by viewModel.searchResults.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val hasMorePages by viewModel.hasMorePages.collectAsStateWithLifecycle()

    var showSortFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showGenreSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var recentHistory by remember { mutableStateOf(MissAvSearchHistoryRepo.load()) }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val refreshState = rememberPullToRefreshState()

    val isBrowsing = searchQuery.isBlank()

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNullOrEmpty()) return@LaunchedEffect
        if (viewModel.searchQuery.value == initialQuery && hasSearched) return@LaunchedEffect
        viewModel.setSearchQuery(initialQuery)
        MissAvSearchHistoryRepo.push(
            MissAvSearchHistoryEntry(query = initialQuery.trim())
        )
        recentHistory = MissAvSearchHistoryRepo.load()
        viewModel.performSearch(resetPage = true)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= total - 4 && total > 4 && hasMorePages && !isLoadingMore &&
                    hasSearched && allVideos.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    fun persistCurrentHistory() {
        MissAvSearchHistoryRepo.push(
            MissAvSearchHistoryEntry(
                query = viewModel.searchQuery.value.trim(),
                sort = viewModel.selectedSort.value,
                filter = viewModel.selectedFilter.value,
                genre = viewModel.selectedGenre.value,
            )
        )
        recentHistory = MissAvSearchHistoryRepo.load()
    }

    fun runSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        coroutineScope.launch { gridState.scrollToItem(0) }
        viewModel.performSearch(resetPage = true)
        persistCurrentHistory()
    }

    if (showSortFilterSheet) {
        MissAvSortFilterSheet(
            initialSort = selectedSort,
            initialFilter = selectedFilter,
            onDismiss = { showSortFilterSheet = false },
            onApply = { sort, filter ->
                viewModel.setSort(sort)
                viewModel.setFilter(filter)
                showSortFilterSheet = false
                runSearch()
            },
            onReset = {
                viewModel.setSort(null)
                viewModel.setFilter(null)
            },
        )
    }

    if (showGenreSheet) {
        MissAvGenreSheet(
            initialGenre = selectedGenre,
            onDismiss = { showGenreSheet = false },
            onApply = { genre ->
                viewModel.setGenre(genre)
                showGenreSheet = false
                runSearch()
            },
            onReset = { viewModel.setGenre(null) },
        )
    }

    if (showHistorySheet) {
        MissAvSearchHistorySheet(
            history = recentHistory,
            onDismiss = { showHistorySheet = false },
            onPick = { entry ->
                viewModel.setSearchQuery(entry.query)
                viewModel.setSort(entry.sort)
                viewModel.setFilter(entry.filter)
                viewModel.setGenre(entry.genre)
                showHistorySheet = false
                focusManager.clearFocus()
                keyboard?.hide()
                coroutineScope.launch { gridState.scrollToItem(0) }
                viewModel.performSearch(resetPage = true)
                MissAvSearchHistoryRepo.push(entry)
                recentHistory = MissAvSearchHistoryRepo.load()
            },
            onDelete = { entry ->
                MissAvSearchHistoryRepo.remove(entry)
                recentHistory = MissAvSearchHistoryRepo.load()
            },
            onClearAll = {
                MissAvSearchHistoryRepo.clear()
                recentHistory = emptyList()
                showHistorySheet = false
            },
        )
    }

    val activeChips = buildList<ActiveChip> {
        if (isBrowsing) {
            selectedGenre?.let { key ->
                val label = MissAvOptions.genreLabel(key) ?: key
                add(
                    ActiveChip("Genre: $label") {
                        viewModel.setGenre(null)
                        runSearch()
                    }
                )
            }
        }
        selectedSort?.let { key ->
            val label = MissAvOptions.displayName(MissAvGroup.SORT, key) ?: key
            add(
                ActiveChip("Sort: $label") {
                    viewModel.setSort(null)
                    runSearch()
                }
            )
        }
        selectedFilter?.let { key ->
            val label = MissAvOptions.displayName(MissAvGroup.FILTER, key) ?: key
            add(
                ActiveChip("Filter: $label") {
                    viewModel.setFilter(null)
                    runSearch()
                }
            )
        }
    }

    val activeFilterCount = setOfNotNull(selectedSort, selectedFilter).size

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopBar(
                query = searchQuery,
                onQueryChange = {
                    viewModel.setSearchQuery(it)
                    showSuggestions = it.isBlank() && recentHistory.isNotEmpty()
                },
                onBack = onBack,
                onClear = {
                    viewModel.resetSearch()
                    recentHistory = MissAvSearchHistoryRepo.load()
                    coroutineScope.launch { gridState.scrollToItem(0) }
                    focusRequester.requestFocus()
                },
                onSearch = { runSearch() },
                onOpenGenre = { showGenreSheet = true },
                onOpenSortFilter = { showSortFilterSheet = true },
                onOpenHistory = { showHistorySheet = true },
                activeFilterCount = activeFilterCount,
                hasHistory = recentHistory.isNotEmpty(),
                focusRequester = focusRequester,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AnimatedVisibility(
                visible = activeChips.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    activeChips.forEach { chip ->
                        InputChip(
                            selected = true,
                            onClick = chip.onClear,
                            label = {
                                Text(chip.label, style = MaterialTheme.typography.labelSmall)
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                    }
                    if (activeChips.size > 1) {
                        AssistChip(
                            onClick = {
                                viewModel.setSort(null)
                                viewModel.setFilter(null)
                                if (isBrowsing) viewModel.setGenre(null)
                                runSearch()
                            },
                            label = { Text("Clear all") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = {
                    coroutineScope.launch { gridState.scrollToItem(0) }
                    viewModel.performSearch(resetPage = true)
                },
                state = refreshState,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        showSuggestions && recentHistory.isNotEmpty() && !hasSearched -> {
                            RecentSuggestionsList(
                                history = recentHistory,
                                onPick = { entry ->
                                    viewModel.setSearchQuery(entry.query)
                                    viewModel.setSort(entry.sort)
                                    viewModel.setFilter(entry.filter)
                                    viewModel.setGenre(entry.genre)
                                    showSuggestions = false
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    coroutineScope.launch { gridState.scrollToItem(0) }
                                    viewModel.performSearch(resetPage = true)
                                    MissAvSearchHistoryRepo.push(entry)
                                    recentHistory = MissAvSearchHistoryRepo.load()
                                },
                                onDelete = { entry ->
                                    MissAvSearchHistoryRepo.remove(entry)
                                    recentHistory = MissAvSearchHistoryRepo.load()
                                },
                            )
                        }

                        else -> {
                            when (val state = searchState) {
                                null -> {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (searchQuery.isBlank())
                                                "Tap the search icon, or pick a genre"
                                            else
                                                "Tap search to look up \"$searchQuery\"",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(32.dp),
                                        )
                                    }
                                }

                                is PageLoadingState.Loading -> {
                                    if (allVideos.isEmpty()) {
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    } else {
                                        DisplayResults(
                                            allVideos,
                                            isLoadingMore,
                                            gridState,
                                            onNavigateToVideo,
                                        )
                                    }
                                }

                                is PageLoadingState.Success,
                                is PageLoadingState.NoMoreData -> {
                                    if (allVideos.isEmpty() && hasSearched) {
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "No results found",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    } else {
                                        DisplayResults(
                                            allVideos,
                                            isLoadingMore,
                                            gridState,
                                            onNavigateToVideo,
                                        )
                                    }
                                }

                                is PageLoadingState.Error -> {
                                    if (allVideos.isEmpty()) {
                                        ErrorContent(
                                            message = state.throwable.message
                                                ?: "Failed to load results",
                                            onRetry = { viewModel.performSearch(resetPage = true) },
                                        )
                                    } else {
                                        Column {
                                            DisplayResults(
                                                allVideos,
                                                isLoadingMore,
                                                gridState,
                                                onNavigateToVideo,
                                            )
                                            Text(
                                                text = "Failed to load more: ${state.throwable.message}",
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch { gridState.scrollToItem(0) }
                            },
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Scroll to top",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onOpenGenre: () -> Unit,
    onOpenSortFilter: () -> Unit,
    onOpenHistory: () -> Unit,
    activeFilterCount: Int,
    hasHistory: Boolean,
    focusRequester: FocusRequester,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .focusRequester(focusRequester),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search videos…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(
                onClick = onOpenHistory,
                enabled = hasHistory,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search history",
                    tint = if (hasHistory) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            IconButton(onClick = onOpenGenre) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = "Browse genre",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text(activeFilterCount.toString()) }
                    }
                },
            ) {
                IconButton(onClick = onOpenSortFilter) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Sort & Filter",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSuggestionsList(
    history: List<MissAvSearchHistoryEntry>,
    onPick: (MissAvSearchHistoryEntry) -> Unit,
    onDelete: (MissAvSearchHistoryEntry) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(history.size) { index ->
            val entry = history[index]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.query.ifBlank { "Filtered search" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val sub = buildList {
                        entry.sort?.let { add("Sort: $it") }
                        entry.filter?.let { add("Filter: $it") }
                        entry.genre?.let { add("Genre: $it") }
                    }.joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { onDelete(entry) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissAvSearchHistorySheet(
    history: List<MissAvSearchHistoryEntry>,
    onDismiss: () -> Unit,
    onPick: (MissAvSearchHistoryEntry) -> Unit,
    onDelete: (MissAvSearchHistoryEntry) -> Unit,
    onClearAll: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Search History",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = onClearAll) {
                        Text("Clear all")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No search history yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(history.size) { index ->
                        val entry = history[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (entry.query.isNotBlank()) {
                                        Text(
                                            text = entry.query,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val sub = buildList {
                                        entry.sort?.let { add("Sort: $it") }
                                        entry.filter?.let { add("Filter: $it") }
                                        entry.genre?.let { add("Genre: $it") }
                                    }.joinToString("  ·  ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDelete(entry) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DisplayResults(
    allVideos: List<HanimeInfo>,
    isLoadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onNavigateToVideo: (String, String) -> Unit,
) {
    val uniqueVideos = allVideos.distinctBy { it.videoCode }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(count = uniqueVideos.size, key = { i -> uniqueVideos[i].videoCode }) { index ->
            val video = uniqueVideos[index]
            VideoCardItem(
                videoItem = video,
                onClickVideosItem = {
                    onNavigateToVideo(video.videoCode, "/en/${video.videoCode}")
                },
                onLongClickVideosItem = { _, _ -> },
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
