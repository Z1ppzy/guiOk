/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

/*
 * Experiment, not production code. Goes further than BundledRenderer: instead of caching only
 * templates that can never change, it works out which values each template actually reads and
 * re-parses a line only when one of those values differs from the previous refresh.
 * 
 * "<gray>Игрок:</gray> <player>" reads exactly one value, and that value never changes while
 * the player is online — so after the first refresh that line costs a map lookup, not a parse.
 * The current renderer parses it once per second forever.
 */

package dev.z1ppzy.guiok.experiment;

import dev.z1ppzy.guiok.HudContext;
import dev.z1ppzy.guiok.PackIcons;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class SessionRenderer {
    private static final Key HUD_FONT = Key.key("guiok", "hud");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** Bit per built-in tag, so a template's dependencies are one int. */
    private static final String[] TAGS = {
            "<player>", "<display_name>", "<world>", "<online>", "<max_online>",
            "<ping>", "<x>", "<y>", "<z>"};
    private static final int PAPI_BIT = 1 << TAGS.length;

    private final Map<String, Integer> dependencies = new HashMap<>();

    /** Which values a template reads; computed once per template, then remembered. */
    int dependenciesOf(String template) {
        return dependencies.computeIfAbsent(template, source -> {
            int mask = 0;
            for (int index = 0; index < TAGS.length; index++) {
                if (source.contains(TAGS[index])) {
                    mask |= 1 << index;
                }
            }
            if (source.contains("<papi:")) {
                mask |= PAPI_BIT;
            }
            return mask;
        });
    }

    public Session openSession() {
        return new Session();
    }

    private static String valueOf(HudContext hud, int index) {
        return switch (index) {
            case 0 -> hud.player();
            case 1 -> hud.displayName().toString();
            case 2 -> hud.world();
            case 3 -> hud.online();
            case 4 -> hud.maxOnline();
            case 5 -> hud.ping();
            case 6 -> hud.x();
            case 7 -> hud.y();
            case 8 -> hud.z();
            default -> throw new IllegalArgumentException("tag " + index);
        };
    }

    /** One player's sidebar; lives as long as the sidebar session does. */
    public final class Session {
        private final Map<String, Cached> lines = new HashMap<>();

        /**
         * Starts one refresh of this player's sidebar. MiniMessage consults a tag resolver
         * twice per parse, so without memoising here every {@code <papi:>} tag costs two trips
         * into PlaceholderAPI — and a second line reading the same placeholder costs two more.
         */
        public Refresh beginRefresh(
                HudContext hud,
                Function<String, Component> externalPlaceholder,
                boolean packApplied) {
            return new Refresh(hud, externalPlaceholder, packApplied);
        }

        public Component render(
                String template,
                HudContext hud,
                Function<String, Component> externalPlaceholder,
                boolean packApplied) {
            return beginRefresh(hud, externalPlaceholder, packApplied).render(template);
        }

        public final class Refresh {
            private final HudContext hud;
            private final Function<String, Component> externalPlaceholder;
            private final boolean packApplied;
            private final Map<String, Component> resolvedPlaceholders = new HashMap<>();

            private Refresh(
                    HudContext hud,
                    Function<String, Component> externalPlaceholder,
                    boolean packApplied) {
                this.hud = hud;
                this.externalPlaceholder = externalPlaceholder;
                this.packApplied = packApplied;
            }

            public Component render(String template) {
                int mask = dependenciesOf(template);
                String signature = signature(hud, mask, packApplied);
                Cached cached = lines.get(template);
                // A template reading PlaceholderAPI can change without any of our values
                // moving, so it is never served from the previous refresh.
                if (cached != null
                        && (mask & PAPI_BIT) == 0
                        && cached.signature.equals(signature)) {
                    return cached.component;
                }
                Component rendered = parse(template, hud, this::resolveOnce, packApplied);
                lines.put(template, new Cached(signature, rendered));
                return rendered;
            }

            private Component resolveOnce(String identifier) {
                return resolvedPlaceholders.computeIfAbsent(identifier, externalPlaceholder);
            }
        }

        private String signature(HudContext hud, int mask, boolean packApplied) {
            StringBuilder key = new StringBuilder(packApplied ? "1" : "0");
            for (int index = 0; index < TAGS.length; index++) {
                if ((mask & (1 << index)) != 0) {
                    key.append(' ').append(valueOf(hud, index));
                }
            }
            return key.toString();
        }
    }

    private record Cached(String signature, Component component) {
    }

    private static Component parse(
            String template,
            HudContext hud,
            Function<String, Component> externalPlaceholder,
            boolean packApplied) {
        TagResolver papi = TagResolver.resolver("papi", (arguments, context) -> {
            String identifier = arguments.popOr("Expected <papi:identifier>").value();
            if (arguments.hasNext()) {
                throw context.newException(
                        "The <papi> tag accepts exactly one identifier", arguments);
            }
            return Tag.inserting(externalPlaceholder.apply(identifier));
        });
        TagResolver icon = TagResolver.resolver("icon", (arguments, context) -> {
            String identifier = arguments.popOr("Expected <icon:name>").value();
            if (arguments.hasNext()) {
                throw context.newException("The <icon> tag accepts exactly one name", arguments);
            }
            String glyph = PackIcons.glyph(identifier);
            if (glyph == null) {
                throw context.newException("Unknown GuiOk icon: " + identifier);
            }
            if (!packApplied) {
                return Tag.inserting(Component.empty());
            }
            return Tag.inserting(Component.text(" ").append(
                    Component.text(glyph, NamedTextColor.WHITE).font(HUD_FONT)));
        });
        return MINI_MESSAGE.deserialize(
                template,
                Placeholder.unparsed("player", hud.player()),
                Placeholder.component("display_name", hud.displayName()),
                Placeholder.unparsed("world", hud.world()),
                Placeholder.unparsed("online", hud.online()),
                Placeholder.unparsed("max_online", hud.maxOnline()),
                Placeholder.unparsed("ping", hud.ping()),
                Placeholder.unparsed("x", hud.x()),
                Placeholder.unparsed("y", hud.y()),
                Placeholder.unparsed("z", hud.z()),
                papi,
                icon);
    }
}
