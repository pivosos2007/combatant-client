/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Immutable producer data for one fluid surface quad. Corner positions are local to the fluid
 * block; world position and flow are explicit and never reconstructed from the rendered image.
 */
public record FluidSurfaceData(
        BlockPos blockPos,
        Identifier fluidId,
        MaterialDomain domain,
        int materialId,
        Identifier spriteId,
        float flowX,
        float flowZ,
        boolean source,
        int amount,
        int fluidConnectivity,
        ModelQuadFacing facing,
        boolean reversed,
        float[] x,
        float[] y,
        float[] z,
        float[] u,
        float[] v,
        int[] color,
        float[] ao,
        int[] light,
        int mapMask,
        int featureMask,
        int packedSurface,
        MaterialSurfaceDescriptor material
) {
    public FluidSurfaceData {
        if (blockPos == null) blockPos = BlockPos.ZERO;
        if (fluidId == null) fluidId = Identifier.fromNamespaceAndPath("combatant", "unknown_fluid");
        if (domain == null) domain = MaterialDomain.UNKNOWN;
        if (spriteId == null) spriteId = Identifier.fromNamespaceAndPath("combatant", "unknown");
        x = copy4(x);
        y = copy4(y);
        z = copy4(z);
        u = copy4(u);
        v = copy4(v);
        color = copy4(color);
        ao = copy4(ao);
        light = copy4(light);
    }

    public static FluidSurfaceData fromCurrent(ModelQuadView quad,
                                               ModelQuadFacing facing,
                                               boolean reversed,
                                               MaterialSurfaceDescriptor material,
                                               ChunkVertexEncoder.Vertex[] vertices,
                                               int mapMask,
                                               int featureMask,
                                               int packedSurface) {
        FluidSurfaceContext.Entry context = FluidSurfaceContext.current();
        if (context == null || quad == null || material == null) return null;
        FluidState state = context.fluidState();
        Identifier fluidId = state == null
                ? Identifier.fromNamespaceAndPath("combatant", "unknown_fluid")
                : BuiltInRegistries.FLUID.getKey(state.getType());
        Vec3 flow = context.flow() == null ? Vec3.ZERO : context.flow();
        float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], v = new float[4];
        int[] color = new int[4], light = new int[4];
        float[] ao = new float[4];
        for (int i = 0; i < 4; i++) {
            x[i] = quad.getX(i);
            y[i] = quad.getY(i);
            z[i] = quad.getZ(i);
            u[i] = quad.getTexU(i);
            v[i] = quad.getTexV(i);
            if (vertices != null && i < vertices.length && vertices[i] != null) {
                color[i] = vertices[i].color;
                ao[i] = vertices[i].ao;
                light[i] = vertices[i].light;
            } else {
                color[i] = 0xFFFFFFFF;
                ao[i] = 1.0f;
                light[i] = 0x00F000F0;
            }
        }
        return new FluidSurfaceData(
                context.worldPos(),
                fluidId,
                material.domain(),
                material.stableId(),
                material.spriteId(),
                (float) flow.x,
                (float) flow.z,
                state != null && state.isSource(),
                state == null ? 0 : state.getAmount(),
                WaterSurfaceContract.fluidConnectivity(context.level(), context.worldPos(), state),
                facing,
                reversed,
                x, y, z, u, v, color, ao, light,
                mapMask, featureMask, packedSurface,
                material
        );
    }

    /** True only for the visible upper liquid surface, excluding side/bottom/back-facing duplicate quads. */
    public boolean isTopSurface() {
        if (reversed) return false;
        if (facing != null && facing.isAligned()) {
            // Sodium explicitly marks planar UP/DOWN/horizontal fluid faces. Trust that producer
            // contract instead of inferring topness from its 0.001 bottom-face epsilon.
            return facing.getAlignedNormal().y() > 0.5f;
        }

        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (float value : y) {
            minY = Math.min(minY, value);
            maxY = Math.max(maxY, value);
        }
        // Sodium can classify a sloped top as UNASSIGNED. Its side/bottom quads touch the
        // producer epsilon plane (0.001), while a genuine top keeps every corner above it.
        return minY > 0.0015f && maxY <= 1.0015f;
    }

    public float[] cornerHeights() {
        return Arrays.copyOf(y, 4);
    }

    /** Producer-known stable fluid registry identity, suitable for GPU/debug contracts. */
    public int fluidTypeId() {
        return WaterSurfaceContract.stableFluidTypeId(fluidId);
    }

    public float flowStrength() {
        return WaterSurfaceContract.flowStrength(flowX, flowZ);
    }

    public boolean still() {
        return flowStrength() <= 1.0e-5f;
    }

    public int surfaceFlags() {
        return WaterSurfaceContract.flags(this);
    }

    public boolean connected(int bit) {
        return (fluidConnectivity & bit) != 0;
    }

    public float[] surfaceNormal() {
        return WaterSurfaceContract.surfaceNormal(this);
    }

    private static float[] copy4(float[] values) {
        if (values == null || values.length != 4) throw new IllegalArgumentException("fluid quad requires four values");
        return Arrays.copyOf(values, 4);
    }

    private static int[] copy4(int[] values) {
        if (values == null || values.length != 4) throw new IllegalArgumentException("fluid quad requires four values");
        return Arrays.copyOf(values, 4);
    }
}

