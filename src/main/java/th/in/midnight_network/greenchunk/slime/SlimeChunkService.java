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
public final class SlimeChunkService implements AutoCloseable {
    private final Plugin plugin;
    private final Set<CompletableFuture<Boolean>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    // A caller timeout does not cancel terrain generation in the server.
    private final Semaphore loading = new Semaphore(1024);

    public SlimeChunkService(Plugin plugin) { this.plugin = plugin; }

    public CompletableFuture<Boolean> isSlimeChunk(World world, int chunkX, int chunkZ) {
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
        // Loading is asynchronous. The boolean is read only on the chunk's owner.
        try {
            world.getChunkAtAsync(chunkX, chunkZ).whenComplete((chunk, error) -> {
                if (error != null) { loading.release(); result.completeExceptionally(error); return; }
                if (result.isDone() || closed || !plugin.isEnabled()) {
                    loading.release(); result.cancel(false); return;
                }
                try {
                    plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                        try {
                            if (!result.isDone()) result.complete(chunk.isSlimeChunk());
                        }
                        catch (RuntimeException failure) { result.completeExceptionally(failure); }
                        finally { loading.release(); }
                    });
                } catch (RuntimeException failure) { loading.release(); result.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { loading.release(); result.completeExceptionally(failure); }
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
        pending.forEach(future -> future.cancel(false));
        pending.clear();
    }

    public record ChunkPos(int x, int z) {}
}
