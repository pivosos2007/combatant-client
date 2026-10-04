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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PlayerStepEvent;
import combatant.client.events.impl.PlayerStepSuccessEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.time.TimerController;

import java.util.LinkedHashMap;
import java.util.Map;

//todo 297 vulcan step remove goofy ass shit
@ModuleInfo(
        id = "step",
        displayName = "Step",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.step.description")
public final class Step extends Module {

    private static final double[] JUMP_ORDER = {
            0.0,
            0.41999998688698,
            0.7531999805212,
            1.00133597911215,
            1.166109260938214,
            1.24918707874468,
            1.25220334025373,
            1.17675927506424,
            1.024424088213685
    };
    private static final String STEP_FLIGHT_EXEMPT_LIQUID = "liquid";
    private static final String STEP_FLIGHT_EXEMPT_WALL = "wall";
    private static final String STEP_FLIGHT_EXEMPT_CONDUIT = "conduit";
    private static final String STEP_FLIGHT_EXEMPT_FARMLAND = "farmland";
    private static final String STEP_FLIGHT_EXEMPT_SLOW_FALLING = "slow_falling";
    private static final String STEP_FLIGHT_EXEMPT_LEVITATION = "levitation";
    private static final String STEP_FLIGHT_EXEMPT_RIPTIDE = "riptide";
    private static final String STEP_FLIGHT_EXEMPT_WEB = "web";
    private static final String STEP_FLIGHT_EXEMPT_WATERLOGGED = "waterlogged";
    private static final String STEP_FLIGHT_EXEMPT_SHULKER_BOX = "shulker_box";
    private static final String STEP_FLIGHT_EXEMPT_SPECTATOR = "spectator";
    private static final String STEP_FLIGHT_EXEMPT_SLEEPING = "sleeping";
    private static final String STEP_FLIGHT_EXEMPT_SLIME = "slime";
    private static final String STEP_FLIGHT_EXEMPT_POWDER_SNOW = "powder_snow";
    private static final String STEP_FLIGHT_EXEMPT_SEAGRASS = "seagrass";
    private static final String STEP_FLIGHT_EXEMPT_SCAFFOLDING = "scaffolding";
    private static final String STEP_FLIGHT_EXEMPT_CLIMBABLE = "climbable";
    private static final String STEP_FLIGHT_EXEMPT_SWIMMING = "swimming";
    private static final String STEP_FLIGHT_EXEMPT_HONEY = "honey";
    private static final String STEP_FLIGHT_EXEMPT_SEA_PICKLE = "sea_pickle";
    private static final String STEP_FLIGHT_EXEMPT_BUBBLE_COLUMN = "bubble_column";
    private static final String STEP_FLIGHT_EXEMPT_VEHICLE = "vehicle";
    private static final String STEP_FLIGHT_EXEMPT_KELP = "kelp";
    private static final String STEP_FLIGHT_EXEMPT_GLIDING = "gliding";
    private static final String STEP_FLIGHT_EXEMPT_CHUNK = "chunk";
    private static final double VULCAN_297_VERTICAL_RECOVERY_COST = 0.55;
    private static final double VULCAN_297_STEP_JUMP_COST = 0.65;
    private static final double VULCAN_297_FLIGHT_F_DECAY = 0.05;
    private static final double VULCAN_297_JUMP_A_DECAY = 0.05;
    private static final double VULCAN_297_FLIGHT_F_LIMIT = 1.35;
    private static final double VULCAN_297_JUMP_A_LIMIT = 1.25;

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumSetting("stepMode", "mode", Mode.INSTANT, Mode.values());
    private final NumberValue<Float> height = visibleWhen(num("stepHeight", "height", 1.0f, 0.6f, 5.0f), () -> mode.get() == Mode.INSTANT);
    private final BooleanValue adaptiveOffsets = visibleWhen(bool("stepAdaptiveOffsets", "adaptive_offsets", true), () -> mode.get() == Mode.INSTANT);
    private final BooleanValue trim = visibleWhen(bool("stepTrim", "trim", false), () -> mode.get() == Mode.INSTANT);
    private final BooleanValue sneak = visibleWhen(bool("stepSneak", "sneak", false), () -> mode.get() == Mode.INSTANT);
    private final NumberValue<Integer> simulateJumpOrderStart =
            visibleWhen(num("stepSimulateJumpOrderStart", "simulate_jump_order_start", 0, 0, JUMP_ORDER.length - 1),
                    () -> mode.get() == Mode.INSTANT && !adaptiveOffsets.get());
    private final NumberValue<Integer> simulateJumpOrderEnd =
            visibleWhen(num("stepSimulateJumpOrderEnd", "simulate_jump_order_end", 2, 0, JUMP_ORDER.length - 1),
                    () -> mode.get() == Mode.INSTANT && !adaptiveOffsets.get());
    private final NumberValue<Integer> waitTicks = visibleWhen(num("stepWaitTicks", "wait_ticks", 0, 0, 60), () -> mode.get() == Mode.INSTANT);
    private final EnumValue<PacketMode> packetMode =
            visibleWhen(enumSetting("stepPacketMode", "packet_mode", PacketMode.FULL, PacketMode.values()), () -> mode.get() == Mode.INSTANT);
    private final BooleanValue useTimer = visibleWhen(bool("stepUseTimer", "use_timer", false), () -> mode.get() == Mode.INSTANT);
    private final NumberValue<Float> timerSpeed =
            visibleWhen(num("stepTimerSpeed", "timer_speed", 0.6f, 0.1f, 1.0f), () -> mode.get() == Mode.INSTANT && useTimer.get());
    private final NumberValue<Integer> timerTicks =
            visibleWhen(num("stepTimerTicks", "timer_ticks", 2, 1, 10), () -> mode.get() == Mode.INSTANT && useTimer.get());
    private final BooleanMapValue vulcan297FlightExempts =
            visibleWhen(group("stepVulcan297FlightExempts", "vulcan297_flight_exempts", vulcan297FlightExemptDefaults()), () -> mode.get() == Mode.VULCAN_297);
    private int ticksWait;
    private int vulcanStepCounter;
    private int vulcanSequenceTicks;
    private boolean vulcanStepping;
    private boolean vulcanEvenStep;
    private boolean vulcan297Aggressive;
    private double vulcan297FlightFRisk;
    private double vulcan297JumpARisk;

    private static Map<String, Boolean> vulcan297FlightExemptDefaults() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(STEP_FLIGHT_EXEMPT_LIQUID, true);
        defaults.put(STEP_FLIGHT_EXEMPT_WALL, true);
        defaults.put(STEP_FLIGHT_EXEMPT_CONDUIT, true);
        defaults.put(STEP_FLIGHT_EXEMPT_FARMLAND, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SLOW_FALLING, true);
        defaults.put(STEP_FLIGHT_EXEMPT_LEVITATION, true);
        defaults.put(STEP_FLIGHT_EXEMPT_RIPTIDE, true);
        defaults.put(STEP_FLIGHT_EXEMPT_WEB, true);
        defaults.put(STEP_FLIGHT_EXEMPT_WATERLOGGED, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SHULKER_BOX, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SPECTATOR, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SLEEPING, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SLIME, true);
        defaults.put(STEP_FLIGHT_EXEMPT_POWDER_SNOW, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SEAGRASS, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SCAFFOLDING, true);
        defaults.put(STEP_FLIGHT_EXEMPT_CLIMBABLE, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SWIMMING, true);
        defaults.put(STEP_FLIGHT_EXEMPT_HONEY, true);
        defaults.put(STEP_FLIGHT_EXEMPT_SEA_PICKLE, true);
        defaults.put(STEP_FLIGHT_EXEMPT_BUBBLE_COLUMN, true);
        defaults.put(STEP_FLIGHT_EXEMPT_VEHICLE, true);
        defaults.put(STEP_FLIGHT_EXEMPT_KELP, true);
        defaults.put(STEP_FLIGHT_EXEMPT_GLIDING, true);
        defaults.put(STEP_FLIGHT_EXEMPT_CHUNK, true);
        return defaults;
    }

    @Override
    public void onDisable() {
        ticksWait = 0;
        resetVulcan();
        vulcan297FlightFRisk = 0.0;
        vulcan297JumpARisk = 0.0;
        TimerController.clear(this);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) return;

        if (ticksWait > 0) {
            ticksWait--;
        }
        decayVulcan297Risk();

        if (mode.get() == Mode.VULCAN_286) {
            tickVulcan286();
        } else if (mode.get() == Mode.VULCAN_297) {
            tickVulcan297();
        }
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        LocalPlayer player = mc.player;
        boolean manualJump = isManualJump(event, player);
        if (mode.get() == Mode.LEGIT) {
            if (canStep(player, 1.0) && !manualJump) {
                event.setJump(true);
            }
            return;
        }

        if ((mode.get() != Mode.VULCAN_286 && mode.get() != Mode.VULCAN_297) || vulcanStepping) return;
        if (mode.get() == Mode.VULCAN_297 && manualJump) return;
        if (mode.get() == Mode.VULCAN_297 && isVulcan297PredictionRiskHigh()) return;
        if (!canStep(player, 1.0)) return;

        event.setJump(true);
        if (mode.get() == Mode.VULCAN_297) {
            vulcan297JumpARisk += VULCAN_297_STEP_JUMP_COST;
            vulcan297FlightFRisk += VULCAN_297_STEP_JUMP_COST * 0.5;
        }
        vulcanStepCounter++;
        vulcan297Aggressive = mode.get() == Mode.VULCAN_297 && isVulcan297FlightExempt(player);
        vulcanEvenStep = vulcanStepCounter % 2 == 0;
        vulcanSequenceTicks = 0;
        vulcanStepping = true;
    }

    @EventHandler
    private void onPlayerStep(PlayerStepEvent event) {
        if (!isEnabled() || mode.get() != Mode.INSTANT || ticksWait > 0) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!player.onGround()) return;
        if (player.isInWater() || player.isInLava() || player.isPassenger() || player.isFallFlying() || player.onClimbable()) return;
        if (!sneak.get() && player.isShiftKeyDown()) return;

        event.setHeight(height.get());
    }

    @EventHandler
    private void onPlayerStepSuccess(PlayerStepSuccessEvent event) {
        if (!isEnabled() || mode.get() != Mode.INSTANT) return;
        if (ticksWait > 0 || mc.player == null || mc.getConnection() == null) return;

        LocalPlayer player = mc.player;
        double stepHeight = event.getAdjustedVec().y;
        if (stepHeight <= 0.5) return;

        if (packetMode.get() != PacketMode.NONE) {
            double[] offsets = getOffsets(stepHeight);
            if (offsets.length > 0) {
                player.awardStat(Stats.JUMP);

                Vec3 before = event.getBeforePos() != null && event.getBeforePos() != Vec3.ZERO
                        ? event.getBeforePos() : player.position();
                Vec3 after = event.getAfterPos() != null && event.getAfterPos() != Vec3.ZERO
                        ? event.getAfterPos() : player.position();
                double stepX = before.x;
                double stepZ = before.z;
                double baseY = before.y;
                double maxY = after.y;

                for (double offset : offsets) {
                    if (offset <= 0.0) continue;

                    double y = baseY + offset;
                    if (trim.get()) {
                        y = Math.min(y, maxY);
                    }
                    Packet<?> packet = movePacket(player, stepX, y, stepZ);
                    if (packet != null) {
                        mc.getConnection().send(packet);
                    }
                }
            }
        }

        if (useTimer.get()) {
            TimerController.requestTimerSpeed(timerSpeed.get(), TimerController.NORMAL, this, timerTicks.get());
        }

        ticksWait = waitTicks.get();
    }

    private double[] getOffsets(double stepHeight) {
        if (adaptiveOffsets.get()) {
            if (stepHeight <= 1.0) {
                return new double[] { 0.41999998688698, 0.7531999805212 };
            } else if (stepHeight <= 1.5) {
                return new double[] { 0.41999998688698, 0.7531999805212, 1.00133597911215, 1.166109260938214, 1.24918707874468, 1.20 };
            } else if (stepHeight <= 2.0) {
                return new double[] { 0.42, 0.78, 1.10, 1.35, 1.50, 1.55 };
            } else if (stepHeight <= 2.5) {
                return new double[] { 0.425, 0.821, 1.199, 1.559, 1.899, 2.219, 2.519 };
            } else {
                int count = (int) Math.ceil(stepHeight / 0.4);
                double[] offsets = new double[Math.max(1, count - 1)];
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = (i + 1) * (stepHeight / count);
                }
                return offsets;
            }
        } else {
            int start = Math.min(simulateJumpOrderStart.get(), simulateJumpOrderEnd.get());
            int end = Math.max(simulateJumpOrderStart.get(), simulateJumpOrderEnd.get());
            if (start == 0 && end == 0) {
                return new double[0];
            }
            int length = Math.max(0, end - start + 1);
            double[] offsets = new double[length];
            int idx = 0;
            for (int i = start; i <= end && i < JUMP_ORDER.length; i++) {
                offsets[idx++] = JUMP_ORDER[i];
            }
            return offsets;
        }
    }

    private void tickVulcan286() {
        if (!vulcanStepping || mc.player == null) return;

        vulcanSequenceTicks++;
        LocalPlayer player = mc.player;
        tickVulcan286Sequence(player);
    }

    private void tickVulcan286Sequence(LocalPlayer player) {

        if (vulcanSequenceTicks == 2) {
            if (vulcanEvenStep) {
                Vec3 velocity = player.getDeltaMovement();
                player.setDeltaMovement(withStrafe(new Vec3(velocity.x, 0.24680001947880004, velocity.z), player, 0.2));
                vulcan297FlightFRisk += VULCAN_297_VERTICAL_RECOVERY_COST;
            }
            return;
        }

        if (vulcanSequenceTicks == 3) {
            if (vulcanEvenStep) {
                Vec3 velocity = player.getDeltaMovement();
                player.setDeltaMovement(velocity.x, 0.0, velocity.z);
                vulcan297FlightFRisk += VULCAN_297_VERTICAL_RECOVERY_COST * 0.5;
            }
            return;
        }

        if (vulcanSequenceTicks >= 4) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, -0.17, velocity.z);
            resetVulcan();
        }
    }

    private void tickVulcan297() {
        if (!vulcanStepping || mc.player == null) return;

        vulcanSequenceTicks++;
        LocalPlayer player = mc.player;

        if (vulcan297Aggressive) {
            tickVulcan297Aggressive(player);
            return;
        }

        tickVulcan297Standard(player);
    }

    private void tickVulcan297Standard(LocalPlayer player) {
        if (vulcanSequenceTicks == 2) {
            if (vulcanEvenStep) {
                Vec3 velocity = player.getDeltaMovement();
                double forwardMotion = MovementUtil.isMoving() ? 0.22 : 0.0;
                player.setDeltaMovement(withStrafe(new Vec3(velocity.x, 0.24680001947880004, velocity.z), player, forwardMotion));
                vulcan297FlightFRisk += VULCAN_297_VERTICAL_RECOVERY_COST;
            }
            return;
        }

        if (vulcanSequenceTicks == 3) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, 0.0, velocity.z);
            vulcan297FlightFRisk += VULCAN_297_VERTICAL_RECOVERY_COST * 0.5;
            return;
        }

        if (vulcanSequenceTicks >= 4) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, -0.17, velocity.z);
            resetVulcan();
        }
    }

    private void tickVulcan297Aggressive(LocalPlayer player) {
        if (vulcanSequenceTicks == 1) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(withStrafe(new Vec3(velocity.x, 0.42, velocity.z), player, 0.28));
            return;
        }

        if (vulcanSequenceTicks == 2) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(withStrafe(new Vec3(velocity.x, 0.2, velocity.z), player, 0.24));
            return;
        }

        if (vulcanSequenceTicks >= 3) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, 0.0, velocity.z);
            resetVulcan();
        }
    }

    private Packet<?> movePacket(LocalPlayer player, double x, double y, double z) {
        return switch (packetMode.get()) {
            case FULL -> new ServerboundMovePlayerPacket.PosRot(
                    x,
                    y,
                    z,
                    player.getYRot(),
                    player.getXRot(),
                    false,
                    false
            );
            case POSITION_AND_ON_GROUND -> new ServerboundMovePlayerPacket.Pos(
                    x,
                    y,
                    z,
                    false,
                    false
            );
            case NONE -> null;
        };
    }

    private boolean canStep(LocalPlayer player, double stepHeight) {
        if (player == null || mc.level == null) return false;
        if (!player.onGround() || player.isShiftKeyDown() || player.isSpectator()) return false;
        if (player.isInWater() || player.isInLava() || player.onClimbable() || player.isPassenger()) return false;
        if (!MovementUtil.isMoving()) return false;

        Vec3 offset = horizontalInputOffset(player, 0.36);
        if (offset.horizontalDistanceSqr() < 1.0E-7) return false;

        AABB box = player.getBoundingBox();
        AABB forwardBox = box.move(offset.x, 0.0, offset.z);
        if (mc.level.noCollision(player, forwardBox)) return false;

        AABB steppedBox = box.move(offset.x, stepHeight, offset.z);
        return mc.level.noCollision(player, steppedBox);
    }

    private boolean isManualJump(MovementInputEvent event, LocalPlayer player) {
        return event.isJump()
                || mc.options != null && mc.options.keyJump.isDown()
                || player.input != null && player.input.keyPresses.jump();
    }

    private boolean isVulcan297FlightExempt(LocalPlayer player) {
        if (player == null || mc.level == null) return false;

        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_LIQUID) && (player.isInWater() || player.isInLava()))
            return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_WALL) && player.horizontalCollision) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_CONDUIT) && player.hasEffect(MobEffects.CONDUIT_POWER))
            return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SLOW_FALLING) && player.hasEffect(MobEffects.SLOW_FALLING))
            return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_LEVITATION) && player.hasEffect(MobEffects.LEVITATION))
            return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_RIPTIDE) && player.isAutoSpinAttack()) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SPECTATOR) && player.isSpectator()) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SLEEPING) && player.isSleeping()) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_CLIMBABLE) && player.onClimbable()) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SWIMMING) && player.isSwimming()) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_VEHICLE) && player.isPassenger()) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_GLIDING) && player.isFallFlying()) return true;
        if (vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_CHUNK) && player.tickCount < 100) return true;

        BlockPos feet = player.blockPosition();
        return isVulcan297FlightExemptBlock(feet)
                || isVulcan297FlightExemptBlock(feet.below())
                || isVulcan297FlightExemptBlock(feet.above());
    }

    private boolean isVulcan297FlightExemptBlock(BlockPos pos) {
        if (mc.level == null) return false;

        Block block = mc.level.getBlockState(pos).getBlock();
        return vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_FARMLAND) && block == Blocks.FARMLAND
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_WEB) && block == Blocks.COBWEB
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_WATERLOGGED) && mc.level.getBlockState(pos).getFluidState().isSource()
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SHULKER_BOX) && block == Blocks.SHULKER_BOX
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SLIME) && block == Blocks.SLIME_BLOCK
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_POWDER_SNOW) && block == Blocks.POWDER_SNOW
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SEAGRASS) && (block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS)
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SCAFFOLDING) && block == Blocks.SCAFFOLDING
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_HONEY) && block == Blocks.HONEY_BLOCK
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_SEA_PICKLE) && block == Blocks.SEA_PICKLE
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_BUBBLE_COLUMN) && block == Blocks.BUBBLE_COLUMN
                || vulcan297FlightExempts.get(STEP_FLIGHT_EXEMPT_KELP) && (block == Blocks.KELP || block == Blocks.KELP_PLANT);
    }

    private boolean isVulcan297StepCExempt(LocalPlayer player) {
        if (player == null || mc.level == null) return false;

        if (player.getAbilities().instabuild || player.getAbilities().flying) return true;
        if (player.isFallFlying() || player.isInWater() || player.isInLava()) return true;
        if (player.isPassenger() || player.isAutoSpinAttack() || player.isSleeping()) return true;
        if (player.hurtTime > 0 || player.hasEffect(MobEffects.JUMP_BOOST) || player.onClimbable()) return true;

        BlockPos feet = player.blockPosition();
        return isVulcan297StepCExemptBlock(feet)
                || isVulcan297StepCExemptBlock(feet.below())
                || isVulcan297StepCExemptBlock(feet.above());
    }

    private boolean isVulcan297StepCExemptBlock(BlockPos pos) {
        if (mc.level == null) return false;

        Block block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.SLIME_BLOCK
                || block == Blocks.ANVIL
                || block == Blocks.END_ROD
                || block == Blocks.IRON_CHAIN
                || block == Blocks.POINTED_DRIPSTONE
                || block == Blocks.SCAFFOLDING
                || block == Blocks.POWDER_SNOW
                || block == Blocks.SNOW;
    }

    private boolean isVulcan297PredictionRiskHigh() {
        return vulcan297FlightFRisk >= VULCAN_297_FLIGHT_F_LIMIT
                || vulcan297JumpARisk >= VULCAN_297_JUMP_A_LIMIT;
    }

    private void decayVulcan297Risk() {
        if (vulcan297FlightFRisk > 0.0) {
            vulcan297FlightFRisk = Math.max(0.0, vulcan297FlightFRisk - VULCAN_297_FLIGHT_F_DECAY);
        }
        if (vulcan297JumpARisk > 0.0) {
            vulcan297JumpARisk = Math.max(0.0, vulcan297JumpARisk - VULCAN_297_JUMP_A_DECAY);
        }
    }

    private Vec3 horizontalInputOffset(LocalPlayer player, double length) {
        if (player.input == null) return Vec3.ZERO;

        Vec2 input = player.input.getMoveVector();
        if (input.lengthSquared() <= 1.0E-7f) return Vec3.ZERO;

        float yaw = player.getYRot();
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = input.x * cos - input.y * sin;
        double z = input.y * cos + input.x * sin;
        Vec3 out = new Vec3(x, 0.0, z);
        return out.lengthSqr() > 1.0 ? out.normalize().scale(length) : out.scale(length);
    }

    private Vec3 withStrafe(Vec3 velocity, LocalPlayer player, double speed) {
        if (player.input == null) return velocity;
        Vec2 input = player.input.getMoveVector();
        Vec3 movementInput = new Vec3(input.x, 0.0, input.y);
        return MovementUtil.withStrafe(velocity, movementInput, player.getYRot(), speed, 1.0);
    }

    private void resetVulcan() {
        vulcanStepping = false;
        vulcanSequenceTicks = 0;
        vulcanEvenStep = false;
        vulcan297Aggressive = false;
    }

    public enum Mode {
        INSTANT,
        LEGIT,
        VULCAN_286,
        VULCAN_297
    }

    public enum PacketMode {
        FULL,
        POSITION_AND_ON_GROUND,
        NONE
    }
}
