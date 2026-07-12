/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.util.text.TextSelection;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Button + modal text editor or picker for editing Set-based values.
 */
public class TextListSetting extends Setting implements TextEditorOwner {

    private final ConfigValue<Set<String>> value;
    private final boolean enforceMinecraftPrefix;
    private final PickerMode pickerMode;
    private final UiState ui = new UiState();
    public TextListSetting(String name, ConfigValue<Set<String>> value) {
        this(name, value, PickerMode.TEXT);
    }

    public TextListSetting(String name, ConfigValue<Set<String>> value, PickerMode pickerMode) {
        super(name, value);
        this.value = value;
        this.enforceMinecraftPrefix = value instanceof ItemIdSetValue;
        this.pickerMode = pickerMode == null ? PickerMode.TEXT : pickerMode;
    }

    ConfigValue<Set<String>> value() {
        return value;
    }

    UiState ui() {
        return ui;
    }

    @Override
    public void render(float x, float y, float w, float mx, float my) {
        SettingRendererBridge.render(this, x, y, w, mx, my);
    }

    @Override
    public void mouseClicked(double mx, double my, int button) {
        SettingRendererBridge.mouseClicked(this, mx, my, button);
    }

    @Override
    public float getHeight() {
        return SettingRendererBridge.getHeight(this);
    }

    @Override
    public void mouseClickedOutside(double mx, double my, int button) {
        SettingRendererBridge.mouseClickedOutside(this, mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return SettingRendererBridge.keyPressed(this, keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return SettingRendererBridge.charTyped(this, chr, modifiers);
    }

    @Override
    public void applyEditorText(String rawText) {
        Set<String> parsed = Arrays.stream(rawText.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalizeEntry)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        value.set(parsed);
        if (getParent() != null) getParent().saveConfig();
    }

    public Set<String> getValueSet() {
        Set<String> current = value.get();
        if (current == null) return new LinkedHashSet<>();
        return new LinkedHashSet<>(current);
    }

    public void setValueSet(Set<String> next) {
        value.set(next == null ? new LinkedHashSet<>() : next);
        if (getParent() != null) getParent().saveConfig();
    }

    public PickerMode getPickerMode() {
        return pickerMode;
    }

    public boolean isMinecraftPrefixEnforced() {
        return enforceMinecraftPrefix;
    }

    public String normalizeEntry(String line) {
        if (!enforceMinecraftPrefix) return line;
        if (line.contains(":")) return line;
        return "minecraft:" + line;
    }

    @Override
    public String getEditorText() {
        Set<String> current = value.get();
        if (current == null || current.isEmpty()) return "";
        return String.join("\n", current);
    }

    @Override
    public boolean isSingleLine() {
        return false;
    }

    public enum PickerMode {
        TEXT,
        SCREENS,
        BLOCKS,
        ITEMS,
        EQUIPPABLE_ARMOR,
        ENCHANTMENTS,
        ALL,
        SOUNDS,
        LIVING_ENTITIES,
        ENTITIES
    }

    static final class UiState {
        final TextSelection editSelection = new TextSelection();
        float lastButtonX, lastButtonY, lastButtonW, lastButtonH;
        float lastX, lastY, lastW;
        float fieldX, fieldY, fieldW, fieldH;
        float moduleHoverAnim;
        float fieldHoverAnim;
        float fieldFocusAnim;
        float fieldErrorAnim;
        float cursorBlink;
        boolean editing;
        String editBuffer = "";
        int editCursor;
    }
}
