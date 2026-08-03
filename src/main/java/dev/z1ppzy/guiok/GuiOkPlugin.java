package dev.z1ppzy.guiok;

import dev.z1ppzy.guiok.api.GuiOkApi;
import dev.z1ppzy.guiok.api.event.GuiOkItemsReloadedEvent;
import dev.z1ppzy.guiok.items.GuiOkItemService;
import dev.z1ppzy.guiok.items.ItemCatalog;
import dev.z1ppzy.guiok.items.ItemConfigLoader;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiOkPlugin extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BuildInfo buildInfo;
    private PluginSettings settings;
    private PlaceholderBridge placeholders;
    private EconomyBridge economy;
    private GuiOkItemService items;
    private SidebarService sidebar;
    private ResourcePackService resourcePacks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("items.yml", false);
        buildInfo = BuildInfo.load(getClassLoader(), getLogger());
        ItemCatalog itemCatalog;
        try {
            settings = SettingsLoader.load(getConfig(), buildInfo);
            itemCatalog = ItemConfigLoader.loadForServer(
                    getDataFolder().toPath().resolve("items.yml"));
        } catch (ConfigException exception) {
            getLogger().severe("Invalid GuiOk configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        placeholders = PlaceholderBridge.discover(getLogger(), getClassLoader());
        economy = EconomyBridge.discover(getLogger(), getServer(), getClassLoader());
        items = new GuiOkItemService(this, itemCatalog);
        getServer().getServicesManager().register(
                GuiOkApi.class, items, this, ServicePriority.Normal);
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
                + ", customItems=" + items.itemIds().size()
                + ", PlaceholderAPI=" + placeholders.available()
                + ", Vault=" + economy.available());
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
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
        ItemCatalog candidateItems;
        try {
            candidate = SettingsLoader.load(getConfig(), buildInfo);
            candidateItems = ItemConfigLoader.loadForServer(
                    getDataFolder().toPath().resolve("items.yml"));
        } catch (ConfigException exception) {
            return ReloadResult.failure(exception.getMessage());
        }

        settings = candidate;
        items.replace(candidateItems);
        sidebar.configure(candidate.sidebar());
        resourcePacks.configure(candidate.resourcePack(), candidate.sidebar());
        for (Player player : getServer().getOnlinePlayers()) {
            resourcePacks.start(player);
        }
        getServer().getPluginManager().callEvent(
                new GuiOkItemsReloadedEvent(items.itemIds()));
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

    public GuiOkItemService items() {
        return items;
    }

    public SidebarService sidebar() {
        return sidebar;
    }

    public ResourcePackService resourcePacks() {
        return resourcePacks;
    }
}
