/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;

import com.mojang.renderpearl.api.GpuFormat;
import combatant.client.config.subsystem.VisualConfig;
import com.mojang.renderpearl.api.textures.GpuSampler;
import combatant.client.render.engine.postprocess.PostProcessExecutionPolicy;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.renderer.Renderer2D;
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

import java.util.List;

/** Owns the compute pipelines and storage images used by the main-menu smoke renderer. */
final class MainMenuSmokeComputeBackend implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final int PRESSURE_ITERATIONS = 30;
    private static final Std430StructLayout PARAMS = Std430StructLayout.builder()
            .member("screen", Std430Type.VEC4)
            .member("mouse", Std430Type.VEC4)
            .member("motion", Std430Type.VEC4)
            .member("accent", Std430Type.VEC4)
            .member("soft", Std430Type.VEC4)
            .member("cold", Std430Type.VEC4)
            .member("warm", Std430Type.VEC4)
            .member("hexGrid", Std430Type.VEC4)
            .member("rimColor", Std430Type.VEC4)
            .array("sources", Std430Type.VEC4, MainMenuHexTrail.MAX_SOURCES)
            .array("sourceProps", Std430Type.VEC4, MainMenuHexTrail.MAX_SOURCES)
            .build();
    private static final ShaderResourceLayout FLOAT_LAYOUT = layout(GpuFormat.RGBA16_FLOAT, false);
    private static final ShaderResourceLayout PROJECT_LAYOUT = layout(GpuFormat.RGBA16_FLOAT, true);
    private static final ShaderResourceLayout COMPOSITE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, GpuFormat.RGBA16_FLOAT),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));
    private static final Identifier SIMULATION = id("ui_menu_smoke_simulate");
    private static final Identifier VORTICITY = id("ui_menu_smoke_vorticity");
    private static final Identifier DIVERGENCE = id("ui_menu_smoke_divergence");
    private static final Identifier PRESSURE = id("ui_menu_smoke_pressure");
    private static final Identifier PROJECT = id("ui_menu_smoke_project");
    private static final Identifier DYE = id("ui_menu_smoke_dye");
    private static final Identifier DYE_CORRECT = id("ui_menu_smoke_dye_correct");
    private static final Identifier BLOOM = id("ui_menu_smoke_bloom");
    private static final Identifier BLOOM_BLUR = id("ui_menu_smoke_bloom_blur");
    private static final Identifier COMPOSITE = id("ui_menu_smoke_composite");

    private CombatantRhi owner;
    private RhiComputePipeline simulation;
    private RhiComputePipeline vorticity;
    private RhiComputePipeline divergence;
    private RhiComputePipeline pressure;
    private RhiComputePipeline projection;
    private RhiComputePipeline dyePipeline;
    private RhiComputePipeline dyeCorrection;
    private RhiComputePipeline bloomPipeline;
    private RhiComputePipeline bloomBlurPipeline;
    private RhiComputePipeline composite;
    private RhiStorageImage stateA;
    private RhiStorageImage stateB;
    private RhiStorageImage advected;
    private RhiStorageImage curled;
    private RhiStorageImage pressureA;
    private RhiStorageImage pressureB;
    private RhiStorageImage dyeA;
    private RhiStorageImage dyeAdvected;
    private RhiStorageImage dyeB;
    private RhiStorageImage bloom;
    private RhiStorageImage bloomBright;
    private RhiStorageImage output;
    private RhiStorageBuffer params;
    private int textureWidth;
    private int textureHeight;
    private int dyeWidth;
    private int dyeHeight;
    private boolean hasHistory;
    private boolean disabled;
    private boolean lastSuccess = true;

    boolean operational() { return !disabled && lastSuccess; }
    boolean canRetry() { return !disabled; }

    boolean render(CombatantRhi rhi, float width, float height,
                   MainMenuHexTrail hex, MainMenuBackdrop.GridLayout grid, float delta, float time, float opacity,
                   int accent, int soft, int cold, int warm, int rimColor, boolean reset) {
        if (rhi == null || disabled || !PostProcessExecutionPolicy.useCompute(rhi)) return false;
        GpuSampler sampler = PostProcessManager.getSampler();
        if (sampler == null) { lastSuccess = false; return false; }
        try {
            if (rhi != owner) {
                closeResources();
                owner = rhi;
            }
            VisualConfig options = VisualConfig.get();
            float quality = switch (options.menuFluidQuality()) {
                case "balanced" -> 0.75f;
                case "ultra" -> 1.30f;
                default -> 1.0f;
            };
            float simulationScale = Math.min(0.52f * quality, Math.min(960f * quality / width, 540f * quality / height));
            float colorScale = Math.min(1.0f, Math.min(1920f / width, 1080f / height));
            int w = Math.max(1, Math.round(width * simulationScale));
            int h = Math.max(1, Math.round(height * simulationScale));
            int cw = Math.max(1, Math.round(width * colorScale));
            int ch = Math.max(1, Math.round(height * colorScale));
            ensureImages(w, h, cw, ch);
            boolean clear = reset || !hasHistory;
            Std430Writer values = new Std430Writer(PARAMS, 1)
                    .putVec4(0, "screen", w, h, Math.min(0.05f, delta), time)
                    .putVec4(0, "mouse", options.menuFluidBloom(), options.menuFluidVorticity(), options.menuFluidSize(), 0f)
                    .putVec4(0, "motion", options.menuAmbientGlowEnabled() ? 1f : 0f, opacity, clear ? 1f : 0f, hex.sourceCount())
                    .putVec4(0, "accent", red(accent), green(accent), blue(accent), 1f)
                    .putVec4(0, "soft", red(soft), green(soft), blue(soft), 1f)
                    .putVec4(0, "cold", red(cold), green(cold), blue(cold), 1f)
                    .putVec4(0, "warm", red(warm), green(warm), blue(warm), 1f)
                    .putVec4(0, "hexGrid", grid.originX() / width, grid.originY() / height,
                            grid.radius() / width, options.menuCursorGlowEnabled() ? 1f : 0f)
                    .putVec4(0, "rimColor", red(rimColor), green(rimColor), blue(rimColor),
                            options.menuCursorIntensity());
            for (int i = 0; i < hex.sourceCount(); i++) {
                MainMenuHexTrail.PlasmaSource source = hex.source(i);
                values.putVec4(0, "sources", i, source.x / width, source.y / height,
                                source.vx / width, source.vy / height)
                        .putVec4(0, "sourceProps", i, source.intensity * options.menuFluidIntensity(), source.tint,
                                source.age, source.momentum);
            }
            if (params == null) params = owner.advancedShaders().createStorageBuffer(
                    new StorageBufferDescriptor("combatant-menu-smoke-params", PARAMS, 1,
                            StorageAccess.READ_ONLY, false));
            params.upload(values.buffer(), 0L);
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ_WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ_WRITE,
                    List.of(), List.of()));

            RhiStorageImage previous = hasHistory ? stateA : stateB;
            execute(pipeline(SIMULATION), previous, advected, null, null, w, h, sampler, values.byteSize(), "Fluid velocity advection");
            barrier(advected);
            execute(pipeline(VORTICITY), advected, curled, null, null, w, h, sampler, values.byteSize(), "Fluid vorticity");
            barrier(curled);
            execute(pipeline(DIVERGENCE), curled, pressureA, null, null, w, h, sampler, values.byteSize(), "Fluid divergence");
            barrier(pressureA);
            RhiStorageImage pressureRead = pressureA;
            RhiStorageImage pressureWrite = pressureB;
            for (int i = 0; i < PRESSURE_ITERATIONS; i++) {
                execute(pipeline(PRESSURE), pressureRead, pressureWrite, null, null, w, h, sampler, values.byteSize(), "Fluid pressure");
                barrier(pressureWrite);
                RhiStorageImage swap = pressureRead;
                pressureRead = pressureWrite;
                pressureWrite = swap;
            }
            execute(pipeline(PROJECT), curled, stateB, pressureRead, dyeA, w, h, sampler, values.byteSize(), "Fluid wall projection");
            barrier(stateB);
            execute(pipeline(DYE), dyeA, dyeAdvected, stateB, null, cw, ch,
                    sampler, values.byteSize(), "Fluid RGB advection");
            barrier(dyeAdvected);
            execute(pipeline(DYE_CORRECT), dyeA, dyeB, dyeAdvected, stateB, cw, ch,
                    sampler, values.byteSize(), "Fluid RGB correction and splat");
            barrier(dyeB);
            execute(pipeline(BLOOM), dyeB, bloomBright, null, null, (cw + 1) / 2, (ch + 1) / 2,
                    sampler, values.byteSize(), "Fluid bloom threshold");
            barrier(bloomBright);
            execute(pipeline(BLOOM_BLUR), bloomBright, bloom, null, null, (cw + 1) / 2, (ch + 1) / 2,
                    sampler, values.byteSize(), "Fluid bloom blur");
            barrier(bloom);
            execute(pipeline(COMPOSITE), dyeB, output, stateB, bloom, cw, ch,
                    sampler, values.byteSize(), "Fluid color composite");
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.GRAPHICS, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(output)));
            RhiStorageImage oldState = stateA;
            stateA = stateB;
            stateB = oldState;
            RhiStorageImage oldDye = dyeA;
            dyeA = dyeB;
            dyeB = oldDye;
            hasHistory = true;
            Renderer2D.COLOR.textureQuad(output.view(), sampler, 0, 0, width, height);
            PostProcessExecutionPolicy.logComputeActive("main-menu-smoke", "Main menu smoke");
            lastSuccess = true;
            return true;
        } catch (Throwable cause) {
            if (PostProcessExecutionPolicy.isTransientBackendResourceMismatch(cause)) {
                PostProcessExecutionPolicy.warnTransientResourceFallback("main-menu-smoke", "Main menu smoke", cause);
                closeResources();
                lastSuccess = false;
                return false;
            }
            disabled = true;
            lastSuccess = false;
            PostProcessExecutionPolicy.warnRuntimeFallback("main-menu-smoke", "Main menu smoke", cause);
            closeResources();
            return false;
        }
    }

    private void execute(RhiComputePipeline program, RhiStorageImage source, RhiStorageImage destination,
                         RhiStorageImage secondSource, RhiStorageImage thirdSource,
                         int width, int height, GpuSampler sampler, int size, String label) {
        List<SampledTextureBinding> textures;
        if (thirdSource != null) {
            textures = List.of(new SampledTextureBinding(0, source.view(), sampler),
                    new SampledTextureBinding(3, secondSource.view(), sampler),
                    new SampledTextureBinding(4, thirdSource.view(), sampler));
        } else if (secondSource != null) {
            textures = List.of(new SampledTextureBinding(0, source.view(), sampler),
                    new SampledTextureBinding(3, secondSource.view(), sampler));
        } else {
            textures = List.of(new SampledTextureBinding(0, source.view(), sampler));
        }
        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                label, program, groups(width), groups(height), 1,
                List.of(new StorageBinding(2, params, 0L, size, StorageAccess.READ_ONLY)),
                textures,
                List.of(new StorageImageBinding(1, destination, StorageAccess.WRITE_ONLY))));
    }

    private void barrier(RhiStorageImage image) {
        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of(image)));
    }

    private RhiComputePipeline pipeline(Identifier shader) {
        if (shader.equals(SIMULATION)) {
            if (simulation == null) simulation = create("advect", SIMULATION, FLOAT_LAYOUT);
            return simulation;
        }
        if (shader.equals(VORTICITY)) {
            if (vorticity == null) vorticity = create("vorticity", VORTICITY, FLOAT_LAYOUT);
            return vorticity;
        }
        if (shader.equals(DIVERGENCE)) {
            if (divergence == null) divergence = create("divergence", DIVERGENCE, FLOAT_LAYOUT);
            return divergence;
        }
        if (shader.equals(PRESSURE)) {
            if (pressure == null) pressure = create("pressure", PRESSURE, FLOAT_LAYOUT);
            return pressure;
        }
        if (shader.equals(PROJECT)) {
            if (projection == null) projection = create("projection", PROJECT, COMPOSITE_LAYOUT);
            return projection;
        }
        if (shader.equals(DYE)) {
            if (dyePipeline == null) dyePipeline = create("dye", DYE, PROJECT_LAYOUT);
            return dyePipeline;
        }
        if (shader.equals(DYE_CORRECT)) {
            if (dyeCorrection == null) dyeCorrection = create("dye-correct", DYE_CORRECT, COMPOSITE_LAYOUT);
            return dyeCorrection;
        }
        if (shader.equals(BLOOM)) {
            if (bloomPipeline == null) bloomPipeline = create("bloom", BLOOM, FLOAT_LAYOUT);
            return bloomPipeline;
        }
        if (shader.equals(BLOOM_BLUR)) {
            if (bloomBlurPipeline == null) bloomBlurPipeline = create("bloom-blur", BLOOM_BLUR, FLOAT_LAYOUT);
            return bloomBlurPipeline;
        }
        if (composite == null) composite = create("composite", COMPOSITE, COMPOSITE_LAYOUT);
        return composite;
    }

    private RhiComputePipeline create(String name, Identifier shader, ShaderResourceLayout layout) {
        return owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-menu-smoke-" + name, shader, layout));
    }

    private void ensureImages(int w, int h, int cw, int ch) {
        if (w == textureWidth && h == textureHeight && cw == dyeWidth && ch == dyeHeight
                && stateA != null && stateB != null && advected != null && curled != null
                && pressureA != null && pressureB != null && dyeA != null && dyeB != null && dyeAdvected != null
                && bloom != null && bloomBright != null && output != null) return;
        closeImages();
        textureWidth = w;
        textureHeight = h;
        dyeWidth = cw;
        dyeHeight = ch;
        stateA = image("history-a", w, h, GpuFormat.RGBA16_FLOAT);
        stateB = image("history-b", w, h, GpuFormat.RGBA16_FLOAT);
        advected = image("advection", w, h, GpuFormat.RGBA16_FLOAT);
        curled = image("curl", w, h, GpuFormat.RGBA16_FLOAT);
        pressureA = image("pressure-a", w, h, GpuFormat.RGBA16_FLOAT);
        pressureB = image("pressure-b", w, h, GpuFormat.RGBA16_FLOAT);
        dyeA = image("dye-a", cw, ch, GpuFormat.RGBA16_FLOAT);
        dyeAdvected = image("dye-advected", cw, ch, GpuFormat.RGBA16_FLOAT);
        dyeB = image("dye-b", cw, ch, GpuFormat.RGBA16_FLOAT);
        bloom = image("bloom", (cw + 1) / 2, (ch + 1) / 2, GpuFormat.RGBA16_FLOAT);
        bloomBright = image("bloom-bright", (cw + 1) / 2, (ch + 1) / 2, GpuFormat.RGBA16_FLOAT);
        output = image("color", cw, ch, GpuFormat.RGBA16_FLOAT);
        hasHistory = false;
    }

    private RhiStorageImage image(String name, int w, int h, GpuFormat format) {
        return owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-menu-smoke-" + name, w, h, format,
                StorageAccess.READ_WRITE, true, false));
    }

    private void closeImages() {
        if (stateA != null) { stateA.close(); stateA = null; }
        if (stateB != null) { stateB.close(); stateB = null; }
        if (advected != null) { advected.close(); advected = null; }
        if (curled != null) { curled.close(); curled = null; }
        if (pressureA != null) { pressureA.close(); pressureA = null; }
        if (pressureB != null) { pressureB.close(); pressureB = null; }
        if (dyeA != null) { dyeA.close(); dyeA = null; }
        if (dyeAdvected != null) { dyeAdvected.close(); dyeAdvected = null; }
        if (dyeB != null) { dyeB.close(); dyeB = null; }
        if (bloom != null) { bloom.close(); bloom = null; }
        if (bloomBright != null) { bloomBright.close(); bloomBright = null; }
        if (output != null) { output.close(); output = null; }
        textureWidth = 0;
        textureHeight = 0;
        dyeWidth = 0;
        dyeHeight = 0;
        hasHistory = false;
    }

    private void closeResources() {
        closeImages();
        if (simulation != null) { simulation.close(); simulation = null; }
        if (vorticity != null) { vorticity.close(); vorticity = null; }
        if (divergence != null) { divergence.close(); divergence = null; }
        if (pressure != null) { pressure.close(); pressure = null; }
        if (projection != null) { projection.close(); projection = null; }
        if (dyePipeline != null) { dyePipeline.close(); dyePipeline = null; }
        if (dyeCorrection != null) { dyeCorrection.close(); dyeCorrection = null; }
        if (bloomPipeline != null) { bloomPipeline.close(); bloomPipeline = null; }
        if (bloomBlurPipeline != null) { bloomBlurPipeline.close(); bloomBlurPipeline = null; }
        if (composite != null) { composite.close(); composite = null; }
        if (params != null) { params.close(); params = null; }
        owner = null;
    }

    @Override
    public void close() {
        closeResources();
        disabled = false;
        lastSuccess = true;
    }

    private static ShaderResourceLayout layout(GpuFormat format, boolean secondary) {
        if (secondary) return new ShaderResourceLayout(List.of(
                new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
                new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, format),
                new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
                new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
        ));
        return new ShaderResourceLayout(List.of(
                new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
                new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, format),
                new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
        ));
    }

    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("combatant", name); }
    private static int groups(int extent) { return Math.max(1, (extent + LOCAL_SIZE - 1) / LOCAL_SIZE); }
    private static float red(int argb) { return ((argb >>> 16) & 255) / 255f; }
    private static float green(int argb) { return ((argb >>> 8) & 255) / 255f; }
    private static float blue(int argb) { return (argb & 255) / 255f; }
}
