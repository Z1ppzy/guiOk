/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

/*
 * Experiment, not production code. How many times does one render of one line actually call
 * PlaceholderAPI? Every call is a reflective hop into PAPI, which GuiOk does not cache.
 */

package dev.z1ppzy.guiok.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.z1ppzy.guiok.HudContext;
import dev.z1ppzy.guiok.HudRenderer;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class PapiCallCountTest {
    /**
     * Guards the fix. MiniMessage consults a tag resolver more than once per parse, so before
     * HudRenderer memoised its answers every {@code <papi:>} tag cost two reflective round
     * trips into PlaceholderAPI — and two lines reading the same placeholder cost four. If
     * this count ever climbs above one per placeholder again, the memoisation has regressed.
     */
    @Test
    void countsPlaceholderApiCallsPerRender() {
        HudRenderer renderer = new HudRenderer();
        HudContext hud = RenderCostTest.context();

        for (String template : List.of(
                "<papi:balance>",
                "<green><papi:balance></green>",
                "<green><papi:balance></green><icon:coin>",
                "<papi:balance> и <papi:rank>",
                "<gradient:#80ff00:#ffffff><papi:balance></gradient>")) {
            List<String> calls = new ArrayList<>();
            renderer.render(template, hud, identifier -> {
                calls.add(identifier);
                return Component.text("1.5K");
            }, true);
            System.out.printf("%-52s вызовов PAPI: %d  %s%n",
                    template, calls.size(), calls);
        }

        List<String> single = new ArrayList<>();
        renderer.render("<papi:balance>", hud, identifier -> {
            single.add(identifier);
            return Component.text("1.5K");
        }, true);
        assertEquals(1, single.size(), "один <papi:> тег — ровно один вызов PAPI");
    }
}
