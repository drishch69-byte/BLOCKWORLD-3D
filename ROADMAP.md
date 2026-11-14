# Minecraft 3D — Roadmap

This roadmap records implemented releases and future proposals. Versions 1.1.0, 1.2.0, 1.8.0, 1.8.1, and 1.9.0 through 1.9.15 are present in the current source. Older proposed milestones may be delivered out of sequence as the project evolves.

## 1.1.0 — Wildlands & Crystal Caves (released)
- Added taiga and swamp biomes, crystal-lined underground caves, and underground water pools.
- Added foxes, turtles, and a dog companion that follows the player.
- Expanded villages with a market stall and garden.
- Added four player color palettes, selectable with V.
- Kept world save format 2 with an optional extended-biome property.

## 1.9.15 — Farmstead Decorations (in progress)
- Build connected 3D wooden fences with visible posts and rails.
- Add a decorative lantern and a wooden scarecrow for farmyards and villages.
- Expand the building selector and preserve compatibility with world save format 2.

## 1.9.14 — Gates & Smarter Animal Pens (released)
- Craft wooden gates with 4 planks and 2 sticks; use **Y** to select a building and **G** to place it.
- Aim at a gate and press **F** to open or close it.
- Open gates remain visible but are non-solid, allowing players and animals to pass through.
- Save open and closed gate states in world format 2.
- Improve animal turns around pen walls; keep gate editing single-player-only until multiplayer synchronization is ready.

## 1.9.13 — Farm Pens & Smart Feeders (released)
- Craft and place wooden fence blocks to build animal pens.
- Add feeders holding up to 64 wheat and 64 seeds, saved with animal sidecar data.
- Automate species-appropriate feeding for nearby farm animals.
- Reduce relevant baby growth and animal cooldown timers when a feed portion is consumed.

## 1.9.12 — Farm Products & Harvest (released)
- Collect milk from cows using empty buckets, wool from sheep using reusable shears, and eggs from chickens.
- Save per-animal product timers with the existing animal sidecar and include them in world backups.
- Keep product collection on U and breeding on F so the interactions do not conflict.
- Add original pixel-art inventory icons for the five new farm items.

## 1.9.11 — Farm Life & Baby Animals (released)
- Breed cows, sheep, pigs, and chickens using wheat or wheat seeds.
- Add calves, lambs, piglets, and chicks that grow into adults after three minutes of active game time.
- Persist baby growth and parent cooldowns in a dedicated animal sidecar included in world backups.
- Give wheat crops a small growth bonus when farm animals are nearby.

## 1.9.10 — Fox Companions & Taming (released)
- Tame a nearby fox with cooked fish using F in single-player survival.
- Command dogs and tamed foxes to Follow or Stay with N.
- Persist fox taming and companion modes in the existing pet sidecar; keep world save format 2 unchanged.

## 1.9.1 — World Creator & Seeds (released)
- Added small, normal, and large world-size choices in the new-world dialog.
- Added optional numeric seeds for repeatable generation.
- Saved the chosen world radius as optional metadata while preserving older saves.

## 1.9.0 — Living World & Companions (released)
- Added Follow and Stay commands for the nearest dog companion using N.
- Persisted pet commands separately from the world file and included them in world backups.

## 1.2.0 — Exploration Update (released)
- Added mineshaft entrances, a watchtower, ruins, and camps to each generated 1.2.0 world.
- Added lootable treasure chests with deterministic, save-friendly inventory rewards.
- Added a toggleable map, structure markers, nearest-site distance, and a compass.

## 1.3.0 — Animals & Companions
- Taming and stay/follow commands for more creature types.
- Optional animal breeding and baby growth.
- More wildlife with biome-aware spawning.
- Additional farm products and recipes.
- Creature information panel.

## 1.4.0 — Transportation
- Boats for water travel.
- Minecarts and simple rail tracks.
- Improved swimming and water movement.
- Bridges, signs, and travel markers.

## 1.5.0 — Builder & Logic
- Tool durability and repair recipes.
- Additional armor materials and visible equipment models.
- Creative-mode structure blueprints.
- Logic blocks, switches, doors, and simple circuits.
- Block rotation and placement preview.

## 2.0.0 — World Creator
- Configurable world sizes and generation presets.
- More biomes and smoother biome transitions.
- World customization before creation.
- Mod API compatibility checks and clearer mod error reports.
- Performance and accessibility settings.
- Optional LAN co-op prototype only after save synchronization and connection safety are designed.

## Development priorities
1. Keep existing world saves readable; add migration tests whenever save data changes.
2. Keep gameplay rules in small testable classes where practical.
3. Do not mark features complete until they have been verified.
4. Keep older version profiles behaving as documented.
5. Prefer optional settings for features that change generation or difficulty.

## Suggest a feature
Open a [Feature Request](https://github.com/drishch69-byte/BLOCKWORLD-3D/issues/new?template=feature_request.md) and describe the problem it solves, how it should work, and whether it should be optional.
