package com.haoqi.training.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.haoqi.training.data.db.ProfileEntity
import com.haoqi.training.data.prefs.ApiConfig
import com.haoqi.training.domain.BmrCalculator
import com.haoqi.training.domain.Sex
import com.haoqi.training.ui.components.GroupCard
import com.haoqi.training.ui.components.KeyboardGuardTextField
import com.haoqi.training.ui.components.SectionLabel
import com.haoqi.training.ui.components.ThinkingDots
import com.haoqi.training.ui.components.pressScaleClickable
import com.haoqi.training.ui.theme.DangerRed
import com.haoqi.training.ui.theme.SuccessGreen

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSaveProfile: (ProfileEntity) -> Unit,
    onSaveApiConfig: (ApiConfig) -> Unit,
    onTestConnection: (ApiConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sex by remember(state.profile.sex) { mutableStateOf(state.profile.sex) }
    var age by remember(state.profile.age) { mutableStateOf(state.profile.age.toString()) }
    var height by remember(state.profile.heightCm) { mutableStateOf(formatNumber(state.profile.heightCm)) }
    var weight by remember(state.profile.weightKg) { mutableStateOf(formatNumber(state.profile.weightKg)) }


    var baseUrl by remember(state.apiConfig.baseUrl) { mutableStateOf(state.apiConfig.baseUrl) }
    var model by remember(state.apiConfig.model) { mutableStateOf(state.apiConfig.model) }
    var apiKey by remember(state.apiConfig.apiKey) { mutableStateOf(state.apiConfig.apiKey) }

    val currentSex = runCatching { Sex.valueOf(sex) }.getOrDefault(Sex.MALE)
    val bmr = BmrCalculator.bmrKcal(
        sex = currentSex,
        weightKg = weight.toFloatOrNull() ?: 70f,
        heightCm = height.toFloatOrNull() ?: 175f,
        ageYears = age.toIntOrNull() ?: 30,
    )

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("个人资料")
            GroupCard {
                if (!state.profile.configured) {
                    Text(
                        "还没保存过资料：下面是占位数字，填好后点「保存资料」，热量对比才会用你的真实数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sex.entries.forEach { option ->
                        val active = option.name == sex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleClickable { sex = option.name },
                        ) {
                            Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberInput("年龄", age, { age = it.take(3) }, Modifier.weight(1f))
                    NumberInput("身高 cm", height, { height = it.take(5) }, Modifier.weight(1f))
                    NumberInput("体重 kg", weight, { weight = it.take(5) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "基础代谢估算 ${bmr.toInt()} kcal/天；活动消耗在饮食页按日填写",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onSaveProfile(
                            ProfileEntity(
                                sex = sex,
                                age = age.toIntOrNull() ?: 30,
                                heightCm = height.toFloatOrNull() ?: 175f,
                                weightKg = weight.toFloatOrNull() ?: 70f,
                                activityKcal = state.profile.activityKcal,
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存资料") }
            }

            SectionLabel("AI 拍照识别（DeepSeek）")
            GroupCard {
                KeyboardGuardTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                KeyboardGuardTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名（deepseek-flash 支持看图片）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                KeyboardGuardTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（只存本机）") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSaveApiConfig(ApiConfig(baseUrl = baseUrl, model = model, apiKey = apiKey))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                    Button(
                        onClick = { onTestConnection(ApiConfig(baseUrl = baseUrl, model = model, apiKey = apiKey)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("测试连接") }
                }
                Spacer(Modifier.height(8.dp))
                when (val test = state.testState) {
                    is TestState.Idle -> Text(
                        "测试会发一条最小请求，用来确认地址、模型和 Key 是否可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is TestState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinkingDots()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在测试连接…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is TestState.Ok -> Text(
                        test.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                    )

                    is TestState.Failed -> Text(
                        test.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                    )
                }
            }

            SectionLabel("隐私")
            GroupCard {
                Text(
                    "训练计划、饮食记录、个人资料只存本机，不上传任何服务器；只有你主动拍照识别时，照片会发送给你配置的 AI 接口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberInput(
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

private fun formatNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
