/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui;

import combatant.client.util.text.SingleLineTextInput;
import combatant.client.input.InputAction;
import combatant.client.input.InputKey;
import combatant.client.input.InputKeyCatalog;
import combatant.client.input.InputKeys;
import combatant.client.input.InputModifiers;
import combatant.client.input.minecraft.CombatantTextInputBridge;
import combatant.client.input.minecraft.CombatantTextInputTarget;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.addon.ClickGuiSectionManager;
import combatant.client.features.gui.clickgui.settings.*;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import combatant.client.features.gui.mainmenu.CombatantMainMenuScreen;
import combatant.client.features.gui.mainmenu.MainMenuBackgroundRenderer;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.ConfigSerializer;
import combatant.client.features.gui.clickgui.editor.ClickGuiTextEditorState;
import combatant.client.features.gui.clickgui.picker.ClickGuiPickerState;
import combatant.client.features.gui.clickgui.protocol.CombatProtocolHeuristicsEditorState;
import combatant.client.features.gui.clickgui.sections.ClickGuiSection;
import combatant.client.features.gui.clickgui.sections.settings.SettingsTabRuntime;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.preview.HudPreviewHost;
import combatant.client.features.gui.hud.nondraggable.impl.DynamicIsland;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.modules.misc.ClickGui;
import combatant.client.mixininterface.IGuiGraphics;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.profiler.RenderProfiler2D;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.features.gui.clickgui.sound.GuiSound;
import combatant.client.util.logging.DebugLog;
import combatant.client.events.Events;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static combatant.client.features.theme.Theme.theme;

