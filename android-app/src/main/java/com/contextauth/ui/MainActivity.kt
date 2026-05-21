package com.contextauth.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contextauth.core.ClockSyncState
import com.contextauth.core.CollectionStatus
import com.contextauth.core.DEFAULT_SERVER_URL
import com.contextauth.core.LocaleText
import com.contextauth.core.TaskCategory
import com.contextauth.BuildConfig
import com.contextauth.service.DataCollectionService
import com.contextauth.ui.theme.ContextAuthLabTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContextAuthLabTheme {
                MainApp(
                    viewModel = viewModel,
                    requestNotification = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
	                    },
	                    startForeground = {
	                        ContextCompat.startForegroundService(
	                            this,
	                            Intent(this, DataCollectionService::class.java).setAction(DataCollectionService.ACTION_START_COLLECTION)
	                        )
	                    },
                    stopForeground = {
                        startService(Intent(this, DataCollectionService::class.java).setAction(DataCollectionService.ACTION_STOP_COLLECTION))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.syncNow()
    }
}

private enum class Screen { CONSENT, ONBOARDING, HOME, TASKS, TASK_RUNNER, SETTINGS, RESEARCHER, DIAGNOSTICS }

private fun l(zh: String, en: String): String = LocaleText.pick(zh, en)

@Composable
private fun MainApp(
    viewModel: MainViewModel,
    requestNotification: () -> Unit,
    startForeground: () -> Unit,
    stopForeground: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by remember { mutableStateOf(if (state.settings.consentGranted) Screen.HOME else Screen.CONSENT) }
    var selectedTask by remember { mutableStateOf<TaskCategory?>(null) }
    var backStack by remember { mutableStateOf<List<Screen>>(emptyList()) }
    val snackbar = remember { SnackbarHostState() }
    fun navigate(next: Screen) {
        if (next != screen) {
            backStack = (backStack + screen).takeLast(12)
            screen = next
        }
    }
	    LaunchedEffect(state.settings.consentGranted) {
	        if (state.settings.consentGranted && screen == Screen.CONSENT) navigate(Screen.ONBOARDING)
	    }
	    LaunchedEffect(
	        screen,
	        state.status,
	        state.settings.consentGranted,
	        state.accessibilityEnabled,
	        state.batteryWhitelisted,
	        state.notificationAllowed,
	        state.serverReachable,
	        state.clock.synced,
	        state.settings.wifiOnly
	    ) {
	        if (screen != Screen.TASK_RUNNER && state.status != CollectionStatus.RUNNING && viewModel.canStart()) {
	            startForeground()
	            viewModel.startCollection()
	        }
	    }
    BackHandler {
        if (screen == Screen.TASK_RUNNER) {
            viewModel.stopCollection()
            stopForeground()
            screen = Screen.TASKS
            return@BackHandler
        }
        if (backStack.isNotEmpty()) {
            screen = backStack.last()
            backStack = backStack.dropLast(1)
        } else if (screen != Screen.HOME && state.settings.consentGranted) {
            screen = Screen.HOME
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (screen in listOf(Screen.HOME, Screen.TASKS, Screen.SETTINGS, Screen.DIAGNOSTICS)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
	                    NavItem(Screen.HOME, screen, Icons.Outlined.Home, l("首页", "Home")) { navigate(Screen.HOME) }
	                    NavItem(Screen.TASKS, screen, Icons.Outlined.PlayArrow, l("任务", "Tasks")) { navigate(Screen.TASKS) }
	                    NavItem(Screen.DIAGNOSTICS, screen, Icons.Outlined.BarChart, l("诊断", "Diagnostics")) { navigate(Screen.DIAGNOSTICS) }
	                    NavItem(Screen.SETTINGS, screen, Icons.Outlined.Settings, l("设置", "Settings")) { navigate(Screen.SETTINGS) }
                }
            }
        }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.CONSENT -> ConsentScreen(
                    onConsent = { viewModel.grantConsent(); navigate(Screen.ONBOARDING) },
                    onResearcher = { navigate(Screen.RESEARCHER) }
                )
                Screen.ONBOARDING -> OnboardingScreen(
                    state = state,
                    requestNotification = requestNotification,
                    onDone = { navigate(Screen.HOME) },
                    onResearcher = { navigate(Screen.RESEARCHER) }
                )
	                Screen.HOME -> HomeScreen(
	                    state = state,
	                    canStart = viewModel.canStart(),
	                    onPermissions = { screen = Screen.ONBOARDING },
	                    onCopy = { copyText(context, it) },
	                    onResearcher = { navigate(Screen.RESEARCHER) }
	                )
                Screen.TASKS -> BuiltInTasksScreen(
                    completed = state.completedTasks,
                    onTask = { task -> selectedTask = task; navigate(Screen.TASK_RUNNER) }
                )
                Screen.TASK_RUNNER -> selectedTask?.let { task ->
                    TaskRunnerScreen(
                        state = state,
                        task = task,
                        canStart = viewModel.canStart(),
                        onStart = { startForeground(); viewModel.startCollection(task) },
                        onDone = {
                            viewModel.markTaskComplete(task)
                            viewModel.stopCollection()
                            stopForeground()
                            navigate(Screen.TASKS)
                        },
                        onBack = { viewModel.stopCollection(); stopForeground(); navigate(Screen.TASKS) },
                        snackbar = snackbar
                    )
                }
                Screen.SETTINGS -> SettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onResearcher = { navigate(Screen.RESEARCHER) }
                )
                Screen.RESEARCHER -> ResearcherSettingsScreen(
                    state = state,
                    onBack = {
                        if (backStack.isNotEmpty()) {
                            screen = backStack.last()
                            backStack = backStack.dropLast(1)
                        } else {
                            screen = if (state.settings.consentGranted) Screen.HOME else Screen.CONSENT
                        }
                    },
                    onSaveUrl = viewModel::updateServerUrl,
                    onTestConnection = viewModel::testConnection,
                    onReset = viewModel::resetServerUrl,
                    onClearQueue = viewModel::clearQueue,
                    onExport = { viewModel.exportDiagnostics() }
                )
                Screen.DIAGNOSTICS -> DiagnosticsScreen(state = state, onExport = { viewModel.exportDiagnostics() })
            }
        }
    }
}

