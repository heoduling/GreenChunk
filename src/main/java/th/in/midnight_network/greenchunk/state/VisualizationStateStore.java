package th.in.midnight_network.greenchunk.state;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class VisualizationStateStore {
    private static final String PATH = "enabled-players";
    private final Plugin plugin;
    private final File file;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    public VisualizationStateStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "visualization-state.yml");
    }

    public void load() {
        enabledPlayers.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String value : data.getStringList(PATH)) {
            try {
                enabledPlayers.add(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid player UUID in " + file.getName() + ": " + value);
            }
        }
    }

    public boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(playerId);
        } else {
            enabledPlayers.remove(playerId);
        }
        saveAsync();
    }

    public void saveAsync() {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> saveNow());
    }

    public synchronized void saveNow() {
        if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Could not create the GreenChunk data directory.");
            return;
        }
        List<String> values = enabledPlayers.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(UUID::toString)
                .toList();
        YamlConfiguration data = new YamlConfiguration();
        data.set(PATH, values);
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + file.getName(), exception);
        }
    }
}
