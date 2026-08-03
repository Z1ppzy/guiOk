/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class PlaceholderBridge {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    private final Logger logger;
    private final Method setPlaceholders;
    private boolean reportedFailure;

    private PlaceholderBridge(Logger logger, Method setPlaceholders) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.setPlaceholders = setPlaceholders;
    }

    public static PlaceholderBridge discover(Logger logger, ClassLoader classLoader) {
        try {
            Class<?> api = Class.forName(
                    "me.clip.placeholderapi.PlaceholderAPI", false, classLoader);
            Method method = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            return new PlaceholderBridge(logger, method);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return new PlaceholderBridge(logger, null);
        }
    }

    public boolean available() {
        return setPlaceholders != null;
    }

    public Component resolve(Player player, String identifier) {
        if (!SAFE_IDENTIFIER.matcher(identifier).matches() || setPlaceholders == null) {
            return Component.text("—");
        }
        String token = '%' + identifier + '%';
        try {
            Object result = setPlaceholders.invoke(null, player, token);
            if (!(result instanceof String rendered) || rendered.equals(token)) {
                return Component.text("—");
            }
            return rendered.indexOf('§') >= 0
                    ? LEGACY.deserialize(rendered)
                    : LEGACY_AMPERSAND.deserialize(rendered);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            if (!reportedFailure) {
                reportedFailure = true;
                logger.log(Level.WARNING, "PlaceholderAPI resolution failed", exception);
            }
            return Component.text("—");
        }
    }
}
