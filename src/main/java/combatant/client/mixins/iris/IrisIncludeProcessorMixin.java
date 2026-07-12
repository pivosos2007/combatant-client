/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.util.logging.DebugLog;

import java.util.Objects;

@Pseudo
@Mixin(value = IncludeProcessor.class, remap = false)
public abstract class IrisIncludeProcessorMixin {
    @Unique
    private ShaderPatchEngine.Session combatant$patchSession;
    @Unique
    private String combatant$patchSessionPackName;

    @Unique
    private static String combatant$currentShaderPackName() {
        String loadingPackName = ShaderPatchEngine.loadingShaderPackName();
        if (loadingPackName != null && !loadingPackName.isBlank()) {
            return loadingPackName;
        }
        try {
            return Iris.getCurrentPackName();
        } catch (Throwable throwable) {
            DebugLog.warn("[IrisPatch] Unable to query current Iris shaderpack name: %s", throwable.getClass().getSimpleName());
            return null;
        }
    }

    @ModifyReturnValue(method = "getIncludedFile", at = @At("RETURN"), remap = false)
    private ImmutableList<String> combatant$compileShaderpackPatch(ImmutableList<String> lines, AbsolutePackPath path) {
        if (lines == null || path == null) {
            return lines;
        }

        String packName = combatant$currentShaderPackName();
        if (combatant$patchSession == null || !Objects.equals(packName, combatant$patchSessionPackName)) {
            combatant$patchSessionPackName = packName;
            combatant$patchSession = ShaderPatchEngine.newSession(packName);
            DebugLog.info("[IrisPatch] IncludeProcessor hook active: shaderPack='%s' active=%s firstPath=%s",
                    packName == null ? "" : packName,
                    combatant$patchSession.isActive(),
                    path.getPathString());
        }

        if (!combatant$patchSession.isActive()) {
            return lines;
        }

        IncludeProcessor processor = (IncludeProcessor) (Object) this;
        return combatant$patchSession.patch(
                path.getPathString(),
                lines,
                candidate -> processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath(candidate))
        );
    }
}
