package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldCreationOptionsTest {
    @Test
    void exposesTheThreeSupportedWorldSizes() {
        assertEquals(96, new WorldCreationOptions(null, WorldCreationOptions.WorldSize.SMALL).worldRadius());
        assertEquals(144, new WorldCreationOptions(null, WorldCreationOptions.WorldSize.NORMAL).worldRadius());
        assertEquals(192, new WorldCreationOptions(null, WorldCreationOptions.WorldSize.LARGE).worldRadius());
    }

    @Test
    void defaultsMissingSizeToNormalAndAllowsRandomSeed() {
        WorldCreationOptions options = new WorldCreationOptions(null, null);
        assertEquals(WorldCreationOptions.WorldSize.NORMAL, options.size());
        assertEquals(144, options.worldRadius());
        assertNull(options.seed());
    }

    @Test
    void preservesAnExplicitSeed() {
        assertEquals(123456789L,
                new WorldCreationOptions(123456789L, WorldCreationOptions.WorldSize.SMALL).seed());
    }
}
