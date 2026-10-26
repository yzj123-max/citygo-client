package com.example.citygo.skeleton.data.remote.community

import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

/** 用实际HTTP请求验证与Spring接口的路径、方法、分页和报文契约。 */
class CommunityApiContractTest {
    private lateinit var server: HttpServer
    private lateinit var api: CommunityApi
    private data class Request(val method: String, val path: String, val query: String?, val body: String)
    private val requests = LinkedBlockingQueue<Request>()
    private var payload = "{}"

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(Request(exchange.requestMethod, exchange.requestURI.path,
                exchange.requestURI.rawQuery, exchange.requestBody.bufferedReader().readText()))
            val bytes = """{"code":200,"msg":"操作成功","data":$payload}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
            .client(OkHttpClient()).addConverterFactory(GsonConverterFactory.create()).build()
            .create(CommunityApi::class.java)
    }
    @After fun stop() { server.stop(0) }

    @Test fun feedAndPersonalListsUseBackendPagination() = runBlocking {
        payload = """{"page":1,"pageSize":20,"total":1,"hasMore":false,"records":[{"postId":4,"authorId":9,"title":"穿搭","images":[{"mediaId":1,"previewUrl":"https://example.test/signed"}],"liked":true,"collected":false,"canEdit":false,"commentCount":2}]}"""
        val page = api.feed(1, 20).body()!!.data!!
        assertFalse(page.hasMore)
        assertEquals(4L, page.records.single().postId)
        assertTrue(page.records.single().liked)
        assertFalse(page.records.single().canEdit)
        assertEquals(1L, page.records.single().images.single().mediaId)
        assertEquals("page=1&pageSize=20", requests.take().query)
        api.mine(2, 20)
        assertEquals("/api/me/posts", requests.take().path)
        api.collections(1, 20)
        assertEquals("/api/me/collections", requests.take().path)
    }

    @Test fun publishUsesMediaIdsAndAudienceRatherThanBase64OrLocalUri() = runBlocking {
        payload = """{"postId":6}"""
        api.publish(CommunityWrite("标题", "正文", listOf(12L, 15L), "ALLOW_LIST", listOf(10L)))
        val request = requests.take()
        assertEquals("POST", request.method)
        assertEquals("/api/posts", request.path)
        val json = Gson().fromJson(request.body, com.google.gson.JsonObject::class.java)
        assertEquals(12L, json.getAsJsonArray("mediaIds")[0].asLong)
        assertEquals(10L, json.getAsJsonArray("audienceUserIds")[0].asLong)
        assertFalse(json.has("authorId"))
        assertFalse(json.has("base64"))
        assertFalse(json.has("imgUrls"))
    }

    @Test fun uploadUsesFileMultipartAndInteractionsUseExplicitVerbs() = runBlocking {
        payload = """{"mediaId":12}"""
        api.upload(MultipartBody.Part.createFormData("file", "photo.jpg", byteArrayOf(1, 2, 3).toRequestBody()))
        val upload = requests.take()
        assertEquals("/api/posts/media", upload.path)
        assertTrue(upload.body.contains("name=\"file\""))
        payload = "{}"
        api.like(4); assertEquals("PUT", requests.take().method)
        api.unlike(4); assertEquals("DELETE", requests.take().method)
        api.collect(4); assertEquals("PUT", requests.take().method)
        api.uncollect(4); assertEquals("DELETE", requests.take().method)
        api.delete(5); assertEquals("/api/posts/5", requests.take().path)
    }

    @Test fun repliesAndEditsCarryActualIdsAndVersion() = runBlocking {
        api.comment(4, CommunityCommentWrite("回复", 2))
        val reply = requests.take()
        assertEquals("POST", reply.method)
        assertTrue(reply.body.contains("\"replyToCommentId\":2"))
        payload = """{"records":[],"hasMore":false}"""
        api.comments(4, 2, 1)
        assertEquals("rootId=2&page=1&pageSize=20", requests.take().query)
        api.edit(4, CommunityWrite("新标题", "正文", listOf(1), "PRIVATE", emptyList(), 3))
        val edit = requests.take()
        assertEquals("PUT", edit.method)
        assertTrue(edit.body.contains("\"version\":3"))
    }
}
