package de.tododl.shared.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

actual class HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause is ResponseException) {
                    val status = cause.response.status.value
                    val bodyText = runCatching { cause.response.bodyAsText() }.getOrDefault("")
                    val msg = parseJsonErrorMsg(bodyText) ?: cause.message

                    val isExpired = msg?.contains("Token has expired", ignoreCase = true) == true ||
                            msg?.contains("expired", ignoreCase = true) == true

                    if (status == 401 || isExpired) {
                        throw Exception("Sitzung/Token abgelaufen. Bitte unter 'Meine Server' neu anmelden.", cause)
                    } else {
                        throw Exception(msg ?: "Unbekannter Fehler", cause)
                    }
                }
            }
        }
    }
}

private fun parseJsonErrorMsg(jsonText: String): String? {
    return runCatching {
        val element = Json.parseToJsonElement(jsonText)
        val obj = element.jsonObject
        obj["msg"]?.jsonPrimitive?.content
            ?: obj["error"]?.jsonPrimitive?.content
            ?: obj["message"]?.jsonPrimitive?.content
    }.getOrNull()
}
