/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Frame-local directional-light cascade source.
 *
 * <p>This is geometry preparation, not a shadow renderer: it turns the authoritative primary
 * camera and explicit directional-light state into stable secondary view/projection contracts. A later
 * SHADOW_MAP producer can render those views into an atlas/array without reconstructing camera
 * state or owning cascade split policy itself.</p>
 */
final class DeferredShadowCascadeSource {
    static final int MAX_CASCADE_COUNT = 8;
    private static final float DEFAULT_NEAR_DISTANCE = 0.1f;
    private static final float BOUNDS_PADDING = 2.0f;
    private static final float RADIUS_QUANTIZATION = 16.0f;
    private static final float BASIS_EPSILON_SQUARED = 1.0e-6f;
    private static final float GRID_RESET_TEXEL_FRACTION = 0.10f;
    private static final float PHOTON_SHADOW_DISTORTION = 0.85f;
    private static final Vector3f OVERWORLD_CELESTIAL_PLANE_NORMAL = new Vector3f(0.0f, 0.0f, 1.0f);

    /**
     * Camera-translation stabilizers. They intentionally do not derive movement from the current
     * light-space position of an old anchor: doing that couples celestial basis rotation back into
     * texel snapping and makes a stationary camera periodically step forward/backward as the sun
     * moves. Only actual camera translation may advance the anchor.
     */
    private final StableGridAnchor[] gridAnchors = new StableGridAnchor[MAX_CASCADE_COUNT];

    void prepare(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView primary = context.primaryView().current();
        DirectionalLightDescriptor directional = context.worldState().directionalLight();
        if (primary == null || !directional.shadowValid()) return;

        boolean nearOnly = DeferredShadowBringupConfig.nearOnly();
        // Photon/Iris do not use cascaded shadow maps for the main directional shadow. Iris
        // provides one camera-centred orthographic projection and Photon redistributes its XY
        // texel density non-linearly in the shadow vertex/receiver shaders. Keep a single view in
        // production too: this removes CSM transition seams instead of trying to hide them with
        // ever wider cross-fades.
        float farDistance = resolveShadowReceiverDistance(primary);
        if (nearOnly) {
            farDistance = Math.min(farDistance, DeferredShadowBringupConfig.nearDistance());
        }
        float nearDistance = Math.min(DEFAULT_NEAR_DISTANCE, farDistance * 0.25f);

        float[] splits = new float[]{nearDistance, farDistance};

        Matrix4f inverseView = primary.inverseView();
        // Directional shadow geometry must be invariant under TAA sample jitter. The primary scene
        // may render with the jittered projection, but the shadow receiver/caster contract derives
        // from the authoritative unjittered camera projection.
        Matrix4f projection = primary.unjitteredProjection();
        Vector3f lightDirection = directional.direction(new Vector3f());
        Vector3f lightUp = deterministicLightUp(lightDirection);

        int resolution = context.settings().shadowResolution();
        DeferredSecondaryView view = buildCascade(
                0,
                splits[0],
                splits[1],
                splits[0],
                splits[1],
                primary.cameraPosition(),
                projection,
                inverseView,
                lightDirection,
                lightUp,
                context.settings().shadowCasterDistance(),
                resolution,
                0,
                0,
                context.rhi().capabilities().zeroToOneDepth(),
                true
        );
        context.secondaryViews().register(view);
    }


    private static float resolveShadowReceiverDistance(DeferredPrimaryViewSource.FrameView primary) {
        float projectionFar = primary.farPlane();
        float renderedFar = 0.0f;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            // The camera projection may intentionally use a very large far plane (2048 in the
            // current 26.2 path) even when terrain is only submitted for ~N chunks. Allocating CSM
            // density to that mathematical far plane wastes almost the complete far cascade and
            // turns distant visibility into multi-block shadow texels. Keep one chunk of guard
            // coverage for terrain/chunk-boundary hysteresis, but shadow only the receiver range
            // that can actually be visible.
            int chunks = Math.max(2, minecraft.options.getEffectiveRenderDistance());
            float axialDistance = (chunks + 1) * 16.0f;
            renderedFar = axialDistance * DeferredShadowBringupConfig.receiverDistanceScale();
        }

