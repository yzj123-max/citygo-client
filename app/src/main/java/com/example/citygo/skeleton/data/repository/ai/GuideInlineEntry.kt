package com.example.citygo.skeleton.data.repository.ai

/** 将单行攻略里的地点名与分号后的配套说明分开，避免把整段说明用于地点检索。 */
internal object GuideInlineEntry {
    fun split(content: String): Pair<String, List<String>> {
        val parts = content.split(Regex("[；;]")).map(String::trim).filter(String::isNotEmpty)
        if (parts.size < 2 || parts.drop(1).none { field.containsMatchIn(it) }) return content to emptyList()
        return parts.first() to parts.drop(1)
    }

    private val field = Regex("""^\s*\*{0,2}(?:门票(?:信息|价格|费用)?|票价|人均(?:消费|价格|费用)?|特色(?:菜品|菜)?|招牌菜|介绍|简介|亮点|时间段?|开放时间|营业时间|来源|类型|宠物友好|地址|出行要点|游玩提示)\s*\*{0,2}\s*[:：]""")
}
