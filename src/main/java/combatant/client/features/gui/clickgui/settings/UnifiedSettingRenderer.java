/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import org.lwjgl.glfw.GLFW;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.ColorValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.features.gui.clickgui.sound.GuiSound;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

enum UnifiedSettingRenderer {
    ;
    private static final float BIND_MARQUEE_SPEED = 22f;
    private static final float BIND_MARQUEE_GAP = 14f;
    private static final float BIND_MARQUEE_PAUSE_SEC = 0.8f;

    private static float m(float settings, float modules) {
        return UnifiedSettingsSkin.metric(settings, modules);
    }

    private static float s() {
        return UnifiedSettingsSkin.scale();
    }

    private static boolean modules() {
        return UnifiedSettingsSkin.modules();
    }

    // ------------------------------------------------------------
    // Boolean
    // ------------------------------------------------------------

    static void render(BooleanSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        BooleanSetting.UiState ui = setting.ui();
        LayoutBoolean layout = booleanLayout(setting, w);
        ui.lastX = x;
        ui.lastY = y;
        ui.lastW = w;
        ui.lastH = layout.rowH;

        boolean enabled = setting.get();
        float dt = AnimationUtility.deltaTime();
        ui.anim = AnimationUtility.approach(ui.anim, enabled ? 1f : 0f, dt, 13f);
        boolean hover = UnifiedSettingsSkin.inside(mx, my, x, y, w, layout.rowH);
        ui.hoverAnim = AnimationUtility.approach(ui.hoverAnim, hover ? 1f : 0f, dt, 12f);

        TextRenderer font = modules() ? UnifiedSettingsSkin.fontRegular() : ClickGuiRenderer.getIosevkaRegular();
        float labelSize = m(18f, 6.8f);

        float cardX = x + m(2f, 2f);
        float cardY = y + m(3f, 2f);
        float cardW = Math.max(1f, w - m(4f, 4f));
        float cardH = Math.max(m(28f, 17f), layout.rowH - m(6f, 4f));
        float radius = m(8f, 5f);
        UnifiedSettingsSkin.drawCard(cardX, cardY, cardW, cardH, radius, 0.42f + ui.hoverAnim * 0.24f);
        float cardStrokeAnim = Math.max(ui.hoverAnim, ui.anim);
        if (cardStrokeAnim > 0.001f) {
            int strokeA = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, Math.round(ui.hoverAnim * 82f));
            int strokeB = UnifiedSettingsSkin.accentGradientStart(ui.anim * 0.50f);
            ClickGuiRenderer.drawRoundedRectStroke(cardX, cardY, cardW, cardH, radius, m(0.65f, 0.42f), UnifiedSettingsSkin.mix(strokeA, strokeB, Math.max(ui.anim, ui.hoverAnim * 0.28f)));
        }

        float sw = m(39f, 16f);
        float sh = m(18f, 8f);
        float sx = cardX + m(8f, 5f);
        float sy = cardY + (cardH - sh) * 0.5f;
        ui.lastControlX = sx;
        ui.lastControlY = sy;
        ui.lastControlW = sw;
        ui.lastControlH = sh;

