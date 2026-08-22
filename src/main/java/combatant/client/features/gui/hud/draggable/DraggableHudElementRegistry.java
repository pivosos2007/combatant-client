/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable;


import combatant.client.features.theme.Theme;
import combatant.client.features.gui.hud.*;
import combatant.client.features.module.HudPhase;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.chat.BetterChatRenderer;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.profiler.RenderProfiler2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.input.KeyManager;
import combatant.client.util.logging.DebugLog;

import java.util.*;

/**
 * Реестр HUD-виджетов + drag-and-drop при открытом чате.
 */
public enum DraggableHudElementRegistry {
    ;

    private static final List<DraggableHudElement> WIDGETS = new ArrayList<>();
    private static final float SNAP_DIST = 8f;
    private static final float PARENT_SNAP_DIST = 10f;
    private static final float PARENT_ALIGN_SNAP_DIST = 5f;
    private static final float PARENT_GAP = 6f;
    private static final int SNAP_PREVIEW_LINE = 0xFFFFFFFF;
    private static final float DRAG_COORD_TEXT_SCALE = 0.85f;
    private static final float DRAG_COORD_MARGIN = 4f;
    private static final float LINK_WIRE_THICKNESS = 1.05f;
    private static final float LINK_PREVIEW_THICKNESS = 1.55f;
    private static DraggableHudElement dragging;
    private static float dragOffsetX;
    private static float dragOffsetY;
    private static boolean draggingWithLeft;
    private static boolean prevLeft;
    private static boolean prevMiddle;
    private static long renderOrderSeq;
    private static boolean forceVisible;
    private static boolean snapPreviewActive;
    private static boolean snapPreviewHasX;
    private static boolean snapPreviewHasY;
    private static float snapPreviewLineX;
    private static float snapPreviewLineY;
    private static String editorWidgetId;
    private static boolean hudCursorActive;
    private static int lastScreenW = -1;
    private static int lastScreenH = -1;
    private static Themes.Theme cachedLinkTheme;
    private static LinkWireColors cachedLinkColors;

    public static void register(DraggableHudElement widget) {
        if (!WIDGETS.contains(widget)) {
            widget.postInit();
            WIDGETS.add(widget);
            DebugLog.config("[HUD] Registered widget: %s", widget.getId());
        }
    }

    public static void clearRuntimeReferences() {
        cancelDragging();
        WIDGETS.clear();
        editorWidgetId = null;
        forceVisible = false;
        cachedLinkTheme = null;
        cachedLinkColors = null;
        clearSnapPreview();
    }

    public static List<DraggableHudElement> getWidgets() {
        return Collections.unmodifiableList(WIDGETS);
    }

    public static DraggableHudElement getById(String id) {
        return findById(id);
    }

    public static boolean isEnabled(String id) {
        if (!RuntimeGate.canRunHud()) return false;
        DraggableHudElement w = findById(id);
        return w != null && w.isEnabled();
    }

    public static <T extends DraggableHudElement> T get(Class<T> type) {
        if (type == null) return null;
        for (DraggableHudElement w : WIDGETS) {
            if (type.isInstance(w)) {
                return type.cast(w);
            }
        }
        return null;
    }

    public static <T extends DraggableHudElement> T widget(Class<T> type) {
        return get(type);
    }

    public static boolean isEnabled(Class<? extends DraggableHudElement> type) {
        if (!RuntimeGate.canRunHud()) return false;
        DraggableHudElement w = get(type);
        return w != null && w.isEnabled();
    }

    public static void tickAll() {
        if (!ProfilerPhase.isActive()) {
            for (DraggableHudElement w : WIDGETS) {
                w.onTick();
            }
            return;
        }

        try (ProfilerPhase.Scope widgetsScope = ProfilerPhase.scope("hud_widgets:tick")) {
            for (DraggableHudElement w : WIDGETS) {
                try (ProfilerPhase.Scope widgetScope = ProfilerPhase.scope("hud_widget:tick:" + w.getId())) {
                    w.onTick();
                }
            }
        }
    }

    public static boolean isForceVisible() {
        return forceVisible;
    }

    public static void setForceVisible(boolean value) {
        forceVisible = value;
    }

    public static void setEditorWidgetId(String id) {
        editorWidgetId = id;
    }

    public static boolean hasEngineWork(HudPhase phase, boolean foreground) {
        if (!RuntimeGate.canRunHud()) return false;
        if (foreground && dragging != null) return true;

        for (DraggableHudElement w : WIDGETS) {
            if (isRenderableCandidate(w, phase)) return true;
        }
        return false;
    }

    public static boolean hasEngineWidgetWork(String id) {
        if (!RuntimeGate.canRunHud()) return false;
        if (id == null || id.isBlank()) return false;
        if (editorWidgetId != null && !editorWidgetId.equals(id)) return false;

        DraggableHudElement w = findById(id);
        if (w == null) return false;
        if (!w.usesEngineRenderer()) return false;
        boolean bypassEnabled = forceVisible && editorWidgetId != null && editorWidgetId.equals(id);
        return bypassEnabled || w.isEnabled() || w.shouldRenderWhenDisabled();
    }

    public static void renderAllEngine(HudPhase phase,
                                       Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       net.minecraft.client.gui.GuiGraphicsExtractor ctx,
                                       float tickDelta) {
        if (!RuntimeGate.canRunHud()) {
            cancelDragging();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        boolean allowOnlyModuleList = false;

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        int screenW = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
        int screenH = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));
        handleScreenResize(screenW, screenH);

        List<DraggableHudElement> renderList = new ArrayList<>();
        for (DraggableHudElement w : WIDGETS) {
            boolean singleEditorPreview = editorWidgetId != null;
            if (singleEditorPreview && (editorWidgetId.isEmpty() || !editorWidgetId.equals(w.getId()))) {
                continue;
            }
            boolean bypassEnabled = singleEditorPreview && forceVisible;
            if (!bypassEnabled && !w.isEnabled() && !w.shouldRenderWhenDisabled()) {
                continue;
            }
            if (!w.usesEngineRenderer()) {
                continue;
            }
            if (w.getHudPhase() != phase) {
                continue;
            }
            if (w.getRenderSpace() != HudRenderSpace.UNSCALED_LOGICAL) {
                continue;
            }
            ensurePositionInitialized(w, screenW, screenH);
            if (allowOnlyModuleList && !"module_list".equals(w.getId())) {
                continue;
            }
            renderList.add(w);
        }

        for (DraggableHudElement w : renderList) {
            if (w == dragging) continue;
            if (w.getParentId() == null || w.getParentSide() == WidgetAnchorSide.NONE) {
                applyScreenAnchor(w, screenW, screenH);
            }
        }
        for (DraggableHudElement w : renderList) {
            if (w == dragging) continue;
            if (w.getParentId() != null && w.getParentSide() != WidgetAnchorSide.NONE) {
                applyAnchors(w, screenW, screenH, new HashSet<>());
            }
        }

        for (DraggableHudElement w : renderList) {
            noteRendered(w);
            try (ProfilerPhase.Scope widgetScope =
                         ProfilerPhase.scope("2d:widget:" + w.getId());
                 RenderProfiler2D.Section ignored = RenderProfiler2D.section("widget:" + w.getId())) {
                w.renderEngine(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
            }
        }
    }

