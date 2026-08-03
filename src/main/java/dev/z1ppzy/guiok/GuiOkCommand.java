package dev.z1ppzy.guiok;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GuiOkCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PLAYER_SUBCOMMANDS =
            List.of("help", "toggle", "resend", "version", "status");
    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("help", "toggle", "resend", "version", "status", "reload");

    private final GuiOkPlugin plugin;

    public GuiOkCommand(GuiOkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "help" -> help(sender);
            case "toggle" -> toggle(sender);
            case "resend", "pack" -> resend(sender);
            case "version" -> version(sender);
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(plugin.message("<red>Неизвестная команда.</red> <gray>/guiok help</gray>"));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> source = sender.hasPermission("guiok.admin")
                ? ADMIN_SUBCOMMANDS
                : PLAYER_SUBCOMMANDS;
        return source.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(plugin.message("<white>/guiok toggle</white> <gray>— показать или скрыть sidebar</gray>"));
        sender.sendMessage(plugin.message("<white>/guiok resend</white> <gray>— повторно отправить ресурспак</gray>"));
        sender.sendMessage(plugin.message("<white>/guiok version</white> <gray>— версия JAR и git-коммит</gray>"));
        sender.sendMessage(plugin.message("<white>/guiok status</white> <gray>— состояние HUD и ресурспака</gray>"));
        if (sender.hasPermission("guiok.admin")) {
            sender.sendMessage(plugin.message("<white>/guiok reload</white> <gray>— безопасно перечитать config.yml</gray>"));
        }
        return true;
    }

    private boolean toggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("<red>Эта команда доступна только игроку.</red>"));
            return true;
        }
        boolean visible = plugin.sidebar().toggle(
                player, plugin.resourcePacks().usesPackedTitle(player));
        String text = visible
                ? plugin.settings().messages().shown()
                : plugin.settings().messages().hidden();
        player.sendMessage(plugin.configuredMessage(text));
        return true;
    }

    private boolean resend(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("<red>Эта команда доступна только игроку.</red>"));
            return true;
        }
        plugin.resourcePacks().resend(player);
        player.sendMessage(plugin.configuredMessage(plugin.settings().messages().packResent()));
        return true;
    }

    private boolean version(CommandSender sender) {
        BuildInfo info = plugin.buildInfo();
        Component commit = Component.text(info.commit())
                .clickEvent(ClickEvent.copyToClipboard(info.commit()));
        sender.sendMessage(plugin.message("<white>Версия:</white> <green>" + info.version() + "</green>"));
        sender.sendMessage(plugin.message("<white>Коммит:</white> ").append(commit));
        sender.sendMessage(plugin.message("<white>Дата коммита:</white> <gray>" + info.commitDate() + "</gray>"));
        sender.sendMessage(plugin.message("<white>Paper API:</white> <gray>" + info.paperTarget() + "</gray>"));
        sender.sendMessage(plugin.message("<white>SHA-1 пака:</white> <gray>"
                + info.resourcePackSha1() + "</gray>"));
        return true;
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(plugin.message("<white>PlaceholderAPI:</white> "
                + enabled(plugin.placeholders().available())));
        sender.sendMessage(plugin.message("<white>Vault economy:</white> "
                + enabled(plugin.economy().available())));
        if (sender instanceof Player player) {
            sender.sendMessage(plugin.message("<white>Ресурспак:</white> <gray>"
                    + plugin.resourcePacks().state(player).name() + "</gray>"));
            sender.sendMessage(plugin.message("<white>Sidebar:</white> "
                    + enabled(plugin.sidebar().visible(player))));
        }
        if (sender.hasPermission("guiok.admin")) {
            sender.sendMessage(plugin.message("<white>URL:</white> <gray>"
                    + plugin.settings().resourcePack().url() + "</gray>"));
            sender.sendMessage(plugin.message("<white>Pack ID:</white> <gray>"
                    + plugin.settings().resourcePack().id() + "</gray>"));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("guiok.admin")) {
            sender.sendMessage(plugin.configuredMessage(plugin.settings().messages().noPermission()));
            return true;
        }
        ReloadResult result = plugin.reloadPlugin();
        if (result.successful()) {
            sender.sendMessage(plugin.configuredMessage(plugin.settings().messages().reloaded()));
        } else {
            sender.sendMessage(plugin.message("<red>Перезагрузка отклонена:</red> <gray>"
                    + result.message() + "</gray>"));
        }
        return true;
    }

    private static String enabled(boolean value) {
        return value ? "<green>да</green>" : "<red>нет</red>";
    }
}
