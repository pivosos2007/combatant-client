/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.map.location.PlayerLocationEvent;
import combatant.client.features.map.location.PlayerLocationEventType;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBoxShape;
import combatant.client.render.engine.renderer.ui.draw.UiCompoundSdf;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.SystemCursor;
import net.minecraft.client.resources.language.I18n;

import java.util.EnumMap;
import java.util.List;

/**
 * Visual ownership boundary for the map screen chrome.
 *
 * <p>The terrain/viewport pipeline stays in {@link XaeroMapSurface}. Buttons, info cards,
 * browser/drawer presentation and context-menu presentation live here so UI work can be done
 * without touching map rendering code.</p>
 */
final class XaeroMapUiRenderer {
    private static final float MAP_INFO_FONT_SIZE = 18.0f;
    private static final float MAP_INFO_PAD_X = 14.0f;
    private static final float MAP_INFO_PAD_Y = 8.0f;
    private static final float MAP_INFO_RADIUS = 10.0f;
    private static final float MAP_INFO_EDGE_INSET = 18.0f;
    private static final float MAP_INFO_TOP_GAP = 10.0f;
    private static final float CONTROL_ISLAND_GAP = 7.0f;
    private static final float CONTROL_GROUP_GAP = 12.0f;
    private static final float CONTROL_SQUIRCLE_EXPONENT = 3.05f;

    private XaeroMapUiRenderer() {
    }


