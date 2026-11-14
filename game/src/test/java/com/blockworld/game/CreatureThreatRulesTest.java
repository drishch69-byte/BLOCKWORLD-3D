package com.blockworld.game;

import com.blockworld.core.CreatureType;
import com.blockworld.core.WorldCreature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatureThreatRulesTest {
    @Test
    void detectsHostilesInsideVillageProtectionRadius() {
        WorldCreature zombie = new WorldCreature(1L, CreatureType.ZOMBIE, 17.9f, 1f, 0f, 0f);
        WorldCreature villager = new WorldCreature(2L, CreatureType.VILLAGER, 0f, 1f, 0f, 0f);

        assertTrue(CreatureThreatRules.isThreateningVillage(zombie, List.of(villager), 18f));
    }

    @Test
    void ignoresHostilesOutsideVillageProtectionRadius() {
        WorldCreature zombie = new WorldCreature(1L, CreatureType.ZOMBIE, 18.1f, 1f, 0f, 0f);
        WorldCreature villager = new WorldCreature(2L, CreatureType.VILLAGER, 0f, 1f, 0f, 0f);

        assertFalse(CreatureThreatRules.isThreateningVillage(zombie, List.of(villager), 18f));
    }

    @Test
    void usesHorizontalDistanceAndIgnoresNonVillagers() {
        WorldCreature zombie = new WorldCreature(1L, CreatureType.ZOMBIE, 0f, 80f, 0f, 0f);
        WorldCreature cow = new WorldCreature(2L, CreatureType.COW, 0f, 1f, 0f, 0f);

        assertFalse(CreatureThreatRules.isThreateningVillage(zombie, List.of(cow), 18f));
        assertTrue(CreatureThreatRules.isThreateningVillage(zombie,
                List.of(new WorldCreature(3L, CreatureType.VILLAGER, 0f, 0f, 1f, 0f)), 18f));
    }
}
