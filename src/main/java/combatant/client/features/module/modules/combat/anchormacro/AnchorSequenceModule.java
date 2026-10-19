/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.anchormacro;

import combatant.client.automation.Action;
import combatant.client.automation.ActionPriority;
import combatant.client.automation.ActionResult;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.InteractBlockAction;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSlotPicker.DetonatorPreference;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputScope;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.aiming.features.MovementCorrection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.Set;

/**
 * Shared shell of the crosshair anchor macros: trigger handling (bound key or held use key), one runtime task at a
 * time, the settings every sequence needs, and the failure notifications. Subclasses decide which anchors the looked-at
 * block turns into and in which order they are placed, charged and detonated.
 */
public abstract class AnchorSequenceModule extends Module {
    /** What starts a sequence. */
    public enum Trigger {
        /** The bound key is held. */
        KEY,
        /** The use key (right mouse button) is held. */
        USE_KEY
    }

    private static final Set<String> REPORTED = Set.of(
            "NO_ANCHOR_ITEM", "NO_GLOWSTONE", "NO_DETONATOR", "OFFHAND_FUEL",
            AnchorSteps.UNSAFE, AnchorSteps.DRIFT, AnchorSteps.UNREACHABLE);

    protected final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.CUSTOM);
    private final EnumValue<Trigger> trigger = enumMode("trigger", Trigger.KEY, Trigger.values());
    private final KeyBindValue key = visibleWhen(bind("key", "NONE", BindMode.HOLD),
            () -> trigger.get() == Trigger.KEY);
    private final NumberValue<Double> range = num("range", 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = num("wall_range", 0.0D, 0.0D, 6.0D);
    private final EnumValue<PlacementRotationMode> rotation = enumMode(
            "rotation", PlacementRotationMode.NORMAL, PlacementRotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = visibleWhen(enumMode(
            "movement_correction", MovementCorrection.SILENT, MovementCorrection.values()),
            () -> anticheat.get().pick(rotation.get(), PlacementRotationMode.NORMAL,
                    PlacementRotationMode.NO_ROTATION) == PlacementRotationMode.NORMAL);
    private final NumberValue<Integer> stepDelay = num("step_delay", 1, 0, 10);
    private final NumberValue<Integer> charges = num("charges", 1, 1, 4);
    private final EnumValue<DetonatorPreference> detonator = enumMode(
            "detonator", DetonatorPreference.TOTEM, DetonatorPreference.values());
    private final NumberValue<Float> maxSelfDamage = num("max_self_damage", 8.0f, 0.0f, 36.0f);
    private final NumberValue<Integer> selfPredictTicks = num("self_predict_ticks", 0, 0, 10);
    private final BooleanValue allowTotemPop = bool("allow_totem_pop", false);
    private final BooleanValue repeat = bool("repeat", false);
    private final NumberValue<Integer> cooldown = visibleWhen(num("cooldown", 5, 0, 40), repeat::get);

    private AutomationTask task;
    private String lastKeyCombo = "NONE";
    private boolean armed = true;
    private int ticks;
    private int nextStartTick;

    /** The sequence for the looked-at block, or null when it cannot be used (the module reports why itself). */
    protected abstract Action buildSequence(LocalPlayer player, ClientLevel level, BlockHitResult look,
                                            AnchorSteps steps, int delayTicks);

    protected final String bindingName() {
        return name() + ":" + key.getName();
    }

    @Override
    public void onEnable() {
        task = null;
        armed = true;
        nextStartTick = 0;
        lastKeyCombo = "NONE";
        ensureBind();
    }

    @Override
    public void onDisable() {
        CombatantInput.bindings().unbind(bindingName());
        lastKeyCombo = "NONE";
        AutomationRuntime.INSTANCE.cancelOwner(this, "disabled");
        task = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        ticks++;
        ensureBind();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) return;
        if (task != null && !task.isDone()) return;

        if (!triggerHeld()) {
            armed = true;
            return;
        }
        if ((!armed && !repeat.get()) || ticks < nextStartTick) return;
        // Respawn anchors only explode outside the Nether.
        if (level.dimension() == Level.NETHER) return;
        if (!(mc.hitResult instanceof BlockHitResult look) || look.getType() != HitResult.Type.BLOCK) return;

        Action root = buildSequence(player, level, look, new AnchorSteps(stepsConfig()), stepDelay.get());
        if (root == null) return;
        armed = false;
        task = AutomationRuntime.INSTANCE.submit(AutomationTask.builder(this, name(), root)
                .priority(ActionPriority.COMBAT)
                .onFinish(this::onTaskFinished)
                .build());
    }

    private void onTaskFinished(AutomationTask finished) {
        task = null;
        nextStartTick = ticks + cooldown.get();
        ActionResult result = finished.result();
        if (result.isFailure() && REPORTED.contains(result.detail())) {
            Notifier.warning(I18n.get("notification.anchormacro.stopped." + result.detail().toLowerCase(Locale.ROOT)));
        }
    }

    private boolean triggerHeld() {
        if (trigger.get() == Trigger.USE_KEY) return mc.options.keyUse.isDown();
        if (key.isNone()) return false;
        return CombatantInput.bindings().isHeld(bindingName(), InputScope.GAME);
    }

    private void ensureBind() {
        String combo = key.get();
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(lastKeyCombo)) return;
        String id = bindingName();
        CombatantInput.bindings().unbind(id);
        if (!key.isNone()) CombatantInput.bindings().bind(id, combo);
        lastKeyCombo = combo;
    }

    protected final int chargesTarget() {
        return charges.get();
    }

    private AnchorSteps.Config stepsConfig() {
        double reach = anticheat.get().limit(range.get(), AntiCheatPreset.GRIM_BLOCK_RANGE,
                AntiCheatPreset.VANILLA_BLOCK_RANGE);
        double wall = anticheat.get().limit(wallRange.get(), 0.0D, AntiCheatPreset.VANILLA_BLOCK_RANGE);
        PlacementRotationMode mode = anticheat.get().pick(
                rotation.get(), PlacementRotationMode.NORMAL, PlacementRotationMode.NO_ROTATION);
        // The swapped slot stays selected until the next step took over.
        int slotReset = stepDelay.get() + 2;
        PlacementConstraints placement = new PlacementConstraints(reach, wall, mode, movementCorrection.get(),
                mode == PlacementRotationMode.NO_ROTATION, false, 1, slotReset, slotReset, true, 0);
        InteractBlockAction.Settings interact = new InteractBlockAction.Settings(
                reach, wall, mode, movementCorrection.get(), slotReset, true, 20);
        return new AnchorSteps.Config(this, charges.get(), detonator.get(), maxSelfDamage.get(),
                selfPredictTicks.get(), allowTotemPop.get(), placement, interact);
    }

    protected final double effectiveRange() {
        return anticheat.get().limit(range.get(), AntiCheatPreset.GRIM_BLOCK_RANGE,
                AntiCheatPreset.VANILLA_BLOCK_RANGE);
    }

    protected final void warn(String key) {
        Notifier.warning(I18n.get(key));
    }
}
