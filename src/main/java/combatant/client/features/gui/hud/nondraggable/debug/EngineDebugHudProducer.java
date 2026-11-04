/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.nondraggable.debug;

import combatant.client.config.MainConfig;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.debug.RenderThread2DDebugRenderer;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontDebugStats;
import combatant.client.render.iris.IrisAaIntegration;
import combatant.client.util.logging.DebugMode;
import combatant.client.util.sound.SoundDebugStats;

/** Expensive, per-frame engine counters are shown only in the detailed debug modes. */
final class EngineDebugHudProducer {
    private EngineDebugHudProducer() {
    }

    static void append(DebugHudLineRegistry.LineSink lines) {
        DebugMode debugMode = MainConfig.get().getDebugMode();
        if (debugMode != DebugMode.ALL && debugMode != DebugMode.RENDER_THREAD) return;

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


        lines.add("§cCombatant Engine");
        lines.add("status:§aok");
        lines.add(msaa);
        lines.add("iris " + IrisAaIntegration.resolve(MainConfig.get().getAntialiasing3dMode()).shortLine());

        RenderThread2DDebugRenderer.Snapshot debug2d = RenderThread2DDebugRenderer.snapshot();
        lines.add(String.format("2d rt gui: frame=%d gui=%dx%d rects=%d err=%s",
                debug2d.guiStateFrame(),
                debug2d.guiWidth(),
                debug2d.guiHeight(),
                debug2d.guiStateRects(),
                debug2d.guiStateError() == null || debug2d.guiStateError().isBlank() ? "none" : debug2d.guiStateError()
        ));
        lines.add(String.format("2d rt imm: frame=%d fb=%dx%d draws=%d/%d v=%d i=%d err=%s",
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
        lines.add(String.format("ui batch: %s, last %d draws / %d batches",
                batchStats.isActive() ? "active" : "idle",
                batchStats.getLastDraws(),
                batchStats.getLastOrder()
        ));
        lines.add(String.format("ui batch frame: %d draws / %d batches",
                batchStats.getFrameDraws(),
                batchStats.getFrameBatches()
        ));
        lines.add(String.format("ui batch geom: v=%d, i=%d, pool=%d",
                batchStats.getLastVertices(),
                batchStats.getLastIndices(),
                batchStats.getPoolTotal()
        ));
        lines.add(String.format("ui batch frame geom: v=%d, i=%d",
                batchStats.getFrameVertices(),
                batchStats.getFrameIndices()
        ));
        if (batchStats.getLastError() != null && !batchStats.getLastError().isBlank()) {
            lines.add("ui batch warn: " + batchStats.getLastError());
        }

        lines.add(String.format("text msdf: ok=%d, fb=%d, attempts=%d",
                FontDebugStats.getMsdfSuccess(),
                FontDebugStats.getMsdfFallbacks(),
                FontDebugStats.getMsdfAttempts()
        ));
        if (!FontDebugStats.getLastMsdfFallback().isBlank()) {
            String reason = FontDebugStats.getLastMsdfFallbackReason();
            lines.add("text msdf last fb: " + FontDebugStats.getLastMsdfFallback()
                    + (reason.isBlank() ? "" : " (" + reason + ")"));
        }

        lines.add(String.format("wav(al): buffers %d/%d, sources %d/%d",
                SoundDebugStats.getBuffersAlive(),
                SoundDebugStats.getBuffersCreated(),
                SoundDebugStats.getSourcesAlive(),
                SoundDebugStats.getSourcesCreated()
        ));
        lines.add(String.format("wav(al): allocs=%d (buf=%d, src=%d)",
                SoundDebugStats.getBuffersCreated() + SoundDebugStats.getSourcesCreated(),
                SoundDebugStats.getBuffersCreated(),
                SoundDebugStats.getSourcesCreated()
        ));
        lines.add(String.format("wav(al): uploads=%d, bytes=%.2f MB",
                SoundDebugStats.getBufferUploads(),
                SoundDebugStats.getBufferBytes() / (1024.0 * 1024.0)
        ));
    }
}
