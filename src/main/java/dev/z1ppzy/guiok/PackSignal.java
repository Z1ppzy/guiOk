/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.Optional;

public enum PackSignal {
    ACCEPTED,
    DECLINED,
    INVALID_URL,
    FAILED_DOWNLOAD,
    DOWNLOADED,
    FAILED_RELOAD,
    DISCARDED,
    SUCCESSFULLY_LOADED;

    /**
     * Maps an Adventure {@code ResourcePackStatus} name onto a signal. Returns empty for a
     * status a newer Adventure release may add, so an unknown status is ignored instead of
     * aborting the pack callback with an {@code IllegalArgumentException}.
     */
    public static Optional<PackSignal> from(String statusName) {
        for (PackSignal signal : values()) {
            if (signal.name().equals(statusName)) {
                return Optional.of(signal);
            }
        }
        return Optional.empty();
    }
}
