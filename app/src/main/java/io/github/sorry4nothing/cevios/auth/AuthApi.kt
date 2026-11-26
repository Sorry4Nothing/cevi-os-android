package io.github.sorry4nothing.cevios.auth

import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult

/** `integrity` ist `null`, wenn der Build keinen Nachweis kann (keine Projektnummer): Die App meldet sich dann ohne Ticket an. */
class AuthApi(private val api: ApiClient, private val integrity: IntegrityProvider? = null) {
    /** Die Sitzung und ihre Person, oder `null` ohne gültige Sitzung. Verlängert die Sitzung, wenn es fällig ist. */
    suspend fun session(): ApiResult<AuthSession?> = api.get("/api/auth/get-session")

    /** Die App meldet sich immer ohne zweiten Faktor an; die Sitzung kommt als Cookie, der Inhalt der Antwort zählt nicht. */
    suspend fun signIn(username: String, password: String): ApiResult<Unit> {
        val ticket = when (val proof = proofTicket(username)) {
            is ApiResult.Failure -> return proof
            is ApiResult.Ok -> proof.value
        }
        val request = api.request("/api/auth/sign-in/username")
            .apply { if (ticket != null) header(PROOF_HEADER, ticket) }
            .post(api.jsonBody(api.json.encodeToString(SignInRequest(username, password)))).build()
        return api.send(request) { }
    }

    /**
     * Das Ticket für genau diese Anmeldung, oder `null`, wenn der Server keinen Nachweis verlangt (404 an einer der beiden Routen)
     * oder der Build ihn nicht kann. Jeder Versuch holt eine neue Herausforderung: Ticket und Nonce gelten einmal und nur kurz.
     * Ein misslungener Nachweis bricht die Anmeldung ab; ein 404 ist die einzige Antwort, nach der sie ohne Ticket läuft.
     */
    private suspend fun proofTicket(username: String): ApiResult<String?> {
        val provider = integrity ?: return ApiResult.Ok(null)
        val challenge = when (val result = api.post<ProofChallengeRequest, ProofChallenge>(PROOF_CHALLENGE_PATH, ProofChallengeRequest(PROOF_PLATFORM))) {
            is ApiResult.Failure -> return if (result.status == 404) ApiResult.Ok(null) else result
            is ApiResult.Ok -> result.value
        }
        val token = provider.token(proofBinding(challenge.nonce, username)) ?: return ApiResult.Failure(ApiError.INTEGRITY_UNAVAILABLE)
        return when (val result = api.post<ProofRequest, ProofTicket>(PROOF_PATH, ProofRequest(PROOF_PLATFORM, challenge.nonce, username, token))) {
            is ApiResult.Failure -> if (result.status == 404) ApiResult.Ok(null) else result
            is ApiResult.Ok -> ApiResult.Ok(result.value.ticket)
        }
    }

    /** Antwortet auch ohne Sitzung mit 200. Das Cookie löscht der Server per `Set-Cookie`. */
    suspend fun signOut(): ApiResult<Unit> = api.postWithoutBody("/api/auth/sign-out")

    /** Beendet auch alle anderen Sitzungen der Person; das neue Cookie dieses Geräts nimmt der Zugang selbst. */
    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        api.postAction("/api/auth/change-password", ChangePasswordRequest(current, new))

    suspend fun acceptPrivacy(): ApiResult<Unit> = api.postWithoutBody("/api/v1/ich/datenschutz")

    /** Bleibt auch bei offenem Pflichtwechsel des Passworts erreichbar (alle anderen Routen antworten dann 403). */
    suspend fun profile(): ApiResult<Profile> = api.get("/api/v1/ich")

    /** Setzt das eigene Profilbild (JPEG, höchstens 5 MiB); ein vorhandenes wird ersetzt. */
    suspend fun uploadImage(jpeg: ByteArray): ApiResult<Unit> = api.putBytes("/api/v1/ich/bild", jpeg, "image/jpeg")

    /** Löscht das eigene Profilbild; ohne Bild ebenfalls 204. */
    suspend fun removeImage(): ApiResult<Unit> = api.delete("/api/v1/ich/bild")
}
