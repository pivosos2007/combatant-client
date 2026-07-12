/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

//todo Description
@ModuleInfo(
        id = "reversestep",
        displayName = "ReverseStep",
        category = ModuleCategory.MOVEMENT
)
public final class ReverseStep extends Module {

    private static final Set<Block> UNWANTED_BLOCKS = Set.of(
            Blocks.WATER,
            Blocks.COBWEB,
            Blocks.POWDER_SNOW,
            Blocks.HAY_BLOCK,
            Blocks.SLIME_BLOCK
    );
    private static final double[] VULCAN_297_STEP_C_MOTION = {
            -0.5,
            -0.07850000262260437,
            -0.15530000627040863,
            -0.2305999994277954,
            -0.3043999969959259,
            -0.376800000667572,
            -0.44749999046325684,
            -0.5170999765396118,
            -0.5860000252723694,
            -0.6520000100135803,
            -0.7171000242233276,
            -0.7811999917030334,
            -0.843999981880188,
            -0.9054999947547913,
            -0.9657999873161316,
            -1.024899959564209,
            -1.082800030708313,
            -1.1395000219345093,
            -1.195099949836731,
            -1.2496000528335571,
            -1.3029999732971191,
            -1.3559999465942383,
            -1.406999945640564,
            -1.4570000171661377,
            -1.5069999694824219,
            -1.5549999475479126
    };
    private static final double VULCAN_297_GROUND_SEARCH_DISTANCE = 50.0;
    private static final double VULCAN_297_MIN_FALLING_MOTION = -0.01;
    private static final double VULCAN_297_EXTERNAL_UPWARD_MOTION = 0.02;
    private static final double VULCAN_297_SHORT_DROP_DISTANCE = 1.25;
    private static final double VULCAN_297_SHORT_DROP_MOTION = -0.49;
    private static final double VULCAN_297_FLIGHT_F_DECAY = 0.04;
    private static final double VULCAN_297_FLIGHT_F_SHORT_DROP_COST = 1.0;
    private static final double VULCAN_297_FLIGHT_F_AGGRESSIVE_LIMIT = 1.6;
    private static final String STEP_C_EXEMPT_CREATIVE = "creative";
    private static final String STEP_C_EXEMPT_FLIGHT = "flight";
    private static final String STEP_C_EXEMPT_GLIDING = "gliding";
    private static final String STEP_C_EXEMPT_EXPLOSION = "explosion";
    private static final String STEP_C_EXEMPT_LIQUID = "liquid";
    private static final String STEP_C_EXEMPT_VEHICLE = "vehicle";
    private static final String STEP_C_EXEMPT_RIPTIDE = "riptide";
    private static final String STEP_C_EXEMPT_SLEEPING = "sleeping";
    private static final String STEP_C_EXEMPT_VELOCITY = "velocity";
    private static final String STEP_C_EXEMPT_SLIME = "slime";
    private static final String STEP_C_EXEMPT_ENDER_PEARL = "ender_pearl";
    private static final String STEP_C_EXEMPT_ANVIL = "anvil";
    private static final String STEP_C_EXEMPT_END_ROD = "end_rod";
    private static final String STEP_C_EXEMPT_CHAIN = "chain";
    private static final String STEP_C_EXEMPT_DRIPSTONE = "dripstone";
    private static final String STEP_C_EXEMPT_JUMP_BOOST = "jump_boost";
    private static final String STEP_C_EXEMPT_CLIMBABLE = "climbable";
    private static final String STEP_C_EXEMPT_SCAFFOLDING = "scaffolding";
    private static final String STEP_C_EXEMPT_POWDER_SNOW = "powder_snow";
    private static final String STEP_C_EXEMPT_SNOW = "snow";
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumSetting("reverseStepMode", "mode", Mode.INSTANT, Mode.values());
    private final NumberValue<Float> maximumFallDistance =
            num("reverseStepMaximumFallDistance", "maximum_fall_distance", 1.0f, 1.0f, 50.0f);
    private final NumberValue<Integer> instantTicks =
            visibleWhen(num("reverseStepInstantTicks", "ticks", 20, 1, 40), () -> mode.get() == Mode.INSTANT);
    private final BooleanValue simulateFalling =
            visibleWhen(bool("reverseStepSimulateFalling", "simulate_falling", false), () -> mode.get() == Mode.INSTANT);
    private final NumberValue<Float> acceleratorFactor =
            visibleWhen(num("reverseStepAcceleratorFactor", "factor", 1.0f, 0.1f, 5.0f), () -> mode.get() == Mode.ACCELERATOR);
    private final NumberValue<Float> strictMotion =
            visibleWhen(num("reverseStepStrictMotion", "motion", 1.0f, 0.1f, 5.0f), () -> mode.get() == Mode.STRICT);
    private final NumberValue<Float> vulcan297Motion =
            visibleWhen(num("reverseStepVulcan297Motion", "vulcan297_motion", 0.65f, 0.08f, 1.55f), () -> mode.get() == Mode.VULCAN_297);
    private final NumberValue<Float> vulcan297ExemptMotion =
            visibleWhen(num("reverseStepVulcan297ExemptMotion", "vulcan297_exempt_motion", 1.0f, 0.08f, 2.0f), () -> mode.get() == Mode.VULCAN_297);
    private final NumberValue<Float> vulcan297StepCMargin =
            visibleWhen(num("reverseStepVulcan297StepCMargin", "vulcan297_stepc_margin", 0.004f, 0.0f, 0.05f), () -> mode.get() == Mode.VULCAN_297);
    private final BooleanMapValue vulcan297StepCExempts =
            visibleWhen(group("reverseStepVulcan297StepCExempts", "vulcan297_stepc_exempts", vulcan297StepCExemptDefaults()), () -> mode.get() == Mode.VULCAN_297);
    private boolean initiatedJump;
    private int vulcan297FallTicks;
    private boolean vulcan297Jumped;
    private boolean vulcan297GroundSeen;
    private boolean vulcan297ExternalAirState;
    private int vulcan297AirTicks;
    private double vulcan297FlightFRisk;

