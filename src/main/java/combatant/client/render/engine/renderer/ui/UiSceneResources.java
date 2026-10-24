/* Combatant Client. GPL-3.0-only. */
package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** Scene-read snapshots shared by blur/glass/materials, with independent material output.
 * Both targets are frame-transient; no GPU handles escape into material specifications.
 */
public final class UiSceneResources {
    private static TextureTarget currentTargetSnapshot;
    private static TextureTarget materialOutput;
    private UiSceneResources() {}

    public static void beginFrame() {
        currentTargetSnapshot = null;
        materialOutput = null;
    }

    public static void onBackendChanged() { beginFrame(); }

    public static @Nullable GpuTextureView captureCurrentTarget(Minecraft minecraft,
                                                                 @Nullable GpuTextureView source) {
        if (minecraft == null || source == null) return null;
        TextureTarget read = ensureTarget(minecraft, "combatant-ui-current-target-snapshot", "Renderer2D.currentTargetSnapshot");
        if (read == null || read.getColorTextureView() == null || read.getColorTextureView() == source) return null;
        try {
            return CombatantRenderSystem.rhi().textureBlitter().copyFast(source, read.getColorTextureView())
                    ? read.getColorTextureView() : null;
        } catch (Throwable ignored) { return null; }
    }

    public static @Nullable TextureTarget materialOutput(Minecraft minecraft) {
        // Resolve on every pass: immediate UI may render outside deferred frame extraction.
        // frameTransient performs keyed reuse and tracks RHI resize/backend generations.
        materialOutput = ensureTarget(minecraft, "combatant-ui-material-output", "Renderer2D.materialOutput");
        return materialOutput;
    }

    public static boolean isCurrentTargetSnapshot(@Nullable GpuTextureView view) {
        return view != null && currentTargetSnapshot != null && view == currentTargetSnapshot.getColorTextureView();
    }

    private static @Nullable TextureTarget ensureTarget(Minecraft minecraft, String key, String owner) {
        if (minecraft == null || minecraft.getWindow() == null) return null;
        int w = minecraft.getWindow().getWidth();
        int h = minecraft.getWindow().getHeight();
        if (w <= 0 || h <= 0) return null;
        TextureTarget target = CombatantRenderSystem.resources().frameTransient(
                TransientTargetDescriptor.frame(key, w, h, false, owner));
        if (key.equals("combatant-ui-current-target-snapshot")) currentTargetSnapshot = target;
        return target;
    }
}
