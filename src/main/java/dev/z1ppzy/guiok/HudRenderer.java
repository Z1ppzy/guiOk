/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

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
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

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
        TagResolver papi = TagResolver.resolver("papi", (arguments, context) -> {
            String identifier = arguments.popOr("Expected <papi:identifier>").value();
            if (arguments.hasNext()) {
                throw context.newException("The <papi> tag accepts exactly one identifier", arguments);
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
        return miniMessage.deserialize(
                template,
                Placeholder.unparsed("player", hud.player()),
                Placeholder.component("display_name", hud.displayName()),
                Placeholder.unparsed("world", hud.world()),
                Placeholder.unparsed("online", hud.online()),
                Placeholder.unparsed("max_online", hud.maxOnline()),
                Placeholder.unparsed("ping", hud.ping()),
                Placeholder.unparsed("balance", hud.balance()),
                Placeholder.unparsed("x", hud.x()),
                Placeholder.unparsed("y", hud.y()),
                Placeholder.unparsed("z", hud.z()),
                papi,
                icon);
    }

    public Component plain(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText);
    }
}
