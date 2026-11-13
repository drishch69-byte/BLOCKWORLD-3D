# BLOCKWORLD 3D — Multiplayer Accounts, Shared Chests & Trading Preview

The launcher can host a password-protected LAN world or join one by address and port. Select either **Create account** or **Sign in** in the launcher. An account uses the in-game nickname as its username and is stored in the account database for that hosted world.

## Included
- Block-style player avatars, position updates, shared block placement/removal, and host-controlled creature movement
- Server persistence for world blocks and creatures
- Per-player profiles with block inventory, crafted items, equipped armor, health, hunger, and food count
- Server-validated crafting recipes and village-trader exchanges
- Server accounts with salted PBKDF2 verifiers and one-time HMAC challenge-response login
- Shared chests with persistent contents, deposits, and withdrawals
- Reciprocal player-to-player item trades validated and saved by the server

## Storage
The selected hosted world has its own data files under:

~/.blockworld3d/servers/

- .bwserver — hosted block world and creatures
- .bwserver.accounts — account salts and PBKDF2 verifiers
- .bwserver.players/ — per-player inventories, armor, and survival stats
- .bwserver.chests — shared chest contents indexed by world coordinates

Profile, chest, and world updates use temporary files and atomic replacement where supported.

## Accounts
1. Enter a nickname and choose **Create account** on first connection.
2. Use the same nickname and choose **Sign in** on later connections.
3. Account passwords must be 8–128 characters. Use a unique password that is not used anywhere else.
4. The room password (if the host sets one) and account password are separate.

The server stores a per-account salt and a PBKDF2-HMAC-SHA256 verifier (210,000 iterations). Login uses a fresh server nonce and HMAC proof instead of sending the account password as ordinary text. However, this preview does not use TLS: account registration sends the derived verifier over the unencrypted TCP connection. A network observer may capture that verifier and impersonate the account. This is **not suitable for public internet servers**; use only a trusted LAN and a unique password.

## Shared chests
Look at a treasure chest and press **F**. The server initializes its contents from the world seed and coordinates and keeps the chest block in place. The contents are shared by all connected players and persist after server restart.

- **1–9** selects an item entry
- **Z** takes one item from the chest
- **X** puts one item from your inventory into the chest
- **Left/Right** changes pages
- **F** closes the chest

Breaking a shared chest removes its saved contents. Chest contents are not dropped into the world.

## Player trading
Press **Enter** in game to open multiplayer chat. To exchange items, send:

/trade Nick ITEM_TO_GIVE COUNT ITEM_TO_RECEIVE COUNT

For example:

/trade Builder WHEAT 4 BREAD 2

The server verifies that both active players have the requested items, updates both profiles, and writes the results before confirming the exchange. Counts must be between 1 and 64. This preview trades crafted ItemType items, not placeable blocks.

## Survival stats and current limitations
Health, hunger, and food count are saved in the server player profile and sent to other connected players. The client still simulates its own survival damage locally; the server stores the latest reported values rather than running an authoritative physics/health simulation. Remote player health and hunger are tracked in network state but are not yet shown above avatars. Direct block drops, death inventory drops, shared crafting stations, and full dedicated-server administration remain future work.

## Network safety
The TCP protocol is not encrypted. Do not expose the preview server to the public internet, and do not publish its address or room password. The account challenge-response avoids sending the account password as plain text, but it does not replace TLS.
