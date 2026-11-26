package io.github.sorry4nothing.cevios.auth

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import io.github.sorry4nothing.cevios.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume

/*
 * Nachweis der App bei der Anmeldung (Backend-Issue #785, `docs/app-nachweis.md`). Nichts davon darf je geloggt, gespeichert oder
 * in einen Absturzbericht geraten: Token, Nonce, Ticket und Benutzername. Darum sind die Modelle keine `data class` (kein `toString`
 * mit Inhalt), und es gibt hier keinen Log-Aufruf.
 */

/** Beleg des Betriebssystems, gebunden an die Bindung. `null`: Das Gerät liefert keinen (kein Google Play, kein Netz, abgelehnt). */
fun interface IntegrityProvider {
    suspend fun token(binding: String): String?
}

/** `SHA-256(nonce + "\n" + benutzername in Kleinbuchstaben)` als base64url ohne Padding (43 Zeichen), wie es Play Integrity als `nonce` verlangt. */
fun proofBinding(nonce: String, username: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest("$nonce\n${username.lowercase()}".toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}

/** Play Integrity, klassische Anfrage. Die Projektnummer kommt nur lokal in den Build (`ceviPlayCloudProject`), nie ins Repo. */
class PlayIntegrity(context: Context, private val cloudProject: Long) : IntegrityProvider {
    private val appContext = context.applicationContext

    override suspend fun token(binding: String): String? = try {
        val request = IntegrityTokenRequest.builder().setNonce(binding).setCloudProjectNumber(cloudProject).build()
        suspendCancellableCoroutine { continuation ->
            IntegrityManagerFactory.create(appContext).requestIntegrityToken(request)
                .addOnSuccessListener { continuation.resume(it.token()) }
                .addOnFailureListener { continuation.resume(null) } // Grund und Meldung bleiben ungelesen
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** `null` ohne Projektnummer im Build: Dann bleibt der Nachweis aus, die App meldet sich ohne Ticket an. */
        fun forBuild(context: Context): IntegrityProvider? =
            BuildConfig.PLAY_CLOUD_PROJECT.takeIf { it > 0 }?.let { PlayIntegrity(context, it) }
    }
}

@Serializable
internal class ProofChallengeRequest(val plattform: String)

@Serializable
internal class ProofChallenge(val nonce: String)

@Serializable
internal class ProofRequest(val plattform: String, val nonce: String, val benutzername: String, val integrityToken: String)

@Serializable
internal class ProofTicket(val ticket: String)

internal const val PROOF_PLATFORM = "android"
internal const val PROOF_HEADER = "X-Cevi-Nachweis"
internal const val PROOF_CHALLENGE_PATH = "/api/v1/app/nachweis/challenge"
internal const val PROOF_PATH = "/api/v1/app/nachweis"
