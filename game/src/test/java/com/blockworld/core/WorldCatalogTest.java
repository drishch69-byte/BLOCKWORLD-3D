package com.blockworld.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldCatalogTest {
    @TempDir
    Path tempDir;

    private String originalHome;

    @AfterEach
    void restoreHome() {
        if (originalHome == null) System.clearProperty("user.home");
        else System.setProperty("user.home", originalHome);
    }

    @Test
    void listsAllNamedWorldsAndIgnoresBackupFiles() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path worlds = GamePaths.worldsDirectory();
        Files.createDirectories(worlds);
        Files.writeString(worlds.resolve("First.bwworld"),
                "BLOCKWORLD_WORLD\nformat=1\ngameVersion=0.1.0\nseed=1\n--blocks--\n");
        Files.writeString(worlds.resolve("Second.bwworld"),
                "BLOCKWORLD_WORLD\nformat=1\ngameVersion=0.4.0\nseed=2\n--blocks--\n");
        Files.writeString(worlds.resolve("First.bwworld.backup-20261112"), "backup");

        List<WorldEntry> entries = WorldCatalog.list();
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.name().equals("First") && e.lastVersion().equals("0.1.0")));
        assertTrue(entries.stream().anyMatch(e -> e.name().equals("Second") && e.lastVersion().equals("0.4.0")));
    }

    @Test
    void sanitizesNewWorldNames() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path path = WorldCatalog.pathForName("My World: Test");
        assertEquals("My_World__Test.bwworld", path.getFileName().toString());
        assertEquals(GamePaths.worldsDirectory(), path.getParent());
    }
}
