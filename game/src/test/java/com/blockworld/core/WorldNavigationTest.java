package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldNavigationTest {
    @Test
    void generatesTheSameFourSitesForTheSameSeed() {
        var first = WorldNavigation.generateSites(123456L, 128);
        var second = WorldNavigation.generateSites(123456L, 128);
        assertEquals(first, second);
        assertEquals(4, first.size());
        assertEquals(4L, first.stream().map(WorldNavigation.Landmark::type).distinct().count());
        for (var site : first) {
            assertTrue(Math.abs(site.x()) < 128);
            assertTrue(Math.abs(site.z()) < 128);
        }
    }

    @Test
    void compassAndLandmarkDirectionsUsePlayerForwardAxes() {
        assertEquals("N", WorldNavigation.compassHeading(0f));
        assertEquals("W", WorldNavigation.compassHeading((float) (Math.PI / 2)));
        assertEquals("E", WorldNavigation.compassHeading((float) (-Math.PI / 2)));
        assertEquals("S", WorldNavigation.compassHeading((float) Math.PI));
        assertEquals("N", WorldNavigation.directionTo(0f, 0f, 0f, -20f));
        assertEquals("E", WorldNavigation.directionTo(0f, 0f, 20f, 0f));
    }

    @Test
    void mapShowsPlayerMarkersAndNearestSite() {
        var markers = WorldNavigation.allMarkers(7L, 128);
        String map = WorldNavigation.renderMap(0f, 0f, 0f, markers);
        assertTrue(map.contains("КОМПАС: N"));
        assertTrue(map.contains("P — ты"));
        assertTrue(map.contains("Метки: T башня"));
        assertTrue(map.contains("Ближайшая:"));
        assertTrue(map.contains("P"));
    }

    @Test
    void explorationProfileIsExplicitAndDoesNotEnableOldProfiles() {
        assertFalse(WorldNavigation.supportsExploration("1.1.0"));
        assertTrue(WorldNavigation.supportsExploration("1.2.0"));
        assertTrue(WorldNavigation.supportsExploration("1.3.0"));
        assertFalse(WorldNavigation.supportsExploration("invalid"));
    }
}