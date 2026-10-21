package com.haoqi.training.ui.diet

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.haoqi.training.domain.DailyDiet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.haoqi.training.ai.FoodDraft
import com.haoqi.training.ai.ImageCompressor
import com.haoqi.training.data.db.FoodEntryEntity
import com.haoqi.training.domain.CalorieChartMath
import com.haoqi.training.data.prefs.ApiConfig
import com.haoqi.training.ui.components.GroupCard
import com.haoqi.training.ui.components.KeyboardGuardTextField
import com.haoqi.training.ui.components.ScreenTitle
import com.haoqi.training.ui.components.SectionLabel
import com.haoqi.training.ui.components.ThinkingDots
import com.haoqi.training.ui.components.pressScaleClickable
import com.haoqi.training.ui.charts.CalorieBars
import com.haoqi.training.ui.charts.MacroBars
import com.haoqi.training.ui.charts.formatGrams
import com.haoqi.training.ui.theme.BarDashedLine
import com.haoqi.training.ui.theme.BarExpenditure
import com.haoqi.training.ui.theme.BarIntake
import com.haoqi.training.ui.theme.DangerRed
import com.haoqi.training.ui.theme.MacroCarbs
import com.haoqi.training.ui.theme.MacroFat
import com.haoqi.training.ui.theme.MacroProtein
import com.haoqi.training.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun DietScreen(
    state: DietUiState,
    apiConfig: ApiConfig,
    onAddFood: (String, MealType, String, String, Float, Float, Float, Float, Boolean) -> Unit,
    onDeleteEntry: (FoodEntryEntity) -> Unit,
    onUpdateEntry: (FoodEntryEntity) -> Unit,
    onAnalyzePhoto: (String, ByteArray, String) -> Unit,
    onConfirmDrafts: (String, MealType, List<FoodDraft>) -> Unit,
    onClearAiState: () -> Unit,
    onSaveActivity: (String, Float) -> Unit,
    foodEstimate: MacrosEstimate,
    onEstimateMacros: (String, String) -> Unit,
    onClearEstimate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var addMeal by remember { mutableStateOf<MealType?>(null) }
    var manualMeal by remember { mutableStateOf<MealType?>(null) }
    var editingEntry by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var pendingMeal by remember { mutableStateOf(MealType.LUNCH) }
    var editorDate by remember { mutableStateOf(state.date) }
    var photoDate by remember { mutableStateOf(state.date) }
    var activityText by remember(state.date, state.activityKcal) { mutableStateOf(state.activityKcal.toString()) }
    val activityValue = activityText.toFloatOrNull()
    LaunchedEffect(state.date) {
        addMeal = null
        manualMeal = null
        editingEntry = null
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun compressAndAnalyze(uri: Uri) {
        val capturedDate = photoDate
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { ImageCompressor.loadJpegBytes(context, uri) }
            if (bytes != null) {
                // 压缩后一律是 JPEG，MIME 跟着实际内容走
                onAnalyzePhoto(capturedDate, bytes, "image/jpeg")
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { compressAndAnalyze(it) } }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { compressAndAnalyze(it) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("饮食")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
            Spacer(Modifier.width(8.dp))
        }

        SectionLabel(state.date)
        GroupCard {
            Text("今日活动消耗", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "只记在今天（${state.date}），新的一天从 0 开始",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            KeyboardGuardTextField(
                value = activityText,
                onValueChange = { activityText = it },
                label = { Text("活动消耗 kcal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (activityValue == null || !DailyDiet.validActivity(activityValue)) {
                Spacer(Modifier.height(6.dp))
                Text("请输入非负有限数字", color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { activityValue?.let { onSaveActivity(state.date, it) } },
                enabled = activityValue != null && DailyDiet.validActivity(activityValue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("保存今日活动消耗") }
            state.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CalorieGroupCard(state)

        SectionLabel("营养摄入")
        GroupCard {
            MacroBars(
                protein = state.totalProtein,
                carbs = state.totalCarbs,
                fat = state.totalFat,
            )
        }

        MealType.entries.forEach { meal ->
            val entries = state.entriesOf(meal)
            if (meal != MealType.SNACK || entries.isNotEmpty()) {
                MealSection(
                    meal = meal,
                    entries = entries,
                    onAdd = { editorDate = state.date; addMeal = meal },
                    onDelete = onDeleteEntry,
                    onEdit = { editingEntry = it },
                )
            }
        }

        if (state.entries.isEmpty()) {
            Text(
                "还没有记录。先拍照识别，或手动录入一餐。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

    addMeal?.let { meal ->
        AddFoodChoiceDialog(
            meal = meal,
            aiConfigured = apiConfig.isConfigured,
            onDismiss = { addMeal = null },
            onPickPhoto = {
                addMeal = null
                pendingMeal = meal
                photoDate = editorDate
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onTakePhoto = {
                addMeal = null
                pendingMeal = meal
                photoDate = editorDate
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File.createTempFile("food_", ".jpg", dir)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri = uri
                takePicture.launch(uri)
            },
            onManual = {
                addMeal = null
                manualMeal = meal
            },
        )
    }

    manualMeal?.let { meal ->
        ManualFoodDialog(
            meal = meal,
            onDismiss = { manualMeal = null; onClearEstimate() },
            onSave = { draft ->
                onAddFood(editorDate, meal, draft.name, draft.portion, draft.kcal, draft.protein, draft.fat, draft.carbs, false)
                manualMeal = null
                onClearEstimate()
            },
            estimate = foodEstimate,
            onRequestEstimate = onEstimateMacros,
            onClearEstimate = onClearEstimate,
        )
    }

    editingEntry?.let { entry ->
        ManualFoodDialog(
            meal = MealType.entries.firstOrNull { it.name == entry.meal } ?: MealType.LUNCH,
            isEditing = true,
            initial = FoodDraft(entry.name, entry.portion, entry.kcal, entry.protein, entry.fat, entry.carbs),
            onDismiss = { editingEntry = null; onClearEstimate() },
            estimate = foodEstimate,
            onRequestEstimate = onEstimateMacros,
            onClearEstimate = onClearEstimate,
            onSave = { draft ->
                onUpdateEntry(
                    entry.copy(
                        name = draft.name,
                        portion = draft.portion,
                        kcal = draft.kcal,
                        protein = draft.protein,
                        fat = draft.fat,
                        carbs = draft.carbs,
                    )
                )
                editingEntry = null
                onClearEstimate()
            },
        )
    }

    when (val ai = state.aiState) {
        is AiState.Analyzing -> AnalyzingDialog(onCancel = onClearAiState)
        is AiState.Failed -> AiErrorDialog(message = ai.message, onDismiss = onClearAiState)
        is AiState.Result -> FoodDraftDialog(
            initialMeal = pendingMeal,
            drafts = ai.drafts,
            onConfirm = { meal, drafts -> onConfirmDrafts(ai.date, meal, drafts) },
            onDismiss = onClearAiState,
        )
        AiState.Idle -> Unit
    }
}

@Composable
private fun CalorieGroupCard(state: DietUiState) {
    GroupCard {
        Text("今日热量", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(BarExpenditure)
            Spacer(Modifier.width(6.dp))
            Text(
                "总消耗 ${state.expenditureKcal.toInt()} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(20.dp))
            Dot(BarIntake)
            Spacer(Modifier.width(6.dp))
            Text(
                "摄入 ${state.totalKcal.toInt()} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "基础代谢 ${state.bmrKcal?.toInt()?.toString() ?: "未配置"} + 今日活动 ${state.activityKcal.toInt()} kcal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        CalorieBars(
            expenditureKcal = state.expenditureKcal,
            intakeKcal = state.totalKcal,
        )

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(BarDashedLine),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "竖虚线 = 差距 500 kcal 的位置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        val delta = state.expenditureKcal - state.totalKcal
        val differenceText = "消耗摄入差为：${delta.toInt()} 千卡（消耗 − 摄入）"
        val overTarget = CalorieChartMath.isAtLeastTarget(state.expenditureKcal, state.totalKcal)
        if (state.bmrKcal == null) {
            Text(
                "基础代谢未配置：当前只算今日活动消耗，去「设置」保存个人资料即可",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                if (overTarget) "已达到 500 千卡目标" else "未达到 500 千卡目标",
                style = MaterialTheme.typography.bodyMedium,
                color = if (overTarget) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                differenceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun MealSection(
    meal: MealType,
    entries: List<FoodEntryEntity>,
    onAdd: () -> Unit,
    onDelete: (FoodEntryEntity) -> Unit,
    onEdit: (FoodEntryEntity) -> Unit,
) {
    var expandedId by remember { mutableStateOf<Long?>(null) }
    SectionLabel(meal.label)
    GroupCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        entries.forEach { entry ->
            FoodRow(
                entry = entry,
                expanded = expandedId == entry.id,
                onClick = { expandedId = if (expandedId == entry.id) null else entry.id },
                onEdit = { onEdit(entry) },
                onDelete = { onDelete(entry) },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .pressScaleClickable(onClick = onAdd)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "添加${meal.label}食物",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FoodRow(
    entry: FoodEntryEntity,
    expanded: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    if (entry.estimated) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "估算",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    "${entry.portion} · ${entry.kcal.toInt()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroTag("蛋白质", entry.protein, MacroProtein)
                MacroTag("碳水", entry.carbs, MacroCarbs)
                MacroTag("脂肪", entry.fat, MacroFat)
            }
        }
    }
}

@Composable
private fun MacroTag(label: String, grams: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            "$label ${formatGrams(grams)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddFoodChoiceDialog(
    meal: MealType,
    aiConfigured: Boolean,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onManual: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "添加${meal.label}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(6.dp))
                if (!aiConfigured) {
                    Text(
                        "还没配置 AI Key，拍照识别暂时不可用，可先手动录入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    icon = Icons.Filled.CameraAlt,
                    title = "拍照识别",
                    subtitle = if (aiConfigured) "AI 自动估算营养，确认后入账" else "需要先在设置里配置 DeepSeek Key",
                    enabled = aiConfigured,
                    onClick = onTakePhoto,
                )
                ChoiceRow(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "从相册选择",
                    subtitle = if (aiConfigured) "选一张食物照片识别" else "需要先在设置里配置 DeepSeek Key",
                    enabled = aiConfigured,
                    onClick = onPickPhoto,
                )
                ChoiceRow(
                    icon = Icons.Filled.EditNote,
                    title = "手动录入",
                    subtitle = "自己填热量和营养",
                    enabled = true,
                    onClick = onManual,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualFoodDialog(
    meal: MealType,
    onDismiss: () -> Unit,
    onSave: (FoodDraft) -> Unit,
    initial: FoodDraft? = null,
    isEditing: Boolean = false,
    estimate: MacrosEstimate = MacrosEstimate.Idle,
    onRequestEstimate: (String, String) -> Unit = { _, _ -> },
    onClearEstimate: () -> Unit = {},
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var portion by remember { mutableStateOf(initial?.portion ?: "1 份") }
    var kcal by remember { mutableStateOf(initial?.kcal?.let { formatNumber(it) } ?: "") }
    var protein by remember { mutableStateOf(initial?.protein?.let { formatNumber(it) } ?: "") }
    var fat by remember { mutableStateOf(initial?.fat?.let { formatNumber(it) } ?: "") }
    var carbs by remember { mutableStateOf(initial?.carbs?.let { formatNumber(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    // 用户一旦自己改过热量/营养，就不再自动估算覆盖
    var numbersEdited by remember { mutableStateOf(false) }
    // 点保存后等 AI 结果再入账
    var pendingSave by remember { mutableStateOf(false) }
    val initialName = initial?.name ?: ""
    val initialPortion = initial?.portion ?: "1 份"
    val nameOrPortionChanged = name.trim() != initialName.trim() || portion.trim() != initialPortion.trim()

    // 只有「保存」时才估算：拿到结果就直接入账
    LaunchedEffect(estimate, pendingSave) {
        if (!pendingSave) return@LaunchedEffect
        when (estimate) {
            is MacrosEstimate.Done -> {
                pendingSave = false
                val draft = estimate.draft
                if (draft.kcal <= 0f) {
                    error = "估算出的热量为 0，请手动填写"
                } else {
                    onSave(
                        FoodDraft(
                            name = name.trim(),
                            portion = portion.trim().ifBlank { "1 份" },
                            kcal = draft.kcal,
                            protein = draft.protein,
                            fat = draft.fat,
                            carbs = draft.carbs,
                        )
                    )
                }
            }

            is MacrosEstimate.Unavailable -> {
                pendingSave = false
                error = "自动估算失败：${estimate.message}"
            }

            else -> Unit
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (isEditing) "编辑食物 · ${meal.label}" else "手动录入 · ${meal.label}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(12.dp))
                KeyboardGuardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("食物名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                KeyboardGuardTextField(
                    value = portion,
                    onValueChange = { portion = it },
                    label = { Text("份量（如 150g）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyboardGuardTextField(
                        value = kcal,
                        onValueChange = {
                            numbersEdited = true
                            kcal = it.filter { c -> c.isDigit() || c == '.' }.take(5)
                        },
                        label = { Text("热量 kcal") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("蛋白质 g", protein, { numbersEdited = true; protein = it }, Modifier.weight(1f))
                    NumberField("碳水 g", carbs, { numbersEdited = true; carbs = it }, Modifier.weight(1f))
                    NumberField("脂肪 g", fat, { numbersEdited = true; fat = it }, Modifier.weight(1f))
                }
                // 固定一行高度的提示：状态切换时不让弹窗高度跳动
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
                    when {
                        estimate is MacrosEstimate.Loading || pendingSave -> Row(verticalAlignment = Alignment.CenterVertically) {
                            ThinkingDots()
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "正在估算营养并保存…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        estimate is MacrosEstimate.Unavailable -> Text(
                            "未自动估算：${estimate.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        numbersEdited -> Text(
                            "已按你填的数字保存，不再自动估算",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> Text(
                            "改了名称或份量后，保存时会自动估算一次营养",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            when {
                                name.isBlank() -> error = "请填写食物名称"
                                // 没自己填营养、且名称/份量有改动 → 保存时让 AI 估算一次
                                !numbersEdited && nameOrPortionChanged -> {
                                    error = null
                                    onClearEstimate()
                                    pendingSave = true
                                    onRequestEstimate(name, portion)
                                }
                                (kcal.toFloatOrNull() ?: 0f) <= 0f -> error = "请填写热量（或让 AI 自动估算）"
                                else -> onSave(
                                    FoodDraft(
                                        name = name.trim(),
                                        portion = portion.trim().ifBlank { "1 份" },
                                        kcal = kcal.toFloatOrNull() ?: 0f,
                                        protein = protein.toFloatOrNull() ?: 0f,
                                        fat = fat.toFloatOrNull() ?: 0f,
                                        carbs = carbs.toFloatOrNull() ?: 0f,
                                    )
                                )
                            }
                        },
                        enabled = !pendingSave,
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyboardGuardTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { c -> c.isDigit() || c == '.' }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun AnalyzingDialog(onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThinkingDots()
                Spacer(Modifier.height(12.dp))
                Text("正在识别食物…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "图片会发送给已配置的 DeepSeek 接口，仅用于本次识别。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    }
}

@Composable
private fun AiErrorDialog(message: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp)) {
                Text("识别失败", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("知道了") }
                }
            }
        }
    }
}

private data class DraftInput(
    val name: String,
    val portion: String,
    val kcal: String,
    val protein: String,
    val fat: String,
    val carbs: String,
)

@Composable
private fun FoodDraftDialog(
    initialMeal: MealType,
    drafts: List<FoodDraft>,
    onConfirm: (MealType, List<FoodDraft>) -> Unit,
    onDismiss: () -> Unit,
) {
    var meal by remember { mutableStateOf(initialMeal) }
    val inputs = remember(drafts) {
        mutableStateListOf(*drafts.map {
            DraftInput(
                name = it.name,
                portion = it.portion,
                kcal = formatNumber(it.kcal),
                protein = formatNumber(it.protein),
                fat = formatNumber(it.fat),
                carbs = formatNumber(it.carbs),
            )
        }.toTypedArray())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("识别结果（估算，可修改）", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "份量和营养都是 AI 估算，建议核对后入账。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.entries.forEach { option ->
                        val selected = option == meal
                        val bg by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            label = "mealChip",
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            modifier = Modifier.pressScaleClickable { meal = option },
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                inputs.forEachIndexed { index, input ->
                    DraftCard(
                        input = input,
                        onChange = { inputs[index] = it },
                        onRemove = { inputs.removeAt(index) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val parsed = inputs.mapNotNull { input ->
                                if (input.name.isBlank()) null else FoodDraft(
                                    name = input.name.trim(),
                                    portion = input.portion.trim().ifBlank { "1 份" },
                                    kcal = input.kcal.toFloatOrNull() ?: 0f,
                                    protein = input.protein.toFloatOrNull() ?: 0f,
                                    fat = input.fat.toFloatOrNull() ?: 0f,
                                    carbs = input.carbs.toFloatOrNull() ?: 0f,
                                )
                            }
                            onConfirm(meal, parsed)
                        },
                        enabled = inputs.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("确认入账") }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    input: DraftInput,
    onChange: (DraftInput) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyboardGuardTextField(
                    value = input.name,
                    onValueChange = { onChange(input.copy(name = it)) },
                    label = { Text("食物") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "移除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyboardGuardTextField(
                    value = input.portion,
                    onValueChange = { onChange(input.copy(portion = it)) },
                    label = { Text("份量") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    "热量",
                    input.kcal,
                    { onChange(input.copy(kcal = it)) },
                    Modifier.weight(0.8f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("蛋白质", input.protein, { onChange(input.copy(protein = it)) }, Modifier.weight(1f))
                NumberField("碳水", input.carbs, { onChange(input.copy(carbs = it)) }, Modifier.weight(1f))
                NumberField("脂肪", input.fat, { onChange(input.copy(fat = it)) }, Modifier.weight(1f))
            }
        }
    }
}

private fun formatNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)
