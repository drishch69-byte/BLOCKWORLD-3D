package com.blockworld.net;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.ArmorSlot;
import com.blockworld.core.ItemType;
import com.blockworld.core.WorldCreature;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** TCP multiplayer client supporting lobby chat, player presence, and world block replication. */
public final class MultiplayerClient implements AutoCloseable {
    private static final int MAX_PROTOCOL_LINE = 2048;
    private final Socket socket = new Socket();
    private final Object eventLock = new Object();
    private final ConcurrentLinkedQueue<String> pendingEvents = new ConcurrentLinkedQueue<>();
    private volatile Consumer<String> eventListener;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected;
    private volatile int playerId;
    private volatile int hostPlayerId;
    private volatile boolean serverWorldReady;

    public void connect(String host, int port, String password, String playerName,
                        Consumer<String> chatMessages) throws IOException {
        connect(host, port, password, playerName, null, false, chatMessages);
    }

    public void connect(String host, int port, String password, String playerName,
                        String accountPassword, boolean createAccount,
                        Consumer<String> chatMessages) throws IOException {
        if (connected) throw new IllegalStateException("Already connected");
        if (socket.isClosed()) throw new IOException("Client is already closed");
        if (host == null || host.isBlank() || host.length() > 253) throw new IllegalArgumentException("Invalid host");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        Consumer<String> chats = chatMessages == null ? ignored -> { } : chatMessages;
        try {
            socket.connect(new InetSocketAddress(host.trim(), port), 5000);
            socket.setSoTimeout(15000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            String encodedPassword = encode(password == null ? "" : password);
            String safeName = playerName == null ? "Player"
                    : playerName.replaceAll("[^A-Za-zА-Яа-я0-9_-]", "").strip();
            if (safeName.isBlank()) safeName = "Player";
            if (safeName.length() > 20) safeName = safeName.substring(0, 20);
            out.println("AUTH " + encodedPassword + " " + encode(safeName));
            String response = readBoundedLine(in, 512);
            if (response != null && response.startsWith("ACCOUNT_CHALLENGE ")) {
                if (accountPassword == null || accountPassword.length() < 8) {
                    throw new IOException("Для этого сервера нужен пароль аккаунта (минимум 8 символов)");
                }
                String[] challenge = response.split(" ");
                if (challenge.length != 4) throw new IOException("Invalid account challenge");
                boolean exists = "EXISTING".equals(challenge[1]);
                if (createAccount && exists) throw new IOException("Аккаунт уже существует. Выбери вход.");
                if (!createAccount && !exists) throw new IOException("Аккаунт не найден. Выбери создание аккаунта.");
                byte[] salt;
                byte[] nonce;
                try {
                    salt = Base64.getUrlDecoder().decode(challenge[2]);
                    nonce = Base64.getUrlDecoder().decode(challenge[3]);
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("Invalid account challenge encoding", invalid);
                }
                byte[] verifier = MultiplayerAccountStore.deriveVerifier(accountPassword.toCharArray(), salt);
                byte[] proof = MultiplayerAccountStore.proof(verifier, nonce);
                String encodedProof = Base64.getUrlEncoder().withoutPadding().encodeToString(proof);
                if (exists) out.println("ACCOUNT_LOGIN " + encodedProof);
                else out.println("ACCOUNT_REGISTER "
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(verifier) + " " + encodedProof);
                if (out.checkError()) throw new IOException("Не удалось отправить проверку аккаунта");
                String accountResult = readBoundedLine(in, 256);
                if (!"ACCOUNT_OK".equals(accountResult)) {
                    throw new IOException(accountResult != null && accountResult.startsWith("ERROR ")
                            ? accountResult.substring(6) : "Проверка аккаунта не пройдена");
                }
                response = readBoundedLine(in, 256);
            }
            if (response == null || !response.startsWith("WELCOME ")) {
                throw new IOException(response != null && response.startsWith("ERROR ")
                        ? response.substring(6) : "Server did not respond");
            }
            String[] welcome = response.split(" ");
            if (welcome.length < 4) throw new IOException("Invalid server handshake");
            playerId = Integer.parseInt(welcome[1]);
            hostPlayerId = Integer.parseInt(welcome[2]);
            serverWorldReady = Boolean.parseBoolean(welcome[3]);
            socket.setSoTimeout(0);
            connected = true;
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = readBoundedLine(in, MAX_PROTOCOL_LINE)) != null) {
                        if (line.startsWith("CHAT ")) {
                            try {
                                String message = decode(line.substring(5));
                                chats.accept(message);
                                dispatchEvent("CHAT_TEXT " + encode(message));
                            } catch (RuntimeException ignored) { }
                        } else {
                            if (line.startsWith("HOST_CHANGED ")) {
                                String[] parts = line.split(" ");
                                if (parts.length == 2) hostPlayerId = Integer.parseInt(parts[1]);
                            }
                            dispatchEvent(line);
                        }
                    }
                } catch (IOException | IllegalArgumentException ignored) {
                    // The final block below reports a single disconnection event.
                } finally {
                    if (connected) dispatchEvent("DISCONNECTED");
                    connected = false;
                }
            }, "BlockWorld-Multiplayer-Reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException | RuntimeException error) {
            close();
            throw error;
        }
    }

    public int playerId() { return playerId; }
    public boolean isHost() { return playerId > 0 && playerId == hostPlayerId; }
    public boolean hasServerWorld() { return serverWorldReady; }
    public boolean isConnected() { return connected && !socket.isClosed(); }

    public void setEventListener(Consumer<String> listener) {
        synchronized (eventLock) {
            eventListener = listener;
            if (listener != null) {
                String queued;
                while ((queued = pendingEvents.poll()) != null) safeAccept(listener, queued);
            }
        }
    }

    private void dispatchEvent(String event) {
        synchronized (eventLock) {
            Consumer<String> listener = eventListener;
            if (listener == null) pendingEvents.add(event);
            else safeAccept(listener, event);
        }
    }

    private static void safeAccept(Consumer<String> listener, String event) {
        try { listener.accept(event); } catch (RuntimeException ignored) { }
    }

    public synchronized void sendChat(String message) throws IOException {
        String safe = message == null ? "" : message.strip();
        if (safe.isEmpty()) return;
        if (safe.length() > 240) safe = safe.substring(0, 240);
        sendLine("CHAT " + encode(safe));
    }

    public synchronized void sendPosition(float x, float y, float z, float yaw, boolean moving) throws IOException {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(yaw)) return;
        sendLine("POS " + x + " " + y + " " + z + " " + yaw + " " + moving);
    }

    public synchronized void sendPlayerAttack() throws IOException {
        sendLine("ATTACK_PLAYER");
    }

    public synchronized void sendCreatureAttack(float ox, float oy, float oz,
                                                float dx, float dy, float dz) throws IOException {
        if (!Float.isFinite(ox) || !Float.isFinite(oy) || !Float.isFinite(oz)
                || !Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(dz)) return;
        float lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 0.7f || lengthSquared > 1.3f) return;
        sendLine("ATTACK_CREATURE " + ox + " " + oy + " " + oz + " " + dx + " " + dy + " " + dz);
    }

    public synchronized void sendBlockAdd(BlockPos pos, BlockType type) throws IOException {
        if (pos == null || type == null) throw new IllegalArgumentException("Block position and type are required");
        sendLine("BLOCK_ADD " + pos.x() + " " + pos.y() + " " + pos.z() + " " + type.name());
    }

    public synchronized void sendBlockRemove(BlockPos pos) throws IOException {
        if (pos == null) throw new IllegalArgumentException("Block position is required");
        sendLine("BLOCK_REMOVE " + pos.x() + " " + pos.y() + " " + pos.z());
    }

    /** Uploads a player's own inventory to the server profile store. */
    public synchronized void sendPlayerProfile(Map<BlockType, Integer> blocks,
                                                Map<ItemType, Integer> items,
                                                java.util.Set<ArmorSlot> armor) throws IOException {
        sendPlayerProfile(blocks, items, armor, 20f, 20f, 0);
    }

    public synchronized void sendPlayerProfile(Map<BlockType, Integer> blocks,
                                                Map<ItemType, Integer> items,
                                                java.util.Set<ArmorSlot> armor,
                                                float health, float hunger, int foodCount) throws IOException {
        validateStats(health, hunger, foodCount);
        sendLine("PROFILE_UPLOAD_BEGIN " + BlockType.values().length + " " + ItemType.values().length
                + " " + (armor == null ? 0 : armor.size()) + " " + health + " " + hunger + " " + foodCount);
        for (BlockType type : BlockType.values()) {
            int count = blocks == null ? 0 : blocks.getOrDefault(type, 0);
            if (count < 0 || count > 1_000_000) throw new IllegalArgumentException("Invalid block stack");
            sendLine("PROFILE_UPLOAD_BLOCK " + type.name() + " " + count);
        }
        for (ItemType type : ItemType.values()) {
            int count = items == null ? 0 : items.getOrDefault(type, 0);
            if (count < 0 || count > 1_000_000) throw new IllegalArgumentException("Invalid item stack");
            sendLine("PROFILE_UPLOAD_ITEM " + type.name() + " " + count);
        }
        if (armor != null) {
            for (ArmorSlot slot : armor) sendLine("PROFILE_UPLOAD_ARMOR " + slot.name());
        }
        sendLine("PROFILE_UPLOAD_END");
    }

    public synchronized void openSharedChest(BlockPos position) throws IOException {
        if (position == null) throw new IllegalArgumentException("Chest position is required");
        sendLine("CHEST_OPEN " + position.x() + " " + position.y() + " " + position.z());
    }

    public synchronized void takeFromChest(BlockPos position, ItemType item, int count) throws IOException {
        sendChestTransfer("CHEST_TAKE", position, item, count);
    }

    public synchronized void putIntoChest(BlockPos position, ItemType item, int count) throws IOException {
        sendChestTransfer("CHEST_PUT", position, item, count);
    }

    private void sendChestTransfer(String action, BlockPos position, ItemType item, int count) throws IOException {
        if (position == null || item == null) throw new IllegalArgumentException("Chest and item are required");
        if (count < 1 || count > 64) throw new IllegalArgumentException("Chest transfer must be 1..64");
        sendLine(action + " " + position.x() + " " + position.y() + " " + position.z()
                + " " + item.name() + " " + count);
    }

    public synchronized void sendPlayerStats(float health, float hunger, int foodCount) throws IOException {
        validateStats(health, hunger, foodCount);
        sendLine("PLAYER_STATS " + health + " " + hunger + " " + foodCount);
    }

    private static void validateStats(float health, float hunger, int foodCount) {
        if (!Float.isFinite(health) || health < 0f || health > 20f
                || !Float.isFinite(hunger) || hunger < 0f || hunger > 20f
                || foodCount < 0 || foodCount > 1_000_000) {
            throw new IllegalArgumentException("Invalid player stats");
        }
    }

    public synchronized void sendCraftRequest(String catalog, int recipeNumber) throws IOException {
        if (!java.util.List.of("starter", "advanced", "release100").contains(catalog))
            throw new IllegalArgumentException("Unknown crafting catalog");
        if (recipeNumber < 1 || recipeNumber > 16) throw new IllegalArgumentException("Invalid recipe number");
        sendLine("CRAFT_REQUEST " + catalog + " " + recipeNumber);
    }

    public synchronized void sendTradeRequest(int offerNumber) throws IOException {
        if (offerNumber < 1 || offerNumber > 4) throw new IllegalArgumentException("Invalid trade offer");
        sendLine("TRADE_REQUEST " + offerNumber);
    }

    public synchronized void sendTradeRequest(CreatureType profession, int offerNumber) throws IOException {
        if (profession == null || !profession.isVillager()) throw new IllegalArgumentException("Invalid villager profession");
        if (offerNumber < 1 || offerNumber > 4) throw new IllegalArgumentException("Invalid trade offer");
        sendLine("TRADE_REQUEST " + profession.name() + " " + offerNumber);
    }

    public synchronized void sendVillageTradeRequest(CreatureType profession, int offerNumber) throws IOException {
        if (profession == null || !profession.isVillager()) throw new IllegalArgumentException("Invalid village trader");
        if (offerNumber < 1 || offerNumber > 4) throw new IllegalArgumentException("Invalid village offer");
        sendLine("VILLAGE_TRADE_REQUEST " + profession.name() + " " + offerNumber);
    }

    /** Sends current active workplace counts; the server accepts this only from its host player. */
    public synchronized void sendVillageWorkSnapshot(int farmers, int blacksmiths, int librarians) throws IOException {
        if (farmers < 0 || farmers > 16 || blacksmiths < 0 || blacksmiths > 16
                || librarians < 0 || librarians > 16) {
            throw new IllegalArgumentException("Invalid village worker counts");
        }
        sendLine("VILLAGE_WORK " + farmers + " " + blacksmiths + " " + librarians);
    }

    /** Sends the host's complete current world. Call from a background thread. */
    public synchronized void sendWorldSnapshot(long seed, boolean activeBiomes, boolean extendedBiomes,
                                                int worldRadius, Map<BlockPos, BlockType> blocks) throws IOException {
        sendWorldSnapshot(seed, activeBiomes, extendedBiomes, false, worldRadius, blocks, java.util.List.of());
    }

    public synchronized void sendWorldSnapshot(long seed, boolean activeBiomes, boolean extendedBiomes,
                                                int worldRadius, Map<BlockPos, BlockType> blocks,
                                                java.util.List<WorldCreature> creatures) throws IOException {
        sendWorldSnapshot(seed, activeBiomes, extendedBiomes, false, worldRadius, blocks, creatures);
    }

    public synchronized void sendWorldSnapshot(long seed, boolean activeBiomes, boolean extendedBiomes,
                                                boolean enchantedBiomes, int worldRadius,
                                                Map<BlockPos, BlockType> blocks,
                                                java.util.List<WorldCreature> creatures) throws IOException {
        if (blocks == null || blocks.size() > 1_000_000) throw new IllegalArgumentException("World snapshot exceeds limits");
        if (creatures == null || creatures.size() > 256) throw new IllegalArgumentException("Creature snapshot exceeds limits");
        sendLine("WORLD_BEGIN " + seed + " " + activeBiomes + " " + extendedBiomes + " " + worldRadius + " " + blocks.size());
        sendLine("WORLD_ENCHANTED " + enchantedBiomes);
        for (Map.Entry<BlockPos, BlockType> entry : blocks.entrySet()) {
            BlockPos p = entry.getKey();
            sendLine("WORLD_BLOCK " + p.x() + " " + p.y() + " " + p.z() + " " + entry.getValue().name());
        }
        sendLine("WORLD_CREATURES_BEGIN " + creatures.size());
        for (WorldCreature creature : creatures) {
            sendLine("WORLD_CREATURE " + creature.id() + " " + creature.type().name() + " "
                    + creature.x() + " " + creature.y() + " " + creature.z() + " " + creature.yaw());
        }
        sendLine("WORLD_CREATURES_END");
        sendLine("WORLD_END");
    }

    /** Host-authoritative creature state; call periodically from a background thread. */
    public synchronized void sendCreatureSnapshot(java.util.List<WorldCreature> creatures) throws IOException {
        if (creatures == null || creatures.size() > 256) throw new IllegalArgumentException("Creature snapshot exceeds limits");
        sendLine("CREATURES_BEGIN " + creatures.size());
        for (WorldCreature creature : creatures) {
            sendLine("CREATURE " + creature.id() + " " + creature.type().name() + " "
                    + creature.x() + " " + creature.y() + " " + creature.z() + " " + creature.yaw());
        }
        sendLine("CREATURES_END");
    }

    private void sendLine(String line) throws IOException {
        if (!isConnected() || out == null) throw new IOException("Нет подключения к серверу");
        out.println(line);
        if (out.checkError()) throw new IOException("Не удалось отправить данные на сервер");
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String readBoundedLine(BufferedReader reader, int limit) throws IOException {
        StringBuilder line = new StringBuilder();
        boolean received = false;
        for (;;) {
            int value = reader.read();
            if (value == -1) return received ? line.toString() : null;
            received = true;
            if (value == '\n') return line.toString();
            if (value != '\r') {
                if (line.length() >= limit) throw new IOException("Protocol line too long");
                line.append((char) value);
            }
        }
    }

    @Override public void close() {
        connected = false;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
