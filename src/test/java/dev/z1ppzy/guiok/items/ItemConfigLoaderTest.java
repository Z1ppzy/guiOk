package dev.z1ppzy.guiok.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.z1ppzy.guiok.ConfigException;
import dev.z1ppzy.guiok.api.GuiOkItemDefinition;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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
