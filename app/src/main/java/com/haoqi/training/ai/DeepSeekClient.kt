package com.haoqi.training.ai

import com.haoqi.training.data.prefs.ApiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** AI 识别出的一条食物（全部为估算值） */
data class FoodDraft(
    val name: String,
    val portion: String,
    val kcal: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
)

/** 可读的 AI 错误，直接展示给用户 */
class AiException(message: String) : Exception(message)

/**
 * DeepSeek（OpenAI 兼容协议）客户端：
 *  - testConnection：GET /models 校验 Key、模型名，并检查模型是否支持图片输入
 *  - analyzeFood：图片 + 文字 → 食物营养估算（deepseek-flash 支持图像理解）
 *
 * 参考官方文档：
 *  - https://api-docs.deepseek.com/zh-cn/guides/vision
 *  - https://api-docs.deepseek.com/zh-cn/api/list-models
 *  - https://api-docs.deepseek.com/zh-cn/guides/thinking_mode
 * 识别时关闭思考模式（thinking disabled），否则思维链会占用 max_tokens、延迟也更高。
 */
class DeepSeekClient {

    suspend fun testConnection(config: ApiConfig): Result<String> = withContext(Dispatchers.IO) {
        resultOf {
            requireConfigured(config)
            val models = try {
                fetchModels(config)
            } catch (e: AiException) {
                // 某些 OpenAI 兼容服务没有 /models，退化成一次最小对话请求
                if (e.message?.contains("HTTP 404") == true) {
                    pingChat(config)
                    return@resultOf "连接成功：${config.model} 可以用（该地址不支持模型列表，已用最小请求验证）"
                }
                throw e
            }
            val target = models.firstOrNull { it.id == config.model }
                ?: throw AiException(
                    "Key 有效，但模型列表里没有「${config.model}」。可用模型：" +
                        models.joinToString("、") { it.id }.ifBlank { "（空）" }
                )
            val vision = if (target.supportsImage) "支持看图" else "不支持看图，无法识别食物"
            "连接成功：${target.id}（$vision），Key 有效"
        }
    }

    suspend fun analyzeFood(
        config: ApiConfig,
        imageBase64: String,
        mime: String,
    ): Result<List<FoodDraft>> = withContext(Dispatchers.IO) {
        resultOf {
            requireConfigured(config)
            val body = JSONObject().apply {
                put("model", config.model)
                // 官方文档：思考模式默认开启；识别任务关掉更快、更省 token
                put("thinking", JSONObject().put("type", "disabled"))
                put("max_tokens", 1600)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "content",
                                JSONArray().apply {
                                    put(
                                        JSONObject().apply {
                                            put("type", "text")
                                            put("text", PROMPT)
                                        }
                                    )
                                    put(
                                        JSONObject().apply {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                JSONObject().apply {
                                                    put("url", "data:$mime;base64,$imageBase64")
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                )
            }
            parseFoods(postChatCompletions(config, body))
        }
    }
    private fun requireConfigured(config: ApiConfig) {
        if (!config.isConfigured) throw AiException("请先在设置里填写 API Key")
    }

    private data class ModelInfo(
        val id: String,
        val supportsImage: Boolean,
    )

    private fun fetchModels(config: ApiConfig): List<ModelInfo> {
        val endpoint = config.baseUrl.trimEnd('/') + "/models"
        val connection = openConnection(config, endpoint)
        try {
            val code = connection.responseCode
            val text = readBody(connection, code)
            if (code !in 200..299) throw AiException(httpErrorMessage(code, text))
            val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
            return (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").trim()
                if (id.isBlank()) return@mapNotNull null
                val modalities = item.optJSONArray("input_modalities")
                val supportsImage = modalities != null &&
                    (0 until modalities.length()).any { modalities.optString(it) == "image" }
                ModelInfo(id = id, supportsImage = supportsImage)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** 没有 /models 的兼容服务：发一条 16 token 的最小对话，验证地址与 Key。 */
    private fun pingChat(config: ApiConfig) {
        val body = JSONObject().apply {
            put("model", config.model)
            put("thinking", JSONObject().put("type", "disabled"))
            put("max_tokens", 16)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", "ping")
                )
            )
        }
        postChatCompletions(config, body)
    }

    private fun openConnection(config: ApiConfig, endpoint: String): HttpURLConnection =
        (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
        }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun postChatCompletions(config: ApiConfig, body: JSONObject): String {
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = readBody(connection, code)
            if (code !in 200..299) throw AiException(httpErrorMessage(code, text))

            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw AiException("服务器返回的不是 JSON，请检查接口地址")
            }
            val choices = json.optJSONArray("choices")
                ?: throw AiException("模型返回格式异常（没有 choices）")
            if (choices.length() == 0) throw AiException("模型没有返回内容")
            val choice = choices.getJSONObject(0)
            val message = choice.optJSONObject("message")
            val content = message?.optString("content").orEmpty()
            if (content.isNotBlank()) return content
            val finishReason = choice.optString("finish_reason")
            throw AiException(
                if (finishReason == "length") "模型输出被截断，请重试或换更小的图"
                else "模型返回内容为空（finish_reason=$finishReason）"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun httpErrorMessage(code: Int, body: String): String {
        // 服务端错误文本可能包含请求片段，截断后再展示
        val snippet = body.take(120).replace("\n", " ").replace("\r", " ")
        return when (code) {
            401, 403 -> "API Key 无效或无权限（HTTP $code）"
            404 -> "接口地址或模型名不存在（HTTP 404）"
            413 -> "图片太大，服务器拒绝（HTTP 413）"
            422 -> "请求参数不被接受（HTTP 422）：$snippet"
            429 -> "请求太频繁或额度不足（HTTP 429）"
            in 500..599 -> "服务器繁忙（HTTP $code），稍后重试"
            else -> "请求失败（HTTP $code）：$snippet"
        }
    }

    /** 从模型输出里抠出 JSON（可能带 ``` 围栏或解释文字），交给纯函数解析器。 */
    private fun parseFoods(content: String): List<FoodDraft> = FoodJsonParser.parse(content)

    private fun Throwable.toAiException(): AiException = when (this) {
        is AiException -> this
        is java.net.SocketTimeoutException -> AiException("网络超时，请检查网络后重试")
        is java.net.UnknownHostException -> AiException("无法连接服务器，请检查网络和接口地址")
        is javax.net.ssl.SSLException -> AiException("HTTPS 连接失败，请检查接口地址")
        else -> AiException("识别失败：${message ?: "未知错误"}")
    }

    private inline fun <T> resultOf(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toAiException())
    }

    private companion object {
        val PROMPT = """
            你是一名营养师。请看这张食物照片，估算每种食物的份量和营养。
            只输出 JSON，不要输出任何解释。格式：
            {"foods":[{"name":"米饭","portion":"150g","kcal":174,"protein":3.8,"fat":0.4,"carbs":38.8}]}
            数值单位：热量 kcal、蛋白质/脂肪/碳水为克。识别不到食物时输出 {"foods":[]}。
        """.trimIndent()
    }
}
