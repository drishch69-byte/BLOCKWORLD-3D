# CUBEVERSE 3D — PNG texture assets

This folder contains original pixel-art PNG resources.

- `src/main/resources/Textures/Creatures/`: 16×16 textures for cow, sheep, pig, chicken, villager, slime, fox, turtle and dog. The creature renderer applies the matching nearest-filtered texture to each block-shaped body part.
- `src/main/resources/Textures/Player/player_skin.png`: editable 64×64 explorer skin template.
- `src/main/resources/Textures/Player/player_sprite.png`: ready-to-use 32×48 front-facing explorer sprite. The sprite is used as a player portrait; the block-shaped third-person model is rendered separately.
- `src/main/resources/Textures/Items/items_atlas.png`: 128×64 atlas with 23 item icons. Each icon occupies one 16×16 tile.
- `src/main/resources/Textures/Items/icons/<item-id>.png`: individual 16×16 PNG crops used by the inventory and crafting workshop. The UI shows the localized item name and current count next to each icon.
- `src/main/resources/Textures/Player/player_sprite.png`: the player portrait shown in the inventory side panel.
- `src/main/resources/Textures/Items/items.properties`: stable item ID to atlas tile mapping. IDs match `ItemType`.

`game/src/main/resources/Textures/Blocks/` is populated from the selected resource pack at startup. The block atlas is now 256×256 (16×16 tiles) with three face tiles per block, including the 1.2.0 treasure chest. Existing block slots 0–20 keep their positions; the chest is appended at slot 21.

The artwork is original to CUBEVERSE 3D. Textures use nearest-neighbour filtering for crisp pixel edges.
