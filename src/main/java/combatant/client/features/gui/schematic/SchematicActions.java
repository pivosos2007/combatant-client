/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.player.schematic.SchematicBuilder;
import combatant.client.features.module.modules.visuals.schematic.SchematicProjection;
import combatant.client.util.schematic.LitematicReader;
import combatant.client.util.schematic.SchematicFiles;
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

import java.nio.file.Path;
import java.util.concurrent.CompletionException;

/** Operations shared by the schematic menu and the {@code @schematic} command. Main thread only. */
public final class SchematicActions {

    private static volatile Path loadingPath;
    private static volatile String lastError = "";

    private SchematicActions() {
    }

    public static Path loadingPath() {
        return loadingPath;
    }

    public static String lastError() {
        return lastError;
    }

    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        // Deferred one frame: the chat screen that ran the command closes itself after the command returns.
        mc.schedule(() -> ClientScreen.show(mc, new SchematicScreen(ClientScreen.current(mc))));
    }

    /** Starts loading {@code path}; false (and nothing happens) when another load is still running. */
    public static boolean load(Path path) {
        if (path == null || loadingPath != null) return false;
        loadingPath = path;
        lastError = "";
        Minecraft mc = Minecraft.getInstance();
        SchematicLibrary.INSTANCE.load(path).whenComplete((loaded, error) -> mc.execute(() -> {
            loadingPath = null;
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                lastError = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                Notifier.error(I18n.get("screen.combatant.schematic.status.load_failed", SchematicFiles.baseName(path), lastError));
                return;
            }
            applyLoaded(mc, loaded, path);
        }));
        return true;
    }

    private static void applyLoaded(Minecraft mc, SchematicLibrary.Loaded loaded, Path path) {
        SchematicState state = SchematicState.INSTANCE;
        state.setSchematic(loaded.schematic(), loaded.stats(), loaded.layout());
        state.setSource(path);
        if (state.placement() == null && mc.player != null) {
            state.setOrigin(mc.player.blockPosition());
        }
        Schematic schematic = loaded.schematic();
        Notifier.success(I18n.get("screen.combatant.schematic.status.loaded", schematic.name(),
                schematic.sizeX() + "x" + schematic.sizeY() + "x" + schematic.sizeZ(), schematic.totalBlocks()));
        if (!schematic.warnings().isEmpty()) {
            Notifier.warning(I18n.get("screen.combatant.schematic.status.warnings", schematic.warnings().size(),
                    schematic.warnings().getFirst()));
        }
    }

    public static void unload() {
        SchematicBuilder builder = ModuleManager.get(SchematicBuilder.class);
        if (builder != null && builder.isEnabled()) builder.setEnabled(false);
        SchematicState.INSTANCE.clear();
    }

    public static boolean setOriginHere() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !SchematicState.INSTANCE.hasSchematic()) return false;
        SchematicState.INSTANCE.setOrigin(player.blockPosition());
        return true;
    }

    /** Origin on the face of the block under the crosshair, so the build starts on top of what you look at. */
    public static boolean setOriginLooked() {
        Minecraft mc = Minecraft.getInstance();
        if (!SchematicState.INSTANCE.hasSchematic()) return false;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos origin = blockHit.getBlockPos().relative(blockHit.getDirection());
        SchematicState.INSTANCE.setOrigin(origin);
        return true;
    }

    public static void toggleBuilder() {
        SchematicBuilder builder = ModuleManager.get(SchematicBuilder.class);
        if (builder != null) builder.toggle();
    }

    public static boolean builderEnabled() {
        SchematicBuilder builder = ModuleManager.get(SchematicBuilder.class);
        return builder != null && builder.isEnabled();
    }

    public static void toggleProjection() {
        SchematicProjection projection = ModuleManager.get(SchematicProjection.class);
        if (projection != null) projection.toggle();
    }

    public static boolean projectionEnabled() {
        SchematicProjection projection = ModuleManager.get(SchematicProjection.class);
        return projection != null && projection.isEnabled();
    }

    public static void cycleLayerMode() {
        SchematicState.LayerMode[] modes = SchematicState.LayerMode.values();
        SchematicState.LayerMode next = modes[(SchematicState.INSTANCE.layerMode().ordinal() + 1) % modes.length];
        SchematicProjection projection = ModuleManager.get(SchematicProjection.class);
        if (projection != null) {
            projection.setLayerMode(next);
        } else {
            SchematicState.INSTANCE.setLayerMode(next);
        }
    }
}
