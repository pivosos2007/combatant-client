/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import combatant.client.render.sodium.SodiumTerrainSubmission;
import combatant.client.util.logging.DebugLog;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Production shadow-map source for the backend graph.
 *
 * <p>The producer reuses Sodium's already-uploaded section geometry, but supplies independent
 * cascade visibility lists, matrices, render areas and a Combatant-owned atlas. It never mutates
 * the primary Minecraft camera or Sodium's primary visibility tree.</p>
 */
final class DeferredShadowMapSource implements AutoCloseable {
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
    private static final Std430StructLayout CASCADE_LAYOUT = Std430StructLayout.builder()
            .member("viewProjection", Std430Type.MAT4)
            .member("atlasScaleBias", Std430Type.VEC4)
            .member("splitRange", Std430Type.VEC4)
            .member("shadowTexel", Std430Type.VEC4)
            .build();

    private final DeferredSecondaryShadowCasterSource secondaryCasters;
    private TextureTarget atlas;
    private int atlasWidth;
    private int atlasHeight;
    private CombatantRhi bufferOwner;
    private RhiStorageBuffer cascadeData;

    DeferredShadowMapSource(DeferredSecondaryShadowCasterSource secondaryCasters) {
        this.secondaryCasters = secondaryCasters;
    }

    boolean available(DeferredPassContext context) {
        if (context == null || !context.secondaryViews().has(DeferredViewFamily.SHADOW_CASCADE)) return false;
        if (CombatantRenderSystem.sodium().terrainInterop().currentSubmission() == null) return false;
        return SodiumWorldRenderer.instanceNullable() != null;
    }

    void render(DeferredPassContext context) {
        RenderSystem.assertOnRenderThread();
        List<DeferredSecondaryView> views = context.secondaryViews().views(DeferredViewFamily.SHADOW_CASCADE);
        if (views.isEmpty()) return;

        SodiumTerrainSubmission primarySubmission = CombatantRenderSystem.sodium().terrainInterop().currentSubmission();
        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
        if (primarySubmission == null || renderer == null) return;

        int requiredWidth = 1;
        int requiredHeight = 1;
        for (DeferredSecondaryView view : views) {
            if (!view.hasExplicitViewport()) {
                throw new IllegalStateException("Shadow cascade has no atlas viewport: " + view.id());
            }
            requiredWidth = Math.max(requiredWidth, view.viewportX() + view.viewportWidth());
            requiredHeight = Math.max(requiredHeight, view.viewportY() + view.viewportHeight());
        }

        TextureTarget target = ensureAtlas(requiredWidth, requiredHeight);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(
                target.getColorTexture(), CLEAR_COLOR,
                target.getDepthTexture(), 0.0
        );

        for (DeferredSecondaryView view : views) {
            secondaryCasters.render(
                    context,
                    SodiumSecondaryTerrainContext.Purpose.SHADOW_DEPTH,
                    view,
                    target,
                    renderer,
                    primarySubmission
            );
        }

        RhiStorageBuffer metadata = uploadCascadeMetadata(context.rhi(), views, requiredWidth, requiredHeight);
        context.resources().bindTexture(DeferredResource.SHADOW_DEPTH, target.getDepthTextureView());
        context.resources().bindBuffer(DeferredResource.SHADOW_CASCADE_DATA, metadata);
    }

    private TextureTarget ensureAtlas(int width, int height) {
        if (atlas != null && atlasWidth == width && atlasHeight == height) return atlas;
        releaseAtlas();
        atlas = new TextureTarget(
                "combatant-deferred-shadow-atlas",
                Math.max(1, width), Math.max(1, height), true,
                GpuFormat.RGBA8_UNORM
        );
        atlasWidth = Math.max(1, width);
        atlasHeight = Math.max(1, height);
        return atlas;
    }

