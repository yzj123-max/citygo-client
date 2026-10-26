package com.example.citygo.skeleton.data.repository.ai

import com.example.citygo.skeleton.data.model.AiSpot
import com.example.citygo.skeleton.data.model.AiSpotType
import com.example.citygo.skeleton.data.model.AiTravelPlan
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/* =====================================================================================
 * 行程对话助手：结构化提议 JSON 的容错解析（不负责生成攻略）
 *
 * 大模型输出可能不完全守规矩（带 ```json 围栏、字段名漂移、经纬度输出成字符串、
 * 图片给的是文字描述等），本解析器统一做防御式处理，保证「能解析的尽量解析」，
 * 彻底无法使用时抛 [AiGuideException] 让上层提示重试，而不是崩溃。
 * ===================================================================================== */

/** 行程提议解析失败；由调用方保留原行程并提示用户。 */
class AiGuideException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** 大模型 JSON 文本 → [AiTravelPlan] 的容错解析器。 */
object AiGuideParser {

    /**
     * @param city 请求时的城市名（模型漏返回 city 字段时用于回填）
     * @param rawContent 模型回复的原始文本
     * @param nowMillis 生成时间戳（由调用方传入，保证与缓存写入时间一致）
     */
    fun parse(city: String, rawContent: String, nowMillis: Long): AiTravelPlan {
        val jsonText = stripCodeFence(rawContent).trim()
        if (jsonText.isEmpty()) {
            throw AiGuideException("大模型返回内容为空")
        }

        val root = try {
            JsonParser.parseString(jsonText)
        } catch (e: Exception) {
            throw AiGuideException("大模型返回不是合法 JSON：${e.message}", e)
        }

        // 顶层可能直接是数组，也可能是 {city, spotList:[...]} 对象
        val spotArray: JsonArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> extractSpotArray(root.asJsonObject)
            else -> throw AiGuideException("无法识别的攻略 JSON 结构")
        }

        val spots = spotArray.mapNotNull { element ->
            // 单个地点解析失败（如经纬度严重非法）不影响其他地点；全部失败时下面再统一报错
            runCatching { parseSpot(element) }.getOrNull()
        }
        if (spots.isEmpty()) {
            throw AiGuideException("攻略中没有任何可解析的有效地点，请让大模型重新生成")
        }

        val resolvedCity = if (root.isJsonObject) {
            root.asJsonObject.getNonBlankString("city")?.takeIf { it.isNotBlank() } ?: city
        } else {
            city
        }

