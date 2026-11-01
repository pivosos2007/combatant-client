/* Pure screen-space gate shared by aim assistance and its regression tests. */
package combatant.client.util.target;

public final class ScreenRingSelection {
    private ScreenRingSelection() { }

    public static boolean contains(double x, double y, double width, double height, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || width <= 0 || height <= 0 || radius <= 0)
            return false;
        double dx = x - width * 0.5;
        double dy = y - height * 0.5;
        return dx * dx + dy * dy <= radius * radius;
    }

    /** ScreenProjection returns coordinates in the ACTIVE ViewportContext,
     * not necessarily framebuffer pixels. Convert against that exact source size. */
    public static double toLogical(double projected, double sourceExtent, double logicalExtent) {
        if (!Double.isFinite(projected) || sourceExtent <= 0 || logicalExtent <= 0)
            return Double.NaN;
        return projected * (logicalExtent / sourceExtent);
    }

    /** Slight target retention while remaining strictly inside the displayed ring. */
    public static double score(double distanceSquared, double retentionPixels) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return Double.POSITIVE_INFINITY;
        double adjusted = Math.max(0.0, Math.sqrt(distanceSquared) - Math.max(0.0, retentionPixels));
        return adjusted * adjusted;
    }
}
