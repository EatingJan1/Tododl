package de.tododl.shared.remote

import io.ktor.client.HttpClient

/**
 * Plattform-spezifische Erzeugung des Ktor-HttpClient (Engine unterscheidet
 * sich pro Plattform: CIO auf Desktop, Darwin auf iOS, OkHttp auf Android).
 */
expect class HttpClientFactory {
    fun create(): HttpClient
}
