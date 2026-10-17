package th.in.midnight_network.greenchunk.slime;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Uses the server's rules, including Shiroha SecureSeed and world overrides. */
public final class SlimeChunkService implements AutoCloseable, org.bukkit.event.Listener {
    private final Plugin plugin;
    private final Set<CompletableFuture<Boolean>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    // A caller timeout does not cancel terrain generation in the server.
    private final Semaphore loading = new Semaphore(1024);
    private final java.util.LinkedHashMap<QueryKey, CachedQuery> cache = new java.util.LinkedHashMap<>();
    private static final long CACHE_NANOS = TimeUnit.SECONDS.toNanos(30);

    public SlimeChunkService(Plugin plugin) { this.plugin = plugin; }

    public CompletableFuture<Boolean> isSlimeChunk(World world, int chunkX, int chunkZ) {
        if (closed || !plugin.isEnabled()) {
            var cancelled = new CompletableFuture<Boolean>(); cancelled.cancel(false); return cancelled;
        }
        var key = new QueryKey(world.getUID(), chunkX, chunkZ);
        CachedQuery entry;
        boolean start = false;
        synchronized (cache) {
            if (closed) { var cancelled = new CompletableFuture<Boolean>(); cancelled.cancel(false); return cancelled; }
            entry = cache.get(key);
            if (entry == null || System.nanoTime() - entry.created >= CACHE_NANOS) {
                entry = new CachedQuery(new CompletableFuture<>(), System.nanoTime());
                cache.put(key, entry);
                while (cache.size() > 8192) cache.remove(cache.keySet().iterator().next());
                start = true;
            }
        }
        var shared = entry;
        if (start) {
            load(world, chunkX, chunkZ).whenComplete((value, error) -> {
                if (error != null) {
                    synchronized (cache) { cache.remove(key, shared); }
                    if (error instanceof java.util.concurrent.CancellationException) shared.result.cancel(false);
                    else shared.result.completeExceptionally(error);
                } else shared.result.complete(value);
            });
        }
        // Each caller owns its timeout/cancellation; it must not cancel another player's query.
        var caller = new CompletableFuture<Boolean>();
        shared.result.whenComplete((value, error) -> {
            if (shared.result.isCancelled()) caller.cancel(false);
            else if (error != null) caller.completeExceptionally(error);
            else caller.complete(value);
        });
        return caller;
    }

    public void invalidate() { synchronized (cache) { cache.clear(); } }

    @org.bukkit.event.EventHandler
    public void onWorldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        var id = event.getWorld().getUID();
        synchronized (cache) { cache.keySet().removeIf(key -> key.world.equals(id)); }
    }

    private CompletableFuture<Boolean> load(World world, int chunkX, int chunkZ) {
        var result = new CompletableFuture<Boolean>();
        pending.add(result);
        result.orTimeout(15, TimeUnit.SECONDS).whenComplete((value, error) -> pending.remove(result));
        if (closed || !plugin.isEnabled()) {
            result.cancel(false);
            return result;
        }
        if (!loading.tryAcquire()) {
            result.completeExceptionally(new RejectedExecutionException("Chunk query capacity reached"));
            return result;
        }
        if (closed) { releaseLoad(); result.cancel(false); return result; }
        // Loading is asynchronous. The boolean is read only on the chunk's owner.
        try {
            world.getChunkAtAsync(chunkX, chunkZ).whenComplete((chunk, error) -> {
                if (error != null) { releaseLoad(); result.completeExceptionally(error); return; }
                if (result.isDone() || closed || !plugin.isEnabled()) {
                    releaseLoad(); result.cancel(false); return;
                }
                try {
                    plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                        try {
                            if (!result.isDone()) result.complete(chunk.isSlimeChunk());
                        }
                        catch (RuntimeException failure) { result.completeExceptionally(failure); }
                        finally { releaseLoad(); }
                    });
                } catch (RuntimeException failure) { releaseLoad(); result.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { releaseLoad(); result.completeExceptionally(failure); }
        return result;
    }

    public CompletableFuture<Map<ChunkPos, Boolean>> scan(World world, int minX, int minZ, int width, int height) {
        if (width < 1 || height < 1 || width > 10 || height > 10) throw new IllegalArgumentException("Scan limit: 10x10");
        Map<ChunkPos, Boolean> values = new ConcurrentHashMap<>();
        var work = new CompletableFuture<?>[width * height];
        int index = 0;
        for (int x = minX; x < minX + width; x++) for (int z = minZ; z < minZ + height; z++) {
            var pos = new ChunkPos(x, z);
            work[index++] = isSlimeChunk(world, x, z).thenAccept(slime -> values.put(pos, slime));
        }
        return CompletableFuture.allOf(work).thenApply(ignored -> Map.copyOf(values));
    }

    @Override public void close() {
        closed = true;
        invalidate();
        pending.forEach(future -> future.cancel(false));
        pending.clear();
        if (loading.availablePermits() == 1024) drained.complete(null);
    }

    public CompletableFuture<Void> drain() {
        close();
        return drained;
    }

    private void releaseLoad() {
        loading.release();
        if (closed && loading.availablePermits() == 1024) drained.complete(null);
    }

    public record ChunkPos(int x, int z) {}
    private record QueryKey(java.util.UUID world, int x, int z) {}
    private record CachedQuery(CompletableFuture<Boolean> result, long created) {}
}
