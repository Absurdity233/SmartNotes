package com.nexo.smartnotes.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleDay(
    val timestamp: Long,
    val notes: List<Note>,
)

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmartNotesApp.instance.repository

    /** 按日期分组的日程 + 待办。 */
    val days: StateFlow<List<ScheduleDay>> = repository.notes
        .map { notes ->
            notes.asSequence()
                .filter { !it.archived && (it.type == NoteType.SCHEDULE || it.type == NoteType.TODO) }
                .mapNotNull { note -> (note.startAt ?: note.dueAt)?.let { it to note } }
                .sortedBy { it.first }
                .groupBy({ it.first }, { it.second })
                .map { (ts, list) -> ScheduleDay(ts, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trash: StateFlow<List<Note>> = repository.trash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(note: Note) {
        viewModelScope.launch { repository.restoreFromTrash(note.id) }
    }

    fun deleteForever(note: Note) {
        viewModelScope.launch { repository.deleteForever(note.id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.emptyTrash() }
    }
}
