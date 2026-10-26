package com.example.citygo.skeleton.data.remote.poi

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/* =====================================================================================
 * 高德 POI JSON 容错解析
 *
 * 高德 v3 Web API 有个历史习惯：字段无内容时不返回 ""，而是返回空数组 []，例如：
 *   "biz_ext": { "rating": [], "cost": [] }
 *   "photos": [{ "title": [], "url": "http://..." }]
 * 直接把这些字段声明为 String 会让 Gson 抛
 *   IllegalStateException: Expected a string but was BEGIN_ARRAY。
 *
 * 这里为所有 String 字段注册一个宽容适配器：
 * - 正常字符串 → 原样读取；
 * - 遇到数组 / 对象（即空值 [] 或异常结构）→ 跳过该值，按 null 处理；
 * - 数字 → 转字符串（个别字段可能返回数字型 id）。
 * ===================================================================================== */

/** 宽容字符串适配器：兼容高德「空值返回 []」的不规范 JSON */
class SafeStringTypeAdapter : TypeAdapter<String>() {

    override fun read(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.STRING -> reader.nextString()
        JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        // 高德空字符串字段实际返回 []（BEGIN_ARRAY）或 {}（BEGIN_OBJECT），跳过并视为 null
        JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> {
            reader.skipValue()
            null
        }
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        else -> {
            reader.skipValue()
            null
        }
    }

    override fun write(writer: JsonWriter, value: String?) {
        writer.value(value)
    }
}

/** POI 模块共享 Gson（接口响应解析 + Room 缓存序列化） */
object PoiJson {

    /** 注册了宽容字符串适配器的 Gson 实例 */
    val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(String::class.java, SafeStringTypeAdapter().nullSafe())
        .create()
}
