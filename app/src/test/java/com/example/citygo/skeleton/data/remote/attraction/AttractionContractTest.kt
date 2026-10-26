package com.example.citygo.skeleton.data.remote.attraction

import com.example.citygo.skeleton.data.repository.*
import com.example.citygo.skeleton.recommend.AttractionLocationFix
import com.example.citygo.skeleton.recommend.isAttractionFixFresh
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.LinkedBlockingQueue

class AttractionContractTest {
    private lateinit var server: HttpServer
    private lateinit var repository: BackendSpotRepository
    private data class Request(val method: String, val path: String, val query: String, val body: String)
    private val requests = LinkedBlockingQueue<Request>()
    private var respond: (String) -> Pair<Int, String> = { 200 to """{"code":200,"data":{"page":1,"records":[],"hasMore":false,"total":0}}""" }
    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests.add(Request(exchange.requestMethod, path, URLDecoder.decode(exchange.requestURI.rawQuery.orEmpty(), "UTF-8"), exchange.requestBody.bufferedReader().readText()))
            val (status, body) = respond(path)
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
            .client(OkHttpClient()).addConverterFactory(GsonConverterFactory.create()).build().create(AttractionApi::class.java)
        repository = BackendSpotRepository { api }
    }
    @After fun stop() { server.stop(0) }

    @Test fun searchAndNearbyUseBackendPaginationAndGcjCoordinates() = runBlocking {
        respond = { 200 to """{"code":200,"data":{"page":2,"returnedCount":1,"hasMore":true,"records":[{"amapPoiId":"B03530QO2H","name":"慈云寺","longitude":106.687651,"latitude":26.330716,"coordSystem":"GCJ02","distanceMeters":92}]}}""" }
        val page = repository.searchPage("青岩古镇", 2)
        val search = requests.take()
        assertEquals("GET", search.method)
        assertEquals("/api/attractions/search", search.path)
        assertTrue(search.query.contains("keyword=青岩古镇"))
        assertTrue(search.query.contains("page=2"))
        assertTrue(page.hasMore)
        assertNull(page.total)
        assertEquals("poi_B03530QO2H", page.spots.single().spotId)
        respond = { 200 to """{"code":200,"data":{"records":[],"total":0,"hasMore":false}}""" }
        val nearby = repository.nearby(106.686834, 26.331095)
        assertEquals("/api/attractions", requests.take().path)
        assertTrue(nearby.spots.isEmpty())
        assertEquals("NEARBY", nearby.source)
    }
    @Test fun remoteResultResolvesBeforeLoadingFreshLocalDetail() = runBlocking {
        respond = { path -> 200 to if (path.endsWith("resolve"))
            """{"code":200,"data":{"attractionId":359,"created":true}}"""
        else """{"code":200,"data":{"attractionId":359,"name":"慈云寺","images":[],"policies":[{"policyType":"PET","policyValue":"UNKNOWN","structuredData":null}],"canNavigate":false}}""" }
        val detail = repository.getSpotById("poi_B03530QO2H")
        val resolve = requests.take()
        assertEquals("POST", resolve.method)
        assertEquals("/api/attractions/resolve", resolve.path)
        assertTrue(resolve.body.contains("B03530QO2H"))
        assertEquals("/api/attractions/359", requests.take().path)
        assertEquals("359", detail.spotId)
        assertFalse(detail.coordinateAvailable)
        assertFalse(detail.canNavigate)
    }
    @Test fun citySuffixAndPolicyFiltersRemainOnLocalListing() = runBlocking {
        repository.recommendations("贵阳", ticket = "FREE", pet = "ALLOWED")
        val first = requests.take()
        val second = requests.take()
        assertEquals("/api/attractions", second.path)
        assertTrue(first.query.contains("city=贵阳"))
        assertTrue(second.query.contains("city=贵阳市"))
        assertTrue(second.query.contains("ticketStatus=FREE"))
        assertTrue(second.query.contains("petPolicy=ALLOWED"))
        assertTrue(requests.isEmpty())
    }
    @Test fun navigationUsesModeSpecificEntranceAndPreservesBackendErrors() = runBlocking {
        respond = { 200 to """{"code":200,"data":{"attractionId":10,"travelMode":"DRIVING","entranceId":7,"longitude":106.68,"latitude":26.32,"coordSystem":"GCJ02","destinationName":"停车场","fallback":false}}""" }
        val target = repository.navigation("10", "DRIVING")
        val request = requests.take()
        assertEquals("/api/attractions/10/navigation-target", request.path)
        assertEquals("travelMode=DRIVING", request.query)
        assertEquals(106.68, target.longitude!!, 0.000001)
        assertEquals("停车场", target.destinationName)
        try { repository.navigation("10", "WALKING"); fail("must reject wrong mode") } catch (expected: IllegalArgumentException) { }
        respond = { 422 to """{"code":422,"msg":"景点缺少可用导航坐标","data":null}""" }
        try { repository.navigation("10", "DRIVING"); fail("must preserve error") }
        catch (expected: AttractionRequestException) { assertEquals(422, expected.code); assertEquals("景点缺少可用导航坐标", expected.message) }
    }
    @Test fun unknownAndUnverifiedPoliciesNeverBecomeFreeOrPetAllowed() {
        val base = AttractionPolicyDto(policyType = "TICKET", policyValue = "FREE")
        assertEquals("UNKNOWN", AttractionDisplayMapper.policyValue(base))
        val verified = base.copy(verifiedAt = "2026-10-25T12:00:00Z", temporalStatus = "CURRENT")
        assertEquals("FREE", AttractionDisplayMapper.policyValue(verified))
        assertEquals("UNKNOWN", AttractionDisplayMapper.policyValue(verified.copy(temporalStatus = "EXPIRED")))
        assertEquals("UNKNOWN", AttractionDisplayMapper.policyValue(verified.copy(temporalStatus = "UPCOMING")))
        assertEquals("UNKNOWN", AttractionDisplayMapper.policyValue(verified.copy(reviewRequired = true)))
        val detail = AttractionDisplayMapper.spot(AttractionDto(attractionId = 10, policies = listOf(base), images = emptyList()))
        assertEquals("UNKNOWN", detail.ticketStatus)
        assertEquals("UNKNOWN", detail.petPolicy)
        assertEquals(0, detail.reviewCount)
        assertTrue(detail.imgUrls.isEmpty())
        assertFalse(detail.canNavigate)
        assertFalse(AttractionDisplayMapper.validCoordinates(0.0, 0.0, "GCJ02"))
        assertFalse(AttractionDisplayMapper.validCoordinates(106.68, 26.33, "WGS84"))
    }
    @Test fun expiredOrMissingPhoneFixCannotStartNavigation() {
        val fix = AttractionLocationFix(26.33, 106.68, 1000, 15f)
        assertTrue(isAttractionFixFresh(fix, 31_000))
        assertFalse(isAttractionFixFresh(fix, 31_001))
        assertFalse(isAttractionFixFresh(fix, 999))
        assertFalse(isAttractionFixFresh(null, 1000))
        assertFalse(isAttractionFixFresh(fix.copy(latitude = Double.NaN), 1001))
    }
    @Test fun nullableAndObjectPolicyExtensionsBothDecodeThroughRetrofit() = runBlocking {
        respond = { 200 to """{"code":200,"data":{"attractionId":10,"name":"青岩古镇景区","policies":[{"policyType":"PET","policyValue":"UNKNOWN","structuredData":null},{"policyType":"TICKET","policyValue":"PAID","structuredData":{"price":60}}]}}""" }
        val detail = repository.getSpotById("10")
        assertTrue(detail.policies[0].structuredData == null || detail.policies[0].structuredData!!.isJsonNull)
        assertEquals(60, detail.policies[1].structuredData!!.asJsonObject.get("price").asInt)
        assertEquals("青岩古镇景区", detail.name)
    }
    @Test fun recommendationsExcludeCampusAndInternalPoisAndRankRatedDestinationsFirst() {
        val school = AttractionDto(attractionId = 1, name = "大学内部广场", categoryCode = "110000")
        val interior = AttractionDto(attractionId = 2, name = "青岩古镇-慈云寺", categoryCode = "110205")
        val national = AttractionDto(attractionId = 3, name = "青岩古镇景区", level = "5A")
        val museum = AttractionDto(attractionId = 4, name = "城市博物馆")
        val park = AttractionDto(attractionId = 5, name = "湿地公园")
        val records = AttractionRecommendationPolicy.select(listOf(school, museum, interior, park, national))
        assertEquals(listOf(3L, 4L, 5L), records.map { it.attractionId })
        assertFalse(AttractionRecommendationPolicy.eligible(AttractionDto(name = "学校风景区", level = "5A")))
    }
    @Test fun missingImageUsesOnlySameCitySamePlaceAndNeverChangesSignedUrls() = runBlocking {
        respond = { 200 to """{"code":200,"data":{"attractionId":10,"name":"青岩古镇景区","city":"贵阳市","images":[]}}""" }
        val detail = repository.getSpotById("10")
        requests.take()
        respond = { 200 to """{"code":200,"data":{"records":[{"name":"甲秀楼","city":"贵阳市","coverUrl":"https://bad.test/wrong.jpg"},{"name":"青岩古镇","city":"贵阳市","coverUrl":"https://good.test/qingyan.jpg"}],"hasMore":false}}""" }
        val enriched = repository.findPhotos(detail)
        assertEquals("https://good.test/qingyan.jpg", enriched.coverImg)
        assertEquals("青岩古镇景区", enriched.name)
        assertEquals("扎龙", AttractionRecommendationPolicy.photoKeyword("扎龙生态旅游区"))
        assertEquals("虎头", AttractionRecommendationPolicy.photoKeyword("虎头旅游景区"))
        assertFalse(AttractionRecommendationPolicy.samePlace(detail, AttractionDto(name = "青岩古镇", city = "其他市")))
        val signed = "http://bucket.test/image?signature=xyz"
        assertEquals(listOf(signed), com.example.citygo.skeleton.recommend.attractionImageCandidates(listOf(signed)))
        assertEquals(listOf("https://store.is.autonavi.com/showpic/abc", "http://store.is.autonavi.com/showpic/abc"),
            com.example.citygo.skeleton.recommend.attractionImageCandidates(listOf("http://store.is.autonavi.com/showpic/abc")))
    }
    @Test fun curatedFeedAndNearbyRequireActualLongDescriptionPicturesAndPolicies() = runBlocking {
        val text = "这是一段经核对的景点历史与看点介绍。".repeat(12)
        val detail = """{"code":200,"data":{"attractionId":10,"name":"青岩古镇景区","city":"贵阳市","description":"$text","descriptionSourceUrl":"https://example.test/source","descriptionVerifiedAt":"2026-10-25T12:00:00Z","longitude":106.686834,"latitude":26.331095,"coordSystem":"GCJ02","images":[{"url":"https://example.test/real.jpg"}],"policies":[{"policyType":"TICKET","policyValue":"PAID","content":"门票10元","verifiedAt":"2026-10-25T12:00:00Z"},{"policyType":"OPENING_HOURS","policyValue":"SCHEDULED","content":"08:30—17:00","verifiedAt":"2026-10-25T12:00:00Z"}]}}"""
        respond = { path -> 200 to if (path == "/api/attractions/10") detail else """{"code":200,"data":{"records":[{"attractionId":10,"name":"青岩古镇景区","level":"5A","coverUrl":"https://example.test/real.jpg"},{"attractionId":11,"name":"其他景区","level":"5A"}],"total":2,"hasMore":false}}""" }
        val ready = repository.recommendations(null)
        assertEquals(listOf("10"), ready.spots.map { it.spotId })
        assertTrue(AttractionRecommendationPolicy.complete(ready.spots.single()))
        assertFalse(AttractionRecommendationPolicy.complete(ready.spots.single().copy(description = "一句话")))
        assertFalse(AttractionRecommendationPolicy.complete(ready.spots.single().copy(coverImg = "")))
        val nearby = repository.nearby(106.686834, 26.331095)
        assertEquals(0L, nearby.spots.single().distanceMeters)
        assertTrue(repository.nearby(116.4, 39.9).spots.isEmpty())
    }
    @Test fun originalDatabaseFieldsDecodeWithoutBecomingVerifiedPolicies() = runBlocking {
        respond = { 200 to """{"code":200,"data":{"attractionId":9,"name":"镇远古城旅游景区","level":"5A","collectedInfo":{"ticketPrice":"免费入城，青龙洞60元","discountPolicy":"子景点各自优待","openTime":"全天开放","feature":"千年水城","petFriendlyLevel":"可直接入园","petFriendlinessScore":"5","petPolicy":"游客记录可牵绳携宠","petPolicySource":"携宠游记","recordedReviewTime":"2026年10月"},"policies":[{"policyType":"PET","policyValue":"UNKNOWN","structuredData":null}]}}""" }
        val spot = repository.getSpotById("9")
        assertEquals("5A", spot.level)
        assertEquals("免费入城，青龙洞60元", spot.collectedInfo!!.ticketPrice)
        assertEquals("5", spot.collectedInfo!!.petFriendlinessScore)
        assertEquals("2026年10月", spot.collectedInfo!!.recordedReviewTime)
        assertEquals("携宠游记", spot.collectedInfo!!.petPolicySource)
        assertEquals("UNKNOWN", spot.petPolicy)
    }
}





