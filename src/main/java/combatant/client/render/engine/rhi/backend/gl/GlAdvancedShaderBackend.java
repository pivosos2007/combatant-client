/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import combatant.client.render.engine.rhi.RhiCapabilities;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.AdvancedShaderBackend;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.PatchDrawCommand;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiPatchPipeline;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.shader.CombatantShaderSources;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;

import java.nio.ByteBuffer;
import java.util.EnumSet;

/**
 * Combatant-owned native OpenGL compute/SSBO backend.
 *
 * <p>Blaze3D 26.2 has no compute dispatch or SSBO binding surface. This implementation therefore
 * owns only the missing advanced-shader state. Program binding goes through {@link GlStateManager}
 * so Mojang's program cache stays coherent; ordinary graphics pipelines remain Blaze3D-owned.</p>
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
    public RhiComputePipeline createComputePipeline(String label, Identifier shader) {
        requireOpen();
        if (!RhiCapabilities.current().nativeComputeSubmission()) {
            throw new UnsupportedOperationException("Native OpenGL compute backend is unavailable");
        }
        if (shader == null) throw new IllegalArgumentException("shader");

        String source = CombatantShaderSources.loadNativeStage(
                Minecraft.getInstance().getResourceManager(), shader, ".comp");
        int computeShader = GL20C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
        int program = 0;
        try {
            GL20C.glShaderSource(computeShader, source);
            GL20C.glCompileShader(computeShader);
            if (GL20C.glGetShaderi(computeShader, GL20C.GL_COMPILE_STATUS) == 0) {
                throw new IllegalStateException("Compute shader compile failed for " + shader + ":\n"
                        + GL20C.glGetShaderInfoLog(computeShader));
            }

            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, computeShader);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("Compute program link failed for " + shader + ":\n"
                        + GL20C.glGetProgramInfoLog(program));
            }
            GL20C.glDetachShader(program, computeShader);
            return new GlComputePipeline(label, shader, program);
        } catch (RuntimeException | Error t) {
            if (program != 0) GL20C.glDeleteProgram(program);
            throw t;
        } finally {
            GL20C.glDeleteShader(computeShader);
        }
    }

    @Override
    public RhiPatchPipeline createPatchPipeline(String label,
                                                Identifier vertexShader,
                                                Identifier tessControlShader,
                                                Identifier tessEvaluationShader,
                                                Identifier geometryShader,
                                                Identifier fragmentShader,
                                                int controlPoints) {
        throw new UnsupportedOperationException(
                "OpenGL tessellation pipeline lowering is not implemented yet; CPU/path fallback remains authoritative");
    }

    @Override
    public void dispatch(ComputeDispatchCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("command");
        if (!(command.pipeline() instanceof GlComputePipeline pipeline) || pipeline.closed) {
            throw new IllegalArgumentException("Compute pipeline does not belong to the active OpenGL backend");
        }
        if (!RhiCapabilities.current().nativeComputeSubmission()) {
            throw new UnsupportedOperationException("Native OpenGL compute backend is unavailable");
        }

        int bound = 0;
        try {
            GlStateManager._glUseProgram(pipeline.program);
            for (StorageBinding binding : command.storageBindings()) {
                if (!(binding.buffer() instanceof GlStorageBuffer buffer) || buffer.closed) {
                    throw new IllegalArgumentException("Storage binding does not belong to the active OpenGL backend");
                }
                if (binding.size() <= 0L) continue;
                GL30C.glBindBufferRange(
                        GL43C.GL_SHADER_STORAGE_BUFFER,
                        binding.binding(),
                        buffer.id,
                        binding.offset(),
                        binding.size());
                bound++;
            }
            GL43C.glDispatchCompute(command.groupsX(), command.groupsY(), command.groupsZ());
            stats.computeDispatch(command.groupsX(), command.groupsY(), command.groupsZ(), bound);
        } finally {
            for (StorageBinding binding : command.storageBindings()) {
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, binding.binding(), 0);
            }
            // Keep Mojang's program cache authoritative rather than leaving an invisible native program bound.
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

    private static int memoryBarrierBits(RhiResourceBarrier barrier) {
        int bits = GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
        switch (barrier.destinationStage()) {
            case INDIRECT -> bits |= GL42C.GL_COMMAND_BARRIER_BIT;
            case TRANSFER -> bits |= GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
            case GRAPHICS -> bits |= GL42C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT
                    | GL42C.GL_ELEMENT_ARRAY_BARRIER_BIT
                    | GL42C.GL_UNIFORM_BARRIER_BIT
                    | GL42C.GL_TEXTURE_FETCH_BARRIER_BIT;
            case COMPUTE -> {
                // SSBO visibility bit above is sufficient for compute-to-compute storage dependencies.
            }
        }
        return bits;
    }

    @Override
    public void drawPatches(PatchDrawCommand command) {
        throw new UnsupportedOperationException(
                "OpenGL tessellation patch submission is not implemented yet; CPU/path fallback remains authoritative");
    }

    @Override
    public void close() {
        closed = true;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("OpenGL advanced shader backend is closed");
    }

    private static final class GlComputePipeline implements RhiComputePipeline {
        private final String label;
        private final Identifier shader;
        private final int program;
        private boolean closed;

        private GlComputePipeline(String label, Identifier shader, int program) {
            this.label = label == null || label.isBlank() ? shader.toString() : label;
            this.shader = shader;
            this.program = program;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            GL20C.glDeleteProgram(program);
        }

        @Override
        public String toString() {
            return "GlComputePipeline{" + label + ", shader=" + shader + '}';
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

        @Override
        public StorageBufferDescriptor descriptor() {
            return descriptor;
        }

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

            if (dsa) {
                ARBDirectStateAccess.glNamedBufferSubData(id, destinationOffset, data);
            } else {
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
