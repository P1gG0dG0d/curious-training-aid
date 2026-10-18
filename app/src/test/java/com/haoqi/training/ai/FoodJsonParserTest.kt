package com.haoqi.training.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodJsonParserTest {

    @Test
    fun `parses plain json`() {
        val foods = FoodJsonParser.parse(
            """{"foods":[{"name":"米饭","portion":"150g","kcal":174,"protein":3.8,"fat":0.4,"carbs":38.8}]}"""
        )
        assertEquals(1, foods.size)
        assertEquals("米饭", foods[0].name)
        assertEquals("150g", foods[0].portion)
        assertEquals(174f, foods[0].kcal, 0.01f)
        assertEquals(3.8f, foods[0].protein, 0.01f)
    }

    @Test
    fun `parses json wrapped in markdown fence and chatter`() {
        val content = """
            好的，识别结果如下：
            ```json
            {"foods":[{"name":"鸡蛋","portion":"2 个","kcal":140,"protein":12,"fat":10,"carbs":1},{"name":"牛奶","portion":"250ml","kcal":150,"protein":8,"fat":8,"carbs":12}]}
            ```
            以上为估算值。
        """.trimIndent()
        val foods = FoodJsonParser.parse(content)
        assertEquals(2, foods.size)
        assertEquals("鸡蛋", foods[0].name)
        assertEquals("牛奶", foods[1].name)
    }

    @Test
    fun `missing fields fall back to safe defaults`() {
        val foods = FoodJsonParser.parse("""{"foods":[{"name":"苹果"}]}""")
        assertEquals("1 份", foods[0].portion)
        assertEquals(0f, foods[0].kcal, 0.001f)
    }

    @Test
    fun `negative values are clamped to zero`() {
        val foods = FoodJsonParser.parse("""{"foods":[{"name":"汤","kcal":-5,"protein":-1}]}""")
        assertEquals(0f, foods[0].kcal, 0.001f)
        assertEquals(0f, foods[0].protein, 0.001f)
    }

    @Test
    fun `unparseable output throws friendly error`() {
        val error = assertThrows(AiException::class.java) {
            FoodJsonParser.parse("抱歉，我看不清这张图")
        }
        assertTrue(error.message!!.contains("没识别到食物"))
    }

    @Test
    fun `empty foods list throws friendly error`() {
        val error = assertThrows(AiException::class.java) {
            FoodJsonParser.parse("""{"foods":[]}""")
        }
        assertTrue(error.message!!.contains("没识别到食物"))
    }

    @Test
    fun `entries without a name are skipped`() {
        val foods = FoodJsonParser.parse(
            """{"foods":[{"name":"","kcal":100},{"name":"鸡胸肉","portion":"100g","kcal":133}]}"""
        )
        assertEquals(1, foods.size)
        assertEquals("鸡胸肉", foods[0].name)
    }
}
