package th.in.midnight_network.greenchunk.config;

import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

/** Migrates legacy settings and known English defaults; custom text and old keys are retained. */
public final class ConfigMigration {
    private static final Map<String, String> ENGLISH_DEFAULTS = Map.ofEntries(
            Map.entry("dialog.title", "<dark_green>GreenChunk"),
            Map.entry("dialog.close-button", "<red>Close"),
            Map.entry("dialog.close-tooltip", "<gray>Close the slime-chunk map"),
            Map.entry("messages.in-slime-chunk", "<green>You are in a slime chunk <gray>(<yellow><x><gray>, <yellow><z><gray>)<green>!"),
            Map.entry("messages.not-in-slime-chunk", "<red>You are not in a slime chunk <gray>(<yellow><x><gray>, <yellow><z><gray>)<red>!"),
            Map.entry("messages.no-permission", "<red>You do not have permission to do that."),
            Map.entry("messages.player-only", "<red>Only players can use this command."),
            Map.entry("messages.map-disabled", "<red>The configured-item interaction is disabled."),
            Map.entry("messages.reload-success", "<yellow>GreenChunk configuration reloaded."),
            Map.entry("messages.reload-failure", "<red>GreenChunk could not reload. Check the server console."),
            Map.entry("messages.slime-chunk", "<green>Slime chunk"),
            Map.entry("messages.normal-chunk", "<red>Normal chunk"),
            Map.entry("messages.current-chunk", "<aqua>Your current chunk"),
            Map.entry("messages.visualization-enabled", "<green>3D slime-chunk overlay enabled."),
            Map.entry("messages.visualization-disabled", "<yellow>3D slime-chunk overlay disabled."));

    // Visible text from the original 1.4 config. Formatting can differ after migration.
    private static final Map<String, String> LEGACY_ENGLISH_TEXT = Map.ofEntries(
            Map.entry("dialog.title", "Slime Maps"),
            Map.entry("messages.normal-chunk", "Not Slime Chunk"),
            Map.entry("messages.slime-chunk", "Is Slime Chunk"),
            Map.entry("messages.not-in-slime-chunk", "You are not in a slime chunk (%d,%d)!"),
            Map.entry("messages.in-slime-chunk", "You are in a slime chunk (%d,%d)!"),
            Map.entry("messages.no-permission", "You do not have permission to use this command!"),
            Map.entry("messages.player-only", "You must be a player to use this command!"),
            Map.entry("messages.reload-success", "GreenChunk config reloaded."),
            Map.entry("messages.reload-failure", "Something went wrong reloading GreenChunk config, see the console for more."));

    private static String visibleText(String template) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(template,
                Placeholder.unparsed("x", "%d"), Placeholder.unparsed("z", "%d")));
    }

    private ConfigMigration() {}

    public static boolean merge(FileConfiguration config, FileConfiguration defaults) {
        boolean changed = false;
        Object storedVersion = config.getValues(false).get("config-version");
        boolean localize = !(storedVersion instanceof Number number && number.intValue() >= 4)
                && defaults.getInt("config-version", 0) >= 4;
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
        if (localize) {
            for (var entry : ENGLISH_DEFAULTS.entrySet()) {
                String actual = config.getString(entry.getKey());
                if (actual == null || !defaults.isString(entry.getKey())) continue;
                String visible = visibleText(actual);
                if (visible.equals(visibleText(entry.getValue()))
                        || visible.equals(LEGACY_ENGLISH_TEXT.get(entry.getKey()))) {
                    config.set(entry.getKey(), defaults.getString(entry.getKey()));
                }
            }
            config.set("config-version", 4);
            changed = true;
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
