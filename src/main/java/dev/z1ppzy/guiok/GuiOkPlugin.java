/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

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
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Deliberately not {@code final}, unlike the rest of the plugin: MockBukkit loads a plugin by
 * generating a proxy subclass, and that is what lets {@code GuiOkPluginTest} start the real
 * plugin — plugin.yml, bundled config, commands and services — inside the test suite. Bukkit
 * only ever instantiates this class reflectively, so nothing else relies on it being final.
 */
public class GuiOkPlugin extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BuildInfo buildInfo;
    private PluginSettings settings;
    private PlaceholderBridge placeholders;
    private BedrockPlayers bedrock;
    private GuiOkItemService items;
    private SidebarService sidebar;
    private ResourcePackService resourcePacks;
    private GuiOkPlaceholderExpansion expansion;

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
        bedrock = BedrockPlayers.discover(getLogger(), getClassLoader());
        items = new GuiOkItemService(this, itemCatalog);
        getServer().getServicesManager().register(
                GuiOkApi.class, items, this, ServicePriority.Normal);
        HudRenderer renderer = new HudRenderer();
        sidebar = new SidebarService(
                this,
                settings.sidebar(),
                renderer,
                new HudContextFactory(getServer()),
                placeholders);
        resourcePacks = new ResourcePackService(
                this, settings.resourcePack(), settings.sidebar(), renderer, sidebar, bedrock);
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

        if (placeholders.available()) {
            expansion = new GuiOkPlaceholderExpansion(this);
            if (!expansion.register()) {
                expansion = null;
                getLogger().warning("PlaceholderAPI refused the GuiOk expansion");
            }
        }

        for (Player player : getServer().getOnlinePlayers()) {
            resourcePacks.start(player);
        }
        getLogger().info("GuiOk " + buildInfo.version()
                + " by " + buildInfo.author()
                + " enabled; license=" + buildInfo.license()
                + ", commit=" + buildInfo.commit()
                + ", packSha1=" + buildInfo.resourcePackSha1()
                + ", customItems=" + items.itemIds().size()
                + ", PlaceholderAPI=" + placeholders.available()
                + ", bedrockVia=" + bedrock.source());
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
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

    /**
     * Renders a prefixed MiniMessage template. Untrusted text — command arguments, player
     * names, YAML parser output — must be passed through an unparsed resolver rather than
     * concatenated into {@code content}, or it is interpreted as markup.
     */
    public Component message(String content, TagResolver... resolvers) {
        return miniMessage.deserialize(settings.messages().prefix() + content, resolvers);
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

    public BedrockPlayers bedrockPlayers() {
        return bedrock;
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
