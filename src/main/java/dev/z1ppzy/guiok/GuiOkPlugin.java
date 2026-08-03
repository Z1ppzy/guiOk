package dev.z1ppzy.guiok;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiOkPlugin extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BuildInfo buildInfo;
    private PluginSettings settings;
    private PlaceholderBridge placeholders;
    private EconomyBridge economy;
    private SidebarService sidebar;
    private ResourcePackService resourcePacks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        buildInfo = BuildInfo.load(getClassLoader(), getLogger());
        try {
            settings = SettingsLoader.load(getConfig(), buildInfo);
        } catch (ConfigException exception) {
            getLogger().severe("Invalid config.yml: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        placeholders = PlaceholderBridge.discover(getLogger(), getClassLoader());
        economy = EconomyBridge.discover(getLogger(), getServer(), getClassLoader());
        HudRenderer renderer = new HudRenderer();
        sidebar = new SidebarService(
                this,
                settings.sidebar(),
                renderer,
                new HudContextFactory(getServer(), economy),
                placeholders);
        resourcePacks = new ResourcePackService(
                this, settings.resourcePack(), settings.sidebar(), renderer, sidebar);
        getServer().getPluginManager().registerEvents(resourcePacks, this);

        GuiOkCommand commandHandler = new GuiOkCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("guiok"), "guiok command");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        try {
            EmbeddedPackExporter.export(getDataFolder().toPath(), getClassLoader(), getLogger());
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Cannot export bundled resource pack", exception);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            resourcePacks.start(player);
        }
        getLogger().info("GuiOk " + buildInfo.version()
                + " enabled; commit=" + buildInfo.commit()
                + ", packSha1=" + buildInfo.resourcePackSha1()
                + ", PlaceholderAPI=" + placeholders.available()
                + ", Vault=" + economy.available());
    }

    @Override
    public void onDisable() {
        if (resourcePacks != null) {
            resourcePacks.shutdown();
        }
        if (sidebar != null) {
            sidebar.shutdown();
        }
    }

    public ReloadResult reloadPlugin() {
        reloadConfig();
        PluginSettings candidate;
        try {
            candidate = SettingsLoader.load(getConfig(), buildInfo);
        } catch (ConfigException exception) {
            return ReloadResult.failure(exception.getMessage());
        }

        settings = candidate;
        sidebar.configure(candidate.sidebar());
        resourcePacks.configure(candidate.resourcePack(), candidate.sidebar());
        for (Player player : getServer().getOnlinePlayers()) {
            resourcePacks.start(player);
        }
        return ReloadResult.success();
    }

    public Component message(String content) {
        return miniMessage.deserialize(settings.messages().prefix() + content);
    }

    public Component configuredMessage(String content) {
        return message(content);
    }

    public BuildInfo buildInfo() {
        return buildInfo;
    }

    public PluginSettings settings() {
        return settings;
    }

    public PlaceholderBridge placeholders() {
        return placeholders;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public SidebarService sidebar() {
        return sidebar;
    }

    public ResourcePackService resourcePacks() {
        return resourcePacks;
    }
}
