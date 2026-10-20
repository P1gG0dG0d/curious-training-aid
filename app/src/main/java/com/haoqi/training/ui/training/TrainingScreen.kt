package com.haoqi.training.ui.training

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haoqi.training.data.db.ExerciseEntity
import com.haoqi.training.data.db.PlanDayEntity
import com.haoqi.training.domain.TrainingLogic
import com.haoqi.training.timer.RestTimerService
import com.haoqi.training.timer.RestTimerState
import com.haoqi.training.ui.components.GroupCard
import com.haoqi.training.ui.components.KeyboardGuardTextField
import com.haoqi.training.ui.components.ScreenTitle
import com.haoqi.training.ui.components.pressScaleClickable
import com.haoqi.training.ui.theme.DangerRed
import com.haoqi.training.ui.theme.Hairline
import com.haoqi.training.ui.theme.Ink
import com.haoqi.training.ui.theme.Motion
import com.haoqi.training.ui.theme.SuccessGreen
import java.util.Locale

@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onSelectDay: (Int) -> Unit,
    onAddDay: () -> Unit,
    onRemoveDay: () -> Unit,
    onSaveExercise: (ExerciseEntity) -> Unit,
    onDeleteExercise: (ExerciseEntity) -> Unit,
    onToggleSet: (ExerciseEntity, Int, Boolean) -> Unit,
    onToggleDayCompleted: (PlanDayEntity, Boolean) -> Unit,
    onRenameDay: (Long, String) -> Unit,
    onReorderExercises: (Long, List<Long>) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = state.days
    var editingExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var addingExercise by remember { mutableStateOf(false) }
    var confirmRemoveDay by remember { mutableStateOf(false) }
    var renamingDay by remember { mutableStateOf<PlanDayEntity?>(null) }

    val pagerState = rememberPagerState(
        initialPage = state.selectedDayIndex,
        pageCount = { days.size.coerceAtLeast(1) },
    )

    LaunchedEffect(state.selectedDayIndex, days.size) {
        if (days.isNotEmpty() && pagerState.currentPage != state.selectedDayIndex) {
            pagerState.animateScrollToPage(state.selectedDayIndex)
        }
    }
    LaunchedEffect(pagerState, days.size) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page < days.size && page != state.selectedDayIndex) onSelectDay(page)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle("训练")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
            Spacer(Modifier.width(8.dp))
        }

        // 训练日切换：点击 + 左右滑动都可以
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(days, key = { _, day -> day.id }) { index, day ->
                DayChip(
                    label = day.displayName(),
                    selected = index == state.selectedDayIndex,
                    completed = day.completed,
                    onClick = {
                        if (index == state.selectedDayIndex) renamingDay = day
                        else onSelectDay(index)
                    },
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.pressScaleClickable(onClick = onAddDay),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("添加一天", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // 组间休息：应用内倒计时条，锁屏时看通知栏
        val appContext = androidx.compose.ui.platform.LocalContext.current
        val restState by RestTimerService.state.collectAsStateWithLifecycle()
        AnimatedVisibility(visible = restState != null) {
            restState?.let { rest ->
                RestBanner(state = rest, onSkip = { RestTimerService.skip(appContext) })
            }
        }

        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val day = days.getOrNull(page) ?: return@HorizontalPager
            DayPage(
                day = day,
                exercises = state.exercisesByDay[day.id].orEmpty(),
                doneSetsByExercise = state.doneSetsByExercise,
                onEditExercise = { editingExercise = it },
                onAddExercise = { addingExercise = true },
                onToggleSet = onToggleSet,
                onToggleCompleted = { completed -> onToggleDayCompleted(day, completed) },
                onRemoveDay = { confirmRemoveDay = true },
                onReorder = { ids -> onReorderExercises(day.id, ids) },
            )
        }
    }

    renamingDay?.let { day ->
        RenameDayDialog(
            day = day,
            onDismiss = { renamingDay = null },
            onSave = { name ->
                onRenameDay(day.id, name)
                renamingDay = null
            },
        )
    }

    val selectedDay = state.selectedDay
    if (addingExercise && selectedDay != null) {
        ExerciseDialog(
            dayId = selectedDay.id,
            existing = null,
            onDismiss = { addingExercise = false },
            onSave = { addingExercise = false; onSaveExercise(it) },
            onDelete = null,
        )
    }
    editingExercise?.let { exercise ->
        ExerciseDialog(
            dayId = exercise.dayId,
            existing = exercise,
            onDismiss = { editingExercise = null },
            onSave = { editingExercise = null; onSaveExercise(it) },
            onDelete = { editingExercise = null; onDeleteExercise(exercise) },
        )
    }
    if (confirmRemoveDay) {
        Dialog(onDismissRequest = { confirmRemoveDay = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp)) {
                    Text("删除这一天？", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这一天的动作和完成记录会一起删除，其他天不受影响。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { confirmRemoveDay = false }) { Text("取消") }
                        TextButton(
                            onClick = {
                                confirmRemoveDay = false
                                onRemoveDay()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = DangerRed),
                        ) { Text("删除") }
                    }
                }
            }
        }
    }
}

