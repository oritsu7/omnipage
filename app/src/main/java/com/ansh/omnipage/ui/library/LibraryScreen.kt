package com.ansh.omnipage.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ansh.omnipage.OmniPageApp
import com.ansh.omnipage.data.Section
import com.ansh.omnipage.data.SortOrder
import com.ansh.omnipage.data.ViewMode
import com.ansh.omnipage.data.db.BookEntity
import com.ansh.omnipage.ui.common.shareBook
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.Night
import com.ansh.omnipage.ui.theme.Panel
import com.ansh.omnipage.ui.theme.Teal
import com.ansh.omnipage.ui.theme.TextMuted
import kotlinx.coroutines.launch

private fun Section.icon(): ImageVector = when (this) {
    Section.READING_NOW -> Icons.Outlined.Repeat
    Section.ALL -> Icons.Outlined.Description
    Section.FAVORITES -> Icons.Outlined.StarBorder
    Section.TO_READ -> Icons.Outlined.Schedule
    Section.HAVE_READ -> Icons.Outlined.DoneAll
    Section.AUTHORS -> Icons.Outlined.Person
    Section.SERIES -> Icons.Outlined.LocalOffer
    Section.COLLECTIONS -> Icons.Outlined.CollectionsBookmark
    Section.FORMATS -> Icons.Outlined.Layers
    Section.FOLDERS -> Icons.Outlined.Folder
    Section.DOWNLOADS -> Icons.Outlined.Download
    Section.TRASH -> Icons.Outlined.Delete
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OmniPageApp).container }
    val vm: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LibraryViewModel(container.repository, container.settings, container.scanScheduler)
            }
        }
    )

    val section by vm.section.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val searchOpen by vm.searchOpen.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val books by vm.books.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val allCollections by vm.allCollections.collectAsStateWithLifecycle()
    val pickerBook by vm.pickerBook.collectAsStateWithLifecycle()
    val pickerMembers by vm.pickerMembers.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(uris)
    }
    val pickFolder = { folderPicker.launch(null) }
    val pickFiles = { filePicker.launch(arrayOf("*/*")) }

    val currentGroup = group
    BackHandler(enabled = searchOpen || currentGroup != null || section != Section.ALL) {
        when {
            searchOpen -> vm.closeSearch()
            currentGroup != null -> vm.clearGroup()
            else -> vm.selectSection(Section.ALL)
        }
    }

    fun actionsFor(book: BookEntity) = BookActions(
        onOpen = { onOpenBook(book.id) },
        onAbout = { onOpenDetails(book.id) },
        onFavorite = { vm.toggleFavorite(book) },
        onToRead = { vm.toggleToRead(book) },
        onFinished = { vm.toggleFinished(book) },
        onCollections = { vm.openCollectionPicker(book) },
        onShare = { shareBook(context, book) },
        onTrash = { vm.trash(book) },
        onRestore = { vm.restore(book) },
    )

    val picking = pickerBook
    if (picking != null) {
        CollectionPickerDialog(
            bookTitle = picking.title,
            collections = allCollections,
            memberIds = pickerMembers,
            onToggle = { collection, member -> vm.setMembership(collection, member) },
            onCreate = vm::createCollectionForPicker,
            onDismiss = vm::closeCollectionPicker,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LibraryDrawer(current = section) { picked ->
                vm.selectSection(picked)
                scope.launch { drawerState.close() }
            }
        },
    ) {
        Scaffold(
            containerColor = Night,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Teal,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        ),
                        title = {
                            if (searchOpen) {
                                SearchField(search, vm::setSearch)
                            } else {
                                Text(currentGroup?.label ?: section.title, maxLines = 1)
                            }
                        },
                        navigationIcon = {
                            if (searchOpen) {
                                IconButton(onClick = vm::closeSearch) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                                }
                            } else if (currentGroup != null) {
                                IconButton(onClick = vm::clearGroup) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Outlined.Menu, "Menu")
                                }
                            }
                        },
                        actions = {
                            if (searchOpen) {
                                if (search.isNotEmpty()) {
                                    IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Outlined.Close, "Clear") }
                                }
                            } else {
                                IconButton(onClick = vm::openSearch) { Icon(Icons.Outlined.Search, "Search") }
                                if (section == Section.TRASH) {
                                    IconButton(onClick = vm::restoreAll) {
                                        Icon(Icons.Outlined.RestoreFromTrash, "Restore all")
                                    }
                                }
                                OverflowMenu(
                                    viewMode = viewMode,
                                    sort = sort,
                                    onViewMode = vm::setViewMode,
                                    onSort = vm::setSort,
                                    onAddFolder = pickFolder,
                                    onAddFiles = pickFiles,
                                    onRescan = vm::rescanAll,
                                )
                            }
                        },
                    )
                    if (scan.active) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Panel)
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val list = books
                when {
                    section == Section.FOLDERS && currentGroup == null -> FolderList(
                        folders = folders,
                        scanning = scan.active,
                        onOpen = vm::openFolder,
                        onRescan = vm::rescanFolder,
                        onRemove = vm::removeFolder,
                        onAddFolder = pickFolder,
                    )
                    section == Section.COLLECTIONS && currentGroup == null -> CollectionList(
                        collections = collections,
                        onOpen = vm::openCollection,
                        onCreate = vm::createCollection,
                        onRename = vm::renameCollection,
                        onDelete = vm::deleteCollection,
                    )
                    section.isGrouped && currentGroup == null -> GroupList(section, groups, vm::openGroup)
                    list == null -> Unit
                    list.isEmpty() -> {
                        val (title, body) = emptyCopy(section, searching = search.isNotBlank())
                        val offerSources = section == Section.ALL && search.isBlank()
                        EmptyState(
                            title = title,
                            body = if (scan.active) "Scanning your folders… books appear as they're found." else body,
                            actions = if (offerSources && !scan.active) {
                                { AddSourceButtons(pickFolder, pickFiles) }
                            } else null,
                        )
                    }
                    viewMode == ViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        gridItems(list, key = { it.id }) { book -> BookGridItem(book, actionsFor(book)) }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(list, key = { it.id }) { book -> BookRow(book, actionsFor(book)) }
                    }
                }

                if (scan.active && list != null && list.isNotEmpty()) {
                    Text(
                        "Scanning… ${scan.found}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryDrawer(current: Section, onSelect: (Section) -> Unit) {
    ModalDrawerSheet(drawerContainerColor = Panel) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AutoStories, null, tint = Accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.padding(start = 16.dp))
                Text("OmniPage", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = Color(0xFF1B3644))
            Spacer(Modifier.padding(top = 8.dp))
            Section.entries.forEach { s ->
                NavigationDrawerItem(
                    label = { Text(s.title) },
                    icon = { Icon(s.icon(), null) },
                    selected = s == current,
                    onClick = { onSelect(s) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Teal.copy(alpha = 0.55f),
                        unselectedIconColor = TextMuted,
                        selectedIconColor = Accent,
                        unselectedTextColor = Color.White,
                        selectedTextColor = Color.White,
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Search title or author", color = Color.White.copy(alpha = 0.6f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}

@Composable
private fun OverflowMenu(
    viewMode: ViewMode,
    sort: SortOrder,
    onViewMode: (ViewMode) -> Unit,
    onSort: (SortOrder) -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onRescan: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        if (viewMode == ViewMode.LIST) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                        null,
                    )
                },
                text = { Text(if (viewMode == ViewMode.LIST) "Grid view" else "List view") },
                onClick = {
                    open = false
                    onViewMode(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                },
            )
            HorizontalDivider()
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Sort, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.padding(start = 12.dp))
                Text("Sort by", style = MaterialTheme.typography.labelLarge, color = TextMuted)
            }
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            order.label,
                            color = if (order == sort) Accent else Color.Unspecified,
                        )
                    },
                    onClick = { open = false; onSort(order) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                text = { Text("Add folder") },
                onClick = { open = false; onAddFolder() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.NoteAdd, null) },
                text = { Text("Add files") },
                onClick = { open = false; onAddFiles() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                text = { Text("Rescan folders") },
                onClick = { open = false; onRescan() },
            )
        }
    }
}
