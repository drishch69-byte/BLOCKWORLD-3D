package com.blockworld.core;

import java.util.EnumMap;
import java.util.Map;

/** Shared village market stocks. Work replenishes goods; successful trades consume market stock. */
public final class VillageEconomy {
    public static final int MAX_STOCK = 64;
    private static final float WORK_CYCLE_SECONDS = 60f;

    private final EnumMap<CreatureType, EnumMap<ItemType, Integer>> stocks =
            new EnumMap<>(CreatureType.class);
    private final EnumMap<CreatureType, Float> workProgress =
            new EnumMap<>(CreatureType.class);

    public VillageEconomy() {
        reset();
    }

    /** Restores the original market stock and clears saved work progress. */
    public synchronized void reset() {
        stocks.clear();
        workProgress.clear();
        for (CreatureType profession : marketProfessions()) {
            stocks.put(profession, new EnumMap<>(ItemType.class));
            workProgress.put(profession, 0f);
        }
        seed(CreatureType.VILLAGER, ItemType.EMERALD, 8);
        seed(CreatureType.VILLAGER, ItemType.BREAD, 6);
        seed(CreatureType.VILLAGER, ItemType.IRON_INGOT, 4);
        seed(CreatureType.VILLAGER_FARMER, ItemType.EMERALD, 8);
        seed(CreatureType.VILLAGER_FARMER, ItemType.BREAD, 6);
        seed(CreatureType.VILLAGER_BLACKSMITH, ItemType.EMERALD, 6);
        seed(CreatureType.VILLAGER_BLACKSMITH, ItemType.IRON_INGOT, 10);
        seed(CreatureType.VILLAGER_BLACKSMITH, ItemType.IRON_PICKAXE, 2);
        seed(CreatureType.VILLAGER_BLACKSMITH, ItemType.SHEARS, 2);
        seed(CreatureType.VILLAGER_LIBRARIAN, ItemType.TORCH, 16);
        seed(CreatureType.VILLAGER_LIBRARIAN, ItemType.LANTERN, 3);
        seed(CreatureType.VILLAGER_LIBRARIAN, ItemType.ANCIENT_RELIC, 1);
        seed(CreatureType.VILLAGER_LIBRARIAN, ItemType.MOON_SIGIL, 1);
        seed(CreatureType.VILLAGER_GUARD, ItemType.IRON_HELMET, 2);
        seed(CreatureType.VILLAGER_GUARD, ItemType.IRON_CHESTPLATE, 1);
        seed(CreatureType.VILLAGER_GUARD, ItemType.IRON_BOOTS, 2);
        seed(CreatureType.VILLAGER_GUARD, ItemType.IRON_LEGGINGS, 1);
    }

    public static java.util.List<CreatureType> marketProfessions() {
        return java.util.List.of(CreatureType.VILLAGER,
                CreatureType.VILLAGER_FARMER,
                CreatureType.VILLAGER_BLACKSMITH,
                CreatureType.VILLAGER_LIBRARIAN,
                CreatureType.VILLAGER_GUARD);
    }

    public synchronized int stock(CreatureType profession, ItemType item) {
        if (profession == null || item == null) return 0;
        return stocks.getOrDefault(profession, new EnumMap<>(ItemType.class)).getOrDefault(item, 0);
    }

    public synchronized void setStock(CreatureType profession, ItemType item, int count) {
        if (!marketProfessions().contains(profession) || item == null
                || count < 0 || count > MAX_STOCK) {
            throw new IllegalArgumentException("Некорректный запас деревенского рынка");
        }
        stocks.get(profession).put(item, count);
    }

    public synchronized float workProgress(CreatureType profession) {
        return workProgress.getOrDefault(profession, 0f);
    }

    public synchronized boolean canTrade(CreatureType profession, TradeRecipe offer,
                                         Map<ItemType, Integer> inventory) {
        return isMarketProfession(profession) && offer != null && offer.canTrade(inventory)
                && stock(profession, offer.result()) >= offer.resultCount();
    }

    /** Performs one atomic exchange against both the player's inventory and market stock. */
    public synchronized boolean trade(CreatureType profession, TradeRecipe offer,
                                      Map<ItemType, Integer> inventory) {
        if (!canTrade(profession, offer, inventory)) return false;
        offer.costs().forEach((item, count) -> inventory.put(item, inventory.get(item) - count));
        setStock(profession, offer.result(), stock(profession, offer.result()) - offer.resultCount());
        offer.costs().forEach((item, count) -> addStock(profession, item, count));
        return true;
    }

