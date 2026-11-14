package com.blockworld.core;

import java.util.List;
import java.util.Map;

/** A finite exchange offered by a village profession. Costs and rewards are item stacks. */
public record TradeRecipe(int number, String description, Map<ItemType, Integer> costs,
                          ItemType result, int resultCount) {
    public TradeRecipe {
        if (number < 1 || description == null || description.isBlank() || result == null || resultCount < 1) {
            throw new IllegalArgumentException("Некорректное предложение торговца");
        }
        costs = Map.copyOf(costs);
        if (costs.isEmpty() || costs.entrySet().stream()
                .anyMatch(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 1)) {
            throw new IllegalArgumentException("Для обмена нужны положительные количества предметов");
        }
    }

    /** The original four offers remain available to ordinary villagers and older profiles. */
    public static List<TradeRecipe> villageOffers() {
        return List.of(
                new TradeRecipe(1, "Обменять пшеницу на изумруд", Map.of(ItemType.WHEAT, 4), ItemType.EMERALD, 1),
                new TradeRecipe(2, "Обменять медные слитки на изумруд", Map.of(ItemType.COPPER_INGOT, 2), ItemType.EMERALD, 1),
                new TradeRecipe(3, "Обменять изумруд на хлеб ×3", Map.of(ItemType.EMERALD, 1), ItemType.BREAD, 3),
                new TradeRecipe(4, "Обменять изумруды на железные слитки ×2", Map.of(ItemType.EMERALD, 3), ItemType.IRON_INGOT, 2)
        );
    }

    /** Returns the profession's own four offers; non-trading villagers keep the legacy catalogue. */
    public static List<TradeRecipe> offersFor(CreatureType profession) {
        if (profession == null) return villageOffers();
        return switch (profession) {
            case VILLAGER_FARMER -> List.of(
                    new TradeRecipe(1, "Пшеница ×4 → изумруд ×1", Map.of(ItemType.WHEAT, 4), ItemType.EMERALD, 1),
                    new TradeRecipe(2, "Семена ×6 → хлеб ×2", Map.of(ItemType.WHEAT_SEEDS, 6), ItemType.BREAD, 2),
                    new TradeRecipe(3, "Яйца ×3 → изумруд ×1", Map.of(ItemType.EGG, 3), ItemType.EMERALD, 1),
                    new TradeRecipe(4, "Шерсть ×2 → изумруд ×1", Map.of(ItemType.WOOL, 2), ItemType.EMERALD, 1)
            );
            case VILLAGER_BLACKSMITH -> List.of(
                    new TradeRecipe(1, "Медные слитки ×2 → изумруд ×1", Map.of(ItemType.COPPER_INGOT, 2), ItemType.EMERALD, 1),
                    new TradeRecipe(2, "Изумруды ×3 → железные слитки ×2", Map.of(ItemType.EMERALD, 3), ItemType.IRON_INGOT, 2),
                    new TradeRecipe(3, "Железные слитки ×4 → железная кирка ×1", Map.of(ItemType.IRON_INGOT, 4), ItemType.IRON_PICKAXE, 1),
                    new TradeRecipe(4, "Изумруды ×2 → ножницы ×1", Map.of(ItemType.EMERALD, 2), ItemType.SHEARS, 1)
            );
            case VILLAGER_LIBRARIAN -> List.of(
                    new TradeRecipe(1, "Изумруд ×1 → факелы ×8", Map.of(ItemType.EMERALD, 1), ItemType.TORCH, 8),
                    new TradeRecipe(2, "Изумруды ×2 → фонарь ×1", Map.of(ItemType.EMERALD, 2), ItemType.LANTERN, 1),
                    new TradeRecipe(3, "Изумруды ×3 → древняя реликвия ×1", Map.of(ItemType.EMERALD, 3), ItemType.ANCIENT_RELIC, 1),
                    new TradeRecipe(4, "Изумруды ×4 → лунная печать ×1", Map.of(ItemType.EMERALD, 4), ItemType.MOON_SIGIL, 1)
            );
            case VILLAGER_GUARD -> List.of(
                    new TradeRecipe(1, "Железные слитки ×2 → железный шлем ×1", Map.of(ItemType.IRON_INGOT, 2), ItemType.IRON_HELMET, 1),
                    new TradeRecipe(2, "Изумруды ×3 → нагрудник ×1", Map.of(ItemType.EMERALD, 3), ItemType.IRON_CHESTPLATE, 1),
                    new TradeRecipe(3, "Железные слитки ×2 → ботинки ×1", Map.of(ItemType.IRON_INGOT, 2), ItemType.IRON_BOOTS, 1),
                    new TradeRecipe(4, "Изумруды ×2 → поножи ×1", Map.of(ItemType.EMERALD, 2), ItemType.IRON_LEGGINGS, 1)
            );
            default -> villageOffers();
        };
    }

    public boolean canTrade(Map<ItemType, Integer> items) {
        return items != null && costs.entrySet().stream()
                .allMatch(e -> items.getOrDefault(e.getKey(), 0) >= e.getValue());
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
