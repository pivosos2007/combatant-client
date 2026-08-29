/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.nativeguard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class NativeMemoryGuard {
    public static final int WINDOWS_PROCESS_DACL = 1;
    public static final int CORE_DUMPS_DISABLED = 1 << 1;
    public static final int LINUX_NONDUMPABLE = 1 << 2;

    private static volatile Status status = new Status(false, false, 0, "not initialized");
    private static volatile boolean nativeLoaded;
    private static volatile boolean shutDown;

    private NativeMemoryGuard() {
    }

    public static synchronized Status initialize() {
        if (status.attempted()) return status;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String resource = resourceFor(os, architecture);

        if (resource == null) {
            status = new Status(true, false, 0, "unsupported operating system or architecture");
            return status;
        }

        String libraryName = resource.substring(resource.lastIndexOf('/') + 1);

        try (InputStream input = NativeMemoryGuard.class.getResourceAsStream(resource)) {
            if (input == null) {
                status = new Status(true, false, 0, "native library resource is missing");
                return status;
            }

            Path directory = Files.createTempDirectory("combatant-guard-");
            Path library = directory.resolve(libraryName);
            Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING);
            library.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            System.load(library.toAbsolutePath().toString());
            nativeLoaded = true;

            int mask = nativeApply();
            int expected = os.contains("win")
                    ? WINDOWS_PROCESS_DACL
                    : CORE_DUMPS_DISABLED | LINUX_NONDUMPABLE;
            String error = nativeLastError();
            boolean active = (mask & expected) == expected;
            status = new Status(true, true, mask,
                    error == null || error.isBlank() ? (active ? "active" : "partially active") : error);
        } catch (IOException | LinkageError | SecurityException exception) {
            status = new Status(true, true, 0,
                    exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage()));
        }

        return status;
    }

    public static Status status() {
        return status;
    }

    /**
     * Restores process-wide state changed by the native guard. The native library itself remains
     * owned by the application class loader, but after this call it no longer leaves a modified
     * process DACL / dumpability state behind during the rest of client shutdown.
     */
    public static synchronized void shutdown() {
        if (shutDown) return;
        shutDown = true;
        if (!nativeLoaded) return;

        try {
            nativeShutdown();
            status = new Status(true, true, 0, "inactive");
        } catch (LinkageError | SecurityException exception) {
            status = new Status(true, true, status.protectionMask(),
                    "shutdown failed: " + exception.getClass().getSimpleName() + ": "
                            + String.valueOf(exception.getMessage()));
        }
    }

    public static String currentPlatformId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return platformIdFor(os, architecture);
    }

    static String platformIdFor(String os, String architecture) {
        boolean x64 = "amd64".equals(architecture) || "x86_64".equals(architecture);
        if (!x64) return null;

        if (os.contains("win")) return "windows-x86_64";
        if (os.contains("linux")) return "linux-x86_64";
        return null;
    }

    static String resourceFor(String os, String architecture) {
        String platformId = platformIdFor(os, architecture);
        if (platformId == null) return null;

        return switch (platformId) {
            case "windows-x86_64" -> "/combatant/nativeguard/windows-x86_64/combatant_memory_guard.dll";
            case "linux-x86_64" -> "/combatant/nativeguard/linux-x86_64/libcombatant_memory_guard.so";
            default -> null;
        };
    }

    private static native int nativeApply();

    private static native String nativeLastError();

    private static native void nativeShutdown();

    public record Status(boolean attempted, boolean supported, int protectionMask, String detail) {
        public boolean active() {
            return supported && protectionMask != 0;
        }

        public boolean complete() {
            return supported && (protectionMask & expectedMask()) == expectedMask();
        }

        private int expectedMask() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            return os.contains("win") ? WINDOWS_PROCESS_DACL : CORE_DUMPS_DISABLED | LINUX_NONDUMPABLE;
        }
    }
}