    static void layoutButtons(List<XaeroMapSurface.UiButton> out,
                              float areaX, float areaY, float areaWidth, float areaHeight,
                              ChromeState state) {
        out.clear();
        float size = 42.0f;
        float inset = 18.0f;

        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.SETTINGS, "settings-2",
                areaX + inset, areaY + inset, size, state.settingsTooltip(), state.settingsOpen(), true));
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.RECENTER, "locate-fixed",
                areaX + inset + size + CONTROL_ISLAND_GAP, areaY + inset, size, state.recenterTooltip(), false, true));

        float leftBottom = areaY + areaHeight - inset - size;
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CAVE, "layers",
                areaX + inset, leftBottom, size, state.caveTooltip(), state.caveActive(), true));
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.DIMENSION, "route",
                areaX + inset, leftBottom - size - CONTROL_ISLAND_GAP, size,
                state.dimensionTooltip(), state.customDimension(), true));

        // Right-side chrome is intentionally split into small semantic islands rather than
        // one long glass rail. Inside an island the lobes sit close enough for a metaball neck;
        // between islands there is enough air that the groups remain readable at a glance.
        float right = areaX + areaWidth - inset - size;
        float cursor = areaY + areaHeight - inset - size;

        if (state.showWaypoints()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.WAYPOINTS, "map-pinned",
                    right, cursor, size, state.waypointsTooltip(),
                    state.drawer() == XaeroMapSurface.Drawer.WAYPOINTS, true));
            cursor -= size + CONTROL_ISLAND_GAP;
        }
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.PLAYERS, "users-round",
                right, cursor, size, state.playersTooltip(),
                state.drawer() == XaeroMapSurface.Drawer.PLAYERS, true));
        cursor -= size + CONTROL_GROUP_GAP;

        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.RADAR_LIST, "list",
                right, cursor, size, state.radarListTooltip(),
                state.drawer() == XaeroMapSurface.Drawer.RADAR, state.radarAvailable()));
        cursor -= size + CONTROL_ISLAND_GAP;
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.RADAR, "radar",
                right, cursor, size, state.radarTooltip(), state.radarActive(), state.radarAvailable()));
        cursor -= size + CONTROL_GROUP_GAP;

        boolean hasOverlayGroup = false;
        if (state.showClaims()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CLAIMS, "land-plot",
                    right, cursor, size, state.claimsTooltip(), state.claimsActive(), true));
            cursor -= size + CONTROL_ISLAND_GAP;
            hasOverlayGroup = true;
        }
        if (hasOverlayGroup) {
            cursor += CONTROL_ISLAND_GAP - CONTROL_GROUP_GAP;
        }

        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CONTROLS, "circle-question-mark",
                right, cursor, size, state.controlsTooltip(), state.helpOpen(), true));
        cursor -= size + CONTROL_GROUP_GAP;

        if (state.showZoomButtons()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.ZOOM_OUT, "zoom-out",
                    right, cursor, size, state.zoomOutTooltip(), false, true));
            cursor -= size + CONTROL_ISLAND_GAP;
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.ZOOM_IN, "zoom-in",
                    right, cursor, size, state.zoomInTooltip(), false, true));
        }
    }

    static void drawZoom(float areaX, float areaY, float areaWidth, float areaHeight,
                         double destinationScale, SettingsGuiPalette palette) {
        String zoom = Math.round(destinationScale * 1000.0) / 1000.0 + "x";
        float textWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), zoom, MAP_INFO_FONT_SIZE);
        float cardWidth = textWidth + MAP_INFO_PAD_X * 2.0f;
        float cardHeight = MAP_INFO_FONT_SIZE + MAP_INFO_PAD_Y * 2.0f;
        float cardX = areaX + (areaWidth - cardWidth) * 0.5f;
        float cardY = areaY + areaHeight - MAP_INFO_EDGE_INSET - cardHeight;
        Renderer2D.COLOR.roundedRect(cardX, cardY, cardWidth, cardHeight,
                MAP_INFO_RADIUS, palette.panelBgLeft());
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getOnestMedium(), zoom,
                cardX + MAP_INFO_PAD_X, cardY + MAP_INFO_PAD_Y,
                MAP_INFO_FONT_SIZE, palette.panelText(), false);
    }

    /**
     * Draws all map chrome controls through one interaction/material system. Each semantic pair
     * is a true smooth union of two animated squircle lobes; missing optional controls collapse
     * to a single animated squircle without changing the rest of the stack.
     */
    static void drawControlChrome(List<XaeroMapSurface.UiButton> buttons,
                                  float mouseX, float mouseY,
                                  XaeroMapSurface.Action pressedAction,
                                  SettingsGuiPalette palette,
                                  ChromeMotion motion) {
        for (XaeroMapSurface.UiButton button : buttons) {
            motion.control(button.action()).update(
                    button, mouseX, mouseY, pressedAction == button.action(), button.active());
            if (button.contains(mouseX, mouseY)) {
                SystemCursor.set(button.enabled()
                        ? SystemCursor.CursorType.HAND
                        : SystemCursor.CursorType.NOT_ALLOWED);
            }
        }

        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.SETTINGS, XaeroMapSurface.Action.RECENTER);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.DIMENSION, XaeroMapSurface.Action.CAVE);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.WAYPOINTS, XaeroMapSurface.Action.PLAYERS);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.RADAR_LIST, XaeroMapSurface.Action.RADAR);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.CLAIMS, XaeroMapSurface.Action.CONTROLS);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.ZOOM_OUT, XaeroMapSurface.Action.ZOOM_IN);
    }

    private static void drawControlIsland(List<XaeroMapSurface.UiButton> buttons,
                                          SettingsGuiPalette palette,
                                          ChromeMotion motion,
                                          XaeroMapSurface.Action firstAction,
                                          XaeroMapSurface.Action secondAction) {
        XaeroMapSurface.UiButton first = findButton(buttons, firstAction);
        XaeroMapSurface.UiButton second = findButton(buttons, secondAction);
        if (first == null && second == null) return;

        XaeroMapSurface.UiButton aButton = first != null ? first : second;
        XaeroMapSurface.UiButton bButton = first != null && second != null ? second : null;
        ControlMotion aMotion = motion.control(aButton.action());
        ControlMotion bMotion = bButton != null ? motion.control(bButton.action()) : null;
        VisualControl a = visual(aButton, aMotion);
        VisualControl b = bButton != null ? visual(bButton, bMotion) : null;

        float hoverA = clamp01(aMotion.hover.value);
        float hoverB = bMotion != null ? clamp01(bMotion.hover.value) : 0.0f;
        float pressA = clamp01(aMotion.press.value);
        float pressB = bMotion != null ? clamp01(bMotion.press.value) : 0.0f;
        float groupHover = Math.max(hoverA, hoverB);
        float groupPress = Math.max(pressA, pressB);
        float availability = bButton == null
                ? (aButton.enabled() ? 1.0f : 0.0f)
                : ((aButton.enabled() ? 1.0f : 0.0f) + (bButton.enabled() ? 1.0f : 0.0f)) * 0.5f;

        Renderer2D renderer = Renderer2D.COLOR;

        // blurAlpha is the opacity of the prepared blur layer, not the blur radius/quality.
        // Keeping it below 1.0 preserves recognizable terrain while the source is still fully blurred.
        int glassBase = SettingsGuiPalette.mix(
                palette.panelBgRight(), palette.controlSurfaceHover(),
                0.055f + groupHover * 0.055f);
        int glassTint = SettingsGuiPalette.withAlpha(
                glassBase, Math.round(55.0f + availability * 59.0f + groupHover * 22.0f));
        int neutralGlow = SettingsGuiPalette.withAlpha(
                palette.panelText(), Math.round(34.0f + 18.0f * groupHover));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.038f + groupHover * 0.042f,
                        4.75f + groupHover * 1.15f, neutralGlow);
        float glassAlpha = 1.0f;
        float blurAlpha = 1.0f;

        renderer.withLiquidGlassMaterial(material, () -> {
            if (b != null) {
                // The idle neck is restrained. Hover/press adds mass so the transition is
                // geometric rather than merely a color change.
                float smoothing = 15.0f + groupHover * 5.5f + groupPress * 2.25f;
                UiCompoundSdf field = UiCompoundSdf.smoothSquircleUnion(
                        UiRect.of(a.x, a.y, a.size, a.size), CONTROL_SQUIRCLE_EXPONENT,
                        UiRect.of(b.x, b.y, b.size, b.size), CONTROL_SQUIRCLE_EXPONENT,
                        smoothing
                );
                renderer.liquidGlassCompound(field, glassTint, glassAlpha, blurAlpha,
                        Renderer2D.LiquidGlassPreset.HUD_SMALL);
            } else {
                // Use the explicit whole-box squircle path. Passing a positive squircle
                // exponent to liquidGlassRect is rounded-rect smoothness and radius=0 therefore
                // degenerates to a square (most visible when Radar is the only overlay control).
                renderer.liquidGlassSquircle(
                        UiBoxShape.squircle(a.x, a.y, a.size, a.size, CONTROL_SQUIRCLE_EXPONENT),
                        glassTint, glassAlpha, blurAlpha,
                        Renderer2D.LiquidGlassPreset.HUD_SMALL);
            }
        });

        drawControlActiveOverlay(renderer, aButton, a, aMotion);
        if (bButton != null) {
            drawControlActiveOverlay(renderer, bButton, b, bMotion);
        }

        drawControlIcon(renderer, aButton, a, aMotion, palette);
        if (bButton != null) {
            drawControlIcon(renderer, bButton, b, bMotion, palette);
        }
    }

    private static void drawControlActiveOverlay(Renderer2D renderer,
                                                 XaeroMapSurface.UiButton button,
                                                 VisualControl visual,
                                                 ControlMotion motion) {
        float active = clamp01(motion.active.value);
        if (!button.enabled() || !button.active() && active <= 0.001f) return;
        int accent = Themes.hudAccentGradient().start();
        int activeFill = SettingsGuiPalette.withAlpha(
                accent, Math.round(10.0f + 22.0f * active));
        renderer.box(UiBoxShape.squircle(
                        visual.x, visual.y, visual.size, visual.size, CONTROL_SQUIRCLE_EXPONENT),
                UiPaint.solid(activeFill));
    }

    private static void drawControlIcon(Renderer2D renderer,
                                        XaeroMapSurface.UiButton button,
                                        VisualControl visual,
                                        ControlMotion motion,
                                        SettingsGuiPalette palette) {
        float hover = clamp01(motion.hover.value);
        float press = clamp01(motion.press.value);
        float active = clamp01(motion.active.value);
        float iconScale = 1.0f + hover * 0.055f - press * 0.065f + active * 0.018f;
        float iconSize = button.size() * 0.50f * iconScale;
        float iconPullX = motion.pointerX.value * hover * 0.75f;
        float iconPullY = motion.pointerY.value * hover * 0.75f;
        int iconColor = button.enabled()
                ? SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), Math.max(hover, active))
                : SettingsGuiPalette.withAlpha(palette.panelMuted(), 92);
        renderer.svg(button.icon(),
                visual.centerX - iconSize * 0.5f + iconPullX,
                visual.centerY - iconSize * 0.5f + iconPullY,
                iconSize, iconSize,
                SvgRenderOptions.overrideColor(iconColor));
    }

    private static VisualControl visual(XaeroMapSurface.UiButton button, ControlMotion motion) {
        float hover = clamp01(motion.hover.value);
        float press = clamp01(motion.press.value);
        float active = clamp01(motion.active.value);
        float scale = 1.0f + hover * 0.040f - press * 0.060f + active * 0.010f;
        float size = button.size() * scale;
        float magneticX = motion.pointerX.value * hover * 1.55f;
        float magneticY = motion.pointerY.value * hover * 1.55f;
        float centerX = button.x() + button.size() * 0.5f + magneticX;
        float centerY = button.y() + button.size() * 0.5f + magneticY;
        return new VisualControl(centerX - size * 0.5f, centerY - size * 0.5f,
                size, centerX, centerY);
    }

    private static XaeroMapSurface.UiButton findButton(List<XaeroMapSurface.UiButton> buttons,
                                                        XaeroMapSurface.Action action) {
        for (XaeroMapSurface.UiButton button : buttons) {
            if (button.action() == action) return button;
        }
        return null;
    }

    static final class ChromeMotion {
        private final EnumMap<XaeroMapSurface.Action, ControlMotion> controls =
                new EnumMap<>(XaeroMapSurface.Action.class);
        private final TooltipMotion tooltip = new TooltipMotion();
        private final DrawerMotion drawer = new DrawerMotion();
        private final ContextMotion context = new ContextMotion();
        private final HelpMotion help = new HelpMotion();

        ControlMotion control(XaeroMapSurface.Action action) {
            return controls.computeIfAbsent(action, ignored -> new ControlMotion());
        }
    }


    private static final class DrawerMotion {
        final Spring reveal = new Spring();
        final Spring hoverBand = new Spring();
        final Spring hoverAlpha = new Spring();
        XaeroMapSurface.Drawer renderedDrawer = XaeroMapSurface.Drawer.NONE;

        DrawerMotion() {
            hoverBand.value = -1.0f;
        }

        void update(XaeroMapSurface.Drawer drawer) {
            float dt = motionDt();
            if (drawer != XaeroMapSurface.Drawer.NONE && drawer != renderedDrawer) {
                renderedDrawer = drawer;
                // Preserve the shell's current reveal when switching tabs. Pulling a fully open
                // drawer back to 72% made it visibly jump before expanding again.
                hoverAlpha.value = 0.0f;
                hoverBand.value = -1.0f;
            }
            reveal.step(drawer != XaeroMapSurface.Drawer.NONE ? 1.0f : 0.0f,
                    dt, 185.0f, 22.0f);
            if (drawer == XaeroMapSurface.Drawer.NONE
                    && reveal.value <= 0.002f && Math.abs(reveal.velocity) < 0.002f) {
                renderedDrawer = XaeroMapSurface.Drawer.NONE;
            }
        }

        void updateHover(int index) {
            float dt = motionDt();
            if (index >= 0) {
                if (hoverBand.value < 0.0f) hoverBand.value = index;
                hoverBand.step(index, dt, 210.0f, 23.0f);
                hoverAlpha.step(1.0f, dt, 190.0f, 22.0f);
            } else {
                hoverAlpha.step(0.0f, dt, 170.0f, 24.0f);
            }
        }
    }

    private static final class ContextMotion {
        final Spring reveal = new Spring();
        final Spring hoverBand = new Spring();
        final Spring hoverAlpha = new Spring();
        boolean wasOpen;

        ContextMotion() {
            hoverBand.value = -1.0f;
        }

        void update(float anchorX, float anchorY, boolean open) {
            float dt = motionDt();
            if (open && !wasOpen) {
                // Reopening during the exit animation continues from the current visual state.
                if (reveal.value < 0.002f) reveal.value = 0.002f;
                hoverAlpha.value = 0.0f;
                hoverBand.value = -1.0f;
            }
            reveal.step(open ? 1.0f : 0.0f, dt, 205.0f, 22.0f);
            if (!open && reveal.value <= 0.002f && Math.abs(reveal.velocity) < 0.002f) {
                hoverAlpha.value = 0.0f;
                hoverBand.value = -1.0f;
            }
            wasOpen = open;
        }

        void updateHover(int index) {
            float dt = motionDt();
            if (index >= 0) {
                if (hoverBand.value < 0.0f) hoverBand.value = index;
                hoverBand.step(index, dt, 138.0f, 19.5f);
                hoverAlpha.step(1.0f, dt, 150.0f, 20.0f);
            } else {
                hoverAlpha.step(0.0f, dt, 105.0f, 18.5f);
            }
        }
    }

    private static final class HelpMotion {
        final Spring reveal = new Spring();

        void update(boolean open) {
            reveal.step(open ? 1.0f : 0.0f, motionDt(), 185.0f, 21.0f);
        }
    }

    private static float motionDt() {
        float dt = AnimationUtility.deltaTime();
        if (!Float.isFinite(dt) || dt < 0.0f) return 1.0f / 60.0f;
        return Math.min(1.0f / 20.0f, dt);
    }

    private static final class ControlMotion {
        final Spring hover = new Spring();
        final Spring press = new Spring();
        final Spring active = new Spring();
        final Spring pointerX = new Spring();
        final Spring pointerY = new Spring();

        void update(XaeroMapSurface.UiButton button,
                    float mouseX, float mouseY,
                    boolean pressed,
                    boolean activeState) {
            boolean hovered = button.enabled() && button.contains(mouseX, mouseY);
            float targetX = 0.0f;
            float targetY = 0.0f;
            if (hovered) {
                float half = Math.max(1.0f, button.size() * 0.5f);
                targetX = clamp((mouseX - (button.x() + half)) / half, -1.0f, 1.0f);
                targetY = clamp((mouseY - (button.y() + half)) / half, -1.0f, 1.0f);
            }
            float dt = motionDt();
            hover.step(hovered ? 1.0f : 0.0f, dt, 180.0f, 20.0f);
            press.step(pressed && button.enabled() ? 1.0f : 0.0f, dt, 260.0f, 18.0f);
            active.step(activeState && button.enabled() ? 1.0f : 0.0f, dt, 135.0f, 20.0f);
            pointerX.step(targetX, dt, 150.0f, 22.0f);
            pointerY.step(targetY, dt, 150.0f, 22.0f);
        }
    }

    private static final class Spring {
        float value;
        float velocity;

        void step(float target, float dt, float stiffness, float damping) {
            if (!Float.isFinite(dt) || dt <= 0.0f) return;
            // Closed-form critically damped spring. It is stable after frame stalls, cannot
            // overshoot alpha endpoints and therefore does not get clipped before snapping back.
            float omega = Math.max(1.0f,
                    (float) Math.sqrt(Math.max(1.0f, stiffness)) * 0.55f + Math.max(0.0f, damping) * 0.24f);
            float displacement = value - target;
            float decay = (float) Math.exp(-omega * dt);
            float carry = (velocity + omega * displacement) * dt;
            value = target + (displacement + carry) * decay;
            velocity = (velocity - omega * carry) * decay;
            if (Math.abs(target - value) < 0.0005f && Math.abs(velocity) < 0.0005f) {
                value = target;
                velocity = 0.0f;
            }
        }
    }

    private record VisualControl(float x, float y, float size, float centerX, float centerY) {
    }

    static void drawTooltip(float areaX, float areaY, float areaWidth, float areaHeight,
                            float mouseX, float mouseY,
                            List<XaeroMapSurface.UiButton> buttons,
                            boolean blocked,
                            SettingsGuiPalette palette,
                            ChromeMotion motion) {
        XaeroMapSurface.UiButton hovered = null;
        if (!blocked) {
            for (XaeroMapSurface.UiButton button : buttons) {
                if (button.contains(mouseX, mouseY)) {
                    hovered = button;
                    break;
                }
            }
        }

        motion.tooltip.update(hovered != null ? hovered.action() : null);
        XaeroMapSurface.Action renderedAction = motion.tooltip.renderedAction;
        float reveal = clamp01(motion.tooltip.reveal.value);
        if (renderedAction == null || reveal <= 0.005f) return;

        XaeroMapSurface.UiButton anchor = findButton(buttons, renderedAction);
        if (anchor == null || anchor.tooltip() == null || anchor.tooltip().isBlank()) return;

        float eased = 1.0f - (1.0f - reveal) * (1.0f - reveal) * (1.0f - reveal);
        float fontSize = 14.5f;
        float textWidth = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), anchor.tooltip(), fontSize);

        float height = 31.0f;
        float tip = 7.0f;
        float bodyPad = 10.0f;
        float totalWidth = textWidth + bodyPad * 2.0f + tip;
        float anchorCenterX = anchor.x() + anchor.size() * 0.5f;
        float anchorCenterY = anchor.y() + anchor.size() * 0.5f;
        boolean opensRight = anchorCenterX < areaX + areaWidth * 0.5f;

        float travel = (1.0f - eased) * 7.0f;
        float x = opensRight
                ? anchor.x() + anchor.size() + 9.0f - travel
                : anchor.x() - totalWidth - 9.0f + travel;
        float y = anchorCenterY - height * 0.5f + (1.0f - eased) * 2.0f;
        x = clamp(x, areaX + 6.0f, areaX + areaWidth - totalWidth - 6.0f);
        y = clamp(y, areaY + 6.0f, areaY + areaHeight - height - 6.0f);

        // Five vertices: the tip is part of the same analytic primitive as the tooltip body.
        // This is not a rounded-rect plus a separately drawn triangle, so the
        // refraction/rim remains continuous across the pointer.
        UiPrimitive tooltipShape = opensRight
                ? UiPrimitive.builder(x, y, totalWidth, height)
                    .customConvex(
                            0.0, 0.50,
                            tip / totalWidth, 0.0,
                            1.0, 0.0,
                            1.0, 1.0,
                            tip / totalWidth, 1.0)
                    .rounding(4.8f)
                    .build()
                : UiPrimitive.builder(x, y, totalWidth, height)
                    .customConvex(
                            0.0, 0.0,
                            1.0 - tip / totalWidth, 0.0,
                            1.0, 0.50,
                            1.0 - tip / totalWidth, 1.0,
                            0.0, 1.0)
                    .rounding(4.8f)
                    .build();

        Renderer2D renderer = Renderer2D.COLOR;
        int glassBase = anchor.enabled()
                ? SettingsGuiPalette.mix(palette.panelBgRight(), palette.controlSurfaceHover(), 0.08f)
                : SettingsGuiPalette.mix(palette.panelBgRight(), 0xFF050608, 0.42f);
        int tint = SettingsGuiPalette.withAlpha(glassBase,
                Math.round((anchor.enabled() ? 112.0f : 76.0f) * eased));
        int inner = SettingsGuiPalette.withAlpha(palette.panelText(),
                Math.round((anchor.enabled() ? 30.0f : 12.0f) * eased));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.045f * eased, 4.4f, inner);
        renderer.withLiquidGlassMaterial(material, () ->
                drawLiquidGlassPrimitive(renderer,
                        tooltipShape, tint,
                        1.0f * eased,
                        1.0f * eased,
                        Renderer2D.LiquidGlassPreset.HUD_SMALL));

        // A very small directional gleam at the body-side edge makes the pointer shape read
        // against both ocean and terrain without adding a conventional border.
        int gleam = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(26.0f * eased));
        float gleamX = opensRight ? x + tip + 1.5f : x + totalWidth - tip - 2.5f;
        renderer.roundedRect(gleamX, y + 7.0f, 1.0f, height - 14.0f, 0.5f, gleam);

        int textColor = SettingsGuiPalette.withAlpha(
                anchor.enabled() ? palette.panelText() : palette.panelMuted(),
                Math.round((anchor.enabled() ? 255.0f : 180.0f) * eased));
        float textX = opensRight ? x + tip + bodyPad : x + bodyPad;
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), anchor.tooltip(),
                textX, y + 8.25f, fontSize, textColor, false);
    }

    private static final class TooltipMotion {
        final Spring reveal = new Spring();
        XaeroMapSurface.Action renderedAction;

        void update(XaeroMapSurface.Action hoveredAction) {
            if (hoveredAction != null && hoveredAction != renderedAction) {
                renderedAction = hoveredAction;
                // Changing directly between neighbouring buttons should feel like a tooltip
                // relocating, not like an unrelated popup being destroyed and recreated.
                reveal.velocity *= 0.35f;
            }
            float dt = motionDt();
            reveal.step(hoveredAction != null ? 1.0f : 0.0f, dt, 205.0f, 22.0f);
            if (hoveredAction == null && reveal.value <= 0.002f && Math.abs(reveal.velocity) < 0.002f) {
                renderedAction = null;
            }
        }
    }

    static void drawCompass(float areaX, float areaY, float areaWidth, float areaHeight,
                            String north, String east, String south, String west,
                            SettingsGuiPalette palette) {
        float centerX = areaX + areaWidth * 0.5f;
        float centerY = areaY + areaHeight * 0.5f;
        float edgeInset = 102.0f;
        drawCardinal(north, centerX, areaY + edgeInset, palette);
        drawCardinal(south, centerX, areaY + areaHeight - edgeInset, palette);
        drawCardinal(west, areaX + edgeInset, centerY, palette);
        drawCardinal(east, areaX + areaWidth - edgeInset, centerY, palette);
    }

    private static void drawCardinal(String text, float centerX, float centerY,
                                     SettingsGuiPalette palette) {
        float size = 16.0f;
        float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestBold(), text, size);
        Renderer2D.COLOR.roundedRect(centerX - width * 0.5f - 8.0f,
                centerY - 5.0f, width + 16.0f, size + 10.0f,
                8.0f, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), text,
                centerX - width * 0.5f, centerY, size, palette.panelText(), false);
    }

    static void drawCoordinates(float areaX, float areaY, float areaWidth,
                                String coordinates, float chromeBottom,
                                SettingsGuiPalette palette) {
        float textWidth = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), coordinates, MAP_INFO_FONT_SIZE);
        float cardWidth = textWidth + MAP_INFO_PAD_X * 2.0f;
        float cardHeight = MAP_INFO_FONT_SIZE + MAP_INFO_PAD_Y * 2.0f;
        float cardX = areaX + (areaWidth - cardWidth) * 0.5f;
        float cardY = Math.max(areaY + MAP_INFO_EDGE_INSET, chromeBottom + MAP_INFO_TOP_GAP);
        Renderer2D.COLOR.roundedRect(cardX, cardY, cardWidth, cardHeight,
                MAP_INFO_RADIUS, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), coordinates,
                cardX + MAP_INFO_PAD_X, cardY + MAP_INFO_PAD_Y,
                MAP_INFO_FONT_SIZE, palette.panelText(), false);
    }

    static void drawDrawer(float areaX, float areaY, float areaWidth,
                           float mouseX, float mouseY,
                           XaeroMapSurface.Drawer drawer,
                           XaeroMapElements.Snapshot snapshot,
                           List<XaeroMapSurface.TargetRow> targets,
                           List<XaeroMapSurface.ElementHit> hits,
                           List<XaeroMapSurface.TargetHit> targetHits,
                           boolean teleportAllowed,
                           SettingsGuiPalette palette,
                           ChromeMotion motion) {
        hits.clear();
        targetHits.clear();

        DrawerMotion state = motion.drawer;
        state.update(drawer);
        XaeroMapSurface.Drawer rendered = state.renderedDrawer;
        float reveal = clamp01(state.reveal.value);
        if (rendered == XaeroMapSurface.Drawer.NONE || reveal <= 0.004f) return;

        float eased = 1.0f - (1.0f - reveal) * (1.0f - reveal) * (1.0f - reveal);
        float width = 322.0f;
        float bodyRightInset = 9.0f;
        float tip = 10.0f;
        float x = areaX + areaWidth - width - 70.0f + (1.0f - eased) * 18.0f;
        float y = areaY + 82.0f + (1.0f - eased) * 4.0f;
        float rowHeight = 43.0f;

        List<XaeroMapElements.Element> rows = snapshot.elements().stream()
                .filter(element -> rendered == XaeroMapSurface.Drawer.WAYPOINTS
                        ? element.kind() == XaeroMapElements.Kind.WAYPOINT
                        : rendered == XaeroMapSurface.Drawer.RADAR
                        && element.kind() != XaeroMapElements.Kind.WAYPOINT)
                .limit(12)
                .toList();
        List<XaeroMapSurface.TargetRow> targetRows = rendered == XaeroMapSurface.Drawer.PLAYERS
                ? targets.stream().limit(12).toList() : List.of();
        int rowCount = rendered == XaeroMapSurface.Drawer.PLAYERS ? targetRows.size() : rows.size();

        int totalMatching = 0;
        int playerCount = 0;
        int radarCount = 0;
        for (XaeroMapElements.Element element : snapshot.elements()) {
            boolean matches = rendered == XaeroMapSurface.Drawer.WAYPOINTS
                    ? element.kind() == XaeroMapElements.Kind.WAYPOINT
                    : rendered == XaeroMapSurface.Drawer.RADAR
                    && element.kind() != XaeroMapElements.Kind.WAYPOINT;
            if (!matches) continue;
            totalMatching++;
            if (element.kind() == XaeroMapElements.Kind.PLAYER) playerCount++;
            if (element.kind() == XaeroMapElements.Kind.ENTITY) radarCount++;
        }
        if (rendered == XaeroMapSurface.Drawer.PLAYERS) totalMatching = targets.size();

        float headerHeight = 61.0f;
        float footerHeight = 28.0f;
        float emptyHeight = rowCount == 0 ? 48.0f : 0.0f;
        float height = headerHeight + Math.max(emptyHeight, rowCount * rowHeight) + footerHeight;
        float bodyWidth = width - tip;
        float tipY = 29.0f;

        UiPrimitive shell = UiPrimitive.builder(x, y, width, height)
                .customConvex(
                        0.0, 0.0,
                        bodyWidth / width, 0.0,
                        1.0, (tipY + 8.0f) / height,
                        bodyWidth / width, 1.0,
                        0.0, 1.0)
                .rounding(5.5f)
                .build();

        Renderer2D renderer = Renderer2D.COLOR;
        int tintBase = SettingsGuiPalette.mix(
                palette.panelBgRight(), palette.controlSurfaceHover(), 0.038f);
        int tint = SettingsGuiPalette.withAlpha(tintBase, Math.round(95.0f * eased));
        int inner = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(18.0f * eased));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.022f * eased, 4.2f, inner);
        renderer.withLiquidGlassMaterial(material, () ->
                drawLiquidGlassPrimitive(renderer, shell, tint,
                        1.0f * eased, 1.0f * eased,
                        Renderer2D.LiquidGlassPreset.BALANCED));

        float contentRight = x + bodyWidth;
        float headerX = x + 17.0f;
        int titleColor = SettingsGuiPalette.withAlpha(
                palette.panelText(), Math.round(255.0f * eased));
        int muted = SettingsGuiPalette.withAlpha(
                palette.panelMuted(), Math.round(225.0f * eased));
        int accent = Themes.hudAccentGradient().start();

        String title = switch (rendered) {
            case WAYPOINTS -> tr("gui.combatant.map.drawer.waypoints", "Waypoints");
            case PLAYERS -> tr("gui.combatant.map.drawer.targets", "Targeted players");
            case RADAR -> tr("gui.combatant.map.drawer.radar", "Radar list");
            case NONE -> "";
        };
        String subtitle = switch (rendered) {
            case WAYPOINTS -> trCount("gui.combatant.map.drawer.waypoint_count", totalMatching,
                    totalMatching + " waypoints");
            case PLAYERS -> trCount("gui.combatant.map.drawer.target_count", totalMatching,
                    totalMatching + " targets");
            case RADAR -> tr("gui.combatant.map.drawer.radar_count", "%s players · %s entities",
                    playerCount, radarCount);
            case NONE -> "";
        };

        renderer.roundedRect(headerX, y + 16.0f, 2.0f, 28.0f, 1.0f,
                SettingsGuiPalette.withAlpha(accent, Math.round(170.0f * eased)));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), title,
                headerX + 10.0f, y + 13.0f, 16.5f, titleColor, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), subtitle,
                headerX + 10.0f, y + 34.0f, 11.5f, muted, false);

        int closeHint = SettingsGuiPalette.withAlpha(palette.panelMuted(), Math.round(155.0f * eased));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.common.esc", "ESC"),
                contentRight - 35.0f, y + 21.0f, 10.5f, closeHint, false);

        float rowsY = y + headerHeight;
        int hoveredIndex = -1;
        for (int i = 0; i < rowCount; i++) {
            float rowY = rowsY + i * rowHeight;
            if (inside(mouseX, mouseY, x + 8.0f, rowY, bodyWidth - 15.0f, rowHeight)) {
                hoveredIndex = i;
                break;
            }
        }
        state.updateHover(hoveredIndex);
        if (hoveredIndex >= 0) {
            if (rendered == XaeroMapSurface.Drawer.PLAYERS) {
                XaeroMapSurface.TargetRow target = targetRows.get(hoveredIndex);
                boolean hasPosition = target.location() != null && target.location().hasPosition();
                boolean teleportable = hasPosition && target.location().exact() && teleportAllowed;
                SystemCursor.set(teleportable ? SystemCursor.CursorType.CROSSHAIR
                        : hasPosition ? SystemCursor.CursorType.HAND : SystemCursor.CursorType.NOT_ALLOWED);
            } else {
                SystemCursor.set(SystemCursor.CursorType.HAND);
            }
        }

        if (hoveredIndex >= 0 || state.hoverBand.value >= 0.0f && state.hoverAlpha.value > 0.002f) {
            float bandY = rowsY + Math.max(0.0f, state.hoverBand.value) * rowHeight;
            float a = clamp01(state.hoverAlpha.value) * eased;
            int fadeA = SettingsGuiPalette.withAlpha(accent, Math.round(24.0f * a));
            int fadeB = SettingsGuiPalette.withAlpha(accent, 0);
            renderer.quadGradientLinear(x + 7.0f, bandY + 2.0f,
                    bodyWidth - 14.0f, rowHeight - 4.0f,
                    fadeB, fadeA, 0.0f, 0.0f);
            renderer.roundedRect(x + 7.0f, bandY + 8.0f,
                    2.0f, rowHeight - 16.0f, 1.0f,
                    SettingsGuiPalette.withAlpha(accent, Math.round(185.0f * a)));
        }

        if (rowCount == 0) {
            String empty = rendered == XaeroMapSurface.Drawer.PLAYERS
                    ? tr("gui.combatant.map.drawer.targets_empty", "No targeted players")
                    : tr("gui.combatant.map.drawer.empty", "Nothing visible on this map");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), empty,
                    headerX + 10.0f, rowsY + 15.0f, 13.0f, muted, false);
        }

        if (rendered == XaeroMapSurface.Drawer.PLAYERS) {
            for (int i = 0; i < targetRows.size(); i++) {
                XaeroMapSurface.TargetRow target = targetRows.get(i);
                float rowY = rowsY + i * rowHeight;
                float hover = hoveredIndex == i ? clamp01(state.hoverAlpha.value) : 0.0f;
                float shift = hover * 2.5f;
                boolean hasPosition = target.location() != null && target.location().hasPosition();
                boolean exact = hasPosition && target.location().exact();
                int targetColor = exact ? 0xFF55D6BE : hasPosition ? 0xFFFFB85C : muted;
                renderer.svg(exact ? "navigation" : hasPosition ? "locate" : "circle-dashed",
                        x + 18.0f + shift, rowY + 11.0f, 18.0f, 18.0f,
                        SvgRenderOptions.overrideColor(SettingsGuiPalette.withAlpha(targetColor,
                                Math.round(235.0f * eased))));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), target.name(),
                        x + 47.0f + shift, rowY + 7.0f, 14.0f, titleColor, false);
                String meta = targetMeta(target);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getIosevkaRegular(), meta,
                        x + 47.0f + shift, rowY + 25.0f, 11.0f, muted, false);
                boolean teleportable = exact && teleportAllowed;
                targetHits.add(new XaeroMapSurface.TargetHit(target,
                        x + 8.0f, rowY, bodyWidth - 15.0f, rowHeight, teleportable));
            }
        } else for (int i = 0; i < rows.size(); i++) {
            XaeroMapElements.Element element = rows.get(i);
            float rowY = rowsY + i * rowHeight;
            float hover = hoveredIndex == i ? clamp01(state.hoverAlpha.value) : 0.0f;
            float shift = hover * 2.5f;

            String icon = switch (element.kind()) {
                case WAYPOINT -> "map-pin";
                case PLAYER -> "users-round";
                case ENTITY -> "radar";
            };

            int iconColor = element.disabled()
                    ? muted
                    : SettingsGuiPalette.withAlpha(element.color(), Math.round(235.0f * eased));
            renderer.svg(icon, x + 18.0f + shift, rowY + 11.0f,
                    18.0f, 18.0f, SvgRenderOptions.overrideColor(iconColor));

            String name = element.plainName();
            if (name == null || name.isBlank()) name = element.kind() == XaeroMapElements.Kind.ENTITY
                    ? tr("gui.combatant.map.drawer.radar_contact", "Radar contact")
                    : tr("gui.combatant.map.drawer.unnamed", "Unnamed");
            int rowText = element.disabled() ? muted : titleColor;
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), name,
                    x + 47.0f + shift, rowY + 7.0f, 14.0f, rowText, false);

            String meta;
            if (element.kind() == XaeroMapElements.Kind.WAYPOINT && element.symbol() != null
                    && !element.symbol().isBlank()) {
                meta = element.symbol() + "  ·  " + (int) element.worldX() + ", " + (int) element.worldZ();
            } else {
                meta = (int) element.worldX() + ", " + (int) element.worldZ();
            }
            ClickGuiRenderer.drawText(ClickGuiRenderer.getIosevkaRegular(), meta,
                    x + 47.0f + shift, rowY + 25.0f, 11.0f, muted, false);

            hits.add(new XaeroMapSurface.ElementHit(
                    element, x + 8.0f, rowY, bodyWidth - 15.0f, rowHeight));
        }

        float footerY = y + height - footerHeight;
        int separator = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(18.0f * eased));
        renderer.quad(x + 16.0f, footerY, bodyWidth - 31.0f, 1.0f, separator);
        String footer = rendered == XaeroMapSurface.Drawer.PLAYERS
                ? tr("gui.combatant.map.drawer.target_hint", "LMB teleport when exact · otherwise focus")
                : tr("gui.combatant.map.drawer.element_hint", "LMB focus · RMB actions");
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), footer,
                x + 17.0f, footerY + 8.0f, 10.5f, muted, false);
    }

    private static String targetMeta(XaeroMapSurface.TargetRow target) {
        var service = PlayerLocationService.get();
        var view = service.snapshot();
        PlayerLocationSnapshot location = target.location();
        PlayerLocationSnapshot locator = view.locatorSource(target.playerUuid());
        String locatorState;
        if (locator != null) {
            locatorState = switch (locator.source()) {
                case LOCATOR_EXACT -> tr("gui.combatant.map.locator.present_exact", "locator exact");
                case LOCATOR_APPROXIMATE -> tr("gui.combatant.map.locator.present_chunk", "locator chunk");
                case LOCATOR_BEARING -> tr("gui.combatant.map.locator.present_bearing", "locator bearing");
                default -> tr("gui.combatant.map.locator.present", "locator visible");
            };
        } else {
            PlayerLocationEvent lost = service.latestLocatorEvent(target.playerUuid(), PlayerLocationEventType.SOURCE_LOST);
            if (lost != null) {
                long age = Math.max(0L, System.currentTimeMillis() - lost.timestampMs());
                locatorState = tr("gui.combatant.map.locator.lost", "locator lost") + " " + Math.max(0L, age / 1000L) + "s";
            } else {
                locatorState = tr("gui.combatant.map.locator.absent", "locator not visible");
            }
        }
        if (location == null) return locatorState + " · "
                + tr("gui.combatant.map.drawer.target_waiting", "waiting for coordinates");
        if (!location.hasPosition()) return locatorState;
        String source = sourceLabel(location.source());
        String prefix = location.exact() ? "" : "≈ ";
        String coordinates = prefix + "X " + (int) Math.floor(location.x()) + "  Z " + (int) Math.floor(location.z());
        return locator != null && locator == location ? coordinates + " · " + locatorState
                : locatorState + " · " + coordinates + " · " + source;
    }

    private static String sourceLabel(PlayerLocationSource source) {
        return switch (source) {
            case LOCAL_ENTITY_EXACT -> tr("gui.combatant.map.source.local", "local");
            case MAPLINK_EXACT -> tr("gui.combatant.map.source.maplink", "MapLink");
            case LOCATOR_EXACT -> tr("gui.combatant.map.source.locator_exact", "locator exact");
            case DUPLEX_TRIANGULATED -> tr("gui.combatant.map.source.duplex", "duplex");
            case TRIANGULATED -> tr("gui.combatant.map.source.triangulated", "triangulated");
            case LOCATOR_BEARING -> tr("gui.combatant.map.source.bearing", "locator bearing");
            case LOCATOR_APPROXIMATE -> tr("gui.combatant.map.source.locator_approx", "locator approximate");
            case HISTORICAL -> tr("gui.combatant.map.source.history", "history");
        };
    }

    static HelpBounds drawHelp(float areaX, float areaY, float areaWidth, float areaHeight,
                               List<XaeroMapSurface.UiButton> buttons,
                               boolean open,
                               SettingsGuiPalette palette,
                               ChromeMotion motion) {
        HelpMotion state = motion.help;
        state.update(open);
        float reveal = clamp01(state.reveal.value);
        if (reveal <= 0.004f) return null;

        XaeroMapSurface.UiButton anchor = findButton(buttons, XaeroMapSurface.Action.CONTROLS);
        if (anchor == null) return null;

        float eased = 1.0f - (1.0f - reveal) * (1.0f - reveal) * (1.0f - reveal);
        float width = 352.0f;
        float height = 190.0f;
        float tip = 10.0f;
        float anchorCenterY = anchor.y() + anchor.size() * 0.5f;
        boolean opensLeft = anchor.x() > areaX + areaWidth * 0.5f;
        float travel = (1.0f - eased) * 12.0f;
        float x = opensLeft
                ? anchor.x() - width - 10.0f + travel
                : anchor.x() + anchor.size() + 10.0f - travel;
        float y = anchorCenterY - 56.0f - (1.0f - eased) * 3.0f;
        x = clamp(x, areaX + 8.0f, areaX + areaWidth - width - 8.0f);
        y = clamp(y, areaY + 8.0f, areaY + areaHeight - height - 8.0f);

        float tipNormY = clamp((anchorCenterY - y) / height, 0.18f, 0.82f);
        UiPrimitive shell = opensLeft
                ? UiPrimitive.builder(x, y, width, height)
                    .customConvex(
                            0.0, 0.0,
                            1.0 - tip / width, 0.0,
                            1.0, tipNormY,
                            1.0 - tip / width, 1.0,
                            0.0, 1.0)
                    .rounding(5.0f)
                    .build()
                : UiPrimitive.builder(x, y, width, height)
                    .customConvex(
                            0.0, tipNormY,
                            tip / width, 0.0,
                            1.0, 0.0,
                            1.0, 1.0,
                            tip / width, 1.0)
                    .rounding(5.0f)
                    .build();

        Renderer2D renderer = Renderer2D.COLOR;
        int tintBase = SettingsGuiPalette.mix(
                palette.panelBgRight(), palette.controlSurfaceHover(), 0.035f);
        int tint = SettingsGuiPalette.withAlpha(tintBase, Math.round(94.0f * eased));
        int inner = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(18.0f * eased));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.020f * eased, 4.0f, inner);
        renderer.withLiquidGlassMaterial(material, () ->
                drawLiquidGlassPrimitive(renderer, shell, tint,
                        1.0f * eased, 1.0f * eased,
                        Renderer2D.LiquidGlassPreset.BALANCED));

        float bodyX = opensLeft ? x : x + tip;
        float bodyW = width - tip;
        float left = bodyX + 17.0f;
        int text = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(255.0f * eased));
        int muted = SettingsGuiPalette.withAlpha(palette.panelMuted(), Math.round(218.0f * eased));
        int accent = SettingsGuiPalette.withAlpha(Themes.hudAccentGradient().start(), Math.round(230.0f * eased));

        renderer.svg("circle-question-mark", left, y + 17.0f, 24.0f, 24.0f,
                SvgRenderOptions.overrideColor(accent));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(),
                tr("gui.combatant.map.help.title", "Map controls"),
                left + 35.0f, y + 13.0f, 18.0f, text, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                tr("gui.combatant.map.help.subtitle", "Navigation & marker actions"),
                left + 35.0f, y + 36.0f, 12.5f, muted, false);

        drawHelpLine(left, y + 69.0f, tr("gui.combatant.map.help.drag_key", "LMB drag"),
                tr("gui.combatant.map.help.drag", "Pan the map"), palette, eased);
        drawHelpLine(left, y + 95.0f, tr("gui.combatant.map.help.zoom_key", "Wheel / +/-"),
                tr("gui.combatant.map.help.zoom", "Zoom around pointer"), palette, eased);
        drawHelpLine(left, y + 121.0f, tr("gui.combatant.map.help.actions_key", "RMB"),
                tr("gui.combatant.map.help.actions", "Open actions / selection"), palette, eased);
        drawHelpLine(left, y + 147.0f, tr("gui.combatant.map.help.marker_key", "T / E"),
                tr("gui.combatant.map.help.marker", "Teleport / edit marker"), palette, eased);

        return new HelpBounds(x, y, width, height);
    }

    private static void drawHelpLine(float x, float y, String key, String description,
                                     SettingsGuiPalette palette, float reveal) {
        int keyColor = SettingsGuiPalette.withAlpha(Themes.hudAccentGradient().start(),
                Math.round(220.0f * reveal));
        int descriptionColor = SettingsGuiPalette.withAlpha(palette.panelText(),
                Math.round(232.0f * reveal));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getIosevkaRegular(), key,
                x, y, 13.0f, keyColor, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), description,
                x + 104.0f, y - 0.25f, 13.5f, descriptionColor, false);
    }

    static void updateContextClosed(ChromeMotion motion) {
        if (motion != null) {
            motion.context.update(Float.NaN, Float.NaN, false);
        }
    }

    static ContextBounds drawContext(float areaX, float areaY, float areaWidth, float areaHeight,
                                     float anchorX, float anchorY,
                                     float mouseX, float mouseY,
                                     List<XaeroMapSurface.MenuEntry> entries,
                                     SettingsGuiPalette palette,
                                     ChromeMotion motion) {
        ContextLayout layout = contextLayout(
                areaX, areaY, areaWidth, areaHeight, anchorX, anchorY, entries);
        ContextMotion state = motion.context;
        state.update(anchorX, anchorY, true);

        float reveal = clamp01(state.reveal.value);
        float eased = 1.0f - (1.0f - reveal) * (1.0f - reveal) * (1.0f - reveal);
        float travel = (1.0f - eased) * 9.0f * (layout.opensRight ? -1.0f : 1.0f);
        float x = layout.x + travel;
        float y = layout.y + (1.0f - eased) * 3.0f;

        float tip = layout.tip;
        float bodyX = layout.opensRight ? x + tip : x;
        float bodyWidth = layout.width - tip;
        float tipNormY = clamp((anchorY - y) / layout.height, 0.14f, 0.86f);

        UiPrimitive shell = layout.opensRight
                ? UiPrimitive.builder(x, y, layout.width, layout.height)
                    .customConvex(
                            0.0, tipNormY,
                            tip / layout.width, 0.0,
                            1.0, 0.0,
                            1.0, 1.0,
                            tip / layout.width, 1.0)
                    .rounding(5.0f)
                    .build()
                : UiPrimitive.builder(x, y, layout.width, layout.height)
                    .customConvex(
                            0.0, 0.0,
                            1.0 - tip / layout.width, 0.0,
                            1.0, tipNormY,
                            1.0 - tip / layout.width, 1.0,
                            0.0, 1.0)
                    .rounding(5.0f)
                    .build();

        Renderer2D renderer = Renderer2D.COLOR;
        int tintBase = SettingsGuiPalette.mix(
                palette.panelBgRight(), palette.controlSurfaceHover(), 0.035f);
        int tint = SettingsGuiPalette.withAlpha(tintBase, Math.round(92.0f * eased));
        int inner = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(17.0f * eased));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.020f * eased, 4.1f, inner);
        renderer.withLiquidGlassMaterial(material, () ->
                drawLiquidGlassPrimitive(renderer, shell, tint,
                        1.0f * eased, 1.0f * eased,
                        Renderer2D.LiquidGlassPreset.BALANCED));

        int titleColor = SettingsGuiPalette.withAlpha(
                palette.panelText(), Math.round(255.0f * eased));
        int muted = SettingsGuiPalette.withAlpha(
                palette.panelMuted(), Math.round(220.0f * eased));
        int accent = Themes.hudAccentGradient().start();

        int headerCount = layout.firstActionIndex;
        float headerX = bodyX + 16.0f;
        if (headerCount > 0) {
            XaeroMapSurface.MenuEntry primary = entries.get(0);
            String icon = primary.icon() == null ? "mouse-pointer-2" : primary.icon();
            renderer.svg(icon, headerX, y + 16.0f, 18.0f, 18.0f,
                    SvgRenderOptions.overrideColor(
                            SettingsGuiPalette.withAlpha(accent, Math.round(220.0f * eased))));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), primary.label(),
                    headerX + 27.0f, y + 13.0f, 15.5f, titleColor, false);
            if (headerCount > 1) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getIosevkaRegular(), entries.get(1).label(),
                        headerX + 27.0f, y + 33.0f, 11.5f, muted, false);
            }
        }

        if (layout.firstActionIndex > 0) {
            int divider = SettingsGuiPalette.withAlpha(
                    palette.panelText(), Math.round(18.0f * eased));
            renderer.quad(bodyX + 15.0f, y + layout.rowStartOffset - 1.0f,
                    bodyWidth - 30.0f, 1.0f, divider);
        }

        int hoveredIndex = -1;
        int pointedIndex = -1;
        for (int i = layout.firstActionIndex; i < entries.size(); i++) {
            float rowY = y + layout.rowStartOffset
                    + (i - layout.firstActionIndex) * layout.rowHeight;
            if (inside(mouseX, mouseY, bodyX + 7.0f, rowY,
                    bodyWidth - 14.0f, layout.rowHeight)) {
                pointedIndex = i;
                if (entries.get(i).enabled()) hoveredIndex = i;
                break;
            }
        }
        if (pointedIndex >= 0) {
            SystemCursor.set(entries.get(pointedIndex).enabled()
                    ? SystemCursor.CursorType.HAND : SystemCursor.CursorType.NOT_ALLOWED);
        }
        state.updateHover(hoveredIndex < 0 ? -1 : hoveredIndex - layout.firstActionIndex);

        if (state.hoverAlpha.value > 0.002f && state.hoverBand.value >= 0.0f) {
            float bandY = y + layout.rowStartOffset
                    + Math.max(0.0f, state.hoverBand.value) * layout.rowHeight;
            float a = clamp01(state.hoverAlpha.value) * eased;
            int bandA = SettingsGuiPalette.withAlpha(accent, Math.round(22.0f * a));
            int bandB = SettingsGuiPalette.withAlpha(accent, 0);
            float gradAngle = layout.opensRight ? 0.0f : 180.0f;
            renderer.quadGradientLinear(bodyX + 7.0f, bandY + 2.0f,
                    bodyWidth - 14.0f, layout.rowHeight - 4.0f,
                    bandB, bandA, gradAngle, 0.0f);
            float lineX = layout.opensRight ? bodyX + 7.0f : bodyX + bodyWidth - 9.0f;
            renderer.roundedRect(lineX, bandY + 9.0f, 2.0f,
                    layout.rowHeight - 18.0f, 1.0f,
                    SettingsGuiPalette.withAlpha(accent, Math.round(190.0f * a)));
        }

        for (int i = layout.firstActionIndex; i < entries.size(); i++) {
            XaeroMapSurface.MenuEntry entry = entries.get(i);
            float rowY = y + layout.rowStartOffset
                    + (i - layout.firstActionIndex) * layout.rowHeight;
            boolean destructive = entry.action() == XaeroMapSurface.ContextAction.DELETE;
            int visualIndex = i - layout.firstActionIndex;
            float hoverDistance = state.hoverBand.value < 0.0f
                    ? 100.0f : Math.abs(state.hoverBand.value - visualIndex);
            float hoverWeight = clamp01(state.hoverAlpha.value)
                    * (float) Math.exp(-hoverDistance * hoverDistance * 1.65f);
            float shift = hoverWeight * (layout.opensRight ? 3.0f : -3.0f);

            int baseColor = entry.enabled() ? titleColor : muted;
            if (destructive && entry.enabled()) {
                baseColor = SettingsGuiPalette.withAlpha(0xFFE96565, Math.round(245.0f * eased));
            } else if (entry.enabled() && hoverWeight > 0.001f) {
                baseColor = SettingsGuiPalette.mix(baseColor, accent, hoverWeight * 0.18f);
            }
            if (entry.icon() != null) {
                renderer.svg(entry.icon(), bodyX + 16.0f + shift, rowY + 9.0f,
                        17.0f, 17.0f, SvgRenderOptions.overrideColor(baseColor));
            }
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), entry.label(),
                    bodyX + 43.0f + shift, rowY + 9.0f, 13.5f, baseColor, false);
        }

        return new ContextBounds(
                x, y, layout.width, layout.height,
                bodyX,
                y + layout.rowStartOffset,
                layout.width - layout.tip,
                layout.rowHeight,
                layout.firstActionIndex);
    }

    static ContextBounds contextBounds(float areaX, float areaY, float areaWidth, float areaHeight,
                                       float anchorX, float anchorY,
                                       List<XaeroMapSurface.MenuEntry> entries) {
        ContextLayout layout = contextLayout(
                areaX, areaY, areaWidth, areaHeight, anchorX, anchorY, entries);
        return new ContextBounds(
                layout.x, layout.y, layout.width, layout.height,
                layout.opensRight ? layout.x + layout.tip : layout.x,
                layout.y + layout.rowStartOffset,
                layout.width - layout.tip,
                layout.rowHeight,
                layout.firstActionIndex);
    }

    private static ContextLayout contextLayout(float areaX, float areaY,
                                               float areaWidth, float areaHeight,
                                               float anchorX, float anchorY,
                                               List<XaeroMapSurface.MenuEntry> entries) {
        int firstAction = 0;
        while (firstAction < entries.size() && !entries.get(firstAction).enabled()) {
            firstAction++;
        }

        float width = 310.0f;
        float tip = 11.0f;
        float rowHeight = 36.0f;
        float headerHeight = firstAction > 1 ? 61.0f : firstAction == 1 ? 49.0f : 12.0f;
        float rowStartOffset = headerHeight;
        float height = headerHeight
                + Math.max(1, entries.size() - firstAction) * rowHeight
                + 10.0f;

        boolean opensRight = anchorX < areaX + areaWidth * 0.5f;
        float x = opensRight ? anchorX + 10.0f : anchorX - width - 10.0f;
        x = clamp(x, areaX + 8.0f, areaX + areaWidth - width - 8.0f);
        float y = anchorY - Math.min(38.0f, height * 0.20f);
        y = clamp(y, areaY + 8.0f, areaY + areaHeight - height - 8.0f);

        return new ContextLayout(x, y, width, height, tip, opensRight,
                rowStartOffset, rowHeight, firstAction);
    }

    static int contextEntryAt(ContextBounds bounds,
                              float mouseX, float mouseY,
                              List<XaeroMapSurface.MenuEntry> entries) {
        if (bounds == null
                || !inside(mouseX, mouseY, bounds.x(), bounds.y(), bounds.width(), bounds.height())) {
            return -1;
        }
        if (!inside(mouseX, mouseY,
                bounds.bodyX(), bounds.rowStartY(), bounds.bodyWidth(),
                bounds.height() - (bounds.rowStartY() - bounds.y()))) {
            return -1;
        }
        int visualRow = (int) ((mouseY - bounds.rowStartY()) / bounds.rowHeight());
        int index = bounds.firstActionIndex() + visualRow;
        return index >= bounds.firstActionIndex() && index < entries.size() ? index : -1;
    }

    private record ContextLayout(float x, float y, float width, float height,
                                 float tip, boolean opensRight,
                                 float rowStartOffset, float rowHeight,
                                 int firstActionIndex) {
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Map callouts are authored as convex analytic primitives, but a bad point layout must never
     * take the whole ClickGUI down. Preserve the optical surface with a rounded analytic-rect
     * fallback while keeping the intended primitive whenever it is shader-eligible.
     */
    private static void drawLiquidGlassPrimitive(Renderer2D renderer,
                                                 UiPrimitive primitive,
                                                 int tint,
                                                 float glassAlpha,
                                                 float blurAlpha,
                                                 Renderer2D.LiquidGlassPreset preset) {
        if (renderer == null || primitive == null) return;
        if (primitive.shaderEligible()) {
            renderer.liquidGlassPrimitive(primitive, tint, glassAlpha, blurAlpha, preset);
            return;
        }
        UiRect bounds = primitive.bounds();
        renderer.liquidGlassRect(
                bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                primitive.rounding(),
                tint, glassAlpha, blurAlpha, preset);
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    record ChromeState(
            boolean settingsOpen,
            boolean caveActive,
            boolean customDimension,
            boolean showWaypoints,
            boolean radarAvailable,
            boolean radarActive,
            boolean showClaims,
            boolean claimsActive,
            boolean showZoomButtons,
            XaeroMapSurface.Drawer drawer,
            boolean helpOpen,
            String settingsTooltip,
            String recenterTooltip,
            String caveTooltip,
            String dimensionTooltip,
            String waypointsTooltip,
            String playersTooltip,
            String radarTooltip,
            String claimsTooltip,
            String radarListTooltip,
            String controlsTooltip,
            String zoomOutTooltip,
            String zoomInTooltip
    ) {
    }

    private static String tr(String key, String fallback, Object... args) {
        try {
            String translated = I18n.get(key, args);
            if (translated != null && !translated.equals(key) && !translated.startsWith("Format error:")) {
                return translated;
            }
        } catch (Throwable ignored) {
        }
        return args.length == 0 ? fallback : String.format(fallback, args);
    }

    private static String trCount(String key, int count, String fallback) {
        return tr(key, fallback, count);
    }

    record HelpBounds(float x, float y, float width, float height) {
        boolean contains(float mouseX, float mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }

    record ContextBounds(float x, float y, float width, float height,
                         float bodyX, float rowStartY, float bodyWidth,
                         float rowHeight, int firstActionIndex) {
    }
}
