/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudRenderSpace;
import combatant.client.features.module.HudPhase;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Registry for non-draggable (vanilla/static) HUD elements.
 */
public enum StaticHudElementRegistry {
    ;

    private static final List<AbstractHudElement> ELEMENTS = new ArrayList<>();

    public static void register(AbstractHudElement element) {
        if (element == null) return;
        if (ELEMENTS.contains(element)) return;
        element.postInit();
        ELEMENTS.add(element);
    }

    public static void clearRuntimeReferences() {
        ELEMENTS.clear();
    }

    public static List<AbstractHudElement> getAll() {
        return Collections.unmodifiableList(ELEMENTS);
    }

    public static AbstractHudElement getById(String id) {
        if (id == null) return null;
        for (AbstractHudElement e : ELEMENTS) {
            if (id.equals(e.getId())) return e;
        }
        return null;
    }

    public static <T extends AbstractHudElement> T widget(Class<T> type) {
        if (type == null) return null;
        for (AbstractHudElement e : ELEMENTS) {
            if (type.isInstance(e)) {
                return type.cast(e);
            }
        }
        return null;
    }

    public static void tickAll() {
        if (!RuntimeGate.canRunHud()) return;
        if (!ProfilerPhase.isActive()) {
            for (AbstractHudElement e : ELEMENTS) {
                e.onTick();
            }
            return;
        }

        try (ProfilerPhase.Scope elementsScope = ProfilerPhase.scope("hud_static:tick")) {
            for (AbstractHudElement e : ELEMENTS) {
                try (ProfilerPhase.Scope elementScope = ProfilerPhase.scope("hud_static:tick:" + e.getId())) {
                    e.onTick();
                }
            }
        }
    }

    public static void renderAllEngine(HudPhase phase,
                                       HudRenderSpace space,
                                       Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       GuiGraphicsExtractor ctx,
                                       float tickDelta) {
        renderAllEngineInternal(phase, space, renderer, textRenderer, ctx, tickDelta, false);
    }

    public static void renderAllEngineForeground(HudPhase phase,
                                                 HudRenderSpace space,
                                                 Renderer2D renderer,
                                                 TextRenderer textRenderer,
                                                 GuiGraphicsExtractor ctx,
                                                 float tickDelta) {
        renderAllEngineInternal(phase, space, renderer, textRenderer, ctx, tickDelta, true);
    }

    public static boolean hasEngineWork(HudPhase phase, HudRenderSpace space, boolean foreground) {
        if (!RuntimeGate.canRunHud()) return false;
        for (AbstractHudElement element : ELEMENTS) {
            if (element == null || !element.isEnabled()) continue;
            if (!element.usesEngineRenderer()) continue;
            if (element.getHudPhase() != phase) continue;
            if (element.getRenderSpace() != space) continue;
            return true;
        }
        return false;
    }

    public static boolean handleMouseButton(int button, boolean pressed) {
        if (!RuntimeGate.canRunHud()) return false;
        if (!pressed) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;

        List<AbstractHudElement> interactive = new ArrayList<>();
        for (AbstractHudElement element : ELEMENTS) {
            if (element == null || !element.isEnabled()) continue;
            if (!element.usesEngineRenderer()) continue;
            interactive.add(element);
        }
        interactive.sort(Comparator.comparingInt(AbstractHudElement::getRenderOrder).reversed());

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float logicalScale = HudScale.scale(fbw, fbh);
        float scaledFactor = (float) mc.getWindow().getGuiScale();
        float rawX = (float) mc.mouseHandler.xpos();
        float rawY = (float) mc.mouseHandler.ypos();

        for (AbstractHudElement element : interactive) {
            float mx = switch (element.getRenderSpace()) {
                case UNSCALED -> rawX;
                case UNSCALED_LOGICAL -> HudScale.toVirtual(rawX, logicalScale);
                case SCALED -> scaledFactor > 0f ? rawX / scaledFactor : rawX;
            };
            float my = switch (element.getRenderSpace()) {
                case UNSCALED -> rawY;
                case UNSCALED_LOGICAL -> HudScale.toVirtual(rawY, logicalScale);
                case SCALED -> scaledFactor > 0f ? rawY / scaledFactor : rawY;
            };
            if (!element.isMouseOverInteractive(mx, my)) continue;
            if (element.onMouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    private static void renderAllEngineInternal(HudPhase phase,
                                                HudRenderSpace space,
                                                Renderer2D renderer,
                                                TextRenderer textRenderer,
                                                GuiGraphicsExtractor ctx,
                                                float tickDelta,
                                                boolean foreground) {
        if (!RuntimeGate.canRunHud()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int[] dims = screenSizeFor(space, mc);
        int screenW = dims[0];
        int screenH = dims[1];

        List<AbstractHudElement> renderList = new ArrayList<>();
        for (AbstractHudElement element : ELEMENTS) {
            if (element == null || !element.isEnabled()) continue;
            if (!element.usesEngineRenderer()) continue;
            if (element.getHudPhase() != phase) continue;
            if (element.getRenderSpace() != space) continue;
            renderList.add(element);
        }
        renderList.sort(Comparator.comparingInt(AbstractHudElement::getRenderOrder));

        if (!ProfilerPhase.isActive()) {
            for (AbstractHudElement element : renderList) {
                if (foreground) {
                    element.renderEngineForeground(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
                } else {
                    element.renderEngine(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
                }
            }
            return;
        }

        String lane = foreground ? "hud_static:render_fg:" : "hud_static:render:";
        for (AbstractHudElement element : renderList) {
            try (ProfilerPhase.Scope elementScope = ProfilerPhase.scope(lane + element.getId())) {
                if (foreground) {
                    element.renderEngineForeground(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
                } else {
                    element.renderEngine(renderer, textRenderer, ctx, tickDelta, screenW, screenH);
                }
            }
        }
    }

    private static int[] screenSizeFor(HudRenderSpace space, Minecraft mc) {
        return switch (space) {
            case UNSCALED -> new int[]{
                    Math.max(1, mc.getWindow().getWidth()),
                    Math.max(1, mc.getWindow().getHeight())
            };
            case SCALED -> new int[]{
                    Math.max(1, mc.getWindow().getGuiScaledWidth()),
                    Math.max(1, mc.getWindow().getGuiScaledHeight())
            };
            case UNSCALED_LOGICAL -> new int[]{
                    Math.max(1, Math.round(HudScale.virtualWidth(
                            mc.getWindow().getWidth(),
                            mc.getWindow().getHeight()
                    ))),
                    Math.max(1, Math.round(HudScale.virtualHeight(
                            mc.getWindow().getWidth(),
                            mc.getWindow().getHeight()
                    )))
            };
        };
    }
}
