package th.in.midnight_network.greenchunk.lifecycle;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HotReloadSupportTest {
    private Consumer<ScheduledTask> callback;
    private Runnable retired;

    private Entity entity(String mode) {
        var scheduler = (EntityScheduler) Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),
                new Class<?>[]{EntityScheduler.class}, (proxy, method, args) -> {
                    if (mode.equals("disabled")) throw new IllegalPluginAccessException("disabled during submission");
                    if (mode.equals("retired")) return null;
                    @SuppressWarnings("unchecked") var action = (Consumer<ScheduledTask>) args[1];
                    callback = action;
                    retired = (Runnable) args[2];
                    return Proxy.newProxyInstance(ScheduledTask.class.getClassLoader(), new Class<?>[]{ScheduledTask.class},
                            (task, call, input) -> null);
                });
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getScheduler")) return scheduler;
                    throw new AssertionError("Entity touched outside owner: " + method);
                });
    }

    @Test void executesOnlyWhenOwnerCallbackRuns() {
        var ran = new AtomicBoolean();
        var result = HotReloadSupport.onOwner(null, entity("normal"), () -> ran.set(true));
        assertFalse(result.isDone()); assertFalse(ran.get());
        callback.accept(null);
        assertTrue(result.isDone()); assertFalse(result.isCompletedExceptionally()); assertTrue(ran.get());
    }

    @Test void alreadyRetiredTerminatesWithoutTouchingEntity() {
        var result = HotReloadSupport.onOwner(null, entity("retired"), () -> fail("retired entity accessed"));
        assertTrue(result.isDone()); assertFalse(result.isCompletedExceptionally());
    }

    @Test void retirementAfterSchedulingTerminatesWithoutRunningAction() {
        var result = HotReloadSupport.onOwner(null, entity("normal"), () -> fail("retired entity accessed"));
        retired.run();
        assertTrue(result.isDone()); assertFalse(result.isCompletedExceptionally());
    }

    @Test void disableDuringSubmissionIsFailureNotSuccessfulCleanup() {
        var result = HotReloadSupport.onOwner(null, entity("disabled"), () -> fail("disabled entity accessed"));
        assertTrue(result.isCompletedExceptionally());
    }

    @Test void ownerActionFailureDoesNotApproveUnload() {
        var result = HotReloadSupport.onOwner(null, entity("normal"), () -> { throw new IllegalStateException("remove failed"); });
        callback.accept(null);
        assertTrue(result.isCompletedExceptionally());
    }
}
