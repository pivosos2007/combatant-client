/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.settings;

import combatant.client.features.gui.diagnostics.FailureText;
import combatant.client.features.gui.chat.diagnostics.FailureDiagnostics;

import combatant.client.runtime.error.FailureIsolation;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsGlassMaterial;
import combatant.client.features.module.Module;
import combatant.client.config.SettingOwner;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureRegistry.Failure;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.SystemCursor;

import java.util.ArrayList;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.List;

/** Shared ClickGUI fault presentation. Uses the same Icons-font warning glyph as HudNotifier. */
public final class SettingErrorView {
    private static final int RED = 0xFFFF5656;
    private static final int RED_SOFT = 0xFFFF9696;
    private static final Map<Object, FailureSetting> FAILURE_VIEWS = new IdentityHashMap<>();
    private static final Map<Object, Float> HOVER_ANIMATIONS = new IdentityHashMap<>();
    private static final Map<Object, float[]> ACTION_HOVER_ANIMATIONS = new IdentityHashMap<>();
    private SettingErrorView() { }

    public static void clearSession() {
        synchronized (FAILURE_VIEWS) { FAILURE_VIEWS.clear(); }
        synchronized (HOVER_ANIMATIONS) { HOVER_ANIMATIONS.clear(); }
        synchronized (ACTION_HOVER_ANIMATIONS) { ACTION_HOVER_ANIMATIONS.clear(); }
    }

    public static boolean hasFailure(Module module) {
        return FailureIsolation.moduleOrSettingFailure(module) != null;
    }

    public static Failure failure(Module module) {
        return FailureIsolation.moduleOrSettingFailure(module);
    }

    public static String description(Module module) {
        if (module == null) return "";
        return module.getDescription();
    }

    public static List<Setting> withDiagnostics(Module module, List<Setting> source) {
        return withDiagnostics((Object) module, source);
    }

    public static List<Setting> withDiagnostics(Object owner, List<Setting> source) {
        if (owner == null) return source == null ? List.of() : source;
        ArrayList<Setting> result = new ArrayList<>();
        if (ErrorHandler.failure(owner) != null) {
            FailureSetting view;
            synchronized (FAILURE_VIEWS) {
                view = FAILURE_VIEWS.computeIfAbsent(owner, FailureSetting::new);
            }
            result.add(view);
        }
        if (source != null) for (Setting setting : source) {
            if (!(setting instanceof FailureSetting)) result.add(setting);
        }
        return result;
    }

    public static void warning(float x, float y, float size, float alpha) {
        TextRenderer icon = Fonts.renderer("Icons", FontInfo.Type.Regular, ClickGuiRenderer.getInterRegular());
        ClickGuiRenderer.drawText(icon, "L", x, y, size, LayoutRender2D.alpha(RED, alpha), false);
    }

    public static float height() { return 118f * SettingRenderContext.scale(); }

    public static void render(Object owner, Failure failure, float x, float y, float w, float mx, float my) {
        if (failure == null) return;
        float s = SettingRenderContext.scale();
        float h = height();
        float cardX = x + 2f * s;
        float cardY = y + 3f * s;
        float cardW = Math.max(1f, w - 4f * s);
        float cardH = h - 6f * s;
        float radius = 6f * s;
        boolean hovered = inside(mx, my, cardX, cardY, cardW, cardH);
        float hover = hoverAnimation(owner, hovered);
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        int surface = SettingsGuiPalette.mix(
                palette.controlSurface(), palette.controlSurfaceHover(), 0.18f + hover * 0.24f);
        int edge = SettingsGuiPalette.mix(palette.glassEdgeSoft(), RED, 0.10f + hover * 0.12f);
        SettingsGlassMaterial.control(cardX, cardY, cardW, cardH, radius, surface, edge);

        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        TextRenderer medium = UnifiedSettingsSkin.fontMedium();
        float innerX = cardX + 10f * s;
        float innerW = Math.max(1f, cardW - 20f * s);
        String title = FailureText.tr("card.incident", failure.id());
        float iconSize = 11f * s;
        float titleSize = 8.4f * s;
        warning(innerX, cardY + 9f * s, iconSize, 1f);
        String fittedTitle = ClickGuiRenderer.fitText(medium, title, titleSize, innerW - iconSize - 6f * s);
        draw(medium, fittedTitle, innerX + iconSize + 6f * s, cardY + 8.2f * s, titleSize, RED);

        float contextSize = 6.8f * s;
        String state = FailureText.state(failure.state());
        String phase = FailureText.phase(failure.phase());
        draw(font, ClickGuiRenderer.fitText(font, state, contextSize, innerW), innerX, cardY + 23.5f * s,
                contextSize, SettingsGuiPalette.mix(palette.panelMuted(), RED_SOFT, 0.30f));
        draw(font, ClickGuiRenderer.fitText(font, phase, contextSize, innerW), innerX, cardY + 33.5f * s,
                contextSize, palette.panelMuted());

        LayoutRender2D.horizontalHairline(
                innerX, cardY + 46f * s, innerW, 0.55f * s,
                LayoutRender2D.alpha(palette.glassEdgeSoft(), 0.15f),
                LayoutRender2D.alpha(palette.glassEdgeStrong(), 0.55f));
        drawReason(font, shortReason(failure.summary()), innerX, cardY + 53f * s,
                innerW, 7.2f * s, palette.panelText());

        float by = cardY + 78f * s;
        boolean retryAllowed = owner instanceof Module module
                ? FailureIsolation.canRetryModule(module)
                : owner instanceof Setting;
        actionSection(owner, innerX, by, innerW, 22f * s, retryAllowed,
                FailureText.tr("action.details"),
                FailureText.tr(owner instanceof Module ? "action.retry_module" : "action.retry_setting"),
                mx, my, s);
    }

