package th.in.midnight_network.greenchunk.config;

import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

/** Adds missing settings only; old keys remain available for rollback. */
public final class ConfigMigration {
    private ConfigMigration() {}

    public static boolean merge(FileConfiguration config, FileConfiguration defaults) {
        boolean changed = false;
        for (var entry : Map.of("enableSlimeMapItem", "item.enabled", "slimeMapItem", "item.material").entrySet()) {
            if (config.contains(entry.getKey(), true) && !config.contains(entry.getValue(), true)) {
                config.set(entry.getValue(), config.get(entry.getKey()));
                changed = true;
            }
        }
        Map<String, String> messages = Map.ofEntries(
                Map.entry("slime-command.messages.in-chunk", "messages.in-slime-chunk"),
                Map.entry("slime-command.messages.not-in-chunk", "messages.not-in-slime-chunk"),
                Map.entry("slime-command.messages.no-permission", "messages.no-permission"),
                Map.entry("slime-command.messages.not-player", "messages.player-only"),
                Map.entry("reload-command.messages.reload-config", "messages.reload-success"),
                Map.entry("reload-command.messages.error-reload-config", "messages.reload-failure"),
                Map.entry("gui.messages.title", "dialog.title"),
                Map.entry("gui.messages.is-slimechunk", "messages.slime-chunk"),
                Map.entry("gui.messages.not-slimechunk", "messages.normal-chunk"));
        for (var entry : messages.entrySet()) {
            if (!config.contains(entry.getValue(), true) && config.isString(entry.getKey())) {
                String text = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getString(entry.getKey(), "").replace('§', '&')));
                if (entry.getValue().endsWith("slime-chunk")) {
                    text = text.replaceFirst("%(?:1\\$)?d", "<x>").replaceFirst("%(?:2\\$)?d", "<z>");
                }
                config.set(entry.getValue(), text);
                changed = true;
            }
        }
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !config.contains(key, true)) {
                config.set(key, defaults.get(key));
                config.setComments(key, defaults.getComments(key));
                config.setInlineComments(key, defaults.getInlineComments(key));
                changed = true;
            }
        }
        return changed;
    }
}
