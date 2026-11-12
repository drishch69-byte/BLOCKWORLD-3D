# Шаблон ресурс-пака BLOCKWORLD 3D 1.0.0

1. Оставь `pack.properties` в корне ZIP.
2. Помести PNG-файлы в `textures/blocks/`, имена совпадают со стабильными ID блоков.
3. Можно добавить варианты `_top`, `_side`, `_bottom`; они имеют приоритет над общей текстурой блока.
4. Архивируй содержимое этой папки так, чтобы `pack.properties` был в корне ZIP.
5. Импортируй архив в игре: «Ресурс-паки» → «Импорт ZIP» → выбери пак → «Применить». Перезапусти мир. Для отсутствующих PNG движок использует стандартные текстуры.

Исходное разрешение PNG: 1×1–256×256, целевой размер в атласе: 16×16 (nearest-neighbor). В BLOCKWORLD 3D 1.0.0 поддерживаются блоки: grass, dirt, stone, wood, leaves, sand, coal_ore, iron_ore, gold_ore, water, lava, snow, sandstone, farmland, wheat_crop, furnace, copper_ore и emerald_ore.
