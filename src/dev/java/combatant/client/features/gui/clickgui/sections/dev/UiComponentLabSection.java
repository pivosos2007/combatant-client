/*
 * Combatant Client (GPL-3.0). Development-only component gallery, currently hosting the Google Material 3 kit.
 * Rendering is native to Combatant; Google components live in their own JS kit.
 */
package combatant.client.features.gui.clickgui.sections.dev;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.sections.ClickGuiSection;
import combatant.client.features.gui.clickgui.sections.ClickGuiSectionInfo;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputDevice;
import combatant.client.input.InputKeys;
import combatant.client.input.InputModifierMask;
import combatant.client.input.minecraft.CombatantTextInputBridge;
import combatant.client.util.text.SingleLineTextInput;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.ui.runtime.input.UiPointerEvent;
import combatant.client.render.engine.renderer.ui.runtime.input.UiScrollEvent;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

@ClickGuiSectionInfo(id = "combatant:component_lab", label = "UI Kits", order = 50)
public final class UiComponentLabSection implements ClickGuiSection {
    private final UiScriptModuleHandle handle = HudScriptLayouts.handle("combatant:modules/dev/component_gallery");
    private final CachedUiScriptRuntime scripts = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private UiRuntime runtime;
    private UiRuntime actionsBoundTo;
    private float x, y, width, height;
    private String kit = "google";
    private String page = "buttons";
    private String tab = "overview", chip = "all", segment = "week", list = "profile";
    private String focus = "";
    // Reuse the same editor, selection, clipboard and IME pipeline as the rest of ClickGUI.
    private final SingleLineTextInput textInput = new SingleLineTextInput(120);
    private String last = "none";
    private boolean enabled = true, checked, selected;
    private int radio, clicks;
    private float slider = 45f;
    private boolean sliderDragging;
    private float animatedTab = Float.NaN, animatedSwitch = Float.NaN;

