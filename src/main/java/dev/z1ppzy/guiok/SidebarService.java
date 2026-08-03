package dev.z1ppzy.guiok;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
        String titleTemplate = session.packedTitle()
                ? settings.packedTitle()
                : settings.fallbackTitle();
        session.objective().displayName(renderer.render(
                titleTemplate,
                context,
                identifier -> placeholders.resolve(player, identifier),
                session.packedTitle()));
        int count = Math.min(session.teams().size(), settings.lines().size());
        for (int index = 0; index < count; index++) {
            Component line = renderer.render(
                    settings.lines().get(index),
                    context,
                    identifier -> placeholders.resolve(player, identifier),
                    session.packedTitle());
            session.teams().get(index).prefix(line);
        }
    }

    private static String uniqueEntry(int index) {
        return "\u00a7" + Integer.toHexString(index);
    }

    private record Session(
            Scoreboard board,
            Scoreboard previous,
            Objective objective,
            List<Team> teams,
            boolean packedTitle) {
    }
}
