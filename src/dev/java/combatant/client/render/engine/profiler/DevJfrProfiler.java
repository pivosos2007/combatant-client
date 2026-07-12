package combatant.client.render.engine.profiler;

import jdk.jfr.EventSettings;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public enum DevJfrProfiler {
    ;

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static Recording recording;
    private static Preset activePreset;
    private static Instant startedAt;
    private static Path outputPath;

    public static synchronized StartResult start(Preset preset) {
        if (recording != null) {
            return StartResult.alreadyRunning(activePreset, outputPath, startedAt);
        }
        try {
            Path dir = ensureOutputDirectory();
            Path file = dir.resolve(defaultFileName(preset, false));

            Recording next = new Recording();
            next.setName("Combatant " + preset.getId());
            next.setToDisk(true);
            next.setDumpOnExit(false);
            configure(next, preset);
            next.start();

            recording = next;
            activePreset = preset;
            startedAt = Instant.now();
            outputPath = file;

            ProfilerLog.info("JFR started (%s) -> %s", preset.getId(), file.toAbsolutePath());
            return StartResult.started(preset, file, startedAt);
        } catch (Throwable t) {
            ProfilerLog.warn("JFR unavailable: %s", t.getClass().getSimpleName() + ": " + t.getMessage());
            return StartResult.failed(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static synchronized DumpResult dumpSnapshot() {
        if (recording == null) {
            return DumpResult.notRunningResult();
        }
        try {
            Path dir = ensureOutputDirectory();
            Path snapshot = dir.resolve(defaultFileName(activePreset, true));
            recording.dump(snapshot);
            ProfilerLog.info("JFR snapshot dumped -> %s", snapshot.toAbsolutePath());
            return DumpResult.dumped(snapshot);
        } catch (Throwable t) {
            ProfilerLog.warn("JFR snapshot failed: %s", t.getClass().getSimpleName() + ": " + t.getMessage());
            return DumpResult.failed(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static synchronized StopResult stop() {
        if (recording == null) {
            return StopResult.notRunningResult();
        }
        Recording current = recording;
        Preset preset = activePreset;
        Instant started = startedAt;
        Path destination = outputPath;
        recording = null;
        activePreset = null;
        startedAt = null;
        outputPath = null;
        try {
            current.stop();
            Files.createDirectories(destination.getParent());
            current.dump(destination);
            current.close();
            ProfilerLog.info("JFR stopped (%s) -> %s", preset.getId(), destination.toAbsolutePath());
            return StopResult.stopped(preset, destination, started);
        } catch (Throwable t) {
            try {
                current.close();
            } catch (Throwable ignored) {
            }
            ProfilerLog.warn("JFR stop failed: %s", t.getClass().getSimpleName() + ": " + t.getMessage());
            return StopResult.failed(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static synchronized String statusLine() {
        if (recording == null) {
            return "Profiler JFR: off";
        }
        long seconds = Math.max(0L, Duration.between(startedAt, Instant.now()).toSeconds());
        return "Profiler JFR: on (" + activePreset.getId()
                + ", " + formatDuration(seconds)
                + ", file " + outputPath.toAbsolutePath() + ")";
    }

    private static void configure(Recording recording, Preset preset) {
        enable(recording, "jdk.GarbageCollection", true, null, null);
        enable(recording, "jdk.GCHeapSummary", false, null, null);
        enable(recording, "jdk.MetaspaceSummary", false, null, null);
        enable(recording, "jdk.CPULoad", false, Duration.ofSeconds(1), null);
        enable(recording, "jdk.JavaThreadStatistics", false, Duration.ofSeconds(1), null);
        if (preset.capturesAllocations()) {
            enable(recording, "jdk.ObjectAllocationSample", true, null, null);
        }
        if (preset == Preset.FULL) {
            enable(recording, "jdk.ExecutionSample", true, Duration.ofMillis(20), null);
            enable(recording, "jdk.NativeMethodSample", true, Duration.ofMillis(20), null);
            enable(recording, "jdk.ThreadPark", true, null, Duration.ofMillis(1));
            enable(recording, "jdk.JavaMonitorEnter", true, null, Duration.ofMillis(1));
        }
    }

    private static void enable(
            Recording recording,
            String eventName,
            boolean stackTrace,
            Duration period,
            Duration threshold
    ) {
        try {
            EventSettings settings = recording.enable(eventName);
            settings = stackTrace ? settings.withStackTrace() : settings.withoutStackTrace();
            if (period != null) {
                settings = settings.withPeriod(period);
            }
            if (threshold != null) {
                settings = settings.withThreshold(threshold);
            }
        } catch (IllegalArgumentException ignored) {
            // Some runtimes ship a slightly different event set.
        }
    }

    private static Path ensureOutputDirectory() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Path dir;
        if (mc != null && mc.gameDirectory != null) {
            dir = mc.gameDirectory.toPath().resolve("profile").resolve("jfr");
        } else {
            dir = Path.of("profile", "jfr");
        }
        Files.createDirectories(dir);
        return dir;
    }

    private static String defaultFileName(Preset preset, boolean snapshot) {
        String timestamp = FILE_TIME.format(LocalDateTime.now(ZoneId.systemDefault()));
        String suffix = snapshot ? "-snapshot" : "";
        return "combatant-" + preset.getId() + suffix + "-" + timestamp + ".jfr";
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        return minutes + "m " + seconds + "s";
    }

    public enum Preset {
        GC("gc"),
        ALLOC("alloc"),
        FULL("full");

        private final String id;

        Preset(String id) {
            this.id = id;
        }

        public static Preset parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return FULL;
            }
            return switch (raw.toLowerCase()) {
                case "gc" -> GC;
                case "alloc", "allocation", "allocs" -> ALLOC;
                case "full", "all", "profile" -> FULL;
                default -> null;
            };
        }

        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        public boolean capturesAllocations() {
            return this == ALLOC || this == FULL;
        }
    }

    public record StartResult(boolean started, boolean alreadyRunning, Preset preset, Path path, Instant startedAt,
                              String error) {
        private static StartResult started(Preset preset, Path path, Instant startedAt) {
            return new StartResult(true, false, preset, path, startedAt, null);
        }

        private static StartResult alreadyRunning(Preset preset, Path path, Instant startedAt) {
            return new StartResult(false, true, preset, path, startedAt, null);
        }

        private static StartResult failed(String error) {
            return new StartResult(false, false, null, null, null, error);
        }
    }

    public record DumpResult(boolean dumped, boolean notRunning, Path path, String error) {
        private static DumpResult dumped(Path path) {
            return new DumpResult(true, false, path, null);
        }

        private static DumpResult notRunningResult() {
            return new DumpResult(false, true, null, null);
        }

        private static DumpResult failed(String error) {
            return new DumpResult(false, false, null, error);
        }
    }

    public record StopResult(boolean stopped, boolean notRunning, Preset preset, Path path, Instant startedAt,
                             String error) {
        private static StopResult stopped(Preset preset, Path path, Instant startedAt) {
            return new StopResult(true, false, preset, path, startedAt, null);
        }

        private static StopResult notRunningResult() {
            return new StopResult(false, true, null, null, null, null);
        }

        private static StopResult failed(String error) {
            return new StopResult(false, false, null, null, null, error);
        }
    }
}
