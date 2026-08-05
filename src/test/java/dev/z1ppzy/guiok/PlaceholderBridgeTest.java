/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaceholderBridgeTest {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();
    private static final Logger LOGGER = Logger.getLogger(PlaceholderBridgeTest.class.getName());

    /** Stands in for {@code PlaceholderAPI.setPlaceholders}; records every token it receives. */
    public static final class FakePlaceholderApi {
        static final List<String> TOKENS = new ArrayList<>();
        static String reply;
        static boolean explode;

        public static String setPlaceholders(OfflinePlayer player, String text) {
            TOKENS.add(text);
            if (explode) {
                throw new IllegalStateException("expansion blew up");
            }
            return reply == null ? text : reply;
        }
    }

    @BeforeEach
    void resetFake() {
        FakePlaceholderApi.TOKENS.clear();
        FakePlaceholderApi.reply = null;
        FakePlaceholderApi.explode = false;
    }

    @Test
    void reportsUnavailableWithoutPlaceholderApi() {
        PlaceholderBridge bridge = new PlaceholderBridge(LOGGER, null);

        assertFalse(bridge.available());
        assertEquals("—", PLAIN.serialize(bridge.resolve(null, "vault_eco_balance")));
    }

    /**
     * The identifier is interpolated into a {@code %...%} token, so anything that could close
     * the token early, smuggle a second placeholder or inject markup must be rejected before
     * PlaceholderAPI ever sees it.
     */
    @Test
    void neverForwardsIdentifiersThatCouldEscapeThePercentToken() throws Exception {
        PlaceholderBridge bridge = availableBridge();

        for (String identifier : new String[] {
                "",
                "%",
                "player%_%server_online",
                "player name",
                "player<red>",
                "player\nname",
                "a".repeat(129)}) {
            assertEquals(
                    "—",
                    PLAIN.serialize(bridge.resolve(null, identifier)),
                    "Identifier should have been rejected: " + identifier);
        }
        assertTrue(
                FakePlaceholderApi.TOKENS.isEmpty(),
                "Rejected identifiers reached PlaceholderAPI: " + FakePlaceholderApi.TOKENS);
    }

    @Test
    void forwardsOrdinaryIdentifiersWrappedInPercentSigns() throws Exception {
        PlaceholderBridge bridge = availableBridge();
        FakePlaceholderApi.reply = "18";

        assertEquals("18", PLAIN.serialize(bridge.resolve(null, "aoneblock_island_level")));
        assertEquals(List.of("%aoneblock_island_level%"), FakePlaceholderApi.TOKENS);
    }

    @Test
    void acceptsTheIdentifierShapesRealExpansionsUse() throws Exception {
        PlaceholderBridge bridge = availableBridge();
        FakePlaceholderApi.reply = "ok";

        for (String identifier : new String[] {
                "player_name", "vault_eco_balance", "some-plugin:stat.kills", "a".repeat(128)}) {
            assertEquals("ok", PLAIN.serialize(bridge.resolve(null, identifier)));
        }
        assertEquals(4, FakePlaceholderApi.TOKENS.size());
    }

    /** An unresolved placeholder comes back verbatim; showing "%foo%" on the HUD is worse. */
    @Test
    void collapsesUnresolvedPlaceholdersToTheDash() throws Exception {
        PlaceholderBridge bridge = availableBridge();

        assertEquals("—", PLAIN.serialize(bridge.resolve(null, "nobody_provides_this")));
    }

    @Test
    void understandsBothLegacyColourConventions() throws Exception {
        PlaceholderBridge bridge = availableBridge();

        FakePlaceholderApi.reply = "&aGreen";
        Component ampersand = bridge.resolve(null, "some_expansion");
        FakePlaceholderApi.reply = "§cRed";
        Component section = bridge.resolve(null, "some_expansion");

        assertEquals("Green", PLAIN.serialize(ampersand));
        assertEquals("Red", PLAIN.serialize(section));
        assertEquals(NamedTextColor.GREEN, ampersand.color());
        assertEquals(NamedTextColor.RED, section.color());
    }

    /** MiniMessage in an expansion result must stay inert text, not become live markup. */
    @Test
    void doesNotInterpretExpansionOutputAsMiniMessage() throws Exception {
        PlaceholderBridge bridge = availableBridge();
        FakePlaceholderApi.reply = "<red><click:run_command:'/op me'>click</click></red>";

        Component resolved = bridge.resolve(null, "hostile_expansion");

        assertEquals(FakePlaceholderApi.reply, PLAIN.serialize(resolved));
        assertNull(resolved.clickEvent());
    }

    @Test
    void survivesAnExpansionThatThrows() throws Exception {
        PlaceholderBridge bridge = availableBridge();
        FakePlaceholderApi.explode = true;

        assertEquals("—", PLAIN.serialize(bridge.resolve(null, "broken_expansion")));
        assertEquals("—", PLAIN.serialize(bridge.resolve(null, "broken_expansion")));
    }

    /**
     * A soft dependency is optional by definition. A half-installed or shaded PlaceholderAPI
     * can make the class lookup fail with something other than ClassNotFoundException, and a
     * plugin that lets that escape takes the whole server's GuiOk down at startup.
     */
    @Test
    void survivesAClassLoaderThatFailsInUnexpectedWays() {
        for (RuntimeException failure : new RuntimeException[] {
                new NullPointerException("No jar file selected"),
                new IllegalStateException("class loader closed")}) {
            PlaceholderBridge bridge = PlaceholderBridge.discover(LOGGER, hostileLoader(failure));

            assertFalse(bridge.available());
            assertEquals("—", PLAIN.serialize(bridge.resolve(null, "player_name")));
        }
    }

    @Test
    void survivesAClassLoaderThatThrowsALinkageError() {
        PlaceholderBridge bridge = PlaceholderBridge.discover(
                LOGGER, hostileLoader(new NoClassDefFoundError("me/clip/placeholderapi/Api")));

        assertFalse(bridge.available());
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

    private static PlaceholderBridge availableBridge() throws NoSuchMethodException {
        Method method = FakePlaceholderApi.class.getMethod(
                "setPlaceholders", OfflinePlayer.class, String.class);
        PlaceholderBridge bridge = new PlaceholderBridge(LOGGER, method);
        assertTrue(bridge.available());
        return bridge;
    }
}
