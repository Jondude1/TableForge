package com.jondude.tableforge.ai

import com.jondude.tableforge.data.AiProvider
import com.jondude.tableforge.data.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : IOException(message)

/** A JPEG/PNG image sent with the prompt, base64 encoded (no data: prefix). */
data class ImagePart(val mediaType: String, val base64: String)

/** Sends one system + user prompt to the configured model and returns the raw text reply. */
object AiClient {

    suspend fun complete(
        settings: AiSettings,
        system: String,
        user: String,
        maxTokens: Int = 3000,
        images: List<ImagePart> = emptyList(),
    ): String =
        withContext(Dispatchers.IO) {
            when (settings.provider) {
                AiProvider.CLAUDE -> claude(settings, system, user, maxTokens, images)
                AiProvider.OLLAMA -> ollama(settings, system, user, images)
                AiProvider.OFFLINE -> throw AiException("Offline mode has no AI model. Pick Claude or Ollama in Settings.")
            }
        }

    private fun claude(s: AiSettings, system: String, user: String, maxTokens: Int, images: List<ImagePart>): String {
        if (s.claudeKey.isBlank()) throw AiException("Add your Claude API key in Settings.")
        val body = JSONObject()
            .put("model", s.claudeModel.ifBlank { AiSettings.DEFAULT_CLAUDE_MODEL })
            .put("max_tokens", maxTokens)
            // No temperature: newer Claude models reject it ("temperature is deprecated").
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", claudeContent(user, images))))
        val json = post(
            "https://api.anthropic.com/v1/messages", body,
            mapOf("x-api-key" to s.claudeKey, "anthropic-version" to "2023-06-01"),
            timeoutMs = 120_000,
        )
        val content = json.optJSONArray("content") ?: throw AiException("Claude returned no content.")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val part = content.getJSONObject(i)
            if (part.optString("type") == "text") sb.append(part.optString("text"))
        }
        return sb.toString()
    }

    private fun claudeContent(user: String, images: List<ImagePart>): Any {
        if (images.isEmpty()) return user
        val arr = JSONArray()
        images.forEach { im ->
            arr.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", im.mediaType).put("data", im.base64),
                ),
            )
        }
        return arr.put(JSONObject().put("type", "text").put("text", user))
    }

    private fun ollama(s: AiSettings, system: String, user: String, images: List<ImagePart>): String {
        val base = s.ollamaUrl.trim().trimEnd('/')
        if (base.isBlank()) throw AiException("Add your Ollama server address in Settings.")
        val body = JSONObject()
            .put("model", s.ollamaModel)
            .put("stream", false)
            .put("format", "json")
            .put("options", JSONObject().put("temperature", s.creativity.toDouble()))
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(
                        JSONObject().put("role", "user").put("content", user).apply {
                            // Vision models (e.g. llava, llama3.2-vision) read images this way.
                            if (images.isNotEmpty()) put("images", JSONArray(images.map { it.base64 }))
                        },
                    ),
            )
        val json = post("$base/api/chat", body, emptyMap(), timeoutMs = 300_000)
        return json.optJSONObject("message")?.optString("content")
            ?: throw AiException("Ollama returned no message.")
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>, timeoutMs: Int): JSONObject {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("Bad server address: $url")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching {
                    val j = JSONObject(text)
                    j.optJSONObject("error")?.optString("message") ?: j.optString("error")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: text.take(200)
                throw AiException("Server error $code: $msg")
            }
            return JSONObject(text)
        } catch (e: AiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw AiException("The model took too long to answer. Try again.")
        } catch (e: java.net.UnknownHostException) {
            throw AiException("Can't reach the server. Check the tablet's internet or the address in Settings.")
        } catch (e: java.net.ConnectException) {
            throw AiException("Can't connect to $url. Is the server running and on the same network?")
        } catch (e: Exception) {
            throw AiException(e.message ?: "Request failed.")
        } finally {
            conn.disconnect()
        }
    }

    /** Pulls the {"items":[...]} list out of a reply, tolerating code fences and stray prose. */
    fun parseItems(raw: String): List<JSONObject> {
        val text = raw.replace("```json", "").replace("```", "").trim()
        val arr: JSONArray? = runCatching {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            JSONObject(text.substring(start, end + 1)).optJSONArray("items")
        }.getOrNull() ?: runCatching {
            JSONArray(text.substring(text.indexOf('['), text.lastIndexOf(']') + 1))
        }.getOrNull()
        if (arr == null) throw AiException("The model's answer wasn't in the expected format. Try regenerating.")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }
}
