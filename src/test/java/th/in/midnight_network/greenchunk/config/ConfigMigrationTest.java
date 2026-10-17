package th.in.midnight_network.greenchunk.config;

import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ConfigMigrationTest {
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
