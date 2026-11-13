package com.blockworld.net;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/** TCP lobby client for the multiplayer preview. */
public final class MultiplayerClient implements AutoCloseable {
    private static final int MAX_PROTOCOL_LINE = 2048;
    private final Socket socket = new Socket();
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected;

    public void connect(String host, int port, String password, String playerName,
                        Consumer<String> messages) throws IOException {
        if (connected) throw new IllegalStateException("Already connected");
        if (socket.isClosed()) throw new IOException("Client is already closed");
        if (host == null || host.isBlank() || host.length() > 253) throw new IllegalArgumentException("Invalid host");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        Consumer<String> sink = messages == null ? ignored -> { } : messages;
        try {
            socket.connect(new InetSocketAddress(host.trim(), port), 5000);
            socket.setSoTimeout(5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            String encoded = Base64.getEncoder().encodeToString(
                    (password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
            String name = (playerName == null ? "Player" : playerName)
                    .replaceAll("[^A-Za-zА-Яа-я0-9_-]", "");
            if (name.isBlank()) name = "Player";
            out.println("AUTH " + encoded + " " + name.substring(0, Math.min(20, name.length())));
            String response = readBoundedLine(in, 128);
            if (response == null || !response.startsWith("WELCOME ")) {
                throw new IOException(response != null && response.startsWith("ERROR ")
                        ? response.substring(6) : "Server did not respond");
            }
            socket.setSoTimeout(0);
            connected = true;
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = readBoundedLine(in, MAX_PROTOCOL_LINE)) != null) {
                        if (line.startsWith("CHAT ")) {
                            String message = new String(Base64.getDecoder().decode(line.substring(5)), StandardCharsets.UTF_8);
                            try { sink.accept(message); } catch (RuntimeException ignored) { }
                        }
                    }
                } catch (IOException | IllegalArgumentException error) {
                    if (connected) {
                        try { sink.accept("Соединение с сервером потеряно"); } catch (RuntimeException ignored) { }
                    }
                } finally {
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

    public boolean isConnected() { return connected && !socket.isClosed(); }

    public synchronized void sendChat(String message) throws IOException {
        if (!isConnected()) throw new IOException("Нет подключения к серверу");
        String safe = message == null ? "" : message.strip();
        if (safe.isEmpty()) return;
        if (safe.length() > 240) safe = safe.substring(0, 240);
        out.println("CHAT " + Base64.getEncoder().encodeToString(safe.getBytes(StandardCharsets.UTF_8)));
        if (out.checkError()) throw new IOException("Не удалось отправить сообщение");
    }

    @Override public synchronized void close() {
        connected = false;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
