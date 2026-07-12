/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on InvMove
 * (https://github.com/PieKing1215/InvMove).
 * Copyright (c) PieKing1215 and contributors.
 *
 * Original portions remain under LGPLv3.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.screen;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.*;
import combatant.client.features.gui.clickgui.ClickGuiEditorScreen;
import combatant.client.features.gui.clickgui.ClickGuiPickerScreen;
import combatant.client.features.gui.clickgui.ClickGuiScreen;

import java.util.*;

/**
 * Screen picker catalog used by InventoryMove.
 * <p>
 * Credit:
 * The idea of configurable per-screen movement rules and discoverable screen entries
 * was adapted from the InvMove mod by pieking1215.
 */
public enum ScreenCatalog {
    ;

    public static final String CLICK_GUI = "combatant:click_gui";
    public static final String CLICK_GUI_EDITOR = "combatant:click_gui_editor";
    public static final String CLICK_GUI_PICKER = "combatant:click_gui_picker";

    private static final Map<String, String> KNOWN = new LinkedHashMap<>();
    private static final Map<String, String> DISCOVERED = new LinkedHashMap<>();

    static {
        registerKnown(Screen.class, "Any Screen");
        registerKnown(AbstractContainerScreen.class, "Any Inventory");
        registerKnown(AbstractRecipeBookScreen.class, "Any Recipe Book");
        registerKnown(ItemCombinerScreen.class, "Any Forging Screen");
        registerKnown(AbstractFurnaceScreen.class, "Any Furnace Screen");
        registerKnown(AbstractMountInventoryScreen.class, "Any Mount Inventory");

        registerKnown(InventoryScreen.class, "Inventory");
        registerKnown(CreativeModeInventoryScreen.class, "Creative Inventory");
        registerKnown(ContainerScreen.class, "Chest");
        registerKnown(DispenserScreen.class, "Dispenser / Dropper");
        registerKnown(HopperScreen.class, "Hopper");
        registerKnown(ShulkerBoxScreen.class, "Shulker Box");
        registerKnown(CraftingScreen.class, "Crafting Table");
        registerKnown(CrafterScreen.class, "Crafter");
        registerKnown(BeaconScreen.class, "Beacon");
        registerKnown(BrewingStandScreen.class, "Brewing Stand");
        registerKnown(CartographyTableScreen.class, "Cartography Table");
        registerKnown(EnchantmentScreen.class, "Enchantment Table");
        registerKnown(GrindstoneScreen.class, "Grindstone");
        registerKnown(LoomScreen.class, "Loom");
        registerKnown(MerchantScreen.class, "Villager Trading");
        registerKnown(HorseInventoryScreen.class, "Horse Inventory");
        registerKnown(AnvilScreen.class, "Anvil");
        registerKnown(SmithingScreen.class, "Smithing Table");
        registerKnown(StonecutterScreen.class, "Stonecutter");
        registerKnown(FurnaceScreen.class, "Furnace");
        registerKnown(BlastFurnaceScreen.class, "Blast Furnace");
        registerKnown(SmokerScreen.class, "Smoker");

        registerKnown(ChatScreen.class, "Chat");
        registerKnown(PauseScreen.class, "Pause Menu");
        registerKnown(CLICK_GUI, "ClickGUI");
        registerKnown(CLICK_GUI_EDITOR, "ClickGUI Editor");
        registerKnown(CLICK_GUI_PICKER, "ClickGUI Picker");
        registerKnown(AdvancementsScreen.class, "Advancements");
        registerKnown(BookViewScreen.class, "Book");
        registerKnown(BookEditScreen.class, "Book Editor");
        registerKnown(BookSignScreen.class, "Book Signing");
        registerKnown(AbstractSignEditScreen.class, "Any Sign Editor");
        registerKnown(SignEditScreen.class, "Sign Editor");
        registerKnown(AbstractCommandBlockEditScreen.class, "Any Command Block");
        registerKnown(StructureBlockEditScreen.class, "Structure Block");
        registerKnown(JigsawBlockEditScreen.class, "Jigsaw Block");
    }

    public static void registerSeen(Screen screen) {
        if (screen == null) return;

        Class<?> current = screen.getClass();
        while (current != null && Screen.class.isAssignableFrom(current)) {
            String id = current.getName();
            DISCOVERED.putIfAbsent(id, humanizeSimpleName(current.getSimpleName()));
            if (current == Screen.class) {
                break;
            }
            current = current.getSuperclass();
        }
    }

    public static boolean matches(Screen screen, Set<String> configuredIds) {
        if (screen == null || configuredIds == null || configuredIds.isEmpty()) return false;

        for (String configuredId : configuredIds) {
            if (matchesStableAlias(screen, configuredId)) {
                return true;
            }
        }

        Class<?> current = screen.getClass();
        while (current != null && Screen.class.isAssignableFrom(current)) {
            String currentName = current.getName();
            for (String configuredId : configuredIds) {
                if (configuredId == null || configuredId.isBlank()) continue;
                if (configuredId.equals(currentName)) {
                    return true;
                }
            }
            if (current == Screen.class) {
                break;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public static List<Entry> entries(Set<String> selectedIds) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        KNOWN.forEach(merged::putIfAbsent);
        DISCOVERED.forEach(merged::putIfAbsent);

        if (selectedIds != null) {
            for (String id : selectedIds) {
                if (id == null || id.isBlank()) continue;
                merged.putIfAbsent(id, labelForId(id));
            }
        }

        List<Entry> entries = new ArrayList<>(merged.size());
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }

        entries.sort(Comparator
                .comparingInt((Entry entry) -> sortWeight(entry.getId()))
                .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public static String labelForId(String id) {
        if (id == null || id.isBlank()) return "";
        String known = KNOWN.get(id);
        if (known != null) return known;
        String discovered = DISCOVERED.get(id);
        if (discovered != null) return discovered;

        int dot = id.lastIndexOf('.');
        String simple = dot >= 0 ? id.substring(dot + 1) : id;
        return humanizeSimpleName(simple);
    }

    private static void registerKnown(Class<? extends Screen> type, String label) {
        KNOWN.put(type.getName(), label);
    }

    private static void registerKnown(String stableId, String label) {
        KNOWN.put(stableId, label);
    }

    private static boolean matchesStableAlias(Screen screen, String configuredId) {
        if (screen == null || configuredId == null || configuredId.isBlank()) return false;

        if (screen instanceof ClickGuiScreen) {
            return CLICK_GUI.equals(configuredId) || hasLegacyTail(configuredId, "ClickGuiScreen");
        }
        if (screen instanceof ClickGuiEditorScreen) {
            return CLICK_GUI_EDITOR.equals(configuredId) || hasLegacyTail(configuredId, "ClickGuiEditorScreen");
        }
        if (screen instanceof ClickGuiPickerScreen) {
            return CLICK_GUI_PICKER.equals(configuredId) || hasLegacyTail(configuredId, "ClickGuiPickerScreen");
        }
        return false;
    }

    private static boolean hasLegacyTail(String configuredId, String tail) {
        return configuredId.equals(tail) || configuredId.endsWith('.' + tail);
    }

    private static int sortWeight(String id) {
        if (Screen.class.getName().equals(id)) return 0;
        if (AbstractContainerScreen.class.getName().equals(id)) return 1;
        if (CLICK_GUI.equals(id) || CLICK_GUI_EDITOR.equals(id) || CLICK_GUI_PICKER.equals(id)) return 2;
        return 10;
    }

    private static String humanizeSimpleName(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return "";

        String base = simpleName.endsWith("Screen")
                ? simpleName.substring(0, simpleName.length() - "Screen".length())
                : simpleName;

        StringBuilder out = new StringBuilder(base.length() + 8);
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            if (i > 0 && Character.isUpperCase(ch) && Character.isLowerCase(base.charAt(i - 1))) {
                out.append(' ');
            }
            out.append(ch);
        }
        return out.toString().trim();
    }

    public record Entry(String id, String label) {
        public String getId() {
            return id;
        }
    }
}
