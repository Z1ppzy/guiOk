package dev.z1ppzy.guiok.api;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/** Immutable public description of an item registered by GuiOk. */
public record GuiOkItemDefinition(
        String id,
        Material material,
        NamespacedKey model,
        Component name,
        List<Component> lore,
        boolean glint) {
    public GuiOkItemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }
}
