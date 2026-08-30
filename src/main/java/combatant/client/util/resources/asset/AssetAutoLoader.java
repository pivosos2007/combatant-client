/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.resources.asset;

import combatant.client.render.engine.text.FontInfo;
import combatant.client.util.logging.DebugLog;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Central ClassGraph-backed discovery for render/resource assets.
 *
 * <p>Discovery reads only classes carrying Combatant asset annotations. Resource-backed
 * subsystems, including player animation scripts, declare their assets through metadata here.</p>
 */
public enum AssetAutoLoader {
    ;

    private static final String DEFAULT_SCOPE = "combatant.client";

    private static final List<Hook> HOOKS = new ArrayList<>();
    private static final List<Class<?>> TEXTURE_CATALOGS = new ArrayList<>();
    private static final List<FontDefinition> FONT_ASSETS = new ArrayList<>();
    private static final Map<String, ScriptDefinition> SCRIPT_ASSETS = new LinkedHashMap<>();
    private static final Map<String, Identifier> RESOURCE_ASSETS = new LinkedHashMap<>();
    private static final Set<String> UI_SCRIPT_IDS = new LinkedHashSet<>();
    private static boolean discovered;

    public static synchronized void discover() {
        if (discovered) return;

        ClassGraph graph = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .enableMethodInfo()
                .enableFieldInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .ignoreFieldVisibility()
                .acceptPackages(DEFAULT_SCOPE);

        try (ScanResult scan = graph.scan()) {
            discoverHooks(scan);
            discoverTextureCatalogs(scan);
            discoverFontCatalogs(scan);
            discoverScriptCatalogs(scan);
            discoverResourceCatalogs(scan);
            discoverUiScripts(scan);
            sortDiscoveredAssets();
            discovered = true;
            DebugLog.info("[Combatant][Assets] discovered hooks=%d textureCatalogs=%d fonts=%d scripts=%d resources=%d uiScripts=%d",
                    HOOKS.size(), TEXTURE_CATALOGS.size(), FONT_ASSETS.size(), SCRIPT_ASSETS.size(), RESOURCE_ASSETS.size(), UI_SCRIPT_IDS.size());
        } catch (Throwable error) {
            clearDiscovery();
            throw new IllegalStateException("Combatant asset discovery failed", error);
        }
    }

    public static void initialize(ResourceManager manager) {
        run(AssetLoadPhase.INITIALIZE, manager);
    }

    public static void reload(ResourceManager manager) {
        run(AssetLoadPhase.RELOAD, manager);
    }

    public static void postReload(ResourceManager manager) {
        run(AssetLoadPhase.POST_RELOAD, manager);
    }

    public static List<FontDefinition> fontAssets() {
        discover();
        return List.copyOf(FONT_ASSETS);
    }

    public static Set<String> uiScriptIds() {
        discover();
        return Set.copyOf(UI_SCRIPT_IDS);
    }

    public static ScriptDefinition scriptAsset(Enum<?> key) {
        if (key == null) throw new IllegalArgumentException("Script asset key cannot be null");
        discover();
        ScriptDefinition definition = SCRIPT_ASSETS.get(catalogKey(key.getDeclaringClass(), key.name()));
        if (definition == null) {
            throw new IllegalArgumentException("No @ScriptAsset metadata for "
                    + key.getDeclaringClass().getName() + "." + key.name());
        }
        return definition;
    }

    public static List<ScriptDefinition> scriptAssets(Class<? extends Enum<?>> catalogType) {
        if (catalogType == null) throw new IllegalArgumentException("Script catalog type cannot be null");
        if (!catalogType.isEnum()) throw new IllegalArgumentException("Script catalog type must be an enum: " + catalogType.getName());
        discover();
        Object[] constants = catalogType.getEnumConstants();
        if (constants == null || constants.length == 0) return List.of();
        ArrayList<ScriptDefinition> definitions = new ArrayList<>(constants.length);
        for (Object constant : constants) {
            definitions.add(scriptAsset((Enum<?>) constant));
        }
        definitions.sort(Comparator
                .comparingInt(ScriptDefinition::order)
                .thenComparing(definition -> definition.resource().toString()));
        return List.copyOf(definitions);
    }

    public static Identifier resourceAsset(Enum<?> key) {
        if (key == null) throw new IllegalArgumentException("Resource asset key cannot be null");
        discover();
        Identifier resource = RESOURCE_ASSETS.get(catalogKey(key.getDeclaringClass(), key.name()));
        if (resource == null) {
            throw new IllegalArgumentException("No @ResourceAsset metadata for "
                    + key.getDeclaringClass().getName() + "." + key.name());
        }
        return resource;
    }

    public static String classpathResource(Enum<?> key) {
        Identifier id = resourceAsset(key);
        return "assets/" + id.getNamespace() + "/" + id.getPath();
    }

