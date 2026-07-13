/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Portions derived from ThunderHack Recode, copyright (c) 2023-2024 Pan4ur & 06ED.
 * Upstream: https://github.com/Pan4ur/ThunderHack-Recode
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventBreakBlock;
import combatant.client.events.impl.EventCollision;
import combatant.client.events.impl.EventPostSync;
import combatant.client.events.impl.EventSync;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.InteractionUtil;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.player.inventory.InventorySwap;

@Deprecated
//todo Description
//работает только на ванилах без ач
@ModuleInfo(
        id = "phase",
        displayName = "Phase",
        category = ModuleCategory.MOVEMENT
)
public class Phase extends Module {

    private final EnumValue<Mode> mode =
            enumSetting("mode", "mode", Mode.VANILLA, Mode.values());
    private final BooleanValue silent =
            visibleWhen(bool("silent", false), () -> mode.get() == Mode.BREAK_ASSIST);
    private final BooleanValue waitBreak =
            visibleWhen(bool("wait_break", true), () -> mode.get() == Mode.BREAK_ASSIST);
    private final NumberValue<Integer> afterBreak =
            visibleWhen(num("break_timeout", 4, 1, 20), () -> mode.get() == Mode.BREAK_ASSIST && waitBreak.get());
    private final BooleanValue onlyOnGround =
            visibleWhen(bool("only_on_ground", false), () -> mode.get() == Mode.PEARL);
    private final BooleanValue autoDisable =
            visibleWhen(bool("auto_disable", false), () -> mode.get() == Mode.PEARL);
    private final NumberValue<Integer> afterPearl =
            visibleWhen(num("pearl_timeout", 0, 0, 60), () -> mode.get() == Mode.PEARL);
    private final NumberValue<Float> pitch =
            visibleWhen(num("pitch", 80f, 0f, 90f), () -> mode.get() == Mode.PEARL);
    private final BooleanValue strict =
            visibleWhen(bool("strict", false), () -> mode.get() == Mode.FORCE_MINE);
    public int clipTimer;
    public int afterPearlTime;

    @Override
    public void onEnable() {
        afterPearlTime = 0;
        clipTimer = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        if (mc.player.onGround() && mode.get() == Mode.CC_CLIP) {
            double[] diagonalOffset = MovementUtil.forwardWithoutStrafe(0.44);
            boolean diagonal = mc.player.getYRot() % 90 > 35 && mc.player.getYRot() % 90 < 55;

            sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));

