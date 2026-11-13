package com.blockworld.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFavoritesTest {
    @TempDir Path tempDir;
    private String originalHome;

    @AfterEach
    void restoreHome() {
        if (originalHome == null) System.clearProperty("user.home");
        else System.setProperty("user.home", originalHome);
    }

    @Test
    void storesAndRemovesFavoriteWorldsByFilename() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path world = GamePaths.worldsDirectory().resolve("My_World.bwworld");

        assertFalse(WorldFavorites.isFavorite(world));
        WorldFavorites.setFavorite(world, true);
        assertTrue(WorldFavorites.isFavorite(world));
        assertTrue(WorldFavorites.list().contains("My_World.bwworld"));

        WorldFavorites.setFavorite(world, false);
        assertFalse(WorldFavorites.isFavorite(world));

        Path ukrainianWorld = GamePaths.worldsDirectory().resolve("Мій_світ.bwworld");
        WorldFavorites.setFavorite(ukrainianWorld, true);
        assertTrue(WorldFavorites.isFavorite(ukrainianWorld));
    }

    @Test
    void ignoresUnsafeEntriesInFavoritesFile() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path file = GamePaths.favoritesFile();
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "../outside.bwworld\nGood.bwworld\nsub/evil.bwworld\n");

        assertTrue(WorldFavorites.list().contains("Good.bwworld"));
        assertFalse(WorldFavorites.list().contains("../outside.bwworld"));
    }
}
