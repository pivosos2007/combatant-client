/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.gui.clickgui.*;
import combatant.client.features.gui.preview.VisualPreviewScreen;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.gui.chat.BetterChatRenderer;
import combatant.client.features.gui.chat.BetterChatSearch;
import combatant.client.features.gui.chat.BetterChatStoreManager;
import combatant.client.features.gui.clickgui.*;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.features.module.ModuleManager;
import combatant.client.mixins.accessors.ChatScreenAccessor;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.NarratorBlocker;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputAction;
import combatant.client.input.InputDevice;
import combatant.client.input.InputKeys;
import combatant.client.input.event.KeyboardInputEvent;
import combatant.client.input.minecraft.MinecraftInputAdapter;
import combatant.client.util.text.ClipboardUtil;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {


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
        if (MinecraftInputAdapter.text(new String(Character.toChars(input.codepoint()))).isCancelled()) {
            ci.cancel();
            return;
        }
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
        Screen activeScreen = ClientScreen.current();
        if (!(activeScreen instanceof ClickGuiScreen
                || activeScreen instanceof ClickGuiPickerScreen
                || activeScreen instanceof ClickGuiEditorScreen
                || activeScreen instanceof VisualPreviewScreen)) return;

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
        KeyboardInputEvent event = MinecraftInputAdapter.keyboard(action, input);
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        // Preserve low-level key state above, then leave the event entirely to the active preview
        // Screen. In particular, do this before addon events and the global ClickGui Esc handler.
        if (ClientScreen.current() instanceof VisualPreviewScreen) return;

        Minecraft mc = Minecraft.getInstance();
        if (event.action() == InputAction.PRESS
                && ClientScreen.current() instanceof ChatScreen
                && RuntimeGate.canRunHud()
                && BetterChat.isActive()
                && event.modifiers().shortcut()
                && event.key().shortcutKey() == 'c') {
            BetterChatRenderer.copySelectionToClipboard();
        }
        if (event.action() == InputAction.PRESS
                && event.key().physicalKey() == InputKeys.D
                && RuntimeGate.canRunHud()
                && CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.F3)) {
            BetterChatStoreManager.clearActive();
            BetterChatRenderer.resetScroll();
        }

        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof ChatScreen
                && BetterChat.isActive()) {
            BetterChat betterChat = BetterChat.get();
            if (betterChat != null && betterChat.consumePasswordRevealToggle()) {
                if (event.action().isDown()) {
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
                && event.action().isDown()
                && event.modifiers().shortcut()
                && event.key().shortcutKey() == 'b') {
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
                MinecraftInputAdapter.forwardMovementKey(mc.options, input, event.action() != InputAction.RELEASE);
            }
        }
        if (RuntimeGate.canRunHud()
                && ClientScreen.current() instanceof ChatScreen
                && BetterChat.isActive()
                && BetterChatSearch.isActive()) {
            int key = event.key().physicalKey();
            if (event.action().isDown()) {
                boolean ctrlOrCmd = event.modifiers().shortcut();
                int shortcutKey = event.key().shortcutKey();

                if (ctrlOrCmd && shortcutKey == 'v') {
                    BetterChatSearch.append(ClipboardUtil.get());
                    ci.cancel();
                    return;
                }

                if (ctrlOrCmd && shortcutKey == 'a') {
                    BetterChatSearch.clearText();
                    ci.cancel();
                    return;
                }

                if (key == InputKeys.BACKSPACE) {
                    BetterChatSearch.backspace();
                    ci.cancel();
                    return;
                }

                if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER) {
                    BetterChatRenderer.resetScroll();
                    BetterChatSearch.startSearch(BetterChatStoreManager.getActiveStore(mc));
                    BetterChatSearch.setActive(false);
                    ci.cancel();
                    return;
                }

                if (key == InputKeys.ESCAPE) {
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
            if (event.action() == InputAction.PRESS) {
                int key = event.key().physicalKey();
                if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER) {
                    EditBox field = ((ChatScreenAccessor) ClientScreen.current()).getChatField();
                    BetterChatRenderer.applyAutoPrefixIfNeeded(field);
                }
            }
        }
        if (!RuntimeGate.canRunClientLogic() || !ModuleManager.isEnabled("clickgui")) return;
        Screen activeScreen = ClientScreen.current();
        if (!(activeScreen instanceof ClickGuiScreen
                || activeScreen instanceof ClickGuiPickerScreen
                || activeScreen instanceof ClickGuiEditorScreen
                || activeScreen instanceof VisualPreviewScreen)) return;

        int key = event.key().physicalKey();

        // ESC вЂ” Р·Р°РєСЂС‹С‚СЊ GUI
        if (key == InputKeys.ESCAPE && event.action() == InputAction.PRESS) {
            if (ClickGuiRenderer.onKey(event.key(), event.action(), event.modifiers())) {
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
            ClickGuiRenderer.onKey(event.key(), event.action(), event.modifiers());
            ci.cancel();
            return;
        }

        // РћР±С‹С‡РЅРѕРµ СЃРѕР±С‹С‚РёРµ РєР»Р°РІРёС€Рё
        if (ClickGuiRenderer.onKey(event.key(), event.action(), event.modifiers())) {
            ci.cancel();
        }
    }

    @Inject(method = "textInput", at = @At("HEAD"), cancellable = true)
    private void combatant$textInput(long window, String text, CallbackInfo ci) {
        if (MinecraftInputAdapter.text(text).isCancelled()) ci.cancel();
    }

    @Inject(method = "textEditing", at = @At("HEAD"), cancellable = true)
    private void combatant$textEditing(long window, @Nullable PreeditEvent event, CallbackInfo ci) {
        if (MinecraftInputAdapter.composition(event).isCancelled()) ci.cancel();
    }
}




