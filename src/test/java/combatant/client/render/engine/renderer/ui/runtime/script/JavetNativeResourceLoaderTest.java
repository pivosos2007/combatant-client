/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.interop.loader.JavetLibLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavetNativeResourceLoaderTest {
    private static final String VERSION = JavetLibLoader.LIB_VERSION;
    private static final String ANDROID_RESOURCE = "android/arm64-v8a/libjavet-v8-android.v." + VERSION + ".so";

    @Test
    void fclSelectsAndroidBytesUnderTheFilenameExpectedByJavetsClassLoader() throws Exception {
        String expectedFileName = "libjavet-v8-linux-arm64.v." + VERSION + ".so";
        var library = JavetNativeResourceLoader.selectLibrary("android-arm64", expectedFileName);
        assertEquals(ANDROID_RESOURCE, library.resourcePath());
        var listener = new JavetNativeResourceLoader.CombatantJavetLibLoadingListener(library);
        Path directory = listener.getLibPath(JSRuntimeType.V8).toPath();
        Path extracted = directory.resolve(expectedFileName);
        try {
            assertEquals(directory.toFile(), listener.getLibPath(JSRuntimeType.V8));
            assertFalse(listener.isDeploy(JSRuntimeType.V8));
            assertFalse(listener.isLibInSystemPath(JSRuntimeType.V8));
            assertFalse(listener.isSuppressingError(JSRuntimeType.V8));
            try (InputStream bundled = getClass().getResourceAsStream("/javet/natives/" + ANDROID_RESOURCE);
                 InputStream actual = Files.newInputStream(extracted)) {
                assertNotNull(bundled);
                assertArrayEquals(sha256(bundled), sha256(actual));
            }
            try (InputStream input = Files.newInputStream(extracted)) {
                byte[] header = input.readNBytes(20);
                assertArrayEquals(new byte[]{0x7f, 'E', 'L', 'F', 2, 1},
                        java.util.Arrays.copyOf(header, 6));
                assertEquals(183, Byte.toUnsignedInt(header[18])); // ELF EM_AARCH64
                assertEquals(0, header[19]);
            }
        } finally {
            Files.deleteIfExists(extracted);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void dalvikKeepsTheAndroidFilename() {
        String fileName = "libjavet-v8-android.v." + VERSION + ".so";
        var library = JavetNativeResourceLoader.selectLibrary("android-arm64", fileName);
        assertEquals(ANDROID_RESOURCE, library.resourcePath());
        assertEquals(fileName, library.fileName());
    }

    @Test
    void unsupportedAndroidAbisNeverFallBackToDesktopNatives() {
        assertNull(JavetNativeResourceLoader.selectLibrary("android-x86_64", "linux.so"));
        assertNull(JavetNativeResourceLoader.selectLibrary("android-arm", "linux.so"));
    }

    @Test
    void desktopTargetsKeepTheirOriginalResources() {
        for (String target : new String[]{"windows-x86_64", "linux-x86_64", "linux-arm64",
                "macos-x86_64", "macos-arm64"}) {
            String fileName = "libjavet-v8-" + target + ".v." + VERSION;
            var library = JavetNativeResourceLoader.selectLibrary(target, fileName);
            assertEquals(fileName, library.resourcePath());
            assertEquals(fileName, library.fileName());
        }
    }

    @Test
    void missingAndroidResourceFailsWithoutDeploymentOrSystemFallback() {
        var library = new JavetNativeResourceLoader.NativeLibrary("android/missing.so", "linux.so");
        var listener = new JavetNativeResourceLoader.CombatantJavetLibLoadingListener(library);
        var error = assertThrows(UnsatisfiedLinkError.class, () -> listener.getLibPath(JSRuntimeType.V8));
        assertTrue(error.getMessage().contains("android/missing.so"));
    }

    @Test
    void bundledHostNativeStillCreatesAndExecutesV8() throws Exception {
        AutoCloseable owner = () -> { };
        var runtime = JavetRuntimeBootstrap.createRuntime(owner);
        try {
            assertEquals(42, runtime.getExecutor("21 * 2").executeInteger());
        } finally {
            JavetRuntimeBootstrap.closeRuntime(owner, runtime);
        }
    }

    private static byte[] sha256(InputStream stream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        new DigestInputStream(stream, digest).transferTo(java.io.OutputStream.nullOutputStream());
        return digest.digest();
    }
}
