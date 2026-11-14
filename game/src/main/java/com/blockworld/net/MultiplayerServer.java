package com.blockworld.net;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CombatRules;
import com.blockworld.core.ArmorSlot;
import com.blockworld.core.CraftingRecipe;
import com.blockworld.core.ItemType;
import com.blockworld.core.TradeRecipe;
import com.blockworld.core.TreasureLoot;
import com.blockworld.core.WorldCreature;
import java.nio.file.Path;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Authoritative TCP server for player presence, shared blocks, and lobby chat. */
public final class MultiplayerServer implements AutoCloseable {
    private static final int MAX_PROTOCOL_LINE = 2048;
    private static final int MAX_WORLD_BLOCKS = 1_000_000;
    private static final int MAX_COORDINATE = 100_000;

    private final String password;
    private final int maxPlayers;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>();
    private final Object membershipLock = new Object();
    private final Object worldLock = new Object();
    private final Object profileLock = new Object();
    private final MultiplayerWorldStore worldStore;
    private final MultiplayerPlayerStore playerStore;
    private final MultiplayerAccountStore accountStore;
    private final MultiplayerChestStore chestStore;
    private final Map<BlockPos, Map<ItemType, Integer>> sharedChests = new HashMap<>();
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();
    private final AtomicInteger nextPlayerId = new AtomicInteger(0);
    private final Map<BlockPos, BlockType> worldBlocks = new HashMap<>();
    private final Map<Long, WorldCreature> worldCreatures = new HashMap<>();

    private volatile boolean running;
    private volatile ServerSocket socket;
    private volatile int hostPlayerId;
    private volatile boolean worldReady;
    private volatile boolean snapshotInProgress;
    private long worldSeed;
    private boolean activeBiomes;
    private boolean extendedBiomes;
    private boolean enchantedBiomes;
    private int worldRadius;
    private long lastCreaturePersistNanos;

    public MultiplayerServer(String password, int maxPlayers) {
        this(password, maxPlayers, null, false);
    }

    public MultiplayerServer(String password, int maxPlayers, Path worldFile) {
        this(password, maxPlayers, worldFile, false);
    }

    public MultiplayerServer(String password, int maxPlayers, Path worldFile, boolean accountsEnabled) {
        this.password = password == null ? "" : password;
        if (maxPlayers < 1 || maxPlayers > 32) throw new IllegalArgumentException("maxPlayers must be 1..32");
        this.maxPlayers = maxPlayers;
        this.worldStore = worldFile == null ? null : new MultiplayerWorldStore(worldFile);
        this.playerStore = worldFile == null ? null : new MultiplayerPlayerStore(worldFile);
        this.accountStore = accountsEnabled && worldFile != null ? new MultiplayerAccountStore(worldFile) : null;
        this.chestStore = worldFile == null ? null : new MultiplayerChestStore(worldFile);
    }

    public synchronized int start(int port) throws IOException {
        if (running) throw new IllegalStateException("Server already running");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
        restoreWorld();
        if (accountStore != null) accountStore.load();
        if (chestStore != null) sharedChests.putAll(chestStore.load());
        ServerSocket candidate = new ServerSocket();
        try {
            candidate.setReuseAddress(true);
            candidate.bind(new InetSocketAddress(port));
            socket = candidate;
            running = true;
        } catch (IOException error) {
            try { candidate.close(); } catch (IOException ignored) { }
            throw error;
        }
        Thread accept = new Thread(this::acceptLoop, "BlockWorld-Multiplayer-Accept");
        accept.setDaemon(true);
        accept.start();
        return candidate.getLocalPort();
    }

    public int port() {
        ServerSocket current = socket;
        return current == null ? -1 : current.getLocalPort();
    }

    public int playerCount() { return clients.size(); }
    public boolean isRunning() { return running; }
    public boolean isWorldReady() { return worldReady; }

    private void restoreWorld() throws IOException {
        if (worldStore == null) return;
        Optional<MultiplayerWorldStore.Snapshot> stored = worldStore.load();
        if (stored.isEmpty()) return;
        MultiplayerWorldStore.Snapshot snapshot = stored.get();
        synchronized (worldLock) {
            worldBlocks.clear();
            worldBlocks.putAll(snapshot.blocks());
            worldCreatures.clear();
            for (WorldCreature creature : snapshot.creatures()) worldCreatures.put(creature.id(), creature);
            worldSeed = snapshot.seed();
            activeBiomes = snapshot.activeBiomes();
            extendedBiomes = snapshot.extendedBiomes();
            enchantedBiomes = snapshot.enchantedBiomes();
            worldRadius = snapshot.radius();
            worldReady = true;
        }
    }

    private void persistCreaturesIfDueLocked() {
        long now = System.nanoTime();
        if (lastCreaturePersistNanos == 0L || now - lastCreaturePersistNanos >= java.util.concurrent.TimeUnit.SECONDS.toNanos(10)) {
            persistWorldLocked();
            lastCreaturePersistNanos = now;
        }
    }

    private void persistWorldLocked() {
        if (worldStore == null || !worldReady) return;
        try {
            worldStore.save(worldSeed, activeBiomes, extendedBiomes, enchantedBiomes, worldRadius,
                    worldBlocks, List.copyOf(worldCreatures.values()));
        } catch (IOException error) {
            System.err.println("CUBEVERSE 3D: failed to save hosted multiplayer world: " + error.getMessage());
            for (ClientHandler client : clients) client.send("WORLD_SAVE_ERROR");
        }
    }

    private void sendProfile(ClientHandler target, MultiplayerPlayerStore.Snapshot profile) {
        List<String> lines = new ArrayList<>();
        lines.add("PROFILE_BEGIN " + BlockType.values().length + " " + ItemType.values().length
                + " " + profile.armor().size() + " " + profile.health() + " " + profile.hunger() + " " + profile.foodCount());
        for (BlockType type : BlockType.values()) {
            lines.add("PROFILE_BLOCK " + type.name() + " " + profile.blocks().getOrDefault(type, 0));
        }
        for (ItemType type : ItemType.values()) {
            lines.add("PROFILE_ITEM " + type.name() + " " + profile.items().getOrDefault(type, 0));
        }
        for (ArmorSlot slot : profile.armor()) lines.add("PROFILE_ARMOR " + slot.name());
        lines.add("PROFILE_END");
        target.sendBatch(lines);
    }

    private void loadPlayerProfile(ClientHandler target) {
        try {
            Optional<MultiplayerPlayerStore.Snapshot> stored = playerStore == null
                    ? Optional.empty() : playerStore.load(target.name);
            if (stored.isPresent()) {
                target.profile = stored.get();
                sendProfile(target, target.profile);
            } else {
                target.send("PROFILE_EMPTY");
            }
        } catch (IOException error) {
            target.send("PROFILE_ERROR");
        }
    }

