package combatant.client.util.logging;

import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Client-thread trace, bounded to 100 ticks per chunk and two 4 MiB files. */
public final class ElytraDiveTrace {
    public static final ElytraDiveTrace INSTANCE = new ElytraDiveTrace(ElytraDiveTrace::write);
    private final Consumer<String> sink;
    private final List<String> rows = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final Set<String> sources = new LinkedHashSet<>();
    private int target = -1;
    private int tick = -1;
    private int omitted;
    private long started;
    private long tickStarted;
    private long session;
    private boolean active;
    private static boolean writeFailed;

    ElytraDiveTrace(Consumer<String> sink) { this.sink = sink; }

    public boolean active() { return active; }

    public void sample(int age, int targetId, String source, String state) {
        if (active && target != targetId) finish("target_changed");
        if (!active) {
            active = true;
            target = targetId;
            started = System.nanoTime();
            session++;
        }
        advanceTick(age);
        if (sources.add(source)) event(source + " " + state);
    }

    public void advanceTick(int age) {
        if (!active || age == tick) return;
        commitTick();
        if (rows.size() >= 100) flush("continuation");
        tick = age;
        tickStarted = System.nanoTime();
    }

    public void event(String value) {
        if (!active) return;
        if (events.size() < 32) {
            events.add(String.format(Locale.ROOT, "+%.3fms %s", (System.nanoTime() - tickStarted) / 1e6, value));
        } else omitted++;
    }

    public void finish(String outcome) {
        if (!active) return;
        commitTick();
        flush(outcome);
        active = false;
        target = -1;
        tick = -1;
    }

    private void commitTick() {
        if (tick < 0) return;
        rows.add("tick=" + tick + " " + String.join(" | ", events)
                + (omitted > 0 ? " | omittedEvents=" + omitted : ""));
        events.clear();
        sources.clear();
        omitted = 0;
    }

    private void flush(String outcome) {
        sink.accept("time=" + Instant.now() + " session=" + session + " target=" + target
                + " outcome=" + outcome + " elapsedMs=" + (System.nanoTime() - started) / 1_000_000
                + "\n" + String.join("\n", rows) + "\n\n");
        rows.clear();
    }

    private static void write(String block) {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/combatant-elytra-dive.log");
            Files.createDirectories(file.getParent());
            byte[] data = block.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (Files.exists(file) && Files.size(file) + data.length > 4L * 1024 * 1024) {
                Files.move(file, file.resolveSibling("combatant-elytra-dive.previous.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            if (!writeFailed) {
                writeFailed = true;
                DebugLog.error("Cannot write elytra dive trace", failure);
            }
        }
    }
}
