/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.*;

/**
 * Object-shaped per-item PvP cooldown rules.
 * <p>
 * JSON shape:
 * {
 * "minecraft:golden_apple": {
 * "enabled": false,
 * "seconds": 30,
 * "uses": 2,
 * "window_seconds": 10,
 * "scope": "pvp_grace",
 * "trigger": "consume_finish",
 * "block_use": false,
 * "block_mode": "none"
 * }
 * }
 */
public class ItemCooldownRulesValue extends ConfigValue<Map<String, ItemCooldownRulesValue.Rule>> {

    private final Map<String, Rule> defaults = new LinkedHashMap<>();

    public ItemCooldownRulesValue(String name, Map<String, Rule> defaults) {
        super(name, new LinkedHashMap<>());
        if (defaults != null) {
            for (var entry : defaults.entrySet()) {
                String id = normalizeItemId(entry.getKey());
                Rule rule = entry.getValue();
                if (id != null && rule != null) {
                    this.defaults.put(id, rule.normalized());
                }
            }
        }
        value.putAll(this.defaults);
    }

    public static String normalizeItemId(String id) {
        if (id == null) return null;
        String s = id.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if (!s.contains(":")) s = "minecraft:" + s;
        return s;
    }

    public Rule getRule(String itemId) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null) return null;
        return value.get(normalized);
    }

    public Rule getDefaultRule(String itemId) {
        String normalized = normalizeItemId(itemId);
        if (normalized != null) {
            Rule configuredDefault = defaults.get(normalized);
            if (configuredDefault != null) return configuredDefault;
        }
        return new Rule(false, 30, 1, 0, Scope.PVP_GRACE, Trigger.CONSUME_FINISH, UseBlockMode.NONE);
    }

    public Rule ensureRule(String itemId) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null) return null;
        Rule existing = value.get(normalized);
        if (existing != null) return existing;
        Rule created = getDefaultRule(normalized).normalized();
        value.put(normalized, created);
        return created;
    }

    public void putRule(String itemId, Rule rule) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null || rule == null) return;
        value.put(normalized, rule.normalized());
    }

    public void removeRule(String itemId) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null) return;
        value.remove(normalized);
    }

    public boolean containsRule(String itemId) {
        String normalized = normalizeItemId(itemId);
        return normalized != null && value.containsKey(normalized);
    }

    public Map<String, Rule> getRules() {
        return value;
    }

    public Set<String> getItemIds() {
        return new LinkedHashSet<>(value.keySet());
    }

    @Override
    public Object toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var entry : value.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            out.put(entry.getKey(), entry.getValue().toJson());
        }
        return out;
    }

    @Override
    public void fromJson(Object json) {
        value.clear();
        value.putAll(defaults);

        if (!(json instanceof Map<?, ?> map)) return;

        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String rawKey)) continue;
            String itemId = normalizeItemId(rawKey);
            if (itemId == null) continue;

            Rule base = value.getOrDefault(itemId, Rule.DEFAULT);
            Rule parsed = Rule.fromJson(entry.getValue(), base);
            if (parsed != null) {
                value.put(itemId, parsed.normalized());
            }
        }
    }

    @Override
    public String toDisplay() {
        return value.toString();
    }

    public enum Scope implements EnumValue.IdProvider {
        ALWAYS("always"),
        PVP_ONLY("pvp_only"),
        PVP_GRACE("pvp_grace");

        private final String id;

        Scope(String id) {
            this.id = id;
        }

        static Scope parse(Object value, Scope fallback) {
            if (value instanceof String s) {
                String normalized = s.trim().toLowerCase(Locale.ROOT);
                for (Scope scope : values()) {
                    if (scope.id.equals(normalized) || scope.name().equalsIgnoreCase(normalized)) {
                        return scope;
                    }
                }
            }
            return fallback;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum Trigger implements EnumValue.IdProvider {
        INTERACT_ACCEPT("interact_accept"),
        CONSUME_FINISH("consume_finish"),
        TOTEM_POP("totem_pop");

        private final String id;

        Trigger(String id) {
            this.id = id;
        }

        static Trigger parse(Object value, Trigger fallback) {
            if (value instanceof String s) {
                String normalized = s.trim().toLowerCase(Locale.ROOT);
                for (Trigger trigger : values()) {
                    if (trigger.id.equals(normalized) || trigger.name().equalsIgnoreCase(normalized)) {
                        return trigger;
                    }
                }
            }
            return fallback;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum UseBlockMode implements EnumValue.IdProvider {
        NONE("none"),
        COOLDOWN("cooldown"),
        WINDOW("window"),
        ANY("any");

        private final String id;

        UseBlockMode(String id) {
            this.id = id;
        }

        static UseBlockMode parse(Object value, UseBlockMode fallback) {
            if (value instanceof String s) {
                String normalized = s.trim().toLowerCase(Locale.ROOT);
                for (UseBlockMode mode : values()) {
                    if (mode.id.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                        return mode;
                    }
                }
            }
            return fallback;
        }

        @Override
        public String id() {
            return id;
        }

        public UseBlockMode next() {
            UseBlockMode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    public record Rule(boolean enabled,
                       int seconds,
                       int uses,
                       int windowSeconds,
                       Scope scope,
                       Trigger trigger,
                       UseBlockMode blockMode) {
        public static final Rule DEFAULT = new Rule(false, 0, 1, 0, Scope.PVP_GRACE, Trigger.CONSUME_FINISH, UseBlockMode.NONE);

        public Rule(boolean enabled,
                    int seconds,
                    int uses,
                    int windowSeconds,
                    Scope scope,
                    Trigger trigger,
                    boolean blockUse) {
            this(enabled, seconds, uses, windowSeconds, scope, trigger, blockUse ? UseBlockMode.COOLDOWN : UseBlockMode.NONE);
        }

        static Rule fromJson(Object json, Rule fallback) {
            Rule base = fallback != null ? fallback : DEFAULT;
            if (!(json instanceof Map<?, ?> map)) return base;

            boolean enabled = readBoolean(map, "enabled", base.enabled);
            int seconds = readInt(map, "seconds", base.seconds);
            int uses = readInt(map, "uses", base.uses);
            int windowSeconds = readInt(map, "window_seconds", base.windowSeconds);
            Scope scope = Scope.parse(map.get("scope"), base.scope);
            Trigger trigger = Trigger.parse(map.get("trigger"), base.trigger);
            UseBlockMode blockMode = UseBlockMode.parse(map.get("block_mode"), null);
            if (blockMode == null) {
                blockMode = readBoolean(map, "block_use", base.blockUse()) ? UseBlockMode.COOLDOWN : UseBlockMode.NONE;
            }

            return new Rule(enabled, seconds, uses, windowSeconds, scope, trigger, blockMode);
        }

        private static boolean readBoolean(Map<?, ?> map, String key, boolean fallback) {
            Object value = map.get(key);
            if (value instanceof Boolean b) return b;
            if (value instanceof String s) return Boolean.parseBoolean(s.trim());
            return fallback;
        }

        private static int readInt(Map<?, ?> map, String key, int fallback) {
            Object value = map.get(key);
            if (value instanceof Number n) return n.intValue();
            if (value instanceof String s) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        public Rule normalized() {
            int safeSeconds = Math.max(0, Math.min(3600, seconds));
            int safeUses = Math.max(1, Math.min(64, uses));
            int safeWindow = Math.max(0, Math.min(3600, windowSeconds));
            return new Rule(
                    enabled,
                    safeSeconds,
                    safeUses,
                    safeWindow,
                    scope != null ? scope : Scope.PVP_GRACE,
                    trigger != null ? trigger : Trigger.CONSUME_FINISH,
                    blockMode != null ? blockMode : UseBlockMode.NONE
            );
        }

        public boolean hasUseWindow() {
            return uses > 1;
        }

        public boolean blockUse() {
            return blockMode != null && blockMode != UseBlockMode.NONE;
        }

        Map<String, Object> toJson() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("enabled", enabled);
            out.put("seconds", seconds);
            out.put("uses", uses);
            out.put("window_seconds", windowSeconds);
            out.put("scope", scope.getId());
            out.put("trigger", trigger.getId());
            out.put("block_use", blockUse());
            out.put("block_mode", (blockMode != null ? blockMode : UseBlockMode.NONE).getId());
            return out;
        }
    }
}
