package th.in.midnight_network.greenchunk.gui;

import java.util.ArrayList;
import java.util.List;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.slime.SlimeChunkService;

public final class SlimeMapService {
    private final ConfigService configs;
    private final SlimeChunkService slimeChunks;

    public SlimeMapService(ConfigService configs, SlimeChunkService slimeChunks) {
        this.configs = configs;
        this.slimeChunks = slimeChunks;
    }

    public void open(Player player) {
        var config = configs.current();
        int width = config.dialog().width();
        int height = config.dialog().height();

        var location = player.getLocation();
        World world = location.getWorld();
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        int rotation = Math.floorMod(Math.round(location.getYaw() / 90.0F), 4);
        boolean currentIsSlime = slimeChunks.isSlimeChunk(world, centerX, centerZ);
        Component status = configs.slimeResult(currentIsSlime, location.getBlockX(), location.getBlockZ());
        List<ActionButton> cells = new ArrayList<>(width * height);

        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int screenX = column - width / 2;
                int screenZ = row - height / 2;
                int chunkX = centerX + rotateX(screenX, screenZ, rotation);
                int chunkZ = centerZ + rotateZ(screenX, screenZ, rotation);
                boolean current = chunkX == centerX && chunkZ == centerZ;
                boolean slime = slimeChunks.isSlimeChunk(world, chunkX, chunkZ);
                cells.add(cell(chunkX, chunkZ, slime, current));
            }
        }

        ActionButton close = ActionButton.builder(config.dialog().closeButton())
                .tooltip(config.dialog().closeTooltip())
                .width(100)
                .action(io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                        (response, audience) -> audience.closeDialog(),
                        ClickCallback.Options.builder().uses(1).build()))
                .build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(config.dialog().title())
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(status, 320)))
                        .build())
                .type(DialogType.multiAction(cells)
                        .columns(width)
                        .exitAction(close)
                        .build()));
        player.showDialog(dialog);
    }

    private ActionButton cell(int chunkX, int chunkZ, boolean slime, boolean current) {
        NamedTextColor color = current ? NamedTextColor.AQUA : slime ? NamedTextColor.GREEN : NamedTextColor.RED;
        Component label = Component.text(configs.current().dialog().cellSymbol(), color);
        Component type = slime ? configs.current().messages().slimeChunk() : configs.current().messages().normalChunk();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int centerX = minX + 8;
        int centerZ = minZ + 8;
        Component tooltip = Component.text("Chunk X: " + chunkX + "  Z: " + chunkZ, NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text("Center X: " + centerX + "  Z: " + centerZ, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Blocks X: " + minX + " to " + maxX, NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Blocks Z: " + minZ + " to " + maxZ, NamedTextColor.GRAY))
                .append(Component.newline())
                .append(type);
        if (current) {
            tooltip = tooltip.append(Component.newline()).append(configs.current().messages().currentChunk());
        }
        return ActionButton.builder(label)
                .tooltip(tooltip)
                .width(30)
                .action(io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                        (response, audience) -> audience.closeDialog(),
                        ClickCallback.Options.builder().uses(1).build()))
                .build();
    }

    private static int rotateX(int x, int z, int rotation) {
        return switch (rotation) {
            case 1 -> -z;
            case 2 -> -x;
            case 3 -> z;
            default -> x;
        };
    }

    private static int rotateZ(int x, int z, int rotation) {
        return switch (rotation) {
            case 1 -> x;
            case 2 -> -z;
            case 3 -> -x;
            default -> z;
        };
    }
}
