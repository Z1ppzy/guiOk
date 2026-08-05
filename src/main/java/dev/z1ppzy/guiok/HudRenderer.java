/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class HudRenderer {
    private static final Key HUD_FONT = Key.key("guiok", "hud");

    /**
     * The built-in tags, in the order their bits are packed into a template's dependency mask.
     * A template that names none of them, and no {@code <papi:>}, renders the same thing on
     * every refresh forever.
     */
    private static final String[] DEPENDENCY_TAGS = {
            "<player>", "<display_name>", "<world>", "<online>", "<max_online>",
            "<ping>", "<x>", "<y>", "<z>"};
    private static final int READS_PLACEHOLDER_API = 1 << DEPENDENCY_TAGS.length;

    /** The icon tag has exactly two behaviours, so it needs exactly two resolvers. */
    private static final TagResolver ICON_WITH_PACK = iconResolver(true);
    private static final TagResolver ICON_WITHOUT_PACK = iconResolver(false);

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    /** Templates come from config, so this is bounded by the sidebar size. */
    private final Map<String, Integer> dependencies = new ConcurrentHashMap<>();

    /**
     * Opens a scope for one refresh of one player's HUD. Every line of that refresh must go
     * through the same scope: it holds the resolvers, which would otherwise be rebuilt per
     * line, and it remembers each PlaceholderAPI answer, which would otherwise be asked for
     * more than once.
     */
    public Refresh beginRefresh(
            HudContext hud, Function<String, Component> externalPlaceholder, boolean packApplied) {
        return new Refresh(hud, externalPlaceholder, packApplied);
    }

    public Component render(
            String template,
            HudContext hud,
            Function<String, Component> externalPlaceholder) {
        return render(template, hud, externalPlaceholder, false);
    }

    public Component render(
            String template,
            HudContext hud,
            Function<String, Component> externalPlaceholder,
            boolean packApplied) {
        return beginRefresh(hud, externalPlaceholder, packApplied).render(template);
    }

    public Component plain(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText);
    }

    private int dependenciesOf(String template) {
        return dependencies.computeIfAbsent(template, source -> {
            int mask = 0;
            for (int index = 0; index < DEPENDENCY_TAGS.length; index++) {
                if (source.contains(DEPENDENCY_TAGS[index])) {
                    mask |= 1 << index;
                }
            }
            if (source.contains("<papi:")) {
                mask |= READS_PLACEHOLDER_API;
            }
            return mask;
        });
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
            default -> throw new IllegalArgumentException("Unknown HUD tag index " + index);
        };
    }

    private static TagResolver iconResolver(boolean packApplied) {
        return TagResolver.resolver("icon", (arguments, context) -> {
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
    }

    /** One refresh of one player's HUD. Not thread safe; it lives inside a single tick. */
    public final class Refresh {
        private final HudContext hud;
        private final Function<String, Component> externalPlaceholder;
        private final boolean packApplied;
        private final Map<String, Component> resolvedPlaceholders = new HashMap<>();
        private final TagResolver resolvers;

        private Refresh(
                HudContext hud,
                Function<String, Component> externalPlaceholder,
                boolean packApplied) {
            this.hud = hud;
            this.externalPlaceholder = externalPlaceholder;
            this.packApplied = packApplied;
            TagResolver papi = TagResolver.resolver("papi", (arguments, context) -> {
                String identifier = arguments.popOr("Expected <papi:identifier>").value();
                if (arguments.hasNext()) {
                    throw context.newException(
                            "The <papi> tag accepts exactly one identifier", arguments);
                }
                return Tag.inserting(resolveOnce(identifier));
            });
            resolvers = TagResolver.resolver(
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
                    packApplied ? ICON_WITH_PACK : ICON_WITHOUT_PACK);
        }

        public Component render(String template) {
            return miniMessage.deserialize(template, resolvers);
        }

        /**
         * Identifies what this template will render to, so a caller can skip re-rendering a
         * line whose values have not moved. Returns {@code null} when the template reads
         * PlaceholderAPI, whose answer can change with none of our own values moving — such a
         * line must be rendered every time.
         */
        public String cacheKey(String template) {
            int mask = dependenciesOf(template);
            if ((mask & READS_PLACEHOLDER_API) != 0) {
                return null;
            }
            StringBuilder key = new StringBuilder(packApplied ? "1" : "0");
            for (int index = 0; index < DEPENDENCY_TAGS.length; index++) {
                if ((mask & (1 << index)) != 0) {
                    key.append(' ').append(valueOf(hud, index));
                }
            }
            return key.toString();
        }

        /**
         * MiniMessage consults a tag resolver more than once per parse, and two lines may read
         * the same placeholder, so every answer is remembered for the length of the refresh.
         * Without this each {@code <papi:>} tag costs several reflective trips into
         * PlaceholderAPI, which GuiOk does not otherwise cache.
         */
        private Component resolveOnce(String identifier) {
            return resolvedPlaceholders.computeIfAbsent(identifier, externalPlaceholder);
        }
    }
}
