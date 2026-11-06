package com.example.citygo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citygo.skeleton.data.remote.AmapGeocoder
import com.example.citygo.skeleton.data.remote.auth.AuthApi
import com.example.citygo.skeleton.data.repository.ai.GuideSpotGeocodeImporter
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 拦截请求验证真实 API 层，不发短信、不登录或改变当前账号。 */
@RunWith(AndroidJUnit4::class)
class UiIntegrationContractTest {
    private fun api(capture: (Request) -> Unit) = AuthApi(OkHttpClient.Builder()
        .addInterceptor { chain ->
            capture(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("""{"code":200,"msg":"OK","data":null}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build())

    @Test fun verificationLoginUsesBackendJsonContract() = runBlocking {
        var request: Request? = null
        api { request = it }.codeLogin("test-phone", "123456")
        val captured = checkNotNull(request)
        assertEquals("/userLogin/code", captured.url.encodedPath)
        assertEquals("POST", captured.method)
        assertTrue(captured.body!!.contentType().toString().startsWith("application/json"))
        val buffer = Buffer(); captured.body!!.writeTo(buffer)
        val json = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        assertEquals("test-phone", json["phone"].asString)
        assertEquals("123456", json["verifyCode"].asString)
        assertFalse(json.has("code"))
    }

    @Test fun sendCodeUsesJsonBodyInsteadOfQuery() = runBlocking {
        var request: Request? = null
        api { request = it }.sendCode("test-phone")
        val captured = checkNotNull(request)
        assertEquals("/sendCode", captured.url.encodedPath)
        assertNull(captured.url.query)
        val buffer = Buffer(); captured.body!!.writeTo(buffer)
        assertEquals("test-phone", JsonParser.parseString(buffer.readUtf8()).asJsonObject["phone"].asString)
    }

    @Test fun inlineGuideFieldsAreNotIncludedInGeocodingName() {
        val importer = GuideSpotGeocodeImporter(AmapGeocoder(InstrumentationRegistry.getInstrumentation().targetContext))
        val parse = importer.javaClass.getDeclaredMethod("parseBlocks", String::class.java).apply { isAccessible = true }
        val rows = parse.invoke(importer, "### 第一天\n- 08:30 故宫博物院【本地库推荐】；门票：暂无相关信息；特色：明清皇宫；建议提前实名预约。") as List<*>
        assertEquals(1, rows.size)
        val row = checkNotNull(rows.single())
        fun field(name: String) = row.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(row)
        assertEquals("故宫博物院", field("name"))
        assertEquals("08:30", field("timeSlot"))
        assertEquals("暂无相关信息", field("ticket"))
        assertEquals("明清皇宫", field("descText"))
        assertTrue(field("extraTips").toString().contains("实名预约"))
    }
}
