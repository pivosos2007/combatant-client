/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.stability;

import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureRegistry.Failure;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.PacketEvent;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Singleplayer stability sweep: toggles every module in turn while the world keeps ticking and rendering,
 * and records isolated failures, logged errors and JVM crashes per module.
 *
 * <p>Progress is appended (synced) to {@code combatant/stability/progress.tsv} before each module is
 * touched, so when a module takes the JVM down the next run attributes the crash to it and continues.
 * Creating {@code combatant/stability/autorun} starts the sweep on world join and quits the game when it
 * stops, which lets an external loop relaunch until every module has a verdict.</p>
 */
public final class ModuleStabilityRun {
    public static final int DEFAULT_HOLD_TICKS = 40;
    private static final int SETTLE_TICKS = 3;
    private static final int AUTORUN_DELAY_TICKS = 100;
    private static final int MAX_CAPTURED_ERRORS = 3;
    private static final String RUNNING = "RUNNING";

    private enum Phase { IDLE, APPLY, HOLD, RESTORE, SETTLE }

    private static boolean registered;
    private static Phase phase = Phase.IDLE;
    private static Deque<Module> queue = new ArrayDeque<>();
    private static volatile Module current;
    private static boolean currentInitiallyEnabled;
    private static int ticks;
    private static int holdTicks = DEFAULT_HOLD_TICKS;
    private static int autorunWait;
    private static long historyMark;
    private static final List<String> capturedErrors = new ArrayList<>();
    private static CapturingAppender appender;

