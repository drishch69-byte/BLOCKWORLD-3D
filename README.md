# BLOCKWORLD 3D

**A 3D voxel sandbox game built with Java 17 and jMonkeyEngine 3.9.0.**

Explore a block-based world, gather resources, build structures, discover creatures, farm crops, craft equipment, trade with villagers, fish, and unlock achievements.

> BLOCKWORLD 3D is an independent voxel sandbox project. It is not an official Minecraft product and does not contain Minecraft source code.

## Current development version

**1.0.0 — Trading, Fishing & Achievements**

The 1.0.0 project package has been prepared and checked as a ZIP in the development conversation. The archive currently stored in this repository is an older **0.8.5** snapshot; it has intentionally been kept so it is not mistaken for the newer build.

### Features in version 1.0.0

- **Voxel world:** chunk-based block terrain with five biomes: plains, forests, deserts, snowy tundra, and mountains.
- **Building and mining:** place and break blocks and explore underground resources.
- **Creatures and villages:** animals, villagers, and wandering slimes.
- **Inventory and crafting:** collect resources and craft useful items.
- **Tools and armor:** pickaxes with mining tiers, a shovel, and equippable iron armor.
- **Farming and furnace:** prepare farmland, grow wheat, harvest crops, and smelt ores or cook fish.
- **Villager trading:** exchange selected resources for emeralds and supplies.
- **Fishing:** craft a fishing rod, catch fish, and cook them.
- **New ores:** copper and emerald.
- **Achievements:** track progress for mining, crafting, trading, fishing, farming, and exploring.
- **Customization:** settings, mods, and resource packs with PNG block textures.
- **Persistence:** world data and player sidecar data are saved separately; the world format remains 2.

## Controls

| Key | Action |
| --- | --- |
| `I` | Open or close inventory |
| `C` | Open or close crafting |
| `M` | View achievements |
| `T` | Switch available tool |
| `P` | Cast fishing rod while aiming at nearby water |
| `F` | Interact with a villager or furnace |
| `G` | Use farming / place a furnace where applicable |
| `E` | Eat food |
| `F6` | Save the world |
| `ESC` | Pause menu |
| `F10` | Exit the game |

Some actions depend on the selected version profile and the item currently held.

## Download

The ZIP currently stored in this repository is the **0.8.5 Inventory & Crafting Update** snapshot:

[Download BLOCKWORLD 3D 0.8.5](BLOCKWORLD_3D_0.8.5_INVENTORY_CRAFTING_UPDATE.zip)

The 1.0.0 package is newer than that repository snapshot. Check the project conversation or the Releases page for the latest package until the 1.0.0 archive is attached here.

## Build from source

Requirements:
- JDK 17
- Apache Maven
- A desktop graphics environment supporting the OpenGL features required by jMonkeyEngine

From the folder containing `pom.xml`, run:

```bash
mvn clean package
mvn test
```

Launch scripts are provided for Windows (`run.bat`) and Linux/macOS (`run.sh`).

## Technology

- Java 17
- jMonkeyEngine 3.9.0
- Maven

## Roadmap

See [ROADMAP.md](ROADMAP.md) for proposed future features. Roadmap items are ideas, not promises or already implemented features.

## Contributing

Bug reports, ideas, and improvement suggestions are welcome. Use the GitHub issue templates when opening an issue.

**Explore. Build. Craft. Create your world.**
