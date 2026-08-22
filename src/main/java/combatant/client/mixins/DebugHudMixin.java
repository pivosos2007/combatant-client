/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import combatant.client.config.MainConfig;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.debug.RenderThread2DDebugRenderer;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontDebugStats;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugMode;
import combatant.client.util.sound.SoundDebugStats;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugHudMixin {
    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;Z)V",
                    ordinal = 0
            ),
            index = 1
    )
    private List<String> combatant$addEngineInfo(List<String> list) {
        if (RuntimeGate.isPanic() || list == null) return list;
        DebugMode debugMode = MainConfig.get().getDebugMode();
        if (debugMode != DebugMode.ALL && debugMode != DebugMode.RENDER_THREAD) return list;

        int samples = MainConfig.get().getMsaa3dSamples();
        boolean supported = CombatantRenderSystem.rhi().msaa().supported();
        MsaaWorldTarget.DebugStatus msaaStatus = MsaaWorldTarget.debugStatus();
        String msaa;
        if (samples <= 1) {
            msaa = "msaa: off";
        } else if (supported) {
            msaa = String.format("msaa: cfg=%dx target=%dx %s %dx%d alloc=%s active=%s resolve=%s",
                    samples,
                    msaaStatus.targetSamples(),
                    msaaStatus.state(),
                    msaaStatus.width(),
                    msaaStatus.height(),
                    msaaStatus.allocated() ? "yes" : "no",
                    msaaStatus.active() ? "yes" : "no",
                    msaaStatus.lastResolveOk() ? "ok" : "no"
            );
        } else {
            msaa = "msaa: unavailable";
        }

        List<String> out = new ArrayList<>(list.size() + 20);
        out.add("§cCombatant Engine");
        out.add("status:§aok");
        out.add(msaa);

        RenderThread2DDebugRenderer.Snapshot debug2d = RenderThread2DDebugRenderer.snapshot();
        out.add(String.format("2d rt gui: frame=%d gui=%dx%d rects=%d err=%s",
                debug2d.guiStateFrame(),
                debug2d.guiWidth(),
                debug2d.guiHeight(),
                debug2d.guiStateRects(),
                debug2d.guiStateError() == null || debug2d.guiStateError().isBlank() ? "none" : debug2d.guiStateError()
        ));
        out.add(String.format("2d rt imm: frame=%d fb=%dx%d draws=%d/%d v=%d i=%d err=%s",
                debug2d.immediateFrame(),
                debug2d.framebufferWidth(),
                debug2d.framebufferHeight(),
                debug2d.immediateDraws(),
                debug2d.immediateBatches(),
                debug2d.immediateVertices(),
                debug2d.immediateIndices(),
                debug2d.immediateError() == null || debug2d.immediateError().isBlank() ? "none" : debug2d.immediateError()
        ));

        Renderer2D.BatchStats batchStats = Renderer2D.getBatchStats();
        out.add(String.format("ui batch: %s, last %d draws / %d batches",
                batchStats.isActive() ? "active" : "idle",
                batchStats.getLastDraws(),
                batchStats.getLastOrder()
        ));
        out.add(String.format("ui batch frame: %d draws / %d batches",
                batchStats.getFrameDraws(),
                batchStats.getFrameBatches()
        ));
        out.add(String.format("ui batch geom: v=%d, i=%d, pool=%d",
                batchStats.getLastVertices(),
                batchStats.getLastIndices(),
                batchStats.getPoolTotal()
        ));
        out.add(String.format("ui batch frame geom: v=%d, i=%d",
                batchStats.getFrameVertices(),
                batchStats.getFrameIndices()
        ));
        if (batchStats.getLastError() != null && !batchStats.getLastError().isBlank()) {
            out.add("ui batch warn: " + batchStats.getLastError());
        }

        out.add(String.format("text msdf: ok=%d, fb=%d, attempts=%d",
                FontDebugStats.getMsdfSuccess(),
                FontDebugStats.getMsdfFallbacks(),
                FontDebugStats.getMsdfAttempts()
        ));
        if (!FontDebugStats.getLastMsdfFallback().isBlank()) {
            String reason = FontDebugStats.getLastMsdfFallbackReason();
            out.add("text msdf last fb: " + FontDebugStats.getLastMsdfFallback()
                    + (reason.isBlank() ? "" : " (" + reason + ")"));
        }

        out.add(String.format("wav(al): buffers %d/%d, sources %d/%d",
                SoundDebugStats.getBuffersAlive(),
                SoundDebugStats.getBuffersCreated(),
                SoundDebugStats.getSourcesAlive(),
                SoundDebugStats.getSourcesCreated()
        ));
        out.add(String.format("wav(al): allocs=%d (buf=%d, src=%d)",
                SoundDebugStats.getBuffersCreated() + SoundDebugStats.getSourcesCreated(),
                SoundDebugStats.getBuffersCreated(),
                SoundDebugStats.getSourcesCreated()
        ));
        out.add(String.format("wav(al): uploads=%d, bytes=%.2f MB",
                SoundDebugStats.getBufferUploads(),
                SoundDebugStats.getBufferBytes() / (1024.0 * 1024.0)
        ));
        out.add("");
        out.addAll(list);
        return out;
    }
}



