/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

/*
 * Experiment, not production code. Checks whether the data-component API — the only route to
 * custom tooltip frames — actually works on this Paper version, before anyone plans around it.
 */

package dev.z1ppzy.guiok.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class DataComponentTest {
    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    /** What GuiOkItemService does today, expressed as data components instead of ItemMeta. */
    @Test
    void buildsAGuiOkItemThroughDataComponents() {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);

        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Монета"));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("guiok", "coin"));
        item.setData(DataComponentTypes.LORE,
                ItemLore.lore(List.of(Component.text("Валюта режима"))));
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        assertEquals(Key.key("guiok", "coin"), item.getData(DataComponentTypes.ITEM_MODEL));
        assertEquals(Component.text("Монета"), item.getData(DataComponentTypes.ITEM_NAME));
        assertNotNull(item.getData(DataComponentTypes.LORE));
        assertEquals(Boolean.TRUE, item.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE));
    }

    /**
     * The interesting one: a per-item tooltip frame, drawn from sprites the GuiOk pack would
     * ship at textures/gui/sprites/tooltip/&lt;name&gt;_background.png and _frame.png.
     */
    @Test
    void setsACustomTooltipStyleFromTheResourcePack() {
        ItemStack item = new ItemStack(Material.PAPER);
        Key style = Key.key("guiok", "legendary");

        item.setData(DataComponentTypes.TOOLTIP_STYLE, style);

        assertEquals(style, item.getData(DataComponentTypes.TOOLTIP_STYLE));
    }

    /** Hiding vanilla tooltip noise without the old ItemFlag dance. */
    @Test
    void hidesSelectedComponentsFromTheTooltip() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);

        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                .build());

        TooltipDisplay display = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        assertNotNull(display);
        assertTrue(display.hiddenComponents().contains(DataComponentTypes.ATTRIBUTE_MODIFIERS));
    }

    /** Data components must coexist with the PDC tag GuiOk stamps items with. */
    @Test
    void coexistsWithThePersistentDataContainerTag() {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("guiok", "coin"));

        item.editMeta(meta -> meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("guiok", "item_id"),
                org.bukkit.persistence.PersistentDataType.STRING,
                "guiok:coin"));

        assertEquals(Key.key("guiok", "coin"), item.getData(DataComponentTypes.ITEM_MODEL));
        assertEquals("guiok:coin", item.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("guiok", "item_id"),
                org.bukkit.persistence.PersistentDataType.STRING));
    }
}
