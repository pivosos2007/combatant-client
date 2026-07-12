/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Camera data captured once for a Combatant frame/phase.
 */
public record CameraContext(Vec3 position, Vec3 look, Quaternionf rotation, Matrix4f projection, Matrix4f modelView) {
    public CameraContext(Vec3 position, Vec3 look, Quaternionf rotation, Matrix4f projection, Matrix4f modelView) {
        this.position = position;
        this.look = look;
        this.rotation = new Quaternionf(rotation);
        this.projection = new Matrix4f(projection);
        this.modelView = new Matrix4f(modelView);
    }

    public static CameraContext capture(Matrix4f projection, Matrix4f modelView) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) {
            return new CameraContext(Vec3.ZERO, Vec3.ZERO, new Quaternionf(), projection, modelView);
        }

        var camera = mc.gameRenderer.mainCamera();
        var forward = camera.forwardVector();
        return new CameraContext(
                camera.position(),
                new Vec3(forward.x(), forward.y(), forward.z()),
                camera.rotation(),
                projection,
                modelView
        );
    }

    @Override
    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    @Override
    public Matrix4f projection() {
        return new Matrix4f(projection);
    }

    @Override
    public Matrix4f modelView() {
        return new Matrix4f(modelView);
    }
}
