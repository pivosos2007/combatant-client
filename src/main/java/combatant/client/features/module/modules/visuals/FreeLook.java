/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.util.input.KeyManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

@ModuleInfo(
        id = "freelook",
        displayName = "FreeLook",
        aliases = {"Perspective", "360View"},
        category = ModuleCategory.VISUALS,
        subcategory = ModuleSubcategory.COSMETIC,
        description = "module.freelook.description")
public final class FreeLook extends Module {
    private final Minecraft mc = Minecraft.getInstance();
    private final KeyBindValue freeLookKey = bind("freeLookKey", "free_look_key", "LEFT_ALT", BindMode.HOLD);
    private final NumberValue<Float> sensitivity = num("freeLookSensitivity", "sensitivity", 0.15f, 0.02f, 0.5f);
    private final EnumValue<Perspective> perspective =
            enumSetting("freeLookPerspective", "perspective", Perspective.BACK, Perspective.values());

    private CameraType previousPerspective;
    private float cameraYaw;
    private float cameraPitch;
    private boolean cameraActive;
    private String lastKeyCombo = "NONE";

    @Override
    public void onEnable() {
        cameraActive = false;
        previousPerspective = null;
        lastKeyCombo = "NONE";
        ensureBind();
    }

    @Override
    public void onDisable() {
        deactivateCamera();
        KeyManager.unregisterAll(bindingName());
        lastKeyCombo = "NONE";
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        ensureBind();

        refreshActivation();
    }

    public boolean isCameraActive() {
        return isEnabled() && cameraActive;
    }

    public boolean captureMouseInput() {
        if (!isEnabled()) return false;
        refreshActivation();
        return cameraActive;
    }

    public float cameraYaw() {
        return cameraYaw;
    }

    public float cameraPitch() {
        return cameraPitch;
    }

    public void turnCamera(double dx, double dy) {
        if (!isCameraActive()) return;
        cameraYaw += (float) dx * sensitivity.get();
        cameraPitch = Mth.clamp(cameraPitch + (float) dy * sensitivity.get(), -90.0F, 90.0F);
    }

    private void activateCamera(LocalPlayer player) {
        if (player == null || cameraActive) return;
        previousPerspective = mc.options.getCameraType();
        if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            cameraYaw = mc.gameRenderer.mainCamera().yRot();
            cameraPitch = mc.gameRenderer.mainCamera().xRot();
        } else {
            cameraYaw = player.getYRot();
            cameraPitch = player.getXRot();
        }
        if (mc.options.getCameraType().isFirstPerson()) {
            mc.options.setCameraType(perspective.get().cameraType());
        }
        cameraActive = true;
    }

    private void deactivateCamera() {
        if (!cameraActive) return;
        if (previousPerspective != null) {
            mc.options.setCameraType(previousPerspective);
        }
        cameraActive = false;
        previousPerspective = null;
    }


    private void refreshActivation() {
        ensureBind();
        LocalPlayer player = mc.player;
        Freecam freecam = Modules.get(Freecam.class);
        boolean canUse = player != null
                && mc.level != null
                && (freecam == null || !freecam.isEnabled())
                && !freeLookKey.isNone()
                && KeyManager.isHeld(bindingName());

        if (canUse) {
            if (!cameraActive) activateCamera(player);
            if (mc.options.getCameraType().isFirstPerson()) {
                mc.options.setCameraType(perspective.get().cameraType());
            }
        } else if (cameraActive) {
            deactivateCamera();
        }
    }

    private String bindingName() {
        return name() + ":" + freeLookKey.getName();
    }

    private void ensureBind() {
        String combo = freeLookKey.get();
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(lastKeyCombo)) return;

        String name = bindingName();
        KeyManager.unregisterAll(name);
        if (!freeLookKey.isNone()) KeyManager.registerCombo(name, combo);
        lastKeyCombo = combo;
    }

    public enum Perspective {
        BACK(CameraType.THIRD_PERSON_BACK),
        FRONT(CameraType.THIRD_PERSON_FRONT);

        private final CameraType cameraType;

        Perspective(CameraType cameraType) {
            this.cameraType = cameraType;
        }

        public CameraType cameraType() {
            return cameraType;
        }
    }
}
