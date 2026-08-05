/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class SidebarServiceTest {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void installsTheSidebarWithRenderedLines() {
        PlayerMock player = server.addPlayer("Alex");
        SidebarService sidebar = service(settings(true, true));

        assertTrue(sidebar.present(player, false));

        Objective objective = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        assertNotNull(objective);
        assertEquals("GUI OK", PLAIN.serialize(objective.displayName()));
        assertEquals("Игрок: Alex", line(player, 0));
        assertEquals("Мир: world", line(player, 1));
        assertTrue(sidebar.visible(player));
    }

    @Test
    void usesThePackedTitleOnlyWhenThePackIsApplied() {
        PlayerMock player = server.addPlayer("Alex");
        SidebarService sidebar = service(settings(true, true));

        sidebar.present(player, true);
        assertEquals("PACKED", title(player));

        sidebar.present(player, false);
        assertEquals("GUI OK", title(player));
    }

    @Test
    void doesNothingWhenTheSidebarIsDisabled() {
        PlayerMock player = server.addPlayer("Alex");
        Scoreboard original = player.getScoreboard();
        SidebarService sidebar = service(settings(false, true));

        assertFalse(sidebar.present(player, false));

        assertSame(original, player.getScoreboard());
        assertFalse(sidebar.visible(player));
    }

    /** Another plugin's scoreboard is only taken over when the server owner allows it. */
    @Test
    void leavesAForeignScoreboardAloneWhenConfiguredTo() {
        PlayerMock player = server.addPlayer("Alex");
        Scoreboard foreign = server.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(foreign);
        SidebarService sidebar = service(settings(true, false));

        assertFalse(sidebar.present(player, false));

        assertSame(foreign, player.getScoreboard());
    }

    @Test
    void replacesAForeignScoreboardWhenConfiguredTo() {
        PlayerMock player = server.addPlayer("Alex");
        Scoreboard foreign = server.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(foreign);
        SidebarService sidebar = service(settings(true, true));

        assertTrue(sidebar.present(player, false));

        assertNotNull(player.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
    }

    @Test
    void restoresThePreviousScoreboardOnHide() {
        PlayerMock player = server.addPlayer("Alex");
        Scoreboard original = player.getScoreboard();
        SidebarService sidebar = service(settings(true, true));
        sidebar.present(player, false);

        sidebar.hide(player, true);

        assertSame(original, player.getScoreboard());
        assertFalse(sidebar.visible(player));
    }

    /**
     * Presenting twice must keep pointing at the scoreboard the player really came from,
     * not at the previous GuiOk board — otherwise hiding restores a GuiOk sidebar.
     */
    @Test
    void remembersTheOriginalScoreboardAcrossRepeatedPresents() {
        PlayerMock player = server.addPlayer("Alex");
        Scoreboard original = player.getScoreboard();
        SidebarService sidebar = service(settings(true, true));

        sidebar.present(player, false);
        sidebar.present(player, true);
        sidebar.present(player, false);
        sidebar.hide(player, true);

        assertSame(original, player.getScoreboard());
    }

    @Test
    void togglePersistsTheChoiceAcrossPresents() {
        PlayerMock player = server.addPlayer("Alex");
        SidebarService sidebar = service(settings(true, true));
        sidebar.present(player, false);

        assertFalse(sidebar.toggle(player, false), "first toggle hides");
        assertTrue(sidebar.isHidden(player));

        assertFalse(sidebar.present(player, false), "a hidden sidebar stays hidden");
        assertNull(player.getScoreboard().getObjective(DisplaySlot.SIDEBAR));

        assertTrue(sidebar.toggle(player, false), "second toggle shows");
        assertFalse(sidebar.isHidden(player));
        assertNotNull(player.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
    }

    @Test
    void refreshTaskRewritesLinesThatChanged() {
        PlayerMock player = server.addPlayer("Alex");
        SidebarService sidebar = service(settings(true, true));
        sidebar.present(player, false);
        assertEquals("Мир: world", line(player, 1));

        player.teleport(server.addSimpleWorld("nether").getSpawnLocation());
        server.getScheduler().performTicks(25);

        assertEquals("Мир: nether", line(player, 1));
    }

    /** When another plugin takes the scoreboard, GuiOk must drop the session, not fight it. */
    @Test
    void dropsTheSessionWhenSomethingElseTakesTheScoreboard() {
        PlayerMock player = server.addPlayer("Alex");
        SidebarService sidebar = service(settings(true, true));
        sidebar.present(player, false);

        player.setScoreboard(server.getScoreboardManager().getNewScoreboard());
        server.getScheduler().performTicks(25);

        assertFalse(sidebar.visible(player));
    }

    @Test
    void shutdownRestoresEveryPlayerAndStopsRefreshing() {
        PlayerMock first = server.addPlayer("Alex");
        PlayerMock second = server.addPlayer("Sam");
        Scoreboard firstOriginal = first.getScoreboard();
        SidebarService sidebar = service(settings(true, true));
        sidebar.present(first, false);
        sidebar.present(second, false);

        sidebar.shutdown();

        assertSame(firstOriginal, first.getScoreboard());
        assertFalse(sidebar.visible(first));
        assertFalse(sidebar.visible(second));
        server.getScheduler().performTicks(50);
    }

    @Test
    void configureAppliesNewLinesOnTheNextPresent() {
        PlayerMock player = server.addPlayer("Alex");
        SidebarService sidebar = service(settings(true, true));
        sidebar.present(player, false);

        sidebar.configure(new PluginSettings.SidebarSettings(
                true, true, true, true, 20, "<white>PACKED", "<white>GUI OK",
                List.of("<gray>Один: <player>")));
        sidebar.present(player, false);

        assertEquals("Один: Alex", line(player, 0));
    }

    private SidebarService service(PluginSettings.SidebarSettings settings) {
        Logger logger = Logger.getLogger(SidebarServiceTest.class.getName());
        EconomyBridge economy = new EconomyBridge(logger, () -> null, null);
        return new SidebarService(
                plugin,
                settings,
                new HudRenderer(),
                new HudContextFactory(server, economy),
                new PlaceholderBridge(logger, null));
    }

    private static PluginSettings.SidebarSettings settings(
            boolean enabled, boolean replaceExistingScoreboard) {
        return new PluginSettings.SidebarSettings(
                enabled,
                true,
                true,
                replaceExistingScoreboard,
                20,
                "<white>PACKED",
                "<white>GUI OK",
                List.of("<gray>Игрок: <player>", "<gray>Мир: <world>"));
    }

    private static String title(PlayerMock player) {
        return PLAIN.serialize(
                player.getScoreboard().getObjective(DisplaySlot.SIDEBAR).displayName());
    }

    private static String line(PlayerMock player, int index) {
        return PLAIN.serialize(player.getScoreboard().getTeam("guiok_" + index).prefix());
    }
}
