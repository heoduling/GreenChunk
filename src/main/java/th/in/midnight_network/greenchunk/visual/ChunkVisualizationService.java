package th.in.midnight_network.greenchunk.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import th.in.midnight_network.greenchunk.lifecycle.HotReloadSupport;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import th.in.midnight_network.greenchunk.config.ConfigService;
import th.in.midnight_network.greenchunk.slime.SlimeChunkService;
import th.in.midnight_network.greenchunk.state.VisualizationStateStore;

public final class ChunkVisualizationService implements Listener {
    private final Plugin plugin;
    private final ConfigService configs;
    private final SlimeChunkService slimeChunks;
    private final VisualizationStateStore stateStore;
    private final Map<UUID, Map<ChunkKey, List<Entity>>> displays = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, ViewState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ViewState> anchors = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Set<Entity> ownedEntities = ConcurrentHashMap.newKeySet();
    private final Map<Entity, CompletableFuture<Void>> removals = new ConcurrentHashMap<>();
    private volatile boolean closing;

    public ChunkVisualizationService(Plugin plugin, ConfigService configs, SlimeChunkService slimeChunks,
                                     VisualizationStateStore stateStore) {
        this.plugin = plugin;
        this.configs = configs;
        this.slimeChunks = slimeChunks;
        this.stateStore = stateStore;
    }

    public boolean isActive(Player player) {
        return displays.containsKey(player.getUniqueId());
    }

    public void toggle(Player player) {
        if (closing) return;
        UUID playerId = player.getUniqueId();
        if (stateStore.isEnabled(playerId)) {
            stateStore.setEnabled(playerId, false);
            stop(playerId);
            player.sendMessage(configs.current().messages().visualizationDisabled());
        } else {
            stateStore.setEnabled(playerId, true);
            start(player);
            player.sendMessage(configs.current().messages().visualizationEnabled()
                    .append(net.kyori.adventure.text.Component.newline())
                    .append(configs.current().messages().spawnNotice()));
        }
    }

    public void stopAll() {
        closing = true;
        List.copyOf(displays.keySet()).forEach(this::stop);
    }

    public void stopAccepting() { closing = true; }

    /** Called after every online player's owner barrier, so no spawn can still be publishing. */
    public CompletableFuture<Void> removeForUnload() {
        stopAll();
        var work = List.copyOf(ownedEntities).stream().map(this::removeEntity).toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(work).thenRun(() -> {
            pending.clear();
            if (!ownedEntities.isEmpty()) throw new IllegalStateException("三维显示实体尚未清理完成");
        });
    }