            if (diagonal) {
                double[] directionVec = MovementUtil.forwardWithoutStrafe(0.51);

                int height = mc.level.clip(
                        new ClipContext(
                                mc.player.getEyePosition(),
                                mc.player.getEyePosition().add(diagonalOffset[0], 0, diagonalOffset[1]),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                mc.player
                        )
                ).getType().equals(HitResult.Type.MISS) ? 1 : 2;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() + height, mc.player.getZ() + directionVec[1]);
                sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));

                height = mc.level.isEmptyBlock(BlockPos.containing(mc.player.position().add(diagonalOffset[0], -2, diagonalOffset[1]))) ? 2 : 1;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() - height, mc.player.getZ() + directionVec[1]);
                sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
                setEnabled(false);
            } else {
                double[] directionVec = MovementUtil.forwardWithoutStrafe(0.57);

                int height = mc.level.clip(
                        new ClipContext(
                                mc.player.getEyePosition(),
                                mc.player.getEyePosition().add(diagonalOffset[0], 0, diagonalOffset[1]),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                mc.player
                        )
                ).getType().equals(HitResult.Type.MISS) ? 1 : 2;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() + height, mc.player.getZ() + directionVec[1]);
                sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY(), mc.player.getZ() + directionVec[1]);
                sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));

                height = mc.level.isEmptyBlock(BlockPos.containing(mc.player.position().add(diagonalOffset[0], -2, diagonalOffset[1]))) ? 2 : 1;

                mc.player.setPos(mc.player.getX() + directionVec[0], mc.player.getY() - height, mc.player.getZ() + directionVec[1]);
                sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
                setEnabled(false);
            }
        }
    }

    @EventHandler
    public void onCollide(EventCollision e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        BlockPos playerPos = BlockPos.containing(mc.player.position());

        if ((!isMode(Mode.CC_CLIP) && !isMode(Mode.PEARL) && !isMode(Mode.FORCE_MINE) && canNoClip()) || afterPearlTime > 0) {
            if (!e.getPos().equals(playerPos.below()) || mc.options.keyShift.isDown()) {
                e.setState(Blocks.AIR.defaultBlockState());
            }
        }

        if (isMode(Mode.FORCE_MINE)) {
            float xDelta = Math.abs(playerPos.getX() - e.getPos().getX());
            float zDelta = Math.abs(playerPos.getZ() - e.getPos().getZ());

            if (xDelta != 0 && zDelta != 0 && strict.get()) {
                return;
            }

            if (!e.getPos().equals(playerPos.below()) || mc.options.keyShift.isDown()) {
                e.setState(Blocks.AIR.defaultBlockState());
            }
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        if (clipTimer > 0) clipTimer--;
        if (afterPearlTime > 0) afterPearlTime--;

        if (isMode(Mode.BREAK_ASSIST)
                && (mc.player.horizontalCollision || playerInsideBlock())
                && !mc.player.isUnderWater()
                && !mc.player.isInLava()
                && clipTimer <= 0) {
            double[] dir = MovementUtil.forward(0.5);

            BlockPos blockToBreak = null;

            if (mc.options.keyJump.isDown()) {
                blockToBreak = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY() + 2, mc.player.getZ() + dir[1]);
            } else if (mc.options.keyShift.isDown()) {
                blockToBreak = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY() - 1, mc.player.getZ() + dir[1]);
            } else if (MovementUtil.isMoving()) {
                blockToBreak = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY(), mc.player.getZ() + dir[1]);
            }

            if (blockToBreak == null) return;
            int bestTool = findBestTool(blockToBreak);
            if (bestTool == -1) return;

            int prevItem = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
            InventorySwap.INSTANCE.selectHotbar(bestTool);
            final BlockPos target = blockToBreak;
            final Direction face = mc.player.getDirection();
            InteractionUtil.sendSequencedPacket(id ->
                    new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, target, face, id));
            InteractionUtil.sendSequencedPacket(id ->
                    new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, target, face, id));
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (silent.get()) InventorySwap.INSTANCE.selectHotbar(prevItem);
        }

        if (isMode(Mode.FORCE_MINE)
                && (mc.player.horizontalCollision || playerInsideBlock())
                && !mc.player.isUnderWater()
                && !mc.player.isInLava()) {
            for (int x = -2; x < 2; x++) {
                for (int y = -1; y < 3; y++) {
                    for (int z = -2; z < 2; z++) {
                        if (((x == 0 && y == 0 && z == 0) || (x == 0 && y == 1 && z == 0)) && !mc.options.keyShift.isDown()) {
                            continue;
                        }

                        BlockPos bp = BlockPos.containing(mc.player.position()).offset(x, y, z);
                        if (mc.player.getBoundingBox().intersects(new AABB(bp)) && !mc.level.isEmptyBlock(bp)) {
                            sendPacket(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, bp, Direction.UP));
                        }
                    }
                }
            }
        }

        if (isMode(Mode.PEARL) && (mc.player.onGround() || !onlyOnGround.get())) {
            if (mc.player.horizontalCollision && !playerInsideBlock() && clipTimer <= 0 && mc.player.tickCount > 60) {
                double[] dir = MovementUtil.forward(0.5);
                BlockPos block = BlockPos.containing(mc.player.getX() + dir[0], mc.player.getY(), mc.player.getZ() + dir[1]);

                if (mc.options.keyShift.isDown()) return;

                float[] angle = InteractionUtil.calculateAngle(Vec3.atCenterOf(block));
                int epSlot = findEPSlot();

                if (epSlot != -1) {
                    mc.player.setYRot(angle[0]);
                    mc.player.setXRot(pitch.get());
                }
            }
        }
    }

    @EventHandler
    public void onPostSync(EventPostSync e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        if (isMode(Mode.PEARL) && (mc.player.onGround() || !onlyOnGround.get())) {
            if (mc.player.horizontalCollision && !playerInsideBlock() && clipTimer <= 0 && mc.player.tickCount > 60) {
                if (mc.options.keyShift.isDown()) return;

                int epSlot = findEPSlot();
                int prevItem = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();

                if (epSlot != -1) {
                    InventorySwap.INSTANCE.selectHotbar(epSlot);
                    InteractionUtil.sendSequencedPacket(id ->
                            new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, mc.player.getYRot(), mc.player.getXRot()));
                    sendPacket(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                    InventorySwap.INSTANCE.selectHotbar(prevItem);
                    if (autoDisable.get()) {
                        setEnabled(false);
                    }
                }
                clipTimer = 20;
                afterPearlTime = afterPearl.get();
            }
        }
    }

    @EventHandler
    public void onBreakBlock(EventBreakBlock e) {
        clipTimer = afterBreak.get();
    }

    public boolean canNoClip() {
        if (isMode(Mode.VANILLA)) return true;
        if (!waitBreak.get()) return true;
        return clipTimer != 0;
    }

    public boolean playerInsideBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return false;
        return !mc.level.isEmptyBlock(BlockPos.containing(mc.player.position()));
    }

    private int findEPSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return -1;
        int epSlot = -1;
        if (mc.player.getMainHandItem().getItem() == net.minecraft.world.item.Items.ENDER_PEARL) {
            epSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
        }
        if (epSlot == -1) {
            for (int i = 0; i < 9; ++i) {
                if (mc.player.getInventory().getItem(i).getItem() == net.minecraft.world.item.Items.ENDER_PEARL) {
                    epSlot = i;
                    break;
                }
            }
        }
        return epSlot;
    }

    private boolean isMode(Mode target) {
        return mode.get() == target;
    }

    private void sendPacket(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null || packet == null) return;
        mc.getConnection().send(packet);
    }

    private int findBestTool(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return -1;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return -1;

        int slot = -1;
        float best = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) continue;

            int level = 0;
            Holder<Enchantment> entry = entryOf(Enchantments.EFFICIENCY);
            if (entry != null) {
                level = EnchantmentHelper.getItemEnchantmentLevel(entry, stack);
            }
            float digSpeed = level;
            float destroySpeed = stack.getDestroySpeed(state);
            float score = digSpeed + destroySpeed;
            if (score > best) {
                best = score;
                slot = i;
            }
        }
        return slot;
    }

    private Holder<Enchantment> entryOf(ResourceKey<Enchantment> key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || key == null) return null;
        Registry<Enchantment> registry =
                mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment value = registry.getValue(key);
        if (value == null) return null;
        return registry.wrapAsHolder(value);
    }

    public enum Mode {
        VANILLA,
        PEARL,
        BREAK_ASSIST,
        FORCE_MINE,
        CC_CLIP
    }
}
