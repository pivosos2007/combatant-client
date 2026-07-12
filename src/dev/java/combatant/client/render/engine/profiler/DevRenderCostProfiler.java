package combatant.client.render.engine.profiler;

import combatant.client.render.engine.core.CombatantRenderSystem;

import java.util.*;

/**
 * Low-overhead owner/effect profiler for the new RHI path.
 *
 * <p>The existing DevRenderProfiler2D/3D tree answers "which Java section is hot".
 * This profiler answers the practical render question: which phase/widget/node/effect/pass
 * consumed CPU time and caused RHI work (draws, uploads, fullscreen passes, copies, uniforms).</p>
 */
public enum DevRenderCostProfiler {
    ;
    private static final int MAX_BUCKETS = 768;
    private static final long NS_IN_MS = 1_000_000L;
    private static final int DEFAULT_LOG_INTERVAL_FRAMES = 120;
    private static final int DEFAULT_LOG_TOP = 20;
    private static final double DEFAULT_LOG_MIN_MS = 0.02;

    private static final String PROP_ENABLED = "combatant.profiler.renderCost";
    private static final String PROP_LOG = "combatant.profiler.renderCost.log";
    private static final String PROP_INTERVAL = "combatant.profiler.renderCost.interval";
    private static final String PROP_TOP = "combatant.profiler.renderCost.top";
    private static final String PROP_MIN_MS = "combatant.profiler.renderCost.minMs";

    private static final LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
    private static final HashMap<String, String> labelCache = new HashMap<>(256);
    private static final ThreadLocal<Deque<Scope>> scopeStack = ThreadLocal.withInitial(ArrayDeque::new);
    private static long frameId = Long.MIN_VALUE;
    private static long lastLoggedFrameId = Long.MIN_VALUE;
    private static boolean active;
    private static Snapshot snapshot = Snapshot.EMPTY;

    public static void beginFrame(long newFrameId) {
        if (frameId == newFrameId) return;
        frameId = newFrameId;
        active = isProfilerEnabled();
        if (!active) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        buckets.clear();
        scopeStack.get().clear();
    }

    public static void endFrame() {
        if (!active) return;
        snapshot = Snapshot.from(frameId, buckets);
        emitLogIfDue(snapshot);
        active = false;
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static boolean isEnabled() {
        return active;
    }

    public static boolean isConfigured() {
        return Boolean.getBoolean(PROP_ENABLED);
    }

    public static Scope scope(String domain, String name) {
        if (!active) return Scope.NOOP;
        String d = clean(domain, "unknown");
        String n = clean(name, "unnamed");
        String owner = clean(DevProfilerPhase.current(), "unknown");
        Bucket bucket = bucket(d, owner, n);
        if (bucket == null) return Scope.NOOP;
        Scope scope = new Scope(bucket, System.nanoTime(), Counters.capture(), true);
        scopeStack.get().push(scope);
        return scope;
    }

    public static Scope phase(String label) {
        return scope("phase", label);
    }

    public static Scope uiNode(Object label) {
        return scope("ui.node", String.valueOf(label));
    }

    public static Scope uiRuntime(String label) {
        return scope("ui.runtime", label);
    }

    public static Scope uiEffect(String label) {
        return scope("ui.effect", label);
    }

    public static Scope postPass(String label) {
        return scope("post.pass", label);
    }

    public static Scope rhiDraw(String label) {
        return scope("rhi.draw", label);
    }

    public static Scope itemRender(String label) {
        return scope("item.render", label);
    }

    public static Scope worldEffect(String label) {
        return scope("world.effect", label);
    }

    public static List<String> debugLines(String title, String domainPrefix, int limit, double minMs) {
        Snapshot snap = snapshot;
        if (snap == null || snap.entries.isEmpty()) return List.of();
        String prefix = domainPrefix == null ? "" : domainPrefix;
        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : snap.entries) {
            if (prefix.isEmpty() || entry.domain.startsWith(prefix)) {
                filtered.add(entry);
            }
        }
        if (filtered.isEmpty()) return List.of();

        filtered.sort(Comparator
                .comparingLong((Entry e) -> e.totalNs).reversed()
                .thenComparingLong((Entry e) -> e.uploadedBytes).reversed()
                .thenComparingLong((Entry e) -> e.drawCalls).reversed());

        List<String> lines = new ArrayList<>();
        lines.add(title + ":");
        int count = 0;
        for (Entry e : filtered) {
            double ms = e.totalNs / (double) NS_IN_MS;
            if (ms < minMs) continue;
            lines.add(String.format(Locale.ROOT,
                    "  %d) [%s] %s:%s %.2f ms max %.2f calls %d | draw %d upload %d/%s fs %d copy %d/%d uni %d/%s",
                    count + 1,
                    shorten(e.owner, 42),
                    e.domain,
                    shorten(e.name, 56),
                    ms,
                    e.maxNs / (double) NS_IN_MS,
                    e.calls,
                    e.drawCalls,
                    e.meshUploads,
                    formatBytes(e.uploadedBytes),
                    e.fullscreenPasses,
                    e.fastCopies,
                    e.shaderCopies,
                    e.uniformWrites,
                    formatBytes(e.uniformBytes)));
            count++;
            if (count >= limit) break;
        }
        if (count == 0) return List.of();
        return lines;
    }

