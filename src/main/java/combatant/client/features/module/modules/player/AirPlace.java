/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer3D;

//todo Description
@ModuleInfo(
        id = "airplace",
        displayName = "AirPlace",
        category = ModuleCategory.PLAYER
)
public class AirPlace extends Module {

    private static final String SETTING_DISTANCE = "distance";
    private static final String SETTING_FILL_COLOR = "fill_color";
    private static final String SETTING_LINE_COLOR = "line_color";
    private static final String SETTING_LINE_WIDTH = "line_width";
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Float> distanceValue =
            num("airplace_distance", SETTING_DISTANCE, 5f, 3f, 6f);
    private final RGBAColorValue fillColor =
            color("airplace_fill_color", SETTING_FILL_COLOR, "#326432FF");
    private final RGBAColorValue lineColor =
            color("airplace_line_color", SETTING_LINE_COLOR, "#966432FF");
    private final NumberValue<Integer> lineWidth =
            num("airplace_line_width", SETTING_LINE_WIDTH, 2, 1, 5);
    private BlockPos lastPos;
    private boolean lastRayMiss = false;
    private int sequence = 0;
    private boolean prevUsePressed = false;

    {
        addAction("increase_distance", "KP_ADD", BindMode.PRESS);
        addAction("decrease_distance", "KP_SUBTRACT", BindMode.PRESS);
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc == null || mc.player == null) return;

        LocalPlayer player = mc.player;
        boolean usePressed = mc.options.keyUse.isDown();

        handleDistanceBinds();
        handleTick(player, usePressed);

        prevUsePressed = usePressed;
    }

    private void handleTick(LocalPlayer player, boolean useKeyPressed) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) {
            lastPos = null;
            lastRayMiss = false;
            return;
        }

        updateTarget(player, distanceValue.get());

        if (useKeyPressed && !prevUsePressed) {
            if (lastRayMiss && lastPos != null) tryPlaceBlock(player);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        boolean main = mc.player.getMainHandItem().getItem() instanceof BlockItem;
        boolean off = mc.player.getOffhandItem().getItem() instanceof BlockItem;
        if (!main && !off) return;
        if (!lastRayMiss || lastPos == null) return;
        if (!isPlaceableTarget(lastPos)) return;

        AABB box = new AABB(lastPos);
        int fill = fillColor.getArgb();
        int line = lineColor.getArgb();

        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = Math.max(1.0f, lineWidth.get());
        try {
            addFilledBox(renderer, box, fill);
            addOutlineBox(renderer, box, line);
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    private void updateTarget(LocalPlayer player, double distance) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).normalize().scale(distance));

        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        BlockPos pos;
        if (hit.getType() == BlockHitResult.Type.MISS) {
            pos = new BlockPos(
                    Mth.floor(end.x),
                    Mth.floor(end.y),
                    Mth.floor(end.z)
            );
            lastRayMiss = true;
        } else {
            pos = hit.getBlockPos().relative(hit.getDirection());
            lastRayMiss = false;
        }

        lastPos = isPlaceableTarget(pos) ? pos : null;
    }

    private void tryPlaceBlock(LocalPlayer player) {
        if (lastPos == null) return;

        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof BlockItem)) return;

        if (mc.player.connection == null) return;

        Vec3 hitVec = new Vec3(
                lastPos.getX() + 0.5,
                lastPos.getY() + 0.5,
                lastPos.getZ() + 0.5
        );

        BlockHitResult hit = new BlockHitResult(
                hitVec, Direction.UP, lastPos, false
        );

        mc.player.connection.send(
                new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, sequence++)
        );

        player.swing(InteractionHand.MAIN_HAND);

    }

    private boolean isPlaceableTarget(BlockPos pos) {
        if (pos == null || mc.level == null) return false;

        var state = mc.level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    private void handleDistanceBinds() {
        double step = 1.0;
        boolean changed = false;

        if (isActionPressedOnce("increase_distance")) {
            distanceValue.set(distanceValue.castToType(Math.min(6.0, distanceValue.get() + step)));
            changed = true;
        }
        if (isActionPressedOnce("decrease_distance")) {
            distanceValue.set(distanceValue.castToType(Math.max(3.0, distanceValue.get() - step)));
            changed = true;
        }

        if (changed) {
            saveConfig();
        }
    }
}

