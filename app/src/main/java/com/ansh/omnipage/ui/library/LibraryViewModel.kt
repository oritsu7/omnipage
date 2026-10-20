package com.ansh.omnipage.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansh.omnipage.data.BookFormat
import com.ansh.omnipage.data.GroupFilter
import com.ansh.omnipage.data.LibraryQuery
import com.ansh.omnipage.data.LibraryRepository
import com.ansh.omnipage.data.Section
import com.ansh.omnipage.data.SortOrder
import com.ansh.omnipage.data.ViewMode
import com.ansh.omnipage.data.db.BookEntity
import com.ansh.omnipage.data.db.CollectionEntity
import com.ansh.omnipage.data.db.CollectionWithCount
import com.ansh.omnipage.data.db.FolderWithCount
import com.ansh.omnipage.data.db.GroupCount
import com.ansh.omnipage.data.settings.SettingsStore
import com.ansh.omnipage.scan.ScanScheduler
import com.ansh.omnipage.scan.ScanState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repo: LibraryRepository,
    private val settings: SettingsStore,
    private val scheduler: ScanScheduler,
) : ViewModel() {

    private val _section = MutableStateFlow(Section.ALL)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _group = MutableStateFlow<GroupFilter?>(null)
    val group: StateFlow<GroupFilter?> = _group.asStateFlow()

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val sort: StateFlow<SortOrder> =
        settings.sortOrder.stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder.RECENTLY_ADDED)

    val viewMode: StateFlow<ViewMode> =
        settings.viewMode.stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.LIST)

    /** null = first query still loading (lets the UI avoid flashing an empty state). */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val books: StateFlow<List<BookEntity>?> = combine(
        _section,
        _search.debounce { if (it.isEmpty()) 0L else 200L },
        _group,
        sort,
    ) { section, search, group, sort -> LibraryQuery(section, search, group, sort) }
        .distinctUntilChanged()
        .flatMapLatest { repo.observeBooks(it).distinctUntilChanged().throttleLatest(200) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: StateFlow<List<GroupCount>> =
        _section.flatMapLatest { repo.observeGroups(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<FolderWithCount>> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanState: StateFlow<ScanState> =
        scheduler.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanState())

    // collections

    val collections: StateFlow<List<CollectionWithCount>> =
        repo.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCollections: StateFlow<List<CollectionEntity>> =
        repo.observeAllCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The book whose "Add to collection" dialog is open (null = closed). 
    private val _pickerBook = MutableStateFlow<BookEntity?>(null)
    val pickerBook: StateFlow<BookEntity?> = _pickerBook.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pickerMembers: StateFlow<Set<Long>> = _pickerBook
        .flatMapLatest { book ->
            if (book == null) flowOf(emptySet<Long>())
            else repo.observeCollectionIds(book.id).map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // navigation / filters

    fun selectSection(section: Section) {
        _section.value = section
        _group.value = null
        closeSearch()
    }

    fun openGroup(item: GroupCount) {
        _group.value = when (_section.value) {
            Section.AUTHORS -> GroupFilter.Author(item.name)
            Section.SERIES -> GroupFilter.Series(item.name)
            Section.FORMATS -> BookFormat.fromName(item.name)?.let { GroupFilter.Format(it) }
            else -> null
        }
    }

    fun openFolder(item: FolderWithCount) {
        _group.value = GroupFilter.Folder(item.folder.id, item.folder.displayName)
    }

    fun openCollection(item: CollectionWithCount) {
        _group.value = GroupFilter.Collection(item.collection.id, item.collection.name)
    }

    fun clearGroup() {
        _group.value = null
    }

    fun openSearch() {
        val s = _section.value
        if (s.isGrouped && _group.value == null) {
            _section.value = Section.ALL
            _group.value = null
        }
        _searchOpen.value = true
    }

    fun closeSearch() {
        _searchOpen.value = false
        _search.value = ""
    }

    fun setSearch(text: String) {
        _search.value = text
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { settings.setViewMode(mode) }
    }

    fun setSort(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    // sources

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            val id = repo.addFolder(uri)
            if (id > 0) {
                scheduler.scanFolder(id)
                _messages.tryEmit("Folder added — scanning…")
            } else {
                _messages.tryEmit("Couldn't add that folder")
            }
        }
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val result = repo.importFiles(uris)
            _messages.tryEmit(
                when {
                    result.added == 0 && result.skipped > 0 -> "Nothing new — already in your library or unsupported"
                    result.skipped > 0 -> "Added ${result.added}, skipped ${result.skipped}"
                    else -> "Added ${result.added} ${if (result.added == 1) "book" else "books"}"
                }
            )
        }
    }

    fun rescanAll() {
        scheduler.scanAll()
        _messages.tryEmit("Scanning folders…")
    }

    fun rescanFolder(item: FolderWithCount) {
        scheduler.scanFolder(item.folder.id)
    }

    fun removeFolder(item: FolderWithCount) {
        viewModelScope.launch {
            repo.removeFolder(item.folder.id)
            if ((_group.value as? GroupFilter.Folder)?.id == item.folder.id) _group.value = null
            _messages.tryEmit("Removed ${item.folder.displayName} from the library")
        }
    }

    // collections

    fun createCollection(name: String) {
        viewModelScope.launch {
            if (repo.createCollection(name) <= 0) _messages.tryEmit("Couldn't create that collection")
        }
    }

    fun renameCollection(item: CollectionWithCount, name: String) {
        viewModelScope.launch {
            if (!repo.renameCollection(item.collection.id, name)) {
                _messages.tryEmit("A collection with that name already exists")
            }
        }
    }

    fun deleteCollection(item: CollectionWithCount) {
        viewModelScope.launch {
            repo.deleteCollection(item.collection.id)
            if ((_group.value as? GroupFilter.Collection)?.id == item.collection.id) _group.value = null
            _messages.tryEmit("Deleted “${item.collection.name}”. Its books are still in your library")
        }
    }

    fun openCollectionPicker(book: BookEntity) {
        _pickerBook.value = book
    }

    fun closeCollectionPicker() {
        _pickerBook.value = null
    }

    fun setMembership(collection: CollectionEntity, member: Boolean) {
        val book = _pickerBook.value ?: return
        viewModelScope.launch { repo.setInCollection(book.id, collection.id, member) }
    }

    fun createCollectionForPicker(name: String) {
        val book = _pickerBook.value ?: return
        viewModelScope.launch {
            val id = repo.createCollection(name)
            if (id > 0) repo.setInCollection(book.id, id, true)
        }
    }

    // per-book actions

    fun toggleFavorite(book: BookEntity) { viewModelScope.launch { repo.toggleFavorite(book.id) } }
    fun toggleToRead(book: BookEntity) { viewModelScope.launch { repo.toggleToRead(book.id) } }
    fun toggleFinished(book: BookEntity) { viewModelScope.launch { repo.toggleFinished(book.id) } }
    fun restoreAll() { viewModelScope.launch { repo.restoreAll() } }
    fun restore(book: BookEntity) { viewModelScope.launch { repo.restore(book.id) } }

    fun trash(book: BookEntity) {
        viewModelScope.launch {
            repo.trash(book.id)
            _messages.tryEmit("Moved to Trash")
        }
    }

    fun notice(text: String) {
        _messages.tryEmit(text)
    }
}

// First value immediately, then at most one per [periodMs] (always the newest). Keeps list refreshes from
// piling up while a scan inserts books, without delaying the first load or a lone tap on a star.
private fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = conflate().transform { value ->
    emit(value)
    delay(periodMs)
}