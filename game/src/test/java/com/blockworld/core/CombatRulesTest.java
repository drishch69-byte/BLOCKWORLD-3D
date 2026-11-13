package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {
    @Test void swordDamageUsesBestAvailableSword() {
        assertEquals(2.5f, CombatRules.meleeDamage(Map.of()), .001f);
        assertEquals(4f, CombatRules.meleeDamage(Map.of(ItemType.WOODEN_SWORD, 1)), .001f);
        assertEquals(6f, CombatRules.meleeDamage(Map.of(ItemType.WOODEN_SWORD, 1, ItemType.STONE_SWORD, 1)), .001f);
        assertEquals(8f, CombatRules.meleeDamage(Map.of(ItemType.IRON_SWORD, 1)), .001f);
    }

    @Test void armorReducesGameDamageAndInvalidDamageIsIgnored() {
        assertTrue(CombatRules.applyArmor(8f, Set.of(ArmorSlot.HELMET)) < 8f);
        assertEquals(0f, CombatRules.applyArmor(Float.NaN, Set.of()), .001f);
    }

    @Test void meleeRangeRejectsDistantOrInvalidTargets() {
        assertTrue(CombatRules.inMeleeRange(0, 0, 0, 2, 0, 0, 3));
        assertFalse(CombatRules.inMeleeRange(0, 0, 0, 5, 0, 0, 3));
        assertFalse(CombatRules.inMeleeRange(0, 0, 0, 1, 5, 0, 3));
        assertFalse(CombatRules.inMeleeRange(Float.NaN, 0, 0, 1, 0, 0, 3));
    }
}
