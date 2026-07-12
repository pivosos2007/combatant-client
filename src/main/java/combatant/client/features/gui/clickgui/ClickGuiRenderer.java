/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui;

import combatant.client.addon.ClickGuiSectionManager;
import combatant.client.features.gui.clickgui.settings.*;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import combatant.client.config.ConfigSerializer;
import combatant.client.features.gui.clickgui.editor.ClickGuiTextEditorState;
import combatant.client.features.gui.clickgui.picker.ClickGuiPickerState;
import combatant.client.features.gui.clickgui.sections.ClickGuiSection;
import combatant.client.features.gui.clickgui.sections.ModulesSection;
import combatant.client.features.gui.clickgui.sections.SettingsSection;
import combatant.client.features.gui.clickgui.sections.settings.SettingsTabRuntime;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.impl.DynamicIsland;
import combatant.client.features.module.ModuleManager;
import combatant.client.mixininterface.IGuiGraphics;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.profiler.RenderProfiler2D;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.input.KeyUtil;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.wav.ClickGuiSounds;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static combatant.client.features.theme.Theme.theme;

public enum ClickGuiRenderer {
    ;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ModulesSection MODULES_SECTION = new ModulesSection();
    private static final SettingsSection SETTINGS_SECTION = new SettingsSection();
    private static final String MODULES_TAB_ID = "combatant:modules";
    private static final String SETTINGS_TAB_ID = "combatant:settings";
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
    private static final Set<Integer> pendingBindKeys = new LinkedHashSet<>();
    private static final List<PickerIcon> pickerIcons = new ArrayList<>();
    private static final java.util.ArrayList<TextBatchItem> TEXT_BATCH = new java.util.ArrayList<>();
    private static final int TEXT_WIDTH_CACHE_LIMIT = 4096;
    private static final int TEXT_HEIGHT_CACHE_LIMIT = 256;
    private static final Map<TextMeasureKey, Float> TEXT_WIDTH_CACHE = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextMeasureKey, Float> eldest) {
            return size() > TEXT_WIDTH_CACHE_LIMIT;
        }
    };
    private static final Map<TextHeightKey, Float> TEXT_HEIGHT_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextHeightKey, Float> eldest) {
            return size() > TEXT_HEIGHT_CACHE_LIMIT;
        }
    };
    public static boolean waitingForKey = false;
    public static Object keyBindOwner = null;
    private static int colorClipboard = 0xFFFFFFFF;
    private static boolean init = false;
    private static float mouseX, mouseY;
    private static boolean cursorShown = false;
    private static int fbWidth, fbHeight;
    private static TextRenderer interRegular;
    private static TextRenderer interMedium;
    private static TextRenderer onestMedium;
    private static TextRenderer onestBold;
    private static TextRenderer monsterratRegular;
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
    private static TextSetting inlineTextOwner = null;
    private static StringBuilder inlineTextBuffer = null;
    private static float inlineTextX, inlineTextY, inlineTextW, inlineTextH;
    private static boolean pickerIconScissorActive = false;
    private static float pickerIconScissorX, pickerIconScissorY, pickerIconScissorW, pickerIconScissorH;
    private static Renderer2D renderer;
    private static float renderAlphaMultiplier = 1.0f;
    private static ClickGuiScreen mainScreen;
    private static ClickGuiPickerScreen pickerScreen;
    private static boolean textBatchActive;

    // =========================
    //  API for MIXIN MOUSE
    // =========================
    public static void onMouseMove(double x, double y) {
        updateMouse(x, y, false);
        if (picker != null) {
            picker.handleMouseMove(mouseX, mouseY);
        }
        if (textEditor != null) {
            textEditor.handleMouseMove(mouseX, mouseY);
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
        if (picker != null) {
            picker.handleMouseMove(mouseX, mouseY);
        }
        if (textEditor != null) {
            textEditor.handleMouseMove(mouseX, mouseY);
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
            ClickGuiSounds.scroll();
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

    public static boolean onEditorKey(int key, int scancode, int action, int mods) {
        return onKey(key, scancode, action, mods);
    }

    public static void closeGuiEditor() {
        SettingsTabRuntime.closeGuiEditor();
    }

    private static void handleMouseButton(int button, boolean pressed) {
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
            getActiveSection().mouseReleased(mouseX, mouseY, button);
            return;
        }

        getActiveSection().mousePressed(mouseX, mouseY, button);
    }

    private static void handleMouseScroll(double delta) {
        if (!isInputReady()) return;
        if (delta != 0.0) {
            ClickGuiSounds.scroll();
        }

        if (textEditor != null) {
            textEditor.scroll(delta);
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

    // =========================
    //  API for MIXIN KEYBOARD
    // =========================
    public static boolean onCharTyped(char c) {
        if (!ModuleManager.isEnabled("clickgui")) return false;
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
        if (inlineTextOwner != null && inlineTextBuffer != null) {
            if (StringUtil.isAllowedChatCharacter(c)) {
                inlineTextBuffer.append(c);
                return true;
            }
            return false;
        }
        return getActiveSection().charTyped(c, 0);
    }

    public static boolean onKey(int key, int scancode, int action, int mods) {
        if (!ModuleManager.isEnabled("clickgui")) return false;

        if (textEditor != null) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
                boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
                switch (key) {
                    case GLFW.GLFW_KEY_ESCAPE -> closeTextEditor(false);
                    case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> textEditor.insertNewline();
                    case GLFW.GLFW_KEY_BACKSPACE -> textEditor.backspace();
                    case GLFW.GLFW_KEY_DELETE -> textEditor.deleteForward();
                    case GLFW.GLFW_KEY_LEFT -> textEditor.moveCaret(-1, shift);
                    case GLFW.GLFW_KEY_RIGHT -> textEditor.moveCaret(1, shift);
                    case GLFW.GLFW_KEY_UP -> textEditor.moveCaretVertical(-1, shift);
                    case GLFW.GLFW_KEY_DOWN -> textEditor.moveCaretVertical(1, shift);
                    case GLFW.GLFW_KEY_HOME -> textEditor.moveCaretToStart(shift);
                    case GLFW.GLFW_KEY_END -> textEditor.moveCaretToEnd(shift);
                    default -> {
                        if (key == GLFW.GLFW_KEY_V && ctrl) textEditor.pasteFromClipboard();
                        if (key == GLFW.GLFW_KEY_C && ctrl) textEditor.copySelection();
                        if (key == GLFW.GLFW_KEY_A && ctrl) textEditor.selectAll();
                    }
                }
            }
            return true;
        }

        if (picker != null) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
                switch (key) {
                    case GLFW.GLFW_KEY_ESCAPE -> closePicker();
                    case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> picker.stopListening();
                    case GLFW.GLFW_KEY_BACKSPACE -> picker.backspace();
                    default -> {
                        if (key == GLFW.GLFW_KEY_F && ctrl) picker.toggleListening();
                    }
                }
            }
            return true;
        }

        if (inlineTextOwner != null && inlineTextBuffer != null) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
                switch (key) {
                    case GLFW.GLFW_KEY_ESCAPE -> finishInlineText(false);
                    case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> finishInlineText(true);
                    case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> inlineBackspace();
                    default -> {
                        if (key == GLFW.GLFW_KEY_V && ctrl) inlinePaste();
                    }
                }
            }
            return true;
        }

        if (waitingForKey) {

            if (key == GLFW.GLFW_KEY_ESCAPE) {
                submitKeySelection((String) null);
                finishKeyBind();
                return true;
            }

            if (action == GLFW.GLFW_PRESS) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    submitKeySelection(pendingBindKeys);
                    finishKeyBind();
                    return true;
                }

                addPendingKey(key, scancode);
            }

            return true;
        }

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            return getActiveSection().keyPressed(key, scancode, mods);
        }
        return false;
    }

    // =========================
    //  INIT
    // =========================
    public static void init() {
        if (init) return;
        init = true;
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
        lastLifecycleAnimNs = System.nanoTime();
        getActiveSection().onSelected();
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

    // =========================
    //  CURSOR OPEN/CLOSE
    // =========================

    private static void detachScreenForExitOverlay() {
        if (MC == null) return;
        if (ClientScreen.current() instanceof ClickGuiScreen cs) {
            mainScreen = cs;
        }
        if (ClientScreen.current() instanceof ClickGuiScreen
                || ClientScreen.current() instanceof ClickGuiPickerScreen
                || ClientScreen.current() instanceof ClickGuiEditorScreen) {
            ClientScreen.show(MC, null);
        }
    }

    public static boolean isLifecycleActive() {
        return ModuleManager.isEnabled("clickgui") || closingForExit || lifecycleAnim > 0.001f;
    }

    public static boolean isClosingForExit() {
        return closingForExit;
    }

    private static boolean isInputReady() {
        return ModuleManager.isEnabled("clickgui") && !closingForExit && lifecycleAnim >= INPUT_READY_PROGRESS;
    }

    public static void finishCloseImmediately() {
        closingForExit = false;
        lifecycleAnim = 0.0f;
        onClose();
        clearScreens();
    }

    public static void onClose() {
        if (!cursorShown) return;
        long handle = MC.getWindow().handle();

        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        SystemCursor.invalidate();
        cursorShown = false;
        DraggableHudElementRegistry.setForceVisible(false);
        ClickGuiSearch.deactivate();
        closeTextEditor(false);
        clearInlineText();
        clearPicker();

        requestConfigSave();
    }

    public static void releaseCursorForGameplay() {
        if (MC == null || MC.getWindow() == null) return;
        if (ClientScreen.current() == null) {
            try {
                MC.mouseHandler.grabMouse();
                SystemCursor.invalidate();
            } catch (Throwable ignored) {
                GLFW.glfwSetInputMode(MC.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                SystemCursor.invalidate();
            }
        } else {
            GLFW.glfwSetInputMode(MC.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            SystemCursor.invalidate();
        }
        cursorShown = false;
        DraggableHudElementRegistry.setForceVisible(false);
    }

    public static void ensureCursorShown() {
        if (cursorShown) return;
        if (MC == null || MC.getWindow() == null) return;
        GLFW.glfwSetInputMode(
                MC.getWindow().handle(),
                GLFW.GLFW_CURSOR,
                GLFW.GLFW_CURSOR_NORMAL
        );
        SystemCursor.invalidate();
        cursorShown = true;
    }

    public static void renderEngine(Renderer2D rendererIn, TextRenderer fallback, GuiGraphicsExtractor ctx, float tickDelta) {
        try (ProfilerPhase.Scope frameScope = ProfilerPhase.scope("2d:clickgui:render");
             RenderProfiler2D.Section ignoredFrame = RenderProfiler2D.section("render")) {
            boolean enabled = ModuleManager.isEnabled("clickgui");
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
                    GLFW.glfwSetInputMode(
                            MC.getWindow().handle(),
                            GLFW.GLFW_CURSOR,
                            GLFW.GLFW_CURSOR_NORMAL
                    );
                    SystemCursor.invalidate();
                    cursorShown = true;
                }
                int fbw = MC.getWindow().getWidth();
                int fbh = MC.getWindow().getHeight();
                int vw = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
                int vh = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));
                fbWidth = vw;
                fbHeight = vh;

                try (ProfilerPhase.Scope layoutScope = ProfilerPhase.scope("2d:clickgui:layout");
                     RenderProfiler2D.Section ignoredLayout = RenderProfiler2D.section("layout");
                     TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:layout")) {
                    layout(vw, vh);
                    updateTabAnim(now);
                }

                if (pickerScreenActive) {
                    try (ProfilerPhase.Scope pickerScope = ProfilerPhase.scope("2d:clickgui:picker");
                         RenderProfiler2D.Section ignoredPicker = RenderProfiler2D.section("picker");
                         TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:picker")) {
                        renderPicker(vw, vh);
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

                try (ProfilerPhase.Scope tabsScope = ProfilerPhase.scope("2d:clickgui:tabs");
                     RenderProfiler2D.Section ignoredTabs = RenderProfiler2D.section("tabs");
                     TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:tabs")) {
                    drawTabs();
                }

                try (ProfilerPhase.Scope sectionScope = ProfilerPhase.scope("2d:clickgui:section");
                     RenderProfiler2D.Section ignoredSection = RenderProfiler2D.section("section");
                     TracyGpuProfiler.Scope gpuScope = TracyGpuProfiler.beginZone("2d:clickgui:section")) {
                    getActiveSection().render(mouseX, mouseY);
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
        return MC != null && (isLifecycleActive()
                || ClientScreen.current() instanceof ClickGuiScreen
                || ClientScreen.current() instanceof ClickGuiPickerScreen
                || ClientScreen.current() instanceof ClickGuiEditorScreen);
    }

    public static void renderTopLayer(GuiGraphicsExtractor ctx, float tickDelta) {
        if (!shouldRenderAsTopLayer()) return;

        CombatantRenderSystem.ensureFrameContext();
        ViewportContext.beginUnscaledLogical(ctx);
        try (RenderPhaseScope phaseScope = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "2d:clickgui_top")) {
            SystemCursor.beginFrame(ctx);
            try {
                Renderer2D.COLOR.begin();
                if (ClientScreen.current() instanceof ClickGuiEditorScreen) {
                    renderEditor(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta);
                } else {
                    renderEngine(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta);
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
        float pad = 8f;
        float areaX = x + pad;
        float areaY = y + pad;
        float areaW = Math.max(1f, w - pad * 2f);
        float areaH = Math.max(1f, h - pad * 2f);
        MODULES_SECTION.layout(areaX, areaY, areaW, areaH);
        float mx = -10_000f;
        float my = -10_000f;
        MODULES_SECTION.render(mx, my);
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
            tab.section().layout(modulesAreaX, modulesAreaY, modulesAreaW, modulesAreaH);
        }

        tabBarH = TAB_HEIGHT;
        tabBarY = 10f;
        ensureTabCapacity(tabs.size());
        float totalTabs = 0f;
        for (int t = 0; t < tabs.size(); t++) {
            float wTab = textWidth(getSfProDisplaySemibold(), tabs.get(t).label(), TAB_FONT) + TAB_PAD_X * 2f;
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
            ClientScreen.show(MC, null);
        }
        clearScreens();
        finishingClose = false;
    }

    private static void drawTabs() {
        float eased = easeOutCubic(lifecycleAnim);
        if (eased <= 0.001f) return;
        List<ClickGuiTabEntry> tabs = currentTabs();
        ensureActiveTab(tabs);
        if (tabs.isEmpty()) return;
        boolean islandShell = DynamicIsland.shouldOwnClickGuiTabShell();

        TextRenderer tabFont = getSfProDisplaySemibold();
        float y = tabBarY - (1.0f - eased) * 18.0f;
        float outerRadius = tabBarH * 0.5f;
        float outerPad = 4.0f;
        float innerX = tabAnimX + outerPad;
        float innerY = y + outerPad;
        float innerW = Math.max(1.0f, tabAnimW - outerPad * 2.0f);
        float innerH = Math.max(1.0f, tabBarH - outerPad * 2.0f);
        float innerRadius = innerH * 0.5f;

        int outerLeft = scaleAlpha(mixColor(theme().surface(), theme().windowBg(), 0.34f), eased);
        int outerRight = scaleAlpha(mixColor(theme().surfaceHover(), theme().windowBg(), 0.22f), eased);
        int outerStrokeLeft = scaleAlpha(mixColor(theme().strokeSoft(), theme().accentSoft(), 0.18f), 0.72f * eased);
        int outerStrokeRight = scaleAlpha(mixColor(theme().strokeSoft(), theme().textPrimary(), 0.08f), 0.58f * eased);

        int activeLeft = scaleAlpha(mixColor(theme().accentSoft(), theme().accent(), 0.18f), eased);
        int activeRight = scaleAlpha(mixColor(theme().accent(), theme().textPrimary(), 0.08f), eased);
        int activeStrokeLeft = scaleAlpha(mixColor(theme().accentSoft(), theme().textPrimary(), 0.20f), 0.88f * eased);
        int activeStrokeRight = scaleAlpha(theme().accent(), 0.92f * eased);

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
                drawRoundedRectShadow(
                        tabBarX,
                        y,
                        tabBarW,
                        tabBarH,
                        outerRadius,
                        10.0f,
                        1.5f,
                        scaleAlpha(0xCC000000, 0.34f * eased)
                );
                drawRoundedRectGradient(tabBarX, y, tabBarW, tabBarH, outerRadius, outerLeft, outerRight, 0.0f);
                drawRoundedRectStrokeGradient(tabBarX, y, tabBarW, tabBarH, outerRadius, 1.0f, outerStrokeLeft, outerStrokeRight, 0.0f);
            }

            drawRoundedRectGlow(
                    innerX,
                    innerY,
                    innerW,
                    innerH,
                    innerRadius,
                    10.0f,
                    scaleAlpha(theme().accentSoft(), 0.30f * eased)
            );
            drawRoundedRectGradient(innerX, innerY, innerW, innerH, innerRadius, activeLeft, activeRight, 0.0f);
            drawRoundedRectStrokeGradient(innerX, innerY, innerW, innerH, innerRadius, 1.0f, activeStrokeLeft, activeStrokeRight, 0.0f);

            for (int i = 0; i < tabs.size(); i++) {
                float x = tabX[i];
                float w = tabW[i];
                ClickGuiTabEntry tab = tabs.get(i);
                boolean active = tab.id().equals(activeTabId);
                boolean hovered = !closingForExit && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + tabBarH;
                if (i > 0) {
                    float sepH = tabBarH - outerPad * 3.4f;
                    float sepY = y + (tabBarH - sepH) * 0.5f;
                    drawRect(x, sepY, 1.0f, sepH, scaleAlpha(theme().strokeSoft(), 0.32f * eased));
                }
                int textCol = active ? theme().textPrimary() : theme().textMuted();
                if (!active && hovered) {
                    textCol = mixColor(textCol, theme().textPrimary(), 0.55f);
                }
                textCol = scaleAlpha(textCol, eased);
                float textH = textHeight(tabFont, TAB_FONT);
                float textY = y + (tabBarH - textH) * 0.5f - 0.35f;
                float labelW = textWidth(tabFont, tab.label(), TAB_FONT);
                float textX = x + (w - labelW) * 0.5f;
                drawText(tabFont, tab.label(), textX, textY, TAB_FONT, textCol, false);
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

    private static ClickGuiSection getActiveSection() {
        List<ClickGuiTabEntry> tabs = currentTabs();
        ensureActiveTab(tabs);
        for (ClickGuiTabEntry tab : tabs) {
            if (tab.id().equals(activeTabId)) {
                return tab.section();
            }
        }
        activeTabId = MODULES_TAB_ID;
        return MODULES_SECTION;
    }

    private static boolean isSettingsTabActive() {
        return SETTINGS_TAB_ID.equals(activeTabId);
    }

    private static List<ClickGuiTabEntry> currentTabs() {
        List<ClickGuiSectionManager.Entry> addonSections = ClickGuiSectionManager.sections();
        ArrayList<ClickGuiTabEntry> out = new ArrayList<>(2 + addonSections.size());
        out.add(new ClickGuiTabEntry(MODULES_TAB_ID, "Modules", MODULES_SECTION));
        out.add(new ClickGuiTabEntry(SETTINGS_TAB_ID, "Settings", SETTINGS_SECTION));
        for (ClickGuiSectionManager.Entry entry : addonSections) {
            out.add(new ClickGuiTabEntry(entry.id(), entry.label(), entry.section()));
        }
        return out;
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
                picker != null
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

    public static TextRenderer getInterRegular() {
        if (interRegular == null) {
            interRegular = Fonts.renderer("Inter", FontInfo.Type.Regular, TextRenderer.get());
        }
        return interRegular;
    }

    public static Renderer2D currentRenderer() {
        return renderer;
    }

    public static TextRenderer getInterMedium() {
        if (interMedium == null) {
            interMedium = Fonts.renderer("InterMedium", FontInfo.Type.Regular, getInterRegular());
        }
        return interMedium;
    }

    public static TextRenderer getIosevkaRegular() {
        return getSfMedium();
    }

    public static TextRenderer getSfMedium() {
        if (onestMedium == null) {
            onestMedium = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, TextRenderer.get());
        }
        return onestMedium;
    }

    public static TextRenderer getSfProDisplaySemibold() {
        if (onestBold == null) {
            onestBold = Fonts.renderer("OnestBold", FontInfo.Type.Regular, getInterMedium());
        }
        return onestBold;
    }

    public static TextRenderer getMonsterratRegular() {
        if (monsterratRegular == null) {
            monsterratRegular = Fonts.renderer("Monsterrat", FontInfo.Type.Regular, TextRenderer.get());
        }
        return monsterratRegular;
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

    public static int mixColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) (((from >>> 16) & 0xFF) * (1 - t) + ((to >>> 16) & 0xFF) * t);
        int g = (int) (((from >>> 8) & 0xFF) * (1 - t) + ((to >>> 8) & 0xFF) * t);
        int b = (int) (((from) & 0xFF) * (1 - t) + ((to) & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int scaleAlpha(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int na = (int) (a * Math.max(0f, Math.min(1f, factor)));
        return (color & 0x00FFFFFF) | (na << 24);
    }

    public static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static float setRenderAlphaMultiplier(float alpha) {
        float previous = renderAlphaMultiplier;
        renderAlphaMultiplier = Math.max(0.0f, Math.min(1.0f, alpha));
        return previous;
    }

    public static float getRenderAlphaMultiplier() {
        return renderAlphaMultiplier;
    }

    public static void restoreRenderAlphaMultiplier(float previous) {
        renderAlphaMultiplier = Math.max(0.0f, Math.min(1.0f, previous));
    }

    private static int applyAlpha(int argb) {
        if (renderAlphaMultiplier >= 0.999f) return argb;
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(a * renderAlphaMultiplier);
        return (argb & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    public static float scaleForSize(float size) {
        return size / 18.0f;
    }

    public static float textWidth(TextRenderer tr, String text, float size) {
        if (tr == null || text == null || text.isEmpty()) return 0f;
        TextMeasureKey key = new TextMeasureKey(tr, Float.floatToIntBits(size), text);
        Float cached = TEXT_WIDTH_CACHE.get(key);
        if (cached != null) return cached;
        float scale = scaleForSize(size);
        tr.begin(scale, true, false);
        try {
            float width = (float) tr.getWidth(text, false);
            TEXT_WIDTH_CACHE.put(key, width);
            return width;
        } finally {
            tr.end();
        }
    }

    public static float textHeight(TextRenderer tr, float size) {
        if (tr == null) return 0f;
        TextHeightKey key = new TextHeightKey(tr, Float.floatToIntBits(size));
        Float cached = TEXT_HEIGHT_CACHE.get(key);
        if (cached != null) return cached;
        float scale = scaleForSize(size);
        tr.begin(scale, true, false);
        try {
            float height = (float) tr.getHeight(false);
            TEXT_HEIGHT_CACHE.put(key, height);
            return height;
        } finally {
            tr.end();
        }
    }

    public static void beginTextBatch() {
        if (textBatchActive) return;
        textBatchActive = true;
        TEXT_BATCH.clear();
    }

    public static void endTextBatch() {
        if (!textBatchActive) return;
        textBatchActive = false;
        if (TEXT_BATCH.isEmpty()) return;

        java.util.LinkedHashMap<TextBatchKey, java.util.ArrayList<TextBatchItem>> groups = new java.util.LinkedHashMap<>();
        for (TextBatchItem item : TEXT_BATCH) {
            TextBatchKey key = new TextBatchKey(item.renderer, Float.floatToIntBits(item.size), item.shadow);
            java.util.ArrayList<TextBatchItem> list = groups.get(key);
            if (list == null) {
                list = new java.util.ArrayList<>();
                groups.put(key, list);
            }
            list.add(item);
        }

        for (var entry : groups.entrySet()) {
            TextBatchKey key = entry.getKey();
            TextRenderer tr = key.renderer;
            if (tr == null) continue;
            float size = Float.intBitsToFloat(key.sizeBits);
            float scale = scaleForSize(size);
            tr.begin(scale, false, false);
            for (TextBatchItem item : entry.getValue()) {
                tr.render(item.text, item.x, item.y, new RenderColor(item.argb), item.shadow);
            }
            tr.end();
        }

        TEXT_BATCH.clear();
    }

    public static void drawText(TextRenderer tr, String text, float x, float y, float size, int argb, boolean shadow) {
        if (tr == null || text == null || text.isEmpty()) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        if (textBatchActive) {
            TEXT_BATCH.add(new TextBatchItem(tr, text, x, y, size, argb, shadow));
            return;
        }
        float scale = scaleForSize(size);
        tr.begin(scale, false, false);
        tr.render(text, x, y, new RenderColor(argb), shadow);
        tr.end();
    }

    public static String fitText(TextRenderer tr, String text, float size, float maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0f) return text;
        String trimmed = text;
        float w = textWidth(tr, trimmed, size);
        if (w <= maxWidth) return trimmed;
        while (trimmed.length() > 1 && textWidth(tr, trimmed + "...", size) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    public static List<String> wrapText(TextRenderer tr, String text, float size, float maxWidth, int maxLines) {
        ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }
        if (maxLines <= 1 || maxWidth <= 0f) {
            lines.add(fitText(tr, text, size, maxWidth));
            return lines;
        }

        String remaining = normalizeSpaces(text);
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (lines.size() == maxLines - 1) {
                lines.add(fitText(tr, remaining, size, maxWidth));
                break;
            }
            if (textWidth(tr, remaining, size) <= maxWidth) {
                lines.add(remaining);
                break;
            }

            int split = bestWrapIndex(tr, remaining, size, maxWidth);
            String line = remaining.substring(0, split).trim();
            if (line.isEmpty()) {
                lines.add(fitText(tr, remaining, size, maxWidth));
                break;
            }
            lines.add(line);
            remaining = remaining.substring(split).trim();
        }

        if (lines.isEmpty()) {
            lines.add(fitText(tr, text, size, maxWidth));
        }
        return lines;
    }

    private static int bestWrapIndex(TextRenderer tr, String text, float size, float maxWidth) {
        int bestSpace = -1;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) continue;
            String candidate = text.substring(0, i).trim();
            if (!candidate.isEmpty() && textWidth(tr, candidate, size) <= maxWidth) {
                bestSpace = i;
                continue;
            }
            if (bestSpace >= 0) break;
        }
        if (bestSpace >= 0) return bestSpace;

        String fitted = fitText(tr, text, size, maxWidth);
        if (fitted.endsWith("...")) {
            fitted = fitted.substring(0, Math.max(1, fitted.length() - 3));
        }
        return Math.max(1, Math.min(text.length(), fitted.length()));
    }

    private static String normalizeSpaces(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(trimmed.length());
        boolean lastSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastSpace) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        return sb.toString();
    }

    private static String normalizeInlineText(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.replace('\n', ' ');
    }

    public static void drawRoundedRect(float x, float y, float w, float h, float radius, int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRect(x, y, w, h, radius, 1.1f, argb);
    }

    public static void drawRoundedRectStroke(float x, float y, float w, float h, float radius, float thickness, int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRectStroke(x, y, w, h, radius, 1.1f, thickness, argb);
    }

    public static void drawRoundedRectGlow(float x, float y, float w, float h,
                                           float radius, float glow, int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRectGlow(x, y, w, h, radius, 1.1f, glow, argb);
    }

    public static void drawRoundedRectShadow(float x, float y, float w, float h,
                                             float radius, float softness, float spread, int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRectShadow(x, y, w, h, radius, softness, spread, argb);
    }

    public static void drawRoundedRectGradient(float x, float y, float w, float h, float radius,
                                               int startArgb, int endArgb, float angleDeg) {
        if (renderer == null) return;
        startArgb = applyAlpha(startArgb);
        endArgb = applyAlpha(endArgb);
        if (((startArgb >>> 24) & 0xFF) <= 0 && ((endArgb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRectGradient(x, y, w, h, radius, 1.1f, startArgb, endArgb, angleDeg);
    }

    public static void drawRoundedRectStrokeGradient(float x, float y, float w, float h, float radius, float thickness,
                                                     int startArgb, int endArgb, float angleDeg) {
        if (renderer == null) return;
        startArgb = applyAlpha(startArgb);
        endArgb = applyAlpha(endArgb);
        if (((startArgb >>> 24) & 0xFF) <= 0 && ((endArgb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRectStrokeGradient(x, y, w, h, radius, 1.1f, thickness, startArgb, endArgb, angleDeg);
    }

    public static void drawRoundedRectCorners(float x, float y, float w, float h,
                                              float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                              int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRectCorners(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, 1.1f, argb);
    }

    public static void drawRect(float x, float y, float w, float h, int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.quad(x, y, w, h, argb);
    }

    public static void flushRenderer() {
        if (renderer == null) return;
        if (Renderer2D.isBatching()) {
            Renderer2D.flushBatch();
        } else {
            renderer.begin();
        }
    }

    public static void drawGradientRect(float x, float y, float w, float h,
                                        int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        if (renderer == null) return;
        cTopLeft = applyAlpha(cTopLeft);
        cTopRight = applyAlpha(cTopRight);
        cBottomRight = applyAlpha(cBottomRight);
        cBottomLeft = applyAlpha(cBottomLeft);
        renderer.quadGradient(x, y, w, h, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public static void drawCircle(float cx, float cy, float r, int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.circle(cx, cy, r, argb);
    }

    public static void drawLine(float x1, float y1, float x2, float y2, int argb) {
        if (renderer == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.line(x1, y1, x2, y2, argb);
    }

    public static void drawBlur(float x, float y, float w, float h, float radius, int argb, float alpha) {
        alpha *= renderAlphaMultiplier;
        alpha = AnimationUtility.clamp(alpha * clickGuiBlurDensityScale(), 0.0f, 1.0f);
        if (alpha <= 0.01f) return;
        float quality = clickGuiBlurQuality();
        float brightness = 1.0f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    public static void drawGlass(float x, float y, float w, float h, float radius, float alpha) {
        if (renderer == null) return;
        alpha *= renderAlphaMultiplier;
        if (alpha <= 0.01f) return;

        float pad = 5f;
        float gx = x - pad;
        float gy = y - pad;
        float gw = w + pad * 2f;
        float gh = h + pad * 2f;
        float gr = Math.max(0f, radius + pad);
        float blurAlpha = AnimationUtility.clamp(alpha * clickGuiGlassBlurScale(), 0.0f, 1.0f);
        float scale = Math.max(0.75f, gr / 10.0f);

        Renderer2D.COLOR.blurRect(gx, gy, gw, gh, gr, clickGuiBlurQuality(), 1.0f, blurAlpha, 0xFFFFFF);

        renderer.liquidGlassRect(
                gx, gy, gw, gh, gr,
                15.0f * scale,
                0xFFFFFFFF,
                alpha,
                blurAlpha,
                -18.0f,
                1.0f,
                0.78f,
                0.52f,
                0.190f * alpha,
                0.0f
        );
    }

    public static float clickGuiBlurQuality() {
        return 27.35f;
    }

    public static float clickGuiBlurDensityScale() {
        return 1.16f;
    }

    public static float clickGuiGlassBlurScale() {
        return 1.28f;
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
        ClickGuiSounds.bindingStart();
    }

    public static void finishKeyBind() {
        waitingForKey = false;
        keyBindOwner = null;
        pendingBindKeys.clear();
    }

    private static void addPendingKey(int keyCode, int scancode) {
        int code = KeyUtil.fromKeyInput(keyCode, scancode);
        if (code == KeyUtil.NONE) return;
        pendingBindKeys.add(code);
    }

    private static void addPendingMouseButton(int button) {
        if (button < GLFW.GLFW_MOUSE_BUTTON_1 || button > GLFW.GLFW_MOUSE_BUTTON_LAST) return;
        pendingBindKeys.add(KeyUtil.mouseButtonToCode(button));
    }

    private static void submitKeySelection(Set<Integer> keys) {
        if (keys == null || keys.isEmpty()) {
            submitKeySelection((String) null);
            return;
        }
        submitKeySelection(KeyUtil.comboToString(keys));
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
        return pendingBindKeys.isEmpty() ? "..." : KeyUtil.comboToString(pendingBindKeys);
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
        return picker != null;
    }

    public static boolean isBlockingModuleKeybinds() {
        if (!ModuleManager.isEnabled("clickgui")) return false;
        if (waitingForKey) return true;
        if (textEditor != null) return true;
        if (inlineTextOwner != null) return true;
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

    public static void openClickGuiScreen() {
        if (MC == null) return;
        if (ClientScreen.current() != null) return;
        if (mainScreen == null) {
            mainScreen = new ClickGuiScreen();
        }
        ClickGuiSounds.guiOpen();
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
        picker = new ClickGuiPickerState(owner, title, mode);
        openPickerScreen();
    }

    public static void closePickerScreen() {
        closePicker();
    }

    private static void closePicker() {
        picker = null;
        if (MC != null && ClientScreen.current() instanceof ClickGuiPickerScreen) {
            if (mainScreen == null) {
                mainScreen = new ClickGuiScreen();
            }
            ClientScreen.show(MC, mainScreen);
        }
    }

    private static void clearPicker() {
        picker = null;
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
                                      boolean pickerActive) {
    }

    private record TextBatchItem(TextRenderer renderer, String text, float x, float y, float size, int argb,
                                 boolean shadow) {
    }

    private record TextBatchKey(TextRenderer renderer, int sizeBits, boolean shadow) {
    }

    private record TextMeasureKey(TextRenderer renderer, int sizeBits, String text) {
    }

    private record TextHeightKey(TextRenderer renderer, int sizeBits) {
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
