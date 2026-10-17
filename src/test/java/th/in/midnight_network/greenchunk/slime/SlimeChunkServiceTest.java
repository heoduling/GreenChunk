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
    @Test void callerTimeoutDoesNotReleaseUnfinishedBackendCapacity() {
        List<CompletableFuture<Chunk>> backend = new ArrayList<>();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("getChunkAtAsync")) throw new AssertionError(method);
                    var future = new CompletableFuture<Chunk>();
                    backend.add(future);
                    return future;
                });
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
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
