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
        "横屏触控挑战",
        "点击蓝色小球",
        "横屏点击随机出现的蓝色小球，完成连续目标触控",
        "Landscape touch challenge",
        "Blue ball tapping",
        "Tap blue balls that appear at random landscape positions to capture target-touch timing"
    ),
    C6(
        "视频观看",
        "本地视频播放",
        "以最舒适的手持姿势观看视频，并自然使用暂停、倍速、进度拖动和横竖屏切换",
        "Video watching",
        "Local video playback",
        "Watch the video in your most comfortable grip and naturally use pause, speed, seek, and orientation controls"
    ),
    C7(
        "显式转腕挑战",
        "手腕转动",
        "按动画指示做标准化左右摇摆、左右平移与前后内收动作",
        "Explicit wrist rotation",
        "Wrist rotation",
        "Follow the animation to perform standardized left-right swing, lateral translation, and forward-back flexion"
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
    val className: String?,
    val viewIdResourceName: String?,
    val text: String?,
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
    val visibleToUser: Boolean = true,
    val longClickable: Boolean = false,
    val boundsGrid: Map<String, Int> = mapOf("left" to 0, "top" to 0, "right" to 0, "bottom" to 0),
    val actionsSummary: List<String> = emptyList()
)

data class ContextEventSnapshot(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val eventTimeWallMillis: Long,
    val appPackageName: String?,
    val foregroundActivityClassName: String?,
    val foregroundComponentName: String?,
    val inputMethodVisible: Boolean,
    val coarseOrientation: String = CoarseOrientation.UNKNOWN,
    val windowTitleRedacted: String?,
    val rootNodes: List<NodeSnapshot>,
    val redactionSummary: Map<String, Int>
)

data class TouchEventSnapshot(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val eventTimeUptimeMillis: Long,
    val eventTimeWallMillis: Long,
    val collectedAtWallMillis: Long
)

data class ContextFeature(
    val featureId: String = UUID.randomUUID().toString(),
    val eventId: String,
    val computedAtWallMillis: Long,
    val collectionSource: CollectionSource,
    val taskCategory: TaskCategory?,
    val taskSessionId: String?,
    val inputMethodVisible: Boolean = false,
    val editableCount: Int,
    val scrollableCount: Int,
    val clickableCount: Int,
    val mediaLikeScore: Double,
    val listLikeScore: Double,
    val formLikeScore: Double,
    val gameLikeScore: Double,
    val passwordNodeSeen: Boolean,
    val nodeClassHistogram: Map<String, Int>,
    val eventType: String,
    val coarseOrientation: String,
    val estimatedContextCategory: String
)

data class Batch(
    val batchId: String,
    val deviceId: String,
    val sessionId: String,
    val collectionSource: CollectionSource,
    val appPackageName: String? = null,
    val foregroundActivityClassName: String? = null,
    val foregroundComponentName: String? = null,
    val taskCategory: TaskCategory?,
    val taskSessionId: String?,
    val taskStartedAtWallMillis: Long?,
    val taskElapsedSecondsAtBatchEnd: Int?,
    val startedAtWallMillis: Long,
    val endedAtWallMillis: Long,
    val baseElapsedNanos: Long,
    val sensorSamples: List<SensorSample>,
    val touchEvents: List<TouchEventSnapshot>,
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

object RuleDefaults {
    const val BASELINE_VERSION = "1"
    const val ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    const val BASELINE_RULE_HASH = ZERO_HASH

    private val sha256Regex = Regex("^[a-f0-9]{64}$")

    fun usableVersion(version: String?): String =
        version?.trim()?.takeIf { it.isNotBlank() } ?: BASELINE_VERSION

    fun usableRuleHash(hash: String?): String {
        val normalized = hash?.trim()?.lowercase()
        return if (normalized != null && sha256Regex.matches(normalized)) {
            normalized
        } else {
            ZERO_HASH
        }
    }
}

data class ClockSyncState(
    val synced: Boolean = false,
    val lastSyncedAtWallMillis: Long = 0L,
    val lastRttMillis: Long = 0L,
    val serverOffsetMillis: Long = 0L,
    val estimatedDriftPpm: Double = 0.0,
    val source: String = "none",
    val lastError: String? = null
)

data class UploadHistoryEntry(
    val fileName: String,
    val batchId: String,
    val uploadedAtWallMillis: Long,
    val sizeBytes: Long,
    val status: String,
    val serverMessage: String
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
    val lastRuleCheck: String = "not_checked",
    val lastUploadAtWallMillis: Long = 0L,
    val uploadHistory: List<UploadHistoryEntry> = emptyList()
)

data class AppSettings(
    val consentGranted: Boolean = false,
    val serverUrl: String = DEFAULT_SERVER_URL,
    val serverStudySalt: String = SERVER_STUDY_SALT,
    val serverOverridden: Boolean = false,
    val batchSeconds: Int = 5,
    val taskSeconds: Int = 30,
    val wifiOnly: Boolean = true,
    val ruleVersion: String = RuleDefaults.BASELINE_VERSION,
    val ruleHash: String = RuleDefaults.BASELINE_RULE_HASH
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
    val accelerometerCollectionHz: Double = 0.0,
    val gyroscopeCollectionHz: Double = 0.0,
    val magnetometerCollectionHz: Double = 0.0,
    val accelerometerAvailable: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val magnetometerAvailable: Boolean = false,
    val accelerometerLostSamples: Int = 0,
    val gyroscopeLostSamples: Int = 0,
    val magnetometerLostSamples: Int = 0,
    val batteryWhitelisted: Boolean = false,
    val notificationAllowed: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val serverReachable: Boolean = false,
    val lastServerHealthAtWallMillis: Long = 0L,
    val recentUploadStatus: String = "not_uploaded",
    val completedTasks: Set<TaskCategory> = emptySet()
)
