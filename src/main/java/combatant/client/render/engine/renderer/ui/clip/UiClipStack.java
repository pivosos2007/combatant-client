/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.clip;

import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiShape;
import combatant.client.render.engine.renderer.ui.draw.UiShapeKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Renderer-owned logical shape-clip stack behind the {@code ClipFunction} facade. */
public final class UiClipStack {
    public static final int MAX_ANALYTIC_PRIMITIVES = 4;
    private final Deque<Layer> layers = new ArrayDeque<>();
    private final int maxDepth;
    private long nextSnapshotId = 1L;

    public UiClipStack(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public boolean canPush() {
        return layers.size() < maxDepth;
    }

    public Layer push(UiShape shape) {
        return push(shape, strategyFor(shape));
    }

    public Layer push(UiShape shape, UiClipStrategy requestedStrategy) {
        if (shape == null || !canPush()) return null;
        UiClipSnapshot parent = current();
        int parentReference = parent.stencilReference();
        int reference = parentReference + 1;
        UiClipStrategy supportedStrategy = strategyFor(shape);
        UiClipStrategy ownStrategy = requestedStrategy == UiClipStrategy.ANALYTIC
                && supportedStrategy == UiClipStrategy.ANALYTIC
                ? UiClipStrategy.ANALYTIC
                : UiClipStrategy.MSAA_STENCIL;
        UiClipStrategy strategy = parent.active() && parent.strategy() == UiClipStrategy.MSAA_STENCIL
                ? UiClipStrategy.MSAA_STENCIL
                : ownStrategy;

        List<UiShape> primitives = new ArrayList<>(parent.primitives().size() + 1);
        primitives.addAll(parent.primitives());
        primitives.add(shape);
        if (primitives.size() > MAX_ANALYTIC_PRIMITIVES) {
            strategy = UiClipStrategy.MSAA_STENCIL;
        }
        UiRect bounds = parent.active() ? intersect(parent.logicalBounds(), shape.bounds()) : shape.bounds();
        UiClipSnapshot snapshot = new UiClipSnapshot(
                allocateSnapshotId(), strategy, bounds, primitives, reference,
                strategy == UiClipStrategy.MSAA_STENCIL ? 4 : 1
        );
        Layer layer = new Layer(shape, reference, parentReference, snapshot);
        layers.push(layer);
        return layer;
    }

    public Layer pop() {
        return layers.poll();
    }

    public UiClipSnapshot current() {
        Layer layer = layers.peek();
        return layer != null ? layer.snapshot() : UiClipSnapshot.NONE;
    }

    public int depth() {
        return layers.size();
    }

    public int currentReference() {
        return current().stencilReference();
    }

    private long allocateSnapshotId() {
        long id = nextSnapshotId++;
        if (id == 0L) id = nextSnapshotId++;
        return id;
    }

    private static UiClipStrategy strategyFor(UiShape shape) {
        UiShapeKind kind = shape.kind();
        return kind == UiShapeKind.RECT || kind == UiShapeKind.CIRCLE
                ? UiClipStrategy.ANALYTIC
                : UiClipStrategy.MSAA_STENCIL;
    }

    private static UiRect intersect(UiRect a, UiRect b) {
        float x1 = Math.max(a.x(), b.x());
        float y1 = Math.max(a.y(), b.y());
        float x2 = Math.min(a.x() + a.width(), b.x() + b.width());
        float y2 = Math.min(a.y() + a.height(), b.y() + b.height());
        return new UiRect(x1, y1, Math.max(0f, x2 - x1), Math.max(0f, y2 - y1));
    }

    public record Layer(UiShape shape, int reference, int parentReference, UiClipSnapshot snapshot) {
    }
}
