package com.contextauth.core

import java.util.UUID

const val DEFAULT_SERVER_URL = "https://cca.macrz.com"
const val SERVER_STUDY_SALT = "Continuous_Authentication"

enum class CollectionStatus {
    IDLE,
    RUNNING,
    PAUSED_BY_SCREEN_OFF,
    PAUSED_BY_LOCKED,
    PAUSED_BY_NO_NETWORK
}

enum class CollectionSource {
    BUILTIN_TASK,
    THIRD_PARTY_APP
}

enum class TaskCategory(
    val intuitiveDescription: String,
    val taskName: String,
    val subtitle: String,
    val intuitiveDescriptionEn: String,
    val taskNameEn: String,
    val subtitleEn: String
) {
    C0(
        "持机静止",
        "静置计时",
        "自然握持手机，尽量不操作，用于观测手部微抖和设备姿态稳定性",
        "Quiet hold",
        "Still timer",
        "Hold the phone naturally with minimal interaction to capture hand tremor and posture stability"
    ),
    C1(
        "静态阅读",
        "研究协议阅读",
        "保持静止查看研究协议，可少量下滑查看完整内容",
        "Static reading",
        "Research protocol reading",
        "Read the study protocol while staying still; light scrolling is allowed for the full text"
    ),
    C2(
        "单指滑动信息流",
        "研究咨询流",
        "阅读分段信息流，并进行连续滚动、停顿、展开、返回等操作",
        "Single-finger feed",
        "Research information feed",
        "Read a segmented feed with natural scrolling, pauses, expansion, and back navigation"
    ),
    C3(
        "文本输入",
        "段落抄写",
        "使用最舒适的输入方式抄写混合文本，体现输入节奏和握持变化",
        "Text entry",
        "Paragraph copy",
        "Copy mixed text with your preferred input method to capture typing rhythm and grip changes"
    ),
    C4(
        "多控件操作",
        "模拟手机设置",
        "在多个 Tab、按钮、滑块、单选框、复选框和输入框之间切换",
        "Multi-control operation",
        "Simulated phone settings",
        "Switch between tabs, buttons, sliders, radio buttons, checkboxes, and text fields"
    ),
    C5(
        "主动倾斜操作",
        "倾斜迷宫",
        "通过设备倾斜控制小球移动，产生明显姿态和角速度变化",
        "Active tilt",
        "Tilt maze",
        "Move the ball by tilting the phone to create clear posture and angular-velocity changes"
    ),
    C6(
        "显式转腕挑战",
        "手腕转动",
        "按动画指示做标准化左右与前后手腕旋转动作",
        "Explicit wrist rotation",
        "Wrist rotation",
        "Follow the animation to perform standardized left-right and forward-back wrist rotations"
    );

    val displayName: String
        get() = "$intuitiveDescription —— $taskName"
    val sequence: Int
        get() = ordinal

    fun localizedIntuitiveDescription(): String = LocaleText.pick(intuitiveDescription, intuitiveDescriptionEn)
    fun localizedTaskName(): String = LocaleText.pick(taskName, taskNameEn)
    fun localizedSubtitle(): String = LocaleText.pick(subtitle, subtitleEn)
    fun localizedDisplayName(): String = "${localizedIntuitiveDescription()} - ${localizedTaskName()}"

    companion object {
        const val POSTURE_GUIDE = "请在坐姿下、身体静止状态下，以自己最放松和舒适的握持姿势完成任务。"

        fun localizedPostureGuide(): String = LocaleText.pick(
            POSTURE_GUIDE,
            "Complete each task seated, keeping your body still and using your most relaxed, comfortable grip."
        )
    }
}

data class SensorSample(
    val sensorType: String,
    val timestampElapsedNanos: Long,
    val wallTimeEstimatedMillis: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int
)

data class NodeSnapshot(
    val nodeId: String,
    val packageNameHash: String?,
    val className: String?,
    val viewIdHash: String?,
    val textRedacted: String?,
    val contentDescRedacted: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
    val childCount: Int,
    val depth: Int,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val boundsGrid: Map<String, Int> = mapOf("left" to 0, "top" to 0, "right" to 0, "bottom" to 0),
    val actionsSummary: List<String> = emptyList()
)

data class ContextEventSnapshot(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val eventTimeWallMillis: Long,
    val packageNameHash: String?,
    val windowTitleRedacted: String?,
    val rootNodes: List<NodeSnapshot>,
    val redactionSummary: Map<String, Int>
)

