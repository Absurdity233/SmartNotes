package com.nexo.smartnotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nexo.smartnotes.ui.components.Lucide
import com.nexo.smartnotes.ui.components.LucideIcon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.ui.AppViewModel
import com.nexo.smartnotes.ui.components.EmptyState
import com.nexo.smartnotes.ui.components.NoteCard
import com.nexo.smartnotes.ui.components.NoteRow
import com.nexo.smartnotes.ui.components.UserAvatar
import com.nexo.smartnotes.ui.theme.TypeAccents
import java.util.Calendar

@Composable
fun HomeScreen(
    appVm: AppViewModel,
    onOpenNote: (String) -> Unit,
    onNewNote: (NoteType) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenAccount: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val menuNote by vm.menuNote.collectAsStateWithLifecycle()
    val grid by appVm.gridLayout.collectAsStateWithLifecycle()
    var showTypePicker by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // 只保留一个主操作按钮，避免悬浮按钮堆叠遮挡列表末尾的卡片内容
            FloatingActionButton(
                onClick = { showTypePicker = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(58.dp),
            ) {
                LucideIcon(Lucide.Plus, "新建笔记", modifier = Modifier.size(26.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Header(
                    displayName = state.session.user?.displayName ?: "游客",
                    avatarColor = state.session.user?.let { Color(it.avatarColor) } ?: MaterialTheme.colorScheme.primary,
                    total = state.total,
                    isGuest = state.session.isGuest,
                    onOpenAccount = onOpenAccount,
                    onOpenSchedule = onOpenSchedule,
                    onOpenSearch = onOpenSearch,
                )
            }

            item {
                FilterBar(
                    filter = state.filter,
                    onToggleType = vm::toggleType,
                    onTogglePinned = { vm.setOnlyPinned(!state.filter.onlyPinned) },
                    onToggleShared = { vm.setOnlyShared(!state.filter.onlyShared) },
                    onClear = vm::clearFilter,
                )
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else if (state.isEmpty) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp)) {
                        EmptyState(
                            icon = if (state.filter.active) Lucide.Search else Lucide.Notebook,
                            title = if (state.filter.active) "没有找到匹配的笔记" else "开始记录第一条笔记",
                            subtitle = if (state.filter.active) {
                                "换个关键词，或清除筛选条件试试"
                            } else {
                                "支持笔记、日记、清单、待办、日程与图文"
                            },
                            action = if (state.filter.active) {
                                { TextButton(onClick = vm::clearFilter) { Text("清除筛选") } }
                            } else null,
                        )
                    }
                }
            } else {
                if (state.pinned.isNotEmpty()) {
                    item { SectionTitle("置顶", state.pinned.size) }
                    items(state.pinned, key = { "pin-${it.id}" }) { note ->
                        NoteItem(note, grid, onOpenNote, vm::openMenu)
                    }
                }
                if (state.others.isNotEmpty()) {
                    item { SectionTitle(if (state.pinned.isEmpty()) "全部笔记" else "其他", state.others.size) }
                    items(state.others, key = { it.id }) { note ->
                        NoteItem(note, grid, onOpenNote, vm::openMenu)
                    }
                }
            }
        }
    }

    if (showTypePicker) {
        TypePickerDialog(
            onDismiss = { showTypePicker = false },
            onPick = {
                showTypePicker = false
                onNewNote(it)
            },
        )
    }

    menuNote?.let { note ->
        NoteMenuDialog(
            note = note,
            onDismiss = { vm.openMenu(null) },
            onPin = { vm.togglePin(note); vm.openMenu(null) },
            onDuplicate = { vm.duplicate(note); vm.openMenu(null) },
            onArchive = { vm.archive(note); vm.openMenu(null) },
            onTrash = { confirmTrash = note; vm.openMenu(null) },
        )
    }

    confirmTrash?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmTrash = null },
            title = { Text("移到回收站？") },
            text = { Text("「${note.displayTitle}」会被移到回收站，可随时恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.trash(note); confirmTrash = null }) { Text("移到回收站") }
            },
            dismissButton = { TextButton(onClick = { confirmTrash = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NoteItem(
    note: Note,
    grid: Boolean,
    onOpen: (String) -> Unit,
    onMenu: (Note?) -> Unit,
) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
        if (grid) {
            NoteCard(note, onClick = { onOpen(note.id) }, onLongClick = { onMenu(note) })
        } else {
            NoteRow(note, onClick = { onOpen(note.id) }, onLongClick = { onMenu(note) })
        }
    }
}

@Composable
private fun Header(
    displayName: String,
    avatarColor: Color,
    total: Int,
    isGuest: Boolean,
    onOpenAccount: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
    Row(
        Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$greeting，$displayName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (isGuest) "游客模式 · 数据仅保存在本机" else "共 $total 条笔记",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSearch) {
            LucideIcon(Lucide.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSchedule) {
            LucideIcon(Lucide.Calendar,
                contentDescription = "日程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(2.dp))
        UserAvatar(
            name = displayName,
            color = avatarColor,
            size = 42,
            modifier = Modifier.clickable(onClick = onOpenAccount),
        )
    }
}

@Composable
private fun FilterBar(
    filter: HomeFilter,
    onToggleType: (NoteType) -> Unit,
    onTogglePinned: () -> Unit,
    onToggleShared: () -> Unit,
    onClear: () -> Unit,
) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                HomeChip("置顶", filter.onlyPinned, onTogglePinned)
            }
            item {
                HomeChip("协作", filter.onlyShared, onToggleShared)
            }
            items(NoteType.creatable) { type ->
                HomeChip(
                    type.label,
                    type in filter.types,
                    { onToggleType(type) },
                    TypeAccents[type.ordinal % TypeAccents.size],
                )
            }
        }
        if (filter.active) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 2.dp),
            ) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(0.dp)) {
                    Text("清除筛选", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HomeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

/** 新建笔记类型选择。 */
@Composable
fun TypePickerDialog(
    onDismiss: () -> Unit,
    onPick: (NoteType) -> Unit,
) {
    val desc = mapOf(
        NoteType.TEXT to "记录想法、灵感与知识",
        NoteType.DIARY to "写下今天的心情与天气",
        NoteType.CHECKLIST to "购物、出行、打包等勾选清单",
        NoteType.TODO to "带优先级与截止时间的任务",
        NoteType.SCHEDULE to "会议、约见与提醒",
        NoteType.IMAGE to "图片配文，随手收藏",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NoteType.creatable.forEach { type ->
                    val accent = TypeAccents[type.ordinal % TypeAccents.size]
                    Surface(
                        onClick = { onPick(type) },
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                LucideIcon(type.icon, null, Modifier.size(20.dp), accent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(type.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    desc[type].orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 长按笔记的快捷菜单。 */
@Composable
fun NoteMenuDialog(
    note: Note,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.displayTitle, maxLines = 1) },
        text = {
            Column {
                MenuRow("${if (note.pinned) "取消置顶" else "置顶"}") { onPin() }
                MenuRow("创建副本") { onDuplicate() }
                MenuRow("归档") { onArchive() }
                MenuRow("移到回收站", danger = true) { onTrash() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun MenuRow(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
