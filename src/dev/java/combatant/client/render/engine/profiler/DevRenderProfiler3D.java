package combatant.client.render.engine.profiler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.resource.RenderResourceStatsSnapshot;
import combatant.client.render.engine.rhi.uniform.UniformAllocatorStatsSnapshot;
import combatant.client.render.engine.world.WorldRenderStatsSnapshot;
import combatant.client.render.iris.IrisRuntimeSnapshot;
import combatant.client.render.sodium.SodiumTerrainInteropStatsSnapshot;
import combatant.client.render.sodium.SodiumVisibilityStatsSnapshot;
import combatant.client.runtime.RuntimeGate;

import java.util.*;

public enum DevRenderProfiler3D {
    ;

    private static final long NS_IN_MS = 1_000_000L;
    private static final int DEFAULT_TOP_LIMIT = 6;
    private static final int DEFAULT_TREE_DEPTH = 32;
    private static final double DEFAULT_MIN_MS = 0.0;
    private static final long DEFAULT_EMIT_INTERVAL_NS = 1_000_000_000L;
    private static final ArrayDeque<Node> stack = new ArrayDeque<>();
    private static boolean active;
    private static Node root;
    private static Node snapshot;
    private static long lastEmitNs;

    public static boolean isEnabled() {
        return getOutputModeRaw() != ProfilerSettings.OutputMode.OFF;
    }

    public static void beginFrame(String name) {
        DevSamplingProfiler.onClientFrame(DevSamplingProfiler.Target.WORLD);
        if (!isEnabled()) return;
        active = true;
        root = new Node(name, null);
        stack.clear();
        stack.push(root);
        root.startNs = System.nanoTime();
    }

    public static boolean beginFrameIfNeeded(String name) {
        DevSamplingProfiler.onClientFrame(DevSamplingProfiler.Target.WORLD);
        if (!isEnabled() || active) return false;
        beginFrame(name);
        return true;
    }

    public static void endFrame() {
        if (!isEnabled() || !active) return;
        long now = System.nanoTime();
        while (stack.size() > 1) {
            endSection();
        }
        Node r = stack.peek();
        if (r != null) {
            r.calls = Math.max(1, r.calls);
            r.totalNs += now - r.startNs;
            r.startNs = 0L;
            snapshot = r;
            DevTracyProfiler.plotWorldFrame(r.totalNs / (double) NS_IN_MS, r.countNodes());
        }
        active = false;
        emitIfDue(now);
    }

    public static Section section(String name) {
        if (!isEnabled() || !active) return Section.NOOP;
        beginSection(name);
        return new Section(true);
    }

    private static void beginSection(String name) {
        Node parent = stack.peek();
        if (parent == null) return;
        Node child = parent.children.computeIfAbsent(name, n -> new Node(n, parent));
        child.calls += 1;
        child.startNs = System.nanoTime();
        stack.push(child);
    }

    private static void endSection() {
        Node node = stack.poll();
        if (node == null) return;
        long now = System.nanoTime();
        node.totalNs += now - node.startNs;
        node.startNs = 0L;
    }

    public static List<String> getDebugLines() {
        return getDebugLines(DEFAULT_TOP_LIMIT, DEFAULT_TREE_DEPTH, DEFAULT_MIN_MS);
    }