    public static void renderAllEngineForeground(HudPhase phase,
                                                 Renderer2D renderer,
                                                 TextRenderer textRenderer,
                                                 net.minecraft.client.gui.GuiGraphicsExtractor ctx,
                                                 float tickDelta) {
        if (!RuntimeGate.canRunHud()) {
            cancelDragging();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        boolean allowOnlyModuleList = false;

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        int screenW = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
        int screenH = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));
        handleScreenResize(screenW, screenH);

        for (DraggableHudElement w : WIDGETS) {
            boolean singleEditorPreview = editorWidgetId != null;
            if (singleEditorPreview && (editorWidgetId.isEmpty() || !editorWidgetId.equals(w.getId()))) {
                continue;
            }
            boolean bypassEnabled = singleEditorPreview && forceVisible;
            if (!bypassEnabled && !w.isEnabled() && !w.shouldRenderWhenDisabled()) {
                continue;
            }
            if (!w.usesEngineRenderer()) {
                continue;
            }
            if (w.getHudPhase() != phase) {
                continue;
            }
            if (w.getRenderSpace() != HudRenderSpace.UNSCALED_LOGICAL) {
                continue;
            }
            if (allowOnlyModuleList && !"module_list".equals(w.getId())) {
                continue;
            }
            try (ProfilerPhase.Scope widgetScope =
                         ProfilerPhase.scope("2d:widget_fg:" + w.getId());
                 RenderProfiler2D.Section ignored = RenderProfiler2D.section("widget_fg:" + w.getId())) {
                w.renderEngineForeground(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
            }
        }

        renderDragOverlay(renderer, textRenderer, screenW, screenH);
        BetterChatRenderer.renderCapturedTooltip(ctx);
        if (phase == HudPhase.AFTER_SUBTITLES) {
            float uiScale = HudScale.scale(fbw, fbh);
            handleDragging(mc, screenW, screenH, uiScale);
        }
    }

    public static void renderNativeHudOverlays(HudPhase phase,
                                               net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
        if (!RuntimeGate.canRunHud() || ctx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        int screenW = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
        int screenH = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));

