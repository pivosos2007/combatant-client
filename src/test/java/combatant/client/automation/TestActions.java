package combatant.client.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Scripted leaf actions for headless runtime tests. */
final class TestActions {
    private TestActions() {
    }

    /** Holds {@code resources} for {@code ticks} ticks, waiting while they are busy. */
    static final class Hold extends AbstractAction {
        final ActionResource[] resources;
        final int ticks;
        final List<String> log = new ArrayList<>();
        int heldTicks;
        int revoked;

        Hold(String name, int ticks, ActionResource... resources) {
            super(name);
            this.ticks = ticks;
            this.resources = resources;
        }

        @Override
        protected ActionResult onTick(ActionContext ctx) {
            if (!acquire(ctx, resources)) {
                log.add("wait");
                return ActionResult.RUNNING;
            }
            heldTicks++;
            log.add("hold");
            return heldTicks >= ticks ? ActionResult.SUCCESS : ActionResult.RUNNING;
        }

        @Override
        protected void onRevoked(ActionLease lease) {
            revoked++;
        }

        @Override
        protected void onEnd(ActionContext ctx, ActionResult result) {
            log.add("end:" + result.status());
        }
    }

    /** Returns the scripted results in order, repeating the last one. */
    static final class Scripted extends AbstractAction {
        private final ActionResult[] script;
        int ticks;
        boolean cancelled;

        Scripted(String name, ActionResult... script) {
            super(name);
            this.script = script;
        }

        @Override
        protected ActionResult onTick(ActionContext ctx) {
            ActionResult result = script[Math.min(ticks, script.length - 1)];
            ticks++;
            return result;
        }

        @Override
        protected void onCancel(ActionContext ctx) {
            cancelled = true;
        }
    }

    static Scripted succeed(String name) {
        return new Scripted(name, ActionResult.SUCCESS);
    }

    static Scripted fail(String name, FailureReason reason) {
        return new Scripted(name, ActionResult.failure(reason, name));
    }

    static Scripted running(String name) {
        return new Scripted(name, ActionResult.RUNNING);
    }

    static Scripted afterTicks(String name, int runningTicks, ActionResult end) {
        ActionResult[] script = new ActionResult[runningTicks + 1];
        for (int i = 0; i < runningTicks; i++) script[i] = ActionResult.RUNNING;
        script[runningTicks] = end;
        return new Scripted(name, script);
    }

    /** Counts factory invocations for retry tests. */
    static final class Factory implements Supplier<Action> {
        private final Supplier<Action> delegate;
        int created;

        Factory(Supplier<Action> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Action get() {
            created++;
            return delegate.get();
        }
    }
}
