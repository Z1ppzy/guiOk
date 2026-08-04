/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackIconsTest {
    @Test
    void resolvesKnownGlyphs() {
        assertEquals("\ue002", PackIcons.glyph("coin"));
        assertEquals("\ue001", PackIcons.glyph("logo"));
    }

    @Test
    void ignoresUnknownName() {
        assertNull(PackIcons.glyph("teapot"));
    }

    @Test
    void resolvesPlaceholderParameters() {
        assertEquals("\ue002", PackIcons.placeholderGlyph("icon_coin"));
        assertEquals("\ue002", PackIcons.placeholderGlyph("ICON_COIN"));
    }

    @Test
    void rejectsForeignPlaceholderParameters() {
        assertNull(PackIcons.placeholderGlyph("balance"));
        assertNull(PackIcons.placeholderGlyph("icon_teapot"));
        assertNull(PackIcons.placeholderGlyph("icon_"));
    }

    @Test
    void exposesEveryNameThroughBothLookups() {
        for (String name : PackIcons.names()) {
            assertEquals(PackIcons.glyph(name), PackIcons.placeholderGlyph("icon_" + name));
            assertTrue(PackIcons.glyph(name).codePointAt(0) >= 0xe000);
        }
    }
}
