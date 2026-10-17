/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.compat.iris.gui;

import combatant.client.config.subsystem.VisualConfig;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.LegacyTextUtil;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.OldImageButton;
import net.irisshaders.iris.gui.element.IrisElementRow;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.element.widget.BaseOptionElementWidget;
import net.irisshaders.iris.gui.element.widget.BooleanElementWidget;
import net.irisshaders.iris.gui.element.widget.CommentedElementWidget;
import net.irisshaders.iris.gui.element.widget.LinkElementWidget;
import net.irisshaders.iris.gui.element.widget.ProfileElementWidget;
import net.irisshaders.iris.gui.element.widget.SliderElementWidget;
import net.irisshaders.iris.gui.element.widget.StringElementWidget;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.Profile;
import net.irisshaders.iris.shaderpack.option.StringOption;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuProfileElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Fixed-coordinate Combatant presentation for Iris' existing shader-pack screen.
 * Iris keeps ownership of shader selection, option values, navigation and apply/cancel semantics.
 */
public final class IrisGraphicsGuiRenderer {
    private static final float CANVAS_W = 1920f;
    private static final float CANVAS_H = 1080f;
    private static final float ROOT_W = 1710f;
    private static final float ROOT_H = 920f;
    private static final float HEADER_H = 92f;
    private static final float FOOTER_H = 86f;
    private static final float DETAILS_W = 500f;
    private static final float DIVIDER_W = 2f;
    private static final float CONTENT_GAP = 18f;
    private static final float TOOLBAR_H = 70f;
    private static final float SCROLL_W = 8f;

    private static final Map<ShaderPackScreen, UiState> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ShaderPackScreen, Frame> LAST_FRAME = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Motion> MOTIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> CONTROL_MOTION_KEYS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Field STRING_OPTION_FIELD = field(StringElementWidget.class, "option");
    private static final Field ELEMENT_FIELD = field(AbstractElementWidget.class, "element");
    private static final Field NAVIGATION_FIELD = field(BaseOptionElementWidget.class, "navigation");

    private IrisGraphicsGuiRenderer() {
    }

    public static boolean shouldUseModernUi() {
        return !RuntimeGate.isPanic()
                && RuntimeGate.canRunRender()
                && VisualConfig.get().isModernIrisGuiEnabled();
    }

