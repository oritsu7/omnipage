package com.ansh.omnipage.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansh.omnipage.data.LibraryRepository
import com.ansh.omnipage.data.db.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val bookId: Long,
    private val repo: LibraryRepository,
) : ViewModel() {

    val book: StateFlow<BookEntity?> =
        repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite() { viewModelScope.launch { repo.toggleFavorite(bookId) } }
    fun toggleToRead() { viewModelScope.launch { repo.toggleToRead(bookId) } }
    fun toggleFinished() { viewModelScope.launch { repo.toggleFinished(bookId) } }
    fun trash(done: () -> Unit) { viewModelScope.launch { repo.trash(bookId); done() } }
    fun restore() { viewModelScope.launch { repo.restore(bookId) } }
    fun edit(title: String, author: String, series: String) {
        viewModelScope.launch { repo.editMeta(bookId, title, author, series) }
    }
}
