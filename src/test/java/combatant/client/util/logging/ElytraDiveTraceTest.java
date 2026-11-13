package combatant.client.util.logging;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ElytraDiveTraceTest {
    @Test
    void deduplicatesSnapshotsButPreservesPacketOrderAndTickBoundaries() {
        List<String> output = new ArrayList<>();
        var trace = new ElytraDiveTrace(output::add);
        trace.sample(10, 4, "sync", "state1");
        trace.sample(10, 4, "sync", "duplicate");
        trace.event("held");
        trace.event("attack");
        trace.event("move");
        assertTrue(output.isEmpty());
        trace.advanceTick(11);
        trace.event("restore");
        trace.finish("done");
        String log = output.getFirst();
        assertFalse(log.contains("duplicate"));
        assertTrue(log.indexOf("held") < log.indexOf("attack"));
        assertTrue(log.indexOf("attack") < log.indexOf("move"));
        assertTrue(log.indexOf("tick=11") < log.indexOf("restore"));
        assertFalse(trace.active());
    }

    @Test
    void longAttemptWritesBoundedChunksWithoutLosingTicks() {
        List<String> output = new ArrayList<>();
        var trace = new ElytraDiveTrace(output::add);
        for (int tick = 0; tick < 205; tick++) trace.sample(tick, 4, "sync", "state");
        trace.finish("timeout");
        assertEquals(3, output.size());
        assertEquals(205, output.stream().flatMap(String::lines).filter(line -> line.startsWith("tick=")).count());
        assertTrue(output.getLast().contains("outcome=timeout"));
    }

    @Test
    void targetSwitchFlushesAndEventOverflowIsExplicit() {
        List<String> output = new ArrayList<>();
        var trace = new ElytraDiveTrace(output::add);
        trace.sample(1, 4, "sync", "state");
        for (int i = 0; i < 50; i++) trace.event("event" + i);
        trace.sample(2, 5, "sync", "state");
        trace.finish("done");
        assertEquals(2, output.size());
        assertTrue(output.getFirst().contains("outcome=target_changed"));
        assertTrue(output.getFirst().contains("omittedEvents=19"));
        assertTrue(output.getLast().contains("target=5"));
    }
}
