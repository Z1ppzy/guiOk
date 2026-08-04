/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Public service for creating and identifying GuiOk custom items, and for reading the
 * pack glyphs GuiOk registers in {@code minecraft:default}.
 */
public interface GuiOkApi {
    int API_VERSION = 2;

    int apiVersion();

    Set<String> itemIds();

    /** Icon names GuiOk publishes as glyphs, the same ones {@code %guiok_icon_<name>%} accepts. */
    Set<String> iconIds();

    /**
     * The character that draws an icon once the player has applied the pack. Because the
     * glyph is registered in the default font as well, the caller can drop it into any text
     * the client renders — a nametag, a scoreboard team prefix, chat or a menu.
     *
     * @return empty when no icon carries that name; never a substitute character
     */
    Optional<String> glyph(String iconId);

    boolean exists(String id);

    Optional<GuiOkItemDefinition> definition(String id);

    ItemStack create(String id);

    ItemStack create(String id, int amount);

    Optional<String> idOf(ItemStack item);

    boolean is(ItemStack item, String id);

    Map<Integer, ItemStack> give(Player player, String id, int amount);
}
