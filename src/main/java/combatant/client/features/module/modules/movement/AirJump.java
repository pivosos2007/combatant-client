/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.KeyInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Freecam;

//todo Description
@ModuleInfo(
        id = "airjump",
        displayName = "AirJump",
        category = ModuleCategory.MOVEMENT
)
public class AirJump extends Module {

    private final EnumValue<Mode> mode =
            enumMode("mode", Mode.NORMAL, Mode.values());
    private int level;
    private boolean maintainLevelReady;

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        level = mc.player.blockPosition().getY();
        maintainLevelReady = false;
    }

    @EventHandler
    private void onKeyInput(KeyInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (ClientScreen.current() != null || mc.player.onGround() || isFreecamActive()) return;

        if (mc.options.keyJump.matches(event.getInput())) {
            level = mc.player.blockPosition().getY();
            maintainLevelReady = false;
            jumpVanillaClamped(mc.player);
        } else if (mc.options.keyShift.matches(event.getInput())) {
            level--;
            maintainLevelReady = false;
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;
        if (mc.player.onGround()) {
            maintainLevelReady = false;
            return;
        }
        if (isFreecamActive()) return;

        if (mc.player.getY() > level + 0.2) {
            maintainLevelReady = true;
        }

        if (mode.get() == Mode.MAINTAIN_LEVEL
                && mc.options.keyJump.isDown()
                && maintainLevelReady
                && mc.player.getDeltaMovement().y <= 0.0
                && mc.player.blockPosition().getY() == level) {
            maintainLevelReady = false;
            jumpVanillaClamped(mc.player);
        }
    }

    private boolean isFreecamActive() {
        return Modules.get(Freecam.class) != null && Modules.get(Freecam.class).isEnabled();
    }

    public boolean performNoFallJump() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || isFreecamActive()) return false;

        level = mc.player.blockPosition().getY();
        maintainLevelReady = false;
        jumpVanillaClamped(mc.player);
        return true;
    }

    private void jumpVanillaClamped(LocalPlayer player) {
        if (player == null) return;

        player.jumpFromGround();
    }

    public enum Mode {
        NORMAL,
        MAINTAIN_LEVEL
    }
}
