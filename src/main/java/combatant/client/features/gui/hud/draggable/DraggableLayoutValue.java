/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable;

import combatant.client.config.values.ConfigValue;
import combatant.client.features.gui.hud.HudAnchorX;
import combatant.client.features.gui.hud.HudAnchorY;
import combatant.client.features.gui.hud.WidgetAnchorSide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-element draggable layout value stored inside element config.
 */
public final class DraggableLayoutValue extends ConfigValue<DraggableLayoutValue.State> {

    public DraggableLayoutValue(String name) {
        super(name, null);
    }

    @Override
    public Object toJson() {
        State v = value;
        if (v == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pos", List.of(v.x(), v.y()));
        if (v.logical()) data.put("space", "logical");
        if (v.anchorX() != HudAnchorX.FREE) data.put("ax", v.anchorX().name());
        if (v.anchorY() != HudAnchorY.FREE) data.put("ay", v.anchorY().name());
        if (v.parentId() != null && !v.parentId().isEmpty() && v.parentSide() != WidgetAnchorSide.NONE) {
            data.put("parent", v.parentId());
            data.put("side", v.parentSide().name());
            data.put("pox", v.parentOffsetX());
            data.put("poy", v.parentOffsetY());
        }
        return data;
    }

    @Override
    public void fromJson(Object json) {
        if (json == null) {
            value = null;
            return;
        }
        if (json instanceof List<?> list && list.size() >= 2) {
            Double x = toNumber(list.get(0));
            Double y = toNumber(list.get(1));
            if (x != null && y != null) {
                value = State.simple(x.floatValue(), y.floatValue());
            }
            return;
        }
        if (json instanceof Map<?, ?> data) {
            float x = readFloat(data.get("x"), 0f);
            float y = readFloat(data.get("y"), 0f);
            Object pos = data.get("pos");
            if (pos instanceof List<?> posList && posList.size() >= 2) {
                Double px = toNumber(posList.get(0));
                Double py = toNumber(posList.get(1));
                if (px != null) x = px.floatValue();
                if (py != null) y = py.floatValue();
            }
            HudAnchorX ax = parseAnchorX(readString(data.get("ax")));
            HudAnchorY ay = parseAnchorY(readString(data.get("ay")));
            String parent = readString(data.get("parent"));
            WidgetAnchorSide side = parseAnchorSide(readString(data.get("side")));
            float pox = readFloat(data.get("pox"), 0f);
            float poy = readFloat(data.get("poy"), 0f);
            boolean logical = "logical".equalsIgnoreCase(readString(data.get("space")));
            value = new State(x, y, ax, ay, parent, side, pox, poy, logical);
        }
    }

    private Double toNumber(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignore) {
            return null;
        }
    }

    private float readFloat(Object v, float def) {
        Double n = toNumber(v);
        return n != null ? n.floatValue() : def;
    }

    private String readString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private HudAnchorX parseAnchorX(String v) {
        if (v == null) return HudAnchorX.FREE;
        try {
            return HudAnchorX.valueOf(v);
        } catch (Exception ignore) {
            return HudAnchorX.FREE;
        }
    }

    private HudAnchorY parseAnchorY(String v) {
        if (v == null) return HudAnchorY.FREE;
        try {
            return HudAnchorY.valueOf(v);
        } catch (Exception ignore) {
            return HudAnchorY.FREE;
        }
    }

    private WidgetAnchorSide parseAnchorSide(String v) {
        if (v == null) return WidgetAnchorSide.NONE;
        try {
            return WidgetAnchorSide.valueOf(v);
        } catch (Exception ignore) {
            return WidgetAnchorSide.NONE;
        }
    }

    public record State(float x, float y,
                        HudAnchorX anchorX, HudAnchorY anchorY,
                        String parentId, WidgetAnchorSide parentSide,
                        float parentOffsetX, float parentOffsetY,
                        boolean logical) {
        public static State simple(float x, float y) {
            return new State(x, y, HudAnchorX.FREE, HudAnchorY.FREE, null, WidgetAnchorSide.NONE, 0f, 0f, false);
        }
    }
}
