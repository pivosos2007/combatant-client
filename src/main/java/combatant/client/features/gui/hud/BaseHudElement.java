/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud;

import combatant.client.config.*;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.config.values.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import combatant.client.config.*;
import combatant.client.config.values.*;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.HudPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.resources.language.I18n;

import java.util.*;
import java.util.function.Supplier;

public abstract class BaseHudElement implements JsonConfigObject, ConfigNameProvider, SettingOwner, SettingDefProvider {

    private final String id;
    private final String title;
    private final BooleanValue enabled;
    private final List<SettingDef> settingDefs = new ArrayList<>();
    private final Map<ConfigValue<?>, SettingDef> settingDefsByValue = new IdentityHashMap<>();
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    private boolean settingsInitialized = false;
    private boolean previewEnabled = false;

    protected BaseHudElement() {
        HudElementInfo info = getClass().getAnnotation(HudElementInfo.class);
        if (info == null) {
            throw new IllegalStateException(getClass().getName() + " must be annotated with @HudElementInfo");
        }

        this.id = info.id();
        this.title = info.displayName();
        this.enabled = new BooleanValue("enabled", info.enabledByDefault());
    }

    protected BaseHudElement(String id, String title, boolean defaultEnabled) {
        this.id = id;
        this.title = title;
        this.enabled = new BooleanValue("enabled", defaultEnabled);
    }

    private static String stripOwnerPrefix(String value, String owner) {
        if (value == null || value.isBlank() || owner == null || owner.isBlank()) return value;

        String normalizedOwner = normalizePrefix(owner);
        String lowerValue = value.toLowerCase(Locale.ROOT);
        if (!lowerValue.startsWith(normalizedOwner)) return value;

        String tail = value.substring(normalizedOwner.length());
        if (tail.isEmpty()) return value;

        char first = tail.charAt(0);
        if (first == '_' || first == '-' || first == '.') {
            tail = tail.substring(1);
        } else if (!Character.isUpperCase(first)) {
            return value;
        }

        return camelToSnake(tail);
    }

