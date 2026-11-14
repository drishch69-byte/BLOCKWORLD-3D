package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameSettingsProfileTest {
    @TempDir
    Path tempDir;

    @Test
    void profileSettingsOverrideLegacySettingsWithoutChangingOtherProfiles() throws IOException {
        Path legacy = tempDir.resolve("settings.properties");
        Path profileA = tempDir.resolve("profiles/1.9.8/settings.properties");
        Path profileB = tempDir.resolve("profiles/1.9.9/settings.properties");

        GameSettings shared = new GameSettings(1280, 720, false, false, 4, 72, 2, 2, 60);
        GameSettings.saveTo(legacy, shared);
        assertEquals(shared, GameSettings.load(profileA, legacy));
        assertEquals(shared, GameSettings.load(profileB, legacy));

        GameSettings settingsA = new GameSettings(1920, 1080, true, true, 8, 90, 3, 6, 144);
        GameSettings.saveTo(profileA, settingsA);
        assertEquals(settingsA, GameSettings.load(profileA, legacy));
        assertEquals(shared, GameSettings.load(profileB, legacy));

        GameSettings settingsB = new GameSettings(1600, 900, false, true, 6, 80, 2, 4, 90);
        GameSettings.saveTo(profileB, settingsB);
        assertEquals(settingsA, GameSettings.load(profileA, legacy));
        assertEquals(settingsB, GameSettings.load(profileB, legacy));
    }

    @Test
    void missingProfileAndLegacyFilesUseDefaults() {
        assertEquals(GameSettings.DEFAULT,
                GameSettings.load(tempDir.resolve("missing-profile.properties"),
                        tempDir.resolve("missing-legacy.properties")));
    }
}
