/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Tells a Bedrock player apart from a Java one.
 *
 * <p>Everything GuiOk draws through the pack — the glyph title, {@code <icon:…>}, the spaces,
 * the item models — is a Java resource pack feature. Geyser does not convert Java packs into
 * the Bedrock format, so a Bedrock player can never see any of it and has to be served the
 * text fallback instead.
 *
 * <p>Such a player cannot be trusted to say so through the pack callback, because Geyser
 * answers the request on the client's behalf: an optional pack is declined, but a
 * {@code required} one is reported as ACCEPTED, DOWNLOADED and SUCCESSFULLY_LOADED so the
 * server does not kick them. Believing that would put a private-use code point, in a font the
 * client does not have, on a screen that cannot draw it. So the client is classified before
 * the request is built, and a Bedrock one is never sent it at all.
 *
 * <p>Three sources answer, in order of how much they know:
 *
 * <ol>
 *   <li>Floodgate on this server. Authoritative, and the only source that recognises a Bedrock
 *       player who linked a Java account and therefore carries an ordinary Java UUID.
 *   <li>Geyser on this server, for a Geyser-Spigot install running without Floodgate.
 *   <li>The shape of the UUID, which needs neither plugin installed here. This is all that is
 *       left when Geyser and Floodgate live on the proxy and the backend sees only the
 *       forwarded profile.
 * </ol>
 *
 * <p>The shape is consulted even when a plugin answered no: it costs one comparison, no Java
 * UUID has it, and a plugin that has not finished registering the player would otherwise cost
 * them their HUD for the rest of the session.
 */
public final class BedrockPlayers {
    /** The name reported when no plugin was found and only the UUID shape answers. */
    private static final String UUID_SHAPE = "UUID";

    private final Logger logger;
    private final Probe probe;
    private boolean reportedFailure;

    BedrockPlayers(Logger logger, Probe probe) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.probe = probe;
    }

    /**
     * Looks for a plugin that can answer the question. Absence is the normal case — a server
     * without Geyser has no Bedrock players — and leaves the UUID shape as the only source.
     */
    public static BedrockPlayers discover(Logger logger, ClassLoader classLoader) {
        Probe floodgate = probe(
                logger,
                classLoader,
                "Floodgate",
                "org.geysermc.floodgate.api.FloodgateApi",
                "getInstance",
                "isFloodgatePlayer");
        if (floodgate != null) {
            return new BedrockPlayers(logger, floodgate);
        }
        return new BedrockPlayers(logger, probe(
                logger,
                classLoader,
                "Geyser",
                "org.geysermc.geyser.api.GeyserApi",
                "api",
                "isBedrockPlayer"));
    }

    /** True when a plugin, rather than the shape of the UUID alone, decides. */
    public boolean available() {
        return probe != null;
    }

    /** What answers the question: {@code Floodgate}, {@code Geyser} or {@code UUID}. */
    public String source() {
        return probe == null ? UUID_SHAPE : probe.name();
    }

    public boolean isBedrock(Player player) {
        return player != null && isBedrock(player.getUniqueId());
    }

    public boolean isBedrock(UUID playerId) {
        return playerId != null && (asks(playerId) || hasFloodgateShape(playerId));
    }

    /**
     * Floodgate builds a Bedrock player's UUID out of the Xbox user id alone, which leaves the
     * high half zero — a shape no Java UUID has, online or offline, since one is a version 4
     * random and the other a version 3 name hash. The nil UUID matches too and belongs to
     * nobody, so it is excluded rather than reported as a Bedrock player.
     */
    static boolean hasFloodgateShape(UUID playerId) {
        return playerId.getMostSignificantBits() == 0 && playerId.getLeastSignificantBits() != 0;
    }

    private boolean asks(UUID playerId) {
        if (probe == null) {
            return false;
        }
        try {
            return probe.asks(playerId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!reportedFailure) {
                reportedFailure = true;
                logger.log(
                        Level.WARNING,
                        "Cannot ask " + probe.name() + " whether a player is on Bedrock;"
                                + " falling back to the shape of the UUID",
                        failure);
            }
            return false;
        }
    }

    private static Probe probe(
            Logger logger,
            ClassLoader classLoader,
            String name,
            String className,
            String accessorName,
            String questionName) {
        try {
            Class<?> api = Class.forName(className, false, classLoader);
            Method accessor = api.getMethod(accessorName);
            Method question = api.getMethod(questionName, UUID.class);
            return new Probe(name, accessor, question);
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            return null;
        } catch (RuntimeException | LinkageError failure) {
            // A soft dependency is optional by definition, and a half-installed or relocated
            // Geyser can fail the lookup with something other than ClassNotFoundException.
            // That must cost the plugin's answer, not the whole plugin.
            logger.log(
                    Level.WARNING,
                    "Cannot inspect " + name + "; continuing without it",
                    failure);
            return null;
        }
    }

    /**
     * A plugin that can be asked: a static accessor returning its API instance, and the
     * instance method that answers for one player. Both Floodgate and Geyser expose exactly
     * this pair, which is why one probe covers both.
     */
    record Probe(String name, Method accessor, Method question) {
        private boolean asks(UUID playerId) throws ReflectiveOperationException {
            Object api = accessor.invoke(null);
            // Null until the plugin finishes enabling; the UUID shape covers that window.
            return api != null && Boolean.TRUE.equals(question.invoke(api, playerId));
        }
    }
}
