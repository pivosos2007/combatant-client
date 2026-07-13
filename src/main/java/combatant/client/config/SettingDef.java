/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import combatant.client.config.common.CommonSettingSchema;
import combatant.client.config.values.*;
import combatant.client.features.gui.clickgui.settings.TextListSetting;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * GUI-neutral setting descriptor. UI layer can build actual widgets from these.
 */
public final class SettingDef {

    private final Kind kind;
    private final String id;
    private final ConfigValue<?> value;
    private final BindMode bindMode;
    private final TextListSetting.PickerMode pickerMode;
    private Supplier<Boolean> visibility;
    private Supplier<String> unavailableReason;
    private final List<String> commonI18nKeys = new ArrayList<>();
    private boolean i18nNameEnabled = true;
    private boolean i18nOptionsEnabled = true;
    private SettingDef(Kind kind, String id, ConfigValue<?> value) {
        this(kind, id, value, null, null);
    }

    private SettingDef(Kind kind, String id, ConfigValue<?> value, BindMode bindMode) {
        this(kind, id, value, bindMode, null);
    }

    private SettingDef(Kind kind,
                       String id,
                       ConfigValue<?> value,
                       BindMode bindMode,
                       TextListSetting.PickerMode pickerMode) {
        this.kind = kind;
        this.id = id;
        this.value = value;
        this.bindMode = bindMode;
        this.pickerMode = pickerMode;
    }

    public static SettingDef bool(String id, BooleanValue value) {
        return new SettingDef(Kind.BOOLEAN, id, value);
    }

    public static SettingDef bool(BooleanValue value) {
        return new SettingDef(Kind.BOOLEAN, value.getName(), value);
    }

    public static SettingDef number(String id, NumberValue<?> value) {
        return new SettingDef(Kind.NUMBER, id, value);
    }

    public static SettingDef number(NumberValue<?> value) {
        return new SettingDef(Kind.NUMBER, value.getName(), value);
    }

    public static SettingDef mode(String id, ModeValue value) {
        return new SettingDef(Kind.MODE, id, value);
    }

    public static SettingDef mode(String id, EnumValue<?> value) {
        return new SettingDef(Kind.MODE, id, value);
    }

    public static SettingDef mode(ModeValue value) {
        return new SettingDef(Kind.MODE, value.getName(), value);
    }

    public static SettingDef mode(EnumValue<?> value) {
        return new SettingDef(Kind.MODE, value.getName(), value);
    }

    public static SettingDef color(String id, RGBAColorValue value) {
        return new SettingDef(Kind.COLOR, id, value);
    }

    public static SettingDef color(RGBAColorValue value) {
        return new SettingDef(Kind.COLOR, value.getName(), value);
    }

    public static SettingDef colorNoAlpha(String id, RGBColorValue value) {
        return new SettingDef(Kind.COLOR_NO_ALPHA, id, value);
    }

    public static SettingDef colorNoAlpha(RGBColorValue value) {
        return new SettingDef(Kind.COLOR_NO_ALPHA, value.getName(), value);
    }

    public static SettingDef text(String id, StringValue value) {
        return new SettingDef(Kind.TEXT, id, value);
    }

    public static SettingDef text(StringValue value) {
        return new SettingDef(Kind.TEXT, value.getName(), value);
    }

    public static SettingDef textList(String id, SetValue value) {
        return new SettingDef(Kind.TEXT_LIST, id, value);
    }

    public static SettingDef textList(String id,
                                      SetValue value,
                                      TextListSetting.PickerMode pickerMode) {
        return new SettingDef(Kind.TEXT_LIST, id, value, null, pickerMode);
    }

    public static SettingDef textList(SetValue value) {
        return new SettingDef(Kind.TEXT_LIST, value.getName(), value);
    }

    public static SettingDef textList(SetValue value,
                                      TextListSetting.PickerMode pickerMode) {
        return new SettingDef(Kind.TEXT_LIST, value.getName(), value, null, pickerMode);
    }

    public static SettingDef textList(String id, ItemIdSetValue value) {
        return new SettingDef(Kind.TEXT_LIST, id, value);
    }

    public static SettingDef textList(String id,
                                      ItemIdSetValue value,
                                      TextListSetting.PickerMode pickerMode) {
        return new SettingDef(Kind.TEXT_LIST, id, value, null, pickerMode);
    }

    public static SettingDef textList(ItemIdSetValue value) {
        return new SettingDef(Kind.TEXT_LIST, value.getName(), value);
    }

    public static SettingDef textList(ItemIdSetValue value,
                                      TextListSetting.PickerMode pickerMode) {
        return new SettingDef(Kind.TEXT_LIST, value.getName(), value, null, pickerMode);
    }

    public static SettingDef cooldownRules(String id, ItemCooldownRulesValue value) {
        return new SettingDef(Kind.COOLDOWN_RULES, id, value);
    }

    public static SettingDef cooldownRules(ItemCooldownRulesValue value) {
        return new SettingDef(Kind.COOLDOWN_RULES, value.getName(), value);
    }

    public static SettingDef protocolHeuristics(String id, BooleanMapValue value) {
        return new SettingDef(Kind.PROTOCOL_HEURISTICS, id, value);
    }

