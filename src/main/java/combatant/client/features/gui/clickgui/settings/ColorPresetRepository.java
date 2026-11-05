/* Combatant GPL-3.0. Shared persisted color presets for Java and JS UI hosts. */
package combatant.client.features.gui.clickgui.settings;

import java.util.List;

public final class ColorPresetRepository {
    private ColorPresetRepository() { }
    public static List<Integer> load() { return List.copyOf(ColorPresetStore.presets()); }
    public static void save(int argb) { ColorPresetStore.add(argb); }
    public static boolean delete(int argb) { return ColorPresetStore.removeExact(argb); }
}
