/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import combatant.client.render.engine.renderer.Renderer3D;

public enum BlockOutlineRenderer {
    ;

    public static void render(Renderer3D renderer,
                              ClientLevel world,
                              BlockPos pos,
                              BlockState state,
                              BlockOutlineShapeMode mode,
                              int argb) {
        if (renderer == null || world == null || pos == null || state == null || state.isAir()) {
            return;
        }

        if (mode == BlockOutlineShapeMode.VOXEL_SHAPE) {
            renderVoxelShape(renderer, world, pos, state, argb);
        } else {
            renderBox(renderer, new AABB(pos), argb);
        }
    }

    public static void renderBox(Renderer3D renderer, AABB box, int argb) {
        if (renderer == null || box == null) {
            return;
        }

        int a = alpha(argb);
        int r = red(argb);
        int g = green(argb);
        int b = blue(argb);

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        line(renderer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(renderer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(renderer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(renderer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        line(renderer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(renderer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(renderer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(renderer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        line(renderer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(renderer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(renderer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(renderer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void renderVoxelShape(Renderer3D renderer,
                                         ClientLevel world,
                                         BlockPos pos,
                                         BlockState state,
                                         int argb) {
        Minecraft mc = Minecraft.getInstance();
        Entity cameraEntity = mc != null ? mc.getCameraEntity() : null;
        CollisionContext context = cameraEntity != null ? CollisionContext.of(cameraEntity) : CollisionContext.empty();
        VoxelShape shape = state.getShape(world, pos, context);
        if (shape.isEmpty()) {
            renderBox(renderer, new AABB(pos), argb);
            return;
        }

        int a = alpha(argb);
        int r = red(argb);
        int g = green(argb);
        int b = blue(argb);
        shape.move(pos.getX(), pos.getY(), pos.getZ()).forAllEdges((x1, y1, z1, x2, y2, z2) ->
                line(renderer, x1, y1, z1, x2, y2, z2, r, g, b, a)
        );
    }

    private static void line(Renderer3D renderer,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int r, int g, int b, int a) {
        renderer.line(x1, y1, z1, x2, y2, z2, r, g, b, a);
    }

    private static int red(int argb) {
        return (argb >>> 16) & 255;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 255;
    }

    private static int blue(int argb) {
        return argb & 255;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 255;
    }
}