        for (DraggableHudElement w : WIDGETS) {
            if (w.getHudPhase() != phase || w.getRenderSpace() != HudRenderSpace.UNSCALED_LOGICAL) {
                continue;
            }
            w.renderNativeHudOverlay(ctx, screenW, screenH);
        }
    }

    public static void renderEngineWidget(String id,
                                          Renderer2D renderer,
                                          TextRenderer textRenderer,
                                          net.minecraft.client.gui.GuiGraphicsExtractor ctx,
                                          float tickDelta) {
        if (!RuntimeGate.canRunHud()) {
            cancelDragging();
            return;
        }
        if (editorWidgetId != null && (!editorWidgetId.equals(id))) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        boolean isChat = ClientScreen.current() instanceof net.minecraft.client.gui.screens.ChatScreen;
        boolean isInventory = ClientScreen.current() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
        boolean allowOnlyModuleList = isInventory && ClientScreen.current() != null;
        if (forceVisible) {
            allowOnlyModuleList = false;
        }
        boolean shouldHideHud = !forceVisible && ClientScreen.current() != null && !isChat && !allowOnlyModuleList;
        if (shouldHideHud) {
            return;
        }

        DraggableHudElement w = findById(id);
        if (w == null) return;
        if (!w.usesEngineRenderer()) return;
        boolean bypassEnabled = forceVisible && editorWidgetId != null && editorWidgetId.equals(id);
        if (!bypassEnabled && !w.isEnabled() && !w.shouldRenderWhenDisabled()) {
            return;
        }
        if (allowOnlyModuleList && !"module_list".equals(w.getId())) {
            return;
        }

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        int screenW = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
        int screenH = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));
        handleScreenResize(screenW, screenH);

        ensurePositionInitialized(w, screenW, screenH);
        if (w != dragging) {
            if (w.getParentId() == null || w.getParentSide() == WidgetAnchorSide.NONE) {
                applyScreenAnchor(w, screenW, screenH);
            } else {
                applyAnchors(w, screenW, screenH, new HashSet<>());
            }
        }

        noteRendered(w);
        try (ProfilerPhase.Scope widgetScope =
                     ProfilerPhase.scope("2d:widget:" + w.getId());
             RenderProfiler2D.Section ignored = RenderProfiler2D.section("widget:" + w.getId())) {
            w.renderEngine(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
        }
        try (ProfilerPhase.Scope widgetScope =
                     ProfilerPhase.scope("2d:widget_fg:" + w.getId());
             RenderProfiler2D.Section ignored = RenderProfiler2D.section("widget_fg:" + w.getId())) {
            w.renderEngineForeground(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
        }
    }

    private static void ensurePositionInitialized(DraggableHudElement w, int screenW, int screenH) {
        if (w.isPositionInitialized()) return;
        DraggableLayoutValue.State saved = w.layoutValue().get();
        if (saved != null) {
            DraggableLayoutValue.State resolved = saved;
            if (!saved.logical()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getWindow() != null) {
                    int fbw = mc.getWindow().getWidth();
                    int fbh = mc.getWindow().getHeight();
                    float uiScale = HudScale.scale(fbw, fbh);
                    float nx = HudScale.toVirtual(saved.x(), uiScale);
                    float ny = HudScale.toVirtual(saved.y(), uiScale);
                    float npx = HudScale.toVirtual(saved.parentOffsetX(), uiScale);
                    float npy = HudScale.toVirtual(saved.parentOffsetY(), uiScale);
                    resolved = new DraggableLayoutValue.State(
                            nx,
                            ny,
                            saved.anchorX(),
                            saved.anchorY(),
                            saved.parentId(),
                            saved.parentSide(),
                            npx,
                            npy,
                            true
                    );
                    w.layoutValue().set(resolved);
                    w.saveConfig();
                }
            }
            float safeW = w.getWidth() > 0f ? w.getWidth() : 20f;
            float safeH = w.getHeight() > 0f ? w.getHeight() : 20f;
            float clampedX = saved.anchorX() == HudAnchorX.FREE
                    ? Math.max(0, Math.min(resolved.x(), screenW - safeW))
                    : resolved.x();
            float clampedY = saved.anchorY() == HudAnchorY.FREE
                    ? Math.max(0, Math.min(resolved.y(), screenH - safeH))
                    : resolved.y();
            if (saved.anchorX() == HudAnchorX.FREE && saved.anchorY() == HudAnchorY.FREE
                    && (clampedX != resolved.x() || clampedY != resolved.y())) {
                DebugLog.config("[HUD] Saved position clamped for %s: (%.1f, %.1f) -> (%.1f, %.1f)",
                        w.getId(), resolved.x(), resolved.y(), clampedX, clampedY);
            } else {
                DebugLog.config("[HUD] Loaded saved position for %s: (%.1f, %.1f)", w.getId(), resolved.x(), resolved.y());
            }
            w.moveTo(clampedX, clampedY);
            w.setAnchors(resolved.anchorX(), resolved.anchorY(), clampedX, clampedY);
            if (resolved.parentId() != null && resolved.parentSide() != WidgetAnchorSide.NONE) {
                w.setParentAnchor(resolved.parentId(), resolved.parentSide(), resolved.parentOffsetX(), resolved.parentOffsetY());
            } else {
                w.clearParentAnchor();
            }
        } else {
            w.applyDefaultPosition(screenW, screenH);
            w.setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, w.getX(), w.getY());
            w.clearParentAnchor();
            DebugLog.config("[HUD] Applied default position for %s: (%.1f, %.1f)", w.getId(), w.getX(), w.getY());
        }
        w.markPositionInitialized();
    }

    private static void handleDragging(Minecraft mc, int screenW, int screenH, float uiScale) {
        boolean chatOpen = ClientScreen.current() instanceof ChatScreen;
        boolean dragEditorOpen = forceVisible && ClientScreen.current() != null;
        boolean allowDragging = chatOpen || dragEditorOpen;
        float fbX = (float) mc.mouseHandler.xpos();
        float fbY = (float) mc.mouseHandler.ypos();
        float mx = HudScale.toVirtual(fbX, uiScale);
        float my = HudScale.toVirtual(fbY, uiScale);

        boolean left = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        boolean middle = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (!allowDragging) {
            if (dragging != null) {
                //DebugLog.config("[HUD] Drag cancelled outside chat for %s", dragging.getId());
            }
            if (left && !prevLeft) {
                // DebugLog.config("[HUD] Mouse down ignored (chat not open) at (%.1f, %.1f)", mx, my);
            }
            if (!left && prevLeft) {
                // DebugLog.config("[HUD] Mouse up ignored (chat not open) at (%.1f, %.1f)", mx, my);
            }
            dragging = null;
            draggingWithLeft = false;
            clearSnapPreview();
            if (hudCursorActive) {
                SystemCursor.reset();
                hudCursorActive = false;
            }
            prevLeft = left;
            prevMiddle = middle;
            return;
        }

        if (dragging != null && !isWidgetVisibleForEditor(dragging)) {
            dragging = null;
            draggingWithLeft = false;
            clearSnapPreview();
        }

        if (left && !prevLeft) {
            //DebugLog.config("[HUD] Mouse down in chat at (%.1f, %.1f)", mx, my);
            for (DraggableHudElement w : WIDGETS) {
                /*DebugLog.config("[HUD] Widget %s bounds x=%.1f y=%.1f w=%.1f h=%.1f contains=%s",
                        w.getId(), w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.contains(mx, my));*/
            }
            DraggableHudElement clickTarget = null;
            long bestClickOrder = Long.MIN_VALUE;
            for (DraggableHudElement w : WIDGETS) {
                if (!isWidgetVisibleForEditor(w)) continue;
                if (!w.contains(mx, my)) continue;
                if (!w.isMouseOverInteractive(mx, my)) continue;
                long order = w.getRuntimeRenderOrder();
                if (order > bestClickOrder) {
                    bestClickOrder = order;
                    clickTarget = w;
                }
            }
            if (clickTarget != null) {
                if (clickTarget.onMouseClicked(mx, my, GLFW.GLFW_MOUSE_BUTTON_1)) {
                    dragging = null;
                    draggingWithLeft = false;
                    clearSnapPreview();
                    prevLeft = left;
                    prevMiddle = middle;
                    return;
                }
                // interactive hitbox: do not start drag on left click
                prevLeft = left;
                prevMiddle = middle;
                return;
            } else {
                // start drag with left click if no interactive hitbox
                DraggableHudElement candidate = null;
                long bestOrder = Long.MIN_VALUE;
                for (DraggableHudElement w : WIDGETS) {
                    if (!isWidgetVisibleForEditor(w)) continue;
                    if (!w.isDraggable() || !w.contains(mx, my)) continue;
                    long order = w.getRuntimeRenderOrder();
                    if (order > bestOrder) {
                        bestOrder = order;
                        candidate = w;
                    }
                }
                if (candidate == null) {
                    for (int i = WIDGETS.size() - 1; i >= 0; i--) {
                        DraggableHudElement w = WIDGETS.get(i);
                        if (!isWidgetVisibleForEditor(w)) continue;
                        if (w.contains(mx, my) && w.isDraggable()) {
                            candidate = w;
                            break;
                        }
                    }
                }
                if (candidate != null) {
                    dragging = candidate;
                    draggingWithLeft = true;
                    dragOffsetX = mx - candidate.getX();
                    dragOffsetY = my - candidate.getY();
                    if (isWidgetAnchorModifierDown()) {
                        detachChildren(candidate);
                    }
                    dragging.clearParentAnchor();
                    dragging.setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, dragging.getX(), dragging.getY());
                    SystemCursor.set(SystemCursor.CursorType.MOVE);
                    hudCursorActive = true;
                }
            }
        }

        if (middle && !prevMiddle) {
            // start drag
            DraggableHudElement candidate = null;
            long bestOrder = Long.MIN_VALUE;
            for (DraggableHudElement w : WIDGETS) {
                if (!isWidgetVisibleForEditor(w)) continue;
                if (!w.isDraggable() || !w.contains(mx, my)) continue;
                long order = w.getRuntimeRenderOrder();
                if (order > bestOrder) {
                    bestOrder = order;
                    candidate = w;
                }
            }
            if (candidate == null) {
                for (int i = WIDGETS.size() - 1; i >= 0; i--) {
                    DraggableHudElement w = WIDGETS.get(i);
                    if (!isWidgetVisibleForEditor(w)) continue;
                    if (w.contains(mx, my) && w.isDraggable()) {
                        candidate = w;
                        break;
                    }
                }
            }
            if (candidate != null) {
                dragging = candidate;
                draggingWithLeft = false;
                dragOffsetX = mx - candidate.getX();
                dragOffsetY = my - candidate.getY();
                if (isWidgetAnchorModifierDown()) {
                    detachChildren(candidate);
                }
                dragging.clearParentAnchor();
                dragging.setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, dragging.getX(), dragging.getY());
                //DebugLog.config("[HUD] Drag start for %s at (%.1f, %.1f)", dragging.getId(), dragging.getX(), dragging.getY());
                SystemCursor.set(SystemCursor.CursorType.MOVE);
                hudCursorActive = true;
            }
            if (dragging == null) {
                //DebugLog.config("[HUD] No widget found under cursor at (%.1f, %.1f); widgets=%d", mx, my, WIDGETS.size());
            }
        } else if (!middle && prevMiddle && dragging != null && !draggingWithLeft) {
            stopDragging(screenW, screenH);
        } else if (!middle && prevMiddle) {
            //DebugLog.config("[HUD] Mouse up with no active drag at (%.1f, %.1f)", mx, my);
        }

        if (draggingWithLeft && !left && prevLeft && dragging != null) {
            stopDragging(screenW, screenH);
        }

        if (((draggingWithLeft && left) || (!draggingWithLeft && middle)) && dragging != null) {
            moveDraggedWidget(dragging, mx, my, screenW, screenH);
            if (!isWidgetAnchorModifierDown()) {
                updateSnapPreview(dragging, screenW, screenH);
            } else {
                clearSnapPreview();
            }
            //DebugLog.config("[HUD] Dragging %s -> (%.1f, %.1f)", dragging.getId(), dragging.getX(), dragging.getY());
            SystemCursor.set(SystemCursor.CursorType.MOVE);
            hudCursorActive = true;
        } else if (dragging == null) {
            clearSnapPreview();
            requestHoverCursor(mx, my);
        }

        prevLeft = left;
        prevMiddle = middle;
    }

    private static void stopDragging(int screenW, int screenH) {
        if (dragging == null) return;
        if (!isWidgetAnchorModifierDown()) {
            applyStickySnap(dragging, screenW, screenH);
        }
        dragging.clearParentAnchor();
        Minecraft mc = Minecraft.getInstance();
        boolean allowWidgetAnchoring = mc == null || !isWidgetAnchorModifierDown();
        updateDragAnchors(dragging, screenW, screenH, allowWidgetAnchoring);
        dragging.layoutValue().set(dragging.snapshotLayout());
        dragging.saveConfig();
        dragging = null;
        draggingWithLeft = false;
        clearSnapPreview();
        if (hudCursorActive) {
            SystemCursor.reset();
            hudCursorActive = false;
        }
    }

    private static void cancelDragging() {
        dragging = null;
        draggingWithLeft = false;
        clearSnapPreview();
        if (hudCursorActive) {
            SystemCursor.reset();
            hudCursorActive = false;
        }
        prevLeft = false;
        prevMiddle = false;
    }

    private static void requestHoverCursor(float mx, float my) {
        DraggableHudElement interactive = findTopVisibleWidget(mx, my, true);
        if (interactive != null) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
            hudCursorActive = true;
            return;
        }

        DraggableHudElement draggable = findTopVisibleWidget(mx, my, false);
        if (draggable != null) {
            SystemCursor.set(SystemCursor.CursorType.MOVE);
            hudCursorActive = true;
        }
    }

    private static DraggableHudElement findTopVisibleWidget(float mx, float my, boolean interactiveOnly) {
        DraggableHudElement best = null;
        long bestOrder = Long.MIN_VALUE;
        for (DraggableHudElement w : WIDGETS) {
            if (!isWidgetVisibleForEditor(w)) continue;
            if (!w.contains(mx, my)) continue;

            if (interactiveOnly) {
                if (!w.isMouseOverInteractive(mx, my)) continue;
            } else if (!w.isDraggable()) {
                continue;
            }

            long order = w.getRuntimeRenderOrder();
            if (order > bestOrder) {
                bestOrder = order;
                best = w;
            }
        }

        if (best != null) return best;
        for (int i = WIDGETS.size() - 1; i >= 0; i--) {
            DraggableHudElement w = WIDGETS.get(i);
            if (!isWidgetVisibleForEditor(w)) continue;
            if (!w.contains(mx, my)) continue;

            if (interactiveOnly) {
                if (!w.isMouseOverInteractive(mx, my)) continue;
            } else if (!w.isDraggable()) {
                continue;
            }

            return w;
        }

        return null;
    }

    private static boolean isWidgetVisibleForEditor(DraggableHudElement w) {
        if (w == null) return false;

        boolean singleEditorPreview = editorWidgetId != null;
        if (singleEditorPreview && (editorWidgetId.isEmpty() || !editorWidgetId.equals(w.getId()))) {
            return false;
        }

        boolean bypassEnabled = singleEditorPreview && forceVisible;
        if (!bypassEnabled && !w.isEnabled()) {
            return false;
        }

        if (!w.usesEngineRenderer()) return false;
        if (w.getRenderSpace() != HudRenderSpace.UNSCALED_LOGICAL) return false;
        return w.getRuntimeRenderOrder() > 0L;
    }

    private static boolean isRenderableCandidate(DraggableHudElement w, HudPhase phase) {
        if (w == null) return false;

        boolean singleEditorPreview = editorWidgetId != null;
        if (singleEditorPreview && (editorWidgetId.isEmpty() || !editorWidgetId.equals(w.getId()))) {
            return false;
        }

        boolean bypassEnabled = singleEditorPreview && forceVisible;
        if (!bypassEnabled && !w.isEnabled() && !w.shouldRenderWhenDisabled()) {
            return false;
        }

        if (!w.usesEngineRenderer()) return false;
        if (w.getHudPhase() != phase) return false;
        return w.getRenderSpace() == HudRenderSpace.UNSCALED_LOGICAL;
    }

    private static boolean isWidgetVisibleForLinkOverlay(DraggableHudElement w) {
        if (w == null) return false;

        boolean singleEditorPreview = editorWidgetId != null;
        if (singleEditorPreview && (editorWidgetId.isEmpty() || !editorWidgetId.equals(w.getId()))) {
            return false;
        }

        boolean bypassEnabled = singleEditorPreview && forceVisible;
        if (!bypassEnabled && !w.isEnabled()) {
            return false;
        }

        return w.usesEngineRenderer() && w.getRenderSpace() == HudRenderSpace.UNSCALED_LOGICAL;
    }

    private static void applyAnchors(DraggableHudElement w, int screenW, int screenH) {
        applyAnchors(w, screenW, screenH, new HashSet<>());
    }

    private static void applyAnchors(DraggableHudElement w, int screenW, int screenH, Set<String> visiting) {
        if (w.getParentId() == null || w.getParentSide() == WidgetAnchorSide.NONE) {
            applyScreenAnchor(w, screenW, screenH);
            return;
        }
        DraggableHudElement parent = findById(w.getParentId());
        if (parent == null) {
            w.clearParentAnchor();
            applyScreenAnchor(w, screenW, screenH);
            return;
        }
        if (!visiting.add(w.getId())) {
            w.clearParentAnchor();
            applyScreenAnchor(w, screenW, screenH);
            return;
        }
        if (parent != dragging) {
            applyAnchors(parent, screenW, screenH, visiting);
        }
        if (isDescendantOf(parent, w)) {
            w.clearParentAnchor();
            applyScreenAnchor(w, screenW, screenH);
        } else {
            applyWidgetAnchor(w, parent, screenW, screenH);
        }
        visiting.remove(w.getId());
    }

    private static void applyScreenAnchor(DraggableHudElement w, int screenW, int screenH) {
        float x = w.getAnchorOffsetX();
        float y = w.getAnchorOffsetY();
        switch (w.getAnchorX()) {
            case LEFT -> x = w.getAnchorOffsetX();
            case CENTER -> x = screenW * 0.5f - w.getWidth() * 0.5f + w.getAnchorOffsetX();
            case RIGHT -> x = screenW - w.getWidth() - w.getAnchorOffsetX();
            case FREE -> x = w.getAnchorOffsetX();
        }
        switch (w.getAnchorY()) {
            case TOP -> y = w.getAnchorOffsetY();
            case CENTER -> y = screenH * 0.5f - w.getHeight() * 0.5f + w.getAnchorOffsetY();
            case BOTTOM -> y = screenH - w.getHeight() - w.getAnchorOffsetY();
            case FREE -> y = w.getAnchorOffsetY();
        }
        w.moveTo(clampX(x, w, screenW), clampY(y, w, screenH));
    }

    private static void applyWidgetAnchor(DraggableHudElement w, DraggableHudElement parent, int screenW, int screenH) {
        float x = w.getX();
        float y = w.getY();
        switch (w.getParentSide()) {
            case TOP -> {
                x = parent.getX() + w.getParentOffsetX();
                y = parent.getY() - w.getHeight() - PARENT_GAP + w.getParentOffsetY();
            }
            case BOTTOM -> {
                x = parent.getX() + w.getParentOffsetX();
                y = parent.getY() + parent.getHeight() + PARENT_GAP + w.getParentOffsetY();
            }
            case LEFT -> {
                x = parent.getX() - w.getWidth() - PARENT_GAP + w.getParentOffsetX();
                y = parent.getY() + w.getParentOffsetY();
            }
            case RIGHT -> {
                x = parent.getX() + parent.getWidth() + PARENT_GAP + w.getParentOffsetX();
                y = parent.getY() + w.getParentOffsetY();
            }
            case NONE -> {
            }
        }
        w.moveTo(clampX(x, w, screenW), clampY(y, w, screenH));
    }

    private static void updateDragAnchors(DraggableHudElement w, int screenW, int screenH, boolean allowWidgetAnchoring) {
        if (w.getWidth() <= 0 || w.getHeight() <= 0) {
            w.clearParentAnchor();
            w.setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, w.getX(), w.getY());
            return;
        }

        if (!allowWidgetAnchoring) {
            w.clearParentAnchor();
            w.setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, w.getX(), w.getY());
            return;
        }

        if (w.supportsWidgetAnchoring()) {
            WidgetAnchorCandidate candidate = findWidgetAnchorCandidate(w, screenW, screenH);
            if (candidate != null) {
                w.setParentAnchor(candidate.parent.getId(), candidate.side, candidate.offsetX, candidate.offsetY);
                return;
            }
        }
        w.clearParentAnchor();

        float x = w.getX();
        float y = w.getY();

        HudAnchorX ax = HudAnchorX.FREE;
        float offX = x;
        float leftDist = Math.abs(x);
        float rightDist = Math.abs((x + w.getWidth()) - screenW);
        float centerDistX = Math.abs((x + w.getWidth() * 0.5f) - screenW * 0.5f);
        if (leftDist <= SNAP_DIST) {
            ax = HudAnchorX.LEFT;
            offX = x;
        } else if (rightDist <= SNAP_DIST) {
            ax = HudAnchorX.RIGHT;
            offX = rightDist;
        } else if (centerDistX <= SNAP_DIST) {
            ax = HudAnchorX.CENTER;
            offX = (x + w.getWidth() * 0.5f) - screenW * 0.5f;
        }

        HudAnchorY ay = HudAnchorY.FREE;
        float offY = y;
        float topDist = Math.abs(y);
        float bottomDist = Math.abs((y + w.getHeight()) - screenH);
        float centerDistY = Math.abs((y + w.getHeight() * 0.5f) - screenH * 0.5f);
        if (topDist <= SNAP_DIST) {
            ay = HudAnchorY.TOP;
            offY = y;
        } else if (bottomDist <= SNAP_DIST) {
            ay = HudAnchorY.BOTTOM;
            offY = bottomDist;
        } else if (centerDistY <= SNAP_DIST) {
            ay = HudAnchorY.CENTER;
            offY = (y + w.getHeight() * 0.5f) - screenH * 0.5f;
        }

        w.setAnchors(ax, ay, offX, offY);
    }

    private static void handleScreenResize(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) return;
        if (lastScreenW <= 0 || lastScreenH <= 0) {
            lastScreenW = screenW;
            lastScreenH = screenH;
            return;
        }
        if (lastScreenW == screenW && lastScreenH == screenH) return;

        int oldW = lastScreenW;
        int oldH = lastScreenH;
        lastScreenW = screenW;
        lastScreenH = screenH;

        for (DraggableHudElement w : WIDGETS) {
            if (!w.isPositionInitialized()) continue;
            if (w.getParentId() != null && w.getParentSide() != WidgetAnchorSide.NONE) {
                // Parent-anchored widgets will be handled when anchors are applied.
                continue;
            }
            if (w.getWidth() <= 0 || w.getHeight() <= 0) continue;

            boolean changed = false;
            if (w.getAnchorX() == HudAnchorX.FREE && w.getAnchorY() == HudAnchorY.FREE) {
                changed |= inferScreenAnchors(w, oldW, oldH);
            }

            applyScreenAnchor(w, screenW, screenH);
            changed |= clampToScreenIfFree(w, screenW, screenH);

            if (changed) {
                w.layoutValue().set(w.snapshotLayout());
                w.saveConfig();
            }
        }
    }

    private static boolean inferScreenAnchors(DraggableHudElement w, int screenW, int screenH) {
        float x = w.getX();
        float y = w.getY();
        boolean changed = false;

        HudAnchorX ax = w.getAnchorX();
        float offX = w.getAnchorOffsetX();
        float leftDist = Math.abs(x);
        float rightDist = Math.abs((x + w.getWidth()) - screenW);
        float centerDistX = Math.abs((x + w.getWidth() * 0.5f) - screenW * 0.5f);
        if (leftDist <= SNAP_DIST) {
            ax = HudAnchorX.LEFT;
            offX = x;
        } else if (rightDist <= SNAP_DIST) {
            ax = HudAnchorX.RIGHT;
            offX = rightDist;
        } else if (centerDistX <= SNAP_DIST) {
            ax = HudAnchorX.CENTER;
            offX = (x + w.getWidth() * 0.5f) - screenW * 0.5f;
        }

        HudAnchorY ay = w.getAnchorY();
        float offY = w.getAnchorOffsetY();
        float topDist = Math.abs(y);
        float bottomDist = Math.abs((y + w.getHeight()) - screenH);
        float centerDistY = Math.abs((y + w.getHeight() * 0.5f) - screenH * 0.5f);
        if (topDist <= SNAP_DIST) {
            ay = HudAnchorY.TOP;
            offY = y;
        } else if (bottomDist <= SNAP_DIST) {
            ay = HudAnchorY.BOTTOM;
            offY = bottomDist;
        } else if (centerDistY <= SNAP_DIST) {
            ay = HudAnchorY.CENTER;
            offY = (y + w.getHeight() * 0.5f) - screenH * 0.5f;
        }

        if (ax != w.getAnchorX() || ay != w.getAnchorY()
                || offX != w.getAnchorOffsetX() || offY != w.getAnchorOffsetY()) {
            w.setAnchors(ax, ay, offX, offY);
            changed = true;
        }
        return changed;
    }

    private static boolean clampToScreenIfFree(DraggableHudElement w, int screenW, int screenH) {
        boolean changed = false;
        float newX = w.getX();
        float newY = w.getY();
        if (w.getAnchorX() == HudAnchorX.FREE) {
            float maxX = Math.max(0f, screenW - w.getWidth());
            newX = Math.max(0f, Math.min(newX, maxX));
        }
        if (w.getAnchorY() == HudAnchorY.FREE) {
            float maxY = Math.max(0f, screenH - w.getHeight());
            newY = Math.max(0f, Math.min(newY, maxY));
        }
        if (newX != w.getX() || newY != w.getY()) {
            w.moveTo(newX, newY);
            if (w.getAnchorX() == HudAnchorX.FREE || w.getAnchorY() == HudAnchorY.FREE) {
                w.setAnchors(w.getAnchorX(), w.getAnchorY(),
                        w.getAnchorX() == HudAnchorX.FREE ? newX : w.getAnchorOffsetX(),
                        w.getAnchorY() == HudAnchorY.FREE ? newY : w.getAnchorOffsetY());
            }
            changed = true;
        }
        return changed;
    }


    private static boolean isWidgetAnchorModifierDown() {
        return KeyManager.isComboHeldAllowScreen("LEFT_ALT")
                || KeyManager.isComboHeldAllowScreen("RIGHT_ALT");
    }

    private static void detachChildren(DraggableHudElement parent) {
        String parentId = parent.getId();
        for (DraggableHudElement w : WIDGETS) {
            if (w == parent) continue;
            if (parentId.equals(w.getParentId()) && w.getParentSide() != WidgetAnchorSide.NONE) {
                w.clearParentAnchor();
                w.setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, w.getX(), w.getY());
                w.layoutValue().set(w.snapshotLayout());
                w.saveConfig();
            }
        }
    }

    private static WidgetAnchorCandidate findWidgetAnchorCandidate(DraggableHudElement dragging, int screenW, int screenH) {
        float bestDist = Float.MAX_VALUE;
        WidgetAnchorCandidate best = null;
        for (DraggableHudElement target : WIDGETS) {
            if (target == dragging) continue;
            if (!isWidgetVisibleForLinkOverlay(target)) continue;
            if (isDescendantOf(target, dragging)) continue;
            if (target.getWidth() <= 0 || target.getHeight() <= 0) continue;

            float dxOverlap = overlap(dragging.getX(), dragging.getX() + dragging.getWidth(),
                    target.getX(), target.getX() + target.getWidth());
            float dyOverlap = overlap(dragging.getY(), dragging.getY() + dragging.getHeight(),
                    target.getY(), target.getY() + target.getHeight());

            float distTop = Math.abs((dragging.getY() + dragging.getHeight()) - (target.getY() - PARENT_GAP));
            if (dxOverlap > 0 && distTop <= PARENT_SNAP_DIST && distTop < bestDist) {
                float offsetX = parentAxisOffset(dragging.getX() - target.getX());
                float desiredY = target.getY() - PARENT_GAP - dragging.getHeight();
                float desiredX = target.getX() + offsetX;
                if (!fitsLinkedBoundsAt(dragging, desiredX, desiredY, screenW, screenH)) continue;
                float offsetY = 0f;
                bestDist = distTop;
                best = new WidgetAnchorCandidate(target, WidgetAnchorSide.TOP, offsetX, offsetY);
            }

            float distBottom = Math.abs(dragging.getY() - (target.getY() + target.getHeight() + PARENT_GAP));
            if (dxOverlap > 0 && distBottom <= PARENT_SNAP_DIST && distBottom < bestDist) {
                float offsetX = parentAxisOffset(dragging.getX() - target.getX());
                float desiredY = target.getY() + target.getHeight() + PARENT_GAP;
                float desiredX = target.getX() + offsetX;
                if (!fitsLinkedBoundsAt(dragging, desiredX, desiredY, screenW, screenH)) continue;
                float offsetY = 0f;
                bestDist = distBottom;
                best = new WidgetAnchorCandidate(target, WidgetAnchorSide.BOTTOM, offsetX, offsetY);
            }

            float distLeft = Math.abs((dragging.getX() + dragging.getWidth()) - (target.getX() - PARENT_GAP));
            if (dyOverlap > 0 && distLeft <= PARENT_SNAP_DIST && distLeft < bestDist) {
                float offsetY = parentAxisOffset(dragging.getY() - target.getY());
                float desiredX = target.getX() - PARENT_GAP - dragging.getWidth();
                float desiredY = target.getY() + offsetY;
                if (!fitsLinkedBoundsAt(dragging, desiredX, desiredY, screenW, screenH)) continue;
                float offsetX = 0f;
                bestDist = distLeft;
                best = new WidgetAnchorCandidate(target, WidgetAnchorSide.LEFT, offsetX, offsetY);
            }

            float distRight = Math.abs(dragging.getX() - (target.getX() + target.getWidth() + PARENT_GAP));
            if (dyOverlap > 0 && distRight <= PARENT_SNAP_DIST && distRight < bestDist) {
                float offsetY = parentAxisOffset(dragging.getY() - target.getY());
                float desiredX = target.getX() + target.getWidth() + PARENT_GAP;
                float desiredY = target.getY() + offsetY;
                if (!fitsLinkedBoundsAt(dragging, desiredX, desiredY, screenW, screenH)) continue;
                float offsetX = 0f;
                bestDist = distRight;
                best = new WidgetAnchorCandidate(target, WidgetAnchorSide.RIGHT, offsetX, offsetY);
            }
        }
        return best;
    }

    private static float parentAxisOffset(float offset) {
        return Math.abs(offset) <= PARENT_ALIGN_SNAP_DIST ? 0f : offset;
    }

    private static float overlap(float a0, float a1, float b0, float b1) {
        float left = Math.max(a0, b0);
        float right = Math.min(a1, b1);
        return right - left;
    }

    private static DraggableHudElement findById(String id) {
        if (id == null) return null;
        for (DraggableHudElement w : WIDGETS) {
            if (id.equals(w.getId())) return w;
        }
        return null;
    }

    private static void moveDraggedWidget(DraggableHudElement w, float mx, float my, int screenW, int screenH) {
        float newX = mx - dragOffsetX;
        float newY = my - dragOffsetY;
        Bounds groupBounds = collectLinkedBounds(w, screenW, screenH);
        float dx = newX - w.getX();
        float dy = newY - w.getY();

        if (groupBounds != null && groupBounds.width() <= screenW && groupBounds.height() <= screenH) {
            dx = clamp(dx, -groupBounds.minX(), screenW - groupBounds.maxX());
            dy = clamp(dy, -groupBounds.minY(), screenH - groupBounds.maxY());
            w.moveTo(w.getX() + dx, w.getY() + dy);
            applyDescendantAnchors(w, screenW, screenH, new HashSet<>());
            return;
        }

        w.moveTo(clampX(newX, w, screenW), clampY(newY, w, screenH));
        applyDescendantAnchors(w, screenW, screenH, new HashSet<>());
    }

    private static void updateSnapPreview(DraggableHudElement w, int screenW, int screenH) {
        if (w == null || w.getWidth() <= 0 || w.getHeight() <= 0) {
            clearSnapPreview();
            return;
        }

        StickySnap snap = findStickySnap(w, screenW, screenH);
        if (!snap.hasX && !snap.hasY) {
            clearSnapPreview();
            return;
        }

        snapPreviewActive = true;
        snapPreviewHasX = snap.hasX;
        snapPreviewHasY = snap.hasY;
        snapPreviewLineX = snap.x;
        snapPreviewLineY = snap.y;
    }

    private static void applyStickySnap(DraggableHudElement w, int screenW, int screenH) {
        if (w == null || w.getWidth() <= 0 || w.getHeight() <= 0) return;

        StickySnap snap = findStickySnap(w, screenW, screenH);
        float newX = w.getX();
        float newY = w.getY();
        if (snap.hasX) newX = snap.x;
        if (snap.hasY) newY = snap.y;
        w.moveTo(clampX(newX, w, screenW), clampY(newY, w, screenH));
    }

    private static StickySnap findStickySnap(DraggableHudElement dragging, int screenW, int screenH) {
        float bestX = 0f;
        float bestY = 0f;
        float bestXDist = Float.MAX_VALUE;
        float bestYDist = Float.MAX_VALUE;
        boolean hasX = false;
        boolean hasY = false;

        boolean rightSide = dragging.getX() > screenW * 0.5f;
        for (DraggableHudElement target : WIDGETS) {
            if (target == dragging) continue;
            if (!target.isEnabled()) continue;
            if (target.getWidth() <= 0 || target.getHeight() <= 0) continue;

            if (rightSide) {
                float targetEdge = target.getX() + target.getWidth();
                float dragEdge = dragging.getX() + dragging.getWidth();
                float dist = Math.abs(targetEdge - dragEdge);
                if (dist <= PARENT_SNAP_DIST && dist < bestXDist) {
                    bestXDist = dist;
                    bestX = targetEdge - dragging.getWidth();
                    hasX = true;
                }
            } else {
                float dist = Math.abs(target.getX() - dragging.getX());
                if (dist <= PARENT_SNAP_DIST && dist < bestXDist) {
                    bestXDist = dist;
                    bestX = target.getX();
                    hasX = true;
                }
            }

            float distY = Math.abs(target.getY() - dragging.getY());
            if (distY <= PARENT_SNAP_DIST && distY < bestYDist) {
                bestYDist = distY;
                bestY = target.getY();
                hasY = true;
            }
        }

        float distTop = Math.abs(dragging.getY());
        if (distTop <= PARENT_SNAP_DIST && distTop < bestYDist) {
            bestY = 0f;
            hasY = true;
        }

        return new StickySnap(hasX, hasY, bestX, bestY);
    }

    private static void clearSnapPreview() {
        snapPreviewActive = false;
        snapPreviewHasX = false;
        snapPreviewHasY = false;
    }

    private static void renderDragOverlay(Renderer2D renderer,
                                          TextRenderer fallbackTextRenderer,
                                          int screenW,
                                          int screenH) {
        renderLinkWires(renderer, screenW, screenH);

        if (snapPreviewActive) {
            if (snapPreviewHasX) {
                renderer.line(snapPreviewLineX, 0f, snapPreviewLineX, screenH, SNAP_PREVIEW_LINE);
            }
            if (snapPreviewHasY) {
                renderer.line(0f, snapPreviewLineY, screenW, snapPreviewLineY, SNAP_PREVIEW_LINE);
            }
        }

        if (dragging == null) return;
        TextRenderer coordRenderer = Fonts.renderer("Comfortaa", FontInfo.Type.Regular,
                fallbackTextRenderer != null ? fallbackTextRenderer : TextRenderer.get());
        if (coordRenderer == null) return;

        String coords = "x: " + Math.round(dragging.getX()) + " y: " + Math.round(dragging.getY());
        coordRenderer.begin(DRAG_COORD_TEXT_SCALE, false, false);
        try {
            float textW = (float) coordRenderer.getWidth(coords, true);
            float textH = (float) coordRenderer.getHeight(true);
            float textX = dragging.getX();
            float textY = dragging.getY() - textH - DRAG_COORD_MARGIN;
            if (textY < DRAG_COORD_MARGIN) {
                textY = dragging.getY() + dragging.getHeight() + DRAG_COORD_MARGIN;
            }
            textX = clamp(textX, DRAG_COORD_MARGIN, Math.max(DRAG_COORD_MARGIN, screenW - textW - DRAG_COORD_MARGIN));
            textY = clamp(textY, DRAG_COORD_MARGIN, Math.max(DRAG_COORD_MARGIN, screenH - textH - DRAG_COORD_MARGIN));
            coordRenderer.render(coords, textX, textY, new RenderColor(0xFFFFFFFF), true);
        } finally {
            coordRenderer.end();
        }
    }

    private static void renderLinkWires(Renderer2D renderer,
                                        int screenW,
                                        int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || !(ClientScreen.current() instanceof ChatScreen))
            return;

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float uiScale = HudScale.scale(fbw, fbh);
        float mx = HudScale.toVirtual((float) mc.mouseHandler.xpos(), uiScale);
        float my = HudScale.toVirtual((float) mc.mouseHandler.ypos(), uiScale);

        LinkWireColors colors = linkWireColors();
        for (DraggableHudElement child : WIDGETS) {
            if (!isWidgetVisibleForLinkOverlay(child)) continue;
            if (child == dragging) continue;
            if (child.getParentId() == null || child.getParentSide() == WidgetAnchorSide.NONE) continue;

            DraggableHudElement parent = findById(child.getParentId());
            if (!isWidgetVisibleForLinkOverlay(parent)) continue;
            if (!parent.contains(mx, my) && !child.contains(mx, my)) continue;
            renderLinkWire(renderer, parent, child, child.getParentSide(),
                    LINK_WIRE_THICKNESS, colors.wireStart(), colors.wireEnd());
        }

        if (dragging == null || isWidgetAnchorModifierDown()) return;
        WidgetAnchorCandidate candidate = findWidgetAnchorCandidate(dragging, screenW, screenH);
        if (candidate == null) return;
        renderLinkWire(renderer, candidate.parent(), dragging, candidate.side(),
                LINK_PREVIEW_THICKNESS, colors.previewStart(), colors.previewEnd());
    }

    private static LinkWireColors linkWireColors() {
        Themes.Theme theme = Theme.theme();
        if (cachedLinkTheme == theme && cachedLinkColors != null) {
            return cachedLinkColors;
        }

        int wireStart = HudRenderUtil.setAlpha(
                HudRenderUtil.mixColor(theme.textMuted(), theme.strokeSoft(), 0.45f), 0x86);
        int wireEnd = HudRenderUtil.setAlpha(
                HudRenderUtil.mixColor(theme.accentSoft(), theme.windowStroke(), 0.24f), 0x78);
        int previewStart = HudRenderUtil.setAlpha(
                HudRenderUtil.mixColor(theme.textPrimary(), theme.accentSoft(), 0.38f), 0xC8);
        int previewEnd = HudRenderUtil.setAlpha(
                HudRenderUtil.mixColor(theme.accent(), theme.accentSoft(), 0.28f), 0xD8);

        cachedLinkTheme = theme;
        cachedLinkColors = new LinkWireColors(wireStart, wireEnd, previewStart, previewEnd);
        return cachedLinkColors;
    }

    private static void renderLinkWire(Renderer2D renderer,
                                       DraggableHudElement parent,
                                       DraggableHudElement child,
                                       WidgetAnchorSide side,
                                       double thickness,
                                       int startArgb,
                                       int endArgb) {
        if (parent == null || child == null || side == null || side == WidgetAnchorSide.NONE) return;
        if (parent.getWidth() <= 0f || parent.getHeight() <= 0f || child.getWidth() <= 0f || child.getHeight() <= 0f) {
            return;
        }

        double parentX = 0.0;
        double parentY = 0.0;
        double childX = 0.0;
        double childY = 0.0;
        double parentCx = 0.0;
        double parentCy = 0.0;
        double childCx = 0.0;
        double childCy = 0.0;

        switch (side) {
            case TOP -> {
                parentX = parent.getX() + parent.getWidth() * 0.5;
                parentY = parent.getY();
                childX = child.getX() + child.getWidth() * 0.5;
                childY = child.getY() + child.getHeight();
                double c = Math.max(14.0, Math.abs(childY - parentY) * 0.45);
                parentCx = parentX;
                parentCy = parentY - c;
                childCx = childX;
                childCy = childY + c;
            }
            case BOTTOM -> {
                parentX = parent.getX() + parent.getWidth() * 0.5;
                parentY = parent.getY() + parent.getHeight();
                childX = child.getX() + child.getWidth() * 0.5;
                childY = child.getY();
                double c = Math.max(14.0, Math.abs(childY - parentY) * 0.45);
                parentCx = parentX;
                parentCy = parentY + c;
                childCx = childX;
                childCy = childY - c;
            }
            case LEFT -> {
                parentX = parent.getX();
                parentY = parent.getY() + parent.getHeight() * 0.5;
                childX = child.getX() + child.getWidth();
                childY = child.getY() + child.getHeight() * 0.5;
                double c = Math.max(14.0, Math.abs(childX - parentX) * 0.45);
                parentCx = parentX - c;
                parentCy = parentY;
                childCx = childX + c;
                childCy = childY;
            }
            case RIGHT -> {
                parentX = parent.getX() + parent.getWidth();
                parentY = parent.getY() + parent.getHeight() * 0.5;
                childX = child.getX();
                childY = child.getY() + child.getHeight() * 0.5;
                double c = Math.max(14.0, Math.abs(childX - parentX) * 0.45);
                parentCx = parentX + c;
                parentCy = parentY;
                childCx = childX - c;
                childCy = childY;
            }
            case NONE -> {
                return;
            }
        }

        renderer.bezierConnectorGradient(childX, childY, childCx, childCy, parentCx, parentCy, parentX, parentY,
                18, thickness, startArgb, endArgb);
    }

    private static void applyDragAnchorLock(DraggableHudElement w, int screenW, int screenH) {
        if (w.getParentId() != null && w.getParentSide() != WidgetAnchorSide.NONE) {
            DraggableHudElement parent = findById(w.getParentId());
            if (parent != null) {
                applyWidgetAnchor(w, parent, screenW, screenH);
            }
            return;
        }
        if (w.getAnchorX() != HudAnchorX.FREE || w.getAnchorY() != HudAnchorY.FREE) {
            applyScreenAnchor(w, screenW, screenH);
        }
    }

    private static float clampX(float x, DraggableHudElement w, int screenW) {
        float maxX = Math.max(0f, screenW - Math.max(0f, w.getWidth()));
        return clamp(x, 0f, maxX);
    }

    private static float clampY(float y, DraggableHudElement w, int screenH) {
        float maxY = Math.max(0f, screenH - Math.max(0f, w.getHeight()));
        return clamp(y, 0f, maxY);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }

    private static void applyDescendantAnchors(DraggableHudElement parent, int screenW, int screenH, Set<String> visited) {
        if (parent == null || !visited.add(parent.getId())) return;
        String parentId = parent.getId();
        for (DraggableHudElement child : WIDGETS) {
            if (child == parent) continue;
            if (!parentId.equals(child.getParentId()) || child.getParentSide() == WidgetAnchorSide.NONE) continue;
            applyWidgetAnchor(child, parent, screenW, screenH);
            applyDescendantAnchors(child, screenW, screenH, visited);
        }
    }

    private static Bounds collectLinkedBounds(DraggableHudElement root, int screenW, int screenH) {
        if (root == null) return null;
        return collectLinkedBounds(root, screenW, screenH, new HashSet<>());
    }

    private static Bounds collectLinkedBounds(DraggableHudElement root, int screenW, int screenH, Set<String> visited) {
        if (root == null || !visited.add(root.getId())) return null;
        Bounds bounds = Bounds.of(root);
        String parentId = root.getId();
        for (DraggableHudElement child : WIDGETS) {
            if (child == root) continue;
            if (!parentId.equals(child.getParentId()) || child.getParentSide() == WidgetAnchorSide.NONE) continue;
            applyWidgetAnchor(child, root, screenW, screenH);
            bounds = Bounds.union(bounds, collectLinkedBounds(child, screenW, screenH, visited));
        }
        return bounds;
    }

    private static boolean fitsLinkedBoundsAt(DraggableHudElement root,
                                              float desiredRootX,
                                              float desiredRootY,
                                              int screenW,
                                              int screenH) {
        Bounds bounds = collectLinkedBounds(root, screenW, screenH);
        if (bounds == null) return false;
        float dx = desiredRootX - root.getX();
        float dy = desiredRootY - root.getY();
        return bounds.minX() + dx >= 0f
                && bounds.minY() + dy >= 0f
                && bounds.maxX() + dx <= screenW
                && bounds.maxY() + dy <= screenH;
    }

    private static boolean isDescendantOf(DraggableHudElement possibleDescendant, DraggableHudElement ancestor) {
        if (possibleDescendant == null || ancestor == null) return false;
        String ancestorId = ancestor.getId();
        Set<String> visited = new HashSet<>();
        DraggableHudElement current = possibleDescendant;
        while (current != null && current.getParentId() != null && current.getParentSide() != WidgetAnchorSide.NONE) {
            if (!visited.add(current.getId())) return false;
            if (ancestorId.equals(current.getParentId())) return true;
            current = findById(current.getParentId());
        }
        return false;
    }

    private static void noteRendered(DraggableHudElement w) {
        if (w == null) return;
        w.markRendered(++renderOrderSeq);
    }

    private record StickySnap(boolean hasX, boolean hasY, float x, float y) {
    }

    private record WidgetAnchorCandidate(DraggableHudElement parent, WidgetAnchorSide side, float offsetX,
                                         float offsetY) {
    }

    private record LinkWireColors(int wireStart, int wireEnd, int previewStart, int previewEnd) {
    }

    private record Bounds(float minX, float minY, float maxX, float maxY) {
        static Bounds of(DraggableHudElement w) {
            return new Bounds(w.getX(), w.getY(), w.getX() + w.getWidth(), w.getY() + w.getHeight());
        }

        static Bounds union(Bounds a, Bounds b) {
            if (a == null) return b;
            if (b == null) return a;
            return new Bounds(
                    Math.min(a.minX, b.minX),
                    Math.min(a.minY, b.minY),
                    Math.max(a.maxX, b.maxX),
                    Math.max(a.maxY, b.maxY)
            );
        }

        float width() {
            return maxX - minX;
        }

        float height() {
            return maxY - minY;
        }
    }
}
