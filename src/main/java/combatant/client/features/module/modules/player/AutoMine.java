/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.relations.CategoryService;
import combatant.client.util.combat.AntiBotTracker;
import combatant.client.util.screen.ClientScreen;

/**
 * Picks blocks to mine on its own.
 *
 * <p>{@code CITY} is the anarchy mode: it finds the nearest enemy and feeds their burrow block,
 * then their surround, to {@link SpeedMine}, so rebreak and double mine apply for free. With double
 * mine the burrow is parked in the server's delayed slot while a surround block digs alongside it.
 * Surround blocks that leave a crystal spot (obsidian or bedrock below, air above) go first. Once
 * the surround has a hole it stops, because that hole is what a crystal aura needs and mining more
 * would move the server's destroy position off the rebreak block.</p>
 *
 * <p>{@code TUNNEL} is the tunnel miner: hold the view at a fixed angle and keep digging
 * whatever the crosshair hits. With SpeedMine on, the dig goes through the packet miner.</p>
 */
@ModuleInfo(
        id = "automine",
        displayName = "AutoMine",
        aliases = {"AutoCity"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.automine.description")
public final class AutoMine extends Module {

    private final EnumValue<Mode> mode = enumMode("mode", Mode.CITY);

    private final NumberValue<Float> targetRange =
            visibleWhen(num("target_range", 6.0f, 2.0f, 12.0f), () -> mode.get() == Mode.CITY);
    private final BooleanValue burrow = visibleWhen(bool("burrow", true), () -> mode.get() == Mode.CITY);
    private final BooleanValue surround = visibleWhen(bool("surround", true), () -> mode.get() == Mode.CITY);
    private final BooleanValue stopWhenOpen =
            visibleWhen(bool("stop_when_open", true), () -> mode.get() == Mode.CITY && surround.get());
    private final BooleanValue preferCrystal =
            visibleWhen(bool("prefer_crystal", true), () -> mode.get() == Mode.CITY && surround.get());
    private final BooleanValue ignoreFriends = visibleWhen(bool("ignore_friends", true), () -> mode.get() == Mode.CITY);
    private final BooleanValue autoEnableSpeedMine =
            visibleWhen(bool("auto_enable_speedmine", true), () -> mode.get() == Mode.CITY);

    private final BooleanValue lockView = visibleWhen(bool("lock_view", true), () -> mode.get() == Mode.TUNNEL);
    private final NumberValue<Float> yaw =
            visibleWhen(num("yaw", 0.0f, -180.0f, 180.0f), () -> mode.get() == Mode.TUNNEL && lockView.get());
    private final NumberValue<Float> pitch =
            visibleWhen(num("pitch", 0.0f, -90.0f, 90.0f), () -> mode.get() == Mode.TUNNEL && lockView.get());
    private final BooleanValue pauseWhileUsing =
            visibleWhen(bool("pause_while_using", true), () -> mode.get() == Mode.TUNNEL);

    private final Minecraft mc = Minecraft.getInstance();
    private boolean digging;

    @Override
    public void onDisable() {
        if (digging && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        digging = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || !player.isAlive()) return;

        if (mode.get() == Mode.CITY) {
            tickCity(player, mc.level);
        } else {
            tickTunnel(player);
        }
    }

    private void tickCity(LocalPlayer player, ClientLevel level) {
        SpeedMine speedMine = Modules.get(SpeedMine.class);
        if (speedMine == null) return;
        if (!speedMine.isEnabled()) {
            if (!autoEnableSpeedMine.get()) return;
            speedMine.setEnabled(true);
        }
        // A free primary slot, or a second slot through double mine; otherwise wait.
        if (!speedMine.isIdle() && !speedMine.canDoubleMine()) return;

        Player target = findTarget(player, level);
        if (target == null) return;

        BlockPos next = pickBlock(player, level, speedMine, target.blockPosition());
        if (next != null) speedMine.mine(next, SpeedMine.faceFor(player, next));
    }

    private Player findTarget(LocalPlayer player, ClientLevel level) {
        double maxSq = targetRange.get() * targetRange.get();
        Player best = null;
        double bestSq = maxSq;
        for (AbstractClientPlayer other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (ignoreFriends.get() && CategoryService.isFriend(other)) continue;
            if (AntiBotTracker.INSTANCE.isBot(other)) continue;
            double distSq = player.distanceToSqr(other);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = other;
            }
        }
        return best;
    }

    private BlockPos pickBlock(LocalPlayer player, ClientLevel level, SpeedMine speedMine, BlockPos feet) {
        double reach = speedMine.mineRange();
        // A burrow already being dug does not block the surround: double mine runs both.
        if (burrow.get() && !speedMine.isMining(feet) && isMineable(level, feet) && SpeedMine.inRange(player, feet, reach)) {
            return feet;
        }
        if (!surround.get()) return null;

        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        boolean bestCrystal = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(direction);
            if (speedMine.isMining(side)) {
                // Already on it (or waiting to rebreak it); one surround block per target is enough.
                if (stopWhenOpen.get()) return null;
                continue;
            }
            BlockState state = level.getBlockState(side);
            if (state.canBeReplaced()) {
                if (stopWhenOpen.get()) return null;
                continue;
            }
            if (!isMineable(level, side) || !SpeedMine.inRange(player, side, reach)) continue;
            boolean crystal = preferCrystal.get() && leavesCrystalSpot(level, side);
            double distSq = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(side));
            if ((crystal && !bestCrystal) || (crystal == bestCrystal && distSq < bestSq)) {
                bestSq = distSq;
                best = side;
                bestCrystal = crystal;
            }
        }
        return best;
    }

    /** Once {@code side} is gone, a crystal fits there: blast-proof base below and headroom above. */
    private static boolean leavesCrystalSpot(ClientLevel level, BlockPos side) {
        BlockState base = level.getBlockState(side.below());
        if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) return false;
        return level.getBlockState(side.above()).isAir();
    }

    private static boolean isMineable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) return false;
        if (state.getDestroySpeed(level, pos) < 0.0f) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private void tickTunnel(LocalPlayer player) {
        if (ClientScreen.current() != null || (pauseWhileUsing.get() && player.isUsingItem())) {
            stopDigging();
            return;
        }
        if (lockView.get()) {
            player.setYRot(yaw.get());
            player.setXRot(pitch.get());
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || mc.level.getBlockState(blockHit.getBlockPos()).isAir()) {
            stopDigging();
            return;
        }
        // Same call vanilla makes every tick while attack is held; SpeedMine's mixin picks it up.
        if (mc.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection())) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        digging = true;
    }

    private void stopDigging() {
        if (!digging) return;
        mc.gameMode.stopDestroyBlock();
        digging = false;
    }

    private enum Mode implements EnumValue.IdProvider {
        CITY("city"),
        TUNNEL("tunnel");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
