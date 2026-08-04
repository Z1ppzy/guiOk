/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GuiOkCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PLAYER_SUBCOMMANDS =
            List.of("help", "toggle", "resend", "version", "status");
    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("help", "toggle", "resend", "version", "status", "items", "icons", "give", "reload");

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
            case "items" -> items(sender);
            case "icons", "glyphs" -> icons(sender);
            case "give" -> give(sender, args);
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
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> source = sender.hasPermission("guiok.admin")
                    ? ADMIN_SUBCOMMANDS
                    : PLAYER_SUBCOMMANDS;
            return source.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (!sender.hasPermission("guiok.admin") || !args[0].equalsIgnoreCase("give")) {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        if (args.length == 3) {
            return plugin.items().itemIds().stream()
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        if (args.length == 4) {
            return List.of("1", "16", "64").stream()
                    .filter(amount -> amount.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(plugin.message("<white>/guiok toggle</white> <gray>— показать или скрыть sidebar</gray>"));
        sender.sendMessage(plugin.message("<white>/guiok resend</white> <gray>— повторно отправить ресурспак</gray>"));
        sender.sendMessage(plugin.message("<white>/guiok version</white> <gray>— версия JAR и git-коммит</gray>"));
        sender.sendMessage(plugin.message("<white>/guiok status</white> <gray>— состояние HUD и ресурспака</gray>"));
        if (sender.hasPermission("guiok.admin")) {
            sender.sendMessage(plugin.message("<white>/guiok items</white> <gray>— список item API</gray>"));
            sender.sendMessage(plugin.message("<white>/guiok icons</white> <gray>— глифы пака и их плейсхолдеры</gray>"));
            sender.sendMessage(plugin.message("<white>/guiok give <игрок> <id> [количество]</white> <gray>— выдать кастомный предмет</gray>"));
            sender.sendMessage(plugin.message("<white>/guiok reload</white> <gray>— безопасно перечитать config.yml и items.yml</gray>"));
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
        sender.sendMessage(plugin.message("<white>Разработчик:</white> <green>" + info.author() + "</green>"));
        sender.sendMessage(plugin.message("<white>Лицензия:</white> <gray>" + info.license() + "</gray>"));
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
            sender.sendMessage(plugin.message("<white>Item API:</white> <green>v"
                    + plugin.items().apiVersion() + "</green> <gray>("
                    + plugin.items().itemIds().size() + " предметов)</gray>"));
            sender.sendMessage(plugin.message("<white>Иконки:</white> <green>"
                    + PackIcons.names().size() + "</green> <gray>(/guiok icons)</gray>"));
            sender.sendMessage(plugin.message("<white>URL:</white> <gray>"
                    + plugin.settings().resourcePack().url() + "</gray>"));
            sender.sendMessage(plugin.message("<white>Pack ID:</white> <gray>"
                    + plugin.settings().resourcePack().id() + "</gray>"));
        }
        return true;
    }

    private boolean items(CommandSender sender) {
        if (!sender.hasPermission("guiok.admin")) {
            sender.sendMessage(plugin.configuredMessage(plugin.settings().messages().noPermission()));
            return true;
        }
        List<String> ids = plugin.items().itemIds().stream().sorted().toList();
        sender.sendMessage(plugin.message("<white>GuiOk items:</white> <green>" + ids.size() + "</green>"));
        for (int index = 0; index < ids.size(); index += 10) {
            int end = Math.min(index + 10, ids.size());
            sender.sendMessage(plugin.message(
                    "<gray>" + String.join(", ", ids.subList(index, end)) + "</gray>"));
        }
        return true;
    }

    /**
     * Glyphs are a resource pack, not a catalog of items, so {@code /guiok items} never
     * showed them and an operator had no way to tell whether the pack they installed
     * carries the icons their other plugins reference.
     *
     * <p>The character is printed only for a player: the console has no resource pack,
     * and a private-use code point there is noise rather than an icon.
     */
    private boolean icons(CommandSender sender) {
        if (!sender.hasPermission("guiok.admin")) {
            sender.sendMessage(plugin.configuredMessage(plugin.settings().messages().noPermission()));
            return true;
        }
        List<String> names = PackIcons.names().stream().sorted().toList();
        sender.sendMessage(plugin.message("<white>GuiOk icons:</white> <green>" + names.size() + "</green>"));
        boolean showGlyph = sender instanceof Player player
                && plugin.resourcePacks().usesPackedTitle(player);
        for (String name : names) {
            String glyph = showGlyph ? PackIcons.glyph(name) + " " : "";
            sender.sendMessage(plugin.message("<white>" + name + "</white> <gray>—</gray> " + glyph
                    + "<dark_gray>%guiok_icon_" + name + "%</dark_gray>"));
        }
        if (sender instanceof Player && !showGlyph) {
            sender.sendMessage(plugin.message(
                    "<gray>Пак не применён к вам, поэтому символы не показаны:</gray>"
                            + " <white>/guiok resend</white>"));
        }
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guiok.admin")) {
            sender.sendMessage(plugin.configuredMessage(plugin.settings().messages().noPermission()));
            return true;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(plugin.message(
                    "<red>Использование:</red> <gray>/guiok give <игрок> <id> [количество]</gray>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.message(
                    "<red>Игрок не найден:</red> <gray>" + args[1] + "</gray>"));
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.items().exists(id)) {
            sender.sendMessage(plugin.message(
                    "<red>Неизвестный GuiOk item:</red> <gray>" + id + "</gray>"));
            return true;
        }
        int amount = 1;
        if (args.length == 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(plugin.message("<red>Количество должно быть целым числом.</red>"));
                return true;
            }
        }
        if (amount < 1 || amount > 6400) {
            sender.sendMessage(plugin.message("<red>Количество должно быть от 1 до 6400.</red>"));
            return true;
        }

        Map<Integer, ItemStack> leftovers = plugin.items().give(target, id, amount);
        int notGiven = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int given = amount - notGiven;
        sender.sendMessage(plugin.message("<green>Выдано " + given + "× " + id
                + " игроку " + target.getName() + ".</green>"));
        if (notGiven > 0) {
            sender.sendMessage(plugin.message(
                    "<yellow>Не поместилось в инвентарь: " + notGiven + ".</yellow>"));
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
