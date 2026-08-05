/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class HudContextFactory {
    private final Server server;

    public HudContextFactory(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public HudContext create(Player player) {
        Location location = player.getLocation();
        return new HudContext(
                player.getName(),
                player.displayName(),
                player.getWorld().getName(),
                Integer.toString(server.getOnlinePlayers().size()),
                Integer.toString(server.getMaxPlayers()),
                Integer.toString(player.getPing()),
                Integer.toString(location.getBlockX()),
                Integer.toString(location.getBlockY()),
                Integer.toString(location.getBlockZ()));
    }
}
