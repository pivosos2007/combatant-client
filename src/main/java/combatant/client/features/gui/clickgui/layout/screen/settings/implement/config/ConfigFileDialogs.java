/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import com.mojang.blaze3d.Blaze3D;
import combatant.client.config.profile.ConfigProfileService;
import combatant.client.features.module.Notifier;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Util;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Native open/save dialogs for {@code .cbcfg} files plus the "open the profiles folder" fallback.
 *
 * <p>Minecraft 26.3 ships SDL3 (lwjgl-sdl) and no tinyfd, so the dialogs are SDL's native ones. SDL shows them
 * on its own thread and reports back through a callback; the result is handed to the client thread, so the
 * render thread never blocks on a dialog. If the dialog cannot be shown, {@code failed} runs (callers fall back
 * to {@link #openProfilesFolder()}).
 */
final class ConfigFileDialogs {
    private static final String FILTER_NAME = "Combatant config (*.cbcfg)";
    private static final String FILTER_PATTERN = "cbcfg";
    private static volatile boolean open;
    private static volatile Path lastFolder;

    private ConfigFileDialogs() {
    }

    static boolean busy() {
        return open;
    }

    static void openProfile(Consumer<Path> picked, Runnable failed) {
        show(false, startIn(null), picked, failed);
    }

    static void saveProfile(String fileName, Consumer<Path> picked, Runnable failed) {
        show(true, startIn(fileName), picked, failed);
    }

    private static String startIn(String fileName) {
        Path folder = lastFolder;
        if (folder == null || !Files.isDirectory(folder)) folder = defaultFolder();
        return fileName == null ? folder + File.separator : folder.resolve(fileName).toString();
    }

    private static Path defaultFolder() {
        try {
            Path home = Path.of(System.getProperty("user.home", "."));
            Path downloads = home.resolve("Downloads");
            return Files.isDirectory(downloads) ? downloads : home;
        } catch (InvalidPathException e) {
            return Path.of(".").toAbsolutePath();
        }
    }

    private static void show(boolean save, String location, Consumer<Path> picked, Runnable failed) {
        if (open) return;
        Minecraft mc = Minecraft.getInstance();
        open = true;
        ByteBuffer name = null;
        ByteBuffer pattern = null;
        SDL_DialogFileFilter.Buffer filters = null;
        SDL_DialogFileCallback callback = null;
        try {
            name = MemoryUtil.memUTF8(FILTER_NAME);
            pattern = MemoryUtil.memUTF8(FILTER_PATTERN);
            filters = SDL_DialogFileFilter.calloc(1);
            filters.name(name).pattern(pattern);

            // SDL keeps these native resources until it has reported back; they are released only afterwards.
            ByteBuffer ownedName = name;
            ByteBuffer ownedPattern = pattern;
            SDL_DialogFileFilter.Buffer ownedFilters = filters;
            SDL_DialogFileCallback[] self = new SDL_DialogFileCallback[1];
            callback = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
                Path result = null;
                boolean error = fileList == 0L;
                if (!error) {
                    long first = MemoryUtil.memGetAddress(fileList);
                    if (first != 0L) result = parse(MemoryUtil.memUTF8(first));
                }
                Path chosen = result;
                boolean broken = error;
                // schedule() always queues, so the callback is fully unwound before its native storage is freed.
                mc.schedule(() -> {
                    open = false;
                    self[0].free();
                    MemoryUtil.memFree(ownedName);
                    MemoryUtil.memFree(ownedPattern);
                    ownedFilters.free();
                    if (chosen != null) {
                        if (chosen.getParent() != null) lastFolder = chosen.getParent();
                        picked.accept(chosen);
                    } else if (broken) {
                        failed.run();
                    }
                });
            });
            self[0] = callback;
            if (save) {
                SDLDialog.SDL_ShowSaveFileDialog(callback, 0L, 0L, filters, location);
            } else {
                SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, 0L, filters, location, false);
            }
        } catch (Throwable t) {
            DebugLog.error("Native file dialog unavailable", t);
            open = false;
            if (callback != null) callback.free();
            if (name != null) MemoryUtil.memFree(name);
            if (pattern != null) MemoryUtil.memFree(pattern);
            if (filters != null) filters.free();
            failed.run();
        }
    }

    private static Path parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Path.of(raw).toAbsolutePath();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Opens the profiles folder in the system file manager, creating it first. Runs the launcher off the
     * render thread; Windows uses {@code explorer.exe} directly because SDL's URL opener does not reliably
     * open file URIs.
     */
    static void openProfilesFolder() {
        Path dir;
        try {
            dir = Files.createDirectories(ConfigProfileService.INSTANCE.storage().root()).toAbsolutePath().normalize();
        } catch (IOException | RuntimeException e) {
            Notifier.error(I18n.get("notification.config.folder_failed", e.getMessage()));
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
            // The file manager can open behind an exclusive-fullscreen game window, so always say where it is.
            mc.execute(() -> Notifier.info(I18n.get("notification.config.folder_path", dir.toString())));
        });
    }
}
