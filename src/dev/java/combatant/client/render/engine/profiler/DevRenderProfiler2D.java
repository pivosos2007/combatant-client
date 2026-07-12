package combatant.client.render.engine.profiler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import combatant.client.render.engine.command.UiStatsSnapshot;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.resource.RenderResourceStatsSnapshot;
import combatant.client.render.engine.rhi.uniform.UniformAllocatorStatsSnapshot;
import combatant.client.render.engine.text.TextCommandStatsSnapshot;
import combatant.client.render.engine.text.TextRenderSystem;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.runtime.RuntimeGate;

import java.util.*;

public enum DevRenderProfiler2D {
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
        DevSamplingProfiler.onClientFrame(DevSamplingProfiler.Target.UI);
        if (!isEnabled()) return;
        CachedUiScriptRuntime.resetFrameStats();
        MeshBuilder.resetFrameStats();
        active = true;
        root = new Node(name, null);
        stack.clear();
        stack.push(root);
        root.startNs = System.nanoTime();
    }

    public static boolean beginFrameIfNeeded(String name) {
        DevSamplingProfiler.onClientFrame(DevSamplingProfiler.Target.UI);
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
            DevTracyProfiler.plotUiFrame(r.totalNs / (double) NS_IN_MS, r.countNodes());
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
        lines.add(String.format("ui prof: %.2f ms, nodes %d", totalMs, snap.countNodes()));
        appendRuntimeCounters(lines);

        List<Node> flat = new ArrayList<>();
        collectFlat(snap, flat);
        flat.sort(Comparator.comparingLong((Node n) -> n.totalNs).reversed());

        int top = Math.min(topLimit, flat.size());
        if (top > 0) {
            lines.add("ui prof top:");
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

        lines.add("ui prof tree:");
        appendTreeAscii(lines, snap, "", 0, depthLimit, minMs);
        return lines;
    }

    private static void appendRuntimeCounters(List<String> lines) {
        CachedUiScriptRuntime.FrameStatsSnapshot ui = CachedUiScriptRuntime.frameStatsSnapshot();
        if (ui.bakeCalls() > 0 || ui.jsRenders() > 0 || ui.layoutPasses() > 0 || ui.propPatches() > 0 || ui.boundsPatches() > 0) {
            lines.add(String.format(
                    "ui runtime: bake %d, js %d, layout %d, propPatch %d/%d, boundsPatch %d/%d, blocked %d, fail %d",
                    ui.bakeCalls(),
                    ui.jsRenders(),
                    ui.layoutPasses(),
                    ui.propPatchPasses(),
                    ui.propPatches(),
                    ui.boundsPatchPasses(),
                    ui.boundsPatches(),
                    ui.blockedCalls(),
                    ui.failedRenders()
            ));
        }

        TextCommandStatsSnapshot textStats = CombatantRenderSystem.textStatsSnapshot();
        if (textStats.recorded() > 0 || textStats.meshUploads() > 0 || textStats.glyphs() > 0) {
            lines.add(String.format(
                    "text: rec/sub %d/%d, backend vanilla/bitmap/msdf/world %d/%d/%d/%d, effect/clip %d/%d, glyphs %d, meshUploads %d, router hit/miss/fallback %d/%d/%d, adjacent %d, uploaded v/i %s/%s",
                    textStats.recorded(),
                    textStats.submitted(),
                    textStats.vanilla(),
                    textStats.bitmap(),
                    textStats.msdf(),
                    textStats.world(),
                    textStats.effect(),
                    textStats.clipped(),
                    textStats.glyphs(),
                    textStats.meshUploads(),
                    textStats.routerCacheHits(),
                    textStats.routerCacheMisses(),
                    textStats.routerFallbackMisses(),
                    textStats.adjacentBatchedCommands(),
                    formatBytes(textStats.uploadedVertexBytes()),
                    formatBytes(textStats.uploadedIndexBytes())
            ));
        }
        var glyphStats = TextRenderSystem.glyphAtlases().stats();
        if (glyphStats.pageCount() > 0 || glyphStats.dirtyUploads() > 0) {
            lines.add(String.format(
                    "glyph atlas: pages %d, glyphs %d, dirtyUploads %d, uploaded %s",
                    glyphStats.pageCount(),
                    glyphStats.glyphCount(),
                    glyphStats.dirtyUploads(),
                    formatBytes(glyphStats.uploadedBytes())
            ));
        }

        UiStatsSnapshot uiDraw = Renderer2D.getUiStatsSnapshot();
        if (uiDraw.recordedCommands() > 0 || uiDraw.compiledBatches() > 0) {
            lines.add(String.format(
                    "ui commands: total %d, shape/path/tex/text/item/effect %d/%d/%d/%d/%d/%d, compiled %d, backend %d",
                    uiDraw.recordedCommands(),
                    uiDraw.shapeCommands(),
                    uiDraw.pathCommands(),
                    uiDraw.textureCommands(),
                    uiDraw.textCommands(),
                    uiDraw.itemCommands(),
                    uiDraw.effectCommands(),
                    uiDraw.compiledBatches(),
                    uiDraw.backendCommands()
            ));
        }

        Renderer2D.BatchStats batch = Renderer2D.getBatchStats();
        int frameBatches = batch.getFrameBatches();
        int frameDraws = batch.getFrameDraws();
        int frameVertices = batch.getFrameVertices();
        int frameIndices = batch.getFrameIndices();
        Map<Renderer2D.FlushReason, Integer> flushReasons = batch.getFrameFlushReasons();
        if (frameBatches > 0 || frameDraws > 0 || frameVertices > 0 || frameIndices > 0 || !flushReasons.isEmpty()) {
            lines.add(String.format(
                    "ui batch: batches %d, draws %d, vertices %d, indices %d, pool %d, last %d/%d/%d, flush %s %s",
                    frameBatches,
                    frameDraws,
                    frameVertices,
                    frameIndices,
                    batch.getPoolTotal(),
                    batch.getLastOrder(),
                    batch.getLastDraws(),
                    batch.getLastVertices(),
                    batch.getLastFlushReason(),
                    formatFlushReasons(flushReasons)
            ));
        }
        if (!batch.getLastError().isEmpty()) {
            lines.add("ui batch error: " + batch.getLastError());
        }

        MeshBuilder.FrameStatsSnapshot mesh = MeshBuilder.frameStatsSnapshot();
        if (mesh.initialAllocations() > 0 || mesh.vertexReallocs() > 0 || mesh.indexReallocs() > 0) {
            lines.add(String.format(
                    "ui mesh: alloc %d, realloc v/i %d/%d, bytes %s, max v/i %s/%s",
                    mesh.initialAllocations(),
                    mesh.vertexReallocs(),
                    mesh.indexReallocs(),
                    formatBytes(mesh.allocatedBytes()),
                    formatBytes(mesh.maxVertexBytes()),
                    formatBytes(mesh.maxIndexBytes())
            ));
        }

        appendRhiCounters(lines);
        var immediatelyFast = CombatantRenderSystem.immediatelyFastSnapshot();
        if (immediatelyFast.modLoaded()) {
            lines.add(immediatelyFast.shortLine());
        }
        appendCostCounters(lines);
    }

    private static String formatFlushReasons(Map<Renderer2D.FlushReason, Integer> reasons) {
        if (reasons == null || reasons.isEmpty()) return "{}";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Renderer2D.FlushReason, Integer> entry : reasons.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                parts.add(entry.getKey().name().toLowerCase(Locale.ROOT) + "=" + entry.getValue());
            }
        }
        return parts.isEmpty() ? "{}" : "{" + String.join(",", parts) + "}";
    }

    private static void appendCostCounters(List<String> lines) {
        List<String> phase = DevRenderCostProfiler.debugLines("render cost phases", "phase", 6, 0.02);
        if (!phase.isEmpty()) lines.addAll(phase);
        List<String> runtime = DevRenderCostProfiler.debugLines("ui cost runtime", "ui.runtime", 8, 0.02);
        if (!runtime.isEmpty()) lines.addAll(runtime);
        List<String> nodes = DevRenderCostProfiler.debugLines("ui cost nodes", "ui.node", 10, 0.02);
        if (!nodes.isEmpty()) lines.addAll(nodes);
        List<String> effects = DevRenderCostProfiler.debugLines("ui cost effects", "ui.effect", 8, 0.02);
        if (!effects.isEmpty()) lines.addAll(effects);
        List<String> items = DevRenderCostProfiler.debugLines("ui cost item render", "item.render", 8, 0.02);
        if (!items.isEmpty()) lines.addAll(items);
        List<String> rhi = DevRenderCostProfiler.debugLines("ui cost rhi draws", "rhi.draw", 8, 0.02);
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
        return ProfilerSettings.getOutput2d();
    }

    private static void emitToLog() {
        List<String> lines = getDebugLines();
        if (lines.isEmpty()) return;
        for (String line : lines) {
            ProfilerLog.info("[UIProfiler] %s", line);
        }
    }

    private static void emitToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        List<String> lines = getDebugLines(4, 2, 0.05);
        if (lines.isEmpty()) return;
        for (String line : lines) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[UIProfiler] " + line));
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
