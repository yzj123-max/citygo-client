package com.example.citygo.skeleton.data

import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.data.repository.CITY_ALL

/** Only a newly active itinerary changes discovery; editing stops does not undo manual exploration. */
class DiscoveryTripBinding {
    private var previous: Pair<String?, String>? = null

    fun destinationFor(tripId: String?, city: String, hasPlaces: Boolean): String? {
        val destination = city.trim().takeIf { hasPlaces && it.isNotEmpty() && it != CITY_ALL }
        if (destination == null) { previous = null; return null }
        val context = tripId to normalizeDiscoveryCity(destination)
        if (previous == context) return null
        previous = context
        return destination
    }
}

fun normalizeDiscoveryCity(city: String): String = city.trim().removeSuffix("市")

/** The city hero is chosen from unfiltered city recommendations, never from nearby/filter results. */
fun discoveryCityHero(city: String, spots: List<ScenicSpot>): ScenicSpot? = spots
    .filter { (city == CITY_ALL || normalizeDiscoveryCity(it.city) == normalizeDiscoveryCity(city)) &&
        it.coverImg.isNotBlank() }
    .sortedWith(compareByDescending<ScenicSpot> {
        when {
            it.level.contains("5A", ignoreCase = true) -> 5
            it.level.contains("4A", ignoreCase = true) -> 4
            else -> 0
        }
    }.thenByDescending { it.score }.thenByDescending { it.reviewCount })
    .firstOrNull()
