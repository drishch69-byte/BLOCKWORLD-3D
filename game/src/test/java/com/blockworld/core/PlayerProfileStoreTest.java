package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProfileStoreTest {
    @TempDir Path temp;

    @Test
    void missingProfileUsesDefaults() throws Exception {
        PlayerProfileStore.Profile profile = PlayerProfileStore.load(temp.resolve("missing.properties"));
        assertEquals("Игрок", profile.displayName());
        assertEquals(0, profile.appearanceIndex());
        assertEquals(0L, profile.sessionsPlayed());
        assertEquals(0L, profile.playTimeSeconds());
    }

    @Test
    void savesUnicodeNameAndAppearance() throws Exception {
        Path file = temp.resolve("config").resolve("profile.properties");
        PlayerProfileStore.Profile saved = new PlayerProfileStore.Profile("Лесной_строитель", 2, 7, 1234);
        PlayerProfileStore.save(file, saved);

        PlayerProfileStore.Profile loaded = PlayerProfileStore.load(file);
        assertEquals("Лесной_строитель", loaded.displayName());
        assertEquals(2, loaded.appearanceIndex());
        assertEquals(7L, loaded.sessionsPlayed());
        assertEquals(1234L, loaded.playTimeSeconds());
        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.exists(file.resolveSibling("profile.properties.tmp")));
    }

    @Test
    void invalidValuesAreBoundedAndEmptyNameGetsFallback() {
        PlayerProfileStore.Profile profile = new PlayerProfileStore.Profile("   ", 99, -5, -20);
        assertEquals("Игрок", profile.displayName());
        assertEquals(3, profile.appearanceIndex());
        assertEquals(0L, profile.sessionsPlayed());
        assertEquals(0L, profile.playTimeSeconds());
    }

    @Test
    void sessionAndPlaytimeUpdatesPreserveIdentity() throws Exception {
        Path file = temp.resolve("profile.properties");
        PlayerProfileStore.save(file, new PlayerProfileStore.Profile("Кристалл", 3, 4, 90));
        PlayerProfileStore.Profile current = PlayerProfileStore.load(file);
        PlayerProfileStore.save(file, current.withAppearance(1));

        PlayerProfileStore.Profile afterStart = new PlayerProfileStore.Profile(
                "Кристалл", 1, 5, 90);
        PlayerProfileStore.save(file, afterStart);
        PlayerProfileStore.Profile afterTime = new PlayerProfileStore.Profile(
                "Кристалл", 1, 5, 450);
        PlayerProfileStore.save(file, afterTime);

        PlayerProfileStore.Profile loaded = PlayerProfileStore.load(file);
        assertEquals("Кристалл", loaded.displayName());
        assertEquals(1, loaded.appearanceIndex());
        assertEquals(5L, loaded.sessionsPlayed());
        assertEquals(450L, loaded.playTimeSeconds());
    }
}
