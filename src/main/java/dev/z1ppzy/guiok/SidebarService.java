/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class SidebarService {
    private final JavaPlugin plugin;
    private final HudRenderer renderer;
    private final HudContextFactory contextFactory;
    private final PlaceholderBridge placeholders;
    private final NamespacedKey hiddenKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private PluginSettings.SidebarSettings settings;
    private BukkitTask refreshTask;

    public SidebarService(
            JavaPlugin plugin,
            PluginSettings.SidebarSettings settings,
            HudRenderer renderer,
            HudContextFactory contextFactory,
            PlaceholderBridge placeholders) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.hiddenKey = new NamespacedKey(plugin, "sidebar_hidden");
        restartRefreshTask();
    }

    public boolean present(Player player, boolean packedTitle) {
        if (!settings.enabled() || isHidden(player)) {
            hide(player, true);
            return false;
        }

        Session previousSession = sessions.remove(player.getUniqueId());
        Scoreboard previous = previousSession == null
                ? player.getScoreboard()
                : previousSession.previous();
        ScoreboardManager manager = Objects.requireNonNull(
                Bukkit.getScoreboardManager(), "scoreboard manager");
        if (!settings.replaceExistingScoreboard()
                && previousSession == null
                && previous != manager.getMainScoreboard()) {
            plugin.getLogger().fine(
                    "Skipping sidebar for " + player.getName() + ": another scoreboard is active");
            return false;
        }

        Scoreboard board = manager.getNewScoreboard();
        HudContext context = contextFactory.create(player);
        String titleTemplate = packedTitle ? settings.packedTitle() : settings.fallbackTitle();
        Component title = renderer.render(
                titleTemplate,
                context,
                identifier -> placeholders.resolve(player, identifier),
                packedTitle);
        Objective objective = board.registerNewObjective("guiok", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        List<Team> teams = new ArrayList<>(settings.lines().size());
        for (int index = 0; index < settings.lines().size(); index++) {
            Team team = board.registerNewTeam("guiok_" + index);
            String entry = uniqueEntry(index);
            team.addEntry(entry);
            objective.getScore(entry).setScore(settings.lines().size() - index);
            teams.add(team);
        }

        Session session = new Session(board, previous, objective, teams, packedTitle);
        sessions.put(player.getUniqueId(), session);
        player.setScoreboard(board);
        refresh(player, session);
        return true;
    }

    public boolean toggle(Player player, boolean packedTitle) {
        if (isHidden(player) || !visible(player)) {
            player.getPersistentDataContainer().remove(hiddenKey);
            return present(player, packedTitle);
        }
        player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
        hide(player, true);
        return false;
    }

    public void hide(Player player, boolean restorePrevious) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null
                && restorePrevious
                && player.getScoreboard() == session.board()) {
            player.setScoreboard(session.previous());
        }
    }

    public boolean visible(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && player.getScoreboard() == session.board();
    }

    public boolean isHidden(Player player) {
        Byte hidden = player.getPersistentDataContainer().get(hiddenKey, PersistentDataType.BYTE);
        return hidden != null && hidden == (byte) 1;
    }

    public void configure(PluginSettings.SidebarSettings newSettings) {
        settings = Objects.requireNonNull(newSettings, "newSettings");
        restartRefreshTask();
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                hide(player, true);
            } else {
                sessions.remove(playerId);
            }
        }
    }

    private void restartRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::refreshAll, settings.refreshTicks(), settings.refreshTicks());
    }

    private void refreshAll() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            Session session = sessions.get(playerId);
            if (player == null || session == null || player.getScoreboard() != session.board()) {
                sessions.remove(playerId);
                continue;
            }
            refresh(player, session);
        }
    }

    private void refresh(Player player, Session session) {
        HudContext context = contextFactory.create(player);
        HudRenderer.Refresh scope = renderer.beginRefresh(
                context,
                identifier -> placeholders.resolve(player, identifier),
                session.packedTitle());

        String titleTemplate = session.packedTitle()
                ? settings.packedTitle()
                : settings.fallbackTitle();
        apply(scope, titleTemplate, session.title(), session.objective()::displayName);

        int count = Math.min(session.teams().size(), settings.lines().size());
        for (int index = 0; index < count; index++) {
            Team team = session.teams().get(index);
            apply(scope, settings.lines().get(index), session.line(index), team::prefix);
        }
    }

    /**
     * Renders one slot and pushes it only when it changed. A slot whose template reads nothing
     * that has moved since the last refresh is skipped without rendering at all — parsing
     * MiniMessage is by far the most expensive part of a refresh, and most lines say the same
     * thing second after second.
     */
    private static void apply(
            HudRenderer.Refresh scope, String template, Slot slot, Consumer<Component> push) {
        String cacheKey = scope.cacheKey(template);
        if (slot.matches(template, cacheKey)) {
            return;
        }
        Component rendered = scope.render(template);
        if (slot.update(template, cacheKey, rendered)) {
            push.accept(rendered);
        }
    }

    private static String uniqueEntry(int index) {
        return "\u00a7" + Integer.toHexString(index);
    }

    /**
     * One rendered position of the sidebar — the title or a line — remembering what produced
     * its current contents so an unchanged slot costs nothing.
     */
    private static final class Slot {
        private String template;
        private String cacheKey;
        private Component rendered;

        /** True when this slot already shows what the template would render right now. */
        private boolean matches(String template, String cacheKey) {
            return cacheKey != null
                    && cacheKey.equals(this.cacheKey)
                    && template.equals(this.template);
        }

        /** @return true when the value on screen has to be replaced */
        private boolean update(String template, String cacheKey, Component rendered) {
            boolean changed = !rendered.equals(this.rendered);
            this.template = template;
            this.cacheKey = cacheKey;
            this.rendered = rendered;
            return changed;
        }
    }

    private static final class Session {
        private final Scoreboard board;
        private final Scoreboard previous;
        private final Objective objective;
        private final List<Team> teams;
        private final boolean packedTitle;
        private final List<Slot> lines;
        private final Slot title = new Slot();

        private Session(
                Scoreboard board,
                Scoreboard previous,
                Objective objective,
                List<Team> teams,
                boolean packedTitle) {
            this.board = board;
            this.previous = previous;
            this.objective = objective;
            this.teams = List.copyOf(teams);
            this.packedTitle = packedTitle;
            lines = new ArrayList<>(teams.size());
            for (int index = 0; index < teams.size(); index++) {
                lines.add(new Slot());
            }
        }

        private Scoreboard board() {
            return board;
        }

        private Scoreboard previous() {
            return previous;
        }

        private Objective objective() {
            return objective;
        }

        private List<Team> teams() {
            return teams;
        }

        private boolean packedTitle() {
            return packedTitle;
        }

        private Slot title() {
            return title;
        }

        private Slot line(int index) {
            return lines.get(index);
        }
    }
}
