# BLOCKWORLD 3D — Multiplayer Preview

The launcher now provides a TCP multiplayer lobby with an optional password, player limit, LAN hosting, joining by host/port, and shared chat.

## LAN
1. On the host computer, open **СЕТЕВАЯ ИГРА → Создать LAN-сервер**.
2. Share the host computer's local network address and selected port with friends on the same trusted Wi-Fi/LAN.
3. Friends choose **Подключиться** and enter the address, port, nickname, and password.

For internet connections, the host needs a reachable network endpoint. Do not publish a public IP or server password in public chats.

## Preview limitations and security
This is a lobby/chat preview, not full Minecraft-style gameplay multiplayer. Player movement, blocks, inventory, creatures, and world saves are not synchronized. The protocol is not encrypted and should only be used on trusted networks; do not send private information. Later work needs authoritative world replication, encrypted transport, server discovery, and permissions.
