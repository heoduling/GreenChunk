package th.in.midnight_network.greenchunk.gui;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.dialog.Dialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/** Exact Shiroha 26.2 bridge: Bukkit exposes no current-dialog identity.
 * Reads player handles only on their owner; compares/writes packets only on the channel event loop.
 */
public final class DialogSessions {
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Method getHandle;
    private final Field playerConnection;
    private final Field networkConnection;
    private final Field channel;
    private final Method dialogHolder;
    private final Method packetDialog;
    private final Object clearPacket;
    private final Method runOnceConnected;

    public DialogSessions() {
        try {
            getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
            playerConnection = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            networkConnection = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl").getField("connection");
            channel = Class.forName("net.minecraft.network.Connection").getField("channel");
            runOnceConnected = Class.forName("net.minecraft.network.Connection")
                    .getMethod("runOnceConnected", java.util.function.Consumer.class);
            dialogHolder = Class.forName("io.papermc.paper.dialog.PaperDialog").getMethod("bukkitToMinecraftHolder", Dialog.class);
            packetDialog = Class.forName("net.minecraft.network.protocol.common.ClientboundShowDialogPacket").getMethod("dialog");
            clearPacket = Class.forName("net.minecraft.network.protocol.common.ClientboundClearDialogPacket").getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("当前核心不支持 GreenChunk 面板归属跟踪", error);
        }
    }

    /** Must run on the player's owner, before showDialog enqueues its packet. */
    public void track(Player player, Dialog dialog) {
        try {
            Object network = networkConnection.get(playerConnection.get(getHandle.invoke(player)));
            Channel connection = (Channel) channel.get(network);
            UUID id = player.getUniqueId();
            Session session = sessions.computeIfAbsent(id, ignored -> {
                var created = new Session(network, connection, new Tracker(packetDialog, clearPacket));
                created.tracker.onDisconnect = () -> sessions.remove(id, created);
                return created;
            });
            session.tracker.expected = dialogHolder.invoke(null, dialog);
            connection.eventLoop().execute(() -> {
                if (connection.isActive() && connection.pipeline().context(session.tracker) == null) {
                    connection.pipeline().addBefore("packet_handler", "greenchunk-dialog", session.tracker);
                } else if (!connection.isActive()) sessions.remove(id, session);
            });
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("无法跟踪 GreenChunk 面板", error);
        }
    }

    /** After player owner barriers, also drain the core's native pending-packet queue. */
    public CompletableFuture<Void> closeForUnload() {
        var work = List.copyOf(sessions.values()).stream().map(session -> finish(session, true))
                .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(work).thenRun(sessions::clear);
    }

    /** Ordinary stop / failed enable also detach our network handlers. */
    public void detach() {
        List.copyOf(sessions.values()).forEach(session -> finish(session, false));
        sessions.clear();
    }

    private CompletableFuture<Void> finish(Session session, boolean close) {
        var done = session.tracker.finished;
        Channel connection = session.connection;
        try {
            Runnable cleanup = () -> {
                try {
                    boolean ours = session.tracker.ours;
                    if (connection.pipeline().context(session.tracker) != null) connection.pipeline().remove(session.tracker);
                    if (close && ours && connection.isActive()) {
                        connection.writeAndFlush(clearPacket).addListener(write -> {
                            if (write.isSuccess()) done.complete(null);
                            else done.completeExceptionally(write.cause());
                        });
                    } else done.complete(null);
                } catch (RuntimeException error) { done.completeExceptionally(error); }
            };
            if (close && connection.isActive()) {
                // Unlike directly queuing an event-loop task, this cannot overtake a show
                // still in Shiroha's pendingActions queue. Its consumer runs on the event loop.
                runOnceConnected.invoke(session.network, (java.util.function.Consumer<Object>) ignored -> cleanup.run());
            } else connection.eventLoop().execute(cleanup);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!connection.isOpen()) done.complete(null);
            else done.completeExceptionally(error);
        }
        return done;
    }

    private record Session(Object network, Channel connection, Tracker tracker) {}

    static final class Tracker extends ChannelDuplexHandler {
        private final Method packetDialog;
        private final Object clearPacket;
        volatile Object expected;
        boolean ours; // Channel event loop only.
        Runnable onDisconnect = () -> {};
        final CompletableFuture<Void> finished = new CompletableFuture<>();

        Tracker(Method packetDialog, Object clearPacket) {
            this.packetDialog = packetDialog;
            this.clearPacket = clearPacket;
        }

        @Override public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
            if (packetDialog.getDeclaringClass().isInstance(packet)) {
                ours = packetDialog.invoke(packet) == expected;
            } else if (packet == clearPacket) ours = false;
            context.write(packet, promise);
        }

        @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
            onDisconnect.run();
            context.pipeline().remove(this);
            finished.complete(null);
            context.fireChannelInactive();
        }
    }
}
