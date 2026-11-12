package com.blockworld.core;

import java.util.Set;

/** Calculates fictional player protection values for the survival ruleset. */
public final class ArmorProtection {
    private ArmorProtection() { }

    public static int defensePoints(Set<ArmorSlot> equipped) {
        return equipped.stream().mapToInt(ArmorSlot::defensePoints).sum();
    }

    public static float landingDamage(float fallDistance, Set<ArmorSlot> equipped) {
        if (!Float.isFinite(fallDistance) || fallDistance <= 5f) return 0f;
        int defense = Math.max(0, defensePoints(equipped));
        float mitigation = Math.min(0.70f, defense * 0.07f);
        return (fallDistance - 5f) * 1.15f * (1f - mitigation);
    }
}
