package com.blockworld.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameSettingsTest {
    @Test
    void clampsUnsupportedValuesToSafeDefaults() {
        GameSettings settings = new GameSettings(3000, 2000, true, true, 99, 120);
        assertEquals(1024, settings.width());
        assertEquals(640, settings.height());
        assertEquals(3, settings.renderDistance());
        assertEquals(100, settings.fov());
    }

    @Test
    void acceptsSupportedLowEndPreset() {
        GameSettings settings = new GameSettings(1024, 640, false, false, 3, 68);
        assertEquals(GameSettings.DEFAULT, settings);
    }

    @Test
    void clampsInvalidPerformanceOptionsAndPreservesLegacyConstructor() {
        GameSettings legacy = new GameSettings(1024, 640, false, false, 3, 68);
        assertEquals(2, legacy.vegetationDensity());
        assertEquals(2, legacy.chunkBuildsPerFrame());

        GameSettings invalid = new GameSettings(1024, 640, false, false, 3, 68, 99, 0);
        assertEquals(2, invalid.vegetationDensity());
        assertEquals(2, invalid.chunkBuildsPerFrame());
    }

    @Test
    void acceptsCustomPerformanceOptions() {
        GameSettings settings = new GameSettings(1280, 720, true, false, 4, 80, 3, 6);
        assertEquals(3, settings.vegetationDensity());
        assertEquals(6, settings.chunkBuildsPerFrame());
    }
}