        if (projectionFar > DEFAULT_NEAR_DISTANCE && renderedFar > DEFAULT_NEAR_DISTANCE) {
            return Math.max(16.0f, Math.min(projectionFar, renderedFar));
        }
        if (renderedFar > DEFAULT_NEAR_DISTANCE) return Math.max(16.0f, renderedFar);
        if (projectionFar > DEFAULT_NEAR_DISTANCE) return Math.max(16.0f, projectionFar);
        return 128.0f;
    }

    private DeferredSecondaryView buildCascade(int index,
                                                       float nearDistance,
                                                       float farDistance,
                                                       float coverageNearDistance,
                                                       float coverageFarDistance,
                                                       Vec3 cameraOrigin,
                                                       Matrix4fc cameraProjection,
                                                       Matrix4fc inverseCameraView,
                                                       Vector3f lightDirection,
                                                       Vector3f lightUp,
                                                       float casterDistance,
                                                       int resolution,
                                                       int viewportX,
                                                       int viewportY,
                                                       boolean zeroToOneDepth,
                                                       boolean distortedSingleMap) {
        Vector3f[] corners = frustumCorners(coverageNearDistance, coverageFarDistance, cameraProjection, inverseCameraView);

        // Stable near-field shadow coverage is camera-centered, matching the fundamental layout of
        // Iris/Photon rather than following the primary frustum center. The old frustum-center
        // anchor moves when the camera yaws/pitches even if the camera position is unchanged, which
        // makes the shadow grid crawl under camera rotation. A sphere around the camera is rotation
        // invariant and still covers every receiver in this split.
        float radius = 0.0f;
        if (distortedSingleMap) {
            // Photon treats shadowDistance as the orthographic half-plane length. Every receiver
            // inside that world-space radius therefore has a representable light-space XY point;
            // the shader-side distortion decides how the fixed resolution is distributed inside
            // the map. Do not derive the footprint from camera FOV, otherwise yaw/FOV changes
            // would reintroduce density breathing.
            radius = Math.max(1.0f, coverageFarDistance);
        } else {
            for (Vector3f corner : corners) {
                radius = Math.max(radius, corner.length());
            }
            radius = Math.max(1.0f, radius + BOUNDS_PADDING);
        }
        radius = (float) Math.ceil(radius * RADIUS_QUANTIZATION) / RADIUS_QUANTIZATION;

        float extent = Math.max(0.001f, radius * 2.0f);
        float worldTexel = extent / (float) Math.max(1, resolution);
        // Warping magnifies the map centre by 1/(1-D). Snap on the smallest effective world
        // texel, not on the undistorted edge texel, otherwise one anchor step becomes a several-
        // texel jump near the camera and reintroduces camera-motion flicker.
        float stabilizationTexel = distortedSingleMap
                ? worldTexel * (1.0f - PHOTON_SHADOW_DISTORTION)
                : worldTexel;
        StableGridAnchor anchor = stabilizeCameraTranslation(
                index, cameraOrigin, lightDirection, lightUp, radius, stabilizationTexel
        );
        Vector3f center = new Vector3f(
                (float) (anchor.worldX - cameraOrigin.x),
                (float) (anchor.worldY - cameraOrigin.y),
                (float) (anchor.worldZ - cameraOrigin.z)
        );

        float effectiveCasterDistance = resolveCasterDistance(
                cameraOrigin, lightDirection, radius, casterDistance);
        Vector3f eye = new Vector3f(center).fma(
                radius + effectiveCasterDistance + BOUNDS_PADDING, lightDirection);
        Matrix4f lightView = new Matrix4f().lookAt(eye, center, lightUp);

        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        Vector3f transformed = new Vector3f();
        for (Vector3f corner : corners) {
            transformPosition(lightView, corner, transformed);
            minZ = Math.min(minZ, transformed.z);
            maxZ = Math.max(maxZ, transformed.z);
        }

        // The target point is the map center, so XY bounds stay fixed. Camera translation changes
        // the center only by integral shadow texels in the current shadow-plane basis; celestial
        // rotation merely rotates the basis around the same world anchor and never triggers a snap.
        float minX = -radius;
        float maxX = radius;
        float minY = -radius;
        float maxY = radius;

        // Keep an explicit caster band on the light-facing side of the receiver frustum.
        // Extending only the far plane would include geometry behind the receivers while clipping
        // exactly the off-screen/above-camera casters this secondary visibility path exists for.
        float lightNear = Math.max(0.01f, -maxZ - effectiveCasterDistance - BOUNDS_PADDING);
        float lightFar = Math.max(lightNear + 0.01f, -minZ + BOUNDS_PADDING);
        // Combatant/Minecraft depth is reversed-Z (GREATER/GEQUAL with clear depth 0). JOML's
        // ordinary ortho maps the geometric near plane to the low depth end, so swap the z planes
        // to keep shadow depth in the same convention as MAIN_DEPTH/Hi-Z on both backends.
        Matrix4f lightProjection = new Matrix4f().setOrtho(
                minX, maxX, minY, maxY, lightFar, lightNear, zeroToOneDepth
        );

        return new DeferredSecondaryView(
                DeferredViewFamily.SHADOW_CASCADE,
                index,
                "shadow.cascade." + index,
                lightView,
                lightProjection,
                cameraOrigin,
                0,
                viewportX,
                viewportY,
                resolution,
                resolution,
                nearDistance,
                farDistance
        );
    }


    /**
     * The configured caster distance is a quality floor, not a safe world-coverage bound. A fixed
     * 64-block band can clip the terrain roof above a deep cave while the receiver still projects
     * perfectly into the CSM, which turns the missing caster into full sunlight. Extend the
     * light-facing depth far enough to cross the dimension's vertical build span for the current
     * light elevation. The XY shadow footprint and resolution are unchanged.
     */
    private static float resolveCasterDistance(Vec3 cameraOrigin,
                                               Vector3f lightDirection,
                                               float receiverRadius,
                                               float configuredDistance) {
        float configured = Math.max(0.0f, configuredDistance);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return configured;

        float minY = minecraft.level.getMinY();
        float maxY = minY + minecraft.level.getHeight();
        float cameraY = (float) cameraOrigin.y;
        float lightY = lightDirection.y;
        float absLightY = Math.abs(lightY);

        // At grazing angles the vertical distance maps to a long light ray. Clamp the denominator
        // rather than letting a near-horizontal celestial direction explode the projection depth;
        // the runtime shadow contract already caps caster distance at 1024 blocks.
        float safeLightY = Math.max(absLightY, 0.25f);
        float receiverExtremeY = lightY >= 0.0f
                ? Math.max(minY, cameraY - receiverRadius)
                : Math.min(maxY, cameraY + receiverRadius);
        float verticalDistance = lightY >= 0.0f
                ? Math.max(0.0f, maxY - receiverExtremeY)
                : Math.max(0.0f, receiverExtremeY - minY);
        float worldCoverageDistance = verticalDistance / safeLightY + BOUNDS_PADDING;
        return Math.min(1024.0f, Math.max(configured, worldCoverageDistance));
    }

    private StableGridAnchor stabilizeCameraTranslation(int index,
                                                        Vec3 cameraOrigin,
                                                        Vector3f lightDirection,
                                                        Vector3f lightUp,
                                                        float radius,
                                                        float worldTexel) {
        int slot = Math.max(0, Math.min(MAX_CASCADE_COUNT - 1, index));
        StableGridAnchor anchor = gridAnchors[slot];
        if (anchor == null) {
            anchor = new StableGridAnchor();
            gridAnchors[slot] = anchor;
        }

        double teleportLimit = Math.max(16.0, radius);
        double dx = anchor.valid ? cameraOrigin.x - anchor.previousCameraX : 0.0;
        double dy = anchor.valid ? cameraOrigin.y - anchor.previousCameraY : 0.0;
        double dz = anchor.valid ? cameraOrigin.z - anchor.previousCameraZ : 0.0;
        double cameraDeltaSquared = dx * dx + dy * dy + dz * dz;
        float texelDelta = anchor.valid && anchor.worldTexel > 1.0e-6f
                ? Math.abs(worldTexel - anchor.worldTexel) / anchor.worldTexel
                : Float.POSITIVE_INFINITY;

        if (!anchor.valid
                || cameraDeltaSquared > teleportLimit * teleportLimit
                || texelDelta > GRID_RESET_TEXEL_FRACTION) {
            anchor.reset(cameraOrigin, worldTexel);
            return anchor;
        }

        Vector3f direction = new Vector3f(lightDirection).normalize();
        Vector3f forward = new Vector3f(direction).negate();
        Vector3f right = new Vector3f(forward).cross(lightUp);
        if (right.lengthSquared() <= BASIS_EPSILON_SQUARED) {
            anchor.reset(cameraOrigin, worldTexel);
            return anchor;
        }
        right.normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();

        // Residual values live in shadow-plane coordinates. When the light rotates, the residual
        // coordinates are deliberately retained instead of re-projecting an old world-space error;
        // therefore light rotation alone cannot cross a snapping threshold.
        Vector3f cameraDelta = new Vector3f((float) dx, (float) dy, (float) dz);
        anchor.residualX += cameraDelta.dot(right);
        anchor.residualY += cameraDelta.dot(up);

        int stepX = nearestIntegerStep(anchor.residualX / Math.max(worldTexel, 1.0e-6f));
        int stepY = nearestIntegerStep(anchor.residualY / Math.max(worldTexel, 1.0e-6f));
        if (stepX != 0 || stepY != 0) {
            float moveX = stepX * worldTexel;
            float moveY = stepY * worldTexel;
            anchor.worldX += right.x * moveX + up.x * moveY;
            anchor.worldY += right.y * moveX + up.y * moveY;
            anchor.worldZ += right.z * moveX + up.z * moveY;
            anchor.residualX -= moveX;
            anchor.residualY -= moveY;
        }

        // Motion along the light axis does not change shadow-map XY. Track it continuously so the
        // receiver/caster depth interval remains centered without introducing a visible XY snap.
        float lightDepthDelta = cameraDelta.dot(direction);
        anchor.worldX += direction.x * lightDepthDelta;
        anchor.worldY += direction.y * lightDepthDelta;
        anchor.worldZ += direction.z * lightDepthDelta;

        anchor.previousCameraX = cameraOrigin.x;
        anchor.previousCameraY = cameraOrigin.y;
        anchor.previousCameraZ = cameraOrigin.z;
        anchor.worldTexel = worldTexel;
        return anchor;
    }

    private static int nearestIntegerStep(float value) {
        if (!Float.isFinite(value)) return 0;
        return value >= 0.0f
                ? (int) Math.floor(value + 0.5f)
                : (int) Math.ceil(value - 0.5f);
    }

    private static final class StableGridAnchor {
        double worldX;
        double worldY;
        double worldZ;
        double previousCameraX;
        double previousCameraY;
        double previousCameraZ;
        float residualX;
        float residualY;
        float worldTexel;
        boolean valid;

        void reset(Vec3 camera, float texel) {
            worldX = camera.x;
            worldY = camera.y;
            worldZ = camera.z;
            previousCameraX = camera.x;
            previousCameraY = camera.y;
            previousCameraZ = camera.z;
            residualX = 0.0f;
            residualY = 0.0f;
            worldTexel = texel;
            valid = true;
        }
    }

    private static Vector3f deterministicLightUp(Vector3f lightDirection) {
        Vector3f direction = new Vector3f(lightDirection).normalize();
        Vector3f up = new Vector3f(OVERWORLD_CELESTIAL_PLANE_NORMAL);
        up.fma(-up.dot(direction), direction);
        if (up.lengthSquared() > BASIS_EPSILON_SQUARED) {
            return up.normalize();
        }

        float ax = Math.abs(direction.x);
        float ay = Math.abs(direction.y);
        float az = Math.abs(direction.z);
        Vector3f helper = ax <= ay && ax <= az
                ? new Vector3f(1.0f, 0.0f, 0.0f)
                : (ay <= az
                ? new Vector3f(0.0f, 1.0f, 0.0f)
                : new Vector3f(0.0f, 0.0f, 1.0f));
        up.set(helper).fma(-helper.dot(direction), direction);
        if (up.lengthSquared() <= BASIS_EPSILON_SQUARED) {
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return up.normalize();
    }

    private static Vector3f[] frustumCorners(float nearDistance,
                                              float farDistance,
                                              Matrix4fc projection,
                                              Matrix4fc inverseView) {
        Vector3f[] corners = new Vector3f[8];
        int cursor = 0;
        for (float distance : new float[]{nearDistance, farDistance}) {
            for (int y = 0; y < 2; y++) {
                float ndcY = y == 0 ? -1.0f : 1.0f;
                for (int x = 0; x < 2; x++) {
                    float ndcX = x == 0 ? -1.0f : 1.0f;
                    float viewX = distance * (ndcX + projection.m20()) / projection.m00();
                    float viewY = distance * (ndcY + projection.m21()) / projection.m11();
                    float viewZ = -distance;
                    corners[cursor++] = transformDirection(
                            inverseView, new Vector3f(viewX, viewY, viewZ), new Vector3f()
                    );
                }
            }
        }
        return corners;
    }

    private static Vector3f transformDirection(Matrix4fc matrix, Vector3f source, Vector3f dest) {
        float x = source.x;
        float y = source.y;
        float z = source.z;
        return dest.set(
                matrix.m00() * x + matrix.m10() * y + matrix.m20() * z,
                matrix.m01() * x + matrix.m11() * y + matrix.m21() * z,
                matrix.m02() * x + matrix.m12() * y + matrix.m22() * z
        );
    }

    private static Vector3f transformPosition(Matrix4fc matrix, Vector3f source, Vector3f dest) {
        float x = source.x;
        float y = source.y;
        float z = source.z;
        return dest.set(
                matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30(),
                matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31(),
                matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32()
        );
    }

}
