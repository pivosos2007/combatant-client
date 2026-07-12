/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.theme;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.theme.Themes;
import combatant.client.features.theme.EditableClickGuiTheme;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.helpers.ScissorFunction;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemeEditorPreviewComponent {
    private static final String PREVIEW_LAYOUT_ID = "combatant:api/clickgui/theme_preview";
    private static final float DESIGN_W = 320f;
    private static final float DESIGN_H = 190f;

    private final Minecraft mc = Minecraft.getInstance();
    private final UiScriptModuleHandle previewModuleHandle = HudScriptLayouts.handle(PREVIEW_LAYOUT_ID);
    private final CachedUiScriptRuntime previewRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private float previewAnim;

    public void reset() {
        previewAnim = 0f;
        previewRuntime.reset();
    }

    public void render(EditableClickGuiTheme editor,
                       float menuX,
                       float menuY,
                       float menuW,
                       float menuH,
                       float mx,
                       float my,
                       float scale) {
        if (editor == null) {
            previewAnim = AnimationUtility.approach(previewAnim, 0f, AnimationUtility.deltaTime(), 12f);
            return;
        }

        float dt = AnimationUtility.deltaTime();
        previewAnim = AnimationUtility.approach(previewAnim, 1f, dt, 12f);
        previewAnim = AnimationUtility.snap(previewAnim, 1f, 0.01f);
        float alpha = AnimationUtility.easeInOutCubic(previewAnim);
        if (alpha <= 0.01f) return;

        float safePad = 22f * scale;
        float availableW = Math.max(1f, menuW - safePad * 2f);
        float availableH = Math.max(1f, menuH - safePad * 2f);
        float unit = Math.min(scale, Math.min(availableW / DESIGN_W, availableH / DESIGN_H));
        if (unit <= 0.05f) return;

        float panelW = DESIGN_W * unit;
        float panelH = DESIGN_H * unit;
        float panelX = menuX + (menuW - panelW) * 0.5f;
        float panelY = menuY + (menuH - panelH) * 0.5f;

        Themes.ThemeEntry entry = editor.toEntry();
        if (entry == null || entry.theme() == null) return;

        boolean clip = ScissorFunction.pushRaw(panelX, panelY, panelW, panelH);
        try {
            renderScripted(entry, panelX, panelY, panelW, panelH, unit, alpha);
        } finally {
            if (clip) ScissorFunction.pop();
        }
    }

    public boolean mouseClicked(EditableClickGuiTheme editor, float mx, float my, int button) {
        return false;
    }

    private void renderScripted(Themes.ThemeEntry entry,
                                float panelX,
                                float panelY,
                                float panelW,
                                float panelH,
                                float unit,
                                float alpha) {
        if (previewModuleHandle.isRuntimeBlocked() || mc == null || mc.getResourceManager() == null) return;
        HudScriptLayouts.pollReloadCombo(mc);
        if (previewModuleHandle.consumeChanged()) {
            previewRuntime.reset();
        }
        UiScriptModule module = ensurePreviewModule();
        if (module == null) return;

        Map<String, Object> props = previewTemplateProps(entry, panelW, panelH, unit, alpha);
        long treeSignature = CachedUiScriptRuntime.signature(props);
        long layoutSignature = CachedUiScriptRuntime.mix(
                CachedUiScriptRuntime.mix(CachedUiScriptRuntime.mix(23L, panelX), panelY),
                CachedUiScriptRuntime.mix(CachedUiScriptRuntime.mix(37L, panelW), panelH)
        );

        UiRuntime runtime = previewRuntime.updatePersistent(
                previewModuleHandle,
                module,
                "theme-preview",
                treeSignature,
                layoutSignature,
                panelW,
                panelH,
                ClickGuiRenderer.getInterRegular(),
                panelX,
                panelY,
                panelW,
                panelH,
                () -> props,
                () -> Collections.<String, Map<String, ?>>emptyMap()
        );
        if (runtime == null) return;
        ClickGuiRenderer.flushRenderer();
        runtime.render(new UiRenderContext(Renderer2D.COLOR, ClickGuiRenderer.getInterRegular(), null, 0f, UiProjectionMode.CURRENT, alpha));
        ClickGuiRenderer.flushRenderer();
    }

    private UiScriptModule ensurePreviewModule() {
        if (!previewModuleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(previewModuleHandle);
            return null;
        }
        previewModuleHandle.consumeChanged();
        return previewModuleHandle.module();
    }

    private Map<String, Object> previewTemplateProps(Themes.ThemeEntry entry,
                                                     float width,
                                                     float height,
                                                     float unit,
                                                     float alpha) {
        Themes.Theme theme = entry.theme();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("width", width);
        props.put("height", height);
        props.put("unit", unit);
        props.put("alpha", alpha);
        props.put("entryName", entry.name() == null || entry.name().isBlank() ? "Custom Theme" : entry.name());

        props.put("windowBg", hex(theme.windowBg()));
        props.put("windowHeader", hex(theme.windowHeader()));
        props.put("windowStroke", hex(theme.windowStroke()));
        props.put("surface", hex(theme.surface()));
        props.put("surfaceHover", hex(theme.surfaceHover()));
        props.put("cardEnabled", hex(theme.cardEnabled()));
        props.put("cardDisabled", hex(theme.cardDisabled()));
        props.put("textPrimary", hex(theme.textPrimary()));
        props.put("textMuted", hex(theme.textMuted()));
        props.put("accent", hex(theme.accent()));
        props.put("accentSoft", hex(theme.accentSoft()));
        props.put("strokeSoft", hex(theme.strokeSoft()));

        putGradient(props, "windowGradient", entry.windowGradient());
        putGradient(props, "headerGradient", entry.headerGradient());
        putGradient(props, "surfaceGradient", entry.surfaceGradient());
        putGradient(props, "cardGradient", entry.cardGradient());
        putGradient(props, "strokeGradient", entry.strokeGradient());
        return props;
    }

    private static void putGradient(Map<String, Object> props, String prefix, Themes.GradientSpec gradient) {
        boolean enabled = gradient != null && gradient.enabled();
        props.put(prefix + "Enabled", enabled);
        props.put(prefix + "Start", hex(gradient != null ? gradient.start() : 0xFFFFFFFF));
        props.put(prefix + "End", hex(gradient != null ? gradient.end() : 0xFFFFFFFF));
        props.put(prefix + "Angle", gradient != null ? gradient.angleDeg() : 90f);
    }

    private static String hex(int argb) {
        return "#" + String.format("%08X", argb);
    }
}
