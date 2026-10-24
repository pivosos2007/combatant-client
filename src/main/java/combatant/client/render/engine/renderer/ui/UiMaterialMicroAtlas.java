/* Combatant Client. GPL-3.0-only. */
package combatant.client.render.engine.renderer.ui;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** One shared non-colour microtexture atlas, resolved per draw after texture reloads.
 * A missing asset never makes existing UI materials invisible: shaders fall back.
 */
public final class UiMaterialMicroAtlas {
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(
            "combatant", "textures/ui/material_micro_atlas.png");
    private UiMaterialMicroAtlas() {}

    public record Binding(GpuTextureView view, GpuSampler sampler) {}

    public static @Nullable Binding resolve() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) return null;
            AbstractTexture tex = mc.getTextureManager().getTexture(ATLAS);
            if (tex == null || tex.getTextureView() == null || tex.getSampler() == null) return null;
            return new Binding(tex.getTextureView(), tex.getSampler());
        } catch (RuntimeException exception) { return null; }
    }
}