    private static String normalizePrefix(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String camelToSnake(String value) {
        if (value == null || value.isEmpty()) return value;

        StringBuilder out = new StringBuilder(value.length() + 4);
        char previous = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '-' || c == '.' || c == ' ') {
                c = '_';
            }
            if (c == '_') {
                if (!out.isEmpty() && out.charAt(out.length() - 1) != '_') {
                    out.append('_');
                }
                previous = '_';
                continue;
            }
            if (Character.isUpperCase(c) && i > 0 && previous != '_' && !Character.isUpperCase(previous)) {
                out.append('_');
            }
            out.append(Character.toLowerCase(c));
            previous = c;
        }
        return out.toString();
    }

    protected void defineSettings(List<SettingDef> defs) {
    }

    protected void onLoaded() {
    }

    protected void collectExtraConfigValues(Map<String, ConfigValue<?>> map) {
    }

    protected final BooleanValue bool(String configName, boolean def) {
        return bool(configName, settingIdFromConfigName(configName), def);
    }

    protected final BooleanValue bool(String configName, String settingId, boolean def) {
        BooleanValue value = new BooleanValue(configName, def);
        declare(value, SettingDef.bool(settingId, value));
        return value;
    }

    protected final NumberValue<Integer> num(String configName, int def, int min, int max) {
        return num(configName, settingIdFromConfigName(configName), def, min, max);
    }

    protected final NumberValue<Integer> num(String configName, String settingId, int def, int min, int max) {
        NumberValue<Integer> value = new NumberValue<>(configName, def, min, max);
        declare(value, SettingDef.number(settingId, value));
        return value;
    }

    protected final NumberValue<Float> num(String configName, float def, float min, float max) {
        return num(configName, settingIdFromConfigName(configName), def, min, max);
    }

    protected final NumberValue<Float> num(String configName, String settingId, float def, float min, float max) {
        NumberValue<Float> value = new NumberValue<>(configName, def, min, max);
        declare(value, SettingDef.number(settingId, value));
        return value;
    }

    protected final NumberValue<Double> num(String configName, double def, double min, double max) {
        return num(configName, settingIdFromConfigName(configName), def, min, max);
    }

    protected final NumberValue<Double> num(String configName, String settingId, double def, double min, double max) {
        NumberValue<Double> value = new NumberValue<>(configName, def, min, max);
        declare(value, SettingDef.number(settingId, value));
        return value;
    }

    protected final ModeValue mode(String configName, String def, String... options) {
        return mode(configName, settingIdFromConfigName(configName), def, options);
    }

    protected final ModeValue mode(String configName, String settingId, String def, String... options) {
        ModeValue value = new ModeValue(configName, def, options);
        declare(value, SettingDef.mode(settingId, value));
        return value;
    }

    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, E def) {
        return enumSetting(configName, settingIdFromConfigName(configName), def);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, String settingId, E def) {
        Class<E> enumClass = def.getDeclaringClass();
        EnumValue<E> value = new EnumValue<>(configName, def, enumClass);
        declare(value, SettingDef.mode(settingId, value));
        return value;
    }

    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, E def, Class<E> enumClass) {
        return enumSetting(configName, settingIdFromConfigName(configName), def, enumClass);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, String settingId, E def, Class<E> enumClass) {
        EnumValue<E> value = new EnumValue<>(configName, def, enumClass);
        declare(value, SettingDef.mode(settingId, value));
        return value;
    }

    @SafeVarargs
    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, E def, E... options) {
        return enumSetting(configName, settingIdFromConfigName(configName), def, options);
    }

    @SafeVarargs
    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, String settingId, E def, E... options) {
        EnumValue<E> value = new EnumValue<>(configName, def, options);
        declare(value, SettingDef.mode(settingId, value));
        return value;
    }

    protected final RGBAColorValue color(String configName, String defHex) {
        return color(configName, settingIdFromConfigName(configName), defHex);
    }

    protected final RGBAColorValue color(String configName, String settingId, String defHex) {
        RGBAColorValue value = new RGBAColorValue(configName, defHex);
        declare(value, SettingDef.color(settingId, value));
        return value;
    }

    protected final RGBColorValue colorNoAlpha(String configName, String defHex) {
        return colorNoAlpha(configName, settingIdFromConfigName(configName), defHex);
    }

    protected final RGBColorValue colorNoAlpha(String configName, String settingId, String defHex) {
        RGBColorValue value = new RGBColorValue(configName, defHex);
        declare(value, SettingDef.colorNoAlpha(settingId, value));
        return value;
    }

    protected final SetValue textList(String configName) {
        return textList(configName, settingIdFromConfigName(configName), TextListSetting.PickerMode.ALL);
    }

    protected final SetValue textList(String configName, String settingId, TextListSetting.PickerMode pickerMode) {
        SetValue value = new SetValue(configName);
        declare(value, SettingDef.textList(settingId, value, pickerMode));
        return value;
    }

    protected final KeyBindValue bind(String configName, String def, BindMode mode) {
        return bind(configName, settingIdFromConfigName(configName), def, mode);
    }

    protected final KeyBindValue bind(String configName, String settingId, String def, BindMode mode) {
        KeyBindValue value = new KeyBindValue(configName, def);
        declare(value, SettingDef.bind(settingId, value, mode));
        return value;
    }

    protected final BooleanMapValue group(String configName, String settingId, Map<String, Boolean> defaults) {
        BooleanMapValue value = new BooleanMapValue(configName, defaults);
        declare(value, SettingDef.group(settingId, value));
        return value;
    }

    protected final <V extends ConfigValue<?>> V visibleWhen(V value, Supplier<Boolean> supplier) {
        SettingDef def = settingDefsByValue.get(value);
        if (def != null) {
            def.visibleWhen(supplier);
        }
        return value;
    }

    protected final <V extends ConfigValue<?>> V common(V value, String commonI18nKey) {
        SettingDef def = settingDefsByValue.get(value);
        if (def != null && commonI18nKey != null && !commonI18nKey.isBlank()) {
            def.common(commonI18nKey);
        }
        return value;
    }

    protected final <V extends ConfigValue<?>> V declareSetting(V value, SettingDef def) {
        declare(value, def);
        return value;
    }

    protected final SettingDef settingDef(ConfigValue<?> value) {
        return settingDefsByValue.get(value);
    }

    protected final SettingDef enabledSettingDef() {
        return SettingDef.bool(enabled);
    }

    public void collectRenderPrewarm(RenderPrewarmCollectEvent event) {
        if (event == null) return;
        event.font("Inter", FontInfo.Type.Regular)
                .font("Inter", FontInfo.Type.Bold)
                .font("InterMedium", FontInfo.Type.Regular)
                .font("OnestMedium", FontInfo.Type.Regular)
                .font("Onest", FontInfo.Type.Regular)
                .font("Onest", FontInfo.Type.Bold)
                .font("OnestMedium", FontInfo.Type.Regular)
                .font("OnestBold", FontInfo.Type.Regular)
                .font("Iosevka", FontInfo.Type.Regular)
                .font("Iosevka", FontInfo.Type.Bold)
                .font("Comfortaa", FontInfo.Type.Regular)
                .font("Icons", FontInfo.Type.Regular)
                .font("IconsNur", FontInfo.Type.Regular)
                .font("WeatherIcons", FontInfo.Type.Regular)
                .font("MediaPlayer", FontInfo.Type.Regular);
    }

    public void onTick() {
    }

    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
    }

    public void renderEngineForeground(Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       GuiGraphicsExtractor ctx,
                                       float tickDelta,
                                       int screenW,
                                       int screenH) {
    }

    public boolean usesEngineRenderer() {
        return false;
    }

    public boolean isMouseOverInteractive(float mx, float my) {
        return false;
    }

    public boolean onMouseClicked(float mx, float my, int button) {
        return false;
    }

    public HudPhase getHudPhase() {
        return HudPhase.AFTER_SUBTITLES;
    }

    public HudRenderSpace getRenderSpace() {
        return HudRenderSpace.UNSCALED_LOGICAL;
    }

    public int getRenderOrder() {
        return 0;
    }

    public final void postInit() {
        ensureSettingsInitialized();
        ConfigSerializer.load(this);
        onLoaded();
    }

    public final String getId() {
        return id;
    }

    public final String getTitle() {
        return translateTitle(title, getTranslationKeyPrefix(), id);
    }

    private static String translateTitle(String fallback, String prefix, String id) {
        List<String> keys = new ArrayList<>(3);
        if (prefix != null && prefix.startsWith("setting.draggable.")) {
            keys.add("hud.draggable." + id);
            keys.add("hud.draggable." + id + ".title");
        } else if (prefix != null && prefix.startsWith("setting.hud_element.")) {
            keys.add("hud.static." + id);
            keys.add("hud.static." + id + ".title");
        }
        keys.add("hud." + id);
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String translated = I18n.get(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return fallback == null ? "" : fallback;
    }

    public boolean isEnabled() {
        return enabled.get() || previewEnabled;
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
        ConfigSerializer.requestSave(this);
    }

    public void setRuntimeEnabledTransient(boolean value) {
        enabled.set(value);
    }

    public void setPreviewEnabled(boolean value) {
        previewEnabled = value;
    }

    public BooleanValue enabledValue() {
        return enabled;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void moveTo(float x, float y) {
        this.x = x;
        this.y = y;
    }

    protected final void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(float mx, float my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    @Override
    public final String name() {
        return id;
    }

    @Override
    public final void saveConfig() {
        ConfigSerializer.requestSave(this);
    }

    @Override
    public final List<SettingDef> getSettingDefs() {
        ensureSettingsInitialized();
        return new ArrayList<>(settingDefs);
    }

    @Override
    public List<ConfigValue<?>> getConfigValues() {
        ensureSettingsInitialized();
        Map<String, ConfigValue<?>> map = new LinkedHashMap<>();
        map.put(enabled.getName(), enabled);
        collectExtraConfigValues(map);
        for (SettingDef def : settingDefs) {
            ConfigValue<?> v = def.value();
            if (v != null) {
                map.put(v.getName(), v);
            }
        }
        return new ArrayList<>(map.values());
    }

    private void ensureSettingsInitialized() {
        if (settingsInitialized) return;
        settingsInitialized = true;
        defineSettings(settingDefs);
        SettingDef.applyI18nAnnotations(this, settingDefs);
    }

    private void declare(ConfigValue<?> value, SettingDef def) {
        settingDefs.add(def);
        settingDefsByValue.put(value, def);
    }

    private String settingIdFromConfigName(String configName) {
        if (configName == null || configName.isBlank()) return configName;

        String tail = stripOwnerPrefix(configName, getId());
        tail = stripOwnerPrefix(tail, getClass().getSimpleName());
        return tail;
    }
}
