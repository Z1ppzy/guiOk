/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.items;

import dev.z1ppzy.guiok.ConfigException;
import dev.z1ppzy.guiok.api.GuiOkItemDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.io.IOException;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ItemConfigLoader {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final int MAX_LORE_LINES = 50;
    private static final int MAX_TEXT_LENGTH = 2048;

    private ItemConfigLoader() {
    }

    public static ItemCatalog load(YamlConfiguration config) throws ConfigException {
        return load(config, material -> true);
    }

    public static ItemCatalog loadForServer(YamlConfiguration config) throws ConfigException {
        return load(config, material -> !material.isAir() && material.isItem());
    }

    public static ItemCatalog loadForServer(Path configFile) throws ConfigException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile.toFile());
        } catch (IOException exception) {
            throw new ConfigException("Cannot read items.yml: " + exception.getMessage());
        } catch (InvalidConfigurationException exception) {
            throw new ConfigException("items.yml is invalid YAML: " + exception.getMessage());
        } catch (RuntimeException exception) {
            // Bukkit splits keys on '.', so a key such as "..:coin" fails while building the
            // node tree. Without this the plugin would abort onEnable with a raw stack trace
            // instead of the configuration-error path.
            throw new ConfigException("items.yml cannot be parsed: " + exception.getMessage());
        }
        return loadForServer(config);
    }

    static ItemCatalog load(
            YamlConfiguration config,
            Predicate<Material> itemMaterial) throws ConfigException {
        Map<String, ItemPackDefinition> definitions = new LinkedHashMap<>();
        for (String id : config.getKeys(false)) {
            NamespacedKey itemKey = key(id, "item id");
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) {
                throw new ConfigException("items.yml section '" + id + "' must be an object");
            }

            Material material = material(required(section, "material"), id, itemMaterial);
            Component name = component(required(section, "name"), id + ".name");
            List<Component> lore = lore(section, id);
            boolean glint = booleanValue(section, "glint", false, id);
            NamespacedKey model = key(section.getString("model", itemKey.toString()), id + ".model");
            NamespacedKey texture = key(
                    section.getString("texture", itemKey.toString()), id + ".texture");
            NamespacedKey parent = key(
                    section.getString("parent", "minecraft:item/generated"), id + ".parent");

            GuiOkItemDefinition item = new GuiOkItemDefinition(
                    itemKey.toString(), material, model, name, lore, glint);
            definitions.put(item.id(), new ItemPackDefinition(item, texture, parent));
        }
        return new ItemCatalog(definitions);
    }

    private static Material material(
            String raw,
            String id,
            Predicate<Material> itemMaterial) throws ConfigException {
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        if (material == null || !itemMaterial.test(material)) {
            throw new ConfigException(id + ".material must be a valid item material");
        }
        return material;
    }

    private static List<Component> lore(ConfigurationSection section, String id)
            throws ConfigException {
        if (section.contains("lore") && !section.isList("lore")) {
            throw new ConfigException(id + ".lore must be a list of MiniMessage strings");
        }
        List<String> lines = section.getStringList("lore");
        if (lines.size() > MAX_LORE_LINES) {
            throw new ConfigException(id + ".lore may contain at most " + MAX_LORE_LINES + " lines");
        }
        List<Component> result = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            result.add(component(lines.get(index), id + ".lore[" + index + "]"));
        }
        return List.copyOf(result);
    }

    private static Component component(String raw, String path) throws ConfigException {
        if (raw.isBlank() || raw.length() > MAX_TEXT_LENGTH) {
            throw new ConfigException(path + " must be a non-empty MiniMessage string up to "
                    + MAX_TEXT_LENGTH + " characters");
        }
        try {
            return MINI_MESSAGE.deserialize(raw)
                    .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        } catch (RuntimeException exception) {
            throw new ConfigException(path + " contains invalid MiniMessage: " + exception.getMessage());
        }
    }

    private static boolean booleanValue(
            ConfigurationSection section,
            String path,
            boolean fallback,
            String id) throws ConfigException {
        if (!section.contains(path)) {
            return fallback;
        }
        if (!section.isBoolean(path)) {
            throw new ConfigException(id + '.' + path + " must be true or false");
        }
        return section.getBoolean(path);
    }

    private static String required(ConfigurationSection section, String path)
            throws ConfigException {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new ConfigException(section.getCurrentPath() + '.' + path
                    + " must be a non-empty string");
        }
        return value;
    }

    private static NamespacedKey key(String raw, String path) throws ConfigException {
        if (!raw.equals(raw.toLowerCase(Locale.ROOT)) || raw.indexOf(':') <= 0) {
            throw new ConfigException(path + " must be a lowercase namespaced id such as prison:token");
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        // A namespace may legally contain dots, so "..:coin" survives NamespacedKey and would
        // walk the generated pack one directory up — check it exactly like the key path.
        if (key == null || unsafePath(key.getNamespace()) || unsafePath(key.getKey())) {
            throw new ConfigException(path + " is not a safe Minecraft resource location");
        }
        return key;
    }

    private static boolean unsafePath(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }
}
