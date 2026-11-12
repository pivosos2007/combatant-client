/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import com.mojang.blaze3d.Blaze3D;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.modules.player.Printer;
import combatant.client.automation.compat.litematica.LitematicaCompat;
import combatant.client.automation.compat.litematica.LitematicaWorkspace;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicLibrary;
import combatant.client.util.schematic.SchematicState;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Operations shared by the schematic menu and the {@code @schematic} command. Main thread only. */
public final class SchematicActions {

    private static volatile Path loadingPath;
    private static volatile String lastError = "";
    private static long feedbackSequence;
    private static final List<Feedback> FEEDBACK = new ArrayList<>();

    public record Feedback(long sequence, String message, String variant) {}

    private static synchronized void feedback(String message, String variant) {
        FEEDBACK.add(new Feedback(++feedbackSequence, message, variant));
        if (FEEDBACK.size() > 16) FEEDBACK.removeFirst();
    }

    public static synchronized long feedbackSequence() { return feedbackSequence; }

    public static synchronized List<Feedback> feedbackSince(long last) {
        return FEEDBACK.stream().filter(item -> item.sequence() > last).toList();
    }

    private SchematicActions() {
    }

    public static Path loadingPath() {
        return loadingPath;
    }

    public static String lastError() {
        return lastError;
    }

    public static void openMenu() {
        if (!combatant.client.automation.compat.litematica.LitematicaCompat.isAvailable()) return;
        Minecraft mc = Minecraft.getInstance();
        // Deferred one frame: the chat screen that ran the command closes itself after the command returns.
        mc.schedule(() -> ClientScreen.show(mc, new SchematicScreen(ClientScreen.current(mc))));
    }

    /** Import into Litematica FIRST. The old parser is used only for the
     * optional in-menu mesh preview, never as the world placement authority. */
    public static void load(Path path) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && (mc.level == null || mc.player == null)) {
            if (path != null) {
                preview(path);
                feedback(I18n.get("screen.combatant.clickgui.schematics_offline"), "informative");
            }
            return;
        }
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        if (path == null || loadingPath != null || bridge == null) return;
        lastError = "";
        LitematicaWorkspace.ImportResult nativeResult = bridge.importFile(path);
        if (!nativeResult.success()) {
            lastError = nativeResult.message();
            feedback("Litematica: " + nativeResult.message(), "negative");
            return;
        }
        feedback("Litematica: " + path.getFileName() + " placed in the world", "positive");
        SchematicState.INSTANCE.clear();
        preview(path);
    }

    /** Preview never inserts another placement. Used for adopting an existing native placement. */
    public static void preview(Path path) {
        if (path == null || loadingPath != null) return;
        loadingPath = path;
        lastError = "";
        SchematicState.INSTANCE.clear();
        Minecraft mc = Minecraft.getInstance();
        SchematicLibrary.INSTANCE.load(path).whenComplete((loaded, error) -> mc.execute(() -> {
            loadingPath = null;
            if (error != null) {
                SchematicState.INSTANCE.clear();
                // Native placement remains valid; this is a preview-only failure.
                feedback("Litematica placement active; preview unavailable for " + path.getFileName(), "informative");
                return;
            }
            applyLoaded(mc, loaded);
        }));
    }

    private static void applyLoaded(Minecraft mc, SchematicLibrary.Loaded loaded) {
        SchematicState state = SchematicState.INSTANCE;
        state.setSchematic(loaded.schematic(), loaded.stats());
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        boolean matched = false;
        if (bridge != null) for (LitematicaWorkspace.Placement placement : bridge.placements()) {
            if (placement.selected()) { state.setOrigin(placement.origin()); matched = true; break; }
        }
        if (!matched && mc.player != null) state.setOrigin(mc.player.blockPosition());
        Schematic schematic = loaded.schematic();
        feedback(I18n.get("screen.combatant.schematic.status.loaded", schematic.name(),
                schematic.sizeX() + "x" + schematic.sizeY() + "x" + schematic.sizeZ(), schematic.totalBlocks()), "positive");
        if (!schematic.warnings().isEmpty()) {
            feedback(I18n.get("screen.combatant.schematic.status.warnings", schematic.warnings().size(),
                    schematic.warnings().getFirst()), "informative");
        }
    }

    /**
     * Opens the schematics folder in the system file manager, creating it first. Runs the launcher off the render
     * thread; Windows uses {@code explorer.exe} directly because SDL's URL opener does not reliably open file URIs.
     */
    public static void openFolder() {
        Path dir;
        try {
            dir = Files.createDirectories(SchematicLibrary.INSTANCE.directory()).toAbsolutePath().normalize();
        } catch (IOException | RuntimeException e) {
            feedback(I18n.get("screen.combatant.schematic.status.open_folder_failed", e.getMessage()), "negative");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Util.nonCriticalIoPool().execute(() -> {
            try {
                String[] command = switch (Util.getPlatform()) {
                    case WINDOWS -> new String[] {"explorer.exe", dir.toString()};
                    case OSX -> new String[] {"open", dir.toString()};
                    default -> new String[] {"xdg-open", dir.toString()};
                };
                new ProcessBuilder(command).start();
            } catch (IOException e) {
                Blaze3D.openPath(dir);
            }
            // The file manager can open behind an exclusive-fullscreen game window, so always say where the folder is.
            mc.execute(() -> feedback(I18n.get("screen.combatant.schematic.status.folder_path", dir.toString()), "informative"));
        });
    }

    public static void unload() {
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        if (bridge != null) bridge.removeSelected();
        SchematicState.INSTANCE.clear();
    }

    private static LitematicaWorkspace.Placement activePlacement() {
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        if (bridge != null) for (LitematicaWorkspace.Placement placement : bridge.placements())
            if (placement.selected()) return placement;
        return null;
    }

    public static boolean setOriginHere() {
        LocalPlayer player = Minecraft.getInstance().player;
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        LitematicaWorkspace.Placement placement = activePlacement();
        if (player == null || bridge == null || placement == null) return false;
        boolean accepted = bridge.setOrigin(placement.index(), player.blockPosition());
        if (accepted && SchematicState.INSTANCE.hasSchematic()) SchematicState.INSTANCE.setOrigin(player.blockPosition());
        return accepted;
    }

    public static boolean setOriginLooked() {
        Minecraft mc = Minecraft.getInstance();
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        LitematicaWorkspace.Placement placement = activePlacement();
        HitResult hit = mc.hitResult;
        if (bridge == null || placement == null || !(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos target = blockHit.getBlockPos().relative(blockHit.getDirection());
        boolean accepted = bridge.setOrigin(placement.index(), target);
        if (accepted && SchematicState.INSTANCE.hasSchematic()) SchematicState.INSTANCE.setOrigin(target);
        return accepted;
    }

    /** Printer reads only Litematica's transformed schematic world. */
    public static void toggleBuilder() {
        Printer printer = ModuleManager.get(Printer.class);
        if (printer != null) printer.toggle();
    }

    public static boolean builderEnabled() {
        Printer printer = ModuleManager.get(Printer.class);
        return printer != null && printer.isEnabled();
    }

    /** Litematica owns all world rendering and the renderer's enabled setting. */
    public static void toggleProjection() {
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        if (bridge != null) bridge.toggleRendering();
    }

    public static boolean projectionEnabled() {
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        return bridge != null && bridge.renderingEnabled();
    }

    public static void cycleLayerMode() {
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        if (bridge != null) bridge.clipCycle();
    }
}
