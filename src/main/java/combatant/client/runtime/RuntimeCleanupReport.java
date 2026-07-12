/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeCleanupReport {
    private final ClientRuntimeState state;
    private final String reason;
    private final Map<String, Integer> counters;
    private final List<String> stoppedSubsystems;
    private final List<String> failedSubsystems;

    private RuntimeCleanupReport(ClientRuntimeState state,
                                 String reason,
                                 Map<String, Integer> counters,
                                 List<String> stoppedSubsystems,
                                 List<String> failedSubsystems) {
        this.state = state;
        this.reason = reason;
        this.counters = Map.copyOf(counters);
        this.stoppedSubsystems = List.copyOf(stoppedSubsystems);
        this.failedSubsystems = List.copyOf(failedSubsystems);
    }

    static Builder builder(ClientRuntimeState state, String reason) {
        return new Builder(state, reason);
    }

    public ClientRuntimeState state() {
        return state;
    }

    public String reason() {
        return reason;
    }

    public Map<String, Integer> counters() {
        return counters;
    }

    public List<String> stoppedSubsystems() {
        return stoppedSubsystems;
    }

    public List<String> failedSubsystems() {
        return failedSubsystems;
    }

    public String summaryLine() {
        return "state=" + state
                + ", reason=" + reason
                + ", counters=" + counters
                + ", stopped=" + stoppedSubsystems
                + ", failed=" + failedSubsystems;
    }

    static final class Builder {
        private final ClientRuntimeState state;
        private final String reason;
        private final Map<String, Integer> counters = new LinkedHashMap<>();
        private final List<String> stoppedSubsystems = new ArrayList<>();
        private final List<String> failedSubsystems = new ArrayList<>();

        private Builder(ClientRuntimeState state, String reason) {
            this.state = state;
            this.reason = reason == null || reason.isBlank() ? "unspecified" : reason;
        }

        void increment(String key, int amount) {
            if (key == null || key.isBlank() || amount == 0) return;
            counters.merge(key, amount, Integer::sum);
        }

        void stopped(String subsystem) {
            if (subsystem != null && !subsystem.isBlank()) {
                stoppedSubsystems.add(subsystem);
            }
        }

        void failed(String subsystem, Throwable error) {
            String name = subsystem == null || subsystem.isBlank() ? "<unknown>" : subsystem;
            failedSubsystems.add(error == null ? name : name + ": " + error.getClass().getSimpleName());
        }

        RuntimeCleanupReport build() {
            return new RuntimeCleanupReport(state, reason, counters, stoppedSubsystems, failedSubsystems);
        }
    }
}
