package com.blockworld.net;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerNetworkTest {
    @Test void serverStartsOnEphemeralPortAndValidatesCapacity() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new MultiplayerServer("", 0));
        try (MultiplayerServer server = new MultiplayerServer("test", 4)) {
            assertTrue(server.start(0) > 0);
            assertTrue(server.isRunning());
            assertEquals(0, server.playerCount());
        }
    }

    @Test void clientAuthenticatesAndExchangesChat() throws Exception {
        try (MultiplayerServer server = new MultiplayerServer("secret", 4)) {
            int port = server.start(0);
            CountDownLatch received = new CountDownLatch(1);
            var messages = new CopyOnWriteArrayList<String>();
            try (MultiplayerClient client = new MultiplayerClient()) {
                client.connect("127.0.0.1", port, "secret", "Tester", message -> {
                    messages.add(message);
                    if (message.contains("hello lobby")) received.countDown();
                });
                client.sendChat("hello lobby");
                assertTrue(received.await(3, TimeUnit.SECONDS));
                assertTrue(messages.stream().anyMatch(m -> m.contains("hello lobby")));
            }
        }
    }

    @Test void wrongPasswordIsRejected() throws Exception {
        try (MultiplayerServer server = new MultiplayerServer("correct", 2);
             MultiplayerClient client = new MultiplayerClient()) {
            int port = server.start(0);
            IOException error = assertThrows(IOException.class,
                    () -> client.connect("127.0.0.1", port, "wrong", "Tester", ignored -> { }));
            assertTrue(error.getMessage().contains("Неверный пароль"));
        }
    }
    @Test void serverRejectsClientsAboveConfiguredLimit() throws Exception {
        try (MultiplayerServer server = new MultiplayerServer("", 1);
             MultiplayerClient first = new MultiplayerClient();
             MultiplayerClient second = new MultiplayerClient()) {
            int port = server.start(0);
            CountDownLatch firstJoined = new CountDownLatch(1);
            first.connect("127.0.0.1", port, "", "First",
                    message -> { if (message.contains("First подключился")) firstJoined.countDown(); });
            assertTrue(firstJoined.await(3, TimeUnit.SECONDS));
            IOException error = assertThrows(IOException.class,
                    () -> second.connect("127.0.0.1", port, "", "Second", ignored -> { }));
            assertTrue(error.getMessage().contains("Сервер заполнен"));
            assertEquals(1, server.playerCount());
        }
    }

    @Test void failedConnectionClosesClientSocket() {
        MultiplayerClient client = new MultiplayerClient();
        assertThrows(IOException.class,
                () -> client.connect("127.0.0.1", 1, "", "Offline", ignored -> { }));
        assertFalse(client.isConnected());
    }
}

