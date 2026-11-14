# Changelog

## 1.9.28 — Village Economy
- Added limited market stock for ordinary villagers, farmers, blacksmiths, librarians, and guards; each successful purchase consumes the matching merchant stock and adds the payment to the village ledger.
- Farmers replenish bread and emeralds while working at their own farmland/crops; blacksmiths replenish iron ingots, tools, and guard-post gear; librarians replenish torches and lanterns.
- Trade UI now shows the remaining reward stock and distinguishes an empty market from a player who lacks payment items.
- Added atomic `.village.properties` sidecar persistence for stock and unfinished work cycles without changing world save format 2.
- Multiplayer hosts report active workers; the server alone advances and persists market stock, validates stock-limited trades, and broadcasts market snapshots to connected players.
- Added economy, persistence, backup/restore, version-gate, and server market exhaustion tests.
- Added the village economy sidecar to world backups while retaining compatibility with older backup manifests.

## 1.9.27 — Village Trading
- Added profession-specific offers for farmers, blacksmiths, librarians, and guards; ordinary villagers retain the original four-offer catalogue.
- Farmer offers now cover wheat, seeds, eggs, and wool; blacksmith offers cover metal ingots, tools, and shears; librarians trade useful supplies and relics; guards trade iron equipment.
- Added a 1.9.27 feature gate so earlier profiles keep the original village-trader offers.
- Multiplayer trade requests now include the selected villager profession; the server validates the profession and offer before consuming items and persisting the updated player profile.
- Added tests for profession catalogues, legacy behavior, version thresholds, and server-side profession trade persistence.
- World save format remains 2; no block, item, or creature IDs were changed.

## 1.9.26 — Village Homes & Social Life
- Added stable `bookshelf` and `villager_bed` block IDs with detailed cuboid models and texture-atlas slots 35 and 36.
- Profile 1.9.26+ generates a separate public library with double-height bookcase rows; each village home includes a furnace and two beds.
- Villagers find nearby beds at night, approach them using collision-aware movement and rest with a subtle breathing animation.
- During daytime, villagers periodically visit public workstations assigned to other professions.
- Added tests for furniture models, block ID save/load round trips, new village generation, old-profile compatibility, bed lookup and social visits.
- World save format remains 2; old village profiles do not generate the new furniture.

## 1.9.25 — Villager Workplaces
- Farmers search for nearby farmland or wheat, blacksmiths seek furnaces, and librarians visit village archive chests.
- Professioned villagers approach their nearest matching workplace during daytime and use a subtle working-arm animation.
- Workplace search is cached between checks instead of scanning nearby blocks every frame.
- The feature is gated to 1.9.25+ profiles, preserving earlier profile behavior.
- Workplace targets are ephemeral AI state; world save format 2 and existing creature records remain unchanged.

## 1.9.24 — Living Villages & Creature AI
- Villagers follow a simple day schedule: ordinary villagers rest at night and resume roaming after dawn.
- Non-guard villagers notice nearby hostile creatures and move away from danger using collision-aware navigation.
- Village guards remain on duty and continue to pursue threats.
- Added tests for daily activity windows, guard duty, hostile filtering, flee radius and invalid inputs.
- World save format remains 2; existing creature records are unchanged.

## 1.9.23 — Feature Progression Fix
- Newer profiles now inherit fox companions, animal breeding, animal products and smart feeders from the versions that introduced those features.
- Replaced fragile text-based version checks for pets, nature and progressive mining with numeric version comparisons.
- Added tests for feature introduction thresholds, newer-profile inheritance, numeric version ordering and malformed versions.
- World save format remains 2; existing creature and item IDs are unchanged.

## 1.9.22 — Chunk Streaming Priority
- Pending chunk meshes are re-sorted around the latest camera chunk whenever the player moves, so nearby terrain is built before stale work from the previous direction.
- A frame build budget of zero now correctly defers mesh generation instead of forcing one chunk to build.
- Added regression tests for camera movement prioritization and zero-budget streaming.
- Removed a duplicate import in the main game class.
- World save format remains 2; existing worlds and player data remain compatible.

## 1.9.21 — Launcher & Log Stability Fix
- Corrected the launcher release banner to match the current version and codename.
- Made runtime logging safe for relative paths without a parent directory.
- Added a regression test for parentless relative log paths.
- Kept world save format 2 and existing world/profile compatibility.

