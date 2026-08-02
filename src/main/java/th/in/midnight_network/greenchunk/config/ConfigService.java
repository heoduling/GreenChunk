package th.in.midnight_network.greenchunk.config;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigService {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private volatile PluginConfig current;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PluginConfig current() {
        return current;
    }

    public void reload() {
        plugin.reloadConfig();
        current = read(plugin.getConfig());
    }

    public Component slimeResult(boolean slimeChunk, int blockX, int blockZ) {
        String template = slimeChunk ? current.messages().inSlimeChunk() : current.messages().notInSlimeChunk();
        return MINI_MESSAGE.deserialize(template,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("x", Integer.toString(blockX)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("z", Integer.toString(blockZ)));
    }

    private PluginConfig read(FileConfiguration config) {
        Material itemMaterial = material(config, "item.material", Material.SLIME_BALL, true);
        Material displayMaterial = material(config, "visualization.block-material", Material.LIME_STAINED_GLASS, false);
        int dialogWidth = Math.clamp(config.getInt("dialog.width", 9), 1, 10);
        int dialogHeight = Math.clamp(config.getInt("dialog.height", 6), 1, 10);
        String cellSymbol = config.getString("dialog.cell-symbol", "■");
        if (cellSymbol.isBlank()) {
            plugin.getLogger().warning("dialog.cell-symbol cannot be blank; using ■.");
            cellSymbol = "■";
        }

        return new PluginConfig(
                new PluginConfig.ItemSettings(config.getBoolean("item.enabled", true), itemMaterial),
                new PluginConfig.DialogSettings(
                        dialogWidth, dialogHeight,
                        component(config, "dialog.title", "<dark_green>GreenChunk"),
                        cellSymbol,
                        component(config, "dialog.close-button", "<red>Close"),
                        component(config, "dialog.close-tooltip", "<gray>Close the slime-chunk map")
                ),
                new PluginConfig.Visualization(
                        Math.clamp(config.getInt("visualization.radius", 2), 1, 4),
                        Math.max(5L, config.getLong("visualization.refresh-ticks", 10L)),
                        displayMaterial,
                        Math.clamp((float) config.getDouble("visualization.view-range", 5.0D), 1.0F, 128.0F)
                ),
                new PluginConfig.Messages(
                        text(config, "messages.in-slime-chunk", "<green>You are in a slime chunk!"),
                        text(config, "messages.not-in-slime-chunk", "<red>You are not in a slime chunk!"),
                        component(config, "messages.no-permission", "<red>You do not have permission to do that."),
                        component(config, "messages.player-only", "<red>Only players can use this command."),
                        component(config, "messages.map-disabled", "<red>The slime-chunk map is disabled."),
                        component(config, "messages.reload-success", "<yellow>GreenChunk configuration reloaded."),
                        component(config, "messages.reload-failure", "<red>GreenChunk could not reload."),
                        component(config, "messages.slime-chunk", "<green>Slime chunk"),
                        component(config, "messages.normal-chunk", "<red>Normal chunk"),
                        component(config, "messages.current-chunk", "<aqua>Your current chunk"),
                        component(config, "messages.visualization-enabled", "<green>3D slime-chunk overlay enabled."),
                        component(config, "messages.visualization-disabled", "<yellow>3D slime-chunk overlay disabled.")
                ));
    }

    private Material material(FileConfiguration config, String path, Material fallback, boolean requireItem) {
        String name = config.getString(path, fallback.name()).toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(name);
        boolean valid = material != null && (requireItem ? material.isItem() : material.isBlock());
        if (!valid) {
            plugin.getLogger().warning("Invalid " + path + " '" + name + "'; using " + fallback + '.');
            return fallback;
        }
        return material;
    }

    private static String text(FileConfiguration config, String path, String fallback) {
        return config.getString(path, fallback);
    }

    private static Component component(FileConfiguration config, String path, String fallback) {
        return MINI_MESSAGE.deserialize(text(config, path, fallback));
    }
}