@Composable
private fun NavItem(screen: Screen, current: Screen, icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(58.dp)) {
        val tint = if (current == screen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ConsentScreen(onConsent: () -> Unit, onResearcher: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
	    Page(
	        title = "ContextAuthLab",
	        footer = { AboutFooter(onResearcher) }
	    ) {
	        Text(l("用于 Android 前端 UI/组件上下文与运动传感器的持续身份认证研究数据采集。", "Research data collection for continuous authentication using Android UI/component context and motion sensors."), style = MaterialTheme.typography.titleMedium)
	        RequirementCard(
	            l("显著披露", "Key disclosure"),
	            listOf(
	                l("采集加速度计、陀螺仪、磁力计数据。", "The app collects accelerometer, gyroscope, and magnetic-field data."),
	                l("使用 AccessibilityService 读取可访问组件结构，不点击、不输入、不截图。", "AccessibilityService reads accessible component structure only; it does not click, type, or take screenshots."),
	                l("不会上传原始输入框文本，密码节点会整棵丢弃。", "Raw input-field text is not uploaded, and password nodes are dropped completely."),
	                l("数据先在端侧脱敏，再 JSON 序列化、LZ4 frame 压缩，通过 HTTPS/TLS 上传。", "Data is redacted on device, serialized as JSON, compressed with LZ4 frame, and uploaded over HTTPS/TLS."),
	                l("本阶段不使用内容 AES 加密；链路安全依赖 HTTPS/TLS 1.2+。", "This stage does not use payload AES encryption; transport security relies on HTTPS/TLS 1.2+."),
	                l("屏幕息屏或锁屏时不会采集任何数据。", "No data is collected while the screen is off or locked.")
	            )
	        )
	        Row(verticalAlignment = Alignment.CenterVertically) {
	            Checkbox(checked = checked, onCheckedChange = { checked = it })
	            Text(l("我已阅读并同意以上科研数据采集说明", "I have read and agree to the research data collection notice"))
	        }
	        Button(onClick = onConsent, enabled = checked, modifier = Modifier.fillMaxWidth()) {
	            Text(l("同意并进入权限引导", "Agree and open permission guide"))
	        }
	    }
	}

@Composable
private fun OnboardingScreen(
    state: com.contextauth.core.UiState,
    requestNotification: () -> Unit,
    onDone: () -> Unit,
    onResearcher: () -> Unit
) {
    val context = LocalContext.current
	    Page(l("权限引导", "Permission Guide"), footer = { AboutFooter(onResearcher) }) {
	        PermissionStep(
	            title = l("开启无障碍服务", "Enable AccessibilityService"),
	            done = state.accessibilityEnabled,
	            body = l("在列表中找到 ContextAuthLab，启用后返回 App。仅读取组件结构，不执行任何动作。", "Find ContextAuthLab in the list, enable it, then return. It reads component structure only and performs no actions."),
	            action = { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
	        )
	        PermissionStep(
	            title = l("电池优化与后台运行", "Battery Optimization and Background Running"),
	            done = state.batteryWhitelisted,
	            body = manufacturerGuide(),
            action = {
                if (Build.VERSION.SDK_INT >= 23) {
                    context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                } else {
                    context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }
	        )
	        PermissionStep(
	            title = l("通知权限", "Notification Permission"),
	            done = state.notificationAllowed,
	            body = l("用于前台 Service 通知，便于随时看到采集进行中状态。", "Used for the foreground-service notification so collection status remains visible."),
	            action = requestNotification
	        )
	        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
	            Text(l("已完成自检", "Checks completed"))
	        }
	    }
	}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    state: com.contextauth.core.UiState,
    canStart: Boolean,
    onPermissions: () -> Unit,
    onCopy: (String) -> Unit,
    onResearcher: () -> Unit
) {
    var showDeviceId by remember { mutableStateOf(false) }
    val hiddenDetector = remember { HiddenGestureDetector() }
    Page(l("首页", "Home")) {
        if (state.settings.serverOverridden) {
            Banner(l("当前上报目标已被研究者覆盖：", "Research endpoint overridden: ") + hostOnly(state.settings.serverUrl))
        }
        ServerConnectionHeader(
            reachable = state.serverReachable,
            lastCheckedAtWallMillis = state.lastServerHealthAtWallMillis,
            latestMessage = displayMessage(state.diagnostics.lastServerMessage)
        )
        CardBlock(l("上报目标与设备", "Endpoint and Device")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Server host", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    hostOnly(state.settings.serverUrl),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.hiddenResearcherGesture(hiddenDetector, onResearcher)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l("研究 device_id", "Research device_id"), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    maskDeviceId(state.deviceId),
                    modifier = Modifier.combinedClickable(
                        onClick = { showDeviceId = true },
                        onLongClick = { showDeviceId = true }
                    ),
                    fontWeight = FontWeight.SemiBold
                )
            }
            InfoRow(l("规则版本", "Rule version"), state.settings.ruleVersion)
        }
        CardBlock(l("权限与状态", "Permissions and Status")) {
            InfoRow("AccessibilityService", yesNo(state.accessibilityEnabled))
            InfoRow(l("电池优化白名单", "Battery optimization allowlist"), yesNo(state.batteryWhitelisted))
            InfoRow(l("通知权限", "Notification permission"), yesNo(state.notificationAllowed))
            InfoRow(l("服务器连通", "Server reachable"), yesNo(state.serverReachable))
            InfoRow("ClockSync", clockSyncSummary(state.clock, includeDrift = true))
        }
        CardBlock(l("传感器", "Sensors")) {
            InfoRow("Accelerometer", "${yesNo(state.accelerometerAvailable)} / ${formatHz(state.accelerometerCollectionHz)}")
            InfoRow("Gyroscope", "${yesNo(state.gyroscopeAvailable)} / ${formatHz(state.gyroscopeCollectionHz)}")
            InfoRow("Magnetic field", "${yesNo(state.magnetometerAvailable)} / ${formatHz(state.magnetometerCollectionHz)}")
        }
        if (!canStart) {
            Banner(l("等待权限、服务器连通、ClockSync 和脱敏规则获取完成后会自动开始采集。", "Collection starts automatically after permissions, server health, ClockSync, and redaction rules are ready."))
            TextButton(onClick = onPermissions) { Text(l("前往权限引导", "Open permission guide")) }
        } else {
            Banner(l("自检已通过，App 会自动保持采集与上传。", "Checks passed. The app keeps collection and upload running automatically."))
        }
    }
    if (showDeviceId) {
        AlertDialog(
            onDismissRequest = { showDeviceId = false },
            title = { Text(l("完整 device_id", "Full device_id")) },
            text = { Text(state.deviceId) },
            confirmButton = {
                TextButton(onClick = { onCopy(state.deviceId); showDeviceId = false }) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Text(l("复制", "Copy"))
                }
            }
        )
    }
}

