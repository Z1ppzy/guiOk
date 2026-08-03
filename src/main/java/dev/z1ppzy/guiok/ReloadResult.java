/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

public record ReloadResult(boolean successful, String message) {
    public static ReloadResult success() {
        return new ReloadResult(true, "ok");
    }

    public static ReloadResult failure(String message) {
        return new ReloadResult(false, message);
    }
}
