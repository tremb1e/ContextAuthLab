package com.contextauth.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import android.widget.VideoView
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
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
import com.contextauth.R
import com.contextauth.service.DataCollectionService
import com.contextauth.ui.theme.ContextAuthLabTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
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

private fun String.toScreen(default: Screen): Screen = runCatching { Screen.valueOf(this) }.getOrDefault(default)
private fun String.toScreenOrNull(): Screen? = runCatching { Screen.valueOf(this) }.getOrNull()
private fun String.toTaskCategory(): TaskCategory? = runCatching { TaskCategory.valueOf(this) }.getOrNull()

private data class PermissionReminder(
    val key: String,
    val title: String,
    val body: String,
    val openSettings: () -> Unit
)

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
    var screenName by rememberSaveable { mutableStateOf(if (state.settings.consentGranted) Screen.HOME.name else Screen.CONSENT.name) }
    var selectedTaskName by rememberSaveable { mutableStateOf<String?>(null) }
    var backStackNames by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val tasksListState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val screen = screenName.toScreen(if (state.settings.consentGranted) Screen.HOME else Screen.CONSENT)
    val selectedTask = selectedTaskName?.toTaskCategory()
    val backStack = backStackNames.mapNotNull { it.toScreenOrNull() }
    fun setScreen(next: Screen) {
        screenName = next.name
    }
    fun navigate(next: Screen) {
        if (next != screen) {
            backStackNames = (backStackNames + screen.name).takeLast(12)
            setScreen(next)
        }
    }
    fun exitTaskRunner(markComplete: Boolean = false) {
        selectedTask?.let { task ->
            if (markComplete) viewModel.markTaskComplete(task)
        }
        viewModel.stopCollection()
        stopForeground()
        selectedTaskName = null
        backStackNames = backStackNames.filterNot { it == Screen.TASK_RUNNER.name }
        setScreen(Screen.TASKS)
    }
	    LaunchedEffect(state.settings.consentGranted) {
	        if (state.settings.consentGranted && screen == Screen.CONSENT) navigate(Screen.ONBOARDING)
	    }
	    LaunchedEffect(
	        screen,
	        state.status,
	        state.deviceId,
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
            exitTaskRunner()
            return@BackHandler
        }
        if (backStack.isNotEmpty()) {
            setScreen(backStack.last())
            backStackNames = backStackNames.dropLast(1)
        } else if (screen != Screen.HOME && state.settings.consentGranted) {
            setScreen(Screen.HOME)
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
	                    NavItem(Screen.DIAGNOSTICS, screen, Icons.Outlined.BarChart, l("详细", "Details")) { navigate(Screen.DIAGNOSTICS) }
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
	                    onPermissions = { setScreen(Screen.ONBOARDING) },
                        requestNotification = requestNotification,
                        onTestServer = { viewModel.testConnection(state.settings.serverUrl) },
	                    onCopy = { copyText(context, it) },
	                    onResearcher = { navigate(Screen.RESEARCHER) }
	                )
                Screen.TASKS -> BuiltInTasksScreen(
                    completed = state.completedTasks,
                    listState = tasksListState,
                    onTask = { task -> selectedTaskName = task.name; navigate(Screen.TASK_RUNNER) }
                )
                Screen.TASK_RUNNER -> selectedTask?.let { task ->
                    TaskRunnerScreen(
                        state = state,
                        task = task,
                        canStart = viewModel.canStart(),
                        onStart = { startForeground(); viewModel.startCollection(task) },
                        onDone = { exitTaskRunner(markComplete = true) },
                        onBack = { exitTaskRunner() },
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
                            setScreen(backStack.last())
                            backStackNames = backStackNames.dropLast(1)
                        } else {
                            setScreen(if (state.settings.consentGranted) Screen.HOME else Screen.CONSENT)
                        }
                    },
                    onSaveUrl = viewModel::updateServerUrl,
                    onTestConnection = viewModel::testConnection,
                    onReset = viewModel::resetServerUrl,
                    onClearQueue = viewModel::clearQueue,
                    onExport = { viewModel.exportDiagnostics() }
                )
                Screen.DIAGNOSTICS -> DetailScreen(state = state)
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
	                l("使用 AccessibilityService 读取可访问组件结构、前台包名/Activity、资源 ID 与控件显示文本；记录全局屏幕触控交互开始/结束的时间；不点击、不输入、不截图，不采集触摸轨迹或位置。", "AccessibilityService reads accessible component structure, foreground package/activity, resource IDs, and visible component text; it records global screen touch interaction start/end timing; it does not click, type, take screenshots, or collect touch paths/positions."),
	                l("不会上传输入框逐字内容或按键时序，密码节点会整棵丢弃，固定格式敏感字符串会替换为占位符。", "Input-field characters and keystroke timing are not uploaded; password nodes are dropped completely, and fixed-format sensitive strings are replaced with placeholders."),
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
	            body = l("在列表中找到 ContextAuthLab，启用后返回 App。仅读取组件结构、控件显示文本和前台组件信息，不执行任何动作。", "Find ContextAuthLab in the list, enable it, then return. It reads component structure, visible component text, and foreground component metadata only; it performs no actions."),
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
    requestNotification: () -> Unit,
    onTestServer: () -> Unit,
    onCopy: (String) -> Unit,
    onResearcher: () -> Unit
) {
    var showDeviceId by remember { mutableStateOf(false) }
    var skippedPermissionPrompts by remember { mutableStateOf<Set<String>>(emptySet()) }
    val hiddenDetector = remember { HiddenGestureDetector() }
    val context = LocalContext.current
    val permissionReminders = buildList {
        if (!state.accessibilityEnabled) {
            add(
                PermissionReminder(
                    key = "accessibility",
                    title = l("需要开启无障碍服务", "AccessibilityService Required"),
                    body = l("请开启 ContextAuthLab 无障碍服务；上传数据需要包含通过无障碍采集到的前端 UI 上下文。", "Enable the ContextAuthLab accessibility service; uploads require UI context collected through accessibility."),
                    openSettings = { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
            )
        }
        if (!state.batteryWhitelisted) {
            add(
                PermissionReminder(
                    key = "battery",
                    title = l("需要允许后台运行", "Background Running Required"),
                    body = manufacturerGuide(),
                    openSettings = {
                        if (Build.VERSION.SDK_INT >= 23) {
                            context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                        } else {
                            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    }
                )
            )
        }
        if (!state.notificationAllowed) {
            add(
                PermissionReminder(
                    key = "notification",
                    title = l("需要通知权限", "Notification Permission Required"),
                    body = l("前台采集服务需要通知权限，以便系统允许采集服务稳定运行。", "The foreground collection service needs notification permission so Android can keep it running."),
                    openSettings = requestNotification
                )
            )
        }
    }
    val pendingPermissionReminder = permissionReminders.firstOrNull { it.key !in skippedPermissionPrompts }
    Page(l("首页", "Home")) {
        if (state.settings.serverOverridden) {
            Banner(l("当前上报目标已被研究者覆盖：", "Research endpoint overridden: ") + hostOnly(state.settings.serverUrl))
        }
        ServerConnectionHeader(
            reachable = state.serverReachable,
            lastCheckedAtWallMillis = state.lastServerHealthAtWallMillis,
            lastUploadAtWallMillis = state.diagnostics.lastUploadAtWallMillis,
            latestMessage = displayMessage(state.diagnostics.lastServerMessage),
            status = state.status,
            canStart = canStart,
            onTestServer = onTestServer
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
            Banner(l("等待必要权限完成后会自动开始采集。", "Collection starts automatically after required permissions are ready."))
            TextButton(onClick = onPermissions) { Text(l("前往权限引导", "Open permission guide")) }
        } else {
            Banner(l("自检已通过，App 会自动保持采集；上传在网络策略允许时执行。", "Checks passed. The app keeps collection running automatically; upload follows the network policy."))
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
    pendingPermissionReminder?.let { reminder ->
        AlertDialog(
            onDismissRequest = { skippedPermissionPrompts = skippedPermissionPrompts + reminder.key },
            title = { Text(reminder.title) },
            text = { Text(reminder.body) },
            dismissButton = {
                TextButton(
                    onClick = {
                        skippedPermissionPrompts = skippedPermissionPrompts + reminder.key
                        reminder.openSettings()
                    }
                ) {
                    Text(l("去设置", "Open Settings"))
                }
            },
            confirmButton = {
                TextButton(onClick = { skippedPermissionPrompts = skippedPermissionPrompts + reminder.key }) {
                    Text(l("忽略", "Ignore"))
                }
            }
        )
    }
}

@Composable
private fun BuiltInTasksScreen(completed: Set<TaskCategory>, listState: LazyListState, onTask: (TaskCategory) -> Unit) {
    val taskCount = TaskCategory.entries.size
    Page(l("内置任务", "Built-in Tasks"), listState = listState) {
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
	            Text(l("按顺序完成全部 $taskCount 项", "Complete all $taskCount tasks in order"))
	        }
	        if (completed.size == taskCount) {
	            Text(l("数据采集已完成，感谢配合。本次共贡献 $taskCount 项任务数据。", "Data collection is complete. Thank you; this session contributed data for $taskCount tasks."))
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
                if (task == TaskCategory.C5) {
                    snackbar.showSnackbar(l("计时已到，请继续点击直到 30 颗小球全部完成。", "Timer ended; keep tapping until all 30 balls are complete."))
                } else {
	                running = false
	                snackbar.showSnackbar(l("已记录 ${state.settings.taskSeconds} 秒数据", "Recorded ${state.settings.taskSeconds} seconds of data"))
	                onDone()
                }
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
            if (!autoStarted && !canStart) {
                Banner(l("等待采集条件满足后将自动开始 30 秒计时。", "The 30-second timer will start automatically when collection requirements are met."))
            }
	        TaskContent(task, onComplete = onDone)
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
	            text = { Text(l("本任务记录传感器、全局屏幕触控交互开始/结束时间、组件结构、控件显示文本和输入法是否显示；不记录触摸轨迹、触摸位置、输入框逐字内容、按键间隔或按住位置。", "This task records sensors, global screen touch interaction start/end timing, component structure, visible component text, and whether the input method is visible; it does not record touch paths, touch positions, input-field characters, key intervals, or held positions.")) },
	            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(l("知道了", "OK")) } }
	        )
	    }
	}

@Composable
private fun TaskContent(task: TaskCategory, onComplete: () -> Unit) {
    CardBlock(task.localizedTaskName()) {
        when (task) {
            TaskCategory.C0 -> QuietHoldClock()
            TaskCategory.C1 -> ProtocolReader()
            TaskCategory.C2 -> ResearchFeed()
            TaskCategory.C3 -> CopyWritingTask()
            TaskCategory.C4 -> PreferenceControls()
            TaskCategory.C5 -> BlueBallTapGame(onComplete)
            TaskCategory.C6 -> VideoWatchingTask()
            TaskCategory.C7 -> WristRotationGuide()
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
private fun BlueBallTapGame(onComplete: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val totalTargets = 30
    val random = remember { Random(System.nanoTime()) }
    var started by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var hitCount by remember { mutableIntStateOf(0) }
    var ball by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var pulse by remember { mutableIntStateOf(0) }
    fun nextBall(): Offset = Offset(
        x = 0.08f + random.nextFloat() * 0.84f,
        y = 0.12f + random.nextFloat() * 0.76f
    )

    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    LaunchedEffect(completed) {
        if (completed) {
            context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            delay(900)
            onComplete()
        }
    }

    Text(l(
        "点击开始后页面会切到横屏。随机位置会出现一颗蓝色小球，点中后立即刷新下一颗；连续点中 30 颗即过关并自动回到任务页。",
        "Tap Start to switch to landscape. A blue ball appears at a random position; tap it to spawn the next ball. Hit 30 balls to pass and return automatically."
    ))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = {}, label = { Text("${hitCount}/$totalTargets") })
        AssistChip(onClick = {}, label = { Text(if (started) l("横屏挑战中", "Landscape challenge") else l("等待开始", "Ready")) })
        if (!started) {
            Button(
                onClick = {
                    context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    ball = nextBall()
                    hitCount = 0
                    completed = false
                    started = true
                }
            ) {
                Text(l("开始", "Start"))
            }
        }
    }

    val gameHeight = if (configuration.screenWidthDp > configuration.screenHeightDp) {
        (configuration.screenHeightDp - 120).coerceAtLeast(260).dp
    } else {
        320.dp
    }
    val ballColor = Color(0xFF1976D2)
    val glowColor = Color(0xFF64B5F6)
    val successColor = Color(0xFF2E7D32)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(gameHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF071421))
            .pointerInput(started, ball, hitCount, completed) {
                detectTapGestures { tap ->
                    if (!started || completed) return@detectTapGestures
                    val radius = minOf(size.width, size.height) * 0.055f
                    val center = Offset(size.width * ball.x, size.height * ball.y)
                    if ((tap - center).getDistance() <= radius * 1.25f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val nextCount = hitCount + 1
                        hitCount = nextCount
                        pulse += 1
                        if (nextCount >= totalTargets) {
                            completed = true
                        } else {
                            ball = nextBall()
                        }
                    }
                }
            }
    ) {
        val minSide = minOf(size.width, size.height)
        val gridColor = Color.White.copy(alpha = 0.08f)
        drawRect(Color(0xFF071421))
        for (i in 1..8) {
            val x = size.width * i / 9f
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.2f)
        }
        for (i in 1..5) {
            val y = size.height * i / 6f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f)
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(6f, 6f),
            size = Size(size.width - 12f, size.height - 12f),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 3f)
        )
        if (completed) {
            drawCircle(successColor.copy(alpha = 0.20f), radius = minSide * 0.36f, center = center)
            drawCircle(successColor, radius = minSide * 0.11f, center = center)
            drawLine(Color.White, center + Offset(-minSide * 0.045f, 0f), center + Offset(-minSide * 0.012f, minSide * 0.035f), strokeWidth = minSide * 0.018f)
            drawLine(Color.White, center + Offset(-minSide * 0.012f, minSide * 0.035f), center + Offset(minSide * 0.055f, -minSide * 0.050f), strokeWidth = minSide * 0.018f)
        } else if (started) {
            val center = Offset(size.width * ball.x, size.height * ball.y)
            val radius = minSide * 0.055f
            val wave = (pulse % 4) * radius * 0.14f
            drawCircle(glowColor.copy(alpha = 0.18f), radius = radius * 2.5f + wave, center = center)
            drawCircle(glowColor.copy(alpha = 0.30f), radius = radius * 1.55f, center = center)
            drawCircle(ballColor, radius = radius, center = center)
            drawCircle(Color.White.copy(alpha = 0.85f), radius = radius * 0.24f, center = center + Offset(-radius * 0.28f, -radius * 0.28f))
        } else {
            drawCircle(glowColor.copy(alpha = 0.10f), radius = minSide * 0.28f, center = center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoWatchingTask() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val videoUri = remember(context) { Uri.parse("android.resource://${context.packageName}/${R.raw.c6_video}") }
    var activated by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.0f) }
    var videoDurationMs by remember { mutableIntStateOf(1) }
    var videoPositionMs by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    fun applyOrientation(isLandscape: Boolean) {
        landscape = isLandscape
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    fun applySpeed() {
        if (Build.VERSION.SDK_INT >= 23) {
            runCatching {
                val player = mediaPlayer
                if (player != null) {
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoView?.stopPlayback()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    LaunchedEffect(mediaPlayer, speed) {
        applySpeed()
    }
    LaunchedEffect(activated, prepared, seeking) {
        while (activated && prepared) {
            if (!seeking) {
                videoPositionMs = videoView?.currentPosition ?: 0
            }
            delay(500)
        }
    }

    Text(l(
        "请按自己最舒适的姿势手握手机观看视频。点击播放器后会自动切到横屏并播放；观看过程中请自然尝试暂停/继续、倍速、拖动进度条，以及横屏/竖屏切换。",
        "Hold the phone in your most comfortable viewing posture. Tap the player to switch to landscape and start playback; naturally try pause/resume, playback speed, seeking, and landscape/portrait switching."
    ))
    if (!activated) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101820))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .combinedClickable(onClick = {
                    activated = true
                    applyOrientation(true)
                }),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
                Text(l("点击开始播放并横屏", "Tap to play in landscape"), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (landscape) 300.dp else 260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    videoView = this
                    setVideoURI(videoUri)
                    setOnPreparedListener { player ->
                        mediaPlayer = player
                        prepared = true
                        playing = true
                        videoDurationMs = duration.coerceAtLeast(1)
                        player.isLooping = true
                        applySpeed()
                        start()
                    }
                    setOnCompletionListener {
                        seekTo(0)
                        start()
                    }
                    setOnClickListener {
                        if (isPlaying) {
                            pause()
                            playing = false
                        } else {
                            start()
                            playing = true
                        }
                    }
                    start()
                }
            },
            update = { view ->
                if (playing && prepared && !view.isPlaying) view.start()
                if (!playing && view.isPlaying) view.pause()
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    videoView?.let { view ->
                        if (view.isPlaying) {
                            view.pause()
                            playing = false
                        } else {
                            view.start()
                            playing = true
                        }
                    }
                }
            ) {
                Text(if (playing) l("暂停", "Pause") else l("播放", "Play"))
            }
            OutlinedButton(onClick = { applyOrientation(!landscape) }) {
                Text(if (landscape) l("切到竖屏", "Portrait") else l("切到横屏", "Landscape"))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { option ->
                FilterChip(
                    selected = speed == option,
                    onClick = {
                        speed = option
                        applySpeed()
                    },
                    label = { Text("${option}x") }
                )
            }
        }
        val sliderValue = if (seeking) seekFraction else (videoPositionMs.toFloat() / videoDurationMs.toFloat()).coerceIn(0f, 1f)
        Slider(
            value = sliderValue,
            onValueChange = {
                seeking = true
                seekFraction = it
            },
            onValueChangeFinished = {
                val target = (seekFraction * videoDurationMs).roundToInt().coerceIn(0, videoDurationMs)
                videoView?.seekTo(target)
                videoPositionMs = target
                seeking = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            l(
                "进度 ${formatDuration(videoPositionMs)} / ${formatDuration(videoDurationMs)}",
                "Progress ${formatDuration(videoPositionMs)} / ${formatDuration(videoDurationMs)}"
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    val translateAngle by transition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(animation = tween(1750), repeatMode = RepeatMode.Reverse),
        label = "translate_angle"
    )
    val facePlaneColor = MaterialTheme.colorScheme.tertiary
    val screenPlaneColor = MaterialTheme.colorScheme.primary
    val motionColor = MaterialTheme.colorScheme.secondary
    Text(l(
        "下面用三个示意图展示左右摇摆、左右平移和前后内收。橙色是人脸平面和面部朝向，蓝色是手机屏幕平面和屏幕朝向，绿色是运动轨迹。请只动手腕/手掌，头部保持正对前方。",
        "The three diagrams show left-right swing, lateral translation, and forward-back flexion. Orange marks the face plane/direction, blue marks the phone screen plane/direction, and green marks the motion path. Move only the wrist/hand; keep your head facing forward."
    ))
    WristGuideLegend(facePlaneColor, screenPlaneColor, motionColor)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 560.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WristMotionPanel(
                    label = l("左右摇摆", "Left-right swing"),
                    viewBadge = l("俯视图 · 从头顶看", "Top view · from above"),
                    hint = l("从头顶俯视：蓝色屏幕朝向随手腕左右扫动，橙色面部朝向保持指向前方。", "From above: the blue screen direction sweeps left/right with the wrist, while the orange face direction stays pointing forward."),
                    detail = l("面部正对前方，转的是手腕，不要扭头追手机。", "Keep your face pointing forward; rotate only the wrist — do not turn your head with the phone."),
                    angle = sideAngle,
                    mode = WristMotionMode.SIDE_TO_SIDE,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                WristMotionPanel(
                    label = l("左右平移", "Lateral translation"),
                    viewBadge = l("俯视图 · 屏幕始终平行面部", "Top view · screen stays parallel"),
                    hint = l("手机沿扇形轨迹左右移动，但蓝色屏幕平面始终与橙色人脸平面平行。", "Move the phone left/right along a fan-shaped path while keeping the blue screen plane parallel to the orange face plane."),
                    detail = l("小臂尽量不动，像扇形一样摆动手掌；不要让手机屏幕跟着旋转。", "Keep the forearm still and sweep the hand like a fan; do not rotate the phone screen."),
                    angle = translateAngle,
                    mode = WristMotionMode.LATERAL_TRANSLATION,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                WristMotionPanel(
                    label = l("前后内收", "Forward-back flexion"),
                    viewBadge = l("侧视图 · 从侧面看", "Side view · from the side"),
                    hint = l("从侧面观察：蓝色屏幕朝向随手腕向面部靠近或远离，橙色面部朝向保持水平。", "From the side: the blue screen direction tilts toward/away from the face with the wrist, while the orange face direction stays horizontal."),
                    detail = l("只弯曲手腕，前臂和头部保持不动。", "Flex only the wrist; keep the forearm and head still."),
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
                    viewBadge = l("俯视图 · 从头顶看", "Top view · from above"),
                    hint = l("从头顶俯视：蓝色屏幕朝向随手腕左右扫动，橙色面部朝向保持指向前方。", "From above: the blue screen direction sweeps left/right with the wrist, while the orange face direction stays pointing forward."),
                    detail = l("面部正对前方，转的是手腕，不要扭头追手机。", "Keep your face pointing forward; rotate only the wrist — do not turn your head with the phone."),
                    angle = sideAngle,
                    mode = WristMotionMode.SIDE_TO_SIDE,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.weight(1f),
                )
                WristMotionPanel(
                    label = l("左右平移", "Lateral translation"),
                    viewBadge = l("俯视图 · 屏幕始终平行面部", "Top view · screen stays parallel"),
                    hint = l("手机沿扇形轨迹左右移动，但蓝色屏幕平面始终与橙色人脸平面平行。", "Move the phone left/right along a fan-shaped path while keeping the blue screen plane parallel to the orange face plane."),
                    detail = l("小臂尽量不动，像扇形一样摆动手掌；不要让手机屏幕跟着旋转。", "Keep the forearm still and sweep the hand like a fan; do not rotate the phone screen."),
                    angle = translateAngle,
                    mode = WristMotionMode.LATERAL_TRANSLATION,
                    facePlaneColor = facePlaneColor,
                    screenPlaneColor = screenPlaneColor,
                    motionColor = motionColor,
                    modifier = Modifier.weight(1f),
                )
                WristMotionPanel(
                    label = l("前后内收", "Forward-back flexion"),
                    viewBadge = l("侧视图 · 从侧面看", "Side view · from the side"),
                    hint = l("从侧面观察：蓝色屏幕朝向随手腕向面部靠近或远离，橙色面部朝向保持水平。", "From the side: the blue screen direction tilts toward/away from the face with the wrist, while the orange face direction stays horizontal."),
                    detail = l("只弯曲手腕，前臂和头部保持不动。", "Flex only the wrist; keep the forearm and head still."),
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

private enum class WristMotionMode { SIDE_TO_SIDE, LATERAL_TRANSLATION, FLEXION }

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
        WristLegendItem(facePlaneColor, l("橙色：人脸平面与面部朝向（固定不动）", "Orange: face plane and face-direction arrow (always fixed)"))
        WristLegendItem(screenPlaneColor, l("蓝色：手机屏幕及其朝向（摇摆/内收时转动，平移时保持平行）", "Blue: phone screen and direction (rotates for swing/flexion, stays parallel for translation)"))
        WristLegendItem(motionColor, l("绿色：手腕与手掌运动轨迹", "Green: wrist and hand motion path"))
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
    viewBadge: String,
    hint: String,
    detail: String,
    angle: Float,
    mode: WristMotionMode,
    facePlaneColor: Color,
    screenPlaneColor: Color,
    motionColor: Color,
    modifier: Modifier = Modifier,
) {
    val angleRange = when (mode) {
        WristMotionMode.SIDE_TO_SIDE -> 34f
        WristMotionMode.LATERAL_TRANSLATION -> 30f
        WristMotionMode.FLEXION -> 26f
    }
    val arcColor = motionColor.copy(alpha = 0.28f)
    val endpointColor = motionColor.copy(alpha = 0.50f)
    val guideColor = MaterialTheme.colorScheme.secondary
    val panelSurface = MaterialTheme.colorScheme.surface
    val canvasBorder = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val badgeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    val badgeText = MaterialTheme.colorScheme.onPrimaryContainer
    val backdropAccent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
    val facePlaneLabel = l("人脸平面", "Face plane")
    val faceArrowLabel = l("面部朝向", "Face direction")
    val screenArrowLabel = l("屏幕朝向", "Screen direction")
    val pivotLabel = l("手腕（转动轴）", "Wrist (pivot)")
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
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(228.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, canvasBorder, RoundedCornerShape(14.dp))
            ) {
                val minSide = minOf(size.width, size.height)
                val skin = Color(0xFFE7BE9C)
                val skinDark = Color(0xFFC68A65)
                val skinShadow = Color(0xFFAD6A4A)
                val phone = Color(0xFF1F2937)
                val phoneEdge = Color(0xFF374151)
                val phoneScreenFill = screenPlaneColor.copy(alpha = 0.80f)
                val hair = Color(0xFF3F2B23)
                val faceFeature = Color(0xFF3A2218)
                val backdropTop = panelSurface.copy(alpha = 0.95f)

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = badgeText.toArgb()
                    textSize = 9.5.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = labelColor.toArgb()
                    textSize = 9.5.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }

                fun drawLabel(text: String, x: Float, y: Float, align: Paint.Align = Paint.Align.CENTER, colorArgb: Int = labelColor.toArgb(), bold: Boolean = false) {
                    val saveColor = labelPaint.color
                    val saveAlign = labelPaint.textAlign
                    val saveBold = labelPaint.isFakeBoldText
                    labelPaint.color = colorArgb
                    labelPaint.textAlign = align
                    labelPaint.isFakeBoldText = bold
                    drawContext.canvas.nativeCanvas.drawText(text, x, y, labelPaint)
                    labelPaint.color = saveColor
                    labelPaint.textAlign = saveAlign
                    labelPaint.isFakeBoldText = saveBold
                }

                fun drawArrow(
                    color: Color,
                    start: Offset,
                    end: Offset,
                    strokeWidth: Float = 3.5f,
                    headLength: Float = minSide * 0.05f,
                ) {
                    drawLine(color, start, end, strokeWidth = strokeWidth)
                    val direction = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                    val spread = PI / 7.0
                    val left = Offset(
                        x = end.x - (headLength * cos(direction - spread)).toFloat(),
                        y = end.y - (headLength * sin(direction - spread)).toFloat(),
                    )
                    val right = Offset(
                        x = end.x - (headLength * cos(direction + spread)).toFloat(),
                        y = end.y - (headLength * sin(direction + spread)).toFloat(),
                    )
                    drawLine(color, end, left, strokeWidth = strokeWidth)
                    drawLine(color, end, right, strokeWidth = strokeWidth)
                }

                // soft backdrop
                drawRoundRect(
                    color = backdropTop,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f, 24f),
                )
                drawCircle(
                    color = backdropAccent,
                    radius = minSide * 0.55f,
                    center = Offset(size.width / 2f, size.height * 0.55f),
                )

                if (mode == WristMotionMode.SIDE_TO_SIDE || mode == WristMotionMode.LATERAL_TRANSLATION) {
                    // ============== TOP-DOWN VIEW (bird's-eye) ==============
                    val cx = size.width / 2f
                    val headCenter = Offset(cx, size.height * 0.20f)
                    val headRadius = minSide * 0.108f
                    val facePlaneY = headCenter.y + headRadius * 0.72f
                    val wrist = Offset(cx, size.height * 0.81f)
                    val arcRadius = minSide * 0.42f
                    val baseArcPoint = Offset(cx, wrist.y - arcRadius)
                    val phoneHalfWidth = minSide * 0.21f
                    val phoneThickness = minSide * 0.075f
                    val phoneCenterY = wrist.y - minSide * 0.165f
                    val screenEdgeY = phoneCenterY - phoneThickness / 2f - minSide * 0.006f

                    // ---- Head from above (hair arc + skull + ears + nose wedge) ----
                    drawArc(
                        color = hair,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(headCenter.x - headRadius * 1.12f, headCenter.y - headRadius * 1.12f),
                        size = Size(headRadius * 2.24f, headRadius * 2.24f),
                        style = Stroke(width = minSide * 0.038f),
                    )
                    drawCircle(skin, radius = headRadius, center = headCenter)
                    drawCircle(skinShadow, radius = headRadius, center = headCenter, style = Stroke(width = minSide * 0.005f))
                    // ears
                    drawCircle(skin, radius = headRadius * 0.16f, center = Offset(headCenter.x - headRadius * 1.02f, headCenter.y + headRadius * 0.14f))
                    drawCircle(skin, radius = headRadius * 0.16f, center = Offset(headCenter.x + headRadius * 1.02f, headCenter.y + headRadius * 0.14f))
                    drawCircle(skinShadow, radius = headRadius * 0.16f, center = Offset(headCenter.x - headRadius * 1.02f, headCenter.y + headRadius * 0.14f), style = Stroke(width = minSide * 0.0035f))
                    drawCircle(skinShadow, radius = headRadius * 0.16f, center = Offset(headCenter.x + headRadius * 1.02f, headCenter.y + headRadius * 0.14f), style = Stroke(width = minSide * 0.0035f))
                    // nose wedge (front of head, points down toward phone)
                    val noseTip = Offset(headCenter.x, headCenter.y + headRadius * 1.06f)
                    val noseLeft = Offset(headCenter.x - headRadius * 0.18f, headCenter.y + headRadius * 0.62f)
                    val noseRight = Offset(headCenter.x + headRadius * 0.18f, headCenter.y + headRadius * 0.62f)
                    drawLine(skinShadow, noseLeft, noseTip, strokeWidth = minSide * 0.012f)
                    drawLine(skinShadow, noseRight, noseTip, strokeWidth = minSide * 0.012f)

                    // ---- Face plane line (orange, fixed) ----
                    drawLine(
                        color = facePlaneColor,
                        start = Offset(cx - minSide * 0.36f, facePlaneY),
                        end = Offset(cx + minSide * 0.36f, facePlaneY),
                        strokeWidth = minSide * 0.022f,
                    )
                    drawLabel(facePlaneLabel, cx - minSide * 0.36f, facePlaneY - minSide * 0.025f, Paint.Align.LEFT, facePlaneColor.toArgb())

                    // ---- Face direction arrow (orange, fixed, points down toward phone) ----
                    val faceArrowEnd = Offset(cx, screenEdgeY - minSide * 0.058f)
                    drawArrow(
                        color = facePlaneColor,
                        start = Offset(cx, facePlaneY + minSide * 0.020f),
                        end = faceArrowEnd,
                        strokeWidth = minSide * 0.015f,
                        headLength = minSide * 0.045f,
                    )
                    drawLabel(faceArrowLabel, cx + minSide * 0.040f, (facePlaneY + faceArrowEnd.y) / 2f + minSide * 0.012f, Paint.Align.LEFT, facePlaneColor.toArgb())

                    // ---- Rotation range arc around wrist ----
                    drawArc(
                        color = arcColor,
                        startAngle = 270f - angleRange,
                        sweepAngle = angleRange * 2f,
                        useCenter = false,
                        topLeft = Offset(wrist.x - arcRadius, wrist.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = minSide * 0.022f),
                    )
                    listOf(-angleRange, angleRange).forEach { limit ->
                        drawCircle(endpointColor, radius = minSide * 0.018f, center = rotatePoint(baseArcPoint, wrist, limit))
                    }
                    drawCircle(motionColor, radius = minSide * 0.024f, center = rotatePoint(baseArcPoint, wrist, angle))

                    // ---- Forearm (fixed, extends down from wrist) ----
                    drawRoundRect(
                        color = skinDark,
                        topLeft = Offset(wrist.x - minSide * 0.078f, wrist.y - minSide * 0.005f),
                        size = Size(minSide * 0.156f, size.height - wrist.y + minSide * 0.08f),
                        cornerRadius = CornerRadius(minSide * 0.080f, minSide * 0.080f),
                    )
                    drawRoundRect(
                        color = skin,
                        topLeft = Offset(wrist.x - minSide * 0.067f, wrist.y + minSide * 0.001f),
                        size = Size(minSide * 0.134f, size.height - wrist.y + minSide * 0.06f),
                        cornerRadius = CornerRadius(minSide * 0.072f, minSide * 0.072f),
                    )

                    // ---- Phone + screen + screen-direction arrow ----
                    fun drawPhoneTopView(alpha: Float, withArrow: Boolean, phoneCx: Float = cx, phoneCy: Float = phoneCenterY) {
                        val localScreenEdgeY = phoneCy - phoneThickness / 2f - minSide * 0.006f
                        // hand (slightly larger than phone, behind phone)
                        drawRoundRect(
                            color = skin.copy(alpha = alpha),
                            topLeft = Offset(phoneCx - phoneHalfWidth - minSide * 0.034f, phoneCy - phoneThickness / 2f - minSide * 0.006f),
                            size = Size((phoneHalfWidth + minSide * 0.034f) * 2f, phoneThickness + minSide * 0.012f),
                            cornerRadius = CornerRadius(minSide * 0.026f, minSide * 0.026f),
                        )
                        drawRoundRect(
                            color = skinDark.copy(alpha = alpha),
                            topLeft = Offset(phoneCx - phoneHalfWidth - minSide * 0.034f, phoneCy - phoneThickness / 2f - minSide * 0.006f),
                            size = Size((phoneHalfWidth + minSide * 0.034f) * 2f, phoneThickness + minSide * 0.012f),
                            cornerRadius = CornerRadius(minSide * 0.026f, minSide * 0.026f),
                            style = Stroke(width = minSide * 0.004f),
                        )
                        // phone body
                        drawRoundRect(
                            color = phone.copy(alpha = alpha),
                            topLeft = Offset(phoneCx - phoneHalfWidth, phoneCy - phoneThickness / 2f),
                            size = Size(phoneHalfWidth * 2f, phoneThickness),
                            cornerRadius = CornerRadius(minSide * 0.018f, minSide * 0.018f),
                        )
                        drawRoundRect(
                            color = phoneEdge.copy(alpha = alpha),
                            topLeft = Offset(phoneCx - phoneHalfWidth, phoneCy - phoneThickness / 2f),
                            size = Size(phoneHalfWidth * 2f, phoneThickness),
                            cornerRadius = CornerRadius(minSide * 0.018f, minSide * 0.018f),
                            style = Stroke(width = minSide * 0.004f),
                        )
                        // screen-plane band along the FRONT edge (face side) of the phone
                        drawRoundRect(
                            color = phoneScreenFill.copy(alpha = alpha),
                            topLeft = Offset(phoneCx - phoneHalfWidth + minSide * 0.012f, phoneCy - phoneThickness / 2f - minSide * 0.018f),
                            size = Size((phoneHalfWidth - minSide * 0.012f) * 2f, minSide * 0.022f),
                            cornerRadius = CornerRadius(minSide * 0.010f, minSide * 0.010f),
                        )
                        // screen-plane line (sharp underline of the band)
                        drawLine(
                            color = screenPlaneColor.copy(alpha = alpha),
                            start = Offset(phoneCx - phoneHalfWidth + minSide * 0.010f, localScreenEdgeY),
                            end = Offset(phoneCx + phoneHalfWidth - minSide * 0.010f, localScreenEdgeY),
                            strokeWidth = minSide * 0.022f,
                        )
                        // screen-direction arrow: perpendicular to screen-plane, points toward face
                        if (withArrow) {
                            drawArrow(
                                color = screenPlaneColor.copy(alpha = alpha),
                                start = Offset(phoneCx, localScreenEdgeY - minSide * 0.020f),
                                end = Offset(phoneCx, localScreenEdgeY - minSide * 0.150f),
                                strokeWidth = minSide * 0.015f,
                                headLength = minSide * 0.042f,
                            )
                        }
                    }

                    val screenLabelAnchor = if (mode == WristMotionMode.LATERAL_TRANSLATION) {
                        listOf(-angleRange, angleRange).forEach { ghostAngle ->
                            val ghost = rotatePoint(baseArcPoint, wrist, ghostAngle)
                            drawPhoneTopView(
                                alpha = 0.15f,
                                withArrow = false,
                                phoneCx = cx + (ghost.x - baseArcPoint.x),
                                phoneCy = phoneCenterY + (ghost.y - baseArcPoint.y)
                            )
                        }
                        val current = rotatePoint(baseArcPoint, wrist, angle)
                        val currentCx = cx + (current.x - baseArcPoint.x)
                        val currentCy = phoneCenterY + (current.y - baseArcPoint.y)
                        drawPhoneTopView(alpha = 1f, withArrow = true, phoneCx = currentCx, phoneCy = currentCy)
                        Offset(currentCx + minSide * 0.090f, currentCy - phoneThickness / 2f - minSide * 0.136f)
                    } else {
                        listOf(-angleRange, angleRange).forEach { ghostAngle ->
                            rotate(ghostAngle, pivot = wrist) { drawPhoneTopView(alpha = 0.15f, withArrow = false) }
                        }
                        rotate(angle, pivot = wrist) { drawPhoneTopView(alpha = 1f, withArrow = true) }
                        rotatePoint(
                            Offset(cx + minSide * 0.090f, screenEdgeY - minSide * 0.130f),
                            wrist,
                            angle,
                        )
                    }
                    drawLabel(screenArrowLabel, screenLabelAnchor.x, screenLabelAnchor.y, Paint.Align.LEFT, screenPlaneColor.toArgb())

                    // Wrist pivot marker (on top of forearm/phone)
                    drawCircle(panelSurface, radius = minSide * 0.030f, center = wrist)
                    drawCircle(motionColor, radius = minSide * 0.026f, center = wrist, style = Stroke(width = minSide * 0.008f))
                    drawCircle(motionColor.copy(alpha = 0.85f), radius = minSide * 0.012f, center = wrist)
                    drawLabel(pivotLabel, wrist.x, wrist.y + minSide * 0.060f, Paint.Align.CENTER, motionColor.toArgb())
                } else {
                    // ============== SIDE VIEW ==============
                    val faceX = size.width * 0.28f
                    val headCenter = Offset(faceX, size.height * 0.34f)
                    val headRadius = minSide * 0.115f
                    val facePlaneX = headCenter.x + headRadius * 0.65f
                    val wrist = Offset(size.width * 0.72f, size.height * 0.78f)
                    val arcRadius = minSide * 0.40f
                    val baseArcPoint = Offset(wrist.x, wrist.y - arcRadius)
                    val phoneWidth = minSide * 0.085f   // side view = thickness only
                    val phoneHeight = minSide * 0.46f
                    val phoneCenterY = wrist.y - phoneHeight / 2f - minSide * 0.055f
                    val screenEdgeX = wrist.x - phoneWidth / 2f - minSide * 0.006f

                    // ---- Side profile head ----
                    // hair (back-top of head)
                    drawArc(
                        color = hair,
                        startAngle = 130f,
                        sweepAngle = 170f,
                        useCenter = false,
                        topLeft = Offset(headCenter.x - headRadius * 1.12f, headCenter.y - headRadius * 1.12f),
                        size = Size(headRadius * 2.24f, headRadius * 2.24f),
                        style = Stroke(width = minSide * 0.040f),
                    )
                    drawCircle(skin, radius = headRadius, center = headCenter)
                    drawCircle(skinShadow, radius = headRadius, center = headCenter, style = Stroke(width = minSide * 0.005f))
                    // ear (visible side)
                    drawCircle(skin, radius = headRadius * 0.18f, center = Offset(headCenter.x - headRadius * 0.20f, headCenter.y + headRadius * 0.12f))
                    drawCircle(skinShadow, radius = headRadius * 0.18f, center = Offset(headCenter.x - headRadius * 0.20f, headCenter.y + headRadius * 0.12f), style = Stroke(width = minSide * 0.004f))
                    // eye looking right toward phone
                    drawCircle(faceFeature, radius = minSide * 0.013f, center = Offset(headCenter.x + headRadius * 0.48f, headCenter.y - headRadius * 0.08f))
                    // nose
                    drawLine(skinShadow, Offset(headCenter.x + headRadius * 0.86f, headCenter.y - headRadius * 0.04f), Offset(headCenter.x + headRadius * 1.12f, headCenter.y + headRadius * 0.18f), strokeWidth = minSide * 0.013f)
                    drawLine(skinShadow, Offset(headCenter.x + headRadius * 1.12f, headCenter.y + headRadius * 0.18f), Offset(headCenter.x + headRadius * 0.80f, headCenter.y + headRadius * 0.30f), strokeWidth = minSide * 0.013f)
                    // mouth
                    drawLine(skinShadow, Offset(headCenter.x + headRadius * 0.62f, headCenter.y + headRadius * 0.55f), Offset(headCenter.x + headRadius * 0.92f, headCenter.y + headRadius * 0.55f), strokeWidth = minSide * 0.011f)
                    // chin/neck hint
                    drawLine(skinShadow, Offset(headCenter.x + headRadius * 0.50f, headCenter.y + headRadius * 0.95f), Offset(headCenter.x, headCenter.y + headRadius * 1.10f), strokeWidth = minSide * 0.008f)

                    // ---- Face plane line (orange, vertical) ----
                    drawLine(
                        color = facePlaneColor,
                        start = Offset(facePlaneX, headCenter.y - headRadius * 1.35f),
                        end = Offset(facePlaneX, headCenter.y + headRadius * 1.65f),
                        strokeWidth = minSide * 0.022f,
                    )
                    drawLabel(facePlaneLabel, facePlaneX, headCenter.y - headRadius * 1.52f, Paint.Align.CENTER, facePlaneColor.toArgb())

                    // ---- Face direction arrow (orange, horizontal pointing right) ----
                    val faceArrowEnd = Offset(screenEdgeX - minSide * 0.065f, headCenter.y + headRadius * 0.05f)
                    drawArrow(
                        color = facePlaneColor,
                        start = Offset(facePlaneX + minSide * 0.020f, headCenter.y + headRadius * 0.05f),
                        end = faceArrowEnd,
                        strokeWidth = minSide * 0.015f,
                        headLength = minSide * 0.045f,
                    )
                    drawLabel(faceArrowLabel, (facePlaneX + faceArrowEnd.x) / 2f, headCenter.y - headRadius * 0.20f, Paint.Align.CENTER, facePlaneColor.toArgb())

                    // ---- Rotation range arc ----
                    drawArc(
                        color = arcColor,
                        startAngle = 270f - angleRange,
                        sweepAngle = angleRange * 2f,
                        useCenter = false,
                        topLeft = Offset(wrist.x - arcRadius, wrist.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = minSide * 0.022f),
                    )
                    listOf(-angleRange, angleRange).forEach { limit ->
                        drawCircle(endpointColor, radius = minSide * 0.018f, center = rotatePoint(baseArcPoint, wrist, limit))
                    }
                    drawCircle(motionColor, radius = minSide * 0.024f, center = rotatePoint(baseArcPoint, wrist, angle))

                    // ---- Forearm (fixed) ----
                    drawRoundRect(
                        color = skinDark,
                        topLeft = Offset(wrist.x - minSide * 0.078f, wrist.y - minSide * 0.005f),
                        size = Size(minSide * 0.156f, size.height - wrist.y + minSide * 0.08f),
                        cornerRadius = CornerRadius(minSide * 0.080f, minSide * 0.080f),
                    )
                    drawRoundRect(
                        color = skin,
                        topLeft = Offset(wrist.x - minSide * 0.067f, wrist.y + minSide * 0.001f),
                        size = Size(minSide * 0.134f, size.height - wrist.y + minSide * 0.06f),
                        cornerRadius = CornerRadius(minSide * 0.072f, minSide * 0.072f),
                    )

                    // ---- Phone + screen + screen-direction arrow (rotate around wrist) ----
                    fun drawPhoneSideView(alpha: Float, withArrow: Boolean) {
                        // hand (gripping bottom of phone, just above wrist)
                        drawRoundRect(
                            color = skin.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - minSide * 0.095f, wrist.y - minSide * 0.135f),
                            size = Size(minSide * 0.190f, minSide * 0.145f),
                            cornerRadius = CornerRadius(minSide * 0.060f, minSide * 0.060f),
                        )
                        drawRoundRect(
                            color = skinDark.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - minSide * 0.095f, wrist.y - minSide * 0.135f),
                            size = Size(minSide * 0.190f, minSide * 0.145f),
                            cornerRadius = CornerRadius(minSide * 0.060f, minSide * 0.060f),
                            style = Stroke(width = minSide * 0.004f),
                        )
                        // phone body
                        drawRoundRect(
                            color = phone.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - phoneWidth / 2f, phoneCenterY - phoneHeight / 2f),
                            size = Size(phoneWidth, phoneHeight),
                            cornerRadius = CornerRadius(minSide * 0.020f, minSide * 0.020f),
                        )
                        drawRoundRect(
                            color = phoneEdge.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - phoneWidth / 2f, phoneCenterY - phoneHeight / 2f),
                            size = Size(phoneWidth, phoneHeight),
                            cornerRadius = CornerRadius(minSide * 0.020f, minSide * 0.020f),
                            style = Stroke(width = minSide * 0.004f),
                        )
                        // screen-plane band on the LEFT edge (face side)
                        drawRoundRect(
                            color = phoneScreenFill.copy(alpha = alpha),
                            topLeft = Offset(wrist.x - phoneWidth / 2f - minSide * 0.024f, phoneCenterY - phoneHeight / 2f + minSide * 0.018f),
                            size = Size(minSide * 0.024f, phoneHeight - minSide * 0.036f),
                            cornerRadius = CornerRadius(minSide * 0.010f, minSide * 0.010f),
                        )
                        // sharp screen-plane line
                        drawLine(
                            color = screenPlaneColor.copy(alpha = alpha),
                            start = Offset(screenEdgeX, phoneCenterY - phoneHeight / 2f + minSide * 0.014f),
                            end = Offset(screenEdgeX, phoneCenterY + phoneHeight / 2f - minSide * 0.014f),
                            strokeWidth = minSide * 0.022f,
                        )
                        // screen-direction arrow: perpendicular to screen-plane, points toward face
                        if (withArrow) {
                            drawArrow(
                                color = screenPlaneColor.copy(alpha = alpha),
                                start = Offset(screenEdgeX - minSide * 0.020f, phoneCenterY),
                                end = Offset(screenEdgeX - minSide * 0.150f, phoneCenterY),
                                strokeWidth = minSide * 0.015f,
                                headLength = minSide * 0.042f,
                            )
                        }
                    }

                    listOf(-angleRange, angleRange).forEach { ghostAngle ->
                        rotate(ghostAngle, pivot = wrist) { drawPhoneSideView(alpha = 0.15f, withArrow = false) }
                    }
                    rotate(angle, pivot = wrist) { drawPhoneSideView(alpha = 1f, withArrow = true) }

                    // Screen-direction label follows the rotating arrow
                    val screenLabelAnchor = rotatePoint(
                        Offset(screenEdgeX - minSide * 0.165f, phoneCenterY - minSide * 0.030f),
                        wrist,
                        angle,
                    )
                    drawLabel(screenArrowLabel, screenLabelAnchor.x, screenLabelAnchor.y, Paint.Align.CENTER, screenPlaneColor.toArgb())

                    // Wrist pivot marker
                    drawCircle(panelSurface, radius = minSide * 0.030f, center = wrist)
                    drawCircle(motionColor, radius = minSide * 0.026f, center = wrist, style = Stroke(width = minSide * 0.008f))
                    drawCircle(motionColor.copy(alpha = 0.85f), radius = minSide * 0.012f, center = wrist)
                    drawLabel(pivotLabel, wrist.x, wrist.y + minSide * 0.060f, Paint.Align.CENTER, motionColor.toArgb())
                }

                // ============== View-perspective badge (top-right corner) ==============
                val badgeStr = viewBadge
                val badgePadX = minSide * 0.030f
                val badgePadY = minSide * 0.014f
                val measured = titlePaint.measureText(badgeStr)
                val badgeWidth = measured + badgePadX * 2f
                val badgeHeight = titlePaint.textSize + badgePadY * 2f
                val badgeLeft = size.width - badgeWidth - minSide * 0.030f
                val badgeTop = minSide * 0.030f
                drawRoundRect(
                    color = badgeBg,
                    topLeft = Offset(badgeLeft, badgeTop),
                    size = Size(badgeWidth, badgeHeight),
                    cornerRadius = CornerRadius(minSide * 0.024f, minSide * 0.024f),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    badgeStr,
                    badgeLeft + badgeWidth / 2f,
                    badgeTop + badgeHeight / 2f + titlePaint.textSize / 3f,
                    titlePaint,
                )
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

    1. 研究目的：本研究用于采集 Android 终端在自然使用中的运动传感器、触控时间、前台组件元数据和 UI 组件上下文，探索连续身份认证中的姿态、触控节奏与上下文特征。

    2. 采集数据：App 采集加速度计、陀螺仪、磁力计数据；记录全局屏幕触控交互开始/结束的时间，不记录触摸轨迹、位置、压力或面积；通过无障碍服务读取前台 App 包名、Activity/ComponentName、输入法是否显示、可访问节点的类型、资源 ID、控件显示文本、位置网格、可点击、可长按、可滚动、可编辑、可见、选中、聚焦、启用状态和事件类型。

    3. 不采集内容：App 不执行点击、输入、手势注入或截图；不读取通讯录、短信、通话记录、相册、麦克风、摄像头、精确位置或不可访问的应用私有数据；不记录逐字按键时刻、按键间隔、按住位置或触摸坐标。

    4. 端侧脱敏：输入框原文不会保存或上传；密码节点整棵丢弃；控件显示文本中的手机号、邮箱、URL、银行卡号、身份证号、长数字串和令牌样式字符串会替换为占位符。服务端会下发可更新的文本脱敏规则；即使远程规则不可用，内置基线规则也会保持生效。不会因为包名跳过前端 UI 采集。

    5. 设备标识：研究 device_id 由 ANDROID_ID 与研究 salt 通过 HMAC-SHA256 生成，不使用 IMEI、序列号、MAC 地址等不可重置硬件标识符。

    6. 上传与安全：数据按 5 秒 batch 生成 JSON，经 LZ4 frame 压缩后上传到配置的研究服务器。生产部署应使用 HTTPS/TLS；本原型阶段不使用内容 AES 加密。

    7. 采集边界：仅在用户同意、三项权限完成、有效研究 device_id 且屏幕点亮解锁时采集。服务器连通、ClockSync、规则刷新和 Wi-Fi 策略只影响上传、重试与诊断状态，不阻塞本地采集；息屏或锁屏时暂停。

    8. 退出与删除：参与者可联系研究者撤回同意、请求导出或删除数据。退出后不会继续采集新的研究数据。

    请保持坐姿稳定阅读，如需查看完整内容，可在此文本框内自然下滑。
    """.trimIndent(),
    """
    Research Protocol

    1. Purpose: This study collects Android motion-sensor data, touch timing, foreground component metadata, and UI component context during natural use to explore posture, touch rhythm, and context features for continuous authentication.

    2. Data collected: The app collects accelerometer, gyroscope, and magnetic-field data. It records global screen touch interaction start and end times without touch paths, positions, pressure, or size. Through AccessibilityService it reads foreground app package name, Activity/ComponentName, input-method visibility, accessible node type, resource ID, visible component text, position grid, clickable, long-clickable, scrollable, editable, visible, checked, focused, enabled states, and event type.

    3. Data not collected: The app does not perform clicks, input, gesture injection, or screenshots. It does not read contacts, SMS, call logs, photos, microphone, camera, precise location, or inaccessible private app data. It does not record per-key timestamps, key intervals, held positions, or touch coordinates.

    4. On-device redaction: Raw input-field text is never stored or uploaded. Password nodes are dropped entirely. Phone numbers, emails, URLs, card numbers, ID numbers, long numeric strings, and token-like strings in visible component text are replaced with placeholders. The server provides updatable text redaction rules; built-in baseline rules remain active even when remote rules are unavailable. Foreground UI collection is not skipped by package name.

    5. Device identifier: The research device_id is derived from ANDROID_ID and the study salt with HMAC-SHA256. IMEI, serial number, MAC address, and other non-resettable hardware identifiers are not used.

    6. Upload and security: Data is batched every 5 seconds as JSON, compressed with LZ4 frame, and uploaded to the configured research server. Production deployment should use HTTPS/TLS. This prototype stage does not use payload AES encryption.

    7. Collection boundaries: Collection runs only after consent, all three permissions, a valid research device_id, and an on/unlocked screen. Server reachability, ClockSync, rule refresh, and the Wi-Fi policy affect upload, retry, and diagnostics only; they do not block local collection. Collection pauses when the screen is off or locked.

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
private fun DetailScreen(state: com.contextauth.core.UiState) {
    var showUploadHistory by remember { mutableStateOf(false) }
    Page(l("详细", "Details")) {
        CardBlock(l("运行概览", "Runtime Overview")) {
            InfoRow(l("自动采集", "Auto collection"), automaticCollectionText(state.status, canStart = state.accessibilityEnabled && state.batteryWhitelisted && state.notificationAllowed))
            InfoRow(l("服务器连通", "Server reachable"), yesNo(state.serverReachable))
            InfoRow(l("最近连通性测试", "Latest connectivity test"), if (state.lastServerHealthAtWallMillis > 0) formatTime(state.lastServerHealthAtWallMillis) else l("未检查", "Not checked"))
            InfoRow(l("最后上传", "Last upload"), formatUploadTime(state.diagnostics.lastUploadAtWallMillis))
            InfoRow(l("最近规则检查", "Latest rule check"), displayMessage(state.diagnostics.lastRuleCheck))
        }
        CardBlock(l("应用与设备", "App and Device")) {
            InfoRow(l("版本", "Version"), BuildConfig.VERSION_NAME)
            InfoRow("commit", BuildConfig.GIT_COMMIT)
            InfoRow(l("研究 device_id", "Research device_id"), maskDeviceId(state.deviceId))
            InfoRow(l("上报目标", "Endpoint"), hostOnly(state.settings.serverUrl))
            InfoRow(l("规则版本", "Rule version"), state.settings.ruleVersion)
            InfoRow(l("规则 hash", "Rule hash"), state.settings.ruleHash.take(12) + "...")
        }
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
        CardBlock(l("本机上传历史", "Local Upload History")) {
            InfoRow(l("已记录", "Recorded"), l("${state.diagnostics.uploadHistory.size} 条", "${state.diagnostics.uploadHistory.size} entries"))
            InfoRow(l("最后上传", "Last upload"), formatUploadTime(state.diagnostics.lastUploadAtWallMillis))
            OutlinedButton(onClick = { showUploadHistory = true }, modifier = Modifier.fillMaxWidth()) {
                Text(l("查看本机上传历史", "View local upload history"))
            }
        }
        CardBlock(l("时钟同步", "Clock Sync")) {
            InfoRow(l("最近校准", "Latest sync"), formatTime(state.clock.lastSyncedAtWallMillis))
            InfoRow("RTT", "${state.clock.lastRttMillis} ms")
            InfoRow("offset", "${state.clock.serverOffsetMillis} ms")
            InfoRow("drift", "${state.clock.estimatedDriftPpm.toInt()} ppm")
            InfoRow(l("来源", "Source"), clockSourceLabel(state.clock.source))
            InfoRow(l("最近错误", "Latest error"), displayClockError(state.clock.lastError))
        }
    }
    if (showUploadHistory) {
        AlertDialog(
            onDismissRequest = { showUploadHistory = false },
            title = { Text(l("本机上传历史", "Local Upload History")) },
            text = {
                val history = state.diagnostics.uploadHistory
                if (history.isEmpty()) {
                    Text(l("暂无本机上传历史", "No local upload history yet"))
                } else {
                    Column(
                        Modifier
                            .height(360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        history.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.fileName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatTime(item.uploadedAtWallMillis)} / ${formatBytes(item.sizeBytes)} / ${uploadStatusLabel(item.status)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    displayMessage(item.serverMessage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUploadHistory = false }) {
                    Text(l("关闭", "Close"))
                }
            }
        )
    }
}

@Composable
private fun Page(
    title: String,
    footer: (@Composable () -> Unit)? = null,
    listState: LazyListState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveListState = listState ?: rememberLazyListState()
    LazyColumn(
        state = effectiveListState,
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
    lastUploadAtWallMillis: Long,
    latestMessage: String,
    status: CollectionStatus,
    canStart: Boolean,
    onTestServer: () -> Unit
) {
    CardBlock(l("采集状态", "Collection Status")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onTestServer,
                label = { Text(serverConnectionText(reachable)) },
                leadingIcon = { Icon(Icons.Outlined.Info, null) }
            )
            AssistChip(
                onClick = {},
                label = { Text(l("最后上传 ", "Last upload ") + formatUploadTime(lastUploadAtWallMillis)) },
                leadingIcon = { Icon(Icons.Outlined.CloudUpload, null) }
            )
        }
        InfoRow(l("自动采集", "Auto collection"), automaticCollectionText(status, canStart))
        InfoRow(l("最近连通性测试", "Latest connectivity test"), if (lastCheckedAtWallMillis > 0) formatTime(lastCheckedAtWallMillis) else l("未检查", "Not checked"))
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
private fun automaticCollectionText(status: CollectionStatus, canStart: Boolean): String = when (status) {
    CollectionStatus.RUNNING -> l("自动采集中", "Auto collecting")
    CollectionStatus.PAUSED_BY_SCREEN_OFF -> l("息屏暂停", "Paused: screen off")
    CollectionStatus.PAUSED_BY_LOCKED -> l("锁屏暂停", "Paused: locked")
    CollectionStatus.PAUSED_BY_NO_NETWORK -> l("等待网络上传", "Waiting for network upload")
    CollectionStatus.IDLE -> if (canStart) l("待自动启动", "Ready to auto-start") else l("未满足权限", "Permissions pending")
}
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

private fun formatUploadTime(millis: Long): String = if (millis <= 0) l("未上传", "Not uploaded") else formatTime(millis)
private fun formatTime(millis: Long): String = if (millis <= 0) l("未完成", "Not completed") else SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(millis))
private fun formatDuration(valueMs: Int): String {
    val totalSeconds = (valueMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, totalSeconds / 60, totalSeconds % 60)
}
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    bytes >= 1024L -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "$bytes B"
}
private fun uploadStatusLabel(status: String): String = when (status) {
    "SUCCESS" -> l("成功", "Success")
    "QUEUED" -> l("已入队", "Queued")
    "QUEUED_NO_NETWORK" -> l("无网络入队", "Queued: no network")
    "REPLAY_SUCCESS" -> l("重试成功", "Replay success")
    "RETRY_SCHEDULED" -> l("等待重试", "Retry scheduled")
    "DEAD_LETTER" -> l("停止重试", "Dead letter")
    "FAILED" -> l("失败", "Failed")
    else -> status
}
private fun manufacturerGuide(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi" -> l("请在 MIUI 自启动管理中允许后台运行。为保证 5 秒一次上传实时性，需关闭后台限制；本 App 仅在屏幕点亮且已解锁时采集，息屏即停止。", "Allow background running in MIUI autostart settings. To keep 5-second uploads timely, disable background restrictions; collection runs only while the screen is on and unlocked.")
    "huawei", "honor" -> l("请在启动管理中允许后台运行。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Allow background running in Launch Management. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    "oppo", "oneplus" -> l("请允许自启动与关联启动。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Allow autostart and associated startup. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    "vivo", "iqoo" -> l("请允许后台高耗电。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Allow high background power use. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    "samsung" -> l("请将 App 从睡眠模式中排除。为保证 5 秒一次上传实时性，需关闭后台限制；息屏即停止采集。", "Exclude the app from sleeping apps. To keep 5-second uploads timely, disable background restrictions; collection stops when the screen is off.")
    else -> l("请在应用详情中允许后台运行。为保证 5 秒一次上传实时性，需关闭后台限制；本 App 仅在屏幕点亮且已解锁时采集，息屏即停止。", "Allow background running in app details. To keep 5-second uploads timely, disable background restrictions; collection runs only while the screen is on and unlocked.")
}
