# Changelog

## 1.0.0 — Trading, Fishing & Achievements
- Added six original 16×16 PNG creature textures and connected them to cow, sheep, pig, chicken, villager, and slime models using nearest-neighbour filtering; color-only fallback remains available if a texture is missing.
- Added an editable 64×64 player skin atlas and a 128×64 PNG atlas containing 23 crafted-item icons, with a stable `ItemType` mapping file.
- Added automated resource tests for creature PNGs, the player skin, item atlas dimensions, and unique item tile mappings.
- Added village trader interaction with four fixed, one-way exchange offers.
- Added a craftable fishing rod, water-targeted fishing with a cooldown, raw fish, and furnace cooking.
- Added stable block IDs for copper and emerald ores and new item IDs for ingots, emeralds, fishing, and food.
- Added copper and rare emerald generation only to newly generated 1.0.0 worlds; older profiles retain their original ore distribution.
- Cooked fish is edible with the E key and restores 10 hunger points.
- Expanded the furnace catalogue to iron, copper, and cooked fish.
- Added eight persistent achievements and five-biome discovery tracking in a dedicated atomic sidecar file.
- Added short optional WAV sound effects for mining, trading, fishing, smelting, and achievements; gameplay remains silent-safe when audio is unavailable.
- Kept world save format 2 and preserved compatibility with earlier world and player sidecar files.
- Added focused tests for trades, fishing rules, achievements, and new ore distribution.
- Full Maven/JUnit execution and actual OpenGL/audio runtime remain unverified when Maven and jMonkeyEngine dependencies are unavailable.


## 0.9.5 — Biomes, Farming & Furnace Update
- Added deterministic biome sampling for plains, forests, deserts, snowy tundra, and mountains.
- Expanded newly generated 0.9.5 worlds to radius 88; legacy profiles retain their original world radius and terrain generation.
- Added snow, sandstone, farmland, wheat crop, and furnace block IDs at the end of the block enum to preserve old IDs.
- Added a wooden hoe, seed drops from grass, plantable wheat crops, a 45-second maturation cycle, and harvest yields.
- Added a furnace block that smelts iron ore using coal ore into iron ingots; advanced iron equipment recipes now use ingots.
- Added bread crafting from wheat and bread as a food item.
- Persisted crop growth in a separate farm sidecar while retaining world save format 2.
- Added tests for biome variety/determinism, crop growth, farm persistence, furnace smelting, and the new default version.
- Full Maven test execution and OpenGL launch remain unverified where Maven/jMonkeyEngine dependencies are unavailable.

## 0.9.0 — Tools & Equipment Update

- Added wooden, stone, and iron pickaxes with tier-based mining requirements for stone, coal, iron, and gold ore.
- Added a wooden shovel with bonus gathering for grass, dirt, and sand.
- Added four equippable iron gear slots: helmet, chestplate, leggings, and boots.
- Added a simple protection score that reduces fall impact damage; no combat or creature attack system was introduced.
- Expanded crafting to 12 recipes and added paged recipe selection.
- Persisted equipped gear in the existing player sidecar file; kept world format 2 and old world loading behavior.
- Added tests for tool tiers, gathering bonuses, armor slots, fall-damage reduction, and gear persistence.
- Full jMonkeyEngine/OpenGL launch verification still depends on Maven and graphics dependencies being available.

## 0.8.5 — Inventory & Crafting Update
- Added an inventory overlay (I) listing mined blocks and crafted items.
- Added a crafting workshop (C), with recipes selected using keys 1–4.
- Added planks, sticks, crafting tables, and torches as crafted item types.
- Added atomic player inventory sidecar persistence without changing the world save format.
- Added tests for recipe consumption, recipe chains, and inventory persistence.

## 0.8.0 — Creatures Update
- Added persistent creature records for cows, sheep, pigs, chickens, villagers, and gentle wandering slimes.
- Added lightweight blocky 3D creature models and low-cost wandering behavior; movement is constrained to safe terrain and avoids water, lava, and blocked columns.
- New 0.8.0 worlds generate two small wooden village houses with villagers and paths.
- Added a visible creature counter to the HUD.
- Bumped world save format to 2 with a separate `--creatures--` section. Format 1 worlds remain loadable and receive an empty creature list; saving them in 0.8.0 writes format 2.
- Creature count is limited to 256 per world and saved creature coordinates are validated.
- Full Maven/OpenGL build remains unverified in this environment because Maven and cached jMonkeyEngine dependencies are unavailable.