    private static boolean isProfilerEnabled() {
        return Boolean.getBoolean(PROP_ENABLED);
    }

    private static boolean shouldLogToLatest() {
        if (!Boolean.getBoolean(PROP_ENABLED)) return false;
        return Boolean.parseBoolean(System.getProperty(PROP_LOG, "true"));
    }

    private static void emitLogIfDue(Snapshot snap) {
        if (!shouldLogToLatest()) return;
        if (snap == null || snap.entries.isEmpty()) return;

        int interval = intProperty(PROP_INTERVAL, DEFAULT_LOG_INTERVAL_FRAMES, 1, 60_000);
        if (lastLoggedFrameId != Long.MIN_VALUE && snap.frameId - lastLoggedFrameId < interval) return;
        lastLoggedFrameId = snap.frameId;

        int top = intProperty(PROP_TOP, DEFAULT_LOG_TOP, 1, 200);
        double minMs = doubleProperty(PROP_MIN_MS, DEFAULT_LOG_MIN_MS, 0.0, 1_000.0);

        ProfilerLog.info("[RenderCost] frame=%d buckets=%d interval=%d top=%d minMs=%.3f", snap.frameId, snap.entries.size(), interval, top, minMs);
        emitDomainToLog("phases", "phase", top, minMs);
        emitDomainToLog("ui runtime", "ui.runtime", top, minMs);
        emitDomainToLog("ui nodes", "ui.node", top, minMs);
        emitDomainToLog("ui effects", "ui.effect", top, minMs);
        emitDomainToLog("item render", "item.render", top, minMs);
        emitDomainToLog("world effects", "world.effect", top, minMs);
        emitDomainToLog("post passes", "post.pass", top, minMs);
        emitDomainToLog("rhi draws", "rhi.draw", top, minMs);
    }

    private static void emitDomainToLog(String title, String prefix, int top, double minMs) {
        List<String> lines = debugLines("[RenderCost] " + title, prefix, top, minMs);
        for (String line : lines) {
            ProfilerLog.info("%s", line);
        }
    }

