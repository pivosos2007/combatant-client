/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.FrameBufferAttachment;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlTextureView;
import combatant.client.mixininterface.IGlBackendInfo;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.RhiCapabilities;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.shader.CombatantShaderSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;

/**
 * Combatant-owned native OpenGL compute/SSBO/tessellation backend.
 *
 * <p>Only stages Blaze3D 26.2 cannot express are owned here. Mojang remains authoritative for
 * device capability selection, framebuffer/VAO caches and ordinary graphics pipelines.</p>
 */
public final class GlAdvancedShaderBackend implements AdvancedShaderBackend {
    private final RhiStats stats;
    private volatile boolean closed;

    public GlAdvancedShaderBackend(RhiStats stats) {
        this.stats = stats;
    }

    @Override
    public EnumSet<RhiShaderStage> stages() {
        EnumSet<RhiShaderStage> result = EnumSet.of(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT);
        RhiCapabilities capabilities = RhiCapabilities.current();
        if (capabilities.nativeComputeSubmission()) result.add(RhiShaderStage.COMPUTE);
        if (capabilities.nativeTessellationSubmission()) {
            result.add(RhiShaderStage.TESS_CONTROL);
            result.add(RhiShaderStage.TESS_EVALUATION);
        }
        if (capabilities.nativeGeometrySubmission()) result.add(RhiShaderStage.GEOMETRY);
        return result;
    }

    @Override
    public RhiStorageBuffer createStorageBuffer(StorageBufferDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        RhiCapabilities capabilities = RhiCapabilities.current();
        if (!capabilities.shaderStorageBuffers() || !capabilities.nativeComputeSubmission()) {
            throw new UnsupportedOperationException("Native OpenGL SSBO backend is unavailable");
        }
        return new GlStorageBuffer(descriptor, capabilities.nativeDirectStateAccess(), stats);
    }

    @Override
    public RhiStorageImage createStorageImage(StorageImageDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        RhiCapabilities caps = RhiCapabilities.current();
        if (!caps.imageLoadStore()) {
            throw new UnsupportedOperationException("Native OpenGL image load/store is unavailable");
        }
        validateStorageImageFormat(descriptor);
        return RhiStorageImages.create(descriptor);
    }

    @Override
    public RhiComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        if (!RhiCapabilities.current().nativeComputeSubmission()) {
            throw new UnsupportedOperationException("Native OpenGL compute backend is unavailable");
        }
        validateResourceLayout(descriptor.resources(), "OpenGL compute");

