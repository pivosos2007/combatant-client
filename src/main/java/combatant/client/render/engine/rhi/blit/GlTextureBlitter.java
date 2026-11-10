/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.blit;

import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.mojang.renderpearl.api.textures.GpuTexture;
import combatant.client.render.engine.rhi.RhiCapabilities;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.backend.gl.GlValidation;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;

/** Modern OpenGL copy candidate layered over the unchanged Blaze3D baseline. */
public final class GlTextureBlitter implements TextureBlitter {
    private final RhiStats stats;
    private final Blaze3dTextureBlitter baseline;

    public GlTextureBlitter(RhiStats stats) {
        this.stats = stats;
        this.baseline = new Blaze3dTextureBlitter(stats);
    }

    @Override
    public boolean copy(RhiCopyRequest request) {
        RhiCopyPath path = RhiCopyPlanner.plan(request, RhiCapabilities.current());
        if (path == RhiCopyPath.NO_OP) return true;
        if (path != RhiCopyPath.GL_COPY_IMAGE) return baseline.copy(request);

        GpuTexture source = request.source().texture();
        GpuTexture destination = request.destination().texture();
        if (!(source instanceof GlTexture sourceGl) || !(destination instanceof GlTexture destinationGl)) {
            return baseline.copy(request);
        }
        if ((source.usage() & GpuTexture.USAGE_COPY_SRC) == 0
                || (destination.usage() & GpuTexture.USAGE_COPY_DST) == 0) return false;
        RhiCopyRequest.Region src = request.sourceRegion();
        RhiCopyRequest.Region dst = request.destinationRegion();
        if (src == null || dst == null || request.scales()) return false;

        if (GlValidation.errorsEnabled()) GlStateManager.clearGlErrors();
        // Multisampled textures must be addressed by GL_TEXTURE_2D_MULTISAMPLE, never GL_TEXTURE_2D.
        int sourceTarget = request.sourceSamples() > 1 ? GL32C.GL_TEXTURE_2D_MULTISAMPLE : GL11C.GL_TEXTURE_2D;
        int destinationTarget = request.destinationSamples() > 1 ? GL32C.GL_TEXTURE_2D_MULTISAMPLE : GL11C.GL_TEXTURE_2D;
        GL43C.glCopyImageSubData(
                sourceGl.glId(), sourceTarget, request.source().baseMipLevel(), src.x(), src.y(), 0,
                destinationGl.glId(), destinationTarget, request.destination().baseMipLevel(), dst.x(), dst.y(), 0,
                src.width(), src.height(), 1
        );
        if (GlValidation.errorsEnabled() && GlStateManager._getError() != 0) return false;
        stats.textureFastCopy();
        stats.textureGlCopyImage();
        return true;
    }
}