## 0.7.5 — TEXTURE PACK ENGINE
- Добавлена загрузка настоящих PNG-текстур блоков из `textures/blocks/`.
- Поддерживаются общие текстуры и отдельные варианты верх/бок/низ.
- Воксельные меши чанков теперь содержат UV-координаты для текстурного атласа.
- Атлас 8×8 создаётся с nearest-фильтрацией; целевой размер тайла — 16×16.
- Встроенный Default pack создаёт стандартные пиксельные PNG-текстуры при первом запуске.
- PNG ограничены 256×256 и 2 МБ; ошибочные изображения заменяются fallback-текстурами.
- Сохранения мира и стабильные block ID не менялись.

# Changelog

## 0.7.0 — Customization Update

- Added launcher screens for settings, mods, and resource packs.
- Persisted resolution, fullscreen, VSync, FOV, and render-distance settings.
- Added a ServiceLoader-based BlockWorld mod API, isolated class loaders, per-mod config folders, and per-mod error logging.
- Added ZIP resource pack import with path traversal and unpacked-size checks.
- Palette overrides can recolor block vertex colors; PNG texture replacement is not implemented yet.
- World save format is unchanged.

## 0.6.3 — Startup Crash Diagnostics
- Added JVM fatal-error reports (`.logs/hs_err_pid*.log`) for native/VM crashes that cannot be caught as ordinary Java exceptions.
- Added heap-dump output under `.logs` for Java out-of-memory failures.
- Added startup lifecycle markers around display initialization, scene/material creation, HUD/input setup, and application shutdown.
- Added explicit uncaught-exception reporting for the game-launch thread and included crash-report locations in the error message.
- Updated the default profile and version metadata to 0.6.3.
- No gameplay or save-format change; world format remains 1 and previous worlds remain compatible.
- Full Maven/OpenGL validation was not possible in the build environment because Maven and cached jMonkeyEngine dependencies are unavailable.

## 0.6.2 — Nature Engine Safe Launch
- Catch startup failures including linkage/native errors in the launcher so the launcher is restored instead of silently remaining hidden.
- Added jMonkeyEngine render-thread error logging and a visible diagnostic dialog.
- Added frame-loop fault containment: on a runtime frame error the game window remains open with a visible message and full stack trace in `.logs/latest-run.log`.
- Made world-save and application cleanup resilient so a save failure cannot skip engine cleanup or the launcher callback.
- Windows `run.bat` now appends Maven output to the log, prints the final log tail, and always pauses so the result is not lost when double-clicked.
- Reduced the default game window to 1024×640 for a lighter startup on modest PCs.
- No save-format change; previous worlds remain on world format 1.

## 0.6.1 — Nature Engine Compiler Fix
- Fixed the reported Java compilation error in `BlockWorldGame.updateNature`: replaced the unavailable `FastMath.max(float, float)` call with `Math.max(float, float)`.
- Updated Maven artifact version, launcher banners, version catalog, and default selectable profile to 0.6.1.
- Added regression coverage for the 0.6.1 default profile and retained all 0.6.0 Nature Engine features.
- No save-format changes; existing worlds remain on world format 1.

## 0.5.2 — World Render Fix
- Improved spawn selection: the player prefers a clear grass/sand column and avoids starting inside tree trunks or leaves.
- Added a dark, readable HUD panel and resolution-aware positioning when the game window is resized.
- Added explicit spawn coordinates and live chunk diagnostics to the HUD.
- Captures mesh-generation and world-preparation exceptions in `.logs/latest-run.log` with full stack traces.
- Exposes the most recent chunk-mesh error instead of leaving the loading screen with no explanation.
- Keeps the 16×16 chunk engine and the version 0.4.0 save format.

## 0.5.1 build 2 — Compiler Fix
- Fixed the invalid Java string escape in `Launcher.java` that stopped Maven compilation (`illegal escape character` at line 213).
- Corrected the log path literal to `.logs\\latest-run.log`.
- No gameplay/save format changes; this build retains the 0.5.1 Chunk Engine Hotfix features.

## 0.5.1 — Chunk Engine Hotfix
- Set a more downward first-person spawn view so the terrain is visible immediately.
- Find a spawn column from actual saved blocks instead of assuming every world is centered at (0, 8).
- Replace an empty/zero-block save with a generated terrain world rather than leaving the player in an empty sky scene.
- Wait for the spawn chunk mesh before enabling gameplay.
- Show loaded chunk meshes, data chunks, and pending queue in the HUD.
- Corrected triangle-to-quad mapping for block targeting.
- Added a visible Maven startup log at `.logs/latest-run.log`.
- Kept the 0.5.0 chunk data and 0.4.0 save compatibility.


## 0.5.0 — CHUNK ENGINE

