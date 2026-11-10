/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.visibility;

import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.policy.VisibilityProvider;
import combatant.client.render.engine.rhi.RhiCapabilities;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Immutable, view-aware visibility/LOD input snapshot.
 *
 * <p>PRIMARY uses stable non-jittered matrices. TAA jitter is excluded so it cannot
 * produce culling or LOD flicker. Section visibility is a coarse optimization and is separately
 * capability-gated from fine frustum/projection work.</p>
 */
public final class SceneViewContext {
    private final long frameId;
    private final SceneViewType type;
    private final Vec3 cameraPosition;
    private final Matrix4f viewMatrix;
    private final Matrix4f projectionMatrix;
    private final Matrix4f frustumProjectionMatrix;
    private final int viewportWidth;
    private final int viewportHeight;
    private final boolean zeroToOneDepth;
    private final boolean stableProjectionAvailable;
    private final VisibilityProvider sectionVisibilityProvider;
    private final boolean sectionVisibilityEnabled;
    private final SceneFrustum frustum;

    private SceneViewContext(long frameId,
                             SceneViewType type,
                             Vec3 cameraPosition,
                             Matrix4fc viewMatrix,
                             Matrix4fc projectionMatrix,
                             Matrix4fc frustumProjectionMatrix,
                             int viewportWidth,
                             int viewportHeight,
                             boolean zeroToOneDepth,
                             boolean stableProjectionAvailable,
                             VisibilityProvider sectionVisibilityProvider,
                             boolean sectionVisibilityEnabled) {
        this.frameId = frameId;
        this.type = type == null ? SceneViewType.DEBUG : type;
        this.cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
        this.viewMatrix = new Matrix4f(viewMatrix);
        this.projectionMatrix = new Matrix4f(projectionMatrix);
        this.frustumProjectionMatrix = new Matrix4f(frustumProjectionMatrix);
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
        this.zeroToOneDepth = zeroToOneDepth;
        this.stableProjectionAvailable = stableProjectionAvailable;
        this.sectionVisibilityProvider = sectionVisibilityProvider == null
                ? VisibilityProvider.ALWAYS_VISIBLE : sectionVisibilityProvider;
        this.sectionVisibilityEnabled = sectionVisibilityEnabled;
        this.frustum = new SceneFrustum(this.viewMatrix, this.frustumProjectionMatrix,
                this.cameraPosition, zeroToOneDepth);
    }

    public static SceneViewContext primary(RenderFrameContext frame, RhiCapabilities capabilities) {
        if (frame == null) throw new IllegalArgumentException("render frame context is required");
        boolean stableAvailable = CombatantWorldMatrices.isValid();
        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        Matrix4f stableProjection = CombatantWorldMatrices.unjitteredRenderProjectionMatrix();
        Matrix4f frustumProjection = CombatantWorldMatrices.frustumProjectionMatrix();

        if (view == null && frame.camera() != null) view = frame.camera().modelView();
        if (stableProjection == null && frame.camera() != null) stableProjection = frame.camera().projection();
        if (frustumProjection == null) frustumProjection = stableProjection;
        if (view == null) view = new Matrix4f();
        if (stableProjection == null) stableProjection = new Matrix4f();
        if (frustumProjection == null) frustumProjection = new Matrix4f(stableProjection);

        Vec3 camera = frame.camera() != null ? frame.camera().position() : Vec3.ZERO;
        int width = frame.viewport() != null ? frame.viewport().framebufferWidth() : 1;
        int height = frame.viewport() != null ? frame.viewport().framebufferHeight() : 1;
        boolean zeroToOne = capabilities != null && capabilities.zeroToOneDepth();
        return new SceneViewContext(
                frame.frameId(), SceneViewType.PRIMARY, camera,
                view, stableProjection, frustumProjection,
                width, height, zeroToOne, stableAvailable,
                frame.visibilityProvider(), true
        );
    }

