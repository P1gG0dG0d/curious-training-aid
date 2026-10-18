package com.haoqi.training

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.haoqi.training.di.AppContainer
import com.haoqi.training.timer.RestTimerService
import com.haoqi.training.ui.components.pressScaleClickable
import com.haoqi.training.ui.diet.DietScreen
import com.haoqi.training.ui.diet.DietViewModel
import com.haoqi.training.ui.settings.SettingsScreen
import com.haoqi.training.ui.settings.SettingsViewModel
import com.haoqi.training.ui.theme.CuriousTrainingTheme
import com.haoqi.training.ui.training.TrainingScreen
import com.haoqi.training.ui.training.TrainingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CuriousTrainingTheme {
                AppRoot()
            }
        }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    TRAINING("训练", Icons.Filled.FitnessCenter),
    DIET("饮食", Icons.Filled.Restaurant),
}

// initializer 以泛型类型作为注册键，不能把 VM 擦成 ViewModel 基类。
private inline fun <reified VM : ViewModel> appViewModelFactory(
    noinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer<VM> {
            val app = this[APPLICATION_KEY] as App
            create(app.container)
        }
    }

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val trainingViewModel: TrainingViewModel = viewModel(
        factory = appViewModelFactory { TrainingViewModel(it.trainingRepository) }
    )
    val dietViewModel: DietViewModel = viewModel(
        factory = appViewModelFactory {
            DietViewModel(it.dietRepository, it.profileRepository, it.apiConfigStore)
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = appViewModelFactory {
            SettingsViewModel(it.profileRepository, it.apiConfigStore)
        }
    )

    val trainingState by trainingViewModel.uiState.collectAsStateWithLifecycle()
    val dietState by dietViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var tabName by rememberSaveable { mutableStateOf(AppTab.TRAINING.name) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val currentTab = remember(tabName) { AppTab.valueOf(tabName) }

    // 回到前台时检查是否跨过午夜：饮食页自动切到新的一天
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) dietViewModel.refreshDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ 通知权限：休息计时的通知需要它（没给也能计时，只是通知不显示）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    AnimatedContent(
        targetState = showSettings,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { direction * it / 5 }) togetherWith
                (fadeOut(tween(180)) + slideOutHorizontally(tween(280)) { -direction * it / 5 })
        },
        label = "settingsTransition",
    ) { settingsVisible ->
    if (settingsVisible) {
        SettingsScreen(
            state = settingsState,
            onBack = { showSettings = false },
            onSaveProfile = settingsViewModel::saveProfile,
            onSaveApiConfig = settingsViewModel::saveApiConfig,
            onTestConnection = settingsViewModel::testConnection,
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                BottomNav(
                    selected = currentTab,
                    onSelect = { tabName = it.name },
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val direction = if (targetState == AppTab.DIET) 1 else -1
                    (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { direction * it / 8 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -direction * it / 8 })
                },
                label = "tabTransition",
            ) { displayedTab ->
            when (displayedTab) {
                AppTab.TRAINING -> TrainingScreen(
                    state = trainingState,
                    onSelectDay = trainingViewModel::selectDay,
                    onAddDay = trainingViewModel::addDay,
                    onRenameDay = trainingViewModel::renameDay,
                    onRemoveDay = trainingViewModel::removeSelectedDay,
                    onSaveExercise = trainingViewModel::saveExercise,
                    onDeleteExercise = trainingViewModel::deleteExercise,
                    onToggleSet = { exercise, setIndex, wasDone ->
                        trainingViewModel.toggleSet(exercise, setIndex, wasDone) { seconds, label ->
                            RestTimerService.start(context, seconds, label)
                        }
                    },
                    onToggleDayCompleted = { day, completed ->
                        trainingViewModel.setDayCompleted(day.id, completed)
                    },
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.padding(padding),
                )

                AppTab.DIET -> DietScreen(
                    state = dietState,
                    apiConfig = settingsState.apiConfig,
                    onAddFood = { date, meal, name, portion, kcal, protein, fat, carbs, estimated ->
                        dietViewModel.addEntry(date, meal, name, portion, kcal, protein, fat, carbs, estimated)
                    },
                    onDeleteEntry = dietViewModel::deleteEntry,
                    onUpdateEntry = dietViewModel::updateEntry,
                    onAnalyzePhoto = dietViewModel::analyzePhoto,
                    onConfirmDrafts = dietViewModel::confirmDrafts,
                    onClearAiState = dietViewModel::clearAiState,
                    onSaveActivity = dietViewModel::saveActivity,
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.padding(padding),
                )
            }
            }
        }
    }
    }
}

@Composable
private fun BottomNav(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
        ) {
            AppTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressScaleClickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
