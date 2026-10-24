/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.distribution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

public final class AndroidRuntime {
    private static final boolean ANDROID = detect(System.getProperties(), System.getenv(),
            path -> Files.exists(Path.of(path)));

    private AndroidRuntime() {
    }

    public static boolean isAndroid() {
        return ANDROID;
    }

    static boolean detect(Properties properties, Map<String, String> environment, Predicate<String> exists) {
        String os = properties.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("android")) return true;
        if (!os.startsWith("linux")) return false;
        for (String key : new String[]{"java.vm.name", "java.runtime.name", "java.vendor", "os.version"}) {
            String value = properties.getProperty(key, "").toLowerCase(Locale.ROOT);
            if (value.contains("android") || value.startsWith("dalvik")) return true;
        }
        // FCL/Pojav run OpenJDK, so neither the VM name nor os.name identifies Bionic.
        if ("/system".equals(environment.get("ANDROID_ROOT"))
                && "/data".equals(environment.get("ANDROID_DATA"))) return true;
        return exists.test("/system/bin/linker") || exists.test("/system/bin/linker64")
                || exists.test("/apex/com.android.runtime/bin/linker64");
    }
}
