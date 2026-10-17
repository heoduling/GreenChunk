package th.in.midnight_network.greenchunk.config;

import java.util.Locale;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
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
        var config = plugin.getConfig();
        var backup = plugin.getDataFolder().toPath().resolve("config-v1-backup.yml");
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) throw new IllegalStateException("缺少默认配置 config.yml");
            if (config.contains("enableSlimeMapItem", true) && !Files.exists(backup)) {
                Files.copy(plugin.getDataFolder().toPath().resolve("config.yml"), backup);
            }
            var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            var chineseBackup = plugin.getDataFolder().toPath().resolve("config-before-zh-v4-backup.yml");
            // Read the stored version, not the attached resource defaults.
            Object storedVersion = config.getValues(false).get("config-version");
            if (!(storedVersion instanceof Number number && number.intValue() >= 4)
                    && !Files.exists(chineseBackup)) {
                Files.copy(plugin.getDataFolder().toPath().resolve("config.yml"), chineseBackup);
            }
            if (ConfigMigration.merge(config, defaults)) plugin.saveConfig();
        } catch (IOException exception) {
            throw new IllegalStateException("无法迁移 GreenChunk 配置", exception);
        }
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
            plugin.getLogger().warning("dialog.cell-symbol 不能为空，已使用 ■。");
            cellSymbol = "■";
        }

        return new PluginConfig(
                new PluginConfig.ItemSettings(config.getBoolean("item.enabled", true), itemMaterial),
                new PluginConfig.DialogSettings(
                        dialogWidth, dialogHeight,
                        component(config, "dialog.title", "<dark_green>史莱姆区块地图"),
                        cellSymbol,
                        component(config, "dialog.close-button", "<red>关闭"),
                        component(config, "dialog.close-tooltip", "<gray>关闭史莱姆区块地图")
                ),
                new PluginConfig.Visualization(
                        Math.clamp(config.getInt("visualization.radius", 2), 1, 4),
                        Math.max(5L, config.getLong("visualization.refresh-ticks", 10L)),
                        displayMaterial,
                        Math.clamp((float) config.getDouble("visualization.view-range", 5.0D), 1.0F, 128.0F)
                ),
                new PluginConfig.Messages(
                        text(config, "messages.in-slime-chunk", "<green>当前位置属于史莱姆区块。"),
                        text(config, "messages.not-in-slime-chunk", "<red>当前位置不属于史莱姆区块。"),
                        component(config, "messages.no-permission", "<red>你没有权限执行此操作。"),
                        component(config, "messages.player-only", "<red>此命令只能由玩家执行。"),
                        component(config, "messages.map-disabled", "<red>使用指定物品交互的功能已关闭。"),
                        component(config, "messages.reload-success", "<yellow>GreenChunk 配置已重新加载。"),
                        component(config, "messages.reload-failure", "<red>GreenChunk 配置重载失败，请查看服务器控制台。"),
                        component(config, "messages.slime-chunk", "<green>史莱姆区块"),
                        component(config, "messages.normal-chunk", "<red>非史莱姆区块"),
                        component(config, "messages.current-chunk", "<aqua>你当前所在的区块"),
                        component(config, "messages.visualization-enabled", "<green>已开启史莱姆区块三维显示，仅自己可见。"),
                        component(config, "messages.visualization-disabled", "<yellow>已关闭史莱姆区块三维显示。"),
                        component(config, "messages.spawn-notice", "<gray>标记仅表示史莱姆区块，不保证能自然刷怪；还需满足群系、维度和生成条件。沼泽地表刷怪不以此标记为准。")
                ));
    }

    private Material material(FileConfiguration config, String path, Material fallback, boolean requireItem) {
        String name = config.getString(path, fallback.name()).toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(name);
        boolean valid = material != null && (requireItem ? material.isItem() : material.isBlock());
        if (!valid) {
            plugin.getLogger().warning("配置 " + path + " 的材质 '" + name + "' 无效，已使用 " + fallback + '。');
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
