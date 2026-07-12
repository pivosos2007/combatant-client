/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.SettingDef;
import combatant.client.config.values.*;
import combatant.client.config.values.*;

/**
 * Builds GUI settings from GUI-neutral SettingDef.
 */
public enum SettingFactory {
    ;

    public static Setting fromDef(SettingDef def) {
        if (def == null) return null;
        Setting setting = switch (def.kind()) {
            case BOOLEAN -> def.value() instanceof BooleanValue v
                    ? new BooleanSetting(def.getId(), v) : null;
            case NUMBER -> def.value() instanceof NumberValue<?> v
                    ? new SliderSetting<>(def.getId(), v) : null;
            case MODE -> def.value() instanceof ModeValue v
                    ? new ModeSetting(def.getId(), v)
                    : def.value() instanceof EnumValue<?> ev
                    ? new ModeSetting(def.getId(), ev)
                    : null;
            case COLOR -> def.value() instanceof RGBAColorValue v
                    ? new ColorSetting(def.getId(), v) : null;
            case COLOR_NO_ALPHA -> def.value() instanceof RGBColorValue v
                    ? new ColorSetting(def.getId(), v) : null;
            case TEXT -> def.value() instanceof StringValue v
                    ? new TextSetting(def.getId(), v) : null;
            case TEXT_LIST -> def.value() instanceof SetValue v
                    ? new TextListSetting(def.getId(), v, def.pickerMode())
                    : def.value() instanceof ItemIdSetValue iv
                    ? new TextListSetting(def.getId(), iv, def.pickerMode())
                    : null;
            case COOLDOWN_RULES -> def.value() instanceof ItemCooldownRulesValue v
                    ? new CooldownRulesSetting(def.getId(), v) : null;
            case GROUP -> def.value() instanceof BooleanMapValue v
                    ? new GroupSetting(def.getId(), v) : null;
            case BIND -> def.value() instanceof KeyBindValue v
                    ? new FunctionBindSetting(def.getId(), v, def.bindMode() != null ? def.bindMode() : BindMode.PRESS)
                    : null;
        };
        if (setting != null) {
            setting.setCommonI18nKeys(def.commonI18nKeys());
            setting.setI18nEnabled(def.i18nNameEnabled(), def.i18nOptionsEnabled());
            setting.unavailableReason(def.unavailableReason());

            if (def.visibility() != null) {
                setting.visibleWhen(def.visibility());
            }
        }

        return setting;
    }

    public static java.util.List<Setting> fromDefs(java.util.List<SettingDef> defs) {
        if (defs == null || defs.isEmpty()) return java.util.List.of();
        java.util.List<Setting> out = new java.util.ArrayList<>();
        for (SettingDef def : defs) {
            Setting setting = fromDef(def);
            if (setting != null) out.add(setting);
        }
        return out;
    }
}
