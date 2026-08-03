/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import net.kyori.adventure.text.Component;

public record HudContext(
        String player,
        Component displayName,
        String world,
        String online,
        String maxOnline,
        String ping,
        String balance,
        String x,
        String y,
        String z) {
}
