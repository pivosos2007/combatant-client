/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import combatant.client.render.engine.renderer.ui.runtime.action.UiActionRegistry;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRegistry;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeDiagnostics;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.renderer.ui.runtime.error.UiErrorPolicy;
import combatant.client.render.engine.renderer.ui.runtime.input.UiInputDispatcher;
import combatant.client.render.engine.renderer.ui.runtime.layout.UiLayoutEngine;
import combatant.client.render.engine.renderer.ui.runtime.layout.UiScrollRuntime;
import combatant.client.render.engine.renderer.ui.runtime.reconcile.UiDiffStats;
import combatant.client.render.engine.renderer.ui.runtime.reconcile.UiReconciler;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderer;
import combatant.client.render.engine.renderer.ui.runtime.render.UiTextRenderer;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyleCache;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyleResolver;
import combatant.client.render.engine.text.TextRenderer;

import java.util.Map;
import java.util.List;

/**
 * Owns the current runtime UI tree and the base services around it.
 *
 * <p>The runtime holds reconciliation, layout, rendering, input dispatch,
 * action registry, asset registry, style cache, and diagnostics. It has no
 * dependency on a concrete HUD element, screen, module, or config system.</p>
 */
public final class UiRuntime {
    private final UiTextRenderer textRenderer;
    private final UiLayoutEngine layoutEngine;
    private final UiRenderer renderer;
    private final UiReconciler reconciler;
    private final UiActionRegistry actionRegistry;
    private final UiAssetRegistry assetRegistry;
    private final UiInputDispatcher inputDispatcher;
    private final UiStyleCache styleCache;
    private final UiStyleResolver styleResolver;
    private final UiRuntimeDiagnostics diagnostics = new UiRuntimeDiagnostics();
    private final Object2ObjectOpenHashMap<String, ObjectArrayList<UiNode>> nodesByKey = new Object2ObjectOpenHashMap<>();
    private UiErrorPolicy errorPolicy = UiErrorPolicy.KEEP_LAST_SUCCESSFUL;
    private UiNode lastSuccessfulRoot;
    private UiDocument document;
    private UiNode root;
    private TextRenderer lastLayoutTextRenderer;
    private float lastLayoutX;
    private float lastLayoutY;
    private float lastLayoutWidth;
    private float lastLayoutHeight;
    private boolean hasLayout;
    private boolean propPatchRequiresLayout;
    private long treeRevision;
    private long legacyPropRevision;

    public UiRuntime() {
        this.textRenderer = new UiTextRenderer();
        this.layoutEngine = new UiLayoutEngine(textRenderer);
        this.assetRegistry = new UiAssetRegistry();
        this.renderer = new UiRenderer(textRenderer, assetRegistry);
        this.reconciler = new UiReconciler();
        this.actionRegistry = new UiActionRegistry();
        this.inputDispatcher = new UiInputDispatcher(actionRegistry, diagnostics.counters());
        this.styleCache = new UiStyleCache();
        this.styleResolver = new UiStyleResolver(styleCache);
    }

    private static int countNodes(UiNode node) {
        if (node == null) return 0;
        int count = 1;
        for (UiNode child : node.children()) {
            count += countNodes(child);
        }
        return count;
    }

    /**
     * Replaces the current document and applies its root spec.
     */
    public void setDocument(UiDocument document) {
        this.document = document;
        setTree(document != null ? document.root() : null);
    }

    public void setTree(UiNodeSpec spec) {
        try {
            UiTreeValidator.validate(spec);
            long styleStart = System.nanoTime();
            UiNodeSpec resolved = resolveStyles(spec);
            diagnostics.counters().setStyleNanos(System.nanoTime() - styleStart);
            long reconcileStart = System.nanoTime();
            this.root = reconciler.reconcile(root, resolved);
            rebuildKeyIndex();
            treeRevision++;
            renderer.compile(root);
            diagnostics.counters().setReconcileNanos(System.nanoTime() - reconcileStart);
            this.lastSuccessfulRoot = root;
            diagnostics.counters().setNodeCount(countNodes(root));
            diagnostics.counters().setLastError("");
        } catch (RuntimeException e) {
            diagnostics.counters().setLastError(e.getMessage());
            // Authoring errors must surface at the first bad tree in development. The release
            // fallback remains KEEP_LAST_SUCCESSFUL so a broken optional UI cannot take down play.
            if (UiRuntimeValidation.enabled() || errorPolicy == UiErrorPolicy.THROW) throw e;
            if (errorPolicy == UiErrorPolicy.KEEP_LAST_SUCCESSFUL) {
                this.root = lastSuccessfulRoot;
                rebuildKeyIndex();
                treeRevision++;
                renderer.compile(root);
            }
        }
    }

