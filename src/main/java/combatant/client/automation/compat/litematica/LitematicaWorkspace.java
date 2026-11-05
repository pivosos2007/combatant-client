/* Combatant Client, GPL-3.0. Optional native Litematica editor contract. */
package combatant.client.automation.compat.litematica;

import net.minecraft.core.BlockPos;
import java.nio.file.Path;
import java.util.List;

public interface LitematicaWorkspace {
    record Placement(int index, String name, BlockPos origin, boolean selected, boolean enabled,
                     boolean locked, Path file, int regions) {}
    record Option(String id, String name, String type, String value, String description,
                  List<String> choices, String min, String max) {
        public Option(String id, String name, String type, String value, String description,
                      List<String> choices) { this(id, name, type, value, description, choices, "", ""); }
        public Option(String id, String name, String type, String value, String description) {
            this(id, name, type, value, description, List.of(), "", "");
        }
    }
    record ImportResult(boolean success, String message, int placementIndex) {}
    List<Placement> placements();
    ImportResult importFile(Path path);
    boolean removeSelected();
    boolean renderingEnabled();
    boolean toggleRendering();
    List<Option> options(String section);
    boolean select(int index);
    boolean setEnabled(int index, boolean enabled);
    boolean setOrigin(int index, BlockPos pos);
    boolean toggleOption(String section, String id);
    boolean setOption(String section, String id, String value);
    /** Record actual SDL scancodes in the MaLiLib keybind, without string parsing. */
    boolean setHotkeyKeys(String id, List<Integer> scanCodes);
    String clipMode();
    void clipStep(int delta);
    void clipCycle();
    boolean rotate(int index);
    boolean mirror(int index);
    void openNativeConfig();
}
