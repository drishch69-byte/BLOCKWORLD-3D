package com.blockworld.ui;

import com.blockworld.core.WorldEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherWorldFilterTest {
    private static final WorldEntry FOREST = new WorldEntry("Тихий лес",
            Path.of("worlds", "forest.bwworld"), "1.9.9", Instant.EPOCH, 2048, "ЗАГОЛОВОК В ПОРЯДКЕ");
    private static final WorldEntry CAVES = new WorldEntry("Crystal Caves",
            Path.of("worlds", "caves.bwworld"), "1.9.8", Instant.EPOCH, 4096, "ТРЕБУЕТ ПРОВЕРКИ");
    private static final WorldEntry BUILDER = new WorldEntry("Builder",
            Path.of("worlds", "builder.bwworld"), "0.5.0", Instant.EPOCH, 1024, "ЗАГОЛОВОК В ПОРЯДКЕ");

    @Test
    void searchIsCaseInsensitiveAndMatchesNameVersionAndIntegrity() {
        assertEquals(List.of(FOREST), LauncherWorldFilter.filter(
                List.of(FOREST, CAVES, BUILDER), "ТИХИЙ 1.9.9", Set.of(), false));
        assertEquals(List.of(CAVES), LauncherWorldFilter.filter(
                List.of(FOREST, CAVES, BUILDER), "проверки", Set.of(), false));
        assertEquals(List.of(BUILDER), LauncherWorldFilter.filter(
                List.of(FOREST, CAVES, BUILDER), "0.5.0", Set.of(), false));
    }

    @Test
    void favoritesFilterCanBeCombinedWithSearch() {
        assertEquals(List.of(CAVES), LauncherWorldFilter.filter(
                List.of(FOREST, CAVES, BUILDER), "crystal",
                Set.of("caves.bwworld", "builder.bwworld"), true));
        assertTrue(LauncherWorldFilter.filter(
                List.of(FOREST, CAVES, BUILDER), "not found", Set.of(), false).isEmpty());
    }

    @Test
    void blankQueryReturnsAllWorlds() {
        assertEquals(3, LauncherWorldFilter.filter(
                List.of(FOREST, CAVES, BUILDER), "   ", Set.of(), false).size());
    }
}
