package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {
    @Test void contactActionUsesFixedNonWeaponGameValue() {
        assertEquals(2.5f, CombatRules.unarmedContactDamage(), .001f);
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
