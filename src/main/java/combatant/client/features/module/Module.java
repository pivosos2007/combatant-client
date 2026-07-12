/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.config.*;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.config.values.*;
import combatant.client.addon.ModuleExtensionManager;
import combatant.client.features.gui.clickgui.settings.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import combatant.client.config.*;
import combatant.client.config.common.CommonBooleanGroupSchema;
import combatant.client.config.common.CommonSettingSchema;
import combatant.client.config.values.*;
import combatant.client.features.gui.clickgui.settings.*;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.input.KeyManager;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.resources.language.I18n;

import java.util.*;
import java.util.function.Supplier;

public abstract class Module implements ConfigObject, ConfigNameProvider, SettingOwner, ConfigValueAliasProvider {

    private static final String SETTING_SHOW_IN_MODULE_LIST = "show_in_module_list";
    private static final String LEGACY_SETTING_SHOW_IN_ACTIVE_MODULES = "show_in_active_modules";
    protected final List<Setting> settings = new ArrayList<>();
    private final String name;
    private final String displayName;
    private final List<String> aliases;
    private final String description;
    private final ModuleCategory category;
    private final BooleanValue enabledValue;
    private final EnumValue<ModuleActivationSource> activationSourceValue =
            new EnumValue<>("activation_source", ModuleActivationSource.NONE, ModuleActivationSource.class);
    private final BooleanValue moduleListVisibleValue = new BooleanValue(SETTING_SHOW_IN_MODULE_LIST, true);
    private final List<SettingDef> declaredSettingDefs = new ArrayList<>();
    private final Map<ConfigValue<?>, SettingDef> declaredSettingDefsByValue = new IdentityHashMap<>();
    private final KeyBindSetting keyBindSetting;
    private final Map<String, FunctionBindSetting> actions = new LinkedHashMap<>();
    private boolean enabled;
    private ModuleActivationSource activationSource = ModuleActivationSource.NONE;

    protected Module() {
        ModuleInfo info = getClass().getAnnotation(ModuleInfo.class);
        if (info == null) {
            throw new IllegalStateException(getClass().getName() + " must be annotated with @ModuleInfo");
        }

        this.name = info.id().toLowerCase(Locale.ROOT);
        this.displayName = info.displayName();
        this.aliases = normalizeAliases(info.aliases());
        this.description = info.description();
        this.category = info.category();
        this.enabledValue = new BooleanValue("enabled", info.enabledByDefault());
        this.keyBindSetting = createKeyBindSetting();

        settings.add(keyBindSetting);
    }

    @Deprecated(forRemoval = true)
    public Module(String name, String displayName, ModuleCategory category) {
        this(name, displayName, category, "");
    }

    @Deprecated(forRemoval = true)
    public Module(String name, String displayName, ModuleCategory category, String description) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.aliases = List.of();
        this.description = description == null ? "" : description;
        this.category = category;
        this.enabledValue = new BooleanValue("enabled", false);
        this.keyBindSetting = createKeyBindSetting();

