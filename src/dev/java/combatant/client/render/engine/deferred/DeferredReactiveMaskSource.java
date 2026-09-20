/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.material.MaterialRegistry;
import combatant.client.render.engine.material.MaterialTemporalPolicy;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the shared reactive mask from explicit material temporal policy and geometry ownership. */
final class DeferredReactiveMaskSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final int TABLE_SIZE = 8192;
    private static final int TABLE_MASK = TABLE_SIZE - 1;
    private static final int MAX_PROBES = 32;
    private static final Identifier SHADER = id("deferred/reactive_mask");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout ENTRY_LAYOUT = Std430StructLayout.builder()
            .member("materialId", Std430Type.UINT)
            .member("occupied", Std430Type.UINT)
            .member("pad0", Std430Type.UINT)
            .member("pad1", Std430Type.UINT)
            .member("policy", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer params;
    private RhiStorageBuffer table;
    private int tableHash = Integer.MIN_VALUE;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.temporal.reactive-mask", DeferredStage.PRE_TRANSLUCENCY_TEMPORAL_VALIDATION)
                .priority(-100)
                .read(DeferredResource.GBUFFER_DEPTH, DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_MATERIAL_ID)
                .write(DeferredResource.REACTIVE_MASK)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_MATERIAL_ID) != null)
                .execute(this::render)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        params();
        table();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void render(DeferredPassContext context) {
        ensureOwner(context.rhi());
        uploadTable();
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView materialId = requireTexture(context, DeferredResource.GBUFFER_MATERIAL_ID);
        RhiStorageImage output = requireImage(context, DeferredResource.REACTIVE_MASK);

        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "params", TABLE_MASK, MAX_PROBES, 1.0e-5f, 0.0f);
        params().upload(writer.buffer(), 0L);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant temporal reactive mask",
                pipeline(), groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(4, params(), 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(5, table(), 0L, ENTRY_LAYOUT.arrayStride() * TABLE_SIZE, StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, gbufferDepth, nearest),
                        new SampledTextureBinding(1, resolvedDepth, nearest),
                        new SampledTextureBinding(2, materialId, nearest)
                ),
                List.of(new StorageImageBinding(3, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void uploadTable() {
        Map<Integer, MaterialTemporalPolicy> policies = MaterialRegistry.global().temporalPoliciesSnapshot();
        int hash = policies.hashCode();
        if (hash == tableHash) return;

        // Build with an occupancy shadow so the CPU and shader use the same bounded probe chain.
        boolean[] occupied = new boolean[TABLE_SIZE];
        int[] ids = new int[TABLE_SIZE];
        float[] reactive = new float[TABLE_SIZE];
        java.util.Arrays.fill(reactive, 1.0f);
        for (Map.Entry<Integer, MaterialTemporalPolicy> entry : policies.entrySet()) {
            int materialId = entry.getKey();
            int slot = hashMaterial(materialId) & TABLE_MASK;
            for (int probe = 0; probe < MAX_PROBES; probe++) {
                int index = (slot + probe) & TABLE_MASK;
                if (!occupied[index] || ids[index] == materialId) {
                    occupied[index] = true;
                    ids[index] = materialId;
                    reactive[index] = entry.getValue().reactiveValue();
                    break;
                }
            }
        }
        Std430Writer writer = new Std430Writer(ENTRY_LAYOUT, TABLE_SIZE);
        for (int i = 0; i < TABLE_SIZE; i++) {
            writer.putInt(i, "materialId", ids[i]).putInt(i, "occupied", occupied[i] ? 1 : 0)
                    .putInt(i, "pad0", 0).putInt(i, "pad1", 0)
                    .putVec4(i, "policy", reactive[i], 0.0f, 0.0f, 0.0f);
        }
        table().upload(writer.buffer(), 0L);
        tableHash = hash;
    }

    private static int hashMaterial(int id) {
        return id * 0x9E3779B1;
    }


    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (owner == null) throw new IllegalStateException("Reactive mask source has no RHI owner");
        if (pipeline == null) pipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-temporal-reactive-mask", SHADER, LAYOUT));
        return pipeline;
    }

    private RhiStorageBuffer params() {
        if (owner == null) throw new IllegalStateException("Reactive mask source has no RHI owner");
        if (params == null) params = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-temporal-reactive-mask-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        return params;
    }

    private RhiStorageBuffer table() {
        if (owner == null) throw new IllegalStateException("Reactive mask source has no RHI owner");
        if (table == null) table = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-temporal-reactive-materials", ENTRY_LAYOUT, TABLE_SIZE, StorageAccess.READ_ONLY, false));
        return table;
    }

    private void closeOwned() {
        if (pipeline != null) { try { pipeline.close(); } catch (Throwable ignored) { } pipeline = null; }
        if (params != null) { try { params.close(); } catch (Throwable ignored) { } params = null; }
        if (table != null) { try { table.close(); } catch (Throwable ignored) { } table = null; }
        tableHash = Integer.MIN_VALUE;
    }

    @Override public void close() { closeOwned(); owner = null; }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static int groups(int extent) { return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE); }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("combatant", path); }
}
