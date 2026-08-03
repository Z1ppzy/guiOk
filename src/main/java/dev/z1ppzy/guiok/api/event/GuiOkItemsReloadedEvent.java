/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.api.event;

import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after GuiOk atomically publishes a successfully reloaded item catalog. */
public final class GuiOkItemsReloadedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<String> itemIds;

    public GuiOkItemsReloadedEvent(Set<String> itemIds) {
        this.itemIds = Set.copyOf(itemIds);
    }

    public Set<String> itemIds() {
        return itemIds;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
