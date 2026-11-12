package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class ArmorProtectionTest {
    @Test void shortFallsDoNotCauseImpactDamage() {
        assertEquals(0f, ArmorProtection.landingDamage(5f, EnumSet.noneOf(ArmorSlot.class)), 0.0001f);
    }

    @Test void fullEquipmentReducesImpactDamage() {
        var empty = EnumSet.noneOf(ArmorSlot.class);
        var full = EnumSet.allOf(ArmorSlot.class);
        assertTrue(ArmorProtection.landingDamage(12f, full) < ArmorProtection.landingDamage(12f, empty));
        assertEquals(8, ArmorProtection.defensePoints(full));
    }
}
