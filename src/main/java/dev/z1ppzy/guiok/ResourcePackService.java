/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final BedrockPlayers bedrock;
    /** Read from PlaceholderAPI requests, which other plugins may issue off the main thread. */
    private final Map<UUID, PackState> states = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    /**
     * Counts pack requests per player. A client answers a request whenever it likes, so a
     * status for a superseded request — one replaced by {@code /guiok resend} or by a reload —
     * can still arrive and would otherwise report on a pack the player is no longer being
     * offered. The callback carries the generation it was built for and is dropped when it no
     * longer matches. Written from the main thread, read from pack callbacks.
     */
    private final Map<UUID, Integer> generations = new ConcurrentHashMap<>();
    private PluginSettings.ResourcePackSettings packSettings;
    private PluginSettings.SidebarSettings sidebarSettings;

    public ResourcePackService(
            JavaPlugin plugin,
            PluginSettings.ResourcePackSettings packSettings,
            PluginSettings.SidebarSettings sidebarSettings,
            HudRenderer renderer,
            SidebarService sidebar,
            BedrockPlayers bedrock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.packSettings = Objects.requireNonNull(packSettings, "packSettings");
        this.sidebarSettings = Objects.requireNonNull(sidebarSettings, "sidebarSettings");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sidebar = Objects.requireNonNull(sidebar, "sidebar");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
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
        generations.remove(playerId);
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
        cancelPending();
        states.clear();
        // The cleared states are about to be rebuilt by a fresh request per player; a status
        // still travelling for the previous config must not land in the middle of that.
        generations.replaceAll((playerId, generation) -> generation + 1);
    }

    public void shutdown() {
        cancelPending();
        states.clear();
        generations.clear();
    }

    private void cancelPending() {
        for (BukkitTask task : pending.values()) {
            task.cancel();
        }
        pending.clear();
    }

    private void start(Player player, long delayTicks) {
        UUID playerId = player.getUniqueId();
        BukkitTask previous = pending.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }
        int generation = supersede(playerId);

        // A Bedrock client cannot render a Java pack, and Geyser answers the request on its
        // behalf — with a lie when the pack is required — so asking is worse than useless.
        // The text HUD goes up immediately: waiting for a pack that can never arrive would
        // leave a player who did nothing wrong staring at an empty screen for the session.
        if (bedrock.isBedrock(player)) {
            states.put(playerId, PackState.BEDROCK);
            sidebar.present(player, false);
            return;
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
                plugin, () -> sendNow(playerId, generation), delayTicks);
        pending.put(playerId, task);
    }

    /** Retires every request in flight for a player and returns the new generation. */
    private int supersede(UUID playerId) {
        return generations.merge(playerId, 1, Integer::sum);
    }

    private void sendNow(UUID playerId, int generation) {
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
                    .callback((packId, status, audience) ->
                            dispatchStatus(playerId, generation, packId, status))
                    .build();
            player.sendResourcePacks(request);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot send GuiOk resource pack", exception);
            states.put(playerId, PackState.FAILED);
            applyFailureFallback(player);
        }
    }

    private void dispatchStatus(
            UUID playerId, int generation, UUID packId, ResourcePackStatus status) {
        Runnable update = () -> handleStatus(playerId, generation, packId, status);
        if (Bukkit.isPrimaryThread()) {
            update.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, update);
        }
    }

    /** The request generation a status must carry to still be relevant; for tests. */
    int currentGeneration(UUID playerId) {
        return generations.getOrDefault(playerId, 0);
    }

    void handleStatus(
            UUID playerId, int generation, UUID packId, ResourcePackStatus status) {
        if (!packSettings.id().equals(packId)) {
            return;
        }
        if (generations.getOrDefault(playerId, 0) != generation) {
            plugin.getLogger().fine(
                    "Ignoring status " + status.name() + " for a superseded pack request");
            return;
        }
        Optional<PackSignal> signal = PackSignal.from(status.name());
        if (signal.isEmpty()) {
            plugin.getLogger().fine("Ignoring unknown resource pack status: " + status.name());
            return;
        }
        PackState current = states.getOrDefault(playerId, PackState.NOT_SENT);
        PackState next = PackStateMachine.transition(current, signal.get());
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