## 1.9.20 — Stability & Log Rotation
- Added automatic log rotation at 1 MiB; the previous run is retained as `latest-run.log.1`.
- Logging I/O failures remain non-fatal and never interrupt gameplay.
- Added tests for rotation, append behavior and preservation of the previous run.
- World save format remains 2; existing worlds and profile settings are unchanged.

## 1.9.19 — Launcher UI Polish
- Redesigned the mods manager with clear enabled/disabled states, mod versions, API-provider status and selected-mod details.
- Redesigned the resource-pack manager as a list with active-pack markers, metadata details, and refresh controls.
- Replaced the basic multiplayer prompt with separate host/join cards and redesigned the host, join and room windows.
- Added connection-state feedback, cleaner chat layout and tooltips for multiplayer fields.
- Added client-side validation for server ports and host player limits.
- Kept the existing world format and profile-specific settings unchanged.

## 1.9.18 — Launcher Backup Update
- Refreshed all launcher buttons with a consistent visual style, clearer tooltips and keyboard-friendly focus.
- Reorganized settings into **Screen**, **Graphics**, and **Performance** tabs with explanations for each option and a live approximate load indicator.
- Added a clear notice that saved graphics settings are applied the next time a world is launched.
- Added a manual **Create Backup** action to the launcher's world backup window.
- Manual backups include the world save and its player, achievement, pet, and animal progress sidecars.
- Added **F5** as a shortcut to refresh the launcher's saved-world list.
- Refreshed the launcher home screen to show the current 1.9.18 launcher changes and 1.9.17 combat highlights.
- Added the 1.9.18 selectable profile; world format remains 2.

## 1.9.17 — Melee Combat Update
- Left-click now hits a mob when the mob is in front of the player and no world block is between them.
- Multiplayer left-click can attack a nearby player; the server checks reach, facing direction and armor.
- Guest attacks against mobs are forwarded to the host for authoritative damage and creature synchronization.
- Block mining remains available when no mob or player is under the melee aim.
- Added tests for facing checks, multiplayer player attacks and guest-to-host creature attack forwarding.

## 1.9.16 — Lantern Lighting
- Lanterns now cast a warm glow on nearby block surfaces with smooth distance falloff up to 7 blocks.
- Placing or mining a lantern refreshes nearby visible chunk meshes so its glow appears or disappears immediately.
- Lighting works with PNG texture atlases and the fallback color renderer; world save format remains 2.
- Added tests for light falloff, range and light removal.

## 1.9.15 — Farmstead Decorations
- Replaced full-cube fence rendering with connected 3D posts and rails; neighboring fences and gates form continuous sections.
- Added a decorative lantern model, crafted from 2 copper ingots and 1 torch.
- Added a wooden scarecrow model, crafted from 4 planks, 2 sticks and 1 wheat.
- Extended **Y** building selection and **G** placement to fence, feeder, gate, lantern and scarecrow.
- Mining lanterns and scarecrows returns their crafted item; both block types are saved in world format 2.
- Expanded the inventory layout to three columns so the new item entries remain visible.
- Added model, recipe, mining-time and save/load tests.

## 1.9.14 — Gates & Smarter Animal Pens
- Added a craftable wooden gate: **4 planks + 2 sticks → 1 gate**.
- **Y** cycles between fence, feeder and gate; **G** places the selected building.
- Aim at a gate and press **F** to open or close it.
- The open gate swings to the side and no longer blocks player or creature movement; the closed gate blocks the doorway.
- Both gate states are stored as normal world blocks. The world save format remains **2**.
- Farm animals turn away sooner when they reach pen walls instead of repeatedly pushing into fences.
- Gate toggling and placement remain single-player-only until multiplayer block-state synchronization is implemented.


## 1.9.13 — Farm Pens & Smart Feeders
- Added wooden fence and feed trough block/item types with stable appended IDs and original texture patterns.
- Added crafting recipes: four fence blocks for 6 planks and 2 sticks; one feed trough for 5 planks and 2 sticks.
- Added automatic feeders with separate wheat and seed stock (up to 64 of each), stored in the existing animal sidecar.
- Nearby cows, sheep and pigs consume wheat; chickens and chicks consume seeds. One matching portion is consumed every 45 seconds when an eligible animal is nearby.
- Feeding advances baby growth or reduces a relevant breeding/product cooldown by 25 seconds.
- Added **Y** to switch selected building, **G** to place it, and **F** while aiming at a trough to refill it.
- Mining a trough returns its remaining wheat and seeds; removing a fence returns a fence item.
- Added save/load and rule tests for feeder stock and new blocks; world save format remains 2.
- Feeder placement and stock changes are single-player-only until these states are synchronized in multiplayer.

