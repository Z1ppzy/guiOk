/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

public enum PackState {
    NOT_SENT,
    REQUESTED,
    ACCEPTED,
    DOWNLOADED,
    APPLIED,
    DECLINED,
    FAILED,
    DISCARDED,
    DISABLED,
    /**
     * A Bedrock client, which is never sent the pack at all: it could not render a Java pack
     * even if it accepted one. Not a failure — the player has the text HUD, which is the best
     * their client can show — so nothing about it is worth reporting as broken.
     */
    BEDROCK;

    public boolean usesPackedTitle() {
        return this == APPLIED;
    }

    public boolean failed() {
        return this == DECLINED || this == FAILED || this == DISCARDED;
    }
}