    public void restoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            HotReloadSupport.onOwner(plugin, player, () -> restore(player));
        }
    }

    private void restore(Player player) {
        if (!closing && stateStore.isEnabled(player.getUniqueId()) && player.hasPermission("greenchunk.visual")) start(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restore(event.getPlayer());
    }

    public void reloadActive() {
        slimeChunks.invalidate();
        if (closing) return;
        for (UUID playerId : List.copyOf(displays.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                stop(playerId);
                continue;
            }
            player.getScheduler().run(plugin, task -> {
                if (closing) return;
                Map<ChunkKey, List<Entity>> active = displays.get(playerId);
                if (active == null) {
                    return;
                }
                stop(playerId);
                start(player);
            }, () -> stop(playerId));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Preserve the preference; only discard this connection's entities.
        stop(event.getPlayer().getUniqueId());
    }

    private void start(Player player) {
        if (closing) return;
        UUID playerId = player.getUniqueId();
        stop(playerId);
        displays.put(playerId, new ConcurrentHashMap<>());
        refresh(player, true);
        long period = configs.current().visualization().refreshTicks();
        var scheduled = player.getScheduler().runAtFixedRate(plugin,
                task -> refresh(player, false),
                () -> stop(playerId),
                period,
                period);
        if (scheduled != null) tasks.put(playerId, scheduled);
        else stop(playerId);
    }

    private void refresh(Player player, boolean force) {
        if (closing) return;
        UUID playerId = player.getUniqueId();
        Map<ChunkKey, List<Entity>> active = displays.get(playerId);
        if (active == null) {
            return;
        }

        Location playerLocation = player.getLocation();
        World world = playerLocation.getWorld();
        int centerX = playerLocation.getBlockX() >> 4;
        int centerZ = playerLocation.getBlockZ() >> 4;
        ViewState next = new ViewState(world.getUID(), centerX, centerZ);
        if (!force && next.equals(states.get(playerId))) {
            return;
        }
        if (!pending.add(playerId)) return;
        var config = configs.current();
        int radius = configs.current().visualization().radius();
        slimeChunks.scan(world, centerX - radius, centerZ - radius, radius * 2 + 1, radius * 2 + 1)
                .whenComplete((values, error) -> {
            if (closing || !plugin.isEnabled()) { pending.remove(playerId); return; }
            try {
                var task = player.getScheduler().run(plugin, ignored -> {
                    pending.remove(playerId);
                    if (closing) return;
                    if (displays.get(playerId) != active || configs.current() != config) return;
                    var now = player.getLocation();
                    if (now.getWorld() != world || now.getBlockX() >> 4 != centerX || now.getBlockZ() >> 4 != centerZ) return;
                    if (error != null) {
                        stop(playerId);
                        plugin.getLogger().warning("史莱姆区块三维显示查询失败：" + error);
                        return;
                    }
                    var anchor = anchors.get(playerId);
                    // Display entities are anchored near the player, not at their transformed volume.
                    // Re-anchor every two chunks so retained entities stay in the minimum tracking range.
                    boolean reanchor = anchor == null || !anchor.worldId.equals(next.worldId)
                            || Math.abs((long) anchor.chunkX - centerX) > 1
                            || Math.abs((long) anchor.chunkZ - centerZ) > 1;
                    for (ChunkKey existing : new ArrayList<>(active.keySet())) {
                        if (reanchor || !existing.worldId.equals(next.worldId)
                                || !Boolean.TRUE.equals(values.get(new SlimeChunkService.ChunkPos(existing.x, existing.z)))) {
                            removeCell(playerId, existing);
                        }
                    }
                    if (reanchor) anchors.put(playerId, next);
                    states.put(playerId, next);
                    values.forEach((pos, slime) -> {
                        if (slime) {
                            var chunk = new ChunkKey(world.getUID(), pos.x(), pos.z());
                            if (active.containsKey(chunk)) return;
                            active.put(chunk, new CopyOnWriteArrayList<>());
                            spawnCell(player, world, chunk, now);
                        }
                    });
                }, () -> pending.remove(playerId));
                if (task == null) pending.remove(playerId);
            } catch (org.bukkit.plugin.IllegalPluginAccessException disabled) {
                pending.remove(playerId);
            }
        });
    }

    private void spawnCell(Player player, World world, ChunkKey chunk, Location anchor) {
        Map<ChunkKey, List<Entity>> active = displays.get(player.getUniqueId());
        if (active == null || !active.containsKey(chunk)) {
            return;
        }

        int worldHeight = world.getMaxHeight() - world.getMinHeight();
        Location displayAnchor = cleanAnchor(anchor);
        BlockDisplay volume = world.spawn(displayAnchor, BlockDisplay.class, display -> {
            prepare(display);
            display.setBlock(configs.current().visualization().blockMaterial().createBlockData());
            display.setTransformation(volumeTransformation(chunk, displayAnchor, world));
            display.setDisplayWidth(configs.current().visualization().radius() * 32.0F + 16.0F);
            display.setDisplayHeight(worldHeight);
        });
        ownedEntities.add(volume);
        track(player, chunk, volume);

    }

    private Transformation volumeTransformation(ChunkKey chunk, Location anchor, World world) {
        float x = (float) ((chunk.x() << 4) - anchor.getX());
        float y = (float) (world.getMinHeight() - anchor.getY());
        float z = (float) ((chunk.z() << 4) - anchor.getZ());
        float height = world.getMaxHeight() - world.getMinHeight();
        return transformation(x, y, z, 16.0F, height, 16.0F);
    }

    private static Location cleanAnchor(Location location) {
        Location anchor = location.clone();
        anchor.setYaw(0.0F);
        anchor.setPitch(0.0F);
        return anchor;
    }

    private void prepare(Entity entity) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setVisibleByDefault(false);
        if (entity instanceof org.bukkit.entity.Display display) {
            display.setViewRange(configs.current().visualization().viewRange());
            display.setShadowRadius(0.0F);
        }
    }

    private void track(Player player, ChunkKey chunk, Entity entity) {
        UUID playerId = player.getUniqueId();
        Map<ChunkKey, List<Entity>> active = displays.get(playerId);
        List<Entity> cell = active == null ? null : active.get(chunk);
        if (cell == null) {
            entity.remove();
            ownedEntities.remove(entity);
            return;
        }
        cell.add(entity);
        // Spawn and visibility already run on the player's owning region.
        player.showEntity(plugin, entity);
    }

    private void stop(UUID playerId) {
        ScheduledTask task = tasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        states.remove(playerId);
        anchors.remove(playerId);
        Map<ChunkKey, List<Entity>> active = displays.remove(playerId);
        if (active != null) {
            active.values().forEach(this::removeEntities);
        }
    }

    private void removeCell(UUID playerId, ChunkKey chunk) {
        Map<ChunkKey, List<Entity>> active = displays.get(playerId);
        List<Entity> entities = active == null ? null : active.remove(chunk);
        if (entities != null) {
            removeEntities(entities);
        }
    }

    private void removeEntities(List<Entity> entities) {
        List.copyOf(entities).forEach(this::removeEntity);
        entities.clear();
    }

    private CompletableFuture<Void> removeEntity(Entity entity) {
        // Supported PlugMan commands drain before disable. Normal server shutdown
        // discards non-persistent entities; direct API hot-disable is not supported.
        if (!plugin.isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("插件已停用，无法调度显示实体清理"));
        }
        var result = new CompletableFuture<Void>();
        var previous = removals.putIfAbsent(entity, result);
        if (previous != null) return previous;
        HotReloadSupport.onOwner(plugin, entity, entity::remove).whenComplete((ignored, error) -> {
            if (error == null) {
                ownedEntities.remove(entity);
                result.complete(null);
            } else result.completeExceptionally(error);
            removals.remove(entity, result);
        });
        return result;
    }

    private static Transformation transformation(float x, float y, float z,
                                                  float scaleX, float scaleY, float scaleZ) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(),
                new Vector3f(scaleX, scaleY, scaleZ), new Quaternionf());
    }

    private record ChunkKey(UUID worldId, int x, int z) {}

    private record ViewState(UUID worldId, int chunkX, int chunkZ) {}
}
