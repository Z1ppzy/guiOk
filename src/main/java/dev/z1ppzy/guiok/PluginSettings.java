/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PluginSettings(
        ResourcePackSettings resourcePack,
        SidebarSettings sidebar,
        MessageSettings messages) {
    public PluginSettings {
        Objects.requireNonNull(resourcePack, "resourcePack");
        Objects.requireNonNull(sidebar, "sidebar");
        Objects.requireNonNull(messages, "messages");
    }

    public record ResourcePackSettings(
            boolean enabled,
            URI url,
            UUID id,
            String sha1,
            boolean required,
            boolean replaceExistingPacks,
            boolean sendOnJoin,
            long delayTicks,
            String prompt) {
        public ResourcePackSettings {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sha1, "sha1");
            Objects.requireNonNull(prompt, "prompt");
        }
    }

    public record SidebarSettings(
            boolean enabled,
            boolean waitForPack,
            boolean fallbackOnPackFailure,
            boolean replaceExistingScoreboard,
            long refreshTicks,
            String packedTitle,
            String fallbackTitle,
            List<String> lines) {
        public SidebarSettings {
            Objects.requireNonNull(packedTitle, "packedTitle");
            Objects.requireNonNull(fallbackTitle, "fallbackTitle");
            lines = List.copyOf(lines);
        }
    }

    public record MessageSettings(
            String prefix,
            String noPermission,
            String reloaded,
            String hidden,
            String shown,
            String packResent) {
        public MessageSettings {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(noPermission, "noPermission");
            Objects.requireNonNull(reloaded, "reloaded");
            Objects.requireNonNull(hidden, "hidden");
            Objects.requireNonNull(shown, "shown");
            Objects.requireNonNull(packResent, "packResent");
        }
    }
}
