package de.tododl.shared.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

/**
 * Dünner Wrapper um die öffentliche GitHub-REST-API - aktuell nur, um offene
 * Issues eines Repos abzurufen (Basis für den GitHub-Connector, siehe
 * desktopApp/.../connectors/GitHubConnectorProvider.kt und GitHubSyncService.kt).
 *
 * Nutzt denselben HttpClient wie TododlApiClient (siehe SharedModule.kt) -
 * die GitHub-Anfragen laufen unabhängig vom eigenen Projektserver, da hier
 * immer die volle GitHub-URL übergeben wird.
 */
class GitHubApiClient(private val httpClient: HttpClient) {

    /**
     * Liefert alle offenen Issues eines Repos (ohne Pull-Requests, die GitHub
     * über denselben Endpoint mit ausliefert). [token] ist ein Personal
     * Access Token (Fine-grained oder Classic) mit "Issues: Read"-Berechtigung.
     */
    suspend fun listOpenIssues(owner: String, repo: String, token: String): List<GitHubIssueDto> {
        val issues: List<GitHubIssueDto> = httpClient.get("https://api.github.com/repos/$owner/$repo/issues") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            parameter("state", "open")
            parameter("per_page", "100")
        }.body()

        return issues.filterNot { it.isPullRequest }
    }
}
