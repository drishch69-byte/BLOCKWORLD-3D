package com.blockworld.net;

import org.junit.jupiter.api.Test;
import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.ItemType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.WorldCreature;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.List;
import java.nio.file.Path;
import java.util.EnumMap;
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
            CountDownLatch worldAccepted = new CountDownLatch(1);
            CountDownLatch promoted = new CountDownLatch(1);
            host.setEventListener(event -> {
                if (event.equals("WORLD_ACCEPTED")) worldAccepted.countDown();
            });
            host.connect("127.0.0.1", port, "", "Host", ignored -> { });
            host.sendWorldSnapshot(1L, false, false, 0, Map.of());
            assertTrue(worldAccepted.await(3, TimeUnit.SECONDS));

            guest.setEventListener(event -> {
                if (event.equals("HOST_CHANGED " + guest.playerId())) promoted.countDown();
            });
            guest.connect("127.0.0.1", port, "", "Guest", ignored -> { });
            assertFalse(guest.isHost());
            host.close();
            assertTrue(promoted.await(3, TimeUnit.SECONDS));
            assertTrue(guest.isHost());
        }
    }

    @Test void serverCraftsAndTradesFromPersistentPersonalInventory(@TempDir Path directory) throws Exception {
        Path worldFile = directory.resolve("hosted.bwserver");
        try (MultiplayerServer server = new MultiplayerServer("profile-pass", 4, worldFile);
             MultiplayerClient player = new MultiplayerClient()) {
            int port = server.start(0);
            var events = new CopyOnWriteArrayList<String>();
            CountDownLatch profileEmpty = new CountDownLatch(1);
            CountDownLatch profileSaved = new CountDownLatch(1);
            CountDownLatch crafted = new CountDownLatch(1);
            CountDownLatch traded = new CountDownLatch(1);
            player.setEventListener(event -> {
                events.add(event);
                if (event.equals("PROFILE_EMPTY")) profileEmpty.countDown();
                if (event.equals("PROFILE_SAVED")) profileSaved.countDown();
                if (event.equals("CRAFT_RESULT 1 true")) crafted.countDown();
                if (event.equals("TRADE_RESULT 1 true")) traded.countDown();
            });
            player.connect("127.0.0.1", port, "profile-pass", "Alice", ignored -> { });
            assertTrue(profileEmpty.await(3, TimeUnit.SECONDS));

            EnumMap<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
            blocks.put(BlockType.WOOD, 2);
            EnumMap<ItemType, Integer> items = new EnumMap<>(ItemType.class);
            items.put(ItemType.WHEAT, 4);
            player.sendPlayerProfile(blocks, items, java.util.Set.of());
            assertTrue(profileSaved.await(3, TimeUnit.SECONDS));

            player.sendCraftRequest("starter", 1);
            assertTrue(crafted.await(3, TimeUnit.SECONDS));
            player.sendTradeRequest(1);
            assertTrue(traded.await(3, TimeUnit.SECONDS));

            MultiplayerPlayerStore store = new MultiplayerPlayerStore(worldFile);
            var saved = store.load("Alice").orElseThrow();
            assertEquals(0, saved.blocks().get(BlockType.WOOD));
            assertEquals(4, saved.items().get(ItemType.PLANKS));
            assertEquals(0, saved.items().get(ItemType.WHEAT));
            assertEquals(1, saved.items().get(ItemType.EMERALD));
        }

        MultiplayerPlayerStore store = new MultiplayerPlayerStore(worldFile);
        var alice = store.load("Alice").orElseThrow();
        assertEquals(4, alice.items().get(ItemType.PLANKS));
        assertEquals(1, alice.items().get(ItemType.EMERALD));
    }

    @Test void reconnectReceivesTheSavedPersonalProfile(@TempDir Path directory) throws Exception {
        Path worldFile = directory.resolve("profile-world.bwserver");
        MultiplayerPlayerStore store = new MultiplayerPlayerStore(worldFile);
        EnumMap<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        blocks.put(BlockType.STONE, 17);
        EnumMap<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        items.put(ItemType.BREAD, 5);
        store.save("Builder", new MultiplayerPlayerStore.Snapshot(blocks, items,
                java.util.EnumSet.of(com.blockworld.core.ArmorSlot.HELMET)));

        try (MultiplayerServer server = new MultiplayerServer("restore-profile", 2, worldFile);
             MultiplayerClient client = new MultiplayerClient()) {
            int port = server.start(0);
            var events = new CopyOnWriteArrayList<String>();
            CountDownLatch profileReceived = new CountDownLatch(1);
            client.setEventListener(event -> {
                events.add(event);
                if (event.equals("PROFILE_END")) profileReceived.countDown();
            });
            client.connect("127.0.0.1", port, "restore-profile", "Builder", ignored -> { });
            assertTrue(profileReceived.await(3, TimeUnit.SECONDS));
            assertTrue(events.contains("PROFILE_BLOCK STONE 17"));
            assertTrue(events.contains("PROFILE_ITEM BREAD 5"));
            assertTrue(events.contains("PROFILE_ARMOR HELMET"));
            assertFalse(events.contains("PROFILE_EMPTY"));
        }
    }

    @Test void serverAccountsRegisterAuthenticateAndRejectWrongPasswords(@TempDir Path directory) throws Exception {
        Path worldFile = directory.resolve("accounts-world.bwserver");
        try (MultiplayerServer server = new MultiplayerServer("room-secret", 2, worldFile, true);
             MultiplayerClient firstLogin = new MultiplayerClient()) {
            int port = server.start(0);
            firstLogin.connect("127.0.0.1", port, "room-secret", "AccountUser",
                    "A-unique-account-pass", true, ignored -> { });
            assertTrue(firstLogin.isConnected());
        }

        try (MultiplayerServer server = new MultiplayerServer("room-secret", 2, worldFile, true);
             MultiplayerClient wrong = new MultiplayerClient();
             MultiplayerClient returning = new MultiplayerClient()) {
            int port = server.start(0);
            IOException rejected = assertThrows(IOException.class,
                    () -> wrong.connect("127.0.0.1", port, "room-secret", "AccountUser",
                            "wrong-account-pass", false, ignored -> { }));
            assertTrue(rejected.getMessage().contains("Неверный пароль аккаунта"));

            returning.connect("127.0.0.1", port, "room-secret", "AccountUser",
                    "A-unique-account-pass", false, ignored -> { });
            assertTrue(returning.isConnected());
        }
    }

    @Test void directPlayerTradeExchangesItemsAndPersistsBothProfiles(@TempDir Path directory) throws Exception {
        Path worldFile = directory.resolve("trade-world.bwserver");
        try (MultiplayerServer server = new MultiplayerServer("trade-room", 4, worldFile);
             MultiplayerClient host = new MultiplayerClient();
             MultiplayerClient guest = new MultiplayerClient()) {
            int port = server.start(0);
            CountDownLatch hostProfileEmpty = new CountDownLatch(1);
            CountDownLatch hostProfileSaved = new CountDownLatch(1);
            CountDownLatch worldAccepted = new CountDownLatch(1);
            CountDownLatch guestProfileEmpty = new CountDownLatch(1);
            CountDownLatch guestProfileSaved = new CountDownLatch(1);
            CountDownLatch tradeMessage = new CountDownLatch(1);
            var hostChats = new CopyOnWriteArrayList<String>();

            host.setEventListener(event -> {
                if (event.equals("PROFILE_EMPTY")) hostProfileEmpty.countDown();
                if (event.equals("PROFILE_SAVED")) hostProfileSaved.countDown();
                if (event.equals("WORLD_ACCEPTED")) worldAccepted.countDown();
            });
            host.connect("127.0.0.1", port, "trade-room", "Host", hostChats::add);
            assertTrue(hostProfileEmpty.await(3, TimeUnit.SECONDS));
            host.sendPlayerProfile(Map.of(), Map.of(ItemType.WHEAT, 5), java.util.Set.of());
            assertTrue(hostProfileSaved.await(3, TimeUnit.SECONDS));
            host.sendWorldSnapshot(9981L, false, false, 8,
                    Map.of(new BlockPos(0, 1, 0), BlockType.GRASS));
            assertTrue(worldAccepted.await(3, TimeUnit.SECONDS));

            guest.setEventListener(event -> {
                if (event.equals("PROFILE_EMPTY")) guestProfileEmpty.countDown();
                if (event.equals("PROFILE_SAVED")) guestProfileSaved.countDown();
            });
            guest.connect("127.0.0.1", port, "trade-room", "Guest", ignored -> { });
            assertTrue(guestProfileEmpty.await(3, TimeUnit.SECONDS));
            guest.sendPlayerProfile(Map.of(), Map.of(ItemType.BREAD, 3), java.util.Set.of());
            assertTrue(guestProfileSaved.await(3, TimeUnit.SECONDS));

            host.sendChat("/trade Guest WHEAT 2 BREAD 1");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (hostChats.stream().noneMatch(message -> message.contains("Обмен с Guest завершён"))
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(hostChats.stream().anyMatch(message -> message.contains("Обмен с Guest завершён")));

            MultiplayerPlayerStore store = new MultiplayerPlayerStore(worldFile);
            var hostProfile = store.load("Host").orElseThrow();
            var guestProfile = store.load("Guest").orElseThrow();
            assertEquals(3, hostProfile.items().get(ItemType.WHEAT));
            assertEquals(1, hostProfile.items().get(ItemType.BREAD));
            assertEquals(2, guestProfile.items().get(ItemType.WHEAT));
            assertEquals(2, guestProfile.items().get(ItemType.BREAD));
        }
    }

    @Test void sharedChestContentsCanBeOpenedTakenAndDeposited(@TempDir Path directory) throws Exception {
        Path worldFile = directory.resolve("chest-world.bwserver");
        BlockPos chest = new BlockPos(2, 1, 3);
        try (MultiplayerServer server = new MultiplayerServer("chest-room", 2, worldFile);
             MultiplayerClient host = new MultiplayerClient()) {
            int port = server.start(0);
            CountDownLatch profileEmpty = new CountDownLatch(1);
            CountDownLatch profileSaved = new CountDownLatch(1);
            CountDownLatch worldAccepted = new CountDownLatch(1);
            CountDownLatch chestOpened = new CountDownLatch(1);
            CountDownLatch chestActions = new CountDownLatch(2);
            CountDownLatch statsSaved = new CountDownLatch(1);
            var events = new CopyOnWriteArrayList<String>();
            host.setEventListener(event -> {
                events.add(event);
                if (event.equals("PROFILE_EMPTY")) profileEmpty.countDown();
                if (event.equals("PROFILE_SAVED")) profileSaved.countDown();
                if (event.equals("WORLD_ACCEPTED")) worldAccepted.countDown();
                if (event.equals("CHEST_END")) chestOpened.countDown();
                if (event.equals("CHEST_ACTION_RESULT true")) chestActions.countDown();
                if (event.equals("PLAYER_STATS_SAVED")) statsSaved.countDown();
            });
            host.connect("127.0.0.1", port, "chest-room", "ChestHost", ignored -> { });
            assertTrue(profileEmpty.await(3, TimeUnit.SECONDS));
            host.sendPlayerProfile(Map.of(), Map.of(), java.util.Set.of(), 20f, 20f, 0);
            assertTrue(profileSaved.await(3, TimeUnit.SECONDS));
            host.sendWorldSnapshot(7234L, true, false, 16, Map.of(chest, BlockType.TREASURE_CHEST));
            assertTrue(worldAccepted.await(3, TimeUnit.SECONDS));

            host.openSharedChest(chest);
            assertTrue(chestOpened.await(3, TimeUnit.SECONDS));
            String firstItemLine = events.stream().filter(line -> line.startsWith("CHEST_ITEM "))
                    .findFirst().orElseThrow();
            String[] itemFields = firstItemLine.split(" ");
            ItemType item = ItemType.valueOf(itemFields[1]);
            int initialCount = Integer.parseInt(itemFields[2]);
            assertTrue(initialCount > 0);

            host.takeFromChest(chest, item, 1);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (events.stream().filter("CHEST_ACTION_RESULT true"::equals).count() < 1
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(events.contains("CHEST_ACTION_RESULT true"));

            host.sendPlayerStats(12.5f, 7.5f, 4);
            assertTrue(statsSaved.await(3, TimeUnit.SECONDS));
            host.putIntoChest(chest, item, 1);
            assertTrue(chestActions.await(3, TimeUnit.SECONDS));

            MultiplayerChestStore chests = new MultiplayerChestStore(worldFile);
            assertEquals(initialCount, chests.load().get(chest).get(item));
            MultiplayerPlayerStore.Snapshot profile =
                    new MultiplayerPlayerStore(worldFile).load("ChestHost").orElseThrow();
            assertEquals(12.5f, profile.health());
            assertEquals(7.5f, profile.hunger());
            assertEquals(4, profile.foodCount());
        }
    }

    @Test void failedConnectionClosesClientSocket() {
        MultiplayerClient client = new MultiplayerClient();
        assertThrows(IOException.class,
                () -> client.connect("127.0.0.1", 1, "", "Offline", ignored -> { }));
        assertFalse(client.isConnected());
    }
}

