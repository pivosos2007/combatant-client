/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.BooleanValue;

public class BooleanSetting extends Setting {

    private final BooleanValue value;
    private final UiState ui = new UiState();

    public BooleanSetting(String name, BooleanValue value) {
        super(name, value);
        this.value = value;
        this.ui.anim = value.get() ? 1f : 0f;
    }

    public boolean get() {
        return value.get();
    }

    public void set(boolean v) {
        value.set(v);
    }

    UiState ui() {
        return ui;
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
        float lastX, lastY, lastW;
        float lastH;
        float lastControlX, lastControlY, lastControlW, lastControlH;
        float anim = 0f;
        float hoverAnim = 0f;
        float layoutW = Float.NaN;
        String layoutLabel = null;
        String[] layoutLines = new String[]{""};
        float layoutLineH = 0f;
        float layoutLabelY = 0f;
        float layoutControlY = 0f;
        float layoutRowH = 44f;
    }
}
