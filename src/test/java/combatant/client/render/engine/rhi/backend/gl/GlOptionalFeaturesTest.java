/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.gl;

import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlOptionalFeaturesTest {
    @Test
    void baselineRasterAndTransferBarriersDoNotRequireNewerGlFunctions() throws Exception {
        var caps = GlTestCapabilities.create(Set.of("OpenGL33"), Set.of("glMemoryBarrier"));
        assertFalse(GlAdvancedShaderBackend.requiresMemoryBarrier(
                barrier(RhiResourceBarrier.Stage.GRAPHICS, RhiResourceBarrier.Stage.GRAPHICS), caps));
        assertFalse(GlAdvancedShaderBackend.requiresMemoryBarrier(
                barrier(RhiResourceBarrier.Stage.TRANSFER, RhiResourceBarrier.Stage.GRAPHICS), caps));
        assertThrows(UnsupportedOperationException.class, () -> GlAdvancedShaderBackend.requiresMemoryBarrier(
                barrier(RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Stage.GRAPHICS), caps));
        assertThrows(UnsupportedOperationException.class, () -> GlAdvancedShaderBackend.requiresMemoryBarrier(
                barrier(RhiResourceBarrier.Stage.GRAPHICS, RhiResourceBarrier.Stage.COMPUTE), caps));
    }

    @Test
    void imageLoadStoreExtensionCanSupplyBarriersWithoutCore42() throws Exception {
        var caps = GlTestCapabilities.create(Set.of("OpenGL33", "GL_ARB_shader_image_load_store"), Set.of());
        assertFalse(caps.OpenGL42);
        assertTrue(GlAdvancedShaderBackend.requiresMemoryBarrier(
                barrier(RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Stage.GRAPHICS), caps));
        var missingBarrier = GlTestCapabilities.create(
                Set.of("OpenGL33", "GL_ARB_shader_storage_buffer_object"), Set.of("glMemoryBarrier"));
        assertThrows(UnsupportedOperationException.class, () -> GlAdvancedShaderBackend.requiresMemoryBarrier(
                barrier(RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Stage.GRAPHICS), missingBarrier));
    }

    @Test
    void barriersDoNotIncludeSsboBitsWithoutSsboSupport() throws Exception {
        var core42 = GlTestCapabilities.create(Set.of("OpenGL42"), Set.of());
        var core43 = GlTestCapabilities.create(Set.of("OpenGL43"), Set.of());
        var extended42 = GlTestCapabilities.create(Set.of("OpenGL42", "GL_ARB_shader_storage_buffer_object"), Set.of());
        var graphics = barrier(RhiResourceBarrier.Stage.GRAPHICS, RhiResourceBarrier.Stage.GRAPHICS);
        int bits42 = GlAdvancedShaderBackend.memoryBarrierBits(graphics, core42);
        assertEquals(0, bits42 & GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        assertNotEquals(0, bits42 & GL42C.GL_TEXTURE_FETCH_BARRIER_BIT);
        assertNotEquals(0, GlAdvancedShaderBackend.memoryBarrierBits(graphics, core43) & GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        assertNotEquals(0, GlAdvancedShaderBackend.memoryBarrierBits(graphics, extended42) & GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        assertEquals(GL42C.GL_ALL_BARRIER_BITS, GlAdvancedShaderBackend.memoryBarrierBits(
                barrier(RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Stage.ALL), core42));
    }

    @Test
    void volumeQueriesRequireBothImmutableStorageAndQuery2() throws Exception {
        assertFalse(GlStorageVolume.allocationSupport(
                GlTestCapabilities.create(Set.of("OpenGL33"), Set.of())).supported());
        assertFalse(GlStorageVolume.allocationSupport(
                GlTestCapabilities.create(Set.of("OpenGL42"), Set.of())).supported());
        assertFalse(GlStorageVolume.allocationSupport(
                GlTestCapabilities.create(Set.of("GL_ARB_internalformat_query2"), Set.of())).supported());
        assertFalse(GlStorageVolume.allocationSupport(
                GlTestCapabilities.create(Set.of("GL_ARB_texture_storage"), Set.of())).supported());
        var extensions = Set.of("GL_ARB_texture_storage", "GL_ARB_internalformat_query", "GL_ARB_internalformat_query2");
        assertTrue(GlStorageVolume.allocationSupport(GlTestCapabilities.create(extensions, Set.of())).supported());
        assertFalse(GlStorageVolume.allocationSupport(
                GlTestCapabilities.create(extensions, Set.of("glTexStorage3D"))).supported());
        assertFalse(GlStorageVolume.allocationSupport(
                GlTestCapabilities.create(extensions, Set.of("glGetInternalformativ"))).supported());
        assertTrue(GlStorageVolume.allocationSupport(
                GlTestCapabilities.create(Set.of("OpenGL43"), Set.of())).supported());
    }

    private static RhiResourceBarrier barrier(RhiResourceBarrier.Stage source, RhiResourceBarrier.Stage destination) {
        return new RhiResourceBarrier(source, RhiResourceBarrier.Access.WRITE,
                destination, RhiResourceBarrier.Access.READ, List.of(), List.of());
    }
}