    /**
     * Explicit factory for shaderpack shadow/reflection/probe adapters. Section visibility is off by
     * default because PRIMARY Sodium occlusion must not be reused for another view.
     */
    public static SceneViewContext secondary(long frameId,
                                              SceneViewType type,
                                              Vec3 cameraPosition,
                                              Matrix4fc viewMatrix,
                                              Matrix4fc projectionMatrix,
                                              int viewportWidth,
                                              int viewportHeight,
                                              boolean zeroToOneDepth) {
        if (type == SceneViewType.PRIMARY) {
            throw new IllegalArgumentException("Use primary(...) for the primary camera view");
        }
        return new SceneViewContext(
                frameId, type, cameraPosition,
                viewMatrix, projectionMatrix, projectionMatrix,
                viewportWidth, viewportHeight, zeroToOneDepth, true,
                VisibilityProvider.ALWAYS_VISIBLE, false
        );
    }

    public SceneViewContext withSectionVisibility(VisibilityProvider provider) {
        return new SceneViewContext(
                frameId, type, cameraPosition, viewMatrix, projectionMatrix, frustumProjectionMatrix,
                viewportWidth, viewportHeight, zeroToOneDepth, stableProjectionAvailable,
                provider, provider != null && provider != VisibilityProvider.ALWAYS_VISIBLE
        );
    }

    public long frameId() { return frameId; }
    public SceneViewType type() { return type; }
    public Vec3 cameraPosition() { return cameraPosition; }
    public Matrix4f viewMatrix() { return new Matrix4f(viewMatrix); }
    public Matrix4f projectionMatrix() { return new Matrix4f(projectionMatrix); }
    public Matrix4f frustumProjectionMatrix() { return new Matrix4f(frustumProjectionMatrix); }
    public int viewportWidth() { return viewportWidth; }
    public int viewportHeight() { return viewportHeight; }
    public boolean zeroToOneDepth() { return zeroToOneDepth; }
    public boolean stableProjectionAvailable() { return stableProjectionAvailable; }
    public VisibilityProvider sectionVisibilityProvider() { return sectionVisibilityProvider; }
    public boolean sectionVisibilityEnabled() { return sectionVisibilityEnabled; }
    public SceneFrustum frustum() { return frustum; }

    public boolean isInStableFrustum(AABB box) {
        return !stableProjectionAvailable || frustum.isVisible(box);
    }

    /**
     * FOV/projection-aware projected sphere radius in framebuffer pixels. This is intentionally
     * exposed by the view contract for the next LOD/tiny-object policy CUT; callers do not need to
     * recover FOV from a game option or assume a perspective projection.
     */
    public float projectedSphereRadiusPixels(double worldX, double worldY, double worldZ, double radius) {
        if (!stableProjectionAvailable) return Float.POSITIVE_INFINITY;
        Vector4f viewPosition = new Vector4f(
                (float) (worldX - cameraPosition.x),
                (float) (worldY - cameraPosition.y),
                (float) (worldZ - cameraPosition.z),
                1.0f
        );
        viewMatrix.transform(viewPosition);
        projectionMatrix.transform(viewPosition);
        float w = Math.abs(viewPosition.w);
        if (!(w > 1.0e-6f) || !Float.isFinite(w)) return Float.POSITIVE_INFINITY;

        float pixelsPerUnitX = Math.abs(projectionMatrix.m00()) * viewportWidth * 0.5f / w;
        float pixelsPerUnitY = Math.abs(projectionMatrix.m11()) * viewportHeight * 0.5f / w;
        float pixelsPerUnit = Math.max(pixelsPerUnitX, pixelsPerUnitY);
        return (float) (Math.max(0.0, radius) * pixelsPerUnit);
    }

    public float projectedSphereDiameterPixels(double worldX, double worldY, double worldZ, double radius) {
        return projectedSphereRadiusPixels(worldX, worldY, worldZ, radius) * 2.0f;
    }
}