    private static String shortReason(String summary) {
        if (summary == null || summary.isBlank()) return "";
        int separator = summary.indexOf(": ");
        return separator > 0 ? summary.substring(separator + 2) : summary;
    }

    private static void draw(TextRenderer font, String value, float x, float y, float size, int color) {
        ClickGuiRenderer.drawText(font, value == null ? "" : value, x, y, size, color, false);
    }

    private static void drawReason(TextRenderer font, String value, float x, float y, float w,
                                   float size, int color) {
        List<String> lines = ClickGuiRenderer.wrapText(font, value == null ? "" : value, size, w, 2);
        float lineH = ClickGuiRenderer.textHeight(font, size) + 1.5f * SettingRenderContext.scale();
        for (int i = 0; i < lines.size(); i++) {
            draw(font, lines.get(i), x, y + i * lineH, size, color);
        }
    }

    private static void actionSection(Object owner, float x, float y, float w, float h,
                                      boolean retryAllowed, String detailsLabel, String retryLabel,
                                      float mx, float my, float s) {
        float detailsW = retryAllowed ? w * 0.5f : w;
        float retryX = x + detailsW;
        float retryW = Math.max(0f, w - detailsW);
        boolean detailsHovered = inside(mx, my, x, y, detailsW, h);
        boolean retryHovered = retryAllowed && inside(mx, my, retryX, y, retryW, h);
        float detailsHover = actionHoverAnimation(owner, 0, detailsHovered);
        float retryHover = actionHoverAnimation(owner, 1, retryHovered);
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float radius = 4f * s;
        int base = SettingsGuiPalette.mix(palette.controlSurface(), palette.controlSurfaceHover(), 0.12f);
        int stroke = SettingsGuiPalette.mix(palette.glassEdgeSoft(), RED,
                0.08f + Math.max(detailsHover, retryHover) * 0.10f);
        LayoutRender2D.rounded(x, y, w, h, radius, base);

        float inset = 1.2f * s;
        if (detailsHover > 0.0001f) {
            int highlight = LayoutRender2D.alpha(
                    SettingsGuiPalette.mix(base, palette.controlSurfaceHover(), 0.82f),
                    detailsHover);
            LayoutRender2D.rounded(x + inset, y + inset,
                    Math.max(1f, detailsW - inset * (retryAllowed ? 1f : 2f)), h - inset * 2f,
                    3f * s, highlight);
        }
        if (retryAllowed && retryHover > 0.0001f) {
            int highlight = LayoutRender2D.alpha(
                    SettingsGuiPalette.mix(base, palette.controlSurfaceHover(), 0.82f),
                    retryHover);
            LayoutRender2D.rounded(retryX, y + inset,
                    Math.max(1f, retryW - inset), h - inset * 2f,
                    3f * s, highlight);
        }
        if (retryAllowed) {
            int separator = SettingsGuiPalette.mix(palette.glassEdgeSoft(), RED,
                    0.06f + Math.max(detailsHover, retryHover) * 0.08f);
            LayoutRender2D.rect(retryX - 0.25f * s, y + 4f * s,
                    0.5f * s, h - 8f * s, separator);
        }
        LayoutRender2D.roundedStroke(x, y, w, h, radius, .45f * s, stroke);

        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        float size = 6.4f * s;
        float textY = y + (h - ClickGuiRenderer.textHeight(font, size)) * .5f - .25f * s;
        drawActionLabel(font, detailsLabel, x, detailsW, textY, size,
                SettingsGuiPalette.mix(palette.panelText(), 0xFFFFD1D1, detailsHover));
        if (retryAllowed) {
            drawActionLabel(font, retryLabel, retryX, retryW, textY, size,
                    SettingsGuiPalette.mix(palette.panelText(), 0xFFFFD1D1, retryHover));
        }
        if (detailsHovered || retryHovered) SystemCursor.set(SystemCursor.CursorType.HAND);
    }

