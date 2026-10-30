package io.github.sorry4nothing.cevios.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Legt die Sitzung mit AES-GCM verschlüsselt ab. Der Schlüssel liegt im Android Keystore und verlässt ihn nie.
 * Backup ist ausgeschaltet (`allowBackup=false`).
 */
class KeystoreSitzungSpeicher(context: Context) : SitzungSpeicher {
    private val prefs = context.applicationContext.getSharedPreferences("sitzung", Context.MODE_PRIVATE)

    override fun lesen(): String? {
        val roh = prefs.getString(FELD, null) ?: return null
        return try {
            val bytes = Base64.decode(roh, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_LAENGE))
            String(cipher.doFinal(bytes, IV_LAENGE, bytes.size - IV_LAENGE), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            prefs.edit().remove(FELD).apply() // Schlüssel weg oder Daten kaputt: Sitzung verwerfen, neu anmelden
            null
        } catch (e: IllegalArgumentException) {
            prefs.edit().remove(FELD).apply()
            null
        }
    }

    override fun schreiben(wert: String?) {
        if (wert == null) {
            prefs.edit().remove(FELD).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, schluessel())
            val bytes = cipher.iv + cipher.doFinal(wert.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(FELD, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        } catch (e: GeneralSecurityException) {
            throw IOException("Sitzung konnte nicht gespeichert werden", e)
        }
    }

    private fun schluessel(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cevios_sitzung"
        const val FELD = "cookies"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LAENGE = 12
        const val TAG_BITS = 128
    }
}
