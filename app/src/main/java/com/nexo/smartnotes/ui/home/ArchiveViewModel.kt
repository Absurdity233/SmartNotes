package com.nexo.smartnotes.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.domain.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 归档笔记列表。 */
class ArchiveViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmartNotesApp.instance.repository

    val notes: StateFlow<List<Note>> = repository.notes
        .map { list -> list.filter { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(note: Note) {
        viewModelScope.launch { repository.setArchived(note.id, false) }
    }

    fun trash(note: Note) {
        viewModelScope.launch { repository.moveToTrash(note.id) }
    }
}
