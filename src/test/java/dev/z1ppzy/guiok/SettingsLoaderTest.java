/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsLoaderTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void autoHashUsesTheHashBakedIntoTheJar() throws Exception {
        PluginSettings settings = SettingsLoader.load(validConfig(), buildInfo());

        assertEquals(HASH, settings.resourcePack().sha1());
        assertEquals(2, settings.sidebar().lines().size());
        assertTrue(settings.resourcePack().url().isAbsolute());
    }

    @Test
    void rejectsMoreThanVanillaSidebarCanRender() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("sidebar.lines", java.util.stream.IntStream.range(0, 16)
                .mapToObj(Integer::toString)
                .toList());

        ConfigException exception = assertThrows(
                ConfigException.class, () -> SettingsLoader.load(config, buildInfo()));

        assertTrue(exception.getMessage().contains("between 1 and 15"));
    }

    @Test
    void rejectsNonHttpPackUrl() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("resource-pack.url", "file:///server/pack.zip");

        assertThrows(ConfigException.class, () -> SettingsLoader.load(config, buildInfo()));
    }

    private static YamlConfiguration validConfig() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                resource-pack:
                  enabled: true
                  url: https://example.com/pack.zip
                  id: 762e72f3-2ff5-4a8b-8b97-4dba960fd660
                  sha1: auto
                  required: false
                  replace-existing-packs: false
                  send-on-join: true
                  delay-ticks: 20
                  prompt: '<green>Pack</green>'
                sidebar:
                  enabled: true
                  wait-for-pack: true
                  fallback-on-pack-failure: true
                  replace-existing-scoreboard: true
                  refresh-ticks: 20
                  title-with-pack: '<font:guiok:hud></font>'
                  title-without-pack: 'GUI OK'
                  lines:
                    - ''
                    - '<player>'
                messages:
                  prefix: 'GuiOk '
                  no-permission: 'no'
                  reloaded: 'reload'
                  hidden: 'hidden'
                  shown: 'shown'
                  pack-resent: 'resent'
                """);
        return config;
    }

    private static BuildInfo buildInfo() {
        return new BuildInfo(
                "1.0.0",
                "abc",
                "today",
                "26.1.2",
                HASH,
                "Z1ppzy",
                "GuiOk Source-Available License 1.0");
    }
}
