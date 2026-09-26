/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

import combatant.client.render.engine.RenderState;
import combatant.client.render.sodium.fluid.WaterSurfaceContract;
import combatant.client.render.sodium.fluid.FluidSurfaceMetadataCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/** Explicit camera-medium contract captured from world/fluid state rather than rendered pixels. */
public record CameraMediumState(
        Medium medium,
        int fluidTypeId,
        float boundarySurfaceY,
        BoundarySource boundarySource
) {
    private static final int MAX_COLUMN_SCAN = 384;

    public CameraMediumState {
        if (medium == null) medium = Medium.UNKNOWN;
        if (boundarySource == null) boundarySource = BoundarySource.UNKNOWN;
        if (!Float.isFinite(boundarySurfaceY)) {
            boundarySurfaceY = 0.0f;
            boundarySource = BoundarySource.UNKNOWN;
        }
    }

    public static CameraMediumState capture() {
        FogType type = RenderState.cameraSubmersion;
        Medium medium = switch (type) {
            case WATER -> Medium.WATER;
            case LAVA -> Medium.LAVA;
            case POWDER_SNOW -> Medium.POWDER_SNOW;
            default -> Medium.AIR;
        };
        if (medium != Medium.WATER) {
            return new CameraMediumState(medium, 0, 0.0f, BoundarySource.UNKNOWN);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return new CameraMediumState(Medium.WATER, 0, 0.0f, BoundarySource.UNKNOWN);
        }

        Vec3 camera = RenderState.cameraPos == null ? Vec3.ZERO : RenderState.cameraPos;
        BlockPos cameraCell = BlockPos.containing(camera);
        FluidState state = minecraft.level.getFluidState(cameraCell);
        if (state == null || state.isEmpty()) {
            return new CameraMediumState(Medium.WATER, 0, 0.0f, BoundarySource.UNKNOWN);
        }

        Fluid fluid = state.getType();
        Identifier fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        int fluidTypeId = WaterSurfaceContract.stableFluidTypeId(fluidId);

        BlockPos topCell = cameraCell;
        FluidState topState = state;
        for (int i = 0; i < MAX_COLUMN_SCAN; i++) {
            BlockPos above = topCell.above();
            FluidState aboveState = minecraft.level.getFluidState(above);
            if (aboveState == null || !fluid.isSame(aboveState.getType())) break;
            topCell = above;
            topState = aboveState;
        }

        FluidSurfaceMetadataCapture.SurfaceSample extracted = FluidSurfaceMetadataCapture.sampleTopSurface(
                topCell, fluidTypeId, camera.x, camera.z);
        if (extracted.valid()) {
            return new CameraMediumState(Medium.WATER, fluidTypeId, extracted.worldY(),
                    BoundarySource.EXTRACTED_SURFACE);
        }

        float logicalHeight = topState == null ? 0.0f : topState.getOwnHeight();
        if (Float.isFinite(logicalHeight) && logicalHeight > 0.0f) {
            return new CameraMediumState(Medium.WATER, fluidTypeId,
                    topCell.getY() + logicalHeight, BoundarySource.FLUID_COLUMN);
        }
        return new CameraMediumState(Medium.WATER, fluidTypeId, 0.0f, BoundarySource.UNKNOWN);
    }

    public boolean insideWater() {
        return medium == Medium.WATER;
    }

    public boolean boundarySurfaceValid() {
        return insideWater() && boundarySource != BoundarySource.UNKNOWN;
    }

    public enum Medium {
        AIR(0), WATER(1), LAVA(2), POWDER_SNOW(3), UNKNOWN(4);

        private final int gpuCode;

        Medium(int gpuCode) {
            this.gpuCode = gpuCode;
        }

        public int gpuCode() {
            return gpuCode;
        }
    }

    public enum BoundarySource {
        UNKNOWN(0),
        FLUID_COLUMN(1),
        EXTRACTED_SURFACE(2);

        private final int gpuCode;

        BoundarySource(int gpuCode) {
            this.gpuCode = gpuCode;
        }

        public int gpuCode() {
            return gpuCode;
        }
    }
}
