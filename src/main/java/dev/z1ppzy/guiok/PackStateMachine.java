/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

public final class PackStateMachine {
    private PackStateMachine() {
    }

    public static PackState transition(PackState current, PackSignal signal) {
        if (current == PackState.DECLINED
                || current == PackState.FAILED
                || current == PackState.BEDROCK) {
            return current;
        }
        if (current == PackState.APPLIED && signal != PackSignal.DISCARDED) {
            return current;
        }
        return switch (signal) {
            case ACCEPTED -> PackState.ACCEPTED;
            case DOWNLOADED -> PackState.DOWNLOADED;
            case SUCCESSFULLY_LOADED -> PackState.APPLIED;
            case DECLINED -> PackState.DECLINED;
            case DISCARDED -> PackState.DISCARDED;
            case INVALID_URL, FAILED_DOWNLOAD, FAILED_RELOAD -> PackState.FAILED;
        };
    }
}
