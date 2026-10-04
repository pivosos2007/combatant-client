/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;

/**
 * Holds a movement key for you. Handy for long walks, tunnel digging and AFK farms. It hops
 * one-block obstacles, and switches itself off when the world changes, you take damage, or your
 * health drops under a threshold, so it never walks you through a portal or into a fight on its own.
 */
@ModuleInfo(
        id = "autowalk",
        displayName = "AutoWalk",
        aliases = {"AutoForward", "AutoMove"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.BASIC,
        description = "module.autowalk.description")
public final class AutoWalk extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Heading> heading =
            enumSetting("autoWalkHeading", "heading", Heading.FORWARD, Heading.values());
    private final BooleanValue sprint = visibleWhen(
            bool("autoWalkSprint", "sprint", true), () -> heading.get() == Heading.FORWARD);
    private final BooleanValue jumpObstacles = bool("autoWalkJumpObstacles", "jump_obstacles", true);
    private final BooleanValue stopOnDamage = bool("autoWalkStopOnDamage", "stop_on_damage", true);
    private final NumberValue<Integer> stopBelowHealth =
            num("autoWalkStopBelowHealth", "stop_below_health", 0, 0, 20);

    private ClientLevel level;
    private float lastHealth = -1.0f;

    @Override
    public void onEnable() {
        level = mc.level;
        lastHealth = mc.player == null ? -1.0f : mc.player.getHealth();
    }

    @Override
    public void onDisable() {
        level = null;
        lastHealth = -1.0f;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            stop("notification.autowalk.stopped_world");
            return;
        }
        if (level != mc.level) {
            stop("notification.autowalk.stopped_world");
            return;
        }

        float health = player.getHealth();
        if (stopOnDamage.get() && lastHealth >= 0.0f && health < lastHealth - 0.01f) {
            stop("notification.autowalk.stopped_damage");
            return;
        }
        lastHealth = health;

        int floor = stopBelowHealth.get();
        if (floor > 0 && health + player.getAbsorptionAmount() <= floor) {
            stop("notification.autowalk.stopped_health");
        }
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null) return;

        switch (heading.get()) {
            case FORWARD -> event.setForward(true);
            case BACKWARD -> event.setBackward(true);
            case LEFT -> event.setLeft(true);
            case RIGHT -> event.setRight(true);
        }
        if (sprint.get() && heading.get() == Heading.FORWARD) event.setSprint(true);
        if (jumpObstacles.get() && player.horizontalCollision && player.onGround()) event.setJump(true);
    }

    private void stop(String messageKey) {
        Notifier.warning(I18n.get(messageKey));
        setEnabled(false);
    }

    public enum Heading {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT
    }
}
