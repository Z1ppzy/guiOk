package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
