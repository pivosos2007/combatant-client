/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.misc.antiafk.AfkScheduler;
import combatant.client.features.module.modules.misc.antiafk.AfkScheduler.Action;
import combatant.client.util.screen.ClientScreen;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps idle-kick checks fed with small, irregular activity: a jump, an arm swing, a short look
 * around, a sidestep or a quick sneak, at a jittered interval. Real input from the player pushes the
 * next action a full interval away, so it never fights you while you play.
 */
@ModuleInfo(
        id = "antiafk",
        displayName = "AntiAFK",
        aliases = {"AFKKick", "AntiKick"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.antiafk.description")
public final class AntiAFK extends Module {

    private static final String ACTION_JUMP = "jump";
    private static final String ACTION_SWING = "swing";
    private static final String ACTION_LOOK = "look";
    private static final String ACTION_STRAFE = "strafe";
    private static final String ACTION_SNEAK = "sneak";
    private static final int SNEAK_TICKS = 3;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> intervalSeconds =
            num("antiAfkIntervalSeconds", "interval_seconds", 30, 5, 300);
    private final NumberValue<Integer> jitterPercent =
            num("antiAfkJitterPercent", "jitter_percent", 25, 0, 80);
    private final BooleanMapValue actions = group("antiAfkActions", "actions", defaultActions());
    private final NumberValue<Float> lookDegrees = visibleWhen(
            num("antiAfkLookDegrees", "look_degrees", 10.0f, 1.0f, 60.0f),
            () -> actions.get(ACTION_LOOK));
    private final NumberValue<Integer> strafeTicks = visibleWhen(
            num("antiAfkStrafeTicks", "strafe_ticks", 6, 2, 20),
            () -> actions.get(ACTION_STRAFE));
    private final BooleanValue resetOnInput = bool("antiAfkResetOnInput", "reset_on_input", true);

    private final AfkScheduler scheduler = new AfkScheduler(new Random());
    private final EnumSet<Action> enabledActions = EnumSet.noneOf(Action.class);

    private int jumpTicks;
    private int strafeLeft;
    private int strafeDirection = 1;
    private int sneakLeft;

    @Override
    public void onEnable() {
        scheduler.noteActivity();
        clearPending();
    }

    @Override
    public void onDisable() {
        clearPending();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            scheduler.noteActivity();
            clearPending();
            return;
        }
        // A screen means the player is there (or dead); hold the timer instead of acting under it.
        if (ClientScreen.current() != null || player.isDeadOrDying()) return;

        if (resetOnInput.get() && playerIsActive()) scheduler.noteActivity();

        scheduler.configure(intervalSeconds.get(), jitterPercent.get());
        refreshEnabledActions();
        Action action = scheduler.tick(enabledActions);
        if (action != null) perform(player, action);
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled()) return;
        if (jumpTicks > 0) {
            event.setJump(true);
            jumpTicks--;
        }
        if (strafeLeft > 0) {
            if (strafeDirection > 0) event.setLeft(true);
            else event.setRight(true);
            strafeLeft--;
        }
        if (sneakLeft > 0) {
            event.setSneak(true);
            sneakLeft--;
        }
    }

    private void perform(LocalPlayer player, Action action) {
        switch (action) {
            case JUMP -> jumpTicks = 1;
            case SWING -> player.swing(InteractionHand.MAIN_HAND);
            case LOOK -> lookAround(player);
            case STRAFE -> {
                strafeDirection = -strafeDirection;
                strafeLeft = strafeTicks.get();
            }
            case SNEAK -> sneakLeft = SNEAK_TICKS;
        }
    }

    private void lookAround(LocalPlayer player) {
        float range = lookDegrees.get();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        player.setYRot(player.getYRot() + (float) random.nextDouble(-range, range));
        player.setXRot(Mth.clamp(player.getXRot() + (float) random.nextDouble(-range / 3.0f, range / 3.0f), -90.0f, 90.0f));
    }

    private boolean playerIsActive() {
        var options = mc.options;
        return options.keyUp.isDown() || options.keyDown.isDown()
                || options.keyLeft.isDown() || options.keyRight.isDown()
                || options.keyJump.isDown() || options.keyShift.isDown()
                || options.keyAttack.isDown() || options.keyUse.isDown();
    }

    private void refreshEnabledActions() {
        enabledActions.clear();
        if (actions.get(ACTION_JUMP)) enabledActions.add(Action.JUMP);
        if (actions.get(ACTION_SWING)) enabledActions.add(Action.SWING);
        if (actions.get(ACTION_LOOK)) enabledActions.add(Action.LOOK);
        if (actions.get(ACTION_STRAFE)) enabledActions.add(Action.STRAFE);
        if (actions.get(ACTION_SNEAK)) enabledActions.add(Action.SNEAK);
    }

    private void clearPending() {
        jumpTicks = 0;
        strafeLeft = 0;
        sneakLeft = 0;
    }

    private static Map<String, Boolean> defaultActions() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(ACTION_JUMP, true);
        defaults.put(ACTION_SWING, true);
        defaults.put(ACTION_LOOK, true);
        defaults.put(ACTION_STRAFE, false);
        defaults.put(ACTION_SNEAK, false);
        return defaults;
    }
}
