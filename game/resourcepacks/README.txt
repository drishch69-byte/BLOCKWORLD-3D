BLOCKWORLD 3D — РЕСУРС-ПАКИ 0.7.5

Импорт ZIP через меню «РЕСУРС-ПАКИ». В корне ZIP должен находиться pack.properties.
Установленные паки: %USERPROFILE%\.blockworld3d\resourcepacks (Windows) или ~/.blockworld3d/resourcepacks (Linux/macOS).

PNG-ТЕКСТУРЫ
Положи файлы в textures/blocks/ внутри ZIP:
  grass.png, dirt.png, stone.png, wood.png, leaves.png, sand.png,
  coal_ore.png, iron_ore.png, gold_ore.png, water.png, lava.png

Размер текстуры — любой от 1×1 до 256×256 пикселей. При загрузке она приводится к 16×16 методом nearest-neighbor. Для каждого блока можно указать отдельные стороны:
  grass_top.png, grass_side.png, grass_bottom.png
Если face-вариант отсутствует, используется <block-id>.png; если нет и его — цвет из block.<block-id>=#RRGGBB либо стандартная текстура.

В pack.properties можно задать pack.name, pack.description и цветовые fallback-значения:
  block.grass=#55AA33
  block.dirt=#93613A
  block.stone=#888888

На один блок зарезервированы три ячейки атласа (верх/бок/низ). Текстуры фильтруются методом Nearest, чтобы пиксельный рисунок оставался чётким. Большие/повреждённые PNG пропускаются, остальные блоки продолжают использовать запасные текстуры.
После выбора «ПРИМЕНИТЬ» закрой текущий мир и запусти его снова. Формат сохранений мира не зависит от ресурс-пака.