    private static int intProperty(String key, int fallback, int min, int max) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double doubleProperty(String key, double fallback, double min, double max) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) return fallback;
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Bucket bucket(String domain, String owner, String name) {
        String key = domain + '\u001F' + owner + '\u001F' + name;
        Bucket existing = buckets.get(key);
        if (existing != null) return existing;
        if (buckets.size() >= MAX_BUCKETS) {
            key = "overflow\u001F" + owner + "\u001Fother";
            existing = buckets.get(key);
            if (existing != null) return existing;
            if (buckets.size() >= MAX_BUCKETS + 1) return null;
            Bucket overflow = new Bucket("overflow", owner, "other");
            buckets.put(key, overflow);
            return overflow;
        }
        Bucket created = new Bucket(domain, owner, name);
        buckets.put(key, created);
        return created;
    }

    private static String clean(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        return s.isEmpty() ? fallback : s;
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        if (max <= 3) return s.substring(0, max);
        return s.substring(0, max - 3) + "...";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KiB", kb);
        return String.format(Locale.ROOT, "%.2f MiB", kb / 1024.0);
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null, 0L, Counters.ZERO, false);
        private final Bucket bucket;
        private final long startNs;
        private final Counters begin;
        private final boolean enabled;
        private boolean closed;
        private long childNs;
        private Counters childCounters = Counters.ZERO;

        private Scope(Bucket bucket, long startNs, Counters begin, boolean enabled) {
            this.bucket = bucket;
            this.startNs = startNs;
            this.begin = begin;
            this.enabled = enabled;
        }

        @Override
        public void close() {
            if (!enabled || closed || bucket == null) return;
            closed = true;
            long elapsed = Math.max(0L, System.nanoTime() - startNs);
            Counters rawDelta = begin.deltaTo(Counters.capture());

            Deque<Scope> stack = scopeStack.get();
            if (!stack.isEmpty() && stack.peek() == this) {
                stack.pop();
            } else {
                stack.remove(this);
            }

            if (!stack.isEmpty()) {
                Scope parent = stack.peek();
                parent.childNs += elapsed;
                parent.childCounters = parent.childCounters.plus(rawDelta);
            }

            long bucketNs = elapsed;
            Counters bucketCounters = rawDelta;
            if ("ui.node".equals(bucket.domain)) {
                bucketNs = Math.max(0L, elapsed - childNs);
                bucketCounters = rawDelta.minus(childCounters);
            }
            bucket.add(bucketNs, bucketCounters);
        }
    }

    private static final class Bucket {
        final String domain;
        final String owner;
        final String name;
        long calls;
        long totalNs;
        long maxNs;
        long drawCalls;
        long meshUploads;
        long fullscreenPasses;
        long fastCopies;
        long shaderCopies;
        long uploadedBytes;
        long uniformWrites;
        long uniformBytes;

        private Bucket(String domain, String owner, String name) {
            this.domain = domain;
            this.owner = owner;
            this.name = name;
        }

        void add(long ns, Counters d) {
            calls++;
            totalNs += ns;
            maxNs = Math.max(maxNs, ns);
            if (d == null) return;
            drawCalls += d.drawCalls;
            meshUploads += d.meshUploads;
            fullscreenPasses += d.fullscreenPasses;
            fastCopies += d.fastCopies;
            shaderCopies += d.shaderCopies;
            uploadedBytes += d.uploadedBytes;
            uniformWrites += d.uniformWrites;
            uniformBytes += d.uniformBytes;
        }
    }

    private record Counters(long drawCalls,
                            long meshUploads,
                            long fullscreenPasses,
                            long fastCopies,
                            long shaderCopies,
                            long uploadedBytes,
                            long uniformWrites,
                            long uniformBytes) {
        static final Counters ZERO = new Counters(0, 0, 0, 0, 0, 0, 0, 0);

        static Counters capture() {
            try {
                long uploaded = CombatantRenderSystem.rhi().stats().uploadedVertexBytes()
                        + CombatantRenderSystem.rhi().stats().uploadedIndexBytes();
                return new Counters(
                        CombatantRenderSystem.rhi().stats().drawCalls(),
                        CombatantRenderSystem.rhi().stats().meshUploads(),
                        CombatantRenderSystem.rhi().stats().fullscreenPasses(),
                        CombatantRenderSystem.rhi().stats().textureFastCopies(),
                        CombatantRenderSystem.rhi().stats().textureShaderCopies(),
                        uploaded,
                        CombatantRenderSystem.uniforms().stats().writes(),
                        CombatantRenderSystem.uniforms().stats().uploadedBytes()
                );
            } catch (Throwable ignored) {
                return ZERO;
            }
        }

        Counters deltaTo(Counters end) {
            if (end == null) return ZERO;
            return new Counters(
                    Math.max(0L, end.drawCalls - drawCalls),
                    Math.max(0L, end.meshUploads - meshUploads),
                    Math.max(0L, end.fullscreenPasses - fullscreenPasses),
                    Math.max(0L, end.fastCopies - fastCopies),
                    Math.max(0L, end.shaderCopies - shaderCopies),
                    Math.max(0L, end.uploadedBytes - uploadedBytes),
                    Math.max(0L, end.uniformWrites - uniformWrites),
                    Math.max(0L, end.uniformBytes - uniformBytes)
            );
        }

        Counters plus(Counters other) {
            if (other == null) return this;
            return new Counters(
                    drawCalls + other.drawCalls,
                    meshUploads + other.meshUploads,
                    fullscreenPasses + other.fullscreenPasses,
                    fastCopies + other.fastCopies,
                    shaderCopies + other.shaderCopies,
                    uploadedBytes + other.uploadedBytes,
                    uniformWrites + other.uniformWrites,
                    uniformBytes + other.uniformBytes
            );
        }

        Counters minus(Counters other) {
            if (other == null) return this;
            return new Counters(
                    Math.max(0L, drawCalls - other.drawCalls),
                    Math.max(0L, meshUploads - other.meshUploads),
                    Math.max(0L, fullscreenPasses - other.fullscreenPasses),
                    Math.max(0L, fastCopies - other.fastCopies),
                    Math.max(0L, shaderCopies - other.shaderCopies),
                    Math.max(0L, uploadedBytes - other.uploadedBytes),
                    Math.max(0L, uniformWrites - other.uniformWrites),
                    Math.max(0L, uniformBytes - other.uniformBytes)
            );
        }
    }

    public record Snapshot(long frameId, List<Entry> entries) {
        static final Snapshot EMPTY = new Snapshot(Long.MIN_VALUE, List.of());

        static Snapshot from(long frameId, Map<String, Bucket> buckets) {
            if (buckets == null || buckets.isEmpty()) return EMPTY;
            List<Entry> out = new ArrayList<>(buckets.size());
            for (Bucket b : buckets.values()) {
                out.add(new Entry(
                        b.domain,
                        b.owner,
                        b.name,
                        b.calls,
                        b.totalNs,
                        b.maxNs,
                        b.drawCalls,
                        b.meshUploads,
                        b.fullscreenPasses,
                        b.fastCopies,
                        b.shaderCopies,
                        b.uploadedBytes,
                        b.uniformWrites,
                        b.uniformBytes
                ));
            }
            return new Snapshot(frameId, List.copyOf(out));
        }
    }

    public record Entry(String domain,
                        String owner,
                        String name,
                        long calls,
                        long totalNs,
                        long maxNs,
                        long drawCalls,
                        long meshUploads,
                        long fullscreenPasses,
                        long fastCopies,
                        long shaderCopies,
                        long uploadedBytes,
                        long uniformWrites,
                        long uniformBytes) {
    }
}
