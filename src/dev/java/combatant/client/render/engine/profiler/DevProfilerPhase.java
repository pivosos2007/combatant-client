package combatant.client.render.engine.profiler;

import java.util.ArrayDeque;

public enum DevProfilerPhase {
    ;

    private static final ThreadLocal<ArrayDeque<String>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    public static Scope scope(String label) {
        if (label == null || label.isBlank()) return Scope.NOOP;
        begin(label);
        return new Scope(true);
    }

    public static void begin(String label) {
        if (label == null || label.isBlank()) return;
        if (!DevSamplingProfiler.isActive()) return;
        STACK.get().push(label);
    }

    public static void end(String label) {
        if (label == null || label.isBlank()) return;
        ArrayDeque<String> stack = STACK.get();
        if (stack == null || stack.isEmpty()) return;
        String top = stack.peek();
        if (label.equals(top)) {
            pop();
        }
    }

    public static String current() {
        ArrayDeque<String> stack = STACK.get();
        if (stack == null || stack.isEmpty()) return "unknown";
        String v = stack.peek();
        return v == null || v.isBlank() ? "unknown" : v;
    }

    public static void clearCurrent() {
        ArrayDeque<String> stack = STACK.get();
        if (stack != null) {
            stack.clear();
        }
    }

    private static void pop() {
        ArrayDeque<String> stack = STACK.get();
        if (stack == null || stack.isEmpty()) return;
        stack.pop();
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(false);
        private final boolean active;

        private Scope(boolean active) {
            this.active = active;
        }

        @Override
        public void close() {
            if (!active) return;
            pop();
        }
    }
}
