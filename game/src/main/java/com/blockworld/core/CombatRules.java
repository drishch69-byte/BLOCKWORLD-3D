package com.blockworld.core;

import java.util.Map;
import java.util.Set;

/** Server-friendly melee and armor rules for fictional game combat. */
public final class CombatRules {
    private CombatRules() { }

    public static float meleeDamage(Map<ItemType, Integer> items) {
        if (items == null) return 2.5f;
        if (items.getOrDefault(ItemType.IRON_SWORD, 0) > 0) return 8f;
        if (items.getOrDefault(ItemType.STONE_SWORD, 0) > 0) return 6f;
        if (items.getOrDefault(ItemType.WOODEN_SWORD, 0) > 0) return 4f;
        return 2.5f;
    }

    public static float applyArmor(float damage, Set<ArmorSlot> armor) {
        if (!Float.isFinite(damage) || damage <= 0f) return 0f;
        int defense = armor == null ? 0 : ArmorProtection.defensePoints(armor);
        return Math.max(0f, damage * (1f - Math.min(.70f, Math.max(0, defense) * .07f)));
    }

    public static boolean inMeleeRange(float ax, float ay, float az,
                                       float bx, float by, float bz, float reach) {
        if (!Float.isFinite(ax) || !Float.isFinite(ay) || !Float.isFinite(az)
                || !Float.isFinite(bx) || !Float.isFinite(by) || !Float.isFinite(bz)
                || !Float.isFinite(reach) || reach <= 0f) return false;
        float dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.abs(dy) <= 2f && dx * dx + dy * dy + dz * dz <= reach * reach;
    }
}
