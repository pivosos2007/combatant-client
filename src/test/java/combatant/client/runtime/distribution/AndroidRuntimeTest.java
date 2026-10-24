/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.distribution;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AndroidRuntimeTest {
    @Test
    void recognizesFclOpenJdkUsingAndroidOsVersion() {
        Properties properties = openJdk("Linux");
        properties.setProperty("os.version", "Android-15");
        assertTrue(AndroidRuntime.detect(properties, Map.of(), path -> false));
    }

    @Test
    void recognizesLauncherEnvironmentWithoutDalvik() {
        assertTrue(AndroidRuntime.detect(openJdk("Linux"),
                Map.of("ANDROID_ROOT", "/system", "ANDROID_DATA", "/data"), path -> false));
    }

    @Test
    void recognizesBionicLinkerWithoutLauncherEnvironment() {
        assertTrue(AndroidRuntime.detect(openJdk("Linux"), Map.of(), "/system/bin/linker64"::equals));
        assertTrue(AndroidRuntime.detect(openJdk("Linux"), Map.of(),
                "/apex/com.android.runtime/bin/linker64"::equals));
    }

    @Test
    void recognizesDalvikAndExplicitAndroid() {
        Properties properties = openJdk("Linux");
        properties.setProperty("java.vm.name", "Dalvik");
        assertTrue(AndroidRuntime.detect(properties, Map.of(), path -> false));
        assertTrue(AndroidRuntime.detect(openJdk("Android"), Map.of(), path -> false));
    }

    @Test
    void desktopLinuxWithAndroidSdkRemainsLinux() {
        assertFalse(AndroidRuntime.detect(openJdk("Linux"),
                Map.of("ANDROID_HOME", "/opt/android-sdk", "ANDROID_SDK_ROOT", "/opt/android-sdk"),
                path -> false));
        assertFalse(AndroidRuntime.detect(openJdk("Linux"), Map.of("ANDROID_ROOT", "/opt/android"),
                path -> false));
    }

    @Test
    void windowsAndMacIgnoreAndroidEnvironment() {
        Map<String, String> environment = Map.of("ANDROID_ROOT", "/system", "ANDROID_DATA", "/data");
        assertFalse(AndroidRuntime.detect(openJdk("Windows 11"), environment, path -> true));
        assertFalse(AndroidRuntime.detect(openJdk("Mac OS X"), environment, path -> true));
    }

    @Test
    void distributionTargetsKeepAndroidSeparateFromLinux() {
        assertEquals("android-arm64", DistributionCapabilities.targetId("Android", "aarch64"));
        assertEquals("android-arm64", DistributionCapabilities.targetId("Linux (Android-15)", "arm64"));
        assertEquals("linux-arm64", DistributionCapabilities.targetId("Linux", "aarch64"));
        assertEquals("windows-x86_64", DistributionCapabilities.targetId("Windows 11", "amd64"));
        assertEquals("macos-arm64", DistributionCapabilities.targetId("Mac OS X", "aarch64"));
    }

    private static Properties openJdk(String os) {
        Properties properties = new Properties();
        properties.setProperty("os.name", os);
        properties.setProperty("java.vm.name", "OpenJDK 64-Bit Server VM");
        properties.setProperty("java.runtime.name", "OpenJDK Runtime Environment");
        return properties;
    }
}
