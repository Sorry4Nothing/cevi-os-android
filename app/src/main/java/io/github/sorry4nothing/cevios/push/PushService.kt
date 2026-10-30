package io.github.sorry4nothing.cevios.push

import com.google.firebase.messaging.FirebaseMessagingService
import io.github.sorry4nothing.cevios.net.ApiClient
import kotlinx.coroutines.runBlocking

/**
 * Firebase erneuert das Token: Wer angemeldet ist, hinterlegt das neue beim Server (ohne Sitzung antwortet er mit 401, dann
 * holt der nächste Login das Token). Eingehende Pushes zeigt das System selbst, solange die App nicht offen ist; ist sie
 * offen, sieht die Person den Chat und die Programme ohnehin.
 */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        runBlocking { PushApi(ApiClient.create(applicationContext)).registerToken(token) }
    }
}
