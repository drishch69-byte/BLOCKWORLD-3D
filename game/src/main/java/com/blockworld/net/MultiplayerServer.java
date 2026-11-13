package com.blockworld.net;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;

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
    private final AtomicInteger nextPlayerId = new AtomicInteger(0);
    private final Map<BlockPos, BlockType> worldBlocks = new HashMap<>();

    private volatile boolean running;
    private volatile ServerSocket socket;
    private volatile int hostPlayerId;
    private volatile boolean worldReady;
    private volatile boolean snapshotInProgress;
    private long worldSeed;
    private boolean activeBiomes;
    private boolean extendedBiomes;
    private int worldRadius;

    public MultiplayerServer(String password, int maxPlayers) {
        this.password = password == null ? "" : password;
        if (maxPlayers < 1 || maxPlayers > 32) throw new IllegalArgumentException("maxPlayers must be 1..32");
        this.maxPlayers = maxPlayers;
    }

    public synchronized int start(int port) throws IOException {
        if (running) throw new IllegalStateException("Server already running");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
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
            target.sendSnapshot(worldSeed, activeBiomes, extendedBiomes, worldRadius, worldBlocks);
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
                if (!worldReady || worldBlocks.remove(pos) == null) return;
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
            worldSeed = sender.pendingSeed;
            activeBiomes = sender.pendingActiveBiomes;
            extendedBiomes = sender.pendingExtendedBiomes;
            worldRadius = sender.pendingWorldRadius;
            worldReady = true;
            snapshotInProgress = false;
            sender.pendingSnapshot = null;

            // Keep snapshot transfer and later block changes ordered for every peer.
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendSnapshot(worldSeed, activeBiomes, extendedBiomes, worldRadius, worldBlocks);
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
        private int expectedSnapshotBlocks;
        private long pendingSeed;
        private boolean pendingActiveBiomes;
        private boolean pendingExtendedBiomes;
        private int pendingWorldRadius;

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

        void sendSnapshot(long seed, boolean active, boolean extended, int radius,
                          Map<BlockPos, BlockType> blocks) {
            synchronized (sendLock) {
                PrintWriter writer = out;
                if (writer == null) return;
                writer.println("WORLD_BEGIN " + seed + " " + active + " " + extended + " " + radius + " " + blocks.size());
                for (Map.Entry<BlockPos, BlockType> entry : blocks.entrySet()) {
                    BlockPos p = entry.getKey();
                    writer.println("WORLD_BLOCK " + p.x() + " " + p.y() + " " + p.z() + " " + entry.getValue().name());
                }
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
                pendingWorldRadius = radius;
            }
        }

        private void acceptSnapshotBlock(String line) {
            if (id != hostPlayerId || pendingSnapshot == null) return;
            String[] p = line.split(" ");
            if (p.length != 5 || pendingSnapshot.size() >= expectedSnapshotBlocks) return;
            BlockPos pos = parsePosition(p, 1);
            BlockType type = BlockType.valueOf(p[4]);
            pendingSnapshot.put(pos, type);
        }

        private void process(String line) {
            if (line.startsWith("CHAT ")) {
                String message = decode(line.substring(5));
                if (message.length() > 240) message = message.substring(0, 240);
                if (!message.isBlank()) broadcastChat(name + ": " + message);
            } else if (line.startsWith("POS ")) {
                handlePosition(this, line);
            } else if (line.startsWith("BLOCK_ADD ")) {
                handleBlockAdd(line);
            } else if (line.startsWith("BLOCK_REMOVE ")) {
                handleBlockRemove(line);
            } else if (line.startsWith("WORLD_BEGIN ")) {
                beginSnapshot(line);
            } else if (line.startsWith("WORLD_BLOCK ")) {
                acceptSnapshotBlock(line);
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
                    if (hostPlayerId != 0 && (!worldReady || snapshotInProgress)) {
                        send("ERROR Мир ещё загружается. Попробуй подключиться позже.");
                        return;
                    }
                    if (!password.equals(supplied)) { send("ERROR Неверный пароль"); return; }
                    id = nextPlayerId.incrementAndGet();
                    if (hostPlayerId == 0) hostPlayerId = id;
                    send("WELCOME " + id + " " + hostPlayerId);

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
                }

                peer.setSoTimeout(0);
                String line;
                while ((line = readBoundedLine(in, MAX_PROTOCOL_LINE)) != null) {
                    try { process(line); }
                    catch (IllegalArgumentException ignored) { send("ERROR Invalid packet"); }
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Disconnected or malformed clients are isolated.
            } finally {
                if (joined) {
                    clients.remove(this);
                    players.remove(id);
                    broadcastExcept(this, "PLAYER_LEAVE " + id);
                }
            }
        }
    }

    private static String positionLine(PlayerState state) {
        return "PLAYER_POS " + state.id + " " + state.x + " " + state.y + " " + state.z + " " + state.yaw + " " + state.moving;
    }

    private static String sanitizeName(String value) {
        if (value == null) return "Player";
        String clean = value.replaceAll("[\\p{Cntrl}]", "").strip();
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
            worldReady = false;
            snapshotInProgress = false;
            synchronized (worldLock) { worldBlocks.clear(); }
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

        PlayerState(int id, String name) { this(id, name, 0f, 0f, 0f, 0f, false); }
        PlayerState(int id, String name, float x, float y, float z, float yaw, boolean moving) {
            this.id = id; this.name = name; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.moving = moving;
        }
    }
}