    public static int preloadTextures(net.minecraft.client.renderer.texture.TextureManager textureManager) {
        discover();
        if (textureManager == null) return 0;

        LinkedHashSet<Identifier> ids = new LinkedHashSet<>();
        for (Class<?> type : TEXTURE_CATALOGS) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    collectIdentifiers(field.get(null), ids);
                } catch (Throwable error) {
                    DebugLog.error("[Combatant][Assets] Failed to read texture catalog field %s.%s",
                            error, type.getName(), field.getName());
                }
            }
        }

        int loaded = 0;
        for (Identifier id : ids) {
            try {
                textureManager.getTexture(id);
                loaded++;
            } catch (Throwable error) {
                DebugLog.error("[Combatant][Assets] Failed to preload texture %s", error, id);
            }
        }
        return loaded;
    }

    private static synchronized void run(AssetLoadPhase phase, ResourceManager manager) {
        discover();
        for (Hook hook : HOOKS) {
            if (!hook.phases.contains(phase)) continue;
            try {
                if (hook.acceptsManager) {
                    hook.method.invoke(null, manager);
                } else {
                    hook.method.invoke(null);
                }
            } catch (Throwable error) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                DebugLog.error("[Combatant][Assets] %s hook failed: %s#%s",
                        cause, phase.name().toLowerCase(Locale.ROOT), hook.method.getDeclaringClass().getName(), hook.method.getName());
            }
        }
    }

    private static void discoverHooks(ScanResult scan) throws ReflectiveOperationException {
        for (ClassInfo info : scan.getClassesWithMethodAnnotation(AssetLoad.class.getName())) {
            Class<?> type = Class.forName(info.getName(), false, AssetAutoLoader.class.getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                AssetLoad annotation = method.getAnnotation(AssetLoad.class);
                if (annotation == null) continue;
                validateHook(method);
                method.setAccessible(true);
                HOOKS.add(new Hook(method,
                        EnumSet.copyOf(Arrays.asList(annotation.value())),
                        annotation.order(),
                        method.getParameterCount() == 1));
            }
        }
    }

    private static void discoverTextureCatalogs(ScanResult scan) throws ClassNotFoundException {
        for (ClassInfo info : scan.getClassesWithAnnotation(TextureCatalog.class.getName())) {
            TEXTURE_CATALOGS.add(Class.forName(info.getName(), false, AssetAutoLoader.class.getClassLoader()));
        }
    }

    private static void discoverFontCatalogs(ScanResult scan) throws ReflectiveOperationException {
        for (ClassInfo info : scan.getClassesWithAnnotation(FontCatalog.class.getName())) {
            Class<?> type = Class.forName(info.getName(), false, AssetAutoLoader.class.getClassLoader());
            if (!type.isEnum()) {
                throw new IllegalArgumentException("@FontCatalog type must be an enum: " + type.getName());
            }
            FontCatalog catalog = type.getAnnotation(FontCatalog.class);
            Object[] constants = type.getEnumConstants();
            if (constants == null) continue;
            for (Object constant : constants) {
                String name = ((Enum<?>) constant).name();
                Field field = type.getField(name);
                FontAsset asset = field.getAnnotation(FontAsset.class);
                if (asset == null) {
                    throw new IllegalArgumentException(type.getName() + "." + name + " must be annotated with @FontAsset");
                }
                Identifier resource = Identifier.fromNamespaceAndPath(
                        catalog.namespace(),
                        join(catalog.root(), asset.value())
                );
                FONT_ASSETS.add(new FontDefinition(
                        new FontInfo(asset.family(), asset.type()),
                        resource,
                        asset.atlasOnly(),
                        asset.primary(),
                        asset.prewarm(),
                        asset.order()
                ));
            }
        }
    }

    private static void discoverScriptCatalogs(ScanResult scan) throws ReflectiveOperationException {
        for (ClassInfo info : scan.getClassesWithAnnotation(ScriptCatalog.class.getName())) {
            Class<?> type = Class.forName(info.getName(), false, AssetAutoLoader.class.getClassLoader());
            if (!type.isEnum()) {
                throw new IllegalArgumentException("@ScriptCatalog type must be an enum: " + type.getName());
            }
            ScriptCatalog catalog = type.getAnnotation(ScriptCatalog.class);
            Object[] constants = type.getEnumConstants();
            if (constants == null) continue;
            for (Object constant : constants) {
                String name = ((Enum<?>) constant).name();
                Field field = type.getField(name);
                ScriptAsset asset = field.getAnnotation(ScriptAsset.class);
                if (asset == null) {
                    throw new IllegalArgumentException(type.getName() + "." + name + " must be annotated with @ScriptAsset");
                }
                Identifier resource = Identifier.fromNamespaceAndPath(
                        catalog.namespace(),
                        join(catalog.root(), asset.value())
                );
                Identifier addon = asset.addon().isBlank()
                        ? null
                        : Identifier.fromNamespaceAndPath(catalog.namespace(), join(catalog.root(), asset.addon()));
                String key = catalogKey(type, name);
                ScriptDefinition duplicate = SCRIPT_ASSETS.putIfAbsent(key,
                        new ScriptDefinition(resource, addon, asset.tree(), asset.order()));
                if (duplicate != null) {
                    throw new IllegalStateException("Duplicate script asset key: " + key);
                }
            }
        }
    }

    private static void discoverResourceCatalogs(ScanResult scan) throws ReflectiveOperationException {
        for (ClassInfo info : scan.getClassesWithAnnotation(ResourceCatalog.class.getName())) {
            Class<?> type = Class.forName(info.getName(), false, AssetAutoLoader.class.getClassLoader());
            if (!type.isEnum()) {
                throw new IllegalArgumentException("@ResourceCatalog type must be an enum: " + type.getName());
            }
            ResourceCatalog catalog = type.getAnnotation(ResourceCatalog.class);
            Object[] constants = type.getEnumConstants();
            if (constants == null) continue;
            for (Object constant : constants) {
                String name = ((Enum<?>) constant).name();
                Field field = type.getField(name);
                ResourceAsset asset = field.getAnnotation(ResourceAsset.class);
                if (asset == null) {
                    throw new IllegalArgumentException(type.getName() + "." + name + " must be annotated with @ResourceAsset");
                }
                Identifier resource = Identifier.fromNamespaceAndPath(
                        catalog.namespace(),
                        join(catalog.root(), asset.value())
                );
                String key = catalogKey(type, name);
                Identifier duplicate = RESOURCE_ASSETS.putIfAbsent(key, resource);
                if (duplicate != null) {
                    throw new IllegalStateException("Duplicate resource asset key: " + key);
                }
            }
        }
    }

    private static void discoverUiScripts(ScanResult scan) throws ClassNotFoundException {
        for (ClassInfo info : scan.getClassesWithAnnotation(UiScriptAsset.class.getName())) {
            Class<?> type = Class.forName(info.getName(), false, AssetAutoLoader.class.getClassLoader());
            UiScriptAsset asset = type.getAnnotation(UiScriptAsset.class);
            if (asset == null || asset.value().isBlank()) continue;
            if (!UI_SCRIPT_IDS.add(asset.value())) {
                throw new IllegalStateException("Duplicate @UiScriptAsset id: " + asset.value());
            }
        }
    }

    private static void validateHook(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("@AssetLoad method must be static: " + method);
        }
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("@AssetLoad method must return void: " + method);
        }
        if (method.getParameterCount() > 1
                || (method.getParameterCount() == 1 && method.getParameterTypes()[0] != ResourceManager.class)) {
            throw new IllegalArgumentException("@AssetLoad method must accept no args or ResourceManager: " + method);
        }
    }

    private static void sortDiscoveredAssets() {
        HOOKS.sort(Comparator
                .comparingInt((Hook hook) -> hook.order)
                .thenComparing(hook -> hook.method.getDeclaringClass().getName())
                .thenComparing(hook -> hook.method.getName()));
        TEXTURE_CATALOGS.sort(Comparator.comparing(Class::getName));
        FONT_ASSETS.sort(Comparator
                .comparingInt(FontDefinition::order)
                .thenComparing(def -> def.info().family())
                .thenComparing(def -> def.info().type().ordinal()));
    }

    private static void collectIdentifiers(Object value, Set<Identifier> out) {
        if (value == null) return;
        if (value instanceof Identifier id) {
            out.add(id);
            return;
        }
        if (value instanceof Identifier[] ids) {
            out.addAll(Arrays.asList(ids));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element instanceof Identifier id) out.add(id);
            }
        }
    }

    private static String catalogKey(Class<?> type, String constant) {
        return type.getName() + "#" + constant;
    }

    private static String join(String left, String right) {
        String a = left == null ? "" : left.replace('\\', '/').replaceAll("^/+|/+$", "");
        String b = right == null ? "" : right.replace('\\', '/').replaceAll("^/+|/+$", "");
        return a.isEmpty() ? b : b.isEmpty() ? a : a + "/" + b;
    }

    private static void clearDiscovery() {
        HOOKS.clear();
        TEXTURE_CATALOGS.clear();
        FONT_ASSETS.clear();
        SCRIPT_ASSETS.clear();
        RESOURCE_ASSETS.clear();
        UI_SCRIPT_IDS.clear();
        discovered = false;
    }

    private record Hook(Method method, EnumSet<AssetLoadPhase> phases, int order, boolean acceptsManager) {
    }

    public record FontDefinition(FontInfo info, Identifier resource, boolean atlasOnly, boolean primary, boolean prewarm, int order) {
    }

    public record ScriptDefinition(Identifier resource, Identifier addonResource, boolean tree, int order) {
    }
}
