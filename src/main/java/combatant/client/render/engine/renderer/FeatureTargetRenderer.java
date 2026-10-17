/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

/** Dispatches a vanilla prepared feature frame into explicit Combatant-owned attachments. */
public final class FeatureTargetRenderer {
    private FeatureTargetRenderer() { }

    public enum Selection { ALL, OUTLINE, DEPTH }

    public static boolean render(String label, FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage,
                              GpuTextureView color, @Nullable GpuTextureView depth, Selection selection) {
        if (color == null) throw new IllegalArgumentException("Color attachment required: " + label);
        try (FeatureRenderDispatcher.PreparedFrame prepared = dispatcher.prepareFrame(storage);
             RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                     () -> label, color, Optional.empty(), depth, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            switch (selection) {
                case ALL -> FeatureRenderDispatcher.renderAllFeatures(pass, prepared);
                case OUTLINE -> {
                    if (!prepared.hasAnyOutline()) return false;
                    prepared.executeOutline(pass);
                }
                case DEPTH -> {
                    prepared.executeSolid(pass);
                    prepared.executeTranslucent(pass);
                    prepared.executeTranslucentAfterTerrain(pass);
                }
            }
            return true;
        }
    }
}
