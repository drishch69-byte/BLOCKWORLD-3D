package com.blockworld.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GamePathsProfileTest {
    @Test
    void eachVersionHasSeparateSettingsAndModDirectories() {
        Path root = Path.of("test-game-home");
        Path oldProfile = GamePaths.profileDirectory(root, "1.9.8");
        Path newProfile = GamePaths.profileDirectory(root, "1.9.9");

        assertNotEquals(oldProfile, newProfile);
        assertEquals(oldProfile.resolve("config/settings.properties"),
                GamePaths.profileDirectory(root, "1.9.8").resolve("config/settings.properties"));
        assertNotEquals(oldProfile.resolve("mods"), newProfile.resolve("mods"));
    }

    @Test
    void rejectsUnknownAndTraversalProfileIds() {
        Path root = Path.of("test-game-home");
        assertThrows(IllegalArgumentException.class, () -> GamePaths.profileDirectory(root, "../outside"));
        assertThrows(IllegalArgumentException.class, () -> GamePaths.profileDirectory(root, "9.9.9"));
    }
}
