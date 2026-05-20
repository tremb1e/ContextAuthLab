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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

class CollectionCoordinator(private val context: Context) {
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
    private var collectionJob: Job? = null
    private var taskSessionId: String? = null
    private var taskCategory: TaskCategory? = null
    private var taskStartedAt: Long? = null
    private var gatedResume = false
    private var resumeAfterGate = false
    private var screenReceiverRegistered = false

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
                    accelerometerAvailable = metrics.accelerometerAvailable,
                    gyroscopeAvailable = metrics.gyroscopeAvailable,
                    magnetometerAvailable = metrics.magnetometerAvailable
                )
            }
        }
        scope.launch {
            clockSync.state.collectLatest { clock ->
                mutableUi.value = mutableUi.value.copy(clock = clock)
            }
        }
        scope.launch {
            AccessibilityEventBus.events.collectLatest { event ->
                synchronized(contextEvents) { contextEvents.add(event) }
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
    fun setAllowThirdParty(value: Boolean) = settingsStore.setAllowThirdParty(value)
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
        collectionJob?.cancel()
        scope.launch { flushBatch(finalFlush = true) }
        sensorCollector.stop()
        taskCategory = null
        taskSessionId = null
        taskStartedAt = null
        mutableUi.value = mutableUi.value.copy(status = CollectionStatus.IDLE)
    }

    fun pauseForScreen(reason: CollectionStatus) {
        resumeAfterGate = mutableUi.value.status == CollectionStatus.RUNNING
        collectionJob?.cancel()
        sensorCollector.stop()
        synchronized(contextEvents) { contextEvents.clear() }
        val history = (listOf("${System.currentTimeMillis()}: $reason") + mutableUi.value.diagnostics.screenGateHistory).take(10)
        mutableUi.value = mutableUi.value.copy(
            status = reason,
            diagnostics = mutableUi.value.diagnostics.copy(screenGateHistory = history)
        )
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
                queueBytes = uploader.queueBytes()
            )
        )
    }

    fun canStart(state: UiState = mutableUi.value): Boolean =
        state.settings.consentGranted &&
            state.accessibilityEnabled &&
            state.batteryWhitelisted &&
            state.notificationAllowed &&
            state.serverReachable &&
            state.clock.synced &&
            isScreenActive() &&
            (!state.settings.wifiOnly || isWifiConnected())

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
            diagnostics = mutableUi.value.diagnostics.copy(lastServerMessage = message),
            recentUploadStatus = message
        )
    }

    private suspend fun flushBatch(finalFlush: Boolean = false) {
        val state = mutableUi.value
        if (state.status != CollectionStatus.RUNNING && !finalFlush) return
        if (!isScreenActive()) {
            pauseForScreen(CollectionStatus.PAUSED_BY_SCREEN_OFF)
            return
        }
        val samples = sensorCollector.drain()
        val events = drainContextEvents()
        if (samples.isEmpty() && events.isEmpty()) return
        if (state.settings.wifiOnly && !isWifiConnected()) {
            val envelope = JsonCodec.buildEnvelope(
                buildBatch(state, samples, events),
                state.settings.ruleVersion,
                state.settings.ruleHash
            )
            uploader.queueOnly(envelope, "paused_by_no_network")
            mutableUi.value = state.copy(
                status = CollectionStatus.PAUSED_BY_NO_NETWORK,
                diagnostics = state.diagnostics.copy(
                    queueEntries = uploader.queueEntries(),
                    queueBytes = uploader.queueBytes(),
                    earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt()
                )
            )
            return
        }
        val batch = buildBatch(state, samples, events)
        gatedResume = false
        val serializeStart = System.nanoTime()
        val envelope = JsonCodec.buildEnvelope(batch, state.settings.ruleVersion, state.settings.ruleHash)
        val serializeMs = (System.nanoTime() - serializeStart) / 1_000_000L
        val result = uploader.uploadOrQueue(state.settings.serverUrl, envelope)
        val diagnostics = mutableUi.value.diagnostics
        val nextDiagnostics = if (result.isSuccess) {
            diagnostics.copy(
                uploadSuccess = diagnostics.uploadSuccess + 1,
                lastBatchId = batch.batchId,
                lastServerMessage = result.getOrDefault("ok"),
                queueEntries = uploader.queueEntries(),
                queueBytes = uploader.queueBytes(),
                serializeCompressP50Ms = serializeMs,
                serializeCompressP95Ms = maxOf(diagnostics.serializeCompressP95Ms, serializeMs),
                shaP50Ms = serializeMs.coerceAtMost(1),
                shaP95Ms = maxOf(diagnostics.shaP95Ms, serializeMs.coerceAtMost(1))
            )
        } else {
            diagnostics.copy(
                uploadFailure = diagnostics.uploadFailure + 1,
                lastBatchId = batch.batchId,
                lastError = result.exceptionOrNull()?.message ?: "upload_failed",
                queueEntries = uploader.queueEntries(),
                queueBytes = uploader.queueBytes(),
                droppedDueToQuota = uploader.droppedDueToQuota(),
                earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt()
            )
        }
        mutableUi.value = mutableUi.value.copy(
            diagnostics = nextDiagnostics,
            recentUploadStatus = nextDiagnostics.lastServerMessage
        )
    }

    private fun refreshQueue() {
        mutableUi.value = mutableUi.value.copy(
            diagnostics = mutableUi.value.diagnostics.copy(
                queueEntries = uploader.queueEntries(),
                queueBytes = uploader.queueBytes(),
                droppedDueToQuota = uploader.droppedDueToQuota(),
                earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt()
            )
        )
    }

    private suspend fun replayFailureQueueIfAllowed() {
        val state = mutableUi.value
        if (!isScreenActive() || (state.settings.wifiOnly && !isWifiConnected())) return
        val replayed = uploader.replayDue(state.settings.serverUrl)
        if (replayed > 0) {
            mutableUi.value = mutableUi.value.copy(
                diagnostics = state.diagnostics.copy(
                    retrying = state.diagnostics.retrying + replayed,
                    queueEntries = uploader.queueEntries(),
                    queueBytes = uploader.queueBytes(),
                    lastQueueReplayAtWallMillis = System.currentTimeMillis(),
                    earliestQueueEntryAtWallMillis = uploader.earliestQueueEntryAt()
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
                        Intent.ACTION_SCREEN_OFF -> pauseForScreen(CollectionStatus.PAUSED_BY_SCREEN_OFF)
                        Intent.ACTION_SCREEN_ON -> {
                            val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                            if (keyguard.isKeyguardLocked) pauseForScreen(CollectionStatus.PAUSED_BY_LOCKED)
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

    private fun buildBatch(
        state: UiState,
        samples: List<SensorSample>,
        events: List<ContextEventSnapshot>
    ): Batch {
        val started = samples.minOfOrNull { it.wallTimeEstimatedMillis } ?: System.currentTimeMillis()
        val ended = samples.maxOfOrNull { it.wallTimeEstimatedMillis } ?: System.currentTimeMillis()
        val source = if (taskCategory != null) CollectionSource.BUILTIN_TASK else CollectionSource.THIRD_PARTY_APP
        val features = events.map { featureExtractor.extract(it, source, taskCategory, taskSessionId) }
        return Batch(
            batchId = UUID.randomUUID().toString(),
            deviceId = state.deviceId,
            collectionSource = source,
            taskCategory = taskCategory,
            taskSessionId = taskSessionId,
            taskStartedAtWallMillis = taskStartedAt,
            taskElapsedSecondsAtBatchEnd = taskStartedAt?.let { ((ended - it) / 1000L).toInt().coerceAtLeast(0) },
            startedAtWallMillis = started,
            endedAtWallMillis = ended,
            baseElapsedNanos = sensorCollector.currentBaseElapsedNanos(),
            sensorSamples = samples,
            contextEvents = events,
            contextFeatures = features,
            skipEvents = emptyList(),
            gatedResume = gatedResume
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
}
