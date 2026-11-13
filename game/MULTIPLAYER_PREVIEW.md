# BLOCKWORLD 3D — Multiplayer Preview 1.8.0

## Included
- Player avatars, shared block placement/removal, host-controlled creature movement
- Persistent world, per-player inventories, shared chests and reciprocal item trades
- Server accounts with PBKDF2 verifiers and one-time HMAC challenge-response
- Server-validated player combat through the `/hit Ник` chat command
- Host-side AI for hostile creatures and village guardians

## PvP

Press **Enter** and send:

`/hit Ник`

The server checks that both players have submitted positions, that they are within 3.25 blocks, and that the attacker is not inside the short attack cooldown. Target armor reduces damage. Updated health is saved in the target's player profile and sent to that client.

## Creatures

The host simulates zombie, skeleton, spider, villager, guard and iron-golem behavior and broadcasts creature snapshots. A client can attack mobs only when it is the host; other clients receive mob movement and removal updates. Offline play supports local mob combat.

## Accounts and data

The launcher supports account creation and sign-in. Player inventory, equipped armor, health, hunger, food, world blocks, mobs, and shared chest contents use separate per-world files under `~/.blockworld3d/servers/`.

## Network limitations

The preview TCP protocol does not use TLS. Account registration sends a derived verifier over the connection. Use a unique password and a trusted LAN only; do not expose this preview server to the public internet. A dedicated-server simulation and fully authoritative multiplayer mob combat remain future work.