- Добавлена система горизонтальных чанков 16×16 блоков с корректной сеткой и для отрицательных координат.
- Вместо отдельной геометрии для каждого блока создаётся один объединённый меш на чанк.
- В меш попадают только открытые грани: внутренние поверхности между соседними блоками не рисуются.
- Добавлено потоковое отображение: чанки рядом с игроком создаются постепенно, дальние чанки снимаются со сцены.
- Добавлена настраиваемая дальность прорисовки: 3, 4, 6 или 8 чанков (F7).
- Генерация/чтение данных мира вынесены в фоновый поток; построение мешей выполняется небольшими порциями в игровом цикле.
- Добавлены FPS, число загруженных чанков, число чанков мира и дальность прорисовки в HUD.
- Отключены MSAA и VSync по умолчанию, чтобы не ограничивать FPS и снизить нагрузку на слабых ПК.
- Радиус нового мира 0.5.0 увеличен до 64 блоков от центра.
- Лимит сохранения увеличен до 1 000 000 блоков.
- Старый формат сохранения 0.4.0 сохранён: существующие `.bwworld` открываются без конвертации.
- Починен выбор блока по лучу: попадание теперь сопоставляется с конкретной гранью объединённого меша чанка.

## 0.4.0 — Survival Update

- Добавлен профиль выживания: ходьба от первого лица, обзор мышью, гравитация и прыжки.
- Добавлены здоровье, голод, стартовая еда и базовый инвентарь блоков.
- В режиме выживания разрушенные блоки попадают в инвентарь, а установка расходует запас.
- Добавлены имена миров, выбор сохранения и резервные копии перед заменой.
- Добавлен кэш высоты колонок.

## 0.3.0 — Adventure Update

- Добавлены пещеры, угольная, железная и золотая руда.
- Добавлены случайные seed и панель быстрого выбора из 9 слотов.
- Сохранение неизвестных ID блоков при открытии файла старой версией.

## 0.2.0 — Builder Update

- Добавлены деревья, листья, песок и увеличенный мир.

## 0.1.0 — Classic

- Первый прототип блочного 3D-мира с разрушением, установкой и сохранением.

## Известные ограничения 0.5.0

- Потоковая загрузка пока выгружает/загружает **отрисовку** чанков; данные всего сохранённого мира остаются в памяти.
- Сохранение игрока (здоровье, голод и инвентарь), крафт, мобы и полноценная боковая коллизия ещё не реализованы.
- Сборка и запуск требуют Java 17+ и Maven с доступом к Maven Central при первой сборке.


## 0.5.3 — Stable Render Diagnostics
- Добавлен временный визуальный loading-pad под точкой появления.
- Добавлен двусторонний режим chunk-материала и переключатель F8 для сравнения с back-face culling.
- В журнал добавлены точка появления, координаты стартового чанка и подтверждение успешного рендеринга.
- HUD показывает текущий режим обработки граней.


## 0.5.3 — Stable Render Diagnostics
- Added a temporary cyan loading pad below the spawn point, removed once the spawn chunk is rendered.
- Enabled two-sided chunk rendering by default for diagnosis; F8 toggles back-face culling for performance comparison.
- Added HUD state and log entries for spawn coordinates, spawn chunk, and successful world readiness.
- Updated launcher and version metadata to 0.5.3.


## 0.6.0 — Nature Engine
- Added WATER and LAVA block types with stable string IDs.
- Added winding surface rivers and underground lava pockets to newly generated worlds.
- Added continuous day/night lighting and sky-color transitions.
- Added R weather toggle (clear/rainy sky).
- Kept world format 1 for compatibility with earlier saves.
- Rain particle effects and flowing-fluid simulation remain future work.

## 0.7.0 — CUSTOMIZATION UPDATE
- Главный экран лаунчера дополнен разделами «Настройки», «Моды» и «Ресурс-паки».
- Настройки разрешения, полноэкранного режима, VSync, FOV и дальности чанков сохраняются в `~/.blockworld3d/config/settings.properties`.
- Добавлен API модов `BlockWorldMod` и изолированная загрузка включённых JAR через `ServiceLoader`; ошибки одного мода логируются отдельно.
- Менеджер модов импортирует, включает, отключает и удаляет JAR-файлы. Перед включением показывается предупреждение о выполнении кода.
- Добавлен импорт ZIP ресурс-паков с проверкой путей и лимитом распаковки 64 МБ.
- Ресурс-паки меняют палитру цветов блоков через `pack.properties`; PNG-текстуры пока не поддерживаются.
- Сохранения и формат мира 1 не менялись.
