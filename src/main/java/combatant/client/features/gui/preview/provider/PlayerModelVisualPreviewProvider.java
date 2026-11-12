/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview.provider;

import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.preview.*;
import combatant.client.features.gui.preview.render.VisualPreviewItemRenderer;
import combatant.client.features.playeranimator.PlayerRigInstance;
import combatant.client.features.module.modules.visuals.PlayerModel;
import combatant.client.features.playermodel.PlayerModels;
import combatant.client.features.playermodel.PlayerModelRenderState;
import combatant.client.features.playeranimator.PlayerAnimator;
import combatant.client.features.playeranimator.PlayerRigRenderState;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.TickDelta;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;

public final class PlayerModelVisualPreviewProvider implements VisualPreviewProvider {
    private final PlayerModel module;
    private final PlayerModel.Target target;
    private final PlayerModelSelector selector;

    public PlayerModelVisualPreviewProvider(PlayerModel module) {
        this(module, PlayerModel.Target.SELF);
    }

    public PlayerModelVisualPreviewProvider(PlayerModel module, PlayerModel.Target target) {
        this.module = module;
        this.target = target;
        this.selector = new PlayerModelSelector(module, target);
    }
    @Override public String id() { return "player_model:" + target.key(); }
    @Override public boolean requiresWorld() { return true; }
    @Override public String title() { return module.getDisplayName() + " — " + target.label(); }
    @Override public VisualPreviewControlMode controlMode() { return VisualPreviewControlMode.OBJECT_ROTATE; }
    @Override public List<Setting> settings() {
        String prefix = target == PlayerModel.Target.SELF ? "" : target.key() + "_";
        return module.getSettings().stream().filter(setting -> {
            String id = setting.getId();
            if (id.equals("texture_resolution") || id.equals("texture_filtering") || id.equals("preview_pose"))
                return true;
            if (target == PlayerModel.Target.SELF)
                return !id.equals("model") && !id.equals("replacement") && !id.equals("reload")
                        && java.util.Arrays.stream(PlayerModel.Target.values())
                            .filter(other -> other != PlayerModel.Target.SELF)
                            .noneMatch(other -> id.startsWith(other.key() + "_"));
            return id.startsWith(prefix) && !id.equals(prefix + "model")
                    && !id.equals(prefix + "replacement");
        }).toList();
    }
    @Override public float leftPanelWidth(float width) { return Math.min(440f, width * 0.35f); }
    @Override public boolean mousePressed(float x, float y, int button) { return selector.mousePressed(x, y, button); }
    @Override public void mouseReleased(float x, float y, int button) { selector.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(float x, float y, double amount) { return selector.mouseScrolled(x, y, amount); }
    @Override public void close() { selector.close(); }

    @Override
    public void renderSubject(VisualPreviewSceneContext context) {
        var mc = context.minecraft();
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null) return;
        PlayerModels.Ready asset = PlayerModels.prepare(module, target);
        VisualPreviewItemRenderer.renderScene(context, true, 0.68f, (matrices, storage) -> {
            var dispatcher = mc.getEntityRenderDispatcher();
            if (!(dispatcher.extractEntity(mc.player, context.tickDelta()) instanceof AvatarRenderState state)) return;
            state.bodyRot = 180f;
            state.nameTag = null;
            state.lightCoords = 0xF000F0;
            applyPreviewPose(state);
            ((PlayerModelRenderState) state).combatant$setReplacement(null);
            if (module.replacement(target).get() && asset != null) {
                PlayerRigInstance rig = PlayerAnimator.animate(mc.player, state, context.tickDelta(),
                        TickDelta.frameDeltaSeconds(), "Hybrid", 1f);
                ((PlayerRigRenderState) state).combatant$setPlayerRig(rig);
                ((PlayerModelRenderState) state).combatant$setReplacement(PlayerModels.snapshot(asset, rig, module, target));
            }
            CameraRenderState camera = new CameraRenderState();
            camera.pos = Vec3.ZERO;
            camera.orientation = new Quaternionf();
            matrices.translate(0f, -1f, 0f);
            dispatcher.getRenderer(state).submit(state, matrices, storage, camera);
        });
    }

    private void applyPreviewPose(AvatarRenderState state) {
        String pose = module.previewPose.get();
        if (pose.equals("Live")) return;
        state.isCrouching = pose.equals("Sneak");
        state.isVisuallySwimming = pose.equals("Swim");
        state.swimAmount = state.isVisuallySwimming ? 1f : 0f;
        state.isFallFlying = false;
        state.isPassenger = false;
        state.pose = state.isVisuallySwimming ? Pose.SWIMMING : state.isCrouching ? Pose.CROUCHING : Pose.STANDING;
        state.walkAnimationSpeed = pose.equals("Walk") || pose.equals("Swim") ? 0.65f : 0f;
        state.walkAnimationPos = state.ageInTicks * 0.6f;
        state.xRot = state.yRot = 0;
    }

    @Override
    public void renderOverlay(VisualPreviewSceneContext context, Renderer2D renderer) {
        selector.render(context);
    }
}
