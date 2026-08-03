package dev.z1ppzy.guiok.items;

import dev.z1ppzy.guiok.api.GuiOkItemDefinition;
import java.util.Objects;
import org.bukkit.NamespacedKey;

record ItemPackDefinition(
        GuiOkItemDefinition item,
        NamespacedKey texture,
        NamespacedKey parent) {
    ItemPackDefinition {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(parent, "parent");
    }
}
