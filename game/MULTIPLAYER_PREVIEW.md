# BLOCKWORLD 3D — Multiplayer Server Save & Creature Sync Preview

The launcher can host a password-protected LAN world or join one by address and port. The selected host world is saved separately from the ordinary single-player save.

## Included
- Block-style player avatars with names and position updates
- Shared block placement and removal
- A host-authoritative creature simulation; creature states and movement animations are relayed to other players
- Complete world transfer for joining players, including block data, seed, biome flags, radius, and creature list
- Server-side persistence of blocks and creatures
- Host migration when the original host disconnects
- A separate server-world file for each selected world

## Server world files
The server stores hosted copies under:

`~/.blockworld3d/servers/`

Each world gets a separate `.bwserver` file based on its world filename. The server writes block edits immediately, periodically stores creature positions, and flushes the latest world when the server closes. On the next host session, the saved server world is restored and sent to the joining host.

## How to play
1. Select a world in the launcher.
2. Choose **СЕТЕВАЯ ИГРА → Создать LAN-сервер**.
3. Connect as host and choose **ЗАПУСТИТЬ ВЫБРАННЫЙ МИР**.
4. Friends on the same trusted network use **Подключиться**, enter the host's local network address, port, and password, then launch the multiplayer world.
5. The server sends the current world snapshot to every new player. Block changes and host-controlled creature states are relayed while playing.

## Personal inventories and current limitations
Inventory, armor, crafting results, health, hunger, farming timers, and achievements remain personal to each player and are saved in the local `.player.properties` and other sidecar files for that world. They are not yet synchronized to a central server or transferred between different computers. Crafting is not a shared transaction system. Creature updates are sent four times per second; interpolation and prediction are still basic. This is an early multiplayer preview, not a complete dedicated-server release.

## Network safety
The TCP protocol is not encrypted. Use trusted local networks only, choose a password, and do not publish public IP addresses or passwords. Do not expose the preview server to the public internet.
