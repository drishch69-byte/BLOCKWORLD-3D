# BLOCKWORLD 3D — PNG texture assets

This folder contains original pixel-art PNG resources.

- `src/main/resources/Textures/Creatures/`: 16×16 textures for cow, sheep, pig, chicken, villager and slime. The creature renderer applies the matching nearest-filtered texture to each block-shaped body part.
- `src/main/resources/Textures/Player/player_skin.png`: editable 64×64 explorer skin atlas. The current camera is first-person, so there is not yet a third-person player model to display it on.
- `src/main/resources/Textures/Items/items_atlas.png`: 128×64 atlas with 23 item icons. Each icon occupies one 16×16 tile.
- `src/main/resources/Textures/Items/items.properties`: stable item ID to atlas tile mapping. IDs match `ItemType`.

The artwork is original to BLOCKWORLD 3D. Textures use nearest-neighbour filtering for crisp pixel edges.
