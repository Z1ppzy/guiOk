/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class ResourcePackServiceTest {
    private static final UUID PACK_ID = UUID.fromString("762e72f3-2ff5-4a8b-8b97-4dba960fd660");
    private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

    private ServerMock server;
    private PluginMock plugin;
    private SidebarService sidebar;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        Logger logger = Logger.getLogger(ResourcePackServiceTest.class.getName());
        sidebar = new SidebarService(
                plugin,
                sidebarSettings(true, true),
                new HudRenderer(),
                new HudContextFactory(server),
                new PlaceholderBridge(logger, null));
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void reportsNotSentBeforeAnythingHappens() {
        PlayerMock player = server.addPlayer("Alex");

        assertEquals(PackState.NOT_SENT, service(packSettings(true)).state(player));
    }

    @Test
    void marksThePackDisabledAndShowsTheFallbackSidebar() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(false));

        service.start(player);

        assertEquals(PackState.DISABLED, service.state(player));
        assertFalse(service.usesPackedTitle(player));
        assertTrue(sidebar.visible(player), "a disabled pack must not leave the player HUD-less");
    }

    @Test
    void hidesTheSidebarWhileWaitingForThePack() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));

        service.start(player);

        assertEquals(PackState.REQUESTED, service.state(player));
        assertFalse(sidebar.visible(player));
    }

    @Test
    void swapsToThePackedTitleWhenTheClientAppliesThePack() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);

        apply(service, player, ResourcePackStatus.SUCCESSFULLY_LOADED);

        assertEquals(PackState.APPLIED, service.state(player));
        assertTrue(service.usesPackedTitle(player));
        assertEquals("PACKED", title(player));
    }

    @Test
    void fallsBackToPlainTitleWhenTheClientDeclines() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);

        apply(service, player, ResourcePackStatus.DECLINED);

        assertEquals(PackState.DECLINED, service.state(player));
        assertEquals("GUI OK", title(player));
    }

    @Test
    void neverOffersThePackToABedrockClient() {
        PlayerMock player = addBedrockPlayer("Steve");
        ResourcePackService service = service(packSettings(true));

        startPastTheSidebar(service, player);

        assertEquals(PackState.BEDROCK, service.state(player));
        assertFalse(service.usesPackedTitle(player), "a Bedrock client cannot draw the glyph");
    }

    /**
     * {@code wait-for-pack} waits for something that can never arrive on Bedrock, so honouring
     * it there would cost the player their HUD for the session rather than for a moment. The
     * state proves the order of the two branches: were the Bedrock one second, this would be
     * REQUESTED and the player would be waiting.
     */
    @Test
    void doesNotMakeABedrockClientWaitForAPackItCannotReceive() {
        PlayerMock player = addBedrockPlayer("Steve");
        ResourcePackService service = service(packSettings(true), sidebarSettings(true, true));

        startPastTheSidebar(service, player);

        assertEquals(PackState.BEDROCK, service.state(player));
    }

    /**
     * Geyser answers a required pack with the whole success sequence on the client's behalf so
     * the server does not kick it. That invented success must not reach the sidebar.
     */
    @Test
    void ignoresASuccessGeyserInventedForABedrockClient() {
        PlayerMock player = addBedrockPlayer("Steve");
        ResourcePackService service = service(packSettings(true));
        startPastTheSidebar(service, player);

        apply(service, player, ResourcePackStatus.ACCEPTED);
        apply(service, player, ResourcePackStatus.DOWNLOADED);
        apply(service, player, ResourcePackStatus.SUCCESSFULLY_LOADED);

        assertEquals(PackState.BEDROCK, service.state(player));
        assertFalse(service.usesPackedTitle(player));
    }

    @Test
    void ignoresAStatusForSomeoneElsesPack() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);

        service.handleStatus(
                player.getUniqueId(),
                service.currentGeneration(player.getUniqueId()),
                UUID.randomUUID(),
                ResourcePackStatus.SUCCESSFULLY_LOADED);

        assertEquals(PackState.REQUESTED, service.state(player));
    }

    /**
     * A client answers whenever it likes. If the player asked for a resend in the meantime,
     * the answer to the retired request describes a pack that is no longer being offered and
     * must not decide what the sidebar shows.
     */
    @Test
    void ignoresAStatusFromASupersededRequest() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);
        int retired = service.currentGeneration(player.getUniqueId());

        service.resend(player);
        int current = service.currentGeneration(player.getUniqueId());
        assertNotEquals(retired, current, "resend must retire the request in flight");

        apply(service, player, retired, ResourcePackStatus.SUCCESSFULLY_LOADED);
        assertEquals(PackState.REQUESTED, service.state(player), "stale success must not apply");

        apply(service, player, current, ResourcePackStatus.SUCCESSFULLY_LOADED);
        assertEquals(PackState.APPLIED, service.state(player));
    }

    /** Reload clears every state, so an answer already travelling belongs to the old config. */
    @Test
    void ignoresAStatusThatSurvivedAReload() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);
        int beforeReload = service.currentGeneration(player.getUniqueId());

        service.configure(packSettings(true), sidebarSettings(true, true));

        apply(service, player, beforeReload, ResourcePackStatus.SUCCESSFULLY_LOADED);

        assertEquals(PackState.NOT_SENT, service.state(player));
        assertFalse(service.usesPackedTitle(player));
    }

    @Test
    void aDeclinedPackCannotBeTalkedIntoSuccessLater() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);

        apply(service, player, ResourcePackStatus.DECLINED);
        apply(service, player, ResourcePackStatus.SUCCESSFULLY_LOADED);

        assertEquals(PackState.DECLINED, service.state(player));
    }

    @Test
    void forgetsAPlayerThatLeaves() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);
        apply(service, player, ResourcePackStatus.SUCCESSFULLY_LOADED);
        assertEquals(PackState.APPLIED, service.state(player));

        server.getPluginManager().callEvent(new PlayerQuitEvent(
                player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(PackState.NOT_SENT, service.state(player));
        assertEquals(0, service.currentGeneration(player.getUniqueId()));
    }

    @Test
    void shutdownDropsEveryTrackedPlayer() {
        PlayerMock player = server.addPlayer("Alex");
        ResourcePackService service = service(packSettings(true));
        service.start(player);
        apply(service, player, ResourcePackStatus.SUCCESSFULLY_LOADED);

        service.shutdown();

        assertEquals(PackState.NOT_SENT, service.state(player));
    }

    private void apply(
            ResourcePackService service, PlayerMock player, ResourcePackStatus status) {
        apply(service, player, service.currentGeneration(player.getUniqueId()), status);
    }

    private void apply(
            ResourcePackService service,
            PlayerMock player,
            int generation,
            ResourcePackStatus status) {
        service.handleStatus(player.getUniqueId(), generation, PACK_ID, status);
    }

    private ResourcePackService service(PluginSettings.ResourcePackSettings packSettings) {
        return service(packSettings, sidebarSettings(true, true));
    }

    private ResourcePackService service(
            PluginSettings.ResourcePackSettings packSettings,
            PluginSettings.SidebarSettings sidebarSettings) {
        return new ResourcePackService(
                plugin,
                packSettings,
                sidebarSettings,
                new HudRenderer(),
                sidebar,
                new BedrockPlayers(
                        Logger.getLogger(ResourcePackServiceTest.class.getName()), null));
    }

    /** A player whose UUID has the shape Floodgate gives a Bedrock client. */
    private PlayerMock addBedrockPlayer(String name) {
        PlayerMock player = new PlayerMock(server, name, new UUID(0, 2535428371852800L));
        server.addPlayer(player);
        return player;
    }

    /**
     * Starts the service and stops where MockBukkit does. {@code Objective#numberFormat} is
     * unimplemented in the mock, so raising a sidebar aborts the test as skipped — which is
     * why most of this class never runs. The pack decision under test here is already made by
     * then, so the assertions after this call are about state the mock cannot take away.
     */
    private static void startPastTheSidebar(ResourcePackService service, PlayerMock player) {
        try {
            service.start(player);
        } catch (UnimplementedOperationException unsupportedByTheMock) {
            // Deliberately swallowed; SidebarServiceTest owns what the sidebar then shows.
        }
    }

    private static PluginSettings.ResourcePackSettings packSettings(boolean enabled) {
        return new PluginSettings.ResourcePackSettings(
                enabled,
                URI.create("https://example.com/pack.zip"),
                PACK_ID,
                HASH,
                false,
                false,
                true,
                // Long enough that the send task never fires during a test; these tests drive
                // the status callback directly, which is the part with the interesting logic.
                1200,
                "<green>Pack</green>");
    }

    private static PluginSettings.SidebarSettings sidebarSettings(
            boolean enabled, boolean waitForPack) {
        return new PluginSettings.SidebarSettings(
                enabled,
                waitForPack,
                true,
                true,
                20,
                "<white>PACKED",
                "<white>GUI OK",
                List.of("<gray>Игрок: <player>"));
    }

    private static String title(PlayerMock player) {
        return PlainTextComponentSerializer.plainText()
                .serialize(player.getScoreboard().getObjective(DisplaySlot.SIDEBAR).displayName());
    }
}
