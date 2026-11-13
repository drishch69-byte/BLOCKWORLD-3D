package com.blockworld.core;

/** Stable achievement IDs stored in the player's achievement sidecar. */
public enum Achievement {
    FIRST_BLOCK("first_block", "Первый блок", "Добудь свой первый блок."),
    FIRST_CRAFT("first_craft", "Умелые руки", "Создай первый предмет в мастерской."),
    FIRST_TRADE("first_trade", "Хорошая сделка", "Заверши первую сделку с жителем."),
    FIRST_FISH("first_fish", "Рыбак", "Поймай первую рыбу."),
    FIRST_HARVEST("first_harvest", "Первый урожай", "Собери созревшую пшеницу."),
    ALL_BIOMES("all_biomes", "Исследователь", "Посети все биомы текущего профиля."),
    FIND_COPPER("find_copper", "Медный век", "Добудь медную руду."),
    FIND_EMERALD("find_emerald", "Редкая находка", "Найди изумрудную руду."),
    FIND_ANCIENT_RELIC("find_ancient_relic", "Археолог", "Найди древнюю реликвию."),
    FIND_SUN_SIGIL("find_sun_sigil", "Солнечный знак", "Найди солнечную печать."),
    FIND_MOON_SIGIL("find_moon_sigil", "Лунный знак", "Найди лунную печать."),
    COMPLETE_ARTIFACT_SET("complete_artifact_set", "Древнее наследие", "Собери реликвию и обе древние печати.");

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
