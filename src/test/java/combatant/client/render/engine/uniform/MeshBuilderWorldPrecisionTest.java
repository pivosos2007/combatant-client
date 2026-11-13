/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeshBuilderWorldPrecisionTest {
    @Test
    void worldVerticesPreserveSmallOffsetsAtLargeCameraCoordinates() {
        try (MeshBuilder mesh = new MeshBuilder(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLES)) {
            Vec3 camera = new Vec3(33_554_432.125, 1_000_000_000.25, -33_554_432.875);
            mesh.beginWorld(camera);
            mesh.vec3(camera.x + 0.25, camera.y + 0.125, camera.z - 0.5)
                    .color(255, 255, 255, 255).next();
            mesh.end();

            ByteBuffer vertices = mesh.vertexBufferView();
            assertEquals(0.25f, vertices.getFloat(0));
            assertEquals(0.125f, vertices.getFloat(Float.BYTES));
            assertEquals(-0.5f, vertices.getFloat(Float.BYTES * 2));
        }
    }

    @Test
    void localMeshResetsCameraAnchors() {
        try (MeshBuilder mesh = new MeshBuilder(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLES)) {
            mesh.beginWorld(new Vec3(10_000_000.0, -1_000_000_000.0, 10_000_000.0));
            mesh.vec3(10_000_001.0, -999_999_999.75, 10_000_001.0)
                    .color(255, 255, 255, 255).next();
            mesh.end();

            mesh.beginLocal();
            mesh.vec3(1.0, 2.5, 3.0).color(255, 255, 255, 255).next();
            mesh.end();

            ByteBuffer vertices = mesh.vertexBufferView();
            assertEquals(1.0f, vertices.getFloat(0));
            assertEquals(2.5f, vertices.getFloat(Float.BYTES));
            assertEquals(3.0f, vertices.getFloat(Float.BYTES * 2));
        }
    }
}
