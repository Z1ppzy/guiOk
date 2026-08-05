/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BedrockPlayersTest {
    private static final Logger LOGGER = Logger.getLogger(BedrockPlayersTest.class.getName());
    /** The shape Floodgate gives a Bedrock player: the Xbox user id, high half left zero. */
    private static final UUID FLOODGATE_ID = new UUID(0, 2535428371852800L);
    private static final UUID JAVA_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    /** Stands in for FloodgateApi/GeyserApi: a static accessor plus one question per UUID. */
    public static final class FakeApi {
        static final List<UUID> ASKED = new ArrayList<>();
        static FakeApi instance;
        static boolean answer;
        static boolean explode;

        public static FakeApi getInstance() {
            return instance;
        }

        public boolean isFloodgatePlayer(UUID playerId) {
            ASKED.add(playerId);
            if (explode) {
                throw new IllegalStateException("Floodgate is still starting");
            }
            return answer;
        }
    }

    @BeforeEach
    void resetFake() {
        FakeApi.ASKED.clear();
        FakeApi.instance = new FakeApi();
        FakeApi.answer = false;
        FakeApi.explode = false;
    }

    @Test
    void recognisesTheFloodgateUuidShapeWithoutAnyPluginInstalled() {
        BedrockPlayers players = new BedrockPlayers(LOGGER, null);

        assertFalse(players.available());
        assertEquals("UUID", players.source());
        assertTrue(players.isBedrock(FLOODGATE_ID));
        assertFalse(players.isBedrock(JAVA_ID));
    }

    /** The nil UUID has the same high half and belongs to no player. */
    @Test
    void doesNotCallTheNilUuidABedrockPlayer() {
        assertFalse(BedrockPlayers.hasFloodgateShape(new UUID(0, 0)));
        assertFalse(new BedrockPlayers(LOGGER, null).isBedrock(new UUID(0, 0)));
    }

    @Test
    void asksThePluginAndReportsWhatItAnswers() throws Exception {
        BedrockPlayers players = fake();
        FakeApi.answer = true;

        assertTrue(players.available());
        assertEquals("Fake", players.source());
        assertTrue(players.isBedrock(JAVA_ID), "the plugin said yes");
        assertEquals(List.of(JAVA_ID), FakeApi.ASKED);
    }

    /**
     * A Bedrock player who linked a Java account carries an ordinary Java UUID, so the plugin
     * is the only thing that can recognise them — the shape alone never would.
     */
    @Test
    void recognisesALinkedAccountThroughThePlugin() throws Exception {
        BedrockPlayers players = fake();
        FakeApi.answer = true;

        assertTrue(players.isBedrock(JAVA_ID));
        assertFalse(BedrockPlayers.hasFloodgateShape(JAVA_ID));
    }

    /**
     * The shape still decides when the plugin says no, which covers the window during login
     * where the player is not registered with it yet.
     */
    @Test
    void trustsTheUuidShapeEvenWhenThePluginSaysNo() throws Exception {
        BedrockPlayers players = fake();
        FakeApi.answer = false;

        assertTrue(players.isBedrock(FLOODGATE_ID));
        assertFalse(players.isBedrock(JAVA_ID));
    }

    @Test
    void survivesAPluginThatThrows() throws Exception {
        BedrockPlayers players = fake();
        FakeApi.explode = true;

        assertTrue(players.isBedrock(FLOODGATE_ID), "the shape answers when the plugin cannot");
        assertFalse(players.isBedrock(JAVA_ID));
        assertFalse(players.isBedrock(JAVA_ID), "a second failure must not spam the log");
    }

    /** The API instance is null until the plugin has finished enabling. */
    @Test
    void survivesAPluginThatHasNotStartedYet() throws Exception {
        BedrockPlayers players = fake();
        FakeApi.instance = null;

        assertTrue(players.isBedrock(FLOODGATE_ID));
        assertFalse(players.isBedrock(JAVA_ID));
    }

    @Test
    void reportsTheUuidShapeWhenNeitherPluginIsPresent() {
        BedrockPlayers players = BedrockPlayers.discover(LOGGER, new ClassLoader(null) {});

        assertFalse(players.available());
        assertEquals("UUID", players.source());
    }

    /**
     * A soft dependency is optional by definition: a half-installed or relocated Geyser can
     * make the class lookup fail with something other than ClassNotFoundException, and that
     * must cost the lookup rather than the whole plugin.
     */
    @Test
    void survivesAClassLoaderThatFailsInUnexpectedWays() {
        for (Throwable failure : new Throwable[] {
                new NullPointerException("No jar file selected"),
                new IllegalStateException("class loader closed"),
                new NoClassDefFoundError("org/geysermc/floodgate/api/FloodgateApi")}) {
            BedrockPlayers players = BedrockPlayers.discover(LOGGER, hostileLoader(failure));

            assertFalse(players.available());
            assertTrue(players.isBedrock(FLOODGATE_ID));
            assertFalse(players.isBedrock(JAVA_ID));
        }
    }

    private static ClassLoader hostileLoader(Throwable failure) {
        return new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw (Error) failure;
            }
        };
    }

    private static BedrockPlayers fake() throws NoSuchMethodException {
        Method accessor = FakeApi.class.getMethod("getInstance");
        Method question = FakeApi.class.getMethod("isFloodgatePlayer", UUID.class);
        return new BedrockPlayers(LOGGER, new BedrockPlayers.Probe("Fake", accessor, question));
    }
}