    private void persistPlayerProfile(ClientHandler target, MultiplayerPlayerStore.Snapshot profile) {
        synchronized (profileLock) {
        try {
            if (playerStore != null) playerStore.save(target.name, profile);
            target.profile = profile;
            target.send("PROFILE_SAVED");
        } catch (IOException error) {
            target.send("PROFILE_ERROR");
        }
    
        }
    }

    private void sendChestSnapshot(ClientHandler target, BlockPos pos) {
        synchronized (worldLock) {
            Map<ItemType, Integer> contents = sharedChests.getOrDefault(pos, Map.of());
            List<String> lines = new ArrayList<>();
            lines.add("CHEST_BEGIN " + pos.x() + " " + pos.y() + " " + pos.z() + " " + contents.size());
            for (Map.Entry<ItemType, Integer> entry : contents.entrySet()) {
                lines.add("CHEST_ITEM " + entry.getKey().name() + " " + entry.getValue());
            }
            lines.add("CHEST_END");
            target.sendBatch(lines);
        }
    }

    private void openSharedChest(ClientHandler target, String line) {
        String[] p = line.split(" ");
        if (p.length != 4 || target.profile == null) return;
        BlockPos pos;
        try {
            pos = parsePosition(p, 1);
        } catch (IllegalArgumentException error) {
            target.send("CHEST_ERROR Недопустимые координаты сундука");
            return;
        }
        synchronized (worldLock) {
            BlockType chestType = worldBlocks.get(pos);
            if (!worldReady || (chestType != BlockType.TREASURE_CHEST && chestType != BlockType.CRYSTAL_CACHE
                    && chestType != BlockType.ANCIENT_CACHE)) {
                target.send("CHEST_ERROR Сундук не найден в общем мире");
                return;
            }
            if (!sharedChests.containsKey(pos)) {
                Map<ItemType, Integer> loot = chestType == BlockType.ANCIENT_CACHE
                        ? TreasureLoot.rollAncientCache(worldSeed, pos)
                        : chestType == BlockType.CRYSTAL_CACHE
                            ? TreasureLoot.rollCrystalCache(worldSeed, pos) : TreasureLoot.roll(worldSeed, pos);
                EnumMap<ItemType, Integer> initialLoot = new EnumMap<>(ItemType.class);
                initialLoot.putAll(loot);
                sharedChests.put(pos, initialLoot);
                try {
                    if (chestStore != null) chestStore.save(sharedChests);
                } catch (IOException error) {
                    sharedChests.remove(pos);
                    target.send("CHEST_ERROR Не удалось сохранить содержимое сундука");
                    return;
                }
            }
            sendChestSnapshot(target, pos);
        }
    }

    private void transferSharedChest(ClientHandler target, String line, boolean take) {
        synchronized (profileLock) {
        String[] p = line.split(" ");
        if (p.length != 6 || target.profile == null) return;
        try {
            BlockPos pos = parsePosition(p, 1);
            ItemType item = ItemType.valueOf(p[4]);
            int count = boundedTradeCount(p[5]);
            synchronized (worldLock) {
                BlockType chestType = worldBlocks.get(pos);
                if (!worldReady || (chestType != BlockType.TREASURE_CHEST && chestType != BlockType.CRYSTAL_CACHE
                        && chestType != BlockType.ANCIENT_CACHE) || !sharedChests.containsKey(pos)) {
                    target.send("CHEST_ERROR Сундук закрыт или не существует");
                    return;
                }
                EnumMap<ItemType, Integer> originalChest = new EnumMap<>(ItemType.class);
                originalChest.putAll(sharedChests.get(pos));
                EnumMap<ItemType, Integer> updatedChest = new EnumMap<>(originalChest);
                EnumMap<ItemType, Integer> updatedItems = target.profile.mutableItems();
                if (take) {
                    if (updatedChest.getOrDefault(item, 0) < count) {
                        target.send("CHEST_ERROR В сундуке недостаточно предметов");
                        return;
                    }
                    updatedChest.merge(item, -count, Integer::sum);
                    updatedItems.merge(item, count, Integer::sum);
                } else {
                    if (updatedItems.getOrDefault(item, 0) < count) {
                        target.send("CHEST_ERROR У тебя недостаточно предметов");
                        return;
                    }
                    updatedItems.merge(item, -count, Integer::sum);
                    updatedChest.merge(item, count, Integer::sum);
                }
                updatedChest.entrySet().removeIf(entry -> entry.getValue() <= 0);
                MultiplayerPlayerStore.Snapshot updatedProfile = new MultiplayerPlayerStore.Snapshot(
                        target.profile.blocks(), updatedItems, target.profile.armor(),
                        target.profile.health(), target.profile.hunger(), target.profile.foodCount());
                Map<BlockPos, Map<ItemType, Integer>> updatedContainers =
                        MultiplayerChestStore.deepCopy(sharedChests);
                updatedContainers.put(pos, updatedChest);
                try {
                    if (chestStore != null) chestStore.save(updatedContainers);
                    if (playerStore != null) playerStore.save(target.name, updatedProfile);
                } catch (IOException saveError) {
                    if (chestStore != null) {
                        try { chestStore.save(sharedChests); } catch (IOException rollbackError) {
                            saveError.addSuppressed(rollbackError);
                        }
                    }
                    target.send("CHEST_ERROR Не удалось сохранить операцию");
                    return;
                }
                sharedChests.put(pos, updatedChest);
                target.profile = updatedProfile;
                sendProfile(target, updatedProfile);
                sendChestSnapshot(target, pos);
                target.send("CHEST_ACTION_RESULT true");
            }
        } catch (IllegalArgumentException error) {
            target.send("CHEST_ERROR Неверный предмет или количество");
        }
    
        }
    }

