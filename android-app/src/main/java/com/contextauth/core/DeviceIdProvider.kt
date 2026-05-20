package com.contextauth.core

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DeviceIdProvider(private val context: Context) {
    private val prefs by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "contextauthlab_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            Log.w("DeviceIdProvider", "Encrypted preferences unavailable; using private preferences")
            context.getSharedPreferences("contextauthlab_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getOrCreateDeviceId(serverStudySalt: String): String {
        val saltKey = prefs.getString("device_id_salt", null)
        val existing = prefs.getString("device_id", null)
        if (saltKey == serverStudySalt && existing?.matches(Regex("^[a-f0-9]{64}$")) == true) {
            return existing
        }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "missing_android_id"
        val generated = computeDeviceId(serverStudySalt, androidId)
        prefs.edit()
            .putString("device_id_salt", serverStudySalt)
            .putString("device_id", generated)
            .apply()
        return generated
    }

    companion object {
        fun computeDeviceId(serverStudySalt: String, androidId: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(serverStudySalt.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(androidId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
