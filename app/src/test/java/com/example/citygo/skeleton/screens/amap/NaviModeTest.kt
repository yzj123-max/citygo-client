package com.example.citygo.skeleton.screens.amap

import org.junit.Assert.assertEquals
import org.junit.Test

class NaviModeTest {
    @Test fun navigationWithoutModeRequiresRealLocation() {
        assertEquals(NaviMode.REAL_GPS, NaviMode.fromWire(null))
        assertEquals(NaviMode.REAL_GPS, NaviMode.fromWire(""))
        assertEquals(NaviMode.REAL_GPS, NaviMode.fromWire("unrecognized"))
    }

    @Test fun simulationRequiresAnExplicitRequest() {
        assertEquals(NaviMode.SIMULATION, NaviMode.fromWire("simulation"))
        assertEquals(NaviMode.REAL_GPS, NaviMode.fromWire("real_gps"))
    }
    @Test fun everyCurrentLocationEntryUsesGps() {
        assertEquals(NaviMode.REAL_GPS, NaviMode.forOrigin(true))
    }

    @Test fun customPointToPointPreviewUsesSimulation() {
        assertEquals(NaviMode.SIMULATION, NaviMode.forOrigin(false))
    }
}
