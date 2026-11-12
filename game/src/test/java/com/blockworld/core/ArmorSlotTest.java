package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class ArmorSlotTest {
    @Test void completeSetHasEightDefensePoints() {
        int points = EnumSet.allOf(ArmorSlot.class).stream().mapToInt(ArmorSlot::defensePoints).sum();
        assertEquals(8, points);
    }

    @Test void armorSlotsUseDistinctInventoryItems() {
        assertNotEquals(ArmorSlot.HELMET.item(), ArmorSlot.CHESTPLATE.item());
        assertNotEquals(ArmorSlot.LEGGINGS.item(), ArmorSlot.BOOTS.item());
    }
}