public enum ClickGuiRenderer {
    ;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final CombatantTextInputTarget TEXT_INPUT_TARGET = text -> {
        if (text == null || text.isEmpty()) return false;
        boolean handled = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isBmpCodePoint(codePoint)) handled |= onCharTyped((char) codePoint);
        }
        return handled;
    };
    private static final String MODULES_TAB_ID = "combatant:modules";
    private static final String SETTINGS_TAB_ID = "combatant:settings";
    private static final String MAP_TAB_ID = "combatant:map";
    // Present only in dev builds. The gallery, like Map, completely covers the 3D scene.
    private static final String UI_KITS_TAB_ID = "combatant:component_lab";
    private static final float TAB_HEIGHT = 34f;
    private static final float TAB_GAP = 0f;
    private static final float TAB_PAD_X = 18f;
    private static final float TAB_FONT = 17f;
    private static final float TAB_MIN_W = 96f;
    private static float[] tabX = new float[0];
    private static float[] tabW = new float[0];
    private static final float LIFECYCLE_OPEN_SPEED = 9.5f;
    private static final float LIFECYCLE_CLOSE_SPEED = 8.0f;
    private static final float INPUT_READY_PROGRESS = 0.72f;
    private static final Set<InputKey> pendingBindKeys = new LinkedHashSet<>();
    private static final List<PickerIcon> pickerIcons = new ArrayList<>();
    public static boolean waitingForKey = false;
    public static Object keyBindOwner = null;
    private static int colorClipboard = 0xFFFFFFFF;
    private static boolean init = false;
    private static float mouseX, mouseY;
    private static boolean cursorShown = false;
    private static int fbWidth, fbHeight;
    private static String activeTabId = MODULES_TAB_ID;
    private static float tabBarX, tabBarY, tabBarW, tabBarH;
    private static float tabTargetX, tabTargetW;
    private static float tabAnimX, tabAnimW;
    private static boolean tabAnimInit = false;
    private static long lastTabAnimNs = System.nanoTime();
    private static float lifecycleAnim = 0.0f;
    private static boolean closingForExit = false;
    private static boolean finishingClose = false;
    private static long lastLifecycleAnimNs = System.nanoTime();
    private static float modulesAreaX, modulesAreaY, modulesAreaW, modulesAreaH;
    private static CompletableFuture<Void> pendingSaveTask;
    private static ClickGuiTextEditorState textEditor = null;
    private static ClickGuiPickerState picker = null;
    private static CombatProtocolHeuristicsEditorState protocolHeuristicsEditor = null;
    private static TextSetting inlineTextOwner = null;
    private static StringBuilder inlineTextBuffer = null;
    private static float inlineTextX, inlineTextY, inlineTextW, inlineTextH;
    private static boolean pickerIconScissorActive = false;
    private static float pickerIconScissorX, pickerIconScissorY, pickerIconScissorW, pickerIconScissorH;
    private static Renderer2D renderer;
    private static ClickGuiScreen mainScreen;
    private static ClickGuiPickerScreen pickerScreen;
    // A return screen is navigation state, not an alternative to the ClickGui module lifecycle.
    // The module owns enable/disable for both entry points.
    private static Screen menuReturnScreen;

    public static float mouseX() { return mouseX; }
    public static float mouseY() { return mouseY; }

    public static boolean hasMenuReturnScreen() {
        return menuReturnScreen != null && MC != null && MC.level == null;
    }

    public static boolean isGuiSessionActive() {
        return ModuleManager.isEnabled("clickgui");
    }

    public static void openFromMainMenu(Screen parent) {
        if (MC == null || parent == null || MC.level != null || ClientScreen.current() != parent) return;
        menuReturnScreen = parent;
        if (isGuiSessionActive()) {
            // An already-enabled ClickGui module must not make the title button inert.
            beginOpenAnimation();
            openClickGuiScreen();
        } else {
            ModuleManager.setEnabled("clickgui", true);
        }
        if (!(ClientScreen.current() instanceof ClickGuiScreen)) menuReturnScreen = null;
    }

    public static void closeFromScreen() {
        ModuleManager.setEnabled("clickgui", false);
    }

    //  API for MIXIN MOUSE
    public static void onMouseMove(double x, double y) {
        updateMouse(x, y, false);
        SingleLineTextInput.mouseMoved(mouseX);
        TextSetting.dragMouseSelection(mouseX);
        if (DraggableHudElementRegistry.isForceVisible() && isInputReady()) {
            DraggableHudElementRegistry.editorMouseMoved(mouseX, mouseY);
        }
        if (picker != null) {
            picker.handleMouseMove(mouseX, mouseY);
        }
        if (protocolHeuristicsEditor != null) {
            protocolHeuristicsEditor.handleMouseMove(mouseX, mouseY);
        }
        if (textEditor != null) {
            textEditor.handleMouseMove(mouseX, mouseY);
        }
        if (picker == null && protocolHeuristicsEditor == null && textEditor == null
                && isInputReady()) {
            ClickGuiSection section = getActiveSection();
            if (section != null) section.mouseMoved(mouseX, mouseY);
        }
    }

    public static void onMouseButton(double x, double y, int button, boolean pressed) {
        updateMouse(x, y, false);
        handleMouseButton(button, pressed);
    }

    public static void onMouseScroll(double x, double y, double delta) {
        updateMouse(x, y, false);
        handleMouseScroll(delta);
    }

    public static void onMouseMoveScaled(double x, double y) {
        updateMouse(x, y, true);
        SingleLineTextInput.mouseMoved(mouseX);
        TextSetting.dragMouseSelection(mouseX);
        if (DraggableHudElementRegistry.isForceVisible() && isInputReady()) {
            DraggableHudElementRegistry.editorMouseMoved(mouseX, mouseY);
        }
        if (picker != null) {
            picker.handleMouseMove(mouseX, mouseY);
        }
        if (protocolHeuristicsEditor != null) {
            protocolHeuristicsEditor.handleMouseMove(mouseX, mouseY);
        }
        if (textEditor != null) {
            textEditor.handleMouseMove(mouseX, mouseY);
        }
        if (picker == null && protocolHeuristicsEditor == null && textEditor == null
                && isInputReady()) {
            ClickGuiSection section = getActiveSection();
            if (section != null) section.mouseMoved(mouseX, mouseY);
        }
    }

    public static void onMouseButtonScaled(double x, double y, int button, boolean pressed) {
        updateMouse(x, y, true);
        handleMouseButton(button, pressed);
    }

    public static void onMouseScrollScaled(double x, double y, double delta) {
        updateMouse(x, y, true);
        handleMouseScroll(delta);
    }

    public static void onEditorMouseMoveScaled(double x, double y) {
        updateMouse(x, y, true);
    }

    public static void onEditorMouseButtonScaled(double x, double y, int button, boolean pressed) {
        updateMouse(x, y, true);
        if (!pressed && button == combatant.client.input.InputKeys.MOUSE_LEFT) {
            SingleLineTextInput.mouseReleased();
            TextSetting.endMouseSelection();
        }
        if (waitingForKey) {
            if (pressed) addPendingMouseButton(button);
            return;
        }
        if (textEditor != null) {
            if (pressed) {
                textEditor.handleMouseDown(mouseX, mouseY, button);
            } else {
                textEditor.handleMouseUp(mouseX, mouseY, button);
            }
            return;
        }
        if (isSettingsTabActive()) {
            SettingsTabRuntime.handleEditorMouseButton(mouseX, mouseY, button, pressed);
        }
    }

    public static void onEditorMouseScrollScaled(double x, double y, double delta) {
        updateMouse(x, y, true);
        if (delta != 0.0) {
            GuiSound.SCROLL.feedback();
        }
        if (textEditor != null) {
            textEditor.scroll(delta);
            return;
        }
        if (isSettingsTabActive()) {
            SettingsTabRuntime.handleEditorMouseScroll(mouseX, mouseY, delta);
        }
    }

    public static boolean onEditorCharTyped(char c) {
        return onCharTyped(c);
    }

    public static boolean onEditorKey(InputKey key, InputAction action, InputModifiers modifiers) {
        return onKey(key, action, modifiers);
    }

    public static void closeGuiEditor() {
        SettingsTabRuntime.closeGuiEditor();
    }

    private static void handleMouseButton(int button, boolean pressed) {
        if (!pressed && button == combatant.client.input.InputKeys.MOUSE_LEFT) {
            SingleLineTextInput.mouseReleased();
            TextSetting.endMouseSelection();
        }
        if (!isInputReady()) return;
        if (waitingForKey) {
            if (pressed) addPendingMouseButton(button);
            return;
        }

        if (textEditor != null) {
            if (pressed) {
                textEditor.handleMouseDown(mouseX, mouseY, button);
            } else {
                textEditor.handleMouseUp(mouseX, mouseY, button);
            }
            return;
        }

        if (protocolHeuristicsEditor != null) {
            if (pressed) {
                protocolHeuristicsEditor.handleMouseDown(mouseX, mouseY, button);
            } else {
                protocolHeuristicsEditor.handleMouseUp(mouseX, mouseY, button);
            }
            return;
        }

        if (pressed && handleTabClick(mouseX, mouseY)) {
            return;
        }

        if (picker != null) {
            if (pressed) {
                picker.handleMouseDown(mouseX, mouseY, button);
            } else {
                picker.handleMouseUp(mouseX, mouseY, button);
            }
            return;
        }

        if (!pressed) {
            // Release is routed to the HUD editor regardless of where the cursor ends.
            if (DraggableHudElementRegistry.isForceVisible()) {
                DraggableHudElementRegistry.editorMouseButton(mouseX, mouseY, button, false);
            }
            getActiveSection().mouseReleased(mouseX, mouseY, button);
            return;
        }

        ClickGuiSection section = getActiveSection();
        if (DraggableHudElementRegistry.isForceVisible()
                && section.allowsHudPreviewPointer(mouseX, mouseY)
                && DraggableHudElementRegistry.editorMouseButton(mouseX, mouseY, button, true)) {
            return;
        }
        section.mousePressed(mouseX, mouseY, button);
    }

    private static void handleMouseScroll(double delta) {
        if (!isInputReady()) return;
        if (delta != 0.0) {
            GuiSound.SCROLL.feedback();
        }

        if (textEditor != null) {
            textEditor.scroll(delta);
            return;
        }

        if (protocolHeuristicsEditor != null) {
            protocolHeuristicsEditor.scroll(delta);
            return;
        }

        if (picker != null) {
            picker.scroll(delta);
            return;
        }

        getActiveSection().mouseScrolled(mouseX, mouseY, delta);
    }

    private static void updateMouse(double x, double y, boolean fromScaledCoords) {
        if (MC == null || MC.getWindow() == null) {
            mouseX = (float) x;
            mouseY = (float) y;
            return;
        }
        if (fromScaledCoords) {
            double scaledW = MC.getWindow().getGuiScaledWidth();
            double scaledH = MC.getWindow().getGuiScaledHeight();
            double fbW = MC.getWindow().getWidth();
            double fbH = MC.getWindow().getHeight();
            double sx = scaledW > 0 ? fbW / scaledW : MC.getWindow().getGuiScale();
            double sy = scaledH > 0 ? fbH / scaledH : MC.getWindow().getGuiScale();
            double fbX = x * sx;
            double fbY = y * sy;
            float uiScale = HudScale.scale((int) fbW, (int) fbH);
            mouseX = (float) (fbX / uiScale);
            mouseY = (float) (fbY / uiScale);
            return;
        }
        double scale = MC.getWindow().getGuiScale();
        double fbW = MC.getWindow().getWidth();
        double fbH = MC.getWindow().getHeight();
        double fbX = x * scale;
        double fbY = y * scale;
        float uiScale = HudScale.scale((int) fbW, (int) fbH);
        mouseX = (float) (fbX / uiScale);
        mouseY = (float) (fbY / uiScale);
    }

    public static void focusSettingTextInput() {
        if (!isGuiSessionActive() || MC == null || MC.getWindow() == null) return;
        CombatantTextInputBridge.focus(TEXT_INPUT_TARGET,
                Math.round(mouseX), Math.round(mouseY), Math.round(mouseX + 1f), Math.round(mouseY + 16f));
    }

    //  API for MIXIN KEYBOARD
    public static boolean onCharTyped(char c) {
        if (!isGuiSessionActive()) return false;
        if (textEditor != null) {
            textEditor.insertChar(c);
            return true;
        }
        if (picker != null) {
            if (picker.isListening()) {
                picker.insertChar(c);
                return true;
            }
            return false;
        }
        if (protocolHeuristicsEditor != null) {
            return protocolHeuristicsEditor.charTyped(c);
        }
        if (inlineTextOwner != null && inlineTextBuffer != null) {
            if (StringUtil.isAllowedChatCharacter(c)) {
                inlineTextBuffer.append(c);
                return true;
            }
            return false;
        }
        return getActiveSection().charTyped(c, 0);
    }

    public static boolean onFilesDrop(List<Path> paths) {
        if (!isGuiSessionActive()) return false;
        if (textEditor != null || picker != null || protocolHeuristicsEditor != null) return false;
        return getActiveSection().onFilesDrop(paths);
    }

    public static boolean onKey(InputKey inputKey, InputAction action, InputModifiers modifiers) {
        if (!isGuiSessionActive()) return false;
        int key = inputKey.physicalKey();
        int shortcutKey = inputKey.shortcutKey();
        int modifiersMask = modifiers.mask();

        if (textEditor != null) {
            if (action.isDown()) {
                boolean shift = modifiers.shift();
                boolean shortcut = modifiers.shortcut();
                switch (key) {
                    case InputKeys.ESCAPE -> closeTextEditor(false);
                    case InputKeys.ENTER, InputKeys.KP_ENTER -> textEditor.insertNewline();
                    case InputKeys.BACKSPACE -> textEditor.backspace();
                    case InputKeys.DELETE -> textEditor.deleteForward();
                    case InputKeys.LEFT -> textEditor.moveCaret(-1, shift);
                    case InputKeys.RIGHT -> textEditor.moveCaret(1, shift);
                    case InputKeys.UP -> textEditor.moveCaretVertical(-1, shift);
                    case InputKeys.DOWN -> textEditor.moveCaretVertical(1, shift);
                    case InputKeys.HOME -> textEditor.moveCaretToStart(shift);
                    case InputKeys.END -> textEditor.moveCaretToEnd(shift);
                    default -> {
                        if (shortcutKey == 'v' && shortcut) textEditor.pasteFromClipboard();
                        if (shortcutKey == 'c' && shortcut) textEditor.copySelection();
                        if (shortcutKey == 'x' && shortcut) textEditor.cutSelection();
                        if (shortcutKey == 'a' && shortcut) textEditor.selectAll();
                    }
                }
            }
            return true;
        }

        if (protocolHeuristicsEditor != null) {
            return protocolHeuristicsEditor.keyPressed(inputKey, action, modifiers);
        }

        if (picker != null) {
            if (action.isDown()) {
                switch (key) {
                    case InputKeys.ESCAPE -> closePicker();
                    case InputKeys.ENTER, InputKeys.KP_ENTER -> picker.stopListening();
                    default -> {
                        if (shortcutKey == 'f' && modifiers.shortcut()) picker.toggleListening();
                        else picker.keyPressed(key, shortcutKey, modifiersMask);
                    }
                }
            }
            return true;
        }

        if (inlineTextOwner != null && inlineTextBuffer != null) {
            if (action.isDown()) {
                switch (key) {
                    case InputKeys.ESCAPE -> finishInlineText(false);
                    case InputKeys.ENTER, InputKeys.KP_ENTER -> finishInlineText(true);
                    case InputKeys.BACKSPACE, InputKeys.DELETE -> inlineBackspace();
                    default -> {
                        if (shortcutKey == 'v' && modifiers.shortcut()) inlinePaste();
                    }
                }
            }
            return true;
        }

        if (waitingForKey) {

            if (key == InputKeys.ESCAPE) {
                submitKeySelection((String) null);
                finishKeyBind();
                return true;
            }

            if (action == InputAction.PRESS) {
                if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER) {
                    submitKeySelection(pendingBindKeys);
                    finishKeyBind();
                    return true;
                }

                addPendingKey(inputKey);
            }

            return true;
        }

        if (action.isDown()) {
            ClickGuiSection active = getActiveSection();
            if (active == null) return false;
            if (key == InputKeys.ESCAPE && active.onCancel()) return true;
            return active.keyPressed(key, shortcutKey, modifiersMask);
        }
        return false;
    }

    //  INIT
    public static void init() {
        if (init) return;
        init = true;
        Events.BUS.register(ClickGuiInputEvents.INSTANCE);
        activeTabId = MODULES_TAB_ID;
        for (ClickGuiTabEntry tab : currentTabs()) {
            if (MODULES_TAB_ID.equals(tab.id())) {
                tab.section().onSelected();
            } else {
                tab.section().onDeselected();
            }
        }
    }

    public static void beginOpenAnimation() {
        init();
        closingForExit = false;
        finishingClose = false;
        lifecycleAnim = 0.0f;
        tabAnimInit = false;
        lastLifecycleAnimNs = System.nanoTime();

        ClickGuiSection active = getActiveSection();
        if (active != null && (MODULES_TAB_ID.equals(activeTabId) || SETTINGS_TAB_ID.equals(activeTabId))) {
            active.onDeselected();
        }
        if (active != null) active.onSelected();
    }

    public static void beginCloseAnimation() {
        if (finishingClose) return;
        init();
        closingForExit = true;
        lastLifecycleAnimNs = System.nanoTime();
        ClickGuiSearch.deactivate();
        closeTextEditor(false);
        clearInlineText();
        clearPicker();
        for (ClickGuiTabEntry tab : currentTabs()) {
            tab.section().onDeselected();
        }
        detachScreenForExitOverlay();
        releaseCursorForGameplay();
    }

    //  CURSOR OPEN/CLOSE

    private static void detachScreenForExitOverlay() {
        if (MC == null) return;
        if (ClientScreen.current() instanceof ClickGuiScreen cs) {
            mainScreen = cs;
        }
        if (hasMenuReturnScreen()) return; // Menu-origin close animation stays on its Screen.
        if (ClientScreen.current() instanceof ClickGuiScreen
                || ClientScreen.current() instanceof ClickGuiPickerScreen
                || ClientScreen.current() instanceof ClickGuiEditorScreen) {
            ClientScreen.show(MC, null);
        }
    }

    public static boolean isLifecycleActive() {
        return isGuiSessionActive() || closingForExit || lifecycleAnim > 0.001f;
    }

    public static boolean isClosingForExit() {
        return closingForExit;
    }

    /**
     * Map and the dev-only component gallery fully cover the viewport. Their own UI content
     * does not sample the live 3D target, so skip world submission, HUD, and world post passes.
     * Resolve this from the selected tab, not the existence of the dev section: switching back
     * to Modules/Settings immediately restores ordinary scene rendering.
     */
    public static boolean suppressesBackgroundRendering() {
        return MC != null
                && !hasMenuReturnScreen()
                && ClientScreen.current() instanceof ClickGuiScreen
                && (MAP_TAB_ID.equals(activeTabId) || UI_KITS_TAB_ID.equals(activeTabId))
                && !closingForExit
                && lifecycleAnim > 0.001f;
    }

    private static boolean isInputReady() {
        return isGuiSessionActive() && !closingForExit && lifecycleAnim >= INPUT_READY_PROGRESS;
    }

    public static void finishCloseImmediately() {
        closingForExit = false;
        lifecycleAnim = 0.0f;
        onClose();
        clearScreens();
        menuReturnScreen = null;
    }

    public static void onClose() {
        combatant.client.input.minecraft.CombatantTextInputBridge.clear();
        if (cursorShown && !hasMenuReturnScreen() && !ClickGui.isSuppressScreenClose()) {
            MC.mouseHandler.grabMouse();
        }
        SystemCursor.invalidate();
        cursorShown = false;
        DraggableHudElementRegistry.setForceVisible(false);
        ClickGuiSearch.deactivate();
        closeTextEditor(false);
        clearInlineText();
        clearPicker();
        finishKeyBind();

        requestConfigSave();
    }

    public static void releaseCursorForGameplay() {
        if (MC == null || MC.getWindow() == null) return;
        if (ClientScreen.current() == null) {
            try {
                MC.mouseHandler.grabMouse();
                SystemCursor.invalidate();
            } catch (Throwable ignored) {
                SystemCursor.invalidate();
            }
        } else {
            MC.mouseHandler.grabMouse();
            SystemCursor.invalidate();
        }
        cursorShown = false;
        DraggableHudElementRegistry.setForceVisible(false);
    }

    public static void ensureCursorShown() {
        if (cursorShown) return;
        if (MC == null || MC.getWindow() == null) return;
        MC.mouseHandler.releaseMouse();
        SystemCursor.invalidate();
        cursorShown = true;
    }

    public static void renderEngine(Renderer2D rendererIn, TextRenderer fallback, GuiGraphicsExtractor ctx, float tickDelta) {
        try (ProfilerPhase.Scope frameScope = ProfilerPhase.scope("2d:clickgui:render");
             RenderProfiler2D.Section ignoredFrame = RenderProfiler2D.section("render")) {
            boolean enabled = isGuiSessionActive();
            long now = System.nanoTime();
            boolean mainScreenActive = ClientScreen.current() instanceof ClickGuiScreen;
            boolean pickerScreenActive = ClientScreen.current() instanceof ClickGuiPickerScreen;

            if (!enabled && !closingForExit && lifecycleAnim <= 0.001f) {
                ClickGuiRenderer.onClose();
                return;
            }

            if (ClientScreen.current() != null && !mainScreenActive && !pickerScreenActive) {
                if (closingForExit) finishCloseImmediately();
                return;
            }
            // Screen renders ClickGUI directly; no extra gating needed here.

            init();
            if (enabled && closingForExit) {
                closingForExit = false;
                getActiveSection().onSelected();
            }
            updateLifecycleAnim(now, enabled && !closingForExit);
            renderer = rendererIn;
            pickerIcons.clear();

            SystemCursor.beginFrame(ctx);
            try {
                if (enabled && !closingForExit && !cursorShown) {
                    MC.mouseHandler.releaseMouse();
                    SystemCursor.invalidate();
                    cursorShown = true;
                }
                int fbw = MC.getWindow().getWidth();
                int fbh = MC.getWindow().getHeight();
                int vw = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
                int vh = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));
                if (!CombatantTextInputBridge.hasOwner() || CombatantTextInputBridge.owns(TEXT_INPUT_TARGET)) {
                    focusSettingTextInput();
                }
                fbWidth = vw;
                fbHeight = vh;

                try (ProfilerPhase.Scope layoutScope = ProfilerPhase.scope("2d:clickgui:layout");
                     RenderProfiler2D.Section ignoredLayout = RenderProfiler2D.section("layout");
                     TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:layout")) {
                    layout(vw, vh);
                    updateTabAnim(now);
                }

                if (pickerScreenActive) {
                    try (ProfilerPhase.Scope pickerScope = ProfilerPhase.scope("2d:clickgui:modal");
                         RenderProfiler2D.Section ignoredPicker = RenderProfiler2D.section("modal");
                         TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:modal")) {
                        if (protocolHeuristicsEditor != null) {
                            protocolHeuristicsEditor.render(vw, vh);
                        } else {
                            renderPicker(vw, vh);
                        }
                    }
                    try (ProfilerPhase.Scope textEditorScope = ProfilerPhase.scope("2d:clickgui:text_editor");
                         RenderProfiler2D.Section ignoredTextEditor = RenderProfiler2D.section("text_editor");
                         TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:text_editor")) {
                        if (!closingForExit) {
                            renderTextEditor(vw, vh);
                        }
                    }
                    return;
                }

                try (ProfilerPhase.Scope sectionScope = ProfilerPhase.scope("2d:clickgui:section");
                     RenderProfiler2D.Section ignoredSection = RenderProfiler2D.section("section");
                     TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:section")) {
                    ClickGuiSection section = getActiveSection();
                    section.render(mouseX, mouseY);
                }
                try (ProfilerPhase.Scope tabsScope = ProfilerPhase.scope("2d:clickgui:tabs");
                     RenderProfiler2D.Section ignoredTabs = RenderProfiler2D.section("tabs");
                     TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:tabs")) {
                    drawTabs();
                }
                try (ProfilerPhase.Scope textEditorScope = ProfilerPhase.scope("2d:clickgui:text_editor");
                     RenderProfiler2D.Section ignoredTextEditor = RenderProfiler2D.section("text_editor");
                     TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:text_editor")) {
                    if (!closingForExit) {
                        renderTextEditor(vw, vh);
                    }
                }

                if (closingForExit && lifecycleAnim <= 0.001f && sectionsClosed()) {
                    completeCloseAnimation();
                }
            } finally {
                SystemCursor.endFrame();
            }
        }
    }

    public static boolean shouldRenderAsTopLayer() {
        return MC != null && (ClientScreen.current() instanceof ClickGuiPickerScreen
                || ClientScreen.current() instanceof ClickGuiEditorScreen);
    }

    public static void renderScreen(GuiGraphicsExtractor ctx, float tickDelta) {
        if (!(ClientScreen.current() instanceof ClickGuiScreen)) return;

        CombatantRenderSystem.ensureFrameContext();
        ViewportContext.beginCurrentStratumUnscaledLogical(ctx);
        try {
            Renderer2D.COLOR.begin();
            if (hasMenuReturnScreen()) {
                MainMenuBackgroundRenderer.render(
                        Math.max(1, Math.round(HudScale.virtualWidth(MC.getWindow().getWidth(), MC.getWindow().getHeight()))),
                        Math.max(1, Math.round(HudScale.virtualHeight(MC.getWindow().getWidth(), MC.getWindow().getHeight()))),
                        mouseX, mouseY);
            }
            if (DraggableHudElementRegistry.isForceVisible()) {
                HudPreviewHost.render(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta,
                        Math.max(1, Math.round(HudScale.virtualWidth(MC.getWindow().getWidth(), MC.getWindow().getHeight()))),
                        Math.max(1, Math.round(HudScale.virtualHeight(MC.getWindow().getWidth(), MC.getWindow().getHeight()))));
            }
            // There is no captured world framebuffer in a title-menu session. Snapshot the
            // already drawn menu background at each ordered glass boundary instead.
            if (hasMenuReturnScreen()) {
                Renderer2D.COLOR.withLiquidGlassSceneSource(UiBackdropRequest.SceneSource.CURRENT_TARGET,
                        () -> renderEngine(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta));
            } else {
                renderEngine(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta);
            }
            if (!hasMenuReturnScreen()) DynamicIsland.renderClickGuiShell(
                    Renderer2D.COLOR,
                    TextRenderer.get(),
                    ctx,
                    tickDelta,
                    fbWidth,
                    fbHeight
            );
            Renderer2D.COLOR.render();
        } finally {
            ViewportContext.endCurrentStratum(ctx);
        }
    }

    public static void renderTopLayer(GuiGraphicsExtractor ctx, float tickDelta) {
        if (!shouldRenderAsTopLayer()) return;

        CombatantRenderSystem.ensureFrameContext();
        ViewportContext.beginUnscaledLogical(ctx);
        try (RenderPhaseScope phaseScope = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "2d:clickgui_top")) {
            SystemCursor.beginFrame(ctx);
            try {
                Renderer2D.COLOR.begin();
                if (hasMenuReturnScreen()) {
                    MainMenuBackgroundRenderer.render(
                            Math.max(1, Math.round(HudScale.virtualWidth(MC.getWindow().getWidth(), MC.getWindow().getHeight()))),
                            Math.max(1, Math.round(HudScale.virtualHeight(MC.getWindow().getWidth(), MC.getWindow().getHeight()))),
                            mouseX, mouseY);
                }
                if (DraggableHudElementRegistry.isForceVisible()) {
                    HudPreviewHost.render(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta,
                            Math.max(1, Math.round(HudScale.virtualWidth(MC.getWindow().getWidth(), MC.getWindow().getHeight()))),
                            Math.max(1, Math.round(HudScale.virtualHeight(MC.getWindow().getWidth(), MC.getWindow().getHeight()))));
                }
                Runnable renderUi = () -> {
                    if (ClientScreen.current() instanceof ClickGuiEditorScreen) {
                        renderEditor(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta);
                    } else {
                        renderEngine(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta);
                    }
                };
                if (hasMenuReturnScreen()) {
                    Renderer2D.COLOR.withLiquidGlassSceneSource(UiBackdropRequest.SceneSource.CURRENT_TARGET, renderUi);
                } else {
                    renderUi.run();
                }
                Renderer2D.COLOR.render();
                if (ClientScreen.current() instanceof ClickGuiPickerScreen) {
                    processPickerIcons(ctx);
                }
            } finally {
                SystemCursor.endFrame();
            }
        }
        ViewportContext.end(ctx);
    }

    public static void renderEditor(Renderer2D rendererIn, TextRenderer fallback, GuiGraphicsExtractor ctx, float tickDelta) {
        if (MC == null || MC.getWindow() == null) return;
        init();
        renderer = rendererIn;
        int fbw = MC.getWindow().getWidth();
        int fbh = MC.getWindow().getHeight();
        int vw = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
        int vh = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));
        fbWidth = vw;
        fbHeight = vh;
        SystemCursor.beginFrame(ctx);
        try {
            SettingsTabRuntime.renderEditor(rendererIn, fallback, ctx, tickDelta, vw, vh, mouseX, mouseY);
        } finally {
            SystemCursor.endFrame();
        }
    }

    public static void renderModulesPreview(float x, float y, float w, float h) {
        if (renderer == null) return;
        init();
        ClickGuiSection modulesSection = sectionById(MODULES_TAB_ID);
        if (modulesSection == null) return;
        float pad = 8f;
        float areaX = x + pad;
        float areaY = y + pad;
        float areaW = Math.max(1f, w - pad * 2f);
        float areaH = Math.max(1f, h - pad * 2f);
        modulesSection.layout(areaX, areaY, areaW, areaH);
        float mx = -10_000f;
        float my = -10_000f;
        modulesSection.render(mx, my);
    }

    private static void layout(int fbw, int fbh) {
        List<ClickGuiTabEntry> tabs = currentTabs();
        ensureActiveTab(tabs);
        float contentMarginX = 28f;
        float contentTop = 52f;
        float contentBottom = 18f;
        modulesAreaX = contentMarginX;
        modulesAreaY = contentTop;
        modulesAreaW = Math.max(1f, fbw - contentMarginX * 2f);
        modulesAreaH = Math.max(1f, fbh - contentTop - contentBottom);
        for (ClickGuiTabEntry tab : tabs) {
            if (tab.section().usesFullViewport()) {
                tab.section().layout(0.0f, 0.0f, fbw, fbh);
            } else {
                tab.section().layout(modulesAreaX, modulesAreaY, modulesAreaW, modulesAreaH);
            }
        }

        tabBarH = TAB_HEIGHT;
        tabBarY = 10f;
        ensureTabCapacity(tabs.size());
        float totalTabs = 0f;
        for (int t = 0; t < tabs.size(); t++) {
            float wTab = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_BOLD.renderer(), tabs.get(t).label(), TAB_FONT) + TAB_PAD_X * 2f;
            if (wTab < TAB_MIN_W) wTab = TAB_MIN_W;
            tabW[t] = wTab;
            totalTabs += wTab;
        }
        if (!tabs.isEmpty()) {
            totalTabs += TAB_GAP * (tabs.size() - 1);
        }
        tabBarW = totalTabs;
        tabBarX = (fbw - totalTabs) * 0.5f;
        float cx = tabBarX;
        for (int t = 0; t < tabs.size(); t++) {
            tabX[t] = cx;
            cx += tabW[t] + TAB_GAP;
        }

        int activeIdx = 0;
        for (int t = 0; t < tabs.size(); t++) {
            if (tabs.get(t).id().equals(activeTabId)) {
                activeIdx = t;
                break;
            }
        }
        tabTargetX = tabX[activeIdx];
        tabTargetW = tabW[activeIdx];
        if (!tabAnimInit) {
            tabAnimX = tabTargetX;
            tabAnimW = tabTargetW;
            tabAnimInit = true;
            lastTabAnimNs = System.nanoTime();
        }
    }

    private static void updateTabAnim(long now) {
        float dt = (now - lastTabAnimNs) / 1_000_000_000f;
        lastTabAnimNs = now;
        if (dt < 0f) dt = 0f;
        float speed = 16f;
        float step = Math.min(1f, dt * speed);
        tabAnimX += (tabTargetX - tabAnimX) * step;
        tabAnimW += (tabTargetW - tabAnimW) * step;
    }

    private static void updateLifecycleAnim(long now, boolean opening) {
        float dt = (now - lastLifecycleAnimNs) / 1_000_000_000f;
        lastLifecycleAnimNs = now;
        if (dt < 0f) dt = 0f;
        float target = opening ? 1.0f : 0.0f;
        float speed = opening ? LIFECYCLE_OPEN_SPEED : LIFECYCLE_CLOSE_SPEED;
        lifecycleAnim = AnimationUtility.approach(lifecycleAnim, target, dt, speed);
        lifecycleAnim = AnimationUtility.snap(lifecycleAnim, target, 0.002f);
    }

    private static boolean sectionsClosed() {
        return !getActiveSection().isVisible();
    }

    private static void completeCloseAnimation() {
        if (finishingClose) return;
        finishingClose = true;
        closingForExit = false;
        onClose();
        if (MC != null && (ClientScreen.current() instanceof ClickGuiScreen
                || ClientScreen.current() instanceof ClickGuiPickerScreen
                || ClientScreen.current() instanceof ClickGuiEditorScreen)) {
            ClientScreen.show(MC, hasMenuReturnScreen() ? menuReturnScreen : null);
        }
        menuReturnScreen = null;
        clearScreens();
        finishingClose = false;
    }

    private static final float TAB_BAR_SLIDE_DISTANCE = 18.0f;
    private static final float TAB_BAR_INNER_PAD = 4.0f;
    private static final float TAB_BAR_SURFACE_ALPHA = 185.0f / 255.0f;
    private static final float TAB_BAR_SHADOW_BLUR = 10.0f;
    private static final float TAB_BAR_SHADOW_SPREAD = 1.5f;
    private static final float TAB_BAR_SHADOW_ALPHA = 0.23f;

    private static void drawTabs() {
        float eased = easeOutCubic(lifecycleAnim);
        if (eased <= 0.001f) return;
        List<ClickGuiTabEntry> tabs = currentTabs();
        ensureActiveTab(tabs);
        if (tabs.isEmpty()) return;
        boolean islandShell = !hasMenuReturnScreen() && DynamicIsland.shouldOwnClickGuiTabShell();
        if (islandShell) return;

        TextRenderer tabFont = BuiltinFontCatalog.ONEST_BOLD.renderer();
        float y = tabBarY - (1.0f - eased) * TAB_BAR_SLIDE_DISTANCE;
        float outerRadius = tabBarH * 0.5f;
        float outerPad = TAB_BAR_INNER_PAD;
        float innerX = tabAnimX + outerPad;
        float innerY = y + outerPad;
        float innerW = Math.max(1.0f, tabAnimW - outerPad * 2.0f);
        float innerH = Math.max(1.0f, tabBarH - outerPad * 2.0f);
        float innerRadius = innerH * 0.5f;

        float outerSurfaceAlpha = TAB_BAR_SURFACE_ALPHA;
        int outerLeft = LayoutRender2D.scaleAlpha(LayoutRender2D.mixColor(theme().surface(), theme().windowBg(), 0.34f), outerSurfaceAlpha * eased);
        int outerRight = LayoutRender2D.scaleAlpha(LayoutRender2D.mixColor(theme().surfaceHover(), theme().windowBg(), 0.22f), outerSurfaceAlpha * eased);
        int outerStrokeLeft = LayoutRender2D.scaleAlpha(LayoutRender2D.mixColor(theme().strokeSoft(), theme().accentSoft(), 0.18f), 0.50f * eased);
        int outerStrokeRight = LayoutRender2D.scaleAlpha(LayoutRender2D.mixColor(theme().strokeSoft(), theme().textPrimary(), 0.08f), 0.42f * eased);

        int activeLeft = LayoutRender2D.scaleAlpha(LayoutRender2D.mixColor(theme().accentSoft(), theme().accent(), 0.18f), eased);
        int activeRight = LayoutRender2D.scaleAlpha(LayoutRender2D.mixColor(theme().accent(), theme().textPrimary(), 0.08f), eased);
        int activeStrokeLeft = LayoutRender2D.scaleAlpha(LayoutRender2D.mixColor(theme().accentSoft(), theme().textPrimary(), 0.20f), 0.88f * eased);
        int activeStrokeRight = LayoutRender2D.scaleAlpha(theme().accent(), 0.92f * eased);

        try (RenderWarpStack.Scope ignored = Renderer2D.pushPerspectiveWarp(
                tabBarX,
                y,
                tabBarW,
                tabBarH,
                0.0f,
                -5.0f * (1.0f - eased),
                0.0f,
                3.2f,
                0.80f,
                0.965f + 0.035f * eased
        )) {
            if (!islandShell) {
                // Match the outer shell exactly and drive the ClickGUI blur at full strength.
                // LayoutRender2D.drawBlur() multiplies by the ClickGUI density scale (1.16) and clamps to 1, so
                // eased == 1.0 resolves to a true 255/255 blur alpha.
                LayoutRender2D.flushRenderer();
                LayoutRender2D.drawBlur(
                        tabBarX, y, tabBarW, tabBarH, outerRadius,
                        0xFFFFFFFF,
                        (1.0f) * eased
                );
                LayoutRender2D.flushRenderer();
                LayoutRender2D.drawRoundedRectShadow(
                        tabBarX,
                        y,
                        tabBarW,
                        tabBarH,
                        outerRadius,
                        TAB_BAR_SHADOW_BLUR,
                        TAB_BAR_SHADOW_SPREAD,
                        LayoutRender2D.scaleAlpha(0xCC000000, TAB_BAR_SHADOW_ALPHA * eased)
                );
                LayoutRender2D.drawRoundedRectGradient(tabBarX, y, tabBarW, tabBarH, outerRadius, outerLeft, outerRight, 0.0f);
                LayoutRender2D.drawRoundedRectStrokeGradient(tabBarX, y, tabBarW, tabBarH, outerRadius, 1.0f, outerStrokeLeft, outerStrokeRight, 0.0f);
            }

            LayoutRender2D.drawRoundedRectGlow(
                    innerX,
                    innerY,
                    innerW,
                    innerH,
                    innerRadius,
                    10.0f,
                    LayoutRender2D.scaleAlpha(theme().accentSoft(), 0.30f * eased)
            );
            LayoutRender2D.drawRoundedRectGradient(innerX, innerY, innerW, innerH, innerRadius, activeLeft, activeRight, 0.0f);
            LayoutRender2D.drawRoundedRectStrokeGradient(innerX, innerY, innerW, innerH, innerRadius, 1.0f, activeStrokeLeft, activeStrokeRight, 0.0f);

            for (int i = 0; i < tabs.size(); i++) {
                float x = tabX[i];
                float w = tabW[i];
                ClickGuiTabEntry tab = tabs.get(i);
                boolean active = tab.id().equals(activeTabId);
                boolean hovered = !closingForExit && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + tabBarH;
                if (i > 0) {
                    float sepH = tabBarH - outerPad * 3.4f;
                    float sepY = y + (tabBarH - sepH) * 0.5f;
                    LayoutRender2D.drawRect(x, sepY, 1.0f, sepH, LayoutRender2D.scaleAlpha(theme().strokeSoft(), 0.32f * eased));
                }
                int textCol = active ? theme().textPrimary() : theme().textMuted();
                if (!active && hovered) {
                    textCol = LayoutRender2D.mixColor(textCol, theme().textPrimary(), 0.55f);
                }
                textCol = LayoutRender2D.scaleAlpha(textCol, eased);
                float textH = LayoutRender2D.textHeight(tabFont, TAB_FONT);
                float textY = y + (tabBarH - textH) * 0.5f - 0.35f;
                float labelW = LayoutRender2D.textWidth(tabFont, tab.label(), TAB_FONT);
                float textX = x + (w - labelW) * 0.5f;
                LayoutRender2D.drawText(tabFont, tab.label(), textX, textY, TAB_FONT, textCol, false);
            }
        }
    }

    private static boolean handleTabClick(float mx, float my) {
        if (!isInputReady()) return false;
        if (mx < tabBarX || mx > tabBarX + tabBarW || my < tabBarY || my > tabBarY + tabBarH) return false;
        List<ClickGuiTabEntry> tabs = currentTabs();
        ensureActiveTab(tabs);
        for (int i = 0; i < tabs.size(); i++) {
            float x = tabX[i];
            float w = tabW[i];
            if (mx >= x && mx <= x + w && my >= tabBarY && my <= tabBarY + tabBarH) {
                ClickGuiTabEntry tab = tabs.get(i);
                if (!activeTabId.equals(tab.id())) {
                    ClickGuiSection previous = getActiveSection();
                    activeTabId = tab.id();
                    previous.onDeselected();
                    ClickGuiSearch.deactivate();
                    clearInlineText();
                    closeTextEditor(false);
                    getActiveSection().onSelected();
                }
                return true;
            }
        }
        return false;
    }

    /** Stable section identifier used by input policies; never infer it from screen classes. */
    public static String activeSectionId() { return activeTabId; }

    public static boolean activeSectionBlocksMovement() {
        ClickGuiSection section = getActiveSection();
        return section != null && section.blocksMovementInput();
    }

    private static ClickGuiSection getActiveSection() {
        List<ClickGuiTabEntry> tabs = currentTabs();
        ensureActiveTab(tabs);
        for (ClickGuiTabEntry tab : tabs) {
            if (tab.id().equals(activeTabId)) {
                return tab.section();
            }
        }
        return tabs.isEmpty() ? null : tabs.get(0).section();
    }

    private static boolean isSettingsTabActive() {
        return SETTINGS_TAB_ID.equals(activeTabId);
    }

    private static List<ClickGuiTabEntry> currentTabs() {
        List<ClickGuiSectionManager.Entry> sections = ClickGuiSectionManager.sections();
        ArrayList<ClickGuiTabEntry> out = new ArrayList<>(sections.size());
        for (ClickGuiSectionManager.Entry entry : sections) {
            out.add(new ClickGuiTabEntry(entry.id(), entry.label(), entry.section()));
        }
        return out;
    }

    private static ClickGuiSection sectionById(String id) {
        for (ClickGuiTabEntry tab : currentTabs()) {
            if (tab.id().equals(id)) {
                return tab.section();
            }
        }
        return null;
    }

    private static void ensureActiveTab(List<ClickGuiTabEntry> tabs) {
        for (ClickGuiTabEntry tab : tabs) {
            if (tab.id().equals(activeTabId)) {
                return;
            }
        }
        activeTabId = MODULES_TAB_ID;
    }

    public static ClickGuiIslandState islandState() {
        List<ClickGuiTabEntry> tabs = currentTabs();
        ensureActiveTab(tabs);
        String activeLabel = tabs.isEmpty() ? "" : tabs.get(0).label();
        for (ClickGuiTabEntry tab : tabs) {
            if (tab.id().equals(activeTabId)) {
                activeLabel = tab.label();
                break;
            }
        }
        boolean geometryReady = tabX.length >= tabs.size() && tabW.length >= tabs.size();
        if (!geometryReady) {
            return new ClickGuiIslandState(
                    tabBarX, tabBarY, tabBarW, tabBarH,
                    tabAnimX, tabAnimW, lifecycleAnim,
                    activeLabel, 0,
                    picker != null || protocolHeuristicsEditor != null,
                    List.of()
            );
        }
        ArrayList<ClickGuiIslandTab> islandTabs = new ArrayList<>(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            ClickGuiTabEntry tab = tabs.get(i);
            boolean active = tab.id().equals(activeTabId);
            boolean hovered = !closingForExit
                    && mouseX >= tabX[i] && mouseX <= tabX[i] + tabW[i]
                    && mouseY >= tabBarY && mouseY <= tabBarY + tabBarH;
            islandTabs.add(new ClickGuiIslandTab(
                    tab.label(),
                    tabX[i] - tabBarX,
                    tabW[i],
                    active,
                    hovered
            ));
        }
        return new ClickGuiIslandState(
                tabBarX,
                tabBarY,
                tabBarW,
                tabBarH,
                tabAnimX,
                tabAnimW,
                lifecycleAnim,
                activeLabel,
                tabs.size(),
                picker != null || protocolHeuristicsEditor != null,
                List.copyOf(islandTabs)
        );
    }

    private static void ensureTabCapacity(int size) {
        if (tabX.length >= size && tabW.length >= size) return;
        tabX = Arrays.copyOf(tabX, size);
        tabW = Arrays.copyOf(tabW, size);
    }

    private static float easeOutCubic(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    public static Renderer2D currentRenderer() {
        return renderer;
    }

    public static int framebufferWidth() {
        return fbWidth;
    }

    public static int framebufferHeight() {
        return fbHeight;
    }

    public static float getAnimAlpha() {
        return 1f;
    }

    public static float getMouseX() {
        return mouseX;
    }

    public static float getMouseY() {
        return mouseY;
    }

    public static int getColorClipboard() {
        return colorClipboard;
    }

    public static void setColorClipboard(int argb) {
        colorClipboard = argb;
    }

    public static void beginKeyBind(Object owner) {
        clearInlineText();
        waitingForKey = true;
        keyBindOwner = owner;
        pendingBindKeys.clear();
        ClickGuiSearch.deactivate();
        GuiSound.BINDING_START.feedback();
    }

    public static void finishKeyBind() {
        waitingForKey = false;
        keyBindOwner = null;
        pendingBindKeys.clear();
    }

    private static void addPendingKey(InputKey key) {
        if (key == null || key.physicalKey() == InputKeys.UNKNOWN) return;
        pendingBindKeys.add(key);
    }

    private static void addPendingMouseButton(int button) {
        if (button < InputKeys.MOUSE_FIRST || button > InputKeys.MOUSE_LAST) return;
        pendingBindKeys.add(InputKeyCatalog.mouseEvent(button));
    }

    private static void submitKeySelection(Set<InputKey> keys) {
        if (keys == null || keys.isEmpty()) {
            submitKeySelection((String) null);
            return;
        }
        submitKeySelection(InputKeyCatalog.serializeChord(keys));
    }

    private static void submitKeySelection(String combo) {
        if (keyBindOwner instanceof KeyBindSetting ks) {
            ks.onKeySelected(combo);
        } else if (keyBindOwner instanceof FunctionBindSetting fs) {
            fs.onKeySelected(combo);
        }
    }

    public static String getPendingBindDisplay() {
        if (!waitingForKey) return null;
        return pendingBindKeys.isEmpty() ? "..." : InputKeyCatalog.serializeChord(pendingBindKeys);
    }

    public static void openTextEditor(TextEditorOwner owner, String title, String seedText, float anchorX, float anchorY, float anchorW) {
        if (owner == null) return;
        clearInlineText();
        if (seedText == null) seedText = "";
        ClickGuiSearch.deactivate();
        textEditor = new ClickGuiTextEditorState(owner, title, seedText, anchorX, anchorY, anchorW);
    }

    public static void closeTextEditor(boolean save) {
        if (textEditor == null) return;
        if (save) textEditor.save();
        textEditor = null;
    }

    public static void renderTextEditor(int fbw, int fbh) {
        if (textEditor == null) return;
        textEditor.render(fbw, fbh);
    }

    private static void renderPicker(int fbw, int fbh) {
        if (picker == null) return;
        picker.render(fbw, fbh);
    }

    public static void toggleInlineText(TextSetting owner) {
        if (owner == null) return;
        if (inlineTextOwner == owner) {
            clearInlineText();
            return;
        }
        inlineTextOwner = owner;
        inlineTextBuffer = new StringBuilder(owner.getEditorText());
        ClickGuiSearch.deactivate();
    }

    public static boolean isInlineTextActive() {
        return inlineTextOwner != null;
    }

    public static boolean isInlineTextOwner(TextSetting owner) {
        return inlineTextOwner == owner;
    }

    public static String getInlineText(TextSetting owner) {
        if (owner == null) return "";
        if (inlineTextOwner == owner && inlineTextBuffer != null) {
            return inlineTextBuffer.toString();
        }
        return owner.getEditorText();
    }

    public static void updateInlineTextBounds(TextSetting owner, float x, float y, float w, float h) {
        if (inlineTextOwner != owner) return;
        inlineTextX = x;
        inlineTextY = y;
        inlineTextW = w;
        inlineTextH = h;
    }

    private static void finishInlineText(boolean save) {
        if (inlineTextOwner == null || inlineTextBuffer == null) {
            clearInlineText();
            return;
        }
        if (save) {
            inlineTextOwner.applyEditorText(inlineTextBuffer.toString());
        }
        clearInlineText();
    }

    private static void clearInlineText() {
        inlineTextOwner = null;
        inlineTextBuffer = null;
        inlineTextX = inlineTextY = inlineTextW = inlineTextH = 0f;
    }

    private static void inlineBackspace() {
        if (inlineTextBuffer == null) return;
        int len = inlineTextBuffer.length();
        if (len <= 0) return;
        inlineTextBuffer.deleteCharAt(len - 1);
    }

    private static String normalizeInlineText(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.replace('\n', ' ');
    }

    private static void inlinePaste() {
        if (inlineTextBuffer == null) return;
        String clip = ClipboardUtil.get();
        if (clip == null || clip.isEmpty()) return;
        inlineTextBuffer.append(normalizeInlineText(clip));
    }

    public static boolean isTextEditorActive() {
        return textEditor != null;
    }

    public static boolean isPickerActive() {
        return picker != null || protocolHeuristicsEditor != null;
    }

    public static boolean isBlockingModuleKeybinds() {
        if (!isGuiSessionActive()) return false;
        if (waitingForKey) return true;
        if (textEditor != null) return true;
        if (inlineTextOwner != null) return true;
        if (protocolHeuristicsEditor != null) return true;
        if (picker != null && picker.isListening()) return true;
        return ClickGuiSearch.isActive();
    }

    public static void processPickerIcons(GuiGraphicsExtractor ctx) {
        if (pickerIcons.isEmpty()) {
            pickerIconScissorActive = false;
            return;
        }
        if (ctx == null) {
            pickerIcons.clear();
            pickerIconScissorActive = false;
            return;
        }
        if (!(ctx instanceof IGuiGraphics accessor)) {
            pickerIcons.clear();
            pickerIconScissorActive = false;
            return;
        }
        Minecraft mc = MC;
        if (mc == null) {
            pickerIcons.clear();
            pickerIconScissorActive = false;
            return;
        }
        int seed = 0;
        for (PickerIcon icon : pickerIcons) {
            ItemStack stack = icon.stack();
            if (stack == null || stack.isEmpty()) continue;
            boolean clippedGlobal = false;
            boolean clippedLocal = false;
            if (icon.hasGlobalClip()) {
                clippedGlobal = ScissorFunction.pushRaw(
                        icon.globalClipX(),
                        icon.globalClipY(),
                        icon.globalClipW(),
                        icon.globalClipH()
                );
            }
            if (icon.hasClip()) {
                clippedLocal = ScissorFunction.pushRaw(
                        icon.clipX(),
                        icon.clipY(),
                        icon.clipW(),
                        icon.clipH()
                );
            }
            Renderer2D.COLOR.begin();
            // Picker screen uses beginUnscaledLogical(), so queued icon coordinates are already logical.
            Renderer2D.COLOR.item(
                    stack,
                    icon.x(),
                    icon.y(),
                    icon.scale(),
                    seed++,
                    Renderer2D.ITEM_OVERLAY_ALL,
                    null
            );
            Renderer2D.COLOR.render();
            if (clippedLocal) ScissorFunction.pop();
            if (clippedGlobal) ScissorFunction.pop();
        }
        pickerIcons.clear();
        pickerIconScissorActive = false;
    }

    public static void beginPickerIconScissor(float x, float y, float w, float h) {
        pickerIconScissorActive = true;
        pickerIconScissorX = x;
        pickerIconScissorY = y;
        pickerIconScissorW = w;
        pickerIconScissorH = h;
    }

    public static void endPickerIconScissor() {
        pickerIconScissorActive = false;
    }

    public static void queuePickerIcon(ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty()) return;
        pickerIcons.add(new PickerIcon(
                stack,
                x,
                y,
                scale,
                currentPickerIconScissorActive(),
                pickerIconScissorX,
                pickerIconScissorY,
                pickerIconScissorW,
                pickerIconScissorH,
                false,
                0f,
                0f,
                0f,
                0f
        ));
    }

    public static void queuePickerIcon(ItemStack stack,
                                       float x,
                                       float y,
                                       float scale,
                                       float clipX,
                                       float clipY,
                                       float clipW,
                                       float clipH) {
        if (stack == null || stack.isEmpty()) return;
        boolean hasClip = clipW > 0f && clipH > 0f;
        pickerIcons.add(new PickerIcon(
                stack,
                x,
                y,
                scale,
                currentPickerIconScissorActive(),
                pickerIconScissorX,
                pickerIconScissorY,
                pickerIconScissorW,
                pickerIconScissorH,
                hasClip,
                clipX,
                clipY,
                clipW,
                clipH
        ));
    }

    private static boolean currentPickerIconScissorActive() {
        return pickerIconScissorActive && pickerIconScissorW > 0f && pickerIconScissorH > 0f;
    }

    /**
     * Materialize cheap ClickGUI screen wrappers and bind already-prewarmed font renderers before
     * the first user-triggered open. Section discovery itself is owned by ClickGuiSectionManager.
     */
    public static void prewarmUiObjects() {
        if (mainScreen == null) mainScreen = new ClickGuiScreen();
        if (pickerScreen == null) pickerScreen = new ClickGuiPickerScreen();
        BuiltinFontCatalog.ONEST_REGULAR.renderer();
        BuiltinFontCatalog.ONEST_MEDIUM.renderer();
        BuiltinFontCatalog.ONEST_MEDIUM.renderer();
        BuiltinFontCatalog.ONEST_BOLD.renderer();
        BuiltinFontCatalog.MONSTERRAT.renderer();
    }
    public static void openClickGuiScreen() {
        if (MC == null) return;
        Screen current = ClientScreen.current();
        if (current != null && current != menuReturnScreen) return;
        mainScreen = new ClickGuiScreen(menuReturnScreen);
        GuiSound.OPEN.feedback();
        ClientScreen.show(MC, mainScreen);
    }

    public static void captureMainScreen() {
        if (MC != null && ClientScreen.current() instanceof ClickGuiScreen cs) {
            mainScreen = cs;
        }
    }

    public static ClickGuiScreen ensureMainScreen() {
        if (mainScreen == null) {
            mainScreen = new ClickGuiScreen();
        }
        return mainScreen;
    }

    private static void openPickerScreen() {
        if (MC == null) return;
        if (ClientScreen.current() instanceof ClickGuiScreen cs) {
            mainScreen = cs;
        }
        if (pickerScreen == null) {
            pickerScreen = new ClickGuiPickerScreen();
        }
        ClientScreen.show(MC, pickerScreen);
    }

    public static void clearScreens() {
        mainScreen = null;
        pickerScreen = null;
        SettingsTabRuntime.clearScreens();
    }

    public static void shutdownForRuntime() {
        finishCloseImmediately();
        clearInlineText();
        clearPicker();
        textEditor = null;
        pickerIcons.clear();
        pendingBindKeys.clear();
        waitingForKey = false;
        keyBindOwner = null;
        renderer = null;
        init = false;
    }

    public static void openPicker(TextListSetting owner, String title, TextListSetting.PickerMode mode) {
        if (owner == null) return;
        clearInlineText();
        ClickGuiSearch.deactivate();
        protocolHeuristicsEditor = null;
        picker = new ClickGuiPickerState(owner, title, mode);
        openPickerScreen();
    }

    public static void openProtocolHeuristicsEditor(ProtocolHeuristicsSetting owner, String title) {
        if (owner == null) return;
        clearInlineText();
        ClickGuiSearch.deactivate();
        picker = null;
        protocolHeuristicsEditor = new CombatProtocolHeuristicsEditorState(owner, title);
        openPickerScreen();
    }

    public static void closeProtocolHeuristicsEditor() {
        closePicker();
    }

    public static void closePickerScreen() {
        closePicker();
    }

    private static void closePicker() {
        picker = null;
        protocolHeuristicsEditor = null;
        textEditor = null;
        if (MC != null && ClientScreen.current() instanceof ClickGuiPickerScreen) {
            if (mainScreen == null) {
                mainScreen = new ClickGuiScreen();
            }
            ClientScreen.show(MC, mainScreen);
        }
    }

    private static void clearPicker() {
        picker = null;
        protocolHeuristicsEditor = null;
    }

    private static synchronized void requestConfigSave() {
        Runnable task = () -> {
            try {
                ConfigSerializer.saveAll();
            } catch (Throwable ignored) {
            }
        };

        if (pendingSaveTask == null || pendingSaveTask.isDone()) {
            pendingSaveTask = CompletableFuture.runAsync(task);
        } else {
            pendingSaveTask = pendingSaveTask.thenRunAsync(task);
        }
    }

    private record ClickGuiTabEntry(String id, String label, ClickGuiSection section) {
    }

    public record ClickGuiIslandState(float tabBarX,
                                      float tabBarY,
                                      float tabBarW,
                                      float tabBarH,
                                      float activeX,
                                      float activeW,
                                      float lifecycle,
                                      String activeLabel,
                                      int tabCount,
                                      boolean pickerActive,
                                      List<ClickGuiIslandTab> tabs) {
    }

    public record ClickGuiIslandTab(String label,
                                    float relativeX,
                                    float width,
                                    boolean active,
                                    boolean hovered) {
    }

    private record PickerIcon(ItemStack stack,
                              float x,
                              float y,
                              float scale,
                              boolean hasGlobalClip,
                              float globalClipX,
                              float globalClipY,
                              float globalClipW,
                              float globalClipH,
                              boolean hasClip,
                              float clipX,
                              float clipY,
                              float clipW,
                              float clipH) {
    }

}
