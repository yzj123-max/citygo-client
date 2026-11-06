package com.example.citygo.skeleton.data

import com.example.citygo.skeleton.data.remote.attraction.AttractionDto
import com.example.citygo.skeleton.data.repository.AttractionDisplayMapper
import com.example.citygo.skeleton.data.repository.CITY_ALL
import org.junit.Assert.*
import org.junit.Test

class DiscoveryTravelContextTest {
    @Test fun restoredTripAndNewDestinationSelectTheirCities() {
        val binding = DiscoveryTripBinding()
        assertEquals("北京", binding.destinationFor("trip-1", "北京", true))
        assertEquals("重庆", binding.destinationFor("trip-2", "重庆", true))
        assertEquals("重庆", DiscoveryTripBinding().destinationFor("trip-2", "重庆", true))
    }
    @Test fun editingStopsDoesNotOverrideManualCityExploration() {
        val binding = DiscoveryTripBinding()
        binding.destinationFor("trip-1", "北京", true)
        assertNull(binding.destinationFor("trip-1", "北京市", true))
        assertEquals("北京", binding.destinationFor("trip-2", "北京", true))
    }
    @Test fun emptyDraftDoesNotPretendToBeAGeneratedDestination() {
        val binding = DiscoveryTripBinding()
        assertNull(binding.destinationFor(null, "长沙", false))
        assertNull(binding.destinationFor(null, CITY_ALL, true))
        assertEquals("成都", binding.destinationFor("trip-1", "成都", true))
        assertNull(binding.destinationFor(null, "成都", false))
        assertEquals("成都", binding.destinationFor("trip-1", "成都", true))
    }
    private fun spot(id: Long, city: String, level: String, image: String = "https://example.test/$id.jpg") =
        AttractionDisplayMapper.spot(AttractionDto(attractionId = id, city = city, name = "景点$id", level = level, coverUrl = image))

    @Test fun heroUsesAnActualPhotoFromTheDestinationAndPrefersLandmarks() {
        val otherCity = spot(1, "西安市", "5A")
        val cityPark = spot(2, "北京市", "4A")
        val landmark = spot(3, "北京市", "5A")
        assertEquals("3", discoveryCityHero("北京", listOf(otherCity, cityPark, landmark))?.spotId)
    }
    @Test fun missingDestinationPhotosNeverFallBackToAnotherCity() {
        assertNull(discoveryCityHero("北京", listOf(spot(1, "西安", "5A"), spot(2, "北京", "5A", ""))))
        assertEquals("1", discoveryCityHero(CITY_ALL, listOf(spot(1, "西安", "5A")))?.spotId)
    }
}
