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
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
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
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    public RhiStorageVolume createStorageVolume(StorageVolumeDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        if (descriptor.storage() && !RhiCapabilities.current().imageLoadStore()) {
            throw new UnsupportedOperationException("Native OpenGL 3D image load/store is unavailable");
        }
        RhiVolumeCapabilities capabilities = queryStorageVolumeCapabilities(descriptor);
        if (!capabilities.supportsDescriptor(descriptor)) {
            throw new UnsupportedOperationException(volumeCapabilityFailure(capabilities, descriptor));
        }
        return new GlStorageVolume(descriptor, stats);
    }

    @Override
    public RhiVolumeCapabilities queryStorageVolumeCapabilities(StorageVolumeDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        if (descriptor.storage() && !RhiCapabilities.current().imageLoadStore()) {
            return RhiVolumeCapabilities.unsupported("Native OpenGL 3D image load/store is unavailable");
        }
        return GlStorageVolume.capabilities(descriptor);
    }

    @Override
    public void copyStorageVolume(VolumeCopyCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("command");
        command.source().requireValid();
        command.destination().requireValid();
        if (!(command.source().volume() instanceof GlStorageVolume source)
                || !(command.destination().volume() instanceof GlStorageVolume destination)) {
            throw new RhiResourceOwnershipException("Volume copy resources do not belong to the active OpenGL backend");
        }
        RhiVolumeCapabilities sourceCaps = queryStorageVolumeCapabilities(source.descriptor());
        RhiVolumeCapabilities destinationCaps = queryStorageVolumeCapabilities(destination.descriptor());
        if (!sourceCaps.copy().supported() || !destinationCaps.copy().supported()) {
            String reason = !sourceCaps.copy().supported() ? sourceCaps.copy().reason() : destinationCaps.copy().reason();
            throw new UnsupportedOperationException("OpenGL 3D volume copy is unavailable: " + reason);
        }
        if (GL.getCapabilities().OpenGL43) {
            GL43C.glCopyImageSubData(
                    source.id, GL12C.GL_TEXTURE_3D, command.source().baseMipLevel(),
                    command.sourceX(), command.sourceY(), command.sourceZ(),
                    destination.id, GL12C.GL_TEXTURE_3D, command.destination().baseMipLevel(),
                    command.destinationX(), command.destinationY(), command.destinationZ(),
                    command.width(), command.height(), command.depth());
        } else {
            ARBCopyImage.glCopyImageSubData(
                    source.id, GL12C.GL_TEXTURE_3D, command.source().baseMipLevel(),
                    command.sourceX(), command.sourceY(), command.sourceZ(),
                    destination.id, GL12C.GL_TEXTURE_3D, command.destination().baseMipLevel(),
                    command.destinationX(), command.destinationY(), command.destinationZ(),
                    command.width(), command.height(), command.depth());
        }
        stats.storageVolumeCopy(command.width(), command.height(), command.depth(), source.descriptor().format().blockSize());
    }

    @Override
    public void clearStorageVolume(VolumeClearCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("command");
        command.target().requireValid();
        if (!(command.target().volume() instanceof GlStorageVolume volume)) {
            throw new RhiResourceOwnershipException("Volume clear target does not belong to the active OpenGL backend");
        }
        RhiFeatureSupport clear = queryStorageVolumeCapabilities(volume.descriptor()).clear();
        if (!clear.supported()) {
            throw new UnsupportedOperationException("OpenGL 3D volume clear is unavailable: " + clear.reason());
        }
        validateClearKind(volume.descriptor().format(), command.value());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(16);
            int glType;
            switch (command.value().kind()) {
                case FLOAT -> {
                    data.putFloat(command.value().floatX()).putFloat(command.value().floatY())
                            .putFloat(command.value().floatZ()).putFloat(command.value().floatW());
                    glType = GL11C.GL_FLOAT;
                }
                case SIGNED_INT -> {
                    data.putInt(command.value().x()).putInt(command.value().y())
                            .putInt(command.value().z()).putInt(command.value().w());
                    glType = GL11C.GL_INT;
                }
                case UNSIGNED_INT -> {
                    data.putInt(command.value().x()).putInt(command.value().y())
                            .putInt(command.value().z()).putInt(command.value().w());
                    glType = GL11C.GL_UNSIGNED_INT;
                }
                default -> throw new IllegalStateException("Unexpected clear kind");
            }
            data.flip();
            int externalFormat = GlConst.toGlExternalId(volume.descriptor().format());
            RhiVolumeSubresource range = command.target();
            for (int i = 0; i < range.mipLevels(); i++) {
                int mip = range.baseMipLevel() + i;
                if (GL.getCapabilities().OpenGL44) {
                    GL44C.glClearTexSubImage(volume.id, mip, 0, 0, 0,
                            volume.descriptor().mipWidth(mip), volume.descriptor().mipHeight(mip),
                            volume.descriptor().mipDepth(mip), externalFormat, glType, data);
                } else {
                    ARBClearTexture.glClearTexSubImage(volume.id, mip, 0, 0, 0,
                            volume.descriptor().mipWidth(mip), volume.descriptor().mipHeight(mip),
                            volume.descriptor().mipDepth(mip), externalFormat, glType, data);
                }
            }
        }
        stats.storageVolumeClear(command.target().mipLevels());
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
        validateBindings(pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages(), command.storageVolumes(), command.sampledVolumes());

        validateImageUnitBindings(command.storageImages(), command.storageVolumes());
        validateSampledBindings(command.sampledTextures(), command.sampledVolumes());
        SampledTextureState sampledTextureState = captureSampledTextureState(command.sampledTextures());
        SampledVolumeState sampledVolumeState = captureSampledVolumeState(command.sampledVolumes());
        List<ImageUnitState> imageUnitState = captureImageUnitState(command.storageImages(), command.storageVolumes());
        int bound = 0;
        try {
            GlStateManager._glUseProgram(pipeline.program);
            bound = bindStorage(command.storageBindings());
            bindSampledTextures(command.sampledTextures());
            bindSampledVolumes(command.sampledVolumes());
            bindStorageImages(command.storageImages());
            bindStorageVolumes(command.storageVolumes());
            GL43C.glDispatchCompute(command.groupsX(), command.groupsY(), command.groupsZ());
            stats.computeDispatch(command.groupsX(), command.groupsY(), command.groupsZ(), bound);
        } finally {
            unbindStorage(command.storageBindings());
            restoreSampledVolumeState(sampledVolumeState);
            restoreSampledTextureState(sampledTextureState);
            restoreImageUnitState(imageUnitState);
            GlNativeStateTracker.restoreBlaze3dProgram();
        }
    }

    @Override
    public void barrier(RhiResourceBarrier barrier) {
        requireOpen();
        if (barrier == null) throw new IllegalArgumentException("barrier");
        validateBarrierResources(barrier);
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
        List<GlTextureView> colors = new ArrayList<>(command.colorAttachments().size());
        for (GpuTextureView view : command.colorAttachments()) {
            if (!(view instanceof GlTextureView color) || color.isClosed()) {
                throw new IllegalArgumentException("OpenGL patch targets must be live GlTextureView objects");
            }
            colors.add(color);
        }
        if (colors.size() != pipeline.descriptor.colorFormats().size()) {
            throw new IllegalArgumentException("Patch color attachment count mismatch: pipeline="
                    + pipeline.descriptor.colorFormats().size() + " draw=" + colors.size());
        }
        GlTextureView color0 = colors.get(0);
        for (int i = 0; i < colors.size(); i++) {
            GlTextureView color = colors.get(i);
            if (color.texture().getFormat() != pipeline.descriptor.colorFormats().get(i)) {
                throw new IllegalArgumentException("Patch color format mismatch at attachment " + i + ": expected="
                        + pipeline.descriptor.colorFormats().get(i) + " actual=" + color.texture().getFormat());
            }
            int colorSamples = color.texture() instanceof IMsaaTexture msaa ? Math.max(1, msaa.combatant$getSamples()) : 1;
            if (colorSamples != pipeline.descriptor.samples()) {
                throw new IllegalArgumentException("Patch color sample mismatch at attachment " + i + ": expected="
                        + pipeline.descriptor.samples() + " actual=" + colorSamples);
            }
            if (color.getWidth(0) != color0.getWidth(0) || color.getHeight(0) != color0.getHeight(0)) {
                throw new IllegalArgumentException("Patch color attachments must have identical dimensions");
            }
        }
        GlTextureView depth = null;
        if (command.depthAttachment() != null) {
            if (!(command.depthAttachment() instanceof GlTextureView glDepth) || glDepth.isClosed()) {
                throw new IllegalArgumentException("OpenGL patch depth target must be a live GlTextureView");
            }
            depth = glDepth;
        }
        if (pipeline.descriptor.depthMode() != AdvancedDepthMode.DISABLED) {
            if (depth == null) throw new IllegalArgumentException("Patch pipeline requires a depth attachment");
            if (depth.texture().getFormat() != pipeline.descriptor.depthFormat()) {
                throw new IllegalArgumentException("Patch depth format mismatch: expected="
                        + pipeline.descriptor.depthFormat() + " actual=" + depth.texture().getFormat());
            }
            int depthSamples = depth.texture() instanceof IMsaaTexture msaa ? Math.max(1, msaa.combatant$getSamples()) : 1;
            if (depthSamples != pipeline.descriptor.samples()) {
                throw new IllegalArgumentException("Patch depth sample mismatch: expected="
                        + pipeline.descriptor.samples() + " actual=" + depthSamples);
            }
            if (depth.getWidth(0) != color0.getWidth(0) || depth.getHeight(0) != color0.getHeight(0)) {
                throw new IllegalArgumentException("Patch color/depth dimensions differ");
            }
        }
        validateBindings(pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages(), List.of(), command.sampledVolumes());

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
                new ArrayList<FrameBufferAttachment>(colors),
                depth);

        validateImageUnitBindings(command.storageImages(), List.of());
        validateSampledBindings(command.sampledTextures(), command.sampledVolumes());
        SampledTextureState sampledTextureState = captureSampledTextureState(command.sampledTextures());
        SampledVolumeState sampledVolumeState = captureSampledVolumeState(command.sampledVolumes());
        List<ImageUnitState> imageUnitState = captureImageUnitState(command.storageImages(), List.of());

        boolean scissorWasEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        try {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, color0.getWidth(0), color0.getHeight(0));
            // Native patch submission bypasses Blaze3D's ordinary render-pass setup. A stale
            // scissor from any prior pass would otherwise clip tessellated world geometry at the
            // framebuffer edge. Patch draws in this backend are full-target draws.
            if (scissorWasEnabled) GlStateManager._disableScissorTest();
            applyRasterState(pipeline.descriptor);
            GlStateManager._glUseProgram(pipeline.program);
            bindStorage(command.storageBindings());
            bindSampledTextures(command.sampledTextures());
            bindSampledVolumes(command.sampledVolumes());
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
            restoreSampledVolumeState(sampledVolumeState);
            restoreSampledTextureState(sampledTextureState);
            restoreImageUnitState(imageUnitState);
            if (scissorWasEnabled) GlStateManager._enableScissorTest();
            GlNativeStateTracker.restoreBlaze3dProgram();
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
            if ((slot.kind() == ShaderResourceKind.STORAGE_IMAGE || slot.kind() == ShaderResourceKind.STORAGE_VOLUME)
                    && !caps.imageLoadStore()) {
                throw new UnsupportedOperationException(owner + " requires OpenGL image load/store support");
            }
        }
    }

    private static void validateBindings(ShaderResourceLayout layout,
                                         List<StorageBinding> buffers,
                                         List<SampledTextureBinding> sampled,
                                         List<StorageImageBinding> images,
                                         List<StorageVolumeBinding> volumes,
                                         List<SampledVolumeBinding> sampledVolumes) {
        validateUniqueBindings(buffers, sampled, images, volumes, sampledVolumes);
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
        for (SampledVolumeBinding binding : sampledVolumes) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.SAMPLED_VOLUME) {
                throw new IllegalArgumentException("No SAMPLED_VOLUME slot declared at binding " + binding.binding());
            }
            binding.view().requireValid();
            validateExpectedFormat(slot, binding.view().volume().descriptor().format(), binding.binding());
        }
        for (StorageImageBinding binding : images) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.STORAGE_IMAGE) {
                throw new IllegalArgumentException("No STORAGE_IMAGE slot declared at binding " + binding.binding());
            }
            validateAccess(slot, binding.access(), binding.binding());
            validateExpectedFormat(slot, binding.image().descriptor().format(), binding.binding());
        }
        for (StorageVolumeBinding binding : volumes) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.STORAGE_VOLUME) {
                throw new IllegalArgumentException("No STORAGE_VOLUME slot declared at binding " + binding.binding());
            }
            validateAccess(slot, binding.access(), binding.binding());
            validateExpectedFormat(slot, binding.volume().descriptor().format(), binding.binding());
        }
        for (ShaderResourceSlot slot : layout.slots()) {
            boolean present = switch (slot.kind()) {
                case STORAGE_BUFFER -> buffers.stream().anyMatch(b -> b.binding() == slot.binding());
                case SAMPLED_TEXTURE -> sampled.stream().anyMatch(b -> b.binding() == slot.binding());
                case SAMPLED_VOLUME -> sampledVolumes.stream().anyMatch(b -> b.binding() == slot.binding());
                case STORAGE_IMAGE -> images.stream().anyMatch(b -> b.binding() == slot.binding());
                case STORAGE_VOLUME -> volumes.stream().anyMatch(b -> b.binding() == slot.binding());
            };
            if (!present) throw new IllegalArgumentException("Missing " + slot.kind() + " binding " + slot.binding());
        }
    }

    private static void validateUniqueBindings(List<StorageBinding> buffers,
                                               List<SampledTextureBinding> sampled,
                                               List<StorageImageBinding> images,
                                               List<StorageVolumeBinding> volumes,
                                               List<SampledVolumeBinding> sampledVolumes) {
        Set<Integer> seen = new java.util.HashSet<>();
        for (StorageBinding binding : buffers) requireUniqueBinding(seen, binding.binding());
        for (SampledTextureBinding binding : sampled) requireUniqueBinding(seen, binding.binding());
        for (StorageImageBinding binding : images) requireUniqueBinding(seen, binding.binding());
        for (StorageVolumeBinding binding : volumes) requireUniqueBinding(seen, binding.binding());
        for (SampledVolumeBinding binding : sampledVolumes) requireUniqueBinding(seen, binding.binding());
    }

    private static void requireUniqueBinding(Set<Integer> seen, int binding) {
        if (!seen.add(binding)) {
            throw new IllegalArgumentException("Duplicate shader resource binding " + binding);
        }
    }

    private static void validateAccess(ShaderResourceSlot slot, StorageAccess actual, int binding) {
        if (!slot.access().allows(actual)) {
            throw new IllegalArgumentException("Binding " + binding + " access " + actual
                    + " exceeds declared access " + slot.access());
        }
    }

    private static void validateExpectedFormat(ShaderResourceSlot slot, com.mojang.blaze3d.GpuFormat actual, int binding) {
        if (slot.format() != null && slot.format() != actual) {
            throw new IllegalArgumentException("Binding " + binding + " format " + actual
                    + " does not match declared shader image format " + slot.format());
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
                Object supplied = binding.texture();
                Object suppliedTexture = binding.texture() != null ? binding.texture().texture() : null;
                throw new RhiResourceOwnershipException("Sampled texture does not belong to the active OpenGL backend: "
                        + "binding=" + binding.binding()
                        + " view=" + className(supplied)
                        + " texture=" + className(suppliedTexture)
                        + " closed=" + (binding.texture() != null && binding.texture().isClosed()));
            }
            if (!(binding.sampler() instanceof GlSampler sampler) || sampler.isClosed()) {
                throw new RhiResourceOwnershipException("Sampler does not belong to the active OpenGL backend: "
                        + "binding=" + binding.binding()
                        + " sampler=" + className(binding.sampler()));
            }
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + binding.binding());
            if (isMultisampled(binding)) {
                GL32C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, texture.glId());
            } else {
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture.glId());
            }
            GL33C.glBindSampler(binding.binding(), sampler.getId());
        }
    }

    private static void bindSampledVolumes(List<SampledVolumeBinding> bindings) {
        for (SampledVolumeBinding binding : bindings) {
            if (!(binding.view().volume() instanceof GlStorageVolume volume) || volume.isClosed()) {
                throw new RhiResourceOwnershipException("Sampled volume does not belong to the active OpenGL backend: binding="
                        + binding.binding());
            }
            if (!(binding.sampler() instanceof GlSampler sampler) || sampler.isClosed()) {
                throw new RhiResourceOwnershipException("Sampler does not belong to the active OpenGL backend: binding="
                        + binding.binding() + " sampler=" + className(binding.sampler()));
            }
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + binding.binding());
            GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, volume.sampledTextureId(binding.view()));
            GL33C.glBindSampler(binding.binding(), sampler.getId());
        }
    }

    private static SampledVolumeState captureSampledVolumeState(List<SampledVolumeBinding> bindings) {
        int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        List<SampledVolumeUnitState> units = new ArrayList<>(bindings.size());
        try {
            for (SampledVolumeBinding binding : bindings) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + binding.binding());
                int texture = GL11C.glGetInteger(GL12C.GL_TEXTURE_BINDING_3D);
                int sampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, binding.binding());
                units.add(new SampledVolumeUnitState(binding.binding(), texture, sampler));
            }
        } finally {
            GL13C.glActiveTexture(previousActiveTexture);
        }
        return new SampledVolumeState(previousActiveTexture, units);
    }

    private static void restoreSampledVolumeState(SampledVolumeState state) {
        try {
            for (SampledVolumeUnitState unit : state.units) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit.unit);
                GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, unit.texture);
                GL33C.glBindSampler(unit.unit, unit.sampler);
            }
        } finally {
            GL13C.glActiveTexture(state.activeTexture);
        }
    }

    private record SampledVolumeState(int activeTexture, List<SampledVolumeUnitState> units) {}
    private record SampledVolumeUnitState(int unit, int texture, int sampler) {}

    private static void validateSampledBindings(List<SampledTextureBinding> sampled,
                                                List<SampledVolumeBinding> sampledVolumes) {
        int maxUnits = GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        for (SampledTextureBinding binding : sampled) {
            if (binding.binding() >= maxUnits) {
                throw new IllegalArgumentException("Sampled texture binding " + binding.binding()
                        + " exceeds GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS=" + maxUnits);
            }
        }
        for (SampledVolumeBinding binding : sampledVolumes) {
            if (binding.binding() >= maxUnits) {
                throw new IllegalArgumentException("Sampled volume binding " + binding.binding()
                        + " exceeds GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS=" + maxUnits);
            }
        }
    }

    private static void validateClearKind(com.mojang.blaze3d.GpuFormat format, VolumeClearValue value) {
        VolumeClearValue.Kind expected = switch (format.componentType()) {
            case UINT_8, UINT_16, UINT_32 -> VolumeClearValue.Kind.UNSIGNED_INT;
            case SINT_8, SINT_16, SINT_32 -> VolumeClearValue.Kind.SIGNED_INT;
            default -> VolumeClearValue.Kind.FLOAT;
        };
        if (value.kind() != expected) {
            throw new IllegalArgumentException("Clear value kind " + value.kind() + " is incompatible with "
                    + format + "; expected " + expected);
        }
    }

    private static String volumeCapabilityFailure(RhiVolumeCapabilities caps, StorageVolumeDescriptor descriptor) {
        if (!caps.allocation().supported()) return caps.allocation().reason();
        if (descriptor.storage() && !caps.storageViews().supported()) return caps.storageViews().reason();
        if (descriptor.sampled() && !caps.sampledViews().supported()) return caps.sampledViews().reason();
        if ((descriptor.copySource() || descriptor.copyDestination()) && !caps.copy().supported()) return caps.copy().reason();
        return "OpenGL 3D volume descriptor is unsupported";
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static SampledTextureState captureSampledTextureState(List<SampledTextureBinding> bindings) {
        int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        List<SampledTextureUnitState> units = new ArrayList<>(bindings.size());
        try {
            for (SampledTextureBinding binding : bindings) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + binding.binding());
                boolean multisampled = isMultisampled(binding);
                int texture = GL11C.glGetInteger(multisampled
                        ? GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE
                        : GL11C.GL_TEXTURE_BINDING_2D);
                int sampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, binding.binding());
                units.add(new SampledTextureUnitState(binding.binding(), multisampled, texture, sampler));
            }
        } finally {
            GL13C.glActiveTexture(previousActiveTexture);
        }
        return new SampledTextureState(previousActiveTexture, units);
    }

    private static void restoreSampledTextureState(SampledTextureState state) {
        try {
            for (SampledTextureUnitState unit : state.units) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit.unit);
                if (unit.multisampled) {
                    GL32C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, unit.texture);
                } else {
                    GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, unit.texture);
                }
                GL33C.glBindSampler(unit.unit, unit.sampler);
            }
        } finally {
            GL13C.glActiveTexture(state.activeTexture);
        }
    }

    private record SampledTextureState(int activeTexture, List<SampledTextureUnitState> units) {}
    private record SampledTextureUnitState(int unit, boolean multisampled, int texture, int sampler) {}

    private static boolean isMultisampled(SampledTextureBinding binding) {
        return binding.texture().texture() instanceof IMsaaTexture msaa && msaa.combatant$getSamples() > 1;
    }

    private static void bindStorageImages(List<StorageImageBinding> bindings) {
        for (StorageImageBinding binding : bindings) {
            if (!(binding.image().storageView(binding.mipLevel()) instanceof GlTextureView texture) || texture.isClosed()) {
                throw new IllegalArgumentException("Storage image does not belong to the active OpenGL backend");
            }
            int format = GlConst.toGlInternalId(binding.image().descriptor().format());
            // storageView(mip) is already a one-mip texture view. Image level is relative to that
            // view, therefore it must always be zero here.
            GL42C.glBindImageTexture(binding.binding(), texture.glId(), 0, false, 0,
                    glImageAccess(binding.access()), format);
        }
    }

    private static void bindStorageVolumes(List<StorageVolumeBinding> bindings) {
        for (StorageVolumeBinding binding : bindings) {
            if (!(binding.volume() instanceof GlStorageVolume volume) || volume.isClosed()) {
                throw new IllegalArgumentException("Storage volume does not belong to the active OpenGL backend");
            }
            RhiVolumeView view = binding.view();
            int format = GlConst.toGlInternalId(volume.descriptor().format());
            GL42C.glBindImageTexture(binding.binding(), volume.id, volume.storageMip(view), true, 0,
                    glImageAccess(binding.access()), format);
        }
    }

    private static void validateImageUnitBindings(List<StorageImageBinding> images,
                                                  List<StorageVolumeBinding> volumes) {
        int maxImageUnits = GL11C.glGetInteger(GL42C.GL_MAX_IMAGE_UNITS);
        for (StorageImageBinding binding : images) {
            if (binding.binding() >= maxImageUnits) {
                throw new IllegalArgumentException("Storage image binding " + binding.binding()
                        + " exceeds GL_MAX_IMAGE_UNITS=" + maxImageUnits);
            }
        }
        for (StorageVolumeBinding binding : volumes) {
            if (binding.binding() >= maxImageUnits) {
                throw new IllegalArgumentException("Storage volume binding " + binding.binding()
                        + " exceeds GL_MAX_IMAGE_UNITS=" + maxImageUnits);
            }
        }
    }

    private static List<ImageUnitState> captureImageUnitState(List<StorageImageBinding> images,
                                                               List<StorageVolumeBinding> volumes) {
        Set<Integer> units = new LinkedHashSet<>();
        for (StorageImageBinding binding : images) units.add(binding.binding());
        for (StorageVolumeBinding binding : volumes) units.add(binding.binding());
        List<ImageUnitState> states = new ArrayList<>(units.size());
        for (int unit : units) {
            states.add(new ImageUnitState(
                    unit,
                    GL30C.glGetIntegeri(GL42C.GL_IMAGE_BINDING_NAME, unit),
                    GL30C.glGetIntegeri(GL42C.GL_IMAGE_BINDING_LEVEL, unit),
                    GL30C.glGetIntegeri(GL42C.GL_IMAGE_BINDING_LAYERED, unit) != 0,
                    GL30C.glGetIntegeri(GL42C.GL_IMAGE_BINDING_LAYER, unit),
                    GL30C.glGetIntegeri(GL42C.GL_IMAGE_BINDING_ACCESS, unit),
                    GL30C.glGetIntegeri(GL42C.GL_IMAGE_BINDING_FORMAT, unit)));
        }
        return states;
    }

    private static void restoreImageUnitState(List<ImageUnitState> states) {
        for (ImageUnitState state : states) {
            if (state.texture == 0) {
                GL42C.glBindImageTexture(state.unit, 0, 0, false, 0, GlConst.GL_READ_ONLY, GlConst.GL_RGBA8);
            } else {
                GL42C.glBindImageTexture(state.unit, state.texture, state.level, state.layered, state.layer,
                        state.access, state.format);
            }
        }
    }

    private record ImageUnitState(int unit, int texture, int level, boolean layered, int layer, int access, int format) {}

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

    private static void validateBarrierResources(RhiResourceBarrier barrier) {
        for (RhiStorageBuffer resource : barrier.buffers()) {
            if (!(resource instanceof GlStorageBuffer buffer) || buffer.closed) {
                throw new IllegalArgumentException("Barrier buffer does not belong to the active OpenGL backend");
            }
        }
        for (RhiStorageImage resource : barrier.images()) {
            GpuTextureView view = resource.view();
            if (!(view instanceof GlTextureView glView) || glView.isClosed()) {
                throw new IllegalArgumentException("Barrier image does not belong to the active OpenGL backend");
            }
        }
        for (RhiStorageVolume resource : barrier.volumes()) {
            if (!(resource instanceof GlStorageVolume volume) || volume.isClosed()) {
                throw new IllegalArgumentException("Barrier volume does not belong to the active OpenGL backend");
            }
        }
    }

    private static int memoryBarrierBits(RhiResourceBarrier barrier) {
        boolean hasBuffers = !barrier.buffers().isEmpty();
        boolean hasImages = !barrier.images().isEmpty();
        boolean hasVolumes = !barrier.volumes().isEmpty();
        if (!hasBuffers && !hasImages && !hasVolumes) {
            return switch (barrier.destinationStage()) {
                case COMPUTE -> GL42C.GL_TEXTURE_FETCH_BARRIER_BIT
                        | GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                        | GL43C.GL_SHADER_STORAGE_BARRIER_BIT
                        | GL42C.GL_UNIFORM_BARRIER_BIT
                        | GL42C.GL_ATOMIC_COUNTER_BARRIER_BIT;
                case GRAPHICS -> GL42C.GL_TEXTURE_FETCH_BARRIER_BIT
                        | GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                        | GL43C.GL_SHADER_STORAGE_BARRIER_BIT
                        | GL42C.GL_FRAMEBUFFER_BARRIER_BIT
                        | GL42C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT
                        | GL42C.GL_ELEMENT_ARRAY_BARRIER_BIT
                        | GL42C.GL_UNIFORM_BARRIER_BIT;
                case INDIRECT -> GL42C.GL_COMMAND_BARRIER_BIT;
                case TRANSFER -> GL42C.GL_TEXTURE_UPDATE_BARRIER_BIT | GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
                case ALL -> GL42C.GL_ALL_BARRIER_BITS;
            };
        }
        int bits = 0;

        if (hasBuffers) bits |= GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
        if (hasImages || hasVolumes) bits |= GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;

        boolean canBeSampled = barrier.images().stream().anyMatch(image -> image.descriptor().sampled())
                || barrier.volumes().stream().anyMatch(volume -> volume.descriptor().sampled());
        if (barrier.destinationAccess() != RhiResourceBarrier.Access.WRITE && canBeSampled) {
            bits |= GL42C.GL_TEXTURE_FETCH_BARRIER_BIT;
        }

        switch (barrier.destinationStage()) {
            case INDIRECT -> {
                if (hasBuffers) bits |= GL42C.GL_COMMAND_BARRIER_BIT;
            }
            case TRANSFER -> {
                if (hasBuffers) bits |= GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
                if (hasImages || hasVolumes) bits |= GL42C.GL_TEXTURE_UPDATE_BARRIER_BIT;
            }
            case GRAPHICS -> {
                if (canBeSampled) bits |= GL42C.GL_TEXTURE_FETCH_BARRIER_BIT;
                if (barrier.images().stream().anyMatch(image -> image.descriptor().renderAttachment())) {
                    bits |= GL42C.GL_FRAMEBUFFER_BARRIER_BIT;
                }
            }
            case COMPUTE -> { }
            case ALL -> {
                // ALL is a stage scope, not a reason to flush unrelated global GL state.
                if (hasBuffers) bits |= GL42C.GL_COMMAND_BARRIER_BIT | GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
                if (hasImages || hasVolumes) bits |= GL42C.GL_TEXTURE_UPDATE_BARRIER_BIT;
                if (canBeSampled) bits |= GL42C.GL_TEXTURE_FETCH_BARRIER_BIT;
                if (barrier.images().stream().anyMatch(image -> image.descriptor().renderAttachment())) {
                    bits |= GL42C.GL_FRAMEBUFFER_BARRIER_BIT;
                }
            }
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
                GlStateManager._depthFunc(GL11C.GL_LEQUAL);
            }
            case READ_WRITE_LEQUAL -> {
                GlStateManager._enableDepthTest();
                GlStateManager._depthMask(true);
                GlStateManager._depthFunc(GL11C.GL_LEQUAL);
            }
            case READ_ONLY_GREATER_EQUAL -> {
                GlStateManager._enableDepthTest();
                GlStateManager._depthMask(false);
                GlStateManager._depthFunc(GL11C.GL_GEQUAL);
            }
            case READ_WRITE_GREATER_EQUAL -> {
                GlStateManager._enableDepthTest();
                GlStateManager._depthMask(true);
                GlStateManager._depthFunc(GL11C.GL_GEQUAL);
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
