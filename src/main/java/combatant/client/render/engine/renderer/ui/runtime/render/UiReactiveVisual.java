/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.animation.UiEasing;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiThemeRegistry;

import java.util.Map;

/**
 * Resolves retained visual state declared by JS components.
 *
 * <p>JS describes semantic targets and color expressions while runtime state
 * supplies hover/press/focus and owns interpolation. This keeps cached script
 * trees visually live without leaking component/domain classes into Java.</p>
 */
public final class UiReactiveVisual {
    private UiReactiveVisual() {
    }

    public static UiNode stateSource(UiNode node) {
        if (node == null) return null;
        String source = node.props().string("stateSource", "self");
        if ("parent".equalsIgnoreCase(source) && node.parent() != null) return node.parent();
        return node;
    }

    public static float signal(UiNode node, String name) {
        if (node == null || name == null || name.isBlank()) return 0.0f;
        UiNode source = stateSource(node);
        if (source == null) return 0.0f;
        UiProps props = source.props();
        float target = switch (name) {
            case "hover", "hovered" -> source.state().hovered() ? 1.0f : 0.0f;
            case "press", "pressed" -> source.state().active() ? 1.0f : 0.0f;
            case "focus", "focused" -> source.state().focused() ? 1.0f : 0.0f;
            default -> stateValue(props.get("stateValues"), name);
        };

        Motion motion = motion(props.get("stateMotions"), name);
        return source.state().motion("reactive:" + name, target, motion.durationMs(), motion.easing(), System.nanoTime());
    }

    public static int color(UiNode node, String propName, int fallback) {
        if (node == null || propName == null || propName.isBlank()) return fallback;
        Object reactive = node.props().get(propName + "Reactive");
        if (!(reactive instanceof Map<?, ?> map)) {
            return rawColor(node.props().get(propName), fallback);
        }

        int base = rawColor(map.get("base"), rawColor(node.props().get(propName), fallback));
        int mixed = base;
        Object mixRaw = map.get("mix");
        if (mixRaw != null) {
            int mix = rawColor(mixRaw, base);
            float amount = number(map.get("mixBase"), 0.0f);
            Object termsRaw = map.get("mixTerms");
            if (termsRaw instanceof Map<?, ?> terms) {
                for (Map.Entry<?, ?> entry : terms.entrySet()) {
                    String signal = String.valueOf(entry.getKey());
                    amount += number(entry.getValue(), 0.0f) * signal(node, signal);
                }
            }
            mixed = mixRgbPreserveAlpha(base, mix, clamp01(amount));
        }

        boolean hasAlpha = map.containsKey("alphaBase") || map.get("alphaTerms") instanceof Map<?, ?>;
        if (hasAlpha) {
            float alpha = number(map.get("alphaBase"), ((mixed >>> 24) & 0xFF) / 255.0f);
            Object alphaTermsRaw = map.get("alphaTerms");
            if (alphaTermsRaw instanceof Map<?, ?> terms) {
                for (Map.Entry<?, ?> entry : terms.entrySet()) {
                    String signal = String.valueOf(entry.getKey());
                    alpha += number(entry.getValue(), 0.0f) * signal(node, signal);
                }
            }
            mixed = UiColor.withAlpha(mixed, clamp01(alpha));
        }
        return mixed;
    }

    public static int rawColor(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            int themed = UiThemeRegistry.current().color(s, Integer.MIN_VALUE);
            return themed != Integer.MIN_VALUE ? themed : UiColor.parse(s, fallback);
        }
        return fallback;
    }

    private static float stateValue(Object raw, String name) {
        if (!(raw instanceof Map<?, ?> map)) return 0.0f;
        Object value = map.get(name);
        if (value instanceof Boolean b) return b ? 1.0f : 0.0f;
        return clamp01(number(value, 0.0f));
    }

    private static Motion motion(Object raw, String name) {
        long duration = "hover".equals(name) || "hovered".equals(name) ? 300L : 160L;
        UiEasing easing = UiEasing.EASE_OUT_CUBIC;
        if (!(raw instanceof Map<?, ?> map)) return new Motion(duration, easing);
        Object value = map.get(name);
        if (value instanceof Number n) return new Motion(Math.max(0L, n.longValue()), easing);
        if (value instanceof Map<?, ?> spec) {
            duration = Math.max(0L, Math.round(number(spec.get("durationMs"), number(spec.get("duration"), duration))));
            Object easingRaw = spec.get("easing");
            if (easingRaw != null) easing = UiEasing.parse(String.valueOf(easingRaw));
        }
        return new Motion(duration, easing);
    }

    private static int mixRgbPreserveAlpha(int base, int target, float amount) {
        int a = (base >>> 24) & 0xFF;
        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;
        int tr = (target >>> 16) & 0xFF;
        int tg = (target >>> 8) & 0xFF;
        int tb = target & 0xFF;
        int r = Math.round(br + (tr - br) * amount);
        int g = Math.round(bg + (tg - bg) * amount);
        int b = Math.round(bb + (tb - bb) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float number(Object value, float fallback) {
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private record Motion(long durationMs, UiEasing easing) {
    }
}
