/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.uniform;

import net.minecraft.client.renderer.DynamicUniformStorage;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

public final class MeshData implements DynamicUniformStorage.DynamicUniform {

    private final Matrix4f proj;
    private final Matrix4f modelView;

    public MeshData(Matrix4f proj, Matrix4f modelView) {
        this.proj = new Matrix4f(proj);
        this.modelView = new Matrix4f(modelView);
    }

    @Override
    public void write(ByteBuffer buffer) {
        // std140: mat4 = 16 floats, column-major
        proj.get(buffer);
        modelView.get(buffer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeshData other)) return false;
        return proj.equals(other.proj) && modelView.equals(other.modelView);
    }

    @Override
    public int hashCode() {
        return proj.hashCode() * 31 + modelView.hashCode();
    }
}
