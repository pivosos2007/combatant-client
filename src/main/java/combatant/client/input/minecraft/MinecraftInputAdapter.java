/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputAction;
import combatant.client.input.InputFocus;
import combatant.client.input.InputKey;
import combatant.client.input.InputKeyCatalog;
import combatant.client.input.InputModifiers;
import combatant.client.input.event.KeyboardInputEvent;
import combatant.client.input.event.MouseInputEvent;
import combatant.client.input.event.ScrollInputEvent;
import combatant.client.input.event.TextCompositionEvent;
import combatant.client.input.event.TextInputEvent;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

public final class MinecraftInputAdapter {
    private static Object platformTextOwner;

    private MinecraftInputAdapter() {
    }

    public static KeyboardInputEvent keyboard(int platformAction, KeyEvent event) {
        refreshFocus();
        InputKey key = InputKeyCatalog.keyboardEvent(event.key(), event.shortcutKey());
        return CombatantInput.router().route(new KeyboardInputEvent(
                key,
                action(platformAction),
                modifiers(event),
                CombatantInput.focus().snapshot()
        ));
    }

    public static MouseInputEvent mouse(int platformAction, MouseButtonInfo info, double x, double y) {
        refreshFocus();
        InputKey key = InputKeyCatalog.mouseEvent(info.button());
        return CombatantInput.router().route(new MouseInputEvent(
                key,
                action(platformAction),
                modifiers(info),
                CombatantInput.focus().snapshot(),
                x,
                y
        ));
    }

    public static ScrollInputEvent scroll(double x, double y, double horizontal, double vertical) {
        refreshFocus();
        CombatantInput.state().updateMousePosition(x, y);
        return CombatantInput.router().route(new ScrollInputEvent(
                x, y, horizontal, vertical, CombatantInput.focus().snapshot()
        ));
    }

    public static TextInputEvent text(String text) {
        refreshFocus();
        TextInputEvent event = CombatantInput.router().route(new TextInputEvent(text, CombatantInput.focus().snapshot()));
        if (!event.isCancelled() && CombatantTextInputBridge.deliverText(text)) event.cancel();
        return event;
    }

    public static TextCompositionEvent composition(@Nullable PreeditEvent event) {
        refreshFocus();
        InputFocus.Snapshot focus = CombatantInput.focus().snapshot();
        if (event == null) {
            TextCompositionEvent cleared = CombatantInput.router().route(
                    new TextCompositionEvent("", 0, java.util.List.of(), -1, focus));
            if (!cleared.isCancelled() && CombatantTextInputBridge.deliverComposition(cleared)) cleared.cancel();
            return cleared;
        }
        TextCompositionEvent composition = CombatantInput.router().route(new TextCompositionEvent(
                event.fullText(), event.caretPosition(), event.blocks(), event.focusedBlock(), focus
        ));
        if (!composition.isCancelled() && CombatantTextInputBridge.deliverComposition(composition)) composition.cancel();
        return composition;
    }

    public static void mouseMoved(double x, double y) {
        CombatantInput.state().updateMousePosition(x, y);
    }

    public static boolean matches(KeyMapping mapping, InputKey key) {
        return mapping != null
                && key != null
                && key.device() == combatant.client.input.InputDevice.KEYBOARD
                && mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(key.physicalKey()));
    }

    public static boolean isPressed(KeyMapping mapping) {
        if (mapping == null) return false;
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        combatant.client.input.InputDevice device = key.getType() == InputConstants.Type.MOUSE
                ? combatant.client.input.InputDevice.MOUSE
                : combatant.client.input.InputDevice.KEYBOARD;
        return CombatantInput.state().isPressed(device, key.getValue());
    }

    public static void forwardMovementKey(Options options, KeyEvent event, boolean pressed) {
        if (options.keyUp.matches(event)
                || options.keyDown.matches(event)
                || options.keyLeft.matches(event)
                || options.keyRight.matches(event)
                || options.keyJump.matches(event)
                || options.keyShift.matches(event)
                || options.keySprint.matches(event)) {
            KeyMapping.set(InputConstants.getKey(event), pressed);
        }
    }

    public static void refreshFocus() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft == null ? null : ClientScreen.current(minecraft);
        CombatantTextInputBridge.syncScreen(screen);
        CombatantInput.focus().updateScreen(screen);

        Object focused = screen == null ? null : screen.getFocused();
        Object nextTextOwner = focused instanceof EditBox editBox && editBox.canConsumeInput() ? focused : null;
        if (platformTextOwner != nextTextOwner) {
            if (platformTextOwner != null) CombatantInput.focus().releaseText(platformTextOwner);
            platformTextOwner = nextTextOwner;
            if (platformTextOwner != null) CombatantInput.focus().claimText(platformTextOwner);
        }
        CombatantInput.bindings().onFocusChanged(CombatantInput.focus().snapshot());
    }

    private static InputAction action(int action) {
        return switch (action) {
            case InputConstants.PRESS -> InputAction.PRESS;
            case InputConstants.RELEASE -> InputAction.RELEASE;
            case InputConstants.REPEAT -> InputAction.REPEAT;
            default -> throw new IllegalArgumentException("Unknown Minecraft input action: " + action);
        };
    }

    public static InputKey key(KeyEvent event) {
        return InputKeyCatalog.keyboardEvent(event.key(), event.shortcutKey());
    }

    public static InputModifiers modifiers(InputWithModifiers input) {
        int raw = input.modifiers();
        return new InputModifiers(
                (raw & InputConstants.MOD_SHIFT) != 0,
                (raw & InputConstants.MOD_CONTROL) != 0,
                (raw & InputConstants.MOD_ALT) != 0,
                (raw & InputConstants.MOD_SUPER) != 0,
                (raw & InputConstants.MOD_CAPS_LOCK) != 0,
                (raw & InputConstants.MOD_NUM_LOCK) != 0,
                input.hasControlDownWithQuirk()
        );
    }
}
