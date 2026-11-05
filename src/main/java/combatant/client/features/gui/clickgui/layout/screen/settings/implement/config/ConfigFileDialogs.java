/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import com.mojang.blaze3d.Blaze3D;
import combatant.client.config.profile.ConfigProfileService;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.module.Notifier;
import combatant.client.util.filedialog.NativeFileDialogService;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Config-specific file operations. Native dialogs are always handled by the shared dialog service. */
final class ConfigFileDialogs {
    private static final List<NativeFileDialogService.Filter> FILTERS = List.of(
            new NativeFileDialogService.Filter("Combatant (*.cbcfg)", List.of("cbcfg")));
    private static Path lastFolder;

    private ConfigFileDialogs() { }

    static boolean busy() {
        return NativeFileDialogService.busy();
    }

    static void openProfile(Consumer<Path> picked, Runnable failed) {
        NativeFileDialogService.openFile(initialFolder(), FILTERS, Minecraft.getInstance()::schedule)
                .thenAccept(result -> complete(result, picked, failed));
    }

    static void saveProfile(String fileName, Consumer<Path> picked, Runnable failed) {
        NativeFileDialogService.saveFile(initialFolder(), fileName, FILTERS, Minecraft.getInstance()::schedule)
                .thenAccept(result -> complete(result, picked, failed));
    }

    private static Path initialFolder() {
        if (lastFolder != null && Files.isDirectory(lastFolder)) return lastFolder;
        Path profiles = ConfigProfileService.INSTANCE.storage().root();
        if (Files.isDirectory(profiles)) return profiles;
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : Files.isDirectory(home) ? home : null;
    }

    private static void complete(NativeFileDialogService.Result result, Consumer<Path> picked, Runnable failed) {
        switch (result.status()) {
            case SELECTED -> {
                Path parent = result.path().getParent();
                if (parent != null) lastFolder = parent;
                picked.accept(result.path());
            }
            case CANCELLED, BUSY -> { /* Cancelling a dialog is not an error. */ }
            case NATIVE_ERROR, INVALID_RESULT -> {
                DebugLog.warn("Config file dialog failed: {} ({})", result.status(), result.detail());
                failed.run();
            }
        }
    }

    /** Open the actual profiles directory in the OS file manager, not a folder picker. */
    static void openProfilesFolder() {
        Path dir;
        try {
            dir = Files.createDirectories(ConfigProfileService.INSTANCE.storage().root()).toAbsolutePath().normalize();
        } catch (IOException | RuntimeException e) {
            Notifier.error(ClickGuiI18n.tr("notification.config.folder_failed", "Could not open profiles folder: %s", e.getMessage()));
            return;
        }
        Util.nonCriticalIoPool().execute(() -> {
            try {
                String[] command = switch (Util.getPlatform()) {
                    case WINDOWS -> new String[] {"explorer.exe", dir.toString()};
                    case OSX -> new String[] {"open", dir.toString()};
                    default -> new String[] {"xdg-open", dir.toString()};
                };
                new ProcessBuilder(command).start();
                Minecraft.getInstance().schedule(() -> Notifier.info(
                        ClickGuiI18n.tr("notification.config.folder_path", "Profiles folder: %s", dir)));
            } catch (IOException | RuntimeException e) {
                try {
                    Blaze3D.openPath(dir);
                    Minecraft.getInstance().schedule(() -> Notifier.info(
                            ClickGuiI18n.tr("notification.config.folder_path", "Profiles folder: %s", dir)));
                } catch (RuntimeException fallbackFailure) {
                    DebugLog.error("Cannot open profiles folder", fallbackFailure);
                    Minecraft.getInstance().schedule(() -> Notifier.error(
                            ClickGuiI18n.tr("notification.config.folder_failed", "Could not open profiles folder: %s", dir)));
                }
            }
        });
    }
}
