package dev.z1ppzy.guiok.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Public service for creating and identifying GuiOk custom items. */
public interface GuiOkApi {
    int API_VERSION = 1;

    int apiVersion();

    Set<String> itemIds();

    boolean exists(String id);

    Optional<GuiOkItemDefinition> definition(String id);

    ItemStack create(String id);

    ItemStack create(String id, int amount);

    Optional<String> idOf(ItemStack item);

    boolean is(ItemStack item, String id);

    Map<Integer, ItemStack> give(Player player, String id, int amount);
}
