# BLOCKWORLD 3D

[![Java CI](https://github.com/drishch69-byte/BLOCKWORLD-3D/actions/workflows/java-ci.yml/badge.svg)](https://github.com/drishch69-byte/BLOCKWORLD-3D/actions/workflows/java-ci.yml)

**A 3D voxel sandbox game built with Java 17 and jMonkeyEngine 3.9.0. Current release: 1.8.0 — Village Defense & Combat.**

Explore a block-based world, gather resources, build structures, discover creatures, farm crops, craft equipment, trade with villagers, fish, and unlock achievements.

> BLOCKWORLD 3D is an independent voxel sandbox project. It is not an official Minecraft product and does not contain Minecraft source code.

## Current release

**1.8.0 — Village Defense & Combat**

- Wooden, stone and iron swords; melee attacks against nearby creatures.
- Zombies, skeletons and spiders; iron golem and villager guard protect village residents.
- Farmer, blacksmith, librarian and guard villager professions.
- Fifteen named flowers, tall and short grass, and ferns in seeded meadows.
- Rain and nearby farmers boost wheat growth; PvP command is checked by the LAN server.

**Earlier: 1.2.0 — Exploration Update**

- Seven surface biomes: plains, forests, deserts, snowy tundra, mountains, taiga, and swamps (the two new biomes appear in the 1.1.0 profile).
- Chunk-based voxel world, mining, and building.
- Nine creature types, including foxes, turtles, and a dog companion that follows the player.
- Crystal caves, mineshafts, watchtowers, ruins, camps, lootable treasure chests, and an on-screen compass map (B).
- Inventory, crafting, tools, and iron armor.
- Farming, wheat, furnaces, smelting, and cooked food.
- Fishing, copper and emerald ores, and achievements.
- Settings, mods, and PNG resource packs.
- Separate persistence files for world and player progression data.

## Download

[**Download latest source ZIP (1.8.0)**](https://github.com/drishch69-byte/BLOCKWORLD-3D/archive/refs/heads/main.zip)

The complete source project is available in [game/](game/), including Java sources, JUnit tests, sound resources, example resource packs, and an example mod. The older [1.0.0 snapshot ZIP](BLOCKWORLD_3D_1.0.0_TRADING_FISHING_ACHIEVEMENTS_UPDATE.zip) is retained as a previous snapshot.

## Build and test

Requirements:
- JDK 17
- Apache Maven
- A desktop graphics environment supporting the OpenGL features required by jMonkeyEngine

From the source folder:

```bash
cd game
mvn clean verify
```

The GitHub Actions Java CI workflow runs the same Maven verification on pushes and pull requests that change the game source. The current source build is verified by the Java 17 CI workflow; the job compiles the project, runs JUnit tests, and packages the application.

Launch scripts are provided for Windows (run.bat) and Linux/macOS (run.sh).

## Project documentation

- [Player guide](docs/PLAYER_GUIDE.md) — controls, survival loop, and save reminders.
- [Roadmap](ROADMAP.md) — proposed features for versions 1.1.0 through 2.0.0.
- [Contributing](CONTRIBUTING.md) — development and testing guidelines.
- [Security policy](SECURITY.md) — especially important when using Java mods.

## Technology

- Java 17
- jMonkeyEngine 3.9.0
- Maven

## Contributing

Bug reports, ideas, and improvement suggestions are welcome. Use the GitHub issue templates when opening an issue. Pull requests are checked by the Java 17 Maven workflow.

**Explore. Build. Craft. Create your world.**
