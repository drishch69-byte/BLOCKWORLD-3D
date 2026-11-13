# BLOCKWORLD 3D — Multiplayer Server Profiles & Crafting Preview

The launcher can host a password-protected LAN world or join one by address and port. Hosted block worlds and personal player profiles are stored separately for each selected world.

## Included
- Block-style player avatars, position updates, shared block placement/removal, and host-controlled creature movement
- Server persistence for world blocks and creature states
- Personal server profiles for block inventory, crafted items, and equipped armor
- Profile restoration when a player reconnects with the same nickname
- Server-validated crafting recipes and village-trader exchanges
- Inventory updates uploaded after mining, block placement, smelting, fishing, eating, and armor changes
- Duplicate active nicknames are rejected to prevent two connected players from writing the same profile at once

## Storage
Hosted world files are stored under:

`~/.blockworld3d/servers/`

Player profiles for a hosted world are stored alongside that world's server file in a folder ending with `.bwserver.players/`. Each sanitized nickname has a separate `.profile` file. Writes use a temporary file and atomic replacement where supported.

## How profiles work
1. On first connection, the server reports that the nickname has no profile. The client uploads its current local inventory and armor.
2. On later connections, the server sends the saved profile to that nickname. The client applies it and refreshes its local sidecar cache.
3. When a player crafts an item or trades with a village trader, the client sends a request, not a claimed result. The server checks the saved inventory, applies the recipe, saves the new profile, and returns the updated data.
4. Normal inventory changes are uploaded as a complete personal profile snapshot. While the profile is loading or a server transaction is pending, inventory-changing actions are paused.

## Crafting and trading
The server supports the starter, advanced, and release 1.0 recipe catalogues plus the four village-trader offers. Recipes are checked against the server-held player profile. Crafting and villager trades are server-authoritative in this preview. Direct player-to-player item exchange, shared chests, central health/hunger, and inventory drops on death are not implemented yet.

## Nicknames and network safety
Player profiles are keyed by nickname, not by a separately authenticated user account. The shared server password controls entry to the room, but anyone who knows it could reconnect using an unoccupied nickname belonging to another player. Use unique nicknames, never reuse account passwords, and only host on a trusted LAN. The TCP protocol is not encrypted; do not expose the preview server to the public internet or share its address and password publicly.
