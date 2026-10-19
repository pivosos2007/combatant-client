/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.automation.ActionPriority;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.HoldJumpAction;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.movement.rules.MovementRules;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.player.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Climbs walls you walk into. VANILLA pushes the upward velocity while you are pressed against a wall; JUMP only
 * jumps from the ground when a wall blocks you, which is what a player can do by hand (it gets you over ledges, not
 * up a tall wall). The Grim preset forces JUMP, because Grim simulates the exact vertical motion of a player that
 * is not on a ladder.
 */
@ModuleInfo(
        id = "spider",
        displayName = "Spider",
        aliases = {"WallClimb", "Climb"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.spider.description")
public final class Spider extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.CUSTOM);
    private final EnumValue<Mode> mode = enumMode("mode", Mode.VANILLA);
    private final NumberValue<Double> climbSpeed = visibleWhen(
            num("climb_speed", 0.2D, 0.05D, 0.6D), () -> effectiveMode() == Mode.VANILLA);

    @Override
    public void onDisable() {
        AutomationRuntime.INSTANCE.cancelOwner(this, "disabled");
    }

    @Override
    public void onTick() {
        boolean wantTask = effectiveMode() == Mode.JUMP;
        boolean hasTask = AutomationRuntime.INSTANCE.hasTasks(this);
        if (wantTask && !hasTask) {
            AutomationRuntime.INSTANCE.submit(AutomationTask.builder(this, "SpiderJump",
                            new HoldJumpAction("SpiderJump", this::shouldJump))
                    .priority(ActionPriority.BACKGROUND)
                    .yieldToManualInput(java.util.Set.of())
                    .build());
        } else if (!wantTask && hasTask) {
            AutomationRuntime.INSTANCE.cancelOwner(this, "mode changed");
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || event.getType() != MoverType.SELF) return;
        if (effectiveMode() != Mode.VANILLA || !canClimb(player)) return;

        Vec3 movement = event.getMovement();
        double y = MovementRules.spiderVelocity(player.horizontalCollision, MovementUtil.isMoving(),
                player.isShiftKeyDown(), climbSpeed.get(), movement.y);
        if (y != movement.y) {
            event.setMovement(new Vec3(movement.x, y, movement.z));
            player.setDeltaMovement(player.getDeltaMovement().x, y, player.getDeltaMovement().z);
            player.resetFallDistance();
        }
    }

    private boolean shouldJump() {
        LocalPlayer player = mc.player;
        if (player == null || !canClimb(player)) return false;
        return MovementRules.spiderJump(player.horizontalCollision, MovementUtil.isMoving(),
                player.isShiftKeyDown(), player.onGround());
    }

    private boolean canClimb(LocalPlayer player) {
        return !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && !player.isPassenger() && !player.isFallFlying() && !player.isSpectator();
    }

    private Mode effectiveMode() {
        return anticheat.get().pick(mode.get(), Mode.JUMP, mode.get());
    }

    public enum Mode {
        VANILLA,
        JUMP
    }
}
