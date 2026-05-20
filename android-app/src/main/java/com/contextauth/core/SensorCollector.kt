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

data class SensorRuntimeMetrics(
    val accelerometerHz: Double = 0.0,
    val gyroscopeHz: Double = 0.0,
    val magnetometerHz: Double = 0.0,
    val accelerometerAvailable: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val magnetometerAvailable: Boolean = false,
    val totalSamples: Int = 0
)

class SensorCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val buffer = ArrayList<SensorSample>(SamplingConfig.SAMPLING_RATE_HZ * 10)
    private val timestamps = mutableMapOf<String, ArrayDeque<Long>>()
    private val mutableMetrics = MutableStateFlow(
        SensorRuntimeMetrics(
            accelerometerAvailable = hasSensor(Sensor.TYPE_ACCELEROMETER),
            gyroscopeAvailable = hasSensor(Sensor.TYPE_GYROSCOPE),
            magnetometerAvailable = hasSensor(Sensor.TYPE_MAGNETIC_FIELD)
        )
    )
    val metrics: StateFlow<SensorRuntimeMetrics> = mutableMetrics.asStateFlow()

    @Volatile
    private var running = false
    private var baseElapsedNanos = SystemClock.elapsedRealtimeNanos()
    private var baseWallMillis = System.currentTimeMillis()

    fun start(serverOffsetMillis: Long) {
        if (running) return
        running = true
        baseElapsedNanos = SystemClock.elapsedRealtimeNanos()
        baseWallMillis = System.currentTimeMillis() + serverOffsetMillis
        register(Sensor.TYPE_ACCELEROMETER)
        register(Sensor.TYPE_GYROSCOPE)
        register(Sensor.TYPE_MAGNETIC_FIELD)
    }

    fun stop(): List<SensorSample> {
        if (running) {
            sensorManager.unregisterListener(this)
        }
        running = false
        return drain()
    }

    fun drain(): List<SensorSample> = synchronized(buffer) {
        val copy = buffer.toList()
        buffer.clear()
        copy
    }

    fun currentBaseElapsedNanos(): Long = baseElapsedNanos

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        val type = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "ACCELEROMETER"
            Sensor.TYPE_GYROSCOPE -> "GYROSCOPE"
            Sensor.TYPE_MAGNETIC_FIELD -> "MAGNETIC_FIELD"
            else -> return
        }
        val wall = baseWallMillis + (event.timestamp - baseElapsedNanos) / 1_000_000L
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

    private fun recordSample(type: String, wallMillis: Long) {
        val queue = timestamps.getOrPut(type) { ArrayDeque() }
        queue.addLast(wallMillis)
        val cutoff = wallMillis - 10_000
        while (queue.isNotEmpty() && queue.first() < cutoff) queue.removeFirst()
        val current = mutableMetrics.value
        mutableMetrics.value = current.copy(
            accelerometerHz = hz("ACCELEROMETER"),
            gyroscopeHz = hz("GYROSCOPE"),
            magnetometerHz = hz("MAGNETIC_FIELD"),
            totalSamples = current.totalSamples + 1
        )
    }

    private fun hz(type: String): Double = (timestamps[type]?.size ?: 0) / 10.0
}