    private void processPlayerAttack(ClientHandler sender, String message) {
        String[] fields = message.strip().split("\\s+");
        boolean aimedAttack = "ATTACK".equalsIgnoreCase(message.strip());
        ClientHandler target = null;
        PlayerState attackerPosition = players.get(sender.id);

        if (aimedAttack) {
            if (attackerPosition == null || !attackerPosition.hasPosition) return;
            float closestDistance = 3.25f * 3.25f;
            for (ClientHandler candidate : clients) {
                if (candidate == sender) continue;
                PlayerState candidatePosition = players.get(candidate.id);
                if (candidatePosition == null || !candidatePosition.hasPosition) continue;
                if (!CombatRules.inMeleeRange(attackerPosition.x, attackerPosition.y, attackerPosition.z,
                        candidatePosition.x, candidatePosition.y, candidatePosition.z, 3.25f)
                        || !CombatRules.isFacingTarget(attackerPosition.x, attackerPosition.z,
                                attackerPosition.yaw, candidatePosition.x, candidatePosition.z)) continue;
                float dx = candidatePosition.x - attackerPosition.x;
                float dy = candidatePosition.y - attackerPosition.y;
                float dz = candidatePosition.z - attackerPosition.z;
                float distance = dx * dx + dy * dy + dz * dz;
                if (distance < closestDistance) {
                    closestDistance = distance;
                    target = candidate;
                }
            }
            // A normal mouse click is silent when no other player is close and in front.
            if (target == null) return;
        } else {
            if (fields.length != 2 || !"/hit".equalsIgnoreCase(fields[0])) {
                sender.send("CHAT " + encode("Использование: /hit Ник (старый способ; теперь можно нажать ЛКМ)."));
                return;
            }
            target = clients.stream()
                    .filter(client -> client.name.equalsIgnoreCase(fields[1])).findFirst().orElse(null);
            if (target == null || target == sender) {
                sender.send("CHAT " + encode("Игрок не найден или нельзя атаковать самого себя."));
                return;
            }
        }

        long now = System.nanoTime();
        if (sender.lastPlayerAttackNanos > 0L && now - sender.lastPlayerAttackNanos < 650_000_000L) {
            if (!aimedAttack) sender.send("CHAT " + encode("Подожди перед следующим ударом."));
            return;
        }
        PlayerState targetPosition = players.get(target.id);
        if (attackerPosition == null || targetPosition == null
                || !attackerPosition.hasPosition || !targetPosition.hasPosition
                || !CombatRules.inMeleeRange(attackerPosition.x, attackerPosition.y, attackerPosition.z,
                        targetPosition.x, targetPosition.y, targetPosition.z, 3.25f)
                || (aimedAttack && !CombatRules.isFacingTarget(attackerPosition.x, attackerPosition.z,
                        attackerPosition.yaw, targetPosition.x, targetPosition.z))) {
            if (!aimedAttack) sender.send("CHAT " + encode("Слишком далеко: подойди к игроку ближе."));
            return;
        }
        synchronized (profileLock) {
            if (sender.profile == null || target.profile == null) {
                if (!aimedAttack) sender.send("CHAT " + encode("Профиль одного из игроков ещё загружается."));
                return;
            }
            float damage = CombatRules.applyArmor(4f, target.profile.armor());
            float health = Math.max(0f, target.profile.health() - damage);
            MultiplayerPlayerStore.Snapshot updated = new MultiplayerPlayerStore.Snapshot(
                    target.profile.blocks(), target.profile.items(), target.profile.armor(),
                    health, target.profile.hunger(), target.profile.foodCount());
            try {
                if (playerStore != null) playerStore.save(target.name, updated);
                target.profile = updated;
                sender.lastPlayerAttackNanos = now;
                sendProfile(target, updated);
                sender.send("CHAT " + encode("Ты попал по игроку " + target.name
                        + "; урон " + Math.round(damage) + "."));
                target.send("CHAT " + encode(sender.name + " ударил тебя! Урон " + Math.round(damage) + "."));
            } catch (IOException error) {
                if (!aimedAttack) sender.send("CHAT " + encode("Не удалось сохранить результат удара."));
            }
        }
    }

    private void processPlayerTrade(ClientHandler sender, String message) {
        String[] fields = message.strip().split("\\s+");
        if (fields.length != 6 || !"/trade".equalsIgnoreCase(fields[0])) {
            sender.send("CHAT " + encode("Использование: /trade Ник ПРЕДМЕТ_ОТДАЮ Количество ПРЕДМЕТ_ПОЛУЧАЮ Количество"));
            return;
        }
        ClientHandler receiver = clients.stream()
                .filter(client -> client.name.equalsIgnoreCase(fields[1])).findFirst().orElse(null);
        if (receiver == null || receiver == sender) {
            sender.send("CHAT " + encode("Игрок не найден или нельзя обмениваться с самим собой."));
            return;
        }
        try {
            ItemType offered = ItemType.valueOf(fields[2].toUpperCase(Locale.ROOT));
            int offeredCount = boundedTradeCount(fields[3]);
            ItemType requested = ItemType.valueOf(fields[4].toUpperCase(Locale.ROOT));
            int requestedCount = boundedTradeCount(fields[5]);
            synchronized (profileLock) {
                if (sender.profile == null || receiver.profile == null) {
                    sender.send("CHAT " + encode("Профиль одного из игроков ещё загружается."));
                    return;
                }
                EnumMap<ItemType, Integer> senderItems = sender.profile.mutableItems();
                EnumMap<ItemType, Integer> receiverItems = receiver.profile.mutableItems();
                if (senderItems.getOrDefault(offered, 0) < offeredCount
                        || receiverItems.getOrDefault(requested, 0) < requestedCount) {
                    sender.send("CHAT " + encode("Обмен отклонён: у одного из игроков недостаточно предметов."));
                    return;
                }
                senderItems.merge(offered, -offeredCount, Integer::sum);
                senderItems.merge(requested, requestedCount, Integer::sum);
                receiverItems.merge(requested, -requestedCount, Integer::sum);
                receiverItems.merge(offered, offeredCount, Integer::sum);
                MultiplayerPlayerStore.Snapshot senderUpdated = new MultiplayerPlayerStore.Snapshot(
                        sender.profile.blocks(), senderItems, sender.profile.armor(),
                        sender.profile.health(), sender.profile.hunger(), sender.profile.foodCount());
                MultiplayerPlayerStore.Snapshot receiverUpdated = new MultiplayerPlayerStore.Snapshot(
                        receiver.profile.blocks(), receiverItems, receiver.profile.armor(),
                        receiver.profile.health(), receiver.profile.hunger(), receiver.profile.foodCount());
                if (playerStore != null) {
                    playerStore.save(sender.name, senderUpdated);
                    try {
                        playerStore.save(receiver.name, receiverUpdated);
                    } catch (IOException error) {
                        playerStore.save(sender.name, sender.profile);
                        throw error;
                    }
                }
                sender.profile = senderUpdated;
                receiver.profile = receiverUpdated;
                sendProfile(sender, senderUpdated);
                sendProfile(receiver, receiverUpdated);
                sender.send("CHAT " + encode("Обмен с " + receiver.name + " завершён."));
                receiver.send("CHAT " + encode("Обмен с " + sender.name + " завершён."));
            }
        } catch (IllegalArgumentException error) {
            sender.send("CHAT " + encode("Неверный предмет или количество. Используй названия предметов из инвентаря."));
        } catch (IOException error) {
            sender.send("CHAT " + encode("Не удалось сохранить обмен. Попробуй позже."));
        }
    }

