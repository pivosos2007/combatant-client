/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.litematica;

import combatant.client.features.gui.schematic.SchematicScreen;
import combatant.client.util.screen.ClientScreen;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/** Does not replace native configuration or interfere with Litematica settings persistence. */
@Pseudo
@Mixin(value = GuiConfigs.class, remap = false)
public abstract class GuiConfigsMixin {
    @Inject(method = "initGui", at = @At("TAIL"), require = 0)
    private void combatant$addWorkspaceEntry(CallbackInfo ci) {
        // Malilib exposes addButton through its GuiBase hierarchy. Resolve dynamically because
        // the inherited method's access/return type differs across malilib builds.
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            ButtonGeneric button = new ButtonGeneric(
                    Math.max(10, mc.getWindow().getGuiScaledWidth() - 148), 5, 136, 20,
                    "Combatant Workspace");
            IButtonActionListener listener = (ButtonBase ignored, int click) ->
                    mc.execute(() -> ClientScreen.show(mc, new SchematicScreen(ClientScreen.current(mc))));
            Class<?> type = this.getClass();
            while (type != null) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals("addButton") || method.getParameterCount() != 2) continue;
                    if (!method.getParameterTypes()[0].isAssignableFrom(button.getClass())) continue;
                    if (!method.getParameterTypes()[1].isAssignableFrom(listener.getClass())) continue;
                    method.setAccessible(true);
                    method.invoke(this, button, listener);
                    return;
                }
                type = type.getSuperclass();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Never break Litematica's native config when a malilib layout changes.
        }
    }
}
