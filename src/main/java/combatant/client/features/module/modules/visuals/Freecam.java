/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.LightmapModifyEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.util.entity.FreecamEntity;

import java.util.Map;

//todo Description
@ModuleInfo(id = "freecam", displayName = "Freecam", aliases = {"camera", "spectator"}, category = ModuleCategory.VISUALS)
public class Freecam extends Module {

    private static final String SETTING_TOGGLES = "toggles";
    private static final String SETTING_HORIZONTAL_SPEED = "horizontal_speed";
    private static final String SETTING_VERTICAL_SPEED = "vertical_speed";
    private static final String SETTING_TOGGLE_INPUT = "toggle_input";
    private final BooleanMapValue toggles = group(
            "freecamToggles",
            SETTING_TOGGLES,
            Map.of(
                    "Freeze player", true,
                    "Allow interact", false,
                    "Render hand", false,
                    "Disable on damage", true,
                    "Use FullBright", false
            )
    );
    private final NumberValue<Double> horizontalSpeed =
            num("horizontalSpeed", SETTING_HORIZONTAL_SPEED, 0.8, 0.1, 5.0);
    private final NumberValue<Double> verticalSpeed =
            num("verticalSpeed", SETTING_VERTICAL_SPEED, 0.8, 0.1, 5.0);
    public CameraType prevPerspective;
    public FreecamEntity camEntity;

    public float camYaw;
    public float camPitch;

    public Vec3 camPos = Vec3.ZERO;
    public Vec3 camPosPrev = Vec3.ZERO;
    private boolean cameraInput = true;

    {
        setDefaultBind("F4");
        action(SETTING_TOGGLE_INPUT, "H", BindMode.PRESS).hudToggle(() -> !cameraInput);
    }

    public boolean isCameraInput() {
        return cameraInput;
    }


    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            toggle();
            return;
        }

        prevPerspective = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);

        camYaw = mc.player.getYRot();
        camPitch = mc.player.getXRot();

        camPos = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        camPosPrev = camPos;

        camEntity = new FreecamEntity(mc.level);
        syncCameraEntity(camPos, camPos);
        mc.setCameraEntity(camEntity);
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (prevPerspective != null) {
            mc.options.setCameraType(prevPerspective);
        }
        if (mc.player != null && mc.getCameraEntity() == camEntity) {
            mc.setCameraEntity(mc.player);
        }
        camEntity = null;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (isActionPressedOnce(SETTING_TOGGLE_INPUT)) {
            cameraInput = !cameraInput;
            notifyInputMode(cameraInput);
        }
        tickCameraMovement();
    }

    private void notifyInputMode(boolean cameraInput) {
        String msg = cameraInput
                ? "Freecam input: Camera"
                : "Freecam input: Player";

        Notifier.info(msg);
    }

    public boolean freezePlayer() {
        return isEnabled() && toggles.get("Freeze player");
    }

    public boolean allowInteract() {
        return isEnabled() && toggles.get("Allow interact");
    }

    public boolean renderHand() {
        return toggles.get("Render hand");
    }

    public boolean disableOnDamage() {
        return toggles.get("Disable on damage");
    }

    public boolean useFullBright() {
        return isEnabled() && toggles.get("Use FullBright");
    }

    @EventHandler
    private void onLightmapState(LightmapModifyEvent event) {
        if (useFullBright()) {
            event.raiseMinimumLight(1.0f);
        }
    }

    private void tickCameraMovement() {
        if (camEntity == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        camPosPrev = camPos;
        if (!cameraInput) {
            syncCameraEntity(camPos, camPosPrev);
            return;
        }

        var o = mc.options;

        double forward = 0;
        double strafe = 0;
        double vertical = 0;

        if (o.keyUp.isDown()) forward += 1;
        if (o.keyDown.isDown()) forward -= 1;

        if (o.keyLeft.isDown()) strafe += 1;
        if (o.keyRight.isDown()) strafe -= 1;

        if (o.keyJump.isDown()) vertical += 1;
        if (o.keyShift.isDown()) vertical -= 1;

        if (forward == 0 && strafe == 0 && vertical == 0) {
            syncCameraEntity(camPos, camPosPrev);
            return;
        }

        double yawRad = Math.toRadians(camYaw);

        Vec3 forwardVec = new Vec3(
                -Math.sin(yawRad),
                0,
                Math.cos(yawRad)
        );

        Vec3 rightVec = new Vec3(
                Math.cos(yawRad),
                0,
                Math.sin(yawRad)
        );

        Vec3 move = Vec3.ZERO
                .add(forwardVec.scale(forward))
                .add(rightVec.scale(strafe));

        if (move.lengthSqr() != 0) {
            double speed = hSpeed();
            if (o.keySprint.isDown()) speed *= 2.0;
            move = move.normalize().scale(speed);
        }

        move = move.add(0, vertical * vSpeed(), 0);

        camPos = camPos.add(move);
        syncCameraEntity(camPos, camPosPrev);
    }

    public Vec3 getCameraPos(float tickDelta) {
        float t = Mth.clamp(tickDelta, 0.0f, 1.0f);
        return new Vec3(
                Mth.lerp(t, camPosPrev.x, camPos.x),
                Mth.lerp(t, camPosPrev.y, camPos.y),
                Mth.lerp(t, camPosPrev.z, camPos.z)
        );
    }

    public void syncCameraEntity(Vec3 current, Vec3 previous) {
        if (camEntity == null) return;

        camEntity.xo = previous.x;
        camEntity.yo = previous.y;
        camEntity.zo = previous.z;
        camEntity.xOld = previous.x;
        camEntity.yOld = previous.y;
        camEntity.zOld = previous.z;
        camEntity.absSnapTo(current.x, current.y, current.z);
        camEntity.setYRot(camYaw);
        camEntity.setXRot(camPitch);
        camEntity.setDeltaMovement(Vec3.ZERO);
    }

    public double hSpeed() {
        return horizontalSpeed.get();
    }

    public double vSpeed() {
        return verticalSpeed.get();
    }

}
