package com.ansh.omnipage.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansh.omnipage.data.db.AnnotationWithBook
import com.ansh.omnipage.data.reader.ReaderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AnnotationsViewModel(private val repo: ReaderRepository) : ViewModel() {

    /** null = still loading. */
    val items: StateFlow<List<AnnotationWithBook>?> =
        repo.observeAnnotations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Long) { viewModelScope.launch { repo.deleteMark(id) } }

    fun updateNote(id: Long, note: String) { viewModelScope.launch { repo.updateNote(id, note) } }
}