private fun PlanDayEntity.displayName(): String = name.ifBlank { "第${dayIndex}天" }

@Composable
private fun RenameDayDialog(
    day: PlanDayEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(day.id) { mutableStateOf(day.name) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
            ) {
                Text("重命名训练日", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                KeyboardGuardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如 练胸日）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "留空恢复为第${day.dayIndex}天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(name.trim()) }) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun RestBanner(state: RestTimerState, onSkip: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "休息中 · ${state.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "剩余 ${formatRestTime(state.remainingSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onSkip) {
                Text("跳过", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

private fun formatRestTime(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}分${secs.toString().padStart(2, '0')}秒" else "${secs}秒"
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    completed: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        Motion.color,
        label = "dayChipBg",
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        border = if (selected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.pressScaleClickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
            if (completed) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) contentColor else SuccessGreen,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun DayPage(
    day: PlanDayEntity,
    exercises: List<ExerciseEntity>,
    doneSetsByExercise: Map<Long, Set<Int>>,
    onEditExercise: (ExerciseEntity) -> Unit,
    onAddExercise: () -> Unit,
    onToggleSet: (ExerciseEntity, Int, Boolean) -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onRemoveDay: () -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // 拖动时先改本地顺序让卡片跟手，松手后再写回数据库
    var order by remember { mutableStateOf(exercises) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Long, Int>() }

    LaunchedEffect(exercises, draggingId) {
        if (draggingId == null && exercises.map { it.id } != order.map { it.id }) {
            order = exercises
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "day-header") {
            GroupCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            day.displayName(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (day.completed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (day.name.isBlank()) "循环第${day.dayIndex}天已练完" else "${day.name}已练完",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                )
                            }
                        }
                    }
                    if (day.completed) {
                        TextButton(onClick = { onToggleCompleted(false) }) { Text("撤销") }
                    } else {
                        Button(
                            onClick = { onToggleCompleted(true) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("完成")
                        }
                    }
                }
            }
        }

        if (exercises.isEmpty()) {
            item(key = "empty") {
                GroupCard {
                    Text(
                        "这一天还没有动作，点下方「添加动作」开始。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(order, key = { _, exercise -> exercise.id }) { _, exercise ->
            val isDragging = draggingId == exercise.id
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffset
                            scaleX = 1.015f
                            scaleY = 1.015f
                            shadowElevation = 18.dp.toPx()
                        }
                    }
                    // 被拖的卡片跟手移动，其余卡片用系统动画滑到新位置
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .onGloballyPositioned { heights[exercise.id] = it.size.height }
                    .pointerInput(exercise.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingId = exercise.id
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val from = order.indexOfFirst { it.id == exercise.id }
                                if (from >= 0) {
                                    val ownHeight = heights[exercise.id] ?: 1
                                    if (dragOffset > ownHeight / 2f && from < order.lastIndex) {
                                        val next = order[from + 1]
                                        order = order.toMutableList().apply { add(from + 1, removeAt(from)) }
                                        dragOffset -= (heights[next.id] ?: ownHeight)
                                    } else if (dragOffset < -ownHeight / 2f && from > 0) {
                                        val previous = order[from - 1]
                                        order = order.toMutableList().apply { add(from - 1, removeAt(from)) }
                                        dragOffset += (heights[previous.id] ?: ownHeight)
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                                onReorder(order.map { it.id })
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = 0f
                                order = exercises
                            },
                        )
                    },
            ) {
                ExerciseCard(
                    exercise = exercise,
                    doneSets = doneSetsByExercise[exercise.id].orEmpty(),
                    onToggleSet = { index, wasDone -> onToggleSet(exercise, index, wasDone) },
                    onEdit = { onEditExercise(exercise) },
                )
            }
        }

        item(key = "add-exercise") {
            OutlinedButton(
                onClick = onAddExercise,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加动作")
            }
        }

        item(key = "remove-day") {
            TextButton(
                onClick = onRemoveDay,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text("删除这一天") }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseEntity,
    doneSets: Set<Int>,
    onToggleSet: (index: Int, wasDone: Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val allDone = TrainingLogic.allSetsDone(doneSets, exercise.setCount)
    GroupCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${exercise.setCount} 组",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        TrainingLogic.restLabel(exercise.restSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (allDone) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑动作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(exercise.setCount) { index ->
                SetBlock(
                    index = index,
                    done = index in doneSets,
                    onClick = { onToggleSet(index, index in doneSets) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PartChip("重量", TrainingLogic.weightLabel(exercise.weightKg), Modifier.weight(1f))
            PartChip("次数", TrainingLogic.repsLabel(exercise.reps), Modifier.weight(1f))
        }
    }
}

/** 重量 / 次数分开显示的小块 */
@Composable
private fun PartChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 组方块：点一下变黑 */
@Composable
private fun SetBlock(index: Int, done: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        if (done) Ink else Color.Transparent,
        Motion.color,
        label = "setBlock",
    )
    val contentColor = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(background)
            .then(
                if (done) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            )
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            TrainingLogic.setLabel(index),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ExerciseDialog(
    dayId: Long,
    existing: ExerciseEntity?,
    onDismiss: () -> Unit,
    onSave: (ExerciseEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sets by remember { mutableStateOf(existing?.setCount?.toString() ?: "4") }
    var rest by remember { mutableStateOf(existing?.restSeconds?.toString() ?: "90") }
    var weight by remember { mutableStateOf(existing?.weightKg?.let { formatWeight(it) } ?: "20") }
    var reps by remember { mutableStateOf(existing?.reps?.toString() ?: "10") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (existing == null) "添加动作" else "编辑动作",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(14.dp))
                KeyboardGuardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("动作名称（如 卧推）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyboardGuardTextField(
                        value = sets,
                        onValueChange = { sets = it.filter(Char::isDigit).take(2) },
                        label = { Text("组数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    KeyboardGuardTextField(
                        value = rest,
                        onValueChange = { rest = it.filter(Char::isDigit).take(3) },
                        label = { Text("休息（秒）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "重量和次数分开设置，统一用于这个动作的每组",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyboardGuardTextField(
                        value = weight,
                        onValueChange = { input ->
                            weight = input.filter { it.isDigit() || it == '.' }.take(5)
                        },
                        label = { Text("重量（kg）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    KeyboardGuardTextField(
                        value = reps,
                        onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                        label = { Text("每组次数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = DangerRed),
                        ) { Text("删除") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val setCount = sets.toIntOrNull() ?: 0
                            val restSeconds = rest.toIntOrNull() ?: 0
                            val weightKg = weight.toFloatOrNull() ?: -1f
                            val repsCount = reps.toIntOrNull() ?: 0
                            when {
                                name.isBlank() -> error = "请填写动作名称"
                                setCount !in 1..20 -> error = "组数请填 1~20"
                                restSeconds !in 0..600 -> error = "休息请填 0~600 秒"
                                weightKg < 0f || weightKg > 1000f -> error = "请填写合理的重量"
                                repsCount !in 1..999 -> error = "次数请填 1~999"
                                else -> onSave(
                                    ExerciseEntity(
                                        id = existing?.id ?: 0L,
                                        dayId = dayId,
                                        orderIndex = existing?.orderIndex ?: 0,
                                        name = name.trim(),
                                        setCount = setCount,
                                        restSeconds = restSeconds,
                                        weightKg = weightKg,
                                        reps = repsCount,
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("保存") }
                }
            }
        }
    }
}

private fun formatWeight(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)
