package com.blockworld.net;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/** TCP lobby client for the multiplayer preview. */
public final class MultiplayerClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected;

    public void connect(String host, int port, String password, String playerName,
                        Consumer<String> messages) throws IOException {
        if (connected) throw new IllegalStateException("Already connected");
        if (host == null || host.isBlank() || host.length() > 253) throw new IllegalArgumentException("Invalid host");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        socket.connect(new InetSocketAddress(host.trim(), port), 5000);
        socket.setSoTimeout(5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        String encoded = Base64.getEncoder().encodeToString((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
        String name = (playerName == null ? "Player" : playerName).replaceAll("[^A-Za-zА-Яа-я0-9_-]", "");
        if (name.isBlank()) name = "Player";
        out.println("AUTH " + encoded + " " + name.substring(0, Math.min(20, name.length())));
        String response = in.readLine();
        if (response == null || !response.startsWith("WELCOME ")) {
            close();
            throw new IOException(response != null && response.startsWith("ERROR ") ? response.substring(6) : "Server did not respond");
        }
        socket.setSoTimeout(0);
        connected = true;
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("CHAT ")) {
                        String message = new String(Base64.getDecoder().decode(line.substring(5)), StandardCharsets.UTF_8);
                        messages.accept(message);
                    }
                }
            } catch (IOException | IllegalArgumentException error) {
                if (connected) messages.accept("Соединение с сервером потеряно");
            } finally { connected = false; }
        }, "BlockWorld-Multiplayer-Reader");
        reader.setDaemon(true);
        reader.start();
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
