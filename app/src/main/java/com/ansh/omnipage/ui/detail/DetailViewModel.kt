package com.ansh.omnipage.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansh.omnipage.data.LibraryRepository
import com.ansh.omnipage.data.db.BookEntity
import com.ansh.omnipage.data.db.CollectionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val bookId: Long,
    private val repo: LibraryRepository,
) : ViewModel() {

    val book: StateFlow<BookEntity?> =
        repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val collections: StateFlow<List<CollectionEntity>> =
        repo.observeAllCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memberIds: StateFlow<Set<Long>> =
        repo.observeCollectionIds(bookId).map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite() { viewModelScope.launch { repo.toggleFavorite(bookId) } }
    fun toggleToRead() { viewModelScope.launch { repo.toggleToRead(bookId) } }
    fun toggleFinished() { viewModelScope.launch { repo.toggleFinished(bookId) } }
    fun trash(done: () -> Unit) { viewModelScope.launch { repo.trash(bookId); done() } }
    fun restore() { viewModelScope.launch { repo.restore(bookId) } }
    fun edit(title: String, author: String, series: String) {
        viewModelScope.launch { repo.editMeta(bookId, title, author, series) }
    }

    fun setMembership(collection: CollectionEntity, member: Boolean) {
        viewModelScope.launch { repo.setInCollection(bookId, collection.id, member) }
    }

    fun createCollectionAndAdd(name: String) {
        viewModelScope.launch {
            val id = repo.createCollection(name)
            if (id > 0) repo.setInCollection(bookId, id, true)
        }
    }
}