package com.contextauth.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class SensorRuntimeMetrics(
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
    val totalSamples: Int = 0
)

class SensorCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val buffer = ArrayList<SensorSample>(SamplingConfig.SAMPLING_RATE_HZ * 10)
    private val timestamps = mutableMapOf<String, ArrayDeque<Long>>()
    private val collectionHzBySensorType = mapOf(
        "ACCELEROMETER" to collectionHz(Sensor.TYPE_ACCELEROMETER),
        "GYROSCOPE" to collectionHz(Sensor.TYPE_GYROSCOPE),
        "MAGNETIC_FIELD" to collectionHz(Sensor.TYPE_MAGNETIC_FIELD)
    )
    private val mutableMetrics = MutableStateFlow(
        SensorRuntimeMetrics(
            accelerometerCollectionHz = collectionHzBySensorType.getValue("ACCELEROMETER"),
            gyroscopeCollectionHz = collectionHzBySensorType.getValue("GYROSCOPE"),
            magnetometerCollectionHz = collectionHzBySensorType.getValue("MAGNETIC_FIELD"),
            accelerometerAvailable = hasSensor(Sensor.TYPE_ACCELEROMETER),
            gyroscopeAvailable = hasSensor(Sensor.TYPE_GYROSCOPE),
            magnetometerAvailable = hasSensor(Sensor.TYPE_MAGNETIC_FIELD)
        )
    )
    val metrics: StateFlow<SensorRuntimeMetrics> = mutableMetrics.asStateFlow()

    @Volatile
    private var running = false
    private var baseElapsedNanos = SystemClock.elapsedRealtimeNanos()
    @Volatile
    private var serverOffsetMillis = 0L

    fun start(serverOffsetMillis: Long) {
        if (running) return
        this.serverOffsetMillis = serverOffsetMillis
        running = true
        synchronized(timestamps) { timestamps.clear() }
        resetMeasuredMetrics()
        baseElapsedNanos = SystemClock.elapsedRealtimeNanos()
        register(Sensor.TYPE_ACCELEROMETER)
        register(Sensor.TYPE_GYROSCOPE)
        register(Sensor.TYPE_MAGNETIC_FIELD)
    }

    fun stop(): List<SensorSample> {
        if (running) {
            sensorManager.unregisterListener(this)
        }
        running = false
        synchronized(timestamps) { timestamps.clear() }
        resetMeasuredMetrics()
        return drain()
    }

    fun drain(): List<SensorSample> = synchronized(buffer) {
        val copy = buffer.toList()
        buffer.clear()
        copy
    }

    fun currentBaseElapsedNanos(): Long = baseElapsedNanos

    fun updateServerOffset(serverOffsetMillis: Long) {
        this.serverOffsetMillis = serverOffsetMillis
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        val type = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "ACCELEROMETER"
            Sensor.TYPE_GYROSCOPE -> "GYROSCOPE"
            Sensor.TYPE_MAGNETIC_FIELD -> "MAGNETIC_FIELD"
            else -> return
        }
        val eventAgeMillis = ((SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L).coerceAtLeast(0L)
        val wall = System.currentTimeMillis() - eventAgeMillis + serverOffsetMillis
        val sample = SensorSample(
            sensorType = type,
            timestampElapsedNanos = event.timestamp,
            wallTimeEstimatedMillis = wall,
            x = event.values.getOrElse(0) { 0f },
            y = event.values.getOrElse(1) { 0f },
            z = event.values.getOrElse(2) { 0f },
            accuracy = event.accuracy
        )
        synchronized(buffer) { buffer.add(sample) }
        recordSample(type, wall)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun register(type: Int) {
        sensorManager.getDefaultSensor(type)?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SamplingConfig.SAMPLING_PERIOD_US,
                SamplingConfig.MAX_REPORT_LATENCY_US
            )
        }
    }

    private fun hasSensor(type: Int): Boolean = sensorManager.getDefaultSensor(type) != null

    private fun collectionHz(type: Int): Double {
        val sensor = sensorManager.getDefaultSensor(type) ?: return 0.0
        val sensorMaxHz = if (sensor.minDelay > 0) {
            1_000_000.0 / sensor.minDelay.toDouble()
        } else {
            SamplingConfig.SAMPLING_RATE_HZ.toDouble()
        }
        return minOf(SamplingConfig.SAMPLING_RATE_HZ.toDouble(), sensorMaxHz).coerceAtLeast(0.0)
    }

    private fun recordSample(type: String, wallMillis: Long) {
        synchronized(timestamps) {
            val queue = timestamps.getOrPut(type) { ArrayDeque() }
            queue.addLast(wallMillis)
            val cutoff = wallMillis - 10_000
            while (queue.isNotEmpty() && queue.first() < cutoff) queue.removeFirst()
        }
        val current = mutableMetrics.value
        mutableMetrics.value = current.copy(
            accelerometerHz = hz("ACCELEROMETER"),
            gyroscopeHz = hz("GYROSCOPE"),
            magnetometerHz = hz("MAGNETIC_FIELD"),
            accelerometerLostSamples = lostSamples("ACCELEROMETER"),
            gyroscopeLostSamples = lostSamples("GYROSCOPE"),
            magnetometerLostSamples = lostSamples("MAGNETIC_FIELD"),
            totalSamples = current.totalSamples + 1
        )
    }

    private fun hz(type: String): Double = synchronized(timestamps) {
        (timestamps[type]?.size ?: 0) / 10.0
    }

    private fun lostSamples(type: String): Int = synchronized(timestamps) {
        val queue = timestamps[type] ?: return@synchronized 0
        if (queue.size < 2) return@synchronized 0
        val expectedHz = collectionHzBySensorType[type] ?: 0.0
        if (expectedHz <= 0.0) return@synchronized 0
        val durationMillis = (queue.last() - queue.first()).coerceAtLeast(0L)
        if (durationMillis <= 0L) return@synchronized 0
        val expectedSamples = ((durationMillis / 1000.0) * expectedHz).roundToInt() + 1
        (expectedSamples - queue.size).coerceAtLeast(0)
    }

    private fun resetMeasuredMetrics() {
        val current = mutableMetrics.value
        mutableMetrics.value = current.copy(
            accelerometerHz = 0.0,
            gyroscopeHz = 0.0,
            magnetometerHz = 0.0,
            accelerometerLostSamples = 0,
            gyroscopeLostSamples = 0,
            magnetometerLostSamples = 0
        )
    }
}
