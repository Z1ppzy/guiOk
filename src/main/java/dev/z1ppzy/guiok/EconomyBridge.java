/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyBridge {
    /**
     * How long a balance stays good enough for the HUD.
     *
     * <p>{@code Economy#getBalance} is a blocking call on the server thread, and a SQL-backed
     * economy makes it a database round trip. The sidebar asks once per player per refresh —
     * once a second by default — so without a cache the cost scales with the player count on
     * the thread that runs the game. Two seconds keeps the balance visibly live while halving
     * that at the default refresh rate.
     */
    static final long CACHE_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final Logger logger;
    private final Supplier<Object> providerLookup;
    private final Method getBalance;
    private final LongSupplier clock;
    private final Map<UUID, CachedBalance> cache = new ConcurrentHashMap<>();
    private volatile Object provider;
    private volatile long lastSweep;
    private boolean reportedFailure;

    EconomyBridge(Logger logger, Supplier<Object> providerLookup, Method getBalance) {
        this(logger, providerLookup, getBalance, System::nanoTime);
    }

    EconomyBridge(
            Logger logger,
            Supplier<Object> providerLookup,
            Method getBalance,
            LongSupplier clock) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.providerLookup = Objects.requireNonNull(providerLookup, "providerLookup");
        this.getBalance = getBalance;
        this.clock = Objects.requireNonNull(clock, "clock");
        lastSweep = clock.getAsLong();
    }

    public static EconomyBridge discover(Logger logger, Server server, ClassLoader classLoader) {
        try {
            Class<?> economyType = Class.forName(
                    "net.milkbowl.vault.economy.Economy", false, classLoader);
            Method balance = economyType.getMethod("getBalance", OfflinePlayer.class);
            return new EconomyBridge(logger, () -> lookup(server, economyType), balance);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return new EconomyBridge(logger, () -> null, null);
        } catch (RuntimeException | LinkageError failure) {
            // Vault is a soft dependency; a broken install must cost the balance line only.
            logger.log(
                    Level.WARNING, "Cannot inspect Vault; continuing without economy", failure);
            return new EconomyBridge(logger, () -> null, null);
        }
    }

    public boolean available() {
        return getBalance != null && provider() != null;
    }

    public OptionalDouble balance(OfflinePlayer player) {
        Object economy = getBalance == null ? null : provider();
        if (economy == null) {
            return OptionalDouble.empty();
        }
        UUID playerId = player == null ? null : player.getUniqueId();
        if (playerId == null) {
            return lookUpBalance(economy, player);
        }
        long now = clock.getAsLong();
        CachedBalance cached = cache.get(playerId);
        if (cached != null && now - cached.readAt() < CACHE_NANOS) {
            return cached.value();
        }
        OptionalDouble fresh = lookUpBalance(economy, player);
        sweep(now);
        cache.put(playerId, new CachedBalance(fresh, now));
        return fresh;
    }

    /** Number of players currently held in the balance cache; for tests. */
    int cachedPlayerCount() {
        return cache.size();
    }

    private OptionalDouble lookUpBalance(Object economy, OfflinePlayer player) {
        try {
            Object result = getBalance.invoke(economy, player);
            return result instanceof Number number
                    ? OptionalDouble.of(number.doubleValue())
                    : OptionalDouble.empty();
        } catch (IllegalAccessException | InvocationTargetException exception) {
            if (!reportedFailure) {
                reportedFailure = true;
                logger.log(Level.WARNING, "Vault economy lookup failed", exception);
            }
            return OptionalDouble.empty();
        }
    }

    /** Discards entries for players who left; at most one pass per cache lifetime. */
    private void sweep(long now) {
        if (now - lastSweep < CACHE_NANOS) {
            return;
        }
        lastSweep = now;
        cache.values().removeIf(entry -> now - entry.readAt() >= CACHE_NANOS);
    }

    /**
     * Economy plugins register their Vault provider on their own schedule and are not GuiOk
     * softdepends, so the registration is resolved on demand — looking it up once during
     * {@code onEnable} would miss every provider that enables after GuiOk — and cached as soon
     * as one shows up.
     */
    private Object provider() {
        Object resolved = provider;
        if (resolved == null) {
            resolved = providerLookup.get();
            provider = resolved;
        }
        return resolved;
    }

    private static Object lookup(Server server, Class<?> economyType) {
        RegisteredServiceProvider<?> registration =
                server.getServicesManager().getRegistration(economyType);
        return registration == null ? null : registration.getProvider();
    }

    private record CachedBalance(OptionalDouble value, long readAt) {
    }
}
