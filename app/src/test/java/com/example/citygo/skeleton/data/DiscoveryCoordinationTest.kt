package com.example.citygo.skeleton.data

import com.example.citygo.skeleton.data.remote.attraction.AttractionApi
import com.example.citygo.skeleton.data.repository.BackendSpotRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Exercises the actual ViewModel and Retrofit mapping without Android, credentials or live writes. */
class DiscoveryCoordinationTest {
    private class Fixture(val curated: Boolean = true, token: String? = "test-session") {
        val tokens = MutableStateFlow(token)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val detailsId = request.url.encodedPath.substringAfterLast('/').toIntOrNull()
            val city = request.url.queryParameter("city") ?: if ((detailsId ?: 0) >= 20) "重庆" else "北京"
            val filtered = request.url.queryParameter("categoryCode") != null || detailsId in listOf(11, 21)
            val id = (if (city.startsWith("重庆")) 20 else 10) + if (filtered) 1 else 0
            val name = if (filtered) "城市寺庙" else "城市博物馆"
            val image = "https://example.test/$city/${if (filtered) "filtered" else "landmark"}.jpg"
            val description = "这是经过核实的景区资料与旅行介绍。".repeat(12)
            val detailCity = if ((detailsId ?: 0) >= 20) "重庆市" else "北京市"
            val isList = request.url.encodedPath == "/api/attractions"
            val json = if (isList && !curated) """{"code":200,"data":{"records":[],"total":0,"hasMore":false}}"""
            else if (isList || request.url.encodedPath.endsWith("/search")) {
                """{"code":200,"data":{"records":[{"attractionId":$id,"name":"$name","city":"${city}市","categoryCode":"110200","level":"5A","coverUrl":"$image"}],"total":1,"hasMore":false}}"""
            } else """{"code":200,"data":{"attractionId":$id,"name":"$name","city":"$detailCity","level":"5A","description":"$description","descriptionSourceUrl":"https://example.test/source","descriptionVerifiedAt":"2026-10-27","coverUrl":"$image","images":[{"url":"$image"}],"longitude":116.4,"latitude":39.9,"coordSystem":"GCJ02","collectedInfo":{"ticketPrice":"收费","openTime":"09:00—17:00"}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        private val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(AttractionApi::class.java)
        val vm = RecommendViewModel(BackendSpotRepository { api }, tokens, scope)
        suspend fun ready(city: String): RecommendUiState = withTimeout(8000) {
            vm.uiState.first { it.currentCity == city && !it.loading && it.spots.isNotEmpty() && it.heroPhotoUrl != null }
        }
        fun close() { scope.cancel(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun activeTripAlignsDestinationFeedAndHeroAndKeepsHeroWhenFiltering() = runBlocking {
        val f = Fixture()
        try {
            f.vm.syncActiveTrip("beijing-trip", "北京", true)
            val initial = f.ready("北京")
            assertTrue(initial.spots.all { it.city == "北京市" })
            assertTrue(initial.heroPhotoUrl!!.contains("北京"))
            f.vm.setFilters("110200", null, null)
            val filtered = withTimeout(8000) { f.vm.uiState.first { !it.loading && it.categoryCode != null && it.spots.isNotEmpty() } }
            assertEquals(initial.heroPhotoUrl, filtered.heroPhotoUrl)
            assertTrue(filtered.spots.single().coverImg.contains("filtered"))
        } finally { f.close() }
    }
    @Test fun manualExplorationSurvivesStopEditsButANewTripResetsDestinationAndFilters() = runBlocking {
        val f = Fixture()
        try {
            f.vm.syncActiveTrip("trip-1", "北京", true); f.ready("北京")
            f.vm.selectCity("重庆"); f.ready("重庆")
            f.vm.syncActiveTrip("trip-1", "北京", true)
            assertEquals("重庆", f.vm.uiState.value.currentCity)
            f.vm.setFilters(null, "FREE", null)
            f.vm.syncActiveTrip("trip-2", "北京", true)
            val restored = f.ready("北京")
            assertNull(restored.ticketStatus)
            assertTrue(restored.spots.all { it.city == "北京市" })
        } finally { f.close() }
    }
    @Test fun delayedSessionHydrationDoesNotResetRestoredItineraryToNationwide() = runBlocking {
        val f = Fixture(token = null)
        try {
            f.vm.syncActiveTrip("restored-trip", "北京", true)
            f.tokens.value = "restored-session"
            val restored = f.ready("北京")
            assertTrue(restored.spots.all { it.city == "北京市" })
            assertTrue(restored.heroPhotoUrl!!.contains("北京"))
        } finally { f.close() }
    }
    @Test fun citiesWithoutCuratedContentGetSameCitySearchResultsAndScopedSearch() = runBlocking {
        val f = Fixture(curated = false)
        try {
            f.vm.syncActiveTrip("trip", "重庆", true)
            val state = f.ready("重庆")
            assertEquals("CITY_SEARCH", state.source)
            assertTrue(state.spots.all { it.city == "重庆市" })
            f.vm.prepareSearch("重庆")
            f.vm.submitSearch("博物馆")
            val results = withTimeout(8000) { f.vm.searchState.first { it.searched && !it.loading } }
            assertEquals("重庆", results.city)
            assertTrue(results.results.isNotEmpty())
            assertTrue(results.results.all { it.city == "重庆市" })
        } finally { f.close() }
    }
}