    public static boolean render(ShaderPackScreen screen,
                                 GuiGraphicsExtractor ctx,
                                 int mouseX,
                                 int mouseY,
                                 float delta,
                                 ShaderPackSelectionList packList,
                                 ShaderPackOptionList optionList,
                                 Button screenSwitchButton,
                                 Button openFolderButton,
                                 OldImageButton showHideButton,
                                 boolean optionMenuOpen,
                                 boolean guiHidden,
                                 Component notificationDialog,
                                 int notificationDialogTimer) {
        if (!shouldUseModernUi() || screen == null || ctx == null || packList == null) return false;
        FixedCanvas canvas = FixedCanvas.capture();
        if (!canvas.valid()) return false;

        UiState state = state(screen);
        boolean projection = false;
        boolean batch = false;
        try {
            CombatantRenderSystem.ensureFrameContext();
            ViewportContext.beginCurrentStratumUnscaledLogical(ctx);
            projection = true;
            Renderer2D.COLOR.begin();
            batch = true;

            Style style = Style.current();
            if (guiHidden) {
                Frame hidden = Frame.hidden(canvas, showHideButton != null);
                LAST_FRAME.put(screen, hidden);
                drawHiddenButton(hidden, style);
            } else {
                Frame frame = buildFrame(canvas, screen, state, packList, optionList,
                        optionMenuOpen, showHideButton != null);
                LAST_FRAME.put(screen, frame);
                updateDrag(frame, state);
                updateSelectPopup(state, frame, optionMenuOpen);
                drawBackdrop(canvas, style);
                drawShell(frame, style);
                drawHeader(frame, state, packList, optionMenuOpen, screenSwitchButton,
                        openFolderButton, showHideButton, notificationDialog, notificationDialogTimer, style);
                if (optionMenuOpen && optionList != null) {
                    drawOptionBrowser(frame, state, style);
                } else {
                    drawPackBrowser(frame, state, packList, style);
                }
                drawFooter(frame, screen, optionMenuOpen, style);
                if (optionMenuOpen) drawSelectPopup(frame, state, style);
            }

            Renderer2D.COLOR.render();
            batch = false;
            ViewportContext.endCurrentStratum(ctx);
            projection = false;
            return true;
        } catch (Throwable t) {
            DebugLog.errorOnce("iris-modern-gui-render-failed",
                    "[IrisGui] Modern renderer failed; falling back to Iris native UI", t);
            try {
                if (batch) Renderer2D.COLOR.render();
            } catch (Throwable ignored) {
            }
            try {
                if (projection) ViewportContext.endCurrentStratum(ctx);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    public static boolean mouseClicked(ShaderPackScreen screen,
                                       MouseButtonEvent event,
                                       boolean doubleClick,
                                       ShaderPackSelectionList packList,
                                       ShaderPackOptionList optionList,
                                       Button screenSwitchButton,
                                       Button openFolderButton,
                                       boolean optionMenuOpen,
                                       boolean guiHidden,
                                       Runnable toggleHidden) {
        if (!shouldUseModernUi() || screen == null || event == null) return false;
        Frame f = LAST_FRAME.get(screen);
        if (f == null) return false;
        float mx = f.canvas.mouseX;
        float my = f.canvas.mouseY;
        UiState state = state(screen);

        if (state.selectPopup != null) {
            if (handleSelectPopupClick(state, mx, my, event.button())) return true;
        }

        if (guiHidden) {
            if (f.hideButton != null && f.hideButton.contains(mx, my) && event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                if (toggleHidden != null) toggleHidden.run();
            }
            // Native Iris widgets are still alive while their rendering is replaced. Consume
            // pointer input in hidden mode so invisible native controls cannot be activated.
            return true;
        }

        if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
            state.packScroll.dragging = false;
            state.optionScroll.dragging = false;
        }

        if (f.hideButton != null && f.hideButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT && toggleHidden != null) {
                pressed(ControlId.HIDE);
                toggleHidden.run();
            }
            return true;
        }
        if (f.gridButton != null && f.gridButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                state.gridView = true;
                pressed(ControlId.GRID);
            }
            return true;
        }
        if (f.listButton != null && f.listButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                state.gridView = false;
                pressed(ControlId.LIST);
            }
            return true;
        }
        if (openFolderButton != null && f.folderButton != null && f.folderButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                pressed(ControlId.FOLDER);
                clickNativeButton(openFolderButton, event, doubleClick);
            }
            return true;
        }
        if (screenSwitchButton != null && f.switchButton != null && f.switchButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                pressed(ControlId.OPTIONS);
                clickNativeButton(screenSwitchButton, event, doubleClick);
            }
            return true;
        }
        if (f.shaderToggle != null && f.shaderToggle.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT && packList.getTopButtonRow() != null) {
                pressed(packList.getTopButtonRow());
                packList.getTopButtonRow().mouseClicked(event, doubleClick);
            }
            return true;
        }
        if (f.downloadButton != null && f.downloadButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT && f.downloadAction != null) {
                pressed(ControlId.DOWNLOAD);
                f.downloadAction.mouseClicked(event, doubleClick);
            }
            return true;
        }

        if (f.scrollbar != null && f.scrollbar.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                ScrollState scroll = optionMenuOpen ? state.optionScroll : state.packScroll;
                beginScrollbarDrag(scroll, my, f.scrollbar, f.contentHeight, f.viewport.h);
            }
            return true;
        }

        if (optionMenuOpen) {
            for (HeaderAction action : f.headerActions) {
                if (!action.rect.contains(mx, my)) continue;
                if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT && action.listener != null) {
                    pressed(action.listener);
                    action.listener.mouseClicked(event, doubleClick);
                }
                return true;
            }
            for (OptionSlot slot : f.optionSlots) {
                if (!slot.rect.contains(mx, my)) continue;
                if (slot.widget == null) return true;
                if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                    pressed(slot.widget);
                    state.focusedOption = slot.widget;
                }
                if (slot.widget instanceof SliderElementWidget slider && event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                    slider.bounds = new ScreenRectangle(0, 0, 100, 22);
                    slider.mouseClicked(new MouseButtonEvent(50.0, 11.0, event.buttonInfo()), doubleClick);
                    state.activeSlider = new ActiveSlider(slider, slot.rect);
                    updateSlider(slider, slot.rect, mx);
                    return true;
                }
                if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                    if (slot.widget instanceof StringElementWidget string
                            && !(slot.widget instanceof SliderElementWidget)
                            && openStringPopup(state, string, slot.rect, f.viewport)) {
                        return true;
                    }
                    if (slot.widget instanceof ProfileElementWidget profile
                            && openProfilePopup(state, profile, slot.rect, f.viewport)) {
                        return true;
                    }
                }
                slot.widget.bounds = new ScreenRectangle(0, 0, 220, 22);
                MouseButtonEvent synthetic = new MouseButtonEvent(110.0, 11.0, event.buttonInfo());
                slot.widget.mouseClicked(synthetic, doubleClick);
                return true;
            }
        } else {
            for (PackSlot slot : f.packSlots) {
                if (!slot.rect.contains(mx, my)) continue;
                if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                    pressed(slot.entry);
                    slot.entry.mouseClicked(event, doubleClick);
                    state.selectedPackName = slot.entry.getPackName();
                }
                return true;
            }
        }

        if (f.cancelButton != null && f.cancelButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                pressed(ControlId.CANCEL);
                clickCancel(screen, event, doubleClick);
            }
            return true;
        }
        if (f.applyButton != null && f.applyButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                pressed(ControlId.APPLY);
                screen.applyChanges();
            }
            return true;
        }
        if (f.doneButton != null && f.doneButton.contains(mx, my)) {
            if (event.button() == combatant.client.input.InputKeys.MOUSE_LEFT) {
                pressed(ControlId.DONE);
                screen.onClose();
            }
            return true;
        }

        // The custom renderer owns all pointer hit-testing while active. Never let an event that
        // missed our fixed-coordinate surface fall through into Iris' still-live GUI-scaled
        // native widget geometry underneath it.
        return true;
    }

    public static boolean mouseScrolled(ShaderPackScreen screen, double vertical) {
        if (!shouldUseModernUi() || screen == null || vertical == 0.0) return false;
        Frame f = LAST_FRAME.get(screen);
        if (f == null || f.hidden) return false;
        UiState state = state(screen);
        if (state.selectPopup != null) {
            SelectPopup popup = state.selectPopup;
            if (popup.bounds.contains(f.canvas.mouseX, f.canvas.mouseY)) {
                popup.scrollTarget -= (float) Math.signum(vertical) * 42f;
                popup.clampScroll();
            }
            return true;
        }
        if (!f.viewport.contains(f.canvas.mouseX, f.canvas.mouseY)) return false;
        ScrollState scroll = f.optionMode ? state.optionScroll : state.packScroll;
        scroll.target -= (float) Math.signum(vertical) * 72f;
        scroll.clamp(f.contentHeight, f.viewport.h);
        return true;
    }


    private static void updateSelectPopup(UiState state, Frame frame, boolean optionMode) {
        SelectPopup popup = state.selectPopup;
        if (!optionMode) {
            state.selectPopup = null;
            state.focusedOption = null;
            return;
        }
        if (popup == null) return;
        float dt = Math.max(0.001f, AnimationUtility.deltaTime());
        popup.reveal = AnimationUtility.approach(popup.reveal, popup.closing ? 0f : 1f, dt, 13f);
        popup.scrollCurrent = AnimationUtility.approach(popup.scrollCurrent, popup.scrollTarget, dt, 12f);
        popup.clampScroll();
        if (popup.closing && popup.reveal <= 0.012f) state.selectPopup = null;
    }

    private static boolean openStringPopup(UiState state,
                                           StringElementWidget widget,
                                           Rect card,
                                           Rect viewport) {
        StringOption option = stringOption(widget);
        if (option == null) return false;
        List<String> values = option.getAllowedValues();
        if (values == null || values.size() <= 1) return false;

        int currentIndex = widget instanceof IrisStringOptionWidgetAccess access
                ? access.combatant$getValueIndex() : values.indexOf(widget.getValue());
        ArrayList<SelectChoice> choices = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            int target = i;
            String raw = values.get(i);
            choices.add(new SelectChoice(displayStringValue(widget, raw), i == currentIndex,
                    () -> applyStringChoice(widget, target)));
        }
        state.selectPopup = buildSelectPopup(widget, card, viewport, choices, Math.max(0, currentIndex));
        return true;
    }

    private static boolean openProfilePopup(UiState state,
                                            ProfileElementWidget widget,
                                            Rect card,
                                            Rect viewport) {
        OptionMenuProfileElement element = profileElement(widget);
        if (element == null || element.profiles == null || element.profiles.size() <= 1) return false;

        Profile current = element.profiles.scan(element.options, element.getPendingOptionValues()).current.orElse(null);
        ArrayList<SelectChoice> choices = new ArrayList<>();
        int[] selectedIndex = {-1};
        int[] index = {0};
        element.profiles.forEach((name, profile) -> {
            int i = index[0]++;
            if (profile == current) selectedIndex[0] = i;
            String label = LegacyTextUtil.stripLegacy(
                    GuiUtil.translateOrDefault(Component.literal(name), "profile." + name).getString());
            choices.add(new SelectChoice(label, profile == current, () -> applyProfileChoice(widget, profile)));
        });
        if (choices.size() <= 1) return false;
        state.selectPopup = buildSelectPopup(widget, card, viewport, choices, Math.max(0, selectedIndex[0]));
        return true;
    }

    private static SelectPopup buildSelectPopup(BaseOptionElementWidget<?> widget,
                                                 Rect card,
                                                 Rect viewport,
                                                 List<SelectChoice> choices,
                                                 int selectedIndex) {
        float controlW = Math.min(238f, card.w * 0.42f);
        float popupW = Math.min(340f, Math.max(250f, controlW + 34f));
        float rowH = 42f;
        int visibleRows = Math.min(7, choices.size());
        float popupH = visibleRows * rowH + 12f;
        float x = card.x + card.w - popupW - 14f;
        x = Math.max(viewport.x + 4f, Math.min(x, viewport.x + viewport.w - popupW - 4f));
        boolean opensUp = card.y + card.h + 8f + popupH > viewport.y + viewport.h;
        float y = opensUp ? card.y - popupH - 8f : card.y + card.h + 8f;
        y = Math.max(viewport.y + 4f, Math.min(y, viewport.y + viewport.h - popupH - 4f));

        SelectPopup popup = new SelectPopup(widget, choices, new Rect(x, y, popupW, popupH), rowH, opensUp);
        float selectedTop = selectedIndex * rowH;
        float visibleH = popupH - 12f;
        if (selectedTop + rowH > visibleH) popup.scrollTarget = selectedTop + rowH - visibleH;
        popup.scrollCurrent = popup.scrollTarget;
        popup.clampScroll();
        return popup;
    }

    private static boolean handleSelectPopupClick(UiState state, float mx, float my, int button) {
        SelectPopup popup = state.selectPopup;
        if (popup == null) return false;
        if (popup.closing) return true;
        if (button != combatant.client.input.InputKeys.MOUSE_LEFT) return true;

        Rect visible = popup.animatedBounds();
        if (!visible.contains(mx, my)) {
            popup.close();
            return true;
        }

        float reveal = AnimationUtility.easeInOutCubic(popup.reveal);
        float slide = (1f - reveal) * (popup.opensUp ? 10f : -10f);
        for (int i = 0; i < popup.choices.size(); i++) {
            Rect row = popup.rowRect(i, slide);
            if (!row.intersects(visible) || !row.contains(mx, my)) continue;
            SelectChoice choice = popup.choices.get(i);
            if (!choice.selected) {
                pressed(popup.widget);
                choice.action.run();
            }
            GuiUtil.playButtonClickSound();
            popup.close();
            return true;
        }
        return true;
    }

    private static void drawSelectPopup(Frame f, UiState state, Style s) {
        SelectPopup popup = state.selectPopup;
        if (popup == null) return;

        float reveal = AnimationUtility.easeInOutCubic(popup.reveal);
        Rect r = popup.animatedBounds();
        if (r.h <= 1f) return;

        Renderer2D.COLOR.quad(r.x - 5f, r.y - 5f, r.w + 10f, r.h + 10f, withAlpha(0xFF000000, Math.round(54f * reveal)));
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(s.control, Math.round(234f * reveal)),
                withAlpha(s.controlHover, Math.round(228f * reveal)),
                withAlpha(s.cardBottom, Math.round(236f * reveal)),
                withAlpha(s.cardBottom, Math.round(230f * reveal)));
        Renderer2D.COLOR.quad(r.x, r.y, r.w, 1f, withAlpha(s.strokeBright, Math.round(92f * reveal)));
        Renderer2D.COLOR.quad(r.x, r.y + r.h - 1f, r.w, 1f, withAlpha(s.stroke, Math.round(74f * reveal)));

        popup.hoveredChoice = null;
        boolean scissor = ScissorFunction.pushRaw(r.x, r.y, r.w, r.h);
        try {
            float slide = (1f - reveal) * (popup.opensUp ? 10f : -10f);
            for (int i = 0; i < popup.choices.size(); i++) {
                SelectChoice choice = popup.choices.get(i);
                Rect row = popup.rowRect(i, slide);
                if (!row.intersects(r)) continue;
                boolean hover = !popup.closing && row.contains(f.canvas.mouseX, f.canvas.mouseY);
                if (hover) {
                    popup.hoveredChoice = choice;
                    SystemCursor.set(SystemCursor.CursorType.HAND);
                }

                Motion motion = popup.motion(choice, hover, choice.selected);
                float h = AnimationUtility.easeOutCubic(motion.hover);
                float a = AnimationUtility.easeOutCubic(motion.active);
                int bg = mix(s.control, s.controlHover, h * 0.72f + a * 0.16f);
                Renderer2D.COLOR.quad(row.x + 6f, row.y, row.w - 12f, row.h - 2f,
                        withAlpha(bg, Math.round((102f + h * 48f + a * 18f) * reveal)));
                if (choice.selected) {
                    Renderer2D.COLOR.quad(row.x + 6f, row.y, 2f, row.h - 2f,
                            withAlpha(s.accentBright, Math.round(210f * reveal)));
                }

                String label = ClickGuiRenderer.fitText(fontMedium(), choice.label, 14f, row.w - 52f);
                drawText(fontMedium(), label, row.x + 18f, row.y + 13f, 14f,
                        withAlpha(choice.selected ? s.accentBright : s.text, Math.round(224f * reveal)));
                if (choice.selected) {
                    Renderer2D.COLOR.svg("check", row.x + row.w - 30f, row.y + 12f, 17f, 17f,
                            SvgRenderOptions.overrideColor(withAlpha(s.accentBright, Math.round(220f * reveal))));
                }
            }
        } finally {
            if (scissor) ScissorFunction.pop();
        }

        if (popup.maxScroll() > 0f) {
            float trackH = Math.max(20f, r.h - 12f);
            float handleH = Math.max(28f, trackH * Math.min(1f, trackH / Math.max(trackH, popup.contentHeight())));
            float travel = Math.max(1f, trackH - handleH);
            float y = r.y + 6f + travel * clamp01(popup.scrollCurrent / popup.maxScroll());
            Renderer2D.COLOR.quad(r.x + r.w - 4f, y, 2f, handleH,
                    withAlpha(s.accentBright, Math.round(126f * reveal)));
        }
    }

    private static void applyStringChoice(StringElementWidget widget, int targetIndex) {
        if (!(widget instanceof IrisStringOptionWidgetAccess access)) return;
        int count = Math.max(1, access.combatant$getValueCount());
        int current = access.combatant$getValueIndex();
        if (current == targetIndex) return;

        if (current >= 0) {
            int forward = Math.floorMod(targetIndex - current, count);
            int backward = Math.floorMod(current - targetIndex, count);
            if (backward < forward) {
                for (int i = 0; i < backward; i++) widget.applyPreviousValue();
            } else {
                for (int i = 0; i < forward; i++) widget.applyNextValue();
            }
        } else {
            for (int guard = 0; guard <= count && access.combatant$getValueIndex() != targetIndex; guard++) {
                widget.applyNextValue();
            }
        }
        refreshOptionWidget(widget);
    }

    private static void applyProfileChoice(ProfileElementWidget widget, Profile profile) {
        if (profile == null) return;
        Iris.queueShaderPackOptionsFromProfile(profile);
        refreshOptionWidget(widget);
    }

    private static void refreshOptionWidget(BaseOptionElementWidget<?> widget) {
        try {
            NavigationController navigation = optionNavigation(widget);
            if (navigation != null) navigation.refresh();
        } catch (Throwable ignored) {
        }
    }

    private static String displayStringValue(StringElementWidget widget, String raw) {
        if (raw == null) return "";
        StringOption option = stringOption(widget);
        if (option == null) return LegacyTextUtil.stripLegacy(raw);
        Component value = GuiUtil.translateOrDefault(Component.literal(raw),
                "option." + option.getName() + ".value." + raw);
        // OptiFine-style shader packs, including Photon, use value.<option>.<value>.
        if (raw.equals(value.getString())) {
            value = GuiUtil.translateOrDefault(Component.literal(raw),
                    "value." + option.getName() + "." + raw);
        }
        return LegacyTextUtil.stripLegacy(value.getString());
    }

    private static boolean isInformationalOption(AbstractElementWidget<?> widget) {
        if (!(widget instanceof StringElementWidget string) || widget instanceof SliderElementWidget) return false;
        StringOption option = stringOption(string);
        if (option == null || !"INFO".equalsIgnoreCase(option.getName())) return false;
        List<String> values = option.getAllowedValues();
        if (values == null || values.size() < 2) return false;
        String caption = null;
        for (String raw : values) {
            String display = displayStringValue(string, raw).trim();
            // Only collapse it when all values mean the exact same thing, not for a real enum.
            if (display.isEmpty() || display.equals(raw)) return false;
            if (caption == null) caption = display;
            else if (!caption.equalsIgnoreCase(display)) return false;
        }
        return true;
    }

    private static StringOption stringOption(StringElementWidget widget) {
        Object value = readField(STRING_OPTION_FIELD, widget);
        return value instanceof StringOption option ? option : null;
    }

    private static OptionMenuProfileElement profileElement(ProfileElementWidget widget) {
        Object value = readField(ELEMENT_FIELD, widget);
        return value instanceof OptionMenuProfileElement element ? element : null;
    }

    private static NavigationController optionNavigation(BaseOptionElementWidget<?> widget) {
        Object value = readField(NAVIGATION_FIELD, widget);
        return value instanceof NavigationController navigation ? navigation : null;
    }

    private static Object readField(Field field, Object owner) {
        if (field == null || owner == null) return null;
        try {
            return field.get(owner);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object controlKey(Object widget) {
        synchronized (CONTROL_MOTION_KEYS) {
            return CONTROL_MOTION_KEYS.computeIfAbsent(widget, unused -> new Object());
        }
    }

    private static void drawBackdrop(FixedCanvas c, Style s) {
        // Iris is intentionally more transparent than Sodium: the world/shader output remains legible.
        Renderer2D.COLOR.quad(0f, 0f, c.logicalWidth, c.logicalHeight, 0x3C000000);
    }

    private static void drawShell(Frame f, Style s) {
        Rect r = f.root;
        Renderer2D.COLOR.roundedRectSoftShadow(r.x, r.y + 4f, r.w, r.h,
                6f, 28f, 0.018f, withAlpha(0xFF000000, 76));
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(s.windowTop, 164), withAlpha(s.windowTop, 154),
                withAlpha(s.windowBottom, 176), withAlpha(s.windowBottom, 168));
        Renderer2D.COLOR.quad(r.x, r.y, r.w, HEADER_H,
                withAlpha(s.headerTop, 126), withAlpha(s.headerTop, 118),
                withAlpha(s.headerBottom, 144), withAlpha(s.headerBottom, 136));
        Renderer2D.COLOR.quad(r.x, r.y + HEADER_H, r.w, 2f,
                withAlpha(s.strokeBright, 82), withAlpha(s.stroke, 62),
                withAlpha(s.stroke, 58), withAlpha(s.strokeBright, 72));
        Renderer2D.COLOR.quad(f.details.x - DIVIDER_W, f.content.y, DIVIDER_W, f.content.h,
                withAlpha(s.strokeBright, 74));
        Renderer2D.COLOR.quad(f.details.x, f.details.y, f.details.w, f.details.h,
                withAlpha(s.detailsTop, 94), withAlpha(s.detailsTop, 88),
                withAlpha(s.detailsBottom, 114), withAlpha(s.detailsBottom, 108));
        Renderer2D.COLOR.quad(r.x, r.y + r.h - FOOTER_H, r.w, 2f, withAlpha(s.stroke, 70));
        Renderer2D.COLOR.quad(r.x, r.y, 1f, r.h, withAlpha(s.strokeBright, 60));
        Renderer2D.COLOR.quad(r.x + r.w - 1f, r.y, 1f, r.h, withAlpha(s.stroke, 54));
        Renderer2D.COLOR.quad(r.x, r.y + r.h - 1f, r.w, 1f, withAlpha(s.stroke, 58));
    }

    private static void drawHeader(Frame f,
                                   UiState state,
                                   ShaderPackSelectionList packList,
                                   boolean optionMode,
                                   Button screenSwitchButton,
                                   Button openFolderButton,
                                   OldImageButton showHideButton,
                                   Component notificationDialog,
                                   int notificationDialogTimer,
                                   Style s) {
        drawText(fontBold(), optionMode ? tr("gui.combatant.iris.options") : tr("gui.combatant.iris.shaderpacks"),
                f.root.x + 30f, f.root.y + 25f, 24f, withAlpha(s.text, 244));
        drawText(fontRegular(), optionMode ? tr("gui.combatant.iris.options.subtitle") : tr("gui.combatant.iris.shaderpacks.subtitle"),
                f.root.x + 30f, f.root.y + 57f, 14f, withAlpha(s.muted, 176));

        if (notificationDialog != null && notificationDialogTimer > 0 && !notificationDialog.getString().isBlank()) {
            String notice = LegacyTextUtil.stripLegacy(notificationDialog.getString());
            float nw = Math.min(560f, ClickGuiRenderer.textWidth(fontMedium(), notice, 14f) + 34f);
            float nx = f.root.x + (f.root.w - nw) * 0.5f;
            Renderer2D.COLOR.quad(nx, f.root.y + 25f, nw, 38f,
                    withAlpha(s.control, 176), withAlpha(s.controlHover, 170),
                    withAlpha(s.controlHover, 154), withAlpha(s.control, 160));
            Renderer2D.COLOR.quad(nx, f.root.y + 61f, nw, 2f, withAlpha(s.accentBright, 178));
            drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), notice, 14f, nw - 24f),
                    nx + 12f, f.root.y + 37f, 14f, withAlpha(s.text, 224));
        }

        if (!optionMode) {
            drawIconButton(f.gridButton, "layout-grid", state.gridView, ControlId.GRID, f, s);
            drawIconButton(f.listButton, "rows-3", !state.gridView, ControlId.LIST, f, s);
        }
        if (openFolderButton != null) drawIconButton(f.folderButton, "folder-open", false, ControlId.FOLDER, f, s);
        if (screenSwitchButton != null && f.switchButton != null) {
            drawIconButton(f.switchButton, "settings-2", optionMode, ControlId.OPTIONS, f, s);
        }
        if (showHideButton != null && f.hideButton != null) {
            drawIconButton(f.hideButton, "eye", false, ControlId.HIDE, f, s);
        }

        if (!optionMode) {
            ShaderPackSelectionList.TopButtonRowEntry top = packList.getTopButtonRow();
            if (top != null && f.shaderToggle != null) {
                boolean enabled = top.shadersEnabled;
                boolean hover = f.shaderToggle.contains(f.canvas.mouseX, f.canvas.mouseY);
                if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
                Motion m = motion(top, hover, enabled);
                float h = AnimationUtility.easeOutCubic(m.hover);
                float a = AnimationUtility.easeOutCubic(m.active);
                int bg = mix(s.control, s.controlHover, h * 0.58f + a * 0.17f + m.press * 0.20f);
                Renderer2D.COLOR.quad(f.shaderToggle.x, f.shaderToggle.y, f.shaderToggle.w, f.shaderToggle.h,
                        withAlpha(bg, 178), withAlpha(bg, 172), withAlpha(bg, 160), withAlpha(bg, 164));
                drawText(fontMedium(), tr("gui.combatant.iris.shader-toggle"),
                        f.shaderToggle.x + 12f, f.shaderToggle.y + 14f, 14f, withAlpha(s.text, 236));
                drawBooleanToggle(f.shaderToggle.x + f.shaderToggle.w - 53f,
                        f.shaderToggle.y + 11f, 42f, 22f, m, s);
            }
        }
    }

    private static void drawPackBrowser(Frame f, UiState state, ShaderPackSelectionList packList, Style s) {
        drawPackToolbar(f, state, s);
        boolean scissor = ScissorFunction.pushRaw(f.viewport.x, f.viewport.y, f.viewport.w, f.viewport.h);
        try {
            PackSlot hovered = null;
            for (PackSlot slot : f.packSlots) {
                if (!slot.rect.intersects(f.viewport)) continue;
                boolean hover = slot.rect.contains(f.canvas.mouseX, f.canvas.mouseY);
                if (hover) hovered = slot;
                drawPackCard(slot, hover, state.gridView, s);
            }
            if (hovered != null) {
                state.hoveredPackName = hovered.entry.getPackName();
                SystemCursor.set(SystemCursor.CursorType.HAND);
            } else {
                state.hoveredPackName = null;
            }
        } finally {
            if (scissor) ScissorFunction.pop();
        }
        drawScrollbar(f, state.packScroll, s);
        boolean detailsClip = ScissorFunction.pushRaw(f.details.x, f.details.y, f.details.w, f.details.h);
        try {
            drawPackDetails(f, state, packList, s);
        } finally {
            if (detailsClip) ScissorFunction.pop();
        }
    }

    private static void drawPackToolbar(Frame f, UiState state, Style s) {
        drawText(fontMedium(), tr("gui.combatant.iris.available"), f.main.x + 22f, f.main.y + 23f, 16f,
                withAlpha(s.text, 222));
        drawText(fontRegular(), Integer.toString(f.packSlots.size()), f.main.x + 122f, f.main.y + 23f, 16f,
                withAlpha(s.accentBright, 226));
        if (f.downloadButton != null) drawSmallToolbarButton(f.downloadButton, "download",
                tr("gui.combatant.iris.getpacks"), ControlId.DOWNLOAD, f, s);
    }

    private static void drawPackCard(PackSlot slot, boolean hover, boolean grid, Style s) {
        Motion m = motion(slot.entry, hover, slot.entry.isSelected());
        float h = AnimationUtility.easeOutCubic(m.hover);
        float a = AnimationUtility.easeOutCubic(m.active);
        float click = AnimationUtility.easeOutCubic(m.press);
        Rect r = slot.rect;
        int bgA = mix(s.cardTop, s.cardHover, h * 0.64f + a * 0.13f);
        int bgB = mix(s.cardBottom, s.cardHover, h * 0.44f + a * 0.10f);
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(bgA, Math.round(162f + click * 38f)), withAlpha(bgA, 158),
                withAlpha(bgB, 173), withAlpha(bgB, Math.round(165f + click * 25f)));
        if (slot.entry.isSelected()) {
            Renderer2D.COLOR.roundedRectStroke(r.x + 1f, r.y + 1f, r.w - 2f, r.h - 2f,
                    4f, 1.5f, withAlpha(s.accentBright, Math.round(92f + a * 106f + click * 46f)));
            Renderer2D.COLOR.quad(r.x, r.y, 4f, r.h,
                    withAlpha(s.accent, 212), withAlpha(s.accentBright, 224),
                    withAlpha(s.accentBright, 224), withAlpha(s.accent, 212));
        } else if (h > 0.001f) {
            Renderer2D.COLOR.quad(r.x, r.y, 2f, r.h, withAlpha(s.accentSoft, Math.round(88f * h)));
        }

        float iconSize = grid ? 42f : 34f;
        float iconX = r.x + 20f;
        float iconY = r.y + (r.h - iconSize) * 0.5f;
        Renderer2D.COLOR.svg("package", iconX, iconY, iconSize, iconSize,
                SvgRenderOptions.overrideColor(withAlpha(slot.entry.isApplied() ? s.accentBright : s.muted,
                        slot.entry.isApplied() ? 224 : Math.round(164f + h * 46f))));

        String name = LegacyTextUtil.stripLegacy(slot.entry.getPackName());
        float textX = iconX + iconSize + 18f;
        PatchSummary patch = patchSummary(slot.entry.getPackName());

        if (grid) {
            float nameSize = 18f;
            float maxW = r.w - (textX - r.x) - 28f;
            String fit = ClickGuiRenderer.fitText(fontMedium(), name, nameSize, maxW);
            drawText(fontMedium(), fit, textX, r.y + 23f, nameSize, withAlpha(s.text, 238));
            drawStatusText(patch, textX, r.y + 58f, s);
            if (slot.entry.isSelected()) {
                Renderer2D.COLOR.svg("check", r.x + r.w - 43f, r.y + 19f, 19f, 19f,
                        SvgRenderOptions.overrideColor(withAlpha(s.accentBright, 228)));
            }
            if (slot.entry.isApplied()) {
                drawText(fontMedium(), tr("gui.combatant.iris.applied"), textX,
                        r.y + r.h - 28f, 13f, withAlpha(s.accentBright, 206));
            } else if (slot.entry.isSelected()) {
                drawText(fontMedium(), tr("gui.combatant.iris.selected.pending"), textX,
                        r.y + r.h - 28f, 13f, withAlpha(s.text, 184));
            }
            return;
        }

        String stateLabel = slot.entry.isSelected() && !slot.entry.isApplied()
                ? tr("gui.combatant.iris.selected.pending")
                : slot.entry.isApplied() ? tr("gui.combatant.iris.applied") : "";
        float rightPad = 22f;
        float metaGap = 20f;
        float stateBudget = stateLabel.isEmpty() ? 0f : 185f;
        float contentRight = r.x + r.w - rightPad;
        float stateCellX = contentRight - stateBudget;
        float leftBudget = Math.max(80f, (stateBudget > 0f ? stateCellX - metaGap : contentRight) - textX);

        String fitName = ClickGuiRenderer.fitText(fontMedium(), name, 17f, leftBudget);
        drawText(fontMedium(), fitName, textX, r.y + 15f, 17f, withAlpha(s.text, 238));

        String patchLabel = ClickGuiRenderer.fitText(fontRegular(), patch.shortLabel, 13f, leftBudget);
        drawStatusText(new PatchSummary(patch.kind, patchLabel, patch.profile, patch.state), textX, r.y + 47f, s);

        if (!stateLabel.isEmpty()) {
            String fitState = ClickGuiRenderer.fitText(fontMedium(), stateLabel, 14f, stateBudget - 24f);
            float stateW = ClickGuiRenderer.textWidth(fontMedium(), fitState, 14f);
            float stateX = stateCellX + stateBudget - stateW;
            Renderer2D.COLOR.quad(stateCellX - metaGap * 0.5f, r.y + 16f, 1f, r.h - 32f, withAlpha(s.stroke, 54));
            drawText(fontMedium(), fitState, stateX, r.y + 31f, 14f,
                    withAlpha(s.accentBright, 238));
            if (slot.entry.isSelected()) Renderer2D.COLOR.svg("check", stateCellX + 4f, r.y + 30f, 15f, 15f,
                    SvgRenderOptions.overrideColor(withAlpha(s.accentBright, 226)));
        }
    }

    private static void drawStatusText(PatchSummary patch, float x, float y, Style s) {
        int color = switch (patch.kind) {
            case COMPLETE -> s.good;
            case PARTIAL -> s.warn;
            case REJECTED -> s.bad;
            case PROFILE -> s.accentBright;
            case NONE -> s.muted;
        };
        drawText(fontRegular(), patch.shortLabel, x, y, 13f, withAlpha(color, 212));
    }

    private static void drawPackDetails(Frame f, UiState state, ShaderPackSelectionList packList, Style s) {
        ShaderPackSelectionList.ShaderPackEntry entry = selectedOrApplied(packList);
        if (entry == null) entry = findPack(packList, state.selectedPackName);
        float x = f.details.x + 28f;
        float y = f.details.y + 28f;
        float w = f.details.w - 56f;

        if (entry == null) {
            drawText(fontBold(), tr("gui.combatant.iris.details"), x, y, 21f, withAlpha(s.text, 230));
            drawText(fontRegular(), tr("gui.combatant.iris.details.empty"), x, y + 40f, 14f, withAlpha(s.muted, 170));
            return;
        }

        String name = LegacyTextUtil.stripLegacy(entry.getPackName());
        drawText(fontBold(), ClickGuiRenderer.fitText(fontBold(), name, 22f, w), x, y, 22f, withAlpha(s.text, 248));
        y += 38f;
        boolean pending = entry.isSelected() && !entry.isApplied();
        boolean shadersEnabled = packList.getTopButtonRow() != null && packList.getTopButtonRow().shadersEnabled;
        String selectedState = pending ? tr("gui.combatant.iris.selection.pending")
                : entry.isApplied() && !shadersEnabled ? tr("gui.combatant.iris.selection.applied.disabled")
                : entry.isApplied() ? tr("gui.combatant.iris.selection.applied")
                : tr("gui.combatant.iris.selection.available");
        int stateColor = pending ? s.warn : entry.isApplied() ? s.good : s.muted;
        Renderer2D.COLOR.roundedRect(x, y, w, 45f, 5f, withAlpha(s.control, 196));
        Renderer2D.COLOR.svg(pending ? "clock-3" : entry.isApplied() ? "check" : "circle-question-mark",
                x + 14f, y + 13f, 19f, 19f,
                SvgRenderOptions.overrideColor(withAlpha(stateColor, 240)));
        drawText(fontMedium(), selectedState, x + 44f, y + 13f, 16f, withAlpha(s.text, 236));
        y += 66f;
        PatchSummary patch = patchSummary(entry.getPackName());
        drawText(fontMedium(), patch.shortLabel, x, y, 16f, withAlpha(s.text, 224));
        y += 38f;
        Renderer2D.COLOR.quad(x, y, w, 1f, withAlpha(s.stroke, 62));
        y += 22f;

        if (!patch.profile.matched()) {
            drawWrapped(tr("gui.combatant.iris.patch.none.body"), x, y, w, 16f, s.muted, 6);
            return;
        }

        infoPair(x, y, w, tr("gui.combatant.iris.patch.profile"), patch.profile.family() + " / " + patch.profile.profileId(), s);
        y += 40f;
        infoPair(x, y, w, tr("gui.combatant.iris.patch.targets"),
                patch.state.appliedTargets() + " / " + patch.state.expectedTargets(), s);
        y += 40f;
        ShaderPatchEngine.ShaderpackIntegration integration = patch.profile.integration();
        infoPair(x, y, w, "TAA", yesNo(integration.taaReplacement()), s);
        y += 32f;
        infoPair(x, y, w, "MSAA", yesNo(integration.msaaReplacement()), s);
        y += 32f;
        infoPair(x, y, w, tr("gui.combatant.iris.patch.temporal"),
                integration.hasTemporalMapping() ? integration.temporalPass() : tr("gui.combatant.iris.no"), s);
        y += 32f;
        infoPair(x, y, w, tr("gui.combatant.iris.patch.aa_hooks"), Integer.toString(integration.aaOptions().size()), s);
        y += 30f;
        infoPair(x, y, w, tr("gui.combatant.iris.patch.motion_blur_hooks"), Integer.toString(integration.motionBlurOptions().size()), s);
        y += 30f;
        infoPair(x, y, w, tr("gui.combatant.iris.patch.dof_hooks"), Integer.toString(integration.depthOfFieldOptions().size()), s);
        y += 30f;
        infoPair(x, y, w, tr("gui.combatant.iris.patch.postfx_hooks"), Integer.toString(integration.postFxOptions().size()), s);
        y += 38f;

        Renderer2D.COLOR.quad(x, y, w, 1f, withAlpha(s.stroke, 56));
        y += 20f;
        drawText(fontMedium(), tr("gui.combatant.iris.patch.hooks"), x, y, 15f, withAlpha(s.text, 210));
        y += 30f;
        ArrayList<String> hooks = new ArrayList<>();
        if (integration.preserveHistoryAlphaOrigin()) hooks.add("History alpha origin");
        if (!integration.msaaPackedTargets().isEmpty()) hooks.add("MSAA packed targets: " + integration.msaaPackedTargets().size());
        if (!integration.msaaForwardTargets().isEmpty()) hooks.add("MSAA forward targets: " + integration.msaaForwardTargets().size());
        hooks.addAll(patch.profile.features());
        if (hooks.isEmpty()) hooks.add(tr("gui.combatant.iris.patch.hooks.none"));
        for (String hook : hooks) {
            Renderer2D.COLOR.quad(x, y + 7f, 5f, 5f, withAlpha(s.accentSoft, 178));
            drawText(fontRegular(), ClickGuiRenderer.fitText(fontRegular(), hook, 16f, w - 18f),
                    x + 16f, y, 16f, withAlpha(s.muted, 220));
            y += 31f;
            if (y > f.details.y + f.details.h - 52f) break;
        }
    }

    private static void drawOptionBrowser(Frame f, UiState state, Style s) {
        drawText(fontMedium(), tr("gui.combatant.iris.options.current"), f.main.x + 22f, f.main.y + 23f, 16f,
                withAlpha(s.text, 218));
        boolean popupOpen = state.selectPopup != null;
        boolean scissor = ScissorFunction.pushRaw(f.viewport.x, f.viewport.y, f.viewport.w, f.viewport.h);
        AbstractElementWidget<?> hovered = null;
        try {
            for (HeaderSlot header : f.headerSlots) {
                if (!header.rect.intersects(f.viewport)) continue;
                drawOptionHeader(header, f, s, !popupOpen);
            }
            for (OptionSlot slot : f.optionSlots) {
                if (!slot.rect.intersects(f.viewport)) continue;
                boolean hover = !popupOpen && slot.rect.contains(f.canvas.mouseX, f.canvas.mouseY);
                if (hover) hovered = slot.widget;
                drawOptionCard(slot, hover, state, s);
            }
        } finally {
            if (scissor) ScissorFunction.pop();
        }
        if (hovered != null) SystemCursor.set(hovered instanceof SliderElementWidget
                ? SystemCursor.CursorType.RESIZE_HORIZONTAL : SystemCursor.CursorType.HAND);
        drawScrollbar(f, state.optionScroll, s);
        if (hovered != null) state.focusedOption = hovered;
        if (!f.optionSlots.isEmpty() && (state.focusedOption == null
                || f.optionSlots.stream().noneMatch(slot -> slot.widget == state.focusedOption))) {
            state.focusedOption = f.optionSlots.get(0).widget;
        }
        boolean detailsClip = ScissorFunction.pushRaw(f.details.x, f.details.y, f.details.w, f.details.h);
        try {
            drawOptionDetails(f, popupOpen ? state.selectPopup.widget : state.focusedOption, state, s);
        } finally {
            if (detailsClip) ScissorFunction.pop();
        }
    }

    private static void drawOptionHeader(HeaderSlot slot, Frame f, Style s, boolean interactive) {
        Rect r = slot.rect;
        Renderer2D.COLOR.quad(r.x, r.y + r.h - 1f, r.w, 1f,
                withAlpha(s.strokeBright, 64), withAlpha(s.stroke, 52), withAlpha(s.stroke, 48), withAlpha(s.strokeBright, 58));
        boolean hasBack = slot.actions.stream().anyMatch(action -> "chevron-left".equals(action.icon));
        float titleX = r.x + (hasBack ? 108f : 12f);
        float titleW = Math.max(80f, r.w - (titleX - r.x) - 142f);
        drawText(fontBold(), ClickGuiRenderer.fitText(fontBold(), slot.title, 18f, titleW),
                titleX, r.y + 18f, 18f, withAlpha(s.text, 224));
        for (HeaderAction action : slot.actions) drawHeaderAction(action, f, s, interactive);
    }

    private static void drawHeaderAction(HeaderAction action, Frame f, Style s, boolean interactive) {
        boolean hover = interactive && action.rect.contains(f.canvas.mouseX, f.canvas.mouseY);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
        Motion m = motion(action.listener, hover, false);
        float h = AnimationUtility.easeOutCubic(m.hover);
        int bg = mix(s.control, s.controlHover, h * 0.68f + m.press * 0.28f);
        Renderer2D.COLOR.quad(action.rect.x, action.rect.y, action.rect.w, action.rect.h,
                withAlpha(bg, 152), withAlpha(bg, 148), withAlpha(bg, 138), withAlpha(bg, 140));
        if (action.icon != null) {
            Renderer2D.COLOR.svg(action.icon, action.rect.x + 10f, action.rect.y + 9f, 18f, 18f,
                    SvgRenderOptions.overrideColor(withAlpha(hover ? s.accentBright : s.muted, 212)));
        }
        if (action.label != null && !action.label.isBlank()) {
            drawText(fontMedium(), action.label, action.rect.x + (action.icon == null ? 10f : 36f), action.rect.y + 10f,
                    13f, withAlpha(s.text, 204));
        }
    }

    private static void drawOptionCard(OptionSlot slot, boolean hover, UiState state, Style s) {
        boolean popupForSlot = state.selectPopup != null && state.selectPopup.widget == slot.widget;
        boolean booleanControl = slot.widget instanceof BooleanElementWidget;
        boolean booleanOn = booleanControl && "true".equalsIgnoreCase(((BooleanElementWidget) slot.widget).getValue());
        Motion m = motion(slot.widget, hover, (booleanControl && booleanOn) || popupForSlot);
        float h = AnimationUtility.easeOutCubic(m.hover);
        float a = AnimationUtility.easeInOutCubic(m.active);
        updateValueMotion(m, slot.value);
        float pulse = AnimationUtility.easeOutCubic(Math.max(m.press, m.change * 0.55f));
        Rect r = slot.rect;
        int top = mix(s.cardTop, s.cardHover, h * 0.58f + a * 0.08f);
        int bottom = mix(s.cardBottom, s.cardHover, h * 0.42f + a * 0.06f);
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(top, Math.round(157f + pulse * 35f)), withAlpha(top, 151),
                withAlpha(bottom, 170), withAlpha(bottom, Math.round(159f + pulse * 22f)));
        if (slot.modified) Renderer2D.COLOR.quad(r.x, r.y, 3f, r.h, withAlpha(s.accent, 182));
        if (pulse > 0.005f) Renderer2D.COLOR.roundedRectStroke(r.x + 1f, r.y + 1f, r.w - 2f, r.h - 2f,
                4f, 1f, withAlpha(s.accentBright, Math.round(pulse * 146f)));

        boolean discrete = (slot.widget instanceof StringElementWidget && !(slot.widget instanceof SliderElementWidget))
                || slot.widget instanceof ProfileElementWidget;
        float labelBudget = discrete ? r.w * 0.50f : r.w * 0.55f;
        drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), slot.label, 15f, labelBudget),
                r.x + 18f, r.y + 18f, 15f, withAlpha(s.text, 226));

        if (booleanControl) {
            drawBooleanToggle(r.x + r.w - 86f, r.y + (r.h - 30f) * 0.5f, 64f, 30f, m, s);
            return;
        }

        if (discrete) {
            float controlW = Math.min(238f, r.w * 0.42f);
            Rect control = new Rect(r.x + r.w - controlW - 14f, r.y + 11f, controlW, 46f);
            Motion cm = motion(controlKey(slot.widget), hover || popupForSlot, popupForSlot);
            float ch = AnimationUtility.easeOutCubic(cm.hover);
            float ca = AnimationUtility.easeOutCubic(cm.active);
            int cbg = mix(s.control, s.controlHover, ch * 0.72f + ca * 0.16f);
            Renderer2D.COLOR.quad(control.x, control.y, control.w, control.h,
                    withAlpha(cbg, 168), withAlpha(cbg, 164), withAlpha(cbg, 150), withAlpha(cbg, 154));
            if (popupForSlot) {
                Renderer2D.COLOR.quad(control.x, control.y + control.h - 2f, control.w, 2f,
                        withAlpha(s.accentBright, Math.round(150f + ca * 72f)));
            }
            String fit = ClickGuiRenderer.fitText(fontMedium(), slot.value, 14f, control.w - 48f);
            drawText(fontMedium(), fit, control.x + 14f, control.y + 15f, 14f,
                    withAlpha(slot.modified ? s.accentBright : s.text, Math.round(224f + m.change * 30f)));
            Renderer2D.COLOR.svg("chevron-down", control.x + control.w - 30f, control.y + 14f, 17f, 17f,
                    SvgRenderOptions.overrideColor(withAlpha(popupForSlot ? s.accentBright : s.muted, 210)));
            return;
        }

        if (!slot.value.isBlank()) {
            float valueW = ClickGuiRenderer.textWidth(fontMedium(), slot.value, 14f);
            drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), slot.value, 14f, r.w * 0.34f),
                    r.x + r.w - Math.min(valueW, r.w * 0.34f) - 18f, r.y + 18f, 14f,
                    withAlpha(slot.modified ? s.accentBright : s.muted, 210));
        }

        if (slot.widget instanceof SliderElementWidget && slot.widget instanceof IrisStringOptionWidgetAccess slider) {
            float trackX = r.x + 18f;
            float trackY = r.y + r.h - 20f;
            float trackW = r.w - 36f;
            int count = Math.max(1, slider.combatant$getValueCount());
            float ratio = count <= 1 ? 0f : slider.combatant$getValueIndex() / (float) (count - 1);
            if (!m.progressInitialized) { m.progress = ratio; m.progressInitialized = true; }
            m.progress = AnimationUtility.approach(m.progress, ratio, Math.max(0.001f, AnimationUtility.deltaTime()), 16f);
            float easedRatio = clamp01(m.progress);
            Renderer2D.COLOR.quad(trackX, trackY, trackW, 3f, withAlpha(s.strokeBright, 62));
            Renderer2D.COLOR.quad(trackX, trackY, trackW * easedRatio, 3f,
                    withAlpha(s.accent, 194), withAlpha(s.accentBright, 205),
                    withAlpha(s.accentBright, 205), withAlpha(s.accent, 194));
            Renderer2D.COLOR.quad(trackX + trackW * easedRatio - 3f, trackY - 4f, 6f, 11f,
                    withAlpha(s.accentBright, 224));
        } else if (slot.widget instanceof LinkElementWidget) {
            Renderer2D.COLOR.svg("chevron-right", r.x + r.w - 34f, r.y + 18f, 18f, 18f,
                    SvgRenderOptions.overrideColor(withAlpha(hover ? s.accentBright : s.muted, 206)));
        }
    }

    private static void drawBooleanToggle(float sx, float sy, float sw, float sh, Motion m, Style s) {
        float h = AnimationUtility.easeOutCubic(m.hover);
        float a = AnimationUtility.easeInOutCubic(m.active);
        float pulse = AnimationUtility.easeOutCubic(m.press);
        int off = mix(s.control, s.controlHover, h * 0.45f);
        int track = mix(off, s.accent, a * 0.78f);
        Renderer2D.COLOR.roundedRect(sx, sy, sw, sh, sh * 0.5f,
                withAlpha(track, Math.round(196f + a * 46f)));
        Renderer2D.COLOR.roundedRectStroke(sx, sy, sw, sh, sh * 0.5f, 1f,
                withAlpha(mix(s.strokeBright, s.accentBright, a),
                        Math.round(100f + h * 50f + pulse * 55f)));
        float knob = sh - 8f;
        float knobX = sx + 4f + (sw - knob - 8f) * a;
        Renderer2D.COLOR.circle(knobX + knob * 0.5f, sy + sh * 0.5f,
                (knob + pulse * 1.8f) * 0.5f, withAlpha(0xFFF2F4F8, 248));
    }

    private static void drawOptionDetails(Frame f, AbstractElementWidget<?> hovered, UiState state, Style s) {
        float x = f.details.x + 28f;
        float y = f.details.y + 28f;
        float w = f.details.w - 56f;
        drawText(fontBold(), tr("gui.combatant.iris.option.info"), x, y, 21f, withAlpha(s.text, 232));
        y += 48f;
        if (!(hovered instanceof CommentedElementWidget<?> commented)) {
            drawWrapped(tr("gui.combatant.iris.option.info.empty"), x, y, w, 16f, s.muted, 7);
            return;
        }
        Component title = commented.getCommentTitle().orElse(Component.empty());
        Component body = commented.getCommentBody().orElse(Component.empty());
        if (!title.getString().isBlank()) {
            drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), LegacyTextUtil.stripLegacy(title.getString()), 17f, w),
                    x, y, 17f, withAlpha(s.accentBright, 232));
            y += 34f;
        }
        if (state.selectPopup != null && state.selectPopup.hoveredChoice != null) {
            String mode = state.selectPopup.hoveredChoice.label;
            drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), mode, 14f, w),
                    x, y, 14f, withAlpha(s.text, 206));
            y += 30f;
        }
        Renderer2D.COLOR.quad(x, y, w, 1f, withAlpha(s.stroke, 58));
        y += 20f;
        drawWrapped(LegacyTextUtil.stripLegacy(body.getString()), x, y, w, 16f, s.text, 16);
    }

    private static void drawFooter(Frame f, ShaderPackScreen screen, boolean optionMode, Style s) {
        drawFooterButton(f.cancelButton, CommonComponents.GUI_CANCEL.getString(), false, ControlId.CANCEL, f, s);
        drawFooterButton(f.applyButton, Component.translatable("options.iris.apply").getString(), true, ControlId.APPLY, f, s);
        drawFooterButton(f.doneButton, CommonComponents.GUI_DONE.getString(), false, ControlId.DONE, f, s);
        String hint = optionMode ? tr("gui.combatant.iris.option.hint")
                : tr("gui.combatant.iris.selection.hint");
        drawText(fontRegular(), LegacyTextUtil.stripLegacy(hint), f.root.x + 26f,
                f.root.y + f.root.h - 31f, 13f, withAlpha(s.muted, 150));
    }

    private static void drawFooterButton(Rect r, String label, boolean accent, ControlId id, Frame f, Style s) {
        if (r == null) return;
        boolean hover = r.contains(f.canvas.mouseX, f.canvas.mouseY);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
        Motion m = motion(id, hover, accent);
        float h = AnimationUtility.easeOutCubic(m.hover);
        int bg = mix(s.control, accent ? s.accentSoft : s.controlHover,
                accent ? 0.18f + h * 0.25f + m.press * 0.20f : h * 0.66f + m.press * 0.28f);
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(bg, 184), withAlpha(bg, 178), withAlpha(bg, 166), withAlpha(bg, 170));
        if (accent) Renderer2D.COLOR.quad(r.x, r.y + r.h - 3f, r.w, 3f, withAlpha(s.accentBright, 210));
        if (m.press > 0.01f) Renderer2D.COLOR.roundedRectStroke(r.x, r.y, r.w, r.h, 3f,
                1f, withAlpha(s.accentBright, Math.round(145f * m.press)));
        float tw = ClickGuiRenderer.textWidth(fontMedium(), label, 14f);
        drawText(fontMedium(), label, r.x + (r.w - tw) * 0.5f, r.y + 15f, 14f,
                withAlpha(hover || accent ? s.text : s.muted, 224));
    }

    private static void drawIconButton(Rect r, String icon, boolean active, ControlId id, Frame f, Style s) {
        if (r == null) return;
        boolean hover = r.contains(f.canvas.mouseX, f.canvas.mouseY);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
        Motion m = motion(id, hover, active);
        float h = AnimationUtility.easeOutCubic(m.hover);
        float a = AnimationUtility.easeOutCubic(m.active);
        int bg = mix(s.control, s.controlHover, h * 0.62f + a * 0.14f + m.press * 0.35f);
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(bg, 166), withAlpha(bg, 160), withAlpha(bg, 150), withAlpha(bg, 154));
        if (active) Renderer2D.COLOR.quad(r.x, r.y + r.h - 2f, r.w, 2f, withAlpha(s.accentBright, 190));
        Renderer2D.COLOR.svg(icon, r.x + 13f, r.y + 13f, r.w - 26f, r.h - 26f,
                SvgRenderOptions.overrideColor(withAlpha(active ? s.accentBright : (hover ? s.text : s.muted), 222)));
    }

    private static void drawSmallToolbarButton(Rect r, String icon, String label, ControlId id, Frame f, Style s) {
        boolean hover = r.contains(f.canvas.mouseX, f.canvas.mouseY);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
        Motion m = motion(id, hover, false);
        float h = AnimationUtility.easeOutCubic(m.hover);
        int bg = mix(s.control, s.controlHover, h * 0.65f + m.press * 0.28f);
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(bg, 154), withAlpha(bg, 150), withAlpha(bg, 140), withAlpha(bg, 142));
        Renderer2D.COLOR.svg(icon, r.x + 12f, r.y + 9f, 18f, 18f,
                SvgRenderOptions.overrideColor(withAlpha(hover ? s.accentBright : s.muted, 214)));
        drawText(fontMedium(), label, r.x + 38f, r.y + 10f, 13f, withAlpha(s.text, 208));
    }

    private static void drawHiddenButton(Frame f, Style s) {
        if (f.hideButton == null) return;
        boolean hover = f.hideButton.contains(f.canvas.mouseX, f.canvas.mouseY);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
        Motion m = motion(ControlId.HIDE, hover, false);
        float h = AnimationUtility.easeOutCubic(m.hover);
        Renderer2D.COLOR.quad(f.hideButton.x, f.hideButton.y, f.hideButton.w, f.hideButton.h,
                withAlpha(mix(s.control, s.controlHover, h * 0.70f), 176));
        Renderer2D.COLOR.svg("eye", f.hideButton.x + 12f, f.hideButton.y + 12f, 24f, 24f,
                SvgRenderOptions.overrideColor(withAlpha(hover ? s.accentBright : s.text, 228)));
    }

    private static void drawScrollbar(Frame f, ScrollState state, Style s) {
        if (f.scrollbar == null || f.contentHeight <= f.viewport.h + 1f) return;
        boolean hover = f.scrollbar.contains(f.canvas.mouseX, f.canvas.mouseY) || state.dragging;
        if (hover) SystemCursor.set(SystemCursor.CursorType.SCROLL);
        Renderer2D.COLOR.quad(f.scrollbar.x, f.scrollbar.y, f.scrollbar.w, f.scrollbar.h,
                withAlpha(s.scrollTrack, hover ? 86 : 54));
        float hh = scrollbarHandleHeight(f.scrollbar, f.contentHeight, f.viewport.h);
        float hy = scrollbarHandleY(f.scrollbar, state.current, f.contentHeight, f.viewport.h);
        Renderer2D.COLOR.quad(f.scrollbar.x, hy, f.scrollbar.w, hh,
                withAlpha(hover ? s.accentSoft : s.scrollHandle, hover ? 170 : 116));
    }

    private static Frame buildFrame(FixedCanvas canvas,
                                    ShaderPackScreen screen,
                                    UiState state,
                                    ShaderPackSelectionList packList,
                                    ShaderPackOptionList optionList,
                                    boolean optionMode,
                                    boolean canHide) {
        float ox = canvas.originX + (CANVAS_W - ROOT_W) * 0.5f;
        float oy = canvas.originY + (CANVAS_H - ROOT_H) * 0.5f;
        Rect root = new Rect(ox, oy, ROOT_W, ROOT_H);
        Rect content = new Rect(ox, oy + HEADER_H, ROOT_W, ROOT_H - HEADER_H - FOOTER_H);
        float mainW = ROOT_W - DETAILS_W - DIVIDER_W;
        Rect main = new Rect(content.x, content.y, mainW, content.h);
        Rect details = new Rect(content.x + mainW + DIVIDER_W, content.y, DETAILS_W, content.h);
        Rect viewport = new Rect(main.x + 18f, main.y + TOOLBAR_H, main.w - 46f, main.h - TOOLBAR_H - 18f);
        Rect scrollbar = new Rect(main.x + main.w - 18f, viewport.y + 4f, SCROLL_W, viewport.h - 8f);

        Rect grid = optionMode ? null : new Rect(root.x + root.w - 316f, root.y + 20f, 48f, 48f);
        Rect list = optionMode ? null : new Rect(root.x + root.w - 258f, root.y + 20f, 48f, 48f);
        Rect folder = new Rect(root.x + root.w - 200f, root.y + 20f, 48f, 48f);
        Rect switchButton = new Rect(root.x + root.w - 142f, root.y + 20f, 48f, 48f);
        Rect hide = canHide ? new Rect(root.x + root.w - 84f, root.y + 20f, 48f, 48f) : null;
        Rect shaderToggle = optionMode ? null : new Rect(main.x + main.w - 218f, main.y + 14f, 182f, 44f);

        GuiEventListener download = findPinned(packList);
        Rect downloadButton = (!optionMode && download != null)
                ? new Rect(main.x + 178f, main.y + 15f, 150f, 42f) : null;

        Rect done = new Rect(root.x + root.w - 184f, root.y + root.h - 62f, 158f, 44f);
        Rect apply = new Rect(done.x - 170f, done.y, 158f, 44f);
        Rect cancel = new Rect(apply.x - 170f, done.y, 158f, 44f);

        ArrayList<PackSlot> packSlots = new ArrayList<>();
        ArrayList<OptionSlot> optionSlots = new ArrayList<>();
        ArrayList<HeaderSlot> headerSlots = new ArrayList<>();
        ArrayList<HeaderAction> headerActions = new ArrayList<>();
        float contentHeight;

        if (optionMode && optionList != null) {
            state.optionScroll.clamp(100000f, viewport.h);
            state.optionScroll.tick();
            contentHeight = buildOptionSlots(optionList, viewport, state.optionScroll.current,
                    optionSlots, headerSlots, headerActions);
            state.optionScroll.clamp(contentHeight, viewport.h);
        } else {
            state.packScroll.clamp(100000f, viewport.h);
            state.packScroll.tick();
            contentHeight = buildPackSlots(packList, viewport, state.packScroll.current, state.gridView, packSlots);
            state.packScroll.clamp(contentHeight, viewport.h);
        }

        updateScrollbarDrag(optionMode ? state.optionScroll : state.packScroll,
                canvas.mouseY, scrollbar, contentHeight, viewport.h);
        return new Frame(canvas, root, content, main, details, viewport, scrollbar,
                grid, list, folder, switchButton, hide, shaderToggle, downloadButton, download,
                done, apply, cancel, packSlots, optionSlots, headerSlots, headerActions,
                contentHeight, optionMode, false);
    }

    private static float buildPackSlots(ShaderPackSelectionList packList,
                                        Rect viewport,
                                        float scroll,
                                        boolean grid,
                                        List<PackSlot> out) {
        ArrayList<ShaderPackSelectionList.ShaderPackEntry> entries = new ArrayList<>();
        for (var child : packList.children()) {
            if (child instanceof ShaderPackSelectionList.ShaderPackEntry entry) entries.add(entry);
        }
        if (grid) {
            float gap = 14f;
            float cardW = (viewport.w - gap) * 0.5f;
            float cardH = 126f;
            for (int i = 0; i < entries.size(); i++) {
                int col = i & 1;
                int row = i >> 1;
                float x = viewport.x + col * (cardW + gap);
                float y = viewport.y + row * (cardH + gap) - scroll;
                out.add(new PackSlot(entries.get(i), new Rect(x, y, cardW, cardH)));
            }
            int rows = (entries.size() + 1) / 2;
            return Math.max(0f, rows * (cardH + gap) - gap + 10f);
        }
        float cardH = 78f;
        float gap = 10f;
        for (int i = 0; i < entries.size(); i++) {
            float y = viewport.y + i * (cardH + gap) - scroll;
            out.add(new PackSlot(entries.get(i), new Rect(viewport.x, y, viewport.w, cardH)));
        }
        return Math.max(0f, entries.size() * (cardH + gap) - gap + 10f);
    }

    private static float buildOptionSlots(ShaderPackOptionList optionList,
                                          Rect viewport,
                                          float scroll,
                                          List<OptionSlot> options,
                                          List<HeaderSlot> headers,
                                          List<HeaderAction> allActions) {
        float cursor = 0f;
        float gap = 12f;
        float cardH = 76f;
        float colGap = 12f;
        float cardW = (viewport.w - colGap) * 0.5f;

        for (var child : optionList.children()) {
            if (child instanceof ShaderPackOptionList.HeaderEntry header) {
                if (cursor > 0f) cursor += 16f;
                Rect r = new Rect(viewport.x, viewport.y + cursor - scroll, viewport.w, 58f);
                String title = header instanceof IrisHeaderEntryAccess access && access.combatant$getText() != null
                        ? LegacyTextUtil.stripLegacy(access.combatant$getText().getString()) : "";
                ArrayList<HeaderAction> actions = headerActions(header, r);
                headers.add(new HeaderSlot(header, r, title, actions));
                allActions.addAll(actions);
                cursor += 66f;
                continue;
            }
            if (!(child instanceof ShaderPackOptionList.ElementRowEntry row)) continue;
            ArrayList<AbstractElementWidget<?>> widgets = new ArrayList<>();
            for (GuiEventListener listener : row.children()) {
                if (listener instanceof AbstractElementWidget<?> widget && widget != AbstractElementWidget.EMPTY
                        && !isInformationalOption(widget)) {
                    widgets.add(widget);
                }
            }
            if (widgets.isEmpty()) continue;
            for (int i = 0; i < widgets.size(); i++) {
                int col = i % 2;
                if (i > 0 && col == 0) cursor += cardH + gap;
                AbstractElementWidget<?> widget = widgets.get(i);
                Rect r = new Rect(viewport.x + col * (cardW + colGap), viewport.y + cursor - scroll, cardW, cardH);
                options.add(optionSlot(widget, r));
            }
            cursor += cardH + gap;
        }
        return cursor + 8f;
    }

    private static ArrayList<HeaderAction> headerActions(ShaderPackOptionList.HeaderEntry header, Rect r) {
        ArrayList<HeaderAction> out = new ArrayList<>();
        if (!(header instanceof IrisHeaderEntryAccess access)) return out;
        float x = r.x + r.w - 42f;
        IrisElementRow.TextButtonElement resetButton = access.combatant$getResetButton();
        if (resetButton != null) {
            out.add(new HeaderAction(new Rect(x - 72f, r.y + 10f, 106f, 34f), resetButton, null,
                    Component.translatable("options.iris.reset").getString()));
        }
        IrisElementRow back = access.combatant$getBackButton();
        if (back instanceof IrisElementRowAccess rowAccess && !rowAccess.combatant$getOrderedElements().isEmpty()) {
            IrisElementRow.Element element = rowAccess.combatant$getOrderedElements().get(0);
            out.add(new HeaderAction(new Rect(r.x + 8f, r.y + 10f, 84f, 34f), element, "chevron-left",
                    Component.translatable("options.iris.back").getString()));
        }
        return out;
    }

    private static OptionSlot optionSlot(AbstractElementWidget<?> widget, Rect r) {
        String label = widget.getClass().getSimpleName();
        String value = "";
        boolean modified = false;
        if (widget instanceof IrisBaseOptionWidgetAccess access) {
            Component c = access.combatant$getUnmodifiedLabel();
            Component v = access.combatant$getValueLabel();
            if (c != null && !c.getString().isBlank()) label = LegacyTextUtil.stripLegacy(c.getString());
            if (v != null) value = LegacyTextUtil.stripLegacy(v.getString());
            // Iris computes valueLabel lazily from its native renderer. The custom renderer owns
            // presentation, so read the live option state directly instead of depending on a
            // native render pass having happened first.
            if (widget instanceof BooleanElementWidget bool) {
                value = bool.getValue();
            } else if (widget instanceof StringElementWidget string) {
                value = displayStringValue(string, string.getValue());
            } else if (widget instanceof IrisProfileOptionWidgetAccess profileAccess
                    && profileAccess.combatant$getProfileLabel() != null) {
                value = LegacyTextUtil.stripLegacy(profileAccess.combatant$getProfileLabel().getString());
            }
        } else if (widget instanceof IrisLinkElementWidgetAccess access) {
            Component c = access.combatant$getLabel();
            if (c != null) label = LegacyTextUtil.stripLegacy(c.getString());
        }
        if (widget instanceof BaseOptionElementWidget<?> base) modified = base.isValueModified();
        return new OptionSlot(widget, r, label, value, modified);
    }

    private static void updateDrag(Frame f, UiState state) {
        if (f == null || f.hidden) return;
        if (state.activeSlider != null) {
            if (isLeftMouseDown()) {
                updateSlider(state.activeSlider.slider, state.activeSlider.rect, f.canvas.mouseX);
                SystemCursor.set(SystemCursor.CursorType.RESIZE_HORIZONTAL);
            } else {
                if (state.activeSlider.slider instanceof IrisSliderElementWidgetAccess access) {
                    access.combatant$onReleased();
                }
                state.activeSlider = null;
            }
        }
    }

    private static void updateSlider(SliderElementWidget slider, Rect rect, float mouseX) {
        if (!(slider instanceof IrisSliderElementWidgetAccess access)) return;
        slider.bounds = new ScreenRectangle(0, 0, 100, 22);
        float ratio = clamp01((mouseX - (rect.x + 18f)) / Math.max(1f, rect.w - 36f));
        int nativeX = Math.round(4f + ratio * 92f);
        access.combatant$whileDragging(nativeX);
    }

    private static void beginScrollbarDrag(ScrollState state,
                                           float mouseY,
                                           Rect track,
                                           float contentHeight,
                                           float viewportHeight) {
        if (state == null || contentHeight <= viewportHeight) return;
        float hh = scrollbarHandleHeight(track, contentHeight, viewportHeight);
        float hy = scrollbarHandleY(track, state.current, contentHeight, viewportHeight);
        boolean onHandle = mouseY >= hy && mouseY <= hy + hh;
        state.dragging = true;
        state.dragOffset = onHandle ? mouseY - hy : hh * 0.5f;
        dragScrollbar(state, mouseY, track, contentHeight, viewportHeight);
    }

    private static void updateScrollbarDrag(ScrollState state,
                                            float mouseY,
                                            Rect track,
                                            float contentHeight,
                                            float viewportHeight) {
        if (state == null || !state.dragging) return;
        if (!isLeftMouseDown()) {
            state.dragging = false;
            return;
        }
        dragScrollbar(state, mouseY, track, contentHeight, viewportHeight);
    }

    private static void dragScrollbar(ScrollState state,
                                      float mouseY,
                                      Rect track,
                                      float contentHeight,
                                      float viewportHeight) {
        float max = Math.max(0f, contentHeight - viewportHeight);
        if (max <= 0f) return;
        float hh = scrollbarHandleHeight(track, contentHeight, viewportHeight);
        float travel = Math.max(1f, track.h - hh);
        float ratio = clamp01((mouseY - state.dragOffset - track.y) / travel);
        state.target = state.current = max * ratio;
    }

    private static float scrollbarHandleHeight(Rect track, float contentHeight, float viewportHeight) {
        return Math.max(44f, track.h * Math.min(1f, viewportHeight / Math.max(viewportHeight, contentHeight)));
    }

    private static float scrollbarHandleY(Rect track, float scroll, float contentHeight, float viewportHeight) {
        float max = Math.max(0f, contentHeight - viewportHeight);
        float hh = scrollbarHandleHeight(track, contentHeight, viewportHeight);
        return track.y + (track.h - hh) * (max <= 0f ? 0f : clamp01(scroll / max));
    }

    private static boolean isLeftMouseDown() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null
                && combatant.client.input.CombatantInput.state().isPressed(
                combatant.client.input.InputDevice.MOUSE, combatant.client.input.InputKeys.MOUSE_LEFT);
    }

    private static void clickNativeButton(Button button, MouseButtonEvent source, boolean doubleClick) {
        if (button == null || source == null) return;
        button.mouseClicked(new MouseButtonEvent(button.getX() + Math.max(1, button.getWidth() / 2.0),
                button.getY() + Math.max(1, button.getHeight() / 2.0), source.buttonInfo()), doubleClick);
    }

    private static void clickCancel(ShaderPackScreen screen, MouseButtonEvent source, boolean doubleClick) {
        String cancel = CommonComponents.GUI_CANCEL.getString();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof Button button) || button.getMessage() == null) continue;
            if (!cancel.equals(button.getMessage().getString())) continue;
            clickNativeButton(button, source, doubleClick);
            return;
        }
    }

    private static GuiEventListener findPinned(ShaderPackSelectionList list) {
        if (list == null) return null;
        for (var child : list.children()) {
            if (child != null && child.getClass().getSimpleName().contains("PinnedEntry")) return child;
        }
        return null;
    }

    private static ShaderPackSelectionList.ShaderPackEntry findPack(ShaderPackSelectionList list, String name) {
        if (list == null || name == null) return null;
        for (var child : list.children()) {
            if (child instanceof ShaderPackSelectionList.ShaderPackEntry entry
                    && name.equals(entry.getPackName())) return entry;
        }
        return null;
    }

    private static ShaderPackSelectionList.ShaderPackEntry selectedOrApplied(ShaderPackSelectionList list) {
        if (list == null) return null;
        for (var child : list.children()) {
            if (child instanceof ShaderPackSelectionList.ShaderPackEntry entry && entry.isSelected()) return entry;
        }
        return list.getApplied();
    }

    private static PatchSummary patchSummary(String packName) {
        ShaderPatchEngine.ShaderpackProfile profile = ShaderPatchEngine.profile(packName);
        if (!profile.matched()) {
            return new PatchSummary(PatchKind.NONE, tr("gui.combatant.iris.patch.none"), profile,
                    ShaderPatchEngine.applicationState(""));
        }
        ShaderPatchEngine.PatchApplicationState state = ShaderPatchEngine.applicationState(profile.manifestId());
        if (state.complete()) {
            return new PatchSummary(PatchKind.COMPLETE,
                    tr("gui.combatant.iris.patch.complete") + " " + state.appliedTargets() + "/" + state.expectedTargets(), profile, state);
        }
        if (state.preflightAccepted() && state.appliedTargets() > 0) {
            return new PatchSummary(PatchKind.PARTIAL,
                    tr("gui.combatant.iris.patch.partial") + " " + state.appliedTargets() + "/" + state.expectedTargets(), profile, state);
        }
        if (!state.preflightAccepted() && !"not loaded".equalsIgnoreCase(state.reason())) {
            return new PatchSummary(PatchKind.REJECTED, tr("gui.combatant.iris.patch.rejected"), profile, state);
        }
        return new PatchSummary(PatchKind.PROFILE, tr("gui.combatant.iris.patch.profile.available"), profile, state);
    }

    private static void infoPair(float x, float y, float w, String key, String value, Style s) {
        float gap = 18f;
        float valueMeasured = ClickGuiRenderer.textWidth(fontMedium(), value, 16f);
        float valueBudget = Math.max(72f, Math.min(w * 0.46f, valueMeasured + 4f));
        float keyBudget = Math.max(72f, w - valueBudget - gap);

        String fitKey = ClickGuiRenderer.fitText(fontRegular(), key, 16f, keyBudget);
        String fitValue = ClickGuiRenderer.fitText(fontMedium(), value, 16f, valueBudget);
        float valueWidth = ClickGuiRenderer.textWidth(fontMedium(), fitValue, 16f);
        float valueCellX = x + keyBudget + gap;

        drawText(fontRegular(), fitKey, x, y, 16f, withAlpha(s.muted, 213));
        drawText(fontMedium(), fitValue, valueCellX + valueBudget - valueWidth, y, 16f, withAlpha(s.text, 238));
    }

    private static void drawWrapped(String text, float x, float y, float w, float size, int color, int maxLines) {
        List<String> lines = ClickGuiRenderer.wrapText(fontRegular(), text == null ? "" : text, size, w, maxLines);
        for (String line : lines) {
            drawText(fontRegular(), line, x, y, size, withAlpha(color, 188));
            y += size + 9f;
        }
    }

    private static String yesNo(boolean value) {
        return value ? tr("gui.combatant.iris.yes") : tr("gui.combatant.iris.no");
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static UiState state(ShaderPackScreen screen) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(screen, unused -> new UiState());
        }
    }

    private static Motion motion(Object key, boolean hover, boolean active) {
        Motion m;
        synchronized (MOTIONS) {
            m = MOTIONS.computeIfAbsent(key, unused -> new Motion());
        }
        float dt = Math.max(0.001f, AnimationUtility.deltaTime());
        m.hover = AnimationUtility.approach(m.hover, hover ? 1f : 0f, dt, 11f);
        m.active = AnimationUtility.approach(m.active, active ? 1f : 0f, dt, 13f);
        m.press = AnimationUtility.approach(m.press, 0f, dt, 10f);
        return m;
    }

    private static void pressed(Object key) {
        if (key == null) return;
        synchronized (MOTIONS) {
            MOTIONS.computeIfAbsent(key, unused -> new Motion()).press = 1f;
        }
    }

    private static void updateValueMotion(Motion m, String value) {
        if (m.lastValue == null) m.lastValue = value;
        else if (!m.lastValue.equals(value)) {
            m.lastValue = value;
            m.change = 1f;
        }
        m.change = AnimationUtility.approach(m.change, 0f,
                Math.max(0.001f, AnimationUtility.deltaTime()), 8f);
    }

    private static TextRenderer fontRegular() {
        return BuiltinFontCatalog.ONEST_REGULAR.renderer();
    }

    private static TextRenderer fontMedium() {
        return ClickGuiRenderer.getOnestMedium();
    }

    private static TextRenderer fontBold() {
        return ClickGuiRenderer.getOnestBold();
    }

    private static void drawText(TextRenderer renderer, String text, float x, float y, float size, int color) {
        ClickGuiRenderer.drawText(renderer, text == null ? "" : text, x, y, size, color, false);
    }

    private static int withAlpha(int color, int alpha) {
        return SettingsGuiPalette.withAlpha(color, Math.max(0, Math.min(255, alpha)));
    }

    private static int mix(int a, int b, float t) {
        return SettingsGuiPalette.mix(a, b, clamp01(t));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static final class UiState {
        boolean gridView = true;
        String selectedPackName;
        String hoveredPackName;
        AbstractElementWidget<?> focusedOption;
        final ScrollState packScroll = new ScrollState();
        final ScrollState optionScroll = new ScrollState();
        ActiveSlider activeSlider;
        SelectPopup selectPopup;
    }

    private static final class ScrollState {
        float current;
        float target;
        boolean dragging;
        float dragOffset;

        void clamp(float contentHeight, float viewportHeight) {
            float max = Math.max(0f, contentHeight - viewportHeight);
            target = Math.max(0f, Math.min(max, target));
            current = Math.max(0f, Math.min(max, current));
        }

        void tick() {
            float dt = Math.max(0.001f, AnimationUtility.deltaTime());
            current = AnimationUtility.approach(current, target, dt, 10.5f);
            current = AnimationUtility.snap(current, target, 0.08f);
        }
    }

    private enum ControlId { GRID, LIST, FOLDER, OPTIONS, HIDE, DOWNLOAD, CANCEL, APPLY, DONE }

    private static final class Motion {
        float hover;
        float active;
        float press;
        float change;
        float progress;
        boolean progressInitialized;
        String lastValue;
    }


    private static final class SelectPopup {
        final BaseOptionElementWidget<?> widget;
        final List<SelectChoice> choices;
        final Rect bounds;
        final float rowHeight;
        final boolean opensUp;
        final Map<SelectChoice, Motion> motions = new IdentityHashMap<>();
        float reveal;
        float scrollCurrent;
        float scrollTarget;
        boolean closing;
        SelectChoice hoveredChoice;

        SelectPopup(BaseOptionElementWidget<?> widget,
                    List<SelectChoice> choices,
                    Rect bounds,
                    float rowHeight,
                    boolean opensUp) {
            this.widget = widget;
            this.choices = choices;
            this.bounds = bounds;
            this.rowHeight = rowHeight;
            this.opensUp = opensUp;
        }

        Rect animatedBounds() {
            float eased = AnimationUtility.easeInOutCubic(reveal);
            float h = bounds.h * eased;
            float y = opensUp ? bounds.y + bounds.h - h : bounds.y;
            return new Rect(bounds.x, y, bounds.w, h);
        }

        Rect rowRect(int index, float slide) {
            return new Rect(bounds.x, bounds.y + 6f + index * rowHeight - scrollCurrent + slide,
                    bounds.w, rowHeight);
        }

        float contentHeight() {
            return choices.size() * rowHeight;
        }

        float maxScroll() {
            return Math.max(0f, contentHeight() - Math.max(0f, bounds.h - 12f));
        }

        void clampScroll() {
            float max = maxScroll();
            scrollTarget = Math.max(0f, Math.min(max, scrollTarget));
            scrollCurrent = Math.max(0f, Math.min(max, scrollCurrent));
        }

        Motion motion(SelectChoice choice, boolean hover, boolean active) {
            Motion motion = motions.computeIfAbsent(choice, unused -> new Motion());
            float dt = Math.max(0.001f, AnimationUtility.deltaTime());
            motion.hover = AnimationUtility.approach(motion.hover, hover ? 1f : 0f, dt, 12f);
            motion.active = AnimationUtility.approach(motion.active, active ? 1f : 0f, dt, 10f);
            return motion;
        }

        void close() {
            closing = true;
        }
    }

    private static final class SelectChoice {
        final String label;
        final boolean selected;
        final Runnable action;

        SelectChoice(String label, boolean selected, Runnable action) {
            this.label = label == null ? "" : label;
            this.selected = selected;
            this.action = action == null ? () -> { } : action;
        }
    }

    private record ActiveSlider(SliderElementWidget slider, Rect rect) {
    }

    private record PackSlot(ShaderPackSelectionList.ShaderPackEntry entry, Rect rect) {
    }

    private record OptionSlot(AbstractElementWidget<?> widget, Rect rect, String label, String value, boolean modified) {
    }

    private record HeaderSlot(ShaderPackOptionList.HeaderEntry entry, Rect rect, String title, List<HeaderAction> actions) {
    }

    private record HeaderAction(Rect rect, GuiEventListener listener, String icon, String label) {
    }

    private enum PatchKind { COMPLETE, PARTIAL, REJECTED, PROFILE, NONE }

    private record PatchSummary(PatchKind kind,
                                String shortLabel,
                                ShaderPatchEngine.ShaderpackProfile profile,
                                ShaderPatchEngine.PatchApplicationState state) {
    }

    private record Rect(float x, float y, float w, float h) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        boolean intersects(Rect other) {
            return x + w >= other.x && x <= other.x + other.w && y + h >= other.y && y <= other.y + other.h;
        }
    }

    private static final class Frame {
        final FixedCanvas canvas;
        final Rect root;
        final Rect content;
        final Rect main;
        final Rect details;
        final Rect viewport;
        final Rect scrollbar;
        final Rect gridButton;
        final Rect listButton;
        final Rect folderButton;
        final Rect switchButton;
        final Rect hideButton;
        final Rect shaderToggle;
        final Rect downloadButton;
        final GuiEventListener downloadAction;
        final Rect doneButton;
        final Rect applyButton;
        final Rect cancelButton;
        final List<PackSlot> packSlots;
        final List<OptionSlot> optionSlots;
        final List<HeaderSlot> headerSlots;
        final List<HeaderAction> headerActions;
        final float contentHeight;
        final boolean optionMode;
        final boolean hidden;

        private Frame(FixedCanvas canvas, Rect root, Rect content, Rect main, Rect details, Rect viewport, Rect scrollbar,
                      Rect gridButton, Rect listButton, Rect folderButton, Rect switchButton, Rect hideButton,
                      Rect shaderToggle, Rect downloadButton, GuiEventListener downloadAction,
                      Rect doneButton, Rect applyButton, Rect cancelButton,
                      List<PackSlot> packSlots, List<OptionSlot> optionSlots, List<HeaderSlot> headerSlots,
                      List<HeaderAction> headerActions, float contentHeight, boolean optionMode, boolean hidden) {
            this.canvas = canvas;
            this.root = root;
            this.content = content;
            this.main = main;
            this.details = details;
            this.viewport = viewport;
            this.scrollbar = scrollbar;
            this.gridButton = gridButton;
            this.listButton = listButton;
            this.folderButton = folderButton;
            this.switchButton = switchButton;
            this.hideButton = hideButton;
            this.shaderToggle = shaderToggle;
            this.downloadButton = downloadButton;
            this.downloadAction = downloadAction;
            this.doneButton = doneButton;
            this.applyButton = applyButton;
            this.cancelButton = cancelButton;
            this.packSlots = packSlots;
            this.optionSlots = optionSlots;
            this.headerSlots = headerSlots;
            this.headerActions = headerActions;
            this.contentHeight = contentHeight;
            this.optionMode = optionMode;
            this.hidden = hidden;
        }

        static Frame hidden(FixedCanvas canvas, boolean canShow) {
            Rect hide = canShow ? new Rect(canvas.logicalWidth - 74f, canvas.logicalHeight - 74f, 48f, 48f) : null;
            Rect empty = new Rect(0, 0, 0, 0);
            return new Frame(canvas, empty, empty, empty, empty, empty, null,
                    null, null, null, null, hide, null, null, null,
                    null, null, null, List.of(), List.of(), List.of(), List.of(),
                    0f, false, true);
        }
    }

    private static final class FixedCanvas {
        final float logicalWidth;
        final float logicalHeight;
        final float originX;
        final float originY;
        final float mouseX;
        final float mouseY;

        private FixedCanvas(float logicalWidth, float logicalHeight, float originX, float originY, float mouseX, float mouseY) {
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
            this.originX = originX;
            this.originY = originY;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        static FixedCanvas capture() {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) {
                return new FixedCanvas(1f, 1f, 0f, 0f, 0f, 0f);
            }
            int fbW = Math.max(1, mc.getWindow().getWidth());
            int fbH = Math.max(1, mc.getWindow().getHeight());
            float logicalW = HudScale.virtualWidth(fbW, fbH);
            float logicalH = HudScale.virtualHeight(fbW, fbH);
            float scale = HudScale.scale(fbW, fbH);
            float mx = scale > 0f ? (float) mc.mouseHandler.xpos() / scale : (float) mc.mouseHandler.xpos();
            float my = scale > 0f ? (float) mc.mouseHandler.ypos() / scale : (float) mc.mouseHandler.ypos();
            return new FixedCanvas(logicalW, logicalH, (logicalW - CANVAS_W) * 0.5f,
                    (logicalH - CANVAS_H) * 0.5f, mx, my);
        }

        boolean valid() {
            return Float.isFinite(logicalWidth) && Float.isFinite(logicalHeight) && logicalWidth > 0f && logicalHeight > 0f;
        }
    }

    private static final class Style {
        final int windowTop;
        final int windowBottom;
        final int headerTop;
        final int headerBottom;
        final int detailsTop;
        final int detailsBottom;
        final int cardTop;
        final int cardBottom;
        final int cardHover;
        final int control;
        final int controlHover;
        final int stroke;
        final int strokeBright;
        final int text;
        final int muted;
        final int accent;
        final int accentSoft;
        final int accentBright;
        final int scrollTrack;
        final int scrollHandle;
        final int good;
        final int warn;
        final int bad;

        private Style(int windowTop, int windowBottom, int headerTop, int headerBottom,
                      int detailsTop, int detailsBottom, int cardTop, int cardBottom, int cardHover,
                      int control, int controlHover, int stroke, int strokeBright,
                      int text, int muted, int accent, int accentSoft, int accentBright,
                      int scrollTrack, int scrollHandle, int good, int warn, int bad) {
            this.windowTop = windowTop;
            this.windowBottom = windowBottom;
            this.headerTop = headerTop;
            this.headerBottom = headerBottom;
            this.detailsTop = detailsTop;
            this.detailsBottom = detailsBottom;
            this.cardTop = cardTop;
            this.cardBottom = cardBottom;
            this.cardHover = cardHover;
            this.control = control;
            this.controlHover = controlHover;
            this.stroke = stroke;
            this.strokeBright = strokeBright;
            this.text = text;
            this.muted = muted;
            this.accent = accent;
            this.accentSoft = accentSoft;
            this.accentBright = accentBright;
            this.scrollTrack = scrollTrack;
            this.scrollHandle = scrollHandle;
            this.good = good;
            this.warn = warn;
            this.bad = bad;
        }

        static Style current() {
            SettingsGuiPalette p = SettingsGuiPalette.current();
            var t = Theme.theme();
            return new Style(
                    p.contentPlaneTop(), p.contentPlaneBottom(),
                    p.navigationPlaneTop(), p.navigationPlaneBottom(),
                    p.workspaceWashLeft(), p.workspaceWashRight(),
                    p.moduleCardTop(), p.moduleCardBottom(), p.moduleCardTopStrong(),
                    p.controlSurface(), p.controlSurfaceHover(), p.glassEdgeSoft(), p.glassEdgeStrong(),
                    t.textPrimary(), t.textMuted(), t.accent(), t.accentSoft(),
                    SettingsGuiPalette.mix(t.accent(), t.textPrimary(), 0.32f),
                    p.moduleScrollTrackA(), p.moduleScrollHandleB(),
                    0xFF65C988, 0xFFE4B866, 0xFFE16C72
            );
        }
    }
}
