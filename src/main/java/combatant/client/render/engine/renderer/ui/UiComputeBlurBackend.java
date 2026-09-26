/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.postprocess.PostProcessExecutionPolicy;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compute implementation of the shared Dual-Kawase chain. Raster remains the capability/runtime fallback. */
final class UiComputeBlurBackend implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("combatant", "ui_blur");
    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .member("region", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private final Map<ImageKey, RhiStorageImage> levels = new HashMap<>();
    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer downParams;
    private RhiStorageBuffer upParams;
    private boolean disabledForSession;

    @Nullable Result tryBlur(CombatantRhi rhi,
                             String domain,
                             GpuTextureView source,
                             GpuSampler sourceSampler,
                             RhiResourceBarrier.Stage producerStage,
                             int screenWidth,
                             int screenHeight,
                             int iterations,
                             float offsetPx,
                             UiBlurRegion region) {
        if (disabledForSession || rhi == null || source == null || sourceSampler == null || region == null) return null;
        if (!PostProcessExecutionPolicy.useCompute(rhi)) return null;
        if (iterations < 1) return null;

        try {
            ensureOwner(rhi);
            GpuSampler linear = PostProcessManager.getSampler();
            if (linear == null) return null;

            GpuTextureView current = source;
            GpuSampler currentSampler = sourceSampler;
            RhiStorageImage lastWritten = null;

            // The UI source has one known producer in this path: ordinary framebuffer/UI draws
            // are GRAPHICS, while captured-world/current-target snapshots come from a transfer copy.
            // Use one producer-specific dependency instead of ALL_COMMANDS or several global barriers.
            RhiResourceBarrier.Stage sourceStage = producerStage != null
                    ? producerStage
                    : RhiResourceBarrier.Stage.GRAPHICS;
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    sourceStage,
                    RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.READ,
                    List.of(),
                    List.of()
            ));

            for (int level = 0; level < iterations; level++) {
                int width = Math.max(1, screenWidth >> (level + 1));
                int height = Math.max(1, screenHeight >> (level + 1));
                RhiStorageImage output = image(domain, level, width, height);
                UiBlurRegion levelRegion = region.scaleTo(screenWidth, screenHeight, width, height);
                dispatch(current, currentSampler, output, offsetPx, false, levelRegion);
                computeReadBarrier(output);
                current = output.view();
                currentSampler = linear;
                lastWritten = output;
            }

            for (int level = iterations - 2; level >= 0; level--) {
                int width = Math.max(1, screenWidth >> (level + 1));
                int height = Math.max(1, screenHeight >> (level + 1));
                RhiStorageImage output = image(domain, level, width, height);
                UiBlurRegion levelRegion = region.scaleTo(screenWidth, screenHeight, width, height);
                dispatch(current, currentSampler, output, offsetPx, true, levelRegion);
                computeReadBarrier(output);
                current = output.view();
                currentSampler = linear;
                lastWritten = output;
            }

            if (lastWritten == null) return null;
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.GRAPHICS,
                    RhiResourceBarrier.Access.READ,
                    List.of(),
                    List.of(lastWritten)
            ));
            PostProcessExecutionPolicy.logComputeActive("ui-blur", "UI blur");
            return new Result(current, linear);
        } catch (Throwable t) {
            if (PostProcessExecutionPolicy.isTransientBackendResourceMismatch(t)) {
                PostProcessExecutionPolicy.warnTransientResourceFallback("ui-blur", "UI blur", t);
                return null;
            }
            disabledForSession = true;
            PostProcessExecutionPolicy.warnRuntimeFallback("ui-blur", "UI blur", t);
            closeOwned();
            return null;
        }
    }

    void resetSessionFailure() {
        disabledForSession = false;
    }

    private void dispatch(GpuTextureView source,
                          GpuSampler sampler,
                          RhiStorageImage output,
                          float offsetPx,
                          boolean upPass,
                          UiBlurRegion region) {
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "params", Math.max(0.0f, offsetPx), upPass ? 1.0f : 0.0f, 1.0f, 0.0f)
                .putVec4(0, "region", region.x(), region.y(), region.width(), region.height());
        RhiStorageBuffer paramsBuffer = params(upPass);
        paramsBuffer.upload(writer.buffer(), 0L);

        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                upPass ? "Combatant UI blur up" : "Combatant UI blur down",
                pipeline(),
                groups(region.width()),
                groups(region.height()),
                1,
                List.of(new StorageBinding(2, paramsBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, source, sampler)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void computeReadBarrier(RhiStorageImage image) {
        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE,
                RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE,
                RhiResourceBarrier.Access.READ,
                List.of(),
                List.of(image)
        ));
    }

    private RhiStorageImage image(String domain, int level, int width, int height) {
        ImageKey key = new ImageKey(domain == null ? "surface" : domain, level);
        RhiStorageImage existing = levels.get(key);
        if (existing != null && existing.descriptor().width() == width && existing.descriptor().height() == height) {
            return existing;
        }
        if (existing != null) {
            try { existing.close(); } catch (Throwable ignored) { }
        }
        RhiStorageImage created = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-ui-blur-" + key.domain + "-level-" + level,
                width,
                height,
                GpuFormat.RGBA8_UNORM,
                StorageAccess.READ_WRITE,
                true,
                false
        ));
        levels.put(key, created);
        return created;
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-ui-blur-compute", SHADER, LAYOUT));
        }
        return pipeline;
    }

    private RhiStorageBuffer params(boolean upPass) {
        if (upPass) {
            if (upParams == null) {
                upParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                        "combatant-ui-blur-up-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
            }
            return upParams;
        }
        if (downParams == null) {
            downParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-ui-blur-down-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return downParams;
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
        disabledForSession = false;
    }

    private void closeOwned() {
        for (RhiStorageImage image : levels.values()) {
            try { image.close(); } catch (Throwable ignored) { }
        }
        levels.clear();
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
        }
        if (downParams != null) {
            try { downParams.close(); } catch (Throwable ignored) { }
            downParams = null;
        }
        if (upParams != null) {
            try { upParams.close(); } catch (Throwable ignored) { }
            upParams = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
        disabledForSession = false;
    }

    record Result(GpuTextureView view, GpuSampler sampler) { }
    private record ImageKey(String domain, int level) { }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }
}
