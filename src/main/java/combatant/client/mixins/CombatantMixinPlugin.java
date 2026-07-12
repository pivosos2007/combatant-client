/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import combatant.client.util.logging.DebugLog;

import java.util.List;
import java.util.Set;

public final class CombatantMixinPlugin implements IMixinConfigPlugin {
    // Sodium 26.2 uses Mojang RenderPipeline for terrain on both GL and Vulkan backends.
    private static final boolean STUB_SODIUM = false;
    private static final boolean STUB_IRIS = false;
    private static final Set<String> DISABLED_SODIUM_SHADOW_GRAPH_MIXINS = Set.of();
    private static final Set<String> OPTIONAL_SODIUM_MIXINS = Set.of(
            "combatant.client.mixins.sodium.SodiumBlockRendererMixin",
            "combatant.client.mixins.sodium.SodiumChunkBuilderMeshingTaskMixin",
            "combatant.client.mixins.sodium.SodiumChunkVertexMixin",
            "combatant.client.mixins.sodium.SodiumRenderSectionManagerVertexFormatMixin",
            "combatant.client.mixins.sodium.SodiumRenderRegionArenasVertexFormatMixin",
            "combatant.client.mixins.sodium.SodiumShaderChunkRendererMixin",
            "combatant.client.mixins.sodium.SodiumVertexConsumerTrackerMixin"
    );
    private static final Set<String> OPTIONAL_IRIS_MIXINS = Set.of(
            "combatant.client.mixins.iris.IrisCommonUniformsMixin",
            "combatant.client.mixins.iris.IrisGameRendererInteropMixin",
            "combatant.client.mixins.iris.IrisHandRendererAccessor",
            "combatant.client.mixins.iris.IrisHandRendererMixin",
            "combatant.client.mixins.iris.IrisIncludeProcessorMixin",
            "combatant.client.mixins.iris.IrisShaderPackLoadMixin",
            "combatant.client.mixins.iris.IrisRenderingPipelineFinalizeMixin",
            "combatant.client.mixins.iris.IrisVanillaHandInteropMixin"
    );
    private static final Set<String> DISABLED_WITH_IRIS_MIXINS = Set.of(
            "combatant.client.mixins.sodium.SodiumBlockRendererMixin",
            "combatant.client.mixins.sodium.SodiumChunkBuilderMeshingTaskMixin",
            "combatant.client.mixins.sodium.SodiumChunkVertexMixin",
            "combatant.client.mixins.sodium.SodiumRenderRegionArenasVertexFormatMixin",
            "combatant.client.mixins.sodium.SodiumRenderSectionManagerVertexFormatMixin",
            "combatant.client.mixins.sodium.SodiumShaderChunkRendererMixin"
    );
    private static final Set<String> OPTIONAL_XAERO_MINIMAP_MIXINS = Set.of(
            "combatant.client.mixins.xaero.minimap.AbstractWaypointRenderProviderMixin"
    );
    private static final Set<String> OPTIONAL_XAERO_WORLDMAP_MIXINS = Set.of(
            "combatant.client.mixins.xaero.worldmap.WorldMapWaypointAccessor",
            "combatant.client.mixins.xaero.worldmap.WaypointRenderProviderMixin"
    );
    private static final Set<String> OPTIONAL_MORECULLING_MIXINS = Set.of(
            "combatant.client.mixins.moreculling.CullingUtilsMixin"
    );
    private static volatile boolean loggedSodiumDecision;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (STUB_SODIUM && OPTIONAL_SODIUM_MIXINS.contains(mixinClassName)) {
            return false;
        }
        if (STUB_IRIS && OPTIONAL_IRIS_MIXINS.contains(mixinClassName)) {
            return false;
        }
        if (DISABLED_SODIUM_SHADOW_GRAPH_MIXINS.contains(mixinClassName)) {
            return false;
        }
        if (OPTIONAL_XAERO_MINIMAP_MIXINS.contains(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("xaerominimap");
        }
        if (OPTIONAL_XAERO_WORLDMAP_MIXINS.contains(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("xaeroworldmap");
        }
        if (OPTIONAL_MORECULLING_MIXINS.contains(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("moreculling");
        }
        if (OPTIONAL_IRIS_MIXINS.contains(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("iris");
        }
        if (DISABLED_WITH_IRIS_MIXINS.contains(mixinClassName)) {
            return !FabricLoader.getInstance().isModLoaded("iris");
        }
        if (!OPTIONAL_SODIUM_MIXINS.contains(mixinClassName)) {
            return true;
        }
        boolean sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
        if (!loggedSodiumDecision) {
            loggedSodiumDecision = true;
            DebugLog.warn("[SodiumCompat] optional mixins %s", sodiumLoaded ? "enabled" : "disabled");
        }
        return sodiumLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
