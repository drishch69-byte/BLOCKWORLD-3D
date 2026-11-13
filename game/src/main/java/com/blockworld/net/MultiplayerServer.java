package com.blockworld.net;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Password-protected TCP lobby and chat server. */
public final class MultiplayerServer implements AutoCloseable {
    private final String password;
    private final int maxPlayers;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private ServerSocket socket;

    public MultiplayerServer(String password, int maxPlayers) {
        this.password = password == null ? "" : password;
        if (maxPlayers < 1 || maxPlayers > 32) throw new IllegalArgumentException("maxPlayers must be 1..32");
        this.maxPlayers = maxPlayers;
    }

    public synchronized int start(int port) throws IOException {
        if (running) throw new IllegalStateException("Server already running");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        running = true;
        Thread accept = new Thread(this::acceptLoop, "BlockWorld-Multiplayer-Accept");
        accept.setDaemon(true);
        accept.start();
        return socket.getLocalPort();
    }

    public int port() { return socket == null ? -1 : socket.getLocalPort(); }
    public int playerCount() { return clients.size(); }
    public boolean isRunning() { return running; }

    private void acceptLoop() {
        while (running) {
            try {
                Socket peer = socket.accept();
                peer.setSoTimeout(10000);
                ClientHandler handler = new ClientHandler(peer);
                Thread worker = new Thread(handler, "BlockWorld-Multiplayer-Client");
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
        void send(String line) { PrintWriter writer = out; if (writer != null) writer.println(line); }

        public void run() {
            try (Socket ignored = peer;
                 BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8), true)) {
                out = writer;
                String auth = in.readLine();
                if (auth == null || !auth.startsWith("AUTH ")) { writer.println("ERROR Invalid protocol"); return; }
                String[] fields = auth.substring(5).split(" ", 2);
                String supplied = new String(java.util.Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8);
                name = fields.length > 1 ? fields[1].replaceAll("[^A-Za-zА-Яа-я0-9_-]", "") : "Player";
                if (!password.equals(supplied)) { writer.println("ERROR Неверный пароль"); return; }
                if (clients.size() >= maxPlayers) { writer.println("ERROR Сервер заполнен"); return; }
                peer.setSoTimeout(0);
                clients.add(this);
                writer.println("WELCOME " + clients.size());
                broadcast(name + " подключился");
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("CHAT ")) {
                        String msg = new String(java.util.Base64.getDecoder().decode(line.substring(5)), StandardCharsets.UTF_8);
                        if (msg.length() > 240) msg = msg.substring(0, 240);
                        broadcast(name + ": " + msg);
                    }
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Invalid or disconnected clients are isolated.
            } finally {
                if (clients.remove(this)) broadcast(name + " отключился");
            }
        }
    }

    @Override public synchronized void close() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        for (ClientHandler client : clients) try { client.peer.close(); } catch (IOException ignored) { }
        clients.clear();
    }
}
