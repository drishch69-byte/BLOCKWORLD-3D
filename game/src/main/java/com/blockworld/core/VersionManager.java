package com.blockworld.core;

import java.util.List;

/** Central registry for all selectable game profiles. */
public final class VersionManager {
    public static final int WORLD_FORMAT = 2;

    private static final List<GameVersion> VERSIONS = List.of(
            new GameVersion("0.1.0", "Classic", "Первый блочный мир", 9, false, false, false, false, false),
            new GameVersion("0.2.0", "Builder Update", "Большой мир, деревья и строительство", 13, true, false, false, true, false),
            new GameVersion("0.3.0", "Adventure Update", "Пещеры, руды и панель из 9 слотов", 16, true, true, true, true, false),
            new GameVersion("0.4.0", "Survival Update", "Ходьба, прыжки, здоровье, голод и сбор блоков", 18, true, true, true, true, true),
            new GameVersion("0.5.0", "Chunk Engine", "Чанки 16×16, потоковая загрузка и оптимизация FPS", 64, true, true, true, true, true),
            new GameVersion("0.5.1", "Chunk Engine Hotfix", "Исправление запуска мира, камеры и диагностики чанков", 64, true, true, true, true, true),
            new GameVersion("0.5.2", "World Render Fix", "Диагностика отрисовки, безопасная точка появления и улучшенный HUD", 64, true, true, true, true, true),
            new GameVersion("0.5.3", "Stable Render Diagnostics", "Двусторонний режим мешей, тестовая площадка загрузки и расширенная диагностика", 64, true, true, true, true, true),
            new GameVersion("0.6.0", "Nature Engine", "Вода, лава, реки, день и ночь, динамическая погода", 64, true, true, true, true, true),
            new GameVersion("0.6.1", "Nature Engine Compiler Fix", "Исправление совместимости Math.max и метаданных запуска 0.6.0", 64, true, true, true, true, true),
            new GameVersion("0.6.2", "Nature Engine Safe Launch", "Защита запуска, журнал ошибок графики и безопасное закрытие", 64, true, true, true, true, true),
            new GameVersion("0.6.3", "Startup Crash Diagnostics", "Диагностика аварий JVM, отметки этапов запуска и отдельные crash-отчёты", 64, true, true, true, true, true),
            new GameVersion("0.7.0", "Customization Update", "Главное меню, настройки, моды и ресурс-паки", 64, true, true, true, true, true),
            new GameVersion("0.7.5", "Texture Pack Engine", "Настоящие PNG-текстуры блоков, атлас 8×8, варианты верх/сторона/низ", 64, true, true, true, true, true),
            new GameVersion("0.8.0", "Creatures Update", "Животные, жители деревни, маленькие деревни и блуждающие существа", 64, true, true, true, true, true),
            new GameVersion("0.8.5", "Inventory & Crafting Update", "Инвентарь игрока, рецепты крафта, доски, палки, верстак и факелы", 64, true, true, true, true, true),
            new GameVersion("0.9.0", "Tools & Equipment Update", "Кирки с уровнями добычи, лопата, экипируемая броня и защита при падении", 64, true, true, true, true, true),
            new GameVersion("0.9.5", "Biomes, Farming & Furnace Update", "Пять биомов, расширенный мир, фермерство с ростом урожая и плавка руды в печи", 88, true, true, true, true, true),
            new GameVersion("1.0.0", "Trading, Fishing & Achievements", "Торговля с жителями, рыбалка, новые руды, достижения и звуковые эффекты", 96, true, true, true, true, true),
            new GameVersion("1.0.1", "Visual Inventory Update", "PNG-иконки предметов в инвентаре и мастерской, портрет игрока и обновлённый интерфейс", 96, true, true, true, true, true),
            new GameVersion("1.0.2", "Third-Person Camera Update", "Переключаемая камера от третьего лица, 3D-модель игрока и анимация ходьбы", 96, true, true, true, true, true),
            new GameVersion("1.0.3", "Creature & Camera Polish", "Камера с коллизией стен, анимация ходьбы мобов, прыжки слайма и улучшенная модель жителя", 96, true, true, true, true, true),
            new GameVersion("1.1.0", "Wildlands & Crystal Caves", "Тайга, болота, кристальные озёра, лисы, черепахи, пёс-компаньон и образы игрока", 112, true, true, true, true, true),
            new GameVersion("1.2.0", "Exploration Update", "Шахты, сторожевые башни, руины, лагеря, сундуки с добычей, карта и компас", 128, true, true, true, true, true),
            new GameVersion("1.8.0", "Village Defense & Combat Update", "Мечи, монстры, железный голем, профессии жителей, цветочные поля и улучшенное фермерство", 144, true, true, true, true, true),
            new GameVersion("1.8.1", "Rabbit & Wildlife Update", "Кролики, новая модель и PNG-текстура, прыжковая анимация и природные места обитания", 144, true, true, true, true, true),
            new GameVersion("1.9.0", "Living World & Companions", "Команды следовать и оставаться для пса-компаньона с сохранением поведения", 144, true, true, true, true, true),
            new GameVersion("1.9.1", "World Creator & Seeds", "Выбор размера мира и seed при создании с сохранением параметров", 144, true, true, true, true, true),
            new GameVersion("1.9.2", "Classic Hotbar Controls", "Переключение слотов панели быстрого доступа колёсиком мыши", 144, true, true, true, true, true),
            new GameVersion("1.9.3", "Inventory Tabs Update", "Вкладки ресурсов и экипировки в инвентаре, переключение клавишами 1–2 и стрелками", 144, true, true, true, true, true),
            new GameVersion("1.9.4", "Mining Feedback Update", "Добыча блоков удержанием ЛКМ, индикатор прогресса и скорость с учётом подходящего инструмента", 144, true, true, true, true, true),
            new GameVersion("1.9.5", "Crystal Caverns & Gem Veins", "Большие подземные пещеры, кристальные камеры, рубиновая и сапфировая руда и новые пиксельные текстуры", 144, true, true, true, true, true),
            new GameVersion("1.9.6", "Cave Biomes & Glowflora", "Кристальные гроты, мшистые пещеры, подземные озёра, светящиеся кристаллы и пещерные растения", 144, true, true, true, true, true),
            new GameVersion("1.9.7", "Crystal Guardians & Hidden Treasures", "Кристальные деревья, кристальные тайники с редкой добычей и пещерный босс Кристальный страж", 144, true, true, true, true, true),
            new GameVersion("1.9.8", "Ancient Temples & Relics", "Два древних храма с храмовыми стражами, древними тайниками и тремя редкими артефактами", 144, true, true, true, true, true),
            new GameVersion("1.9.9", "Enchanted Grove & Artifact Collection", "Зачарованная роща, редкие светящиеся растения и коллекция древних артефактов с достижениями", 144, true, true, true, true, true),
            new GameVersion("1.9.10", "Fox Companions & Taming", "Приручение лис приготовленной рыбой, команды питомцам и сохранение приручения", 144, true, true, true, true, true),
            new GameVersion("1.9.11", "Farm Life & Baby Animals", "Разведение коров, овец, свиней и кур, детёныши, рост животных и улучшенный урожай", 144, true, true, true, true, true),
            new GameVersion("1.9.12", "Farm Products & Harvest", "Молоко, шерсть и яйца, ведро, ножницы и сохраняемые таймеры продукции животных", 144, true, true, true, true, true),
            new GameVersion("1.9.13", "Farm Pens & Smart Feeders", "Деревянные загоны, кормушки с запасом пшеницы и семян, автоматическое кормление и ускорение роста животных", 144, true, true, true, true, true)
    );

    private VersionManager() {
    }

    public static List<GameVersion> getSupportedVersions() {
        return VERSIONS;
    }

    public static GameVersion getDefaultVersion() {
        return VERSIONS.get(VERSIONS.size() - 1);
    }

    public static GameVersion find(String id) {
        if (id == null) {
            return getDefaultVersion();
        }
        return VERSIONS.stream()
                .filter(version -> version.id().equals(id))
                .findFirst()
                .orElse(getDefaultVersion());
    }
}