    private static Map<String, Boolean> vulcan297StepCExemptDefaults() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(STEP_C_EXEMPT_CREATIVE, true);
        defaults.put(STEP_C_EXEMPT_FLIGHT, true);
        defaults.put(STEP_C_EXEMPT_GLIDING, true);
        defaults.put(STEP_C_EXEMPT_EXPLOSION, true);
        defaults.put(STEP_C_EXEMPT_LIQUID, true);
        defaults.put(STEP_C_EXEMPT_VEHICLE, true);
        defaults.put(STEP_C_EXEMPT_RIPTIDE, true);
        defaults.put(STEP_C_EXEMPT_SLEEPING, true);
        defaults.put(STEP_C_EXEMPT_VELOCITY, true);
        defaults.put(STEP_C_EXEMPT_SLIME, true);
        defaults.put(STEP_C_EXEMPT_ENDER_PEARL, true);
        defaults.put(STEP_C_EXEMPT_ANVIL, true);
        defaults.put(STEP_C_EXEMPT_END_ROD, true);
        defaults.put(STEP_C_EXEMPT_CHAIN, true);
        defaults.put(STEP_C_EXEMPT_DRIPSTONE, true);
        defaults.put(STEP_C_EXEMPT_JUMP_BOOST, true);
        defaults.put(STEP_C_EXEMPT_CLIMBABLE, true);
        defaults.put(STEP_C_EXEMPT_SCAFFOLDING, true);
        defaults.put(STEP_C_EXEMPT_POWDER_SNOW, true);
        defaults.put(STEP_C_EXEMPT_SNOW, true);
        return defaults;
    }

    @Override
    public void onDisable() {
        initiatedJump = false;
        vulcan297FallTicks = 0;
        vulcan297Jumped = false;
        vulcan297GroundSeen = false;
        vulcan297ExternalAirState = false;
        vulcan297AirTicks = 0;
        vulcan297FlightFRisk = 0.0;
    }

    @EventHandler
    private void onPlayerJump(PlayerJumpEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (mode.get() == Mode.VULCAN_297) {
            vulcan297Jumped = true;
            vulcan297FallTicks = 0;
        } else {
            initiatedJump = true;
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        LocalPlayer player = mc.player;
        decayVulcan297Risk();
        if (player.onGround()) {
            initiatedJump = false;
            vulcan297FallTicks = 0;
            vulcan297Jumped = false;
            vulcan297GroundSeen = mode.get() == Mode.VULCAN_297;
            vulcan297ExternalAirState = false;
            vulcan297AirTicks = 0;
            return;
        }

        if (mode.get() == Mode.VULCAN_297) {
            if (vulcan297AirTicks == 0) {
                vulcan297ExternalAirState = !vulcan297GroundSeen
                        || vulcan297Jumped
                        || player.hurtTime > 0
                        || player.getDeltaMovement().y > VULCAN_297_EXTERNAL_UPWARD_MOTION;
            } else if (player.hurtTime > 0 || player.getDeltaMovement().y > VULCAN_297_EXTERNAL_UPWARD_MOTION) {
                vulcan297ExternalAirState = true;
            }

            vulcan297AirTicks++;

            if (vulcan297ExternalAirState) {
                vulcan297FallTicks = 0;
                return;
            }
        } else {
            vulcan297GroundSeen = false;
            vulcan297ExternalAirState = false;
            vulcan297AirTicks = 0;

            if (player.getDeltaMovement().y > 0.08) {
                initiatedJump = true;
            }

            if (initiatedJump) {
                return;
            }
            if (isFallingTooFar(player) || unwantedBlocksBelow(player)) {
                return;
            }
        }

        switch (mode.get()) {
            case INSTANT -> handleInstant(player);
            case STRICT -> {
                Vec3 velocity = player.getDeltaMovement();
                player.setDeltaMovement(velocity.x, -strictMotion.get(), velocity.z);
            }
            case ACCELERATOR -> {
                Vec3 velocity = player.getDeltaMovement();
                if (velocity.y < 0.0) {
                    player.setDeltaMovement(velocity.x, velocity.y * acceleratorFactor.get(), velocity.z);
                }
            }
            case VULCAN_297 -> handleVulcan297(player);
        }
    }

    private void handleInstant(LocalPlayer player) {
        BlockHitResult ground = raycastGround(player, maximumFallDistance.get());
        if (ground == null || ground.getType() != HitResult.Type.BLOCK) return;

        double targetY = ground.getLocation().y;
        if (targetY >= player.getY()) return;

        if (simulateFalling.get() && mc.getConnection() != null) {
            double startY = player.getY();
            int ticks = Math.max(1, instantTicks.get());
            for (int i = 1; i <= ticks; i++) {
                double progress = i / (double) ticks;
                double y = startY + (targetY - startY) * progress;
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                        player.getX(),
                        y,
                        player.getZ(),
                        i == ticks,
                        player.horizontalCollision
                ));
            }
        }

        player.setPos(player.getX(), targetY, player.getZ());
    }

    private void handleVulcan297(LocalPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y >= VULCAN_297_MIN_FALLING_MOTION) {
            vulcan297FallTicks = 0;
            return;
        }

        double searchDistance = Math.min(maximumFallDistance.get(), VULCAN_297_GROUND_SEARCH_DISTANCE);
        BlockHitResult ground = raycastGround(player, searchDistance);
        if (ground == null || ground.getType() != HitResult.Type.BLOCK) return;

        double targetY = ground.getLocation().y;
        double distanceToGround = player.getY() - targetY;
        if (distanceToGround <= 0.0 || distanceToGround > searchDistance) return;

        vulcan297FallTicks = Math.min(vulcan297FallTicks + 1, VULCAN_297_STEP_C_MOTION.length - 1);

        boolean exempt = isVulcan297StepCExempt(player, ground.getBlockPos());
        double totalDropDistance = player.fallDistance + distanceToGround;
        boolean shortDrop = totalDropDistance <= VULCAN_297_SHORT_DROP_DISTANCE;
        double targetMotion;

        if (exempt) {
            targetMotion = -vulcan297ExemptMotion.get();
        } else if (shortDrop && vulcan297FallTicks == 1 && canUseVulcan297AggressiveShortDrop()) {
            targetMotion = Math.max(-vulcan297Motion.get(), VULCAN_297_SHORT_DROP_MOTION);
            vulcan297FlightFRisk += VULCAN_297_FLIGHT_F_SHORT_DROP_COST;
        } else {
            targetMotion = Math.max(-vulcan297Motion.get(), vulcan297SafeStepCMotion());
        }

        if (velocity.y > targetMotion) {
            player.setDeltaMovement(velocity.x, targetMotion, velocity.z);
        }
    }

    private double vulcan297SafeStepCMotion() {
        int index = Math.max(1, Math.min(vulcan297FallTicks, VULCAN_297_STEP_C_MOTION.length - 1));
        return VULCAN_297_STEP_C_MOTION[index] + vulcan297StepCMargin.get();
    }

    private boolean canUseVulcan297AggressiveShortDrop() {
        return vulcan297FlightFRisk < VULCAN_297_FLIGHT_F_AGGRESSIVE_LIMIT;
    }

    private void decayVulcan297Risk() {
        if (vulcan297FlightFRisk > 0.0) {
            vulcan297FlightFRisk = Math.max(0.0, vulcan297FlightFRisk - VULCAN_297_FLIGHT_F_DECAY);
        }
    }

    private boolean isVulcan297StepCExempt(LocalPlayer player, BlockPos groundPos) {
        if (player == null || mc.level == null) return false;

        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_CREATIVE) && player.getAbilities().instabuild) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_FLIGHT) && player.getAbilities().flying) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_GLIDING) && player.isFallFlying()) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_EXPLOSION) && player.hurtTime > 0) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_LIQUID) && (player.isInWater() || player.isInLava())) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_VEHICLE) && player.isPassenger()) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_RIPTIDE) && player.isAutoSpinAttack()) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_SLEEPING) && player.isSleeping()) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_VELOCITY) && player.hurtTime > 0) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_ENDER_PEARL) && player.tickCount < 100) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_JUMP_BOOST) && player.hasEffect(MobEffects.JUMP_BOOST)) return true;
        if (vulcan297StepCExempts.get(STEP_C_EXEMPT_CLIMBABLE) && player.onClimbable()) return true;

        BlockPos feet = player.blockPosition();
        return isVulcan297StepCExemptBlock(feet)
                || isVulcan297StepCExemptBlock(feet.below())
                || isVulcan297StepCExemptBlock(groundPos);
    }

    private boolean isVulcan297StepCExemptBlock(BlockPos pos) {
        if (mc.level == null) return false;

        Block block = mc.level.getBlockState(pos).getBlock();
        return vulcan297StepCExempts.get(STEP_C_EXEMPT_SLIME) && block == Blocks.SLIME_BLOCK
                || vulcan297StepCExempts.get(STEP_C_EXEMPT_ANVIL) && block == Blocks.ANVIL
                || vulcan297StepCExempts.get(STEP_C_EXEMPT_END_ROD) && block == Blocks.END_ROD
                || vulcan297StepCExempts.get(STEP_C_EXEMPT_CHAIN) && block == Blocks.IRON_CHAIN
                || vulcan297StepCExempts.get(STEP_C_EXEMPT_DRIPSTONE) && block == Blocks.POINTED_DRIPSTONE
                || vulcan297StepCExempts.get(STEP_C_EXEMPT_SCAFFOLDING) && block == Blocks.SCAFFOLDING
                || vulcan297StepCExempts.get(STEP_C_EXEMPT_POWDER_SNOW) && block == Blocks.POWDER_SNOW
                || vulcan297StepCExempts.get(STEP_C_EXEMPT_SNOW) && block == Blocks.SNOW;
    }

    private boolean isFallingTooFar(LocalPlayer player) {
        if (player.fallDistance > maximumFallDistance.get()) {
            return true;
        }

        AABB box = player.getBoundingBox().move(0.0, -maximumFallDistance.get(), 0.0);
        return !mc.level.getBlockCollisions(player, box).iterator().hasNext();
    }

    private boolean unwantedBlocksBelow(LocalPlayer player) {
        BlockHitResult hit = raycastGround(player, 20.0);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        return UNWANTED_BLOCKS.contains(block);
    }

    private BlockHitResult raycastGround(LocalPlayer player, double distance) {
        HitResult result = mc.level.clip(new ClipContext(
                player.position(),
                player.position().add(0.0, -distance, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY,
                player
        ));

        return result instanceof BlockHitResult blockHit ? blockHit : null;
    }

    public enum Mode {
        INSTANT,
        STRICT,
        ACCELERATOR,
        VULCAN_297
    }
}