    private static int boundedTradeCount(String text) {
        int value = Integer.parseInt(text);
        if (value < 1 || value > 64) throw new IllegalArgumentException("Trade count must be 1..64");
        return value;
    }

    private void processCraftRequest(ClientHandler target, String[] parts) {
        synchronized (profileLock) {
        if (parts.length != 3 || target.profile == null) {
            target.send("CRAFT_RESULT 0 false");
            return;
        }
        try {
            String catalog = parts[1];
            int number = Integer.parseInt(parts[2]);
            List<CraftingRecipe> recipes = switch (catalog) {
                case "starter" -> CraftingRecipe.starterRecipes();
                case "advanced" -> CraftingRecipe.advancedRecipes();
                case "release100" -> CraftingRecipe.release100Recipes();
                default -> List.of();
            };
            CraftingRecipe recipe = recipes.stream().filter(entry -> entry.number() == number).findFirst().orElse(null);
            if (recipe == null) {
                target.send("CRAFT_RESULT " + number + " false");
                return;
            }
            EnumMap<BlockType, Integer> blocks = target.profile.mutableBlocks();
            EnumMap<ItemType, Integer> items = target.profile.mutableItems();
            if (!recipe.craft(blocks, items)) {
                target.send("CRAFT_RESULT " + number + " false");
                return;
            }
            MultiplayerPlayerStore.Snapshot updated = new MultiplayerPlayerStore.Snapshot(
                    blocks, items, target.profile.armor(), target.profile.health(),
                    target.profile.hunger(), target.profile.foodCount());
            if (playerStore != null) playerStore.save(target.name, updated);
            target.profile = updated;
            sendProfile(target, updated);
            target.send("CRAFT_RESULT " + number + " true");
        } catch (IOException | IllegalArgumentException error) {
            target.send("PROFILE_ERROR");
            target.send("CRAFT_RESULT 0 false");
        }
    
        }
    }

    private void processTradeRequest(ClientHandler target, String[] parts) {
        synchronized (profileLock) {
        if (parts.length != 2 || target.profile == null) {
            target.send("TRADE_RESULT 0 false");
            return;
        }
        try {
            int number = Integer.parseInt(parts[1]);
            TradeRecipe offer = TradeRecipe.villageOffers().stream()
                    .filter(entry -> entry.number() == number).findFirst().orElse(null);
            if (offer == null) {
                target.send("TRADE_RESULT " + number + " false");
                return;
            }
            EnumMap<ItemType, Integer> items = target.profile.mutableItems();
            if (!offer.trade(items)) {
                target.send("TRADE_RESULT " + number + " false");
                return;
            }
            MultiplayerPlayerStore.Snapshot updated = new MultiplayerPlayerStore.Snapshot(
                    target.profile.blocks(), items, target.profile.armor(),
                    target.profile.health(), target.profile.hunger(), target.profile.foodCount());
            if (playerStore != null) playerStore.save(target.name, updated);
            target.profile = updated;
            sendProfile(target, updated);
            target.send("TRADE_RESULT " + number + " true");
        } catch (IOException | IllegalArgumentException error) {
            target.send("PROFILE_ERROR");
            target.send("TRADE_RESULT 0 false");
        }
    
        }
    }

