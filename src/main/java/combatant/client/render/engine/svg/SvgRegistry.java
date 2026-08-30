/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import combatant.client.util.logging.DebugLog;

import java.util.*;

public enum SvgRegistry {
    ;
    public static final String DEFAULT_NAMESPACE = "combatant";
    public static final String SVG_ROOT = "svg";

    private static final Map<String, Identifier> BY_RELATIVE = new HashMap<>();
    private static final Map<String, Identifier> BY_BASENAME = new HashMap<>();

    @AssetLoad(value = AssetLoadPhase.INITIALIZE, order = 100)
    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        reload(mc.getResourceManager());
    }

    @AssetLoad(order = 300)
    public static void reload(@Nullable ResourceManager manager) {
        BY_RELATIVE.clear();
        BY_BASENAME.clear();
        SvgMeshBackend.clearCache();
        SvgMsdfRegistry.clearCache();
        if (manager == null) return;

        Map<Identifier, Resource> found = manager.listResources(SVG_ROOT, id -> id.getPath().endsWith(".svg"));
        if (found.isEmpty()) return;

        List<Identifier> ids = new ArrayList<>(found.keySet());
        ids.sort(Comparator
                .comparing((Identifier id) -> !DEFAULT_NAMESPACE.equals(id.getNamespace()))
                .thenComparing(Identifier::toString));

        Set<String> basenameConflicts = new HashSet<>();
        for (Identifier id : ids) {
            String path = id.getPath();
            if (path == null || !path.startsWith(SVG_ROOT + "/") || !path.endsWith(".svg")) continue;

            String relativeNoExt = path.substring((SVG_ROOT + "/").length(), path.length() - 4);
            if (relativeNoExt.isBlank()) continue;

            BY_RELATIVE.putIfAbsent(relativeNoExt, id);

            String basename = relativeNoExt;
            int slash = basename.lastIndexOf('/');
            if (slash >= 0) basename = basename.substring(slash + 1);
            if (basename.isBlank()) continue;

            Identifier prev = BY_BASENAME.putIfAbsent(basename, id);
            if (prev != null && !prev.equals(id)) {
                basenameConflicts.add(basename);
            }
        }

        for (String conflict : basenameConflicts) {
            BY_BASENAME.remove(conflict);
        }

        DebugLog.renderThread("[Combatant][SVG] Indexed %d svg files (%d basename aliases, %d conflicts)",
                BY_RELATIVE.size(), BY_BASENAME.size(), basenameConflicts.size());
    }

    public static @Nullable Identifier resolve(String nameOrPath) {
        if (nameOrPath == null) return null;
        String raw = nameOrPath.trim();
        if (raw.isEmpty()) return null;

        String normalized = raw.endsWith(".svg") ? raw.substring(0, raw.length() - 4) : raw;
        if (normalized.isEmpty()) return null;

        if (normalized.indexOf(':') >= 0) {
            try {
                Identifier id = Identifier.parse(normalized);
                if (!id.getPath().endsWith(".svg")) {
                    return id.withSuffix(".svg");
                }
                return id;
            } catch (Throwable t) {
                return null;
            }
        }

        Identifier byRelative = BY_RELATIVE.get(normalized);
        if (byRelative != null) return byRelative;

        String basename = normalized;
        int slash = basename.lastIndexOf('/');
        if (slash >= 0) basename = basename.substring(slash + 1);
        Identifier byBasename = BY_BASENAME.get(basename);
        if (byBasename != null) return byBasename;

        return Identifier.fromNamespaceAndPath(DEFAULT_NAMESPACE, SVG_ROOT + "/" + normalized + ".svg");
    }
}
