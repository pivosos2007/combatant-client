/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.blit;

import combatant.client.render.engine.rhi.RhiCapabilities;

import java.util.Locale;

/** Pure copy/resolve policy. It selects a path; it never performs API calls. */
public final class RhiCopyPlanner {
    private RhiCopyPlanner() {
    }

    public static RhiCopyPath plan(RhiCopyRequest request, RhiCapabilities capabilities) {
        if (request == null || request.source() == null || request.destination() == null
                || request.source() == request.destination()
                || request.source().texture() == request.destination().texture()) {
            return RhiCopyPath.NO_OP;
        }
        if (request.materialConversion() || !request.sameFormat()) {
            return RhiCopyPath.SHADER_CONVERSION;
        }
        if (request.sourceSamples() > 1 && request.destinationSamples() == 1) {
            return RhiCopyPath.FRAMEBUFFER_RESOLVE;
        }
        if (request.sourceSamples() != request.destinationSamples()) {
            return RhiCopyPath.SHADER_CONVERSION;
        }
        if (request.scales() || request.filter() == RhiCopyRequest.Filter.LINEAR) {
            return RhiCopyPath.FRAMEBUFFER_BLIT;
        }
        if (copyImageRequested(capabilities) && capabilities != null && capabilities.copyImage()) {
            return RhiCopyPath.GL_COPY_IMAGE;
        }
        return RhiCopyPath.BACKEND_COPY;
    }

    /**
     * AUTO selects raw image copy for exact desktop texture copies when the active Mojang GL
     * context supports ARB_copy_image. Known problematic mobile/software renderer families retain
     * Mojang's framebuffer blit. FORCE bypasses the renderer safety policy but never the capability
     * check; OFF always uses the baseline.
     */
    private static boolean copyImageRequested(RhiCapabilities capabilities) {
        String mode = System.getProperty("combatant.rhi.copy.glCopyImage", "auto")
                .trim().toLowerCase(Locale.ROOT);
        if (mode.equals("off") || mode.equals("false") || mode.equals("baseline")) return false;
        if (mode.equals("force") || mode.equals("on") || mode.equals("true")) return true;
        return capabilities != null && capabilities.copyImageAutoSafe();
    }
}