@Composable
private fun BuiltInTasksScreen(completed: Set<TaskCategory>, onTask: (TaskCategory) -> Unit) {
    val taskCount = TaskCategory.entries.size
    Page(l("内置任务", "Built-in Tasks")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = completed.size / taskCount.toFloat(), modifier = Modifier.size(72.dp))
                Text("${completed.size}/$taskCount", fontWeight = FontWeight.SemiBold)
            }
            Column {
                Text(l("每项进入后自动记录 30 秒，返回会退出本项。", "Each task records automatically for 30 seconds after entry; Back exits the task."), style = MaterialTheme.typography.bodyLarge)
                Text(TaskCategory.localizedPostureGuide(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TaskCategory.entries.forEach { task ->
            Card(
                onClick = { onTask(task) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
	                    Column(Modifier.weight(1f)) {
	                        AssistChip(onClick = {}, label = { Text(task.name) })
	                        Text(task.localizedDisplayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
	                        Text(task.localizedSubtitle(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
	                    }
	                    if (task in completed) AssistChip(onClick = {}, label = { Text(l("已记录", "Recorded")) })
	                }
	            }
	        }
	        OutlinedButton(onClick = { onTask(TaskCategory.C0) }, modifier = Modifier.fillMaxWidth()) {
	            Text(l("按顺序完成全部 7 项", "Complete all 7 tasks in order"))
	        }
	        if (completed.size == taskCount) {
	            Text(l("数据采集已完成，感谢配合。本次共贡献 7 项任务数据。", "Data collection is complete. Thank you; this session contributed data for 7 tasks."))
	        }
	    }
	}

@Composable
private fun TaskRunnerScreen(
    state: com.contextauth.core.UiState,
    task: TaskCategory,
    canStart: Boolean,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var seconds by remember(task) { mutableIntStateOf(state.settings.taskSeconds) }
    var running by remember(task) { mutableStateOf(false) }
    var autoStarted by remember(task) { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { context.findActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(task, canStart) {
        if (!autoStarted && canStart) {
            seconds = state.settings.taskSeconds
            autoStarted = true
            running = true
            onStart()
        }
    }
    LaunchedEffect(running, task) {
        if (!running) return@LaunchedEffect
        context.findActivity()?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        while (running && seconds > 0) {
            delay(1_000)
            seconds -= 1
            if (seconds == 3) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
	        if (running && seconds == 0) {
	            running = false
	            snackbar.showSnackbar(l("已记录 ${state.settings.taskSeconds} 秒数据", "Recorded ${state.settings.taskSeconds} seconds of data"))
	            onDone()
	        }
    }
	    Page(task.localizedDisplayName()) {
	        Row(verticalAlignment = Alignment.CenterVertically) {
	            TextButton(onClick = onBack) { Text(l("返回", "Back")) }
	            AssistChip(onClick = {}, label = { Text(task.name) })
	            Spacer(Modifier.weight(1f))
	            IconButton(onClick = { showPrivacy = true }) { Icon(Icons.Outlined.Security, l("隐私提示", "Privacy notice")) }
	        }
	        CardBlock(l("坐姿与动作", "Posture and Motion")) {
	            Text(TaskCategory.localizedPostureGuide())
	            Text(task.localizedSubtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
	        }
        LinearProgressIndicator(
            progress = 1f - seconds / state.settings.taskSeconds.toFloat(),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp))
        )
	        Text(l("剩余 ${seconds}s", "${seconds}s remaining"), style = MaterialTheme.typography.titleMedium)
            if (!autoStarted) {
                Banner(l("等待采集条件满足后将自动开始 30 秒计时。", "The 30-second timer will start automatically when collection requirements are met."))
            }
	        TaskContent(task)
	        CardBlock(l("采集状态", "Collection Status")) {
	            InfoRow(l("采集目标", "Collection target"), sensorTargetSummary(state))
	            InfoRow(l("传感器可用", "Sensors available"), "acc ${yesNo(state.accelerometerAvailable)} / gyro ${yesNo(state.gyroscopeAvailable)} / mag ${yesNo(state.magnetometerAvailable)}")
	            InfoRow(l("已记录事件数", "Recorded events"), state.diagnostics.eventBuckets.values.sum().toString())
	            InfoRow(l("最近 batch", "Latest batch"), state.diagnostics.lastBatchId)
	            InfoRow("ClockSync", clockSyncSummary(state.clock, includeDrift = false))
	        }
            AssistChip(
                onClick = {},
                label = { Text(if (running) l("自动记录中", "Auto recording") else l("等待自动开始", "Waiting to auto-start")) }
            )
	    }
	    if (showPrivacy) {
	        AlertDialog(
	            onDismissRequest = { showPrivacy = false },
	            title = { Text(l("隐私提示", "Privacy Notice")) },
	            text = { Text(l("本任务不会记录原始内容；仅记录传感器和组件结构。", "This task does not record raw content; it records sensors and component structure only.")) },
	            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(l("知道了", "OK")) } }
	        )
	    }
	}

@Composable
private fun TaskContent(task: TaskCategory) {
    CardBlock(task.localizedTaskName()) {
        when (task) {
            TaskCategory.C0 -> QuietHoldClock()
            TaskCategory.C1 -> ProtocolReader()
            TaskCategory.C2 -> ResearchFeed()
            TaskCategory.C3 -> CopyWritingTask()
            TaskCategory.C4 -> PreferenceControls()
            TaskCategory.C5 -> TiltMazeGame()
            TaskCategory.C6 -> WristRotationGuide()
        }
    }
}

@Composable
private fun QuietHoldClock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    Text(SimpleDateFormat("HH:mm:ss", Locale.US).format(now), style = MaterialTheme.typography.displayMedium)
    Text(l("请自然持机并看着计时数字，尽量不点击、不滑动、不改变握持方式。", "Hold the phone naturally and watch the timer. Avoid tapping, scrolling, or changing your grip."))
}

@Composable
private fun ProtocolReader() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text(protocolText(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResearchFeed() {
    val items = listOf(
        l("终端体验研究关注真实持机姿态、触控节奏和页面组件结构之间的对应关系。", "Terminal experience research studies how natural grip posture, touch rhythm, and page component structure relate to each other."),
        l("连续滑动、短暂停顿、展开信息和返回上一段内容，会形成不同的加速度与陀螺仪模式。", "Continuous scrolling, short pauses, expanding items, and navigating back create different accelerometer and gyroscope patterns."),
        l("采集系统只记录脱敏后的 UI 结构，例如按钮、列表、输入框数量和事件类型。", "The collection system records only redacted UI structure, such as counts of buttons, lists, input fields, and event types."),
        l("通信终端业务常见场景包括设备连接、网络体验、业务办理、售后支持与应用服务。", "Common terminal-service scenarios include device connection, network experience, service handling, support, and app services."),
        l("同一页面内容不会作为身份特征直接使用，后续分析更关注动作稳定性和上下文对齐质量。", "Page content itself is not used directly as an identity feature; later analysis focuses on motion stability and context alignment.")
    )
    items.forEachIndexed { index, text ->
        var expanded by remember { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(l("资讯 ${index + 1}", "Item ${index + 1}"), fontWeight = FontWeight.SemiBold)
                Text(if (expanded) "$text ${l("请按平时阅读习惯继续浏览，不需要刻意加快速度。", "Keep reading naturally; there is no need to speed up.")}" else text)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) l("收起", "Collapse") else l("展开", "Expand")) }
            }
        }
    }
}

@Composable
private fun CopyWritingTask() {
    var value by remember { mutableStateOf("") }
    val prompt = l(
        "中国电信终端体验记录：5G-A signal OK; Wi-Fi 6, eSIM, NFC, $%^&*; ticket #A-27, 09:30。请重复抄写直到本轮 30s 结束。",
        "Telecom terminal experience note: 5G-A signal OK; Wi-Fi 6, eSIM, NFC, $%^&*; ticket #A-27, 09:30. Copy this repeatedly until the 30-second round ends."
    )
    Text(l("请使用自己最舒适的输入方式，如 9 键、全键盘、手写或语音输入。输入内容只保留在本地输入框中，不会持久化或上传。", "Use your most comfortable input method, such as 9-key, full keyboard, handwriting, or voice. Text stays only in this local field and is not persisted or uploaded."))
    Text(prompt, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(l("段落抄写输入框", "Paragraph copy field")) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4
    )
    Text(l("当前本地字符数：${value.length}", "Local character count: ${value.length}"))
}

@Composable
private fun PreferenceControls() {
    var tab by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableStateOf(62f) }
    var volume by remember { mutableStateOf(36f) }
    var selectedNetwork by remember { mutableStateOf("5G") }
    var selectedTextSize by remember { mutableStateOf("standard") }
    var wifi by remember { mutableStateOf(true) }
    var bluetooth by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(true) }
    var darkMode by remember { mutableStateOf(false) }
    var haptics by remember { mutableStateOf(true) }
    var cloudBackup by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    TabRow(selectedTabIndex = tab) {
        listOf(
            l("连接", "Connections"),
            l("显示", "Display"),
            l("声音", "Sound"),
            l("隐私", "Privacy")
        ).forEachIndexed { index, title ->
            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
        }
    }
    when (tab) {
        0 -> {
            SwitchRow(l("Wi-Fi", "Wi-Fi"), wifi) { wifi = it }
            SwitchRow(l("蓝牙", "Bluetooth"), bluetooth) { bluetooth = it }
            SwitchRow(l("定位服务", "Location services"), location) { location = it }
            Text(l("首选网络模式", "Preferred network mode"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("5G", "4G", l("自动", "Auto")).forEach { option ->
                    FilterChip(selected = selectedNetwork == option, onClick = { selectedNetwork = option }, label = { Text(option) })
                }
            }
        }
        1 -> {
            SwitchRow(l("深色模式", "Dark mode"), darkMode) { darkMode = it }
            Text(l("屏幕亮度：${brightness.roundToInt()}%", "Brightness: ${brightness.roundToInt()}%"))
            Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..100f)
            Text(l("文字大小", "Text size"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                "small" to l("小", "Small"),
                "standard" to l("标准", "Standard"),
                "large" to l("大", "Large")
            ).forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedTextSize == key, onClick = { selectedTextSize = key })
                    Text(label)
                }
            }
        }
        2 -> {
            SwitchRow(l("触感反馈", "Haptic feedback"), haptics) { haptics = it }
            Text(l("媒体音量：${volume.roundToInt()}%", "Media volume: ${volume.roundToInt()}%"))
            Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..100f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { volume = 0f }) { Text(l("静音", "Mute")) }
                OutlinedButton(onClick = { volume = 50f }) { Text(l("恢复", "Restore")) }
            }
        }
        else -> {
            SwitchRow(l("云端备份", "Cloud backup"), cloudBackup) { cloudBackup = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = wifi && cloudBackup, onCheckedChange = { checked -> cloudBackup = checked; if (checked) wifi = true })
                Text(l("仅在 Wi-Fi 下备份", "Back up on Wi-Fi only"))
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(l("本地备注，不上传原文", "Local note, raw text not uploaded")) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TiltMazeGame() {
    val context = LocalContext.current
    var position by remember { mutableStateOf(TiltMazeModel.start) }
    var velocity by remember { mutableStateOf(MazeVelocity(0f, 0f)) }
    var lastSensorNanos by remember { mutableStateOf(0L) }
    var reached by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val dt = if (lastSensorNanos == 0L) 0.016f else ((event.timestamp - lastSensorNanos) / 1_000_000_000f)
                lastSensorNanos = event.timestamp
                val (nextPosition, nextVelocity) = MazePhysics.step(
                    position = position,
                    velocity = velocity,
                    tiltX = -event.values.getOrElse(0) { 0f },
                    tiltY = event.values.getOrElse(1) { 0f },
                    dtSeconds = dt
                )
                position = nextPosition
                velocity = nextVelocity
                reached = MazePhysics.reachedExit(nextPosition)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    Text(l("请通过倾斜手机移动小球，沿通道从左上起点到达右下出口。碰到墙体会停止对应方向的运动，请缓慢调整角度。", "Tilt the phone to move the ball from the upper-left start to the lower-right exit. Wall contact stops movement on that axis, so adjust angles slowly."))
    if (reached) {
        AssistChip(onClick = { position = TiltMazeModel.start; velocity = MazeVelocity(0f, 0f); reached = false }, label = { Text(l("已到达出口，点击重置", "Exit reached, tap to reset")) })
    }
    val wall = MaterialTheme.colorScheme.outline
    val ball = MaterialTheme.colorScheme.primary
    val finish = Color(0xFF2E7D32)
    val start = Color(0xFF1565C0)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        fun MazeRect.toOffset() = Offset(size.width * left, size.height * top)
        fun MazeRect.toSize() = Size(size.width * (right - left), size.height * (bottom - top))
        drawRect(color = wall, style = Stroke(width = 6f))
        TiltMazeModel.walls.forEach { rect ->
            drawRoundRect(wall, topLeft = rect.toOffset(), size = rect.toSize(), cornerRadius = CornerRadius(5f, 5f))
        }
        drawRoundRect(
            color = finish,
            topLeft = TiltMazeModel.exit.toOffset(),
            size = TiltMazeModel.exit.toSize(),
            cornerRadius = CornerRadius(12f, 12f)
        )
        drawLine(Color.White, Offset(size.width * 0.84f, size.height * 0.82f), Offset(size.width * 0.88f, size.height * 0.88f), strokeWidth = 6f)
        drawLine(Color.White, Offset(size.width * 0.88f, size.height * 0.88f), Offset(size.width * 0.94f, size.height * 0.80f), strokeWidth = 6f)
        drawCircle(start, radius = 18f, center = Offset(size.width * TiltMazeModel.start.x, size.height * TiltMazeModel.start.y), style = Stroke(width = 4f))
        drawCircle(ball, radius = size.minDimension * TiltMazeModel.BALL_RADIUS, center = Offset(size.width * position.x, size.height * position.y))
    }
}

