/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.theme;

import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;
import combatant.client.config.profile.ConfigProfileStorage;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EditableClickGuiTheme implements SettingOwner {
    private final String id;
    private final StringValue displayName;
    private final RGBAColorValue windowBg;
    private final RGBAColorValue windowHeader;
    private final RGBAColorValue windowStroke;
    private final RGBAColorValue surface;
    private final RGBAColorValue surfaceHover;
    private final RGBAColorValue cardEnabled;
    private final RGBAColorValue cardDisabled;
    private final RGBAColorValue textPrimary;
    private final RGBAColorValue textMuted;
    private final RGBAColorValue accent;
    private final RGBAColorValue accentSoft;
    private final RGBAColorValue strokeSoft;
    private final GradientValues windowGradient;
    private final GradientValues headerGradient;
    private final GradientValues surfaceGradient;
    private final GradientValues cardGradient;
    private final GradientValues strokeGradient;

    private EditableClickGuiTheme(String id, Themes.ThemeEntry entry) {
        Themes.ThemeEntry src = entry != null ? entry : Theme.currentEntry();
        if (src == null) src = Themes.presetEntry(Themes.THEME_CLASSIC);
        this.id = normalizeId(id == null || id.isBlank() ? src.getId() : id);
        Themes.Theme theme = src.theme();
        displayName = new StringValue("theme_name", src.name());
        windowBg = color("window_bg", theme.windowBg());
        windowHeader = color("window_header", theme.windowHeader());
        windowStroke = color("window_stroke", theme.windowStroke());
        surface = color("surface", theme.surface());
        surfaceHover = color("surface_hover", theme.surfaceHover());
        cardEnabled = color("card_enabled", theme.cardEnabled());
        cardDisabled = color("card_disabled", theme.cardDisabled());
        textPrimary = color("text_primary", theme.textPrimary());
        textMuted = color("text_muted", theme.textMuted());
        accent = color("accent", theme.accent());
        accentSoft = color("accent_soft", theme.accentSoft());
        strokeSoft = color("stroke_soft", theme.strokeSoft());
        windowGradient = new GradientValues("window", src.windowGradient(), theme.windowBg());
        headerGradient = new GradientValues("header", src.headerGradient(), theme.windowHeader());
        surfaceGradient = new GradientValues("surface", src.surfaceGradient(), theme.surface());
        cardGradient = new GradientValues("card", src.cardGradient(), theme.cardEnabled());
        strokeGradient = new GradientValues("stroke", src.strokeGradient(), theme.windowStroke());
    }

    public static EditableClickGuiTheme existing(String id) {
        Themes.ThemeEntry entry = Themes.presetEntry(id) == null ? Theme.themes().stream()
                .filter(theme -> theme.getId().equals(normalizeId(id)))
                .findFirst()
                .orElse(null) : null;
        return entry == null || entry.builtin() ? null : new EditableClickGuiTheme(entry.getId(), entry);
    }

    public static EditableClickGuiTheme createFromCurrent() {
        String name = "Custom Theme";
        Themes.ThemeEntry created = Theme.createCustomTheme(name, Theme.currentEntry(), true);
        return created == null ? null : new EditableClickGuiTheme(created.getId(), created);
    }

    public String id() {
        return id;
    }

    public String title() {
        String n = displayName.get();
        return n == null || n.isBlank() ? "Custom Theme" : n;
    }

    public List<Setting> buildSettings() {
        List<Setting> out = new ArrayList<>();
        add(out, SettingDef.text("name", displayName));
        add(out, SettingDef.color("window_bg", windowBg));
        add(out, SettingDef.color("window_header", windowHeader));
        add(out, SettingDef.color("window_stroke", windowStroke));
        add(out, SettingDef.color("surface", surface));
        add(out, SettingDef.color("surface_hover", surfaceHover));
        add(out, SettingDef.color("card_enabled", cardEnabled));
        add(out, SettingDef.color("card_disabled", cardDisabled));
        add(out, SettingDef.color("text_primary", textPrimary));
        add(out, SettingDef.color("text_muted", textMuted));
        add(out, SettingDef.color("accent", accent));
        add(out, SettingDef.color("accent_soft", accentSoft));
        add(out, SettingDef.color("stroke_soft", strokeSoft));
        windowGradient.addSettings(out, this);
        headerGradient.addSettings(out, this);
        surfaceGradient.addSettings(out, this);
        cardGradient.addSettings(out, this);
        strokeGradient.addSettings(out, this);
        for (Setting setting : out) {
            setting.setParent(this);
            setting.preflightI18n();
        }
        return out;
    }

    private void add(List<Setting> out, SettingDef def) {
        Setting setting = SettingFactory.fromDef(def);
        if (setting != null) out.add(setting);
    }

    public Themes.ThemeEntry toEntry() {
        Themes.Theme theme = new Themes.Theme(
                windowBg.getArgb(),
                windowHeader.getArgb(),
                windowStroke.getArgb(),
                surface.getArgb(),
                surfaceHover.getArgb(),
                cardEnabled.getArgb(),
                cardDisabled.getArgb(),
                textPrimary.getArgb(),
                textMuted.getArgb(),
                accent.getArgb(),
                accentSoft.getArgb(),
                strokeSoft.getArgb()
        );
        return new Themes.ThemeEntry(
                id,
                title(),
                false,
                theme,
                windowGradient.toSpec(theme.windowBg()),
                headerGradient.toSpec(theme.windowHeader()),
                surfaceGradient.toSpec(theme.surface()),
                cardGradient.toSpec(theme.cardEnabled()),
                strokeGradient.toSpec(theme.windowStroke())
        );
    }

    @Override
    public String name() {
        return "theme." + id;
    }

    @Override
    public void saveConfig() {
        Theme.saveCustomTheme(toEntry(), true);
    }

    public boolean delete() {
        return Theme.deleteCustomTheme(id);
    }

    private static RGBAColorValue color(String name, int argb) {
        return new RGBAColorValue(name, Themes.colorString(argb));
    }

    private static String normalizeId(String id) {
        String safe = ConfigProfileStorage.sanitizeFileName(id == null || id.isBlank() ? "custom_theme" : id);
        return safe.toLowerCase(Locale.ROOT);
    }

    private static final class GradientValues {
        private final String prefix;
        private final BooleanValue enabled;
        private final RGBAColorValue start;
        private final RGBAColorValue end;
        private final NumberValue<Double> angle;

        private GradientValues(String prefix, Themes.GradientSpec spec, int baseColor) {
            Themes.GradientSpec src = spec != null ? spec : new Themes.GradientSpec(false, baseColor, baseColor, 90f);
            this.prefix = prefix;
            this.enabled = new BooleanValue(prefix + "_gradient_enabled", src.enabled());
            this.start = color(prefix + "_gradient_start", src.start());
            this.end = color(prefix + "_gradient_end", src.end());
            this.angle = new NumberValue<>(prefix + "_gradient_angle", (double) src.angleDeg(), 0.0, 360.0);
        }

        private void addSettings(List<Setting> out, EditableClickGuiTheme owner) {
            SettingDef enabledDef = SettingDef.bool(prefix + "_gradient", enabled);
            SettingDef startDef = SettingDef.color(prefix + "_gradient_start", start).visibleWhen(enabled::get);
            SettingDef endDef = SettingDef.color(prefix + "_gradient_end", end).visibleWhen(enabled::get);
            SettingDef angleDef = SettingDef.number(prefix + "_gradient_angle", angle).visibleWhen(enabled::get);
            owner.add(out, enabledDef);
            owner.add(out, startDef);
            owner.add(out, endDef);
            owner.add(out, angleDef);
        }

        private Themes.GradientSpec toSpec(int baseColor) {
            return new Themes.GradientSpec(
                    enabled.get(),
                    start.getArgb(),
                    end.getArgb(),
                    angle.get().floatValue()
            );
        }
    }
}
