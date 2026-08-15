/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.loader;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

public final class StandardFabricLoaderBridge implements LoaderBridge {
    private static final String[] MOD_IDS = {"combatant"};
    private static final String MANAGED_RUNTIME_CLASS =
            "net.fabricmc.loader.impl.runtime.ClientBoundRuntime";

    @Override
    public boolean isStandardFabric() {
        return true;
    }

    @Override
    public boolean supportsSafeJarReplacement() {
        return false;
    }

    @Override
    public boolean supportsManagedRuntime() {
        try {
            Class.forName(MANAGED_RUNTIME_CLASS, false, StandardFabricLoaderBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @Override
    public boolean suspendManagedRuntime(String reason) {
        return invokeManagedRuntime("suspend", reason);
    }

    @Override
    public boolean resumeManagedRuntime(String reason) {
        return invokeManagedRuntime("resume", reason);
    }

    @Override
    public Path currentModJarPath() {
        Optional<ModContainer> container = currentContainer();
        if (container.isEmpty() || container.get().getRootPaths().isEmpty()) {
            return null;
        }
        return container.get().getRootPaths().get(0);
    }

    @Override
    public String describeClassSource(String className) {
        if (className == null || className.isBlank()) return "<unknown>";
        String resource = className.replace('.', '/') + ".class";
        URL url = StandardFabricLoaderBridge.class.getClassLoader().getResource(resource);
        return url == null ? "<not found>" : url.toString();
    }

    @Override
    public String describeResourceSource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return "<unknown>";
        URL url = StandardFabricLoaderBridge.class.getClassLoader().getResource(resourcePath);
        return url == null ? "<not found>" : url.toString();
    }

    private Optional<ModContainer> currentContainer() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String id : MOD_IDS) {
            Optional<ModContainer> container = loader.getModContainer(id);
            if (container.isPresent()) return container;
        }
        return Optional.empty();
    }

    private boolean invokeManagedRuntime(String methodName, String reason) {
        try {
            Class<?> runtimeClass = Class.forName(
                    MANAGED_RUNTIME_CLASS,
                    false,
                    StandardFabricLoaderBridge.class.getClassLoader()
            );
            Method method = runtimeClass.getMethod(methodName, String.class, String.class);
            for (String modId : MOD_IDS) {
                Object result = method.invoke(null, modId, reason);
                if (Boolean.TRUE.equals(result)) return true;
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
