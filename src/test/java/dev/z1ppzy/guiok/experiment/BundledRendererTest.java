/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

/*
 * Experiment, not production code. Two jobs: prove the prototype renders byte-for-byte what
 * the current renderer renders, then measure what the change actually buys.
 */

package dev.z1ppzy.guiok.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.z1ppzy.guiok.HudContext;
import dev.z1ppzy.guiok.HudRenderer;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class BundledRendererTest {
    /** Nothing is worth optimising if it changes a single pixel of what players see. */
    @Test
    void rendersExactlyWhatTheCurrentRendererRenders() {
        HudRenderer current = new HudRenderer();
        BundledRenderer prototype = new BundledRenderer();
        HudContext hud = RenderCostTest.context();

        for (boolean packApplied : new boolean[] {true, false}) {
            BundledRenderer.Bundle bundle =
                    prototype.open(hud, RenderCostTest::papi, packApplied);
            for (String template : templates()) {
                assertEquals(
                        current.render(template, hud, RenderCostTest::papi, packApplied),
                        bundle.render(template),
                        "Prototype diverged on: " + template);
            }
        }
    }

    /** A cached line must not survive a change in the value it interpolates. */
    @Test
    void neverCachesATemplateThatCanChange() {
        BundledRenderer prototype = new BundledRenderer();
        HudContext first = new HudContext(
                "Alex", Component.text("Alex"), "prison", "1", "200", "10", "0", "64", "0");
        HudContext second = new HudContext(
                "Sam", Component.text("Sam"), "nether", "2", "200", "20", "9", "64", "9");

        String line = "<gray>Игрок:</gray> <white><player></white> <world> <online> <x>";
        Component one = prototype.open(first, RenderCostTest::papi, true).render(line);
        Component two = prototype.open(second, RenderCostTest::papi, true).render(line);

        assertEquals("Игрок: Alex prison 1 0", plain(one));
        assertEquals("Игрок: Sam nether 2 9", plain(two));
        assertEquals(0, prototype.cachedTemplateCount(), "a dynamic line must not be cached");
    }

    @Test
    void doesNotCacheAcrossPackStateForIconLines() {
        BundledRenderer prototype = new BundledRenderer();
        HudContext hud = RenderCostTest.context();
        String line = "<icon:coin>";

        Component applied = prototype.open(hud, RenderCostTest::papi, true).render(line);
        Component missing = prototype.open(hud, RenderCostTest::papi, false).render(line);

        assertFalse(applied.equals(missing), "icon must still disappear without the pack");
        assertEquals(0, prototype.cachedTemplateCount());
    }

    @Test
    void recognisesWhichShippedTemplatesAreStatic() {
        assertTrue(BundledRenderer.isStatic(RenderCostTest.PACKED_TITLE));
        assertTrue(BundledRenderer.isStatic(RenderCostTest.PLAIN_TITLE));
        assertTrue(BundledRenderer.isStatic(""));
        assertFalse(BundledRenderer.isStatic("<gray>Игрок:</gray> <player>"));
        assertFalse(BundledRenderer.isStatic("<papi:cmi_user_balance_formatted>"));
        assertFalse(BundledRenderer.isStatic("<icon:coin>"));
    }

    @Test
    void measuresTheDifference() {
        HudRenderer current = new HudRenderer();
        BundledRenderer prototype = new BundledRenderer();
        HudContext hud = RenderCostTest.context();

        long before = measure(() -> {
            long sum = 0;
            for (String template : templates()) {
                sum += current.render(template, hud, RenderCostTest::papi, true).hashCode();
            }
            return sum;
        });
        BundledRenderer hoistOnly = new BundledRenderer();
        long hoisted = measure(() -> {
            BundledRenderer.Bundle bundle = hoistOnly.open(hud, RenderCostTest::papi, true);
            long sum = 0;
            for (String template : templates()) {
                sum += bundle.renderWithoutCache(template).hashCode();
            }
            return sum;
        });
        long after = measure(() -> {
            BundledRenderer.Bundle bundle = prototype.open(hud, RenderCostTest::papi, true);
            long sum = 0;
            for (String template : templates()) {
                sum += bundle.render(template).hashCode();
            }
            return sum;
        });

        System.out.printf(
                "СЕЙЧАС                    %6d нс/рефреш  (500 игроков: %5.1f мс в тике)%n"
                        + "+ резолверы на рефреш     %6d нс/рефреш  (%5.1f мс)  выигрыш %.1f%%%n"
                        + "+ кэш статичных строк     %6d нс/рефреш  (%5.1f мс)  выигрыш %.1f%%%n"
                        + "закэшировано %d статичных шаблонов из %d%n",
                before, before * 500 / 1e6,
                hoisted, hoisted * 500 / 1e6, 100.0 * (before - hoisted) / before,
                after, after * 500 / 1e6, 100.0 * (before - after) / before,
                prototype.cachedTemplateCount(), templates().size());

        assertTrue(after > 0);
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

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }
}