@Composable
private fun WristRotationGuide() {
    val transition = rememberInfiniteTransition(label = "wrist")
    val sideAngle by transition.animateFloat(
        initialValue = -34f,
        targetValue = 34f,
        animationSpec = infiniteRepeatable(animation = tween(1700), repeatMode = RepeatMode.Reverse),
        label = "side_angle"
    )
    val flexAngle by transition.animateFloat(
        initialValue = -26f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(animation = tween(1650), repeatMode = RepeatMode.Reverse),
        label = "flex_angle"
    )
    val facePlaneColor = MaterialTheme.colorScheme.tertiary
    val screenPlaneColor = MaterialTheme.colorScheme.primary
    val motionColor = MaterialTheme.colorScheme.secondary
    Text(l("保持前臂稳定，想象脸前有一块固定的人脸平面，手机屏幕是一块会随手腕转动的屏幕平面。动作时让面部始终朝向手机，不要用头部追随手机。", "Keep the forearm still. Imagine a fixed face plane in front of your face and the phone screen as a screen plane that rotates with the wrist. Keep your face oriented toward the phone; do not follow it with your head."))
    WristGuideLegend(facePlaneColor, screenPlaneColor, motionColor)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 560.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WristMotionPanel(
                    label = l("左右摇摆", "Left-right swing"),
                    hint = l("蓝色屏幕面围绕手腕向左/右扫过，橙色人脸面保持固定。", "The blue screen plane sweeps left/right around the wrist while the orange face plane stays fixed."),
                    detail = l("面部朝向保持在中线，不要转头追手机。", "Keep face direction on the center line; do not turn your head to follow the phone."),
                    angle = sideAngle,
                    mode = WristMotionMode.SIDE_TO_SIDE,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                WristMotionPanel(
                    label = l("前后内收", "Forward-back flexion"),
                    hint = l("蓝色屏幕面向面部靠近/远离，手腕是转动轴。", "The blue screen plane moves toward/away from the face, with the wrist as the pivot."),
                    detail = l("只弯曲手腕，前臂和头部尽量不动。", "Flex only the wrist; keep the forearm and head as still as possible."),
                    angle = flexAngle,
                    mode = WristMotionMode.FLEXION,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WristMotionPanel(
                    label = l("左右摇摆", "Left-right swing"),
                    hint = l("蓝色屏幕面围绕手腕向左/右扫过，橙色人脸面保持固定。", "The blue screen plane sweeps left/right around the wrist while the orange face plane stays fixed."),
                    detail = l("面部朝向保持在中线，不要转头追手机。", "Keep face direction on the center line; do not turn your head to follow the phone."),
                    angle = sideAngle,
                    mode = WristMotionMode.SIDE_TO_SIDE,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.weight(1f),
                )
                WristMotionPanel(
                    label = l("前后内收", "Forward-back flexion"),
                    hint = l("蓝色屏幕面向面部靠近/远离，手腕是转动轴。", "The blue screen plane moves toward/away from the face, with the wrist as the pivot."),
                    detail = l("只弯曲手腕，前臂和头部尽量不动。", "Flex only the wrist; keep the forearm and head as still as possible."),
                    angle = flexAngle,
                    mode = WristMotionMode.FLEXION,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class WristMotionMode { SIDE_TO_SIDE, FLEXION }

@Composable
private fun WristGuideLegend(facePlaneColor: Color, screenPlaneColor: Color, motionColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WristLegendItem(facePlaneColor, l("橙色线：人脸平面与面部朝向固定", "Orange line: fixed face plane and face direction"))
        WristLegendItem(screenPlaneColor, l("蓝色面：手机屏幕平面随手腕转动", "Blue plane: phone screen plane rotates with the wrist"))
        WristLegendItem(motionColor, l("绿色弧线：手腕转动方向与范围", "Green arc: wrist rotation direction and range"))
    }
}

@Composable
private fun WristLegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun rotatePoint(point: Offset, pivot: Offset, degrees: Float): Offset {
    val radians = degrees.toDouble() * PI / 180.0
    val dx = point.x - pivot.x
    val dy = point.y - pivot.y
    return Offset(
        x = pivot.x + (dx * cos(radians) - dy * sin(radians)).toFloat(),
        y = pivot.y + (dx * sin(radians) + dy * cos(radians)).toFloat()
    )
}

@Composable
private fun WristMotionPanel(
    label: String,
    hint: String,
    detail: String,
    angle: Float,
    mode: WristMotionMode,
    facePlaneColor: Color,
    screenPlaneColor: Color,
    motionColor: Color,
    modifier: Modifier = Modifier,
) {
    val arcColor = motionColor.copy(alpha = 0.30f)
    val endpointColor = motionColor.copy(alpha = 0.52f)
    val guideColor = MaterialTheme.colorScheme.secondary
    val panelSurface = MaterialTheme.colorScheme.surface
    val canvasBorder = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val facePlaneLabel = l("人脸平面", "face plane")
    val screenPlaneLabel = l("屏幕平面", "screen plane")
    val faceDirectionLabel = l("面部朝向", "face direction")
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(l("脸部朝向手机屏幕，头部不跟随摆动", "Face the phone screen; do not follow the swing with your head"), style = MaterialTheme.typography.bodySmall, color = guideColor, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(212.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, canvasBorder, RoundedCornerShape(14.dp))
            ) {
                val minSide = minOf(size.width, size.height)
                val skin = Color(0xFFE0B38F)
                val skinDark = Color(0xFFC6906D)
                val skinLine = Color(0xFFB87858)
                val phone = Color(0xFF1F2937)
                val phoneScreen = screenPlaneColor.copy(alpha = 0.82f)
                val hair = Color(0xFF3F2B23)
                val gaze = guideColor.copy(alpha = 0.70f)
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = labelColor.toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }

                fun drawLabel(text: String, x: Float, y: Float, align: Paint.Align = Paint.Align.CENTER) {
                    labelPaint.textAlign = align
                    drawContext.canvas.nativeCanvas.drawText(text, x, y, labelPaint)
                }

                fun drawArrow(color: Color, start: Offset, end: Offset, strokeWidth: Float = 3.5f, headLength: Float = minSide * 0.055f) {
                    drawLine(color, start, end, strokeWidth = strokeWidth)
                    val direction = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                    val spread = PI / 7.0
                    val left = Offset(
                        x = end.x - (headLength * cos(direction - spread)).toFloat(),
                        y = end.y - (headLength * sin(direction - spread)).toFloat()
                    )
                    val right = Offset(
                        x = end.x - (headLength * cos(direction + spread)).toFloat(),
                        y = end.y - (headLength * sin(direction + spread)).toFloat()
                    )
                    drawLine(color, end, left, strokeWidth = strokeWidth)
                    drawLine(color, end, right, strokeWidth = strokeWidth)
                }

                fun drawFrontFace(center: Offset) {
                    drawCircle(skin, radius = minSide * 0.105f, center = center)
                    drawArc(
                        color = hair,
                        startAngle = 190f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(center.x - minSide * 0.112f, center.y - minSide * 0.105f),
                        size = Size(minSide * 0.224f, minSide * 0.19f),
                        style = Stroke(width = minSide * 0.038f)
                    )
                    drawCircle(Color(0xFF263238), radius = minSide * 0.013f, center = Offset(center.x - minSide * 0.034f, center.y - minSide * 0.010f))
                    drawCircle(Color(0xFF263238), radius = minSide * 0.013f, center = Offset(center.x + minSide * 0.034f, center.y - minSide * 0.010f))
                    drawLine(Color(0xFF8D5B48), Offset(center.x - minSide * 0.035f, center.y + minSide * 0.052f), Offset(center.x + minSide * 0.035f, center.y + minSide * 0.052f), strokeWidth = minSide * 0.010f)
                }

                fun drawSideFace(center: Offset) {
                    drawCircle(skin, radius = minSide * 0.108f, center = center)
                    drawArc(
                        color = hair,
                        startAngle = 95f,
                        sweepAngle = 170f,
                        useCenter = false,
                        topLeft = Offset(center.x - minSide * 0.118f, center.y - minSide * 0.108f),
                        size = Size(minSide * 0.19f, minSide * 0.20f),
                        style = Stroke(width = minSide * 0.040f)
                    )
                    drawCircle(Color(0xFF263238), radius = minSide * 0.013f, center = Offset(center.x + minSide * 0.040f, center.y - minSide * 0.014f))
                    drawLine(Color(0xFF8D5B48), Offset(center.x + minSide * 0.060f, center.y + minSide * 0.012f), Offset(center.x + minSide * 0.106f, center.y + minSide * 0.028f), strokeWidth = minSide * 0.011f)
                    drawLine(Color(0xFF8D5B48), Offset(center.x + minSide * 0.030f, center.y + minSide * 0.058f), Offset(center.x + minSide * 0.080f, center.y + minSide * 0.060f), strokeWidth = minSide * 0.010f)
                }

                fun drawWristAndForearm(wrist: Offset) {
                    drawRoundRect(
                        color = skinDark,
                        topLeft = Offset(wrist.x - minSide * 0.085f, wrist.y - minSide * 0.015f),
                        size = Size(minSide * 0.17f, size.height - wrist.y + minSide * 0.08f),
                        cornerRadius = CornerRadius(minSide * 0.09f, minSide * 0.09f)
                    )
                    drawCircle(skinLine, radius = minSide * 0.11f, center = wrist)
                    drawCircle(skin, radius = minSide * 0.088f, center = wrist)
                }

                drawRoundRect(
                    color = panelSurface.copy(alpha = 0.72f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f, 24f)
                )

                if (mode == WristMotionMode.SIDE_TO_SIDE) {
                    val cx = size.width / 2f
                    val wrist = Offset(cx, size.height * 0.82f)
                    val face = Offset(cx, size.height * 0.16f)
                    val facePlaneY = size.height * 0.31f
                    val arcRadius = minSide * 0.47f
                    val baseArcPoint = Offset(cx, wrist.y - arcRadius)
                    val phoneHeight = minSide * 0.55f
                    val phoneWidth = minSide * 0.27f

                    drawFrontFace(face)
                    drawLine(facePlaneColor, Offset(cx - minSide * 0.34f, facePlaneY), Offset(cx + minSide * 0.34f, facePlaneY), strokeWidth = minSide * 0.025f)
                    drawLabel(facePlaneLabel, cx, facePlaneY - minSide * 0.035f)
                    drawArrow(gaze, Offset(cx, facePlaneY + minSide * 0.03f), Offset(cx, wrist.y - phoneHeight * 0.78f), strokeWidth = minSide * 0.014f)
                    drawLabel(faceDirectionLabel, cx + minSide * 0.26f, size.height * 0.47f, Paint.Align.LEFT)
                    drawArc(
                        color = arcColor,
                        startAngle = 236f,
                        sweepAngle = 68f,
                        useCenter = false,
                        topLeft = Offset(wrist.x - arcRadius, wrist.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = minSide * 0.024f)
                    )
                    listOf(-34f, 34f).forEach { limit ->
                        drawCircle(endpointColor, radius = minSide * 0.020f, center = rotatePoint(baseArcPoint, wrist, limit))
                    }
                    drawCircle(motionColor, radius = minSide * 0.026f, center = rotatePoint(baseArcPoint, wrist, angle))

                    drawWristAndForearm(wrist)

                    fun drawFrontGrip(alpha: Float) {
                        drawRoundRect(
                            color = skin.copy(alpha = alpha),
                            topLeft = Offset(cx - minSide * 0.17f, wrist.y - minSide * 0.30f),
                            size = Size(minSide * 0.34f, minSide * 0.34f),
                            cornerRadius = CornerRadius(minSide * 0.11f, minSide * 0.11f)
                        )
                        drawRoundRect(
                            color = phone.copy(alpha = alpha),
                            topLeft = Offset(cx - phoneWidth / 2f, wrist.y - phoneHeight - minSide * 0.03f),
                            size = Size(phoneWidth, phoneHeight),
                            cornerRadius = CornerRadius(minSide * 0.060f, minSide * 0.060f)
                        )
                        drawRoundRect(
                            color = phoneScreen.copy(alpha = alpha),
                            topLeft = Offset(cx - phoneWidth * 0.37f, wrist.y - phoneHeight * 0.88f),
                            size = Size(phoneWidth * 0.74f, phoneHeight * 0.72f),
                            cornerRadius = CornerRadius(minSide * 0.032f, minSide * 0.032f)
                        )
                        drawLine(
                            color = screenPlaneColor.copy(alpha = alpha),
                            start = Offset(cx - phoneWidth * 0.37f, wrist.y - phoneHeight * 0.53f),
                            end = Offset(cx + phoneWidth * 0.37f, wrist.y - phoneHeight * 0.53f),
                            strokeWidth = minSide * 0.022f
                        )
                        repeat(4) { index ->
                            val y = wrist.y - minSide * 0.28f + index * minSide * 0.070f
                            drawRoundRect(
                                color = skin.copy(alpha = alpha),
                                topLeft = Offset(cx + minSide * 0.115f, y),
                                size = Size(minSide * 0.115f, minSide * 0.050f),
                                cornerRadius = CornerRadius(minSide * 0.035f, minSide * 0.035f)
                            )
                        }
                        drawRoundRect(
                            color = skin.copy(alpha = alpha),
                            topLeft = Offset(cx - minSide * 0.235f, wrist.y - minSide * 0.14f),
                            size = Size(minSide * 0.125f, minSide * 0.06f),
                            cornerRadius = CornerRadius(minSide * 0.04f, minSide * 0.04f)
                        )
                    }

                    listOf(-34f, 34f).forEach { ghostAngle ->
                        rotate(ghostAngle, pivot = wrist) { drawFrontGrip(alpha = 0.16f) }
                    }
                    rotate(angle, pivot = wrist) { drawFrontGrip(alpha = 1f) }
                    val labelAnchor = rotatePoint(Offset(cx + phoneWidth * 0.94f, wrist.y - phoneHeight * 0.58f), wrist, angle)
                    drawLabel(screenPlaneLabel, labelAnchor.x, labelAnchor.y)
                } else {
                    val wrist = Offset(size.width * 0.68f, size.height * 0.82f)
                    val face = Offset(size.width * 0.28f, size.height * 0.23f)
                    val facePlaneX = face.x + minSide * 0.18f
                    val arcRadius = minSide * 0.43f
                    val baseArcPoint = Offset(wrist.x, wrist.y - arcRadius)
                    val phoneHeight = minSide * 0.58f
                    val phoneWidth = minSide * 0.12f
                    val phoneFocus = rotatePoint(Offset(wrist.x - phoneWidth * 0.12f, wrist.y - phoneHeight * 0.55f), wrist, angle)

                    drawSideFace(face)
                    drawLine(facePlaneColor, Offset(facePlaneX, face.y - minSide * 0.18f), Offset(facePlaneX, face.y + minSide * 0.24f), strokeWidth = minSide * 0.025f)
                    drawLabel(facePlaneLabel, facePlaneX, face.y - minSide * 0.22f)
                    drawArrow(gaze, Offset(facePlaneX + minSide * 0.028f, face.y + minSide * 0.060f), phoneFocus, strokeWidth = minSide * 0.014f)
                    drawLabel(faceDirectionLabel, facePlaneX + minSide * 0.105f, face.y + minSide * 0.31f, Paint.Align.LEFT)
                    drawArc(
                        color = arcColor,
                        startAngle = 244f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(wrist.x - arcRadius, wrist.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = minSide * 0.024f)
                    )
                    listOf(-26f, 26f).forEach { limit ->
                        drawCircle(endpointColor, radius = minSide * 0.020f, center = rotatePoint(baseArcPoint, wrist, limit))
                    }
                    drawCircle(motionColor, radius = minSide * 0.026f, center = rotatePoint(baseArcPoint, wrist, angle))

                    drawWristAndForearm(wrist)

                    fun drawSideGrip(alpha: Float) {
                        drawRoundRect(
                            color = skin.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - minSide * 0.115f, wrist.y - minSide * 0.31f),
                            size = Size(minSide * 0.23f, minSide * 0.33f),
                            cornerRadius = CornerRadius(minSide * 0.10f, minSide * 0.10f)
                        )
                        drawRoundRect(
                            color = phone.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - phoneWidth / 2f, wrist.y - phoneHeight - minSide * 0.035f),
                            size = Size(phoneWidth, phoneHeight),
                            cornerRadius = CornerRadius(minSide * 0.048f, minSide * 0.048f)
                        )
                        drawRoundRect(
                            color = phoneScreen.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - phoneWidth * 0.33f, wrist.y - phoneHeight * 0.88f),
                            size = Size(phoneWidth * 0.36f, phoneHeight * 0.76f),
                            cornerRadius = CornerRadius(minSide * 0.020f, minSide * 0.020f)
                        )
                        drawLine(
                            color = screenPlaneColor.copy(alpha = alpha),
                            start = Offset(wrist.x - phoneWidth * 0.34f, wrist.y - phoneHeight * 0.86f),
                            end = Offset(wrist.x - phoneWidth * 0.34f, wrist.y - phoneHeight * 0.15f),
                            strokeWidth = minSide * 0.022f
                        )
                        drawRoundRect(
                            color = skin.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - minSide * 0.17f, wrist.y - minSide * 0.16f),
                            size = Size(minSide * 0.11f, minSide * 0.060f),
                            cornerRadius = CornerRadius(minSide * 0.040f, minSide * 0.040f)
                        )
                    }

                    listOf(-26f, 26f).forEach { ghostAngle ->
                        rotate(ghostAngle, pivot = wrist) { drawSideGrip(alpha = 0.16f) }
                    }
                    rotate(angle, pivot = wrist) { drawSideGrip(alpha = 1f) }
                    val labelAnchor = rotatePoint(Offset(wrist.x + minSide * 0.11f, wrist.y - phoneHeight * 0.55f), wrist, angle)
                    drawLabel(screenPlaneLabel, labelAnchor.x, labelAnchor.y)
                }
            }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = guideColor, textAlign = TextAlign.Center)
        Text(l("当前偏转 ${abs(angle).roundToInt()}°", "Current deflection ${abs(angle).roundToInt()}°"), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun protocolText(): String = l(
    """
    研究协议

    1. 研究目的：本研究用于采集 Android 终端在自然使用中的运动传感器与脱敏组件结构，探索连续身份认证中的姿态、触控节奏与上下文特征。

    2. 采集数据：App 采集加速度计、陀螺仪、磁力计数据；通过无障碍服务读取可访问节点的类型、位置网格、可点击、可滚动、可编辑、选中、聚焦、启用状态和事件类型。

    3. 不采集内容：App 不执行点击、输入、手势注入或截图；不读取通讯录、短信、通话记录、相册、麦克风、摄像头、精确位置或不可访问的应用私有数据。

    4. 端侧脱敏：输入框原文不会保存或上传；密码节点整棵丢弃；手机号、邮箱、URL、银行卡号、身份证号、长数字串和令牌样式字符串会替换为占位符。服务端会下发可更新的脱敏规则与包名阻断列表；即使远程规则不可用，内置基线规则也会保持生效。

    5. 设备标识：研究 device_id 由 ANDROID_ID 与研究 salt 通过 HMAC-SHA256 生成，不使用 IMEI、序列号、MAC 地址等不可重置硬件标识符。

    6. 上传与安全：数据按 5 秒 batch 生成 JSON，经 LZ4 frame 压缩后上传到配置的研究服务器。生产部署应使用 HTTPS/TLS；本原型阶段不使用内容 AES 加密。

    7. 采集边界：仅在用户同意、三项权限完成、服务器可连接、ClockSync 成功且屏幕点亮解锁时采集。息屏、锁屏或网络不满足条件时暂停。

    8. 退出与删除：参与者可联系研究者撤回同意、请求导出或删除数据。退出后不会继续采集新的研究数据。

    请保持坐姿稳定阅读，如需查看完整内容，可在此文本框内自然下滑。
    """.trimIndent(),
    """
    Research Protocol

    1. Purpose: This study collects Android motion-sensor data and redacted component structure during natural use to explore posture, touch rhythm, and context features for continuous authentication.

    2. Data collected: The app collects accelerometer, gyroscope, and magnetic-field data. Through AccessibilityService it reads accessible node type, position grid, clickable, scrollable, editable, checked, focused, enabled states, and event type.

    3. Data not collected: The app does not perform clicks, input, gesture injection, or screenshots. It does not read contacts, SMS, call logs, photos, microphone, camera, precise location, or inaccessible private app data.

    4. On-device redaction: Raw input-field text is never stored or uploaded. Password nodes are dropped entirely. Phone numbers, emails, URLs, card numbers, ID numbers, long numeric strings, and token-like strings are replaced with placeholders. The server provides updatable redaction rules and a package blocklist; built-in baseline rules remain active even when remote rules are unavailable.

    5. Device identifier: The research device_id is derived from ANDROID_ID and the study salt with HMAC-SHA256. IMEI, serial number, MAC address, and other non-resettable hardware identifiers are not used.

    6. Upload and security: Data is batched every 5 seconds as JSON, compressed with LZ4 frame, and uploaded to the configured research server. Production deployment should use HTTPS/TLS. This prototype stage does not use payload AES encryption.

    7. Collection boundaries: Collection runs only after consent, all three permissions, server reachability, successful ClockSync, and an on/unlocked screen. It pauses when the screen is off, locked, or network conditions are not satisfied.

    8. Withdrawal and deletion: Participants may contact the researcher to withdraw consent, request export, or request deletion. After withdrawal, no new research data is collected.

    Please stay seated while reading. Scroll naturally inside this text box if you need to view the full protocol.
    """.trimIndent()
)

@Composable
private fun SettingsScreen(
    state: com.contextauth.core.UiState,
    viewModel: MainViewModel,
    onResearcher: () -> Unit
	) {
	    Page(l("设置", "Settings"), footer = { AboutFooter(onResearcher) }) {
	        CardBlock(l("采集", "Collection")) {
	            InfoRow(l("采样率", "Sampling rate"), l("请求 100Hz，按设备上限降级", "Request 100Hz, capped by device support"))
	            InfoRow(l("batch 时长", "Batch duration"), l("5s（固定）", "5s (fixed)"))
	            InfoRow(l("单任务时长", "Task duration"), l("30s（固定）", "30s (fixed)"))
	            SwitchRow(l("仅 Wi-Fi 上传", "Upload on Wi-Fi only"), state.settings.wifiOnly, viewModel::setWifiOnly)
	        }
	        CardBlock(l("规则与队列", "Rules and Queue")) {
	            InfoRow(l("当前规则版本", "Current rule version"), state.settings.ruleVersion)
	            InfoRow("serverStudySalt", if (state.settings.serverStudySalt.isBlank()) l("未获取", "Not fetched") else l("已从服务端配置", "Fetched from server config"))
	            InfoRow(l("云规则更新", "Cloud rule updates"), l("启动与服务器变更时自动应用，可手动检查", "Applied on startup and server changes; manual check available"))
	            InfoRow(l("最近规则检查", "Latest rule check"), displayMessage(state.diagnostics.lastRuleCheck))
	            OutlinedButton(onClick = { viewModel.checkRules(apply = false) }) { Text(l("检查规则", "Check Rules")) }
	            InfoRow(l("失败队列", "Failure queue"), l("${state.diagnostics.queueEntries} 条 / ${state.diagnostics.queueBytes / 1024} KB / 上限 200MB", "${state.diagnostics.queueEntries} entries / ${state.diagnostics.queueBytes / 1024} KB / 200MB cap"))
	            InfoRow(l("最近回灌", "Latest replay"), formatTime(state.diagnostics.lastQueueReplayAtWallMillis))
	        }
	        CardBlock(l("时钟同步", "Clock Sync")) {
	            InfoRow(l("最近校准", "Latest sync"), formatTime(state.clock.lastSyncedAtWallMillis))
	            InfoRow(l("最近 RTT", "Latest RTT"), "${state.clock.lastRttMillis} ms")
	            InfoRow(l("来源", "Source"), clockSourceLabel(state.clock.source))
	            InfoRow("server_offset_millis", state.clock.serverOffsetMillis.toString())
	            InfoRow(l("估计漂移", "Estimated drift"), "${state.clock.estimatedDriftPpm.toInt()} ppm")
	        }
	    }
	}

@Composable
private fun ResearcherSettingsScreen(
    state: com.contextauth.core.UiState,
    onBack: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onReset: () -> Unit,
    onClearQueue: () -> Unit,
    onExport: () -> String
) {
    var url by remember(state.settings.serverUrl) { mutableStateOf(state.settings.serverUrl) }
	    var result by remember { mutableStateOf("") }
	    var pendingUrl by remember { mutableStateOf<String?>(null) }
	    var confirmClear by remember { mutableStateOf(false) }
	    Page(l("研究者设置", "Researcher Settings")) {
	        Banner(if (state.settings.consentGranted) l("修改这些值会改变数据上报目标，仅供研究者使用", "Changing these values changes the upload endpoint. Researcher use only.") else l("你尚未同意采集协议；本页面不进行任何采集", "Consent has not been granted; this page performs no collection."))
	        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("server URL") }, modifier = Modifier.fillMaxWidth())
	        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
	            Button(onClick = { if (isBasicUrl(url)) pendingUrl = url else result = l("URL 格式无效", "Invalid URL format") }) { Text(l("确认覆盖", "Override")) }
	            OutlinedButton(onClick = { onTestConnection(url); result = l("已发起 /health 连通性测试", "Started /health connectivity test") }) { Text(l("测试连通性", "Test Connection")) }
	        }
	        OutlinedButton(onClick = { url = DEFAULT_SERVER_URL; onReset() }) { Text(l("重置为默认（cca.macrz.com）", "Reset to default (cca.macrz.com)")) }
	        OutlinedButton(onClick = { confirmClear = true }) { Text(l("清空本地失败队列", "Clear Local Failure Queue")) }
	        OutlinedButton(onClick = {
	            val path = onExport()
	            result = l("已导出：$path", "Exported: $path")
	        }) { Text(l("导出 Diagnostics 快照", "Export diagnostics snapshot")) }
	        InfoRow(l("最近服务端响应", "Latest server response"), displayMessage(state.diagnostics.lastServerMessage))
	        if (result.isNotBlank()) Text(result)
	        TextButton(onClick = onBack) { Text(l("返回", "Back")) }
	    }
	    pendingUrl?.let { candidate ->
	        AlertDialog(
	            onDismissRequest = { pendingUrl = null },
	            title = { Text(l("确认修改上报目标", "Confirm Endpoint Change")) },
	            text = { Text(l("确定将上报目标改为 ${sanitizeUrlForDisplay(candidate)}？", "Change upload endpoint to ${sanitizeUrlForDisplay(candidate)}?")) },
	            confirmButton = {
	                TextButton(onClick = {
	                    onSaveUrl(candidate)
	                    result = l("已保存并触发 ClockSync 与配置拉取", "Saved and triggered ClockSync plus config/rule fetch")
	                    pendingUrl = null
	                }) { Text(l("确定", "Confirm")) }
	            },
	            dismissButton = { TextButton(onClick = { pendingUrl = null }) { Text(l("取消", "Cancel")) } }
	        )
	    }
	    if (confirmClear) {
	        AlertDialog(
	            onDismissRequest = { confirmClear = false },
	            title = { Text(l("清空失败队列", "Clear Failure Queue")) },
	            text = { Text(l("确定清空本地失败队列？这不会影响已上传到服务端的数据。", "Clear the local failure queue? Uploaded server data is not affected.")) },
	            confirmButton = { TextButton(onClick = { onClearQueue(); confirmClear = false }) { Text(l("清空", "Clear")) } },
	            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(l("取消", "Cancel")) } }
	        )
	    }
	}

