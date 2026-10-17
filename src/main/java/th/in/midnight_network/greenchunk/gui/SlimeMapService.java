package th.in.midnight_network.greenchunk.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.Location;
import th.in.midnight_network.greenchunk.config.PluginConfig;
import th.in.midnight_network.greenchunk.slime.SlimeChunkService.ChunkPos;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.slime.SlimeChunkService;

public final class SlimeMapService {
    private final Plugin plugin;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final ConfigService configs;
    private final SlimeChunkService slimeChunks;
    private volatile boolean closed;
    private final DialogSessions dialogs = new DialogSessions();

    public void close() { closed = true; pending.clear(); }

    public java.util.concurrent.CompletableFuture<Void> closeDialogsForUnload() { return dialogs.closeForUnload(); }

    public void detachDialogs() { dialogs.detach(); }

    public SlimeMapService(Plugin plugin, ConfigService configs, SlimeChunkService slimeChunks) {
        this.plugin = plugin;
        this.configs = configs;
        this.slimeChunks = slimeChunks;
    }

    public void open(Player player) {
        request(player, false);
    }

    public void check(Player player) {
        request(player, true);
    }

    private void request(Player player, boolean checkOnly) {
        if (closed) return;
        UUID id = player.getUniqueId();
        if (!pending.add(id)) return;
        var config = configs.current();
        int width = checkOnly ? 1 : config.dialog().width();
        int height = checkOnly ? 1 : config.dialog().height();
        var location = player.getLocation();
        World world = location.getWorld();
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        int rotation = Math.floorMod(Math.round(location.getYaw() / 90.0F), 4);
        List<ChunkPos> cells = new ArrayList<>();
        for (int row = 0; row < height; row++) for (int column = 0; column < width; column++) {
            int x = column - width / 2, z = row - height / 2;
            cells.add(new ChunkPos(centerX + rotateX(x, z, rotation), centerZ + rotateZ(x, z, rotation)));
        }
        int minX = cells.stream().mapToInt(ChunkPos::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(ChunkPos::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(ChunkPos::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(ChunkPos::z).max().orElseThrow();
        slimeChunks.scan(world, minX, minZ, maxX - minX + 1, maxZ - minZ + 1).whenComplete((values, error) -> {
            if (closed || !plugin.isEnabled()) { pending.remove(id); return; }
            try {
                var task = player.getScheduler().run(plugin, ignored -> {
                    pending.remove(id);
                    if (closed) return;
                    var now = player.getLocation();
                    if (now.getWorld() != world || now.getBlockX() >> 4 != centerX
                            || now.getBlockZ() >> 4 != centerZ || configs.current() != config) return;
                    if (error != null) {
                        player.sendMessage(Component.text("区块查询暂时失败，请稍后重试。", NamedTextColor.RED));
                        plugin.getLogger().warning("史莱姆区块查询失败：" + error);
                        return;
                    }
                    if (checkOnly) player.sendMessage(configs.slimeResult(values.get(new ChunkPos(centerX, centerZ)),
                            location.getBlockX(), location.getBlockZ())
                            .append(Component.newline()).append(config.messages().spawnNotice()));
                    else show(player, config, location, values);
                }, () -> pending.remove(id));
                if (task == null) pending.remove(id);
            } catch (org.bukkit.plugin.IllegalPluginAccessException disabled) { pending.remove(id); }
        });
    }

    private void show(Player player, PluginConfig config, Location location, Map<ChunkPos, Boolean> values) {
        int width = config.dialog().width(), height = config.dialog().height();
        int centerX = location.getBlockX() >> 4, centerZ = location.getBlockZ() >> 4;
        int rotation = Math.floorMod(Math.round(location.getYaw() / 90.0F), 4);
        boolean currentIsSlime = values.get(new ChunkPos(centerX, centerZ));
        Component status = configs.slimeResult(currentIsSlime, location.getBlockX(), location.getBlockZ());
        List<ActionButton> cells = new ArrayList<>(width * height);

        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int screenX = column - width / 2;
                int screenZ = row - height / 2;
                int chunkX = centerX + rotateX(screenX, screenZ, rotation);
                int chunkZ = centerZ + rotateZ(screenX, screenZ, rotation);
                boolean current = chunkX == centerX && chunkZ == centerZ;
                boolean slime = values.get(new ChunkPos(chunkX, chunkZ));
                cells.add(cell(chunkX, chunkZ, slime, current));
            }
        }

        ActionButton close = ActionButton.builder(config.dialog().closeButton())
                .tooltip(config.dialog().closeTooltip())
                .width(100)
                .action(io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                        net.kyori.adventure.key.Key.key("greenchunk:close"), null))
                .build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(config.dialog().title())
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(status, 320),
                                DialogBody.plainMessage(config.messages().spawnNotice(), 320)))
                        .build())
                .type(DialogType.multiAction(cells)
                        .columns(width)
                        .exitAction(close)
                        .build()));
        dialogs.track(player, dialog);
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
        Component tooltip = Component.text("区块坐标 X: " + chunkX + "  Z: " + chunkZ, NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text("中心坐标 X: " + centerX + "  Z: " + centerZ, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("方块范围 X: " + minX + " 至 " + maxX, NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("方块范围 Z: " + minZ + " 至 " + maxZ, NamedTextColor.GRAY))
                .append(Component.newline())
                .append(type);
        if (current) {
            tooltip = tooltip.append(Component.newline()).append(configs.current().messages().currentChunk());
        }
        return ActionButton.builder(label)
                .tooltip(tooltip)
                .width(30)
                .action(io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                        net.kyori.adventure.key.Key.key("greenchunk:close"), null))
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
