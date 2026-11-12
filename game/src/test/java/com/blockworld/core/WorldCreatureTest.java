package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldCreatureTest {
    @Test
    void creatureIdsAndTypesRemainStableForSaveFiles() {
        assertEquals("cow", CreatureType.COW.id());
        assertEquals("villager", CreatureType.fromId("villager").id());
        assertEquals("slime", CreatureType.SLIME.id());
        WorldCreature creature = new WorldCreature(17L, CreatureType.SHEEP, 1.5f, 4f, -2.5f, 0.75f);
        assertEquals(17L, creature.id());
        assertEquals(CreatureType.SHEEP, creature.type());
    }

    @Test
    void rejectsInvalidCreatureCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldCreature(1L, CreatureType.COW, Float.NaN, 1f, 1f, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldCreature(-1L, CreatureType.COW, 0f, 1f, 1f, 0f));
    }
}
