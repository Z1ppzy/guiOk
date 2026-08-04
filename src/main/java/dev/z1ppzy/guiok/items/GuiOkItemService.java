/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.items;

import dev.z1ppzy.guiok.PackIcons;
import dev.z1ppzy.guiok.api.GuiOkApi;
import dev.z1ppzy.guiok.api.GuiOkItemDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class GuiOkItemService implements GuiOkApi {
    private final NamespacedKey itemIdKey;
    private volatile ItemCatalog catalog;

    public GuiOkItemService(Plugin plugin, ItemCatalog catalog) {
        itemIdKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "item_id");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public Set<String> itemIds() {
        return catalog.ids();
    }

    @Override
    public Set<String> iconIds() {
        return PackIcons.names();
    }

    @Override
    public Optional<String> glyph(String iconId) {
        return iconId == null ? Optional.empty() : Optional.ofNullable(PackIcons.glyph(iconId));
    }

    @Override
    public boolean exists(String id) {
        return id != null && catalog.definition(id).isPresent();
    }

    @Override
    public Optional<GuiOkItemDefinition> definition(String id) {
        return id == null ? Optional.empty() : catalog.definition(id);
    }

    @Override
    public ItemStack create(String id) {
        return create(id, 1);
    }

    @Override
    public ItemStack create(String id, int amount) {
        requirePrimaryThread();
        GuiOkItemDefinition definition = catalog.definition(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown GuiOk item: " + id));
        int maximum = definition.material().getMaxStackSize();
        if (amount < 1 || amount > maximum) {
            throw new IllegalArgumentException("Amount for " + id + " must be between 1 and " + maximum);
        }

        ItemStack item = new ItemStack(definition.material(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(definition.name());
        if (!definition.lore().isEmpty()) {
            meta.lore(definition.lore());
        }
        meta.setItemModel(definition.model());
        if (definition.glint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Optional<String> idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(itemIdKey, PersistentDataType.STRING);
        return Optional.ofNullable(id);
    }

    @Override
    public boolean is(ItemStack item, String id) {
        return id != null && idOf(item).filter(id::equals).isPresent();
    }

    @Override
    public Map<Integer, ItemStack> give(Player player, String id, int amount) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be positive");
        }
        GuiOkItemDefinition definition = catalog.definition(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown GuiOk item: " + id));
        int maximum = definition.material().getMaxStackSize();
        List<ItemStack> stacks = new ArrayList<>((amount + maximum - 1) / maximum);
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maximum);
            stacks.add(create(id, stackAmount));
            remaining -= stackAmount;
        }
        return Map.copyOf(player.getInventory().addItem(stacks.toArray(ItemStack[]::new)));
    }

    public void replace(ItemCatalog newCatalog) {
        catalog = Objects.requireNonNull(newCatalog, "newCatalog");
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("GuiOk item API must be used on the server thread");
        }
    }
}
