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
    DISABLED;

    public boolean usesPackedTitle() {
        return this == APPLIED;
    }

    public boolean failed() {
        return this == DECLINED || this == FAILED || this == DISCARDED;
    }
}
