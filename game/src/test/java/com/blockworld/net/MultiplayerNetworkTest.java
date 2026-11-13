package com.blockworld.net;

import org.junit.jupiter.api.Test;
import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.WorldCreature;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
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
            first.connect("127.0.0.1", port, "", "First", ignored -> { });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (server.playerCount() != 1 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, server.playerCount());
            IOException error = assertThrows(IOException.class,
                    () -> second.connect("127.0.0.1", port, "", "Second", ignored -> { }));
            assertTrue(error.getMessage().contains("Сервер заполнен"));
            assertEquals(1, server.playerCount());
        }
    }


    @Test void hostSnapshotIsSentToJoiningPlayersAndBlocksAreReplicated() throws Exception {
        try (MultiplayerServer server = new MultiplayerServer("lan-key", 4);
             MultiplayerClient host = new MultiplayerClient();
             MultiplayerClient guest = new MultiplayerClient()) {
            int port = server.start(0);
            var hostEvents = new CopyOnWriteArrayList<String>();
            var guestEvents = new CopyOnWriteArrayList<String>();
            CountDownLatch accepted = new CountDownLatch(1);
            CountDownLatch guestReceivedWorld = new CountDownLatch(1);
            CountDownLatch hostReceivedGuestPosition = new CountDownLatch(1);
            CountDownLatch hostReceivedBlock = new CountDownLatch(1);

            host.setEventListener(event -> {
                hostEvents.add(event);
                if (event.equals("WORLD_ACCEPTED")) accepted.countDown();
                if (event.startsWith("PLAYER_POS ")) hostReceivedGuestPosition.countDown();
                if (event.startsWith("BLOCK_ADD ")) hostReceivedBlock.countDown();
            });
            host.connect("127.0.0.1", port, "lan-key", "Host", ignored -> { });
            assertTrue(host.isHost());

            Map<BlockPos, BlockType> initialWorld = Map.of(
                    new BlockPos(0, 3, 0), BlockType.GRASS,
                    new BlockPos(1, 3, 0), BlockType.STONE);
            host.sendWorldSnapshot(987654321L, true, true, 32, initialWorld);
            assertTrue(accepted.await(3, TimeUnit.SECONDS));
            assertTrue(server.isWorldReady());

            guest.setEventListener(event -> {
                guestEvents.add(event);
                if (event.equals("WORLD_END")) guestReceivedWorld.countDown();
            });
            guest.connect("127.0.0.1", port, "lan-key", "Builder", ignored -> { });
            assertFalse(guest.isHost());
            assertTrue(guestReceivedWorld.await(3, TimeUnit.SECONDS));
            assertTrue(guestEvents.stream().anyMatch(line -> line.startsWith("WORLD_BEGIN 987654321 true true 32 2")));
            assertTrue(guestEvents.stream().anyMatch(line -> line.equals("WORLD_BLOCK 0 3 0 GRASS")));
            assertTrue(guestEvents.stream().anyMatch(line -> line.equals("WORLD_BLOCK 1 3 0 STONE")));

            guest.sendPosition(4.5f, 8.0f, -2.5f, 0.75f, true);
            guest.sendBlockAdd(new BlockPos(2, 3, 0), BlockType.WOOD);
            assertTrue(hostReceivedGuestPosition.await(3, TimeUnit.SECONDS));
            assertTrue(hostReceivedBlock.await(3, TimeUnit.SECONDS));
            assertTrue(hostEvents.stream().anyMatch(line -> line.startsWith("PLAYER_JOIN ")));
            assertTrue(hostEvents.stream().anyMatch(line -> line.equals("BLOCK_ADD 2 3 0 WOOD")));
            assertEquals(2, server.playerCount());
        }
    }

    @Test void worldSnapshotIncludesCreaturesAndLiveCreatureUpdatesReachGuests() throws Exception {
        try (MultiplayerServer server = new MultiplayerServer("mobs", 4);
             MultiplayerClient host = new MultiplayerClient();
             MultiplayerClient guest = new MultiplayerClient()) {
            int port = server.start(0);
            CountDownLatch hostAccepted = new CountDownLatch(1);
            CountDownLatch guestInitialSnapshot = new CountDownLatch(1);
            CountDownLatch guestLiveUpdate = new CountDownLatch(1);
            var guestEvents = new CopyOnWriteArrayList<String>();

            host.setEventListener(event -> {
                if (event.equals("WORLD_ACCEPTED")) hostAccepted.countDown();
            });
            host.connect("127.0.0.1", port, "mobs", "Host", ignored -> { });
            assertTrue(host.isHost());

            var blocks = Map.of(new BlockPos(0, 1, 0), BlockType.GRASS);
            var initialCreatures = List.of(new WorldCreature(42, CreatureType.COW, 1f, 2f, 3f, 0.25f));
            host.sendWorldSnapshot(8123L, true, false, 16, blocks, initialCreatures);
            assertTrue(hostAccepted.await(3, TimeUnit.SECONDS));

            guest.setEventListener(event -> {
                guestEvents.add(event);
                if (event.equals("WORLD_END")) guestInitialSnapshot.countDown();
                if (event.equals("CREATURES_END")) guestLiveUpdate.countDown();
            });
            guest.connect("127.0.0.1", port, "mobs", "Guest", ignored -> { });
            assertTrue(guestInitialSnapshot.await(3, TimeUnit.SECONDS));
            assertTrue(guestEvents.stream().anyMatch(line -> line.equals("WORLD_CREATURE 42 COW 1.0 2.0 3.0 0.25")));

            host.sendCreatureSnapshot(List.of(
                    new WorldCreature(42, CreatureType.COW, 2f, 2f, 3f, 0.5f),
                    new WorldCreature(43, CreatureType.SHEEP, 4f, 2f, 3f, 0.1f)));
            assertTrue(guestLiveUpdate.await(3, TimeUnit.SECONDS));
            assertTrue(guestEvents.stream().anyMatch(line -> line.equals("CREATURE 42 COW 2.0 2.0 3.0 0.5")));
            assertTrue(guestEvents.stream().anyMatch(line -> line.startsWith("CREATURE 43 SHEEP")));
        }
    }

    @Test void serverWorldIsRestoredAfterRestart(@TempDir Path temporaryDirectory) throws Exception {
        Path worldFile = temporaryDirectory.resolve("hosted-world.bwserver");
        var blocks = Map.of(
                new BlockPos(0, 2, 0), BlockType.GRASS,
                new BlockPos(1, 2, 0), BlockType.STONE);
        var creatures = List.of(new WorldCreature(7, CreatureType.FOX, 2f, 3f, 4f, 0.75f));

        try (MultiplayerServer firstServer = new MultiplayerServer("persist", 2, worldFile);
             MultiplayerClient firstHost = new MultiplayerClient()) {
            int port = firstServer.start(0);
            CountDownLatch saved = new CountDownLatch(1);
            firstHost.setEventListener(event -> {
                if (event.equals("WORLD_ACCEPTED")) saved.countDown();
            });
            firstHost.connect("127.0.0.1", port, "persist", "Host", ignored -> { });
            firstHost.sendWorldSnapshot(555123L, true, true, 24, blocks, creatures);
            assertTrue(saved.await(3, TimeUnit.SECONDS));
            assertTrue(java.nio.file.Files.isRegularFile(worldFile));
        }

        try (MultiplayerServer restoredServer = new MultiplayerServer("persist", 2, worldFile);
             MultiplayerClient returningHost = new MultiplayerClient()) {
            int port = restoredServer.start(0);
            CountDownLatch restored = new CountDownLatch(1);
            var received = new CopyOnWriteArrayList<String>();
            returningHost.setEventListener(event -> {
                received.add(event);
                if (event.equals("WORLD_END")) restored.countDown();
            });
            returningHost.connect("127.0.0.1", port, "persist", "Host", ignored -> { });
            assertTrue(returningHost.isHost());
            assertTrue(returningHost.hasServerWorld());
            assertTrue(restored.await(3, TimeUnit.SECONDS));
            assertTrue(received.stream().anyMatch(line -> line.startsWith("WORLD_BEGIN 555123 true true 24 2")));
            assertTrue(received.stream().anyMatch(line -> line.equals("WORLD_BLOCK 1 2 0 STONE")));
            assertTrue(received.stream().anyMatch(line -> line.startsWith("WORLD_CREATURE 7 FOX")));
        }
    }

    @Test void hostDisconnectPromotesAnotherConnectedPlayer() throws Exception {
        try (MultiplayerServer server = new MultiplayerServer("", 3);
             MultiplayerClient host = new MultiplayerClient();
             MultiplayerClient guest = new MultiplayerClient()) {
            int port = server.start(0);
            CountDownLatch promoted = new CountDownLatch(1);
            guest.setEventListener(event -> {
                if (event.equals("HOST_CHANGED " + guest.playerId())) promoted.countDown();
            });
            host.connect("127.0.0.1", port, "", "Host", ignored -> { });
            guest.connect("127.0.0.1", port, "", "Guest", ignored -> { });
            assertFalse(guest.isHost());
            host.close();
            assertTrue(promoted.await(3, TimeUnit.SECONDS));
            assertTrue(guest.isHost());
        }
    }

    @Test void failedConnectionClosesClientSocket() {
        MultiplayerClient client = new MultiplayerClient();
        assertThrows(IOException.class,
                () -> client.connect("127.0.0.1", 1, "", "Offline", ignored -> { }));
        assertFalse(client.isConnected());
    }
}

