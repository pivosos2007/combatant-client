package combatant.client.render.engine.uniform;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeshBuilderCapacityTest {
    /** Writes without ensureCapacity() must grow the native buffers, never write past them. */
    @Test
    void unreservedWritesGrowBuffersAndKeepData() {
        String previous = System.setProperty("combatant.render.debug", "false");
        MeshBuilder mesh;
        try {
            mesh = new MeshBuilder(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLES, 1, 3);
        } finally {
            if (previous == null) System.clearProperty("combatant.render.debug");
            else System.setProperty("combatant.render.debug", previous);
        }
        try {
            mesh.beginWorld(Vec3.ZERO);
            int quads = 10_000;
            for (int q = 0; q < quads; q++) {
                int a = mesh.vec3(q, 0, 0).color(1, 2, 3, 4).next();
                int b = mesh.vec3(q, 1, 0).color(1, 2, 3, 4).next();
                int c = mesh.vec3(q, 1, 1).color(1, 2, 3, 4).next();
                int d = mesh.vec3(q, 0, 1).color(1, 2, 3, 4).next();
                mesh.quad(a, b, c, d);
            }
            mesh.end();

            assertEquals(quads * 4, mesh.getVertexCount());
            assertEquals(quads * 6, mesh.getIndicesCount());
            var vertices = mesh.vertexBufferView();
            assertEquals(quads * 4 * mesh.getVertexStride(), vertices.remaining());
            int lastVertex = (quads * 4 - 1) * mesh.getVertexStride();
            assertEquals(quads - 1, vertices.getFloat(lastVertex), 0.0f);
            var indices = mesh.indexBufferView();
            assertEquals(quads * 4 - 4, indices.getInt((quads * 6 - 1) * Integer.BYTES));
        } finally {
            mesh.close();
        }
    }
}
