/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Walks on liquid surfaces. Distinct from WaterSpeed, which only accelerates swimming.
 */
@ModuleInfo(
        id = "jesus",
        displayName = "Jesus",
        aliases = {"LiquidWalk", "WaterWalk"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.jesus.description")
public final class Jesus extends Module {

    private static final double PROBE_DEPTH = 0.3D;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumSetting("jesusMode", "mode", Mode.SOLID, Mode.values());
    private final BooleanValue lava = bool("jesusLava", "lava", false);
    private final NumberValue<Double> lift = visibleWhen(num("jesusLift", "lift", 0.12D, 0.02D, 0.5D),
            () -> mode.get() != Mode.STEP);
    private final NumberValue<Integer> hopDelay = visibleWhen(num("jesusHopDelay", "hop_delay", 4, 1, 20),
            () -> mode.get() == Mode.STEP);

    private int hopTicks;

    @Override
    public void onEnable() {
        hopTicks = 0;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (player.isPassenger() || player.getAbilities().flying || player.isFallFlying()) return;
        if (mc.options.keyShift.isDown()) return;
        if (player.onClimbable()
                || mc.level.getBlockState(player.blockPosition()).is(net.minecraft.world.level.block.Blocks.COBWEB)
                || mc.level.getBlockState(BlockPos.containing(player.getEyePosition())).is(net.minecraft.world.level.block.Blocks.COBWEB)) {
            return;
        }

        switch (mode.get()) {
            case SOLID -> tickSolid(player);
            case DOLPHIN -> tickDolphin(player);
            case STEP -> tickStep(player);
        }
    }

    private void tickSolid(LocalPlayer player) {
        if (isSubmerged(player)) {
            if (mc.options.keyJump.isDown()) return;
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(v.y, lift.get()), v.z);
            return;
        }

        BlockPos below = liquidBelow(player);
        if (below == null) return;

        double surface = surfaceY(below);
        Vec3 v = player.getDeltaMovement();
        if (v.y <= 0.0D && player.getY() >= surface - PROBE_DEPTH) {
            double aligned = Math.ceil(surface * 64.0D) / 64.0D;
            player.setPos(player.getX(), Math.max(player.getY(), aligned), player.getZ());
            player.setDeltaMovement(v.x, 0.0D, v.z);
            player.setOnGround(true);
        }
    }

    private void tickDolphin(LocalPlayer player) {
        boolean submerged = isSubmerged(player);
        if (!submerged && liquidBelow(player) == null) return;

        Vec3 v = player.getDeltaMovement();
        double up = submerged ? lift.get() : lift.get() * 0.5D;
        player.setDeltaMovement(v.x, Math.max(v.y, up), v.z);
    }

    private void tickStep(LocalPlayer player) {
        if (isSubmerged(player)) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(v.y, 0.1D), v.z);
            return;
        }
        if (liquidBelow(player) == null) {
            hopTicks = 0;
            return;
        }

        if (++hopTicks >= hopDelay.get()) {
            hopTicks = 0;
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, 0.42D, v.z);
        }
    }

    /** Liquid block the player is standing on top of, or null. */
    private BlockPos liquidBelow(LocalPlayer player) {
        BlockPos pos = BlockPos.containing(player.getX(), player.getY() - 0.05D, player.getZ());
        return isLiquid(pos) ? pos : null;
    }

    private boolean isSubmerged(LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        return isLiquid(feet) && player.getY() < surfaceY(feet) - 0.1D;
    }

    private double surfaceY(BlockPos pos) {
        FluidState fluid = mc.level.getFluidState(pos);
        return pos.getY() + fluid.getHeight(mc.level, pos);
    }

    private boolean isLiquid(BlockPos pos) {
        FluidState fluid = mc.level.getFluidState(pos);
        if (fluid.isEmpty()) return false;
        return fluid.is(FluidTags.WATER) || (lava.get() && fluid.is(FluidTags.LAVA));
    }

    private enum Mode {
        SOLID,
        DOLPHIN,
        STEP
    }
}
