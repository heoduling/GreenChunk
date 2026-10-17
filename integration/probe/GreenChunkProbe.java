package integration;

import java.lang.reflect.Constructor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import java.util.Map;

/** Runs only in an isolated server; never included in the release plugin. */
public final class GreenChunkProbe extends JavaPlugin {
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            var target = Bukkit.getPluginManager().getPlugin("GreenChunk");
            var field = target.getClass().getDeclaredField("visualization");
            field.setAccessible(true);
            Object visual = field.get(target);
            for (String key : new String[]{"displays", "tasks", "states", "pending"}) {
                var state = visual.getClass().getDeclaredField(key);
                state.setAccessible(true);
                Object value = state.get(visual);
                getLogger().info("GC_STATE " + key + "=" + value);
            }
            if (sender instanceof Player player && args.length > 0) {
                if (args[0].equals("move")) player.teleportAsync(new Location(player.getWorld(), 4096.5, -59, 4096.5))
                        .thenAccept(ok -> getLogger().info("GC_RESULT cross-region-teleport=" + ok));
                if (args[0].equals("nether")) player.teleportAsync(new Location(Bukkit.getWorlds().get(1), 0.5, 80, 0.5))
                        .thenAccept(ok -> getLogger().info("GC_RESULT cross-world-teleport=" + ok));
                if (args[0].equals("legacy")) {
                    var attachment = player.addAttachment(this, "greenchunk.map.item", false);
                    getLogger().info("GC_RESULT legacy-denial=" + !player.hasPermission("greenchunk.item.map"));
                    player.removeAttachment(attachment);
                }
                if (args[0].equals("close")) player.closeDialog();
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        return true;
    }
    @Override public void onEnable() {
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> runChecks(), 40);
    }

    private void runChecks() {
        try {
            JavaPlugin target = (JavaPlugin) Bukkit.getPluginManager().getPlugin("GreenChunk");
            var field = target.getClass().getDeclaredField("configService");
            field.setAccessible(true);
            Object config = field.get(target);
            Object current = config.getClass().getMethod("current").invoke(config);
            Object item = current.getClass().getMethod("item").invoke(current);
            boolean enabled = (boolean) item.getClass().getMethod("enabled").invoke(item);
            getLogger().info("GC_RESULT production-item-disabled=" + !enabled);
            getLogger().info("GC_RESULT migrated-on-disk=" + target.getConfig().contains("item.enabled", true));

            Class<?> serviceClass = Class.forName("th.in.midnight_network.greenchunk.slime.SlimeChunkService", true,
                    target.getClass().getClassLoader());
            Object service;
            try {
                Constructor<?> ctor = serviceClass.getConstructor(Plugin.class);
                service = ctor.newInstance(target);
            } catch (NoSuchMethodException oldVersion) {
                service = serviceClass.getConstructor().newInstance();
            }
            Object checker = service;
            var method = serviceClass.getMethod("isSlimeChunk", World.class, int.class, int.class);
            World world = Bukkit.getWorlds().getFirst();
            AtomicInteger left = new AtomicInteger(25);
            AtomicInteger mismatches = new AtomicInteger();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                int cx = x, cz = z;
                world.getChunkAtAsync(cx, cz).thenAccept(chunk ->
                    Bukkit.getRegionScheduler().execute(this, world, cx, cz, () -> {
                        try {
                            boolean expected = chunk.isSlimeChunk();
                            Object result = method.invoke(checker, world, cx, cz);
                            CompletableFuture<?> future = result instanceof CompletableFuture<?> f
                                    ? f : CompletableFuture.completedFuture(result);
                            future.whenComplete((actual, error) -> {
                                if (error != null || !Boolean.valueOf(expected).equals(actual)) mismatches.incrementAndGet();
                                if (left.decrementAndGet() == 0)
                                    getLogger().info("GC_RESULT native-slime-parity=" + (mismatches.get() == 0)
                                            + " checked=25 mismatches=" + mismatches.get());
                            });
                        } catch (ReflectiveOperationException error) {
                            throw new IllegalStateException(error);
                        }
                    }));
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
