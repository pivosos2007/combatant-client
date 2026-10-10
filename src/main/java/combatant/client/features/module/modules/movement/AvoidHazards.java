/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 *
 * Hazard categories and the collision-blocking approach adapted from LiquidBounce
 * AvoidHazards, copyright (c) 2015-2025 CCBlueX (GPL-3.0-or-later).
 * https://github.com/CCBlueX/LiquidBounce
 */
package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.BlockCollisionShapeEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.navigation.HazardAvoidance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.LinkedHashMap;
import java.util.Map;


@ModuleInfo(id = "avoidhazards", displayName = "AvoidHazards",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.BASIC,
        description = "module.avoidhazards.description")
public final class AvoidHazards extends Module {
    private static final VoxelShape UNSAFE_CAP = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.25, 1.0);
    private static final int NORMAL_LOOKAHEAD_TICKS = 6;
    private static final int SLIPPERY_LOOKAHEAD_TICKS = 30;
    private static final HazardAvoidance.Kind[] KINDS = HazardAvoidance.Kind.values();
    private static final String[] KIND_IDS = java.util.Arrays.stream(KINDS)
            .map(kind -> kind.name().toLowerCase(java.util.Locale.ROOT))
            .toArray(String[]::new);
    private static final double INPUT_SCALE = 0.98;

    private final Minecraft mc = Minecraft.getInstance();
    // Keep the original behaviour for existing configurations.
    private final EnumValue<Mode> mode = enumMode("mode", Mode.VANILLA);
    private final BooleanMapValue avoid = group("avoidHazardsBlocks", "avoid", defaults());
    private final boolean[] selectedKinds = new boolean[KINDS.length];
    private final CollisionListener collisionListener = new CollisionListener();
    private boolean collisionRegistered;

    public enum Mode { VANILLA, LEGIT }

    private static Map<String, Boolean> defaults() {
        Map<String, Boolean> entries = new LinkedHashMap<>();
        for (HazardAvoidance.Kind kind : HazardAvoidance.Kind.values()) {
            entries.put(kind.name().toLowerCase(java.util.Locale.ROOT), true);
        }
        return entries;
    }

    @Override
    public void onEnable() {
        updateCollisionListener();
    }

    @Override
    public void onDisable() {
        if (collisionRegistered) {
            Events.BUS.unregister(collisionListener);
            collisionRegistered = false;
        }
    }

    @Override
    public void onTick() {
        if (isEnabled()) updateCollisionListener();
    }

    private void updateCollisionListener() {
        boolean wanted = isEnabled() && mode.get() == Mode.VANILLA;
        if (wanted == collisionRegistered) return;
        if (wanted) {
            Events.BUS.registerOwned(this, collisionListener);
        } else {
            Events.BUS.unregister(collisionListener);
        }
        collisionRegistered = wanted;
    }

    private final class CollisionListener {
        @EventHandler
        private void onCollisionShape(BlockCollisionShapeEvent event) {
            if (!isEnabled() || mode.get() != Mode.VANILLA || mc.level == null
                    || mc.player == null || event.getWorld() != mc.level) return;
            if (!(event.getContext() instanceof EntityCollisionContext ctx)
                    || ctx.getEntity() != mc.player) return;

            BlockPos pos = event.getPos();
            HazardAvoidance.Kind kind = HazardAvoidance.classify(mc.level, pos, event.getState());
            // Vanilla already has a full magma-block collision at this position.
            if (kind == HazardAvoidance.Kind.MAGMA) return;
            if (kind == null && event.getState().isAir() && avoid.get("magma")
                    && pos.getY() == net.minecraft.util.Mth.floor(mc.player.getY())
                    && mc.level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
                kind = HazardAvoidance.Kind.MAGMA;
            }
            if (kind == null || !enabled(kind)) return;
            if (HazardAvoidance.contains(mc.level, mc.player.getBoundingBox(), kind)) return;
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            if (px >= pos.getX() && px < pos.getX() + 1
                    && py >= pos.getY() && py < pos.getY() + 1
                    && pz >= pos.getZ() && pz < pos.getZ() + 1) return;
            event.setShape(kind == HazardAvoidance.Kind.MAGMA
                    || kind == HazardAvoidance.Kind.PRESSURE_PLATE ? UNSAFE_CAP : Shapes.block());
        }
    }

    private boolean enabled(HazardAvoidance.Kind kind) {
        return avoid.get(KIND_IDS[kind.ordinal()]);
    }

    /**
     * Runs after the other MovementInputEvent consumers (AutoWalk, TargetStrafe,
     * etc.), so their synthetic input cannot bypass the hazard guard.
     * Never intervene during jumps, falling, flying or unusual fluid physics.
     */
    @EventHandler(priority = -10000)
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || mode.get() != Mode.LEGIT) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.onGround() || event.isJump()
                || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
                || player.isInWater() || player.isInLava() || player.onClimbable()) return;

        int longitudinal = (event.isForward() ? 1 : 0) - (event.isBackward() ? 1 : 0);
        int lateral = (event.isLeft() ? 1 : 0) - (event.isRight() ? 1 : 0);
        if (longitudinal == 0 && lateral == 0) return;

        boolean hasSelected = false;
        for (int i = 0; i < KINDS.length; i++) {
            selectedKinds[i] = avoid.get(KIND_IDS[i]);
            hasSelected |= selectedKinds[i];
        }
        if (!hasSelected) return;

        AABB bounds = player.getBoundingBox();
        int currentExposure = exposure(bounds);
        // Predict with the real block's friction and standard input acceleration.
        // This predicts a *warning*, not an alternative client physics state:
        // the actual movement and all collision resolution stay vanilla.
        float slipperiness = mc.level.getBlockState(BlockPos.containing(
                player.getX(), player.getY() - 0.5000001, player.getZ())).getBlock().getFriction();
        double groundDrag = slipperiness * 0.91;
        // Ice / other slippery ground preserves momentum far longer than
        // ordinary ground. Extend the warning horizon without ever changing
        // the actual client velocity/friction or adding a fake collision.
        int lookahead = groundDrag > 0.75 ? SLIPPERY_LOOKAHEAD_TICKS : NORMAL_LOOKAHEAD_TICKS;
        double accel = player.getSpeed() * (0.21600002 / (slipperiness * slipperiness * slipperiness)) * INPUT_SCALE;
        Vec3 velocity = player.getDeltaMovement();

        int full = projectedExposure(bounds, velocity, player.getYRot(), longitudinal, lateral,
                accel, groundDrag, currentExposure, lookahead);
        if (full <= currentExposure) return;

        int forward = longitudinal == 0 ? Integer.MAX_VALUE : projectedExposure(bounds, velocity,
                player.getYRot(), longitudinal, 0, accel, groundDrag, currentExposure, lookahead);
        int side = lateral == 0 ? Integer.MAX_VALUE : projectedExposure(bounds, velocity,
                player.getYRot(), 0, lateral, accel, groundDrag, currentExposure, lookahead);
        int neutral = projectedExposure(bounds, velocity, player.getYRot(), 0, 0,
                accel, groundDrag, currentExposure, lookahead);

        // Prefer a single safe axis rather than stopping all movement near a
        // corner. If coasting is safest, release directional keys normally.
        if (forward <= currentExposure && forward <= side) {
            event.setLeft(false);
            event.setRight(false);
        } else if (side <= currentExposure) {
            event.setForward(false);
            event.setBackward(false);
        } else if (neutral <= forward && neutral <= side) {
            event.setForward(false);
            event.setBackward(false);
            event.setLeft(false);
            event.setRight(false);
        } else if (forward <= side) {
            event.setLeft(false);
            event.setRight(false);
        } else {
            event.setForward(false);
            event.setBackward(false);
        }
        // Do not modify sprint, jump, velocity or the onGround bit. Vanilla
        // handles deceleration through its own friction model.
    }

    /** Worst exposure over the next few vanilla-style horizontal steps. */
    private int projectedExposure(AABB box, Vec3 v, float yaw, int forward, int left,
                                  double accel, double drag, int initial, int lookahead) {
        double vx = v.x, vz = v.z;
        double x = 0.0, z = 0.0;
        double length = Math.hypot(forward, left);
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians), cos = Math.cos(radians);
        // Vanilla: forward -sin(yaw), +cos(yaw); left -cos(yaw), -sin(yaw).
        double ax = length < 1e-6 ? 0 : (-forward * sin - left * cos) / length * accel;
        double az = length < 1e-6 ? 0 : (forward * cos - left * sin) / length * accel;
        int worst = initial;
        for (int i = 0; i < lookahead; i++) {
            vx += ax;
            vz += az;
            // Movement uses velocity *before* the tick's friction update.
            double nx = x + vx, nz = z + vz;
            int next = exposure(box.move(nx, 0.0, nz));
            if (next > worst) worst = next;
            // Check the swept midpoint so we cannot skip a thin fire/web cell.
            if (Math.abs(nx - x) + Math.abs(nz - z) > 0.24) {
                int middle = exposure(box.move((x + nx) * 0.5, 0.0, (z + nz) * 0.5));
                if (middle > worst) worst = middle;
            }
            if (worst > initial) return worst;
            x = nx;
            z = nz;
            vx *= drag;
            vz *= drag;
        }
        return worst;
    }

    /** Count only user-selected hazards; never use simulated collision shapes here. */
    private int exposure(AABB box) {
        int minX = (int) Math.floor(box.minX + 0.015);
        int maxX = (int) Math.floor(box.maxX - 0.015);
        int minZ = (int) Math.floor(box.minZ + 0.015);
        int maxZ = (int) Math.floor(box.maxZ - 0.015);
        int minY = (int) Math.floor(box.minY - 0.12);
        int maxY = (int) Math.floor(box.maxY - 0.015);
        int score = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!mc.level.hasChunkAt(cursor)) continue;
                    HazardAvoidance.Kind kind = HazardAvoidance.classify(mc.level, cursor,
                            mc.level.getBlockState(cursor));
                    if (kind == null || !selectedKinds[kind.ordinal()]) continue;
                    // Magma/plate below the feet only matters if within contact range.
                    if ((kind == HazardAvoidance.Kind.MAGMA
                            || kind == HazardAvoidance.Kind.PRESSURE_PLATE)
                            && y + 1.0 < box.minY - 0.13) continue;
                    score += switch (kind) {
                        case LAVA -> 100;
                        case FIRE -> 64;
                        case CAMPFIRE -> 40;
                        case CACTUS, MAGMA -> 24;
                        case WITHER_ROSE -> 20;
                        case POWDER_SNOW -> 12;
                        case BERRY_BUSH -> 8;
                        case PRESSURE_PLATE -> 2;
                        case COBWEB -> 1;
                    };
                }
            }
        }
        return score;
    }
}
