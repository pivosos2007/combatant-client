/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import combatant.client.util.logging.DebugLog;

@Mixin(GlProgram.class)
public abstract class GlProgramMixin {
    @Unique
    private static final String combatant$namespace = "combatant";

    @WrapOperation(
            method = "link",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V"
            )
    )
    private static void combatant$wrapProgramLinkInfo(Logger logger,
                                                       String format,
                                                       Object[] args,
                                                       Operation<Void> original,
                                                       GlShaderModule vertexShader,
                                                       GlShaderModule fragmentShader,
                                                       VertexFormat[] vertexFormats,
                                                       String debugLabel) {
        if (combatant$isCombatantShader(vertexShader)
                || combatant$isCombatantShader(fragmentShader)
                || combatant$isCombatantLabel(debugLabel)) {
            DebugLog.renderThread("[RenderPipeline] %s", combatant$formatSlf4j(format, args));
            return;
        }

        original.call(logger, format, args);
    }

    @WrapOperation(
            method = "setupBindGroupLayouts",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"
            )
    )
    private void combatant$wrapPipelineWarning(Logger logger,
                                                 String format,
                                                 Object first,
                                                 Object second,
                                                 Operation<Void> original) {
        String debugLabel = ((GlProgram) (Object) this).getDebugLabel();
        if (combatant$isCombatantLabel(debugLabel)) {
            DebugLog.warn("[RenderPipeline] %s", combatant$formatSlf4j(format, first, second));
            return;
        }

        original.call(logger, format, first, second);
    }

    @Unique
    private static boolean combatant$isCombatantShader(GlShaderModule shader) {
        return shader != null && combatant$isCombatantId(shader.getId());
    }

    @Unique
    private static boolean combatant$isCombatantId(Identifier id) {
        return id != null && combatant$namespace.equals(id.getNamespace());
    }

    @Unique
    private static boolean combatant$isCombatantLabel(String label) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        return label.startsWith(combatant$namespace + ":") || label.startsWith("Combatant");
    }

    @Unique
    private static String combatant$formatSlf4j(String format, Object... args) {
        String template = String.valueOf(format);
        if (args == null || args.length == 0) {
            return template;
        }

        StringBuilder out = new StringBuilder(template.length() + args.length * 16);
        int cursor = 0;
        int argIndex = 0;
        while (argIndex < args.length) {
            int placeholder = template.indexOf("{}", cursor);
            if (placeholder < 0) {
                break;
            }
            out.append(template, cursor, placeholder);
            out.append(String.valueOf(args[argIndex++]));
            cursor = placeholder + 2;
        }
        out.append(template, cursor, template.length());
        while (argIndex < args.length) {
            out.append(' ').append(String.valueOf(args[argIndex++]));
        }
        return out.toString();
    }
}