    private ModuleStabilityRun() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("combatant").resolve("stability");
    }

    private static Path progressFile() {
        return directory().resolve("progress.tsv");
    }

    private static Path autorunFlag() {
        return directory().resolve("autorun");
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(ModuleStabilityRun::tick);
    }

    public static boolean running() {
        return phase != Phase.IDLE;
    }

    /** Starts (or resumes) the sweep; returns an error message when it cannot start. */
    public static String start(boolean fresh, int hold) {
        return start(fresh, hold, null);
    }

    /**
     * Starts the sweep, or only {@code onlyModule} when given (its verdict is appended to the same progress
     * file); returns an error message when it cannot start.
     */
    public static String start(boolean fresh, int hold, String onlyModule) {
        Minecraft mc = Minecraft.getInstance();
        if (running()) return "A stability run is already active.";
        if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer()) {
            return "Stability runs toggle every module, so they only run in a singleplayer world.";
        }
        holdTicks = Math.max(1, hold);
        try {
            Files.createDirectories(directory());
            if (onlyModule != null) {
                Module module = ModuleManager.get(onlyModule);
                if (module == null) return "Module not found: " + onlyModule;
                queue = new ArrayDeque<>(List.of(module));
            } else {
                if (fresh) Files.deleteIfExists(progressFile());
                Map<String, String> verdicts = readVerdicts();
                List<Module> pending = new ArrayList<>();
                for (Module module : ModuleManager.getModules()) {
                    String verdict = verdicts.get(module.name());
                    if (RUNNING.equals(verdict)) {
                        append(module, "CRASHED", "JVM exited while this module was being toggled");
                    } else if (verdict == null) {
                        pending.add(module);
                    }
                }
                queue = new ArrayDeque<>(pending);
            }
        } catch (IOException e) {
            return "Cannot write " + directory() + ": " + e.getMessage();
        }
        installAppender();
        Events.BUS.register(PacketValidator.INSTANCE);
        phase = Phase.APPLY;
        CommandOutput.send("Stability run: " + queue.size() + " modules, " + holdTicks + " ticks each. Progress: "
                + progressFile());
        return null;
    }

    public static void stop(String reason) {
        if (current != null) {
            restoreCurrent();
            append(current, "ABORTED", reason);
            current = null;
        }
        finish(reason);
    }

    private static void tick(Minecraft mc) {
        if (phase == Phase.IDLE) {
            tickAutorun(mc);
            return;
        }
        if (mc.player == null || mc.level == null) {
            if (current != null) append(current, "LEFT_WORLD", "world unloaded while the module was active");
            current = null;
            finish("left the world");
            return;
        }
        if (mc.player.isDeadOrDying()) mc.player.respawn();
        try {
            switch (phase) {
                case APPLY -> apply(mc);
                case HOLD -> {
                    if (++ticks >= holdTicks) {
                        restoreCurrent();
                        ticks = 0;
                        phase = Phase.RESTORE;
                    }
                }
                case RESTORE -> {
                    if (++ticks >= SETTLE_TICKS) {
                        verdict(mc);
                        ticks = 0;
                        phase = Phase.SETTLE;
                    }
                }
                case SETTLE -> phase = Phase.APPLY;
                default -> {
                }
            }
        } catch (Throwable t) {
            if (current != null) append(current, "HARNESS_ERROR", t.toString());
            current = null;
            phase = Phase.APPLY;
        }
    }

    private static void tickAutorun(Minecraft mc) {
        if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer() || !Files.exists(autorunFlag())) {
            autorunWait = 0;
            return;
        }
        if (++autorunWait < AUTORUN_DELAY_TICKS) return;
        autorunWait = 0;
        String only = null;
        try {
            only = Files.readString(autorunFlag(), StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
        }
        String error = start(false, DEFAULT_HOLD_TICKS, only == null || only.isEmpty() ? null : only);
        if (error != null) CommandOutput.error(error);
    }

    private static void apply(Minecraft mc) {
        Module next = queue.pollFirst();
        if (next == null) {
            finish("complete");
            return;
        }
        current = next;
        currentInitiallyEnabled = next.isEnabled();
        if (!currentInitiallyEnabled && !next.isAvailable()) {
            append(next, "SKIPPED", "unavailable: " + next.getAvailabilityReason());
            current = null;
            return;
        }
        append(next, RUNNING, "");
        synchronized (capturedErrors) {
            capturedErrors.clear();
        }
        historyMark = latestHistoryId();
        ticks = 0;
        phase = Phase.HOLD;
        next.setEnabled(!currentInitiallyEnabled);
        closeForeignScreen(mc);
    }

    private static void restoreCurrent() {
        if (current == null) return;
        if (current.isEnabled() != currentInitiallyEnabled) current.setEnabled(currentInitiallyEnabled);
        closeForeignScreen(Minecraft.getInstance());
    }

    private static void verdict(Minecraft mc) {
        Module module = current;
        current = null;
        if (module == null) return;
        Failure failure = ErrorHandler.failure(module);
        List<String> details = new ArrayList<>();
        if (failure != null) details.add("isolated " + failure.phase() + ": " + failure.summary());
        for (Failure other : ErrorHandler.history()) {
            if (other.id() <= historyMark || (failure != null && other.id() == failure.id())) continue;
            details.add("failure in " + other.component() + " " + other.phase() + ": " + other.summary());
        }
        synchronized (capturedErrors) {
            details.addAll(capturedErrors);
        }
        if (details.isEmpty()) {
            append(module, "OK", "");
        } else {
            append(module, failure != null ? "FAILED" : "ERRORS", String.join(" | ", details));
        }
    }

    private static void finish(String reason) {
        phase = Phase.IDLE;
        queue = new ArrayDeque<>();
        removeAppender();
        Events.BUS.unregister(PacketValidator.INSTANCE);
        Map<String, Integer> counts = new LinkedHashMap<>();
        try {
            writeReport(counts);
        } catch (IOException e) {
            CommandOutput.error("Stability report not written: " + e.getMessage());
        }
        CommandOutput.send("Stability run " + reason + ": " + counts + ". Report: " + directory().resolve("report.md"));
        if (Files.exists(autorunFlag())) {
            if ("complete".equals(reason)) {
                try {
                    Files.deleteIfExists(autorunFlag());
                } catch (IOException ignored) {
                }
            }
            Minecraft.getInstance().stop();
        }
    }

    private static void closeForeignScreen(Minecraft mc) {
        Screen screen = ClientScreen.current(mc);
        if (screen != null && !(screen instanceof ChatScreen)) ClientScreen.show(mc, null);
    }

    private static long latestHistoryId() {
        long max = 0L;
        for (Failure failure : ErrorHandler.history()) max = Math.max(max, failure.id());
        return max;
    }

    private static void append(Module module, String status, String detail) {
        String line = module.name() + '\t' + status + '\t' + detail.replace('\t', ' ').replace('\n', ' ') + '\n';
        try {
            Files.createDirectories(directory());
            Files.writeString(progressFile(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        } catch (IOException e) {
            CommandOutput.error("Stability progress not written: " + e.getMessage());
        }
    }

    /** Last status per module; a trailing RUNNING means the JVM died during that module. */
    private static Map<String, String> readVerdicts() throws IOException {
        Map<String, String> verdicts = new LinkedHashMap<>();
        if (!Files.exists(progressFile())) return verdicts;
        for (String line : Files.readAllLines(progressFile(), StandardCharsets.UTF_8)) {
            String[] parts = line.split("\t", 3);
            if (parts.length >= 2) verdicts.put(parts[0], parts[1]);
        }
        return verdicts;
    }

    private static void writeReport(Map<String, Integer> counts) throws IOException {
        Map<String, String[]> last = new LinkedHashMap<>();
        if (Files.exists(progressFile())) {
            for (String line : Files.readAllLines(progressFile(), StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 3);
                if (parts.length >= 2) last.put(parts[0], parts);
            }
        }
        Set<String> known = new HashSet<>();
        for (Module module : ModuleManager.getModules()) known.add(module.name());
        StringBuilder problems = new StringBuilder();
        for (String[] parts : last.values()) {
            counts.merge(parts[1], 1, Integer::sum);
            if (!"OK".equals(parts[1]) && !"SKIPPED".equals(parts[1])) {
                problems.append("| ").append(parts[0]).append(" | ").append(parts[1]).append(" | ")
                        .append(parts.length > 2 ? parts[2].replace("|", "\\|") : "").append(" |\n");
            }
        }
        int untested = 0;
        for (String name : known) if (!last.containsKey(name)) untested++;
        if (untested > 0) counts.put("UNTESTED", untested);
        StringBuilder report = new StringBuilder("# Module stability report\n\n");
        counts.forEach((status, count) -> report.append("- ").append(status).append(": ").append(count).append('\n'));
        report.append("\n| Module | Status | Detail |\n| --- | --- | --- |\n").append(problems);
        Files.writeString(directory().resolve("report.md"), report, StandardCharsets.UTF_8);
    }

    private static void installAppender() {
        if (appender != null) return;
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        appender = new CapturingAppender();
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        context.updateLoggers();
    }

    private static void removeAppender() {
        if (appender == null) return;
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getRootLogger().removeAppender(appender.getName());
        context.updateLoggers();
        appender.stop();
        appender = null;
    }

    private static void capture(String text) {
        synchronized (capturedErrors) {
            if (capturedErrors.size() < MAX_CAPTURED_ERRORS && !capturedErrors.contains(text)) capturedErrors.add(text);
        }
    }

    /**
     * Flags outgoing movement the vanilla server rejects ("Invalid move player packet received": NaN or
     * infinite position/rotation, or a coordinate beyond the world limit) and records the code that sent it.
     */
    public static final class PacketValidator {
        static final PacketValidator INSTANCE = new PacketValidator();
        private static final double MAX_HORIZONTAL = 3.0E7;
        private static final double MAX_VERTICAL = 2.0E7;

        private PacketValidator() {
        }

        @EventHandler
        public void onSent(PacketEvent.SendPost event) {
            if (current == null || !(event.getPacket() instanceof ServerboundMovePlayerPacket move)) return;
            String problem = invalidMove(move);
            if (problem != null) capture("invalid move packet (" + problem + ") from " + combatantCallers());
        }

        static String invalidMove(ServerboundMovePlayerPacket move) {
            if (move.hasPosition()) {
                double x = move.getX(0.0), y = move.getY(0.0), z = move.getZ(0.0);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    return "position " + x + ", " + y + ", " + z;
                }
                if (Math.abs(x) > MAX_HORIZONTAL || Math.abs(z) > MAX_HORIZONTAL || Math.abs(y) > MAX_VERTICAL) {
                    return "position out of range " + x + ", " + y + ", " + z;
                }
            }
            if (move.hasRotation()) {
                float yaw = move.getYRot(0.0f), pitch = move.getXRot(0.0f);
                if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return "rotation " + yaw + ", " + pitch;
            }
            return null;
        }

        private static String combatantCallers() {
            List<String> frames = StackWalker.getInstance().walk(stream -> stream
                    .filter(frame -> frame.getClassName().startsWith("combatant.")
                            && !frame.getClassName().startsWith(ModuleStabilityRun.class.getName())
                            && !frame.getClassName().startsWith("combatant.client.events."))
                    .limit(6)
                    .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                            + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                    .toList());
            return frames.isEmpty() ? "vanilla code (position/rotation set earlier)" : String.join(" < ", frames);
        }
    }

    /** Records errors and exceptions logged while a module is under test. */
    private static final class CapturingAppender extends AbstractAppender {
        private CapturingAppender() {
            super("CombatantStabilityCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (current == null) return;
            Throwable thrown = event.getThrown();
            if (thrown == null && !event.getLevel().isMoreSpecificThan(Level.ERROR)) return;
            String text = event.getLoggerName() + ": " + event.getMessage().getFormattedMessage()
                    + (thrown != null ? " [" + thrown + "]" : "");
            synchronized (capturedErrors) {
                if (capturedErrors.size() < MAX_CAPTURED_ERRORS) capturedErrors.add("log " + text);
            }
        }
    }
}
