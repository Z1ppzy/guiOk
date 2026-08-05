/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    /** The config.yml shipped inside the JAR must survive our own validator. */
    @Test
    void bundledDefaultConfigIsValid() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream input = SettingsLoaderTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(input, "config.yml is missing from the plugin resources");
            config.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }

        PluginSettings settings = SettingsLoader.load(config, buildInfo());

        assertEquals(HASH, settings.resourcePack().sha1());
        assertFalse(settings.sidebar().lines().isEmpty());
    }

    /**
     * MiniMessage swallows the error an unknown {@code <icon:…>} raises and prints the raw tag,
     * so a typo would otherwise sit on every player's sidebar until somebody noticed.
     */
    @Test
    void rejectsSidebarLineReferencingAnUnknownIcon() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("sidebar.lines", List.of("<player><icon:coins>"));

        ConfigException exception = assertThrows(
                ConfigException.class, () -> SettingsLoader.load(config, buildInfo()));

        assertTrue(exception.getMessage().contains("sidebar.lines[0]"));
        assertTrue(exception.getMessage().contains("coins"));
        assertTrue(exception.getMessage().contains("coin"), "should list the available icons");
    }

    /**
     * The Vault-backed {@code <balance>} tag is gone. MiniMessage prints an unknown tag
     * verbatim, so an upgraded server would quietly show "&lt;balance&gt;" to every player;
     * the config has to be rejected with a pointer at the PlaceholderAPI replacement instead.
     */
    @Test
    void rejectsTheRemovedBalanceTagAndNamesTheReplacement() throws Exception {
        YamlConfiguration line = validConfig();
        line.set("sidebar.lines", List.of("<green><balance></green>"));

        ConfigException exception = assertThrows(
                ConfigException.class, () -> SettingsLoader.load(line, buildInfo()));

        assertTrue(exception.getMessage().contains("sidebar.lines[0]"));
        assertTrue(exception.getMessage().contains("<balance>"));
        assertTrue(exception.getMessage().contains("papi"), "should point at the replacement");
    }

    @Test
    void rejectsTheRemovedBalanceTagInEitherTitle() throws Exception {
        YamlConfiguration packed = validConfig();
        packed.set("sidebar.title-with-pack", "<balance>");
        YamlConfiguration fallback = validConfig();
        fallback.set("sidebar.title-without-pack", "<balance>");

        assertThrows(ConfigException.class, () -> SettingsLoader.load(packed, buildInfo()));
        assertThrows(ConfigException.class, () -> SettingsLoader.load(fallback, buildInfo()));
    }

    /** A PlaceholderAPI balance is the supported replacement and must load cleanly. */
    @Test
    void acceptsAPlaceholderApiBalanceLine() throws Exception {
        PluginSettings settings = load(
                "sidebar.lines",
                List.of("<green><papi:cmi_user_balance_formatted></green><icon:coin>"));

        assertEquals(1, settings.sidebar().lines().size());
    }

    @Test
    void rejectsUnknownIconInEitherTitle() throws Exception {
        YamlConfiguration packed = validConfig();
        packed.set("sidebar.title-with-pack", "<icon:teapot>");
        YamlConfiguration fallback = validConfig();
        fallback.set("sidebar.title-without-pack", "<icon>");

        assertThrows(ConfigException.class, () -> SettingsLoader.load(packed, buildInfo()));
        assertThrows(ConfigException.class, () -> SettingsLoader.load(fallback, buildInfo()));
    }

    @Test
    void acceptsEveryIconTheResourcePackShips() throws Exception {
        for (String name : PackIcons.names()) {
            YamlConfiguration config = validConfig();
            config.set("sidebar.lines", List.of("<icon:" + name + ">", "<icon:" + name.toUpperCase(java.util.Locale.ROOT) + ">"));

            assertEquals(2, SettingsLoader.load(config, buildInfo()).sidebar().lines().size());
        }
    }

    @Test
    void rejectsShaThatIsNeitherAutoNorADigest() throws Exception {
        for (String value : new String[] {
                "not-a-hash",
                HASH.substring(0, 39),
                HASH + "0",
                HASH.replace('0', 'g'),
                ""}) {
            YamlConfiguration config = validConfig();
            config.set("resource-pack.sha1", value);

            assertThrows(
                    ConfigException.class,
                    () -> SettingsLoader.load(config, buildInfo()),
                    "sha1 should have been rejected: " + value);
        }
    }

    @Test
    void acceptsAnExplicitDigestAndNormalisesItsCase() throws Exception {
        String digest = "abcdef0123456789abcdef0123456789abcdef01";

        assertEquals(digest, load("resource-pack.sha1", digest).resourcePack().sha1());
        assertEquals(
                digest,
                load("resource-pack.sha1", digest.toUpperCase(java.util.Locale.ROOT))
                        .resourcePack().sha1());
    }

    @Test
    void rejectsTickCountsOutsideTheSupportedRange() throws Exception {
        assertThrows(ConfigException.class, () -> load("resource-pack.delay-ticks", -1));
        assertThrows(ConfigException.class, () -> load("resource-pack.delay-ticks", 1201));
        assertThrows(ConfigException.class, () -> load("resource-pack.delay-ticks", "20"));
        assertThrows(ConfigException.class, () -> load("resource-pack.delay-ticks", 1.5));
        assertThrows(ConfigException.class, () -> load("sidebar.refresh-ticks", 9));
        assertThrows(ConfigException.class, () -> load("sidebar.refresh-ticks", 1201));
    }

    @Test
    void acceptsTickCountsOnTheBoundary() throws Exception {
        assertEquals(0, load("resource-pack.delay-ticks", 0).resourcePack().delayTicks());
        assertEquals(1200, load("resource-pack.delay-ticks", 1200).resourcePack().delayTicks());
        assertEquals(10, load("sidebar.refresh-ticks", 10).sidebar().refreshTicks());
    }

    @Test
    void rejectsAnEmptySidebar() throws Exception {
        assertThrows(ConfigException.class, () -> load("sidebar.lines", List.of()));
    }

    @Test
    void rejectsSidebarLineLongerThanTheTemplateLimit() throws Exception {
        assertThrows(ConfigException.class, () -> load("sidebar.lines", List.of("x".repeat(2049))));
        assertEquals(1, load("sidebar.lines", List.of("x".repeat(2048))).sidebar().lines().size());
    }

    @Test
    void rejectsMalformedPackIdentity() throws Exception {
        assertThrows(ConfigException.class, () -> load("resource-pack.id", "not-a-uuid"));
        assertThrows(ConfigException.class, () -> load("resource-pack.url", "not a url"));
        assertThrows(ConfigException.class, () -> load("resource-pack.url", "/relative/pack.zip"));
        assertThrows(
                ConfigException.class,
                () -> load("resource-pack.url", "https://пример.рф/pack.zip"));
    }

    /**
     * "https://" and "http://" were already rejected as malformed URIs; these three parse
     * cleanly and carry an http(s) scheme, so nothing but a host check stops them reaching
     * the client as an undownloadable pack.
     */
    @Test
    void rejectsAPackUrlThatNamesNoHost() throws Exception {
        for (String url : new String[] {"https:///pack.zip", "https:/pack.zip", "https:pack.zip"}) {
            ConfigException exception = assertThrows(
                    ConfigException.class,
                    () -> load("resource-pack.url", url),
                    "Should have rejected host-less URL " + url);
            assertTrue(
                    exception.getMessage().contains("host"),
                    "Unexpected reason for " + url + ": " + exception.getMessage());
        }
    }

    @Test
    void stillRejectsUrlsThatAreNotEvenWellFormed() throws Exception {
        for (String url : new String[] {"https://", "http://", "not a url"}) {
            assertThrows(ConfigException.class, () -> load("resource-pack.url", url));
        }
    }

    @Test
    void rejectsAbsurdlyLongPackUrls() throws Exception {
        String tooLong = "https://example.com/" + "a".repeat(33_000) + ".zip";

        assertThrows(ConfigException.class, () -> load("resource-pack.url", tooLong));
    }

    /**
     * A pre-signed S3 or CDN link is the normal way to host a private pack and easily runs
     * past the 255 characters the legacy Bukkit pack API documents. GuiOk sends packs through
     * Adventure, so that limit does not apply and must not be enforced here.
     */
    @Test
    void acceptsLongPreSignedPackUrls() throws Exception {
        String preSigned = "https://bucket.s3.eu-central-1.amazonaws.com/guiok/pack.zip"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=" + "A".repeat(64)
                + "&X-Amz-Date=20260805T000000Z&X-Amz-Expires=604800"
                + "&X-Amz-SignedHeaders=host&X-Amz-Signature=" + "0".repeat(64);
        assertTrue(preSigned.length() > 255, "test URL should exceed the legacy limit");

        PluginSettings settings = load("resource-pack.url", preSigned);

        assertEquals(preSigned, settings.resourcePack().url().toString());
    }

    @Test
    void rejectsMissingOrBlankRequiredStrings() throws Exception {
        for (String path : new String[] {
                "resource-pack.url",
                "resource-pack.id",
                "resource-pack.sha1",
                "resource-pack.prompt",
                "sidebar.title-with-pack",
                "sidebar.title-without-pack",
                "messages.prefix",
                "messages.no-permission",
                "messages.reloaded",
                "messages.hidden",
                "messages.shown",
                "messages.pack-resent"}) {
            assertThrows(
                    ConfigException.class,
                    () -> load(path, null),
                    path + " should be required");
            assertThrows(
                    ConfigException.class,
                    () -> load(path, "   "),
                    path + " should reject blank values");
        }
    }

    private static PluginSettings load(String path, Object value) throws Exception {
        YamlConfiguration config = validConfig();
        config.set(path, value);
        return SettingsLoader.load(config, buildInfo());
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
