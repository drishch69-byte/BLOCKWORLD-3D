package com.blockworld.core;

import java.util.Set;

/** Server-friendly melee and armor rules for fictional game combat. */
public final class CombatRules {
    private CombatRules() { }

    /** A simple non-weapon contact action used by the sandbox interaction system. */
    public static float unarmedContactDamage() { return 2.5f; }

    public static float applyArmor(float damage, Set<ArmorSlot> armor) {
        if (!Float.isFinite(damage) || damage <= 0f) return 0f;
        int defense = armor == null ? 0 : ArmorProtection.defensePoints(armor);
        return Math.max(0f, damage * (1f - Math.min(.70f, Math.max(0, defense) * .07f)));
    }

    /** Returns true when the attacker faces the target within a generous 60-degree cone. */
    public static boolean isFacingTarget(float x, float z, float yaw, float targetX, float targetZ) {
        if (!Float.isFinite(x) || !Float.isFinite(z) || !Float.isFinite(yaw)
                || !Float.isFinite(targetX) || !Float.isFinite(targetZ)) return false;
        float dx = targetX - x;
        float dz = targetZ - z;
        float distanceSquared = dx * dx + dz * dz;
        if (distanceSquared < 0.0001f) return false;
        float inverseDistance = 1f / (float) Math.sqrt(distanceSquared);
        // The game camera looks along (-sin(yaw), -cos(yaw)).
        float forwardX = (float) -Math.sin(yaw);
        float forwardZ = (float) -Math.cos(yaw);
        return (dx * forwardX + dz * forwardZ) * inverseDistance >= 0.5f;
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
