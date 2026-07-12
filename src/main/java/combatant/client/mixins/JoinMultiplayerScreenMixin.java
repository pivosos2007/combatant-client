/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;


import combatant.client.features.theme.Theme;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.util.proxy.ProxyBackend;
import combatant.client.util.proxy.ProxyEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class JoinMultiplayerScreenMixin {
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_X = 8f;
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_Y = 8f;
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_W = 24f;
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_H = 24f;
    @Unique
    private float combatant$proxyHover;
    @Unique
    private float combatant$proxyPress;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"))
    private void combatant$renderProxyEntry(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof JoinMultiplayerScreen)) return;
        float dt = AnimationUtility.deltaTime();
        boolean hovered = combatant$inside(mouseX, mouseY, COMBATANT_PROXY_BUTTON_X, COMBATANT_PROXY_BUTTON_Y,
                COMBATANT_PROXY_BUTTON_W, COMBATANT_PROXY_BUTTON_H);
        combatant$proxyHover = combatant$animate(combatant$proxyHover, hovered ? 1f : 0f, dt, 11f);
        combatant$proxyPress = combatant$animate(combatant$proxyPress, 0f, dt, 8f);

        ViewportContext.beginScaled(context);
        Renderer2D.COLOR.begin();
        try {
            combatant$drawProxyButton();
        } finally {
            Renderer2D.COLOR.render();
            ViewportContext.end(context);
        }
    }

    @Unique
    private void combatant$drawProxyButton() {
        float hover = AnimationUtility.easeOutCubic(combatant$proxyHover);
        float press = AnimationUtility.easeOutCubic(combatant$proxyPress);
        Themes.Theme theme = Theme.theme();
        boolean enabled = ProxyBackend.isEnabled();
        ProxyEntry active = ProxyBackend.activeProxy();
        boolean configured = active.isConfigured();

        float size = COMBATANT_PROXY_BUTTON_W * (1.0f + hover * 0.035f - press * 0.025f);
        float x = COMBATANT_PROXY_BUTTON_X + (COMBATANT_PROXY_BUTTON_W - size) * 0.5f;
        float y = COMBATANT_PROXY_BUTTON_Y + (COMBATANT_PROXY_BUTTON_H - size) * 0.5f - hover * 0.35f;
        float radius = 5.0f;
        float centerX = x + size * 0.5f;
        float centerY = y + size * 0.5f;
        int accent = enabled && configured ? theme.accent() : 0x8A94A3;
        int baseTop = enabled && configured ? 0x222A20 : 0x1B2028;
        int baseBottom = enabled && configured ? 0x121812 : 0x10141B;
        int fillTop = HudRenderUtil.mixColor(withAlpha(baseTop, 226), withAlpha(accent, 96), (enabled && configured ? 0.18f : 0.06f) + hover * 0.12f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(baseBottom, 236), withAlpha(accent, 70), (enabled && configured ? 0.12f : 0.04f) + hover * 0.08f);
        int stroke = HudRenderUtil.mixColor(withAlpha(0x46505D, 180), withAlpha(accent, 218), (enabled && configured ? 0.38f : 0.10f) + hover * 0.35f);
        int iconColor = enabled && configured
                ? HudRenderUtil.mixColor(withAlpha(0xFFFFFF, 245), withAlpha(theme.accent(), 255), 0.18f)
                : withAlpha(0xC5CCD6, 235);

        Renderer2D.COLOR.roundedRectSoftShadow(x, y + 0.8f, size, size, radius,
                7.5f, 0.026f + hover * 0.012f, withAlpha(0x060810, 96));
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, size, size, radius, 1f,
                fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x + 0.5f, y + 0.5f, size - 1.0f, size - 1.0f,
                radius - 0.5f, 1f, 0.55f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.08f), stroke, 90f);
        Renderer2D.COLOR.svg("shield-user", centerX - 5.6f, centerY - 5.6f, 11.2f, 11.2f, SvgRenderOptions.overrideColor(iconColor));
    }

    @Unique
    private static float combatant$animate(float current, float target, float dt, float speed) {
        float next = AnimationUtility.approach(current, target, dt, speed);
        return AnimationUtility.snap(next, target, 0.001f);
    }

    @Unique
    private static boolean combatant$inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Unique
    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

}
