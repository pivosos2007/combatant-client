/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.vulkan.util;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.util.logging.DebugLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vulkan 26.3 compiles a frontend RenderPipeline once, but fixed-function stencil and
 * multisampling are properties of the active native render pass. Resolve extensions only
 * when VulkanRenderPass.setPipeline is called, without mutating Minecraft's cached pipeline.
 *
 * <p>PipelineBuilder closes its temporary SPIR-V modules after the first compilation.
 * We therefore snapshot the module bytes, rather than keeping a CreateInfo containing
 * closed modules. The snapshot is released with the owning VulkanRenderPipeline.</p>
 */
public final class VulkanPipelineVariants {
    private static final Map<VulkanRenderPipeline, Entry> ENTRIES = new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> VARIANT_COMPILATION = ThreadLocal.withInitial(() -> false);

    private VulkanPipelineVariants() {
    }

    public static boolean compilingVariant() {
        return VARIANT_COMPILATION.get();
    }

    /** Called only after a successful base VulkanRenderPipeline.compile. */
    public static synchronized void registerBase(VulkanRenderPipeline base, BackendRenderPipeline.CreateInfo info) {
        if (base == null || info == null || compilingVariant() || !VulkanRenderStateBridge.vulkanBackendActive()) return;
        List<SavedShader> shaders = new ArrayList<>(info.shaders().size());
        for (BackendRenderPipeline.CreateInfo.Shader shader : info.shaders()) {
            ByteBuffer bytes = shader.module().spv().duplicate();
            byte[] copy = new byte[bytes.remaining()];
            bytes.get(copy);
            shaders.add(new SavedShader(shader.name(), shader.entryPoint(), shader.module().type(), copy));
        }
        ENTRIES.put(base, new Entry(info, List.copyOf(shaders)));
    }

    /** Called before a base pipeline is closed; variants use the same deferred GPU destruction. */
    public static synchronized void release(VulkanRenderPipeline base) {
        Entry entry = ENTRIES.remove(base);
        if (entry == null) return;
        for (VulkanRenderPipeline variant : entry.variants.values()) {
            variant.close();
        }
        entry.variants.clear();
    }

    public static synchronized BackendRenderPipeline select(BackendRenderPipeline original) {
        if (!(original instanceof VulkanRenderPipeline base)
                || !VulkanRenderStateBridge.vulkanBackendActive()) return original;
        Entry entry = ENTRIES.get(base);
        if (entry == null || (!VulkanRenderStateBridge.currentRenderPassHasStencilAttachment()
                && VulkanRenderStateBridge.currentRenderPassSamples() == 1)) return original;

        RenderPipeline logical = RenderPipelineRegistry.global().nativeById(entry.createInfo.name());
        VulkanRenderStateBridge.PipelineVariantKey key = VulkanRenderStateBridge.pipelineVariantKey(logical);
        VulkanRenderPipeline cached = entry.variants.get(key);
        if (cached != null && !cached.isClosed()) return cached;

        // No fallthrough to a single-sampled pipeline while a multisampled render pass is open:
        // Vulkan requires rasterizationSamples and attachment count/formats to be compatible.
        VARIANT_COMPILATION.set(true);
        VulkanRenderStateBridge.beginPipelineCompile(logical);
        try {
            List<BackendRenderPipeline.CreateInfo.Shader> shaders = new ArrayList<>(entry.shaders.size());
            for (SavedShader saved : entry.shaders) shaders.add(saved.restore());
            BackendRenderPipeline.CreateInfo source = entry.createInfo;
            BackendRenderPipeline.CreateInfo variantInfo = new BackendRenderPipeline.CreateInfo(
                    source.name(), shaders, source.vertexBuffers(), source.attribBindings(),
                    source.uniforms(), source.pushConstantsSize(), source.depthStencilState(),
                    source.polygonMode(), source.cull(), source.colorTargetStates(), source.primitiveTopology());
            VulkanRenderPipeline variant = VulkanRenderPipeline.compile(base.device(), variantInfo);
            entry.variants.put(key, variant);
            return variant;
        } catch (RuntimeException error) {
            if (key.usesMsaa()) VulkanRenderStateBridge.disableMsaaAfterFailure("pipeline " + key, error);
            if (key.usesStencil()) VulkanRenderStateBridge.disableStencilAfterFailure("pipeline " + key, error);
            DebugLog.error("[Vulkan/RHI] incompatible pipeline variant " + key, error);
            throw error; // Never bind a pipeline with the wrong sample/stencil attachment compatibility.
        } finally {
            VulkanRenderStateBridge.endPipelineCompile();
            VARIANT_COMPILATION.remove();
        }
    }

    private record SavedShader(String name, String entryPoint, ShaderType type, byte[] binary) {
        BackendRenderPipeline.CreateInfo.Shader restore() {
            ByteBuffer copy = ByteBuffer.allocateDirect(binary.length).order(ByteOrder.nativeOrder());
            copy.put(binary).flip();
            SpvModule module = new SpvModule() {
                @Override public void close() { }
                @Override public ByteBuffer spv() { return copy.duplicate(); }
                @Override public ShaderType type() { return type; }
                @Override public Reflection reflect() { throw new UnsupportedOperationException("Vulkan variants use already-reflected shader interfaces"); }
                @Override public Reflection getReflectionInfoIfAvailable() { return null; }
            };
            return new BackendRenderPipeline.CreateInfo.Shader(name, entryPoint, module);
        }
    }

    private static final class Entry {
        final BackendRenderPipeline.CreateInfo createInfo;
        final List<SavedShader> shaders;
        final Map<VulkanRenderStateBridge.PipelineVariantKey, VulkanRenderPipeline> variants = new HashMap<>();

        Entry(BackendRenderPipeline.CreateInfo createInfo, List<SavedShader> shaders) {
            this.createInfo = createInfo;
            this.shaders = shaders;
        }
    }
}
