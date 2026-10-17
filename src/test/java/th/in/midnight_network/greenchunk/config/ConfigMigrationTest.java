package th.in.midnight_network.greenchunk.config;

import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ConfigMigrationTest {
    @Test void repairsScreenshotTextsInAlreadyUpgradedConfiguration() throws Exception {
        var config = resource("config.yml");
        config.set("config-version", 3);
        config.set("dialog.title", "Slime Maps");
        config.set("messages.normal-chunk", "<yellow><bold>Not Slime Chunk</bold></yellow>");
        config.set("messages.slime-chunk", "<green><bold>Is Slime Chunk</bold></green>");
        config.set("messages.not-in-slime-chunk", "<red>You are not in a slime chunk (<yellow><x><gray>,<yellow><z><red>)!");
        config.set("messages.in-slime-chunk", "<green>You are in a slime chunk (<yellow><x><gray>,<yellow><z><green>)!");
        var defaults = resource("config.yml");
        assertTrue(ConfigMigration.merge(config, defaults));
        for (String key : List.of("dialog.title", "messages.normal-chunk", "messages.slime-chunk",
                "messages.in-slime-chunk", "messages.not-in-slime-chunk")) {
            assertEquals(defaults.getString(key), config.getString(key), key);
        }
        assertFalse(ConfigMigration.merge(config, defaults));
    }

    @Test void translatesOriginalLegacyDefaultsBeforeAndAfterSerialization() throws Exception {
        var config = new YamlConfiguration();
        config.set("gui.messages.title", "Slime Maps");
        config.set("gui.messages.not-slimechunk", "§e§lNot Slime Chunk");
        config.set("slime-command.messages.not-in-chunk", "&cYou are not in a slime chunk (&e%d&7,&e%d&c)!");
        config.set("slime-command.messages.no-permission", "&cYou do not have permission to use this command!");
        config.set("enableSlimeMapItem", false);
        var defaults = resource("config.yml");
        config.setDefaults(defaults);
        assertTrue(ConfigMigration.merge(config, defaults));
        for (String key : List.of("dialog.title", "messages.normal-chunk", "messages.not-in-slime-chunk", "messages.no-permission")) {
            assertEquals(defaults.getString(key), config.getString(key), key);
        }
        assertFalse(config.getBoolean("item.enabled"));
    }

    @Test void preservesCustomTextDuringScreenshotRepair() throws Exception {
        var config = resource("config.yml");
        config.set("config-version", 3);
        config.set("dialog.title", "<gold>My Slime Maps");
        config.set("messages.normal-chunk", "<yellow>Not Slime Chunk - custom");
        config.set("messages.in-slime-chunk", "<green>自定义 <x> <z>");
        ConfigMigration.merge(config, resource("config.yml"));
        assertEquals("<gold>My Slime Maps", config.getString("dialog.title"));
        assertEquals("<yellow>Not Slime Chunk - custom", config.getString("messages.normal-chunk"));
        assertEquals("<green>自定义 <x> <z>", config.getString("messages.in-slime-chunk"));
    }

    private static YamlConfiguration resource(String name) throws Exception {
        try (var stream = ConfigMigrationTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(stream);
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    stream, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test void translatesPreviousDefaultsOnceAndPreservesSettings() throws Exception {
        var config = resource("config-v2-en.yml");
        var defaults = resource("config.yml");
        config.set("item.enabled", false);
        config.set("visualization.radius", 3);
        assertTrue(ConfigMigration.merge(config, defaults));
        assertEquals(4, config.getInt("config-version"));
        assertEquals("<red>关闭", config.getString("dialog.close-button"));
        for (String key : defaults.getKeys(true)) {
            if (key.startsWith("messages.") && defaults.isString(key)) {
                assertEquals(defaults.getString(key), config.getString(key), key);
            }
        }
        assertFalse(config.getBoolean("item.enabled"));
        assertEquals(3, config.getInt("visualization.radius"));
        assertFalse(ConfigMigration.merge(config, defaults));
        // After the one-time upgrade, an administrator can deliberately choose English.
        config.set("dialog.close-button", "<red>Close");
        assertFalse(ConfigMigration.merge(config, defaults));
        assertEquals("<red>Close", config.getString("dialog.close-button"));
    }

    @Test void localizationPreservesCustomMessagesAndAddsNoticeComments() throws Exception {
        var config = resource("config-v2-en.yml");
        var defaults = resource("config.yml");
        config.set("messages.in-slime-chunk", "<green>服主自定义 <x>, <z>");
        config.set("messages.not-in-slime-chunk", "<red>Custom English");
        config.set("dialog.title", "<gold>我的地图");
        config.set("unrelated", "keep");
        assertTrue(ConfigMigration.merge(config, defaults));
        assertEquals("<green>服主自定义 <x>, <z>", config.getString("messages.in-slime-chunk"));
        assertEquals("<red>Custom English", config.getString("messages.not-in-slime-chunk"));
        assertEquals("<gold>我的地图", config.getString("dialog.title"));
        assertEquals("keep", config.getString("unrelated"));
        assertTrue(config.getString("messages.spawn-notice", "").contains("不保证"));
        assertFalse(config.getComments("messages.spawn-notice").isEmpty());
    }

    @Test void chineseDefaultsRenderCoordinatePlaceholders() throws Exception {
        var defaults = resource("config.yml");
        var actual = MiniMessage.miniMessage().deserialize(defaults.getString("messages.in-slime-chunk"),
                Placeholder.unparsed("x", "-32"), Placeholder.unparsed("z", "48"));
        assertEquals("当前位置属于史莱姆区块（X: -32，Z: 48）。",
                PlainTextComponentSerializer.plainText().serialize(actual));
    }

    @Test void preservesDisabledLegacyItem() throws Exception {
        var old = new YamlConfiguration();
        old.loadFromString("enableSlimeMapItem: false\nslimeMapItem: STICK\ncustom: keep\n");
        var defaults = new YamlConfiguration();
        defaults.loadFromString("item:\n  enabled: true\n  material: SLIME_BALL\n");
        old.setDefaults(defaults); // JavaPlugin also attaches defaults; they must not mask absent disk keys.
        assertTrue(ConfigMigration.merge(old, defaults));
        assertFalse(old.getBoolean("item.enabled"));
        assertEquals("STICK", old.getString("item.material"));
        assertEquals("keep", old.getString("custom"));
    }

    @Test void migratesChineseColorsAndCoordinates() throws Exception {
        var old = new YamlConfiguration();
        old.set("slime-command.messages.in-chunk", "&a你在X:&e%d&a, Z:&e%d&a的史莱姆区块内！");
        old.set("gui.messages.not-slimechunk", "§e§l普通区块");
        ConfigMigration.merge(old, new YamlConfiguration());
        var actual = MiniMessage.miniMessage().deserialize(old.getString("messages.in-slime-chunk"),
                Placeholder.unparsed("x", "-32"), Placeholder.unparsed("z", "48"));
        assertEquals("你在X:-32, Z:48的史莱姆区块内！", PlainTextComponentSerializer.plainText().serialize(actual));
        assertEquals("普通区块", PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(old.getString("messages.normal-chunk"))));
    }

    @Test void keepsExplicitModernSettingsAndIsIdempotent() {
        var config = new YamlConfiguration();
        config.set("enableSlimeMapItem", false);
        config.set("item.enabled", true);
        config.set("slime-command.messages.in-chunk", "old");
        config.set("messages.in-slime-chunk", "new");
        assertFalse(ConfigMigration.merge(config, new YamlConfiguration()));
        assertTrue(config.getBoolean("item.enabled"));
        assertEquals("new", config.getString("messages.in-slime-chunk"));
    }

    @Test void writesDefaultsAndTheirComments() {
        var config = new YamlConfiguration();
        var defaults = new YamlConfiguration();
        defaults.set("visualization.radius", 2);
        defaults.setComments("visualization.radius", List.of("Bounded scan radius"));
        assertTrue(ConfigMigration.merge(config, defaults));
        assertEquals(2, config.getInt("visualization.radius"));
        assertEquals(List.of("Bounded scan radius"), config.getComments("visualization.radius"));
        String first = config.saveToString();
        assertFalse(ConfigMigration.merge(config, defaults));
        assertEquals(first, config.saveToString());
    }

    @Test void treatsLegacyMarkupAsLiteralText() {
        var config = new YamlConfiguration();
        config.set("gui.messages.title", "<red>不是标签");
        ConfigMigration.merge(config, new YamlConfiguration());
        assertEquals("<red>不是标签", PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(config.getString("dialog.title"))));
    }
}
