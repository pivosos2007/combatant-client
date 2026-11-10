/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleListInfoProvider;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.movement.jesus.Vulcan297JesusController;
import combatant.client.util.item.EnchantUtil;
import combatant.client.util.player.effect.StatusEffectAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;

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

    private final EnumValue<Mode> mode =
            enumCommon("jesusMode", "mode", CommonSettingSchemas.MODE, Mode.SOLID, Mode.values());
    private final BooleanValue lava = visibleWhen(bool("jesusLava", "lava", false),
            () -> mode.get() != Mode.VULCAN_297);
    private final NumberValue<Double> lift = visibleWhen(num("jesusLift", "lift", 0.12D, 0.02D, 0.5D),
            () -> mode.get() == Mode.SOLID || mode.get() == Mode.DOLPHIN);
    private final NumberValue<Integer> hopDelay = visibleWhen(num("jesusHopDelay", "hop_delay", 4, 1, 20),
            () -> mode.get() == Mode.STEP);

    private final Vulcan297JesusController vulcanController = new Vulcan297JesusController();
    private final AtomicBoolean correctionPending = new AtomicBoolean();
    private int hopTicks;
    private Mode previousMode;
    private LocalPlayer previousPlayer;
    private net.minecraft.client.multiplayer.ClientLevel previousLevel;
    private final BlockPos.MutableBlockPos waterProbe = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos solidProbe = new BlockPos.MutableBlockPos();

    private boolean isVulcan297() {
        return mode.get() == Mode.VULCAN_297;
    }

    /**
     * Shared movement-module arbitration. Speed/Strafe/WaterSpeed must not
     * override a water-surface movement tick handled by Vulcan297.
     */
    public boolean isManagingVulcan297(LocalPlayer player) {
        if (!isEnabled() || !isVulcan297() || player == null || player != mc.player
                || mc.level == null || player.isPassenger() || player.getAbilities().flying
                || player.isFallFlying() || player.isSpectator() || player.isInLava()) return false;
        // Reserve water movement even during swimming/sneaking. Jesus itself
        // yields there, but another active speed multiplier must
        // not immediately take over and upset Vulcan's movement prediction.
        if (player.isInWater() || player.isSwimming() || vulcanController.isAnchored()) return true;
        double surface = waterLevel(player);
        return Double.isFinite(surface) && surface - player.getY() >= -0.32D;
    }

    @ModuleListInfoProvider
    private String moduleListDetail() {
        return switch (mode.get()) {
            case SOLID -> "Solid";
            case DOLPHIN -> "Dolphin";
            case STEP -> "Step";
            case VULCAN_297 -> "Vulcan 2.9.7";
        };
    }

    @Override
    public void onEnable() {
        resetState();
        previousMode = mode.get();
        previousPlayer = mc.player;
        previousLevel = mc.level;
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        hopTicks = 0;
        if (vulcanController.isAnchored() && mc.player != null && mc.level != null
                && !hasPhysicalSupport(mc.player)) {
            mc.player.setOnGround(false);
        }
        vulcanController.reset();
        correctionPending.set(false);
    }

    @EventHandler
    private void onPositionCorrection(PacketEvent.Receive event) {
        if (event.isOriginal() && event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            // Packet callbacks can be on a network thread. Consume this on the game tick.
            correctionPending.set(true);
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (previousMode != mode.get() || previousPlayer != player || previousLevel != mc.level) {
            resetState();
            previousMode = mode.get();
            previousPlayer = player;
            previousLevel = mc.level;
        }
        if (isVulcan297() && correctionPending.getAndSet(false)) {
            vulcanController.onServerCorrection();
        }
        if (player.isPassenger() || player.getAbilities().flying || player.isFallFlying()
                || player.isSpectator() || mc.options.keyShift.isDown() || player.onClimbable()
                || mc.level.getBlockState(player.blockPosition()).is(Blocks.COBWEB)
                || mc.level.getBlockState(BlockPos.containing(player.getEyePosition())).is(Blocks.COBWEB)) {
            if (isVulcan297() && vulcanController.onInactiveTick()
                    && !hasPhysicalSupport(player)) player.setOnGround(false);
            return;
        }

        switch (mode.get()) {
            case SOLID -> tickSolid(player);
            case DOLPHIN -> tickDolphin(player);
            case STEP -> tickStep(player);
            case VULCAN_297 -> tickVulcan297(player);
        }
    }

    /**
     * Jesus E measures horizontal displacement from movement packets. The
     * controller's earlier Y-only decision cannot bound that measurement.
     * Execute after other move handlers so the final SELF displacement is
     * limited, including vanilla sprinting and water-current acceleration.
     */
    @EventHandler(priority = -10000)
    private void onMove(PlayerMoveEvent event) {
        if (event.getType() != MoverType.SELF || !isVulcan297()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !isManagingVulcan297(player)
                || player.isSwimming() || player.isShiftKeyDown() || player.onClimbable()
                || player.hurtTime > 0 || player.horizontalCollision) return;

        double surface = waterLevel(player);
        double depth = surface - player.getY();
        if (!Double.isFinite(surface) || depth < -0.32D || hasPhysicalSupport(player)) return;

        // Deep swimming is already outside this mode's surface-walk envelope.
        // Keep normal underwater movement without constraining it unnecessarily.
        if (player.isEyeInFluid(FluidTags.WATER)
                && player.getFluidHeight(FluidTags.WATER) >= player.getBbHeight() + 0.08D) return;

        Vec3 movement = event.getMovement();
        if (movement == null) return;
        var limited = vulcanController.limitSurfaceMovement(player.getX(), player.getZ(),
                movement.x, movement.z);
        if (limited.x() != movement.x || limited.z() != movement.z) {
            event.setMovement(new Vec3(limited.x(), movement.y, limited.z()));
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

    /**
     * Vulcan297 follows Solid's anchored surface behavior. The controller owns
     * the jump transition and automatic return; no GUI tuning is necessary.
     */
    private void tickVulcan297(LocalPlayer player) {
        if (player.isInLava() || player.isSwimming() || player.hurtTime > 0
                || player.horizontalCollision) {
            if (vulcanController.onInactiveTick() && !hasPhysicalSupport(player)) {
                player.setOnGround(false);
            }
            return;
        }

        // Record the origin before Minecraft performs any SELF moves this tick.
        vulcanController.beginMovementTick(player.getX(), player.getZ());
        double surface = waterLevel(player);
        boolean waterNearby = Double.isFinite(surface)
                && surface - player.getY() >= -0.32D;

        // A prior synthetic onGround flag is NOT evidence of a real block.
        // The former (onGround && !isInWater) guard disabled the mode just
        // after it succeeded in aligning the player on top of the water.
        boolean physicalSupport = waterNearby && player.onGround() && hasPhysicalSupport(player);
        boolean nearSolid = waterNearby && isNearSolid(player);
        double height = player.getFluidHeight(FluidTags.WATER);
        boolean headSubmerged = player.isEyeInFluid(FluidTags.WATER);
        boolean fullySubmerged = headSubmerged && height >= player.getBbHeight() + 0.08D;
        boolean bubbleColumn = waterNearby && (mc.level.getBlockState(player.blockPosition()).is(Blocks.BUBBLE_COLUMN)
                || mc.level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.1D, player.getZ()))
                .is(Blocks.BUBBLE_COLUMN));

        // These observations are per-check hints only: they never authorize
        // extra acceleration, since Speed E has a different exemption set.
        boolean dolphinsGrace = waterNearby && StatusEffectAccess.has(player, MobEffects.DOLPHINS_GRACE);
        boolean depthStrider = waterNearby && EnchantUtil.level(mc.level, Enchantments.DEPTH_STRIDER,
                player.getItemBySlot(EquipmentSlot.FEET)) > 0;
        Vec3 velocity = player.getDeltaMovement();
        double horizontal = Math.hypot(velocity.x, velocity.z);
        boolean movingInput = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        Vulcan297JesusController.Decision decision = vulcanController.tick(
                new Vulcan297JesusController.Sample(surface, player.getY(), velocity.y,
                        horizontal, movingInput, mc.options.keyJump.isDown(),
                        headSubmerged, fullySubmerged, physicalSupport, nearSolid,
                        bubbleColumn, dolphinsGrace, depthStrider));

        if (decision == Vulcan297JesusController.Decision.NONE) return;
        if (decision.releaseGround() && player.onGround()) {
            player.setOnGround(false);
        }
        if (decision.snapToSurface() && Math.abs(decision.anchorY() - player.getY()) > 1.0E-5D) {
            player.setPos(player.getX(), decision.anchorY(), player.getZ());
        }
        if (decision.ground()) {
            player.setOnGround(true);
        }

        if (decision.overrideY() && velocity.y != decision.verticalY()) {
            player.setDeltaMovement(velocity.x, decision.verticalY(), velocity.z);
        }
    }

    /**
     * Minecraft's cached fluid height can describe the water within the entity
     * AABB. While above water, probe below the bounding box (also when falling
     * back from a surface jump) rather than requiring the feet to be wet.
     */
    private double waterLevel(LocalPlayer player) {
        double cached = player.isInWater() ? player.getFluidHeight(FluidTags.WATER) : 0.0D;
        double level = cached > 0.0D ? player.getY() + cached : Double.NEGATIVE_INFINITY;
        if (cached <= 0.2D) level = Math.max(level, waterSurfaceBelowFeet(player));
        return level == Double.NEGATIVE_INFINITY ? Double.NaN : level;
    }

    private double waterSurfaceBelowFeet(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        int y = (int) Math.floor(player.getY() - 0.075D);
        int minX = (int) Math.floor(box.minX + 0.035D);
        int maxX = (int) Math.floor(box.maxX - 0.035D);
        int minZ = (int) Math.floor(box.minZ + 0.035D);
        int maxZ = (int) Math.floor(box.maxZ - 0.035D);
        double surface = Double.NEGATIVE_INFINITY;
        // Catch a return from a jump up to two blocks over water. Do not snap
        // from that height: the controller only anchors in its narrow band.
        for (int dy = 0; dy >= -2; dy--) {
            int checkY = y + dy;
            surface = Math.max(surface, probeWater(minX, checkY, minZ));
            if (maxX != minX) surface = Math.max(surface, probeWater(maxX, checkY, minZ));
            if (maxZ != minZ) {
                surface = Math.max(surface, probeWater(minX, checkY, maxZ));
                if (maxX != minX) surface = Math.max(surface, probeWater(maxX, checkY, maxZ));
            }
            if (surface > Double.NEGATIVE_INFINITY) break;
        }
        return surface;
    }

    private double probeWater(int x, int y, int z) {
        waterProbe.set(x, y, z);
        FluidState fluid = mc.level.getFluidState(waterProbe);
        return fluid.is(FluidTags.WATER)
                ? y + fluid.getHeight(mc.level, waterProbe)
                : Double.NEGATIVE_INFINITY;
    }

    private boolean hasPhysicalSupport(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        int y = (int) Math.floor(box.minY - 0.06D);
        int minX = (int) Math.floor(box.minX + 0.035D);
        int maxX = (int) Math.floor(box.maxX - 0.035D);
        int minZ = (int) Math.floor(box.minZ + 0.035D);
        int maxZ = (int) Math.floor(box.maxZ - 0.035D);
        return supportsAt(minX, y, minZ) || supportsAt(maxX, y, minZ)
                || supportsAt(minX, y, maxZ) || supportsAt(maxX, y, maxZ);
    }

    private boolean supportsAt(int x, int y, int z) {
        solidProbe.set(x, y, z);
        var state = mc.level.getBlockState(solidProbe);
        return !state.getCollisionShape(mc.level, solidProbe).isEmpty()
                && state.getFluidState().isEmpty();
    }

    /** Conservative local approximation of Vulcan's NEAR_SOLID exemption. */
    private boolean isNearSolid(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(0.28D, 0.16D, 0.28D);
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    solidProbe.set(x, y, z);
                    if (mc.level.getBlockState(solidProbe).isSolid()) return true;
                }
            }
        }
        return false;
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
        STEP,
        VULCAN_297
    }
}
