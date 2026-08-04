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

    /**
     * The status icons are a published contract: TAB configs and the prison plugin address
     * them by name, so renaming one silently breaks live nametags.
     */
    @Test
    void publishesEveryStatusIcon() {
        assertEquals("\ue200", PackIcons.glyph("star"));
        assertEquals("\ue201", PackIcons.glyph("crown"));
        assertEquals("\ue202", PackIcons.glyph("ember"));
        assertEquals("\ue203", PackIcons.glyph("pickaxe"));
        assertEquals("\ue204", PackIcons.glyph("shackle"));
        assertEquals("\ue205", PackIcons.glyph("skull"));
        assertEquals("\ue206", PackIcons.glyph("gem"));
        assertEquals("\ue207", PackIcons.glyph("clover"));
        assertEquals("\ue208", PackIcons.glyph("bracket_left"));
        assertEquals("\ue209", PackIcons.glyph("bracket_right"));
        assertEquals("\ue210", PackIcons.glyph("crown_tall"));
        assertEquals("\ue220", PackIcons.glyph("gui_backpack"));
    }

    @Test
    void keepsCodePointsUnique() {
        assertEquals(
                PackIcons.names().size(),
                PackIcons.names().stream().map(PackIcons::glyph).distinct().count());
    }

    @Test
    void exposesEveryNameThroughBothLookups() {
        for (String name : PackIcons.names()) {
            assertEquals(PackIcons.glyph(name), PackIcons.placeholderGlyph("icon_" + name));
            assertTrue(PackIcons.glyph(name).codePointAt(0) >= 0xe000);
        }
    }
}
