/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.Objects;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Exposes GuiOk pack glyphs as {@code %guiok_icon_<name>%} so plugins with their own text
 * pipeline — TAB, hologram and menu plugins — can print an icon without pasting an
 * invisible private-use character into their configuration.
 *
 * <p>The class references PlaceholderAPI directly and must only be loaded when that plugin
 * is installed.
 */
public final class GuiOkPlaceholderExpansion extends PlaceholderExpansion {
    private final GuiOkPlugin plugin;

    public GuiOkPlaceholderExpansion(GuiOkPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String getIdentifier() {
        return "guiok";
    }

    @Override
    public String getAuthor() {
        return plugin.buildInfo().author();
    }

    @Override
    public String getVersion() {
        return plugin.buildInfo().version();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer requester, String parameters) {
        String glyph = PackIcons.placeholderGlyph(parameters);
        if (glyph == null) {
            return null;
        }
        Player player = requester == null ? null : requester.getPlayer();
        if (player == null || !plugin.resourcePacks().usesPackedTitle(player)) {
            return "";
        }
        return glyph;
    }
}
