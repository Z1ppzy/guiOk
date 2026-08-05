/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

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

public final class HudRenderer {
    private static final Key HUD_FONT = Key.key("guiok", "hud");

    /** The icon tag has exactly two behaviours, so it needs exactly two resolvers. */
    private static final TagResolver ICON_WITH_PACK = iconResolver(true);
    private static final TagResolver ICON_WITHOUT_PACK = iconResolver(false);

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

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
        private final Function<String, Component> externalPlaceholder;
        private final Map<String, Component> resolvedPlaceholders = new HashMap<>();
        private final TagResolver resolvers;

        private Refresh(
                HudContext hud,
                Function<String, Component> externalPlaceholder,
                boolean packApplied) {
            this.externalPlaceholder = externalPlaceholder;
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
