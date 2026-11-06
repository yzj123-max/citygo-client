package com.example.citygo.skeleton.data.repository.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideInlineEntryTest {
    @Test fun separatesTicketAndDescriptionFromPoiQueryWithoutDroppingTips() {
        val (name, fields) = GuideInlineEntry.split("08:30 故宫博物院【本地库推荐】；门票：暂无相关信息；特色：明清皇宫；建议提前实名预约。")
        assertEquals("08:30 故宫博物院【本地库推荐】", name)
        assertEquals(listOf("门票：暂无相关信息", "特色：明清皇宫", "建议提前实名预约。"), fields)
    }

    @Test fun preservesMealPrefixAndSupportsAsciiSemicolons() {
        val (name, fields) = GuideInlineEntry.split("午餐：同盛祥;人均：80元;特色菜：泡馍")
        assertEquals("午餐：同盛祥", name)
        assertEquals(2, fields.size)
    }

    @Test fun doesNotTruncateTextWithoutKnownInlineFields() {
        val text = "博物馆；艺术中心"
        assertEquals(text to emptyList<String>(), GuideInlineEntry.split(text))
    }
}
