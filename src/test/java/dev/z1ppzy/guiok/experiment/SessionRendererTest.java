/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

/*
 * Experiment, not production code. Correctness first — a cache that shows a stale number is
 * worse than a slow one — then the measurement under a realistic movement pattern.
 */

package dev.z1ppzy.guiok.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.z1ppzy.guiok.HudContext;
import dev.z1ppzy.guiok.HudRenderer;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class SessionRendererTest {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private static HudContext at(String world, String online, String x, String ping) {
        return new HudContext(
                "Z1ppzy", Component.text("Z1ppzy"), world, online, "200", ping, x, "64", "-512");
    }

    /** Every refresh, against every context, must match the current renderer exactly. */
    @Test
    void matchesTheCurrentRendererAcrossAChangingWorld() {
        HudRenderer current = new HudRenderer();
        SessionRenderer prototype = new SessionRenderer();
        SessionRenderer.Session session = prototype.openSession();

        List<HudContext> timeline = List.of(
                at("prison", "87", "128", "31"),
                at("prison", "87", "128", "31"),
                at("prison", "88", "128", "44"),
                at("prison", "88", "129", "44"),
                at("nether", "88", "129", "44"),
                at("nether", "90", "500", "12"));

        for (HudContext hud : timeline) {
            for (String template : templates()) {
                assertEquals(
                        current.render(template, hud, RenderCostTest::papi, true),
                        session.render(template, hud, RenderCostTest::papi, true),
                        "Diverged on '" + template + "' at " + hud.world() + '/' + hud.online());
            }
        }
    }

    /** The whole point: a line must refresh the moment the value it reads moves. */
    @Test
    void reRendersAsSoonAsADependencyChanges() {
        SessionRenderer.Session session = new SessionRenderer().openSession();
        String worldLine = "<gray>Мир:</gray> <white><world></white>";

        assertEquals("Мир: prison",
                PLAIN.serialize(session.render(
                        worldLine, at("prison", "1", "0", "10"), RenderCostTest::papi, true)));
        // Values the line does not read moved; the rendered text must not.
        assertEquals("Мир: prison",
                PLAIN.serialize(session.render(
                        worldLine, at("prison", "99", "777", "250"), RenderCostTest::papi, true)));
        // The value it does read moved.
        assertEquals("Мир: nether",
                PLAIN.serialize(session.render(
                        worldLine, at("nether", "99", "777", "250"), RenderCostTest::papi, true)));
    }

    /** PlaceholderAPI can change with none of our values moving, so it is never cached. */
    @Test
    void neverCachesALineThatReadsPlaceholderApi() {
        SessionRenderer.Session session = new SessionRenderer().openSession();
        String line = "<green><papi:balance></green>";
        HudContext frozen = at("prison", "1", "0", "10");
        int[] refresh = {0};

        String first = PLAIN.serialize(session
                .beginRefresh(frozen, id -> Component.text(++refresh[0] + "K"), true)
                .render(line));
        String second = PLAIN.serialize(session
                .beginRefresh(frozen, id -> Component.text(++refresh[0] + "K"), true)
                .render(line));

        assertEquals("1K", first);
        assertEquals("2K", second, "a PlaceholderAPI line must never be served from the cache");
    }

    /**
     * MiniMessage consults a tag resolver twice per parse, so the current renderer pays two
     * PlaceholderAPI round trips per tag — and two more for a second line reading the same
     * placeholder. One refresh must ask for a given placeholder exactly once.
     */
    @Test
    void asksPlaceholderApiOncePerPlaceholderPerRefresh() {
        SessionRenderer.Session session = new SessionRenderer().openSession();
        HudContext hud = at("prison", "87", "128", "31");
        List<String> asked = new java.util.ArrayList<>();

        SessionRenderer.Session.Refresh refresh =
                session.beginRefresh(hud, identifier -> {
                    asked.add(identifier);
                    return Component.text("1.5K");
                }, true);
        refresh.render("<papi:balance>");
        refresh.render("<green><papi:balance></green> и <papi:rank>");

        assertEquals(List.of("balance", "rank"), asked,
                "каждый плейсхолдер ровно один раз за рефреш, фактически: " + asked);
    }

    @Test
    void tracksExactlyTheTagsATemplateReads() {
        SessionRenderer renderer = new SessionRenderer();

        assertEquals(0, renderer.dependenciesOf("<gray>статичная строка</gray>"));
        assertTrue(renderer.dependenciesOf("<player>") != 0);
        assertTrue(renderer.dependenciesOf("<x> <y> <z>")
                > renderer.dependenciesOf("<x>"), "more tags means more dependencies");
    }

    /**
     * A standing player is the common case on a prison server: mining in one spot, ping and
     * coordinates steady between refreshes.
     */
    @Test
    void measuresAStandingPlayer() {
        report("СТОИТ НА МЕСТЕ", at("prison", "87", "128", "31"), at("prison", "87", "128", "31"));
    }

    /** A walking player moves coordinates every refresh — the worst case for the cache. */
    @Test
    void measuresAWalkingPlayer() {
        report("ИДЁТ (x и ping меняются)",
                at("prison", "87", "128", "31"), at("prison", "87", "129", "34"));
    }

    private static void report(String label, HudContext first, HudContext second) {
        HudRenderer current = new HudRenderer();
        SessionRenderer prototype = new SessionRenderer();
        SessionRenderer.Session session = prototype.openSession();
        HudContext[] flip = {first, second};
        int[] tick = {0};

        long before = measure(() -> {
            HudContext hud = flip[tick[0]++ & 1];
            long sum = 0;
            for (String template : templates()) {
                sum += current.render(template, hud, RenderCostTest::papi, true).hashCode();
            }
            return sum;
        });
        long after = measure(() -> {
            HudContext hud = flip[tick[0]++ & 1];
            long sum = 0;
            for (String template : templates()) {
                sum += session.render(template, hud, RenderCostTest::papi, true).hashCode();
            }
            return sum;
        });

        System.out.printf(
                "%-26s сейчас %6d нс -> прототип %6d нс   выигрыш %.1f%%"
                        + "   (500 игроков: %.1f -> %.1f мс в тике)%n",
                label, before, after, 100.0 * (before - after) / before,
                before * 500 / 1e6, after * 500 / 1e6);
    }

    private static long measure(java.util.function.LongSupplier work) {
        long checksum = 0;
        for (int round = 0; round < 20_000; round++) {
            checksum += work.getAsLong();
        }
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 7; round++) {
            long start = System.nanoTime();
            for (int iteration = 0; iteration < 2_000; iteration++) {
                checksum += work.getAsLong();
            }
            best = Math.min(best, (System.nanoTime() - start) / 2_000);
        }
        assertTrue(checksum != Long.MIN_VALUE);
        return best;
    }

    private static List<String> templates() {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(RenderCostTest.PACKED_TITLE),
                RenderCostTest.LINES.stream()).toList();
    }
}
