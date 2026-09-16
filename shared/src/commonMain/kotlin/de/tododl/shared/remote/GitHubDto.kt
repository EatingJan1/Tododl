package de.tododl.shared.remote

import kotlinx.serialization.Serializable

/** Minimaler Ausschnitt der GitHub-Issues-API-Antwort - nur was für den Todo-Sync gebraucht wird. */
@Serializable
data class GitHubIssueDto(
    val number: Int,
    val title: String,
    val html_url: String,
    val state: String,
    /** GitHub liefert Pull-Requests über denselben Issues-Endpoint mit - dieses Feld ist dann gesetzt. */
    val pull_request: PullRequestMarker? = null
) {
    @Serializable
    data class PullRequestMarker(val url: String? = null)

    val isPullRequest: Boolean get() = pull_request != null
}

/** Antwort von POST https://github.com/login/device/code */
@Serializable
data class GitHubDeviceCodeDto(
    val device_code: String,
    val user_code: String,
    val verification_uri: String,
    val expires_in: Int,
    val interval: Int
)

/** Antwort von POST https://github.com/login/oauth/access_token (Device-Flow-Polling). */
@Serializable
data class GitHubAccessTokenDto(
    val access_token: String? = null,
    val token_type: String? = null,
    val scope: String? = null,
    /** Solange der Nutzer noch nicht bestätigt hat: "authorization_pending". Siehe GitHubDeviceFlowClient. */
    val error: String? = null,
    val error_description: String? = null
)
