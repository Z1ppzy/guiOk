/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.z1ppzy.guiok.ConfigException;
import dev.z1ppzy.guiok.api.GuiOkItemDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemConfigLoaderTest {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    @Test
    void loadsNamespacedItemAndPackDefinition() throws Exception {
        ItemCatalog catalog = ItemConfigLoader.load(config("""
                guiok:coin:
                  material: GOLD_NUGGET
                  name: '<gold>Монета'
                  lore:
                    - '<gray>Валюта режима'
                  model: guiok:coin
                  texture: guiok:coin
                  parent: minecraft:item/generated
                  glint: true
                """));

        ItemPackDefinition source = catalog.packDefinitions().get("guiok:coin");
        GuiOkItemDefinition item = source.item();

        assertEquals(Material.GOLD_NUGGET, item.material());
        assertEquals("guiok:coin", item.model().toString());
        assertEquals("Монета", PLAIN.serialize(item.name()));
        assertEquals("Валюта режима", PLAIN.serialize(item.lore().getFirst()));
        assertTrue(item.glint());
        assertEquals("guiok:coin", source.texture().toString());
        assertEquals("minecraft:item/generated", source.parent().toString());
    }

    @Test
    void defaultsModelTextureAndParentFromItemId() throws Exception {
        ItemCatalog catalog = ItemConfigLoader.load(config("""
                prison:token:
                  material: PAPER
                  name: '<yellow>Жетон'
                """));

        ItemPackDefinition source = catalog.packDefinitions().get("prison:token");

        assertEquals("prison:token", source.item().model().toString());
        assertEquals("prison:token", source.texture().toString());
        assertEquals("minecraft:item/generated", source.parent().toString());
    }

    @Test
    void rejectsIdsThatCannotBecomeSafeResourceLocations() throws Exception {
        YamlConfiguration uppercase = config("""
                GuiOk:coin:
                  material: PAPER
                  name: Coin
                """);
        YamlConfiguration traversal = config("""
                guiok:coin:
                  material: PAPER
                  name: Coin
                  texture: guiok:../secret
                """);

        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(uppercase));
        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(traversal));
    }

    @Test
    void rejectsEmptySegmentsInTheKeyPath() throws Exception {
        YamlConfiguration config = config("""
                guiok:a//b:
                  material: PAPER
                  name: Coin
                """);

        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(config));
    }

    /**
     * A NamespacedKey namespace may legally contain dots, so "..:coin" survives
     * {@code NamespacedKey.fromString}. Reached through a model or texture value it used to
     * walk the generated pack one directory up, writing outside the assets tree.
     */
    @Test
    void rejectsExplicitModelOrTextureWithAnUnsafeNamespace() throws Exception {
        for (String field : new String[] {"model", "texture", "parent"}) {
            YamlConfiguration config = config("""
                    guiok:coin:
                      material: PAPER
                      name: Coin
                      %s: '..:coin'
                    """.formatted(field));

            ConfigException exception = assertThrows(
                    ConfigException.class,
                    () -> ItemConfigLoader.load(config),
                    "Should have rejected an unsafe namespace in " + field);
            assertTrue(exception.getMessage().contains("safe Minecraft resource location"));
        }
    }

    /**
     * Bukkit splits configuration keys on '.', so an id like "..:coin" blows up while the node
     * tree is built. That has to surface as a configuration error, not as a raw runtime
     * exception out of onEnable.
     */
    @Test
    void reportsUnparseableItemFilesAsConfigurationErrors(@TempDir Path temporary)
            throws Exception {
        Path items = temporary.resolve("items.yml");
        Files.writeString(items, """
                ..:coin:
                  material: PAPER
                  name: Coin
                """);

        assertThrows(ConfigException.class, () -> ItemConfigLoader.loadForServer(items));
    }

    @Test
    void reportsMissingItemFileAsAConfigurationError(@TempDir Path temporary) {
        assertThrows(
                ConfigException.class,
                () -> ItemConfigLoader.loadForServer(temporary.resolve("absent.yml")));
    }

    @Test
    void rejectsIdsWithoutANamespace() throws Exception {
        YamlConfiguration bare = config("""
                coin:
                  material: PAPER
                  name: Coin
                """);
        YamlConfiguration leadingColon = config("""
                ':coin':
                  material: PAPER
                  name: Coin
                """);

        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(bare));
        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(leadingColon));
    }

    @Test
    void rejectsSectionsThatAreNotObjects() throws Exception {
        YamlConfiguration config = config("guiok:coin: 5\n");

        ConfigException exception = assertThrows(
                ConfigException.class, () -> ItemConfigLoader.load(config));

        assertTrue(exception.getMessage().contains("must be an object"));
    }

    @Test
    void rejectsMissingOrUnusableText() throws Exception {
        YamlConfiguration noMaterial = config("""
                guiok:coin:
                  name: Coin
                """);
        YamlConfiguration noName = config("""
                guiok:coin:
                  material: PAPER
                """);
        YamlConfiguration blankName = config("""
                guiok:coin:
                  material: PAPER
                  name: '   '
                """);
        YamlConfiguration unknownMaterial = config("""
                guiok:coin:
                  material: NOT_A_REAL_MATERIAL
                  name: Coin
                """);

        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(noMaterial));
        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(noName));
        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(blankName));
        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(unknownMaterial));
    }

    @Test
    void rejectsLoreThatIsNotAListOrIsTooLong() throws Exception {
        YamlConfiguration scalarLore = config("""
                guiok:coin:
                  material: PAPER
                  name: Coin
                  lore: 'one line'
                """);
        StringBuilder longLore = new StringBuilder("""
                guiok:coin:
                  material: PAPER
                  name: Coin
                  lore:
                """);
        for (int index = 0; index < 51; index++) {
            longLore.append("    - 'line ").append(index).append("'\n");
        }

        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(scalarLore));
        assertThrows(ConfigException.class, () -> ItemConfigLoader.load(config(longLore.toString())));
    }

    @Test
    void rejectsGlintThatIsNotABoolean() throws Exception {
        YamlConfiguration config = config("""
                guiok:coin:
                  material: PAPER
                  name: Coin
                  glint: 'yes please'
                """);

        ConfigException exception = assertThrows(
                ConfigException.class, () -> ItemConfigLoader.load(config));

        assertTrue(exception.getMessage().contains("glint"));
    }

    /** Vanilla italicises custom names and lore; GuiOk must cancel that for every line. */
    @Test
    void stripsTheVanillaItalicDecorationFromNameAndLore() throws Exception {
        ItemCatalog catalog = ItemConfigLoader.load(config("""
                guiok:coin:
                  material: PAPER
                  name: '<gold>Монета'
                  lore:
                    - '<gray>Первая'
                    - '<gray>Вторая'
                """));
        GuiOkItemDefinition item = catalog.packDefinitions().get("guiok:coin").item();

        assertEquals(TextDecoration.State.FALSE, item.name().decoration(TextDecoration.ITALIC));
        for (Component line : item.lore()) {
            assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
        }
    }

    @Test
    void loadsAnEmptyCatalogFromAnEmptyFile() throws Exception {
        ItemCatalog catalog = ItemConfigLoader.load(config(""));

        assertEquals(0, catalog.size());
        assertTrue(catalog.packDefinitions().isEmpty());
    }

    @Test
    void ordersIdsSoGeneratedPacksAreReproducible() throws Exception {
        ItemCatalog catalog = ItemConfigLoader.load(config("""
                guiok:zebra:
                  material: PAPER
                  name: Z
                guiok:alpha:
                  material: PAPER
                  name: A
                prison:token:
                  material: PAPER
                  name: T
                """));

        assertEquals(
                List.of("guiok:alpha", "guiok:zebra", "prison:token"),
                List.copyOf(catalog.packDefinitions().keySet()));
    }

    @Test
    void rejectsNonItemMaterials() throws Exception {
        YamlConfiguration config = config("""
                guiok:water:
                  material: WATER
                  name: Water
                """);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> ItemConfigLoader.load(config, material -> material != Material.WATER));

        assertTrue(exception.getMessage().contains("material"));
    }

    private static YamlConfiguration config(String source) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(source);
        return config;
    }
}
