package com.nexo.smartnotes.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.data.remote.ApiUser
import com.nexo.smartnotes.domain.Comment
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.ShareInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailState(
    val note: Note? = null,
    val comments: List<Comment> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    val candidates: List<ApiUser> = emptyList(),
    val searching: Boolean = false,
)

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmartNotesApp.instance.repository

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private var noteId: String? = null

    fun load(id: String) {
        if (noteId == id) return
        noteId = id
        viewModelScope.launch {
            repository.observeNote(id).collect { note ->
                _state.value = _state.value.copy(note = note, loading = false)
            }
        }
        viewModelScope.launch {
            repository.observeComments(id).collect { list ->
                _state.value = _state.value.copy(comments = list)
            }
        }
    }

    fun togglePin() {
        _state.value.note?.let { note ->
            viewModelScope.launch { repository.togglePin(note.id) }
        }
    }

    fun toggleChecklistItem(itemId: String) {
        _state.value.note?.let { note ->
            viewModelScope.launch { repository.toggleChecklistItem(note.id, itemId) }
        }
    }

    fun trash(onDone: () -> Unit) {
        _state.value.note?.let { note ->
            viewModelScope.launch {
                repository.moveToTrash(note.id)
                onDone()
            }
        }
    }

    fun duplicate(onDone: (String) -> Unit) {
        _state.value.note?.let { note ->
            viewModelScope.launch {
                repository.duplicate(note.id)?.let { onDone(it.id) }
            }
        }
    }

    fun addComment(text: String) {
        _state.value.note?.let { note ->
            viewModelScope.launch { repository.addComment(note.id, text) }
        }
    }

    fun deleteComment(id: String) {
        viewModelScope.launch { repository.deleteComment(id) }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(candidates = emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)
            repository.searchUsers(query)
                .onSuccess { users ->
                    _state.value = _state.value.copy(candidates = users, searching = false)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(searching = false, message = err.message)
                }
        }
    }

    fun share(username: String, permission: String) {
        _state.value.note?.let { note ->
            viewModelScope.launch {
                repository.shareNote(note.id, username, permission)
                    .onSuccess { _state.value = _state.value.copy(message = "已分享给 $username") }
                    .onFailure { _state.value = _state.value.copy(message = it.message ?: "分享失败") }
            }
        }
    }

    fun unshare(userId: String) {
        _state.value.note?.let { note ->
            viewModelScope.launch {
                repository.unshareNote(note.id, userId)
                    .onSuccess { _state.value = _state.value.copy(message = "已取消分享") }
                    .onFailure { _state.value = _state.value.copy(message = it.message ?: "操作失败") }
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
