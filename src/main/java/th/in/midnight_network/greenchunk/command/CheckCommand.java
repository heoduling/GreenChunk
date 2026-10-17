package th.in.midnight_network.greenchunk.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.gui.SlimeMapService;

public final class CheckCommand implements CommandExecutor {
    private final ConfigService configs;
    private final SlimeMapService slimeMap;

    public CheckCommand(ConfigService configs, SlimeMapService slimeMap) {
        this.configs = configs;
        this.slimeMap = slimeMap;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configs.current().messages().playerOnly());
            return true;
        }
        slimeMap.check(player);
        return true;
    }
}
