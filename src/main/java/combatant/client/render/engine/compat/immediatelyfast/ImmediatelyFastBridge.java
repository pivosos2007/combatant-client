/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.compat.immediatelyfast;

import net.raphimc.immediatelyfast.ImmediatelyFast;
import net.raphimc.immediatelyfast.feature.core.ImmediatelyFastConfig;

enum ImmediatelyFastBridge {
    ;

    static ImmediatelyFastRuntimeSnapshot snapshot(String version) {
        ImmediatelyFastConfig config = ImmediatelyFast.config;
        if (config == null) {
            return defaults(version, "config not initialized yet");
        }

        boolean enhancedBatching = config.enhanced_batching;
        boolean fastTextLookup = config.fast_text_lookup;
        boolean fontAtlasResizing = config.font_atlas_resizing;
        boolean skipTextSorting = config.skip_text_translucency_sorting;
        boolean signTextBuffering = config.experimental_sign_text_buffering;
        boolean framebufferSwitching = config.avoid_redundant_framebuffer_switching;
        boolean appleUploadFix = config.fix_slow_buffer_upload_on_apple_gpu;

        return new ImmediatelyFastRuntimeSnapshot(
                true,
                version,
                framebufferSwitching,
                enhancedBatching,
                fastTextLookup || fontAtlasResizing || skipTextSorting || signTextBuffering,
                enhancedBatching || signTextBuffering || appleUploadFix,
                true,
                "ok"
        );
    }

    static ImmediatelyFastRuntimeSnapshot defaults(String version, String status) {
        return new ImmediatelyFastRuntimeSnapshot(
                true,
                version,
                true,
                true,
                true,
                true,
                false,
                status == null || status.isBlank() ? "using default assumptions" : status
        );
    }
}
