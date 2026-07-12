/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.layout;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiTextRenderer;
import combatant.client.render.engine.renderer.ui.runtime.style.UiAlign;
import combatant.client.render.engine.renderer.ui.runtime.style.UiJustify;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import combatant.client.render.engine.text.TextRenderer;

public final class UiLayoutEngine {
    private final UiTextRenderer textRenderer;

    public UiLayoutEngine(UiTextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    private static float intrinsic(UiNode node, String key, float fallback) {
        return node.props().number(key, fallback);
    }

    private static float childWidth(UiNode child, float available, UiAlign align) {
        float inner = Math.max(0.0f, available - child.style().marginX());
        return align == UiAlign.STRETCH ? inner : Math.min(inner, child.measuredWidth());
    }

    private static float childHeight(UiNode child, float available, UiAlign align) {
        float inner = Math.max(0.0f, available - child.style().marginY());
        return align == UiAlign.STRETCH ? inner : Math.min(inner, child.measuredHeight());
    }

    private static float alignedStart(float start, float available, float size, UiAlign align) {
        float free = Math.max(0.0f, available - size);
        return switch (align) {
            case CENTER -> start + free * 0.5f;
            case END -> start + free;
            default -> start;
        };
    }

    private static float stackedY(UiJustify justify, float start, float available, float size) {
        float free = Math.max(0.0f, available - size);
        return switch (justify) {
            case CENTER -> start + free * 0.5f;
            case END -> start + free;
            default -> start;
        };
    }

    private static float justifyOffset(UiJustify justify, float free, float grow) {
        if (grow > 0.0f) return 0.0f;
        return switch (justify) {
            case CENTER -> free * 0.5f;
            case END -> free;
            default -> 0.0f;
        };
    }

    private static float absoluteRight(UiNode child) {
        UiStyle style = child.style();
        return (style.offsetX() != null ? style.offsetX() : 0.0f) + style.marginLeft() + child.measuredWidth() + style.marginRight();
    }

    private static float absoluteBottom(UiNode child) {
        UiStyle style = child.style();
        return (style.offsetY() != null ? style.offsetY() : 0.0f) + style.marginTop() + child.measuredHeight() + style.marginBottom();
    }

    public void layout(UiNode root,
                       TextRenderer fallbackTextRenderer,
                       float x,
                       float y,
                       float width,
                       float height) {
        if (root == null) return;
        measure(root, fallbackTextRenderer);
        float resolvedW = root.style().width() != null ? root.style().resolveWidth(root.measuredWidth()) : width;
        float resolvedH = root.style().height() != null ? root.style().resolveHeight(root.measuredHeight()) : height;
        assign(root, fallbackTextRenderer, x, y, resolvedW, resolvedH);
    }

    private void measure(UiNode node, TextRenderer fallbackTextRenderer) {
        UiStyle style = node.style();
        float contentW = 0.0f;
        float contentH = 0.0f;

        switch (node.type()) {
            case TEXT -> {
                String text = node.props().string("text", "");
                contentW = textRenderer.measureWidth(fallbackTextRenderer, text, style);
                contentH = textRenderer.measureHeight(fallbackTextRenderer, style);
            }
            case ROW -> {
                int visibleChildren = 0;
                for (UiNode child : node.children()) {
                    measure(child, fallbackTextRenderer);
                    UiStyle childStyle = child.style();
                    if (childStyle.absolute()) {
                        contentW = Math.max(contentW, absoluteRight(child));
                        contentH = Math.max(contentH, absoluteBottom(child));
                        continue;
                    }
                    contentW += child.measuredWidth() + childStyle.marginX();
                    contentH = Math.max(contentH, child.measuredHeight() + childStyle.marginY());
                    visibleChildren++;
                }
                if (visibleChildren > 1) {
                    contentW += style.gap() * (visibleChildren - 1);
                }
            }
            case COLUMN, PANEL -> {
                int visibleChildren = 0;
                for (UiNode child : node.children()) {
                    measure(child, fallbackTextRenderer);
                    UiStyle childStyle = child.style();
                    if (childStyle.absolute()) {
                        contentW = Math.max(contentW, absoluteRight(child));
                        contentH = Math.max(contentH, absoluteBottom(child));
                        continue;
                    }
                    contentW = Math.max(contentW, child.measuredWidth() + childStyle.marginX());
                    contentH += child.measuredHeight() + childStyle.marginY();
                    visibleChildren++;
                }
                if (visibleChildren > 1) {
                    contentH += style.gap() * (visibleChildren - 1);
                }
            }
            case IMAGE, SVG, SHAPE, CONNECTOR, ITEM, SPACER, DIVIDER, INPUT, INPUT_TEXT, CHECKBOX, SLIDER -> {
                contentW = intrinsic(node, "intrinsicWidth", style.width() != null ? style.width() : 16.0f);
                contentH = intrinsic(node, "intrinsicHeight", style.height() != null ? style.height() : 16.0f);
            }
            case ROOT, STACK, BUTTON, SCROLL, CANVAS -> {
                for (UiNode child : node.children()) {
                    measure(child, fallbackTextRenderer);
                    UiStyle childStyle = child.style();
                    float right = childStyle.absolute()
                            ? absoluteRight(child)
                            : child.measuredWidth() + childStyle.marginX();
                    float bottom = childStyle.absolute()
                            ? absoluteBottom(child)
                            : child.measuredHeight() + childStyle.marginY();
                    contentW = Math.max(contentW, right);
                    contentH = Math.max(contentH, bottom);
                }
            }
        }

        float measuredW = contentW + style.paddingX();
        float measuredH = contentH + style.paddingY();
        node.state().setContentSize(contentW, contentH);
        node.setMeasuredSize(style.resolveWidth(measuredW), style.resolveHeight(measuredH));
    }

    private void assign(UiNode node,
                        TextRenderer fallbackTextRenderer,
                        float x,
                        float y,
                        float width,
                        float height) {
        UiStyle style = node.style();
        float w = style.resolveWidth(width);
        float h = style.resolveHeight(height);
        node.setBounds(new UiBounds(x, y, w, h));

        float contentX = x + style.paddingLeft();
        float contentY = y + style.paddingTop();
        float contentW = Math.max(0.0f, w - style.paddingX());
        float contentH = Math.max(0.0f, h - style.paddingY());
        if (style.overflow().clips() || node.type() == UiNodeType.SCROLL) {
            contentX -= node.state().scrollX();
            contentY -= node.state().scrollY();
        }

        switch (node.type()) {
            case ROW -> assignRow(node, fallbackTextRenderer, contentX, contentY, contentW, contentH);
            case COLUMN, PANEL -> assignColumn(node, fallbackTextRenderer, contentX, contentY, contentW, contentH);
            case ROOT, STACK, BUTTON, SCROLL, INPUT, INPUT_TEXT, CHECKBOX, SLIDER, DIVIDER, CANVAS, IMAGE, SVG, SHAPE,
                 CONNECTOR, ITEM, SPACER, TEXT ->
                    assignStack(node, fallbackTextRenderer, contentX, contentY, contentW, contentH);
        }
    }

    private void assignRow(UiNode node,
                           TextRenderer fallbackTextRenderer,
                           float x,
                           float y,
                           float width,
                           float height) {
        int flowCount = 0;
        float fixed = 0.0f;
        float grow = 0.0f;
        for (UiNode child : node.children()) {
            UiStyle childStyle = child.style();
            if (childStyle.absolute()) continue;
            fixed += child.measuredWidth() + childStyle.marginX();
            grow += childStyle.grow();
            flowCount++;
        }
        float baseGap = flowCount > 1 ? node.style().gap() : 0.0f;
        float totalGap = baseGap * Math.max(0, flowCount - 1);
        float free = Math.max(0.0f, width - fixed - totalGap);
        float cursor = x + justifyOffset(node.style().justify(), free, grow);
        float gap = node.style().justify() == UiJustify.BETWEEN && flowCount > 1 && grow <= 0.0f
                ? baseGap + free / (flowCount - 1)
                : baseGap;
        for (UiNode child : node.children()) {
            UiStyle childStyle = child.style();
            if (childStyle.absolute()) {
                assignAbsolute(child, fallbackTextRenderer, x, y, width, height);
                continue;
            }
            float childW = child.measuredWidth();
            if (grow > 0.0f && childStyle.grow() > 0.0f) {
                childW += free * (childStyle.grow() / grow);
            }
            float childH = childHeight(child, height, node.style().align());
            float childX = cursor + childStyle.marginLeft();
            float childY = alignedStart(y + childStyle.marginTop(), height - childStyle.marginY(), childH, node.style().align());
            assign(child, fallbackTextRenderer, childX, childY, childW, childH);
            cursor += childW + childStyle.marginX() + gap;
        }
    }

    private void assignColumn(UiNode node,
                              TextRenderer fallbackTextRenderer,
                              float x,
                              float y,
                              float width,
                              float height) {
        int flowCount = 0;
        float fixed = 0.0f;
        float grow = 0.0f;
        for (UiNode child : node.children()) {
            UiStyle childStyle = child.style();
            if (childStyle.absolute()) continue;
            fixed += child.measuredHeight() + childStyle.marginY();
            grow += childStyle.grow();
            flowCount++;
        }
        float baseGap = flowCount > 1 ? node.style().gap() : 0.0f;
        float totalGap = baseGap * Math.max(0, flowCount - 1);
        float free = Math.max(0.0f, height - fixed - totalGap);
        float cursor = y + justifyOffset(node.style().justify(), free, grow);
        float gap = node.style().justify() == UiJustify.BETWEEN && flowCount > 1 && grow <= 0.0f
                ? baseGap + free / (flowCount - 1)
                : baseGap;
        for (UiNode child : node.children()) {
            UiStyle childStyle = child.style();
            if (childStyle.absolute()) {
                assignAbsolute(child, fallbackTextRenderer, x, y, width, height);
                continue;
            }
            float childW = childWidth(child, width, node.style().align());
            float childH = child.measuredHeight();
            if (grow > 0.0f && childStyle.grow() > 0.0f) {
                childH += free * (childStyle.grow() / grow);
            }
            float childX = alignedStart(x + childStyle.marginLeft(), width - childStyle.marginX(), childW, node.style().align());
            float childY = cursor + childStyle.marginTop();
            assign(child, fallbackTextRenderer, childX, childY, childW, childH);
            cursor += childH + childStyle.marginY() + gap;
        }
    }

    private void assignStack(UiNode node,
                             TextRenderer fallbackTextRenderer,
                             float x,
                             float y,
                             float width,
                             float height) {
        for (UiNode child : node.children()) {
            UiStyle childStyle = child.style();
            if (childStyle.absolute()) {
                assignAbsolute(child, fallbackTextRenderer, x, y, width, height);
                continue;
            }
            float childW = childWidth(child, width, node.style().align());
            float childH = Math.min(Math.max(0.0f, height - childStyle.marginY()), child.measuredHeight());
            float childX = alignedStart(x + childStyle.marginLeft(), width - childStyle.marginX(), childW, node.style().align());
            float childY = stackedY(node.style().justify(), y + childStyle.marginTop(), height - childStyle.marginY(), childH);
            assign(child, fallbackTextRenderer, childX, childY, childW, childH);
        }
    }

    private void assignAbsolute(UiNode child,
                                TextRenderer fallbackTextRenderer,
                                float x,
                                float y,
                                float width,
                                float height) {
        UiStyle style = child.style();
        float childW = style.width() != null ? style.resolveWidth(child.measuredWidth()) : Math.min(width, child.measuredWidth());
        float childH = style.height() != null ? style.resolveHeight(child.measuredHeight()) : Math.min(height, child.measuredHeight());
        float childX = x + style.marginLeft() + (style.offsetX() != null ? style.offsetX() : 0.0f);
        float childY = y + style.marginTop() + (style.offsetY() != null ? style.offsetY() : 0.0f);
        assign(child, fallbackTextRenderer, childX, childY, childW, childH);
    }
}
