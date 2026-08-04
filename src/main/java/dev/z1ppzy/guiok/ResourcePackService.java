/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ResourcePackService implements Listener {
    private final JavaPlugin plugin;
    private final HudRenderer renderer;
    private final SidebarService sidebar;
    /** Read from PlaceholderAPI requests, which other plugins may issue off the main thread. */
    private final Map<UUID, PackState> states = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private PluginSettings.ResourcePackSettings packSettings;
    private PluginSettings.SidebarSettings sidebarSettings;

    public ResourcePackService(
            JavaPlugin plugin,
            PluginSettings.ResourcePackSettings packSettings,
            PluginSettings.SidebarSettings sidebarSettings,
            HudRenderer renderer,
            SidebarService sidebar) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.packSettings = Objects.requireNonNull(packSettings, "packSettings");
        this.sidebarSettings = Objects.requireNonNull(sidebarSettings, "sidebarSettings");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sidebar = Objects.requireNonNull(sidebar, "sidebar");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer(), packSettings.delayTicks());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitTask task = pending.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        states.remove(playerId);
        sidebar.hide(event.getPlayer(), false);
    }

    public void start(Player player) {
        start(player, packSettings.delayTicks());
    }

    public void resend(Player player) {
        start(player, 0);
    }

    public PackState state(Player player) {
        return states.getOrDefault(player.getUniqueId(), PackState.NOT_SENT);
    }

    public boolean usesPackedTitle(Player player) {
        return state(player).usesPackedTitle();
    }

    public void configure(
            PluginSettings.ResourcePackSettings newPackSettings,
            PluginSettings.SidebarSettings newSidebarSettings) {
        packSettings = Objects.requireNonNull(newPackSettings, "newPackSettings");
        sidebarSettings = Objects.requireNonNull(newSidebarSettings, "newSidebarSettings");
        for (BukkitTask task : pending.values()) {
            task.cancel();
        }
        pending.clear();
        states.clear();
    }

    public void shutdown() {
        for (BukkitTask task : pending.values()) {
            task.cancel();
        }
        pending.clear();
        states.clear();
    }

    private void start(Player player, long delayTicks) {
        UUID playerId = player.getUniqueId();
        BukkitTask previous = pending.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }

        if (!packSettings.enabled() || !packSettings.sendOnJoin()) {
            states.put(playerId, PackState.DISABLED);
            sidebar.present(player, false);
            return;
        }

        states.put(playerId, PackState.REQUESTED);
        if (!sidebarSettings.waitForPack()) {
            sidebar.present(player, false);
        } else {
            sidebar.hide(player, true);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin, () -> sendNow(playerId), delayTicks);
        pending.put(playerId, task);
    }

    private void sendNow(UUID playerId) {
        pending.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            states.remove(playerId);
            return;
        }

        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                    packSettings.id(), packSettings.url(), packSettings.sha1());
            ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .replace(packSettings.replaceExistingPacks())
                    .required(packSettings.required())
                    .prompt(renderer.plain(packSettings.prompt()))
                    .callback((packId, status, audience) -> dispatchStatus(playerId, packId, status))
                    .build();
            player.sendResourcePacks(request);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot send GuiOk resource pack", exception);
            states.put(playerId, PackState.FAILED);
            applyFailureFallback(player);
        }
    }

    private void dispatchStatus(UUID playerId, UUID packId, ResourcePackStatus status) {
        Runnable update = () -> handleStatus(playerId, packId, status);
        if (Bukkit.isPrimaryThread()) {
            update.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, update);
        }
    }

    private void handleStatus(UUID playerId, UUID packId, ResourcePackStatus status) {
        if (!packSettings.id().equals(packId)) {
            return;
        }
        PackState current = states.getOrDefault(playerId, PackState.NOT_SENT);
        PackState next = PackStateMachine.transition(current, PackSignal.valueOf(status.name()));
        states.put(playerId, next);

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (next == PackState.APPLIED) {
            sidebar.present(player, true);
        } else if (next.failed()) {
            applyFailureFallback(player);
        }
    }

    private void applyFailureFallback(Player player) {
        if (sidebarSettings.fallbackOnPackFailure()) {
            sidebar.present(player, false);
        } else {
            sidebar.hide(player, true);
        }
    }
}
