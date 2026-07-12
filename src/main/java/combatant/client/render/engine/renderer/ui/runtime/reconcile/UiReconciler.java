/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.reconcile;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;

import java.util.List;

public final class UiReconciler {
    private final UiNodeMatcher matcher = new UiNodeMatcher();
    private UiDiffStats lastStats = new UiDiffStats();

    public UiNode reconcile(UiNode current, UiNodeSpec spec) {
        UiReconcileContext context = new UiReconcileContext(null);
        UiNode result = reconcile(current, spec, context);
        lastStats = context.stats();
        return result;
    }

    public UiNode reconcile(UiNode current, UiNodeSpec spec, UiReconcileContext context) {
        if (spec == null) {
            if (current != null) unmountSubtree(current, context);
            return null;
        }

        UiNode node;
        if (matcher.canReuse(current, spec)) {
            node = current;
            node.updateFromSpec(spec);
            context.stats().noteReused();
            context.lifecycle().onUpdate(node);
        } else {
            if (current != null) {
                context.stats().noteReplaced();
                unmountSubtree(current, context);
            }
            node = UiNode.create(spec);
            context.stats().noteMounted();
            context.lifecycle().onMount(node);
        }

        node.replaceChildren(reconcileChildren(node, current, spec, context));
        return node;
    }

    public UiDiffStats lastStats() {
        return lastStats;
    }

    private List<UiNode> reconcileChildren(UiNode parent,
                                           UiNode previousParent,
                                           UiNodeSpec parentSpec,
                                           UiReconcileContext context) {
        List<UiNode> previousChildren = previousParent != null ? previousParent.children() : List.of();
        Object2ObjectOpenHashMap<String, UiNode> keyed = new Object2ObjectOpenHashMap<>(previousChildren.size());
        for (UiNode child : previousChildren) {
            if (!child.key().isBlank()) {
                keyed.put(child.key(), child);
            }
        }

        List<UiNodeSpec> childSpecs = parentSpec.children();
        ObjectOpenHashSet<UiNode> used = new ObjectOpenHashSet<>(childSpecs.size());
        ObjectArrayList<UiNode> next = new ObjectArrayList<>(childSpecs.size());
        for (int i = 0; i < childSpecs.size(); i++) {
            UiNodeSpec childSpec = childSpecs.get(i);
            UiNode candidate = null;
            if (!childSpec.key().isBlank()) {
                candidate = keyed.get(childSpec.key());
            } else if (i < previousChildren.size()) {
                candidate = previousChildren.get(i);
            }

            UiNode child = reconcile(candidate, childSpec, context);
            if (child != null) {
                child.setParent(parent);
                next.add(child);
                used.add(child);
            }
        }

        for (UiNode oldChild : previousChildren) {
            if (!used.contains(oldChild)) {
                unmountSubtree(oldChild, context);
            }
        }
        keyed.clear();
        used.clear();
        next.trim();
        return next;
    }

    private void unmountSubtree(UiNode node, UiReconcileContext context) {
        if (node == null) return;
        for (UiNode child : node.children()) {
            unmountSubtree(child, context);
        }
        context.stats().noteUnmounted();
        context.lifecycle().onUnmount(node);
    }
}