        String source = CombatantShaderSources.loadNativeStage(
                Minecraft.getInstance().getResourceManager(), descriptor.shader(), CombatantShaderSources.COMPUTE_EXTENSION);
        int computeShader = compileStage(GL43C.GL_COMPUTE_SHADER, source, descriptor.shader());
        int program = 0;
        try {
            program = linkProgram(descriptor.label(), computeShader);
            return new GlComputePipeline(descriptor, program);
        } catch (RuntimeException | Error t) {
            if (program != 0) GL20C.glDeleteProgram(program);
            throw t;
        } finally {
            GL20C.glDeleteShader(computeShader);
        }
    }

    @Override
    public RhiPatchPipeline createPatchPipeline(PatchPipelineDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        RhiCapabilities caps = RhiCapabilities.current();
        if (!caps.nativeTessellationSubmission()) {
            throw new UnsupportedOperationException("Native OpenGL tessellation backend is unavailable");
        }
        if (descriptor.geometryShader() != null && !caps.nativeGeometrySubmission()) {
            throw new UnsupportedOperationException("Patch pipeline requests geometry stage but it is unsupported");
        }
        validateResourceLayout(descriptor.resources(), "OpenGL patch");

        var rm = Minecraft.getInstance().getResourceManager();
        int vs = compileStage(GL20C.GL_VERTEX_SHADER,
                CombatantShaderSources.load(rm, descriptor.vertexShader(), com.mojang.blaze3d.shaders.ShaderType.VERTEX),
                descriptor.vertexShader());
        int tcs = compileStage(GL40C.GL_TESS_CONTROL_SHADER,
                CombatantShaderSources.loadNativeStage(rm, descriptor.tessControlShader(), CombatantShaderSources.TESS_CONTROL_EXTENSION),
                descriptor.tessControlShader());
        int tes = compileStage(GL40C.GL_TESS_EVALUATION_SHADER,
                CombatantShaderSources.loadNativeStage(rm, descriptor.tessEvaluationShader(), CombatantShaderSources.TESS_EVALUATION_EXTENSION),
                descriptor.tessEvaluationShader());
        int gs = 0;
        int fs = 0;
        int program = 0;
        try {
            if (descriptor.geometryShader() != null) {
                gs = compileStage(GL32C.GL_GEOMETRY_SHADER,
                        CombatantShaderSources.loadNativeStage(rm, descriptor.geometryShader(), CombatantShaderSources.GEOMETRY_EXTENSION),
                        descriptor.geometryShader());
            }
            fs = compileStage(GL20C.GL_FRAGMENT_SHADER,
                    CombatantShaderSources.load(rm, descriptor.fragmentShader(), com.mojang.blaze3d.shaders.ShaderType.FRAGMENT),
                    descriptor.fragmentShader());
            program = linkProgram(descriptor.label(), vs, tcs, tes, gs, fs);
            return new GlPatchPipeline(descriptor, program);
        } catch (RuntimeException | Error t) {
            if (program != 0) GL20C.glDeleteProgram(program);
            throw t;
        } finally {
            GL20C.glDeleteShader(vs);
            GL20C.glDeleteShader(tcs);
            GL20C.glDeleteShader(tes);
            if (gs != 0) GL20C.glDeleteShader(gs);
            if (fs != 0) GL20C.glDeleteShader(fs);
        }
    }

    @Override
    public void dispatch(ComputeDispatchCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("command");
        if (!(command.pipeline() instanceof GlComputePipeline pipeline) || pipeline.closed) {
            throw new IllegalArgumentException("Compute pipeline does not belong to the active OpenGL backend");
        }
        validateBindings(pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages());

        int bound = 0;
        try {
            GlStateManager._glUseProgram(pipeline.program);
            bound = bindStorage(command.storageBindings());
            bindSampledTextures(command.sampledTextures());
            bindStorageImages(command.storageImages());
            GL43C.glDispatchCompute(command.groupsX(), command.groupsY(), command.groupsZ());
            stats.computeDispatch(command.groupsX(), command.groupsY(), command.groupsZ(), bound);
        } finally {
            unbindStorage(command.storageBindings());
            unbindSampledTextures(command.sampledTextures());
            unbindStorageImages(command.storageImages());
            GlStateManager._glUseProgram(0);
        }
    }

    @Override
    public void barrier(RhiResourceBarrier barrier) {
        requireOpen();
        if (barrier == null) throw new IllegalArgumentException("barrier");
        int bits = memoryBarrierBits(barrier);
        if (bits == 0) return;
        GL42C.glMemoryBarrier(bits);
        stats.advancedShaderBarrier();
    }

    @Override
    public void drawPatches(PatchDrawCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("command");
        if (!(command.pipeline() instanceof GlPatchPipeline pipeline) || pipeline.closed) {
            throw new IllegalArgumentException("Patch pipeline does not belong to the active OpenGL backend");
        }
        if (!(command.colorAttachment() instanceof GlTextureView color) || color.isClosed()) {
            throw new IllegalArgumentException("OpenGL patch target must be a live GlTextureView");
        }
        GlTextureView depth = null;
        if (command.depthAttachment() != null) {
            if (!(command.depthAttachment() instanceof GlTextureView glDepth) || glDepth.isClosed()) {
                throw new IllegalArgumentException("OpenGL patch depth target must be a live GlTextureView");
            }
            depth = glDepth;
        }
        validateBindings(pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages());

        GpuMeshHandle mesh = command.mesh();
        mesh.validateForDraw(command.label());
        if (!(mesh.vertexBuffer() instanceof GlBuffer vertex) || !(mesh.indexBuffer() instanceof GlBuffer index)) {
            throw new IllegalArgumentException("Patch mesh is not backed by Mojang GlBuffer objects");
        }
        var format = pipeline.descriptor.vertexLayout().nativeFormat();
        if (mesh.vertexStride() > 0 && mesh.vertexStride() != format.getVertexSize()) {
            throw new IllegalArgumentException("Patch mesh stride " + mesh.vertexStride()
                    + " does not match pipeline vertex format stride " + format.getVertexSize());
        }

        IGlBackendInfo backend = GlBackendAccess.current();
        if (backend == null) throw new IllegalStateException("Mojang OpenGL backend is unavailable");
        int fbo = backend.combatant$frameBufferCache().getFbo(
                backend.combatant$directStateAccess(),
                List.<FrameBufferAttachment>of(color),
                depth);

        try {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, color.getWidth(0), color.getHeight(0));
            applyRasterState(pipeline.descriptor);
            GlStateManager._glUseProgram(pipeline.program);
            bindStorage(command.storageBindings());
            bindSampledTextures(command.sampledTextures());
            bindStorageImages(command.storageImages());

            // Match the ordinary RHI path: bind the arena buffer at byte offset zero and use
            // baseVertex for the mesh range. Binding vertexOffsetBytes here as well would double-apply it.
            GpuBufferSlice vertexSlice = new GpuBufferSlice(vertex, 0L, vertex.size());
            backend.combatant$vertexArrayCache().bindVertexArray(
                    new com.mojang.blaze3d.vertex.VertexFormat[]{format},
                    new GpuBufferSlice[]{vertexSlice},
                    null);
            GlStateManager._glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, index.handle());
            GL40C.glPatchParameteri(GL40C.GL_PATCH_VERTICES, pipeline.controlPoints());
            GL32C.glDrawElementsBaseVertex(
                    GL40C.GL_PATCHES,
                    mesh.indexCount(),
                    glIndexType(mesh.indexType()),
                    (long) mesh.firstIndex() * mesh.indexType().bytes,
                    mesh.baseVertex());
            stats.drawCall();
        } finally {
            unbindStorage(command.storageBindings());
            unbindSampledTextures(command.sampledTextures());
            unbindStorageImages(command.storageImages());
            GlStateManager._glUseProgram(0);
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("OpenGL advanced shader backend is closed");
    }

    private static int compileStage(int type, String source, Identifier id) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == 0) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("Native shader compile failed for " + id + ":\n" + log);
        }
        return shader;
    }

    private static int linkProgram(String label, int... stages) {
        int program = GL20C.glCreateProgram();
        for (int stage : stages) if (stage != 0) GL20C.glAttachShader(program, stage);
        GL20C.glLinkProgram(program);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) {
            String log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            throw new IllegalStateException("Native program link failed for " + label + ":\n" + log);
        }
        for (int stage : stages) if (stage != 0) GL20C.glDetachShader(program, stage);
        return program;
    }

    private static void validateResourceLayout(ShaderResourceLayout layout, String owner) {
        RhiCapabilities caps = RhiCapabilities.current();
        for (ShaderResourceSlot slot : layout.slots()) {
            if (slot.kind() == ShaderResourceKind.STORAGE_IMAGE && !caps.imageLoadStore()) {
                throw new UnsupportedOperationException(owner + " requires OpenGL image load/store support");
            }
        }
    }

    private static void validateBindings(ShaderResourceLayout layout,
                                         List<StorageBinding> buffers,
                                         List<SampledTextureBinding> sampled,
                                         List<StorageImageBinding> images) {
        for (StorageBinding binding : buffers) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.STORAGE_BUFFER) {
                throw new IllegalArgumentException("No STORAGE_BUFFER slot declared at binding " + binding.binding());
            }
            validateAccess(slot, binding.access(), binding.binding());
        }
        for (SampledTextureBinding binding : sampled) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.SAMPLED_TEXTURE) {
                throw new IllegalArgumentException("No SAMPLED_TEXTURE slot declared at binding " + binding.binding());
            }
        }
        for (StorageImageBinding binding : images) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.STORAGE_IMAGE) {
                throw new IllegalArgumentException("No STORAGE_IMAGE slot declared at binding " + binding.binding());
            }
            validateAccess(slot, binding.access(), binding.binding());
        }
        for (ShaderResourceSlot slot : layout.slots()) {
            boolean present = switch (slot.kind()) {
                case STORAGE_BUFFER -> buffers.stream().anyMatch(b -> b.binding() == slot.binding());
                case SAMPLED_TEXTURE -> sampled.stream().anyMatch(b -> b.binding() == slot.binding());
                case STORAGE_IMAGE -> images.stream().anyMatch(b -> b.binding() == slot.binding());
            };
            if (!present) throw new IllegalArgumentException("Missing " + slot.kind() + " binding " + slot.binding());
        }
    }

    private static void validateAccess(ShaderResourceSlot slot, StorageAccess actual, int binding) {
        if (!slot.access().allows(actual)) {
            throw new IllegalArgumentException("Binding " + binding + " access " + actual
                    + " exceeds declared access " + slot.access());
        }
    }

    private static int bindStorage(List<StorageBinding> bindings) {
        int count = 0;
        for (StorageBinding binding : bindings) {
            if (!(binding.buffer() instanceof GlStorageBuffer buffer) || buffer.closed) {
                throw new IllegalArgumentException("Storage binding does not belong to the active OpenGL backend");
            }
            if (binding.size() <= 0L) continue;
            GL30C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER,
                    binding.binding(), buffer.id, binding.offset(), binding.size());
            count++;
        }
        return count;
    }

    private static void unbindStorage(List<StorageBinding> bindings) {
        for (StorageBinding binding : bindings) {
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, binding.binding(), 0);
        }
    }

    private static void bindSampledTextures(List<SampledTextureBinding> bindings) {
        for (SampledTextureBinding binding : bindings) {
            if (!(binding.texture() instanceof GlTextureView texture) || texture.isClosed()) {
                throw new IllegalArgumentException("Sampled texture does not belong to the active OpenGL backend");
            }
            if (!(binding.sampler() instanceof GlSampler sampler) || sampler.isClosed()) {
                throw new IllegalArgumentException("Sampler does not belong to the active OpenGL backend");
            }
            GlStateManager._activeTexture(GlConst.GL_TEXTURE0 + binding.binding());
            GlStateManager._bindTexture(texture.glId());
            GL33C.glBindSampler(binding.binding(), sampler.getId());
        }
    }

    private static void unbindSampledTextures(List<SampledTextureBinding> bindings) {
        for (SampledTextureBinding binding : bindings) {
            GL33C.glBindSampler(binding.binding(), 0);
            GlStateManager._activeTexture(GlConst.GL_TEXTURE0 + binding.binding());
            GlStateManager._bindTexture(0);
        }
        if (!bindings.isEmpty()) GlStateManager._activeTexture(GlConst.GL_TEXTURE0);
    }

    private static void bindStorageImages(List<StorageImageBinding> bindings) {
        for (StorageImageBinding binding : bindings) {
            if (!(binding.image().view() instanceof GlTextureView texture) || texture.isClosed()) {
                throw new IllegalArgumentException("Storage image does not belong to the active OpenGL backend");
            }
            int format = GlConst.toGlInternalId(binding.image().descriptor().format());
            GL42C.glBindImageTexture(binding.binding(), texture.glId(), 0, false, 0,
                    glImageAccess(binding.access()), format);
        }
    }

    private static void unbindStorageImages(List<StorageImageBinding> bindings) {
        for (StorageImageBinding binding : bindings) {
            GL42C.glBindImageTexture(binding.binding(), 0, 0, false, 0, GlConst.GL_READ_ONLY, GlConst.GL_RGBA8);
        }
    }

    private static int glImageAccess(StorageAccess access) {
        return switch (access) {
            case READ_ONLY -> GlConst.GL_READ_ONLY;
            case WRITE_ONLY -> GlConst.GL_WRITE_ONLY;
            case READ_WRITE -> GlConst.GL_READ_WRITE;
        };
    }

    private static void validateStorageImageFormat(StorageImageDescriptor descriptor) {
        if (!descriptor.format().hasColorAspect() || descriptor.format().componentCount() == 3) {
            throw new UnsupportedOperationException("OpenGL storage image format is not supported by the conservative RHI contract: "
                    + descriptor.format());
        }
        if (GlConst.toGlInternalId(descriptor.format()) == 0) {
            throw new UnsupportedOperationException("OpenGL has no sized internal format for " + descriptor.format());
        }
    }

    private static int memoryBarrierBits(RhiResourceBarrier barrier) {
        int bits = GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
        if (!barrier.images().isEmpty()) bits |= GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
        switch (barrier.destinationStage()) {
            case INDIRECT -> bits |= GL42C.GL_COMMAND_BARRIER_BIT;
            case TRANSFER -> bits |= GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
            case GRAPHICS -> bits |= GL42C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT
                    | GL42C.GL_ELEMENT_ARRAY_BARRIER_BIT
                    | GL42C.GL_UNIFORM_BARRIER_BIT
                    | GL42C.GL_TEXTURE_FETCH_BARRIER_BIT;
            case COMPUTE -> { }
        }
        return bits;
    }

    private static int glIndexType(IndexType type) {
        return type == IndexType.SHORT ? GL11C.GL_UNSIGNED_SHORT : GL11C.GL_UNSIGNED_INT;
    }

    private static void applyRasterState(PatchPipelineDescriptor descriptor) {
        switch (descriptor.depthMode()) {
            case DISABLED -> {
                GlStateManager._disableDepthTest();
                GlStateManager._depthMask(false);
            }
            case READ_ONLY_LEQUAL -> {
                GlStateManager._enableDepthTest();
                GlStateManager._depthMask(false);
                GL11C.glDepthFunc(GL11C.GL_LEQUAL);
            }
            case READ_WRITE_LEQUAL -> {
                GlStateManager._enableDepthTest();
                GlStateManager._depthMask(true);
                GL11C.glDepthFunc(GL11C.GL_LEQUAL);
            }
        }

        switch (descriptor.blendMode()) {
            case OPAQUE -> GlStateManager._disableBlend(0);
            case ALPHA -> {
                GlStateManager._enableBlend(0);
                GlStateManager._blendFuncSeparate(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA,
                        GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA);
            }
            case PREMULTIPLIED_ALPHA -> {
                GlStateManager._enableBlend(0);
                GlStateManager._blendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA,
                        GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA);
            }
            case ADDITIVE -> {
                GlStateManager._enableBlend(0);
                GlStateManager._blendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ONE, GL11C.GL_ONE, GL11C.GL_ONE);
            }
        }

        switch (descriptor.cullMode()) {
            case NONE -> GlStateManager._disableCull();
            case BACK -> {
                GlStateManager._enableCull();
                GL11C.glCullFace(GL11C.GL_BACK);
            }
            case FRONT -> {
                GlStateManager._enableCull();
                GL11C.glCullFace(GL11C.GL_FRONT);
            }
        }
    }

    private static final class GlComputePipeline implements RhiComputePipeline {
        private final ComputePipelineDescriptor descriptor;
        private final int program;
        private boolean closed;

        private GlComputePipeline(ComputePipelineDescriptor descriptor, int program) {
            this.descriptor = descriptor;
            this.program = program;
        }

        @Override public String label() { return descriptor.label(); }
        @Override public ShaderResourceLayout resources() { return descriptor.resources(); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            GL20C.glDeleteProgram(program);
        }
    }

    private static final class GlPatchPipeline implements RhiPatchPipeline {
        private final PatchPipelineDescriptor descriptor;
        private final int program;
        private boolean closed;

        private GlPatchPipeline(PatchPipelineDescriptor descriptor, int program) {
            this.descriptor = descriptor;
            this.program = program;
        }

        @Override public String label() { return descriptor.label(); }
        @Override public int controlPoints() { return descriptor.controlPoints(); }
        @Override public ShaderResourceLayout resources() { return descriptor.resources(); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            GL20C.glDeleteProgram(program);
        }
    }

    private static final class GlStorageBuffer implements RhiStorageBuffer {
        private final StorageBufferDescriptor descriptor;
        private final boolean dsa;
        private final RhiStats stats;
        private final int id;
        private boolean closed;

        private GlStorageBuffer(StorageBufferDescriptor descriptor, boolean dsa, RhiStats stats) {
            this.descriptor = descriptor;
            this.dsa = dsa;
            this.stats = stats;
            long bytes = descriptor.byteSize();
            if (bytes <= 0L) throw new IllegalArgumentException("Storage buffer byte size must be positive");

            if (dsa) {
                id = ARBDirectStateAccess.glCreateBuffers();
                ARBDirectStateAccess.glNamedBufferData(id, bytes, GL15C.GL_DYNAMIC_DRAW);
            } else {
                id = GL15C.glGenBuffers();
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, id);
                GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, bytes, GL15C.GL_DYNAMIC_DRAW);
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
            }
        }

        @Override public StorageBufferDescriptor descriptor() { return descriptor; }

        @Override
        public void upload(ByteBuffer source, long destinationOffset) {
            if (closed) throw new IllegalStateException("Storage buffer is closed: " + descriptor.label());
            if (source == null) throw new IllegalArgumentException("source");
            if (destinationOffset < 0L) throw new IllegalArgumentException("destinationOffset");
            ByteBuffer data = source.duplicate();
            long bytes = data.remaining();
            if (destinationOffset + bytes > descriptor.byteSize()) {
                throw new IndexOutOfBoundsException("SSBO upload exceeds " + descriptor.label()
                        + ": offset=" + destinationOffset + " bytes=" + bytes
                        + " capacity=" + descriptor.byteSize());
            }
            if (bytes == 0L) return;

            if (dsa) ARBDirectStateAccess.glNamedBufferSubData(id, destinationOffset, data);
            else {
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, id);
                GL15C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, destinationOffset, data);
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
            }
            stats.storageBufferUpload(bytes);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            GL15C.glDeleteBuffers(id);
        }
    }
}
