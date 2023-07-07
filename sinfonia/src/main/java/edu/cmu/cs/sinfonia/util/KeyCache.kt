package edu.cmu.cs.sinfonia.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.util.UUID

class KeyCache(context: Context) {
    private val sharedPreferences: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
                "sinfonia",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun saveKeys(uuid: UUID, keyPair: KeyPair): Boolean {
        val data = serialize(keyPair)
        try {
            sharedPreferences.edit()
                    .putString(uuid.toString(), data)
                    .apply()
        } catch (_: Throwable) {
            return false
        }
        return true
    }
    fun getKeys(uuid: UUID): KeyPair {
        val data = sharedPreferences.getString(uuid.toString(), null)
        if (data == null) {
            Log.d(TAG, "Cache miss")
            val keyPair = KeyPair()
            return if (saveKeys(uuid, keyPair)) keyPair else KeyPair()
        }

        val keyStore = deserialize(data)
        if (keyStore.created + LIFETIME < System.currentTimeMillis()) {
            Log.d(TAG, "Cache stale")
            val keyPair = KeyPair()
            return if (saveKeys(uuid, keyPair)) keyPair else KeyPair()
        }

        Log.d(TAG, "Cache hit")
        return KeyPair(keyStore.privateKey)
    }

    fun clearKeys(uuid: UUID): Boolean {
        try {
            sharedPreferences.edit()
                    .remove(uuid.toString())
                    .apply()
        } catch (_: Throwable) {
            return false
        }
        return true
    }

    private fun serialize(keyPair: KeyPair): String {
        val data = mapOf(
                PRIVATE_KEY to keyPair.privateKey.toBase64(),
                CREATED to System.currentTimeMillis()
        )
        return JSONObject(data).toString()
    }

    private fun deserialize(data: String): KeyStore {
        return KeyStore(data)
    }

    private class KeyStore(data: String) {
        val privateKey: Key
        val created: Long

        init {
            val jsonObject = JSONObject(data)
            val privateKeyString = jsonObject.getString(PRIVATE_KEY)
            this.privateKey = Key.fromBase64(privateKeyString)
            this.created = jsonObject.getLong(CREATED)
        }
    }

    companion object {
        private const val TAG = "Sinfonia/KeyCache"
        private const val PRIVATE_KEY = "privateKey"
        private const val CREATED = "created"
        private const val LIFETIME = 1 * 60 * 60 * 1000    // TODO("Set a reasonable key lifetime")
    }
}