package th.in.midnight_network.greenchunk.lifecycle;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Drains before named PlugManX operations, while this plugin can still schedule owner work. */
public final class HotReloadSupport implements Listener {
    private static final Set<String> OPERATIONS = Set.of("unload", "reload", "disable", "restart");
    private final JavaPlugin plugin;
    private final Supplier<CompletableFuture<Void>> prepare;
    private final AtomicBoolean started = new AtomicBoolean();

    public HotReloadSupport(JavaPlugin plugin, Supplier<CompletableFuture<Void>> prepare) {
        this.plugin = plugin;
        this.prepare = prepare;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (intercept(event.getPlayer(), event.getMessage())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (intercept(event.getSender(), event.getCommand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRemoteCommand(RemoteServerCommandEvent event) {
        if (intercept(event.getSender(), event.getCommand())) event.setCancelled(true);
    }

    private boolean intercept(CommandSender sender, String line) {
        line = line.strip();
        if (line.startsWith("/")) line = line.substring(1);
        String[] words = line.split("\\s+");
        if (words.length < 3 || !OPERATIONS.contains(words[1].toLowerCase(Locale.ROOT))) return false;
        Command command = plugin.getServer().getCommandMap().getCommand(words[0]);
        if (!(command instanceof PluginIdentifiableCommand owned)
                || !owned.getPlugin().getName().equalsIgnoreCase("PlugManX")) return false;
        String operation = words[1].toLowerCase(Locale.ROOT);
        if (!command.testPermissionSilent(sender) || !sender.hasPermission("plugman." + operation)) return false;
        if (words[2].equalsIgnoreCase("all") || words[2].equals("*")) {
            tell(sender, "为保证 GreenChunk 清理完成，请单独执行 /plugman " + operation + " GreenChunk。");
            return true;
        }
        if (!words[2].equalsIgnoreCase(plugin.getName())) return false;
        // PlugMan's supported named syntax; don't drain for malformed/flagged requests.
        if (words.length != 3) {
            tell(sender, "请使用 /plugman " + operation + " GreenChunk，不附加其他参数。");
            return true;
        }
        if (!started.compareAndSet(false, true)) {
            tell(sender, "GreenChunk 正在清理或清理已失败，请查看控制台；不要重复卸载。");
            return true;
        }
        tell(sender, "正在清理 GreenChunk 的显示与查询，完成后自动继续。");
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            CompletableFuture<Void> ready;
            try { ready = prepare.get(); }
            catch (RuntimeException error) { ready = CompletableFuture.failedFuture(error); }
            ready.orTimeout(20, java.util.concurrent.TimeUnit.SECONDS).whenComplete((ignored, error) -> {
                if (!plugin.isEnabled()) return;
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "热卸载清理失败，已拒绝继续。GreenChunk 保持停止服务，请完整重启服务器。", error);
                        tell(sender, "GreenChunk 清理失败，未执行卸载。请查看控制台并完整重启服务器。");
                        return;
                    }
                    // Execute exactly once as the original sender, retaining PlugMan's permission checks.
                    // Calling the resolved command avoids recursively re-entering command events.
                    plugin.getLogger().info("热卸载清理完成，继续 PlugManX " + operation + " GreenChunk。");
                    command.execute(sender, words[0], Arrays.copyOfRange(words, 1, words.length));
                    if (plugin.isEnabled() && org.bukkit.event.HandlerList.getRegisteredListeners(plugin)
                            .stream().anyMatch(listener -> listener.getListener() == this)) {
                        plugin.getLogger().severe("PlugManX 未停用 GreenChunk；清理后的插件已停止服务，请完整重启服务器。");
                        tell(sender, "PlugManX 未完成停用，GreenChunk 已停止服务。请完整重启服务器。");
                    }
                });
            });
        });
        return true;
    }

    private void tell(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            onOwner(plugin, player, () -> player.sendMessage(message));
        } else sender.sendMessage(message);
    }

    /** Retirement means this entity can no longer receive owner work; never touch it there. */
    public static CompletableFuture<Void> onOwner(Plugin plugin, Entity entity, Runnable action) {
        var result = new CompletableFuture<Void>();
        try {
            var task = entity.getScheduler().run(plugin, ignored -> {
                try { action.run(); result.complete(null); }
                catch (RuntimeException error) { result.completeExceptionally(error); }
            }, () -> result.complete(null));
            if (task == null) result.complete(null);
        } catch (RuntimeException error) { result.completeExceptionally(error); }
        return result;
    }
}
