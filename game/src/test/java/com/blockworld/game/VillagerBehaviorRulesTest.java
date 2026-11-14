package com.blockworld.game;

import com.blockworld.core.CreatureType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerBehaviorRulesTest {
    @Test
    void villagersRestAtNightAndResumeTheirRoutineAfterDawn() {
        assertTrue(VillagerBehaviorRules.shouldRest(CreatureType.VILLAGER, 0.70f));
        assertTrue(VillagerBehaviorRules.shouldRest(CreatureType.VILLAGER_FARMER, 0.95f));
        assertTrue(VillagerBehaviorRules.shouldRest(CreatureType.VILLAGER_LIBRARIAN, 0.02f));
        assertFalse(VillagerBehaviorRules.shouldRest(CreatureType.VILLAGER, 0.25f));
        assertFalse(VillagerBehaviorRules.shouldRest(CreatureType.VILLAGER_BLACKSMITH, 0.40f));
    }

    @Test
    void guardsRemainOnDutyDuringTheNight() {
        assertFalse(VillagerBehaviorRules.shouldRest(CreatureType.VILLAGER_GUARD, 0.80f));
    }

    @Test
    void villagersFleeFromHostilesInsideTheirSafetyRadius() {
        assertTrue(VillagerBehaviorRules.shouldFlee(
                CreatureType.VILLAGER_FARMER, CreatureType.ZOMBIE, 25f, 9f));
        assertTrue(VillagerBehaviorRules.shouldFlee(
                CreatureType.VILLAGER, CreatureType.SKELETON, 81f, 9f));
    }

    @Test
    void villagersIgnoreDistantThreatsAndDoNotFleeFromPeacefulCreatures() {
        assertFalse(VillagerBehaviorRules.shouldFlee(
                CreatureType.VILLAGER, CreatureType.ZOMBIE, 81.1f, 9f));
        assertFalse(VillagerBehaviorRules.shouldFlee(
                CreatureType.VILLAGER, CreatureType.COW, 1f, 9f));
        assertFalse(VillagerBehaviorRules.shouldFlee(
                CreatureType.VILLAGER_GUARD, CreatureType.ZOMBIE, 1f, 9f));
    }

    @Test
    void rejectsInvalidDistanceInputs() {
        assertFalse(VillagerBehaviorRules.shouldFlee(
                CreatureType.VILLAGER, CreatureType.ZOMBIE, Float.NaN, 9f));
        assertFalse(VillagerBehaviorRules.shouldFlee(
                CreatureType.VILLAGER, CreatureType.ZOMBIE, 1f, Float.NaN));
    }
}