        return AiTravelPlan(
            city = resolvedCity.trim(),
            generateTime = nowMillis,
            spotList = spots,
        )
    }

    /** 去除模型可能包裹的 ```json … ``` / ``` … ``` 代码围栏。 */
    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        // 去掉首行（```json 或 ```）与结尾的 ```
        return trimmed
            .lineSequence()
            .drop(1)
            .joinToString("\n")
            .removeSuffix("```")
            .trim()
    }

    /** 从顶层对象里按常见字段名找到地点数组。 */
    private fun extractSpotArray(obj: JsonObject): JsonArray {
        val candidates = listOf("spotList", "spots", "places", "items", "data", "list")
        for (key in candidates) {
            val element = obj.get(key)
            if (element != null && element.isJsonArray) return element.asJsonArray
        }
        // 找不到数组字段：若整个对象只有一个数组值字段，也兜底取它
        obj.entrySet().firstOrNull { it.value.isJsonArray }?.value?.asJsonArray
            ?.let { return it }
        throw AiGuideException("攻略 JSON 中找不到地点列表字段（spotList/spots/places）")
    }

    /** 解析单个地点；字段名做常见漂移兼容。 */
    private fun parseSpot(element: JsonElement): AiSpot {
        if (!element.isJsonObject) throw AiGuideException("地点项不是 JSON 对象")
        val obj = element.asJsonObject

        val name = obj.getNonBlankString("name", "title", "spotName")
            ?: throw AiGuideException("地点缺少 name 字段")

        val typeRaw = obj.getNonBlankString("type", "category", "kind", "spotType").orEmpty()
        val latitude = obj.getAsDoubleLenient("latitude", "lat")
        val longitude = obj.getAsDoubleLenient("longitude", "lng", "lon")
        if (latitude == null || longitude == null) {
            throw AiGuideException("地点「$name」缺少有效经纬度")
        }

        val desc = obj.getNonBlankString("desc", "description", "intro", "summary")
            ?: obj.getNonBlankString("name").orEmpty()

        return AiSpot(
            name = name.trim(),
            type = mapType(typeRaw),
            desc = desc.trim(),
            latitude = latitude,
            longitude = longitude,
            isPetFriendly = obj.getAsBooleanLenient(
                "isPetFriendly", "petFriendly", "petsAllowed", "pet",
            ) ?: false,
            imageUrls = obj.getAsStringList(
                "imageUrls", "images", "pics", "imageUrl", "imageDesc", "image",
            ),
            tip = obj.getNonBlankString("tip", "tips", "remark", "notes", "advice").orEmpty().trim(),
            // 多日行程时模型给的第几天（1 起）；缺失 / 非法由上层均匀分组兜底
            day = obj.getAsIntLenient("day", "dayIndex", "tripDay")?.takeIf { it >= 1 },
        )
    }

    /** 中文类型关键词 → 枚举（模型输出不稳定时用包含匹配兜底）。 */
    private fun mapType(raw: String): AiSpotType {
        val text = raw.trim()
        return when {
            // 住宿类优先于其他关键词判断（如"网红酒店"不应被判成探店）
            text.contains("酒店") || text.contains("住宿") ||
                text.contains("宾馆") || text.contains("民宿") || text.contains("客栈") ->
                AiSpotType.HOTEL
            text.contains("网红") || text.contains("探店") || text.contains("打卡") ->
                AiSpotType.INTERNET_FAMOUS_SHOP
            text.contains("小众") -> AiSpotType.NICHE_SPOT
            text.contains("美食") || text.contains("小吃") || text.contains("吃") ->
                AiSpotType.LOCAL_FOOD
            // 显式命中"大众景点"或无法识别时，均按大众景点处理（枚举 fromLabel 也是该兜底策略）
            else -> AiSpotType.fromLabel(text)
        }
    }

    /* ------------------------------ JsonObject 容错取值辅助 ------------------------------ */

    private fun JsonObject.getNonBlankString(vararg keys: String): String? {
        for (key in keys) {
            val element = this.get(key) ?: continue
            if (element.isJsonPrimitive && !element.asJsonPrimitive.isBoolean) {
                val value = element.asString.trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun JsonObject.getAsDoubleLenient(vararg keys: String): Double? {
        for (key in keys) {
            val element = this.get(key) ?: continue
            if (!element.isJsonPrimitive) continue
            val primitive = element.asJsonPrimitive
            val value = if (primitive.isNumber) {
                primitive.asNumber.toDouble()
            } else {
                // 模型把数字输出成字符串（如 "30.12,104.12" 取第一段）时容错解析
                primitive.asString.trim().split(",").firstOrNull()
                    ?.toDoubleOrNull()
            }
            if (value != null && value.isFinite()) return value
        }
        return null
    }

    private fun JsonObject.getAsIntLenient(vararg keys: String): Int? {
        for (key in keys) {
            val element = this.get(key) ?: continue
            if (!element.isJsonPrimitive) continue
            val primitive = element.asJsonPrimitive
            val value = when {
                primitive.isNumber -> primitive.asNumber.toInt()
                else -> primitive.asString.trim().toIntOrNull()
            }
            if (value != null) return value
        }
        return null
    }

    private fun JsonObject.getAsBooleanLenient(vararg keys: String): Boolean? {
        for (key in keys) {
            val element = this.get(key) ?: continue
            if (!element.isJsonPrimitive) continue
            val primitive = element.asJsonPrimitive
            when {
                primitive.isBoolean -> return primitive.asBoolean
                primitive.isNumber -> return primitive.asNumber.toInt() != 0
                else -> primitive.asString.trim().lowercase().let {
                    if (it in listOf("true", "yes", "1", "可", "允许")) return true
                    if (it in listOf("false", "no", "0", "否", "不允许")) return false
                }
            }
        }
        return null
    }

    /** 图片字段兼容：数组多值 / 单字符串；图片描述文字同样收入（后续可替换为图床 URL）。 */
    private fun JsonObject.getAsStringList(vararg keys: String): List<String> {
        for (key in keys) {
            val element = this.get(key) ?: continue
            when {
                element.isJsonArray -> return element.asJsonArray
                    .mapNotNull { it.takeIf { v -> v.isJsonPrimitive }?.asString?.trim() }
                    .filter { it.isNotEmpty() }
                element.isJsonPrimitive -> {
                    val single = element.asString.trim()
                    if (single.isNotEmpty()) return listOf(single)
                }
            }
        }
        return emptyList()
    }
}
