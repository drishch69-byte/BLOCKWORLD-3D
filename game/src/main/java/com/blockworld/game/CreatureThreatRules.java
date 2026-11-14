package com.blockworld.game;

import com.blockworld.core.WorldCreature;

import java.util.Collection;

/** Small, allocation-free village threat check shared by guardian AI and tests. */
final class CreatureThreatRules {
    private CreatureThreatRules() { }

    static boolean isThreateningVillage(WorldCreature hostile,
                                        Collection<WorldCreature> villagers,
                                        float radius) {
        if (hostile == null || villagers == null || !Float.isFinite(radius) || radius < 0f) {
            return false;
        }
        float radiusSquared = radius * radius;
        for (WorldCreature villager : villagers) {
            if (villager == null || !villager.type().isVillager()) continue;
            float dx = hostile.x() - villager.x();
            float dz = hostile.z() - villager.z();
            if (dx * dx + dz * dz <= radiusSquared) return true;
        }
        return false;
    }
}