        settings.add(keyBindSetting);
    }

    private static KeyBindSetting createKeyBindSetting() {
        return new KeyBindSetting(new KeyBindValue("bind", "NONE"));
    }

    private static List<String> normalizeAliases(String[] aliases) {
        if (aliases == null || aliases.length == 0) return List.of();

        List<String> out = new ArrayList<>(aliases.length);
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) continue;
            out.add(alias.trim());
        }
        return List.copyOf(out);
    }

    private static String stripOwnerPrefix(String value, String owner) {
        if (value == null || value.isBlank() || owner == null || owner.isBlank()) return value;

        String normalizedOwner = normalizePrefix(owner);
        String lowerValue = value.toLowerCase(java.util.Locale.ROOT);
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

    private static ModuleActivationSource normalizeActivationSource(boolean state, ModuleActivationSource source) {
        if (!state) return ModuleActivationSource.NONE;
        return source == null || source == ModuleActivationSource.NONE ? ModuleActivationSource.INTERNAL : source;
    }

    /**
     * Must be called after subclass construction completes so that its fields are initialized
     * before settings are added. ModuleAutoLoader calls this automatically.
     */
    public final void postInit() {
        SettingDef.applyI18nAnnotations(this, declaredSettingDefs);
        settings.addAll(SettingFactory.fromDefs(declaredSettingDefs));
        loadSettings(settings);

        // attach parent for settings so they can request saves
        for (Setting s : settings) {
            s.setParent(this);
            s.preflightI18n();
        }

        ModuleManager.register(this);
    }

    @Deprecated(forRemoval = false)
    protected void loadSettings(List<Setting> settings) {
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

    protected final NumberValue<Long> num(String configName, long def, long min, long max) {
        return num(configName, settingIdFromConfigName(configName), def, min, max);
    }

    protected final NumberValue<Long> num(String configName, String settingId, long def, long min, long max) {
        NumberValue<Long> value = new NumberValue<>(configName, def, min, max);
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
        return modeSetting(configName, settingIdFromConfigName(configName), def, options);
    }

    protected final ModeValue modeSetting(String configName, String settingId, String def, String... options) {
        ModeValue value = new ModeValue(configName, def, options);
        declare(value, SettingDef.mode(settingId, value));
        return value;
    }

    protected final <E extends Enum<E>> EnumValue<E> enumMode(String configName, E def) {
        return enumSetting(configName, settingIdFromConfigName(configName), def);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, String settingId, E def) {
        Class<E> enumClass = def.getDeclaringClass();
        EnumValue<E> value = new EnumValue<>(configName, def, enumClass);
        declare(value, SettingDef.mode(settingId, value));
        return value;
    }

    @Deprecated(forRemoval = false)
    protected final <E extends Enum<E>> EnumValue<E> enumMode(String configName, E def, Class<E> enumClass) {
        return enumSetting(configName, settingIdFromConfigName(configName), def, enumClass);
    }

    @Deprecated(forRemoval = false)
    protected final <E extends Enum<E>> EnumValue<E> enumSetting(String configName, String settingId, E def, Class<E> enumClass) {
        EnumValue<E> value = new EnumValue<>(configName, def, enumClass);
        declare(value, SettingDef.mode(settingId, value));
        return value;
    }

    @SafeVarargs
    protected final <E extends Enum<E>> EnumValue<E> enumMode(String configName, E def, E... options) {
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

    protected final StringValue text(String configName, String def) {
        return text(configName, settingIdFromConfigName(configName), def);
    }

    protected final StringValue text(String configName, String settingId, String def) {
        StringValue value = new StringValue(configName, def);
        declare(value, SettingDef.text(settingId, value));
        return value;
    }

    protected final SetValue textList(String configName) {
        return textList(configName, settingIdFromConfigName(configName), TextListSetting.PickerMode.ALL);
    }

    protected final SetValue textList(String configName, TextListSetting.PickerMode pickerMode) {
        return textList(configName, settingIdFromConfigName(configName), pickerMode);
    }

    protected final SetValue textList(String configName, String settingId, TextListSetting.PickerMode pickerMode) {
        SetValue value = new SetValue(configName);
        declare(value, SettingDef.textList(settingId, value, pickerMode));
        return value;
    }

    protected final SetValue textList(String configName, String settingId, TextListSetting.PickerMode pickerMode, Set<String> defaults) {
        SetValue value = new SetValue(configName, defaults);
        declare(value, SettingDef.textList(settingId, value, pickerMode));
        return value;
    }

    protected final ItemIdSetValue itemList(String configName) {
        return itemList(configName, settingIdFromConfigName(configName), TextListSetting.PickerMode.ITEMS);
    }

    protected final ItemIdSetValue itemList(String configName, TextListSetting.PickerMode pickerMode) {
        return itemList(configName, settingIdFromConfigName(configName), pickerMode);
    }

    protected final ItemIdSetValue itemList(String configName, String settingId, TextListSetting.PickerMode pickerMode) {
        ItemIdSetValue value = new ItemIdSetValue(configName);
        declare(value, SettingDef.textList(settingId, value, pickerMode));
        return value;
    }

    protected final BooleanMapValue group(String configName, Map<String, Boolean> defaults) {
        return group(configName, settingIdFromConfigName(configName), defaults);
    }

    protected final BooleanMapValue group(String configName, String settingId, Map<String, Boolean> defaults) {
        BooleanMapValue value = new BooleanMapValue(configName, defaults);
        declare(value, SettingDef.group(settingId, value));
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

    protected final BooleanMapValue groupCommon(String configName,
                                                String commonI18nKey,
                                                Map<String, Boolean> defaults) {
        return groupCommon(configName, settingIdFromConfigName(configName), commonI18nKey, defaults);
    }

    protected final BooleanMapValue groupCommon(String configName,
                                                String settingId,
                                                String commonI18nKey,
                                                Map<String, Boolean> defaults) {
        return common(group(configName, settingId, defaults), commonI18nKey);
    }

    protected final ModeValue modeCommon(String configName,
                                         String commonI18nKey,
                                         String def,
                                         String... options) {
        return modeCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def, options);
    }

    protected final ModeValue modeCommon(String configName,
                                         String settingId,
                                         String commonI18nKey,
                                         String def,
                                         String... options) {
        return common(modeSetting(configName, settingId, def, options), commonI18nKey);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String commonI18nKey,
                                                                E def) {
        return enumCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String settingId,
                                                                String commonI18nKey,
                                                                E def) {
        return common(enumSetting(configName, settingId, def), commonI18nKey);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String commonI18nKey,
                                                                E def,
                                                                Class<E> enumClass) {
        return enumCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def, enumClass);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String settingId,
                                                                String commonI18nKey,
                                                                E def,
                                                                Class<E> enumClass) {
        return common(enumSetting(configName, settingId, def, enumClass), commonI18nKey);
    }

    @SafeVarargs
    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String commonI18nKey,
                                                                E def,
                                                                E... options) {
        return enumCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def, options);
    }

    @SafeVarargs
    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String settingId,
                                                                String commonI18nKey,
                                                                E def,
                                                                E... options) {
        return common(enumSetting(configName, settingId, def, options), commonI18nKey);
    }

    protected final NumberValue<Integer> numCommon(String configName,
                                                   String commonI18nKey,
                                                   int def,
                                                   int min,
                                                   int max) {
        return numCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def, min, max);
    }

    protected final NumberValue<Integer> numCommon(String configName,
                                                   String settingId,
                                                   String commonI18nKey,
                                                   int def,
                                                   int min,
                                                   int max) {
        return common(num(configName, settingId, def, min, max), commonI18nKey);
    }

    protected final NumberValue<Float> numCommon(String configName,
                                                 String commonI18nKey,
                                                 float def,
                                                 float min,
                                                 float max) {
        return numCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def, min, max);
    }

    protected final NumberValue<Float> numCommon(String configName,
                                                 String settingId,
                                                 String commonI18nKey,
                                                 float def,
                                                 float min,
                                                 float max) {
        return common(num(configName, settingId, def, min, max), commonI18nKey);
    }

    protected final NumberValue<Double> numCommon(String configName,
                                                  String commonI18nKey,
                                                  double def,
                                                  double min,
                                                  double max) {
        return numCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def, min, max);
    }

    protected final NumberValue<Double> numCommon(String configName,
                                                  String settingId,
                                                  String commonI18nKey,
                                                  double def,
                                                  double min,
                                                  double max) {
        return common(num(configName, settingId, def, min, max), commonI18nKey);
    }

    protected final BooleanValue boolCommon(String configName,
                                            String commonI18nKey,
                                            boolean def) {
        return boolCommon(configName, settingIdFromConfigName(configName), commonI18nKey, def);
    }

    protected final BooleanValue boolCommon(String configName,
                                            String settingId,
                                            String commonI18nKey,
                                            boolean def) {
        return common(bool(configName, settingId, def), commonI18nKey);
    }

    protected final BooleanMapValue groupCommon(String configName,
                                                CommonBooleanGroupSchema schema) {
        return groupCommon(configName, settingIdFromConfigName(configName), schema);
    }

    protected final BooleanMapValue groupCommon(String configName,
                                                String settingId,
                                                CommonBooleanGroupSchema schema) {
        if (schema == null) {
            return group(configName, settingId, Map.of());
        }
        return common(group(configName, settingId, schema.defaults()), schema);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                CommonSettingSchema schema,
                                                                E def) {
        return enumCommon(configName, settingIdFromConfigName(configName), schema, def);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String settingId,
                                                                CommonSettingSchema schema,
                                                                E def) {
        return common(enumSetting(configName, settingId, def), schema);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                CommonSettingSchema schema,
                                                                E def,
                                                                Class<E> enumClass) {
        return enumCommon(configName, settingIdFromConfigName(configName), schema, def, enumClass);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String settingId,
                                                                CommonSettingSchema schema,
                                                                E def,
                                                                Class<E> enumClass) {
        return common(enumSetting(configName, settingId, def, enumClass), schema);
    }

    @SafeVarargs
    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                CommonSettingSchema schema,
                                                                E def,
                                                                E... options) {
        return enumCommon(configName, settingIdFromConfigName(configName), schema, def, options);
    }

    @SafeVarargs
    protected final <E extends Enum<E>> EnumValue<E> enumCommon(String configName,
                                                                String settingId,
                                                                CommonSettingSchema schema,
                                                                E def,
                                                                E... options) {
        return common(enumSetting(configName, settingId, def, options), schema);
    }

    protected final ModeValue modeCommon(String configName,
                                         CommonSettingSchema schema,
                                         String def,
                                         String... options) {
        return modeCommon(configName, settingIdFromConfigName(configName), schema, def, options);
    }

    protected final ModeValue modeCommon(String configName,
                                         String settingId,
                                         CommonSettingSchema schema,
                                         String def,
                                         String... options) {
        return common(modeSetting(configName, settingId, def, options), schema);
    }

    protected final NumberValue<Integer> numCommon(String configName,
                                                   CommonSettingSchema schema,
                                                   int def,
                                                   int min,
                                                   int max) {
        return numCommon(configName, settingIdFromConfigName(configName), schema, def, min, max);
    }

    protected final NumberValue<Integer> numCommon(String configName,
                                                   String settingId,
                                                   CommonSettingSchema schema,
                                                   int def,
                                                   int min,
                                                   int max) {
        return common(num(configName, settingId, def, min, max), schema);
    }

    protected final NumberValue<Float> numCommon(String configName,
                                                 CommonSettingSchema schema,
                                                 float def,
                                                 float min,
                                                 float max) {
        return numCommon(configName, settingIdFromConfigName(configName), schema, def, min, max);
    }

    protected final NumberValue<Float> numCommon(String configName,
                                                 String settingId,
                                                 CommonSettingSchema schema,
                                                 float def,
                                                 float min,
                                                 float max) {
        return common(num(configName, settingId, def, min, max), schema);
    }

    protected final NumberValue<Double> numCommon(String configName,
                                                  CommonSettingSchema schema,
                                                  double def,
                                                  double min,
                                                  double max) {
        return numCommon(configName, settingIdFromConfigName(configName), schema, def, min, max);
    }

    protected final NumberValue<Double> numCommon(String configName,
                                                  String settingId,
                                                  CommonSettingSchema schema,
                                                  double def,
                                                  double min,
                                                  double max) {
        return common(num(configName, settingId, def, min, max), schema);
    }

    protected final BooleanValue boolCommon(String configName,
                                            CommonSettingSchema schema,
                                            boolean def) {
        return boolCommon(configName, settingIdFromConfigName(configName), schema, def);
    }

    protected final BooleanValue boolCommon(String configName,
                                            String settingId,
                                            CommonSettingSchema schema,
                                            boolean def) {
        return common(bool(configName, settingId, def), schema);
    }

    protected final <V extends ConfigValue<?>> V visibleWhen(V value, Supplier<Boolean> supplier) {
        SettingDef def = declaredSettingDefsByValue.get(value);
        if (def != null) {
            def.visibleWhen(supplier);
        }
        return value;
    }

    protected final <V extends ConfigValue<?>> V shownWhen(V value, Supplier<Boolean> supplier) {
        return visibleWhen(value, supplier);
    }

    protected final <V extends ConfigValue<?>> V unavailableWhen(V value, Supplier<Boolean> supplier, String reason) {
        SettingDef def = declaredSettingDefsByValue.get(value);
        if (def != null) {
            def.unavailableWhen(supplier, reason);
        }
        return value;
    }

    protected final <V extends ConfigValue<?>> V unavailableWhen(V value, Supplier<Boolean> supplier, Supplier<String> reason) {
        SettingDef def = declaredSettingDefsByValue.get(value);
        if (def != null) {
            def.unavailableWhen(supplier, reason);
        }
        return value;
    }

    protected final <V extends ConfigValue<?>> V notAppliedWhen(V value, Supplier<Boolean> supplier, String reason) {
        return unavailableWhen(value, supplier, reason);
    }

    protected final <V extends ConfigValue<?>> V notAppliedWhen(V value, Supplier<Boolean> supplier, Supplier<String> reason) {
        return unavailableWhen(value, supplier, reason);
    }

    protected final <V extends ConfigValue<?>> V appliesWhen(V value, Supplier<Boolean> supplier, String reasonWhenUnavailable) {
        SettingDef def = declaredSettingDefsByValue.get(value);
        if (def != null) {
            def.availableWhen(supplier, reasonWhenUnavailable);
        }
        return value;
    }

    protected final <V extends ConfigValue<?>> V appliesWhen(V value, Supplier<Boolean> supplier, Supplier<String> reasonWhenUnavailable) {
        SettingDef def = declaredSettingDefsByValue.get(value);
        if (def != null) {
            def.availableWhen(supplier, reasonWhenUnavailable);
        }
        return value;
    }

    private void declare(ConfigValue<?> value, SettingDef def) {
        declaredSettingDefs.add(def);
        declaredSettingDefsByValue.put(value, def);
    }

    protected final SettingDef setting(SettingDef def) {
        if (def != null && def.value() != null) {
            declare(def.value(), def);
        }
        return def;
    }

    public final void addExtensionSetting(SettingDef def) {
        Setting setting = SettingFactory.fromDef(def);
        if (setting == null) return;
        setting.setParent(this);
        setting.preflightI18n();
        settings.add(setting);
        if (def != null && def.value() != null) {
            declaredSettingDefsByValue.put(def.value(), def);
        }
    }

    protected final <V extends ConfigValue<?>> V common(V value, String commonI18nKey) {
        SettingDef def = declaredSettingDefsByValue.get(value);
        if (def != null && commonI18nKey != null && !commonI18nKey.isBlank()) {
            def.common(commonI18nKey);
        }
        return value;
    }

    protected final <V extends ConfigValue<?>> V common(V value, CommonSettingSchema schema) {
        SettingDef def = declaredSettingDefsByValue.get(value);
        if (def != null && schema != null) {
            def.common(schema);
        }
        return value;
    }

    private String settingIdFromConfigName(String configName) {
        if (configName == null || configName.isBlank()) return configName;

        String tail = stripOwnerPrefix(configName, name());
        tail = stripOwnerPrefix(tail, getClass().getSimpleName());
        return tail;
    }

    @Override
    public List<String> getLegacyValueNames(String currentValueName) {
        if (SETTING_SHOW_IN_MODULE_LIST.equals(currentValueName)) {
            return List.of(LEGACY_SETTING_SHOW_IN_ACTIVE_MODULES);
        }
        return List.of();
    }

    public List<Setting> getSettings() {
        return settings;
    }

    public ConfigValue<?> getConfigValue(String configName) {
        if (configName == null || configName.isBlank()) return null;
        for (ConfigValue<?> value : getConfigValues()) {
            if (value != null && configName.equals(value.getName())) {
                return value;
            }
        }
        return null;
    }

    public KeyBindSetting getKeyBindSetting() {
        return keyBindSetting;
    }

    public Collection<FunctionBindSetting> getActionSettings() {
        return Collections.unmodifiableCollection(actions.values());
    }

    public void setDefaultBind(String keyName) {
        keyBindSetting.setKeyByName(keyName);
    }

    protected final FunctionBindSetting action(String name, String defaultKey, BindMode mode) {
        FunctionBindSetting setting = new FunctionBindSetting(name, defaultKey, mode);
        actions.put(name, setting);
        settings.add(setting);
        return setting;
    }

    protected final FunctionBindSetting action(String name, String defaultKey) {
        return action(name, defaultKey, BindMode.PRESS);
    }

    protected final void addAction(String name, String defaultKey, BindMode mode) {
        action(name, defaultKey, mode);
    }

    protected final void addAction(String name, String defaultKey) {
        action(name, defaultKey);
    }

    protected final boolean isActionPressedOnce(String name) {
        if (!isEnabled()) return false;
        FunctionBindSetting setting = actions.get(name);
        if (setting == null) return false;
        return KeyManager.wasPressed(setting.getBindingName());
    }

    protected final boolean isActionHeld(String name) {
        if (!isEnabled()) return false;
        FunctionBindSetting setting = actions.get(name);
        if (setting == null) return false;
        return KeyManager.isHeld(setting.getBindingName());
    }

    protected final String getActionKey(String name) {
        FunctionBindSetting setting = actions.get(name);
        return setting == null ? "NONE" : setting.get();
    }

    public boolean isShownInModuleList() {
        return moduleListVisibleValue.get();
    }

    public void setShownInModuleList(boolean value) {
        if (moduleListVisibleValue.get() == value) return;
        moduleListVisibleValue.set(value);
        try {
            ConfigSerializer.requestSave(this);
        } catch (Throwable ignored) {
        }
    }

    public void toggleShownInModuleList() {
        setShownInModuleList(!moduleListVisibleValue.get());
    }

    public boolean isEnabled() {
        return enabled;
    }

    protected String getUnavailableReason() {
        return "";
    }

    public final boolean isAvailable() {
        return getAvailabilityReason().isEmpty();
    }

    public final String getAvailabilityReason() {
        try {
            String reason = getUnavailableReason();
            return reason == null ? "" : reason.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public final void setEnabled(boolean state) {
        setEnabled(state, ModuleActivationSource.MANUAL);
    }

    public ModuleActivationSource getActivationSource() {
        return activationSource;
    }

    public final void setActivationSource(ModuleActivationSource source) {
        activationSource = normalizeActivationSource(enabled, source);
        activationSourceValue.set(activationSource);
        try {
            ConfigSerializer.requestSave(this);
        } catch (Throwable ignored) {
        }
    }

    public boolean isEnabledFromKeybind() {
        return enabled && activationSource == ModuleActivationSource.KEYBIND;
    }

    public final void setEnabledFromKeybind(boolean state) {
        setEnabled(state, ModuleActivationSource.KEYBIND);
    }

    public String name() {
        return name;
    }

    @Override
    public String getConfigName() {
        return name();
    }

    public String getDisplayName() {
        return translateFirst(displayName, "module." + name(), "module." + name() + ".name");
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getDescription() {
        return translateFirst(description, "module." + name() + ".description");
    }

    public ModuleCategory getCategory() {
        return category;
    }

    private static String translateFirst(String fallback, String... keys) {
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String translated = I18n.get(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return fallback == null ? "" : fallback;
    }

    public final void toggle() {
        setEnabled(!enabled, ModuleActivationSource.MANUAL);
    }

    public final void toggleFromKeybind() {
        setEnabled(!enabled, ModuleActivationSource.KEYBIND);
    }

    public final void setEnabled(boolean state, ModuleActivationSource source) {
        if (state && !RuntimeGate.canRunModules() && !(this instanceof RuntimeControlModule)) {
            return;
        }
        ModuleActivationSource normalizedSource = normalizeActivationSource(state, source);
        if (enabled == state) {
            if (activationSource == normalizedSource
                    && enabledValue.get() == enabled
                    && activationSourceValue.get() == activationSource) {
                return;
            }
            activationSource = normalizedSource;
            enabledValue.set(enabled);
            activationSourceValue.set(activationSource);
            try {
                ConfigSerializer.requestSave(this);
            } catch (Throwable ignored) {
            }
            return;
        }

        activationSource = normalizedSource;
        enabled = state;
        if (enabled) {
            if (!ModuleExtensionManager.beforeEnable(this)) {
                enabled = false;
                activationSource = ModuleActivationSource.NONE;
                return;
            }
            onEnable();
            ModuleExtensionManager.afterEnable(this);
        } else {
            if (!ModuleExtensionManager.beforeDisable(this)) {
                enabled = true;
                activationSource = normalizedSource;
                return;
            }
            onDisable();
            ModuleExtensionManager.afterDisable(this);
        }
        ModuleManager.notifyListeners(name, enabled);

        // Persist change
        enabledValue.set(enabled);
        activationSourceValue.set(activationSource);
        try {
            ConfigSerializer.requestSave(this);
        } catch (Throwable ignored) {
        }
    }

    public final void setEnabledFromConfig(boolean state) {
        setEnabled(state, ModuleActivationSource.CONFIG);
    }

    public final void setEnabledInternal(boolean state) {
        setEnabled(state, ModuleActivationSource.INTERNAL);
    }

    public final void setEnabledManual(boolean state) {
        setEnabled(state, ModuleActivationSource.MANUAL);
    }

    final void setRuntimeEnabledTransient(boolean state) {
        if (enabled == state) return;
        enabled = state;
        if (enabled) {
            if (ModuleExtensionManager.beforeEnable(this)) {
                onEnable();
                ModuleExtensionManager.afterEnable(this);
            } else {
                enabled = false;
            }
        } else if (ModuleExtensionManager.beforeDisable(this)) {
            onDisable();
            ModuleExtensionManager.afterDisable(this);
        } else {
            enabled = true;
        }
        ModuleManager.notifyListeners(name, enabled);
    }

    public void collectRenderPrewarm(RenderPrewarmCollectEvent event) {
        if (event == null) return;
        event.font("Onest", FontInfo.Type.Regular)
                .font("OnestMedium", FontInfo.Type.Regular)
                .font("OnestBold", FontInfo.Type.Regular)
                .font("Inter", FontInfo.Type.Regular)
                .font("Inter", FontInfo.Type.Bold)
                .font("OnestMedium", FontInfo.Type.Regular)
                .font("Iosevka", FontInfo.Type.Regular)
                .font("Iosevka", FontInfo.Type.Bold);
    }

    public void onTick() {
    }

    public void onFrame(float tickDelta) {
    }

    public void onKey(int key, int action) {
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onRender2D(GuiGraphicsExtractor ctx) {
    }

    public void onRender2D(GuiGraphicsExtractor ctx, float tickDelta) {
        onRender2D(ctx);
    }

    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer) {
    }

    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, float tickDelta) {
        onRenderHudEngine(renderer, textRenderer);
    }

    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        onRenderHudEngine(renderer, textRenderer, tickDelta);
    }

    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
    }

    public void onRenderWorld(PoseStack matrices, SubmitNodeCollector consumers) {
    }

    public void onRenderWorld(PoseStack matrices, SubmitNodeCollector consumers, float tickDelta) {
        onRenderWorld(matrices, consumers);
    }

    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer) {
    }

    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        onRenderWorldEngine(renderer, depthRenderer);
    }

    public HudPhase getHudPhase() {
        return HudPhase.NONE;
    }

    public WorldPhase getWorldPhase() {
        return WorldPhase.NONE;
    }

    @Override
    public List<ConfigValue<?>> getConfigValues() {
        Map<String, ConfigValue<?>> map = new LinkedHashMap<>();

        map.put(enabledValue.getName(), enabledValue);
        map.put(activationSourceValue.getName(), activationSourceValue);
        map.put(moduleListVisibleValue.getName(), moduleListVisibleValue);

        for (Setting s : settings) {
            ConfigValue<?> v = s.getConfigValue();
            if (v != null) {
                map.put(v.getName(), v);
            }
        }

        return new ArrayList<>(map.values());
    }

    /**
     * Legacy support: apply values from the old "settings" map saved by previous versions.
     */
    public void applyLegacySettings(Object json) {
        if (json == null) return;

        if (json instanceof de.marhali.json5.Json5Object obj) {
            for (var e : obj.asMap().entrySet()) {
                applySetting(e.getKey(), e.getValue());
            }
        } else if (json instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                if (e.getKey() instanceof String k) {
                    applySetting(k, e.getValue());
                }
            }
        }
    }

    private void applySetting(String id, Object data) {
        if (SETTING_SHOW_IN_MODULE_LIST.equals(id) || LEGACY_SETTING_SHOW_IN_ACTIVE_MODULES.equals(id)) {
            moduleListVisibleValue.fromJson(data);
            return;
        }
        for (Setting s : settings) {
            if (s.getId().equals(id)) {
                s.load(data);
                break;
            }
        }
    }

    /**
     * Re-applies values to settings to trigger any side effects in Setting.load().
     */
    public void syncSettingsAfterLoad() {
        for (Setting s : settings) {
            ConfigValue<?> v = s.getConfigValue();
            if (v != null) {
                s.load(v.toJson());
            }
        }
    }

    public void applyConfiguredEnabledState() {
        setEnabled(enabledValue.get(), activationSourceValue.get());
    }

    // Helpers for ModuleManager orchestration
    public void loadAndApplyConfig() {
        try {
            DebugLog.config("Module config load -> %s", name());
            ConfigSerializer.load(this);
        } catch (Throwable ignored) {
            DebugLog.error("Module config load failed for %s", ignored, name());
        }
        enabled = enabledValue.get();
        activationSource = normalizeActivationSource(enabled, activationSourceValue.get());
        activationSourceValue.set(activationSource);
        if (enabled) {
            if (ModuleExtensionManager.beforeEnable(this)) {
                onEnable();
                ModuleExtensionManager.afterEnable(this);
            } else {
                enabled = false;
                activationSource = ModuleActivationSource.NONE;
                enabledValue.set(false);
                activationSourceValue.set(activationSource);
            }
        }
    }

    public void saveConfig() {
        try {
            ConfigSerializer.requestSave(this);
        } catch (Throwable ignored) {
        }
    }
}
