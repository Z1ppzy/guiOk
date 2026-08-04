/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the PNG glyphs shipped by the GuiOk resource pack. The same
 * code points are registered in {@code guiok:hud} and in {@code minecraft:default}, so the
 * sidebar renders them with an explicit font while third-party plugins print the bare
 * character.
 */
public final class PackIcons {
    private static final String PLACEHOLDER_PREFIX = "icon_";
    private static final Map<String, String> GLYPHS = Map.of(
            "logo", "\ue001",
            "coin", "\ue002");

    private PackIcons() {
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
