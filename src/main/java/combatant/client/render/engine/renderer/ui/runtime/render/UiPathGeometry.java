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

import java.util.Map;

/** Converts authored local points or semantic chart data into renderer-space path geometry. */
final class UiPathGeometry {
    static final int MAX_INPUT_POINTS = 2048;

    private final double[] points = new double[MAX_INPUT_POINTS * 2];
    private final double[] seriesX = new double[MAX_INPUT_POINTS];
    private final double[] seriesY = new double[MAX_INPUT_POINTS];

    private boolean valueSeries;
    private double yDomainMin;
    private double yDomainMax;

    double[] points() {
        return points;
    }

    int read(UiProps props, UiBounds bounds) {
        valueSeries = false;
        Object values = props.get("values");
        if (!(values instanceof Iterable<?>)) values = props.get("data");
        if (values instanceof Iterable<?> iterable) {
            int count = readValues(iterable, props, bounds);
            if (count > 0) return count;
        }
        return readPoints(props.get("points"), bounds, props.bool("normalized", false));
    }

    /** Returns the renderer-space area baseline for the most recently resolved geometry. */
    double baseline(UiProps props, UiBounds bounds) {
        if (props.get("baseline") != null) {
            return bounds.y() + props.number("baseline", bounds.height());
        }
        if (!valueSeries) return bounds.y() + bounds.height();

        double value;
        Object authored = props.get("baselineValue");
        if (authored != null) {
            value = number(authored, 0.0);
        } else {
            value = 0.0;
        }
        return mapY(value, bounds, props.bool("clampValues", true));
    }

    private int readValues(Iterable<?> iterable, UiProps props, UiBounds bounds) {
        int count = 0;
        boolean explicitX = false;
        double observedXMin = Double.POSITIVE_INFINITY;
        double observedXMax = Double.NEGATIVE_INFINITY;
        double observedYMin = Double.POSITIVE_INFINITY;
        double observedYMax = Double.NEGATIVE_INFINITY;

        int inputIndex = 0;
        for (Object item : iterable) {
            if (count >= MAX_INPUT_POINTS) {
                UiRuntimeValidation.warnOnce("ui-path-max-points",
                        "UI path series is limited to " + MAX_INPUT_POINTS + " points; extra values are ignored.");
                break;
            }

            double x = count;
            double y;
            boolean itemExplicitX = false;
            if (item instanceof Number n) {
                y = n.doubleValue();
            } else if (item instanceof Map<?, ?> map) {
                Object rawY = map.containsKey("value") ? map.get("value") : map.get("y");
                Double parsedY = finiteNumber(rawY);
                if (parsedY == null) {
                    invalidSeriesValue(inputIndex, item);
                    inputIndex++;
                    continue;
                }
                y = parsedY;
                Object rawX = map.get("x");
                if (rawX != null) {
                    Double parsedX = finiteNumber(rawX);
                    if (parsedX == null) {
                        invalidSeriesValue(inputIndex, item);
                        inputIndex++;
                        continue;
                    }
                    x = parsedX;
                    itemExplicitX = true;
                }
            } else {
                invalidSeriesValue(inputIndex, item);
                inputIndex++;
                continue;
            }
            if (!Double.isFinite(y)) {
                invalidSeriesValue(inputIndex, item);
                inputIndex++;
                continue;
            }

            seriesX[count] = x;
            seriesY[count] = y;
            explicitX |= itemExplicitX;
            observedXMin = Math.min(observedXMin, x);
            observedXMax = Math.max(observedXMax, x);
            observedYMin = Math.min(observedYMin, y);
            observedYMax = Math.max(observedYMax, y);
            count++;
            inputIndex++;
        }
        if (count < 2) return count;

        yDomainMin = domain(props, "yDomainMin", "domainMin", observedYMin);
        yDomainMax = domain(props, "yDomainMax", "domainMax", observedYMax);
        if (!Double.isFinite(yDomainMin)) yDomainMin = 0.0;
        if (!Double.isFinite(yDomainMax)) yDomainMax = yDomainMin + 1.0;
        if (yDomainMax <= yDomainMin) {
            double padding = Math.max(1.0, Math.abs(yDomainMin) * 0.05);
            yDomainMin -= padding;
            yDomainMax += padding;
        }

        boolean clamp = props.bool("clampValues", true);
        if (explicitX) {
            double xMin = domain(props, "xDomainMin", null, observedXMin);
            double xMax = domain(props, "xDomainMax", null, observedXMax);
            if (!Double.isFinite(xMin)) xMin = 0.0;
            if (!Double.isFinite(xMax) || xMax <= xMin) xMax = xMin + 1.0;
            double xRange = xMax - xMin;
            for (int i = 0; i < count; i++) {
                double tx = (seriesX[i] - xMin) / xRange;
                if (clamp) tx = clamp01(tx);
                points[i * 2] = bounds.x() + bounds.width() * tx;
                points[i * 2 + 1] = mapY(seriesY[i], bounds, clamp);
            }
        } else {
            int slots = Math.max(count, Math.round(props.number("historySlots", count)));
            slots = Math.max(2, slots);
            int firstSlot = Math.max(0, slots - count);
            for (int i = 0; i < count; i++) {
                int slot = firstSlot + i;
                points[i * 2] = bounds.x() + bounds.width() * slot / (slots - 1.0);
                points[i * 2 + 1] = mapY(seriesY[i], bounds, clamp);
            }
        }
        valueSeries = true;
        return count;
    }

