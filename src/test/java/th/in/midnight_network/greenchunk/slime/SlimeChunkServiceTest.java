package th.in.midnight_network.greenchunk.slime;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SlimeChunkServiceTest {
    @Test void sharesBackendButNotCancellationAndInvalidationStartsFresh() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var backend = new CompletableFuture<Chunk>();
        var chunk = (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[]{Chunk.class},
                (proxy, method, args) -> method.getName().equals("isSlimeChunk") ? true : null);
        var scheduler = (io.papermc.paper.threadedregions.scheduler.RegionScheduler) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(), new Class<?>[]{io.papermc.paper.threadedregions.scheduler.RegionScheduler.class},
                (proxy, method, args) -> { ((Runnable) args[4]).run(); return null; });
        var server = (org.bukkit.Server) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{org.bukkit.Server.class},
                (proxy, method, args) -> method.getName().equals("getRegionScheduler") ? scheduler : null);
        var plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> method.getName().equals("isEnabled") ? true : server);
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUID")) return new java.util.UUID(0, 1);
                    if (method.getName().equals("getChunkAtAsync")) { calls.incrementAndGet(); return backend; }
                    throw new AssertionError(method);
                });
        try (var service = new SlimeChunkService(plugin)) {
            var first = service.isSlimeChunk(world, 1, 2);
            var second = service.isSlimeChunk(world, 1, 2);
            first.cancel(false);
            assertEquals(1, calls.get());
            backend.complete(chunk);
            assertTrue(second.join());
            assertTrue(service.isSlimeChunk(world, 1, 2).join());
            assertEquals(1, calls.get(), "Successful result must be reused");
            service.invalidate();
            assertTrue(service.isSlimeChunk(world, 1, 2).join());
            assertEquals(2, calls.get(), "Reload invalidates cached result");
            for (int x = 0; x < 8300; x++) assertTrue(service.isSlimeChunk(world, x, 9).join());
            var cacheField = SlimeChunkService.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            assertEquals(8192, ((java.util.Map<?, ?>) cacheField.get(service)).size(), "Cache must stay bounded");
            int before = calls.get();
            assertTrue(service.isSlimeChunk(world, 1, 2).join());
            assertEquals(before + 1, calls.get(), "Evicted entry must query core again");
            service.onWorldUnload(new org.bukkit.event.world.WorldUnloadEvent(world));
            assertTrue(((java.util.Map<?, ?>) cacheField.get(service)).isEmpty(), "World unload discards cached results");
        }
    }

    @Test void failedSharedQueryIsRetryableAndWorldsDoNotShareEntries() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var id = new java.util.concurrent.atomic.AtomicReference<>(new java.util.UUID(0, 1));
        var backends = new ArrayList<CompletableFuture<Chunk>>();
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUID")) return id.get();
                    if (method.getName().equals("getChunkAtAsync")) {
                        calls.incrementAndGet(); var b = new CompletableFuture<Chunk>(); backends.add(b); return b;
                    }
                    throw new AssertionError(method);
                });
        var plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> method.getName().equals("isEnabled") ? true : null);
        try (var service = new SlimeChunkService(plugin)) {
            var first = service.isSlimeChunk(world, 0, 0);
            backends.getFirst().completeExceptionally(new IllegalStateException("load failed"));
            assertTrue(first.isCompletedExceptionally());
            service.isSlimeChunk(world, 0, 0);
            id.set(new java.util.UUID(0, 2));
            service.isSlimeChunk(world, 0, 0);
            assertEquals(3, calls.get());
        }
    }
    @Test void unloadWaitsForBackendEvenAfterCallerWasCancelled() {
        var backend = new CompletableFuture<Chunk>();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUID")) return new java.util.UUID(0, 1);
                    if (method.getName().equals("getChunkAtAsync")) return backend;
                    throw new AssertionError(method);
                });
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> method.getName().equals("isEnabled") ? true : null);
        var service = new SlimeChunkService(plugin);
        var caller = service.isSlimeChunk(world, 0, 0);
        var drained = service.drain();
        assertTrue(caller.isCancelled());
        assertFalse(drained.isDone(), "Cancelled caller does not imply backend completed");
        backend.completeExceptionally(new IllegalStateException("cancelled world load"));
        assertTrue(drained.isDone());
        assertFalse(drained.isCompletedExceptionally());
        assertTrue(service.isSlimeChunk(world, 1, 0).isCancelled());
    }

    @Test void callerTimeoutDoesNotReleaseUnfinishedBackendCapacity() {
        List<CompletableFuture<Chunk>> backend = new ArrayList<>();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUID")) return new java.util.UUID(0, 1);
                    if (!method.getName().equals("getChunkAtAsync")) throw new AssertionError(method);
                    var future = new CompletableFuture<Chunk>();
                    backend.add(future);
                    return future;
                });
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUID")) return new java.util.UUID(0, 1);
                    if (method.getName().equals("isEnabled")) return true;
                    throw new AssertionError(method);
                });
        try (var service = new SlimeChunkService(plugin)) {
            for (int x = 0; x < 1100; x++) {
                // Same terminal state as orTimeout; backend stays pending.
                service.isSlimeChunk(world, x, 0).completeExceptionally(new TimeoutException());
            }
            assertEquals(1024, backend.size(), "Timed-out callers must not bypass the backend limit");
            backend.getFirst().completeExceptionally(new IllegalStateException("load failed"));
            service.isSlimeChunk(world, 2000, 0);
            assertEquals(1025, backend.size(), "A completed backend request must release its slot");
        }
    }
}