    private RhiStorageBuffer uploadCascadeMetadata(CombatantRhi rhi,
                                                    List<DeferredSecondaryView> views,
                                                    int width,
                                                    int height) {
        if (bufferOwner != rhi) {
            releaseCascadeBuffer();
            bufferOwner = rhi;
        }
        if (cascadeData == null) {
            cascadeData = rhi.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-shadow-cascade-data",
                    CASCADE_LAYOUT,
                    DeferredShadowCascadeSource.MAX_CASCADE_COUNT,
                    StorageAccess.READ_ONLY,
                    false
            ));
        }

        Std430Writer writer = new Std430Writer(CASCADE_LAYOUT, DeferredShadowCascadeSource.MAX_CASCADE_COUNT);
        int count = Math.min(views.size(), DeferredShadowCascadeSource.MAX_CASCADE_COUNT);
        StringBuilder footprint = DebugLog.isEnabled()
                ? new StringBuilder(160).append("count=").append(count)
                .append(" mode=").append(DeferredShadowBringupConfig.nearOnly() ? "near-only" : "csm")
                .append(" atlas=").append(width).append('x').append(height)
                : null;
        for (int i = 0; i < count; i++) {
            DeferredSecondaryView view = views.get(i);
            Matrix4f viewProjection = view.viewProjection();
            writer.putMat4(i, "viewProjection", viewProjection);
            writer.putVec4(i, "atlasScaleBias",
                    (float) view.viewportWidth() / (float) width,
                    (float) view.viewportHeight() / (float) height,
                    (float) view.viewportX() / (float) width,
                    (float) view.viewportY() / (float) height
            );
            writer.putVec4(i, "splitRange",
                    view.nearPlane(), view.farPlane(), (float) i, (float) count
            );
            Matrix4f projection = view.projection();
            float extentX = Math.abs(projection.m00()) > 1.0e-6f ? Math.abs(2.0f / projection.m00()) : 0.0f;
            float extentY = Math.abs(projection.m11()) > 1.0e-6f ? Math.abs(2.0f / projection.m11()) : 0.0f;
            float worldTexel = Math.max(
                    extentX / Math.max(1.0f, view.viewportWidth()),
                    extentY / Math.max(1.0f, view.viewportHeight())
            );
            // Orthographic depth is linear. Store the normalized shadow-depth change produced by
            // one world-space texel so receiver bias can be expressed in texels without depending
            // on the shading normal/normal map.
            float depthPerWorldUnit = Math.abs(projection.m22())
                    * (rhi.capabilities().zeroToOneDepth() ? 1.0f : 0.5f);
            writer.putVec4(i, "shadowTexel",
                    1.0f / Math.max(1.0f, width),
                    1.0f / Math.max(1.0f, height),
                    worldTexel,
                    depthPerWorldUnit * worldTexel
            );
            if (footprint != null) {
                footprint.append(" c").append(i)
                        .append("[")
                        .append(String.format(java.util.Locale.ROOT, "%.2f..%.2f", view.nearPlane(), view.farPlane()))
                        .append(" texel=")
                        .append(String.format(java.util.Locale.ROOT, "%.5f", worldTexel))
                        .append(" tile=").append(view.viewportWidth()).append('x').append(view.viewportHeight())
                        .append(']');
            }
        }
        if (footprint != null) {
            String footprintState = footprint.toString();
            DebugLog.infoOnChange(
                    "combatant.deferred.shadow-footprint",
                    footprintState,
                    "[Deferred][Shadow] %s", footprintState
            );
        }
        cascadeData.upload(writer.buffer(), 0L);
        return cascadeData;
    }

    void release(CombatantRhi owner) {
        if (owner != null && bufferOwner != null && owner != bufferOwner) return;
        releaseCascadeBuffer();
        releaseAtlas();
    }

    private void releaseCascadeBuffer() {
        if (cascadeData != null) {
            try {
                cascadeData.close();
            } catch (Throwable ignored) {
                // Device teardown remains authoritative if the native buffer can no longer close.
            }
            cascadeData = null;
        }
        bufferOwner = null;
    }

    private void releaseAtlas() {
        if (atlas != null) {
            atlas.destroyBuffers();
            atlas = null;
        }
        atlasWidth = 0;
        atlasHeight = 0;
    }

    @Override
    public void close() {
        release(null);
    }
}
