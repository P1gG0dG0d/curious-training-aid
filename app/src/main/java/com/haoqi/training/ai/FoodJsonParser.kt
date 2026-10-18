package com.haoqi.training.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * 把模型输出的文本解析成食物列表。
 * 模型可能返回 ```json 围栏或前后解释文字，所以先截取第一个 { 到最后一个 }。
 * 纯函数、不依赖 Android，方便单元测试。
 */
object FoodJsonParser {

    fun parse(content: String): List<FoodDraft> {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("没识别到食物，请换个角度重拍")
        val json = try {
            JSONObject(content.substring(start, end + 1))
        } catch (e: Exception) {
            throw AiException("解析识别结果失败，请重试")
        }
        val foods = json.optJSONArray("foods") ?: JSONArray()
        val list = mutableListOf<FoodDraft>()
        for (i in 0 until foods.length()) {
            val item = foods.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            list += FoodDraft(
                name = name,
                portion = item.optString("portion").trim().ifBlank { "1 份" },
                kcal = item.optDouble("kcal", 0.0).toFloat().coerceAtLeast(0f),
                protein = item.optDouble("protein", 0.0).toFloat().coerceAtLeast(0f),
                fat = item.optDouble("fat", 0.0).toFloat().coerceAtLeast(0f),
                carbs = item.optDouble("carbs", 0.0).toFloat().coerceAtLeast(0f),
            )
        }
        if (list.isEmpty()) throw AiException("没识别到食物，请换个角度重拍")
        return list
    }
}
