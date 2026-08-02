package th.in.midnight_network.greenchunk.visual;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
        UUID playerId = player.getUniqueId();
        if (stateStore.isEnabled(playerId)) {
            stateStore.setEnabled(playerId, false);
            stop(playerId);
            player.sendMessage(configs.current().messages().visualizationDisabled());
        } else {
            stateStore.setEnabled(playerId, true);
            start(player);
            player.sendMessage(configs.current().messages().visualizationEnabled());
        }
    }

    public void stopAll() {
        List.copyOf(displays.keySet()).forEach(this::stop);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (stateStore.isEnabled(player.getUniqueId())) {
            start(player);
        }
    }

    public void reloadActive() {
        for (UUID playerId : List.copyOf(displays.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                stop(playerId);
                continue;
            }
            player.getScheduler().run(plugin, task -> {
                Map<ChunkKey, List<Entity>> active = displays.get(playerId);
                if (active == null) {
                    return;
                }
                for (ChunkKey chunk : new ArrayList<>(active.keySet())) {
                    removeCell(playerId, chunk);
                }
                states.remove(playerId);
                refresh(player, true);
            }, () -> stop(playerId));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Preserve the preference; only discard this connection's entities.
        stop(event.getPlayer().getUniqueId());
    }

    private void start(Player player) {
        UUID playerId = player.getUniqueId();
        displays.put(playerId, new ConcurrentHashMap<>());
        refresh(player, true);
        long period = configs.current().visualization().refreshTicks();
        tasks.put(playerId, player.getScheduler().runAtFixedRate(plugin,
                task -> refresh(player, false),
                () -> stop(playerId),
                period,
                period));
    }

    private void refresh(Player player, boolean force) {
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
        states.put(playerId, next);

        Set<ChunkKey> wanted = wantedSlimeChunks(world, centerX, centerZ);
        for (ChunkKey existing : new ArrayList<>(active.keySet())) {
            if (!wanted.contains(existing)) {
                removeCell(playerId, existing);
            }
        }

        for (ChunkKey chunk : wanted) {
            if (active.putIfAbsent(chunk, new CopyOnWriteArrayList<>()) == null) {
                spawnCell(player, world, chunk, playerLocation);
            }
        }
    }

    private Set<ChunkKey> wantedSlimeChunks(World world, int centerX, int centerZ) {
        int radius = configs.current().visualization().radius();
        Set<ChunkKey> wanted = new HashSet<>();
        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                if (slimeChunks.isSlimeChunk(world, chunkX, chunkZ)) {
                    wanted.add(new ChunkKey(world.getUID(), chunkX, chunkZ));
                }
            }
        }
        return wanted;
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
            return;
        }
        cell.add(entity);
        player.getScheduler().run(plugin,
                task -> {
                    Map<ChunkKey, List<Entity>> latest = displays.get(playerId);
                    if (latest != null && latest.containsKey(chunk)) {
                        player.showEntity(plugin, entity);
                    } else {
                        removeEntity(entity);
                    }
                },
                () -> removeEntity(entity));
    }

    private void stop(UUID playerId) {
        ScheduledTask task = tasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        states.remove(playerId);
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

    private void removeEntity(Entity entity) {
        // Bukkit marks the plugin disabled before onDisable is invoked. Folia
        // rejects any newly registered entity task at that point. Displays are
        // non-persistent, so server shutdown/world unload will discard them.
        if (!plugin.isEnabled()) {
            return;
        }
        entity.getScheduler().run(plugin, task -> entity.remove(), null);
    }

    private static Transformation transformation(float x, float y, float z,
                                                  float scaleX, float scaleY, float scaleZ) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(),
                new Vector3f(scaleX, scaleY, scaleZ), new Quaternionf());
    }

    private record ChunkKey(UUID worldId, int x, int z) {}

    private record ViewState(UUID worldId, int chunkX, int chunkZ) {}
}
