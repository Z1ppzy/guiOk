/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

class EconomyBridgeTest {
    private static final Logger LOGGER = Logger.getLogger(EconomyBridgeTest.class.getName());

    /** Stands in for a Vault {@code Economy} implementation; only the signature matters. */
    public static final class FakeEconomy {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile double balance;
        private final boolean explode;

        FakeEconomy(double balance, boolean explode) {
            this.balance = balance;
            this.explode = explode;
        }

        public double getBalance(OfflinePlayer player) {
            calls.incrementAndGet();
            if (explode) {
                throw new IllegalStateException("economy backend is down");
            }
            return balance;
        }
    }

    /**
     * Economy plugins are not GuiOk softdepends, so on a busy server the Vault service is
     * often registered after GuiOk enables. Resolving the provider once during startup left
     * the balance line permanently empty until the next full restart.
     */
    @Test
    void picksUpAnEconomyRegisteredAfterStartup() throws Exception {
        AtomicReference<Object> registration = new AtomicReference<>();
        EconomyBridge bridge = new EconomyBridge(LOGGER, registration::get, balanceMethod());

        assertFalse(bridge.available());
        assertEquals(OptionalDouble.empty(), bridge.balance(null));

        registration.set(new FakeEconomy(1_500, false));

        assertTrue(bridge.available());
        assertEquals(OptionalDouble.of(1_500), bridge.balance(null));
    }

    @Test
    void stopsLookingUpTheServiceOnceResolved() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        EconomyBridge bridge = new EconomyBridge(
                LOGGER,
                () -> {
                    lookups.incrementAndGet();
                    return new FakeEconomy(42, false);
                },
                balanceMethod());

        bridge.balance(null);
        bridge.balance(null);
        bridge.available();

        assertEquals(1, lookups.get());
    }

    @Test
    void reportsUnavailableWhenVaultIsMissingEntirely() {
        EconomyBridge bridge = new EconomyBridge(LOGGER, () -> null, null);

        assertFalse(bridge.available());
        assertEquals(OptionalDouble.empty(), bridge.balance(null));
    }

    /** A throwing provider must degrade to "no balance", never break sidebar rendering. */
    @Test
    void swallowsProviderFailures() throws Exception {
        EconomyBridge bridge = new EconomyBridge(
                LOGGER, () -> new FakeEconomy(0, true), balanceMethod());

        assertEquals(OptionalDouble.empty(), bridge.balance(null));
        assertEquals(OptionalDouble.empty(), bridge.balance(null));
    }

    /**
     * {@code getBalance} blocks the server thread, and a SQL-backed economy turns it into a
     * database round trip. The sidebar asks once per player per refresh, so the reads have to
     * collapse onto a cache rather than scale with the player count.
     */
    @Test
    void servesRepeatedReadsFromTheCacheWithinItsLifetime() throws Exception {
        FakeEconomy economy = new FakeEconomy(1_500, false);
        AtomicLong clock = new AtomicLong();
        EconomyBridge bridge = new EconomyBridge(
                LOGGER, () -> economy, balanceMethod(), clock::get);
        OfflinePlayer player = player(UUID.randomUUID());

        for (int refresh = 0; refresh < 20; refresh++) {
            assertEquals(OptionalDouble.of(1_500), bridge.balance(player));
            clock.addAndGet(EconomyBridge.CACHE_NANOS / 10);
        }

        assertEquals(2, economy.calls.get(), "20 refreshes should collapse onto 2 lookups");
    }

    @Test
    void readsAgainOnceTheCachedBalanceExpires() throws Exception {
        FakeEconomy economy = new FakeEconomy(100, false);
        AtomicLong clock = new AtomicLong();
        EconomyBridge bridge = new EconomyBridge(
                LOGGER, () -> economy, balanceMethod(), clock::get);
        OfflinePlayer player = player(UUID.randomUUID());

        assertEquals(OptionalDouble.of(100), bridge.balance(player));
        economy.balance = 250;
        assertEquals(OptionalDouble.of(100), bridge.balance(player), "still inside the cache");

        clock.addAndGet(EconomyBridge.CACHE_NANOS);

        assertEquals(OptionalDouble.of(250), bridge.balance(player));
        assertEquals(2, economy.calls.get());
    }

    @Test
    void cachesEachPlayerSeparately() throws Exception {
        FakeEconomy economy = new FakeEconomy(7, false);
        AtomicLong clock = new AtomicLong();
        EconomyBridge bridge = new EconomyBridge(
                LOGGER, () -> economy, balanceMethod(), clock::get);

        bridge.balance(player(UUID.randomUUID()));
        bridge.balance(player(UUID.randomUUID()));
        bridge.balance(player(UUID.randomUUID()));

        assertEquals(3, economy.calls.get());
    }

    /** Entries for players who left must not pile up for the lifetime of the server. */
    @Test
    void forgetsPlayersThatStoppedBeingRefreshed() throws Exception {
        FakeEconomy economy = new FakeEconomy(7, false);
        AtomicLong clock = new AtomicLong();
        EconomyBridge bridge = new EconomyBridge(
                LOGGER, () -> economy, balanceMethod(), clock::get);

        for (int player = 0; player < 500; player++) {
            bridge.balance(player(UUID.randomUUID()));
            clock.addAndGet(EconomyBridge.CACHE_NANOS / 4);
        }

        assertTrue(
                bridge.cachedPlayerCount() <= 8,
                "Cache kept " + bridge.cachedPlayerCount() + " departed players");
    }

    @Test
    void doesNotCacheWhenThereIsNoPlayerToKeyOn() throws Exception {
        FakeEconomy economy = new FakeEconomy(7, false);
        EconomyBridge bridge = new EconomyBridge(LOGGER, () -> economy, balanceMethod());

        bridge.balance(null);
        bridge.balance(null);

        assertEquals(2, economy.calls.get());
        assertEquals(0, bridge.cachedPlayerCount());
    }

    /** A broken Vault install must cost the balance line, not the whole plugin's startup. */
    @Test
    void survivesAClassLoaderThatFailsInUnexpectedWays() {
        ClassLoader hostile = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                throw new NullPointerException("No jar file selected");
            }
        };

        EconomyBridge bridge = EconomyBridge.discover(LOGGER, null, hostile);

        assertFalse(bridge.available());
        assertEquals(OptionalDouble.empty(), bridge.balance(null));
    }

    private static Method balanceMethod() throws NoSuchMethodException {
        return FakeEconomy.class.getMethod("getBalance", OfflinePlayer.class);
    }

    /** An OfflinePlayer that answers getUniqueId and nothing else — all the cache keys on. */
    private static OfflinePlayer player(UUID id) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                EconomyBridgeTest.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakePlayer(" + id + ')';
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
