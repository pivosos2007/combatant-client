/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import combatant.client.features.gui.mainmenu.CombatantProxyManagerScreen;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.gui.chat.BetterChatRenderer;
import combatant.client.features.gui.clickgui.ClickGuiEditorScreen;
import combatant.client.features.gui.clickgui.ClickGuiPickerScreen;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiScreen;
import combatant.client.features.gui.preview.VisualPreviewScreen;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.impl.DynamicIsland;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.module.modules.visuals.Zoom;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.input.KeyManager;
import combatant.client.util.logging.DebugLog;

@Mixin(MouseHandler.class)
public class MouseMixin {


    @Unique
    private double lastX;
    @Unique
    private double lastY;
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_X = 8f;
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_Y = 8f;
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_W = 24f;
    @Unique
    private static final float COMBATANT_PROXY_BUTTON_H = 24f;

    // ===== Mouse scroll for ClickGUI =====
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    public void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (RuntimeGate.canRunClientLogic()
                && ModuleManager.isEnabled("clickgui")
                && ClientScreen.current() == null) {
            ClickGuiRenderer.onMouseScroll(lastX, lastY, vertical);
            ci.cancel();
            return;
        }
        if (RuntimeGate.canRunHud()
                && ClientScreen.current(net.minecraft.client.Minecraft.getInstance()) instanceof net.minecraft.client.gui.screens.ChatScreen) {
            if (BetterChatRenderer.onScroll(vertical)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void betterChat$mouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        KeyManager.handleMouseButtonEvent(input.button(), action);

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (RuntimeGate.canRunHud() && mc != null) {
            boolean pressed = action == 1;
            int button = input.button();
            if (pressed && button == 0 && ClientScreen.current() instanceof JoinMultiplayerScreen screen) {
                double scale = Math.max(1.0, mc.getWindow().getGuiScale());
                float mx = (float) (mc.mouseHandler.xpos() / scale);
                float my = (float) (mc.mouseHandler.ypos() / scale);
                if (combatant$proxyButtonHit(mx, my)) {
                    ClientScreen.show(mc, new CombatantProxyManagerScreen(screen));
                    ci.cancel();
                    return;
                }
            }
            if (ClientScreen.current() instanceof net.minecraft.client.gui.screens.ChatScreen) {
                double mx = mc.mouseHandler.xpos();
                double my = mc.mouseHandler.ypos();
                if (BetterChatRenderer.onMouseButton(mx, my, button, pressed)) {
                    ci.cancel();
                    return;
                }
            }
            if ((ClientScreen.current() instanceof net.minecraft.client.gui.screens.ChatScreen
                    || DynamicIsland.shouldRenderInScreenOverlay(ClientScreen.current()))
                    && StaticHudElementRegistry.handleMouseButton(button, pressed)) {
                ci.cancel();
            }
        }
    }

    @Unique
    private static boolean combatant$proxyButtonHit(float mx, float my) {
        return mx >= COMBATANT_PROXY_BUTTON_X
                && mx <= COMBATANT_PROXY_BUTTON_X + COMBATANT_PROXY_BUTTON_W
                && my >= COMBATANT_PROXY_BUTTON_Y
                && my <= COMBATANT_PROXY_BUTTON_Y + COMBATANT_PROXY_BUTTON_H;
    }

    // ===== Mouse button for ClickGUI =====
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void clickGuiMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (RuntimeGate.canRunClientLogic()
                && ModuleManager.isEnabled("clickgui")
                && ClientScreen.current() == null) {

            // action == 1 — это "нажато" (см. оригинальный код: boolean bl = action == 1;)
            boolean pressed = (action == 1);

            // Кнопка теперь внутри MouseInput
            int button = input.button();

            DebugLog.info("[CLICK] button=%d action=%d x=%.2f y=%.2f",
                    button, action, lastX, lastY);

            ClickGuiRenderer.onMouseButton(lastX, lastY, button, pressed);

            ci.cancel();
        }
    }

    // ===== Mouse move for ClickGUI =====
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void clickGuiCursorMove(long window, double x, double y, CallbackInfo ci) {

        Minecraft mc = Minecraft.getInstance();

        // Always track last mouse position
        lastX = x;
        lastY = y;

        if (RuntimeGate.canRunClientLogic()
                && ModuleManager.isEnabled("clickgui")
                && ClientScreen.current() == null) {
            ClickGuiRenderer.onMouseMove(x, y);
            ci.cancel();
            return;
        }

        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof net.minecraft.client.gui.screens.ChatScreen) {
            BetterChatRenderer.onMouseMove(x, y);
        }
    }

    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void freecam$look(LocalPlayer player, double dx, double dy) {
        Freecam fc = Modules.get(Freecam.class);

        if (!RuntimeGate.canRunClientLogic() || fc == null || !fc.isEnabled()) {
            player.turn(dx, dy);
            return;
        }

        if (!fc.isCameraInput()) {
            player.turn(dx, dy);
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        double scale = f * f * f * 8.0;

        float yawDelta = (float) (dx * scale * 0.15F);
        float pitchDelta = (float) (dy * scale * 0.15F);

        fc.camYaw += yawDelta;
        fc.camPitch += pitchDelta;

        fc.camPitch = Mth.clamp(fc.camPitch, -90.0F, 90.0F);
    }

    @ModifyExpressionValue(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object combatant$zoomSensitivity(Object original) {
        Zoom zoom = Modules.get(Zoom.class);
        if (!RuntimeGate.canRunClientLogic() || zoom == null || !zoom.isEnabled() || !zoom.shouldApplyZoom())
            return original;

        if (!(original instanceof Double originalValue)) return original;

        float divisor = zoom.getZoomDivisor();
        if (divisor <= 1.0f) return original;

        float percent = zoom.getSensitivityPercent() / 100.0f;
        double factor = Mth.lerp(percent, 1.0f, divisor);
        if (factor <= 0.0001) return original;
        return originalValue / factor;
    }
}