@Composable
private fun DiagnosticsScreen(state: com.contextauth.core.UiState, onExport: () -> String) {
    var exported by remember { mutableStateOf("") }
    Page(l("诊断", "Diagnostics")) {
        CardBlock(l("传感器", "Sensors")) {
            InfoRow("Accelerometer", sensorDiagnosticsSummary(state.accelerometerHz, state.accelerometerCollectionHz, state.accelerometerLostSamples))
            InfoRow("Gyroscope", sensorDiagnosticsSummary(state.gyroscopeHz, state.gyroscopeCollectionHz, state.gyroscopeLostSamples))
            InfoRow("Magnetic field", sensorDiagnosticsSummary(state.magnetometerHz, state.magnetometerCollectionHz, state.magnetometerLostSamples))
        }
        CardBlock("Accessibility / ScreenGate") {
            InfoRow("AccessibilityService", yesNo(state.diagnostics.accessibilityEnabled))
            InfoRow(l("事件类型计数", "Event type counts"), state.diagnostics.eventBuckets.toString())
            InfoRow("ScreenGate", state.status.name.lowercase())
            Text(state.diagnostics.screenGateHistory.joinToString("\n").ifBlank { l("暂无切换历史", "No transition history") })
        }
        CardBlock(l("上传与性能", "Upload and Performance")) {
            InfoRow(l("本次运行 batch", "This-run batches"), l("成功 ${state.diagnostics.uploadSuccess} / 失败 ${state.diagnostics.uploadFailure} / 重试 ${state.diagnostics.retrying}", "success ${state.diagnostics.uploadSuccess} / failure ${state.diagnostics.uploadFailure} / retry ${state.diagnostics.retrying}"))
            InfoRow("JSON+LZ4 p50/p95", "${state.diagnostics.serializeCompressP50Ms}/${state.diagnostics.serializeCompressP95Ms} ms")
            InfoRow("sha256 p50/p95", "${state.diagnostics.shaP50Ms}/${state.diagnostics.shaP95Ms} ms")
            InfoRow(l("上传 p50/p95", "Upload p50/p95"), "${state.diagnostics.uploadP50Ms}/${state.diagnostics.uploadP95Ms} ms")
            InfoRow(l("失败队列", "Failure queue"), l("${state.diagnostics.queueEntries} 条 / ${state.diagnostics.queueBytes / 1024} KB", "${state.diagnostics.queueEntries} entries / ${state.diagnostics.queueBytes / 1024} KB"))
            InfoRow(l("队列最早条目", "Earliest queue entry"), formatTime(state.diagnostics.earliestQueueEntryAtWallMillis))
            InfoRow(l("FIFO 淘汰", "FIFO evictions"), l("${state.diagnostics.droppedDueToQuota} 条", "${state.diagnostics.droppedDueToQuota} entries"))
            InfoRow(l("最近 batch", "Latest batch"), state.diagnostics.lastBatchId)
            InfoRow(l("最近服务端响应", "Latest server response"), displayMessage(state.diagnostics.lastServerMessage))
            InfoRow(l("最近错误", "Latest error"), displayMessage(state.diagnostics.lastError))
        }
        CardBlock(l("时钟同步", "Clock Sync")) {
            InfoRow(l("最近校准", "Latest sync"), formatTime(state.clock.lastSyncedAtWallMillis))
            InfoRow("RTT", "${state.clock.lastRttMillis} ms")
            InfoRow("offset", "${state.clock.serverOffsetMillis} ms")
            InfoRow("drift", "${state.clock.estimatedDriftPpm.toInt()} ppm")
            InfoRow(l("来源", "Source"), clockSourceLabel(state.clock.source))
            InfoRow(l("最近错误", "Latest error"), displayClockError(state.clock.lastError))
        }
        Button(onClick = { exported = onExport() }) { Text(l("一键导出 Diagnostics 报告", "Export Diagnostics Report")) }
        if (exported.isNotBlank()) Text(exported)
    }
}

