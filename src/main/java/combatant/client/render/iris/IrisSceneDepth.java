/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.irisshaders.iris.targets.RenderTargets;

/**
 * Read-only views of the resolved Iris scene depth used by Combatant postprocess passes.
 */
public enum IrisSceneDepth {
    ;
    private static GpuTexture mainTexture;
    private static GpuTexture preTranslucentTexture;
    private static GpuTexture preHandTexture;
    private static GpuTextureView mainView;
    private static GpuTextureView preTranslucentView;
    private static GpuTextureView preHandView;
    private static boolean valid;

    public static void capture(RenderTargets targets) {
        RenderSystem.assertOnRenderThread();
        if (targets == null) {
            valid = false;
            return;
        }
        mainView = updateView(mainTexture, mainView, targets.getDepthTexture());
        mainTexture = targets.getDepthTexture();
        preTranslucentView = updateView(preTranslucentTexture, preTranslucentView, targets.getDepthTextureNoTranslucents());
        preTranslucentTexture = targets.getDepthTextureNoTranslucents();
        preHandView = updateView(preHandTexture, preHandView, targets.getDepthTextureNoHand());
        preHandTexture = targets.getDepthTextureNoHand();
        valid = mainView != null;
    }

    public static void resetFrame() {
        valid = false;
    }

    public static void shutdown() {
        valid = false;
        close(mainView);
        close(preTranslucentView);
        close(preHandView);
        mainTexture = null;
        preTranslucentTexture = null;
        preHandTexture = null;
        mainView = null;
        preTranslucentView = null;
        preHandView = null;
    }

    public static boolean isValid() {
        return valid && mainView != null;
    }

    public static GpuTextureView mainDepthView() {
        return isValid() ? mainView : null;
    }

    public static GpuTextureView preTranslucentDepthView() {
        return isValid() ? preTranslucentView : null;
    }

    public static GpuTextureView preHandDepthView() {
        return isValid() ? preHandView : null;
    }

    private static GpuTextureView updateView(GpuTexture previousTexture,
                                             GpuTextureView previousView,
                                             GpuTexture nextTexture) {
        if (nextTexture == previousTexture && previousView != null && !previousView.isClosed()) {
            return previousView;
        }
        close(previousView);
        if (nextTexture == null || nextTexture.isClosed()) {
            return null;
        }
        return RenderSystem.getDevice().createTextureView(nextTexture);
    }

    private static void close(GpuTextureView view) {
        if (view != null && !view.isClosed()) {
            view.close();
        }
    }
}
