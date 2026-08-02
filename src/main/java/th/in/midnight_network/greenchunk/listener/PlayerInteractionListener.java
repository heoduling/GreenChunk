package th.in.midnight_network.greenchunk.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.gui.SlimeMapService;
import th.in.midnight_network.greenchunk.visual.ChunkVisualizationService;

public final class PlayerInteractionListener implements Listener {
    private final ConfigService configs;
    private final SlimeMapService slimeMap;
    private final ChunkVisualizationService visualization;

    public PlayerInteractionListener(ConfigService configs, SlimeMapService slimeMap,
                                     ChunkVisualizationService visualization) {
        this.configs = configs;
        this.slimeMap = slimeMap;
        this.visualization = visualization;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null
                || event.getItem().getType() != configs.current().item().material()) {
            return;
        }

        var player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!configs.current().item().enabled()) {
            return;
        }

        event.setCancelled(true);
        if (player.isSneaking()) {
            if (!player.hasPermission("greenchunk.item.map")) {
                player.sendMessage(configs.current().messages().noPermission());
                return;
            }
            slimeMap.open(player);
        } else {
            if (!player.hasPermission("greenchunk.visual")) {
                player.sendMessage(configs.current().messages().noPermission());
                return;
            }
            visualization.toggle(player);
        }
    }
}
