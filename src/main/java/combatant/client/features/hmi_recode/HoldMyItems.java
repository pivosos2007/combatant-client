/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode;

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.features.hmi_recode.render.HmiModelCommand;
import combatant.client.features.hmi_recode.render.HmiTransformCommand;
import combatant.client.features.hmi_recode.script.HmiScriptRuntime;
import combatant.client.render.helpers.TickDelta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public enum HoldMyItems {
    ;
    private static final HmiScriptRuntime SCRIPTS = new HmiScriptRuntime();
    private static final ThreadLocal<Deque<RenderScope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> REPLAY_DEPTH = ThreadLocal.withInitial(() -> 0);

    private static String previousMainItem = "minecraft:air";
    private static String previousOffItem = "minecraft:air";
    private static float previousMainSwing;
    private static float previousOffSwing;
    private static int swingCount;
    private static volatile boolean active;
    private static ReplayData lastMainReplay;
    private static ReplayData lastOffReplay;

    public static synchronized void activate() {
        if (active) return;
        resetTransientState();
        SCRIPTS.close();
        active = true;
    }

    public static synchronized void deactivate() {
        active = false;
        SCOPES.remove();
        REPLAY_DEPTH.remove();
        resetTransientState();
        SCRIPTS.close();
    }

    public static synchronized void invalidateScripts() {
        if (!active) return;
        lastMainReplay = null;
        lastOffReplay = null;
        SCRIPTS.invalidate();
    }

    public static boolean beginReplayPass() {
        if (!active) return false;
        REPLAY_DEPTH.set(REPLAY_DEPTH.get() + 1);
        return true;
    }

    public static void endReplayPass() {
        int depth = REPLAY_DEPTH.get();
        if (depth <= 1) {
            REPLAY_DEPTH.remove();
        } else {
            REPLAY_DEPTH.set(depth - 1);
        }
    }

    private static boolean isReplayPass() {
        return REPLAY_DEPTH.get() > 0;
    }

    private static void resetTransientState() {
        previousMainItem = "minecraft:air";
        previousOffItem = "minecraft:air";
        previousMainSwing = 0.0f;
        previousOffSwing = 0.0f;
        swingCount = 0;
        lastMainReplay = null;
        lastOffReplay = null;
    }

    public static void beginHandRender(
            AbstractClientPlayer rawPlayer,
            float tickDelta,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack matrices,
            MotionSettings motionSettings
    ) {
        if (!active) return;
        if (!(rawPlayer instanceof LocalPlayer player)) return;

        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean replay = isReplayPass();
        ReplayData replayData = replay ? (mainHand ? lastMainReplay : lastOffReplay) : null;

        String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        boolean mainSwitch = false;
        boolean offSwitch = false;
        if (!replay) {
            mainSwitch = mainHand && !itemId.equals(previousMainItem);
            offSwitch = !mainHand && !itemId.equals(previousOffItem);
            if (mainHand) previousMainItem = itemId; else previousOffItem = itemId;

            if (mainHand) {
                if (swingProgress + 0.25f < previousMainSwing) swingCount++;
                previousMainSwing = swingProgress;
            } else {
                if (swingProgress + 0.25f < previousOffSwing) swingCount++;
                previousOffSwing = swingProgress;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        boolean blockBreaking = mc.gameMode != null && mc.gameMode.isDestroying();
        MotionSettings tuning = motionSettings != null ? motionSettings : MotionSettings.DEFAULT;
        RenderScope scope = new RenderScope(
                player,
                tickDelta,
                hand,
                item,
                swingProgress,
                previousMainSwing,
                previousOffSwing,
                equipProgress,
                mainHand,
                arm == HumanoidArm.RIGHT,
                mainSwitch,
                offSwitch,
                blockBreaking,
                replay ? 0.0f : Math.min(0.05f, Math.max(0.0f, TickDelta.frameDeltaSeconds())),
                swingCount,
                replay,
                replayData,
                tuning
        );
        SCOPES.get().push(scope);
        if (replay) {
            scope.handPoseResult = replayData != null ? replayData.handPose() : null;
            applyResult(scope.handPoseResult, matrices, scope, false);
        } else {
            scope.handPoseResult = apply(HmiScriptKind.HAND_POSE, scope.scriptContext(), matrices, scope);
        }
    }

    public static void endHandRender() {
        if (!active) return;
        Deque<RenderScope> stack = SCOPES.get();
        if (stack.isEmpty()) return;

        RenderScope finished = stack.pop();
        if (!finished.replay) {
            ReplayData replay = new ReplayData(
                    finished.handPoseResult,
                    finished.handRelativeResult,
                    finished.itemPoseResult,
                    finished.itemModelResult
            );
            if (finished.mainHand()) lastMainReplay = replay; else lastOffReplay = replay;
        }
        if (stack.isEmpty()) SCOPES.remove();
    }

    public static void applyHandRelative(PoseStack matrices) {
        if (!active) return;
        RenderScope scope = current();
        if (scope == null) return;

        if (scope.replay) {
            scope.handRelativeResult = scope.replayData != null ? scope.replayData.handRelative() : null;
            applyResult(scope.handRelativeResult, matrices, scope, false);
        } else {
            scope.handRelativeResult = apply(
                    HmiScriptKind.HAND_RELATIVE_POSE,
                    scope.scriptContext(),
                    matrices,
                    scope
            );
        }
    }

    public static void applyItemPose(ItemStack renderedItem, PoseStack matrices) {
        if (!active) return;
        RenderScope scope = current();
        if (scope == null) return;
        RenderScope itemScope = renderedItem == scope.item() ? scope : scope.withItem(renderedItem);

        if (scope.replay) {
            scope.itemPoseResult = scope.replayData != null ? scope.replayData.itemPose() : null;
            scope.itemModelResult = scope.replayData != null ? scope.replayData.itemModel() : null;
            applyResult(scope.itemPoseResult, matrices, itemScope, false);
            scope.modelCommands = scope.itemModelResult != null ? scope.itemModelResult.modelCommands() : List.of();
            return;
        }

        scope.itemPoseResult = apply(HmiScriptKind.ITEM_POSE, itemScope.scriptContext(), matrices, itemScope);
        scope.itemModelResult = SCRIPTS.execute(HmiScriptKind.ITEM_MODEL, itemScope.scriptContext());
        playSounds(scope.itemModelResult, itemScope);
        scope.modelCommands = scope.itemModelResult.modelCommands();
    }

    public static boolean hasModelCommands() {
        if (!active) return false;
        RenderScope scope = current();
        return scope != null && scope.modelCommands != null && !scope.modelCommands.isEmpty();
    }

    public static List<HmiModelCommand> snapshotModelCommands() {
        if (!active) return List.of();
        RenderScope scope = current();
        if (scope == null || scope.modelCommands == null || scope.modelCommands.isEmpty()) return List.of();
        return List.copyOf(scope.modelCommands);
    }

    public static void applyModelCommands(PoseStack matrices, int quadIndex) {
        if (!active) return;
        RenderScope scope = current();
        if (scope == null || scope.modelCommands == null) return;
        HmiModelCommand.apply(scope.modelCommands, quadIndex, matrices);
    }

    private static HmiScriptRuntime.Result apply(
            HmiScriptKind kind,
            java.util.Map<String, Object> context,
            PoseStack matrices,
            RenderScope scope
    ) {
        HmiScriptRuntime.Result result = SCRIPTS.execute(kind, context);
        applyResult(result, matrices, scope, true);
        return result;
    }

    private static void applyResult(
            HmiScriptRuntime.Result result,
            PoseStack matrices,
            RenderScope scope,
            boolean withSounds
    ) {
        if (result == null) return;
        for (HmiTransformCommand command : result.commands()) command.apply(matrices);
        if (withSounds) playSounds(result, scope);
        if (!result.modelCommands().isEmpty()) scope.modelCommands = result.modelCommands();
    }

    private static void playSounds(HmiScriptRuntime.Result result, RenderScope scope) {
        if (result == null) return;
        for (var sound : result.sounds()) sound.play(scope.player());
    }

    private static RenderScope current() {
        Deque<RenderScope> stack = SCOPES.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public record MotionSettings(
            float swingStrength,
            float swordSwingStrength,
            float offhandSwingStrength,
            float movementStrength,
            float lookStrength,
            float switchStrength,
            float useStrength,
            float impactStrength,
            boolean replaceSwing,
            String swingStyle
    ) {
        public static final MotionSettings DEFAULT =
                new MotionSettings(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, false, "HMI");

        public MotionSettings {
            swingStrength = finiteNonNegative(swingStrength, 1.0f);
            swordSwingStrength = finiteNonNegative(swordSwingStrength, 1.0f);
            offhandSwingStrength = finiteNonNegative(offhandSwingStrength, 1.0f);
            movementStrength = finiteNonNegative(movementStrength, 1.0f);
            lookStrength = finiteNonNegative(lookStrength, 1.0f);
            switchStrength = finiteNonNegative(switchStrength, 1.0f);
            useStrength = finiteNonNegative(useStrength, 1.0f);
            impactStrength = finiteNonNegative(impactStrength, 1.0f);
            swingStyle = swingStyle == null || swingStyle.isBlank() ? "HMI" : swingStyle;
        }

        private static float finiteNonNegative(float value, float fallback) {
            if (!Float.isFinite(value)) return fallback;
            return Math.max(0.0f, value);
        }
    }

    private record ReplayData(
            HmiScriptRuntime.Result handPose,
            HmiScriptRuntime.Result handRelative,
            HmiScriptRuntime.Result itemPose,
            HmiScriptRuntime.Result itemModel
    ) {
    }

    public static final class RenderScope {
        private final LocalPlayer player;
        private final float tickDelta;
        private final InteractionHand hand;
        private final ItemStack item;
        private final float swingProgress;
        private final float mainHandSwingProgress;
        private final float offHandSwingProgress;
        private final float equipProgress;
        private final boolean mainHand;
        private final boolean rightArm;
        private final boolean mainHandSwitchEvent;
        private final boolean offHandSwitchEvent;
        private final boolean blockBreaking;
        private final float deltaSeconds;
        private final int swingCount;
        private final boolean replay;
        private final ReplayData replayData;
        private final MotionSettings motionSettings;
        private List<HmiModelCommand> modelCommands = List.of();
        private HmiScriptRuntime.Result handPoseResult;
        private HmiScriptRuntime.Result handRelativeResult;
        private HmiScriptRuntime.Result itemPoseResult;
        private HmiScriptRuntime.Result itemModelResult;
        private Map<String, Object> scriptContext;

        private RenderScope(LocalPlayer player, float tickDelta, InteractionHand hand, ItemStack item, float swingProgress,
                            float mainHandSwingProgress, float offHandSwingProgress, float equipProgress,
                            boolean mainHand, boolean rightArm, boolean mainHandSwitchEvent,
                            boolean offHandSwitchEvent, boolean blockBreaking, float deltaSeconds, int swingCount,
                            boolean replay, ReplayData replayData, MotionSettings motionSettings) {
            this.player = player;
            this.tickDelta = tickDelta;
            this.hand = hand;
            this.item = item;
            this.swingProgress = swingProgress;
            this.mainHandSwingProgress = mainHandSwingProgress;
            this.offHandSwingProgress = offHandSwingProgress;
            this.equipProgress = equipProgress;
            this.mainHand = mainHand;
            this.rightArm = rightArm;
            this.mainHandSwitchEvent = mainHandSwitchEvent;
            this.offHandSwitchEvent = offHandSwitchEvent;
            this.blockBreaking = blockBreaking;
            this.deltaSeconds = deltaSeconds;
            this.swingCount = swingCount;
            this.replay = replay;
            this.replayData = replayData;
            this.motionSettings = motionSettings;
        }

        private RenderScope withItem(ItemStack replacement) {
            RenderScope scope = new RenderScope(player, tickDelta, hand, replacement, swingProgress,
                    mainHandSwingProgress, offHandSwingProgress, equipProgress, mainHand,
                    rightArm, mainHandSwitchEvent, offHandSwitchEvent, blockBreaking, deltaSeconds, swingCount,
                    replay, replayData, motionSettings);
            scope.modelCommands = modelCommands;
            return scope;
        }

        private Map<String, Object> scriptContext() {
            if (scriptContext == null) {
                scriptContext = HmiContextFactory.renderContext(this);
            }
            return scriptContext;
        }

        public LocalPlayer player() { return player; }
        public float tickDelta() { return tickDelta; }
        public InteractionHand hand() { return hand; }
        public ItemStack item() { return item; }
        public float swingProgress() { return swingProgress; }
        public float rawSwingProgress() { return swingProgress; }
        public float mainHandSwingProgress() { return motionSettings.replaceSwing() ? 0.0f : mainHandSwingProgress; }
        public float offHandSwingProgress() { return motionSettings.replaceSwing() ? 0.0f : offHandSwingProgress; }
        public float scriptSwingProgress() { return motionSettings.replaceSwing() ? 0.0f : swingProgress; }
        public float equipProgress() { return equipProgress; }
        public boolean mainHand() { return mainHand; }
        public boolean rightArm() { return rightArm; }
        public boolean mainHandSwitchEvent() { return mainHandSwitchEvent; }
        public boolean offHandSwitchEvent() { return offHandSwitchEvent; }
        public boolean blockBreaking() { return blockBreaking; }
        public float deltaSeconds() { return deltaSeconds; }
        public int swingCount() { return swingCount; }
        public MotionSettings motionSettings() { return motionSettings; }
    }
}
