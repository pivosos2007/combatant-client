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
        if (copyImageRequested() && capabilities != null && capabilities.copyImage()) {
            return RhiCopyPath.GL_COPY_IMAGE;
        }
        return RhiCopyPath.BACKEND_COPY;
    }

    /**
     * AUTO intentionally stays on Mojang's measured baseline. A benchmark run can opt into the
     * candidate with {@code -Dcombatant.rhi.copy.glCopyImage=force}; no extension-only auto-enable.
     */
    private static boolean copyImageRequested() {
        String mode = System.getProperty("combatant.rhi.copy.glCopyImage", "auto")
                .trim().toLowerCase(Locale.ROOT);
        return mode.equals("force") || mode.equals("on") || mode.equals("true");
    }
}
