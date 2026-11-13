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
        boolean added = discoveredBiomes.add(biome);
        if (hasDiscoveredAllBiomes()) unlocked.add(Achievement.ALL_BIOMES);
        return added;
    }

    public int unlockedCount() { return unlocked.size(); }
    public int totalCount() { return Achievement.values().length; }
    public int discoveredBiomeCount() { return discoveredBiomes.size(); }

    /** Older profiles use five biomes; expanded profiles use the seven-biome set. */
    public void setExtendedBiomeSet(boolean extended) {
        requiredBiomes = extended ? BiomeType.extendedSet() : BiomeType.legacySet();
        if (hasDiscoveredAllBiomes()) unlocked.add(Achievement.ALL_BIOMES);
    }

    /** The enchanted grove is required only for the 1.9.9 profile. */
    public void setEnchantedBiomeSet(boolean enchanted) {
        requiredBiomes = enchanted ? BiomeType.enchantedSet()
                : requiredBiomes.contains(BiomeType.TAIGA) ? BiomeType.extendedSet() : BiomeType.legacySet();
        if (hasDiscoveredAllBiomes()) unlocked.add(Achievement.ALL_BIOMES);
    }

    public int requiredBiomeCount() { return requiredBiomes.size(); }

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
