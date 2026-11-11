package com.nexo.smartnotes.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.domain.Attachment
import com.nexo.smartnotes.domain.ChecklistItem
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.util.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorState(
    val note: Note? = null,
    val isNew: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null,
    val canEdit: Boolean = true,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmartNotesApp.instance.repository

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun load(noteId: String, typeId: String?) {
        if (loadedId == noteId) return
        loadedId = noteId
        viewModelScope.launch {
            if (noteId == "new") {
                val type = NoteType.from(typeId)
                val created = repository.createNote(type)
                _state.value = EditorState(note = created, isNew = true, loading = false)
            } else {
                val note = repository.getNote(noteId)
                _state.value = EditorState(
                    note = note,
                    loading = false,
                    canEdit = note?.canEdit ?: false,
                    message = if (note == null) "笔记不存在或已被删除" else null,
                )
            }
        }
    }

    private fun mutate(transform: (Note) -> Note) {
        _state.update { st -> st.note?.let { st.copy(note = transform(it)) } ?: st }
    }

    fun setTitle(value: String) = mutate { it.copy(title = value) }
    fun setContent(value: String) = mutate { it.copy(content = value) }
    fun setColor(index: Int) = mutate { it.copy(colorIndex = index) }
    fun setMood(value: String) = mutate { it.copy(mood = value) }
    fun setWeather(value: String) = mutate { it.copy(weather = value) }
    fun setLocation(value: String?) = mutate { it.copy(location = value) }
    fun setStartAt(value: Long?) = mutate { it.copy(startAt = value) }
    fun setEndAt(value: Long?) = mutate { it.copy(endAt = value) }
    fun setDueAt(value: Long?) = mutate { it.copy(dueAt = value) }
    fun setReminderAt(value: Long?) = mutate { it.copy(reminderAt = value) }
    fun setPinned(value: Boolean) = mutate { it.copy(pinned = value) }
    fun toggleTag(tag: String) = mutate {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }
    fun setTags(tags: List<String>) = mutate { it.copy(tags = tags) }

    /* ------------------------------------------------------ 清单 */

    fun addChecklistItem() = mutate {
        it.copy(checklist = it.checklist + ChecklistItem())
    }

    fun updateChecklistItem(id: String, transform: (ChecklistItem) -> ChecklistItem) = mutate { note ->
        note.copy(checklist = note.checklist.map { if (it.id == id) transform(it) else it })
    }

    fun removeChecklistItem(id: String) = mutate { note ->
        note.copy(checklist = note.checklist.filterNot { it.id == id })
    }

    fun moveChecklistItem(from: Int, to: Int) = mutate { note ->
        val list = note.checklist.toMutableList()
        if (from in list.indices && to in list.indices) {
            list.add(to, list.removeAt(from))
        }
        note.copy(checklist = list)
    }

    /* ------------------------------------------------------ 图片 */

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val att = repository.importImage(uri)
            if (att == null) {
                _state.update { it.copy(message = "图片读取失败") }
            } else {
                mutate { it.copy(attachments = it.attachments + att) }
            }
        }
    }

    fun removeImage(id: String) = mutate { note ->
        note.copy(attachments = note.attachments.filterNot { it.id == id })
    }

    /* ------------------------------------------------------ 保存 */

    fun save(onSaved: (String) -> Unit) {
        val note = _state.value.note ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.saveNote(note)
            // 安排提醒
            if (note.reminderAt != null) {
                ReminderScheduler.schedule(
                    getApplication(),
                    note.id,
                    note.displayTitle,
                    note.preview.take(60).ifBlank { "有一条笔记到时间了" },
                    note.reminderAt,
                )
            } else {
                ReminderScheduler.cancel(getApplication(), note.id)
            }
            _state.update { it.copy(saving = false) }
            onSaved(note.id)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun newItemId(): String = UUID.randomUUID().toString()
}

/** 供 UI 使用的附件展示地址（本地优先，其次云端）。 */
fun Attachment.displaySource(): String? =
    localPath.takeIf { it.isNotBlank() } ?: remoteUrl
