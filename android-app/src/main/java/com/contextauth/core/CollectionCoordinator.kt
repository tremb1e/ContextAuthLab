package com.contextauth.core

import android.app.NotificationManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.KeyguardManager
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class CollectionCoordinator(private val context: Context) {
    private data class ActiveCollectionContext(
        val collectionSessionId: String,
        val taskCategory: TaskCategory?,
        val taskSessionId: String?,
        val taskStartedAt: Long?,
        val gatedResume: Boolean,
        val baseElapsedNanos: Long
    )

    private data class PendingStoppedBatch(
        val state: UiState,
        val samples: List<SensorSample>,
        val touches: List<TouchEventSnapshot>,
        val events: List<ContextEventSnapshot>,
        val collectionContext: ActiveCollectionContext
    )

    private val appContext = context.applicationContext
    private val settingsStore = SettingsStore(appContext)
    private val sensorCollector = SensorCollector(appContext)
    private val deviceIdProvider = DeviceIdProvider(appContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
    private val uploader = Uploader(appContext, httpClient)
    private val featureExtractor = ContextFeatureExtractor()
    private val clockSync = ClockSyncService(httpClient, settingsStore)
    private val ruleClient = RuleUpdateClient(httpClient)
    private val connectionTester = ConnectionTester(httpClient)
    private val mutableUi = MutableStateFlow(UiState(settings = settingsStore.settings.value))
    val uiState: StateFlow<UiState> = mutableUi.asStateFlow()
    val clockState: StateFlow<ClockSyncState> = clockSync.state

    private val contextEvents = ArrayList<ContextEventSnapshot>()
    private val touchEvents = ArrayList<TouchEventSnapshot>()
    private var collectionJob: Job? = null
    private var collectionSessionId: String = UUID.randomUUID().toString()
    private var taskSessionId: String? = null
    private var taskCategory: TaskCategory? = null
    private var taskStartedAt: Long? = null
    private var gatedResume = false
    private var resumeAfterGate = false
    private var screenReceiverRegistered = false
    private val serializeCompressTimes = ArrayDeque<Long>()
    private val shaTimes = ArrayDeque<Long>()
    private val uploadTimes = ArrayDeque<Long>()

    fun startRuntime(scope: CoroutineScope) {
        scope.launch {
            settingsStore.settings.collectLatest {
                val id = deviceIdProvider.getOrCreateDeviceId(it.serverStudySalt)
                mutableUi.value = mutableUi.value.copy(settings = it, deviceId = id)
            }
        }
        scope.launch {
            sensorCollector.metrics.collectLatest { metrics ->
                mutableUi.value = mutableUi.value.copy(
                    accelerometerHz = metrics.accelerometerHz,
                    gyroscopeHz = metrics.gyroscopeHz,
                    magnetometerHz = metrics.magnetometerHz,
                    accelerometerCollectionHz = metrics.accelerometerCollectionHz,
                    gyroscopeCollectionHz = metrics.gyroscopeCollectionHz,
                    magnetometerCollectionHz = metrics.magnetometerCollectionHz,
                    accelerometerAvailable = metrics.accelerometerAvailable,
                    gyroscopeAvailable = metrics.gyroscopeAvailable,
                    magnetometerAvailable = metrics.magnetometerAvailable,
                    accelerometerLostSamples = metrics.accelerometerLostSamples,
                    gyroscopeLostSamples = metrics.gyroscopeLostSamples,
                    magnetometerLostSamples = metrics.magnetometerLostSamples
                )
            }
        }
        scope.launch {
            clockSync.state.collectLatest { clock ->
                mutableUi.value = mutableUi.value.copy(clock = clock)
            }
        }
        scope.launch {
            AccessibilityEventBus.events.collect { event ->
                if (mutableUi.value.status != CollectionStatus.RUNNING) return@collect
                synchronized(contextEvents) { contextEvents.add(event) }
                val buckets = mutableUi.value.diagnostics.eventBuckets.toMutableMap()
                buckets[event.eventType] = (buckets[event.eventType] ?: 0) + 1
                mutableUi.value = mutableUi.value.copy(diagnostics = mutableUi.value.diagnostics.copy(eventBuckets = buckets))
            }
        }
        scope.launch {
            TouchEventBus.events.collect { event ->
                if (mutableUi.value.status != CollectionStatus.RUNNING) return@collect
                synchronized(touchEvents) { touchEvents.add(event) }
                val buckets = mutableUi.value.diagnostics.eventBuckets.toMutableMap()
                buckets[event.eventType] = (buckets[event.eventType] ?: 0) + 1
                mutableUi.value = mutableUi.value.copy(diagnostics = mutableUi.value.diagnostics.copy(eventBuckets = buckets))
            }
        }
        scope.launch {
            CollectionControlBus.stopRequests.collectLatest {
                stopCollection(scope)
            }
        }
        clockSync.start(scope)
        registerScreenGate(scope)
        scope.launch { serverHealthLoop() }
        scope.launch {
            while (true) {
                delay(15_000)
                replayFailureQueueIfAllowed()
            }
        }
        refreshPermissionState()
    }

    fun grantConsent() {
        settingsStore.setConsent(true)
    }

    fun syncNow(scope: CoroutineScope) {
        refreshPermissionState()
        clockSync.trigger(scope)
        scope.launch {
            recordServerHealth(connectionTester.testHealth(settingsStore.settings.value.serverUrl))
        }
    }

    fun updateServerUrl(url: String, scope: CoroutineScope) {
        settingsStore.setServerUrl(url)
        mutableUi.value = mutableUi.value.copy(serverReachable = false, lastServerHealthAtWallMillis = 0L)
        clockSync.trigger(scope)
        scope.launch { checkRules(apply = true) }
    }

    fun resetServerUrl(scope: CoroutineScope) {
        settingsStore.resetServerUrl()
        mutableUi.value = mutableUi.value.copy(serverReachable = false, lastServerHealthAtWallMillis = 0L)
        clockSync.trigger(scope)
    }

    fun setBatchSeconds(value: Int) = settingsStore.setBatchSeconds(value)
    fun setTaskSeconds(value: Int) = settingsStore.setTaskSeconds(value)
    fun setWifiOnly(value: Boolean) = settingsStore.setWifiOnly(value)
    fun clearQueue() = uploader.clearQueue().also { refreshQueue() }

    fun testConnection(url: String, scope: CoroutineScope) {
        scope.launch {
            val result = connectionTester.testHealth(url)
            recordServerHealth(result)
        }
    }

    fun checkRules(scope: CoroutineScope, apply: Boolean = false) {
        scope.launch { checkRules(apply) }
    }

    fun startCollection(scope: CoroutineScope, category: TaskCategory? = null) {
        refreshPermissionState()
        val state = mutableUi.value
        if (!canStart(state)) return
        val previousContext = currentCollectionContext()
        if (state.status == CollectionStatus.RUNNING && previousContext.taskCategory == category) return
        if (state.status == CollectionStatus.RUNNING) {
            collectionJob?.cancel()
            val samples = sensorCollector.stop()
            val events = drainContextEvents()
            val touches = drainTouchEvents()
            scope.launch { uploadBatch(state, samples, touches, events, previousContext) }
        }
        val previousCategory = taskCategory
        val previousSession = taskSessionId
        val previousStartedAt = taskStartedAt
        taskCategory = category
        taskSessionId = if (category != null && gatedResume && previousCategory == category && previousSession != null) {
            previousSession
        } else {
            category?.let { UUID.randomUUID().toString() }
        }
        taskStartedAt = if (category != null && gatedResume && previousCategory == category && previousStartedAt != null) {
            previousStartedAt
        } else {
            category?.let { System.currentTimeMillis() }
        }
        drainContextEvents()
        drainTouchEvents()
        sensorCollector.start(state.clock.serverOffsetMillis)
        mutableUi.value = mutableUi.value.copy(status = CollectionStatus.RUNNING)
        collectionJob?.cancel()
        collectionJob = scope.launch {
            while (true) {
                delay(settingsStore.settings.value.batchSeconds * 1000L)
                flushBatch()
            }
        }
    }

    fun stopCollection(scope: CoroutineScope) {
        val state = mutableUi.value
        if (state.status == CollectionStatus.IDLE) return
        val finishedContext = currentCollectionContext()
        collectionJob?.cancel()
        val samples = sensorCollector.stop()
        val events = drainContextEvents()
        val touches = drainTouchEvents()
        taskCategory = null
        taskSessionId = null
        taskStartedAt = null
        if (!finishedContext.gatedResume) collectionSessionId = UUID.randomUUID().toString()
        mutableUi.value = mutableUi.value.copy(status = CollectionStatus.IDLE)
        scope.launch { uploadBatch(state, samples, touches, events, finishedContext) }
    }

    fun pauseForScreen(reason: CollectionStatus, scope: CoroutineScope? = null) {
        val pending = stopForScreenGate(reason)
        if (scope != null && (pending.samples.isNotEmpty() || pending.touches.isNotEmpty() || pending.events.isNotEmpty())) {
            scope.launch { uploadBatch(pending.state, pending.samples, pending.touches, pending.events, pending.collectionContext) }
        }
    }

    fun resumeAfterUnlock(scope: CoroutineScope) {
        if (resumeAfterGate && mutableUi.value.settings.consentGranted) {
            gatedResume = true
            resumeAfterGate = false
            startCollection(scope, taskCategory)
        }
    }

    fun markTaskComplete(category: TaskCategory) {
        val next = mutableUi.value.completedTasks + category
        mutableUi.value = mutableUi.value.copy(completedTasks = next)
    }

    fun exportDiagnostics(): String {
        val file = appContext.filesDir.resolve("diagnostics-${System.currentTimeMillis()}.json")
        val state = mutableUi.value
        file.writeText(
            """
            {
              "status": "${state.status}",
              "device_id_prefix": "${state.deviceId.take(8)}",
              "queue_entries": ${state.diagnostics.queueEntries},
              "last_batch_id": "${state.diagnostics.lastBatchId}",
              "clock_synced": ${state.clock.synced}
            }
            """.trimIndent(),
            Charsets.UTF_8
        )
        return file.absolutePath
    }

    fun refreshPermissionState() {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val notificationAllowed = if (Build.VERSION.SDK_INT >= 33) {
            val nm = appContext.getSystemService(NotificationManager::class.java)
            nm.areNotificationsEnabled()
        } else {
            true
        }
        val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = accessibilityManager.getEnabledAccessibilityServiceList(-1)
            .any { it.resolveInfo.serviceInfo.packageName == appContext.packageName }
        mutableUi.value = mutableUi.value.copy(
            batteryWhitelisted = if (Build.VERSION.SDK_INT >= 23) pm.isIgnoringBatteryOptimizations(appContext.packageName) else true,
            notificationAllowed = notificationAllowed,
            accessibilityEnabled = enabled,
            diagnostics = mutableUi.value.diagnostics.copy(
                accessibilityEnabled = enabled,
                queueEntries = uploader.queueEntries(),
                queueBytes = uploader.queueBytes(),
                uploadHistory = uploader.uploadHistory()
            )
        )
    }

    fun canStart(state: UiState = mutableUi.value): Boolean =
        state.settings.consentGranted &&
            isValidDeviceId(state.deviceId) &&
            state.accessibilityEnabled &&
            state.batteryWhitelisted &&
            state.notificationAllowed &&
            isScreenActive()

    private suspend fun serverHealthLoop() {
        while (true) {
            refreshPermissionState()
            val result = connectionTester.testHealth(settingsStore.settings.value.serverUrl)
            recordServerHealth(result)
            if (result.isSuccess) {
                checkRules(apply = true)
            }
            delay(if (result.isSuccess) 60_000 else 15_000)
        }
    }

    private fun recordServerHealth(result: Result<String>) {
        val now = System.currentTimeMillis()
        val message = result.getOrElse { it.message ?: it::class.java.simpleName }
        mutableUi.value = mutableUi.value.copy(
            serverReachable = result.isSuccess,
            lastServerHealthAtWallMillis = if (result.isSuccess) now else mutableUi.value.lastServerHealthAtWallMillis,
            diagnostics = mutableUi.value.diagnostics.copy(lastServerMessage = message)
        )
    }

    private suspend fun flushBatch(finalFlush: Boolean = false) {
        val state = mutableUi.value
        if (state.status != CollectionStatus.RUNNING && !finalFlush) return
        if (!isScreenActive()) {
            val pending = stopForScreenGate(CollectionStatus.PAUSED_BY_SCREEN_OFF)
            uploadBatch(pending.state, pending.samples, pending.touches, pending.events, pending.collectionContext)
            return
        }
        val samples = sensorCollector.drain()
        val touches = drainTouchEvents()
        val events = drainContextEvents()
        uploadBatch(state, samples, touches, events, currentCollectionContext())
    }

    private suspend fun uploadBatch(
        state: UiState,
        samples: List<SensorSample>,
        touches: List<TouchEventSnapshot>,
        events: List<ContextEventSnapshot>,
        collectionContext: ActiveCollectionContext
    ) {
        if (samples.isEmpty() && touches.isEmpty() && events.isEmpty()) return
        refreshPermissionState()
        if (!mutableUi.value.accessibilityEnabled) return
        val batch = buildBatch(state, samples, touches, events, collectionContext)
        val envelopeResult = JsonCodec.buildEnvelopeWithMetrics(
            batch,
            state.settings.ruleVersion,
            state.settings.ruleHash
        )
        recordTiming(serializeCompressTimes, envelopeResult.serializeCompressMillis)
        recordTiming(shaTimes, envelopeResult.shaMillis)
        if (state.settings.wifiOnly && !isWifiConnected()) {
            uploader.queueOnly(envelopeResult.envelope, "paused_by_no_network")
            val currentUi = mutableUi.value
            mutableUi.value = currentUi.copy(
                status = currentUi.status,
                diagnostics = currentUi.diagnostics.copy(
                    queueEntries = uploader.queueEntries(),
                    queueBytes = uploader.queueBytes(),
                    serializeCompressP50Ms = percentileMillis(serializeCompressTimes, 0.50),
                    serializeCompressP95Ms = percentileMillis(serializeCompressTimes, 0.95),
                    shaP50Ms = percentileMillis(shaTimes, 0.50),
                    shaP95Ms = percentileMillis(shaTimes, 0.95),
                    earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt(),
                    uploadHistory = uploader.uploadHistory()
                )
            )
            return
        }
        if (collectionContext == currentCollectionContext()) gatedResume = false
        val uploadStart = System.nanoTime()
        val result = uploader.uploadOrQueue(state.settings.serverUrl, envelopeResult.envelope)
        val uploadMillis = (System.nanoTime() - uploadStart) / 1_000_000L
        recordTiming(uploadTimes, uploadMillis)
        val diagnostics = mutableUi.value.diagnostics
        val nextDiagnostics = if (result.isSuccess) {
            diagnostics.copy(
                uploadSuccess = diagnostics.uploadSuccess + 1,
                lastBatchId = batch.batchId,
                lastServerMessage = result.getOrDefault("ok"),
                queueEntries = uploader.queueEntries(),
                queueBytes = uploader.queueBytes(),
                serializeCompressP50Ms = percentileMillis(serializeCompressTimes, 0.50),
                serializeCompressP95Ms = percentileMillis(serializeCompressTimes, 0.95),
                shaP50Ms = percentileMillis(shaTimes, 0.50),
                shaP95Ms = percentileMillis(shaTimes, 0.95),
                uploadP50Ms = percentileMillis(uploadTimes, 0.50),
                uploadP95Ms = percentileMillis(uploadTimes, 0.95),
                lastUploadAtWallMillis = System.currentTimeMillis(),
                uploadHistory = uploader.uploadHistory()
            )
        } else {
            diagnostics.copy(
                uploadFailure = diagnostics.uploadFailure + 1,
                lastBatchId = batch.batchId,
                lastError = result.exceptionOrNull()?.message ?: "upload_failed",
                queueEntries = uploader.queueEntries(),
                queueBytes = uploader.queueBytes(),
                serializeCompressP50Ms = percentileMillis(serializeCompressTimes, 0.50),
                serializeCompressP95Ms = percentileMillis(serializeCompressTimes, 0.95),
                shaP50Ms = percentileMillis(shaTimes, 0.50),
                shaP95Ms = percentileMillis(shaTimes, 0.95),
                uploadP50Ms = percentileMillis(uploadTimes, 0.50),
                uploadP95Ms = percentileMillis(uploadTimes, 0.95),
                droppedDueToQuota = uploader.droppedDueToQuota(),
                earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt(),
                uploadHistory = uploader.uploadHistory()
            )
        }
        mutableUi.value = mutableUi.value.copy(
            diagnostics = nextDiagnostics
        )
    }

    private fun currentCollectionContext(): ActiveCollectionContext = ActiveCollectionContext(
        collectionSessionId = collectionSessionId,
        taskCategory = taskCategory,
        taskSessionId = taskSessionId,
        taskStartedAt = taskStartedAt,
        gatedResume = gatedResume,
        baseElapsedNanos = sensorCollector.currentBaseElapsedNanos()
    )

    private fun recordTiming(window: ArrayDeque<Long>, value: Long) {
        window.addLast(value)
        while (window.size > 100) window.removeFirst()
    }

    private fun percentileMillis(window: ArrayDeque<Long>, percentile: Double): Long {
        if (window.isEmpty()) return 0L
        val sorted = window.sorted()
        val index = ((sorted.lastIndex) * percentile).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun refreshQueue() {
        mutableUi.value = mutableUi.value.copy(
            diagnostics = mutableUi.value.diagnostics.copy(
                queueEntries = uploader.queueEntries(),
                queueBytes = uploader.queueBytes(),
                droppedDueToQuota = uploader.droppedDueToQuota(),
                earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt(),
                uploadHistory = uploader.uploadHistory()
            )
        )
    }

    private suspend fun replayFailureQueueIfAllowed() {
        val state = mutableUi.value
        if (!state.accessibilityEnabled || !isScreenActive() || (state.settings.wifiOnly && !isWifiConnected())) return
        val replayed = uploader.replayDue(state.settings.serverUrl)
        if (replayed > 0) {
            val now = System.currentTimeMillis()
            mutableUi.value = mutableUi.value.copy(
                diagnostics = state.diagnostics.copy(
                    retrying = state.diagnostics.retrying + replayed,
                    queueEntries = uploader.queueEntries(),
                    queueBytes = uploader.queueBytes(),
                    lastQueueReplayAtWallMillis = now,
                    earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt(),
                    lastUploadAtWallMillis = now,
                    uploadHistory = uploader.uploadHistory()
                )
            )
        }
    }

    private fun registerScreenGate(scope: CoroutineScope) {
        if (screenReceiverRegistered) return
        screenReceiverRegistered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        appContext.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> pauseForScreen(CollectionStatus.PAUSED_BY_SCREEN_OFF, scope)
                        Intent.ACTION_SCREEN_ON -> {
                            val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                            if (keyguard.isKeyguardLocked) pauseForScreen(CollectionStatus.PAUSED_BY_LOCKED, scope)
                        }
                        Intent.ACTION_USER_PRESENT -> resumeAfterUnlock(scope)
                    }
                }
            },
            filter
        )
    }

    private suspend fun checkRules(apply: Boolean) {
        val result = ruleClient.fetch(settingsStore.settings.value.serverUrl)
        result.onSuccess {
            if (apply) {
                RedactionPolicyStore.update(it.policy)
                settingsStore.setRule(it.version, it.ruleHash)
            }
            mutableUi.value = mutableUi.value.copy(
                diagnostics = mutableUi.value.diagnostics.copy(lastRuleCheck = it.message)
            )
        }.onFailure {
            mutableUi.value = mutableUi.value.copy(
                diagnostics = mutableUi.value.diagnostics.copy(lastRuleCheck = it.message ?: it::class.java.simpleName)
            )
        }
    }

    private fun drainContextEvents(): List<ContextEventSnapshot> = synchronized(contextEvents) {
        val copy = contextEvents.toList()
        contextEvents.clear()
        copy
    }

    private fun drainTouchEvents(): List<TouchEventSnapshot> = synchronized(touchEvents) {
        val copy = touchEvents.toList()
        touchEvents.clear()
        copy
    }

    private fun stopForScreenGate(reason: CollectionStatus): PendingStoppedBatch {
        val state = mutableUi.value
        val pausedContext = currentCollectionContext()
        resumeAfterGate = state.status == CollectionStatus.RUNNING
        collectionJob?.cancel()
        val samples = sensorCollector.stop()
        val touches = drainTouchEvents()
        val events = drainContextEvents()
        val history = (listOf("${System.currentTimeMillis()}: $reason") + state.diagnostics.screenGateHistory).take(10)
        mutableUi.value = state.copy(
            status = reason,
            diagnostics = state.diagnostics.copy(screenGateHistory = history)
        )
        return PendingStoppedBatch(state, samples, touches, events, pausedContext)
    }

    private fun buildBatch(
        state: UiState,
        samples: List<SensorSample>,
        touches: List<TouchEventSnapshot>,
        events: List<ContextEventSnapshot>,
        collectionContext: ActiveCollectionContext
    ): Batch {
        val now = System.currentTimeMillis()
        val started = listOfNotNull(
            samples.minOfOrNull { it.wallTimeEstimatedMillis },
            touches.minOfOrNull { it.eventTimeWallMillis },
            events.minOfOrNull { it.eventTimeWallMillis }
        ).minOrNull() ?: now
        val ended = listOfNotNull(
            samples.maxOfOrNull { it.wallTimeEstimatedMillis },
            touches.maxOfOrNull { it.eventTimeWallMillis },
            events.maxOfOrNull { it.eventTimeWallMillis }
        ).maxOrNull() ?: now
        val source = if (collectionContext.taskCategory != null) CollectionSource.BUILTIN_TASK else CollectionSource.THIRD_PARTY_APP
        val foregroundEvent = events.lastOrNull { !it.appPackageName.isNullOrBlank() }
        val foregroundActivityEvent = events.lastOrNull { !it.foregroundActivityClassName.isNullOrBlank() }
        val foregroundComponentEvent = events.lastOrNull { !it.foregroundComponentName.isNullOrBlank() }
        val features = events.map {
            featureExtractor.extract(
                it,
                source,
                collectionContext.taskCategory,
                collectionContext.taskSessionId
            )
        }
        return Batch(
            batchId = UUID.randomUUID().toString(),
            deviceId = state.deviceId,
            sessionId = collectionContext.taskSessionId ?: collectionContext.collectionSessionId,
            collectionSource = source,
            appPackageName = foregroundEvent?.appPackageName,
            foregroundActivityClassName = foregroundActivityEvent?.foregroundActivityClassName,
            foregroundComponentName = foregroundComponentEvent?.foregroundComponentName,
            taskCategory = collectionContext.taskCategory,
            taskSessionId = collectionContext.taskSessionId,
            taskStartedAtWallMillis = collectionContext.taskStartedAt,
            taskElapsedSecondsAtBatchEnd = collectionContext.taskStartedAt?.let { ((ended - it) / 1000L).toInt().coerceAtLeast(0) },
            startedAtWallMillis = started,
            endedAtWallMillis = ended,
            baseElapsedNanos = collectionContext.baseElapsedNanos,
            sensorSamples = samples,
            touchEvents = touches,
            contextEvents = events,
            contextFeatures = features,
            skipEvents = emptyList(),
            gatedResume = collectionContext.gatedResume
        )
    }

    private fun isWifiConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isScreenActive(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return (if (Build.VERSION.SDK_INT >= 20) pm.isInteractive else true) && !keyguard.isKeyguardLocked
    }

    private fun isValidDeviceId(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
