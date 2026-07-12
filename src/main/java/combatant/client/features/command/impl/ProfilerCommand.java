/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandOutput;
import combatant.client.render.engine.profiler.FrameStutterProfiler;
import combatant.client.render.engine.profiler.JfrProfiler;
import combatant.client.render.engine.profiler.ProfilerSettings;
import combatant.client.render.engine.profiler.TracyProfiler;
import combatant.client.runtime.RuntimeGate;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ProfilerCommand implements ClientCommand {

    @Override
    public String name() {
        return "profiler";
    }

    @Override
    public List<String> aliases() {
        return List.of("prof");
    }

    @Override
    public String usage() {
        return "@profiler [2d|3d|all|sample|stutter|tracy|jfr] ...";
    }

    @Override
    public boolean isAvailable() {
        return RuntimeGate.canRunClientLogic();
    }

    @Override
    public boolean execute(CommandContext ctx) {
        if (!RuntimeGate.canRunClientLogic()) {
            CommandOutput.send("Profiler: disabled in Panic mode.");
            return true;
        }
        String target = ctx.arg(0);
        if (target == null
                || !(target.equalsIgnoreCase("2d")
                || target.equalsIgnoreCase("3d")
                || target.equalsIgnoreCase("all")
                || target.equalsIgnoreCase("sample")
                || target.equalsIgnoreCase("stutter")
                || target.equalsIgnoreCase("tracy")
                || target.equalsIgnoreCase("jfr"))) {
            CommandOutput.send("Usage: " + usage());
            return true;
        }

        String mode = ctx.arg(1);
        String targetArg = ctx.arg(2);
        String detailArg = ctx.arg(3);
        if (target.equalsIgnoreCase("jfr")) {
            return executeJfr(mode, targetArg);
        }
        if (target.equalsIgnoreCase("stutter")) {
            return executeStutter(mode, targetArg, detailArg);
        }
        if (target.equalsIgnoreCase("tracy")) {
            boolean enable = mode == null || mode.equalsIgnoreCase("on");
            if (mode != null && !mode.equalsIgnoreCase("on") && !mode.equalsIgnoreCase("off")) {
                CommandOutput.send("Usage: " + usage());
                return true;
            }
            boolean active = TracyProfiler.setEnabled(enable);
            if (!enable) {
                CommandOutput.send("Profiler tracy: off");
            } else if (active) {
                CommandOutput.send("Profiler tracy: on (streams to external Tracy viewer, not latest.log)");
            } else {
                CommandOutput.send("Profiler tracy: unavailable");
            }
            return true;
        }
        if (mode == null) {
            mode = "chat";
        }

        ProfilerSettings.OutputMode modeOutput = ProfilerSettings.parseOutput(mode);
        ProfilerSettings.SampleTarget modeTarget = ProfilerSettings.parseTarget(mode);
        ProfilerSettings.SampleTarget targetTarget = ProfilerSettings.parseTarget(targetArg);
        ProfilerSettings.OutputMode targetOutput = ProfilerSettings.parseOutput(targetArg);
        Boolean detailMode = parseDetailMode(detailArg);

        boolean modeIsOutput = modeOutput != null;
        boolean modeIsTarget = modeTarget != null;

        if (!modeIsOutput && !modeIsTarget) {
            CommandOutput.send("Usage: " + usage());
            return true;
        }

        String t = target.toLowerCase();
        if (!t.equals("sample") && !modeIsOutput) {
            CommandOutput.send("Usage: " + usage());
            return true;
        }
        if (t.equals("2d")) {
            ProfilerSettings.setOutput2d(modeOutput);
        } else if (t.equals("3d")) {
            ProfilerSettings.setOutput3d(modeOutput);
        } else if (t.equals("sample")) {
            if (modeIsTarget) {
                ProfilerSettings.setSampleTarget(modeTarget);
                if (targetOutput != null) {
                    ProfilerSettings.setSampleOutput(targetOutput);
                    modeOutput = targetOutput;
                } else {
                    ProfilerSettings.setSampleOutput(ProfilerSettings.OutputMode.CHAT);
                    modeOutput = ProfilerSettings.OutputMode.CHAT;
                }
            } else {
                ProfilerSettings.setSampleOutput(modeOutput);
            }
            if (targetTarget != null) {
                ProfilerSettings.setSampleTarget(targetTarget);
            }
            if (detailMode != null) {
                ProfilerSettings.setSampleDetailedStacks(detailMode);
            }
        } else {
            ProfilerSettings.setOutput2d(modeOutput);
            ProfilerSettings.setOutput3d(modeOutput);
        }
        if (t.equals("sample")) {
            CommandOutput.send("Profiler sample output: " + modeOutput.name().toLowerCase()
                    + " (target " + ProfilerSettings.formatTarget(ProfilerSettings.getSampleTarget())
                    + ", stacks " + (ProfilerSettings.isSampleDetailedStacks() ? "on" : "off") + ")");
        } else {
            CommandOutput.send("Profiler " + t + " output: " + modeOutput.name().toLowerCase());
        }
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex == 1) {
            return matchToken(token, List.of("2d", "3d", "all", "sample", "stutter", "tracy", "jfr"));
        }
        if (argIndex == 2) {
            String t = ctx.arg(0);
            if (t != null && (t.equalsIgnoreCase("sample") || t.equalsIgnoreCase("stutter"))) {
                return matchToken(token, List.of("off", "log", "chat", "2d", "3d", "all"));
            }
            if (t != null && t.equalsIgnoreCase("tracy")) {
                return matchToken(token, List.of("on", "off"));
            }
            if (t != null && t.equalsIgnoreCase("jfr")) {
                return matchToken(token, List.of("start", "stop", "dump", "status"));
            }
            return matchToken(token, List.of("off", "log", "chat"));
        }
        if (argIndex == 3) {
            String t = ctx.arg(0);
            if (t != null && t.equalsIgnoreCase("sample")) {
                return matchToken(token, List.of("2d", "3d", "all"));
            }
            if (t != null && t.equalsIgnoreCase("jfr")) {
                String action = ctx.arg(1);
                if (action == null || action.equalsIgnoreCase("start")) {
                    return matchToken(token, List.of("gc", "alloc", "full"));
                }
            }
        }
        if (argIndex == 4) {
            String t = ctx.arg(0);
            if (t != null && t.equalsIgnoreCase("sample")) {
                return matchToken(token, List.of("stacks", "nostacks"));
            }
        }
        return List.of();
    }

    private boolean executeStutter(String mode, String thresholdArg, String sampleArg) {
        if (mode == null || mode.equalsIgnoreCase("status")) {
            CommandOutput.send(FrameStutterProfiler.statusLine());
            return true;
        }
        ProfilerSettings.OutputMode output = ProfilerSettings.parseOutput(mode);
        if (output == null) {
            CommandOutput.send("Profiler stutter usage: @profiler stutter [off|log|chat|status] [thresholdMs] [sampleMs]");
            return true;
        }
        double thresholdMs = parseDouble(thresholdArg, 35.0);
        double sampleMs = parseDouble(sampleArg, 2.0);
        FrameStutterProfiler.configure(output, thresholdMs, sampleMs);
        CommandOutput.send(FrameStutterProfiler.statusLine());
        return true;
    }

    private boolean executeJfr(String action, String presetArg) {
        if (action == null || action.equalsIgnoreCase("status")) {
            CommandOutput.send(JfrProfiler.statusLine());
            return true;
        }
        if (action.equalsIgnoreCase("start")) {
            JfrProfiler.Preset preset = JfrProfiler.Preset.parse(presetArg);
            if (preset == null) {
                CommandOutput.send("Profiler JFR usage: @profiler jfr start [gc|alloc|full]");
                return true;
            }
            JfrProfiler.StartResult result = JfrProfiler.start(preset);
            if (result.started()) {
                CommandOutput.send("Profiler JFR: started " + preset.getId() + " -> " + absolute(result.path()));
                return true;
            }
            if (result.alreadyRunning()) {
                CommandOutput.send("Profiler JFR: already running ("
                        + result.preset().getId() + ", file " + absolute(result.path()) + ")");
                return true;
            }
            CommandOutput.send("Profiler JFR: unavailable"
                    + (result.error() == null ? "" : " (" + result.error() + ")"));
            return true;
        }
        if (action.equalsIgnoreCase("dump")) {
            JfrProfiler.DumpResult result = JfrProfiler.dumpSnapshot();
            if (result.dumped()) {
                CommandOutput.send("Profiler JFR: snapshot -> " + absolute(result.path()));
                return true;
            }
            if (result.notRunning()) {
                CommandOutput.send("Profiler JFR: not running");
                return true;
            }
            CommandOutput.send("Profiler JFR: snapshot failed"
                    + (result.error() == null ? "" : " (" + result.error() + ")"));
            return true;
        }
        if (action.equalsIgnoreCase("stop")) {
            JfrProfiler.StopResult result = JfrProfiler.stop();
            if (result.stopped()) {
                long seconds = result.startedAt() == null
                        ? 0L
                        : Math.max(0L, Duration.between(result.startedAt(), Instant.now()).toSeconds());
                CommandOutput.send("Profiler JFR: saved " + result.preset().getId()
                        + " (" + formatDuration(seconds) + ") -> " + absolute(result.path()));
                return true;
            }
            if (result.notRunning()) {
                CommandOutput.send("Profiler JFR: not running");
                return true;
            }
            CommandOutput.send("Profiler JFR: stop failed"
                    + (result.error() == null ? "" : " (" + result.error() + ")"));
            return true;
        }
        CommandOutput.send("Profiler JFR usage: @profiler jfr [start|stop|dump|status] [gc|alloc|full]");
        return true;
    }

    private Boolean parseDetailMode(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase()) {
            case "stacks", "stack", "detail", "detailed" -> Boolean.TRUE;
            case "nostacks", "nostack", "basic", "light" -> Boolean.FALSE;
            default -> null;
        };
    }

    private double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<String> matchToken(String token, List<String> options) {
        String lower = token == null ? "" : token.toLowerCase();
        List<String> out = new java.util.ArrayList<>();
        for (String opt : options) {
            if (lower.isEmpty() || opt.startsWith(lower)) {
                out.add(opt);
            }
        }
        return out;
    }

    private String absolute(Path path) {
        return path == null ? "<unknown>" : path.toAbsolutePath().toString();
    }

    private String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        return minutes + "m " + seconds + "s";
    }
}
