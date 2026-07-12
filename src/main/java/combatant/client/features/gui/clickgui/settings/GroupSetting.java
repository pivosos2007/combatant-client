/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.BooleanMapValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class GroupSetting extends Setting {

    private final BooleanMapValue value;
    private final UiState ui = new UiState();
    private Predicate<String> optionVisibility = key -> true;

    public GroupSetting(String name, BooleanMapValue value) {
        super(name, value);   // <--- FIX
        this.value = value;
    }

    BooleanMapValue value() {
        return value;
    }

    UiState ui() {
        return ui;
    }

    boolean isOptionVisible(String key) {
        return optionVisibility.test(key);
    }

    public GroupSetting visibleOptionsWhen(Predicate<String> predicate) {
        this.optionVisibility = predicate != null ? predicate : key -> true;
        ui.lastLayoutW = Float.NaN;
        ui.layoutOptionCount = -1;
        return this;
    }

    @Override
    public void preflightI18n() {
        super.preflightI18n();
        if (value == null) return;
        for (String key : value.getAll().keySet()) {
            if (!isOptionVisible(key)) continue;
            preflightOptionI18n(key);
        }
    }

    @Override
    public Object save() {
        return value == null ? null : value.toJson();
    }

    @Override
    public void load(Object o) {
        if (value != null) value.fromJson(o);
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
        final Map<String, Float> selectAnims = new HashMap<>();
        float baseX, baseY, baseW;
        float lastLayoutW = Float.NaN;
        int layoutOptionCount = -1;
        float cachedHeight = 34f;
    }

    record OptionLayout(String id, String label, float x, float y, float w) {
        public String getId() {
            return id;
        }
    }
}


