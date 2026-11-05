/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.automation.compat.litematica.impl;

import combatant.client.automation.compat.litematica.LitematicaWorkspace;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.litematica.config.Hotkeys;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;
import java.nio.file.Files;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import fi.dy.masa.malilib.config.IConfigBase;
import combatant.client.util.text.LegacyTextUtil;
import net.minecraft.client.resources.language.I18n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.position.LayerRange;
import fi.dy.masa.malilib.util.LayerMode;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.gui.Message.MessageType;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public final class LitematicaWorkspaceImpl implements LitematicaWorkspace {
    private static final IMessageConsumer PLACEMENT_FEEDBACK = new IMessageConsumer() {
        @Override public void addMessage(MessageType type, String messageKey, Object... args) { }
        @Override public void addMessage(MessageType type, int lifeTime, String messageKey, Object... args) { }
    };

    private static List<SchematicPlacement> current() {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
    }
    private static SchematicPlacement get(int index) {
        List<SchematicPlacement> items = current();
        return index < 0 || index >= items.size() ? null : items.get(index);
    }
    @Override public ImportResult importFile(Path file) {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file))
            return new ImportResult(false, "File is not readable", -1);
        try {
            String name = file.getFileName().toString();
            String suffix = name.toLowerCase(java.util.Locale.ROOT);
            Path dir = file.getParent();
            LitematicaSchematic schematic;
            if (suffix.endsWith(".litematic")) {
                schematic = SchematicHolder.getInstance().getOrLoad(file);
            } else if (suffix.endsWith(".schem")) {
                schematic = WorldUtils.convertSpongeSchematicToLitematicaSchematic(dir, name);
            } else if (suffix.endsWith(".schematic")) {
                schematic = WorldUtils.convertSchematicaSchematicToLitematicaSchematic(dir, name, false, msg -> {});
            } else if (suffix.endsWith(".nbt")) {
                schematic = WorldUtils.convertStructureToLitematicaSchematic(dir, name);
            } else {
                return new ImportResult(false, "Unsupported format", -1);
            }
            if (schematic == null) return new ImportResult(false, "Litematica could not read this file", -1);
            SchematicHolder.getInstance().addSchematic(schematic, false);
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            BlockPos origin = Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.blockPosition() : BlockPos.ZERO;
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin,
                    schematic.getMetadata().getName(), true, true);
            manager.addSchematicPlacement(placement, false);
            manager.setSelectedSchematicPlacement(placement);
            return new ImportResult(true, "Loaded in Litematica", current().indexOf(placement));
        } catch (RuntimeException | LinkageError ex) {
            return new ImportResult(false, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), -1);
        }
    }
    @Override public boolean removeSelected() {
        SchematicPlacement selected = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        return selected != null && DataManager.getSchematicPlacementManager().removeSchematicPlacement(selected);
    }
    @Override public boolean renderingEnabled() {
        return Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue();
    }
    @Override public boolean toggleRendering() {
        Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.setBooleanValue(!renderingEnabled());
        Configs.saveToFile();
        SchematicWorldRefresher.INSTANCE.updateAll();
        return renderingEnabled();
    }
    @Override public List<Placement> placements() {
        List<Placement> out = new ArrayList<>();
        SchematicPlacement selected = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        List<SchematicPlacement> placements = current();
        for (int i = 0; i < placements.size(); i++) {
            SchematicPlacement entry = placements.get(i);
            out.add(new Placement(i, entry.getName(), entry.getOrigin(), entry == selected,
                    entry.isEnabled(), entry.isLocked(), entry.getSchematicFile(), entry.getSubRegionCount()));
        }
        return out;
    }
    @Override public boolean select(int index) {
        SchematicPlacement p = get(index);
        if (p == null) return false;
        DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(p);
        return true;
    }
    @Override public boolean setEnabled(int index, boolean enabled) {
        SchematicPlacement p = get(index);
        if (p == null || p.isLocked()) return false;
        p.setEnabled(enabled);
        return true;
    }
    @Override public boolean setOrigin(int index, BlockPos origin) {
        SchematicPlacement p = get(index);
        if (p == null || p.isLocked() || origin == null) return false;
        p.setOrigin(origin, message -> {});
        return p.getOrigin().equals(origin); // coordinate locks may reject individual axes
    }
    private static List<? extends IConfigBase> group(String section) {
        return switch (section) {
            case "visuals" -> Configs.Visuals.OPTIONS;
            case "overlays" -> Configs.InfoOverlays.OPTIONS;
            case "colors" -> Configs.Colors.OPTIONS;
            case "hotkeys" -> Hotkeys.HOTKEY_LIST;
            default -> Configs.Generic.OPTIONS;
        };
    }
    private static final ClassValue<Map<String, Optional<Method>>> READ_METHODS = new ClassValue<>() {
        @Override protected Map<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static Method method(Object obj, String name) {
        if (obj == null) return null;
        return READ_METHODS.get(obj.getClass()).computeIfAbsent(name, key -> {
            try {return Optional.of(obj.getClass().getMethod(key));}
            catch (NoSuchMethodException ex) {return Optional.empty();}
        }).orElse(null);
    }
    private static Object invoke(Object obj, String name) {
        Method get = method(obj, name);
        if (get == null) return null;
        try { return get.invoke(obj); }
        catch (ReflectiveOperationException | RuntimeException ex) { return null; }
    }
    private static String localText(Object original) {
        if (original == null) return "";
        String value = original.toString();
        // I18n.get returns the key unchanged when no vanilla translation exists.
        // Avoid depending on version-specific I18n.exists overloads.
        value = I18n.get(value);
        return LegacyTextUtil.convertLegacyCodesRobust(net.minecraft.network.chat.Component.literal(value)).getString();
    }

    /** Get options from MaLiLib's enum/list implementations without maintaining our own list. */
    private static List<Object> possibleChoices(Object current) {
        if (current == null) return List.of();
        Object all = invoke(current, "getAllValues");
        if (all instanceof java.util.Collection<?> choices) return new ArrayList<>(choices);
        if (all != null && all.getClass().isArray()) {
            int size = java.lang.reflect.Array.getLength(all);
            List<Object> choices = new ArrayList<>(size);
            for (int i = 0; i < size; i++) choices.add(java.lang.reflect.Array.get(all, i));
            return choices;
        }
        if (current instanceof Enum<?> e) return new ArrayList<>(List.of(e.getDeclaringClass().getEnumConstants()));
        return List.of();
    }
    private static String optionName(Object object) {
        Object display = invoke(object, "getStringValue");
        if (display == null) display = invoke(object, "getDisplayName");
        return localText(display == null ? object : display);
    }

    private static Option describe(IConfigBase config) {
        String kind = "string";
        Object value = null;
        if ((value = invoke(config, "getBooleanValue")) instanceof Boolean) kind = "boolean";
        else if ((value = invoke(config, "getIntegerValue")) instanceof Number) kind = "number";
        else if ((value = invoke(config, "getDoubleValue")) instanceof Number) kind = "number";
        else if ((value = invoke(config, "getFloatValue")) instanceof Number) kind = "number";
        else if ((value = invoke(config, "getOptionListValue")) != null) kind = "choice";
        else if ((value = invoke(config, "getKeybind")) != null) {
            kind = "hotkey";
            Object display = invoke(value, "getKeysDisplayString");
            if (display != null) value = display;
        }
        else if ((value = invoke(config, "getStrings")) instanceof List<?> entries) {
            kind = "list";
            value = String.join(", ", entries.stream().map(Object::toString).toList());
        }
        else if ((value = invoke(config, "getColor")) != null) kind = "color";
        else if ((value = invoke(config, "getStringValue")) != null) kind = "string";
        else if ((value = invoke(config, "getAsJsonElement")) != null) kind = "json";
        if (config.getClass().getSimpleName().contains("Color")) {
            kind = "color";
            Object color = invoke(config, "getStringValue");
            if (color != null) value = color;
        }
        String label = config.getName().replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        Object pretty = invoke(config, "getPrettyName");
        if (pretty instanceof String str && !str.isBlank()) label = str;
        Object comment = invoke(config, "getComment");
        if (kind.equals("choice")) value = optionName(value);
        Object choice = kind.equals("choice") ? invoke(config, "getOptionListValue") : null;
        List<String> choices = possibleChoices(choice).stream().map(LitematicaWorkspaceImpl::optionName).toList();
        String min = "", max = "";
        if (kind.equals("number")) {
            Object low = invoke(config, "getMinIntegerValue");
            Object high = invoke(config, "getMaxIntegerValue");
            if (!(low instanceof Number) || !(high instanceof Number)) {
                low = invoke(config, "getMinDoubleValue");
                high = invoke(config, "getMaxDoubleValue");
            }
            if (!(low instanceof Number) || !(high instanceof Number)) {
                low = invoke(config, "getMinFloatValue");
                high = invoke(config, "getMaxFloatValue");
            }
            if (low instanceof Number && high instanceof Number) {
                min = low.toString(); max = high.toString();
            }
        }
        if (config.getName().equals("customSchematicBaseDirectory") && kind.equals("string")) kind = "directory";
        return new Option(config.getName(), localText(label), kind, localText(value),
                localText(comment), choices, min, max);
    }
    @Override public List<Option> options(String section) {
        List<Option> out = new ArrayList<>();
        for (IConfigBase option : group(section)) out.add(describe(option));
        return out;
    }
    @Override public boolean toggleOption(String section, String id) {
        for (IConfigBase option : group(section)) {
            if (!option.getName().equals(id)) continue;
            Object current = invoke(option, "getBooleanValue");
            if (current instanceof Boolean value) return setOption(section, id, Boolean.toString(!value));
            Object choice = invoke(option, "getOptionListValue");
            if (choice != null) {
                try {
                    Method cycle = choice.getClass().getMethod("cycle", boolean.class);
                    Object next = cycle.invoke(choice, true);
                    for (Method setter : option.getClass().getMethods()) {
                        if (!setter.getName().equals("setOptionListValue") || setter.getParameterCount() != 1) continue;
                        if (!setter.getParameterTypes()[0].isInstance(next)) continue;
                        setter.invoke(option, next);
                        saveOptionChange();
                        return true;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            return false;
        }
        return false;
    }
    @Override public boolean setHotkeyKeys(String id, List<Integer> keys) {
        if (keys == null || keys.size() > 8) return false;
        for (IConfigBase config : group("hotkeys")) {
            if (!config.getName().equals(id)) continue;
            Object nativeBind = invoke(config, "getKeybind");
            if (!(nativeBind instanceof IKeybind bind)) return false;
            for (Integer code : keys) if (code == null || code == 0) return false;
            bind.clearKeys();
            for (Integer code : keys) bind.addKey(code);
            saveOptionChange();
            return true;
        }
        return false;
    }
    @Override public boolean setOption(String section, String id, String text) {
        if (text == null) return false;
        for (IConfigBase option : group(section)) {
            if (!option.getName().equals(id)) continue;
            Object numeric = invoke(option, "getIntegerValue");
            Object minimum = invoke(option, "getMinIntegerValue");
            Object maximum = invoke(option, "getMaxIntegerValue");
            if (!(numeric instanceof Number)) {
                numeric = invoke(option, "getDoubleValue");
                minimum = invoke(option, "getMinDoubleValue");
                maximum = invoke(option, "getMaxDoubleValue");
            }
            if (!(numeric instanceof Number)) {
                numeric = invoke(option, "getFloatValue");
                minimum = invoke(option, "getMinFloatValue");
                maximum = invoke(option, "getMaxFloatValue");
            }
            if (numeric instanceof Number) {
                double parsed;
                try { parsed = Double.parseDouble(text.trim()); }
                catch (NumberFormatException ex) { return false; }
                if (!Double.isFinite(parsed)) return false;
                if (minimum instanceof Number n && parsed < n.doubleValue()) return false;
                if (maximum instanceof Number n && parsed > n.doubleValue()) return false;
                if (numeric instanceof Integer && parsed != Math.rint(parsed)) return false;
            }
            Object selected = invoke(option, "getOptionListValue");
            if (selected != null) {
                for (Object candidate : possibleChoices(selected)) {
                    String display = optionName(candidate);
                    if (!display.equals(text) && !candidate.toString().equals(text)) continue;
                    for (Method setter : option.getClass().getMethods()) {
                        if (!setter.getName().equals("setOptionListValue") || setter.getParameterCount() != 1
                                || !setter.getParameterTypes()[0].isInstance(candidate)) continue;
                        try {
                            setter.invoke(option, candidate);
                            saveOptionChange();
                            return true;
                        } catch (ReflectiveOperationException | RuntimeException ignored) {return false;}
                    }
                }
                return false;
            }
            if (invoke(option, "getStrings") instanceof List<?>) {
                try {
                    Method set = option.getClass().getMethod("setStrings", List.class);
                    set.invoke(option, text.isBlank() ? List.of() :
                            java.util.Arrays.stream(text.split(","))
                                .map(String::strip).filter(item -> !item.isEmpty()).toList());
                    saveOptionChange();
                    return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
            }
            if (section.equals("colors")) {
                String hex = text.strip().replaceFirst("(?i)^(#|0x)", "");
                if (!hex.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?")) return false;
                if (hex.length() == 6) hex = "FF" + hex;
                String normalized = "0x" + hex.toUpperCase(java.util.Locale.ROOT);
                try {
                    Method setter = option.getClass().getMethod("setValueFromString", String.class);
                    setter.invoke(option, normalized);
                    saveOptionChange();
                    return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
                for (Method setter : option.getClass().getMethods()) {
                    if (!setter.getName().equals("setColor") || setter.getParameterCount() != 1) continue;
                    try {
                        Class<?> type = setter.getParameterTypes()[0];
                        if (type == int.class || type == Integer.class) {
                            setter.invoke(option, (int) Long.parseLong(hex, 16));
                        } else if (type == String.class) {
                            setter.invoke(option, normalized);
                        } else continue;
                        saveOptionChange();
                        return true;
                    } catch (ReflectiveOperationException | RuntimeException ignored) { }
                }
                return false;
            }
            if (section.equals("hotkeys")) {
                Object target = invoke(option, "getKeybind");
                if (target == null) return false;
                try {
                    Method setter = target.getClass().getMethod("setValueFromString", String.class);
                    setter.invoke(target, text);
                    saveOptionChange();
                    return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) {return false;}
            }
            for (Method setter : option.getClass().getMethods()) {
                if (setter.getParameterCount() != 1) continue;
                String name = setter.getName();
                if (!name.equals("setBooleanValue") && !name.equals("setIntegerValue")
                        && !name.equals("setDoubleValue") && !name.equals("setFloatValue")
                        && !name.equals("setStringValue")
                        && !name.equals("setColor")) continue;
                Class<?> type = setter.getParameterTypes()[0];
                try {
                    Object value;
                    if (type == boolean.class || type == Boolean.class) {
                        if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) continue;
                        value = Boolean.parseBoolean(text);
                    } else if (type == int.class || type == Integer.class) value = Integer.parseInt(text.trim());
                    else if (type == double.class || type == Double.class) value = Double.parseDouble(text.trim());
                    else if (type == float.class || type == Float.class) value = Float.parseFloat(text.trim());
                    else if (type == String.class) value = text;
                    else continue;
                    setter.invoke(option, value);
                    saveOptionChange();
                    return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            return false;
        }
        return false;
    }
    private static void saveOptionChange() {
        Configs.saveToFile();
        SchematicWorldRefresher.INSTANCE.updateAll();
    }
    @Override public String clipMode() {
        return DataManager.getRenderLayerRange().getLayerMode().toString();
    }
    @Override public void clipStep(int delta) {
        DataManager.getRenderLayerRange().moveLayer(delta);
    }
    @Override public void clipCycle() {
        LayerRange range = DataManager.getRenderLayerRange();
        range.setLayerMode((LayerMode) range.getLayerMode().cycle(true));
    }
    @Override public boolean rotate(int index) {
        SchematicPlacement p = get(index);
        if (p == null || p.isLocked()) return false;
        p.setRotation(p.getRotation().getRotated(Rotation.CLOCKWISE_90), PLACEMENT_FEEDBACK);
        return true;
    }
    @Override public boolean mirror(int index) {
        SchematicPlacement p = get(index);
        if (p == null || p.isLocked()) return false;
        Mirror next = switch (p.getMirror()) {
            case NONE -> Mirror.LEFT_RIGHT;
            case LEFT_RIGHT -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.NONE;
        };
        p.setMirror(next, PLACEMENT_FEEDBACK);
        return true;
    }
    @Override public void openNativeConfig() { GuiBase.openGui(new GuiConfigs()); }
}
