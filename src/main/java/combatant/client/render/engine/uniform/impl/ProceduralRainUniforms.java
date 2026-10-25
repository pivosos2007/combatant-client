/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Shared std430 compute/vertex/fragment weather frame*/
public enum ProceduralRainUniforms {
    ;
    public static final Std430StructLayout COMPUTE_LAYOUT = Std430StructLayout.builder()
            .member("viewProjection", Std430Type.MAT4)
            .member("frame", Std430Type.VEC4)
            .member("windScreen", Std430Type.VEC4)
            .member("camera", Std430Type.VEC4)
            .member("nearAnchor", Std430Type.VEC4)
            .member("midAnchor", Std430Type.VEC4)
            .member("farAnchor", Std430Type.VEC4)
            .member("extra", Std430Type.VEC4)
            .build();
    private static final Data DATA = new Data();
    private static final Std430Writer WRITER = new Std430Writer(COMPUTE_LAYOUT, 1);

    public static void update(Matrix4f cameraProjection, Vec3 camera, float seconds,
                              float rain, float density, int distance, float windX, float windZ,
                              float thunder, int width, int height, boolean zeroToOne, int frameStamp) {
        DATA.viewProjection.set(cameraProjection);
        DATA.time = seconds;
        DATA.frameStamp = frameStamp;
        DATA.rain = rain;
        DATA.density = density;
        DATA.distance = distance;
        DATA.windX = windX;
        DATA.windZ = windZ;
        DATA.width = width;
        DATA.height = height;
        DATA.cameraY = (float) camera.y;
        DATA.thunder = thunder;
        DATA.depthScale = zeroToOne ? 1.0f : 0.5f;
        DATA.depthBias = zeroToOne ? 0.0f : 0.5f;
        putAnchor(DATA.near, camera, 0.75);
        putAnchor(DATA.mid, camera, 1.75);
        putAnchor(DATA.far, camera, 3.0);
    }

    private static void putAnchor(float[] anchor, Vec3 camera, double spacing) {
        // The world coordinate portion stays modulo a 8192-cell repeat; shader
        // floor and hash therefore retain sub-block precision near world borders.
        long cellX = (long) Math.floor(camera.x / spacing);
        long cellZ = (long) Math.floor(camera.z / spacing);
        anchor[0] = Math.floorMod(cellX, 8192L);
        anchor[1] = Math.floorMod(cellZ, 8192L);
        anchor[2] = (float) (camera.x - cellX * spacing);
        anchor[3] = (float) (camera.z - cellZ * spacing);
    }

    public static java.nio.ByteBuffer computeParameters() {
        return WRITER
                .putMat4(0, "viewProjection", DATA.viewProjection)
                .putVec4(0, "frame", DATA.time, DATA.rain, DATA.density, DATA.distance)
                .putVec4(0, "windScreen", DATA.windX, DATA.windZ, DATA.width, DATA.height)
                .putVec4(0, "camera", DATA.cameraY, DATA.thunder, DATA.depthScale, DATA.depthBias)
                .putVec4(0, "nearAnchor", DATA.near[0], DATA.near[1], DATA.near[2], DATA.near[3])
                .putVec4(0, "midAnchor", DATA.mid[0], DATA.mid[1], DATA.mid[2], DATA.mid[3])
                .putVec4(0, "farAnchor", DATA.far[0], DATA.far[1], DATA.far[2], DATA.far[3])
                .putVec4(0, "extra", DATA.frameStamp, 0.0f, 0.0f, 0.0f)
                .buffer();
    }

    private static final class Data {
        private final Matrix4f viewProjection = new Matrix4f();
        private final float[] near = new float[4];
        private final float[] mid = new float[4];
        private final float[] far = new float[4];
        private float time, rain, density, distance;
        private float windX, windZ, width, height;
        private float cameraY, thunder, depthScale, depthBias;
        private int frameStamp;
    }
}
