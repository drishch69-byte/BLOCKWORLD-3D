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
    void addingVeryLargePlaytimeSaturatesInsteadOfOverflowing() throws Exception {
        Path file = temp.resolve("large-profile.properties");
        PlayerProfileStore.save(file, new PlayerProfileStore.Profile("Игрок", 0, 1,
                999_999_999_999L));

        PlayerProfileStore.Profile updated = PlayerProfileStore.addPlayTimeSeconds(file, Long.MAX_VALUE);

        assertEquals(1_000_000_000_000L, updated.playTimeSeconds());
        assertEquals(1L, updated.sessionsPlayed());
    }

    @Test
    void sessionAndPlaytimeUpdatesPreserveIdentity() throws Exception {
        Path file = temp.resolve("profile.properties");
        PlayerProfileStore.save(file, new PlayerProfileStore.Profile("Кристалл", 3, 4, 90));

        PlayerProfileStore.Profile afterStart = PlayerProfileStore.recordSessionStart(file);
        assertEquals(5L, afterStart.sessionsPlayed());
        assertEquals(90L, afterStart.playTimeSeconds());

        PlayerProfileStore.Profile afterTime = PlayerProfileStore.addPlayTimeSeconds(file, 360);
        assertEquals("Кристалл", afterTime.displayName());
        assertEquals(3, afterTime.appearanceIndex());
        assertEquals(5L, afterTime.sessionsPlayed());
        assertEquals(450L, afterTime.playTimeSeconds());
    }
}