        int bgOffA = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE, UnifiedSettingsSkin.SURFACE_HOVER, 0.45f + ui.hoverAnim * 0.20f);
        int bgOffB = UnifiedSettingsSkin.mix(bgOffA, 0xFF000000, 0.30f);
        int bgOnA = UnifiedSettingsSkin.accentGradientStart(1f);
        int bgOnB = UnifiedSettingsSkin.accentGradientEnd(1f);
        int bgA = UnifiedSettingsSkin.mix(bgOffA, bgOnA, ui.anim);
        int bgB = UnifiedSettingsSkin.mix(bgOffB, bgOnB, ui.anim);
        ClickGuiRenderer.drawRoundedRectGradient(sx, sy, sw, sh, sh * 0.5f, bgA, bgB, UnifiedSettingsSkin.ACCENT_GRADIENT_ANGLE);
        ClickGuiRenderer.drawRoundedRectStroke(sx, sy, sw, sh, sh * 0.5f, m(0.65f, 0.42f), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 50 + Math.round(ui.hoverAnim * 44f)));

        float knob = sh - m(4f, 2.4f);
        float kx = sx + m(2f, 1.2f) + (sw - knob - m(4f, 2.4f)) * AnimationUtility.easeInOutCubic(ui.anim);
        float ky = sy + (sh - knob) * 0.5f;
        int knobCol = UnifiedSettingsSkin.mix(0xFFECEEF2, 0xFFFFFFFF, ui.anim * 0.55f + ui.hoverAnim * 0.18f);
        ClickGuiRenderer.drawRoundedRect(kx, ky, knob, knob, knob * 0.5f, knobCol);

        float labelX = sx + sw + m(9f, 6f);
        int labelColor = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, 0.72f + ui.hoverAnim * 0.16f + ui.anim * 0.12f);

        ClickGuiRenderer.beginTextBatch();
        for (int i = 0; i < layout.lines.length; i++) {
            ClickGuiRenderer.drawText(font, layout.lines[i], labelX, y + layout.labelY + i * (layout.lineH + m(2f, 0.7f)), labelSize, labelColor, false);
        }
        ClickGuiRenderer.endTextBatch();
    }

    static void mouseClicked(BooleanSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        BooleanSetting.UiState ui = setting.ui();
        if (!UnifiedSettingsSkin.inside(mx, my, ui.lastX, ui.lastY, ui.lastW, ui.lastH)) return;
        setting.set(!setting.get());
        GuiSound.CHANGE_MODE.feedback();
        if (setting.getParent() != null) setting.getParent().saveConfig();
    }

    static float getHeight(BooleanSetting setting) {
        return booleanLayout(setting, setting.ui().lastW > 0f ? setting.ui().lastW : m(170f, 115f)).rowH;
    }

    private static LayoutBoolean booleanLayout(BooleanSetting setting, float width) {
        BooleanSetting.UiState ui = setting.ui();
        TextRenderer font = modules() ? UnifiedSettingsSkin.fontRegular() : ClickGuiRenderer.getIosevkaRegular();
        float labelSize = m(18f, 6.8f);
        float switchW = m(39f, 16f);
        String labelText = setting.getDisplayName();
        boolean needsLayout = !Float.isFinite(ui.layoutW)
                || Math.abs(ui.layoutW - width) > 0.5f
                || !labelText.equals(ui.layoutLabel);
        if (needsLayout) {
            float lineH = UnifiedSettingsSkin.textHeight(font, labelSize);
            float labelMaxW = Math.max(m(72f, 36f), width - switchW - m(31f, 18f));
            var linesList = ClickGuiRenderer.wrapText(font, labelText, labelSize, labelMaxW, 2);
            String[] lines = linesList.toArray(new String[0]);
            float gap = m(2f, 0.7f);
            float textBlockH = lines.length * lineH + Math.max(0, lines.length - 1) * gap;
            float contentH = Math.max(textBlockH, m(18f, 8f));
            float minRow = m(44f, 24f);
            ui.layoutW = width;
            ui.layoutLabel = labelText;
            ui.layoutLines = lines;
            ui.layoutLineH = lineH;
            ui.layoutRowH = Math.max(minRow, contentH + m(14f, 8f));
            ui.layoutLabelY = (ui.layoutRowH - textBlockH) * 0.5f;
            ui.layoutControlY = (ui.layoutRowH - m(18f, 8f)) * 0.5f;
        }
        return new LayoutBoolean(ui.layoutLines, ui.layoutLineH, ui.layoutLabelY, ui.layoutControlY, ui.layoutRowH);
    }

    static <N extends Number> void render(SliderSetting<N> setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        SliderSetting.UiState ui = setting.ui();
        NumberValue<N> value = setting.value();
        double min = value.getMin().doubleValue();
        double max = value.getMax().doubleValue();
        double val = value.get().doubleValue();

        ui.lastX = x;
        ui.lastY = y;
        ui.lastW = w;

        float dt = AnimationUtility.deltaTime();
        if (ui.dragging && !ui.editing) {
            setSliderValueFromMouse(setting, mx, x + m(6f, 5f), Math.max(1f, w - m(6f, 5f) * 2f), min, max);
            val = value.get().doubleValue();
        }

        float trackPad = m(6f, 5f);
        float trackX = x + trackPad;
        float trackW = Math.max(1f, w - trackPad * 2f);
        float trackY = y + m(38f, 15f);
        float trackH = m(2.4f, 1.2f);

        float target = max == min ? 0f : (float) ((val - min) / (max - min));
        target = UnifiedSettingsSkin.clamp01(target);
        ui.anim = AnimationUtility.approach(ui.anim, target, 0.40f);
        boolean trackHover = UnifiedSettingsSkin.inside(mx, my, trackX, trackY - m(6f, 2f), trackW, trackH + m(12f, 4f));
        if ((trackHover && !ui.editing) || ui.dragging) {
            SystemCursor.set(SystemCursor.CursorType.RESIZE_HORIZONTAL);
        }
        ui.hoverAnim = AnimationUtility.approach(ui.hoverAnim, trackHover ? 1f : 0f, dt, 12f);
        ui.editAnim = AnimationUtility.approach(ui.editAnim, ui.editing ? 1f : 0f, dt, 14f);
        ui.errorAnim = AnimationUtility.approach(ui.errorAnim, 0f, dt, 7.5f);
        if (ui.editing) ui.cursorBlink += dt * 2.3f;
        else ui.cursorBlink = 0f;

        TextRenderer font = modules() ? UnifiedSettingsSkin.fontRegular() : ClickGuiRenderer.getIosevkaRegular();
        float labelSize = m(17f, 6.8f);
        float valueSize = m(17f, 6.8f);
        float textY = y + m(6f, 1.9f);
        String display = ui.editing ? ui.editBuffer : value.toDisplay();
        if (display == null) display = "";
        float valuePad = m(6f, 4f);
        float rawValueW = UnifiedSettingsSkin.textWidth(font, display.isEmpty() ? "0" : display, valueSize) + valuePad * 2f;
        float valueW = Math.max(m(34f, 20f), rawValueW);
        valueW = Math.min(valueW, Math.max(m(46f, 28f), w * 0.48f));
        ui.valueX = x + w - valueW - trackPad;
        ui.valueY = textY - m(4f, 1.5f);
        ui.valueW = valueW;
        ui.valueH = UnifiedSettingsSkin.textHeight(font, valueSize) + m(8f, 4f);
        boolean valueHover = UnifiedSettingsSkin.inside(mx, my, ui.valueX, ui.valueY, ui.valueW, ui.valueH);
        ui.valueHoverAnim = AnimationUtility.approach(ui.valueHoverAnim, valueHover ? 1f : 0f, dt, 12f);
        ui.inputHoverAnim = AnimationUtility.approach(ui.inputHoverAnim, valueHover ? 1f : 0f, dt, 15f);
        ui.inputFocusGlowAnim = AnimationUtility.approach(ui.inputFocusGlowAnim, ui.editing ? 1f : 0f, dt, 12f);
        ui.inputPressAnim = AnimationUtility.approach(ui.inputPressAnim, ui.editing ? 1f : 0f, dt, 10f);

        int textCol = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_PRIMARY, 0xFFFF7777, ui.errorAnim * 0.85f);
        float fieldAnim = Math.max(ui.inputHoverAnim * 0.62f, ui.inputFocusGlowAnim);
        int fieldBg = UnifiedSettingsSkin.withAlpha(
                UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE_SOFT, UnifiedSettingsSkin.SURFACE_HOVER, fieldAnim * 0.72f),
                Math.round(fieldAnim * 104f)
        );
        int fieldStrokeBase = UnifiedSettingsSkin.mix(
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, Math.round(fieldAnim * 70f)),
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, Math.round(ui.inputFocusGlowAnim * 160f)),
                ui.inputFocusGlowAnim
        );
        int fieldStroke = UnifiedSettingsSkin.mix(fieldStrokeBase, 0xFFFF5F66, ui.errorAnim);

        ClickGuiRenderer.beginTextBatch();
        String label = setting.getDisplayName();
        float labelMax = Math.max(1f, ui.valueX - trackX - m(7f, 5f));
        ClickGuiRenderer.drawText(font, UnifiedSettingsSkin.fit(font, label, labelSize, labelMax), trackX, textY, labelSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        ClickGuiRenderer.endTextBatch();

        if (fieldAnim > 0.001f || ui.errorAnim > 0.001f) {
            float lift = ui.inputFocusGlowAnim * m(1.0f, 0.45f);
            if (UnifiedSettingsSkin.SURFACE_GRADIENT_ENABLED) {
                UnifiedSettingsSkin.drawSurface(ui.valueX, ui.valueY - lift, ui.valueW, ui.valueH + lift * 2f, m(6f, 3.5f), fieldAnim);
            } else {
                ClickGuiRenderer.drawRoundedRect(ui.valueX, ui.valueY - lift, ui.valueW, ui.valueH + lift * 2f, m(6f, 3.5f), fieldBg);
            }
            if (ui.inputFocusGlowAnim > 0.02f) {
                ClickGuiRenderer.drawRoundedRectStrokeGradient(ui.valueX, ui.valueY - lift, ui.valueW, ui.valueH + lift * 2f, m(6f, 3.5f), m(0.8f, 0.45f), UnifiedSettingsSkin.accentGradientStart(0.72f), UnifiedSettingsSkin.accentGradientEnd(0.72f), UnifiedSettingsSkin.ACCENT_GRADIENT_ANGLE);
            } else {
                ClickGuiRenderer.drawRoundedRectStroke(ui.valueX, ui.valueY - lift, ui.valueW, ui.valueH + lift * 2f, m(6f, 3.5f), m(0.7f, 0.45f), fieldStroke);
            }
            if (ui.errorAnim > 0.01f) {
                ClickGuiRenderer.drawRoundedRectStroke(ui.valueX, ui.valueY - lift, ui.valueW, ui.valueH + lift * 2f, m(6f, 3.5f), m(1.0f, 0.55f), UnifiedSettingsSkin.withAlpha(0xFFFF5F66, Math.round(170f * ui.errorAnim)));
            }
        }

        float textBaseY = y + m(6f, 1.9f);
        ui.valueTextSize = valueSize;
        ui.valueTextPad = valuePad;
        ui.valueTextY = textBaseY;
        String drawValue = ui.editing ? display : UnifiedSettingsSkin.fit(font, display, valueSize, ui.valueW - valuePad * 2f);
        float drawW = UnifiedSettingsSkin.textWidth(font, drawValue, valueSize);
        float drawX = ui.editing ? ui.valueX + valuePad : ui.valueX + ui.valueW - drawW - valuePad;
        ui.valueTextX = drawX;
        boolean textClip = ScissorFunction.pushRaw(ui.valueX + valuePad * 0.65f, ui.valueY, Math.max(1f, ui.valueW - valuePad * 1.3f), ui.valueH);
        if (ui.editing) {
            renderInlineSelection(font, ui.editBuffer, sliderSelStart(ui), sliderSelEnd(ui), drawX, textBaseY, valueSize, ui.valueH, 0f);
        }
        ClickGuiRenderer.drawText(font, drawValue, drawX, textBaseY, valueSize, textCol, false);
        if (ui.editing && ((int) (ui.cursorBlink * 2f) & 1) == 0) {
            int cursor = Math.max(0, Math.min(ui.editCursor, ui.editBuffer.length()));
            float cx = drawX + UnifiedSettingsSkin.textWidth(font, ui.editBuffer.substring(0, cursor), valueSize) + m(0.5f, 0.3f);
            ClickGuiRenderer.drawRect(cx, ui.valueY + m(3f, 1.5f), m(0.75f, 0.45f), ui.valueH - m(6f, 3f), UnifiedSettingsSkin.withAlpha(textCol, 205));
        }
        if (textClip) ScissorFunction.pop();

        int baseTrack = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE, UnifiedSettingsSkin.SURFACE_HOVER, 0.55f + ui.hoverAnim * 0.15f);
        ClickGuiRenderer.drawRoundedRect(trackX, trackY, trackW, trackH, 0.6f * s(), baseTrack);
        float fillW = trackW * ui.anim;
        if (fillW > 0.5f) {
            ClickGuiRenderer.drawGradientRect(trackX, trackY, fillW, trackH, UnifiedSettingsSkin.accentGradientStart(1f), UnifiedSettingsSkin.accentGradientEnd(1f), UnifiedSettingsSkin.accentGradientEnd(1f), UnifiedSettingsSkin.accentGradientStart(1f));
        }

        float knobW = m(2.4f, 1.2f);
        float knobH = m(7.2f, 4.2f);
        float knobX = trackX + fillW - knobW * 0.5f;
        float knobY = trackY - (knobH - trackH) * 0.5f;
        int knobCol = UnifiedSettingsSkin.mix(0xFFE1E1E1, UnifiedSettingsSkin.accentGradientStart(1f), 0.25f + ui.hoverAnim * 0.20f);
        ClickGuiRenderer.drawRoundedRect(knobX, knobY, knobW, knobH, knobW * 0.5f, knobCol);
    }

    // ------------------------------------------------------------
    // Slider
    // ------------------------------------------------------------

    static <N extends Number> void mouseClicked(SliderSetting<N> setting, double mx, double my, int button) {
        if (button != 0) return;
        float trackPad = m(6f, 5f);
        SliderSetting.UiState ui = setting.ui();
        if (ui.editing) {
            if (UnifiedSettingsSkin.inside(mx, my, ui.valueX, ui.valueY, ui.valueW, ui.valueH)) {
                setSliderEditCursorFromMouse(setting, (float) mx, false);
            } else {
                commitSliderEdit(setting, true);
            }
            return;
        }
        if (UnifiedSettingsSkin.inside(mx, my, ui.valueX, ui.valueY, ui.valueW, ui.valueH)) {
            beginSliderEdit(setting);
            setSliderEditCursorFromMouse(setting, (float) mx, false);
            return;
        }
        float trackX = ui.lastX + trackPad;
        float trackW = Math.max(1f, ui.lastW - trackPad * 2f);
        float trackY = ui.lastY + m(38f, 15f);
        float trackH = m(2.4f, 1.2f);
        if (!UnifiedSettingsSkin.inside(mx, my, trackX, trackY - m(6f, 2f), trackW, trackH + m(12f, 4f))) return;
        ui.dragging = true;
        setSliderValueFromMouse(setting, (float) mx, trackX, trackW, setting.value().getMin().doubleValue(), setting.value().getMax().doubleValue());
    }

    static <N extends Number> void mouseReleased(SliderSetting<N> setting, double mx, double my, int button) {
        if (button != 0 || !setting.ui().dragging) return;
        setting.ui().dragging = false;
        if (setting.getParent() != null) setting.getParent().saveConfig();
    }

    static <N extends Number> void mouseClickedOutside(SliderSetting<N> setting, double mx, double my, int button) {
        if (button != 0) return;
        SliderSetting.UiState ui = setting.ui();
        if (!ui.editing) return;
        commitSliderEdit(setting, true);
    }

    static <N extends Number> boolean keyPressed(SliderSetting<N> setting, int keyCode, int scanCode, int modifiers) {
        SliderSetting.UiState ui = setting.ui();
        if (!ui.editing) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAllSliderText(ui);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            ClipboardUtil.copy(selectedOrAllSliderText(ui));
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            insertSliderText(setting, ClipboardUtil.get());
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            ClipboardUtil.copy(selectedOrAllSliderText(ui));
            deleteSliderSelection(ui);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitSliderEdit(setting, true);
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                cancelSliderEdit(setting);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteSliderSelection(ui) && ui.editCursor > 0) {
                    ui.editBuffer = ui.editBuffer.substring(0, ui.editCursor - 1) + ui.editBuffer.substring(ui.editCursor);
                    ui.editCursor--;
                }
                validateSliderLive(setting, ui);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!deleteSliderSelection(ui) && ui.editCursor < ui.editBuffer.length()) {
                    ui.editBuffer = ui.editBuffer.substring(0, ui.editCursor) + ui.editBuffer.substring(ui.editCursor + 1);
                }
                validateSliderLive(setting, ui);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveSliderCursor(ui, -1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveSliderCursor(ui, 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                setSliderCursor(ui, 0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                setSliderCursor(ui, ui.editBuffer.length(), shift);
                return true;
            }
        }
        return true;
    }

    static <N extends Number> boolean charTyped(SliderSetting<N> setting, char chr, int modifiers) {
        SliderSetting.UiState ui = setting.ui();
        if (!ui.editing) return false;
        if (chr == ',' || chr == '.') {
            if (sliderInteger(setting) || sliderTextHasDecimal(ui.editBuffer)) {
                pulseSliderError(ui);
                return true;
            }
            insertSliderTextRaw(ui, ".");
            validateSliderLive(setting, ui);
            return true;
        }
        if (chr == '-') {
            if (setting.value().getMin().doubleValue() >= 0.0) {
                pulseSliderError(ui);
                return true;
            }
            if (hasSliderSelection(ui)) deleteSliderSelection(ui);
            if (ui.editCursor != 0 || ui.editBuffer.indexOf('-') >= 0) {
                pulseSliderError(ui);
                return true;
            }
            insertSliderTextRaw(ui, "-");
            validateSliderLive(setting, ui);
            return true;
        }
        if (chr >= '0' && chr <= '9') {
            insertSliderTextRaw(ui, String.valueOf(chr));
            validateSliderLive(setting, ui);
            return true;
        }
        pulseSliderError(ui);
        return true;
    }

    static float getHeight(SliderSetting<?> setting) {
        return m(58f, 24f);
    }

    private static boolean sliderInteger(SliderSetting<?> setting) {
        Object current = setting.value().get();
        return current instanceof Integer || current instanceof Long;
    }

    private static void beginSliderEdit(SliderSetting<?> setting) {
        SliderSetting.UiState ui = setting.ui();
        ui.editing = true;
        ui.dragging = false;
        ui.editBuffer = String.valueOf(setting.value().get()).replace(',', '.');
        ui.editCursor = ui.editBuffer.length();
        ui.editSelection.clear();
        ui.errorAnim = 0f;
        ui.cursorBlink = 0f;
    }

    private static void cancelSliderEdit(SliderSetting<?> setting) {
        SliderSetting.UiState ui = setting.ui();
        ui.editing = false;
        ui.editBuffer = "";
        ui.editCursor = 0;
        ui.editSelection.clear();
        ui.errorAnim = 0f;
    }

    private static <N extends Number> void commitSliderEdit(SliderSetting<N> setting, boolean save) {
        SliderSetting.UiState ui = setting.ui();
        String raw = ui.editBuffer == null ? "" : ui.editBuffer.trim().replace(',', '.');
        if (!sliderTextCanCommit(setting, raw)) {
            pulseSliderError(ui);
            return;
        }
        try {
            double parsed = Double.parseDouble(raw);
            double min = setting.value().getMin().doubleValue();
            double max = setting.value().getMax().doubleValue();
            parsed = Math.max(min, Math.min(max, parsed));
            if (sliderInteger(setting)) parsed = Math.round(parsed);
            else parsed = Math.round(parsed * 1000d) / 1000d;
            N next = setting.value().castToType(parsed);
            setting.value().set(next);
            ui.editing = false;
            ui.editBuffer = "";
            ui.editCursor = 0;
            ui.editSelection.clear();
            if (save && setting.getParent() != null) setting.getParent().saveConfig();
        } catch (Throwable ignored) {
            pulseSliderError(ui);
        }
    }

    private static boolean sliderTextCanCommit(SliderSetting<?> setting, String raw) {
        if (raw == null) return false;
        String text = raw.trim().replace(',', '.');
        if (text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) return false;
        if (sliderInteger(setting) && text.indexOf('.') >= 0) return false;
        try {
            double parsed = Double.parseDouble(text);
            return Double.isFinite(parsed);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String sanitizeSliderText(String text, boolean integer) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        boolean dot = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
            } else if (c == '-' && out.length() == 0) {
                out.append('-');
            } else if (!integer && (c == '.' || c == ',') && !dot) {
                out.append('.');
                dot = true;
            }
        }
        return out.toString();
    }

    private static boolean sliderTextHasDecimal(String text) {
        return text != null && (text.indexOf('.') >= 0 || text.indexOf(',') >= 0);
    }

    private static void validateSliderLive(SliderSetting<?> setting, SliderSetting.UiState ui) {
        if (ui.editBuffer == null || ui.editBuffer.isEmpty() || ui.editBuffer.equals("-") || ui.editBuffer.equals("."))
            return;
        if (!sliderTextCanCommit(setting, ui.editBuffer)) pulseSliderError(ui);
    }

    private static void setSliderEditCursorFromMouse(SliderSetting<?> setting, float mx, boolean shift) {
        SliderSetting.UiState ui = setting.ui();
        TextRenderer font = modules() ? UnifiedSettingsSkin.fontRegular() : ClickGuiRenderer.getIosevkaRegular();
        int cursor = caretFromText(font, ui.editBuffer == null ? "" : ui.editBuffer, ui.valueTextSize <= 0f ? m(17f, 6.8f) : ui.valueTextSize, ui.valueTextX, mx);
        setSliderCursor(ui, cursor, shift);
    }

    private static void selectAllSliderText(SliderSetting.UiState ui) {
        ui.editSelection.begin(0, 0);
        ui.editSelection.updateCaret(0, ui.editBuffer == null ? 0 : ui.editBuffer.length());
        ui.editCursor = ui.editBuffer == null ? 0 : ui.editBuffer.length();
    }

    private static boolean hasSliderSelection(SliderSetting.UiState ui) {
        return ui.editSelection.hasRange();
    }

    private static int sliderSelStart(SliderSetting.UiState ui) {
        return hasSliderSelection(ui) ? Math.max(0, Math.min(ui.editSelection.start(), ui.editBuffer.length())) : -1;
    }

    private static int sliderSelEnd(SliderSetting.UiState ui) {
        return hasSliderSelection(ui) ? Math.max(0, Math.min(ui.editSelection.end(), ui.editBuffer.length())) : -1;
    }

    private static String selectedOrAllSliderText(SliderSetting.UiState ui) {
        if (hasSliderSelection(ui)) return ui.editBuffer.substring(sliderSelStart(ui), sliderSelEnd(ui));
        return ui.editBuffer == null ? "" : ui.editBuffer;
    }

    private static boolean deleteSliderSelection(SliderSetting.UiState ui) {
        if (!hasSliderSelection(ui)) return false;
        int start = sliderSelStart(ui);
        int end = sliderSelEnd(ui);
        ui.editBuffer = ui.editBuffer.substring(0, start) + ui.editBuffer.substring(end);
        ui.editCursor = start;
        ui.editSelection.clear();
        return true;
    }

    private static <N extends Number> void insertSliderText(SliderSetting<N> setting, String text) {
        String sanitized = sanitizeSliderText(text, sliderInteger(setting));
        if (sanitized.isEmpty()) {
            pulseSliderError(setting.ui());
            return;
        }
        SliderSetting.UiState ui = setting.ui();
        if (hasSliderSelection(ui)) deleteSliderSelection(ui);
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c == '.' && (sliderInteger(setting) || sliderTextHasDecimal(ui.editBuffer))) {
                pulseSliderError(ui);
                continue;
            }
            if (c == '-' && (setting.value().getMin().doubleValue() >= 0.0 || ui.editCursor != 0 || ui.editBuffer.indexOf('-') >= 0)) {
                pulseSliderError(ui);
                continue;
            }
            insertSliderTextRaw(ui, String.valueOf(c));
        }
        validateSliderLive(setting, ui);
    }

    private static void insertSliderTextRaw(SliderSetting.UiState ui, String text) {
        if (text == null || text.isEmpty()) return;
        deleteSliderSelection(ui);
        int cursor = Math.max(0, Math.min(ui.editCursor, ui.editBuffer.length()));
        ui.editBuffer = ui.editBuffer.substring(0, cursor) + text + ui.editBuffer.substring(cursor);
        ui.editCursor = cursor + text.length();
        ui.editSelection.clear();
    }

    private static void moveSliderCursor(SliderSetting.UiState ui, int dir, boolean shift) {
        setSliderCursor(ui, Math.max(0, Math.min(ui.editBuffer.length(), ui.editCursor + dir)), shift);
    }

    private static void setSliderCursor(SliderSetting.UiState ui, int pos, boolean shift) {
        int next = Math.max(0, Math.min(ui.editBuffer == null ? 0 : ui.editBuffer.length(), pos));
        if (shift) {
            if (!ui.editSelection.hasRange()) ui.editSelection.begin(0, ui.editCursor);
            ui.editSelection.updateCaret(0, next);
        } else {
            ui.editSelection.clear();
        }
        ui.editCursor = next;
        ui.cursorBlink = 0f;
    }

    private static void pulseSliderError(SliderSetting.UiState ui) {
        ui.errorAnim = 1f;
    }

    private static <N extends Number> void setSliderValueFromMouse(SliderSetting<N> setting, float mx, float trackX, float trackW, double min, double max) {
        float percent = UnifiedSettingsSkin.clamp01((mx - trackX) / Math.max(1f, trackW));
        double nextRaw = min + (max - min) * percent;
        if (setting.value().get() instanceof Integer || setting.value().get() instanceof Long)
            nextRaw = Math.round(nextRaw);
        else nextRaw = Math.round(nextRaw * 100d) / 100d;
        N current = setting.value().get();
        N next = setting.value().castToType(nextRaw);
        if (Double.compare(current.doubleValue(), next.doubleValue()) != 0) {
            setting.value().set(next);
            GuiSound.SLIDER_MOVE.feedback();
        }
    }

    static void render(ModeSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        ModeSetting.UiState ui = setting.ui();
        ui.baseX = x;
        ui.baseY = y;
        ui.baseW = w;
        layoutMode(setting);

        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(17f, 6.5f);
        ClickGuiRenderer.drawText(font, setting.getDisplayName(), x + m(5f, 5f), y + m(4f, 1.3f), size, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        renderOptionContainer(x + m(5f, 5f), y + m(24f, 9.7f), ui.selectW, optionContainerHeightMode(ui), false);

        for (ModeSetting.OptionLayout opt : ui.optionLayouts) {
            float ox = x + opt.x();
            float oy = y + opt.y();
            boolean active = opt.getId().equals(setting.selectedId());
            boolean hover = UnifiedSettingsSkin.inside(mx, my, ox - m(2f, 2f), oy - m(1f, 1f), opt.w() + m(4f, 4f), m(16f, 8f));
            float hoverAnim = AnimationUtility.approach(ui.hoverAnims.getOrDefault(opt.getId(), 0f), hover ? 1f : 0f, 0.20f);
            ui.hoverAnims.put(opt.getId(), hoverAnim);
            int color = active ? UnifiedSettingsSkin.ACCENT : UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, hoverAnim * 0.50f);
            ClickGuiRenderer.drawText(font, opt.label(), ox, oy, size, color, false);
        }
    }

    // ------------------------------------------------------------
    // Mode / Group chips
    // ------------------------------------------------------------

    static void mouseClicked(ModeSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        ModeSetting.UiState ui = setting.ui();
        for (ModeSetting.OptionLayout opt : ui.optionLayouts) {
            float ox = ui.baseX + opt.x();
            float oy = ui.baseY + opt.y();
            if (!UnifiedSettingsSkin.inside(mx, my, ox - m(2f, 2f), oy - m(1f, 1f), opt.w() + m(4f, 4f), m(16f, 8f)))
                continue;
            String before = setting.selectedId();
            setting.selectId(opt.getId());
            if (!opt.getId().equals(before)) GuiSound.CHANGE_MODE.feedback();
            if (setting.onChange() != null) setting.onChange().run();
            if (setting.getParent() != null) setting.getParent().saveConfig();
            return;
        }
    }

    static float getHeight(ModeSetting setting) {
        layoutMode(setting);
        return setting.ui().cachedHeight;
    }

    private static void layoutMode(ModeSetting setting) {
        ModeSetting.UiState ui = setting.ui();
        float width = ui.baseW > 0f ? ui.baseW : m(160f, 115f);
        int count = setting.optionIds().size();
        if (Float.isFinite(ui.lastLayoutW) && Math.abs(ui.lastLayoutW - width) <= 0.5f && ui.layoutOptionCount == count)
            return;
        ui.optionLayouts.clear();
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(17f, 6.5f);
        float maxW = Math.max(1f, width - m(10f, 10f));
        float offsetX = 0f, offsetY = 0f, maxRowW = 0f;
        for (String id : setting.optionIds()) {
            String label = setting.getOptionDisplayName(id);
            float tw = UnifiedSettingsSkin.textWidth(font, label, size) + m(2f, 2f);
            if (offsetX + tw >= maxW && offsetX > 0f) {
                maxRowW = Math.max(maxRowW, offsetX);
                offsetX = 0f;
                offsetY += m(16f, 8f);
            }
            ui.optionLayouts.add(new ModeSetting.OptionLayout(id, label, m(7f, 6f) + offsetX, m(27f, 10.7f) + offsetY, tw));
            offsetX += tw;
        }
        maxRowW = Math.max(maxRowW, offsetX);
        ui.selectW = Math.max(0f, maxRowW + m(2f, 2f));
        ui.cachedHeight = m(45f, 22f) + offsetY;
        ui.lastLayoutW = width;
        ui.layoutOptionCount = count;
    }

    static void render(GroupSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        GroupSetting.UiState ui = setting.ui();
        ui.baseX = x;
        ui.baseY = y;
        ui.baseW = w;
        layoutGroup(setting);

        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(17f, 6.5f);
        ClickGuiRenderer.drawText(font, setting.getDisplayName(), x + m(5f, 5f), y + m(4f, 1.7f), size, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        renderOptionContainer(x + m(6f, 6f), y + m(24f, 9.7f), Math.max(1f, containerWidth(ui.optionLayouts)), optionContainerHeightGroup(ui), false);

        for (GroupSetting.OptionLayout opt : ui.optionLayouts) {
            float ox = x + opt.x();
            float oy = y + opt.y();
            boolean active = setting.value().get(opt.getId());
            boolean hover = UnifiedSettingsSkin.inside(mx, my, ox - m(2f, 2f), oy - m(1f, 1f), opt.w() + m(4f, 4f), m(16f, 8f));
            float hoverAnim = AnimationUtility.approach(ui.hoverAnims.getOrDefault(opt.getId(), 0f), hover ? 1f : 0f, 0.20f);
            float activeAnim = AnimationUtility.approach(ui.selectAnims.getOrDefault(opt.getId(), active ? 1f : 0f), active ? 1f : 0f, 0.22f);
            ui.hoverAnims.put(opt.getId(), hoverAnim);
            ui.selectAnims.put(opt.getId(), activeAnim);
            int color = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, 0.72f + hoverAnim * 0.28f);
            color = UnifiedSettingsSkin.mix(color, UnifiedSettingsSkin.ACCENT, activeAnim);
            ClickGuiRenderer.drawText(font, opt.label(), ox, oy, size, color, false);
        }
    }

    static void mouseClicked(GroupSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        GroupSetting.UiState ui = setting.ui();
        for (GroupSetting.OptionLayout opt : ui.optionLayouts) {
            float ox = ui.baseX + opt.x();
            float oy = ui.baseY + opt.y();
            if (!UnifiedSettingsSkin.inside(mx, my, ox - m(2f, 2f), oy - m(1f, 1f), opt.w() + m(4f, 4f), m(16f, 8f)))
                continue;
            setting.value().set(opt.getId(), !setting.value().get(opt.getId()));
            GuiSound.CHANGE_MODE.feedback();
            if (setting.getParent() != null) setting.getParent().saveConfig();
            return;
        }
    }

    static float getHeight(GroupSetting setting) {
        layoutGroup(setting);
        return setting.ui().cachedHeight;
    }

    private static void layoutGroup(GroupSetting setting) {
        GroupSetting.UiState ui = setting.ui();
        float width = ui.baseW > 0f ? ui.baseW : m(160f, 115f);
        int count = visibleGroupCount(setting);
        if (Float.isFinite(ui.lastLayoutW) && Math.abs(ui.lastLayoutW - width) <= 0.5f && ui.layoutOptionCount == count)
            return;
        ui.optionLayouts.clear();
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(17f, 6.5f);
        float maxW = Math.max(1f, width - m(10f, 10f));
        float offsetX = 0f, offsetY = 0f, maxRowW = 0f;
        Set<String> alive = new HashSet<>();
        for (String id : setting.value().getAll().keySet()) {
            if (!setting.isOptionVisible(id)) continue;
            String label = setting.getOptionDisplayName(id);
            float tw = UnifiedSettingsSkin.textWidth(font, label, size) + m(4f, 4f);
            if (offsetX + tw >= maxW && offsetX > 0f) {
                maxRowW = Math.max(maxRowW, offsetX);
                offsetX = 0f;
                offsetY += m(16f, 8f);
            }
            ui.optionLayouts.add(new GroupSetting.OptionLayout(id, label, m(8f, 8f) + offsetX, m(27f, 10.7f) + offsetY, tw));
            offsetX += tw;
            alive.add(id);
        }
        maxRowW = Math.max(maxRowW, offsetX);
        ui.cachedHeight = m(45f, 22f) + offsetY;
        ui.lastLayoutW = width;
        ui.layoutOptionCount = count;
        ui.hoverAnims.keySet().removeIf(key -> !alive.contains(key));
        ui.selectAnims.keySet().removeIf(key -> !alive.contains(key));
    }

    private static int visibleGroupCount(GroupSetting setting) {
        int count = 0;
        for (String id : setting.value().getAll().keySet()) if (setting.isOptionVisible(id)) count++;
        return count;
    }

    private static float containerWidth(java.util.List<GroupSetting.OptionLayout> layouts) {
        float max = 0f;
        for (GroupSetting.OptionLayout opt : layouts) max = Math.max(max, opt.x() - m(6f, 6f) + opt.w());
        return max + m(0.5f, 0.5f);
    }

    private static float optionContainerHeightMode(ModeSetting.UiState ui) {
        float max = m(10f, 10f);
        for (ModeSetting.OptionLayout opt : ui.optionLayouts) max = Math.max(max, opt.y() - m(19f, 9.7f) + m(16f, 8f));
        return max;
    }

    private static float optionContainerHeightGroup(GroupSetting.UiState ui) {
        float max = m(10f, 10f);
        for (GroupSetting.OptionLayout opt : ui.optionLayouts) max = Math.max(max, opt.y() - m(19f, 9.7f) + m(16f, 8f));
        return max;
    }

    private static void renderOptionContainer(float x, float y, float w, float h, boolean active) {
        float alpha = active ? 0.22f : 0.12f;
        if (active) {
            UnifiedSettingsSkin.drawAccent(x, y, Math.max(1f, w), Math.max(m(14f, 10f), h), m(3f, 3f), alpha);
        } else {
            UnifiedSettingsSkin.drawSurface(x, y, Math.max(1f, w), Math.max(m(14f, 10f), h), m(3f, 3f), 0.34f);
        }
    }

    static void render(TextSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        TextSetting.UiState ui = setting.ui();
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float labelSize = m(15.5f, 6.3f);
        float fieldSize = m(14f, 6.1f);
        float fx = x + m(5f, 5f);
        float fy = y + m(23f, 11.5f);
        float fw = Math.max(1f, w - m(10f, 10f));
        float fh = m(20f, 13f);
        String value = setting.getEditorText();
        ui.fieldX = fx;
        ui.fieldY = fy;
        ui.fieldW = fw;
        ui.fieldH = fh;

        boolean hover = UnifiedSettingsSkin.inside(mx, my, fx, fy, fw, fh);
        float dt = AnimationUtility.deltaTime();
        ui.hoverAnim = AnimationUtility.approach(ui.hoverAnim, hover ? 1f : 0f, dt, 13f);
        ui.focusAnim = AnimationUtility.approach(ui.focusAnim, ui.editing ? 1f : 0f, dt, 12f);
        ui.errorAnim = AnimationUtility.approach(ui.errorAnim, 0f, dt, 7f);
        if (ui.editing) ui.cursorBlink += dt * 2.2f;
        else ui.cursorBlink = 0f;

        ClickGuiRenderer.drawText(font, UnifiedSettingsSkin.fit(font, setting.getDisplayName(), labelSize, fw), fx, y + m(5f, 2.5f), labelSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        float fieldAnim = Math.max(ui.hoverAnim * 0.65f, ui.focusAnim);
        int bg = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE_SOFT, UnifiedSettingsSkin.SURFACE_HOVER, fieldAnim), 56 + Math.round(fieldAnim * 42f));
        int stroke = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 58), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, 155), ui.focusAnim);
        stroke = UnifiedSettingsSkin.mix(stroke, 0xFFFF5F66, ui.errorAnim);
        if (UnifiedSettingsSkin.SURFACE_GRADIENT_ENABLED) {
            UnifiedSettingsSkin.drawSurface(fx, fy, fw, fh, m(5f, 3.5f), 0.55f + fieldAnim * 0.32f);
        } else {
            ClickGuiRenderer.drawRoundedRect(fx, fy, fw, fh, m(5f, 3.5f), bg);
        }
        ClickGuiRenderer.drawRoundedRectStroke(fx, fy, fw, fh, m(5f, 3.5f), m(0.7f, 0.45f), stroke);

        String text = ui.editing ? ui.editBuffer : (value == null ? "" : value);
        String placeholder = "text...";
        float pad = m(6f, 4f);
        float textX = fx + pad;
        float textY = fy + (fh - UnifiedSettingsSkin.textHeight(font, fieldSize)) * 0.5f - m(0.2f, 0.15f);
        float clipW = Math.max(1f, fw - pad * 2f);
        if (ui.editing) ensureTextCursorVisible(ui, font, ui.editBuffer, fieldSize, clipW);
        else ui.textScroll = 0f;
        float drawX = textX - ui.textScroll;
        ui.textX = drawX;
        ui.textY = textY;
        ui.textSize = fieldSize;
        boolean clip = ScissorFunction.pushRaw(textX, fy, clipW, fh);
        if (ui.editing) {
            renderInlineSelection(font, ui.editBuffer, textSelStart(ui), textSelEnd(ui), drawX, textY, fieldSize, fh, fy);
            ClickGuiRenderer.drawText(font, ui.editBuffer, drawX, textY, fieldSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
            if (((int) (ui.cursorBlink * 2f) & 1) == 0) {
                int cursor = Math.max(0, Math.min(ui.editCursor, ui.editBuffer.length()));
                float cx = drawX + UnifiedSettingsSkin.textWidth(font, ui.editBuffer.substring(0, cursor), fieldSize) + m(0.5f, 0.3f);
                ClickGuiRenderer.drawRect(cx, fy + m(3f, 2f), m(0.65f, 0.4f), fh - m(6f, 4f), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_PRIMARY, 205));
            }
        } else {
            boolean empty = text.isBlank();
            String draw = empty ? placeholder : UnifiedSettingsSkin.fit(font, text, fieldSize, fw - pad * 2f);
            ClickGuiRenderer.drawText(font, draw, textX, textY, fieldSize, empty ? UnifiedSettingsSkin.TEXT_FAINT : UnifiedSettingsSkin.TEXT_PRIMARY, false);
        }
        if (clip) ScissorFunction.pop();
    }

    // ------------------------------------------------------------
    // Text / TextList
    // ------------------------------------------------------------

    static void mouseClicked(TextSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        TextSetting.UiState ui = setting.ui();
        if (UnifiedSettingsSkin.inside(mx, my, ui.fieldX, ui.fieldY, ui.fieldW, ui.fieldH)) {
            beginTextEdit(setting);
            setTextCursorFromMouse(setting, (float) mx, false);
        } else if (ui.editing) {
            commitTextEdit(setting, true);
        }
    }

    static void mouseClickedOutside(TextSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        if (setting.ui().editing) commitTextEdit(setting, true);
    }

    static boolean keyPressed(TextSetting setting, int keyCode, int scanCode, int modifiers) {
        TextSetting.UiState ui = setting.ui();
        if (!ui.editing) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAllText(ui);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            ClipboardUtil.copy(selectedOrAllText(ui));
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            insertTextText(setting, ClipboardUtil.get());
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            ClipboardUtil.copy(selectedOrAllText(ui));
            deleteTextSelection(ui);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitTextEdit(setting, true);
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                cancelTextEdit(setting);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteTextSelection(ui) && ui.editCursor > 0) {
                    ui.editBuffer = ui.editBuffer.substring(0, ui.editCursor - 1) + ui.editBuffer.substring(ui.editCursor);
                    ui.editCursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!deleteTextSelection(ui) && ui.editCursor < ui.editBuffer.length())
                    ui.editBuffer = ui.editBuffer.substring(0, ui.editCursor) + ui.editBuffer.substring(ui.editCursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                setTextCursor(ui, ui.editCursor - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                setTextCursor(ui, ui.editCursor + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                setTextCursor(ui, 0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                setTextCursor(ui, ui.editBuffer.length(), shift);
                return true;
            }
        }
        return true;
    }

    static boolean charTyped(TextSetting setting, char chr, int modifiers) {
        TextSetting.UiState ui = setting.ui();
        if (!ui.editing) return false;
        if (chr == '\n' || chr == '\r') return true;
        insertTextText(setting, String.valueOf(chr));
        return true;
    }

    static float getHeight(TextSetting setting) {
        return m(50f, 28f);
    }

    private static void beginTextEdit(TextSetting setting) {
        TextSetting.UiState ui = setting.ui();
        if (!ui.editing) {
            ui.editBuffer = setting.getEditorText();
            ui.editCursor = ui.editBuffer.length();
            ui.editSelection.clear();
            ui.errorAnim = 0f;
        }
        ui.editing = true;
        ui.cursorBlink = 0f;
    }

    private static void cancelTextEdit(TextSetting setting) {
        TextSetting.UiState ui = setting.ui();
        ui.editing = false;
        ui.editBuffer = setting.getEditorText();
        ui.editCursor = ui.editBuffer.length();
        ui.textScroll = 0f;
        ui.editSelection.clear();
        ui.errorAnim = 0f;
    }

    private static void commitTextEdit(TextSetting setting, boolean save) {
        TextSetting.UiState ui = setting.ui();
        setting.applyEditorText(ui.editBuffer == null ? "" : ui.editBuffer);
        ui.editing = false;
        ui.textScroll = 0f;
        ui.editSelection.clear();
        if (!save && setting.getParent() != null) setting.getParent().saveConfig();
    }

    private static void setTextCursorFromMouse(TextSetting setting, float mx, boolean shift) {
        TextSetting.UiState ui = setting.ui();
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        int cursor = caretFromText(font, ui.editBuffer == null ? "" : ui.editBuffer, ui.textSize <= 0f ? m(14f, 6.1f) : ui.textSize, ui.textX, mx);
        setTextCursor(ui, cursor, shift);
    }

    private static void setTextCursor(TextSetting.UiState ui, int pos, boolean shift) {
        int next = Math.max(0, Math.min(ui.editBuffer == null ? 0 : ui.editBuffer.length(), pos));
        if (shift) {
            if (!ui.editSelection.hasRange()) ui.editSelection.begin(0, ui.editCursor);
            ui.editSelection.updateCaret(0, next);
        } else {
            ui.editSelection.clear();
        }
        ui.editCursor = next;
        ui.cursorBlink = 0f;
    }

    private static void insertTextText(TextSetting setting, String text) {
        TextSetting.UiState ui = setting.ui();
        if (text == null || text.isEmpty()) return;
        deleteTextSelection(ui);
        String clean = text.replace("\r", "").replace("\n", " ");
        int cursor = Math.max(0, Math.min(ui.editCursor, ui.editBuffer.length()));
        ui.editBuffer = ui.editBuffer.substring(0, cursor) + clean + ui.editBuffer.substring(cursor);
        ui.editCursor = cursor + clean.length();
        ui.editSelection.clear();
    }

    private static void ensureTextCursorVisible(TextSetting.UiState ui, TextRenderer font, String text, float size, float visibleW) {
        String safe = text == null ? "" : text;
        int cursor = Math.max(0, Math.min(ui.editCursor, safe.length()));
        float textW = UnifiedSettingsSkin.textWidth(font, safe, size);
        float maxScroll = Math.max(0f, textW - visibleW);
        float caretX = UnifiedSettingsSkin.textWidth(font, safe.substring(0, cursor), size);
        float margin = Math.min(m(8f, 4f), visibleW * 0.35f);

        if (caretX - ui.textScroll > visibleW - margin) {
            ui.textScroll = caretX - visibleW + margin;
        } else if (caretX - ui.textScroll < margin) {
            ui.textScroll = caretX - margin;
        }
        ui.textScroll = Math.max(0f, Math.min(maxScroll, ui.textScroll));
    }

    private static void selectAllText(TextSetting.UiState ui) {
        ui.editSelection.begin(0, 0);
        ui.editSelection.updateCaret(0, ui.editBuffer == null ? 0 : ui.editBuffer.length());
        ui.editCursor = ui.editBuffer == null ? 0 : ui.editBuffer.length();
    }

    private static String selectedOrAllText(TextSetting.UiState ui) {
        if (hasTextSelection(ui)) return ui.editBuffer.substring(textSelStart(ui), textSelEnd(ui));
        return ui.editBuffer == null ? "" : ui.editBuffer;
    }

    private static boolean hasTextSelection(TextSetting.UiState ui) {
        return ui.editSelection.hasRange();
    }

    private static int textSelStart(TextSetting.UiState ui) {
        return hasTextSelection(ui) ? Math.max(0, Math.min(ui.editSelection.start(), ui.editBuffer.length())) : -1;
    }

    private static int textSelEnd(TextSetting.UiState ui) {
        return hasTextSelection(ui) ? Math.max(0, Math.min(ui.editSelection.end(), ui.editBuffer.length())) : -1;
    }

    private static boolean deleteTextSelection(TextSetting.UiState ui) {
        if (!hasTextSelection(ui)) return false;
        int start = textSelStart(ui);
        int end = textSelEnd(ui);
        ui.editBuffer = ui.editBuffer.substring(0, start) + ui.editBuffer.substring(end);
        ui.editCursor = start;
        ui.editSelection.clear();
        return true;
    }

    static void render(TextListSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        TextListSetting.UiState ui = setting.ui();
        ui.lastX = x;
        ui.lastY = y;
        ui.lastW = w;

        if (isInlineTextList(setting)) {
            renderInlineTextList(setting, x, y, w, mx, my);
            return;
        }

        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float labelSize = m(15.5f, 6.4f);
        float small = m(11.5f, 5.7f);
        float bh = m(18f, 13f);
        float editW = Math.max(m(36f, 28f), UnifiedSettingsSkin.textWidth(font, "Edit", small) + m(12f, 12f));
        float labelX = x + m(5f, 5f);
        float labelMax = Math.max(m(50f, 34f), w - editW - m(22f, 16f));
        java.util.List<String> labelLines = ClickGuiRenderer.wrapText(font, setting.getDisplayName(), labelSize, labelMax, 2);
        if (labelLines.isEmpty()) labelLines = java.util.List.of(setting.getDisplayName());
        float lineH = UnifiedSettingsSkin.textHeight(font, labelSize);
        float lineGap = m(2f, 0.7f);
        float textBlockH = labelLines.size() * lineH + Math.max(0, labelLines.size() - 1) * lineGap;
        float rowH = Math.max(m(44f, 24f), textBlockH + m(14f, 8f));
        float bx = x + w - m(9f, 9f) - editW;
        float by = y + (rowH - bh) * 0.5f;
        ui.lastButtonX = bx;
        ui.lastButtonY = by;
        ui.lastButtonW = editW;
        ui.lastButtonH = bh;
        boolean hover = UnifiedSettingsSkin.inside(mx, my, bx, by, editW, bh);
        ui.moduleHoverAnim = AnimationUtility.approach(ui.moduleHoverAnim, hover ? 1f : 0f, AnimationUtility.deltaTime(), 13f);
        float labelY = y + (rowH - textBlockH) * 0.5f;
        for (int i = 0; i < labelLines.size(); i++) {
            ClickGuiRenderer.drawText(font, labelLines.get(i), labelX, labelY + i * (lineH + lineGap), labelSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        }
        renderChipButton(bx, by, editW, bh, "Edit", ui.moduleHoverAnim, false);
    }

    private static void renderInlineTextList(TextListSetting setting, float x, float y, float w, float mx, float my) {
        TextListSetting.UiState ui = setting.ui();
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float labelSize = m(15.5f, 6.3f);
        float fieldSize = m(14f, 6.1f);
        float rowH = inlineTextListHeight();
        String label = setting.getDisplayName();
        float labelY = y + m(5f, 2.5f);
        float fieldY = y + m(23f, 11.5f);
        float fieldH = m(20f, 13f);
        float fieldX = x + m(5f, 5f);
        float fieldW = Math.max(1f, w - m(10f, 10f));
        ui.fieldX = fieldX;
        ui.fieldY = fieldY;
        ui.fieldW = fieldW;
        ui.fieldH = fieldH;
        boolean hover = UnifiedSettingsSkin.inside(mx, my, fieldX, fieldY, fieldW, fieldH);
        float dt = AnimationUtility.deltaTime();
        ui.fieldHoverAnim = AnimationUtility.approach(ui.fieldHoverAnim, hover ? 1f : 0f, dt, 13f);
        ui.fieldFocusAnim = AnimationUtility.approach(ui.fieldFocusAnim, ui.editing ? 1f : 0f, dt, 12f);
        ui.fieldErrorAnim = AnimationUtility.approach(ui.fieldErrorAnim, 0f, dt, 7f);
        if (ui.editing) ui.cursorBlink += dt * 2.2f;
        else ui.cursorBlink = 0f;

        ClickGuiRenderer.drawText(font, UnifiedSettingsSkin.fit(font, label, labelSize, fieldW), fieldX, labelY, labelSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        float fieldAnim = Math.max(ui.fieldHoverAnim * 0.65f, ui.fieldFocusAnim);
        int bg = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE_SOFT, UnifiedSettingsSkin.SURFACE_HOVER, fieldAnim), 56 + Math.round(fieldAnim * 42f));
        int stroke = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 58), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, 155), ui.fieldFocusAnim);
        stroke = UnifiedSettingsSkin.mix(stroke, 0xFFFF5F66, ui.fieldErrorAnim);
        if (UnifiedSettingsSkin.SURFACE_GRADIENT_ENABLED) {
            UnifiedSettingsSkin.drawSurface(fieldX, fieldY, fieldW, fieldH, m(5f, 3.5f), 0.55f + fieldAnim * 0.32f);
        } else {
            ClickGuiRenderer.drawRoundedRect(fieldX, fieldY, fieldW, fieldH, m(5f, 3.5f), bg);
        }
        ClickGuiRenderer.drawRoundedRectStroke(fieldX, fieldY, fieldW, fieldH, m(5f, 3.5f), m(0.7f, 0.45f), stroke);

        String current = firstTextListEntry(setting);
        String text = ui.editing ? ui.editBuffer : current;
        if (text == null) text = "";
        String placeholder = "value...";
        float pad = m(6f, 4f);
        float textX = fieldX + pad;
        float textY = fieldY + (fieldH - UnifiedSettingsSkin.textHeight(font, fieldSize)) * 0.5f - m(0.2f, 0.15f);
        boolean clip = ScissorFunction.pushRaw(textX, fieldY, Math.max(1f, fieldW - pad * 2f), fieldH);
        if (ui.editing) {
            renderInlineSelection(font, ui.editBuffer, textListSelStart(ui), textListSelEnd(ui), textX, textY, fieldSize, fieldH, fieldY);
            ClickGuiRenderer.drawText(font, ui.editBuffer, textX, textY, fieldSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
            if (((int) (ui.cursorBlink * 2f) & 1) == 0) {
                int cursor = Math.max(0, Math.min(ui.editCursor, ui.editBuffer.length()));
                float cx = textX + UnifiedSettingsSkin.textWidth(font, ui.editBuffer.substring(0, cursor), fieldSize) + m(0.5f, 0.3f);
                ClickGuiRenderer.drawRect(cx, fieldY + m(3f, 2f), m(0.65f, 0.4f), fieldH - m(6f, 4f), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_PRIMARY, 205));
            }
        } else {
            boolean empty = text.isBlank();
            String draw = empty ? placeholder : UnifiedSettingsSkin.fit(font, text, fieldSize, fieldW - pad * 2f);
            ClickGuiRenderer.drawText(font, draw, textX, textY, fieldSize, empty ? UnifiedSettingsSkin.TEXT_FAINT : UnifiedSettingsSkin.TEXT_PRIMARY, false);
        }
        if (clip) ScissorFunction.pop();
    }

    static void mouseClicked(TextListSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        TextListSetting.UiState ui = setting.ui();
        if (isInlineTextList(setting)) {
            if (UnifiedSettingsSkin.inside(mx, my, ui.fieldX, ui.fieldY, ui.fieldW, ui.fieldH)) {
                beginTextListEdit(setting);
                setTextListCursorFromMouse(setting, (float) mx, false);
            } else if (ui.editing) {
                commitTextListEdit(setting, true);
            }
            return;
        }
        if (!UnifiedSettingsSkin.inside(mx, my, ui.lastButtonX, ui.lastButtonY, ui.lastButtonW, ui.lastButtonH)) return;
        if (setting.getPickerMode() == TextListSetting.PickerMode.TEXT) {
            ClickGuiRenderer.openTextEditor(setting, setting.getDisplayName(), setting.getEditorText(), ui.lastButtonX, ui.lastButtonY, ui.lastButtonW);
        } else {
            ClickGuiRenderer.openPicker(setting, setting.getDisplayName(), setting.getPickerMode());
        }
    }

    static void mouseClickedOutside(TextListSetting setting, double mx, double my, int button) {
        if (button != 0 || !isInlineTextList(setting)) return;
        if (setting.ui().editing) commitTextListEdit(setting, true);
    }

    static boolean keyPressed(TextListSetting setting, int keyCode, int scanCode, int modifiers) {
        if (!isInlineTextList(setting)) return false;
        TextListSetting.UiState ui = setting.ui();
        if (!ui.editing) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAllTextList(ui);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            ClipboardUtil.copy(selectedOrAllTextList(ui));
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            insertTextListText(setting, ClipboardUtil.get());
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            ClipboardUtil.copy(selectedOrAllTextList(ui));
            deleteTextListSelection(ui);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitTextListEdit(setting, true);
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                cancelTextListEdit(setting);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteTextListSelection(ui) && ui.editCursor > 0) {
                    ui.editBuffer = ui.editBuffer.substring(0, ui.editCursor - 1) + ui.editBuffer.substring(ui.editCursor);
                    ui.editCursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!deleteTextListSelection(ui) && ui.editCursor < ui.editBuffer.length())
                    ui.editBuffer = ui.editBuffer.substring(0, ui.editCursor) + ui.editBuffer.substring(ui.editCursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                setTextListCursor(ui, ui.editCursor - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                setTextListCursor(ui, ui.editCursor + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                setTextListCursor(ui, 0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                setTextListCursor(ui, ui.editBuffer.length(), shift);
                return true;
            }
        }
        return true;
    }

    static boolean charTyped(TextListSetting setting, char chr, int modifiers) {
        if (!isInlineTextList(setting)) return false;
        TextListSetting.UiState ui = setting.ui();
        if (!ui.editing) return false;
        if (chr == '\n' || chr == '\r') return true;
        insertTextListText(setting, String.valueOf(chr));
        return true;
    }

    static float getHeight(TextListSetting setting) {
        return isInlineTextList(setting) ? inlineTextListHeight() : textListButtonHeight(setting);
    }

    private static boolean isInlineTextList(TextListSetting setting) {
        return setting.getPickerMode() == TextListSetting.PickerMode.TEXT && setting.isSingleLine();
    }

    private static float inlineTextListHeight() {
        return m(50f, 28f);
    }

    private static String firstTextListEntry(TextListSetting setting) {
        Set<String> set = setting.getValueSet();
        if (set == null || set.isEmpty()) return "";
        return set.iterator().next();
    }

    private static float textListButtonHeight(TextListSetting setting) {
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float width = setting.ui().lastW > 0f ? setting.ui().lastW : m(170f, 115f);
        float labelSize = m(15.5f, 6.4f);
        float small = m(11.5f, 5.7f);
        float editW = Math.max(m(36f, 28f), UnifiedSettingsSkin.textWidth(font, "Edit", small) + m(12f, 12f));
        float labelMax = Math.max(m(50f, 34f), width - editW - m(22f, 16f));
        java.util.List<String> labelLines = ClickGuiRenderer.wrapText(font, setting.getDisplayName(), labelSize, labelMax, 2);
        int lines = Math.max(1, labelLines.size());
        float lineH = UnifiedSettingsSkin.textHeight(font, labelSize);
        float textBlockH = lines * lineH + Math.max(0, lines - 1) * m(2f, 0.7f);
        return Math.max(m(44f, 24f), textBlockH + m(14f, 8f));
    }

    private static void beginTextListEdit(TextListSetting setting) {
        TextListSetting.UiState ui = setting.ui();
        if (!ui.editing) {
            ui.editBuffer = firstTextListEntry(setting);
            ui.editCursor = ui.editBuffer.length();
            ui.editSelection.clear();
            ui.fieldErrorAnim = 0f;
        }
        ui.editing = true;
        ui.cursorBlink = 0f;
    }

    private static void cancelTextListEdit(TextListSetting setting) {
        TextListSetting.UiState ui = setting.ui();
        ui.editing = false;
        ui.editBuffer = firstTextListEntry(setting);
        ui.editCursor = ui.editBuffer.length();
        ui.editSelection.clear();
        ui.fieldErrorAnim = 0f;
    }

    private static void commitTextListEdit(TextListSetting setting, boolean save) {
        TextListSetting.UiState ui = setting.ui();
        String raw = ui.editBuffer == null ? "" : ui.editBuffer.trim();
        java.util.LinkedHashSet<String> next = new java.util.LinkedHashSet<>();
        if (!raw.isEmpty()) next.add(setting.normalizeEntry(raw));
        setting.value().set(next);
        ui.editing = false;
        ui.editSelection.clear();
        if (save && setting.getParent() != null) setting.getParent().saveConfig();
    }

    private static void setTextListCursorFromMouse(TextListSetting setting, float mx, boolean shift) {
        TextListSetting.UiState ui = setting.ui();
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(14f, 6.1f);
        int cursor = caretFromText(font, ui.editBuffer == null ? "" : ui.editBuffer, size, ui.fieldX + m(6f, 4f), mx);
        setTextListCursor(ui, cursor, shift);
    }

    private static void setTextListCursor(TextListSetting.UiState ui, int pos, boolean shift) {
        int next = Math.max(0, Math.min(ui.editBuffer == null ? 0 : ui.editBuffer.length(), pos));
        if (shift) {
            if (!ui.editSelection.hasRange()) ui.editSelection.begin(0, ui.editCursor);
            ui.editSelection.updateCaret(0, next);
        } else {
            ui.editSelection.clear();
        }
        ui.editCursor = next;
        ui.cursorBlink = 0f;
    }

    private static void insertTextListText(TextListSetting setting, String text) {
        TextListSetting.UiState ui = setting.ui();
        if (text == null || text.isEmpty()) return;
        deleteTextListSelection(ui);
        String clean = text.replace("\r", "").replace("\n", " ");
        int cursor = Math.max(0, Math.min(ui.editCursor, ui.editBuffer.length()));
        ui.editBuffer = ui.editBuffer.substring(0, cursor) + clean + ui.editBuffer.substring(cursor);
        ui.editCursor = cursor + clean.length();
        ui.editSelection.clear();
    }

    private static void selectAllTextList(TextListSetting.UiState ui) {
        ui.editSelection.begin(0, 0);
        ui.editSelection.updateCaret(0, ui.editBuffer == null ? 0 : ui.editBuffer.length());
        ui.editCursor = ui.editBuffer == null ? 0 : ui.editBuffer.length();
    }

    private static String selectedOrAllTextList(TextListSetting.UiState ui) {
        if (hasTextListSelection(ui)) return ui.editBuffer.substring(textListSelStart(ui), textListSelEnd(ui));
        return ui.editBuffer == null ? "" : ui.editBuffer;
    }

    private static boolean hasTextListSelection(TextListSetting.UiState ui) {
        return ui.editSelection.hasRange();
    }

    private static int textListSelStart(TextListSetting.UiState ui) {
        return hasTextListSelection(ui) ? Math.max(0, Math.min(ui.editSelection.start(), ui.editBuffer.length())) : -1;
    }

    private static int textListSelEnd(TextListSetting.UiState ui) {
        return hasTextListSelection(ui) ? Math.max(0, Math.min(ui.editSelection.end(), ui.editBuffer.length())) : -1;
    }

    private static boolean deleteTextListSelection(TextListSetting.UiState ui) {
        if (!hasTextListSelection(ui)) return false;
        int start = textListSelStart(ui);
        int end = textListSelEnd(ui);
        ui.editBuffer = ui.editBuffer.substring(0, start) + ui.editBuffer.substring(end);
        ui.editCursor = start;
        ui.editSelection.clear();
        return true;
    }

    static void render(KeyBindSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        KeyBindSetting.UiState ui = setting.ui();
        String pending = ClickGuiRenderer.getPendingBindDisplay();
        String display = ui.waiting ? (pending == null ? "..." : pending) : setting.getValue().get();
        renderBindBase(setting.getDisplayName(), display, null, ui.waiting, x, y, w, mx, my, ui);
    }

    // ------------------------------------------------------------
    // Key binds
    // ------------------------------------------------------------

    static void mouseClicked(KeyBindSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        KeyBindSetting.UiState ui = setting.ui();
        if (!UnifiedSettingsSkin.inside(mx, my, ui.lastBx, ui.lastBy, ui.lastBw, ui.lastBh)) return;
        ui.waiting = true;
        ClickGuiRenderer.beginKeyBind(setting);
    }

    static float getHeight(KeyBindSetting setting) {
        return bindHeight(setting.ui().lastW, setting.getDisplayName(), setting.getValue().get(), null);
    }

    static void render(FunctionBindSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        FunctionBindSetting.UiState ui = setting.ui();
        String pending = ClickGuiRenderer.getPendingBindDisplay();
        String display = ui.waiting ? (pending == null ? "..." : pending) : setting.get();
        String modeText = setting.mode() == BindMode.HOLD ? "Hold" : "Press";
        renderBindBase(setting.getDisplayName(), display, modeText, ui.waiting, x, y, w, mx, my, ui);
    }

    static void mouseClicked(FunctionBindSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        FunctionBindSetting.UiState ui = setting.ui();
        if (!UnifiedSettingsSkin.inside(mx, my, ui.lastBx, ui.lastBy, ui.lastBw, ui.lastBh)) return;
        ui.waiting = true;
        ClickGuiRenderer.beginKeyBind(setting);
    }

    static float getHeight(FunctionBindSetting setting) {
        String modeText = setting.mode() == BindMode.HOLD ? "Hold" : "Press";
        return bindHeight(setting.ui().lastW, setting.getDisplayName(), setting.get(), modeText);
    }

    private static void renderBindBase(String label, String display, String modeText, boolean waiting, float x, float y, float w, float mx, float my, Object uiObj) {
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(17f, 7.0f);
        float small = m(11.5f, 5.7f);
        String displayText = display == null || display.isBlank() ? "NONE" : display;
        boolean none = displayText.equalsIgnoreCase("NONE");
        float pad = m(6f, 5f);
        float gap = m(4f, 4f);
        float chipPad = m(7f, 5f);
        float chipH = m(22f, 13.5f);
        float minLabelW = m(72f, 38f);
        float modeW = modeText == null ? 0f : Math.max(m(36f, 25f), UnifiedSettingsSkin.textWidth(font, modeText, small) + m(10f, 8f));
        float modeBlockW = modeW > 0f ? modeW + gap : 0f;
        float rawTextW = UnifiedSettingsSkin.textWidth(font, displayText, size);
        float wantedChipW = rawTextW + chipPad * 2f;

        float sameLineMaxChipW = Math.max(m(48f, 30f), w - pad * 3f - modeBlockW - minLabelW);
        boolean nextLine = sameLineMaxChipW < m(48f, 30f);
        float maxChipW = nextLine
                ? Math.max(m(48f, 30f), w - pad * 2f - modeBlockW)
                : sameLineMaxChipW;
        float chipW = Math.max(m(48f, 30f), Math.min(wantedChipW, maxChipW));
        float labelMax = nextLine ? w - pad * 2f : Math.max(1f, w - pad * 3f - modeBlockW - chipW);
        if (!nextLine && labelMax < minLabelW) {
            nextLine = true;
            maxChipW = Math.max(m(48f, 30f), w - pad * 2f - modeBlockW);
            chipW = Math.max(m(48f, 30f), Math.min(wantedChipW, maxChipW));
            labelMax = w - pad * 2f;
        }

        float rowH = nextLine ? m(48f, 31f) : m(38f, 22f);
        float cardX = x + m(2f, 2f);
        float cardY = y + m(3f, 2f);
        float cardW = Math.max(1f, w - m(4f, 4f));
        float cardH = Math.max(m(30f, 18f), rowH - m(6f, 4f));
        float cardRadius = m(8f, 5f);
        boolean hover = UnifiedSettingsSkin.inside(mx, my, cardX, cardY, cardW, cardH);
        float hoverAnim = bindHoverAnim(uiObj, hover);
        float waitAnim = bindWaitAnim(uiObj, waiting);

        UnifiedSettingsSkin.drawCard(cardX, cardY, cardW, cardH, cardRadius, 0.38f + hoverAnim * 0.24f + waitAnim * 0.10f);
        if (hoverAnim > 0.001f || waitAnim > 0.001f) {
            int strokeA = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, Math.round(hoverAnim * 72f));
            int strokeB = UnifiedSettingsSkin.accentGradientStart(waitAnim * 0.70f);
            ClickGuiRenderer.drawRoundedRectStroke(cardX, cardY, cardW, cardH, cardRadius, m(0.65f, 0.42f), UnifiedSettingsSkin.mix(strokeA, strokeB, Math.max(waitAnim, hoverAnim * 0.28f)));
        }

        float labelY = y + (nextLine ? m(7f, 4f) : (rowH - UnifiedSettingsSkin.textHeight(font, size)) * 0.5f);
        float chipY = nextLine ? y + m(27f, 16f) : y + (rowH - chipH) * 0.5f;
        float chipX;
        float modeX = 0f;
        float modeY = chipY;
        if (nextLine) {
            chipX = cardX + pad;
            if (modeW > 0f) modeX = x + w - pad - modeW;
        } else {
            chipX = x + w - pad - chipW;
            if (modeW > 0f) modeX = chipX - gap - modeW;
        }

        int labelColor = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, 0.72f + hoverAnim * 0.18f + waitAnim * 0.10f);
        ClickGuiRenderer.drawText(font, UnifiedSettingsSkin.fit(font, label, size, Math.max(1f, labelMax)), cardX + pad, labelY, size, labelColor, false);
        if (modeW > 0f) renderSmallModeChip(modeX, modeY, modeW, chipH, modeText, small, hoverAnim, waitAnim);

        float chipInset = m(0.8f, 0.45f);
        float chipDrawX = chipX + chipInset;
        float chipDrawY = chipY + chipInset;
        float chipDrawW = Math.max(1f, chipW - chipInset * 2f);
        float chipDrawH = Math.max(1f, chipH - chipInset * 2f);
        float chipRadius = Math.max(0f, m(6f, 4f) - chipInset);
        if (waiting) {
            UnifiedSettingsSkin.drawAccent(chipDrawX, chipDrawY, chipDrawW, chipDrawH, chipRadius, 0.62f + hoverAnim * 0.18f);
            ClickGuiRenderer.drawRoundedRectStrokeGradient(chipDrawX, chipDrawY, chipDrawW, chipDrawH, chipRadius, m(0.75f, 0.45f), UnifiedSettingsSkin.accentGradientStart(0.92f), UnifiedSettingsSkin.accentGradientEnd(0.92f), UnifiedSettingsSkin.ACCENT_GRADIENT_ANGLE);
        } else {
            UnifiedSettingsSkin.drawSurface(chipDrawX, chipDrawY, chipDrawW, chipDrawH, chipRadius, none ? 0.30f + hoverAnim * 0.12f : 0.46f + hoverAnim * 0.16f);
            int chipStroke = none
                    ? UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, Math.round(34f + hoverAnim * 36f))
                    : UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, Math.round(48f + hoverAnim * 54f));
            ClickGuiRenderer.drawRoundedRectStroke(chipDrawX, chipDrawY, chipDrawW, chipDrawH, chipRadius, m(0.65f, 0.42f), chipStroke);
        }

        float contentX = chipX + chipPad;
        float contentW = Math.max(1f, chipW - chipPad * 2f);
        float textX = contentX;
        if (rawTextW <= contentW) {
            textX = chipX + (chipW - rawTextW) * 0.5f;
        }
        float textY = chipY + (chipH - UnifiedSettingsSkin.textHeight(font, size)) * 0.5f;
        boolean textClip = ScissorFunction.pushRaw(contentX, chipY, contentW, chipH);
        int valueColor = waiting
                ? 0xFFFFFFFF
                : UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, none ? 0.20f + hoverAnim * 0.12f : 0.82f + hoverAnim * 0.12f);
        if (rawTextW > contentW + 0.5f) {
            drawBindMarquee(font, displayText, contentX, textY, rawTextW, contentW, size, valueColor);
        } else {
            ClickGuiRenderer.drawText(font, displayText, textX, textY, size, valueColor, false);
        }
        if (textClip) ScissorFunction.pop();

        if (uiObj instanceof KeyBindSetting.UiState ui) {
            ui.lastX = x;
            ui.lastY = y;
            ui.lastW = w;
            ui.lastH = rowH;
            ui.lastBx = chipX;
            ui.lastBy = chipY;
            ui.lastBw = chipW;
            ui.lastBh = chipH;
        } else if (uiObj instanceof FunctionBindSetting.UiState ui) {
            ui.lastX = x;
            ui.lastY = y;
            ui.lastW = w;
            ui.lastH = rowH;
            ui.lastBx = chipX;
            ui.lastBy = chipY;
            ui.lastBw = chipW;
            ui.lastBh = chipH;
        }
    }

    private static float bindHoverAnim(Object uiObj, boolean hover) {
        float dt = AnimationUtility.deltaTime();
        if (uiObj instanceof KeyBindSetting.UiState ui) {
            ui.hoverAnim = AnimationUtility.approach(ui.hoverAnim, hover ? 1f : 0f, dt, 12f);
            return ui.hoverAnim;
        }
        if (uiObj instanceof FunctionBindSetting.UiState ui) {
            ui.hoverAnim = AnimationUtility.approach(ui.hoverAnim, hover ? 1f : 0f, dt, 12f);
            return ui.hoverAnim;
        }
        return hover ? 1f : 0f;
    }

    private static float bindWaitAnim(Object uiObj, boolean waiting) {
        float dt = AnimationUtility.deltaTime();
        if (uiObj instanceof KeyBindSetting.UiState ui) {
            ui.waitAnim = AnimationUtility.approach(ui.waitAnim, waiting ? 1f : 0f, dt, 14f);
            return ui.waitAnim;
        }
        if (uiObj instanceof FunctionBindSetting.UiState ui) {
            ui.waitAnim = AnimationUtility.approach(ui.waitAnim, waiting ? 1f : 0f, dt, 14f);
            return ui.waitAnim;
        }
        return waiting ? 1f : 0f;
    }

    private static void renderSmallModeChip(float x, float y, float w, float h, String text, float size, float hoverAnim, float waitAnim) {
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float inset = m(0.8f, 0.45f);
        float dx = x + inset;
        float dy = y + inset;
        float dw = Math.max(1f, w - inset * 2f);
        float dh = Math.max(1f, h - inset * 2f);
        float radius = Math.max(0f, m(6f, 4f) - inset);
        int bg = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE_SOFT, UnifiedSettingsSkin.SURFACE_HOVER, 0.45f + hoverAnim * 0.25f + waitAnim * 0.20f);
        ClickGuiRenderer.drawRoundedRect(dx, dy, dw, dh, radius, bg);
        ClickGuiRenderer.drawRoundedRectStroke(dx, dy, dw, dh, radius, m(0.55f, 0.35f), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, Math.round(34f + hoverAnim * 24f + waitAnim * 30f)));
        String fitted = UnifiedSettingsSkin.fit(font, text, size, Math.max(1f, w - m(6f, 6f)));
        float tw = UnifiedSettingsSkin.textWidth(font, fitted, size);
        int color = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, waitAnim * 0.35f + hoverAnim * 0.15f);
        ClickGuiRenderer.drawText(font, fitted, x + (w - tw) * 0.5f, y + (h - UnifiedSettingsSkin.textHeight(font, size)) * 0.5f, size, color, false);
    }

    private static float bindHeight(float lastW, String label, String display, String modeText) {
        float w = lastW > 0f ? lastW : m(160f, 115f);
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(17f, 7.0f);
        float small = m(11.5f, 5.7f);
        float pad = m(6f, 5f);
        float gap = m(4f, 4f);
        float chipPad = m(7f, 5f);
        float minLabelW = m(72f, 38f);
        float modeW = modeText == null ? 0f : Math.max(m(36f, 25f), UnifiedSettingsSkin.textWidth(font, modeText, small) + m(10f, 8f));
        float modeBlockW = modeW > 0f ? modeW + gap : 0f;
        String displayText = display == null || display.isBlank() ? "NONE" : display;
        float wantedChipW = UnifiedSettingsSkin.textWidth(font, displayText, size) + chipPad * 2f;
        float sameLineMaxChipW = Math.max(m(48f, 30f), w - pad * 3f - modeBlockW - minLabelW);
        boolean nextLine = sameLineMaxChipW < m(48f, 30f);
        if (!nextLine) {
            float chipW = Math.max(m(48f, 30f), Math.min(wantedChipW, sameLineMaxChipW));
            float labelMax = w - pad * 3f - modeBlockW - chipW;
            nextLine = labelMax < minLabelW;
        }
        return nextLine ? m(48f, 31f) : m(38f, 22f);
    }

    static void render(ColorSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingsSkin.syncTheme();
        ColorSetting.UiState ui = setting.ui();
        ColorValue value = setting.value();
        boolean hasAlpha = value.supportsAlpha();
        ui.lastX = x;
        ui.lastY = y;
        ui.lastW = w;
        if (!ui.hexFocused && !colorDragActive(ui)) syncColorFromValue(ui, value);
        ui.rainbow = value.isRainbow();
        if (ui.rainbow) clearColorFocus(ui);

        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float labelSize = m(18f, 7f);
        float previewW = m(34f, 20f);
        float previewH = m(16f, 10f);
        float previewX = x + w - previewW - m(5f, 5f);
        float previewY = y + (colorHeaderH() - previewH) * 0.5f;
        String label = UnifiedSettingsSkin.fit(font, setting.getDisplayName(), labelSize, Math.max(1f, previewX - x - m(12f, 12f)));
        ClickGuiRenderer.drawText(font, label, x + m(7f, 7f), y + (colorHeaderH() - UnifiedSettingsSkin.textHeight(font, labelSize)) * 0.5f, labelSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        renderColorPreview(previewX, previewY, previewW, previewH, value.getArgb(), m(3f, 3f));
        ui.previewGlowAnim = AnimationUtility.approach(ui.previewGlowAnim, ui.editing ? 1f : UnifiedSettingsSkin.inside(mx, my, previewX, previewY, previewW, previewH) ? 0.55f : 0f, 0.20f);

        if (ui.expandAnim <= 0.001f) return;
        layoutColor(ui, x, y, w, hasAlpha);
        updateColorDrag(ui, value, mx, my, hasAlpha);
        float reveal = colorReveal(ui);
        float slide = ui.editing ? (1f - reveal) * m(8f, 8f) : (1f - reveal) * m(6f, 6f);
        float clipY = y + colorHeaderH();
        float clipH = (colorExpandedHeight(ui, hasAlpha) + m(5f, 5f)) * reveal;
        boolean clipped = clipH > 0.5f && ScissorFunction.pushRaw(x, clipY, w, clipH);

        float cx = ui.squareX;
        float cy = ui.squareY + slide;
        float containerX = cx - m(5f, 5f);
        float containerY = cy - m(5f, 5f);
        float containerW = x + w - m(5f, 5f) - containerX;
        float containerH = colorExpandedHeight(ui, hasAlpha);
        UnifiedSettingsSkin.drawSurface(containerX, containerY, containerW, containerH, m(6f, 6f), 0.90f);

        renderColorSquare(ui, cy);
        renderHueBar(ui, slide);
        if (hasAlpha) {
            renderChecker(ui.alphaX, ui.alphaY + slide, ui.alphaW, ui.alphaH, m(3f, 3f), m(3f, 3f));
            renderAlphaBar(ui, slide);
        }
        if (ui.rgbExpandAnim > 0.001f) renderRgbBars(ui, hasAlpha, slide);
        renderColorMarkers(ui, hasAlpha, slide);
        renderColorPresets(ui, value, mx, my, slide);
        renderHexInput(ui, value, mx, my, slide);
        renderColorActions(ui, value, mx, my, slide);
        if (clipped) ScissorFunction.pop();
    }

    // ------------------------------------------------------------
    // Color
    // ------------------------------------------------------------

    static void mouseClicked(ColorSetting setting, double mx, double my, int button) {
        if (button != 0 && button != 1 && button != 2) return;
        ColorSetting.UiState ui = setting.ui();
        ColorValue value = setting.value();
        boolean hasAlpha = value.supportsAlpha();
        float pw = m(34f, 20f);
        float ph = m(16f, 10f);
        float px = ui.lastX + ui.lastW - pw - m(5f, 5f);
        float py = ui.lastY + (colorHeaderH() - ph) * 0.5f;
        boolean previewHit = UnifiedSettingsSkin.inside(mx, my, px, py, pw, ph);
        boolean headerHit = UnifiedSettingsSkin.inside(mx, my, ui.lastX, ui.lastY, ui.lastW, colorHeaderH());
        if (button == 0 && headerHit) {
            ui.editing = !ui.editing;
            clearColorFocus(ui);
            return;
        }
        if (!ui.editing) return;
        layoutColor(ui, ui.lastX, ui.lastY, ui.lastW, hasAlpha);
        if (handleColorPresetClick(setting, value, ui, mx, my, button)) return;
        if (button != 0) return;
        if (UnifiedSettingsSkin.inside(mx, my, ui.hexX, ui.hexY, ui.hexW, ui.hexH)) {
            beginHexEdit(ui, value);
            setHexCursorFromMouse(ui, (float) mx, false);
            return;
        }
        if (ui.hexFocused) {
            if (!commitHexEdit(setting, value, ui, true)) return;
        }
        if (UnifiedSettingsSkin.inside(mx, my, ui.sliderX, ui.sliderY, ui.sliderW, ui.sliderH)) {
            ui.showRgbBars = !ui.showRgbBars;
            clearColorFocus(ui);
            return;
        }
        if (UnifiedSettingsSkin.inside(mx, my, ui.rnbX, ui.rnbY, ui.rnbW, ui.rnbH)) {
            value.set(value.isRainbow() ? value.rainbowFallbackHex() : value.toRainbowValue());
            syncColorFromValue(ui, value);
            clearColorFocus(ui);
            if (setting.getParent() != null) setting.getParent().saveConfig();
            return;
        }
        if (value.isRainbow()) return;
        clearColorFocus(ui);
        if (UnifiedSettingsSkin.inside(mx, my, ui.squareX, ui.squareY, ui.squareW, ui.squareH)) {
            ui.sbFocused = true;
            return;
        }
        if (UnifiedSettingsSkin.inside(mx, my, ui.hueX - m(2f, 2f), ui.hueY, ui.hueW + m(4f, 4f), ui.hueH)) {
            ui.hFocused = true;
            return;
        }
        if (hasAlpha && UnifiedSettingsSkin.inside(mx, my, ui.alphaX - m(2f, 2f), ui.alphaY, ui.alphaW + m(4f, 4f), ui.alphaH)) {
            ui.aFocused = true;
            return;
        }
        if (ui.showRgbBars && ui.rgbExpandAnim > 0.20f) {
            if (UnifiedSettingsSkin.inside(mx, my, ui.rX, ui.rY, ui.rW, ui.rH)) {
                ui.rFocused = true;
                return;
            }
            if (UnifiedSettingsSkin.inside(mx, my, ui.gX, ui.gY, ui.gW, ui.gH)) {
                ui.gFocused = true;
                return;
            }
            if (UnifiedSettingsSkin.inside(mx, my, ui.bX, ui.bY, ui.bW, ui.bH)) {
                ui.bFocused = true;
                return;
            }
            if (hasAlpha && UnifiedSettingsSkin.inside(mx, my, ui.aX, ui.aY, ui.aW, ui.aH)) ui.aRgbFocused = true;
        }
    }

    static void mouseReleased(ColorSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        ColorSetting.UiState ui = setting.ui();
        boolean changed = ui.sbFocused || ui.hFocused || ui.aFocused || ui.rFocused || ui.gFocused || ui.bFocused || ui.aRgbFocused;
        ui.presetsScrollbarDragging = false;
        clearColorFocus(ui);
        if (changed && setting.getParent() != null) setting.getParent().saveConfig();
    }

    static void mouseClickedOutside(ColorSetting setting, double mx, double my, int button) {
        if (button != 0) return;
        ColorSetting.UiState ui = setting.ui();
        if (ui.hexFocused) {
            commitHexEdit(setting, setting.value(), ui, true);
        }
        ui.presetsScrollbarDragging = false;
        clearColorFocus(ui);
    }

    static boolean keyPressed(ColorSetting setting, int keyCode, int scanCode, int modifiers) {
        ColorSetting.UiState ui = setting.ui();
        if (!ui.hexFocused) return false;
        ColorValue value = setting.value();
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAllHex(ui);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            ClipboardUtil.copy("#" + selectedOrAllHex(ui));
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            insertHexText(ui, ClipboardUtil.get());
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            ClipboardUtil.copy("#" + selectedOrAllHex(ui));
            deleteHexSelection(ui);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitHexEdit(setting, value, ui, true);
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                cancelHexEdit(ui, value);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteHexSelection(ui) && ui.hexCursor > 0) {
                    ui.hexBuffer = ui.hexBuffer.substring(0, ui.hexCursor - 1) + ui.hexBuffer.substring(ui.hexCursor);
                    ui.hexCursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!deleteHexSelection(ui) && ui.hexCursor < ui.hexBuffer.length())
                    ui.hexBuffer = ui.hexBuffer.substring(0, ui.hexCursor) + ui.hexBuffer.substring(ui.hexCursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveHexCursor(ui, -1, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveHexCursor(ui, 1, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                setHexCursor(ui, 0, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                setHexCursor(ui, ui.hexBuffer.length(), (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
        }
        return true;
    }

    static boolean charTyped(ColorSetting setting, char chr, int modifiers) {
        ColorSetting.UiState ui = setting.ui();
        if (!ui.hexFocused) return false;
        if (isHexChar(chr)) {
            insertHexText(ui, String.valueOf(chr));
        } else if (chr == '#') {
            // accepted as no-op so pasted/manual values with # don't flash error
        } else {
            ui.hexErrorAnim = 1f;
        }
        return true;
    }

    static boolean mouseScrolled(ColorSetting setting, double mx, double my, double amount) {
        ColorSetting.UiState ui = setting.ui();
        if (!ui.editing || !ui.presetsExpanded || ui.presetsMaxScroll <= 0.5f) return false;
        float slide = (1f - colorReveal(ui)) * m(8f, 8f);
        float listY = ui.presetListY + slide;
        if (!UnifiedSettingsSkin.inside(mx, my, ui.presetListX, listY, ui.presetListW, Math.max(1f, ui.presetListH)))
            return false;
        ui.presetsScroll = AnimationUtility.clamp(ui.presetsScroll - (float) amount * m(16f, 12f), 0f, ui.presetsMaxScroll);
        return true;
    }

    static float getHeight(ColorSetting setting) {
        ColorSetting.UiState ui = setting.ui();
        boolean hasAlpha = setting.value().supportsAlpha();
        float dt = AnimationUtility.deltaTime();
        ui.expandAnim = AnimationUtility.approach(ui.expandAnim, ui.editing ? 1f : 0f, dt, ui.editing ? 5.2f : 4.0f);
        ui.expandAnim = AnimationUtility.snap(ui.expandAnim, ui.editing ? 1f : 0f, 0.001f);
        ui.rgbExpandAnim = AnimationUtility.approach(ui.rgbExpandAnim, ui.showRgbBars ? 1f : 0f, dt, ui.showRgbBars ? 10f : 9f);
        ui.rgbExpandAnim = AnimationUtility.snap(ui.rgbExpandAnim, ui.showRgbBars ? 1f : 0f, 0.002f);
        ui.presetsExpandAnim = AnimationUtility.approach(ui.presetsExpandAnim, ui.presetsExpanded ? 1f : 0f, dt, ui.presetsExpanded ? 9.5f : 8.5f);
        ui.presetsExpandAnim = AnimationUtility.snap(ui.presetsExpandAnim, ui.presetsExpanded ? 1f : 0f, 0.002f);
        return colorHeaderH() + colorExpandedHeight(ui, hasAlpha) * colorLayoutAnim(ui);
    }

    private static void layoutColor(ColorSetting.UiState ui, float x, float y, float w, boolean hasAlpha) {
        int verticalBars = hasAlpha ? 2 : 1;
        float sidePad = m(5f, 5f);
        float rightInset = m(5f, 4f);
        float barW = m(6f, 4f);
        float barGap = m(7f, 5f);
        ui.squareX = x + sidePad;
        ui.squareY = y + colorHeaderH() + m(6f, 5f);
        ui.squareH = m(82f, 60f);
        ui.squareW = Math.max(m(74f, 44f), w - sidePad * 2f - rightInset - verticalBars * barW - verticalBars * barGap);
        ui.hueX = ui.squareX + ui.squareW + barGap;
        ui.hueY = ui.squareY;
        ui.hueW = barW;
        ui.hueH = ui.squareH;
        if (hasAlpha) {
            ui.alphaX = ui.hueX + barW + barGap;
            ui.alphaY = ui.squareY;
            ui.alphaW = barW;
            ui.alphaH = ui.squareH;
        } else {
            ui.alphaX = ui.hueX;
            ui.alphaY = ui.hueY;
            ui.alphaW = 0f;
            ui.alphaH = 0f;
        }
        float rgbReveal = colorRgbReveal(ui);
        int rgbBars = hasAlpha ? 4 : 3;
        float rgbGapY = m(5f, 5f);
        float rgbH = m(9f, 7f);
        float valueColumn = m(25f, 18f);
        float rgbBlockFull = m(8f, 7f) + rgbBars * rgbH + Math.max(0, rgbBars - 1) * rgbGapY;
        float rgbBlockH = rgbBlockFull * rgbReveal;
        ui.rX = ui.squareX;
        ui.rY = ui.squareY + ui.squareH + m(8f, 7f);
        ui.rW = Math.max(1f, ui.squareW - valueColumn);
        ui.rH = rgbH;
        ui.gX = ui.rX;
        ui.gY = ui.rY + rgbH + rgbGapY;
        ui.gW = ui.rW;
        ui.gH = ui.rH;
        ui.bX = ui.rX;
        ui.bY = ui.gY + rgbH + rgbGapY;
        ui.bW = ui.rW;
        ui.bH = ui.rH;
        if (hasAlpha) {
            ui.aX = ui.rX;
            ui.aY = ui.bY + rgbH + rgbGapY;
            ui.aW = ui.rW;
            ui.aH = ui.rH;
        } else {
            ui.aX = ui.rX;
            ui.aY = ui.bY;
            ui.aW = 0f;
            ui.aH = 0f;
        }
        ui.rgbBottom = ui.squareY + ui.squareH + rgbBlockH;

        float actionX = ui.squareX;
        float actionW = Math.max(1f, x + w - m(10f, 10f) - actionX);
        float gap = m(4f, 4f);
        ui.presetX = actionX;
        ui.presetY = ui.rgbBottom + m(6f, 4f);
        ui.presetW = actionW;
        ui.presetH = m(20f, 12f);

        float presetButtonW = m(20f, 14f);
        ui.presetToggleW = presetButtonW;
        ui.savePresetW = presetButtonW;
        ui.deletePresetW = presetButtonW;
        ui.presetToggleX = actionX + actionW - presetButtonW * 3f;
        ui.savePresetX = ui.presetToggleX + presetButtonW;
        ui.deletePresetX = ui.savePresetX + presetButtonW;
        ui.savePresetY = ui.presetY;
        ui.deletePresetY = ui.presetY;
        ui.presetToggleY = ui.presetY;
        ui.savePresetH = ui.presetH;
        ui.deletePresetH = ui.presetH;
        ui.presetToggleH = ui.presetH;

        float listReveal = colorPresetReveal(ui);
        float listGap = m(5f, 3f);
        float listFullH = m(44f, 31f);
        ui.presetListX = actionX;
        ui.presetListY = ui.presetY + ui.presetH + listGap;
        ui.presetListW = actionW;
        ui.presetListH = listFullH * listReveal;

        ui.hexX = actionX;
        ui.hexY = ui.presetY + ui.presetH + (listGap + listFullH) * listReveal + m(5f, 3f);
        ui.hexW = actionW;
        ui.hexH = m(18f, 12f);
        float actionY = ui.hexY + ui.hexH + m(5f, 3f);
        float bw = Math.max(1f, (actionW - gap) / 2f);
        float bh = m(17f, 10.5f);
        ui.sliderX = actionX;
        ui.sliderY = actionY;
        ui.sliderW = bw;
        ui.sliderH = bh;
        ui.rnbX = ui.sliderX + bw + gap;
        ui.rnbY = actionY;
        ui.rnbW = bw;
        ui.rnbH = bh;
    }

    private static void renderColorPreview(float x, float y, float w, float h, int color, float radius) {
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, radius, color);
    }

    private static void renderColorSquare(ColorSetting.UiState ui, float y) {
        int left = hsbToArgb(ui.hue, 0f, 1f, 255);
        int right = hsbToArgb(ui.hue, 1f, 1f, 255);
        ClickGuiRenderer.drawRoundedRect(ui.squareX, y, ui.squareW, ui.squareH, m(6f, 6f), right);
        ClickGuiRenderer.drawGradientRect(ui.squareX, y, ui.squareW, ui.squareH, left, right, right, left);
        ClickGuiRenderer.drawGradientRect(ui.squareX, y, ui.squareW, ui.squareH, 0x00FFFFFF, 0x00FFFFFF, 0xFF000000, 0xFF000000);
    }

    private static void renderHueBar(ColorSetting.UiState ui, float slide) {
        int steps = Math.max(1, Math.round(ui.hueH));
        for (int i = 0; i < steps; i++) {
            float h = i / Math.max(1f, ui.hueH - 1f);
            ClickGuiRenderer.drawCircle(ui.hueX + ui.hueW * 0.5f, ui.hueY + slide + i, ui.hueW * 0.5f, hsbToArgb(h, 1f, 1f, 255));
        }
    }

    private static void renderAlphaBar(ColorSetting.UiState ui, float slide) {
        int rgb = hsbToArgb(ui.hue, ui.saturation, ui.brightness, 255);
        ClickGuiRenderer.drawGradientRect(ui.alphaX, ui.alphaY + slide, ui.alphaW, ui.alphaH, withAlpha(rgb, 255), withAlpha(rgb, 255), withAlpha(rgb, 0), withAlpha(rgb, 0));
    }

    private static void renderRgbBars(ColorSetting.UiState ui, boolean hasAlpha, float slide) {
        float reveal = colorRgbReveal(ui);
        if (reveal <= 0.001f) return;
        int alpha = hasAlpha ? ui.alpha : 255;
        float top = ui.rY + slide - m(2f, 2f);
        float bottom = (hasAlpha ? ui.aY + ui.aH : ui.bY + ui.bH) + slide + m(2f, 2f);
        boolean clip = ScissorFunction.pushRaw(ui.rX - m(10f, 8f), top, ui.rW + m(40f, 28f), Math.max(1f, (bottom - top) * reveal));
        renderRgbBar("R", ui.rX, ui.rY + slide, ui.rW, ui.rH, (alpha << 24) | (ui.green << 8) | ui.blue, (alpha << 24) | (255 << 16) | (ui.green << 8) | ui.blue, ui.red / 255f, ui.red);
        renderRgbBar("G", ui.gX, ui.gY + slide, ui.gW, ui.gH, (alpha << 24) | (ui.red << 16) | ui.blue, (alpha << 24) | (ui.red << 16) | (255 << 8) | ui.blue, ui.green / 255f, ui.green);
        renderRgbBar("B", ui.bX, ui.bY + slide, ui.bW, ui.bH, (alpha << 24) | (ui.red << 16) | (ui.green << 8), (alpha << 24) | (ui.red << 16) | (ui.green << 8) | 255, ui.blue / 255f, ui.blue);
        if (hasAlpha) {
            renderChecker(ui.aX + m(8f, 8f), ui.aY + slide, Math.max(1f, ui.aW - m(16f, 8f)), ui.aH, m(3f, 3f), m(2.5f, 2.5f));
            renderRgbBar("A", ui.aX, ui.aY + slide, ui.aW, ui.aH, (ui.red << 16) | (ui.green << 8) | ui.blue, (255 << 24) | (ui.red << 16) | (ui.green << 8) | ui.blue, ui.alpha / 255f, ui.alpha);
        }
        if (clip) ScissorFunction.pop();
    }

    private static void renderRgbBar(String label, float x, float y, float w, float h, int c0, int c1, float progress, int value) {
        TextRenderer font = UnifiedSettingsSkin.fontLight();
        float size = m(11f, 5.5f);
        ClickGuiRenderer.drawText(font, label, x, y - m(0.3f, 0.3f), size, UnifiedSettingsSkin.TEXT_MUTED, false);
        float bx = x + m(8f, 8f);
        float bw = Math.max(1f, w - m(16f, 8f));
        ClickGuiRenderer.drawGradientRect(bx, y, bw, h, c0, c1, c1, c0);
        float px = bx + UnifiedSettingsSkin.clamp01(progress) * bw;
        ClickGuiRenderer.drawRoundedRect(px - m(1f, 1f), y - m(1f, 1f), m(2f, 2f), h + m(2f, 2f), m(1f, 1f), UnifiedSettingsSkin.TEXT_PRIMARY);
        String valueText = Integer.toString(Math.max(0, Math.min(255, value)));
        float valueSize = m(10.5f, 5.2f);
        float valueX = x + w + m(3f, 3f);
        float valueY = y + (h - UnifiedSettingsSkin.textHeight(font, valueSize)) * 0.5f - m(0.2f, 0.15f);
        ClickGuiRenderer.drawText(font, valueText, valueX, valueY, valueSize, UnifiedSettingsSkin.TEXT_MUTED, false);
    }

    private static void renderColorPresets(ColorSetting.UiState ui, ColorValue value, float mx, float my, float slide) {
        float y = ui.presetY + slide;
        float swatch = m(12f, 8f);
        float gap = m(4f, 3f);
        int labelColor = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 190);
        Renderer2D.COLOR.svg("palette", ui.presetX, y + (ui.presetH - swatch) * 0.5f, swatch, swatch,
                SvgRenderOptions.overrideColor(labelColor));
        float sx = ui.presetX + swatch + gap;
        float available = Math.max(1f, ui.presetToggleX - gap - sx);
        java.util.List<Integer> presets = ColorPresetStore.presets();
        int max = Math.max(1, (int) Math.floor(available / (swatch + gap)));
        for (int i = 0; i < Math.min(max, presets.size()); i++) {
            float px = sx + i * (swatch + gap);
            int color = presets.get(i);
            boolean hover = UnifiedSettingsSkin.inside(mx, my, px, y + (ui.presetH - swatch) * 0.5f, swatch, swatch);
            renderPresetSwatch(px, y + (ui.presetH - swatch) * 0.5f, swatch, presetDisplayColor(value, color), hover, value.supportsAlpha());
        }

        float dt = AnimationUtility.deltaTime();
        ui.presetsToggleHoverAnim = AnimationUtility.approach(ui.presetsToggleHoverAnim, UnifiedSettingsSkin.inside(mx, my, ui.presetToggleX, y, ui.presetToggleW, ui.presetToggleH) ? 1f : 0f, dt, 14f);
        ui.savePresetHoverAnim = AnimationUtility.approach(ui.savePresetHoverAnim, UnifiedSettingsSkin.inside(mx, my, ui.savePresetX, y, ui.savePresetW, ui.savePresetH) ? 1f : 0f, dt, 14f);
        ui.deletePresetHoverAnim = AnimationUtility.approach(ui.deletePresetHoverAnim, UnifiedSettingsSkin.inside(mx, my, ui.deletePresetX, y, ui.deletePresetW, ui.deletePresetH) ? 1f : 0f, dt, 14f);
        renderColorPresetActionGroup(ui, y);

        float reveal = colorPresetReveal(ui);
        if (reveal <= 0.035f) {
            ui.presetsMaxScroll = 0f;
            ui.presetsContentH = 0f;
            ui.presetsViewportH = 0f;
            ui.presetListH = 0f;
            ui.presetScrollbarX = ui.presetScrollbarY = ui.presetScrollbarW = ui.presetScrollbarH = 0f;
            return;
        }

        float listY = ui.presetListY + slide;
        float listH = ui.presetListH;
        if (listH <= m(2f, 1.2f)) {
            ui.presetsMaxScroll = 0f;
            return;
        }
        float pad = m(5f, 4f);
        UnifiedSettingsSkin.drawSurface(ui.presetListX, listY, ui.presetListW, listH, m(6f, 4f), 0.68f * reveal);
        if (UnifiedSettingsSkin.STROKE_GRADIENT_ENABLED) {
            UnifiedSettingsSkin.drawStroke(ui.presetListX, listY, ui.presetListW, listH, m(6f, 4f), m(0.65f, 0.42f), 0.32f * reveal);
        } else {
            ClickGuiRenderer.drawRoundedRectStroke(ui.presetListX, listY, ui.presetListW, listH, m(6f, 4f), m(0.65f, 0.42f), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, Math.round(58f * reveal)));
        }

        int cols = Math.max(1, (int) Math.floor((ui.presetListW - pad * 2f - m(7f, 5f)) / (swatch + gap)));
        int rows = (int) Math.ceil(presets.size() / (double) cols);
        float rowH = swatch + gap;
        ui.presetsContentH = Math.max(0f, rows * rowH - gap);
        ui.presetsViewportH = Math.max(1f, listH - pad * 2f);
        ui.presetsMaxScroll = Math.max(0f, ui.presetsContentH - ui.presetsViewportH);
        ui.presetsScroll = AnimationUtility.clamp(ui.presetsScroll, 0f, ui.presetsMaxScroll);

        boolean clip = ScissorFunction.pushRaw(ui.presetListX + pad, listY + pad, Math.max(1f, ui.presetListW - pad * 2f - (ui.presetsMaxScroll > 0.5f ? m(5f, 4f) : 0f)), ui.presetsViewportH);
        for (int i = 0; i < presets.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            float px = ui.presetListX + pad + col * (swatch + gap);
            float py = listY + pad + row * rowH - ui.presetsScroll;
            if (py + swatch < listY + pad - 1f || py > listY + pad + ui.presetsViewportH + 1f) continue;
            boolean hover = UnifiedSettingsSkin.inside(mx, my, px, py, swatch, swatch);
            renderPresetSwatch(px, py, swatch, presetDisplayColor(value, presets.get(i)), hover, value.supportsAlpha());
        }
        if (clip) ScissorFunction.pop();

        if (ui.presetsMaxScroll > 0.5f) {
            ui.presetScrollbarW = m(2.5f, 1.7f);
            ui.presetScrollbarX = ui.presetListX + ui.presetListW - pad - ui.presetScrollbarW;
            ui.presetScrollbarY = listY + pad;
            ui.presetScrollbarH = ui.presetsViewportH;
            ui.presetScrollbarHandleH = Math.max(m(12f, 8f), ui.presetScrollbarH * (ui.presetScrollbarH / (ui.presetScrollbarH + ui.presetsMaxScroll)));
            ui.presetScrollbarHandleY = ui.presetScrollbarY + (ui.presetScrollbarH - ui.presetScrollbarHandleH) * (ui.presetsScroll / Math.max(1f, ui.presetsMaxScroll));
            boolean barHover = UnifiedSettingsSkin.inside(mx, my, ui.presetScrollbarX - m(3f, 2f), ui.presetScrollbarY, ui.presetScrollbarW + m(6f, 4f), ui.presetScrollbarH) || ui.presetsScrollbarDragging;
            ui.presetsScrollbarHoverAnim = AnimationUtility.approach(ui.presetsScrollbarHoverAnim, barHover ? 1f : 0f, dt, 14f);
            ClickGuiRenderer.drawRoundedRect(ui.presetScrollbarX, ui.presetScrollbarY, ui.presetScrollbarW, ui.presetScrollbarH, ui.presetScrollbarW * 0.5f, UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 42));
            ClickGuiRenderer.drawRoundedRect(ui.presetScrollbarX, ui.presetScrollbarHandleY, ui.presetScrollbarW, ui.presetScrollbarHandleH, ui.presetScrollbarW * 0.5f, UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 95 + Math.round(65f * ui.presetsScrollbarHoverAnim)));
            if (ui.presetsScrollbarDragging) {
                updatePresetScrollbarDrag(ui, my);
            }
        } else {
            ui.presetScrollbarX = ui.presetScrollbarY = ui.presetScrollbarW = ui.presetScrollbarH = 0f;
        }
    }

    private static void renderPresetSwatch(float x, float y, float size, int color, boolean hover, boolean alphaContext) {
        int stroke = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, hover ? 130 : 78);
        if (alphaContext || ((color >>> 24) & 0xFF) < 255) {
            renderChecker(x, y, size, size, m(3f, 2f), m(3f, 2f));
        }
        ClickGuiRenderer.drawRoundedRect(x, y, size, size, m(3f, 2f), color);
        ClickGuiRenderer.drawRoundedRectStroke(x, y, size, size, m(3f, 2f), m(0.7f, 0.45f), stroke);
    }

    private static int presetDisplayColor(ColorValue value, int argb) {
        return value.supportsAlpha() ? argb : (0xFF000000 | (argb & 0x00FFFFFF));
    }

    private static void renderColorPresetActionGroup(ColorSetting.UiState ui, float y) {
        float x = ui.presetToggleX;
        float w = ui.presetToggleW + ui.savePresetW + ui.deletePresetW;
        float h = ui.presetToggleH;
        float hover = Math.max(ui.presetsToggleHoverAnim, Math.max(ui.savePresetHoverAnim, ui.deletePresetHoverAnim));
        float active = ui.presetsExpanded ? 1f : 0f;
        if (ui.presetsExpanded) {
            UnifiedSettingsSkin.drawAccent(x, y, w, h, m(5f, 4f), 0.32f + hover * 0.16f);
        } else {
            UnifiedSettingsSkin.drawSurface(x, y, w, h, m(5f, 4f), 0.58f + hover * 0.24f);
        }
        int stroke = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 62), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, 150), active);
        stroke = UnifiedSettingsSkin.mix(stroke, UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 120), hover * 0.35f);
        if (UnifiedSettingsSkin.STROKE_GRADIENT_ENABLED || ui.presetsExpanded) {
            ClickGuiRenderer.drawRoundedRectStrokeGradient(x, y, w, h, m(5f, 4f), m(0.7f, 0.45f), UnifiedSettingsSkin.accentGradientStart(0.58f + active * 0.20f), UnifiedSettingsSkin.accentGradientEnd(0.58f + active * 0.20f), UnifiedSettingsSkin.ACCENT_GRADIENT_ANGLE);
        } else {
            ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, m(5f, 4f), m(0.7f, 0.45f), stroke);
        }

        float sepW = m(0.7f, 0.45f);
        int sep = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 55 + Math.round(35f * hover));
        ClickGuiRenderer.drawRect(ui.savePresetX, y + m(3f, 2f), sepW, h - m(6f, 4f), sep);
        ClickGuiRenderer.drawRect(ui.deletePresetX, y + m(3f, 2f), sepW, h - m(6f, 4f), sep);

        renderPresetActionIcon("folder-closed", ui.presetToggleX, y, ui.presetToggleW, h, ui.presetsToggleHoverAnim, ui.presetsExpanded);
        renderPresetActionIcon("heart-plus", ui.savePresetX, y, ui.savePresetW, h, ui.savePresetHoverAnim, false);
        renderPresetActionIcon("minus", ui.deletePresetX, y, ui.deletePresetW, h, ui.deletePresetHoverAnim, false);
    }

    private static void renderPresetActionIcon(String icon, float x, float y, float w, float h, float hoverAnim, boolean active) {
        float size = Math.min(m(11f, 7f), Math.max(m(8f, 5f), Math.min(w, h) - m(5f, 3f)));
        int color = active
                ? UnifiedSettingsSkin.ACCENT
                : UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, 0.55f + hoverAnim * 0.35f);
        Renderer2D.COLOR.svg(icon, x + (w - size) * 0.5f, y + (h - size) * 0.5f, size, size,
                SvgRenderOptions.overrideColor(color));
    }

    private static void renderHexInput(ColorSetting.UiState ui, ColorValue value, float mx, float my, float slide) {
        float y = ui.hexY + slide;
        boolean hover = UnifiedSettingsSkin.inside(mx, my, ui.hexX, y, ui.hexW, ui.hexH);
        float dt = AnimationUtility.deltaTime();
        ui.hexAnim = AnimationUtility.approach(ui.hexAnim, ui.hexFocused ? 1f : hover ? 0.55f : 0f, dt, 13f);
        ui.hexErrorAnim = AnimationUtility.approach(ui.hexErrorAnim, 0f, dt, 7.5f);
        if (ui.hexFocused) ui.hexCursorBlink += dt * 2.2f;
        else ui.hexCursorBlink = 0f;
        int bg = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE_SOFT, UnifiedSettingsSkin.SURFACE_HOVER, ui.hexAnim), 58 + Math.round(35f * ui.hexAnim));
        int stroke = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 70), 0xFFFF5F66, ui.hexErrorAnim);
        ClickGuiRenderer.drawRoundedRect(ui.hexX, y, ui.hexW, ui.hexH, m(5f, 3f), bg);
        ClickGuiRenderer.drawRoundedRectStroke(ui.hexX, y, ui.hexW, ui.hexH, m(5f, 3f), m(0.7f, 0.45f), stroke);
        int icon = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 185);
        Renderer2D.COLOR.svg("brush", ui.hexX + m(5f, 3f), y + (ui.hexH - m(9f, 6f)) * 0.5f, m(9f, 6f), m(9f, 6f),
                SvgRenderOptions.overrideColor(icon));
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(11.5f, 5.4f);
        String text = ui.hexFocused ? ui.hexBuffer : displayHex(value, value.supportsAlpha());
        String label = "HEX";
        float tx = ui.hexX + m(18f, 12f);
        ClickGuiRenderer.drawText(font, label, tx, y + (ui.hexH - UnifiedSettingsSkin.textHeight(font, size)) * 0.5f, size, UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 175), false);
        float labelW = UnifiedSettingsSkin.textWidth(font, label, size) + m(5f, 4f);
        int textColor = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_PRIMARY, 0xFFFF7777, ui.hexErrorAnim);
        float textX = tx + labelW;
        float textY = y + (ui.hexH - UnifiedSettingsSkin.textHeight(font, size)) * 0.5f;
        float textClipW = Math.max(1f, ui.hexW - (textX - ui.hexX) - m(25f, 17f));
        boolean hexClip = ScissorFunction.pushRaw(textX, y, textClipW, ui.hexH);
        String prefix = "#";
        String draw = prefix + text;
        if (ui.hexFocused) {
            renderInlineSelection(font, text, hexSelStart(ui), hexSelEnd(ui), textX + UnifiedSettingsSkin.textWidth(font, prefix, size), textY, size, ui.hexH, y);
            ClickGuiRenderer.drawText(font, draw, textX, textY, size, textColor, false);
            if (((int) (ui.hexCursorBlink * 2f) & 1) == 0) {
                int cursor = Math.max(0, Math.min(ui.hexCursor, ui.hexBuffer.length()));
                float cx = textX + UnifiedSettingsSkin.textWidth(font, prefix + ui.hexBuffer.substring(0, cursor), size) + m(0.5f, 0.3f);
                ClickGuiRenderer.drawRect(cx, y + m(3f, 2f), m(0.65f, 0.4f), ui.hexH - m(6f, 4f), UnifiedSettingsSkin.withAlpha(textColor, 190));
            }
        } else {
            String display = "#" + UnifiedSettingsSkin.fit(font, text, size, textClipW);
            ClickGuiRenderer.drawText(font, display, textX, textY, size, textColor, false);
        }
        if (hexClip) ScissorFunction.pop();
        float preview = ui.hexH - m(5f, 3f);
        float px = ui.hexX + ui.hexW - preview - m(3f, 2f);
        renderChecker(px, y + (ui.hexH - preview) * 0.5f, preview, preview, m(3f, 2f), m(3f, 2f));
        ClickGuiRenderer.drawRoundedRect(px, y + (ui.hexH - preview) * 0.5f, preview, preview, m(3f, 2f), value.getArgb());
    }

    private static void renderColorMarkers(ColorSetting.UiState ui, boolean hasAlpha, float slide) {
        float sbX = ui.squareX + ui.saturation * ui.squareW;
        float sbY = ui.squareY + slide + (1f - ui.brightness) * ui.squareH;
        ClickGuiRenderer.drawCircle(sbX, sbY, m(4f, 4f), 0xFF000000);
        ClickGuiRenderer.drawCircle(sbX, sbY, m(3f, 3f), 0xFFFFFFFF);
        float hueY = ui.hueY + slide + ui.hue * Math.max(1f, ui.hueH - 1f);
        ClickGuiRenderer.drawCircle(ui.hueX + ui.hueW * 0.5f, hueY, m(4f, 4f), 0xFF000000);
        ClickGuiRenderer.drawCircle(ui.hueX + ui.hueW * 0.5f, hueY, m(3f, 3f), 0xFFFFFFFF);
        if (hasAlpha) {
            float alphaY = ui.alphaY + slide + (1f - ui.alpha / 255f) * Math.max(1f, ui.alphaH - 1f);
            ClickGuiRenderer.drawCircle(ui.alphaX + ui.alphaW * 0.5f, alphaY, m(4f, 4f), 0xFF000000);
            ClickGuiRenderer.drawCircle(ui.alphaX + ui.alphaW * 0.5f, alphaY, m(3f, 3f), 0xFFFFFFFF);
        }
    }

    private static void renderColorActions(ColorSetting.UiState ui, ColorValue value, float mx, float my, float slide) {
        float dt = AnimationUtility.deltaTime();
        boolean rgbHover = UnifiedSettingsSkin.inside(mx, my, ui.sliderX, ui.sliderY + slide, ui.sliderW, ui.sliderH);
        boolean rnbHover = UnifiedSettingsSkin.inside(mx, my, ui.rnbX, ui.rnbY + slide, ui.rnbW, ui.rnbH);
        ui.sliderHoverAnim = AnimationUtility.approach(ui.sliderHoverAnim, rgbHover ? 1f : 0f, dt, 14f);
        ui.rnbHoverAnim = AnimationUtility.approach(ui.rnbHoverAnim, rnbHover ? 1f : 0f, dt, 14f);
        ui.rnbOffAnim = AnimationUtility.approach(ui.rnbOffAnim, value.isRainbow() ? 0f : 1f, dt, 8f);
        renderChipButton(ui.sliderX, ui.sliderY + slide, ui.sliderW, ui.sliderH, "RGB", ui.sliderHoverAnim, ui.showRgbBars);
        renderRainbowChipButton(ui.rnbX, ui.rnbY + slide, ui.rnbW, ui.rnbH, ui.rnbHoverAnim, value.isRainbow());
    }

    private static boolean handleColorPresetClick(ColorSetting setting, ColorValue value, ColorSetting.UiState ui, double mx, double my, int button) {
        if (button != 0 && button != 1) return false;
        float slide = (1f - colorReveal(ui)) * m(8f, 8f);
        float rowY = ui.presetY + slide;
        float swatch = m(12f, 8f);
        float gap = m(4f, 3f);
        float sx = ui.presetX + swatch + gap;
        float available = Math.max(1f, ui.presetToggleX - gap - sx);
        java.util.List<Integer> presets = ColorPresetStore.presets();
        int max = Math.max(1, (int) Math.floor(available / (swatch + gap)));
        for (int i = 0; i < Math.min(max, presets.size()); i++) {
            float px = sx + i * (swatch + gap);
            if (!UnifiedSettingsSkin.inside(mx, my, px, rowY + (ui.presetH - swatch) * 0.5f, swatch, swatch)) continue;
            int color = presets.get(i);
            if (button == 1) ColorPresetStore.removeExact(color);
            else applyColor(value, color);
            syncColorFromValue(ui, value);
            if (setting.getParent() != null) setting.getParent().saveConfig();
            return true;
        }
        if (button == 0 && UnifiedSettingsSkin.inside(mx, my, ui.presetToggleX, rowY, ui.presetToggleW, ui.presetToggleH)) {
            ui.presetsExpanded = !ui.presetsExpanded;
            return true;
        }
        if (button == 0 && UnifiedSettingsSkin.inside(mx, my, ui.savePresetX, rowY, ui.savePresetW, ui.savePresetH)) {
            ColorPresetStore.add(value.getArgb());
            return true;
        }
        if (button == 0 && UnifiedSettingsSkin.inside(mx, my, ui.deletePresetX, rowY, ui.deletePresetW, ui.deletePresetH)) {
            ColorPresetStore.removeClosest(value.getArgb());
            return true;
        }

        if (ui.presetsExpanded && ui.presetsExpandAnim > 0.05f) {
            float listY = ui.presetListY + slide;
            if (button == 0 && ui.presetsMaxScroll > 0.5f && UnifiedSettingsSkin.inside(mx, my, ui.presetScrollbarX - m(3f, 2f), ui.presetScrollbarY, ui.presetScrollbarW + m(6f, 4f), ui.presetScrollbarH)) {
                ui.presetsScrollbarDragging = true;
                ui.presetsScrollbarDragOffset = (float) my - ui.presetScrollbarHandleY;
                updatePresetScrollbarDrag(ui, (float) my);
                return true;
            }
            if (UnifiedSettingsSkin.inside(mx, my, ui.presetListX, listY, ui.presetListW, Math.max(1f, ui.presetListH))) {
                float pad = m(5f, 4f);
                float innerX = ui.presetListX + pad;
                float innerY = listY + pad;
                int cols = Math.max(1, (int) Math.floor((ui.presetListW - pad * 2f - m(7f, 5f)) / (swatch + gap)));
                float rowH = swatch + gap;
                float localX = (float) mx - innerX;
                float localY = (float) my - innerY + ui.presetsScroll;
                int col = (int) Math.floor(localX / (swatch + gap));
                int row = (int) Math.floor(localY / rowH);
                if (col >= 0 && col < cols && row >= 0) {
                    float cellX = col * (swatch + gap);
                    float cellY = row * rowH;
                    if (localX >= cellX && localX <= cellX + swatch && localY >= cellY && localY <= cellY + swatch) {
                        int index = row * cols + col;
                        if (index >= 0 && index < presets.size()) {
                            int color = presets.get(index);
                            if (button == 1) ColorPresetStore.removeExact(color);
                            else applyColor(value, color);
                            syncColorFromValue(ui, value);
                            if (setting.getParent() != null) setting.getParent().saveConfig();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void beginHexEdit(ColorSetting.UiState ui, ColorValue value) {
        ui.hexFocused = true;
        ui.hexBuffer = displayHex(value, value.supportsAlpha());
        ui.hexCursor = ui.hexBuffer.length();
        ui.hexSelection.clear();
        ui.hexErrorAnim = 0f;
    }

    private static void cancelHexEdit(ColorSetting.UiState ui, ColorValue value) {
        ui.hexFocused = false;
        ui.hexBuffer = displayHex(value, value.supportsAlpha());
        ui.hexCursor = ui.hexBuffer.length();
        ui.hexSelection.clear();
        ui.hexErrorAnim = 0f;
    }

    private static boolean commitHexEdit(ColorSetting setting, ColorValue value, ColorSetting.UiState ui, boolean save) {
        Integer parsed = parseHexInput(ui.hexBuffer, value.supportsAlpha());
        if (parsed == null) {
            ui.hexErrorAnim = 1f;
            return false;
        }
        applyColor(value, parsed);
        syncColorFromValue(ui, value);
        ui.hexFocused = false;
        ui.hexSelection.clear();
        if (save && setting.getParent() != null) setting.getParent().saveConfig();
        return true;
    }

    private static String displayHex(ColorValue value, boolean hasAlpha) {
        int argb = value.isRainbow() ? parseFallbackHex(value.rainbowFallbackHex(), value.getArgb()) : value.getArgb();
        if (hasAlpha)
            return String.format("%02X%02X%02X%02X", (argb >>> 24) & 0xFF, (argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF);
        return String.format("%02X%02X%02X", (argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF);
    }

    private static int parseFallbackHex(String hex, int fallback) {
        Integer parsed = parseHexInput(hex, true);
        return parsed == null ? fallback : parsed;
    }

    private static Integer parseHexInput(String raw, boolean hasAlpha) {
        if (raw == null) return null;
        StringBuilder clean = new StringBuilder();
        String t = raw.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (isHexChar(c)) clean.append(Character.toUpperCase(c));
        }
        String s = clean.toString();
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1) + s.substring(0, 1), 16);
                int g = Integer.parseInt(s.substring(1, 2) + s.substring(1, 2), 16);
                int b = Integer.parseInt(s.substring(2, 3) + s.substring(2, 3), 16);
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            if (s.length() == 6) {
                return (int) (0xFF000000L | Long.parseUnsignedLong(s, 16));
            }
            if (hasAlpha && s.length() == 8) {
                return (int) Long.parseUnsignedLong(s, 16);
            }
            if (!hasAlpha && s.length() == 8) {
                return (int) (0xFF000000L | (Long.parseUnsignedLong(s.substring(2), 16) & 0xFFFFFFL));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    private static void insertHexText(ColorSetting.UiState ui, String text) {
        if (text == null || text.isEmpty()) return;
        deleteHexSelection(ui);
        StringBuilder insert = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHexChar(c)) insert.append(Character.toUpperCase(c));
        }
        if (insert.length() == 0) {
            ui.hexErrorAnim = 1f;
            return;
        }
        int maxLen = 8;
        int allowed = Math.max(0, maxLen - ui.hexBuffer.length());
        if (insert.length() > allowed) {
            insert.setLength(allowed);
            ui.hexErrorAnim = 1f;
        }
        if (insert.length() == 0) return;
        ui.hexBuffer = ui.hexBuffer.substring(0, ui.hexCursor) + insert + ui.hexBuffer.substring(ui.hexCursor);
        ui.hexCursor += insert.length();
    }

    private static void selectAllHex(ColorSetting.UiState ui) {
        ui.hexSelection.begin(0, 0);
        ui.hexSelection.updateCaret(0, ui.hexBuffer == null ? 0 : ui.hexBuffer.length());
        ui.hexCursor = ui.hexBuffer == null ? 0 : ui.hexBuffer.length();
    }

    private static String selectedOrAllHex(ColorSetting.UiState ui) {
        if (hasHexSelection(ui)) return ui.hexBuffer.substring(hexSelStart(ui), hexSelEnd(ui));
        return ui.hexBuffer == null ? "" : ui.hexBuffer;
    }

    private static boolean hasHexSelection(ColorSetting.UiState ui) {
        return ui.hexSelection.hasRange();
    }

    private static int hexSelStart(ColorSetting.UiState ui) {
        return hasHexSelection(ui) ? Math.max(0, Math.min(ui.hexSelection.start(), ui.hexBuffer.length())) : -1;
    }

    private static int hexSelEnd(ColorSetting.UiState ui) {
        return hasHexSelection(ui) ? Math.max(0, Math.min(ui.hexSelection.end(), ui.hexBuffer.length())) : -1;
    }

    private static boolean deleteHexSelection(ColorSetting.UiState ui) {
        if (!hasHexSelection(ui)) return false;
        int start = hexSelStart(ui);
        int end = hexSelEnd(ui);
        ui.hexBuffer = ui.hexBuffer.substring(0, start) + ui.hexBuffer.substring(end);
        ui.hexCursor = start;
        ui.hexSelection.clear();
        return true;
    }

    private static void moveHexCursor(ColorSetting.UiState ui, int dir, boolean shift) {
        setHexCursor(ui, Math.max(0, Math.min(ui.hexBuffer.length(), ui.hexCursor + dir)), shift);
    }

    private static void setHexCursor(ColorSetting.UiState ui, int pos, boolean shift) {
        int next = Math.max(0, Math.min(ui.hexBuffer == null ? 0 : ui.hexBuffer.length(), pos));
        if (shift) {
            if (!ui.hexSelection.hasRange()) ui.hexSelection.begin(0, ui.hexCursor);
            ui.hexSelection.updateCaret(0, next);
        } else {
            ui.hexSelection.clear();
        }
        ui.hexCursor = next;
        ui.hexCursorBlink = 0f;
    }

    private static void setHexCursorFromMouse(ColorSetting.UiState ui, float mx, boolean shift) {
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        float size = m(11.5f, 5.4f);
        String label = "HEX";
        float tx = ui.hexX + m(18f, 12f);
        float labelW = UnifiedSettingsSkin.textWidth(font, label, size) + m(5f, 4f);
        float textX = tx + labelW + UnifiedSettingsSkin.textWidth(font, "#", size);
        int cursor = caretFromText(font, ui.hexBuffer == null ? "" : ui.hexBuffer, size, textX, mx);
        setHexCursor(ui, cursor, shift);
    }

    private static void updateColorDrag(ColorSetting.UiState ui, ColorValue value, float mx, float my, boolean hasAlpha) {
        if (value.isRainbow()) return;
        if (ui.sbFocused) {
            ui.saturation = UnifiedSettingsSkin.clamp01((mx - ui.squareX) / Math.max(1f, ui.squareW));
            ui.brightness = UnifiedSettingsSkin.clamp01(1f - ((my - ui.squareY) / Math.max(1f, ui.squareH)));
            setColorFromHsb(ui, value);
        }
        if (ui.hFocused) {
            ui.hue = UnifiedSettingsSkin.clamp01((my - ui.hueY) / Math.max(1f, ui.hueH));
            setColorFromHsb(ui, value);
        }
        if (hasAlpha && ui.aFocused) {
            ui.alpha = Math.round(UnifiedSettingsSkin.clamp01(1f - ((my - ui.alphaY) / Math.max(1f, ui.alphaH))) * 255f);
            setColorFromHsb(ui, value);
        }
        if (ui.rFocused) {
            ui.red = Math.round(UnifiedSettingsSkin.clamp01((mx - ui.rX) / Math.max(1f, ui.rW)) * 255f);
            setColorFromRgb(ui, value);
        }
        if (ui.gFocused) {
            ui.green = Math.round(UnifiedSettingsSkin.clamp01((mx - ui.gX) / Math.max(1f, ui.gW)) * 255f);
            setColorFromRgb(ui, value);
        }
        if (ui.bFocused) {
            ui.blue = Math.round(UnifiedSettingsSkin.clamp01((mx - ui.bX) / Math.max(1f, ui.bW)) * 255f);
            setColorFromRgb(ui, value);
        }
        if (hasAlpha && ui.aRgbFocused) {
            ui.alpha = Math.round(UnifiedSettingsSkin.clamp01((mx - ui.aX) / Math.max(1f, ui.aW)) * 255f);
            setColorFromRgb(ui, value);
        }
    }

    private static float colorHeaderH() {
        return m(48f, 18f);
    }

    private static float colorExpandedHeight(ColorSetting.UiState ui, boolean hasAlpha) {
        int bars = hasAlpha ? 4 : 3;
        float rgbBlock = (m(8f, 7f) + bars * m(9f, 7f) + (bars - 1) * m(5f, 5f)) * colorRgbReveal(ui);
        float presets = m(20f, 12f);
        float presetsExpanded = (m(5f, 3f) + m(44f, 31f)) * colorPresetReveal(ui);
        float hex = m(18f, 12f);
        float actions = m(17f, 10.5f);
        return Math.max(0f, colorHeaderH() + m(6f, 5f) + m(82f, 60f) + rgbBlock + m(6f, 4f) + presets + presetsExpanded + m(5f, 3f) + hex + m(5f, 3f) + actions + m(14f, 8f) - colorHeaderH());
    }

    private static float colorRgbReveal(ColorSetting.UiState ui) {
        return AnimationUtility.easeInOutCubic(UnifiedSettingsSkin.clamp01(ui.rgbExpandAnim));
    }

    private static float colorPresetReveal(ColorSetting.UiState ui) {
        return AnimationUtility.easeInOutCubic(UnifiedSettingsSkin.clamp01(ui.presetsExpandAnim));
    }

    private static void updatePresetScrollbarDrag(ColorSetting.UiState ui, float my) {
        if (ui.presetsMaxScroll <= 0.5f) return;
        float track = Math.max(1f, ui.presetScrollbarH - ui.presetScrollbarHandleH);
        float handleY = AnimationUtility.clamp(my - ui.presetsScrollbarDragOffset, ui.presetScrollbarY, ui.presetScrollbarY + track);
        float t = (handleY - ui.presetScrollbarY) / track;
        ui.presetsScroll = AnimationUtility.clamp(t * ui.presetsMaxScroll, 0f, ui.presetsMaxScroll);
    }

    private static float colorLayoutAnim(ColorSetting.UiState ui) {
        float t = UnifiedSettingsSkin.clamp01(ui.expandAnim);
        return ui.editing ? AnimationUtility.clamp(AnimationUtility.easeOutBack(t, 0.42f), 0f, 1.02f) : 1f - AnimationUtility.easeInCubic(1f - t);
    }

    private static float colorReveal(ColorSetting.UiState ui) {
        float t = UnifiedSettingsSkin.clamp01(ui.expandAnim);
        if (ui.editing) return AnimationUtility.easeInOutCubic(UnifiedSettingsSkin.clamp01((t - 0.08f) / 0.92f));
        return 1f - AnimationUtility.easeInCubic(1f - t);
    }

    private static void syncColorFromValue(ColorSetting.UiState ui, ColorValue value) {
        int argb = value.getArgb();
        ui.lastArgb = argb;
        ui.red = (argb >>> 16) & 0xFF;
        ui.green = (argb >>> 8) & 0xFF;
        ui.blue = argb & 0xFF;
        ui.alpha = (argb >>> 24) & 0xFF;
        float[] hsb = Color.RGBtoHSB(ui.red, ui.green, ui.blue, null);
        if (hsb[1] > 0.0001f && hsb[2] > 0.0001f) ui.hue = hsb[0];
        ui.saturation = hsb[1];
        ui.brightness = hsb[2];
    }

    private static void setColorFromHsb(ColorSetting.UiState ui, ColorValue value) {
        int rgb = Color.HSBtoRGB(ui.hue, ui.saturation, ui.brightness) & 0x00FFFFFF;
        ui.red = (rgb >>> 16) & 0xFF;
        ui.green = (rgb >>> 8) & 0xFF;
        ui.blue = rgb & 0xFF;
        applyColor(value, ((value.supportsAlpha() ? ui.alpha : 255) << 24) | rgb);
    }

    private static void setColorFromRgb(ColorSetting.UiState ui, ColorValue value) {
        float[] hsb = Color.RGBtoHSB(ui.red, ui.green, ui.blue, null);
        if (hsb[1] > 0.0001f && hsb[2] > 0.0001f) ui.hue = hsb[0];
        ui.saturation = hsb[1];
        ui.brightness = hsb[2];
        applyColor(value, ((value.supportsAlpha() ? ui.alpha : 255) << 24) | (ui.red << 16) | (ui.green << 8) | ui.blue);
    }

    private static void applyColor(ColorValue value, int argb) {
        if (value.supportsAlpha())
            value.set(String.format("#%02X%02X%02X%02X", (argb >>> 24) & 0xFF, (argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF));
        else value.set(String.format("#%02X%02X%02X", (argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF));
    }

    private static void pasteColor(ColorSetting setting, ColorValue value) {
        applyColor(value, ClickGuiRenderer.getColorClipboard());
        syncColorFromValue(setting.ui(), value);
        if (setting.getParent() != null) setting.getParent().saveConfig();
    }

    private static boolean colorDragActive(ColorSetting.UiState ui) {
        return ui.sbFocused || ui.hFocused || ui.aFocused || ui.rFocused || ui.gFocused || ui.bFocused || ui.aRgbFocused;
    }

    private static void clearColorFocus(ColorSetting.UiState ui) {
        ui.sbFocused = false;
        ui.hFocused = false;
        ui.aFocused = false;
        ui.rFocused = false;
        ui.gFocused = false;
        ui.bFocused = false;
        ui.aRgbFocused = false;
    }

    private static int hsbToArgb(float h, float s, float b, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (Color.HSBtoRGB(h, s, b) & 0x00FFFFFF);
    }

    private static int withAlpha(int argb, int a) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, a)) & 0xFF) << 24);
    }

    private static int caretFromText(TextRenderer font, String text, float size, float textX, float mx) {
        if (text == null || text.isEmpty()) return 0;
        float rel = mx - textX;
        if (rel <= 0f) return 0;
        for (int i = 1; i <= text.length(); i++) {
            float prev = UnifiedSettingsSkin.textWidth(font, text.substring(0, i - 1), size);
            float cur = UnifiedSettingsSkin.textWidth(font, text.substring(0, i), size);
            if (rel < (prev + cur) * 0.5f) return i - 1;
        }
        return text.length();
    }

    // ------------------------------------------------------------
    // Common helpers
    // ------------------------------------------------------------

    private static void renderInlineSelection(TextRenderer font, String text, int start, int end, float textX, float textY, float size, float fieldH, float fieldY) {
        if (text == null || start < 0 || end < 0 || start == end) return;
        int a = Math.max(0, Math.min(start, text.length()));
        int b = Math.max(0, Math.min(end, text.length()));
        if (a == b) return;
        if (a > b) {
            int t = a;
            a = b;
            b = t;
        }
        float x0 = textX + UnifiedSettingsSkin.textWidth(font, text.substring(0, a), size);
        float x1 = textX + UnifiedSettingsSkin.textWidth(font, text.substring(0, b), size);
        float h = Math.max(m(8f, 5f), UnifiedSettingsSkin.textHeight(font, size) + m(3f, 2f));
        float y = fieldY > 0f ? fieldY + (fieldH - h) * 0.5f : textY - m(1.5f, 0.7f);
        ClickGuiRenderer.drawRoundedRect(x0, y, Math.max(m(1f, 0.7f), x1 - x0), h, m(2f, 1.5f), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, 92));
    }

    private static void renderRainbowChipButton(float x, float y, float w, float h, float hoverAnim, boolean active) {
        float t = (System.nanoTime() % 3_000_000_000L) / 3_000_000_000f;
        int c0 = hsbToArgb((t + 0.00f) % 1f, 0.80f, 1.0f, active ? 205 : 92 + Math.round(hoverAnim * 34f));
        int c1 = hsbToArgb((t + 0.18f) % 1f, 0.80f, 1.0f, active ? 215 : 96 + Math.round(hoverAnim * 38f));
        int c2 = hsbToArgb((t + 0.36f) % 1f, 0.80f, 1.0f, active ? 215 : 96 + Math.round(hoverAnim * 38f));
        int c3 = hsbToArgb((t + 0.54f) % 1f, 0.80f, 1.0f, active ? 205 : 92 + Math.round(hoverAnim * 34f));
        if (active) {
            ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, m(4f, 4f), c0, c2, 25f + t * 360f);
            ClickGuiRenderer.drawRoundedRect(x, y, w, h, m(4f, 4f), UnifiedSettingsSkin.withAlpha(0xFF000000, 18 - Math.round(hoverAnim * 6f)));
            ClickGuiRenderer.drawRoundedRectStrokeGradient(x, y, w, h, m(4f, 4f), m(0.8f, 0.45f), c0, c2, 25f + t * 360f);
        } else {
            int bg = UnifiedSettingsSkin.mix(UnifiedSettingsSkin.SURFACE_SOFT, UnifiedSettingsSkin.SURFACE_HOVER, hoverAnim * 0.75f);
            ClickGuiRenderer.drawRoundedRect(x, y, w, h, m(4f, 4f), UnifiedSettingsSkin.withAlpha(bg, 70 + Math.round(hoverAnim * 42f)));
            if (hoverAnim > 0.02f) {
                ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, m(4f, 4f), UnifiedSettingsSkin.withAlpha(c0, Math.round(52f * hoverAnim)), UnifiedSettingsSkin.withAlpha(c2, Math.round(52f * hoverAnim)), 25f + t * 360f);
                ClickGuiRenderer.drawRoundedRectStrokeGradient(x, y, w, h, m(4f, 4f), m(0.7f, 0.45f), UnifiedSettingsSkin.withAlpha(c0, 80 + Math.round(96f * hoverAnim)), UnifiedSettingsSkin.withAlpha(c2, 80 + Math.round(96f * hoverAnim)), 25f + t * 360f);
            } else {
                ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, m(4f, 4f), m(0.7f, 0.45f), UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 72));
            }
        }
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        String label = "RNB";
        float size = Math.min(m(13f, 6f), Math.max(m(10f, 4.8f), (w - m(4f, 4f)) / Math.max(1f, label.length()) * 1.45f));
        float tw = UnifiedSettingsSkin.textWidth(font, label, size);
        float ty = y + (h - UnifiedSettingsSkin.textHeight(font, size)) * 0.5f - m(0.2f, 0.2f);
        int labelColor = active ? 0xFFFFFFFF : UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, 0xFFFFFFFF, hoverAnim * 0.62f);
        ClickGuiRenderer.drawText(font, label, x + Math.max(m(2f, 2f), (w - tw) * 0.5f), ty, size, labelColor, false);
    }

    private static void renderChipButton(float x, float y, float w, float h, String label, float hoverAnim, boolean active) {
        TextRenderer font = UnifiedSettingsSkin.fontMedium();
        int bg = active ? UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, 70) : UnifiedSettingsSkin.SURFACE_SOFT;
        bg = UnifiedSettingsSkin.mix(bg, UnifiedSettingsSkin.SURFACE_HOVER, hoverAnim * 0.72f);
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, m(4f, 4f), bg);
        int stroke = active
                ? UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT, 135 + Math.round(hoverAnim * 55f))
                : UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_FAINT, 58 + Math.round(hoverAnim * 65f));
        ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, m(4f, 4f), m(0.7f, 0.45f), stroke);
        float size = Math.min(m(13f, 6f), Math.max(m(10f, 4.8f), (w - m(4f, 4f)) / Math.max(1f, label.length()) * 1.45f));
        float tw = UnifiedSettingsSkin.textWidth(font, label, size);
        float ty = y + (h - UnifiedSettingsSkin.textHeight(font, size)) * 0.5f - m(0.2f, 0.2f);
        int color = active ? UnifiedSettingsSkin.ACCENT : UnifiedSettingsSkin.mix(UnifiedSettingsSkin.TEXT_MUTED, UnifiedSettingsSkin.TEXT_PRIMARY, 0.68f + hoverAnim * 0.32f);
        ClickGuiRenderer.drawText(font, label, x + Math.max(m(2f, 2f), (w - tw) * 0.5f), ty, size, color, false);
    }

    private static void renderChecker(float x, float y, float w, float h, float cell, float radius) {
        int light = 0xFFE7E7E7;
        int dark = 0xFF777777;
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, radius, light);
        float safeCell = Math.max(1f, cell);
        int cols = Math.max(1, (int) Math.ceil(w / safeCell));
        int rows = Math.max(1, (int) Math.ceil(h / safeCell));
        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (((row + col) & 1) == 0) continue;
                ClickGuiRenderer.drawRect(x + col * safeCell, y + row * safeCell, safeCell, safeCell, dark);
            }
        }
        if (clipped) ScissorFunction.pop();
    }

    private static float calculateTextBoxHeight(TextRenderer font, String text, float size, float maxW) {
        String safe = text == null || text.isEmpty() ? " " : text;
        int lines = Math.max(1, ClickGuiRenderer.wrapText(font, safe, size, maxW, 6).size());
        return m(5f, 5f) + lines * UnifiedSettingsSkin.textHeight(font, size) + Math.max(0, lines - 1) * m(1f, 1f);
    }

    private static void drawWrappedText(TextRenderer font, String text, float x, float y, float size, float maxW, int color, int maxLines) {
        java.util.List<String> lines = ClickGuiRenderer.wrapText(font, text == null ? "" : text, size, maxW, maxLines);
        float cy = y;
        for (String line : lines) {
            ClickGuiRenderer.drawText(font, line, x, cy, size, color, false);
            cy += UnifiedSettingsSkin.textHeight(font, size) + m(1f, 1f);
        }
    }

    private static void drawBindMarquee(
            TextRenderer font,
            String text,
            float x,
            float y,
            float fullWidth,
            float viewWidth,
            float fontSize,
            int color
    ) {
        final float speed = 22.0f;
        final float gap = 14.0f;
        final float pauseSec = 0.8f;

        if (text == null || text.isEmpty()) return;
        if (speed <= 0.0f) {
            ClickGuiRenderer.drawText(font, text, x, y, fontSize, color, false);
            return;
        }

        float cycleDistance = fullWidth + gap;
        float cycleTime = pauseSec + cycleDistance / speed;
        if (cycleTime <= 0.0f) {
            ClickGuiRenderer.drawText(font, text, x, y, fontSize, color, false);
            return;
        }

        float now = net.minecraft.util.Util.getMillis() / 1000.0f;
        float t = now % cycleTime;

        float offset = 0.0f;
        if (t > pauseSec) {
            offset = -(t - pauseSec) * speed;
        }

        ClickGuiRenderer.drawText(font, text, x + offset, y, fontSize, color, false);

        if (cycleDistance > viewWidth * 0.5f) {
            ClickGuiRenderer.drawText(font, text, x + offset + cycleDistance, y, fontSize, color, false);
        }
    }

    private record LayoutBoolean(String[] lines, float lineH, float labelY, float controlY, float rowH) {
    }
}
