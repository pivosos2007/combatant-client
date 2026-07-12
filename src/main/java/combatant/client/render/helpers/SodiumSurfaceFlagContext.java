/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayDeque;

public enum SodiumSurfaceFlagContext {
    ;
    private static final ThreadLocal<ArrayDeque<StateFlags>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile boolean loggedSoftFadeState;

    public static void pushForState(Object stateObj) {
        pushForState(stateObj, null);
    }

    public static void pushForState(Object stateObj, BlockPos origin) {
        boolean softFade = false;
        WaveType waveType = WaveType.NONE;
        if (stateObj instanceof BlockState state) {
            softFade = NoRender.shouldFadeSoftBlock(state);
            if (ReimaginedVisual.isWavyVegetationEnabledStatic()) {
                waveType = getVegetationWaveType(state);
            }
            if (softFade && !loggedSoftFadeState) {
                loggedSoftFadeState = true;
                DebugLog.renderThread("[SodiumSurface] state hook saw soft fade block: %s", state.getBlock());
            }
        }
        STACK.get().addLast(new StateFlags(softFade, waveType, origin == null ? 0 : origin.getY()));
    }

    public static void pop() {
        ArrayDeque<StateFlags> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.removeLast();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static int getSurfaceFlags() {
        return getSurfaceFlags(0.0f);
    }

    public static int getSurfaceFlags(float vertexY) {
        StateFlags flags = current();
        int encoded = 0;
        if (flags.softFade) {
            encoded |= SodiumMaterialFlags.SURFACE_FLAG_SOFT_FADE;
        }
        if (flags.waveType != WaveType.NONE) {
            encoded |= SodiumMaterialFlags.SURFACE_FLAG_WAVY_VEGETATION;
            encoded |= ReimaginedVisual.packWavyVegetationSettingsStatic();
            if (flags.waveType == WaveType.FREE) {
                encoded |= SodiumMaterialFlags.SURFACE_FLAG_WAVY_VEGETATION_FREE;
            }
            encoded |= encodeLocalY(vertexY - flags.originY) << SodiumMaterialFlags.WAVE_LOCAL_Y_SHIFT;
        }
        return encoded;
    }

    private static WaveType getVegetationWaveType(BlockState state) {
        Block block = state.getBlock();
        if (state.is(BlockTags.LEAVES)
                || state.is(BlockTags.CAVE_VINES)
                || block instanceof LeavesBlock
                || block instanceof VineBlock) {
            return WaveType.FREE;
        }

        if (state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS)
                || block instanceof VegetationBlock
                || block instanceof GrowingPlantBlock) {
            return WaveType.ROOTED;
        }

        return WaveType.NONE;
    }

    private static int encodeLocalY(float localY) {
        float clamped = Math.max(0.0f, Math.min(1.0f, localY));
        return Math.round(clamped * SodiumMaterialFlags.WAVE_LOCAL_Y_MASK);
    }

    private static StateFlags current() {
        ArrayDeque<StateFlags> stack = STACK.get();
        return stack.isEmpty() ? StateFlags.EMPTY : stack.peekLast();
    }

    private enum WaveType {
        NONE,
        ROOTED,
        FREE
    }

    private record StateFlags(boolean softFade, WaveType waveType, int originY) {
        private static final StateFlags EMPTY = new StateFlags(false, WaveType.NONE, 0);
    }
}
