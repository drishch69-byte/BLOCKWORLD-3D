# BLOCKWORLD 3D

**A 3D voxel sandbox game built with Java 17 and jMonkeyEngine 3.9.0.**

Explore a block-based world, gather resources, build structures, discover creatures, manage your inventory, and craft useful items. BLOCKWORLD 3D is an actively evolving sandbox project focused on expanding its world, gameplay systems, and customization options.

## Current release

**0.8.5 — Inventory & Crafting Update**

The current project package is available here:

[Download BLOCKWORLD 3D 0.8.5 (ZIP)](BLOCKWORLD_3D_0.8.5_INVENTORY_CRAFTING_UPDATE.zip)

## Features

- **Voxel world:** block-based terrain with a chunk system.
- **Building and mining:** place and break blocks while exploring.
- **Creatures:** animals and other world inhabitants.
- **Inventory:** collect and manage block stacks and crafted items.
- **Crafting:** create planks, sticks, crafting tables, and torches.
- **World saves:** save and load worlds, with player inventory data stored separately.
- **Customization:** main menu, settings, texture packs, and mod support.

## Controls

| Key | Action |
| --- | --- |
| `I` | Open or close inventory |
| `C` | Open or close crafting |
| `1–4` | Select a recipe while the crafting panel is open |
| `1–9` | Select a hotbar slot during normal gameplay |
| `F6` | Save the world |
| `F` | Interact |
| `E` | Eat |
| `ESC` | Pause menu |
| `F10` | Exit the game |

## Requirements

- Java Development Kit (JDK) 17
- Apache Maven
- A graphics environment that supports the OpenGL features required by jMonkeyEngine

## Build

1. Download and extract the ZIP archive above.
2. Open a terminal in the extracted project folder (the folder containing `pom.xml`).
3. Run:

```bash
mvn clean package
```

To run the automated tests:

```bash
mvn test
```

## Technology

- Java 17
- jMonkeyEngine 3.9.0
- Maven

## Roadmap

Planned areas for future development include tools and armor, furnaces, farming, additional blocks and creatures, new biomes, and expanded world-building options. These are planned features and are not all part of release 0.8.5.

## Project status

BLOCKWORLD 3D is a hobby development project. Features and controls may change between releases. Bug reports and suggestions are welcome through GitHub Issues.

---

**Explore. Build. Craft. Create your world.**
