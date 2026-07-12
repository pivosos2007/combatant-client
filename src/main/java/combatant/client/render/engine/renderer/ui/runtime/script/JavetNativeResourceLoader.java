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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads Javet V8 natives from Combatant resources when they are bundled under
 * /javet/natives. If the resource bundle is not present, Javet's own classpath
 * loader is left untouched so dev-runtime native jars can still work.
 */
enum JavetNativeResourceLoader {
    ;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final String RESOURCE_ROOT = "/javet/natives/";

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        String libFileName = detectLibFileName();
        if (libFileName == null || !resourceExists(libFileName)) {
            return;
        }
        JavetLibLoader.setLibLoadingListener(new CombatantJavetLibLoadingListener(libFileName));
    }

    private static String detectLibFileName() {
        try {
            return new JavetLibLoader(JSRuntimeType.V8).getLibFileName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean resourceExists(String libFileName) {
        try (InputStream stream = JavetNativeResourceLoader.class.getResourceAsStream(RESOURCE_ROOT + libFileName)) {
            return stream != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static final class CombatantJavetLibLoadingListener implements IJavetLibLoadingListener {
        private final String libFileName;
        private volatile File libPath;

        private CombatantJavetLibLoadingListener(String libFileName) {
            this.libFileName = libFileName;
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
            String resource = RESOURCE_ROOT + libFileName;
            try (InputStream stream = JavetNativeResourceLoader.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    throw new UnsatisfiedLinkError("Missing Javet native resource: " + resource);
                }
                Path dir = Files.createTempDirectory("combatant-javet-v8-");
                Path target = dir.resolve(libFileName);
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                target.toFile().deleteOnExit();
                dir.toFile().deleteOnExit();
                return dir.toFile();
            } catch (IOException e) {
                UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to extract Javet native: " + resource);
                error.initCause(e);
                throw error;
            }
        }
    }
}
