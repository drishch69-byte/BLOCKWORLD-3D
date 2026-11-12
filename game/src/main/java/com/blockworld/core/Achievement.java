package com.blockworld.core;

/** Stable achievement IDs stored in the player's achievement sidecar. */
public enum Achievement {
    FIRST_BLOCK("first_block", "Первый блок", "Добудь свой первый блок."),
    FIRST_CRAFT("first_craft", "Умелые руки", "Создай первый предмет в мастерской."),
    FIRST_TRADE("first_trade", "Хорошая сделка", "Заверши первую сделку с жителем."),
    FIRST_FISH("first_fish", "Рыбак", "Поймай первую рыбу."),
    FIRST_HARVEST("first_harvest", "Первый урожай", "Собери созревшую пшеницу."),
    ALL_BIOMES("all_biomes", "Исследователь", "Посети все пять биомов."),
    FIND_COPPER("find_copper", "Медный век", "Добудь медную руду."),
    FIND_EMERALD("find_emerald", "Редкая находка", "Найди изумрудную руду.");

    private final String id;
    private final String title;
    private final String description;

    Achievement(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }

    public static Achievement fromId(String id) {
        for (Achievement achievement : values()) {
            if (achievement.id.equalsIgnoreCase(id)) return achievement;
        }
        throw new IllegalArgumentException("Неизвестное достижение: " + id);
    }
}
