/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.features.gui.hud.HudClimateSurface;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Uses separate RGBA16F read and write images at half framebuffer resolution.
 * Sampling coordinates are in physical UI space.
 */
public final class UiClimateField {
    private static final int LOCAL = 8;
    private static final UiClimateField INSTANCE = new UiClimateField();
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("combatant", "ui_climate_field");
    private static final Std430StructLayout PARAMETERS = Std430StructLayout.builder()
            .member("u_Frame", Std430Type.VEC4)
            .member("u_Weather", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout RESOURCES = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, GpuFormat.RGBA16_FLOAT),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer parameters;
    private RhiStorageImage a;
    private RhiStorageImage b;
    private RhiStorageImage latest;
    private int width;
    private int height;
    private long lastFrame = Long.MIN_VALUE;
    private long lastNanos;
    private boolean valid;
    private int lastExitSequence = -1;

    private UiClimateField() { }

    public static GpuTextureView update(float framebufferW, float framebufferH) {
        return INSTANCE.advance(CombatantRenderSystem.rhi(), framebufferW, framebufferH);
    }

    /** Discard precipitation from the previous world/dimension without reallocating textures. */
    public static void invalidate() {
        INSTANCE.valid = false;
        INSTANCE.lastFrame = Long.MIN_VALUE;
        INSTANCE.lastExitSequence = -1;
    }

    /** Release on render-resource teardown or backend migration; next use recreates the field. */
    public static void release() {
        INSTANCE.closeResources();
        INSTANCE.owner = null;
    }

    private GpuTextureView advance(CombatantRhi rhi, float fbWidth, float fbHeight) {
        if (owner != rhi) {
            closeResources();
            owner = rhi;
        }
        if (rhi == null || !rhi.capabilities().nativeComputeSubmission() || !rhi.capabilities().imageLoadStore()) {
            throw new IllegalStateException("Climate GPU layer requires native compute and storage images");
        }
        int desiredW = Math.max(1, Math.round(fbWidth * 0.5f));
        int desiredH = Math.max(1, Math.round(fbHeight * 0.5f));
        ensureImages(desiredW, desiredH);
        long frame = CombatantRenderSystem.uniforms().frameId();
        if (latest != null && lastFrame == frame) return latest.view();

        long now = System.nanoTime();
        if (lastNanos != 0 && now - lastNanos > 15_000_000_000L) valid = false;
        float dt = lastNanos == 0 ? 1.0f / 60.0f
                : Math.min(Math.max((now - lastNanos) * 1.0e-9f, 0.0f), 0.05f);
        lastNanos = now;
        HudClimateSurface.Sample weather = HudClimateSurface.sample();
        int event = HudClimateSurface.waterExitSequence();
        boolean exitPulse = event > 0 && event != lastExitSequence
                && Util.getMillis() - HudClimateSurface.waterExitAtMillis() < 350L;
        lastExitSequence = event;
        Std430Writer writer = new Std430Writer(PARAMETERS, 1)
                .putVec4(0, "u_Frame", fbWidth, fbHeight, dt, valid ? 1f : 0f)
                .putVec4(0, "u_Weather", weather.rain(), exitPulse ? 1f : 0f,
                        (float) ((now * 1.0e-9) % 4096.0), (float) (event % 8192));
        if (parameters == null) {
            parameters = rhi.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-ui-climate-parameters", PARAMETERS, 1, StorageAccess.READ_ONLY, false));
        }
        parameters.upload(writer.buffer(), 0L);
        RhiStorageImage target = latest == a ? b : a;
        if (target == null) throw new IllegalStateException("Climate ping-pong images missing");
        if (valid) {
            // Both transitions matter with multiple frames in flight.
            rhi.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ_WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(latest)));
            rhi.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ_WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    List.of(), List.of(target)));
        }
        rhi.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant UI Climate Field", pipeline(),
                (width + LOCAL - 1) / LOCAL, (height + LOCAL - 1) / LOCAL, 1,
                List.of(new StorageBinding(2, parameters, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, target == a ? b.view() : a.view(),
                        PostProcessManager.getSampler())),
                List.of(new StorageImageBinding(1, target, StorageAccess.WRITE_ONLY))));
        rhi.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.GRAPHICS, RhiResourceBarrier.Access.READ,
                List.of(), List.of(target)));
        latest = target;
        lastFrame = frame;
        valid = true;
        return latest.view();
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) pipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-ui-climate-field", SHADER, RESOURCES));
        return pipeline;
    }

    private void ensureImages(int desiredW, int desiredH) {
        if (a != null && b != null && desiredW == width && desiredH == height) return;
        closeImages();
        width = desiredW;
        height = desiredH;
        try {
            a = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-climate-history-a", width, height, GpuFormat.RGBA16_FLOAT,
                    StorageAccess.READ_WRITE, true, false));
            b = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-climate-history-b", width, height, GpuFormat.RGBA16_FLOAT,
                    StorageAccess.READ_WRITE, true, false));
        } catch (RuntimeException | Error e) {
            closeImages();
            throw e;
        }
    }

    private void closeImages() {
        if (a != null) a.close();
        if (b != null) b.close();
        a = null;
        b = null;
        latest = null;
        valid = false;
        lastFrame = Long.MIN_VALUE;
        lastNanos = 0;
        lastExitSequence = -1;
        width = height = 0;
    }

    private void closeResources() {
        closeImages();
        if (pipeline != null) pipeline.close();
        if (parameters != null) parameters.close();
        pipeline = null;
        parameters = null;
    }
}