data class ContextFeature(
    val featureId: String = UUID.randomUUID().toString(),
    val eventId: String,
    val computedAtWallMillis: Long,
    val collectionSource: CollectionSource,
    val taskCategory: TaskCategory?,
    val taskSessionId: String?,
    val editableCount: Int,
    val scrollableCount: Int,
    val clickableCount: Int,
    val mediaLikeScore: Double,
    val listLikeScore: Double,
    val formLikeScore: Double,
    val gameLikeScore: Double,
    val nodeClassHistogram: Map<String, Int>,
    val eventType: String,
    val coarseOrientation: String,
    val estimatedContextCategory: String
)

data class Batch(
    val batchId: String,
    val deviceId: String,
    val collectionSource: CollectionSource,
    val taskCategory: TaskCategory?,
    val taskSessionId: String?,
    val taskStartedAtWallMillis: Long?,
    val taskElapsedSecondsAtBatchEnd: Int?,
    val startedAtWallMillis: Long,
    val endedAtWallMillis: Long,
    val baseElapsedNanos: Long,
    val sensorSamples: List<SensorSample>,
    val contextEvents: List<ContextEventSnapshot>,
    val contextFeatures: List<ContextFeature>,
    val skipEvents: List<Map<String, Any?>>,
    val gatedResume: Boolean = false
)

data class PayloadEnvelope(
    val algorithm: String,
    val payloadBase64: String,
    val payloadSha256Hex: String,
    val deviceId: String,
    val batchId: String,
    val ruleVersion: String,
    val ruleHash: String,
    val createdAtWallMillis: Long
)

data class ClockSyncState(
    val synced: Boolean = false,
    val lastSyncedAtWallMillis: Long = 0L,
    val lastRttMillis: Long = 0L,
    val serverOffsetMillis: Long = 0L,
    val estimatedDriftPpm: Double = 0.0,
    val source: String = "none",
    val lastError: String? = null
)

data class DiagnosticsState(
    val accessibilityEnabled: Boolean = false,
    val uploadSuccess: Int = 0,
    val uploadFailure: Int = 0,
    val retrying: Int = 0,
    val queueBytes: Long = 0L,
    val queueEntries: Int = 0,
    val lastBatchId: String = "-",
    val lastServerMessage: String = "not_uploaded",
    val lastError: String = "-",
    val serializeCompressP50Ms: Long = 0L,
    val serializeCompressP95Ms: Long = 0L,
    val shaP50Ms: Long = 0L,
    val shaP95Ms: Long = 0L,
    val uploadP50Ms: Long = 0L,
    val uploadP95Ms: Long = 0L,
    val eventBuckets: Map<String, Int> = emptyMap(),
    val screenGateHistory: List<String> = emptyList(),
    val droppedDueToQuota: Int = 0,
    val lastQueueReplayAtWallMillis: Long = 0L,
    val earliestQueueEntryAtWallMillis: Long = 0L,
    val lastRuleCheck: String = "not_checked"
)

data class AppSettings(
    val consentGranted: Boolean = false,
    val serverUrl: String = DEFAULT_SERVER_URL,
    val serverStudySalt: String = SERVER_STUDY_SALT,
    val serverOverridden: Boolean = false,
    val batchSeconds: Int = 5,
    val taskSeconds: Int = 30,
    val allowThirdParty: Boolean = true,
    val wifiOnly: Boolean = true,
    val ruleVersion: String = "1",
    val ruleHash: String = "0".repeat(64)
)

data class UiState(
    val settings: AppSettings = AppSettings(),
    val status: CollectionStatus = CollectionStatus.IDLE,
    val deviceId: String = "pending",
    val clock: ClockSyncState = ClockSyncState(),
    val diagnostics: DiagnosticsState = DiagnosticsState(),
    val accelerometerHz: Double = 0.0,
    val gyroscopeHz: Double = 0.0,
    val magnetometerHz: Double = 0.0,
    val accelerometerAvailable: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val magnetometerAvailable: Boolean = false,
    val batteryWhitelisted: Boolean = false,
    val notificationAllowed: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val serverReachable: Boolean = false,
    val lastServerHealthAtWallMillis: Long = 0L,
    val recentUploadStatus: String = "not_uploaded",
    val completedTasks: Set<TaskCategory> = emptySet()
)