    private int readPoints(Object value, UiBounds bounds, boolean normalized) {
        if (!(value instanceof Iterable<?> iterable)) return 0;
        int count = 0;
        double pendingX = Double.NaN;
        for (Object item : iterable) {
            if (count >= MAX_INPUT_POINTS) {
                UiRuntimeValidation.warnOnce("ui-path-max-points",
                        "UI path geometry is limited to " + MAX_INPUT_POINTS + " points; extra values are ignored.");
                break;
            }
            if (item instanceof Map<?, ?> map) {
                Double xValue = finiteNumber(map.get("x"));
                Double yValue = finiteNumber(map.get("y"));
                if (xValue == null || yValue == null) {
                    if (UiRuntimeValidation.enabled()) {
                        throw UiRuntimeValidation.invalid("UI path point maps require finite numeric x/y values.");
                    }
                    continue;
                }
                double x = xValue;
                double y = yValue;
                points[count * 2] = bounds.x() + (normalized ? x * bounds.width() : x);
                points[count * 2 + 1] = bounds.y() + (normalized ? y * bounds.height() : y);
                count++;
                continue;
            }
            if (item instanceof Number n) {
                if (Double.isNaN(pendingX)) {
                    pendingX = n.doubleValue();
                } else {
                    double x = pendingX;
                    double y = n.doubleValue();
                    points[count * 2] = bounds.x() + (normalized ? x * bounds.width() : x);
                    points[count * 2 + 1] = bounds.y() + (normalized ? y * bounds.height() : y);
                    count++;
                    pendingX = Double.NaN;
                }
            }
        }
        return count;
    }

    private double mapY(double value, UiBounds bounds, boolean clamp) {
        double t = (value - yDomainMin) / (yDomainMax - yDomainMin);
        if (clamp) t = clamp01(t);
        return bounds.y() + bounds.height() * (1.0 - t);
    }

    private static Double finiteNumber(Object value) {
        double parsed;
        if (value instanceof Number n) {
            parsed = n.doubleValue();
        } else if (value instanceof String text) {
            try {
                parsed = Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        } else {
            return null;
        }
        return Double.isFinite(parsed) ? parsed : null;
    }

    private static void invalidSeriesValue(int index, Object value) {
        if (!UiRuntimeValidation.enabled()) return;
        String type = value == null ? "null" : value.getClass().getSimpleName();
        throw UiRuntimeValidation.invalid(
                "UI path series value at index " + index + " must be a finite number or {x?, y/value}; got " + type + "."
        );
    }

    private static double domain(UiProps props, String primary, String alias, double fallback) {
        Object value = props.get(primary);
        if (value == null && alias != null) value = props.get(alias);
        return value != null ? number(value, fallback) : fallback;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
