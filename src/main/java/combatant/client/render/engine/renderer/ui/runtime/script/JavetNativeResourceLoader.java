/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.interop.loader.IJavetLibLoadingListener;
import com.caoccao.javet.interop.loader.JavetLibLoader;
import combatant.client.runtime.distribution.AndroidRuntime;
import combatant.client.runtime.distribution.DistributionCapabilities;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads Javet V8 natives from Combatant resources when they are bundled under
 * /javet/natives. On desktop, an absent bundle leaves Javet's classpath loader
 * available for development. Android must never fall back to Linux/glibc binaries.
 */
enum JavetNativeResourceLoader {
    ;

    private static boolean installed;
    private static final String RESOURCE_ROOT = "/javet/natives/";

    static boolean hasBundledNativeForCurrentRuntime() {
        NativeLibrary library = detectLibrary();
        return library != null && resourceExists(library.resourcePath());
    }

    static synchronized void install() {
        if (installed) return;
        NativeLibrary library = detectLibrary();
        if (library == null || !resourceExists(library.resourcePath())) {
            if (AndroidRuntime.isAndroid()) {
                throw new IllegalStateException("Bundled Javet V8 native is missing or unsupported for "
                        + DistributionCapabilities.currentTargetId() + "; refusing to load Linux/glibc natives on Android.");
            }
            return;
        }
        JavetLibLoader.setLibLoadingListener(new CombatantJavetLibLoadingListener(library));
        installed = true;
    }

    private static NativeLibrary detectLibrary() {
        try {
            return selectLibrary(DistributionCapabilities.currentTargetId(),
                    new JavetLibLoader(JSRuntimeType.V8).getLibFileName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static NativeLibrary selectLibrary(String target, String javetFileName) {
        if (target.startsWith("android-")) {
            if (!target.equals("android-arm64")) return null;
            // Javet 5.0.9 appends its own filename before System.load() in its JNI classloader.
            // On OpenJDK that name says Linux, but the extracted bytes must be the Android ABI.
            return new NativeLibrary("android/arm64-v8a/libjavet-v8-android.v."
                    + JavetLibLoader.LIB_VERSION + ".so", javetFileName);
        }
        return new NativeLibrary(javetFileName, javetFileName);
    }

    record NativeLibrary(String resourcePath, String fileName) {
    }

    private static boolean resourceExists(String libFileName) {
        try (InputStream stream = JavetNativeResourceLoader.class.getResourceAsStream(RESOURCE_ROOT + libFileName)) {
            return stream != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    static final class CombatantJavetLibLoadingListener implements IJavetLibLoadingListener {
        private final NativeLibrary library;
        private volatile File libPath;

        CombatantJavetLibLoadingListener(NativeLibrary library) {
            this.library = library;
        }

        @Override
        public File getLibPath(JSRuntimeType jsRuntimeType) {
            if (jsRuntimeType != JSRuntimeType.V8) return null;
            File cached = libPath;
            if (cached != null) return cached;
            synchronized (this) {
                cached = libPath;
                if (cached != null) return cached;
                libPath = extractNativeDirectory();
                return libPath;
            }
        }

        @Override
        public boolean isDeploy(JSRuntimeType jsRuntimeType) {
            return false;
        }

        @Override
        public boolean isLibInSystemPath(JSRuntimeType jsRuntimeType) {
            return false;
        }

        @Override
        public boolean isSuppressingError(JSRuntimeType jsRuntimeType) {
            return false;
        }

        private File extractNativeDirectory() {
            String resource = RESOURCE_ROOT + library.resourcePath();
            try (InputStream stream = JavetNativeResourceLoader.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    throw new UnsatisfiedLinkError("Missing Javet native resource: " + resource);
                }
                Path dir = Files.createTempDirectory("combatant-javet-v8-");
                Path target = dir.resolve(library.fileName());
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                dir.toFile().deleteOnExit();
                target.toFile().deleteOnExit();
                return dir.toFile();
            } catch (IOException e) {
                UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to extract Javet native: " + resource);
                error.initCause(e);
                throw error;
            }
        }
    }
}
