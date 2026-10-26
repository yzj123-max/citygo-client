package com.example.citygo.skeleton.data.repository.ai

import com.example.citygo.skeleton.data.model.AiSpotType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证行程提议中的酒店类型与别名解析。
 */
class AiGuideParserHotelTest {

    private val rawJson = """
        {
          "city": "杭州",
          "spotList": [
            {"name":"西湖国宾馆","type":"酒店住宿","desc":"湖边园林酒店",
             "latitude":30.2451,"longitude":120.1502,"isPetFriendly":false,
             "imageUrls":[],"tip":"","day":1},
            {"name":"某连锁宾馆","type":"宾馆","desc":"交通方便",
             "latitude":30.2460,"longitude":120.1510,"imageUrls":[],"day":1},
            {"name":"断桥","type":"大众景点","desc":"地标",
             "latitude":30.2590,"longitude":120.1500,"imageUrls":[],"day":1}
          ]
        }
    """.trimIndent()

    @Test
    fun hotelKeywords_mapToHotelType() {
        val plan = AiGuideParser.parse("杭州", rawJson, nowMillis = 1L)
        val byName = plan.spotList.associateBy { it.name }
        assertEquals(AiSpotType.HOTEL, byName["西湖国宾馆"]?.type)
        // 容错关键词："宾馆" 也应识别为酒店
        assertEquals(AiSpotType.HOTEL, byName["某连锁宾馆"]?.type)
        assertEquals(AiSpotType.POPULAR_SPOT, byName["断桥"]?.type)
    }


}
