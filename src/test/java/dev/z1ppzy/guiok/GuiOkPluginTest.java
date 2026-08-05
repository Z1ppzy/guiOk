/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.z1ppzy.guiok.api.GuiOkApi;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Boots the real plugin against a real server implementation. This is the only test that
 * exercises plugin.yml, the bundled config.yml and items.yml, the command registration and
 * the service registration together — the wiring that a unit test of any single class cannot
 * see, and that only shows up as a failed startup on a live server.
 */
class GuiOkPluginTest {
    private ServerMock server;
    private GuiOkPlugin plugin;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(GuiOkPlugin.class);
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void enablesWithTheConfigurationItShips() {
        assertTrue(plugin.isEnabled(), "GuiOk refused to enable with its own default config");
        assertNotNull(plugin.settings());
        assertNotNull(plugin.buildInfo());
        assertFalse(plugin.settings().sidebar().lines().isEmpty());
    }

    @Test
    void registersTheItemApiAsAService() {
        GuiOkApi api = server.getServicesManager().load(GuiOkApi.class);

        assertNotNull(api, "third-party plugins resolve the item API through the services manager");
        assertEquals(GuiOkApi.API_VERSION, api.apiVersion());
        assertTrue(api.iconIds().containsAll(PackIcons.names()));
    }

    @Test
    void loadsTheItemsItShips() {
        assertFalse(plugin.items().itemIds().isEmpty(), "bundled items.yml produced no items");
        for (String id : plugin.items().itemIds()) {
            assertTrue(plugin.items().exists(id));
            assertTrue(plugin.items().definition(id).isPresent());
        }
    }

    @Test
    void answersItsOwnCommand() {
        PlayerMock player = server.addPlayer("Alex");
        player.setOp(true);

        assertTrue(player.performCommand("guiok help"));
        assertTrue(player.performCommand("guiok version"));
        assertTrue(player.performCommand("guiok status"));
        assertTrue(player.performCommand("guiok items"));
        assertTrue(player.performCommand("guiok icons"));
        assertNotNull(player.nextMessage(), "the command produced no output at all");
    }

    @Test
    void rejectsAdminCommandsFromOrdinaryPlayers() {
        PlayerMock player = server.addPlayer("Sam");

        assertTrue(player.performCommand("guiok reload"));

        String reply = plainMessages(player);
        assertTrue(reply.contains("Недостаточно прав"), "unexpected reply: " + reply);
    }

    @Test
    void reloadsWithoutLosingTheItemCatalogue() {
        int before = plugin.items().itemIds().size();

        ReloadResult result = plugin.reloadPlugin();

        assertTrue(result.successful(), "reload failed: " + result.message());
        assertEquals(before, plugin.items().itemIds().size());
    }

    @Test
    void toggleHidesAndRestoresTheSidebarForARealPlayer() {
        PlayerMock player = server.addPlayer("Alex");
        plugin.sidebar().present(player, false);
        assertTrue(plugin.sidebar().visible(player));

        assertTrue(player.performCommand("guiok toggle"));
        assertFalse(plugin.sidebar().visible(player));

        assertTrue(player.performCommand("guiok toggle"));
        assertTrue(plugin.sidebar().visible(player));
    }

    @Test
    void tabCompletionOffersAdminSubcommandsOnlyToAdmins() {
        PlayerMock admin = server.addPlayer("Root");
        admin.setOp(true);
        PlayerMock player = server.addPlayer("Sam");

        List<String> forAdmin = admin.getServer().getCommandMap()
                .tabComplete(admin, "guiok ");
        List<String> forPlayer = player.getServer().getCommandMap()
                .tabComplete(player, "guiok ");

        assertNotNull(forAdmin);
        assertNotNull(forPlayer);
        assertTrue(forAdmin.contains("reload"));
        assertFalse(forPlayer.contains("reload"), "a plain player was offered /guiok reload");
    }

    /** A name that is not a GuiOk item must be reported, not silently accepted. */
    @Test
    void refusesToGiveAnUnknownItem() {
        PlayerMock admin = server.addPlayer("Root");
        admin.setOp(true);

        assertTrue(admin.performCommand("guiok give Root definitely:missing 1"));

        assertTrue(plainMessages(admin).contains("Неизвестный GuiOk item"));
    }

    @Test
    void refusesAnAmountOutsideTheAllowedRange() {
        PlayerMock admin = server.addPlayer("Root");
        admin.setOp(true);
        String id = plugin.items().itemIds().iterator().next();

        assertTrue(admin.performCommand("guiok give Root " + id + " 0"));
        assertTrue(admin.performCommand("guiok give Root " + id + " 999999"));
        assertTrue(admin.performCommand("guiok give Root " + id + " abc"));

        String reply = plainMessages(admin);
        assertTrue(reply.contains("Количество"), "unexpected reply: " + reply);
    }

    @Test
    void givesARealItemThatTheApiRecognisesAgain() {
        PlayerMock admin = server.addPlayer("Root");
        admin.setOp(true);
        String id = plugin.items().itemIds().iterator().next();

        assertTrue(admin.performCommand("guiok give Root " + id + " 3"));

        assertTrue(plugin.items().is(admin.getInventory().getItem(0), id));
        assertEquals(3, admin.getInventory().getItem(0).getAmount());
    }

    private static String plainMessages(PlayerMock player) {
        StringBuilder messages = new StringBuilder();
        for (String message = player.nextMessage();
                message != null;
                message = player.nextMessage()) {
            messages.append(message).append('\n');
        }
        return messages.toString();
    }
}
