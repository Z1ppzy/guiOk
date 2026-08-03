package dev.z1ppzy.guiok;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyBridge {
    private final Logger logger;
    private final Object provider;
    private final Method getBalance;
    private boolean reportedFailure;

    private EconomyBridge(Logger logger, Object provider, Method getBalance) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.provider = provider;
        this.getBalance = getBalance;
    }

    public static EconomyBridge discover(Logger logger, Server server, ClassLoader classLoader) {
        try {
            Class<?> economyType = Class.forName(
                    "net.milkbowl.vault.economy.Economy", false, classLoader);
            RegisteredServiceProvider<?> registration =
                    server.getServicesManager().getRegistration(economyType);
            if (registration == null) {
                return new EconomyBridge(logger, null, null);
            }
            Object provider = registration.getProvider();
            Method balance = economyType.getMethod("getBalance", OfflinePlayer.class);
            return new EconomyBridge(logger, provider, balance);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return new EconomyBridge(logger, null, null);
        }
    }

    public boolean available() {
        return provider != null && getBalance != null;
    }

    public OptionalDouble balance(OfflinePlayer player) {
        if (!available()) {
            return OptionalDouble.empty();
        }
        try {
            Object result = getBalance.invoke(provider, player);
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
}