    public static List<String> getDebugLines(int topLimit, int depthLimit, double minMs) {
        if (!isEnabled()) return List.of();
        Node snap = snapshot;
        if (snap == null) return List.of();

        List<String> lines = new ArrayList<>();
        double totalMs = snap.totalNs / (double) NS_IN_MS;
        lines.add(String.format("world prof: %.2f ms, nodes %d", totalMs, snap.countNodes()));
        appendRhiCounters(lines);
        appendCostCounters(lines);

        List<Node> flat = new ArrayList<>();
        collectFlat(snap, flat);
        flat.sort(Comparator.comparingLong((Node n) -> n.totalNs).reversed());

        int top = Math.min(topLimit, flat.size());
        if (top > 0) {
            lines.add("world prof top:");
            for (int i = 0; i < top; i++) {
                Node n = flat.get(i);
                double ms = n.totalNs / (double) NS_IN_MS;
                if (ms < minMs) break;
                double selfMs = selfNs(n) / (double) NS_IN_MS;
                Node hot = hottestChild(n);
                if (hot != null) {
                    double hotMs = hot.totalNs / (double) NS_IN_MS;
                    lines.add(String.format(
                            "  %d) %s %.2f ms (self %.2f, hot %s %.2f, %d)",
                            i + 1,
                            nodePath(n),
                            ms,
                            selfMs,
                            hot.name,
                            hotMs,
                            n.calls
                    ));
                } else {
                    lines.add(String.format(
                            "  %d) %s %.2f ms (self %.2f, %d)",
                            i + 1,
                            nodePath(n),
                            ms,
                            selfMs,
                            n.calls
                    ));
                }
            }
        }

        lines.add("world prof tree:");
        appendTreeAscii(lines, snap, "", 0, depthLimit, minMs);
        return lines;
    }

    private static void appendCostCounters(List<String> lines) {
        List<String> phase = DevRenderCostProfiler.debugLines("render cost phases", "phase", 6, 0.02);
        if (!phase.isEmpty()) lines.addAll(phase);
        List<String> post = DevRenderCostProfiler.debugLines("world cost post", "post.pass", 8, 0.02);
        if (!post.isEmpty()) lines.addAll(post);
        List<String> effects = DevRenderCostProfiler.debugLines("world cost effects", "world.effect", 8, 0.02);
        if (!effects.isEmpty()) lines.addAll(effects);
        List<String> rhi = DevRenderCostProfiler.debugLines("world cost rhi draws", "rhi.draw", 8, 0.02);
        if (!rhi.isEmpty()) lines.addAll(rhi);
    }

