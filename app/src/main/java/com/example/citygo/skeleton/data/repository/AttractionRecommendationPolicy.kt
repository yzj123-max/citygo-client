package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.remote.attraction.AttractionDto

/** 推荐只展示旅游目的地；名称搜索仍允许查找具体景点和内部地点。 */
object AttractionRecommendationPolicy {
    fun photoKeyword(name: String): String = name.trim().replace(Regex("(生态)?旅游景区$|(生态)?旅游区$|风景名胜区$|风景区$|景区$"), "").ifBlank { name.trim() }
    fun eligible(item: AttractionDto): Boolean {
        val name = item.name.orEmpty()
        if (name.isBlank()) return false
        if (Regex("校园|校内|学校|学院|教学|宿舍|操场|运动场|食堂|停车场|售票|游客中心|服务中心|出入口|观光车|公交站").containsMatchIn(name)) return false
        if (Regex("校园内|校内|大学内|学院内").containsMatchIn(item.address.orEmpty())) return false
        // 不把景区内的门、池、碑、表演点当成独立的城市推荐。
        if (Regex("[-—－·].+(门|池|坊|堂|寺|馆|桥|楼|表演|遗址|城墙)$").containsMatchIn(name)) return false
        if (Regex("广场|花坛|凉亭|放生池|节孝坊|健身|绿化|雕塑").containsMatchIn(name) && !rated(item)) return false
        return rated(item) || item.categoryCode in setOf("110200", "110202", "110203", "110204", "110206", "110207", "110209", "140100") ||
            Regex("景区|风景区|风景名胜|旅游区|旅游度假|古镇|古城|国家公园|森林公园|地质公园|湿地公园|博物馆|名胜|故居|遗址公园|文化遗产").containsMatchIn(name)
    }
    fun rank(item: AttractionDto): Int = when {
        item.level.orEmpty().uppercase().contains("5A") -> 500
        item.level.orEmpty().uppercase().contains("4A") -> 400
        rated(item) -> 300
        item.categoryCode == "110202" -> 250
        else -> 100
    }
    private fun rated(item: AttractionDto) = Regex("[1-5]A", RegexOption.IGNORE_CASE).containsMatchIn(item.level.orEmpty())
    fun select(items: List<AttractionDto>) = items.filter(::eligible).distinctBy { it.amapPoiId ?: it.attractionId?.toString() ?: it.name }
        .sortedByDescending(::rank)

    fun complete(spot: com.example.citygo.skeleton.data.model.ScenicSpot): Boolean =
        spot.description.trim().length >= 150 && !spot.description.contains("接口测试") &&
            !spot.descriptionSourceUrl.isNullOrBlank() && !spot.descriptionVerifiedAt.isNullOrBlank() &&
            spot.coverImg.isNotBlank() && spot.imgUrls.isNotEmpty() && spot.coordinateAvailable &&
            (spot.ticketStatus in setOf("FREE", "PAID", "PARTIAL") || !spot.collectedInfo?.ticketPrice.isNullOrBlank()) &&
            (AttractionDisplayMapper.policyValue(spot.policies.firstOrNull { it.policyType == "OPENING_HOURS" }) != "UNKNOWN" || !spot.collectedInfo?.openTime.isNullOrBlank())

    /** 配图只能匹配同一POI或同城市的同名景点，不能借用附近其他景点照片。 */
    fun samePlace(spot: com.example.citygo.skeleton.data.model.ScenicSpot, candidate: AttractionDto): Boolean {
        if (spot.backendId != null && spot.backendId == candidate.attractionId) return true
        if (!spot.amapPoiId.isNullOrBlank() && spot.amapPoiId == candidate.amapPoiId) return true
        fun normalize(value: String) = photoKeyword(value).replace(" ", "")
        val sameCity = spot.city.isNotBlank() && spot.city.removeSuffix("市") == candidate.city.orEmpty().removeSuffix("市")
        return sameCity && normalize(spot.name) == normalize(candidate.name.orEmpty())
    }
}