    /**
     * Last document passed through {@link #setDocument(UiDocument)}.
     */
    public UiDocument document() {
        return document;
    }

    /**
     * Current root runtime node after reconciliation.
     */
    public UiNode root() {
        return root;
    }

    /**
     * Statistics from the last reconciliation pass.
     */
    public UiDiffStats lastDiffStats() {
        return reconciler.lastStats();
    }

    /**
     * Cache for parsed class-string styles.
     */
    public UiStyleCache styleCache() {
        return styleCache;
    }

    /**
     * Registry for string action references.
     */
    public UiActionRegistry actions() {
        return actionRegistry;
    }

    /**
     * Registry for dynamic asset providers.
     */
    public UiAssetRegistry assets() {
        return assetRegistry;
    }

    /**
     * Input dispatcher for hover, click, scroll, and focus state.
     */
    public UiInputDispatcher input() {
        return inputDispatcher;
    }

    /**
     * Diagnostics counters and tree dumping.
     */
    public UiRuntimeDiagnostics diagnostics() {
        return diagnostics;
    }

    public void setErrorPolicy(UiErrorPolicy errorPolicy) {
        this.errorPolicy = errorPolicy != null ? errorPolicy : UiErrorPolicy.KEEP_LAST_SUCCESSFUL;
    }

    public void layout(TextRenderer fallbackTextRenderer, float x, float y, float width, float height) {
        long start = System.nanoTime();
        layoutEngine.layout(root, fallbackTextRenderer, x, y, width, height);
        lastLayoutTextRenderer = fallbackTextRenderer;
        lastLayoutX = x;
        lastLayoutY = y;
        lastLayoutWidth = width;
        lastLayoutHeight = height;
        hasLayout = true;
        diagnostics.counters().setLayoutNanos(System.nanoTime() - start);
    }

    /**
     * Patches props on existing runtime nodes matched by stable keys.
     *
     * <p>This is the cheap path for script templates whose structure did not
     * change but text/progress/value props did. It avoids JS execution, object
     * conversion, style resolution, and reconciliation.</p>
     */
    public int patchPropsByKey(Map<String, ? extends Map<String, ?>> patches) {
        propPatchRequiresLayout = false;
        if (root == null || patches == null || patches.isEmpty()) {
            diagnostics.counters().setPropPatchCount(0);
            return 0;
        }
        long start = System.nanoTime();
        int patched = patchPropsByKeyIndexed(patches);
        // A map-based patch may overwrite a cached typed slot. Invalidate
        // optimistic value equality so the next typed write restores it.
        if (patched != 0) legacyPropRevision++;
        diagnostics.counters().addPatchNanos(System.nanoTime() - start);
        diagnostics.counters().setPropPatchCount(patched);
        return patched;
    }

    /** Applies a dynamic property patch and returns the number of changed properties. */
    public int applyDynamicProps(UiDynamicPatchBuffer patches) {
        propPatchRequiresLayout = false;
        if (root == null || patches == null) {
            diagnostics.counters().setPropPatchCount(0);
            return 0;
        }
        long start = System.nanoTime();
        patches.applyProps(this);
        propPatchRequiresLayout = patches.requiresLayout();
        diagnostics.counters().addPatchNanos(System.nanoTime() - start);
        diagnostics.counters().setPropPatchCount(patches.propChanges());
        return patches.propChanges();
    }

    public int applyDynamicBounds(UiDynamicPatchBuffer patches) {
        if (root == null || patches == null) {
            diagnostics.counters().setBoundsPatchCount(0);
            return 0;
        }
        long start = System.nanoTime();
        patches.applyBounds(this);
        diagnostics.counters().addPatchNanos(System.nanoTime() - start);
        diagnostics.counters().setBoundsPatchCount(patches.boundsChanges());
        return patches.boundsChanges();
    }

    /** Monotonically increasing revision of the retained UI tree after reconciliation. */
    public long treeRevision() { return treeRevision; }
    long legacyPropRevision() { return legacyPropRevision; }

    List<UiNode> nodesForDynamicPatch(String key, String kind) {
        ObjectArrayList<UiNode> nodes = nodesByKey.get(key);
        validatePatchTarget(key, nodes, kind);
        return nodes != null ? nodes : List.of();
    }

    /** True only when the latest prop patch changed a value read by UiLayoutEngine. */
    public boolean lastPropPatchRequiresLayout() {
        return propPatchRequiresLayout;
    }

    public int patchBoundsByKey(Map<String, UiBounds> patches) {
        if (root == null || patches == null || patches.isEmpty()) {
            diagnostics.counters().setBoundsPatchCount(0);
            return 0;
        }
        long start = System.nanoTime();
        int patched = patchBoundsByKeyIndexed(patches);
        diagnostics.counters().addPatchNanos(System.nanoTime() - start);
        diagnostics.counters().setBoundsPatchCount(patched);
        return patched;
    }

