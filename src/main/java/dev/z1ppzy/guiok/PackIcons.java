/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the PNG glyphs shipped by the GuiOk resource pack. The same
 * code points are registered in {@code guiok:hud} and in {@code minecraft:default}, so the
 * sidebar renders them with an explicit font while third-party plugins print the bare
 * character.
 *
 * <p>The default-font half is what makes these usable above a player's head: TAB writes the
 * character into a scoreboard-team prefix and the client draws the picture, because nametags
 * have no way to select a font. The resource-pack build refuses to publish a glyph that is
 * missing from this map, so a placeholder can never resolve to nothing.
 *
 * <p>{@code U+E0xx} is the HUD range, {@code U+E1xx} the pause menu, {@code U+E2xx} the
 * status icons meant for names, tags and chat.
 */
public final class PackIcons {
    private static final String PLACEHOLDER_PREFIX = "icon_";
    private static final Map<String, String> GLYPHS = glyphs();

    private PackIcons() {
    }

    private static Map<String, String> glyphs() {
        Map<String, String> glyphs = new LinkedHashMap<>();
        glyphs.put("logo", "\ue001");
        glyphs.put("coin", "\ue002");
        glyphs.put("star", "\ue200");
        glyphs.put("crown", "\ue201");
        glyphs.put("ember", "\ue202");
        glyphs.put("pickaxe", "\ue203");
        glyphs.put("shackle", "\ue204");
        glyphs.put("skull", "\ue205");
        glyphs.put("gem", "\ue206");
        glyphs.put("clover", "\ue207");
        glyphs.put("bracket_left", "\ue208");
        glyphs.put("bracket_right", "\ue209");
        glyphs.put("crown_tall", "\ue210");
        return Collections.unmodifiableMap(glyphs);
    }

    /** Returns the glyph for an icon name, or {@code null} when the name is unknown. */
    public static String glyph(String name) {
        return GLYPHS.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Resolves a PlaceholderAPI parameter such as {@code icon_coin}. Returns {@code null}
     * when the parameter is not an icon request or names an unknown icon.
     */
    public static String placeholderGlyph(String parameters) {
        String normalized = parameters.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(PLACEHOLDER_PREFIX)) {
            return null;
        }
        return GLYPHS.get(normalized.substring(PLACEHOLDER_PREFIX.length()));
    }

    public static Set<String> names() {
        return GLYPHS.keySet();
    }
}
