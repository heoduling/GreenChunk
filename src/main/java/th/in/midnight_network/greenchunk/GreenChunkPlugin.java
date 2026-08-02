package th.in.midnight_network.greenchunk;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import th.in.midnight_network.greenchunk.command.CheckCommand;
import th.in.midnight_network.greenchunk.command.MapCommand;
import th.in.midnight_network.greenchunk.command.ReloadCommand;
import th.in.midnight_network.greenchunk.command.VisualCommand;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.gui.SlimeMapService;
import th.in.midnight_network.greenchunk.listener.PlayerInteractionListener;
import th.in.midnight_network.greenchunk.slime.SlimeChunkService;
import th.in.midnight_network.greenchunk.visual.ChunkVisualizationService;
import th.in.midnight_network.greenchunk.state.VisualizationStateStore;

public final class GreenChunkPlugin extends JavaPlugin {
    private ConfigService configService;
    private ChunkVisualizationService visualization;
    private VisualizationStateStore visualizationState;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configService = new ConfigService(this);
        configService.reload();

        var slimeChunks = new SlimeChunkService();
        visualizationState = new VisualizationStateStore(this);
        visualizationState.load();
        visualization = new ChunkVisualizationService(this, configService, slimeChunks, visualizationState);
        var slimeMap = new SlimeMapService(configService, slimeChunks);

        command("slime").setExecutor(new CheckCommand(configService, slimeMap));
        command("slimemap").setExecutor(new MapCommand(configService, slimeMap));
        command("greenchunkreload").setExecutor(new ReloadCommand(this, configService, visualization));
        command("slimevisual").setExecutor(new VisualCommand(configService, visualization));

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerInteractionListener(configService, slimeMap, visualization), this);
        pluginManager.registerEvents(visualization, this);

        getLogger().info("GreenChunk 2 enabled for Paper/Folia 26.2.");
    }

    @Override
    public void onDisable() {
        if (visualization != null) {
            visualization.stopAll();
        }
        if (visualizationState != null) {
            visualizationState.saveNow();
        }
    }

    private PluginCommand command(String name) {
        return Objects.requireNonNull(getCommand(name), "Command missing from plugin.yml: " + name);
    }
}
