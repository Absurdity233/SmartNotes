package com.nexo.smartnotes.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页筛选条件。 */
data class HomeFilter(
    val query: String = "",
    val types: Set<NoteType> = emptySet(),
    val onlyPinned: Boolean = false,
    val onlyShared: Boolean = false,
) {
    val active: Boolean
        get() = query.isNotBlank() || types.isNotEmpty() || onlyPinned || onlyShared
}

data class HomeUiState(
    val pinned: List<Note> = emptyList(),
    val others: List<Note> = emptyList(),
    val total: Int = 0,
    val filter: HomeFilter = HomeFilter(),
    val session: Session = Session(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
    val notes: List<Note> get() = pinned + others
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmartNotesApp.instance.repository

    private val _filter = MutableStateFlow(HomeFilter())
    val filter: StateFlow<HomeFilter> = _filter

    private val _menuNote = MutableStateFlow<Note?>(null)
    val menuNote: StateFlow<Note?> = _menuNote

    val state: StateFlow<HomeUiState> = combine(
        repository.notes,
        _filter,
        repository.session,
    ) { notes, filter, session ->
        val active = notes.filter { !it.archived }
        val matched = active.filter { note ->
            (filter.types.isEmpty() || note.type in filter.types) &&
                (!filter.onlyPinned || note.pinned) &&
                (!filter.onlyShared || note.isSharedWithMe) &&
                matchesQuery(note, filter.query)
        }
        HomeUiState(
            pinned = matched.filter { it.pinned },
            others = matched.filterNot { it.pinned },
            total = active.size,
            filter = filter,
            session = session,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun matchesQuery(note: Note, query: String): Boolean {
        if (query.isBlank()) return true
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.all { it in note.searchBlob }
    }

    /* ------------------------------------------------------ 筛选 */

    fun setQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun toggleType(type: NoteType) {
        val types = _filter.value.types
        _filter.value = _filter.value.copy(types = if (type in types) types - type else types + type)
    }

    fun setOnlyPinned(value: Boolean) {
        _filter.value = _filter.value.copy(onlyPinned = value)
    }

    fun setOnlyShared(value: Boolean) {
        _filter.value = _filter.value.copy(onlyShared = value)
    }

    fun clearFilter() {
        _filter.value = HomeFilter()
    }

    /* ------------------------------------------------------ 操作 */

    fun openMenu(note: Note?) {
        _menuNote.value = note
    }

    fun togglePin(note: Note) {
        viewModelScope.launch { repository.togglePin(note.id) }
    }

    fun duplicate(note: Note) {
        viewModelScope.launch { repository.duplicate(note.id) }
    }

    fun archive(note: Note) {
        viewModelScope.launch { repository.setArchived(note.id, true) }
    }

    fun trash(note: Note) {
        viewModelScope.launch { repository.moveToTrash(note.id) }
    }

    fun toggleChecklistItem(note: Note, itemId: String) {
        viewModelScope.launch { repository.toggleChecklistItem(note.id, itemId) }
    }
}
