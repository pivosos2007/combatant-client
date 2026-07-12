/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import combatant.client.render.engine.core.RenderFrameContext;

/**
 * Engine global render flags/state. Set these in render hooks.
 */
public enum RenderState {
    ;
    public static final Quaternionf cameraRotation = new Quaternionf();
    public static final Matrix4f worldProjection = new Matrix4f().identity();
    public static boolean rendering3D = false;
    /**
     * Legacy alias for vanilla tick progress.
     */
    public static float tickDelta = 0f;
    public static float tickProgress = 0f;
    public static float frameDeltaTicks = 0f;
    public static float frameDeltaSeconds = 0f;
    public static float fixedDeltaTicks = 0f;
    public static Vec3 cameraPos = Vec3.ZERO;
    public static float cameraYaw = 0f;
    public static float cameraPitch = 0f;
    public static Vec3 cameraLook = Vec3.ZERO;
    public static FogType cameraSubmersion = FogType.NONE;
    public static boolean worldTranslucent = false;
    public static @Nullable Frustum frustum;
    public static float lineWidth = 1.0f;
    // Active world frame id (set on begin).
    public static int activeWorldFrameId = -1;

    public static void applyContext(RenderFrameContext context) {
        if (context == null) return;
        tickDelta = context.tickDelta();
        tickProgress = context.tickProgress();
        frameDeltaTicks = context.frameDeltaTicks();
        frameDeltaSeconds = context.frameDeltaSeconds();
        fixedDeltaTicks = context.fixedDeltaTicks();
        if (context.camera() != null) {
            cameraPos = context.camera().position();
            cameraLook = context.camera().look();
            cameraRotation.set(context.camera().rotation());
            worldProjection.set(context.camera().projection());
        }
    }

    public static void clearContextBackedState() {
        rendering3D = false;
        worldTranslucent = false;
        activeWorldFrameId = -1;
        tickDelta = 0f;
        tickProgress = 0f;
        frameDeltaTicks = 0f;
        frameDeltaSeconds = 0f;
        fixedDeltaTicks = 0f;
    }

}
