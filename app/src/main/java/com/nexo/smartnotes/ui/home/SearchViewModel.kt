package com.nexo.smartnotes.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val types: Set<NoteType> = emptySet(),
    val results: List<Note> = emptyList(),
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmartNotesApp.instance.repository
    private val query = MutableStateFlow("")
    private val types = MutableStateFlow<Set<NoteType>>(emptySet())

    val state: StateFlow<SearchState> = combine(
        repository.notes,
        query,
        types,
        ::buildState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    /**
     * 结果组装。
     *
     * 说明：这里仍然基于已收集的笔记列表做过滤，原因是搜索页需要「边输入边出结果」，
     * 而 repository.notes 本身已经是按账号订阅好的 Flow，改动成本最低。
     * 数据库侧索引查询（NoteDao.search）留给后续「大数量级」场景使用。
     */
    private fun buildState(
        notes: List<Note>,
        q: String,
        t: Set<NoteType>,
    ): SearchState {
        val tokens = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = notes.filter { note ->
            !note.archived &&
                (t.isEmpty() || note.type in t) &&
                tokens.all { it in note.searchBlob }
        }.sortedWith(
            compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }
        )
        return SearchState(query = q, types = t, results = results)
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleType(type: NoteType) {
        types.value = if (type in types.value) types.value - type else types.value + type
    }

    fun clear() {
        query.value = ""
        types.value = emptySet()
    }

    fun trash(note: Note) {
        viewModelScope.launch { repository.moveToTrash(note.id) }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch { repository.togglePin(note.id) }
    }
}
