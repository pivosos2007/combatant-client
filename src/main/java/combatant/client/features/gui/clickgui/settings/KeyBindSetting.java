/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.SettingOwner;
import combatant.client.config.values.KeyBindValue;
import combatant.client.util.input.KeyManager;
import combatant.client.util.wav.ClickGuiSounds;

public class KeyBindSetting extends Setting {

    private final KeyBindValue value;
    private final UiState ui = new UiState();

    public KeyBindSetting(KeyBindValue value) {
        super(value.getName(), value);
        this.value = value;

        if (!value.isNone()) {
            KeyManager.registerCombo(bindingName(), value.get());
        }
    }

    public KeyBindValue getValue() {
        return value;
    }

    UiState ui() {
        return ui;
    }

    private String bindingName() {
        return getParent() != null ? getParent().name() : value.getName();
    }

    @Override
    public void setParent(SettingOwner owner) {
        super.setParent(owner);
        // re-register under module name once parent is known
        KeyManager.unregisterAll(value.getName());
        if (!value.isNone()) {
            KeyManager.registerCombo(bindingName(), value.get());
        }
    }

    public void setKeyByName(String name) {
        value.set(name);
        KeyManager.unregisterAll(bindingName());
        if (!value.isNone()) {
            KeyManager.registerCombo(bindingName(), name);
        }
    }

    @Override
    public String getTranslationKey() {
        return "setting.module.bind";
    }
    // ========================== CONFIG ============================ //

    @Override
    public Object save() {
        return value.toJson();
    }

    @Override
    public void load(Object o) {
        value.fromJson(o);

        KeyManager.unregisterAll(bindingName());

        if (!value.isNone())
            KeyManager.registerCombo(bindingName(), value.get());
    }

    // =============================== UI ================================ //

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

    public void onKeySelected(String comboString) {
        String previous = value.get();
        boolean wasBound = previous != null && !previous.equalsIgnoreCase("NONE");
        KeyManager.unregisterAll(bindingName());

        if (comboString == null || comboString.equalsIgnoreCase("NONE")) {
            value.set("NONE");
            ClickGuiSounds.bindingNull();
        } else {
            value.set(comboString.toUpperCase());
            KeyManager.registerCombo(bindingName(), comboString);
            if (wasBound) {
                ClickGuiSounds.bindingReset();
            } else {
                ClickGuiSounds.bindingSuccess();
            }
        }

        ui.waiting = false;
        if (getParent() != null) getParent().saveConfig();
    }

    static final class UiState {
        float lastX;
        float lastY;
        boolean waiting = false;
        float lastW;
        float lastH;
        float lastBx, lastBy, lastBw, lastBh;
        float hoverAnim = 0f;
        float waitAnim = 0f;
        float layoutW = Float.NaN;
        String layoutLabel = null;
        String layoutDisplay = null;
        String[] layoutLines = new String[]{""};
        float layoutLineH = 0f;
        float layoutLabelY = 0f;
        float layoutBoxY = 0f;
        float layoutRowH = 46f;
        float layoutBoxW = 36f;
        float layoutTextW = 0f;
    }
}

