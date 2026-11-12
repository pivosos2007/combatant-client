/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.mixins.accessors.ShaderLoaderDefinitionsAccessor;
import combatant.client.mixins.accessors.ShaderSourceKeyAccessor;
import combatant.client.render.engine.shader.CombatantShaderSources;
import combatant.client.render.engine.shader.EntityFadeShaderSource;
import combatant.client.render.iris.IrisCompatibilityGuards;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.RenderResourceReadiness;
import combatant.client.util.resources.ResourceReloadHooks;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(ShaderManager.class)
public abstract class ShaderLoaderMixin {
    @Unique
    private static final Identifier ENTITY_FADE_MARKER = Identifier.fromNamespaceAndPath(
            "combatant", "shaders/include/entity_fade_marker.glsl");
    @Unique
    private static final Identifier VANILLA_ENTITY_ID = Identifier.withDefaultNamespace("core/entity");
    @Unique
    private static boolean combatant$loggedEntityOverride;
    @Unique
    private static boolean combatant$loggedExtendedShaderSources;
    @Unique
    private static Constructor<?> combatant$shaderSourceKeyConstructor;

    @Unique
    private static boolean combatant$addExtendedShaderSources(ResourceManager resourceManager, Map<Object, String> shaderSources) {
        Map<Identifier, Resource> resources = resourceManager.listResources("shaders", CombatantShaderSources::isCombatantExtendedSource);
        if (resources.isEmpty()) {
            return false;
        }

        int added = 0;
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
            ShaderType type = CombatantShaderSources.typeByLocation(resourceId);
            if (type == null) continue;

            String source = CombatantShaderSources.load(resourceManager, resourceId, entry.getValue());
            shaderSources.put(combatant$newShaderSourceKey(resourceId, type), source);
            added++;

            Identifier canonicalId = CombatantShaderSources.canonicalShaderId(resourceId);
            if (!canonicalId.equals(resourceId)) {
                shaderSources.put(combatant$newShaderSourceKey(canonicalId, type), source);
                added++;
            }
        }

        if (added > 0 && !combatant$loggedExtendedShaderSources) {
            combatant$loggedExtendedShaderSources = true;
            DebugLog.renderThread("[ShaderSource] registered Combatant .vert/.frag sources in ShaderManager: " + added + " keys");
        }
        return added > 0;
    }

    @Unique
    private static boolean combatant$allowVanillaEntityShaderOverride() {
        if (IrisCompatibilityGuards.suppressCombatantTerrainShaderOverrides()) {
            return false;
        }

        return true;
    }

    @Unique
    private static boolean combatant$applyEntityOverride(ResourceManager resourceManager, Map<Object, String> shaderSources) {
        String marker = combatant$loadShaderSource(resourceManager, ENTITY_FADE_MARKER);
        if (marker == null || marker.isBlank()) {
            DebugLog.warn("[ShaderOverride] entity fade marker unavailable; preserving vanilla entity shader");
            return false;
        }

        boolean changed = false;
        for (Map.Entry<Object, String> entry : shaderSources.entrySet()) {
            if (!combatant$isEntityFragmentKey(entry.getKey())) continue;
            String patched = EntityFadeShaderSource.patch(entry.getValue(), marker);
            if (patched == null) {
                DebugLog.warn("[ShaderOverride] entity fade marker insertion point missing; preserving vanilla entity shader");
                return false;
            }
            entry.setValue(patched);
            changed = true;
        }
        if (!changed && !combatant$loggedEntityOverride) {
            combatant$loggedEntityOverride = true;
            DebugLog.warn("[ShaderOverride] entity fade marker skipped: vanilla entity fragment key not found");
        } else if (changed && !combatant$loggedEntityOverride) {
            combatant$loggedEntityOverride = true;
            DebugLog.renderThread("[ShaderOverride] entity fade marker injected into vanilla entity fragment source");
        }
        return changed;
    }

    @Unique
    private static boolean combatant$isEntityFragmentKey(Object key) {
        if (!(key instanceof ShaderSourceKeyAccessor accessor)) return false;
        return VANILLA_ENTITY_ID.equals(accessor.combatant$getId()) && accessor.combatant$getType() == ShaderType.FRAGMENT;
    }

    @Unique
    private static String combatant$loadShaderSource(ResourceManager resourceManager, Identifier id) {
        try {
            return CombatantShaderSources.load(resourceManager, id);
        } catch (Exception e) {
            DebugLog.error("[ShaderOverride] read shader override failed: " + id, e);
            return null;
        }
    }

    @Unique
    private static Object combatant$newShaderSourceKey(Identifier id, ShaderType type) {
        try {
            if (combatant$shaderSourceKeyConstructor == null) {
                Class<?> keyClass = Class.forName("net.minecraft.client.renderer.ShaderManager$ShaderSourceKey");
                Constructor<?> constructor = keyClass.getDeclaredConstructor(Identifier.class, ShaderType.class);
                constructor.setAccessible(true);
                combatant$shaderSourceKeyConstructor = constructor;
            }
            return combatant$shaderSourceKeyConstructor.newInstance(id, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create ShaderManager shader source key for " + type + " " + id, e);
        }
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private void combatant$beforeShaderReload(GpuDevice device,
                                              @Coerce Object pendingResults,
                                              CallbackInfo ci) {
        RenderResourceReadiness.markReloading("shader reload");
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void combatant$afterShaderReload(GpuDevice device,
                                             @Coerce Object pendingResults,
                                             CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceReloadHooks.onReload(minecraft != null ? minecraft.getResourceManager() : null);
    }

    @ModifyReturnValue(method = "loadConfigs", at = @At("RETURN"))
    private static ShaderManager.Configs combatant$extendShaderSources(ShaderManager.Configs definitions,
                                                                       ResourceManager resourceManager) {
        ShaderLoaderDefinitionsAccessor accessor = (ShaderLoaderDefinitionsAccessor) (Object) definitions;
        Map<?, String> shaderSources = accessor.combatant$getShaderSources();
        Map<Object, String> replaced = new LinkedHashMap<>(shaderSources.size() + 64);
        replaced.putAll(shaderSources);

        boolean changed = combatant$addExtendedShaderSources(resourceManager, replaced);
        if (combatant$allowVanillaEntityShaderOverride()) {
            changed |= combatant$applyEntityOverride(resourceManager, replaced);
        }

        if (!changed) {
            return definitions;
        }

        Map<Identifier, PostChainConfig> postChains = accessor.combatant$getPostChains();
        @SuppressWarnings({"rawtypes", "unchecked"})
        ShaderManager.Configs updated = new ShaderManager.Configs((Map) replaced, definitions.includeSources(), postChains);
        return updated;
    }
}
