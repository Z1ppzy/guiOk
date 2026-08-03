/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class BuildInfoTest {
    @Test
    void readsEveryBuildIdentityField() {
        Properties properties = new Properties();
        properties.setProperty("version", "1.2.3");
        properties.setProperty("commit", "abcdef123456");
        properties.setProperty("commitDate", "2026-08-03T10:15:30Z");
        properties.setProperty("paperTarget", "26.1.2.build.74-stable");
        properties.setProperty("resourcePackSha1", "0123456789abcdef0123456789abcdef01234567");
        properties.setProperty("author", "Z1ppzy");
        properties.setProperty("license", "GuiOk Source-Available License 1.0");

        BuildInfo info = BuildInfo.from(properties);

        assertEquals("1.2.3", info.version());
        assertEquals("abcdef123456", info.commit());
        assertEquals("2026-08-03T10:15:30Z", info.commitDate());
        assertEquals("26.1.2.build.74-stable", info.paperTarget());
        assertEquals("0123456789abcdef0123456789abcdef01234567", info.resourcePackSha1());
        assertEquals("Z1ppzy", info.author());
        assertEquals("GuiOk Source-Available License 1.0", info.license());
    }

    @Test
    void missingFieldsHaveExplicitUnknownFallbacks() {
        BuildInfo info = BuildInfo.from(new Properties());

        assertEquals("unknown", info.version());
        assertEquals("unknown", info.commit());
        assertEquals("unknown", info.resourcePackSha1());
        assertEquals("unknown", info.author());
        assertEquals("unknown", info.license());
    }
}
