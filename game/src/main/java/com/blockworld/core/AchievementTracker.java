package com.blockworld.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Tracks one-time milestones and discovered biomes for a player. */
public final class AchievementTracker {
    private final EnumSet<Achievement> unlocked = EnumSet.noneOf(Achievement.class);
    private final EnumSet<BiomeType> discoveredBiomes = EnumSet.noneOf(BiomeType.class);
    private EnumSet<BiomeType> requiredBiomes = BiomeType.legacySet();

    public boolean unlock(Achievement achievement) {
        if (achievement == null) throw new IllegalArgumentException("Достижение обязательно");
        return unlocked.add(achievement);
    }

    public boolean isUnlocked(Achievement achievement) { return unlocked.contains(achievement); }
    public boolean discover(BiomeType biome) {
        if (biome == null) return false;
        return discoveredBiomes.add(biome);
    }

    public int unlockedCount() { return unlocked.size(); }
    public int totalCount() { return Achievement.values().length; }
    public int discoveredBiomeCount() { return discoveredBiomes.size(); }

    /** Older game profiles only generate the original five biomes. */
    public void setExtendedBiomeSet(boolean extended) {
        requiredBiomes = extended ? EnumSet.allOf(BiomeType.class) : BiomeType.legacySet();
        if (hasDiscoveredAllBiomes()) unlocked.add(Achievement.ALL_BIOMES);
    }

    public boolean hasDiscoveredAllBiomes() { return discoveredBiomes.containsAll(requiredBiomes); }
    public Set<Achievement> unlocked() { return Collections.unmodifiableSet(EnumSet.copyOf(unlocked)); }
    public Set<BiomeType> discoveredBiomes() { return Collections.unmodifiableSet(EnumSet.copyOf(discoveredBiomes)); }

    public void restore(Set<Achievement> achievements, Set<BiomeType> biomes) {
        unlocked.clear();
        discoveredBiomes.clear();
        if (achievements != null) unlocked.addAll(achievements);
        if (biomes != null) discoveredBiomes.addAll(biomes);
        if (hasDiscoveredAllBiomes()) unlocked.add(Achievement.ALL_BIOMES);
    }
}