    private static void appendRhiCounters(List<String> lines) {
        RhiStatsSnapshot rhi = CombatantRenderSystem.rhiStatsSnapshot();
        UniformAllocatorStatsSnapshot uniforms = CombatantRenderSystem.uniformStatsSnapshot();
        if (rhi.drawCalls() > 0 || rhi.renderPasses() > 0 || rhi.meshUploads() > 0 || rhi.fullscreenPasses() > 0
                || rhi.textureFastCopies() > 0 || rhi.textureShaderCopies() > 0) {
            lines.add(String.format(
                    "rhi: draws %d, passes/switches %d/%d, meshUploads %d, fullscreen %d, copies fast/shader %d/%d, uploaded v/i %s/%s",
                    rhi.drawCalls(),
                    rhi.renderPasses(),
                    rhi.renderPassAttachmentSwitches(),
                    rhi.meshUploads(),
                    rhi.fullscreenPasses(),
                    rhi.textureFastCopies(),
                    rhi.textureShaderCopies(),
                    formatBytes(rhi.uploadedVertexBytes()),
                    formatBytes(rhi.uploadedIndexBytes())
            ));
        }
        WorldRenderStatsSnapshot world = CombatantRenderSystem.worldRenderStatsSnapshot();
        if (world.recordedCommands() > 0 || world.submittedCommands() > 0 || world.skippedEmptyCommands() > 0) {
            lines.add(String.format(
                    "world3d: commands rec/sub/skip %d/%d/%d, vertices/indices %d/%d, fog %d, depth pre/main/none %d/%d/%d",
                    world.recordedCommands(),
                    world.submittedCommands(),
                    world.skippedEmptyCommands(),
                    world.submittedVertices(),
                    world.submittedIndices(),
                    world.fogBindings(),
                    world.depthPrePassBindings(),
                    world.depthMainBindings(),
                    world.depthDisabledBindings()
            ));
        }

        if (rhi.ringWraps() > 0 || rhi.ringStalls() > 0 || rhi.immediateFallbackUploads() > 0) {
            lines.add(String.format(
                    "rhi upload: wraps %d, stalls %d, immediateFallback %d, arena reuse/retire %d/%d",
                    rhi.ringWraps(),
                    rhi.ringStalls(),
                    rhi.immediateFallbackUploads(),
                    rhi.dynamicArenaReuses(),
                    rhi.dynamicArenaRetires()
            ));
        }
        if (rhi.legacyPathUses() > 0) {
            lines.add("rhi legacy paths: " + rhi.legacyPathUses() + " " + rhi.legacyPathBreakdown());
        }
        if (uniforms.writes() > 0 || uniforms.ringRotations() > 0 || uniforms.staleReadMisses() > 0) {
            lines.add(String.format(
                    "uniforms: writes %d, bytes %s, activeStreams %d, rotations %d, grows %d, staleMiss %d",
                    uniforms.writes(),
                    formatBytes(uniforms.uploadedBytes()),
                    uniforms.activeStreams(),
                    uniforms.ringRotations(),
                    uniforms.ringGrows(),
                    uniforms.staleReadMisses()
            ));
        }
        RenderResourceStatsSnapshot resources = CombatantRenderSystem.resourceStatsSnapshot();
        if (resources.persistentFramebuffers() > 0 || resources.temporaryFramebuffers() > 0
                || resources.retirementBacklog() > 0 || resources.leakedResources() > 0) {
            lines.add(String.format(
                    "resources: fb persistent/temp %d/%d, create/resize %d/%d, borrow/release %d/%d, retire backlog/leaks %d/%d",
                    resources.persistentFramebuffers(),
                    resources.temporaryFramebuffers(),
                    resources.framebufferCreates(),
                    resources.framebufferResizes(),
                    resources.framebufferBorrows(),
                    resources.framebufferReleases(),
                    resources.retirementBacklog(),
                    resources.leakedResources()
            ));
        }

        IrisRuntimeSnapshot iris = CombatantRenderSystem.irisSnapshot();
        if (iris.modLoaded() || iris.shaderpackInUse()) {
            lines.add(iris.shortLine());
        }

        var immediatelyFast = CombatantRenderSystem.immediatelyFastSnapshot();
        if (immediatelyFast.modLoaded()) {
            lines.add(immediatelyFast.shortLine());
        }

        SodiumVisibilityStatsSnapshot sodiumVisibility = CombatantRenderSystem.sodiumVisibilityStatsSnapshot();
        if (sodiumVisibility.queries() > 0 || sodiumVisibility.errors() > 0) {
            lines.add(String.format(
                    "sodium vis: queries %d, accepts/rejects %d/%d, bypass %d (unavail %d, optout %d), errors %d",
                    sodiumVisibility.queries(),
                    sodiumVisibility.accepts(),
                    sodiumVisibility.rejects(),
                    sodiumVisibility.bypasses(),
                    sodiumVisibility.unavailableBypasses(),
                    sodiumVisibility.optOutBypasses(),
                    sodiumVisibility.errors()
            ));
        }

        SodiumTerrainInteropStatsSnapshot sodiumTerrain = CombatantRenderSystem.sodiumTerrainStatsSnapshot();
        if (sodiumTerrain.terrainUpdatesScheduled() > 0
                || sodiumTerrain.rebuildsScheduled() > 0
                || sodiumTerrain.interopErrors() > 0) {
            lines.add(String.format(
                    "sodium terrain: updates %d, rebuilds %d, errors %d",
                    sodiumTerrain.terrainUpdatesScheduled(),
                    sodiumTerrain.rebuildsScheduled(),
                    sodiumTerrain.interopErrors()
            ));
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KiB", kb);
        return String.format(Locale.ROOT, "%.2f MiB", kb / 1024.0);
    }

    private static void collectFlat(Node node, List<Node> out) {
        for (Node child : node.children.values()) {
            out.add(child);
            collectFlat(child, out);
        }
    }

    private static void appendTreeAscii(List<String> out, Node node, String indent, int depth, int maxDepth, double minMs) {
        if (node == null) return;
        double ms = node.totalNs / (double) NS_IN_MS;
        if (depth > 0 && ms < minMs) return;
        double selfMs = selfNs(node) / (double) NS_IN_MS;
        String line = depth == 0
                ? String.format("%s %.2f ms (self %.2f, %d)", node.name, ms, selfMs, node.calls)
                : String.format("%s|-- %s %.2f ms (self %.2f, %d)", indent, node.name, ms, selfMs, node.calls);
        out.add(line);
        if (depth >= maxDepth) return;
        List<Node> children = new ArrayList<>(node.children.values());
        children.sort(Comparator.comparingLong((Node n) -> n.totalNs).reversed());
        String childIndent = indent + "|   ";
        for (Node child : children) {
            appendTreeAscii(out, child, childIndent, depth + 1, maxDepth, minMs);
        }
    }

    private static void emitIfDue(long nowNs) {
        ProfilerSettings.OutputMode mode = getOutputMode();
        if (mode == ProfilerSettings.OutputMode.OFF) return;
        if (RuntimeGate.isPanic()) return;
        if (nowNs - lastEmitNs < DEFAULT_EMIT_INTERVAL_NS) return;
        lastEmitNs = nowNs;

        if (mode == ProfilerSettings.OutputMode.LOG) {
            emitToLog();
        } else if (mode == ProfilerSettings.OutputMode.CHAT) {
            emitToChat();
        }
    }

    private static ProfilerSettings.OutputMode getOutputMode() {
        if (!isEnabled()) return ProfilerSettings.OutputMode.OFF;
        return getOutputModeRaw();
    }

    private static ProfilerSettings.OutputMode getOutputModeRaw() {
        return ProfilerSettings.getOutput3d();
    }

    private static void emitToLog() {
        List<String> lines = getDebugLines();
        if (lines.isEmpty()) return;
        for (String line : lines) {
            ProfilerLog.info("[WorldProfiler] %s", line);
        }
    }

    private static void emitToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        List<String> lines = getDebugLines(4, 2, 0.05);
        if (lines.isEmpty()) return;
        for (String line : lines) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[WorldProfiler] " + line));
        }
    }

    private static long selfNs(Node node) {
        if (node == null) return 0L;
        long childrenNs = 0L;
        for (Node child : node.children.values()) {
            childrenNs += child.totalNs;
        }
        long self = node.totalNs - childrenNs;
        return Math.max(0L, self);
    }

    private static Node hottestChild(Node node) {
        if (node == null || node.children.isEmpty()) return null;
        Node best = null;
        long bestNs = 0L;
        for (Node child : node.children.values()) {
            if (best == null || child.totalNs > bestNs) {
                best = child;
                bestNs = child.totalNs;
            }
        }
        return best;
    }

    private static String nodePath(Node node) {
        if (node == null) return "";
        List<String> parts = new ArrayList<>();
        Node cur = node;
        while (cur != null) {
            parts.add(cur.name);
            cur = cur.parent;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = parts.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static final class Section implements AutoCloseable {
        private static final Section NOOP = new Section(false);
        private final boolean active;

        private Section(boolean active) {
            this.active = active;
        }

        @Override
        public void close() {
            if (!active) return;
            endSection();
        }
    }

    private static final class Node {
        final String name;
        final Node parent;
        final Map<String, Node> children = new HashMap<>();
        long totalNs;
        long startNs;
        int calls;

        private Node(String name, Node parent) {
            this.name = name;
            this.parent = parent;
        }

        int countNodes() {
            int count = 1;
            for (Node child : children.values()) {
                count += child.countNodes();
            }
            return count;
        }
    }
}
