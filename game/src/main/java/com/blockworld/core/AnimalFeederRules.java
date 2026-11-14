package com.blockworld.core;

/** Stock, refill, and automatic feeding rules for player-built animal troughs. */
public final class AnimalFeederRules {
    public static final int MAX_STOCK_PER_FOOD = 64;
    public static final float FEED_INTERVAL_SECONDS = 45f;
    public static final float FEEDING_RADIUS = 4.5f;
    public static final float GROWTH_BOOST_SECONDS = 25f;

    public record Stock(int wheat, int seeds, float secondsUntilFeed) {
        public Stock {
            if (wheat < 0 || wheat > MAX_STOCK_PER_FOOD || seeds < 0 || seeds > MAX_STOCK_PER_FOOD
                    || !Float.isFinite(secondsUntilFeed) || secondsUntilFeed < 0f
                    || secondsUntilFeed > FEED_INTERVAL_SECONDS) {
                throw new IllegalArgumentException("Некорректный запас кормушки");
            }
        }
        public static Stock empty() { return new Stock(0, 0, FEED_INTERVAL_SECONDS); }
        public boolean isEmpty() { return wheat == 0 && seeds == 0; }
    }

    private AnimalFeederRules() { }

    public static ItemType feedFor(CreatureType animal) {
        if (AnimalBreedingRules.isBaby(animal)) animal = AnimalBreedingRules.adultTypeFor(animal);
        return AnimalBreedingRules.feedFor(animal);
    }

    public static boolean hasFood(Stock stock, CreatureType animal) {
        if (stock == null) return false;
        ItemType feed = feedFor(animal);
        return feed == ItemType.WHEAT ? stock.wheat() > 0
                : feed == ItemType.WHEAT_SEEDS && stock.seeds() > 0;
    }

    public static Stock refill(Stock current, int wheatAdded, int seedsAdded) {
        Stock stock = current == null ? Stock.empty() : current;
        int wheat = Math.max(0, Math.min(MAX_STOCK_PER_FOOD - stock.wheat(), wheatAdded));
        int seeds = Math.max(0, Math.min(MAX_STOCK_PER_FOOD - stock.seeds(), seedsAdded));
        if (wheat == 0 && seeds == 0) return stock;
        float timer = stock.isEmpty() ? FEED_INTERVAL_SECONDS : stock.secondsUntilFeed();
        return new Stock(stock.wheat() + wheat, stock.seeds() + seeds, timer);
    }

    public static Stock advance(Stock stock, float elapsedSeconds) {
        if (stock == null || stock.isEmpty() || !Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0f) {
            return stock == null ? Stock.empty() : stock;
        }
        return new Stock(stock.wheat(), stock.seeds(),
                Math.max(0f, stock.secondsUntilFeed() - elapsedSeconds));
    }

    public static Stock consume(Stock stock, CreatureType animal) {
        if (stock == null || stock.secondsUntilFeed() > 0f) return stock;
        ItemType feed = feedFor(animal);
        if (feed == ItemType.WHEAT && stock.wheat() > 0) {
            return new Stock(stock.wheat() - 1, stock.seeds(), FEED_INTERVAL_SECONDS);
        }
        if (feed == ItemType.WHEAT_SEEDS && stock.seeds() > 0) {
            return new Stock(stock.wheat(), stock.seeds() - 1, FEED_INTERVAL_SECONDS);
        }
        return stock;
    }
}
