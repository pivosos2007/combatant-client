/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ModeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModeSetting extends Setting {

    private final ModeValue modeValue;
    private final EnumValue<?> enumValue;
    private final Runnable onChange;
    private final UiState ui = new UiState();

    public ModeSetting(String name, ModeValue value) {
        this(name, value, null);
    }

    public ModeSetting(String name, ModeValue value, Runnable onChange) {
        super(name, value);
        this.modeValue = value;
        this.enumValue = null;
        this.onChange = onChange;
    }

    public ModeSetting(String name, EnumValue<?> value) {
        this(name, value, null);
    }

    public ModeSetting(String name, EnumValue<?> value, Runnable onChange) {
        super(name, value);
        this.modeValue = null;
        this.enumValue = value;
        this.onChange = onChange;
    }

    Runnable onChange() {
        return onChange;
    }

    UiState ui() {
        return ui;
    }

    List<String> optionIds() {
        return modeValue != null ? modeValue.getOptions() : enumValue.getOptions();
    }

    String selectedId() {
        return modeValue != null ? modeValue.get() : enumValue.getId();
    }

    void selectId(String id) {
        if (modeValue != null) {
            modeValue.fromJson(id);
        } else {
            enumValue.setById(id);
        }
    }

    @Override
    public void preflightI18n() {
        super.preflightI18n();
        for (String opt : optionIds()) {
            preflightOptionI18n(opt);
        }
    }

    @Override
    public Object save() {
        if (modeValue != null) return modeValue.toJson();
        return enumValue == null ? null : enumValue.toJson();
    }

    @Override
    public void load(Object o) {
        if (modeValue != null) {
            modeValue.fromJson(o);
        } else if (enumValue != null) {
            enumValue.fromJson(o);
        }
        if (onChange != null) onChange.run();
    }

    @Override
    public void render(float x, float y, float w, float mx, float my) {
        SettingRendererBridge.render(this, x, y, w, mx, my);
    }

    @Override
    public void mouseClicked(double mx, double my, int b) {
        SettingRendererBridge.mouseClicked(this, mx, my, b);
    }

    @Override
    public float getHeight() {
        return SettingRendererBridge.getHeight(this);
    }

    static final class UiState {
        final List<OptionLayout> optionLayouts = new ArrayList<>();
        final Map<String, Float> hoverAnims = new HashMap<>();
        float baseX, baseY, baseW;
        float cachedHeight = 64f;
        float selectX, selectY, selectW;
        boolean selectInit = false;
        float lastLayoutW = Float.NaN;
        int layoutOptionCount = -1;
    }

    record OptionLayout(String id, String label, float x, float y, float w) {
        public String getId() {
            return id;
        }
    }
}
