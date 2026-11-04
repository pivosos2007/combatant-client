/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

/**
 * A flat, preallocated instruction tape for a reconciled UI tree.
 *
 * <p>Node references are deliberately retained, not snapshots: layout, props, animation,
 * scroll and input state remain live. Recompile after reconciliation changes the tree.
 * Each node contributes ENTER and LEAVE instructions; the ENTER's jump points past the
 * corresponding LEAVE so invisible/clipped subtrees can be skipped in constant time.
 * No per-frame traversal, boxing, collections or command-list rebuilding is required.</p>
 *
 * <p>Owned by the render thread. Neither compilation nor execution touches V8.</p>
 */
final class UiRenderProgram {
    static final byte ENTER = 0;
    static final byte LEAVE = 1;
    static final byte CLIP = 1;
    static final byte VECTOR = 2;
    static final byte SCROLL = 4;
    static final byte DRAW_NONE = 0;
    static final byte DRAW_TEXT = 1;
    static final byte DRAW_ASSET = 2;
    static final byte DRAW_SHAPE = 3;
    static final byte DRAW_CONNECTOR = 4;
    static final byte DRAW_PATH = 5;
    static final byte DRAW_ITEM = 6;

    final UiNode root;
    final UiNode[] nodes;
    final byte[] operation;
    final byte[] flags;
    final byte[] drawCode;
    final int[] jump;
    final UiRenderContext[] nodeContexts;
    final UiRenderContext[] childContexts;
    final boolean[] pushedClips;
    final int length;

    private UiRenderProgram(UiNode root, int count, int maxDepth) {
        this.root = root;
        nodes = new UiNode[count * 2];
        operation = new byte[count * 2];
        flags = new byte[count * 2];
        drawCode = new byte[count * 2];
        jump = new int[count * 2];
        nodeContexts = new UiRenderContext[maxDepth + 1];
        childContexts = new UiRenderContext[maxDepth + 1];
        pushedClips = new boolean[maxDepth + 1];
        length = nodes.length;
    }

    static UiRenderProgram compile(UiNode root) {
        if (root == null) return null;
        Size size = new Size();
        measure(root, 0, size);
        UiRenderProgram program = new UiRenderProgram(root, size.nodes, size.maxDepth);
        int end = append(root, program, 0);
        if (end != program.length) throw new IllegalStateException("Invalid UI render tape length");
        return program;
    }

    private static void measure(UiNode node, int depth, Size size) {
        size.nodes++;
        size.maxDepth = Math.max(size.maxDepth, depth);
        for (UiNode child : node.children()) measure(child, depth + 1, size);
    }

    private static int append(UiNode node, UiRenderProgram program, int index) {
        int enter = index++;
        program.operation[enter] = ENTER;
        program.nodes[enter] = node;
        UiStyle style = node.style();
        byte flags = 0;
        if (style.clip() || style.marquee()) flags |= CLIP;
        if (node.type() == UiNodeType.VECTOR) flags |= VECTOR;
        if (node.type() == UiNodeType.SCROLL) flags |= SCROLL;
        program.flags[enter] = flags;
        program.drawCode[enter] = switch (node.type()) {
            case TEXT -> DRAW_TEXT;
            case IMAGE, SVG -> DRAW_ASSET;
            case SHAPE -> DRAW_SHAPE;
            case CONNECTOR -> DRAW_CONNECTOR;
            case PATH -> DRAW_PATH;
            case ITEM -> DRAW_ITEM;
            default -> DRAW_NONE;
        };
        for (UiNode child : node.children()) index = append(child, program, index);
        program.operation[index] = LEAVE;
        program.nodes[index] = node;
        program.flags[index] = flags;
        program.jump[enter] = index + 1;
        return index + 1;
    }

    private static final class Size {
        int nodes;
        int maxDepth;
    }
}
