/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

/**
 * Explicit analytic local-light contract.
 *
 * <p>Positions are absolute world-space doubles so producers do not lose precision. RGB values are
 * linear-light source intensity. Radius is a finite influence bound used for culling; it is not an
 * artistic brightness multiplier. Consumers convert the position to camera-relative/view space
 * before upload. Shadow participation is explicit producer data; the renderer never infers it from
 * source color, light type or screen-space appearance. Shadow-casting descriptors should provide
 * a non-zero stable ID; without stable identity the bounded atlas intentionally leaves the light
 * unshadowed rather than inventing persistence from position/color heuristics.</p>
 */
public record LightDescriptor(
        Type type,
        double x,
        double y,
        double z,
        float directionX,
        float directionY,
        float directionZ,
        float red,
        float green,
        float blue,
        float radius,
        float innerConeCos,
        float outerConeCos,
        float areaRadius,
        long stableId,
        boolean castsShadow,
        int shadowCasterExclusionEntityId
) {
    public enum Type {
        POINT,
        SPOT,
        SPHERE
    }

    public LightDescriptor {
        type = type == null ? Type.POINT : type;
        if (!Double.isFinite(x)) x = 0.0;
        if (!Double.isFinite(y)) y = 0.0;
        if (!Double.isFinite(z)) z = 0.0;
        directionX = finite(directionX, 0.0f);
        directionY = finite(directionY, -1.0f);
        directionZ = finite(directionZ, 0.0f);
        red = nonNegative(red);
        green = nonNegative(green);
        blue = nonNegative(blue);
        radius = Math.max(0.0f, finite(radius, 0.0f));
        areaRadius = Math.max(0.0f, finite(areaRadius, 0.0f));
        innerConeCos = clamp(finite(innerConeCos, 1.0f), -1.0f, 1.0f);
        outerConeCos = clamp(finite(outerConeCos, -1.0f), -1.0f, 1.0f);
        if (outerConeCos > innerConeCos) {
            float swap = outerConeCos;
            outerConeCos = innerConeCos;
            innerConeCos = swap;
        }
    }

    /** Source-compatible constructor for providers using the pre-exclusion shadow contract. */
    public LightDescriptor(Type type,
                           double x, double y, double z,
                           float directionX, float directionY, float directionZ,
                           float red, float green, float blue,
                           float radius, float innerConeCos, float outerConeCos,
                           float areaRadius, long stableId, boolean castsShadow) {
        this(type, x, y, z, directionX, directionY, directionZ,
                red, green, blue, radius, innerConeCos, outerConeCos, areaRadius,
                stableId, castsShadow, -1);
    }

    /** Source-compatible constructor for providers that do not request local shadows. */
    public LightDescriptor(Type type,
                           double x, double y, double z,
                           float directionX, float directionY, float directionZ,
                           float red, float green, float blue,
                           float radius, float innerConeCos, float outerConeCos,
                           float areaRadius, long stableId) {
        this(type, x, y, z, directionX, directionY, directionZ,
                red, green, blue, radius, innerConeCos, outerConeCos, areaRadius, stableId, false, -1);
    }

    public boolean valid() {
        return radius > 0.0f && (red > 0.0f || green > 0.0f || blue > 0.0f);
    }

    public static LightDescriptor point(long stableId,
                                        double x, double y, double z,
                                        float red, float green, float blue,
                                        float radius) {
        return new LightDescriptor(Type.POINT, x, y, z, 0.0f, -1.0f, 0.0f,
                red, green, blue, radius, 1.0f, -1.0f, 0.0f, stableId, false, -1);
    }

    public static LightDescriptor spot(long stableId,
                                       double x, double y, double z,
                                       float directionX, float directionY, float directionZ,
                                       float red, float green, float blue,
                                       float radius, float innerConeCos, float outerConeCos) {
        return new LightDescriptor(Type.SPOT, x, y, z, directionX, directionY, directionZ,
                red, green, blue, radius, innerConeCos, outerConeCos, 0.0f, stableId, false, -1);
    }

    public static LightDescriptor sphere(long stableId,
                                         double x, double y, double z,
                                         float red, float green, float blue,
                                         float radius, float areaRadius) {
        return new LightDescriptor(Type.SPHERE, x, y, z, 0.0f, -1.0f, 0.0f,
                red, green, blue, radius, 1.0f, -1.0f, areaRadius, stableId, false, -1);
    }

    /** Returns the same physical light with explicit local-shadow participation. */
    public LightDescriptor withShadowCasting(boolean value) {
        return new LightDescriptor(type, x, y, z,
                directionX, directionY, directionZ,
                red, green, blue, radius,
                innerConeCos, outerConeCos, areaRadius,
                stableId, value, shadowCasterExclusionEntityId);
    }

    /** Excludes the light-owning entity from its own local shadow map. */
    public LightDescriptor withShadowCasterExclusion(int entityId) {
        return new LightDescriptor(type, x, y, z,
                directionX, directionY, directionZ,
                red, green, blue, radius,
                innerConeCos, outerConeCos, areaRadius,
                stableId, castsShadow, entityId);
    }

    private static float nonNegative(float value) {
        return Math.max(0.0f, finite(value, 0.0f));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
