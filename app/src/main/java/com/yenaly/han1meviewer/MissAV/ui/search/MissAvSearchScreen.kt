package com.yenaly.han1meviewer.MissAV.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.MissAV.common.MissAvGroup
import com.yenaly.han1meviewer.MissAV.common.MissAvOptions
import com.yenaly.han1meviewer.MissAV.common.MissAvTag
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    var showGenreDialog by rememberSaveable { mutableStateOf(false) }
    var showSortFilterDialog by rememberSaveable { mutableStateOf(false) }
    var recentHistory by remember { mutableStateOf(MissAvSearchHistoryRepo.load()) }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val refreshState = rememberPullToRefreshState()

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

    if (showGenreDialog) {
        GenrePickerDialog(
            initialGenre = selectedGenre,
            recentHistory = recentHistory,
            onHistoryPick = { entry ->
                viewModel.setSearchQuery(entry.query)
                viewModel.setSort(entry.sort)
                viewModel.setFilter(entry.filter)
                viewModel.setGenre(entry.genre)
                showGenreDialog = false
                runSearch()
            },
            onHistoryDelete = { entry ->
                MissAvSearchHistoryRepo.remove(entry)
                recentHistory = MissAvSearchHistoryRepo.load()
            },
            onDismiss = { showGenreDialog = false },
            onApplyGenre = { genre ->
                viewModel.setGenre(genre)
                showGenreDialog = false
                runSearch()
            },
        )
    }

    if (showSortFilterDialog) {
        SortFilterDialog(
            initialSort = selectedSort,
            initialFilter = selectedFilter,
            onDismiss = { showSortFilterDialog = false },
            onApply = { sort, filter ->
                viewModel.setSort(sort)
                viewModel.setFilter(filter)
                showSortFilterDialog = false
                runSearch()
            },
        )
    }

    val activeChips = buildList<ActiveChip> {
        if (!hasSearched) {
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

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopBar(
                query = searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                onBack = onBack,
                onClear = {
                    viewModel.resetSearch()
                    recentHistory = MissAvSearchHistoryRepo.load()
                    coroutineScope.launch { gridState.scrollToItem(0) }
                    focusRequester.requestFocus()
                },
                onSearch = { runSearch() },
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
                        AssistChip(
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
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                    if (activeChips.size > 1) {
                        AssistChip(
                            onClick = {
                                viewModel.setSort(null)
                                viewModel.setFilter(null)
                                if (!hasSearched) viewModel.setGenre(null)
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
                        !hasSearched -> {
                            SearchLandingPage(
                                recentHistory = recentHistory,
                                hasGenre = selectedGenre != null,
                                genreLabel = selectedGenre?.let {
                                    MissAvOptions.genreLabel(it) ?: it
                                },
                                onHistoryPick = { entry ->
                                    viewModel.setSearchQuery(entry.query)
                                    viewModel.setSort(entry.sort)
                                    viewModel.setFilter(entry.filter)
                                    viewModel.setGenre(entry.genre)
                                    runSearch()
                                },
                                onHistoryDelete = { entry ->
                                    MissAvSearchHistoryRepo.remove(entry)
                                    recentHistory = MissAvSearchHistoryRepo.load()
                                },
                                onClearHistory = {
                                    MissAvSearchHistoryRepo.clear()
                                    recentHistory = emptyList()
                                },
                                onOpenGenre = { showGenreDialog = true },
                                onClearGenre = {
                                    viewModel.setGenre(null)
                                },
                                onOpenSortFilter = { showSortFilterDialog = true },
                                onRunSearch = { runSearch() },
                            )
                        }

                        else -> {
                            when (val state = searchState) {
                                null, is PageLoadingState.Loading -> {
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
                                    if (allVideos.isEmpty()) {
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

                            if (showScrollToTop) {
                                FloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch { gridState.scrollToItem(0) }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
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
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLandingPage(
    recentHistory: List<MissAvSearchHistoryEntry>,
    hasGenre: Boolean,
    genreLabel: String?,
    onHistoryPick: (MissAvSearchHistoryEntry) -> Unit,
    onHistoryDelete: (MissAvSearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onOpenGenre: () -> Unit,
    onClearGenre: () -> Unit,
    onOpenSortFilter: () -> Unit,
    onRunSearch: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        if (recentHistory.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Combinations",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearHistory) {
                        Text("Clear")
                    }
                }
            }
            items(
                items = recentHistory,
                key = { "${it.createdAt}-${it.query}-${it.genre.orEmpty()}" },
            ) { entry ->
                HistoryCard(
                    entry = entry,
                    onClick = { onHistoryPick(entry) },
                    onDelete = { onHistoryDelete(entry) },
                )
            }
        }

        item(key = "chips") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SearchChip(
                    title = if (hasGenre) "Genre: ${genreLabel.orEmpty()}" else "Genre",
                    checked = hasGenre,
                    modifier = Modifier.weight(1f),
                    onLongClick = if (hasGenre) onClearGenre else null,
                    onClick = onOpenGenre,
                )
                SearchChip(
                    title = "Sort & Filter",
                    checked = false,
                    modifier = Modifier.weight(1f),
                    onLongClick = null,
                    onClick = onOpenSortFilter,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        item(key = "search_button") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Tap the search button to look up your filters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledIconButton(
                    onClick = onRunSearch,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchChip(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .heightIn(min = 52.dp),
        color = containerColor,
        tonalElevation = if (checked) 2.dp else 0.dp,
        shadowElevation = if (checked) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HistoryCard(
    entry: MissAvSearchHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val conditions = buildList {
        entry.sort?.let { add("Sort: $it") }
        entry.filter?.let { add("Filter: $it") }
        entry.genre?.let { add("Genre: ${MissAvOptions.genreLabel(it) ?: it}") }
    }.joinToString(" || ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (entry.query.isNotBlank()) {
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (conditions.isNotBlank()) {
                    Text(
                        text = conditions,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Remove",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenrePickerDialog(
    initialGenre: String?,
    recentHistory: List<MissAvSearchHistoryEntry>,
    onHistoryPick: (MissAvSearchHistoryEntry) -> Unit,
    onHistoryDelete: (MissAvSearchHistoryEntry) -> Unit,
    onDismiss: () -> Unit,
    onApplyGenre: (String?) -> Unit,
) {
    var selectedGenre by remember { mutableStateOf(initialGenre) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var showHistoryTab by remember { mutableStateOf(false) }

    val groups = remember { MissAvOptions.genreGroupNames }
    val genresByGroup = remember { MissAvOptions.genreOptions.groupBy { it.group.orEmpty() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (openGroup == null) "Genre" else openGroup.orEmpty()) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showHistoryTab,
                        onClick = { showHistoryTab = false },
                        label = { Text("Genres") },
                    )
                    FilterChip(
                        selected = showHistoryTab,
                        onClick = { showHistoryTab = true },
                        label = { Text("History (${recentHistory.size})") },
                    )
                }

                if (showHistoryTab) {
                    if (recentHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No recent history yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(
                                items = recentHistory,
                                key = { "${it.createdAt}-${it.query}-${it.genre.orEmpty()}" },
                            ) { entry ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    onClick = { onHistoryPick(entry) },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.query.ifBlank { "Filtered search" },
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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
                                            onClick = { onHistoryDelete(entry) },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (openGroup == null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = groups,
                            key = { it },
                        ) { group ->
                            val selected = selectedGenre != null &&
                                    MissAvOptions.genreOptions.firstOrNull {
                                        it.searchKey == selectedGenre
                                    }?.group == group
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openGroup = group },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (selected) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                        )
                                        Text(
                                            text = "${genresByGroup[group].orEmpty().size} genres",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val groupTags = genresByGroup[openGroup].orEmpty()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = groupTags,
                            key = { it.searchKey },
                        ) { tag ->
                            val selected = tag.searchKey == selectedGenre
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedGenre =
                                        if (selected) null else tag.searchKey
                                },
                                label = { Text(tag.name) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor =
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (openGroup != null) {
                    TextButton(onClick = { openGroup = null }) {
                        Text("Back")
                    }
                } else {
                    TextButton(onClick = {
                        selectedGenre = null
                        onApplyGenre(null)
                    }) {
                        Text("Reset")
                    }
                }
                TextButton(onClick = { onApplyGenre(selectedGenre) }) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SortFilterDialog(
    initialSort: String?,
    initialFilter: String?,
    onDismiss: () -> Unit,
    onApply: (sort: String?, filter: String?) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf(initialSort) }
    var filter by remember { mutableStateOf(initialFilter) }

    val sortOptions = MissAvOptions.sortOptions
    val filterOptions = MissAvOptions.filterOptions

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort & Filter") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(if (sort != null) "Sort •" else "Sort")
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(if (filter != null) "Filter •" else "Filter")
                        },
                    )
                }

                val options: List<MissAvTag> = if (selectedTab == 0) sortOptions else filterOptions
                val selectedKey = if (selectedTab == 0) sort else filter
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = options,
                        key = { it.searchKey },
                    ) { tag ->
                        val selected = tag.searchKey == selectedKey
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selectedTab == 0) {
                                    sort = if (selected) null else tag.searchKey
                                } else {
                                    filter = if (selected) null else tag.searchKey
                                }
                            },
                            label = { Text(tag.name) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor =
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    sort = null
                    filter = null
                    onApply(null, null)
                }) {
                    Text("Reset")
                }
                TextButton(onClick = { onApply(sort, filter) }) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
