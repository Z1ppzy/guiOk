/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

/*
 * Experiment, not production code. Measures the real cost of one sidebar refresh so the
 * optimisation discussion is about numbers instead of intuition.
 */

package dev.z1ppzy.guiok.experiment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.z1ppzy.guiok.HudContext;
import dev.z1ppzy.guiok.HudRenderer;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class RenderCostTest {
    /** The shipped config: two titles plus five lines, three of which never change. */
    static final String PACKED_TITLE = "<font:guiok:hud><white></white></font>";
    static final String PLAIN_TITLE = "<gradient:#80ff00:#ffffff><bold>PRISON</bold></gradient>";
    static final List<String> LINES = List.of(
            "",
            "<gray>Игрок:</gray> <white><player></white>",
            "<gray>Мир:</gray> <white><world></white>",
            "<green><papi:cmi_user_balance_formatted></green><icon:coin>",
            "<gray>Онлайн:</gray> <white><online>/<max_online></white>");

    static HudContext context() {
        return new HudContext(
                "Z1ppzy", Component.text("Z1ppzy"), "prison",
                "87", "200", "31", "128", "64", "-512");
    }

    @Test
    void measuresOneRefreshOfTheShippedSidebar() {
        HudRenderer renderer = new HudRenderer();
        HudContext hud = context();
        long checksum = 0;

        // Warm up the JIT and MiniMessage's internal caches.
        for (int round = 0; round < 20_000; round++) {
            checksum += refresh(renderer, hud);
        }

        long best = Long.MAX_VALUE;
        for (int round = 0; round < 7; round++) {
            long start = System.nanoTime();
            for (int iteration = 0; iteration < 2_000; iteration++) {
                checksum += refresh(renderer, hud);
            }
            best = Math.min(best, (System.nanoTime() - start) / 2_000);
        }

        System.out.printf(
                "BASELINE один рефреш (заголовок + 5 строк): %d нс%n"
                        + "  100 игроков раз в секунду: %.2f мс/с — %.2f%% одного тика (50 мс)%n"
                        + "  500 игроков раз в секунду: %.2f мс/с — %.2f%% одного тика%n"
                        + "  (checksum %d)%n",
                best,
                best * 100 / 1e6, best * 100 / 1e6 / 50 * 100,
                best * 500 / 1e6, best * 500 / 1e6 / 50 * 100,
                checksum);

        assertTrue(best > 0);
    }

    private static long refresh(HudRenderer renderer, HudContext hud) {
        long sum = renderer.render(PACKED_TITLE, hud, RenderCostTest::papi, true).hashCode();
        for (String line : LINES) {
            sum += renderer.render(line, hud, RenderCostTest::papi, true).hashCode();
        }
        return sum;
    }

    /**
     * The path SidebarService actually takes now: one refresh scope for the whole sidebar, and
     * a per-slot cache that skips rendering a line whose values have not moved. Mirrors
     * {@code SidebarService#apply} closely enough to be worth trusting as a number.
     */
    @Test
    void measuresTheProductionRefreshPath() {
        HudRenderer renderer = new HudRenderer();
        HudContext standing = context();
        String[] templates = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(PACKED_TITLE), LINES.stream()).toArray(String[]::new);
        String[] cachedKeys = new String[templates.length];
        long checksum = 0;

        for (int round = 0; round < 20_000; round++) {
            checksum += productionRefresh(renderer, standing, templates, cachedKeys);
        }
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 7; round++) {
            long start = System.nanoTime();
            for (int iteration = 0; iteration < 2_000; iteration++) {
                checksum += productionRefresh(renderer, standing, templates, cachedKeys);
            }
            best = Math.min(best, (System.nanoTime() - start) / 2_000);
        }

        System.out.printf(
                "ПРОДАКШЕН путь (beginRefresh + кэш слотов): %d нс/рефреш%n"
                        + "  500 игроков в одном тике: %.1f мс из 50 (%.1f%%)  [checksum %d]%n",
                best, best * 500 / 1e6, best * 500 / 1e6 / 50 * 100, checksum);

        assertTrue(best > 0);
    }

    private static long productionRefresh(
            HudRenderer renderer, HudContext hud, String[] templates, String[] cachedKeys) {
        HudRenderer.Refresh scope = renderer.beginRefresh(hud, RenderCostTest::papi, true);
        long sum = 0;
        for (int slot = 0; slot < templates.length; slot++) {
            String key = scope.cacheKey(templates[slot]);
            if (key != null && key.equals(cachedKeys[slot])) {
                continue;
            }
            cachedKeys[slot] = key;
            sum += scope.render(templates[slot]).hashCode();
        }
        return sum;
    }

    static Component papi(String identifier) {
        return Component.text("1.5K");
    }
}
