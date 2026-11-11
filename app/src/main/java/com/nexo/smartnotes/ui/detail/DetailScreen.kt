package com.nexo.smartnotes.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.nexo.smartnotes.ui.components.Lucide
import com.nexo.smartnotes.ui.components.LucideIcon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexo.smartnotes.domain.Comment
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.domain.ShareInfo
import com.nexo.smartnotes.ui.components.TagChip
import com.nexo.smartnotes.ui.components.ThinProgress
import com.nexo.smartnotes.ui.components.UserAvatar
import com.nexo.smartnotes.ui.AppViewModel
import com.nexo.smartnotes.ui.theme.TypeAccents
import com.nexo.smartnotes.util.DateFormats

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    noteId: String,
    appVm: AppViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by appVm.session.collectAsStateWithLifecycle()
    LaunchedEffect(noteId) { vm.load(noteId) }

    val note = state.note
    var showMenu by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    if (note == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.loading) "加载中…" else "笔记不存在",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val accent = TypeAccents[note.type.ordinal % TypeAccents.size]
    val canCollaborate = note.isSharedWithMe
    val cloudReady = session.user?.cloudToken != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(note.type.label, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        LucideIcon(Lucide.ArrowLeft, "返回")
                    }
                },
                actions = {
                    if (note.isOwner && note.canEdit && cloudReady) {
                        IconButton(onClick = { showShare = true }) {
                            LucideIcon(Lucide.Users, "分享", tint = accent)
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            LucideIcon(Lucide.More, "更多")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (note.pinned) "取消置顶" else "置顶") },
                                leadingIcon = { LucideIcon(Lucide.Pin, null, Modifier.size(18.dp)) },
                                onClick = { vm.togglePin(); showMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("创建副本") },
                                onClick = {
                                    showMenu = false
                                    vm.duplicate { id -> onEdit(id) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("移到回收站", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; vm.trash(onBack) },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            if (note.canEdit) {
                ExtendedFloatingActionButton(
                    onClick = { onEdit(note.id) },
                    containerColor = accent,
                    contentColor = Color.White,
                    icon = { LucideIcon(Lucide.Pencil, null) },
                    text = { Text("编辑") },
                )
            }
        },
        bottomBar = {
            if (canCollaborate || note.isOwner && (note.shares.isNotEmpty() || state.comments.isNotEmpty())) {
                CommentBar(
                    comments = state.comments,
                    draft = draft,
                    onDraft = { draft = it },
                    onSend = {
                        if (draft.isNotBlank()) {
                            vm.addComment(draft)
                            draft = ""
                        }
                    },
                    onDelete = vm::deleteComment,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.14f)) {
                        Row(
                            Modifier.padding(start = 9.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LucideIcon(note.type.icon, null, Modifier.size(14.dp), accent)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                note.type.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                            )
                        }
                    }
                    if (note.isSharedWithMe) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                        ) {
                            Text(
                                "来自 ${note.ownerName ?: "协作者"}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    note.displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(DateFormats.full(note.createdAt))
                        if (note.updatedAt != note.createdAt) {
                            append(" · 更新于 ").append(DateFormats.relative(note.updatedAt))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
            }

            if (note.trashed) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LucideIcon(
                                Lucide.Trash,
                                null,
                                Modifier.size(15.dp),
                                MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "这条笔记在回收站中，可在「我的 → 回收站」恢复",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            when (note.type) {
                NoteType.DIARY -> item { DiaryMeta(note) }
                NoteType.CHECKLIST -> item { ChecklistBody(note, accent, vm::toggleChecklistItem, note.canEdit, withPriority = false) }
                NoteType.TODO -> item { ChecklistBody(note, accent, vm::toggleChecklistItem, note.canEdit, withPriority = true) }
                NoteType.SCHEDULE -> item { ScheduleBody(note, accent) }
                else -> Unit
            }

            if (note.attachments.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        note.attachments.forEach { att ->
                            AsyncImage(
                                model = att.localPath.takeIf { it.isNotBlank() } ?: att.remoteUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.5f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (note.content.isNotBlank()) {
                item {
                    Text(
                        note.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 27.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }

            if (note.tags.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        note.tags.forEach { TagChip("#$it", accent) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (note.shares.isNotEmpty()) {
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("共享给", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        note.shares.forEach { share ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(share.displayName, accent, size = 20)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${share.displayName} · ${if (share.permission == ShareInfo.EDIT) "可编辑" else "只读"}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            item { Spacer(Modifier.height(90.dp)) }
        }
    }

    if (showShare) {
        ShareDialog(
            note = note,
            candidates = state.candidates,
            searching = state.searching,
            onSearch = vm::searchUsers,
            onShare = { username, perm -> vm.share(username, perm) },
            onUnshare = vm::unshare,
            onDismiss = { showShare = false },
        )
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::consumeMessage,
            confirmButton = { TextButton(onClick = vm::consumeMessage) { Text("好") } },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun DiaryMeta(note: Note) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(note.mood ?: "😊", fontSize = 26.sp)
        Spacer(Modifier.width(8.dp))
        Text(note.weather ?: "☀️", fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        if (!note.location.isNullOrBlank()) {
            LucideIcon(
                Lucide.MapPin,
                null,
                Modifier.size(14.dp),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                note.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ChecklistBody(
    note: Note,
    accent: Color,
    onToggle: (String) -> Unit,
    canEdit: Boolean,
    withPriority: Boolean,
) {
    val priorityColors = listOf(
        MaterialTheme.colorScheme.onSurfaceVariant,
        com.nexo.smartnotes.ui.theme.Amber,
        com.nexo.smartnotes.ui.theme.Coral,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        note.checklist.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = canEdit) { onToggle(item.id) }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(if (item.done) Lucide.SquareCheck else Lucide.Square,
                    null,
                    tint = if (item.done) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.text.ifBlank { "未命名条目" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onBackground,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                    if (withPriority && item.dueAt != null) {
                        Text(
                            DateFormats.monthDayTime(item.dueAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (DateFormats.isOverdue(item.dueAt) && !item.done) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                if (withPriority && item.priority > 0) {
                    Text(
                        if (item.priority == 2) "紧急" else "重要",
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColors[item.priority],
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(priorityColors[item.priority].copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (note.checklist.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThinProgress(note.progress ?: 0f, accent, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(
                    "${note.completedCount}/${note.checklist.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun ScheduleBody(note: Note, accent: Color) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.Clock, null, Modifier.size(16.dp), accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    DateFormats.scheduleRange(note.startAt ?: note.createdAt, note.endAt),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!note.location.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(Lucide.MapPin, null, Modifier.size(16.dp), accent)
                    Spacer(Modifier.width(10.dp))
                    Text(note.location, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (note.reminderAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(Lucide.BellRing, null, Modifier.size(16.dp), accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "提醒：" + DateFormats.monthDayTime(note.reminderAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun CommentBar(
    comments: List<Comment>,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onDelete: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (comments.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                    comments.takeLast(3).forEach { c ->
                        Row(
                            Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(c.authorName, MaterialTheme.colorScheme.primary, size = 22)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${c.authorName} · ${DateFormats.relative(c.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(c.text, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = { onDelete(c.id) },
                                contentPadding = PaddingValues(4.dp),
                            ) {
                                Text("删除", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    placeholder = { Text("添加评论…", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (draft.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                ) {
                    LucideIcon(Lucide.Send, "发送", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun ShareDialog(
    note: Note,
    candidates: List<com.nexo.smartnotes.data.remote.ApiUser>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onShare: (String, String) -> Unit,
    onUnshare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(ShareInfo.EDIT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享笔记") },
        text = {
            Column {
                Text(
                    "输入对方的用户名；对方登录同一云服务后即可看到这条笔记。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onSearch(it) },
                    placeholder = { Text("用户名，例如 alice") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ShareInfo.EDIT to "可编辑", ShareInfo.VIEW to "仅查看").forEach { (p, label) ->
                        Surface(
                            onClick = { permission = p },
                            shape = RoundedCornerShape(50),
                            color = if (permission == p) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (permission == p) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (searching) {
                    Text("搜索中…", style = MaterialTheme.typography.labelSmall)
                }
                candidates.forEach { user ->
                    Surface(
                        onClick = { onShare(user.username, permission) },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(user.displayName, MaterialTheme.colorScheme.primary, size = 30)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "@" + user.username,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (note.shares.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("已共享", style = MaterialTheme.typography.labelMedium)
                    note.shares.forEach { share ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${share.displayName}（${if (share.permission == ShareInfo.EDIT) "可编辑" else "只读"}）",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onUnshare(share.userId) }) {
                                Text(
                                    "移除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