## 1.9.12 — Farm Products & Harvest
- Added separate **U** interaction for product collection, leaving **F** for animal breeding.
- Cows produce a milk bucket when the player has an empty bucket; they have a 90-second cooldown.
- Sheep produce two wool when the player has shears; wool regrows after 180 seconds. Shears are reusable.
- Chickens lay one egg every 75 seconds; no tool is needed to collect it.
- Milk can be consumed with **E**, restores 6 hunger, and returns the empty bucket.
- Added craftable empty buckets (3 iron ingots) and reusable shears (2 iron ingots).
- Added original 16×16 PNG icons for the empty bucket, shears, milk bucket, wool and egg.
- Persisted per-animal production timers in the animal sidecar and included them in world backups; world save format remains 2.
- Kept product collection single-player-only until per-animal timers and inventory changes are synchronized over multiplayer.

## 1.9.11 — Farm Life & Baby Animals
- Added breeding for adult cows, sheep, pigs, and chickens in single-player survival. Bring two adults of the same species together, carry two feed items, and press **F** near either parent.
- Cows, sheep, and pigs eat wheat; chickens eat wheat seeds.
- Added calves, lambs, piglets, and chicks with smaller models; babies grow into adults after three minutes of active game time.
- Added a five-minute breeding cooldown for both parents, persisted together with baby growth in a new atomic animal sidecar file.
- Nearby farm animals provide a small extra wheat-growth bonus alongside the existing rain and farmer bonuses.
- Added animal-sidecar backup/restore support and coverage for backup manifests from older releases.
- Kept world save format 2 and all existing creature IDs unchanged; baby creature IDs are appended.


## 1.9.10 — Fox Companions & Taming
- Added fox taming in survival mode: interact with a nearby fox using **F** while carrying cooked fish.
- Tamed foxes follow the player and can be told to **Stay** or **Follow** again with **N**, alongside existing dog companions.
- Saved tamed-fox state and companion commands in the existing pets sidecar; old dog commands and world save format 2 remain compatible.
- Kept fox taming disabled in earlier version profiles while preserving saved fox records when opening/saving a world in an older profile; multiplayer taming remains unavailable until companion-state synchronization is supported.
- Added regression tests for taming rules, companion eligibility, profile registration, and persistence of a tamed fox in Follow mode.

# Changelog

## 1.9.9 — Version Profile Manager
- Added a manager for built-in game-version profiles, with activation, profile folder access, and shortcuts to profile-specific settings and mods.
- Added independent settings files and enabled/disabled mod folders per version profile.
- Kept the old shared settings as the initial fallback and preserved the legacy shared mods folder; shared mods continue loading alongside profile mods while retaining their original shared config directory.
- Kept worlds, save format, profile IDs, resource packs, and existing shared user data unchanged.
- Added tests for safe profile paths and isolated mod install/disable/enable/remove operations.

## 1.9.9 — Launcher Refresh
- Refreshed the desktop launcher with a clearer release header, selected-profile details, and system/runtime summary.
- Added live world search by save name, profile version, and integrity status; favorites filtering can be combined with search.
- Reworked the home screen around the current 1.9.9 release and retained the existing world, mod, resource-pack, backup, multiplayer, profile, settings, and diagnostics tools.

## 1.9.9 — Enchanted Grove & Artifact Collection
- Added a rare deterministic **Enchanted Grove** surface biome with moss-covered ground, denser tree growth and sparse glow-crystal/fern markers.
- Added a separate 1.9.9 biome sampler and persisted `enchantedBiomes` metadata; existing 1.9.8 worlds retain their old biome layout and world save format 2.
- Added artifact milestones for the Ancient Relic, Sun Sigil, Moon Sigil and completion of the full collection.
- Guaranteed that the two opposite Ancient Temple caches provide one Sun Sigil and one Moon Sigil, while keeping their other deterministic rewards.
- Updated the achievements screen to show the correct biome target for older five-biome, expanded seven-biome and enchanted eight-biome profiles plus artifact collection progress.
- Added regression tests for biome gating/determinism, enchanted grove generation and save/load, profile selection, artifact milestones and guaranteed sigil pairing.

