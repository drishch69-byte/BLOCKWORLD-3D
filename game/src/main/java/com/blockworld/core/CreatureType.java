package com.blockworld.core;

/** Stable creature IDs stored in world files. Do not rename existing IDs. */
public enum CreatureType {
    COW("cow", "Корова", 0.55f),
    SHEEP("sheep", "Овца", 0.48f),
    PIG("pig", "Свинья", 0.62f),
    CHICKEN("chicken", "Курица", 0.78f),
    VILLAGER("villager", "Житель", 0.30f),
    SLIME("slime", "Слайм", 0.45f),
    FOX("fox", "Лиса", 0.58f),
    TURTLE("turtle", "Черепаха", 0.20f),
    DOG("dog", "Пёс-компаньон", 0.85f),
    VILLAGER_FARMER("villager_farmer", "Житель-фермер", 0.30f),
    VILLAGER_BLACKSMITH("villager_blacksmith", "Житель-кузнец", 0.30f),
    VILLAGER_LIBRARIAN("villager_librarian", "Житель-библиотекарь", 0.30f),
    VILLAGER_GUARD("villager_guard", "Житель-страж", 0.34f),
    IRON_GOLEM("iron_golem", "Железный голем", 0.24f),
    ZOMBIE("zombie", "Зомби", 0.30f),
    SKELETON("skeleton", "Скелет", 0.36f),
    SPIDER("spider", "Паук", 0.52f),
    RABBIT("rabbit", "Кролик", 0.72f),
    CRYSTAL_SENTINEL("crystal_sentinel", "Кристальный страж", 0.42f);

    private final String id;
    private final String displayName;
    private final float moveSpeed;

    CreatureType(String id, String displayName, float moveSpeed) {
        this.id = id;
        this.displayName = displayName;
        this.moveSpeed = moveSpeed;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public float moveSpeed() { return moveSpeed; }

    public boolean isHostile() {
        return this == ZOMBIE || this == SKELETON || this == SPIDER || this == CRYSTAL_SENTINEL;
    }
    public boolean isVillager() {
        return this == VILLAGER || this == VILLAGER_FARMER || this == VILLAGER_BLACKSMITH
                || this == VILLAGER_LIBRARIAN || this == VILLAGER_GUARD;
    }
    public boolean isGuardian() { return this == IRON_GOLEM || this == VILLAGER_GUARD; }
    public float maxHealth() {
        return switch (this) {
            case IRON_GOLEM -> 100f;
            case CRYSTAL_SENTINEL -> 70f;
            case ZOMBIE -> 16f;
            case SKELETON -> 14f;
            case SPIDER -> 12f;
            case VILLAGER, VILLAGER_FARMER, VILLAGER_BLACKSMITH, VILLAGER_LIBRARIAN, VILLAGER_GUARD -> 20f;
            default -> 10f;
        };
    }
    public float attackDamage() {
        return switch (this) {
            case ZOMBIE -> 2.5f;
            case SKELETON -> 2.0f;
            case SPIDER -> 1.5f;
            case CRYSTAL_SENTINEL -> 3.5f;
            default -> 0f;
        };
    }

    public static CreatureType fromId(String id) {
        for (CreatureType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        throw new IllegalArgumentException("Неизвестное существо: " + id);
    }
}
