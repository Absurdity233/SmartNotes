package com.nexo.smartnotes.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import com.nexo.smartnotes.ui.components.Lucide
import com.nexo.smartnotes.ui.components.LucideIcon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexo.smartnotes.domain.ChecklistItem
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.ui.components.ThinProgress
import com.nexo.smartnotes.ui.theme.LocalNoteColors
import com.nexo.smartnotes.ui.theme.TypeAccents
import com.nexo.smartnotes.util.DateFormats
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    noteId: String,
    initialType: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    vm: EditorViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(noteId, initialType) { vm.load(noteId, initialType) }

    val note = state.note
    if (note == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.loading) "加载中…" else (state.message ?: "笔记不存在"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val accent = TypeAccents[note.type.ordinal % TypeAccents.size]
    val palette = LocalNoteColors.current
    val cardColor = palette[note.colorIndex.coerceIn(0, palette.lastIndex)]

    // 这些 UI 状态必须以笔记 id 为 key：ViewModel 是复用的，
    // 从笔记 A 退出再进笔记 B 时，若沿用无 key 的 remember，
    // 会把 A 的颜色选择器/标签编辑器的展开状态带到 B 上。
    var showColorPicker by remember(note.id) { mutableStateOf(false) }
    var showTagEditor by remember(note.id) { mutableStateOf(false) }
    var showReminder by remember(note.id) { mutableStateOf(false) }
    var reminderForItem by remember(note.id) { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let(vm::addImage)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) "新建${note.type.label}" else note.type.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        LucideIcon(Lucide.ArrowLeft, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.setPinned(!note.pinned) }) {
                        LucideIcon(Lucide.Pin,
                            "置顶",
                            tint = if (note.pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showColorPicker = true }) {
                        LucideIcon(Lucide.Palette, "背景色", tint = accent)
                    }
                    TextButton(onClick = { vm.save(onSaved) }, enabled = !state.saving) {
                        Text("保存", color = accent, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .imePadding()
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardColor,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    TitleInput(note.title, accent, vm::setTitle)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        DateFormats.full(note.updatedAt) + " · " + note.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(16.dp))

                    when (note.type) {
                        NoteType.TEXT -> ContentInput(note.content, accent, vm::setContent)
                        NoteType.DIARY -> {
                            DiaryMetaEditor(note, accent, vm)
                            Spacer(Modifier.height(14.dp))
                            ContentInput(note.content, accent, vm::setContent)
                        }
                        NoteType.IMAGE -> {
                            ImageEditor(note, accent, vm, onPick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            Spacer(Modifier.height(14.dp))
                            ContentInput(note.content, accent, vm::setContent)
                        }
                        NoteType.CHECKLIST -> ChecklistEditor(note, accent, vm, withPriority = false, onPickDate = {})
                        NoteType.TODO -> ChecklistEditor(
                            note, accent, vm, withPriority = true,
                            onPickDate = { itemId -> reminderForItem = itemId },
                        )
                        NoteType.SCHEDULE -> {
                            ScheduleEditor(note, accent, vm, onPickReminder = { showReminder = true })
                            Spacer(Modifier.height(14.dp))
                            ContentInput(note.content, accent, vm::setContent)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            EditorActions(note, accent, onTags = { showTagEditor = true }, onReminder = { showReminder = true })
            Spacer(Modifier.height(100.dp))
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(note.colorIndex) {
            vm.setColor(it)
            showColorPicker = false
        }
    }

    if (showTagEditor) {
        TagEditorDialog(note.tags) { tags ->
            vm.setTags(tags)
            showTagEditor = false
        }
    }

    if (showReminder) {
        DateTimePickerDialog(
            initial = note.reminderAt ?: DateFormats.todayAt(9),
            onDismiss = { showReminder = false },
            onPick = { at ->
                vm.setReminderAt(at)
                showReminder = false
            },
            onClear = {
                vm.setReminderAt(null)
                showReminder = false
            },
        )
    }

    reminderForItem?.let { itemId ->
        val item = note.checklist.firstOrNull { it.id == itemId }
        DateTimePickerDialog(
            initial = item?.dueAt ?: DateFormats.todayAt(18),
            onDismiss = { reminderForItem = null },
            onPick = { at ->
                vm.updateChecklistItem(itemId) { it.copy(dueAt = at) }
                reminderForItem = null
            },
            onClear = {
                vm.updateChecklistItem(itemId) { it.copy(dueAt = null) }
                reminderForItem = null
            },
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

/* ------------------------------------------------------------------ 基础输入 */

@Composable
private fun TitleInput(value: String, accent: Color, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(accent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    "标题",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
            inner()
        },
    )
}

@Composable
private fun ContentInput(value: String, accent: Color, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 27.sp,
        ),
        cursorBrush = SolidColor(accent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    "开始记录…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
            inner()
        },
    )
}

/* ------------------------------------------------------------------ 日记 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryMetaEditor(note: Note, accent: Color, vm: EditorViewModel) {
    val moods = listOf("😊", "😌", "😐", "😔", "😤", "🥰", "🤔", "😴")
    val weathers = listOf("☀️", "⛅", "🌧️", "⛈️", "❄️", "🌫️", "🌈")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("心情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            moods.forEach { m ->
                EmojiCell(m, note.mood == m, accent) { vm.setMood(m) }
            }
        }
        Text("天气", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weathers.forEach { w ->
                EmojiCell(w, note.weather == w, accent) { vm.setWeather(w) }
            }
        }
        LocationInput(note.location.orEmpty(), accent) { vm.setLocation(it.ifBlank { null }) }
    }
}

@Composable
private fun EmojiCell(emoji: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) accent.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 19.sp)
    }
}

@Composable
private fun LocationInput(value: String, accent: Color, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(Lucide.MapPin, null, Modifier.size(14.dp), accent)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "添加地点",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                }
                inner()
            },
        )
    }
}

/* ------------------------------------------------------------------ 清单 / 待办 */

@Composable
private fun ChecklistEditor(
    note: Note,
    accent: Color,
    vm: EditorViewModel,
    withPriority: Boolean,
    onPickDate: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        note.checklist.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { vm.updateChecklistItem(item.id) { it.copy(done = !it.done) } },
                    modifier = Modifier.size(34.dp),
                ) {
                    LucideIcon(if (item.done) Lucide.SquareCheck else Lucide.Square,
                        null,
                        tint = if (item.done) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(21.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    BasicTextField(
                        value = item.text,
                        onValueChange = { v -> vm.updateChecklistItem(item.id) { it.copy(text = v) } },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        ),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (item.text.isEmpty()) {
                                Text(
                                    if (withPriority) "待办事项" else "清单条目",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                            }
                            inner()
                        },
                    )
                    if (withPriority) {
                        TodoItemMeta(
                            item = item,
                            accent = accent,
                            onPriority = { p -> vm.updateChecklistItem(item.id) { it.copy(priority = p) } },
                            onPickDate = { onPickDate(item.id) },
                        )
                    }
                }
                IconButton(
                    onClick = { vm.removeChecklistItem(item.id) },
                    modifier = Modifier.size(34.dp),
                ) {
                    LucideIcon(Lucide.Trash,
                        "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = vm::addChecklistItem) {
            LucideIcon(Lucide.Plus, null, Modifier.size(18.dp), tint = accent)
            Spacer(Modifier.width(4.dp))
            Text(if (withPriority) "添加待办" else "添加条目", color = accent)
        }

        if (note.checklist.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThinProgress(note.progress ?: 0f, accent, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(
                    "${note.checklist.count { it.done }}/${note.checklist.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun TodoItemMeta(
    item: ChecklistItem,
    accent: Color,
    onPriority: (Int) -> Unit,
    onPickDate: () -> Unit,
) {
    val colors = listOf(
        MaterialTheme.colorScheme.onSurfaceVariant,
        com.nexo.smartnotes.ui.theme.Amber,
        com.nexo.smartnotes.ui.theme.Coral,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("普通", "重要", "紧急").forEachIndexed { p, label ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.priority == p) colors[p] else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onPriority(p) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            item.dueAt?.let { DateFormats.monthDayTime(it) } ?: "设置时间",
            style = MaterialTheme.typography.labelSmall,
            color = if (item.dueAt != null) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onPickDate)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/* ------------------------------------------------------------------ 日程 */

@Composable
private fun ScheduleEditor(note: Note, accent: Color, vm: EditorViewModel, onPickReminder: () -> Unit) {
    var pickerFor by remember(note.id) { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScheduleTimeRow("开始", note.startAt, accent) { pickerFor = "start" }
            ScheduleTimeRow("结束", note.endAt, accent) { pickerFor = "end" }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPickReminder)
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(Lucide.AlarmClock, null, tint = accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("提醒", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    note.reminderAt?.let { DateFormats.monthDayTime(it) } ?: "未设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (note.reminderAt != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LocationInput(note.location.orEmpty(), accent) { vm.setLocation(it.ifBlank { null }) }
        }
    }

    pickerFor?.let { which ->
        val initial = if (which == "start") {
            note.startAt ?: System.currentTimeMillis()
        } else {
            note.endAt ?: ((note.startAt ?: System.currentTimeMillis()) + 3_600_000L)
        }
        DateTimePickerDialog(
            initial = initial,
            onDismiss = { pickerFor = null },
            onPick = { at ->
                if (which == "start") {
                    vm.setStartAt(at)
                    if ((note.endAt ?: 0L) <= at) vm.setEndAt(at + 3_600_000L)
                } else {
                    vm.setEndAt(at)
                }
                pickerFor = null
            },
            onClear = { pickerFor = null },
        )
    }
}

@Composable
private fun ScheduleTimeRow(label: String, value: Long?, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value?.let { DateFormats.monthDayTime(it) } ?: "选择时间",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ------------------------------------------------------------------ 图文 */

@Composable
private fun ImageEditor(note: Note, accent: Color, vm: EditorViewModel, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        note.attachments.forEach { att ->
            Box {
                AsyncImage(
                    model = att.localPath.takeIf { it.isNotBlank() } ?: att.remoteUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                IconButton(
                    onClick = { vm.removeImage(att.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    LucideIcon(Lucide.Close, "移除", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        if (note.attachments.isEmpty()) {
            Surface(
                onClick = onPick,
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LucideIcon(Lucide.ImagePlus, null, tint = accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("点击添加图片", style = MaterialTheme.typography.bodySmall, color = accent)
                }
            }
        } else {
            TextButton(onClick = onPick) {
                LucideIcon(Lucide.Plus, null, Modifier.size(18.dp), tint = accent)
                Spacer(Modifier.width(4.dp))
                Text("继续添加", color = accent)
            }
        }
    }
}

/* ------------------------------------------------------------------ 底部操作 */

@Composable
private fun EditorActions(note: Note, accent: Color, onTags: () -> Unit, onReminder: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionPill(
            icon = { LucideIcon(Lucide.Tag, null, Modifier.size(16.dp), tint = accent) },
            text = if (note.tags.isEmpty()) "标签" else note.tags.joinToString(" ") { "#$it" },
            onClick = onTags,
        )
        ActionPill(
            icon = {
                LucideIcon(Lucide.AlarmClock,
                    null,
                    Modifier.size(16.dp),
                    tint = if (note.reminderAt != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            text = note.reminderAt?.let { DateFormats.monthDayTime(it) } ?: "提醒",
            onClick = onReminder,
        )
    }
}

@Composable
private fun ActionPill(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ 对话框 */

@Composable
private fun ColorPickerDialog(selected: Int, onPick: (Int) -> Unit) {
    val palette = LocalNoteColors.current
    AlertDialog(
        onDismissRequest = { onPick(selected) },
        title = { Text("卡片颜色") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEachIndexed { index, color ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onPick(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected == index) {
                            LucideIcon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text("完成") } },
    )
}

@Composable
private fun TagEditorDialog(initial: List<String>, onConfirm: (List<String>) -> Unit) {
    var draft by remember { mutableStateOf(initial.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = { onConfirm(initial) },
        title = { Text("标签") },
        text = {
            Column {
                Text(
                    "多个标签用逗号分隔，例如：工作, 灵感",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    draft.split(',', '，')
                        .map { it.trim().removePrefix("#") }
                        .filter { it.isNotBlank() }
                        .distinct()
                )
            }) { Text("保存") }
        },
    )
}

/** 日期 + 时间选择（两步）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val cal = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
    val timeState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true,
    )

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { step = 1 }) { Text("下一步") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onClear) { Text("清除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            },
        ) {
            DatePicker(state = dateState, title = { Text("选择日期", Modifier.padding(16.dp)) })
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = dateState.selectedDateMillis ?: initial
                    val c = Calendar.getInstance().apply {
                        timeInMillis = date
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPick(c.timeInMillis)
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("上一步") } },
        )
    }
}
