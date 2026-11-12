package com.blockworld.core;

import java.util.Map;

/** A finite exchange offered by village traders. Costs and rewards are item stacks. */
public record TradeRecipe(int number, String description, Map<ItemType, Integer> costs,
                          ItemType result, int resultCount) {
    public TradeRecipe {
        if (number < 1 || description == null || description.isBlank() || result == null || resultCount < 1) {
            throw new IllegalArgumentException("Некорректное предложение торговца");
        }
        costs = Map.copyOf(costs);
        if (costs.isEmpty() || costs.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 1)) {
            throw new IllegalArgumentException("Для обмена нужны положительные количества предметов");
        }
    }

    public static java.util.List<TradeRecipe> villageOffers() {
        return java.util.List.of(
                new TradeRecipe(1, "Обменять пшеницу на изумруд", Map.of(ItemType.WHEAT, 4), ItemType.EMERALD, 1),
                new TradeRecipe(2, "Обменять медные слитки на изумруд", Map.of(ItemType.COPPER_INGOT, 2), ItemType.EMERALD, 1),
                new TradeRecipe(3, "Обменять изумруд на хлеб ×3", Map.of(ItemType.EMERALD, 1), ItemType.BREAD, 3),
                new TradeRecipe(4, "Обменять изумруды на железные слитки ×2", Map.of(ItemType.EMERALD, 3), ItemType.IRON_INGOT, 2)
        );
    }

    public boolean canTrade(Map<ItemType, Integer> items) {
        return costs.entrySet().stream().allMatch(e -> items.getOrDefault(e.getKey(), 0) >= e.getValue());
    }

    public boolean trade(Map<ItemType, Integer> items) {
        if (!canTrade(items)) return false;
        costs.forEach((item, count) -> items.put(item, items.get(item) - count));
        items.merge(result, resultCount, Integer::sum);
        return true;
    }

    public String costText() {
        return costs.entrySet().stream()
                .map(e -> e.getKey().displayName() + " × " + e.getValue())
                .collect(java.util.stream.Collectors.joining(" + "));
    }
}
