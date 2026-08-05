/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;

public final class SettingsLoader {
    /**
     * Protocol ceiling for the pack URL. Deliberately not the 255 characters the legacy
     * {@code Player#setResourcePack(String)} documents: GuiOk pushes packs through Adventure's
     * {@code sendResourcePacks}, and a pre-signed S3 or CDN link — the normal way to host a
     * private pack — carries enough query string to sail past 255 while working perfectly.
     */
    private static final int MAX_URL_LENGTH = 32_767;

    private SettingsLoader() {
    }

    public static PluginSettings load(FileConfiguration config, BuildInfo buildInfo)
            throws ConfigException {
        boolean packEnabled = config.getBoolean("resource-pack.enabled", true);
        URI packUrl = uri(required(config, "resource-pack.url"), "resource-pack.url");
        UUID packId = uuid(required(config, "resource-pack.id"), "resource-pack.id");
        String configuredHash = required(config, "resource-pack.sha1").toLowerCase(Locale.ROOT);
        String packHash = configuredHash.equals("auto")
                ? buildInfo.resourcePackSha1().toLowerCase(Locale.ROOT)
                : configuredHash;
        if (!packHash.matches("[0-9a-f]{40}")) {
            throw new ConfigException(
                    "resource-pack.sha1 must be 'auto' or a lowercase 40-character SHA-1 digest");
        }
        long delayTicks = boundedLong(config, "resource-pack.delay-ticks", 0, 1200);

        List<String> lines = config.getStringList("sidebar.lines");
        if (lines.isEmpty() || lines.size() > 15) {
            throw new ConfigException("sidebar.lines must contain between 1 and 15 entries");
        }
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index) == null || lines.get(index).length() > 2048) {
                throw new ConfigException("sidebar.lines[" + index + "] is invalid or longer than 2048 chars");
            }
            requireKnownIcons(lines.get(index), "sidebar.lines[" + index + ']');
            requireNoRemovedTags(lines.get(index), "sidebar.lines[" + index + ']');
        }

        String packedTitle = required(config, "sidebar.title-with-pack");
        String fallbackTitle = required(config, "sidebar.title-without-pack");
        requireKnownIcons(packedTitle, "sidebar.title-with-pack");
        requireKnownIcons(fallbackTitle, "sidebar.title-without-pack");
        requireNoRemovedTags(packedTitle, "sidebar.title-with-pack");
        requireNoRemovedTags(fallbackTitle, "sidebar.title-without-pack");

        PluginSettings.ResourcePackSettings resourcePack =
                new PluginSettings.ResourcePackSettings(
                        packEnabled,
                        packUrl,
                        packId,
                        packHash,
                        config.getBoolean("resource-pack.required", false),
                        config.getBoolean("resource-pack.replace-existing-packs", false),
                        config.getBoolean("resource-pack.send-on-join", true),
                        delayTicks,
                        required(config, "resource-pack.prompt"));

        PluginSettings.SidebarSettings sidebar = new PluginSettings.SidebarSettings(
                config.getBoolean("sidebar.enabled", true),
                config.getBoolean("sidebar.wait-for-pack", true),
                config.getBoolean("sidebar.fallback-on-pack-failure", true),
                config.getBoolean("sidebar.replace-existing-scoreboard", true),
                boundedLong(config, "sidebar.refresh-ticks", 10, 1200),
                packedTitle,
                fallbackTitle,
                lines);

        PluginSettings.MessageSettings messages = new PluginSettings.MessageSettings(
                required(config, "messages.prefix"),
                required(config, "messages.no-permission"),
                required(config, "messages.reloaded"),
                required(config, "messages.hidden"),
                required(config, "messages.shown"),
                required(config, "messages.pack-resent"));

        return new PluginSettings(resourcePack, sidebar, messages);
    }

    /**
     * Tags GuiOk used to provide itself, with the replacement to migrate to. MiniMessage
     * prints an unknown tag verbatim, so without this an upgraded server would quietly show
     * "&lt;balance&gt;" on every sidebar instead of a number. Drop an entry once operators
     * have had a release or two to move over.
     */
    private static final Map<String, String> REMOVED_TAGS = Map.of(
            "balance",
            "GuiOk no longer reads Vault; use a PlaceholderAPI placeholder instead, "
                    + "for example <papi:cmi_user_balance_formatted> or "
                    + "<papi:vault_eco_balance_formatted>");

    private static void requireNoRemovedTags(String template, String path)
            throws ConfigException {
        for (Map.Entry<String, String> removed : REMOVED_TAGS.entrySet()) {
            if (template.contains('<' + removed.getKey() + '>')) {
                throw new ConfigException(path + " uses the removed <" + removed.getKey()
                        + "> tag: " + removed.getValue());
            }
        }
    }

    private static void requireKnownIcons(String template, String path) throws ConfigException {
        Set<String> unknown = PackIcons.unknownIconNames(template);
        if (!unknown.isEmpty()) {
            throw new ConfigException(path + " references unknown GuiOk icons "
                    + unknown + "; available: " + new TreeSet<>(PackIcons.names()));
        }
    }

    private static String required(FileConfiguration config, String path) throws ConfigException {
        String value = config.getString(path);
        if (value == null || value.isBlank()) {
            throw new ConfigException(path + " must be a non-empty string");
        }
        return value;
    }

    private static long boundedLong(FileConfiguration config, String path, long min, long max)
            throws ConfigException {
        if (!config.isInt(path) && !config.isLong(path)) {
            throw new ConfigException(path + " must be an integer between " + min + " and " + max);
        }
        long value = config.getLong(path);
        if (value < min || value > max) {
            throw new ConfigException(path + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static URI uri(String raw, String path) throws ConfigException {
        if (raw.length() > MAX_URL_LENGTH) {
            throw new ConfigException(
                    path + " must be at most " + MAX_URL_LENGTH + " characters");
        }
        try {
            URI uri = new URI(raw);
            if (!uri.isAbsolute()
                    || !(uri.getScheme().equalsIgnoreCase("https")
                    || uri.getScheme().equalsIgnoreCase("http"))) {
                throw new ConfigException(path + " must be an absolute HTTP(S) URL");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ConfigException(
                        path + " must name a host, such as https://example.com/pack.zip");
            }
            if (!raw.chars().allMatch(character -> character <= 0x7f)) {
                throw new ConfigException(path + " must contain only ASCII characters; URL-encode it first");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ConfigException(path + " is not a valid URI: " + exception.getMessage());
        }
    }

    private static UUID uuid(String raw, String path) throws ConfigException {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(path + " is not a valid UUID");
        }
    }
}
