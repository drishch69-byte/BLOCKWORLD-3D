package com.blockworld.net;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Password-protected TCP lobby and chat server. */
public final class MultiplayerServer implements AutoCloseable {
    private static final int MAX_PROTOCOL_LINE = 2048;
    private final String password;
    private final int maxPlayers;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final Object membershipLock = new Object();
    private volatile boolean running;
    private volatile ServerSocket socket;

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

    private void broadcast(String message) {
        String line = "CHAT " + java.util.Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        for (ClientHandler client : clients) client.send(line);
    }

    private final class ClientHandler implements Runnable {
        private final Socket peer;
        private volatile PrintWriter out;
        private String name = "Player";

        ClientHandler(Socket peer) { this.peer = peer; }

        void send(String line) {
            PrintWriter writer = out;
            if (writer != null) {
                writer.println(line);
                if (writer.checkError()) {
                    try { peer.close(); } catch (IOException ignored) { }
                }
            }
        }

        @Override public void run() {
            try (Socket ignored = peer;
                 BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8), true)) {
                out = writer;
                String auth = readBoundedLine(in, 256);
                if (auth == null || !auth.startsWith("AUTH ")) {
                    writer.println("ERROR Invalid protocol");
                    return;
                }
                String[] fields = auth.substring(5).split(" ", 2);
                String supplied = new String(java.util.Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8);
                name = fields.length > 1 ? fields[1].replaceAll("[^A-Za-zА-Яа-я0-9_-]", "") : "Player";
                if (name.isBlank()) name = "Player";
                if (!password.equals(supplied)) {
                    writer.println("ERROR Неверный пароль");
                    return;
                }

                synchronized (membershipLock) {
                    if (!running) {
                        writer.println("ERROR Сервер остановлен");
                        return;
                    }
                    if (clients.size() >= maxPlayers) {
                        writer.println("ERROR Сервер заполнен");
                        return;
                    }
                    // The handshake must be the first server response for a new client.
                    writer.println("WELCOME " + (clients.size() + 1));
                    clients.add(this);
                }
                peer.setSoTimeout(0);
                broadcast(name + " подключился");

                String line;
                while ((line = readBoundedLine(in, MAX_PROTOCOL_LINE)) != null) {
                    if (!line.startsWith("CHAT ")) continue;
                    String msg = new String(java.util.Base64.getDecoder().decode(line.substring(5)), StandardCharsets.UTF_8);
                    if (msg.length() > 240) msg = msg.substring(0, 240);
                    if (!msg.isBlank()) broadcast(name + ": " + msg);
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Invalid or disconnected clients are isolated.
            } finally {
                if (clients.remove(this)) broadcast(name + " отключился");
            }
        }
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
        }
        if (current != null) {
            try { current.close(); } catch (IOException ignored) { }
        }
    }
}
