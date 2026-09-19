/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a reusable vector/data coordinate system into a retained UI viewport.
 *
 * <p>The mapping is deliberately independent from {@link UiRenderTransform}: viewBox coordinates
 * first become logical UI coordinates, then the root/widget render transform converts those logical
 * coordinates into framebuffer/render coordinates. This keeps authored data coordinates stable when
 * a HUD widget animates or changes GUI scale.</p>
 */
public final class UiVectorSpace {
    private final UiBounds viewport;
    private final double minX;
    private final double minY;
    private final double width;
    private final double height;
    private final double scaleX;
    private final double scaleY;
    private final double originX;
    private final double originY;
    private final boolean yUp;

    private UiVectorSpace(UiBounds viewport,
                          double minX,
                          double minY,
                          double width,
                          double height,
                          double scaleX,
                          double scaleY,
                          double originX,
                          double originY,
                          boolean yUp) {
        this.viewport = viewport;
        this.minX = minX;
        this.minY = minY;
        this.width = width;
        this.height = height;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.originX = originX;
        this.originY = originY;
        this.yUp = yUp;
    }

    public static UiVectorSpace from(UiProps props, UiBounds viewport) {
        if (props == null || viewport == null) return null;
        double[] box = readViewBox(props.get("viewBox"));
        if (box == null) {
            box = new double[]{
                    props.number("viewBoxX", 0.0f),
                    props.number("viewBoxY", 0.0f),
                    props.number("viewBoxWidth", Math.max(1.0f, viewport.width())),
                    props.number("viewBoxHeight", Math.max(1.0f, viewport.height()))
            };
        }
        double minX = box[0];
        double minY = box[1];
        double width = box[2];
        double height = box[3];
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0.0 || height <= 0.0) {
            if (UiRuntimeValidation.enabled()) {
                throw UiRuntimeValidation.invalid("UI vector viewBox must contain finite positive width/height values.");
            }
            minX = 0.0;
            minY = 0.0;
            width = Math.max(1.0, viewport.width());
            height = Math.max(1.0, viewport.height());
        }

        String aspect = props.string("preserveAspectRatio", "meet").trim().toLowerCase(Locale.ROOT);
        double rawScaleX = viewport.width() / width;
        double rawScaleY = viewport.height() / height;
        double scaleX;
        double scaleY;
        double offsetX = 0.0;
        double offsetY = 0.0;
        if (aspect.equals("none") || aspect.startsWith("none ")) {
            scaleX = rawScaleX;
            scaleY = rawScaleY;
        } else {
            boolean slice = aspect.contains("slice");
            double uniformScale = slice ? Math.max(rawScaleX, rawScaleY) : Math.min(rawScaleX, rawScaleY);
            if (!Double.isFinite(uniformScale) || uniformScale <= 0.0) uniformScale = 1.0;
            scaleX = uniformScale;
            scaleY = uniformScale;
            double contentW = width * uniformScale;
            double contentH = height * uniformScale;
            String align = props.string("viewBoxAlign", inferAlign(aspect)).trim().toLowerCase(Locale.ROOT);
            offsetX = align.contains("xmin") || align.contains("left")
                    ? 0.0
                    : align.contains("xmax") || align.contains("right")
                    ? viewport.width() - contentW
                    : (viewport.width() - contentW) * 0.5;
            offsetY = align.contains("ymin") || align.contains("top")
                    ? 0.0
                    : align.contains("ymax") || align.contains("bottom")
                    ? viewport.height() - contentH
                    : (viewport.height() - contentH) * 0.5;
        }
        if (!Double.isFinite(scaleX) || scaleX <= 0.0) scaleX = 1.0;
        if (!Double.isFinite(scaleY) || scaleY <= 0.0) scaleY = 1.0;

