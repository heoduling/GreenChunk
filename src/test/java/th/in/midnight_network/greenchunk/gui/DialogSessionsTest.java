package th.in.midnight_network.greenchunk.gui;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DialogSessionsTest {
    public record Show(Object dialog) {}

    @Test void comparesIdentityAndTracksReplacementAndClearInWireOrder() throws Exception {
        Object own = new String("same title"), foreign = new String("same title");
        Object clear = new Object();
        var tracker = new DialogSessions.Tracker(Show.class.getMethod("dialog"), clear);
        tracker.expected = own;
        var channel = new EmbeddedChannel(tracker);
        channel.writeOutbound(new Show(own));
        assertTrue(tracker.ours);
        channel.writeOutbound(new Show(foreign));
        assertFalse(tracker.ours, "Another plugin's equal-looking dialog must survive unload");
        channel.writeOutbound(new Show(own));
        assertTrue(tracker.ours);
        channel.writeOutbound(clear);
        assertFalse(tracker.ours);
        channel.finishAndReleaseAll();
    }

    @Test void disconnectReleasesSessionAndPipelineHandler() throws Exception {
        var tracker = new DialogSessions.Tracker(Show.class.getMethod("dialog"), new Object());
        var removed = new java.util.concurrent.atomic.AtomicBoolean();
        tracker.onDisconnect = () -> removed.set(true);
        var channel = new EmbeddedChannel(tracker);
        channel.close();
        assertTrue(removed.get());
        assertNull(channel.pipeline().context(tracker));
        channel.finishAndReleaseAll();
    }
}
