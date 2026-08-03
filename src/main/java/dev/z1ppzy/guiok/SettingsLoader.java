/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;

public final class SettingsLoader {
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
        }

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
                required(config, "sidebar.title-with-pack"),
                required(config, "sidebar.title-without-pack"),
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
        try {
            URI uri = new URI(raw);
            if (!uri.isAbsolute()
                    || !(uri.getScheme().equalsIgnoreCase("https")
                    || uri.getScheme().equalsIgnoreCase("http"))) {
                throw new ConfigException(path + " must be an absolute HTTP(S) URL");
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