    private int patchPropsByKeyIndexed(Map<String, ? extends Map<String, ?>> patches) {
        int patched = 0;
        for (Map.Entry<String, ? extends Map<String, ?>> entry : patches.entrySet()) {
            String key = entry.getKey();
            Map<String, ?> patch = entry.getValue();
            if (key == null || key.isBlank() || patch == null || patch.isEmpty()) continue;
            ObjectArrayList<UiNode> nodes = nodesByKey.get(key);
            validatePatchTarget(key, nodes, "props");
            if (nodes == null || nodes.isEmpty()) continue;
            for (UiNode node : nodes) {
                // Check the small set of layout dependencies BEFORE mutating the
                // runtime-owned prop slots. No per-frame props/map allocation.
                boolean geometryChanged = !propPatchRequiresLayout
                        && UiPropLayoutDependency.requiresLayout(node.type(), node.props(), patch);
                if (node.patchProps(patch)) {
                    if (geometryChanged) propPatchRequiresLayout = true;
                    patched++;
                }
            }
        }
        return patched;
    }

    private int patchBoundsByKeyIndexed(Map<String, UiBounds> patches) {
        int patched = 0;
        for (Map.Entry<String, UiBounds> entry : patches.entrySet()) {
            String key = entry.getKey();
            UiBounds bounds = entry.getValue();
            if (key == null || key.isBlank() || bounds == null) continue;
            ObjectArrayList<UiNode> nodes = nodesByKey.get(key);
            validatePatchTarget(key, nodes, "bounds");
            if (nodes == null || nodes.isEmpty()) continue;
            for (UiNode node : nodes) {
                if (!bounds.equals(node.bounds())) {
                    node.setBounds(bounds);
                    patched++;
                }
            }
        }
        return patched;
    }

    private static void validatePatchTarget(String key, ObjectArrayList<UiNode> nodes, String patchKind) {
        if (!UiRuntimeValidation.enabled()) return;
        if (nodes == null || nodes.isEmpty()) {
            UiRuntimeValidation.warnOnce(
                    "missing-patch:" + patchKind + ":" + key,
                    "UI " + patchKind + " patch targets missing key '" + key + "'."
            );
            return;
        }
        if (nodes.size() > 1) {
            throw UiRuntimeValidation.invalid(
                    "UI " + patchKind + " patch key '" + key + "' is ambiguous: " + nodes.size() + " runtime nodes share it."
            );
        }
    }

    private void rebuildKeyIndex() {
        nodesByKey.clear();
        indexNode(root);
    }

    private void indexNode(UiNode node) {
        if (node == null) return;
        String key = node.key();
        if (key != null && !key.isBlank()) {
            ObjectArrayList<UiNode> list = nodesByKey.get(key);
            if (list == null) {
                list = new ObjectArrayList<>(1);
                nodesByKey.put(key, list);
            }
            list.add(node);
        }
        for (UiNode child : node.children()) {
            indexNode(child);
        }
    }

    public void render(UiRenderContext context) {
        render(context, null);
    }

    /**
     * Invokes paint-only retained-node updates after the smooth-scroll layout,
     * but before drawing. Callers that animate node bounds must use this phase:
     * applying patches before render() lets the scroll tick erase them for one frame.
     * This callback must not reconcile the tree or trigger another layout pass.
     */
    public void render(UiRenderContext context, Runnable afterScrollLayout) {
        long start = System.nanoTime();
        if (hasLayout && UiScrollRuntime.tick(root, System.nanoTime())) {
            layoutEngine.layout(root, lastLayoutTextRenderer, lastLayoutX, lastLayoutY, lastLayoutWidth, lastLayoutHeight);
        }
        inputDispatcher.setCoordinateTransform(context != null ? context.transform() : null);
        if (afterScrollLayout != null) afterScrollLayout.run();
        renderer.render(root, context);
        diagnostics.counters().setRenderNanos(System.nanoTime() - start);
    }

    private UiNodeSpec resolveStyles(UiNodeSpec spec) {
        if (spec == null) return null;
        ObjectArrayList<UiNodeSpec> children = new ObjectArrayList<>(spec.children().size());
        for (UiNodeSpec child : spec.children()) {
            children.add(resolveStyles(child));
        }
        children.trim();
        return new UiNodeSpec(
                spec.key(),
                spec.type(),
                spec.props(),
                styleResolver.resolve(spec),
                spec.styleClass(),
                spec.events(),
                spec.metadata(),
                children
        );
    }
}
