/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import combatant.client.features.gui.clickgui.*;
import combatant.client.features.gui.preview.VisualPreviewScreen;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.events.Events;
import combatant.client.events.impl.KeyInputEvent;
import combatant.client.features.gui.chat.BetterChatRenderer;
import combatant.client.features.gui.chat.BetterChatSearch;
import combatant.client.features.gui.chat.BetterChatStoreManager;
import combatant.client.features.gui.clickgui.*;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.features.module.ModuleManager;
import combatant.client.mixins.accessors.ChatScreenAccessor;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.NarratorBlocker;
import combatant.client.util.input.KeyManager;
import combatant.client.util.text.ClipboardUtil;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {


    @Unique
    private static void forwardMovementKeys(Options options, KeyEvent input, boolean pressed) {
        if (options.keyUp.matches(input)
                || options.keyDown.matches(input)
                || options.keyLeft.matches(input)
                || options.keyRight.matches(input)
                || options.keyJump.matches(input)
                || options.keyShift.matches(input)
                || options.keySprint.matches(input)) {
            InputConstants.Key key = InputConstants.getKey(input);
            KeyMapping.set(key, pressed);
        }
    }

    @Unique
    private static void releaseMovementKeys(Options options) {
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keyShift.setDown(false);
        options.keySprint.setDown(false);
    }

    // ===== CHAR TYPING =====
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void combatant$onChar(long window, CharacterEvent input, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof ChatScreen
                && BetterChat.isActive()) {
            BetterChat betterChat = BetterChat.get();
            if (betterChat != null && betterChat.isPasswordRevealBindHeld()) {
                ci.cancel();
                return;
            }
        }
        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof ChatScreen
                && BetterChat.isActive()
                && BetterChatSearch.isActive()) {
            BetterChatSearch.append((char) input.codepoint());
            ci.cancel();
            return;
        }
        // VisualPreviewScreen owns its text fields. The enabled ClickGui module must not route
        // characters into the hidden ClickGui section while this separate screen is active.
        if (ClientScreen.current() instanceof VisualPreviewScreen) return;
        if (!RuntimeGate.canRunClientLogic() || !ModuleManager.isEnabled("clickgui")) return;

        char c = (char) input.codepoint();

        if (ClickGuiSearch.isActive()) {
            ClickGuiRenderer.onCharTyped(c);
            ci.cancel();
            return;
        }

        if (ClickGuiRenderer.onCharTyped(c)) {
            ci.cancel();
        }
    }

    // ===== KEY DOWN =====
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void combatant$onKey(long window, int action, KeyEvent input, CallbackInfo ci) {
        KeyManager.handleKeyEvent(input.key(), input.scancode(), action);

        // Preserve low-level key state above, then leave the event entirely to the active preview
        // Screen. In particular, do this before addon events and the global ClickGui Esc handler.
        if (ClientScreen.current() instanceof VisualPreviewScreen) return;

        Minecraft mc = Minecraft.getInstance();
        KeyInputEvent event = new KeyInputEvent(action, input);
        Events.BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof ChatScreen
                && BetterChat.isActive()) {
            BetterChat betterChat = BetterChat.get();
            if (betterChat != null && betterChat.consumePasswordRevealToggle()) {
                if (action != GLFW.GLFW_RELEASE) {
                    BetterChatRenderer.togglePasswordRevealFromBind();
                    ci.cancel();
                    return;
                }
            }
        }

        Screen screen = ClientScreen.current();
        boolean narratorHotkeyContext = screen == null
                || !(screen.getFocused() instanceof EditBox)
                || !((EditBox) screen.getFocused()).canConsumeInput();
        if (RuntimeGate.canRunClientLogic()
                && NarratorBlocker.isBlocked()
                && narratorHotkeyContext
                && action != GLFW.GLFW_RELEASE
                && input.hasControlDownWithQuirk()
                && input.key() == GLFW.GLFW_KEY_B) {
            NarratorBlocker.enforce(mc);
            ci.cancel();
            return;
        }

        if (RuntimeGate.canRunClientLogic()
                && ModuleManager.isEnabled("clickgui")
                && (ClientScreen.current() instanceof ClickGuiScreen
                || ClientScreen.current() instanceof ClickGuiPickerScreen
                || ClientScreen.current() instanceof ClickGuiEditorScreen)
        ) {
            if (ClickGuiRenderer.isBlockingModuleKeybinds()) {
                releaseMovementKeys(mc.options);
            } else {
                forwardMovementKeys(mc.options, input, action != GLFW.GLFW_RELEASE);
            }
        }
        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof ChatScreen
                && BetterChat.isActive()
                && BetterChatSearch.isActive()) {
            int key = input.key();
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                boolean ctrlOrCmd = input.hasControlDownWithQuirk();

                if (ctrlOrCmd && key == GLFW.GLFW_KEY_V) {
                    BetterChatSearch.append(ClipboardUtil.get());
                    ci.cancel();
                    return;
                }

                if (ctrlOrCmd && key == GLFW.GLFW_KEY_A) {
                    BetterChatSearch.clearText();
                    ci.cancel();
                    return;
                }

                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    BetterChatSearch.backspace();
                    ci.cancel();
                    return;
                }

                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    BetterChatRenderer.resetScroll();
                    BetterChatSearch.startSearch(BetterChatStoreManager.getActiveStore(mc));
                    BetterChatSearch.setActive(false);
                    ci.cancel();
                    return;
                }

                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    BetterChatSearch.deactivate();
                    ci.cancel();
                    return;
                }
            }
        }
        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof ChatScreen
                && BetterChat.isActive()
                && !BetterChatSearch.isActive()) {
            if (action == 1) { // press
                int key = input.key();
                if (key == 257 || key == 335) { // enter
                    EditBox field = ((ChatScreenAccessor) ClientScreen.current()).getChatField();
                    BetterChatRenderer.applyAutoPrefixIfNeeded(field);
                }
            }
        }
        if (!RuntimeGate.canRunClientLogic() || !ModuleManager.isEnabled("clickgui")) return;

        int key = input.key();
        int scancode = input.scancode();
        int modifiers = input.modifiers();

        // ESC вЂ” Р·Р°РєСЂС‹С‚СЊ GUI
        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
            if (ClickGuiRenderer.onKey(GLFW.GLFW_KEY_ESCAPE, scancode, GLFW.GLFW_PRESS, modifiers)) {
                ci.cancel();
                return;
            }
            if (ClientScreen.current() instanceof ClickGuiEditorScreen) {
                ClickGuiRenderer.closeGuiEditor();
                ci.cancel();
                return;
            }
            ModuleManager.setEnabled("clickgui", false);
            ci.cancel();
            return;
        }

        // РџРѕРёСЃРє РІ GUI
        if (ClickGuiSearch.isActive()) {
            ClickGuiRenderer.onKey(key, scancode, action, modifiers);
            ci.cancel();
            return;
        }

        // РћР±С‹С‡РЅРѕРµ СЃРѕР±С‹С‚РёРµ РєР»Р°РІРёС€Рё
        if (ClickGuiRenderer.onKey(key, scancode, action, modifiers)) {
            ci.cancel();
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void combatant$betterChatCopy(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (action != 1) return; // GLFW_PRESS
        Minecraft mc = Minecraft.getInstance();
        if (!(ClientScreen.current() instanceof ChatScreen)) return;
        if (!RuntimeGate.canRunHud()) return;
        if (!BetterChat.isActive()) return;
        if ((input.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        if (input.key() == GLFW.GLFW_KEY_C) {
            BetterChatRenderer.copySelectionToClipboard();
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void combatant$betterChat$clearOnDebug(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        if (input.key() != GLFW.GLFW_KEY_D) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (!RuntimeGate.canRunHud()) return;
        // F3 + D clears vanilla chat; mirror it for BetterChat
        if (InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F3)) {
            BetterChatStoreManager.clearActive();
            BetterChatRenderer.resetScroll();
        }
    }
}




