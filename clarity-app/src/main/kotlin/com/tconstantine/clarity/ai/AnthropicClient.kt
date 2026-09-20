package com.tconstantine.clarity.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class AiCallResult {
    data class Success(val text: String) : AiCallResult()
    data class Failure(val message: String) : AiCallResult()
}

private val responseJson = Json { ignoreUnknownKeys = true }

class AnthropicClient(
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(responseJson)
        }
    },
) {
    suspend fun sendPrompt(
        apiKey: String,
        model: AnthropicModel,
        prompt: String,
    ): AiCallResult {
        if (apiKey.isBlank()) {
            return AiCallResult.Failure("Add your Anthropic API key in Settings first.")
        }
        return try {
            val response = httpClient.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(
                    MessagesRequest(
                        model = model.apiId,
                        maxTokens = 4096,
                        messages = listOf(MessageParam(role = "user", content = prompt)),
                    ),
                )
            }
            parseResponse(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiCallResult.Failure(e.message ?: "Couldn't reach the AI service. Check your connection.")
        }
    }

    private suspend fun parseResponse(response: HttpResponse): AiCallResult {
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message = runCatching {
                responseJson.decodeFromString<ErrorResponse>(bodyText).error.message
            }.getOrNull()
            return AiCallResult.Failure(message ?: "Request failed (${response.status.value}).")
        }
        val parsed = runCatching {
            responseJson.decodeFromString<MessagesResponse>(bodyText)
        }.getOrNull() ?: return AiCallResult.Failure("Couldn't parse the AI response.")
        val text = parsed.content.firstOrNull { it.type == "text" }?.text
        return if (text != null) {
            AiCallResult.Success(text)
        } else {
            AiCallResult.Failure("The AI response didn't contain any text.")
        }
    }

    fun close() = httpClient.close()
}

@Serializable
private data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<MessageParam>,
)

@Serializable
private data class MessageParam(val role: String, val content: String)

@Serializable
private data class MessagesResponse(val content: List<ContentBlock>)

@Serializable
private data class ContentBlock(val type: String, val text: String? = null)

@Serializable
private data class ErrorResponse(val error: ErrorDetail)

@Serializable
private data class ErrorDetail(val message: String)
