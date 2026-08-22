/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;


import combatant.client.features.theme.Theme;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.proxy.ProxyBackend;
import combatant.client.util.proxy.ProxyEntry;
import combatant.client.util.proxy.ProxyType;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class CombatantProxyManagerScreen extends Screen {
    private static final float SCALE = 2.28f;
    private static final float TEXT_SCALE = 1.30f;
    private static final float PANEL_W = 184f * SCALE;
    private static final float PANEL_H = 184f * SCALE;
    private static final float PANEL_R = 6f * SCALE;
    private static final float FIELD_H = 14f * SCALE;
    private static final float BUTTON_H = 16f * SCALE;
    private static final float GAP = 5f * SCALE;
    private static final float TITLE_FONT = 0.84f * TEXT_SCALE;
    private static final float LABEL_FONT = 0.55f * TEXT_SCALE;
    private static final float FIELD_FONT = 0.60f * TEXT_SCALE;
    private static final float BUTTON_FONT = 0.58f * TEXT_SCALE;
    private static final int FIELD_COUNT = 3;

    private final Screen parent;
    private final String[] values = {"", "", ""};
    private final float[] fieldHover = new float[FIELD_COUNT];
    private final float[] fieldFocus = new float[FIELD_COUNT];
    private Field focusedField;
    private ProxyType type = ProxyType.SOCKS5;
    private boolean enabled;
    private String status = "";
    private float fixedWidth;
    private float fixedHeight;
    private float openAnim;
    private float enableHover;
    private float enablePress;
    private float socks4Hover;
    private float socks5Hover;
    private float socks4Press;
    private float socks5Press;
    private float saveHover;
    private float savePress;
    private float clearHover;
    private float clearPress;
    private float backHover;
    private float backPress;
    private TextRenderer titleRenderer;
    private TextRenderer bodyRenderer;

    public CombatantProxyManagerScreen(Screen parent) {
        super(Component.translatable("screen.combatant.proxy_manager.title"));
        this.parent = parent;
    }

    private static float animate(float current, float target, float dt, float speed) {
        float next = AnimationUtility.approach(current, target, dt, speed);
        return AnimationUtility.snap(next, target, 0.001f);
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    @Override
    protected void init() {
        titleRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        bodyRenderer = Fonts.renderer("InterMedium", FontInfo.Type.Regular, titleRenderer);
        loadProxy();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateUiMetrics();
        renderBackgroundTexture();
        float fx = toFixedX(mouseX);
        float fy = toFixedY(mouseY);
        updateAnimations(fx, fy);

        ViewportContext.beginUnscaledLogical(context);
        Renderer2D.COLOR.begin();
        try {
            renderDimmer();
            renderPanel(fx, fy);
        } finally {
            Renderer2D.COLOR.render();
            ViewportContext.end(context);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return true;
        return handleClick(toFixedX((float) click.x()), toFixedY((float) click.y()));
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (focusedField != null) {
            int key = input.key();
            if (key == 259) {
                int index = focusedField.index;
                if (!values[index].isEmpty()) values[index] = values[index].substring(0, values[index].length() - 1);
                return true;
            }
            if (key == 258) {
                focusedField = focusedField.next();
                return true;
            }
            if (key == 257 || key == 335) {
                saveProxy();
                return true;
            }
            if (key == 256) {
                focusedField = null;
                return true;
            }
        }

        if (input.key() == 256) {
            closeToParent();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (focusedField == null) return super.charTyped(input);
        int cp = input.codepoint();
        if (cp < 32 || cp > 126) return true;
        int index = focusedField.index;
        int max = focusedField == Field.ADDRESS ? 96 : 64;
        if (values[index].length() >= max) return true;
        values[index] += Character.toString(cp);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void renderPanel(float mouseX, float mouseY) {
        Bounds b = bounds();
        float open = AnimationUtility.easeOutCubic(openAnim);
        PanelColors c = colors(open);

        Renderer2D.COLOR.blurRect(b.x, b.y, PANEL_W, PANEL_H, PANEL_R, 15f, 1.0f, 0.30f, 0xFFFFFF);
        Renderer2D.COLOR.roundedRectSoftShadow(b.x, b.y, PANEL_W, PANEL_H, PANEL_R, 12f * SCALE, 0.022f + open * 0.012f, c.shadow);
        Renderer2D.COLOR.roundedRectGradientQuad(b.x, b.y, PANEL_W, PANEL_H, PANEL_R, 1f, c.bgTopLeft, c.bgTopRight, c.bgBottomRight, c.bgBottomLeft);
        Renderer2D.COLOR.roundedRectMaskedQuad(b.x, b.y, PANEL_W, 22f * SCALE, b.x, b.y, PANEL_W, PANEL_H, PANEL_R, 1f,
                c.headerTopLeft, c.headerTopRight, c.headerBottomRight, c.headerBottomLeft);
        Renderer2D.COLOR.roundedRectStrokeGradient(b.x, b.y, PANEL_W, PANEL_H, PANEL_R, 1f, 1f, HudRenderUtil.mixColor(c.stroke, 0xFFFFFFFF, 0.04f), c.stroke, 90f);

        drawCentered(titleRenderer, tr("screen.combatant.proxy_manager.header"), b.x + PANEL_W * 0.5f, b.y + 7f * SCALE, TITLE_FONT, c.title);

        float innerX = b.x + 8f * SCALE;
        float innerW = PANEL_W - 16f * SCALE;
        float rowY = b.y + 28f * SCALE;
        renderToggle(innerX, rowY, 66f * SCALE, 13f * SCALE, tr(enabled ? "screen.combatant.proxy_manager.enabled" : "screen.combatant.proxy_manager.disabled"), enabled, enableHover, enablePress, c);
        renderTypeToggle(innerX + 72f * SCALE, rowY, innerW - 72f * SCALE, 13f * SCALE, c);

        float fieldY = b.y + 53f * SCALE;
        renderField(Field.ADDRESS, tr("screen.combatant.proxy_manager.address"), tr("screen.combatant.proxy_manager.placeholder.address"), innerX, fieldY, innerW, c);
        renderField(Field.USERNAME, tr("screen.combatant.proxy_manager.username"), tr("screen.combatant.proxy_manager.placeholder.optional"), innerX, fieldY + 27f * SCALE, innerW, c);
        renderField(Field.PASSWORD, tr("screen.combatant.proxy_manager.password"), tr("screen.combatant.proxy_manager.placeholder.optional"), innerX, fieldY + 54f * SCALE, innerW, c);

        float buttonY = b.y + PANEL_H - 26f * SCALE;
        float third = (innerW - GAP * 2f) / 3f;
        renderAction(innerX, buttonY, third, BUTTON_H, tr("screen.combatant.proxy_manager.save"), false, saveHover, savePress, c);
        renderAction(innerX + third + GAP, buttonY, third, BUTTON_H, tr("screen.combatant.proxy_manager.clear"), true, clearHover, clearPress, c);
        renderAction(innerX + (third + GAP) * 2f, buttonY, third, BUTTON_H, tr("screen.combatant.proxy_manager.back"), false, backHover, backPress, c);

        String info = status.isBlank() ? currentSummary() : status;
        draw(bodyRenderer, ellipsize(info, FIELD_FONT, innerW), innerX, b.y + PANEL_H - 42f * SCALE, FIELD_FONT, c.muted);
    }

    private void renderField(Field field, String label, String placeholder, float x, float y, float w, PanelColors c) {
        int i = field.index;
        draw(bodyRenderer, label, x, y, LABEL_FONT, c.label);
        float fy = y + 9f * SCALE;
        boolean focused = focusedField == field;
        float hoverAnim = AnimationUtility.easeOutCubic(fieldHover[i]);
        float focusAnim = AnimationUtility.easeOutCubic(fieldFocus[i]);
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 188), withAlpha(c.surfaceHover, 208), hoverAnim * 0.45f + focusAnim * 0.16f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 176), withAlpha(0xFF0D1118, 196), 0.18f);
        int stroke = focused ? HudRenderUtil.mixColor(c.strokeSoft, c.accentSoft, 0.55f) : HudRenderUtil.mixColor(c.stroke, c.strokeSoft, hoverAnim * 0.72f);
        Renderer2D.COLOR.roundedRectGradientQuad(x, fy, w, FIELD_H, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, fy, w, FIELD_H, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);
        if (focusAnim > 0.01f) {
            Renderer2D.COLOR.roundedRectGradientQuad(x + 1.2f * SCALE, fy + FIELD_H - 1.6f * SCALE, w - 2.4f * SCALE, 1.1f * SCALE, 0.6f * SCALE, 1f,
                    withAlpha(c.accentSoft, Math.round(48f * focusAnim)), withAlpha(c.accent, Math.round(28f * focusAnim)),
                    withAlpha(c.accent, Math.round(28f * focusAnim)), withAlpha(c.accentSoft, Math.round(48f * focusAnim)));
        }

        String value = values[i];
        String display = field == Field.PASSWORD && !value.isEmpty() ? "*".repeat(value.length()) : value;
        if (display.isEmpty() && !focused) display = placeholder;
        if (focused && AnimationUtility.blink(500L)) display += "|";
        int color = values[i].isEmpty() && !focused ? c.mutedLabel : c.title;
        draw(bodyRenderer, ellipsize(display, FIELD_FONT, w - 8f * SCALE), x + 4f * SCALE, fy + 4.3f * SCALE, FIELD_FONT, color);
    }

    private void renderTypeToggle(float x, float y, float w, float h, PanelColors c) {
        float gap = 2f * SCALE;
        float itemW = (w - gap) * 0.5f;
        renderChoice(x, y, itemW, h, "SOCKS5", type == ProxyType.SOCKS5, socks5Hover, socks5Press, c);
        renderChoice(x + itemW + gap, y, itemW, h, "SOCKS4", type == ProxyType.SOCKS4, socks4Hover, socks4Press, c);
    }

    private void renderToggle(float x, float y, float w, float h, String label, boolean selected, float hover, float press, PanelColors c) {
        renderChoice(x, y, w, h, label, selected, hover, press, c);
    }

    private void renderChoice(float x, float y, float w, float h, String label, boolean selected, float hover, float press, PanelColors c) {
        float hoverAnim = AnimationUtility.easeOutCubic(hover);
        float pressAnim = AnimationUtility.easeOutCubic(press);
        float active = selected ? 1f : 0f;
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 170), withAlpha(c.surfaceHover, 210), hoverAnim * 0.42f + active * 0.38f + pressAnim * 0.10f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 158), withAlpha(0x1A2418, 210), active * 0.24f + hoverAnim * 0.16f);
        int stroke = HudRenderUtil.mixColor(c.stroke, selected ? c.accentSoft : c.strokeSoft, active * 0.70f + hoverAnim * 0.30f + pressAnim * 0.10f);
        int text = selected ? HudRenderUtil.mixColor(c.title, c.accent, 0.28f) : HudRenderUtil.mixColor(c.muted, c.title, hoverAnim * 0.35f);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);
        drawCentered(bodyRenderer, label, x + w * 0.5f, y + 4.1f * SCALE - pressAnim * 0.35f * SCALE, BUTTON_FONT, text);
    }

    private void renderAction(float x, float y, float w, float h, String label, boolean destructive, float hover, float press, PanelColors c) {
        float hoverAnim = AnimationUtility.easeOutCubic(hover);
        float pressAnim = AnimationUtility.easeOutCubic(press);
        int fillTop;
        int fillBottom;
        int stroke;
        int text;
        if (destructive) {
            fillTop = HudRenderUtil.mixColor(withAlpha(0x1A1416, 170), withAlpha(0x4B2224, 212), 0.25f + hoverAnim * 0.45f + pressAnim * 0.12f);
            fillBottom = HudRenderUtil.mixColor(withAlpha(0x120E10, 165), withAlpha(0x261012, 220), 0.20f + hoverAnim * 0.40f + pressAnim * 0.10f);
            stroke = HudRenderUtil.mixColor(withAlpha(0x352A2A, 215), withAlpha(0x8C5054, 235), 0.22f + hoverAnim * 0.58f + pressAnim * 0.12f);
            text = HudRenderUtil.mixColor(withAlpha(0xD0A0A0, 255), withAlpha(0xFFB2B2, 255), 0.24f + hoverAnim * 0.56f + pressAnim * 0.08f);
        } else {
            fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 176), withAlpha(0x223746, 212), 0.20f + hoverAnim * 0.44f + pressAnim * 0.10f);
            fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 166), withAlpha(0x13292F, 220), 0.16f + hoverAnim * 0.40f + pressAnim * 0.08f);
            stroke = HudRenderUtil.mixColor(c.stroke, withAlpha(0x466A76, 228), 0.18f + hoverAnim * 0.50f + pressAnim * 0.10f);
            text = HudRenderUtil.mixColor(withAlpha(0xD0D8E4, 255), withAlpha(0xEEF8FF, 255), 0.18f + hoverAnim * 0.38f + pressAnim * 0.08f);
        }
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, 3f * SCALE, 7f * SCALE, 0.018f + hoverAnim * 0.015f, c.shadow);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);
        drawCentered(bodyRenderer, label, x + w * 0.5f, y + 5f * SCALE - (hoverAnim * 0.65f + pressAnim * 0.55f) * SCALE, BUTTON_FONT, text);
    }

    private boolean handleClick(float mouseX, float mouseY) {
        Bounds b = bounds();
        float innerX = b.x + 8f * SCALE;
        float innerW = PANEL_W - 16f * SCALE;
        float rowY = b.y + 28f * SCALE;
        focusedField = null;

        if (inside(mouseX, mouseY, innerX, rowY, 66f * SCALE, 13f * SCALE)) {
            enabled = !enabled;
            enablePress = 1f;
            status = enabled ? tr("screen.combatant.proxy_manager.status.enabled") : tr("screen.combatant.proxy_manager.status.disabled");
            return true;
        }

        float typeX = innerX + 72f * SCALE;
        float typeW = innerW - 72f * SCALE;
        float typeItemW = (typeW - 2f * SCALE) * 0.5f;
        if (inside(mouseX, mouseY, typeX, rowY, typeItemW, 13f * SCALE)) {
            type = ProxyType.SOCKS5;
            socks5Press = 1f;
            return true;
        }
        if (inside(mouseX, mouseY, typeX + typeItemW + 2f * SCALE, rowY, typeItemW, 13f * SCALE)) {
            type = ProxyType.SOCKS4;
            socks4Press = 1f;
            return true;
        }

        float fieldY = b.y + 53f * SCALE;
        for (Field field : Field.values()) {
            float y = fieldY + field.index * 27f * SCALE + 9f * SCALE;
            if (inside(mouseX, mouseY, innerX, y, innerW, FIELD_H)) {
                focusedField = field;
                return true;
            }
        }

        float buttonY = b.y + PANEL_H - 26f * SCALE;
        float third = (innerW - GAP * 2f) / 3f;
        if (inside(mouseX, mouseY, innerX, buttonY, third, BUTTON_H)) {
            savePress = 1f;
            saveProxy();
            return true;
        }
        if (inside(mouseX, mouseY, innerX + third + GAP, buttonY, third, BUTTON_H)) {
            clearPress = 1f;
            clearProxy();
            return true;
        }
        if (inside(mouseX, mouseY, innerX + (third + GAP) * 2f, buttonY, third, BUTTON_H)) {
            backPress = 1f;
            closeToParent();
            return true;
        }
        return true;
    }

    private void loadProxy() {
        ProxyEntry proxy = ProxyBackend.getDefaultProxy();
        type = proxy.type();
        values[Field.ADDRESS.index] = proxy.ipPort();
        values[Field.USERNAME.index] = proxy.username();
        values[Field.PASSWORD.index] = proxy.password();
        enabled = ProxyBackend.isEnabled();
        status = "";
    }

    private void saveProxy() {
        String address = values[Field.ADDRESS.index].trim();
        if (!address.isEmpty() && !ProxyEntry.isValidIpPort(address)) {
            status = tr("screen.combatant.proxy_manager.status.invalid_address");
            return;
        }
        if (address.isEmpty()) {
            enabled = false;
        }
        ProxyEntry entry = address.isEmpty()
                ? ProxyEntry.empty()
                : new ProxyEntry(type, address, values[Field.USERNAME.index], values[Field.PASSWORD.index]);
        ProxyBackend.setDefaultProxy(entry);
        ProxyBackend.setEnabled(enabled);
        status = address.isEmpty()
                ? tr("screen.combatant.proxy_manager.status.cleared")
                : tr("screen.combatant.proxy_manager.status.saved", type.name());
    }

    private void clearProxy() {
        values[0] = "";
        values[1] = "";
        values[2] = "";
        enabled = false;
        type = ProxyType.SOCKS5;
        focusedField = null;
        ProxyBackend.setDefaultProxy(ProxyEntry.empty());
        ProxyBackend.setEnabled(false);
        status = tr("screen.combatant.proxy_manager.status.cleared");
    }

    private String currentSummary() {
        if (!enabled) return tr("screen.combatant.proxy_manager.status.disabled");
        String address = values[Field.ADDRESS.index].trim();
        if (address.isEmpty()) return tr("screen.combatant.proxy_manager.status.enabled_no_address");
        return type.name() + " " + address;
    }

    private void updateAnimations(float mouseX, float mouseY) {
        float dt = AnimationUtility.deltaTime();
        openAnim = animate(openAnim, 1f, dt, 7f);
        Bounds b = bounds();
        float innerX = b.x + 8f * SCALE;
        float innerW = PANEL_W - 16f * SCALE;
        float rowY = b.y + 28f * SCALE;
        enableHover = animate(enableHover, inside(mouseX, mouseY, innerX, rowY, 66f * SCALE, 13f * SCALE) ? 1f : 0f, dt, 11f);
        enablePress = animate(enablePress, 0f, dt, 8f);

        float typeX = innerX + 72f * SCALE;
        float typeW = innerW - 72f * SCALE;
        float itemW = (typeW - 2f * SCALE) * 0.5f;
        socks5Hover = animate(socks5Hover, inside(mouseX, mouseY, typeX, rowY, itemW, 13f * SCALE) ? 1f : 0f, dt, 11f);
        socks4Hover = animate(socks4Hover, inside(mouseX, mouseY, typeX + itemW + 2f * SCALE, rowY, itemW, 13f * SCALE) ? 1f : 0f, dt, 11f);
        socks5Press = animate(socks5Press, 0f, dt, 8f);
        socks4Press = animate(socks4Press, 0f, dt, 8f);

        float fieldY = b.y + 53f * SCALE;
        for (Field field : Field.values()) {
            float y = fieldY + field.index * 27f * SCALE + 9f * SCALE;
            fieldHover[field.index] = animate(fieldHover[field.index], inside(mouseX, mouseY, innerX, y, innerW, FIELD_H) ? 1f : 0f, dt, 11f);
            fieldFocus[field.index] = animate(fieldFocus[field.index], focusedField == field ? 1f : 0f, dt, 10f);
        }

        float buttonY = b.y + PANEL_H - 26f * SCALE;
        float third = (innerW - GAP * 2f) / 3f;
        saveHover = animate(saveHover, inside(mouseX, mouseY, innerX, buttonY, third, BUTTON_H) ? 1f : 0f, dt, 11f);
        clearHover = animate(clearHover, inside(mouseX, mouseY, innerX + third + GAP, buttonY, third, BUTTON_H) ? 1f : 0f, dt, 11f);
        backHover = animate(backHover, inside(mouseX, mouseY, innerX + (third + GAP) * 2f, buttonY, third, BUTTON_H) ? 1f : 0f, dt, 11f);
        savePress = animate(savePress, 0f, dt, 8f);
        clearPress = animate(clearPress, 0f, dt, 8f);
        backPress = animate(backPress, 0f, dt, 8f);
    }

    private PanelColors colors(float alpha) {
        int titleAlpha = Math.round(alpha * 255f);
        int labelAlpha = Math.round(alpha * 155f);
        return new PanelColors(
                withAlpha(0x0D0F14, Math.round(alpha * 120f)),
                withAlpha(0x101218, Math.round(alpha * 120f)),
                withAlpha(0x0D0F14, Math.round(alpha * 120f)),
                withAlpha(0x08090C, Math.round(alpha * 120f)),
                withAlpha(0x14171F, Math.round(alpha * 150f)),
                withAlpha(0x181B24, Math.round(alpha * 150f)),
                withAlpha(0x14171F, Math.round(alpha * 150f)),
                withAlpha(0x10131A, Math.round(alpha * 150f)),
                withAlpha(0x252A36, Math.round(alpha * 100f)),
                withAlpha(0x3A4A5A, Math.round(alpha * 150f)),
                withAlpha(0x060810, Math.round(alpha * 80f)),
                withAlpha(0xFFFFFF, titleAlpha),
                withAlpha(0xFFFFFF, labelAlpha),
                withAlpha(0x808890, titleAlpha),
                withAlpha(0x606878, labelAlpha),
                withAlpha(0x1A1D24, Math.round(alpha * 160f)),
                withAlpha(0x1A1F28, Math.round(alpha * 200f)),
                withAlpha(0xFFD700, titleAlpha),
                withAlpha(0x4A3A10, Math.round(alpha * 180f))
        );
    }

    private void renderDimmer() {
        Themes.Theme t = Theme.theme();
        int soft = withAlpha(HudRenderUtil.mixColor(t.windowBg(), 0xFF000000, 0.46f), 140);
        int deep = withAlpha(HudRenderUtil.mixColor(t.windowBg(), 0xFF000000, 0.68f), 180);
        Renderer2D.COLOR.quad(0, 0, fixedWidth, fixedHeight, soft, soft, deep, deep);
    }

    private void renderBackgroundTexture() {
        Minecraft mc = this.minecraft;
        if (mc != null) MenuBackgroundRenderer.renderConfigured(mc);
    }

    private void updateUiMetrics() {
        Minecraft mc = minecraft;
        if (mc == null) {
            fixedWidth = width;
            fixedHeight = height;
            return;
        }
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        fixedWidth = Math.max(1f, HudScale.virtualWidth(fbw, fbh));
        fixedHeight = Math.max(1f, HudScale.virtualHeight(fbw, fbh));
    }

    private float toFixedX(float screenX) {
        Minecraft mc = minecraft;
        if (mc == null) return screenX;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (hudScale <= 0f) return screenX;
        return screenX * mc.getWindow().getGuiScale() / hudScale;
    }

    private float toFixedY(float screenY) {
        Minecraft mc = minecraft;
        if (mc == null) return screenY;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (hudScale <= 0f) return screenY;
        return screenY * mc.getWindow().getGuiScale() / hudScale;
    }

    private Bounds bounds() {
        return new Bounds(fixedWidth * 0.5f - PANEL_W * 0.5f, fixedHeight * 0.5f - PANEL_H * 0.5f);
    }

    private void closeToParent() {
        if (minecraft != null) ClientScreen.show(minecraft, parent);
    }

    private void draw(TextRenderer renderer, String text, float x, float y, float size, int argb) {
        if (renderer == null || text == null || text.isEmpty()) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    private void drawCentered(TextRenderer renderer, String text, float centerX, float y, float size, int argb) {
        draw(renderer, text, centerX - width(renderer, text, size) * 0.5f, y, size, argb);
    }

    private float width(TextRenderer renderer, String text, float size) {
        if (renderer == null || text == null) return 0f;
        renderer.begin(size, true, false);
        float w = (float) renderer.getWidth(text, false);
        renderer.end();
        return w;
    }

    private String ellipsize(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (width(bodyRenderer, text, size) <= maxWidth) return text;
        String out = text;
        while (out.length() > 3 && width(bodyRenderer, out + "...", size) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    private enum Field {
        ADDRESS(0),
        USERNAME(1),
        PASSWORD(2);

        private final int index;

        Field(int index) {
            this.index = index;
        }

        private Field next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private record Bounds(float x, float y) {
    }

    private record PanelColors(
            int bgTopLeft,
            int bgTopRight,
            int bgBottomRight,
            int bgBottomLeft,
            int headerTopLeft,
            int headerTopRight,
            int headerBottomRight,
            int headerBottomLeft,
            int stroke,
            int strokeSoft,
            int shadow,
            int title,
            int label,
            int muted,
            int mutedLabel,
            int surface,
            int surfaceHover,
            int accent,
            int accentSoft
    ) {
    }
}
