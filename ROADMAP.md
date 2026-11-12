# BLOCKWORLD 3D — Roadmap

This roadmap describes possible future development. Items below are proposals and are **not part of version 1.0.0 unless explicitly listed in the README**. Priorities may change after testing and feedback.

## 1.1.0 — Living World
- Day/night cycle with configurable time speed.
- Weather: rain, snow, fog, and clear skies.
- Biome ambience and optional music controls.
- Improved creature schedules and simple animal interactions.
- Persist time-of-day and weather state in world saves.

## 1.2.0 — Exploration Update
- More cave layouts and underground mine structures.
- Small puzzle dungeons with keys and treasure chests.
- Surface structures: watchtowers, camps, ruins, and landmarks.
- More decorative blocks and rare loot.
- Map markers and a simple compass.

## 1.3.0 — Animals & Companions
- Tameable companions with follow/stay behavior.
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
