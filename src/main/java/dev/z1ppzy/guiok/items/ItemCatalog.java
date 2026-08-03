package dev.z1ppzy.guiok.items;

import dev.z1ppzy.guiok.api.GuiOkItemDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class ItemCatalog {
    private final Map<String, ItemPackDefinition> packDefinitions;
    private final Map<String, GuiOkItemDefinition> items;

    ItemCatalog(Map<String, ItemPackDefinition> definitions) {
        TreeMap<String, ItemPackDefinition> sorted = new TreeMap<>(definitions);
        packDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        LinkedHashMap<String, GuiOkItemDefinition> apiItems = new LinkedHashMap<>();
        packDefinitions.forEach((id, source) -> apiItems.put(id, source.item()));
        items = Collections.unmodifiableMap(apiItems);
    }

    Map<String, ItemPackDefinition> packDefinitions() {
        return packDefinitions;
    }

    Optional<GuiOkItemDefinition> definition(String id) {
        return Optional.ofNullable(items.get(id));
    }

    Set<String> ids() {
        return items.keySet();
    }

    int size() {
        return items.size();
    }
}
