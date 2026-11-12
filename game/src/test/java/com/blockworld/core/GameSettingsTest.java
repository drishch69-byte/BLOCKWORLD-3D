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
}
