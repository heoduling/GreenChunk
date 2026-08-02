package th.in.midnight_network.greenchunk.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.visual.ChunkVisualizationService;

public final class VisualCommand implements CommandExecutor {
    private final ConfigService configs;
    private final ChunkVisualizationService visualization;

    public VisualCommand(ConfigService configs, ChunkVisualizationService visualization) {
        this.configs = configs;
        this.visualization = visualization;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configs.current().messages().playerOnly());
            return true;
        }
        visualization.toggle(player);
        return true;
    }
}
