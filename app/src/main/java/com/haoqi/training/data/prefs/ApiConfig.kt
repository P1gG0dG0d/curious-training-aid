package com.haoqi.training.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** OpenAI 兼容接口配置。默认指向 DeepSeek 官方（模型名 deepseek-flash 支持图像理解）。 */
data class ApiConfig(
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-flash",
    val apiKey: String = "",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

/** API Key 只存本机 SharedPreferences，不进代码仓库、不写日志。 */
class ApiConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<ApiConfig> = _config.asStateFlow()

    fun save(config: ApiConfig) {
        val cleaned = config.copy(
            baseUrl = config.baseUrl.trim(),
            model = config.model.trim(),
            apiKey = config.apiKey.trim(),
        )
        prefs.edit()
            .putString(KEY_BASE_URL, cleaned.baseUrl)
            .putString(KEY_MODEL, cleaned.model)
            .putString(KEY_API_KEY, cleaned.apiKey)
            .apply()
        _config.value = cleaned
    }

    private fun load(): ApiConfig = ApiConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, null) ?: DEFAULT_BASE_URL,
        model = prefs.getString(KEY_MODEL, null) ?: DEFAULT_MODEL,
        apiKey = prefs.getString(KEY_API_KEY, null) ?: "",
    )

    private companion object {
        const val PREFS_NAME = "ai_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
    }
}
