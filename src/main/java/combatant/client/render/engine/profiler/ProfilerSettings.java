/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

public enum ProfilerSettings {
    ;

    private static volatile OutputMode output2d = OutputMode.OFF;
    private static volatile OutputMode output3d = OutputMode.OFF;
    private static volatile OutputMode sampleOutput = OutputMode.OFF;
    private static volatile SampleTarget sampleTarget = SampleTarget.ALL;
    private static volatile boolean sampleDetailedStacks = false;
    private static volatile boolean tracyEnabled = false;

    public static OutputMode getOutput2d() {
        return output2d;
    }

    public static void setOutput2d(OutputMode mode) {
        output2d = mode == null ? OutputMode.OFF : mode;
    }

    public static OutputMode getOutput3d() {
        return output3d;
    }

    public static void setOutput3d(OutputMode mode) {
        output3d = mode == null ? OutputMode.OFF : mode;
    }

    public static OutputMode getSampleOutput() {
        return sampleOutput;
    }

    public static void setSampleOutput(OutputMode mode) {
        sampleOutput = mode == null ? OutputMode.OFF : mode;
    }

    public static SampleTarget getSampleTarget() {
        return sampleTarget;
    }

    public static void setSampleTarget(SampleTarget target) {
        sampleTarget = target == null ? SampleTarget.ALL : target;
    }

    public static boolean isSampleDetailedStacks() {
        return sampleDetailedStacks;
    }

    public static void setSampleDetailedStacks(boolean detailed) {
        sampleDetailedStacks = detailed;
    }

    public static boolean isTracyEnabled() {
        return tracyEnabled;
    }

    public static void setTracyEnabled(boolean enabled) {
        tracyEnabled = enabled;
    }

    public static OutputMode parseOutput(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase()) {
            case "off" -> OutputMode.OFF;
            case "log" -> OutputMode.LOG;
            case "chat" -> OutputMode.CHAT;
            default -> null;
        };
    }

    public static SampleTarget parseTarget(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase()) {
            case "2d", "ui" -> SampleTarget.D2;
            case "3d" -> SampleTarget.D3;
            case "all" -> SampleTarget.ALL;
            default -> null;
        };
    }

    public static String formatTarget(SampleTarget target) {
        if (target == null) return "all";
        return switch (target) {
            case D2 -> "2d";
            case D3 -> "3d";
            case ALL -> "all";
        };
    }

    public enum OutputMode {
        OFF,
        LOG,
        CHAT
    }

    public enum SampleTarget {
        ALL,
        D2,
        D3
    }
}
