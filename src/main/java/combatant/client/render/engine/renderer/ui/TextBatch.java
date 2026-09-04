/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.render.engine.text.GlyphFont;
import combatant.client.render.engine.text.backend.TextPlacementMode;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;

public final class TextBatch {
    public String label;
    public GlyphFont font;
    public RenderPipeline pipeline;
    public TextPlacementMode placement;
    public UiClipSnapshot clipSnapshot = UiClipSnapshot.NONE;
    public UiScissorSnapshot scissorSnapshot = UiScissorSnapshot.NONE;
    public MeshBuilder mesh;

    public void begin(String label, GlyphFont font, RenderPipeline pipeline,
               TextPlacementMode placement, UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        this.label = label != null ? label : "Combatant UI Text Batch";
        this.font = font;
        this.pipeline = pipeline;
        this.placement = placement != null ? placement : TextPlacementMode.UI;
        this.scissorSnapshot = scissorSnapshot != null ? scissorSnapshot : UiScissorSnapshot.NONE;
        this.clipSnapshot = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        if (mesh == null) {
            mesh = new MeshBuilder(pipeline);
        } else if (mesh.isBuilding()) {
            mesh.end();
        }
        mesh.begin();
    }

    public boolean canMerge(GlyphFont font, RenderPipeline pipeline, TextPlacementMode placement,
                            UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        return this.font == font
                && this.pipeline == pipeline
                && this.placement == (placement != null ? placement : TextPlacementMode.UI)
                && this.scissorSnapshot.id() == scissorSnapshot.id()
                && this.clipSnapshot.id() == clipSnapshot.id();
    }

    public void append(MeshBuilder source) {
        if (source == null) return;
        if (source.isBuilding()) source.end();
        if (source.getIndicesCount() <= 0) return;
        if (mesh == null || !mesh.isBuilding()) {
            begin(label, font, pipeline, placement, scissorSnapshot, clipSnapshot);
        }
        mesh.appendMesh(source);
    }

    public boolean isEmpty() {
        if (mesh == null) return true;
        if (mesh.isBuilding()) mesh.end();
        return mesh.getIndicesCount() <= 0;
    }
}
