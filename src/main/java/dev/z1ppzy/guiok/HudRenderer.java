package dev.z1ppzy.guiok;

import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class HudRenderer {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component render(
            String template,
            HudContext hud,
            Function<String, Component> externalPlaceholder) {
        TagResolver papi = TagResolver.resolver("papi", (arguments, context) -> {
            String identifier = arguments.popOr("Expected <papi:identifier>").value();
            if (arguments.hasNext()) {
                throw context.newException("The <papi> tag accepts exactly one identifier", arguments);
            }
            return Tag.inserting(externalPlaceholder.apply(identifier));
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
                papi);
    }

    public Component plain(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText);
    }
}
