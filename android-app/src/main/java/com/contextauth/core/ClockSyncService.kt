package com.contextauth.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ClockSyncService(
    private val client: OkHttpClient,
    private val settingsStore: SettingsStore
) {
    private val httpClient = client.newBuilder()
        .callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
    private val mutableState = MutableStateFlow(ClockSyncState())
    val state: StateFlow<ClockSyncState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            while (true) {
                syncOnce()
                delay(60_000)
            }
        }
    }

    fun trigger(scope: CoroutineScope) {
        scope.launch { syncOnce() }
    }

    suspend fun syncOnce() = withContext(Dispatchers.IO) {
        val previous = mutableState.value
        val configResult = runCatching { fetchServerConfig() }
        val ntpHosts = configResult.getOrNull()?.ntpServers?.ifEmpty { CHINA_NTP_HOSTS } ?: CHINA_NTP_HOSTS
        val ntpResult = runCatching { syncFromNtp(previous, ntpHosts) }
        if (ntpResult.isSuccess) return@withContext

        val serverTimeResult = runCatching {
            val config = configResult.getOrElse { fetchServerConfig() }
            val t0 = System.currentTimeMillis()
            publishSync(previous, t0, t0, config.serverTimeMillis, "server_config_fallback")
        }
        serverTimeResult.onFailure {
            val configError = configResult.exceptionOrNull()?.message ?: configResult.exceptionOrNull()?.javaClass?.simpleName
            val ntpError = ntpResult.exceptionOrNull()?.message ?: ntpResult.exceptionOrNull()?.javaClass?.simpleName
            val fallbackError = it.message ?: it::class.java.simpleName
            mutableState.value = previous.copy(lastError = "config=$configError; ntp=$ntpError; fallback=$fallbackError")
        }
    }

    private fun fetchServerConfig(): ServerConfig {
        val url = settingsStore.settings.value.serverUrl.trimEnd('/') + "/api/v1/config"
        val resp = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
        resp.use {
            if (!it.isSuccessful) error("HTTP ${it.code}")
            val body = it.body?.string() ?: error("empty config")
            val json = JSONObject(body)
            val serverTime = json.getLong("serverTimeMillis")
            settingsStore.setServerStudySalt(json.optString("serverStudySalt", SERVER_STUDY_SALT))
            settingsStore.setRule(json.optString("rulesVersion", "1"), settingsStore.settings.value.ruleHash)
            val ntpServers = buildList {
                val timeSync = json.optJSONObject("timeSync")
                val array = timeSync?.optJSONArray("recommendedNtpServers") ?: json.optJSONArray("ntpServers")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val host = array.optString(index).trim()
                        if (host.isNotBlank()) add(host)
                    }
                }
            }
            return ServerConfig(serverTimeMillis = serverTime, ntpServers = ntpServers)
        }
    }

    private fun syncFromNtp(previous: ClockSyncState, hosts: List<String>) {
        val errors = mutableListOf<String>()
        hosts.forEach { host ->
            runCatching {
                val t0 = System.currentTimeMillis()
                val serverTime = NtpClient.requestTimeMillis(host, timeoutMillis = 3_000)
                val t1 = System.currentTimeMillis()
                publishSync(previous, t0, t1, serverTime, "ntp")
                return
            }.onFailure {
                errors += it.message ?: it::class.java.simpleName
            }
        }
        error("ntp_failed ${errors.size}/${hosts.size}")
    }

    private fun publishSync(previous: ClockSyncState, t0: Long, t1: Long, serverTime: Long, source: String) {
        val rtt = (t1 - t0).coerceAtLeast(0)
        val offset = ClockSyncMath.offsetMillis(t0, t1, serverTime)
        val drift = ClockSyncMath.driftPpm(previous.serverOffsetMillis, offset, previous.synced)
        mutableState.value = ClockSyncState(
            synced = true,
            lastSyncedAtWallMillis = t1,
            lastRttMillis = rtt,
            serverOffsetMillis = offset,
            estimatedDriftPpm = drift,
            source = source,
            lastError = null
        )
    }

    companion object {
        val CHINA_NTP_HOSTS = listOf(
            "ntp.ntsc.ac.cn",
            "ntp.cloud.aliyuncs.com",
            "ntp.aliyun.com",
            "ntp.tencent.com",
            "0.cn.pool.ntp.org",
            "1.cn.pool.ntp.org",
            "2.cn.pool.ntp.org",
            "3.cn.pool.ntp.org"
        )
    }
}

data class ServerConfig(
    val serverTimeMillis: Long,
    val ntpServers: List<String>
)

object ClockSyncMath {
    fun offsetMillis(t0Millis: Long, t1Millis: Long, serverTimeMillis: Long): Long {
        val rtt = t1Millis - t0Millis
        return serverTimeMillis - (t0Millis + rtt / 2)
    }

    fun driftPpm(oldOffsetMillis: Long, newOffsetMillis: Long, previousSynced: Boolean, syncIntervalMillis: Long = 60_000): Double {
        if (!previousSynced) return 0.0
        return ((newOffsetMillis - oldOffsetMillis).toDouble() / syncIntervalMillis.toDouble()) * 1_000_000.0
    }
}

object NtpClient {
    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 4
    private const val TRANSMIT_TIME_OFFSET = 40
    private const val OFFSET_1900_TO_1970_SECONDS = 2_208_988_800L

    fun requestTimeMillis(host: String, timeoutMillis: Int): Long {
        val address = InetAddress.getByName(host)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMillis
            val buffer = ByteArray(NTP_PACKET_SIZE)
            buffer[0] = ((NTP_VERSION shl 3) or NTP_MODE_CLIENT).toByte()
            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
            socket.send(request)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            return readTimestampMillis(buffer, TRANSMIT_TIME_OFFSET)
        }
    }

    fun readTimestampMillis(buffer: ByteArray, offset: Int): Long {
        require(buffer.size >= offset + 8) { "short_ntp_packet" }
        val seconds = readUnsignedInt(buffer, offset)
        val fraction = readUnsignedInt(buffer, offset + 4)
        if (seconds == 0L) error("empty_ntp_timestamp")
        return (seconds - OFFSET_1900_TO_1970_SECONDS) * 1000L + ((fraction * 1000L) ushr 32)
    }

    private fun readUnsignedInt(buffer: ByteArray, offset: Int): Long =
        ((buffer[offset].toLong() and 0xffL) shl 24) or
            ((buffer[offset + 1].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 8) or
            (buffer[offset + 3].toLong() and 0xffL)
}
