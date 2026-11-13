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
        assertTrue(entries.stream().allMatch(e -> e.integrityStatus().equals("ЗАГОЛОВОК В ПОРЯДКЕ")));
        assertTrue(entries.stream().allMatch(e -> e.sizeBytes() > 0L));
    }

    @Test
    void marksBrokenHeadersWithoutHidingTheWorldFile() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path worlds = GamePaths.worldsDirectory();
        Files.createDirectories(worlds);
        Files.writeString(worlds.resolve("Broken.bwworld"), "not-a-blockworld-save\\n");

        WorldEntry broken = WorldCatalog.list().stream()
                .filter(entry -> entry.name().equals("Broken"))
                .findFirst().orElseThrow();
        assertEquals("НЕВЕРНЫЙ ЗАГОЛОВОК", broken.integrityStatus());
    }

    @Test
    void favoritesAreSortedBeforeOtherWorlds() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path worlds = GamePaths.worldsDirectory();
        Files.createDirectories(worlds);
        Path first = worlds.resolve("First.bwworld");
        Path second = worlds.resolve("Second.bwworld");
        String data = "BLOCKWORLD_WORLD\\nformat=1\\ngameVersion=0.1.0\\nseed=1\\n--blocks--\\n";
        Files.writeString(first, data);
        Files.writeString(second, data);
        WorldFavorites.setFavorite(second, true);

        assertEquals("Second", WorldCatalog.list().get(0).name());
    }

    @Test
    void createsASeparateLauncherVisiblePathForRecoveredWorlds() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path worlds = GamePaths.worldsDirectory();
        Files.createDirectories(worlds);
        Path original = worlds.resolve("Broken_Save.bwworld");
        Files.writeString(original, "corrupt");

        Path recovered = WorldCatalog.recoveryPathFor(original);

        assertEquals(worlds, recovered.getParent());
        assertTrue(recovered.getFileName().toString().startsWith("Recovered_Broken_Save_"));
        assertTrue(recovered.getFileName().toString().endsWith(".bwworld"));
        assertTrue(!recovered.equals(original));
        assertTrue(!Files.exists(recovered));
        assertEquals("corrupt", Files.readString(original));
    }

    @Test
    void sanitizesNewWorldNames() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        Path path = WorldCatalog.pathForName("My World: Test");
        assertEquals("My_World__Test.bwworld", path.getFileName().toString());
        assertEquals(GamePaths.worldsDirectory(), path.getParent());
    }

    @Test
    void protectsWindowsReservedDeviceNamesAndTrailingDots() throws IOException {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        assertEquals("_CON.bwworld", WorldCatalog.pathForName("CON").getFileName().toString());
        assertEquals("_LPT1.bwworld", WorldCatalog.pathForName("LPT1").getFileName().toString());
        assertEquals("My_World.bwworld", WorldCatalog.pathForName("My World.").getFileName().toString());
    }
}
