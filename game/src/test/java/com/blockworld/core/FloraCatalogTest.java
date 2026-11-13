package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class FloraCatalogTest {
    @Test void flowerCatalogHasNamedVarieties() {
        assertTrue(FloraCatalog.Flower.values().length >= 12);
        Set<String> names = Arrays.stream(FloraCatalog.Flower.values())
                .map(FloraCatalog.Flower::displayName).collect(Collectors.toSet());
        assertEquals(FloraCatalog.Flower.values().length, names.size());
        assertEquals("Одуванчик", FloraCatalog.Flower.DANDELION.displayName());
    }

    @Test void meadowChoicesAreDeterministic() {
        assertEquals(FloraCatalog.grassAt(99, 12, 4), FloraCatalog.grassAt(99, 12, 4));
        assertEquals(FloraCatalog.flowerAt(99, 12, 4, BiomeType.FOREST),
                FloraCatalog.flowerAt(99, 12, 4, BiomeType.FOREST));
    }
}
