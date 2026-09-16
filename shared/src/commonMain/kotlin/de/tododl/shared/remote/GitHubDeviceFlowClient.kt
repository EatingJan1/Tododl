package de.tododl.shared.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * GitHub-Login ohne eigenen Redirect-Server: der "Device Flow"
 * (https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow).
 * Ablauf:
 *  1. requestDeviceCode() - GitHub liefert einen kurzen Code + eine URL.
 *  2. Der Nutzer öffnet die URL im Browser (automatisch, siehe GitHubLoginDialog)
 *     und bestätigt dort mit dem angezeigten Code.
 *  3. pollForAccessToken() fragt im Hintergrund alle paar Sekunden nach, bis
 *     die Bestätigung erfolgt ist, und liefert dann das Access Token zurück.
 *
 * Voraussetzung: eine GitHub-OAuth-App mit aktiviertem "Device Flow" (siehe
 * GitHubOAuthConfig.CLIENT_ID - muss einmalig von Jan angelegt werden, dafür
 * ist kein Client Secret nötig, da der Device Flow für public clients gedacht ist).
 */
class GitHubDeviceFlowClient(private val httpClient: HttpClient) {

    class AuthorizationDeniedException : Exception("Anmeldung auf GitHub wurde abgelehnt.")
    class AuthorizationExpiredException : Exception("Der Code ist abgelaufen. Bitte erneut versuchen.")

    suspend fun requestDeviceCode(clientId: String, scope: String = "repo"): GitHubDeviceCodeDto {
        return httpClient.submitForm(
            url = "https://github.com/login/device/code",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("scope", scope)
            }
        ) {
            header("Accept", "application/json")
        }.body()
    }

    /**
     * Fragt im Intervall von [device].interval Sekunden nach, ob der Nutzer
     * die Anmeldung im Browser bestätigt hat. Blockiert (suspend), bis
     * entweder ein Token da ist oder ein Fehler auftritt - am besten aus
     * einer eigenen Coroutine aufrufen (siehe GitHubLoginDialog).
     */
    suspend fun pollForAccessToken(clientId: String, device: GitHubDeviceCodeDto): String {
        var intervalSeconds = device.interval
        val deadline = Clock.System.now().toEpochMilliseconds() + device.expires_in * 1000L

        while (Clock.System.now().toEpochMilliseconds() < deadline) {
            delay(intervalSeconds * 1000L)

            val response: GitHubAccessTokenDto = httpClient.submitForm(
                url = "https://github.com/login/oauth/access_token",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("device_code", device.device_code)
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }
            ) {
                header("Accept", "application/json")
            }.body()

            when {
                response.access_token != null -> return response.access_token
                response.error == "authorization_pending" -> continue
                response.error == "slow_down" -> intervalSeconds += 5
                response.error == "expired_token" -> throw AuthorizationExpiredException()
                response.error == "access_denied" -> throw AuthorizationDeniedException()
                response.error != null -> throw Exception(response.error_description ?: response.error)
            }
        }
        throw AuthorizationExpiredException()
    }
}

/**
 * Client-ID der GitHub-OAuth-App. TODO: einmalig auf
 * https://github.com/settings/developers -> "New OAuth App" anlegen,
 * unter "Enable Device Flow" aktivieren, KEIN Client Secret nötig.
 * Callback-URL kann auf "http://localhost" stehen bleiben - wird beim
 * Device Flow nicht verwendet.
 */
object GitHubOAuthConfig {
    const val CLIENT_ID = "Ov23liFNKtbpsXyOhUXe"
}