    @Override
    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(1f, width);
        this.height = Math.max(1f, height);
    }

    private Map<String, Object> model() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("width", width);
        m.put("height", height);
        m.put("kit", kit);
        m.put("page", page);
        // Only active-page props contribute to the tree signature. In particular,
        // clicking a button must not launch a new Javet render just because the
        // dev event counter changed on a different page.
        switch (page) {
            case "selection" -> {
                m.put("chip", chip);
                m.put("segment", segment);
                m.put("radio", radio);
                m.put("enabled", enabled);
                m.put("checked", checked);
            }
            case "inputs" -> {
                m.put("focus", focus);
                m.put("value", textInput.text());
                m.put("caret", textInput.cursor());
                m.put("caretOffset", caretOffset());
                m.put("slider", slider);
            }
            case "navigation" -> {
                m.put("tab", tab);
                m.put("list", list);
            }
            case "states" -> {
                m.put("clicks", clicks);
                m.put("last", last);
            }
            default -> { }
        }
        return m;
    }

    private float caretOffset() {
        TextRenderer renderer = Fonts.renderer("Roboto", FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        if (renderer == null) return textInput.cursor() * 8f;
        renderer.beginSize(16f, true, false);
        try {
            String content = textInput.text();
            return (float) renderer.getWidth(content.substring(0, Math.max(0, Math.min(content.length(), textInput.cursor()))), false);
        } finally {
            renderer.end();
        }
    }

    @Override
    public void render(float mouseX, float mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null || ClickGuiRenderer.currentRenderer() == null) return;
        if (handle.consumeChanged()) {
            runtime = null;
            actionsBoundTo = null;
            scripts.reset();
        }
        if (!handle.ensureLoaded(minecraft.getResourceManager())) {
            HudScriptLayouts.reportLoadError(handle);
            return;
        }
        UiScriptModule script = handle.module();
        if (script == null) return;
        float dt = Math.max(0f, Math.min(.1f, AnimationUtility.deltaTime(AnimationUtility.Mode.NANOS)));
        float targetTab = switch (tab) { case "activity" -> 1f; case "settings" -> 2f; default -> 0f; };
        if (!Float.isFinite(animatedTab)) animatedTab = targetTab;
        if (!Float.isFinite(animatedSwitch)) animatedSwitch = enabled ? 1f : 0f;
        float response = 1f - (float) Math.exp(-14f * dt);
        animatedTab += (targetTab - animatedTab) * response;
        animatedSwitch += ((enabled ? 1f : 0f) - animatedSwitch) * response;
        Map<String, Object> m = model();
        TextRenderer font = Fonts.renderer("Roboto", FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        UiRuntime next = scripts.bake(handle, script, "component-lab", CachedUiScriptRuntime.signature(m),
                width, height, font, x, y, width, height, () -> m, null, this::animatedBounds);
        if (next == null) return;
        runtime = next;
        if (actionsBoundTo != next) {
            registerActions(next);
            actionsBoundTo = next;
        }
        runtime.input().updateHover(runtime.root(), mouseX, mouseY);
        if (sliderDragging) adjustSlider(mouseX);
        updateTextInputArea();
        runtime.render(new UiRenderContext(Renderer2D.COLOR, font, null, 0f, UiProjectionMode.CURRENT, 1f));
    }

    private static UiNode find(UiNode root, String key) {
        if (root == null) return null;
        if (key.equals(root.key())) return root;
        for (UiNode child : root.children()) {
            UiNode found = find(child, key);
            if (found != null) return found;
        }
        return null;
    }

    /** Patch drawn geometry using the live node's own computed layout, never hard-coded screen coordinates. */
    private Map<String, UiBounds> animatedBounds() {
        if (runtime == null || runtime.root() == null) return Map.of();
        Map<String, UiBounds> patches = new LinkedHashMap<>();
        UiNode tabNode = find(runtime.root(), "lab:tabs");
        if (tabNode != null && "navigation".equals(page)) {
            UiBounds b = tabNode.bounds();
            float itemWidth = b.width() / 3f;
            patches.put("lab:tabs:indicator", new UiBounds(
                    b.x() + animatedTab * itemWidth + 12f, b.y() + 45f,
                    Math.max(1f, itemWidth - 24f), 3f));
        }
        UiNode toggle = find(runtime.root(), "lab:switch");
        if (toggle != null && "selection".equals(page)) {
            UiBounds b = toggle.bounds();
            float handleSize = 16f + 8f * animatedSwitch;
            float left = 8f + 16f * animatedSwitch;
            patches.put("lab:switch:handle", new UiBounds(
                    b.x() + left, b.y() + (32f - handleSize) * .5f,
                    handleSize, handleSize));
            patches.put("lab:switch:check", new UiBounds(
                    b.x() + left + (handleSize - 16f) * .5f,
                    b.y() + (32f - 12f) * .5f, 16f, 12f));
        }
        return patches;
    }

    private void event(String name) {
        clicks++;
        last = name;
    }

    private void registerActions(UiRuntime ui) {
        ui.actions().register("lab:kit:google", action -> {
            kit = "google";
            page = "buttons";
            clearTextFocus();
            return true;
        });
        for (String p : new String[]{"buttons","selection","inputs","navigation","surfaces","states"}) {
            ui.actions().register("lab:nav:" + p, action -> {
                page = p;
                clearTextFocus();
                return true;
            });
        }
        for (String p : new String[]{"filled","tonal","elevated","outlined","text","icon",
                "fab","extended","enabled","save","cancel"}) {
            final String id = p;
            ui.actions().register("lab:clicked:" + id, action -> { event(id); return true; });
        }
        ui.actions().register("lab:toggle", action -> { enabled = !enabled; event("switch"); return true; });
        ui.actions().register("lab:check", action -> { checked = !checked; event("checkbox"); return true; });
        for (int i=0; i<3; i++) {
            final int index=i;
            ui.actions().register("lab:radio:"+i, action -> {radio=index; event("radio"); return true;});
        }
        for (String value : new String[]{"all","design","code","assist"}) {
            ui.actions().register("lab:chip:"+value, action -> {chip=value; event("chip"); return true;});
        }
        for (String value : new String[]{"day","week","month"}) {
            ui.actions().register("lab:segment:"+value, action -> {segment=value; event("segment"); return true;});
        }
        for (String value : new String[]{"overview","activity","settings"}) {
            ui.actions().register("lab:tab:"+value, action -> {tab=value; event("tabs"); return true;});
        }
        for (String value : new String[]{"profile","notifications","settings"}) {
            ui.actions().register("lab:list:"+value, action -> {list=value; event("list"); return true;});
        }
        for (String value : new String[]{"filled","outlined"}) {
            ui.actions().register("lab:focus:"+value, action -> {
                focusTextField(value, false, 0f);
                return true;
            });
        }
    }

    private UiNode focusedTextField() {
        if (runtime == null || runtime.root() == null || focus.isEmpty() || !"inputs".equals(page)) return null;
        return find(runtime.root(), "lab:field:" + focus);
    }

    private void clearTextFocus() {
        focus = "";
        textInput.unfocus();
    }

    /** Keep platform text focus and IME area in sync with the actual UiNode layout. */
    private void updateTextInputArea() {
        UiNode field = focusedTextField();
        if (field == null) {
            if (!focus.isEmpty()) clearTextFocus();
            return;
        }
        UiBounds bounds = field.bounds();
        TextRenderer font = Fonts.renderer("Roboto", FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        textInput.layout(bounds.x() + 16f, Math.max(1f, bounds.width() - 32f), font, 16f);
        CombatantTextInputBridge.focus(textInput,
                Math.round(bounds.x()), Math.round(bounds.y()),
                Math.round(bounds.x() + bounds.width()), Math.round(bounds.y() + bounds.height()));
    }

    private void focusTextField(String field, boolean positionCaret, float mouseX) {
        if (!"filled".equals(field) && !"outlined".equals(field)) return;
        focus = field;
        updateTextInputArea();
        if (positionCaret) {
            boolean shift = CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.LEFT_SHIFT)
                    || CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.RIGHT_SHIFT);
            textInput.beginDrag(mouseX, shift);
        }
    }

    private static int runtimePointerButton(int button) {
        return switch (button) {
            case InputKeys.MOUSE_LEFT -> 0;
            case InputKeys.MOUSE_RIGHT -> 1;
            case InputKeys.MOUSE_MIDDLE -> 2;
            default -> button;
        };
    }

    private void adjustSlider(float pointerX) {
        UiNode node = runtime == null ? null : find(runtime.root(), "lab:slider");
        if (node == null) return;
        UiBounds b = node.bounds();
        float fraction = Math.max(0f, Math.min(1f, (pointerX - b.x()) / Math.max(1f, b.width())));
        slider = Math.round(fraction * 100f);
    }

    @Override
    public void mouseMoved(float mouseX, float mouseY) {
        if (runtime == null || runtime.root() == null) return;
        runtime.input().updateHover(runtime.root(), mouseX, mouseY);
        if (sliderDragging) adjustSlider(mouseX);
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        if (runtime == null) return false;
        boolean handled = runtime.input().pointerDown(runtime.root(),
                new UiPointerEvent(mouseX, mouseY, runtimePointerButton(button)));
        UiNode pressed = runtime.input().state().pressedNode();
        if (button == InputKeys.MOUSE_LEFT) {
            String pressedKey = pressed == null ? "" : pressed.key();
            if ("lab:field:filled".equals(pressedKey)) focusTextField("filled", true, mouseX);
            else if ("lab:field:outlined".equals(pressedKey)) focusTextField("outlined", true, mouseX);
            else if (!focus.isEmpty()) clearTextFocus();
        }
        sliderDragging = button == InputKeys.MOUSE_LEFT && pressed != null && "lab:slider".equals(pressed.key());
        if (sliderDragging) adjustSlider(mouseX);
        return handled;
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (sliderDragging) {
            adjustSlider(mouseX);
            event("slider");
            sliderDragging = false;
        }
        if (runtime != null) runtime.input().pointerUp(runtime.root(),
                new UiPointerEvent(mouseX, mouseY, runtimePointerButton(button)));
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return runtime != null && runtime.input().scroll(runtime.root(),
                new UiScrollEvent(mouseX, mouseY, 0, -amount));
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focus.isEmpty()) return false;
        // Committed Unicode normally arrives via MinecraftInputAdapter and
        // CombatantTextInputBridge. Only use this for legacy char callbacks.
        if (!CombatantTextInputBridge.owns(textInput)) textInput.type(chr);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int shortcutKey, int modifiers) {
        if (!focus.isEmpty()) {
            if (keyCode == InputKeys.ESCAPE || keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER) {
                clearTextFocus();
                return true;
            }
            // Both physical key codes and shortcut keys are already normalized
            // by ClickGuiRenderer/CombatantInput; do not reinterpret them as platform key codes.
            textInput.keyPressed(keyCode, shortcutKey, modifiers);
            return true;
        }
        UiNode focused = runtime != null ? runtime.input().state().focusedNode() : null;
        if (focused != null && "lab:slider".equals(focused.key())) {
            float step = InputModifierMask.has(modifiers, InputModifierMask.SHIFT) ? 10f : 1f;
            if (keyCode == InputKeys.LEFT || keyCode == InputKeys.DOWN) {
                slider = Math.max(0f, slider - step);
                return true;
            }
            if (keyCode == InputKeys.RIGHT || keyCode == InputKeys.UP) {
                slider = Math.min(100f, slider + step);
                return true;
            }
            if (keyCode == InputKeys.HOME) { slider = 0f; return true; }
            if (keyCode == InputKeys.END) { slider = 100f; return true; }
        }
        return false;
    }

    @Override public void onSelected() { selected = true; }
    @Override public void onDeselected() { selected = false; clearTextFocus(); sliderDragging = false; }
    @Override public boolean isVisible() { return selected; }
    @Override public boolean usesFullViewport() { return true; }
}