    private static void drawActionLabel(TextRenderer font, String label, float x, float w,
                                        float y, float size, int color) {
        String fitted = ClickGuiRenderer.fitText(font, label, size, Math.max(1f, w - 5f * SettingRenderContext.scale()));
        float textX = x + (w - ClickGuiRenderer.textWidth(font, fitted, size)) * .5f;
        draw(font, fitted, textX, y, size, color);
    }

    private static float hoverAnimation(Object owner, boolean hovered) {
        synchronized (HOVER_ANIMATIONS) {
            float current = HOVER_ANIMATIONS.getOrDefault(owner, 0f);
            float next = AnimationUtility.approach(current, hovered ? 1f : 0f, 0.18f);
            HOVER_ANIMATIONS.put(owner, next);
            return next;
        }
    }

    private static float actionHoverAnimation(Object owner, int index, boolean hovered) {
        synchronized (ACTION_HOVER_ANIMATIONS) {
            float[] values = ACTION_HOVER_ANIMATIONS.computeIfAbsent(owner, ignored -> new float[2]);
            float target = hovered ? 1f : 0f;
            float next = AnimationUtility.approach(
                    values[index], target, AnimationUtility.deltaTime(), 14f);
            values[index] = AnimationUtility.snap(next, target, 0.0001f);
            return values[index];
        }
    }

    private static boolean inside(double mx,double my,float x,float y,float w,float h) {
        return mx>=x && mx<=x+w && my>=y && my<=y+h;
    }

    public static boolean click(Object owner, Failure failure,float x,float y,float w,double mx,double my,int button) {
        if(failure==null || button!=0) return false;
        float s = SettingRenderContext.scale();
        float cardX = x + 2f * s;
        float cardW = Math.max(1f, w - 4f * s);
        float by = y + 81f * s;
        float innerX = cardX + 10f * s;
        float innerW = cardW - 20f * s;
        boolean retryAllowed = owner instanceof Module module
                ? FailureIsolation.canRetryModule(module)
                : owner instanceof Setting;
        float detailsW = retryAllowed ? innerW * 0.5f : innerW;
        if (inside(mx, my, innerX, by, detailsW, 22f * s)) {
            FailureDiagnostics.show(failure);
            return true;
        }
        if (retryAllowed) {
            if (!inside(mx, my, innerX + detailsW, by, innerW - detailsW, 22f * s)) return false;
            if (owner instanceof Module module) FailureIsolation.retryModule(module);
            else if (owner instanceof Setting setting) FailureIsolation.retrySetting(setting);
            return true;
        }
        return false;
    }

    /** A synthetic, non-persistent setting: module recovery remains accessible when normal settings are empty. */
    private static final class FailureSetting extends Setting {
        private final Object owner;
        private float x,y,w;
        private FailureSetting(Object owner) {
            super("ErrorHandler");
            this.owner=owner;
            if (owner instanceof SettingOwner settingOwner) setParent(settingOwner);
            setI18nEnabled(false,false);
        }
        @Override public String getDisplayName() { return FailureText.tr("card.name"); }
        @Override public float getHeight() { return SettingErrorView.height(); }
        @Override public void render(float x,float y,float w,float mx,float my) {
            this.x=x;this.y=y;this.w=w;
            SettingErrorView.render(owner,ErrorHandler.failure(owner),x,y,w,mx,my);
        }
        @Override public void mouseClicked(double mx,double my,int button) {
            SettingErrorView.click(owner,ErrorHandler.failure(owner),x,y,w,mx,my,button);
        }
    }
}
