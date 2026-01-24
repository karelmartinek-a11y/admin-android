package cz.hcasc.adminhcasc.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local gate (single password) for AdminHCASC.
 *
 * NOTE: This is NOT server authentication. Server-side admin login stays unchanged
 * and continues to be enforced by the backends.
 *
 * We store only a one-way hash (SHA-256) of the password so it cannot be recovered.
 */
class AdminGateStore(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isUnlocked(): Boolean = prefs.getBoolean(KEY_UNLOCKED, false)

    fun lock() {
        prefs.edit().putBoolean(KEY_UNLOCKED, false).apply()
    }

    fun unlock(password: String): Boolean {
        // First run: if no hash exists, set it.
        val existingHash = prefs.getString(KEY_PASS_HASH, null)
        val newHash = sha256(password)
        if (existingHash == null) {
            prefs.edit()
                .putString(KEY_PASS_HASH, newHash)
                .putBoolean(KEY_UNLOCKED, true)
                .apply()
            return true
        }

        val ok = existingHash == newHash
        if (ok) {
            prefs.edit().putBoolean(KEY_UNLOCKED, true).apply()
        }
        return ok
    }

    private fun sha256(s: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "adminhcasc_gate"
        private const val KEY_PASS_HASH = "pass_hash"
        private const val KEY_UNLOCKED = "unlocked"
    }
}
