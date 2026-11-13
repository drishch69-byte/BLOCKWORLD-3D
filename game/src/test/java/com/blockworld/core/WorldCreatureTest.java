package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldCreatureTest {
    @Test
    void creatureIdsAndTypesRemainStableForSaveFiles() {
        assertEquals("cow", CreatureType.COW.id());
        assertEquals("villager", CreatureType.fromId("villager").id());
        assertEquals("slime", CreatureType.SLIME.id());
        assertEquals("fox", CreatureType.fromId("fox").id());
        assertEquals("turtle", CreatureType.fromId("turtle").id());
        assertEquals("dog", CreatureType.fromId("dog").id());
        assertEquals("zombie", CreatureType.fromId("zombie").id());
        assertEquals("skeleton", CreatureType.fromId("skeleton").id());
        assertEquals("spider", CreatureType.fromId("spider").id());
        assertEquals("rabbit", CreatureType.fromId("rabbit").id());
        assertEquals("crystal_sentinel", CreatureType.fromId("crystal_sentinel").id());
        assertTrue(CreatureType.CRYSTAL_SENTINEL.isHostile());
        assertEquals(70f, CreatureType.CRYSTAL_SENTINEL.maxHealth());
        assertEquals(3.5f, CreatureType.CRYSTAL_SENTINEL.attackDamage());
        assertEquals("Кролик", CreatureType.RABBIT.displayName());
        assertEquals(0f, CreatureType.RABBIT.attackDamage());
        assertTrue(!CreatureType.RABBIT.isHostile());
        assertEquals("iron_golem", CreatureType.fromId("iron_golem").id());
        assertTrue(CreatureType.ZOMBIE.isHostile());
        assertTrue(CreatureType.IRON_GOLEM.isGuardian());
        assertTrue(CreatureType.VILLAGER_FARMER.isVillager());
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