    private boolean authenticateAccount(BufferedReader in, ClientHandler target) throws IOException {
        if (accountStore == null) return true;
        boolean exists = accountStore.exists(target.name);
        byte[] salt = exists ? accountStore.saltFor(target.name) : new byte[16];
        if (!exists) secureRandom.nextBytes(salt);
        byte[] nonce = new byte[32];
        secureRandom.nextBytes(nonce);
        target.send("ACCOUNT_CHALLENGE " + (exists ? "EXISTING" : "NEW") + " "
                + Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + " "
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        String answer = readBoundedLine(in, 512);
        if (answer == null) return false;
        String[] parts = answer.split(" ");
        try {
            if (exists && parts.length == 2 && "ACCOUNT_LOGIN".equals(parts[0])) {
                byte[] proof = Base64.getUrlDecoder().decode(parts[1]);
                if (!accountStore.authenticate(target.name, nonce, proof)) {
                    target.send("ERROR Неверный пароль аккаунта");
                    return false;
                }
            } else if (!exists && parts.length == 3 && "ACCOUNT_REGISTER".equals(parts[0])) {
                byte[] verifier = Base64.getUrlDecoder().decode(parts[1]);
                byte[] proof = Base64.getUrlDecoder().decode(parts[2]);
                if (!accountStore.register(target.name, salt, verifier, nonce, proof)) {
                    target.send("ERROR Не удалось создать аккаунт");
                    return false;
                }
            } else {
                target.send(exists ? "ERROR Аккаунт уже существует. Выбери вход."
                        : "ERROR Аккаунт не найден. Выбери создание аккаунта.");
                return false;
            }
        } catch (IllegalArgumentException invalid) {
            target.send("ERROR Некорректные данные аккаунта");
            return false;
        }
        target.send("ACCOUNT_OK");
        return true;
    }

    private void acceptLoop() {
        while (running) {
            ServerSocket listener = socket;
            if (listener == null) return;
            try {
                Socket peer = listener.accept();
                peer.setSoTimeout(10000);
                Thread worker = new Thread(new ClientHandler(peer), "BlockWorld-Multiplayer-Client");
                worker.setDaemon(true);
                worker.start();
            } catch (SocketException closed) {
                if (running) close();
            } catch (IOException error) {
                if (running) close();
            }
        }
    }

    private void broadcastChat(String message) {
        String line = "CHAT " + encode(message);
        for (ClientHandler client : clients) client.send(line);
    }

    private void broadcastExcept(ClientHandler excluded, String line) {
        for (ClientHandler client : clients) {
            if (client != excluded) client.send(line);
        }
    }

    private String playerJoinLine(PlayerState state) {
        return "PLAYER_JOIN " + state.id + " " + encode(state.name);
    }

    private void sendSnapshotTo(ClientHandler target) {
        synchronized (worldLock) {
            if (!worldReady) return;
            target.sendSnapshot(worldSeed, activeBiomes, extendedBiomes, enchantedBiomes, worldRadius,
                    worldBlocks, List.copyOf(worldCreatures.values()));
        }
    }

    private void relayCreatureAttack(ClientHandler sender, String line) {
        if (!worldReady || sender.id == hostPlayerId) return;
        String[] fields = line.split(" ");
        if (fields.length != 7) return;
        try {
            float ox = Float.parseFloat(fields[1]);
            float oy = Float.parseFloat(fields[2]);
            float oz = Float.parseFloat(fields[3]);
            float dx = Float.parseFloat(fields[4]);
            float dy = Float.parseFloat(fields[5]);
            float dz = Float.parseFloat(fields[6]);
            if (!Float.isFinite(ox) || !Float.isFinite(oy) || !Float.isFinite(oz)
                    || !Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(dz)
                    || Math.abs(ox) > MAX_COORDINATE || Math.abs(oz) > MAX_COORDINATE
                    || oy < -512f || oy > 2048f) return;
            float directionLength = dx * dx + dy * dy + dz * dz;
            if (directionLength < 0.7f || directionLength > 1.3f) return;

            PlayerState player = players.get(sender.id);
            if (player == null || !player.hasPosition) return;
            float px = ox - player.x, py = oy - player.y, pz = oz - player.z;
            if (px * px + py * py + pz * pz > 36f) return;

            long now = System.nanoTime();
            if (sender.lastCreatureAttackNanos > 0L
                    && now - sender.lastCreatureAttackNanos < 180_000_000L) return;
            ClientHandler host = clients.stream().filter(candidate -> candidate.id == hostPlayerId)
                    .findFirst().orElse(null);
            if (host == null) return;
            sender.lastCreatureAttackNanos = now;
            host.send("REMOTE_CREATURE_ATTACK " + sender.id + " " + ox + " " + oy + " " + oz
                    + " " + dx + " " + dy + " " + dz);
        } catch (NumberFormatException ignored) {
            // Ignore malformed or non-finite attack packets.
        }
    }

    private void handlePosition(ClientHandler sender, String line) {
        String[] parts = line.split(" ");
        if (parts.length != 6) return;
        try {
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float z = Float.parseFloat(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            boolean moving = Boolean.parseBoolean(parts[5]);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(yaw)
                    || Math.abs(x) > MAX_COORDINATE || Math.abs(z) > MAX_COORDINATE
                    || y < -512f || y > 2048f || Math.abs(yaw) > 1000f) return;
            PlayerState state = new PlayerState(sender.id, sender.name, x, y, z, yaw, moving);
            players.put(sender.id, state);
            broadcastExcept(sender, "PLAYER_POS " + sender.id + " " + x + " " + y + " " + z + " " + yaw + " " + moving);
        } catch (NumberFormatException ignored) {
            // Ignore malformed movement packets.
        }
    }

    private void handleBlockAdd(String line) {
        String[] parts = line.split(" ");
        if (parts.length != 5) return;
        try {
            BlockPos pos = parsePosition(parts, 1);
            BlockType type = BlockType.valueOf(parts[4]);
            String broadcast;
            synchronized (worldLock) {
                if (!worldReady) return;
                if (!worldBlocks.containsKey(pos) && worldBlocks.size() >= MAX_WORLD_BLOCKS) return;
                worldBlocks.put(pos, type);
                persistWorldLocked();
                broadcast = "BLOCK_ADD " + pos.x() + " " + pos.y() + " " + pos.z() + " " + type.name();
                for (ClientHandler client : clients) client.send(broadcast);
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown block IDs and invalid coordinates are ignored.
        }
    }

    private void handleBlockRemove(String line) {
        String[] parts = line.split(" ");
        if (parts.length != 4) return;
        try {
            BlockPos pos = parsePosition(parts, 1);
            synchronized (worldLock) {
                if (!worldReady) return;
                BlockType removed = worldBlocks.remove(pos);
                if (removed == null) return;
                if ((removed == BlockType.TREASURE_CHEST || removed == BlockType.CRYSTAL_CACHE
                        || removed == BlockType.ANCIENT_CACHE)
                        && sharedChests.remove(pos) != null && chestStore != null) {
                    try { chestStore.save(sharedChests); }
                    catch (IOException error) {
                        System.err.println("CUBEVERSE 3D: failed to clean a removed shared chest: " + error.getMessage());
                    }
                }
                persistWorldLocked();
                String broadcast = "BLOCK_REMOVE " + pos.x() + " " + pos.y() + " " + pos.z();
                for (ClientHandler client : clients) client.send(broadcast);
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore invalid coordinates.
        }
    }

    private static BlockPos parsePosition(String[] parts, int offset) {
        int x = Integer.parseInt(parts[offset]);
        int y = Integer.parseInt(parts[offset + 1]);
        int z = Integer.parseInt(parts[offset + 2]);
        if (Math.abs(x) > MAX_COORDINATE || y < -512 || y > 2048 || Math.abs(z) > MAX_COORDINATE) {
            throw new IllegalArgumentException("Block position out of bounds");
        }
        return new BlockPos(x, y, z);
    }

    private void finishSnapshot(ClientHandler sender) {
        synchronized (worldLock) {
            if (sender.id != hostPlayerId || sender.pendingSnapshot == null
                    || sender.pendingSnapshot.size() != sender.expectedSnapshotBlocks) {
                sender.pendingSnapshot = null;
                snapshotInProgress = false;
                return;
            }
            worldBlocks.clear();
            worldBlocks.putAll(sender.pendingSnapshot);
            worldCreatures.clear();
            worldCreatures.putAll(sender.pendingCreatures);
            worldSeed = sender.pendingSeed;
            activeBiomes = sender.pendingActiveBiomes;
            extendedBiomes = sender.pendingExtendedBiomes;
            enchantedBiomes = sender.pendingEnchantedBiomes;
            worldRadius = sender.pendingWorldRadius;
            worldReady = true;
            persistWorldLocked();
            snapshotInProgress = false;
            sender.pendingSnapshot = null;

            // Keep snapshot transfer and later block changes ordered for every peer.
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendSnapshot(worldSeed, activeBiomes, extendedBiomes, enchantedBiomes, worldRadius,
                            worldBlocks, List.copyOf(worldCreatures.values()));
                }
            }
        }
        sender.send("WORLD_ACCEPTED");
    }

    private final class ClientHandler implements Runnable {
        private final Socket peer;
        private final Object sendLock = new Object();
        private volatile PrintWriter out;
        private int id;
        private String name = "Player";
        private Map<BlockPos, BlockType> pendingSnapshot;
        private Map<Long, WorldCreature> pendingCreatures = new HashMap<>();
        private int expectedCreatures;
        private int expectedSnapshotBlocks;
        private long pendingSeed;
        private boolean pendingActiveBiomes;
        private boolean pendingExtendedBiomes;
        private boolean pendingEnchantedBiomes;
        private int pendingWorldRadius;
        private MultiplayerPlayerStore.Snapshot profile;
        private long lastPlayerAttackNanos;
        private long lastCreatureAttackNanos;
        private EnumMap<BlockType, Integer> pendingProfileBlocks;
        private EnumMap<ItemType, Integer> pendingProfileItems;
        private EnumSet<ArmorSlot> pendingProfileArmor;
        private int expectedProfileBlocks;
        private int expectedProfileItems;
        private int expectedProfileArmor;
        private int receivedProfileBlocks;
        private int receivedProfileItems;
        private float pendingHealth = 20f;
        private float pendingHunger = 20f;
        private int pendingFoodCount;

        ClientHandler(Socket peer) { this.peer = peer; }

        void send(String line) {
            synchronized (sendLock) {
                PrintWriter writer = out;
                if (writer != null) {
                    writer.println(line);
                    if (writer.checkError()) {
                        try { peer.close(); } catch (IOException ignored) { }
                    }
                }
            }
        }

        void sendBatch(List<String> lines) {
            synchronized (sendLock) {
                PrintWriter writer = out;
                if (writer == null) return;
                for (String line : lines) writer.println(line);
                if (writer.checkError()) {
                    try { peer.close(); } catch (IOException ignored) { }
                }
            }
        }

        void sendSnapshot(long seed, boolean active, boolean extended, boolean enchanted, int radius,
                          Map<BlockPos, BlockType> blocks, List<WorldCreature> creatures) {
            synchronized (sendLock) {
                PrintWriter writer = out;
                if (writer == null) return;
                writer.println("WORLD_BEGIN " + seed + " " + active + " " + extended + " " + radius + " " + blocks.size());
                writer.println("WORLD_ENCHANTED " + enchanted);
                for (Map.Entry<BlockPos, BlockType> entry : blocks.entrySet()) {
                    BlockPos p = entry.getKey();
                    writer.println("WORLD_BLOCK " + p.x() + " " + p.y() + " " + p.z() + " " + entry.getValue().name());
                }
                writer.println("WORLD_CREATURES_BEGIN " + creatures.size());
                for (WorldCreature creature : creatures) {
                    writer.println("WORLD_CREATURE " + creature.id() + " " + creature.type().name() + " "
                            + creature.x() + " " + creature.y() + " " + creature.z() + " " + creature.yaw());
                }
                writer.println("WORLD_CREATURES_END");
                writer.println("WORLD_END");
                if (writer.checkError()) {
                    try { peer.close(); } catch (IOException ignored) { }
                }
            }
        }

        private void beginSnapshot(String line) {
            if (id != hostPlayerId) return;
            String[] p = line.split(" ");
            if (p.length != 6) throw new IllegalArgumentException("Invalid world header");
            long seed = Long.parseLong(p[1]);
            boolean active = Boolean.parseBoolean(p[2]);
            boolean extended = Boolean.parseBoolean(p[3]);
            int radius = Integer.parseInt(p[4]);
            int count = Integer.parseInt(p[5]);
            if (count < 0 || count > MAX_WORLD_BLOCKS || radius < 0 || radius > MAX_COORDINATE) {
                throw new IllegalArgumentException("World snapshot exceeds limits");
            }
            synchronized (worldLock) {
                if (snapshotInProgress) throw new IllegalArgumentException("Snapshot already in progress");
                snapshotInProgress = true;
                pendingSnapshot = new HashMap<>(Math.max(16, count));
                expectedSnapshotBlocks = count;
                pendingSeed = seed;
                pendingActiveBiomes = active;
                pendingExtendedBiomes = extended;
                pendingEnchantedBiomes = false;
                pendingWorldRadius = radius;
                pendingCreatures = new HashMap<>();
            }
        }

        private void acceptSnapshotEnchanted(String line) {
            if (id != hostPlayerId || pendingSnapshot == null) return;
            String[] p = line.split(" ");
            if (p.length != 2 || !(p[1].equals("true") || p[1].equals("false"))) {
                throw new IllegalArgumentException("Invalid enchanted-biome metadata");
            }
            pendingEnchantedBiomes = Boolean.parseBoolean(p[1]);
        }

        private void acceptSnapshotBlock(String line) {
            if (id != hostPlayerId || pendingSnapshot == null) return;
            String[] p = line.split(" ");
            if (p.length != 5 || pendingSnapshot.size() >= expectedSnapshotBlocks) return;
            BlockPos pos = parsePosition(p, 1);
            BlockType type = BlockType.valueOf(p[4]);
            pendingSnapshot.put(pos, type);
        }

        private void acceptWorldCreature(String line) {
            if (id != hostPlayerId || pendingSnapshot == null) return;
            String[] p = line.split(" ");
            if (p.length != 7 || pendingCreatures.size() >= 256) return;
            long creatureId = Long.parseLong(p[1]);
            WorldCreature creature = new WorldCreature(creatureId,
                    com.blockworld.core.CreatureType.valueOf(p[2]),
                    Float.parseFloat(p[3]), Float.parseFloat(p[4]),
                    Float.parseFloat(p[5]), Float.parseFloat(p[6]));
            pendingCreatures.put(creatureId, creature);
        }

        private void processCreatureSnapshot(String line) {
            String[] p = line.split(" ");
            if (id != hostPlayerId) return;
            if (p[0].equals("CREATURES_BEGIN")) {
                if (p.length != 2) return;
                expectedCreatures = Integer.parseInt(p[1]);
                if (expectedCreatures < 0 || expectedCreatures > 256)
                    throw new IllegalArgumentException("Invalid creature count");
                pendingCreatures = new HashMap<>();
            } else if (p[0].equals("CREATURE")) {
                if (p.length != 7 || pendingCreatures.size() >= expectedCreatures) return;
                long creatureId = Long.parseLong(p[1]);
                WorldCreature creature = new WorldCreature(creatureId,
                        com.blockworld.core.CreatureType.valueOf(p[2]),
                        Float.parseFloat(p[3]), Float.parseFloat(p[4]),
                        Float.parseFloat(p[5]), Float.parseFloat(p[6]));
                pendingCreatures.put(creatureId, creature);
            } else if (p[0].equals("CREATURES_END")) {
                if (pendingCreatures.size() != expectedCreatures)
                    throw new IllegalArgumentException("Incomplete creature snapshot");
                synchronized (worldLock) {
                    if (!worldReady) return;
                    worldCreatures.clear();
                    worldCreatures.putAll(pendingCreatures);
                    persistCreaturesIfDueLocked();
                    for (ClientHandler client : clients) {
                        if (client == this) continue;
                        client.send("CREATURES_BEGIN " + worldCreatures.size());
                        for (WorldCreature creature : worldCreatures.values()) {
                            client.send("CREATURE " + creature.id() + " " + creature.type().name() + " "
                                    + creature.x() + " " + creature.y() + " " + creature.z() + " " + creature.yaw());
                        }
                        client.send("CREATURES_END");
                    }
                }
            }
        }

        private void beginProfileUpload(String line) {
            String[] p = line.split(" ");
            if (p.length != 7) throw new IllegalArgumentException("Invalid profile header");
            expectedProfileBlocks = Integer.parseInt(p[1]);
            expectedProfileItems = Integer.parseInt(p[2]);
            expectedProfileArmor = Integer.parseInt(p[3]);
            pendingHealth = parseStat(p[4]);
            pendingHunger = parseStat(p[5]);
            pendingFoodCount = parseProfileCount(p[6]);
            if (expectedProfileBlocks != BlockType.values().length
                    || expectedProfileItems != ItemType.values().length
                    || expectedProfileArmor < 0 || expectedProfileArmor > ArmorSlot.values().length) {
                throw new IllegalArgumentException("Invalid profile counts");
            }
            pendingProfileBlocks = new EnumMap<>(BlockType.class);
            pendingProfileItems = new EnumMap<>(ItemType.class);
            pendingProfileArmor = EnumSet.noneOf(ArmorSlot.class);
            receivedProfileBlocks = 0;
            receivedProfileItems = 0;
        }

        private void acceptProfileBlock(String line) {
            if (pendingProfileBlocks == null) return;
            String[] p = line.split(" ");
            if (p.length != 3 || receivedProfileBlocks >= expectedProfileBlocks) throw new IllegalArgumentException("Invalid profile block");
            BlockType type = BlockType.valueOf(p[1]);
            int count = parseProfileCount(p[2]);
            if (pendingProfileBlocks.put(type, count) != null) throw new IllegalArgumentException("Duplicate profile block");
            receivedProfileBlocks++;
        }

        private void acceptProfileItem(String line) {
            if (pendingProfileItems == null) return;
            String[] p = line.split(" ");
            if (p.length != 3 || receivedProfileItems >= expectedProfileItems) throw new IllegalArgumentException("Invalid profile item");
            ItemType type = ItemType.valueOf(p[1]);
            int count = parseProfileCount(p[2]);
            if (pendingProfileItems.put(type, count) != null) throw new IllegalArgumentException("Duplicate profile item");
            receivedProfileItems++;
        }

        private void acceptProfileArmor(String line) {
            if (pendingProfileArmor == null) return;
            String[] p = line.split(" ");
            if (p.length != 2 || pendingProfileArmor.size() >= expectedProfileArmor) throw new IllegalArgumentException("Invalid profile armor");
            if (!pendingProfileArmor.add(ArmorSlot.valueOf(p[1]))) throw new IllegalArgumentException("Duplicate armor slot");
        }

        private void finishProfileUpload() {
        synchronized (profileLock) {
            if (pendingProfileBlocks == null || pendingProfileItems == null || pendingProfileArmor == null
                    || receivedProfileBlocks != expectedProfileBlocks
                    || receivedProfileItems != expectedProfileItems
                    || pendingProfileArmor.size() != expectedProfileArmor) {
                throw new IllegalArgumentException("Incomplete player profile");
            }
            MultiplayerPlayerStore.Snapshot updated = new MultiplayerPlayerStore.Snapshot(
                    pendingProfileBlocks, pendingProfileItems, pendingProfileArmor,
                    pendingHealth, pendingHunger, pendingFoodCount);
            pendingProfileBlocks = null;
            pendingProfileItems = null;
            pendingProfileArmor = null;
            persistPlayerProfile(this, updated);
        
        }
    }

        private int parseProfileCount(String value) {
            int count = Integer.parseInt(value);
            if (count < 0 || count > 1_000_000) throw new IllegalArgumentException("Profile stack out of range");
            return count;
        }

        private float parseStat(String value) {
            float stat = Float.parseFloat(value);
            if (!Float.isFinite(stat) || stat < 0f || stat > 20f)
                throw new IllegalArgumentException("Player stat out of range");
            return stat;
        }

        private void processPlayerStats(String line) {
        synchronized (profileLock) {
            String[] p = line.split(" ");
            if (p.length != 4 || profile == null) return;
            try {
                MultiplayerPlayerStore.Snapshot updated = new MultiplayerPlayerStore.Snapshot(
                        profile.blocks(), profile.items(), profile.armor(),
                        parseStat(p[1]), parseStat(p[2]), parseProfileCount(p[3]));
                if (playerStore != null) playerStore.save(name, updated);
                profile = updated;
                broadcastExcept(this, "PLAYER_STATS " + id + " " + updated.health()
                        + " " + updated.hunger() + " " + updated.foodCount());
                send("PLAYER_STATS_SAVED");
            } catch (IOException | IllegalArgumentException error) {
                send("PROFILE_ERROR");
            }
        
        }
    }

        private void process(String line) {
            if (line.startsWith("CHEST_OPEN ")) {
                openSharedChest(this, line);
            } else if (line.startsWith("CHEST_TAKE ")) {
                transferSharedChest(this, line, true);
            } else if (line.startsWith("CHEST_PUT ")) {
                transferSharedChest(this, line, false);
            } else if (line.startsWith("PROFILE_UPLOAD_BEGIN ")) {
                beginProfileUpload(line);
            } else if (line.startsWith("PROFILE_UPLOAD_BLOCK ")) {
                acceptProfileBlock(line);
            } else if (line.startsWith("PROFILE_UPLOAD_ITEM ")) {
                acceptProfileItem(line);
            } else if (line.startsWith("PROFILE_UPLOAD_ARMOR ")) {
                acceptProfileArmor(line);
            } else if (line.equals("PROFILE_UPLOAD_END")) {
                finishProfileUpload();
            } else if (line.startsWith("PLAYER_STATS ")) {
                processPlayerStats(line);
            } else if (line.startsWith("CRAFT_REQUEST ")) {
                processCraftRequest(this, line.split(" "));
            } else if (line.startsWith("TRADE_REQUEST ")) {
                processTradeRequest(this, line.split(" "));
            } else if (line.startsWith("CHAT ")) {
                String message = decode(line.substring(5));
                if (message.length() > 240) message = message.substring(0, 240);
                if (!message.isBlank()) {
                    if (message.startsWith("/trade ")) processPlayerTrade(this, message);
                    else if (message.startsWith("/hit ")) processPlayerAttack(this, message);
                    else if (message.startsWith("/")) send("CHAT " + encode("Команды: /trade Ник ПРЕДМЕТ Количество ПРЕДМЕТ Количество; /hit Ник."));
                    else broadcastChat(name + ": " + message);
                }
            } else if (line.equals("ATTACK_PLAYER")) {
                processPlayerAttack(this, "ATTACK");
            } else if (line.startsWith("ATTACK_CREATURE ")) {
                relayCreatureAttack(this, line);
            } else if (line.startsWith("POS ")) {
                handlePosition(this, line);
            } else if (line.startsWith("BLOCK_ADD ")) {
                handleBlockAdd(line);
            } else if (line.startsWith("BLOCK_REMOVE ")) {
                handleBlockRemove(line);
            } else if (line.startsWith("WORLD_BEGIN ")) {
                beginSnapshot(line);
            } else if (line.startsWith("WORLD_ENCHANTED ")) {
                acceptSnapshotEnchanted(line);
            } else if (line.startsWith("WORLD_BLOCK ")) {
                acceptSnapshotBlock(line);
            } else if (line.startsWith("WORLD_CREATURES_BEGIN ")) {
                String[] p = line.split(" ");
                if (p.length == 2 && id == hostPlayerId && pendingSnapshot != null) {
                    expectedCreatures = Integer.parseInt(p[1]);
                    if (expectedCreatures < 0 || expectedCreatures > 256)
                        throw new IllegalArgumentException("Invalid creature count");
                    pendingCreatures = new HashMap<>();
                }
            } else if (line.startsWith("WORLD_CREATURE ")) {
                acceptWorldCreature(line);
            } else if (line.equals("WORLD_CREATURES_END")) {
                if (id == hostPlayerId && pendingCreatures.size() != expectedCreatures)
                    throw new IllegalArgumentException("Incomplete world creature snapshot");
            } else if (line.startsWith("CREATURES_BEGIN ") || line.startsWith("CREATURE ")) {
                processCreatureSnapshot(line);
            } else if (line.equals("CREATURES_END")) {
                processCreatureSnapshot(line);
            } else if (line.equals("WORLD_END")) {
                finishSnapshot(this);
            }
        }

        @Override public void run() {
            boolean joined = false;
            try (Socket ignored = peer;
                 BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8), true)) {
                out = writer;
                String auth = readBoundedLine(in, 512);
                if (auth == null || !auth.startsWith("AUTH ")) {
                    send("ERROR Invalid protocol");
                    return;
                }
                String[] fields = auth.substring(5).split(" ", 2);
                String supplied = decode(fields[0]);
                name = fields.length > 1 ? sanitizeName(decode(fields[1])) : "Player";

                synchronized (membershipLock) {
                    if (!running) { send("ERROR Сервер остановлен"); return; }
                    if (clients.size() >= maxPlayers) { send("ERROR Сервер заполнен"); return; }
                    if (clients.stream().anyMatch(client -> client.name.equalsIgnoreCase(name))) {
                        send("ERROR Этот ник уже используется. Выбери другой ник.");
                        return;
                    }
                    if (hostPlayerId != 0 && (!worldReady || snapshotInProgress)) {
                        send("ERROR Мир ещё загружается. Попробуй подключиться позже.");
                        return;
                    }
                    if (!password.equals(supplied)) { send("ERROR Неверный пароль"); return; }
                    if (!authenticateAccount(in, this)) return;
                    id = nextPlayerId.incrementAndGet();
                    if (hostPlayerId == 0) hostPlayerId = id;
                    send("WELCOME " + id + " " + hostPlayerId + " " + worldReady);

                    for (ClientHandler existing : clients) {
                        PlayerState state = players.get(existing.id);
                        send(playerJoinLine(new PlayerState(existing.id, existing.name)));
                        if (state != null) send(positionLine(state));
                    }
                    clients.add(this);
                    players.put(id, new PlayerState(id, name));
                    joined = true;
                    broadcastExcept(this, playerJoinLine(new PlayerState(id, name)));
                    sendSnapshotTo(this);
                    loadPlayerProfile(this);
                }

                peer.setSoTimeout(0);
                String line;
                while ((line = readBoundedLine(in, MAX_PROTOCOL_LINE)) != null) {
                    try { process(line); }
                    catch (IllegalArgumentException malformedPacket) { send("ERROR Invalid packet"); }
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Disconnected or malformed clients are isolated.
            } finally {
                if (joined) {
                    clients.remove(this);
                    players.remove(id);
                    broadcastExcept(this, "PLAYER_LEAVE " + id);
                    if (id == hostPlayerId) {
                        ClientHandler successor = clients.stream().findFirst().orElse(null);
                        hostPlayerId = successor == null ? 0 : successor.id;
                        for (ClientHandler client : clients) client.send("HOST_CHANGED " + hostPlayerId);
                    }
                }
            }
        }
    }

