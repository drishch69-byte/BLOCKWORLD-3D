# BLOCKWORLD 3D — Multiplayer World Sync Preview

The launcher can create a password-protected LAN server or join one by address and port. Once the selected world has loaded, the host publishes its complete block map to the server. New players receive that map when they join.

## Included in this preview
- A visible block-style avatar for each connected player, with a name label
- Player position and facing updates during gameplay
- Synchronized block placement and block removal
- An authoritative server-side copy of the current block map
- Full block-map transfer to newly joined players
- Lobby chat before entering the world
- Optional server password and a player limit of up to 32

## How to play together
1. Create or select a saved world in the launcher.
2. The host chooses **СЕТЕВАЯ ИГРА → Создать LAN-сервер**, connects, then clicks **ЗАПУСТИТЬ ВЫБРАННЫЙ МИР**.
3. Friends on the same trusted network choose **Подключиться**, enter the host computer's local network address, port, and password, then launch the same profile/world slot from their launcher.
4. The host's loaded world is copied to joining players. Block changes and player movement are relayed by the server.

## Current limitations and safety
This is an early multiplayer preview, not a complete dedicated-server release. Inventory, crafting transactions, health, hunger, farming timers, achievements, mob positions, and creature AI are still local to each player. The server's block map is held in memory and the host's local world file is saved when the host exits. The protocol is not encrypted; use trusted LANs only, choose a password, and do not share public IP addresses or passwords in public chats. Do not expose the server to the public internet.
