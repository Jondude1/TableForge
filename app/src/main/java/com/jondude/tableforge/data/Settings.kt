package com.jondude.tableforge.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiProvider(val label: String) {
    OFFLINE("Offline (built-in tables)"),
    CLAUDE("Claude API"),
    OLLAMA("Local Ollama server");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: OFFLINE
    }
}

data class AiSettings(
    val provider: AiProvider = AiProvider.OFFLINE,
    val claudeKey: String = "",
    val claudeModel: String = DEFAULT_CLAUDE_MODEL,
    val ollamaUrl: String = "http://192.168.1.10:11434",
    val ollamaModel: String = "llama3.1:8b",
    val creativity: Float = 0.9f,
) {
    val isConfigured: Boolean
        get() = when (provider) {
            AiProvider.OFFLINE -> true
            AiProvider.CLAUDE -> claudeKey.isNotBlank()
            AiProvider.OLLAMA -> ollamaUrl.isNotBlank() && ollamaModel.isNotBlank()
        }

    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5"
    }
}

/** Small SharedPreferences-backed store. Keys never leave the device except in API calls. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tableforge", Context.MODE_PRIVATE)

    private val _ai = MutableStateFlow(load())
    val ai: StateFlow<AiSettings> = _ai.asStateFlow()

    private val _campaignId = MutableStateFlow(prefs.getLong("campaignId", -1L))
    val campaignId: StateFlow<Long> = _campaignId.asStateFlow()

    private fun load() = AiSettings(
        provider = AiProvider.from(prefs.getString("provider", null)),
        claudeKey = prefs.getString("claudeKey", "") ?: "",
        claudeModel = prefs.getString("claudeModel", AiSettings.DEFAULT_CLAUDE_MODEL) ?: AiSettings.DEFAULT_CLAUDE_MODEL,
        ollamaUrl = prefs.getString("ollamaUrl", "http://192.168.1.10:11434") ?: "",
        ollamaModel = prefs.getString("ollamaModel", "llama3.1:8b") ?: "",
        creativity = prefs.getFloat("creativity", 0.9f),
    )

    fun saveAi(s: AiSettings) {
        prefs.edit()
            .putString("provider", s.provider.name)
            .putString("claudeKey", s.claudeKey.trim())
            .putString("claudeModel", s.claudeModel.trim())
            .putString("ollamaUrl", s.ollamaUrl.trim().trimEnd('/'))
            .putString("ollamaModel", s.ollamaModel.trim())
            .putFloat("creativity", s.creativity)
            .apply()
        _ai.value = load()
    }

    fun setCampaign(id: Long) {
        prefs.edit().putLong("campaignId", id).apply()
        _campaignId.value = id
    }
}
