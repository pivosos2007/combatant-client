/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime;

public final class RuntimeShutdownContext {
    private final ClientRuntimeState targetState;
    private final String reason;
    private final RuntimeCleanupReport.Builder report;

    RuntimeShutdownContext(ClientRuntimeState targetState, String reason, RuntimeCleanupReport.Builder report) {
        this.targetState = targetState;
        this.reason = reason == null || reason.isBlank() ? "unspecified" : reason;
        this.report = report;
    }

    public ClientRuntimeState targetState() {
        return targetState;
    }

    public String reason() {
        return reason;
    }

    public boolean jarReplacement() {
        return targetState == ClientRuntimeState.JAR_REPLACEMENT_PANIC;
    }

    public void increment(String key, int amount) {
        report.increment(key, amount);
    }

    public void stopped(String subsystem) {
        report.stopped(subsystem);
    }

    public void failed(String subsystem, Throwable error) {
        report.failed(subsystem, error);
    }
}
