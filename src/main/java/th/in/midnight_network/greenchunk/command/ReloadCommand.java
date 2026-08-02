package th.in.midnight_network.greenchunk.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.visual.ChunkVisualizationService;

public final class ReloadCommand implements CommandExecutor {
    private final Plugin plugin;
    private final ConfigService configs;
    private final ChunkVisualizationService visualization;

    public ReloadCommand(Plugin plugin, ConfigService configs, ChunkVisualizationService visualization) {
        this.plugin = plugin;
        this.configs = configs;
        this.visualization = visualization;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        try {
            configs.reload();
            visualization.reloadActive();
            sender.sendMessage(configs.current().messages().reloadSuccess());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to reload configuration", exception);
            sender.sendMessage(configs.current().messages().reloadFailure());
        }
        return true;
    }
}