    public static SettingDef protocolHeuristics(BooleanMapValue value) {
        return new SettingDef(Kind.PROTOCOL_HEURISTICS, value.getName(), value);
    }

    public static SettingDef group(String id, BooleanMapValue value) {
        return new SettingDef(Kind.GROUP, id, value);
    }

    public static SettingDef group(BooleanMapValue value) {
        return new SettingDef(Kind.GROUP, value.getName(), value);
    }

    public static SettingDef bind(String id, KeyBindValue value, BindMode mode) {
        return new SettingDef(Kind.BIND, id, value, mode);
    }

    public static SettingDef bind(KeyBindValue value, BindMode mode) {
        return new SettingDef(Kind.BIND, value.getName(), value, mode);
    }

    public static SettingDef bind(String id, KeyBindValue value) {
        return new SettingDef(Kind.BIND, id, value, BindMode.PRESS);
    }

    public static SettingDef bind(KeyBindValue value) {
        return new SettingDef(Kind.BIND, value.getName(), value, BindMode.PRESS);
    }

    public static void applyI18nAnnotations(Object owner, Iterable<SettingDef> defs) {
        if (owner == null || defs == null) return;

        Map<ConfigValue<?>, SettingDef> defsByValue = new IdentityHashMap<>();
        for (SettingDef def : defs) {
            if (def != null && def.value() != null) {
                defsByValue.put(def.value(), def);
            }
        }
        if (defsByValue.isEmpty()) return;

        Class<?> type = owner.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                DisableSettingI18n annotation = field.getAnnotation(DisableSettingI18n.class);
                if (annotation == null) continue;
                if (!ConfigValue.class.isAssignableFrom(field.getType())) continue;

                boolean accessible = field.canAccess(owner);
                try {
                    if (!accessible) {
                        field.setAccessible(true);
                    }
                    if (field.get(owner) instanceof ConfigValue<?> value) {
                        SettingDef def = defsByValue.get(value);
                        if (def != null) {
                            def.disableI18n(annotation.name(), annotation.options());
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (!accessible) {
                        try {
                            field.setAccessible(false);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            type = type.getSuperclass();
        }
    }

    public Kind kind() {
        return kind;
    }

    public String id() {
        return id;
    }

    public String getId() {
        return id;
    }

    public ConfigValue<?> value() {
        return value;
    }

    public BindMode bindMode() {
        return bindMode;
    }

    public TextListSetting.PickerMode pickerMode() {
        return pickerMode;
    }

    public Supplier<Boolean> visibility() {
        return visibility;
    }

    public Supplier<String> unavailableReason() {
        return unavailableReason;
    }

    public String commonI18nKey() {
        return commonI18nKeys.isEmpty() ? null : commonI18nKeys.get(0);
    }

    public List<String> commonI18nKeys() {
        return List.copyOf(commonI18nKeys);
    }

    public boolean i18nNameEnabled() {
        return i18nNameEnabled;
    }

    public boolean i18nOptionsEnabled() {
        return i18nOptionsEnabled;
    }

    public SettingDef visibleWhen(Supplier<Boolean> supplier) {
        this.visibility = supplier;
        return this;
    }

    public SettingDef unavailableWhen(Supplier<Boolean> supplier, String reason) {
        return unavailableWhen(supplier, () -> reason);
    }

    public SettingDef unavailableWhen(Supplier<Boolean> supplier, Supplier<String> reason) {
        if (supplier == null) {
            this.unavailableReason = null;
            return this;
        }
        this.unavailableReason = () -> {
            boolean unavailable;
            try {
                unavailable = supplier.get();
            } catch (Exception ignored) {
                unavailable = false;
            }
            if (!unavailable) {
                return null;
            }
            try {
                return reason == null ? "" : reason.get();
            } catch (Exception ignored) {
                return "";
            }
        };
        return this;
    }

    public SettingDef availableWhen(Supplier<Boolean> supplier, String reasonWhenUnavailable) {
        return availableWhen(supplier, () -> reasonWhenUnavailable);
    }

    public SettingDef availableWhen(Supplier<Boolean> supplier, Supplier<String> reasonWhenUnavailable) {
        if (supplier == null) {
            this.unavailableReason = null;
            return this;
        }
        return unavailableWhen(() -> !supplier.get(), reasonWhenUnavailable);
    }

    public SettingDef common(String key) {
        addCommonKey(key);
        return this;
    }

    public SettingDef common(CommonSettingSchema schema) {
        if (schema != null) {
            for (String key : schema.commonI18nKeys()) {
                addCommonKey(key);
            }
        }
        return this;
    }

    private void addCommonKey(String key) {
        if (key == null) return;
        String normalized = key.trim();
        if (!normalized.isBlank() && !commonI18nKeys.contains(normalized)) {
            commonI18nKeys.add(normalized);
        }
    }

    public SettingDef disableI18n(boolean name, boolean options) {
        if (name) {
            this.i18nNameEnabled = false;
        }
        if (options) {
            this.i18nOptionsEnabled = false;
        }
        return this;
    }

    public enum Kind {
        BOOLEAN,
        NUMBER,
        MODE,
        COLOR,
        COLOR_NO_ALPHA,
        TEXT,
        TEXT_LIST,
        COOLDOWN_RULES,
        PROTOCOL_HEURISTICS,
        GROUP,
        BIND
    }
}
