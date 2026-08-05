/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

/*
 * Experiment, not production code. A prototype of HudRenderer with three changes:
 * 
 * 1. Templates with no dynamic tag are parsed once and reused forever.
 * 2. The built-in placeholders are built once per refresh instead of once per line.
 * 3. The <icon> resolver has only two possible behaviours, so it is a pair of constants
 * rather than a fresh lambda per call.
 * 
 * The public shape mirrors how SidebarService actually calls the renderer: open a bundle for
 * one player, render every line through it, drop it.
 */

package dev.z1ppzy.guiok.experiment;

import dev.z1ppzy.guiok.HudContext;
import dev.z1ppzy.guiok.PackIcons;
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

public final class BundledRenderer {
    private static final Key HUD_FONT = Key.key("guiok", "hud");

    /** Every tag whose value can differ between two refreshes of the same template. */
    private static final String[] DYNAMIC_TAGS = {
            "<player>", "<display_name>", "<world>", "<online>", "<max_online>",
            "<ping>", "<x>", "<y>", "<z>", "<papi:", "<icon:", "<icon>"};

    private static final TagResolver ICON_APPLIED = iconResolver(true);
    private static final TagResolver ICON_MISSING = iconResolver(false);

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Component> staticTemplates = new ConcurrentHashMap<>();

    /** Opens a bundle for one player; every line of that player's sidebar goes through it. */
    public Bundle open(
            HudContext hud, Function<String, Component> externalPlaceholder, boolean packApplied) {
        return new Bundle(hud, externalPlaceholder, packApplied);
    }

    int cachedTemplateCount() {
        return staticTemplates.size();
    }

    static boolean isStatic(String template) {
        for (String tag : DYNAMIC_TAGS) {
            if (template.contains(tag)) {
                return false;
            }
        }
        return true;
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

    public final class Bundle {
        private final TagResolver resolvers;

        private Bundle(
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
                    packApplied ? ICON_APPLIED : ICON_MISSING);
        }

        public Component render(String template) {
            Component cached = staticTemplates.get(template);
            if (cached != null) {
                return cached;
            }
            Component rendered = renderWithoutCache(template);
            if (isStatic(template)) {
                staticTemplates.put(template, rendered);
            }
            return rendered;
        }

        /** Hoisted resolvers only, so the two optimisations can be measured apart. */
        public Component renderWithoutCache(String template) {
            return miniMessage.deserialize(template, resolvers);
        }
    }
}
