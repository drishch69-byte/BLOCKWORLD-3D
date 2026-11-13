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
    void recommendsConservativePresetForLowMemoryComputers() {
        GameSettings settings = GameSettings.recommended(2L * 1024L * 1024L * 1024L, 2);
        assertEquals(1024, settings.width());
        assertEquals(3, settings.renderDistance());
        assertEquals(1, settings.vegetationDensity());
        assertEquals(1, settings.chunkBuildsPerFrame());
    }

    @Test
    void recommendsBalancedPresetForMidRangeComputers() {
        GameSettings settings = GameSettings.recommended(4L * 1024L * 1024L * 1024L, 6);
        assertEquals(1280, settings.width());
        assertEquals(4, settings.renderDistance());
        assertEquals(2, settings.vegetationDensity());
        assertEquals(2, settings.chunkBuildsPerFrame());
    }

    @Test
    void recommendationsDoNotOverstateWeakCpuCapabilities() {
        GameSettings settings = GameSettings.recommended(16L * 1024L * 1024L * 1024L, 4);
        assertEquals(1280, settings.width());
        assertEquals(2, settings.chunkBuildsPerFrame());
    }

    @Test
    void acceptsCustomPerformanceOptions() {
        GameSettings settings = new GameSettings(1280, 720, true, false, 4, 80, 3, 6);
        assertEquals(3, settings.vegetationDensity());
        assertEquals(6, settings.chunkBuildsPerFrame());
    }
}