## 1.9.8 — Ancient Temples & Relics
- Added two remote sandstone temples with leveled foundations, entrance steps, rune pillars and open central arenas.
- Added two **Temple Wardens** with their own blocky model, 95 health, hostile behaviour and persistent creature records.
- Added a distinct **Ancient Cache** block and a deterministic reward table containing an Ancient Relic, a Sun or Moon Sigil, emeralds and metal ingots.
- Added three new persistent player items with original 16×16 PNG icons and inventory atlas mappings.
- Added shared-multiplayer support for Ancient Caches and their artifact rewards.
- Restricted temples, wardens and Ancient Caches to newly generated 1.9.8 worlds; preserved previous worlds and world save format 2.
- Added generation, loot, texture, inventory-item and save/load regression tests.

## 1.9.7 — Crystal Guardians & Hidden Treasures
- Added small crystal trees to Crystal Grotto rooms, with glowing trunks, amethyst cores and fern-like crowns.
- Added the distinct **Crystal Cache** block with an original 16×16 texture and a deterministic emerald/ingot/food reward table.
- Added the **Crystal Sentinel**, a larger crystal-armored cave boss with 70 health, a dedicated blocky model, hostile movement and a persistent world-creature record.
- Added cave-floor navigation for the Sentinel so it can move on underground arena floors instead of checking surface terrain.
- Added shared-multiplayer chest support for Crystal Caches; ordinary treasure chest generation and loot remain unchanged.
- Gated crystal trees, caches and the boss to newly generated 1.9.7 worlds; world save format remains 2.
- Added tests for boss generation and persistence, cave movement floor lookup, cache loot, stable texture slots and asset files.

## 1.9.6 — Cave Biomes & Glowflora
- Added deterministic underground biome sampling for **Crystal Grotto**, **Moss Cavern**, and **Underground Lake**.
- Crystal grottos contain bright luminous crystal blocks; moss caverns add cave moss and fern clusters; underground lakes carve larger rooms and add water pools.
- Added a cave-biome name to the HUD while the player is underground.
- Added original 16×16 textures and stable atlas slots for glowing crystals, cave moss, and cave ferns.
- Added optional caveBiomes world metadata without changing save format 2; pre-1.9.6 worlds remain unchanged when reopened.
- Added deterministic generator, texture, save/load, and version-profile regression tests.

## 1.9.5 — Crystal Caverns & Gem Veins
- Added seed-stable underground cavern rooms with crystal-lined walls and scattered water pools.
- Added ruby and sapphire ores to deep layers in the new profile; quantized coordinate rolls create short veins instead of isolated specks.
- Added new 16×16 pixel-art textures and stable texture-atlas slots for both ores.
- Added iron-tier mining requirements, mining timings, and focused JUnit coverage for ore selection, generated deposits, save/load, and default textures.
- Kept previous version ore rolls, existing block IDs, old world generation, and world save format 2 unchanged.

## 1.9.4 — Mining Feedback Update
- Survival mining now requires holding the left mouse button; releasing it or changing the targeted block cancels progress.
- Added a live mining progress indicator below the crosshair.
- Block hardness changes mining duration, and a suitable tool speeds up matching blocks.
- Creative mode keeps instant block removal; creature interaction on left-click remains available.
- Kept world format, block IDs, inventory files, and existing worlds unchanged.
- Added JUnit coverage for block hardness and tool speed rules.

## 1.9.3 — Inventory Tabs Update
- Split the survival inventory into dedicated **Resources** and **Equipment** tabs.
- Press **1** for resources and **2** for equipment; left/right arrows cycle between tabs.
- Prevented number keys from changing the hotbar while the inventory is open.
- Kept the existing item icons, player portrait, player sidecar format, and world save format unchanged.
- Added a version-catalog regression assertion for the 1.9.3 default profile.

## 1.9.2 — Classic Hotbar Controls
- Added mouse-wheel cycling through quick-access block slots, wrapping around at either end.
- Kept number keys 1–9 as direct slot shortcuts.
- Disabled wheel switching while paused or while inventory, crafting, chest, furnace, or trading interfaces are open.

