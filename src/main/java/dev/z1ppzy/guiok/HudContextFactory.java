package dev.z1ppzy.guiok;

import java.util.Objects;
import java.util.OptionalDouble;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class HudContextFactory {
    private final Server server;
    private final EconomyBridge economy;

    public HudContextFactory(Server server, EconomyBridge economy) {
        this.server = Objects.requireNonNull(server, "server");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    public HudContext create(Player player) {
        OptionalDouble balance = economy.balance(player);
        Location location = player.getLocation();
        return new HudContext(
                player.getName(),
                player.displayName(),
                player.getWorld().getName(),
                Integer.toString(server.getOnlinePlayers().size()),
                Integer.toString(server.getMaxPlayers()),
                Integer.toString(player.getPing()),
                balance.isPresent() ? NumberFormatter.compact(balance.getAsDouble()) : "—",
                Integer.toString(location.getBlockX()),
                Integer.toString(location.getBlockY()),
                Integer.toString(location.getBlockZ()));
    }
}