@Composable
private fun Page(title: String, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
        if (footer != null) item { footer() }
    }
}

@Composable
private fun CardBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun RequirementCard(title: String, items: List<String>) {
    CardBlock(title) {
        items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun PermissionStep(title: String, done: Boolean, body: String, action: () -> Unit) {
    CardBlock(title) {
        AssistChip(onClick = {}, label = { Text(if (done) l("已完成", "Done") else l("未完成", "Not done")) }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) })
        Text(body)
        Button(onClick = action) { Text(l("打开设置", "Open Settings")) }
    }
}

@Composable
private fun ServerConnectionHeader(
    reachable: Boolean,
    lastCheckedAtWallMillis: Long,
    latestMessage: String
) {
    CardBlock(l("采集状态", "Collection Status")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(serverConnectionText(reachable)) },
                leadingIcon = { Icon(Icons.Outlined.Info, null) }
            )
            AssistChip(
                onClick = {},
                label = { Text(if (lastCheckedAtWallMillis > 0) formatTime(lastCheckedAtWallMillis) else l("未检查", "Not checked")) },
                leadingIcon = { Icon(Icons.Outlined.CloudUpload, null) }
            )
        }
        Text(l("最近服务端响应", "Latest server response"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(latestMessage, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Banner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), color = Color(0xFF5C4700))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Segmented(label: String, values: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach {
            FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text("${it}s") })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutFooter(onResearcher: () -> Unit) {
    val detector = remember { HiddenGestureDetector() }
    Divider()
    Text("ContextAuthLab ${BuildConfig.VERSION_NAME} · commit ${BuildConfig.GIT_COMMIT} · academic prototype")
    Text(
        l("版本 ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.hiddenResearcherGesture(detector, onResearcher)
    )
}

private fun Modifier.hiddenResearcherGesture(detector: HiddenGestureDetector, onResearcher: () -> Unit): Modifier =
    pointerInput(detector) {
        detectTapGestures(
            onTap = { if (detector.recordTap(System.currentTimeMillis())) onResearcher() },
            onPress = {
                val started = System.currentTimeMillis()
                val released = tryAwaitRelease()
                val duration = System.currentTimeMillis() - started
                if (!released && detector.recordPress(duration)) onResearcher()
                if (released && detector.recordPress(duration)) onResearcher()
            }
        )
    }

private fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun copyText(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("device_id", value))
}

private fun hostOnly(url: String): String = runCatching { URI(url).host ?: url }.getOrDefault(url)
private fun maskDeviceId(value: String): String = if (value.length >= 8) "${value.take(8)}****" else value
private fun yesNo(value: Boolean): String = if (value) l("已完成", "Done") else l("未完成", "Not done")
private fun formatHz(value: Double): String = "${"%.1f".format(value)} Hz"
private fun sensorTargetSummary(state: com.contextauth.core.UiState): String =
    "acc ${formatHz(state.accelerometerCollectionHz)} / gyro ${formatHz(state.gyroscopeCollectionHz)} / mag ${formatHz(state.magnetometerCollectionHz)}"
private fun sensorDiagnosticsSummary(actualHz: Double, targetHz: Double, lostSamples: Int): String = l(
    "实测 ${formatHz(actualHz)} / 可采 ${formatHz(targetHz)} / 丢样估计 $lostSamples",
    "measured ${formatHz(actualHz)} / target ${formatHz(targetHz)} / estimated lost $lostSamples"
)
private fun serverConnectionText(reachable: Boolean): String =
    if (reachable) l("服务器已连接", "Server connected") else l("服务器未连接", "Server disconnected")
private fun isBasicUrl(url: String): Boolean = Regex("""^https?://[^/]+.*""").matches(url)
private fun sanitizeUrlForDisplay(url: String): String {
    val host = hostOnly(url)
    return if (host.length <= 3) "$host****" else host.take(3) + "****"
}

private fun clockSyncSummary(clock: ClockSyncState, includeDrift: Boolean): String {
    if (!clock.synced) return l("未同步", "Not synced")
    val base = "${formatTime(clock.lastSyncedAtWallMillis)} / ${clockSourceLabel(clock.source)}"
    return if (includeDrift) "$base / drift ${clock.estimatedDriftPpm.toInt()} ppm" else base
}

private fun clockSourceLabel(source: String): String = when {
    source == "ntp" || source.startsWith("ntp:") -> l("NTP 已同步", "NTP synced")
    source == "server_config_fallback" -> l("服务器时间回退", "Server time fallback")
    source == "none" -> l("未同步", "Not synced")
    else -> source
}

private fun displayClockError(value: String?): String = when {
    value.isNullOrBlank() -> "-"
    value.contains("ntp", ignoreCase = true) -> l("NTP 同步失败，等待服务端时间回退", "NTP sync failed; waiting for server-time fallback")
    else -> value
}

private fun displayMessage(value: String): String = when (value) {
    "not_uploaded" -> l("未上传", "Not uploaded")
    "not_checked" -> l("未检查", "Not checked")
    "pending" -> l("未生成", "Pending")
    "-" -> "-"
    else -> value
}

private fun formatTime(millis: Long): String = if (millis <= 0) l("未完成", "Not completed") else SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(millis))
private fun manufacturerGuide(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi" -> l("请在 MIUI 自启动管理中允许后台运行。为保证 5 秒一次上传实时性，需关闭后台限制；本 App 仅在屏幕点亮且已解锁时采集，息屏即停止。", "Allow background running in MIUI autostart settings. To keep 5-second uploads timely, disable background restrictions; collection runs only while the screen is on and unlocked.")
    "huawei", "honor" -> l("请在启动管理中允许后台运行。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Allow background running in Launch Management. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    "oppo", "oneplus" -> l("请允许自启动与关联启动。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Allow autostart and associated startup. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    "vivo", "iqoo" -> l("请允许后台高耗电。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Allow high background power use. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    "samsung" -> l("请将 App 从睡眠模式中排除。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Exclude the app from sleeping apps. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    else -> l("请在应用详情中允许后台运行。为保证 5 秒一次上传实时性，需关闭后台限制；本 App 仅在屏幕点亮且已解锁时采集，息屏即停止。", "Allow background running in app details. To keep 5-second uploads timely, disable background restrictions; collection runs only while the screen is on and unlocked.")
}
