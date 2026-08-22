/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.SettingOwner;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.KeyBindValue;
import combatant.client.util.input.KeyManager;
import combatant.client.features.gui.clickgui.sound.GuiSound;

import java.util.function.BooleanSupplier;

/**
 * Function bind without toggle(): listens to active state (PRESS/HOLD).
 */
public class FunctionBindSetting extends Setting {

    private final KeyBindValue value;
    private final BindMode mode;
    private final UiState ui = new UiState();
    private String registeredName = null;
    private String hudIcon = null;
    private String hudLabel = null;
    private BooleanSupplier hudToggleState = null;

    public FunctionBindSetting(String id, String defaultKey) {
        this(id, defaultKey, BindMode.PRESS);
    }

    public FunctionBindSetting(String id, String defaultKey, BindMode mode) {
        this(id, new KeyBindValue(id, defaultKey), mode);
    }

    public FunctionBindSetting(String id, KeyBindValue value, BindMode mode) {
        super("Function: " + id, value);
        this.value = value;
        this.mode = mode;
    }

    public String get() {
        return value.get();
    }

    public void set(String keyName) {
        value.set(keyName.toUpperCase());
    }

    public boolean isPressed() {
        return KeyManager.wasPressed(bindingName());
    }

    BindMode mode() {
        return mode;
    }

    public BindMode getMode() {
        return mode;
    }

    public String getActionId() {
        return value != null ? value.getName() : getId();
    }

    public String getHudLabel() {
        return hudLabel;
    }

    public String getHudIcon() {
        return hudIcon;
    }

    public FunctionBindSetting hudIcon(String icon) {
        this.hudIcon = icon == null || icon.isBlank() ? null : icon.trim();
        return this;
    }

    public FunctionBindSetting hudSvg(String svgName) {
        return hudIcon(svgName);
    }

    public FunctionBindSetting hudLabel(String label) {
        this.hudLabel = label == null || label.isBlank() ? null : label.trim();
        return this;
    }

    public FunctionBindSetting hud(String label, String icon) {
        return hudLabel(label).hudSvg(icon);
    }

    public FunctionBindSetting hudToggle(BooleanSupplier activeState) {
        this.hudToggleState = activeState;
        return this;
    }

    public boolean isHudToggle() {
        return hudToggleState != null;
    }

    public boolean isHeldForHud() {
        return KeyManager.isHeldAllowScreen(bindingName());
    }

    public boolean isHudToggleActive() {
        if (hudToggleState == null) return false;
        try {
            return hudToggleState.getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    UiState ui() {
        return ui;
    }

    // ====================== CONFIG ======================

    @Override
    public Object save() {
        return value.toJson();
    }

    @Override
    public void load(Object o) {
        value.fromJson(o);
        refreshRegistration();
    }

    // ======================== UI ========================

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

    public void onKeySelected(String combo) {
        String previous = value.get();
        boolean wasBound = previous != null && !previous.equalsIgnoreCase("NONE");
        if (combo == null || combo.equalsIgnoreCase("NONE")) {
            set("NONE");
            GuiSound.BINDING_NULL.feedback();
        } else {
            set(combo);
            if (wasBound) {
                GuiSound.BIND_RESET.feedback();
            } else {
                GuiSound.BINDING.feedback();
            }
        }
        refreshRegistration();
        ui.waiting = false;
        if (getParent() != null) getParent().saveConfig();
    }

    public String getBindingName() {
        return bindingName();
    }

    @Override
    public void setParent(SettingOwner owner) {
        // unregister under previous name (could be null before parent is set)
        unregister();
        super.setParent(owner);
        refreshRegistration();
    }

    private String bindingName() {
        SettingOwner parent = getParent();
        String actionId = value.getName();
        return parent != null ? parent.name() + ":" + actionId : actionId;
    }

    @Override
    protected String getTranslationId() {
        return value != null ? value.getName() : super.getTranslationId();
    }

    private void refreshRegistration() {
        unregister();
        if (!value.isNone()) {
            registeredName = bindingName();
            KeyManager.registerCombo(registeredName, value.get());
        }
    }

    private void unregister() {
        if (registeredName != null) {
            KeyManager.unregisterAll(registeredName);
        } else {
            // In case it was registered before parent was set, try using current binding name
            KeyManager.unregisterAll(bindingName());
        }
        registeredName = null;
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
        float layoutMarkerY = 0f;
        float layoutBoxY = 0f;
        float layoutRowH = 54f;
        float layoutBoxW = 36f;
        float layoutTextW = 0f;
    }
}