        String axis = props.string("yAxis", props.string("coordinateSystem", "down"))
                .trim().toLowerCase(Locale.ROOT);
        boolean yUp = axis.equals("up") || axis.equals("cartesian") || axis.equals("math");
        return new UiVectorSpace(
                viewport,
                minX,
                minY,
                width,
                height,
                scaleX,
                scaleY,
                viewport.x() + offsetX,
                viewport.y() + offsetY,
                yUp
        );
    }

    public UiBounds viewport() {
        return viewport;
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double maxX() {
        return minX + width;
    }

    public double maxY() {
        return minY + height;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public double logicalScaleX() {
        return scaleX;
    }

    public double logicalScaleY() {
        return scaleY;
    }

    public double logicalX(double vectorX) {
        return originX + (vectorX - minX) * scaleX;
    }

    public double logicalY(double vectorY) {
        if (yUp) {
            return originY + (minY + height - vectorY) * scaleY;
        }
        return originY + (vectorY - minY) * scaleY;
    }

    /**
     * Maps a rect whose width/height extend in the positive viewBox axes. With yAxis="up" this
     * naturally means the authored y coordinate is the lower edge, which matches chart/data space.
     */
    public UiBounds logicalRect(double x, double y, double w, double h) {
        double x0 = logicalX(x);
        double x1 = logicalX(x + w);
        double y0 = logicalY(y);
        double y1 = logicalY(y + h);
        return new UiBounds(
                (float) Math.min(x0, x1),
                (float) Math.min(y0, y1),
                (float) Math.abs(x1 - x0),
                (float) Math.abs(y1 - y0)
        );
    }

    public double renderX(double vectorX, UiRenderTransform transform) {
        return transform.x(logicalX(vectorX));
    }

    public double renderY(double vectorY, UiRenderTransform transform) {
        return transform.y(logicalY(vectorY));
    }

    private static String inferAlign(String aspect) {
        if (aspect == null || aspect.isBlank() || aspect.equals("meet") || aspect.equals("slice")) return "xMidYMid";
        return aspect;
    }

    private static double[] readViewBox(Object value) {
        if (value == null) return null;
        if (value instanceof String text) {
            String[] tokens = text.trim().replace(',', ' ').split("\\s+");
            if (tokens.length != 4) return invalidViewBox(value);
            double[] out = new double[4];
            for (int i = 0; i < 4; i++) {
                try {
                    out[i] = Double.parseDouble(tokens[i]);
                } catch (NumberFormatException ignored) {
                    return invalidViewBox(value);
                }
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Double x = number(first(map, "x", "minX"));
            Double y = number(first(map, "y", "minY"));
            Double w = number(first(map, "width", "w"));
            Double h = number(first(map, "height", "h"));
            if (x == null) x = 0.0;
            if (y == null) y = 0.0;
            return w != null && h != null ? new double[]{x, y, w, h} : invalidViewBox(value);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Double> numbers = new ArrayList<>(4);
            for (Object item : iterable) {
                Double parsed = number(item);
                if (parsed == null) return invalidViewBox(value);
                numbers.add(parsed);
                if (numbers.size() > 4) return invalidViewBox(value);
            }
            return numbers.size() == 4
                    ? new double[]{numbers.get(0), numbers.get(1), numbers.get(2), numbers.get(3)}
                    : invalidViewBox(value);
        }
        return invalidViewBox(value);
    }

    private static Object first(Map<?, ?> map, String first, String second) {
        Object value = map.get(first);
        return value != null ? value : map.get(second);
    }

    private static Double number(Object value) {
        if (value instanceof Number n) {
            double parsed = n.doubleValue();
            return Double.isFinite(parsed) ? parsed : null;
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text);
                return Double.isFinite(parsed) ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double[] invalidViewBox(Object value) {
        if (UiRuntimeValidation.enabled()) {
            throw UiRuntimeValidation.invalid(
                    "UI vector viewBox must be 'minX minY width height', [minX,minY,width,height], or an object."
            );
        }
        return null;
    }
}