## 1.9.1 — World Creator & Seeds
- Added a world-creation dialog with a world name, three map sizes, and an optional numeric seed.
- Added small, normal, and large generation radii; the selected size is used by terrain, structures, and map landmarks.
- Persisted the actual world radius in save metadata so custom-sized worlds reopen at their original size.
- Kept old saves compatible: files without the optional worldRadius property use their historical version radius.
- Kept the world save format number and block/creature records unchanged.

## 1.9.0 — Living World & Companions
- Renamed the player-facing game brand to Minecraft 3D.
- Kept Java package names, save headers, and the existing .blockworld3d data folder stable so earlier saves, mods, and settings remain compatible.
- Added a dog companion command: press N near the closest dog to switch between Follow and Stay.
- Dogs in Stay mode keep their position instead of automatically following the player; pressing N nearby switches them back to Follow.
- Saved non-default pet commands in a separate pets.properties sidecar file.
- Included pet sidecars in world backup, restore, and backup deletion operations.
- Kept world save format 2 and all existing creature IDs unchanged; older worlds without pet data keep the default Follow behavior.
- Added tests for pet-mode storage, corrupted entries, version selection, and pet-sidecar backup restoration.


## 1.2.0 — Exploration Update
- Fixed the gameplay mouse pointer: it is now hidden in both survival and creative modes, while pause, launcher, and settings screens keep the normal visible pointer.
- Added four deterministic exploration sites per world: a watchtower, ruins, a camp, and a mineshaft entrance; village markers are included in the map.
- Added treasure chest blocks with seed-and-coordinate-based loot. Press F while targeting a chest; opening removes the chest and awards loot to the player inventory.
- Added a toggleable 9×9 map and compass on B, showing player position, villages, nearby landmarks, direction, and distance.
- Expanded the block texture atlas to 16×16 tiles for the treasure chest without renumbering existing texture slots.
- Fixed a world-load cleanup bug: a malformed save no longer leaves biome flags or world metadata from the failed file.
- Added deterministic tests for loot, compass directions, structure markers, chest persistence, and failed-load cleanup.

## 1.1.0 — Wildlands & Crystal Caves
- Added taiga and swamp surface biomes to the new profile while keeping the legacy biome sampler unchanged for earlier profiles.
- Added underground crystal geodes with amethyst ore veins and small water pools.
- Added fox and turtle wildlife plus a dog companion that follows the player while keeping a short distance.
- Expanded villages with a market stall and a small crop garden.
- Added four switchable player outfit palettes on the V key.
- Added an optional extendedBiomes world property without changing world format 2; appended block and creature IDs remain stable.
- Added tests for the new biomes, underground deposits, new creatures, companion movement, atlas tile allocation, and save/load compatibility.

## 1.0.3 — Creature & Camera Polish
- Added a world-geometry ray check between the player and desired third-person camera position; the camera moves closer before block walls instead of clipping through them.
- Added procedural leg/arm gait animation for walking cows, sheep, pigs, chickens, and villagers.
- Added a gentle idle hop and squash/stretch motion to slimes.
- Improved villager face and clothing details with eyes, pupils, a hat brim, and a robe belt.
- Added unit coverage for camera obstruction distance and creature animation ranges.
- Kept world save format and creature record layout unchanged; animation state is transient.

## 1.0.2 — Third-Person Camera Update
- Added F5 camera switching between first-person and third-person views for survival profiles.
- Added a lightweight blocky player model with head, body, arms, legs, and simple facial details.
- Added walking limb animation and yaw-aligned model orientation.
- Separated the player's world position from the camera position so movement, creature spacing, block placement checks, and biome HUD data remain player-centered in third-person mode.
- Added unit tests for camera orbit and movement direction; preserved earlier profiles and all world/player save formats.

## 1.0.1 — Visual Inventory Update
- Added 23 separate 16×16 PNG item icons generated from the original item atlas.
- Replaced the long crafted-item text list in inventory with a visual side panel showing each icon, localized item name, and current count.
- Added the player sprite as a character portrait in the inventory/workshop panel.
- Kept the visual item panel available while the crafting workshop is open.
- Made 1.0.1 the default profile while keeping 1.0.0 selectable.
- Kept world save format 2 and existing player/farm/achievement sidecar formats unchanged.
- Added tests that verify every individual item icon is a valid 16×16 PNG.

## 1.0.0 — Trading, Fishing & Achievements
- Added three 16×16 PNG creature textures for the fox, turtle, and dog, bringing the roster to nine textured creature types; nearest-neighbour filtering and safe color fallback remain available.
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
