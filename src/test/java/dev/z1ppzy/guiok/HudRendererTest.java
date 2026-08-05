/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class HudRendererTest {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    @Test
    void resolvesBuiltInAndExternalPlaceholdersAsComponents() {
        HudContext context = new HudContext(
                "Alex",
                Component.text("Alex"),
                "oneblock",
                "7",
                "100",
                "42",
                "1.5K",
                "10",
                "64",
                "-3");

        Component result = new HudRenderer().render(
                "<player> | <world> | <balance> | <papi:oneblock_level>",
                context,
                identifier -> Component.text(identifier.equals("oneblock_level") ? "18" : "—"));

        assertEquals("Alex | oneblock | 1.5K | 18", PLAIN.serialize(result));
    }

    @Test
    void rendersPackIconAfterValueOnlyWhenPackIsApplied() {
        HudContext context = new HudContext(
                "Alex",
                Component.text("Alex"),
                "prison",
                "1",
                "100",
                "42",
                "1.5K",
                "10",
                "64",
                "-3");
        HudRenderer renderer = new HudRenderer();

        Component packed = renderer.render(
                "<balance><icon:coin>", context, identifier -> Component.empty(), true);
        Component fallback = renderer.render(
                "<balance><icon:coin>", context, identifier -> Component.empty(), false);

        assertEquals("1.5K \ue002", PLAIN.serialize(packed));
        assertEquals("1.5K", PLAIN.serialize(fallback));
    }

    @Test
    void resolvesEveryBuiltInPlaceholder() {
        Component result = new HudRenderer().render(
                "<player>|<world>|<online>|<max_online>|<ping>|<balance>|<x>|<y>|<z>",
                context("Alex"),
                identifier -> Component.empty());

        assertEquals("Alex|oneblock|7|100|42|1.5K|10|64|-3", PLAIN.serialize(result));
    }

    /**
     * Player names, display names and expansion output are attacker-influenced on a public
     * server, so none of them may be re-parsed as MiniMessage markup.
     */
    @Test
    void neverInterpretsPlayerOrExpansionTextAsMarkup() {
        String hostile = "<red><click:run_command:'/op me'>x</click></red>";
        HudContext hud = new HudContext(
                hostile, Component.text(hostile), hostile,
                "7", "100", "42", "1.5K", "10", "64", "-3");

        Component result = new HudRenderer().render(
                "<player> <world> <papi:some_expansion>",
                hud,
                identifier -> Component.text(hostile));

        assertEquals(hostile + ' ' + hostile + ' ' + hostile, PLAIN.serialize(result));
        assertNull(result.clickEvent());
        result.children().forEach(child -> assertNull(child.clickEvent()));
    }

    /**
     * MiniMessage swallows the error an unknown icon raises and emits the raw tag, so nothing
     * fails loudly at render time \u2014 {@code SettingsLoader} is what has to reject the typo.
     */
    @Test
    void printsUnknownIconTagsVerbatimInsteadOfFailing() {
        Component result = new HudRenderer().render(
                "<icon:coins>", context("Alex"), identifier -> Component.empty(), true);

        assertEquals("<icon:coins>", PLAIN.serialize(result));
        assertTrue(PackIcons.unknownIconNames("<icon:coins>").contains("coins"));
    }

    @Test
    void rendersAnUnresolvedExpansionWithoutBreakingTheLine() {
        Component result = new HudRenderer().render(
                "<gray>\u0423\u0440\u043e\u0432\u0435\u043d\u044c:</gray> <papi:missing_expansion>",
                context("Alex"),
                identifier -> Component.text("\u2014"));

        assertEquals("\u0423\u0440\u043e\u0432\u0435\u043d\u044c: \u2014", PLAIN.serialize(result));
    }

    @Test
    void plainRendersPromptsWithoutHudPlaceholders() {
        assertEquals(
                "\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u043e\u0444\u043e\u0440\u043c\u043b\u0435\u043d\u0438\u0435?",
                PLAIN.serialize(new HudRenderer().plain("<green>\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u043e\u0444\u043e\u0440\u043c\u043b\u0435\u043d\u0438\u0435?</green>")));
    }

    private static HudContext context(String player) {
        return new HudContext(
                player,
                Component.text(player),
                "oneblock",
                "7",
                "100",
                "42",
                "1.5K",
                "10",
                "64",
                "-3");
    }
}