    private static String positionLine(PlayerState state) {
        return "PLAYER_POS " + state.id + " " + state.x + " " + state.y + " " + state.z + " " + state.yaw + " " + state.moving;
    }

    private static String sanitizeName(String value) {
        if (value == null) return "Player";
        String clean = value.replaceAll("[^A-Za-zА-Яа-я0-9_-]", "").strip();
        if (clean.isBlank()) return "Player";
        return clean.substring(0, Math.min(20, clean.length()));
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
        ServerSocket current;
        synchronized (membershipLock) {
            running = false;
            current = socket;
            socket = null;
            for (ClientHandler client : clients) {
                try { client.peer.close(); } catch (IOException ignored) { }
            }
            clients.clear();
            players.clear();
            hostPlayerId = 0;
            snapshotInProgress = false;
            synchronized (worldLock) {
                persistWorldLocked();
                worldReady = false;
                worldBlocks.clear();
                worldCreatures.clear();
            }
        }
        if (current != null) {
            try { current.close(); } catch (IOException ignored) { }
        }
    }

    private static final class PlayerState {
        final int id;
        final String name;
        final float x, y, z, yaw;
        final boolean moving;
        final boolean hasPosition;

        PlayerState(int id, String name) {
            this(id, name, 0f, 0f, 0f, 0f, false, false);
        }

        PlayerState(int id, String name, float x, float y, float z, float yaw, boolean moving) {
            this(id, name, x, y, z, yaw, moving, true);
        }

        private PlayerState(int id, String name, float x, float y, float z, float yaw,
                            boolean moving, boolean hasPosition) {
            this.id = id; this.name = name; this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.moving = moving; this.hasPosition = hasPosition;
        }
    }
}
