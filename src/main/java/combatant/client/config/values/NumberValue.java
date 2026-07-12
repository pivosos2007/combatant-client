/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.text.DecimalFormat;

public class NumberValue<N extends Number> extends ConfigValue<N> {

    private final N min, max;

    public NumberValue(String name, N def, N min, N max) {
        super(name, def);
        this.min = min;
        this.max = max;
        clamp();
    }

    public N getMin() {
        return min;
    }

    public N getMax() {
        return max;
    }

    private void clamp() {
        double v = value.doubleValue();
        double lo = min.doubleValue();
        double hi = max.doubleValue();

        if (v < lo) value = castToType(lo);
        else if (v > hi) value = castToType(hi);
    }

    @SuppressWarnings("unchecked")
    public N castToType(double v) {
        if (value instanceof Integer) return (N) Integer.valueOf((int) v);
        if (value instanceof Float) return (N) Float.valueOf((float) v);
        if (value instanceof Long) return (N) Long.valueOf((long) v);
        return (N) Double.valueOf(v);
    }

    @Override
    public Object toJson() {
        double v = value.doubleValue();
        if (value instanceof Float || value instanceof Double) {
            v = Math.round(v * 100d) / 100d;
        }
        return v;
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof Number n) {
            value = castToType(n.doubleValue());
            clamp();
        }
    }

    @Override
    public String toDisplay() {
        if (value instanceof Float || value instanceof Double) {
            return new DecimalFormat("0.##").format(value.doubleValue());
        }
        return super.toDisplay();
    }
}
