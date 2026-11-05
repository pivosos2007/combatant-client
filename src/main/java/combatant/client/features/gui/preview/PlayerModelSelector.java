/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview;

import combatant.client.config.ConfigSerializer;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.module.modules.visuals.PlayerModel;
import combatant.client.features.playermodel.ModelCatalog;
import combatant.client.features.playermodel.PlayerModels;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.input.UiPointerEvent;
import combatant.client.render.engine.renderer.ui.runtime.input.UiScrollEvent;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.util.resources.asset.UiScriptAsset;
import net.minecraft.client.resources.language.I18n;

import java.util.*;

@UiScriptAsset("combatant:modules/preview/player_models")
public final class PlayerModelSelector {
    private final PlayerModel module;
    private final PlayerModel.Target target;
    private final UiScriptModuleHandle handle = HudScriptLayouts.handle(PlayerModelSelector.class);
    private final CachedUiScriptRuntime scripts = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private UiRuntime runtime;
    private boolean variants;
    private float x, y, width, height;

    public PlayerModelSelector(PlayerModel module) { this(module, PlayerModel.Target.SELF); }
    public PlayerModelSelector(PlayerModel module, PlayerModel.Target target) {
        this.module = module;
        this.target = target;
    }

    public void render(VisualPreviewSceneContext scene) {
        x = 12f; y = scene.subjectY(); width = scene.screen().provider().leftPanelWidth(scene.width()) - 12;
        height = scene.subjectHeight();
        if (!handle.ensureLoaded(scene.minecraft().getResourceManager())) {
            HudScriptLayouts.reportLoadError(handle);
            return;
        }
        if (handle.consumeChanged()) scripts.reset();
        var catalog = ModelCatalog.discover(scene.minecraft().getResourceManager()).entries();
        var theme = Theme.theme();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("width", width); props.put("height", height);
        Map<String, String> palette = new LinkedHashMap<>();
        palette.put("background", hex(theme.windowBg())); palette.put("surface", hex(theme.windowBg()));
        palette.put("raised", hex(theme.surface())); palette.put("field", hex(theme.windowHeader()));
        palette.put("border", hex(theme.strokeSoft())); palette.put("text", hex(theme.textPrimary()));
        palette.put("muted", hex(theme.textMuted())); palette.put("accent", hex(theme.accent()));
        palette.put("selected", hex(theme.accentSoft())); palette.put("disabled", hex(theme.textMuted()));
        palette.put("disabledSurface", hex(theme.surface())); palette.put("onAccent", contrast(theme.accent()));
        props.put("palette", palette);
        var selected = catalog.stream().filter(e -> e.id().equals(module.model(target).get())).findFirst().orElse(null);
        List<ModelCatalog.Entry> choices;
        if (variants) choices = selected == null ? List.of() : catalog.stream().filter(e -> e.model().equals(selected.model())).toList();
        else {
            Map<String, ModelCatalog.Entry> models = new LinkedHashMap<>();
            for (var entry : catalog) {
                models.putIfAbsent(entry.model(), entry);
            }
            choices = List.copyOf(models.values());
        }
        Map<String, Long> textureCounts = new HashMap<>();
        for (var entry : catalog) textureCounts.merge(entry.model(), 1L, Long::sum);
        String variantsCountLabel = I18n.get("playermodel.selector.variantsCountLabel");
        props.put("items", choices.stream().map(e -> Map.of("id", e.id(), "label", e.label(),
                "icon", e.icon() == null ? "" : e.icon(),
                "detail", variants ? shortResourceName(e.texture())
                        : variantsCountLabel + ": " + textureCounts.getOrDefault(e.model(), 1L))).toList());
        props.put("selected", selected == null ? "" : variants ? selected.id() : choices.stream()
                .filter(e -> e.model().equals(selected.model())).findFirst().orElse(selected).id());
        props.put("selection", selected == null ? "" : selected.label()); props.put("variants", variants);
        props.put("geometry", selected == null ? "" : selected.model());
        props.put("hasSelection", selected != null);
        props.put("enabled", module.isEnabled() && module.replacement(target).get());
        for (String key : List.of("title", "modelsLabel", "variantsLabel", "applyLabel", "restoreLabel", "reloadLabel", "reloadShortLabel", "emptyLabel", "emptyVariantsLabel", "subtitle", "currentLabel", "noSelectionLabel")) {
            props.put(key, I18n.get("playermodel.selector." + key));
        }
        runtime = scripts.bakeTracked(handle, handle.module(), "player-model-selector",
                width, height, ClickGuiRenderer.getOnestMedium(),
                x, y, width, height, () -> props);
        if (runtime == null) return;
        runtime.actions().register("models:tab:models", action -> { variants = false; return true; });
        runtime.actions().register("models:tab:variants", action -> { variants = true; return true; });
        runtime.actions().register("models:reload", action -> { ModelCatalog.invalidate(); PlayerModels.invalidate(); return true; });
        runtime.actions().register("models:apply", action -> {
            boolean enable = !(module.isEnabled() && module.replacement(target).get());
            module.replacement(target).set(enable);
            if (enable && !module.isEnabled()) module.setEnabledManual(true);
            ConfigSerializer.requestSave(module);
            return true;
        });
        for (var choice : choices) runtime.actions().register("models:select:" + choice.id(), action -> {
            module.model(target).addOption(choice.id());
            module.model(target).set(choice.id());
            module.replacement(target).set(true);
            if (!module.isEnabled()) module.setEnabledManual(true);
            ConfigSerializer.requestSave(module);
            return true;
        });
        ClickGuiRenderer.flushRenderer();
        runtime.render(new UiRenderContext(Renderer2D.COLOR, ClickGuiRenderer.getOnestMedium(), null,
                0f, UiProjectionMode.CURRENT, 1f), () -> runtime.input().updateHover(runtime.root(),
                scene.screen().mouseXVirtual(), scene.screen().mouseYVirtual()));
        ClickGuiRenderer.flushRenderer();
    }

    public boolean mousePressed(float mx, float my, int button) {
        if (!contains(mx, my)) return false;
        if (runtime != null) runtime.input().pointerDown(runtime.root(), new UiPointerEvent(mx, my, pointerButton(button)));
        return true;
    }
    public void mouseReleased(float mx, float my, int button) {
        if (runtime != null) runtime.input().pointerUp(runtime.root(), new UiPointerEvent(mx, my, pointerButton(button)));
    }
    public boolean mouseScrolled(float mx, float my, double amount) {
        if (!contains(mx, my)) return false;
        if (runtime != null) runtime.input().scroll(runtime.root(), new UiScrollEvent(mx, my, 0, -amount));
        return true;
    }
    public void close() { scripts.reset(); runtime = null; }
    private static String shortResourceName(String path) {
        if (path == null || path.isBlank()) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
    private boolean contains(float mx, float my) { return mx >= x && mx <= x + width && my >= y && my <= y + height; }
    private static String hex(int color) { return String.format("#%08X", color); }
    private static String contrast(int color) {
        double luminance = 0;
        double[] weights = {.2126, .7152, .0722};
        for (int i = 0; i < 3; i++) {
            double v = ((color >>> (16 - i * 8)) & 255) / 255.0;
            luminance += weights[i] * (v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4));
        }
        return luminance > .179 ? "#FF000000" : "#FFFFFFFF";
    }
    private static int pointerButton(int button) {
        return button == combatant.client.input.InputKeys.MOUSE_LEFT ? 0
                : button == combatant.client.input.InputKeys.MOUSE_RIGHT ? 1
                : button == combatant.client.input.InputKeys.MOUSE_MIDDLE ? 2 : button;
    }
}
