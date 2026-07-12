package combatant.client.render.engine.profiler;

import com.mojang.jtracy.GpuApi;
import com.mojang.jtracy.GpuContext;
import com.mojang.jtracy.TracyClient;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import combatant.client.render.engine.guard.LegacyRenderPath;
import combatant.client.render.engine.guard.RenderBoundaryExempt;

import java.util.ArrayDeque;

@RenderBoundaryExempt(value = LegacyRenderPath.DIRECT_GL_OUTSIDE_BACKEND, reason = "Profiler-only timer query path; not part of renderer submission architecture")
public enum DevTracyGpuProfiler {
    ;

    private static final Scope NOOP_SCOPE = new Scope(false);
    private static final Object LOCK = new Object();
    private static final ArrayDeque<QueryEvent> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<Integer> FREE_GL_QUERIES = new ArrayDeque<>();

    private static volatile boolean initAttempted;
    private static volatile boolean available;
    private static volatile GpuContext context;
    private static int nextQueryId = 1;

    public static boolean isEnabled() {
        return DevTracyProfiler.isEnabled() && isAvailable();
    }

    public static Scope beginZone(String name) {
        if (!isEnabled() || name == null || name.isBlank()) {
            return NOOP_SCOPE;
        }
        int beginId = allocateQueryId();
        int beginQuery = allocateGlQuery();
        context.beginZone(beginId, name, "", "", 0);
        GL33C.glQueryCounter(beginQuery, GL33C.GL_TIMESTAMP);
        enqueue(beginId, beginQuery);
        return new Scope(true);
    }

    public static void onFrameEnd() {
        if (!isEnabled()) {
            return;
        }
        pump();
    }

    private static boolean isAvailable() {
        ensureContext();
        return available;
    }

    private static void ensureContext() {
        if (initAttempted) {
            return;
        }
        synchronized (LOCK) {
            if (initAttempted) {
                return;
            }
            initAttempted = true;
            try {
                GLCapabilities caps = GL.getCapabilities();
                boolean timerQuery = caps.OpenGL33 || caps.GL_ARB_timer_query;
                if (!timerQuery) {
                    available = false;
                    ProfilerLog.warn("Tracy GPU unavailable: timer queries are not supported by this OpenGL context");
                    return;
                }
                long gpuTimestamp = GL33C.glGetInteger64(GL33C.GL_TIMESTAMP);
                context = TracyClient.createGpuContext(GpuApi.OPENGL, gpuTimestamp, 1.0f);
                context.setName("main");
                available = true;
                ProfilerLog.info("Tracy GPU context initialized");
            } catch (Throwable t) {
                available = false;
                ProfilerLog.warn("Tracy GPU unavailable: %s", t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static void pump() {
        synchronized (LOCK) {
            while (!PENDING.isEmpty()) {
                QueryEvent event = PENDING.peekFirst();
                if (GL15C.glGetQueryObjecti(event.glQuery, GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) {
                    break;
                }
                long timestamp = GL33C.glGetQueryObjecti64(event.glQuery, GL15C.GL_QUERY_RESULT);
                context.submitQueryTimestamp(event.queryId, timestamp);
                PENDING.removeFirst();
                FREE_GL_QUERIES.addLast(event.glQuery);
            }
        }
    }

    private static void enqueue(int queryId, int glQuery) {
        synchronized (LOCK) {
            PENDING.addLast(new QueryEvent(queryId, glQuery));
        }
    }

    private static int allocateQueryId() {
        synchronized (LOCK) {
            int id = nextQueryId++;
            if (id <= 0) {
                nextQueryId = 1;
                id = nextQueryId++;
            }
            return id;
        }
    }

    private static int allocateGlQuery() {
        synchronized (LOCK) {
            Integer existing = FREE_GL_QUERIES.pollFirst();
            if (existing != null) {
                return existing;
            }
        }
        return GL15C.glGenQueries();
    }

    private record QueryEvent(int queryId, int glQuery) {
    }

    public static final class Scope implements AutoCloseable {
        private final boolean active;

        private Scope(boolean active) {
            this.active = active;
        }

        @Override
        public void close() {
            if (!active || !isEnabled()) {
                return;
            }
            int endId = allocateQueryId();
            int endQuery = allocateGlQuery();
            context.endZone(endId);
            GL33C.glQueryCounter(endQuery, GL33C.GL_TIMESTAMP);
            enqueue(endId, endQuery);
        }
    }
}