    /**
     * Advances production from active workers. The caller supplies only villagers currently
     * working at their own profession's workstation during daytime.
     */
    public synchronized boolean advanceWork(Map<CreatureType, Integer> activeWorkers, float elapsedSeconds) {
        if (activeWorkers == null || !Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0f) return false;
        float elapsed = Math.min(elapsedSeconds, 60f);
        boolean changed = false;
        for (CreatureType profession : marketProfessions()) {
            if (profession == CreatureType.VILLAGER_GUARD) continue;
            int workers = Math.max(0, Math.min(4, activeWorkers.getOrDefault(profession, 0)));
            if (workers == 0) continue;
            float progress = workProgress.getOrDefault(profession, 0f) + elapsed * workers;
            int cycles = Math.min(8, (int) (progress / WORK_CYCLE_SECONDS));
            if (cycles <= 0) {
                workProgress.put(profession, progress);
                changed = true;
                continue;
            }
            progress -= cycles * WORK_CYCLE_SECONDS;
            workProgress.put(profession, progress);
            for (int cycle = 0; cycle < cycles; cycle++) {
                produce(profession);
                changed = true;
            }
        }
        return changed;
    }

    private void produce(CreatureType profession) {
        switch (profession) {
            case VILLAGER_FARMER -> {
                addStock(profession, ItemType.BREAD, 1);
                addStock(profession, ItemType.EMERALD, 1);
            }
            case VILLAGER_BLACKSMITH -> {
                addStock(profession, ItemType.IRON_INGOT, 2);
                if (stock(profession, ItemType.IRON_PICKAXE) < 4) addStock(profession, ItemType.IRON_PICKAXE, 1);
                if (stock(profession, ItemType.SHEARS) < 4) addStock(profession, ItemType.SHEARS, 1);
                // The smith also delivers a small supply of replacement gear to the guard post.
                addStock(CreatureType.VILLAGER_GUARD, ItemType.IRON_HELMET, 1);
                addStock(CreatureType.VILLAGER_GUARD, ItemType.IRON_BOOTS, 1);
            }
            case VILLAGER_LIBRARIAN -> {
                addStock(profession, ItemType.TORCH, 4);
                if (stock(profession, ItemType.LANTERN) < 8) addStock(profession, ItemType.LANTERN, 1);
            }
            default -> { }
        }
    }

    private void seed(CreatureType profession, ItemType item, int count) {
        stocks.get(profession).put(item, count);
    }

    private void addStock(CreatureType profession, ItemType item, int count) {
        if (!isMarketProfession(profession) || item == null || count <= 0) return;
        int current = stock(profession, item);
        stocks.get(profession).put(item, Math.min(MAX_STOCK, current + count));
    }

    private static boolean isMarketProfession(CreatureType profession) {
        return marketProfessions().contains(profession);
    }

    public synchronized Snapshot snapshot() {
        EnumMap<CreatureType, Map<ItemType, Integer>> stockCopy = new EnumMap<>(CreatureType.class);
        for (CreatureType profession : marketProfessions()) {
            stockCopy.put(profession, Map.copyOf(stocks.get(profession)));
        }
        return new Snapshot(stockCopy, Map.copyOf(workProgress));
    }

    public synchronized void restore(Snapshot snapshot) {
        if (snapshot == null) {
            reset();
            return;
        }
        reset();
        for (CreatureType profession : marketProfessions()) {
            Map<ItemType, Integer> restored = snapshot.stocks().get(profession);
            if (restored != null) {
                for (Map.Entry<ItemType, Integer> entry : restored.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        setStock(profession, entry.getKey(), Math.max(0, Math.min(MAX_STOCK, entry.getValue())));
                    }
                }
            }
            Float progress = snapshot.workProgress().get(profession);
            if (progress != null && Float.isFinite(progress) && progress >= 0f && progress < WORK_CYCLE_SECONDS) {
                workProgress.put(profession, progress);
            }
        }
    }

    public record Snapshot(Map<CreatureType, Map<ItemType, Integer>> stocks,
                           Map<CreatureType, Float> workProgress) {
        public Snapshot {
            EnumMap<CreatureType, Map<ItemType, Integer>> stockCopy = new EnumMap<>(CreatureType.class);
            if (stocks != null) stocks.forEach((profession, values) ->
                    stockCopy.put(profession, values == null ? Map.of() : Map.copyOf(values)));
            stocks = Map.copyOf(stockCopy);
            workProgress = workProgress == null ? Map.of() : Map.copyOf(workProgress);
        }
    }
}
