package io.github.sorry4nothing.cevios.net

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Verschlüsselt Bytes für die Ablage auf dem Gerät. `context` bindet die Daten an ihren Ort: Eine vertauschte Datei lässt sich nicht öffnen. */
interface Sealer {
    fun seal(plain: ByteArray, context: String): ByteArray

    /** `null`, wenn die Daten kaputt sind, zu einem anderen Ort gehören oder der Schlüssel fehlt. */
    fun open(sealed: ByteArray, context: String): ByteArray?
}

/** AES-GCM mit zufälligem IV vor den Daten; den Schlüssel liefert der Aufrufer (auf dem Gerät der Keystore, im Test ein Schlüssel im Speicher). */
class AesGcmSealer(private val key: () -> SecretKey) : Sealer {
    override fun seal(plain: ByteArray, context: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray, context: String): ByteArray? {
        if (sealed.size <= IV_LENGTH) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_LENGTH))
            cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
            cipher.doFinal(sealed, IV_LENGTH, sealed.size - IV_LENGTH)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}

/** Der AES-256-Schlüssel mit diesem Namen im Android Keystore; er wird beim ersten Mal angelegt und verlässt den Keystore nie. */
fun keystoreAesKey(alias: String): SecretKey {
    val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
}

private const val KEYSTORE = "AndroidKeyStore"
