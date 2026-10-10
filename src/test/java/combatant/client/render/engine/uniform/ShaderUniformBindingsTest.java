package combatant.client.render.engine.uniform;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ShaderUniformBindingsTest {
    @Test
    void loadsGeneratedBlockAndMemberMetadata() {
        ShaderUniformBindings.Block block = ShaderUniformBindings.block("RigBones");

        assertEquals("std140", block.standard());
        assertEquals(4096, block.size());
        assertEquals(64, block.member("u_SkinMatrices").count());
        assertEquals(64, block.member("u_SkinMatrices").stride());
    }

    @Test
    void writesUsingOffsetsLoadedFromMetadata() {
        ShaderUniformBindings.Writer writer = ShaderUniformBindings.block("UIBatch").writer()
                .vec4("uScreen", 1.0f, 2.0f, 3.0f, 4.0f)
                .vec4("uLayer", 5.0f, 6.0f, 7.0f, 8.0f);
        ByteBuffer buffer = writer.buffer();

        assertEquals(1.0f, buffer.getFloat(0));
        assertEquals(4.0f, buffer.getFloat(12));
        assertEquals(5.0f, buffer.getFloat(16));
        assertEquals(8.0f, buffer.getFloat(28));
    }

    @Test
    void rebuildsStd430LayoutFromGeneratedMetadata() {
        ShaderUniformBindings.Block block = ShaderUniformBindings.block("UIBlurComputeParams");

        assertEquals(32, block.storageLayout().size());
        assertEquals(16, block.storageLayout().member("region").offset());
        assertThrows(IllegalArgumentException.class, () -> block.writer().vec4("missing", 0, 0, 0, 0));
    }
}
