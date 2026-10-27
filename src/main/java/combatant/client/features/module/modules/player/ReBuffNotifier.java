/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.sound.MiscAlertSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashSet;
import java.util.Set;

/**
 * Warns when a tracked buff is about to run out, and again once it is gone, so it can be re-applied.
 */
@ModuleInfo(
        id = "rebuffnotifier",
        displayName = "ReBuffNotifier",
        aliases = {"BuffNotifier"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.rebuffnotifier.description")
public final class ReBuffNotifier extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue speed = bool("rebuffSpeed", "speed", true);
    private final BooleanValue strength = bool("rebuffStrength", "strength", true);
    private final BooleanValue fireResistance = bool("rebuffFireResistance", "fire_resistance", true);
    private final NumberValue<Integer> thresholdSeconds = num("rebuffThreshold", "threshold", 10, 1, 120);
    private final BooleanValue notifyOnExpire = bool("rebuffNotifyOnExpire", "notify_on_expire", true);
    private final BooleanValue sound = bool("rebuffSound", "sound", true);

    // Effects that were active last tick, to tell "expired" from "never had it".
    private final Set<Holder<MobEffect>> seen = new HashSet<>();
    private final Set<Holder<MobEffect>> warned = new HashSet<>();

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }
        check(player, MobEffects.SPEED, speed.get(), "speed");
        check(player, MobEffects.STRENGTH, strength.get(), "strength");
        check(player, MobEffects.FIRE_RESISTANCE, fireResistance.get(), "fire_resistance");
    }

    private void check(LocalPlayer player, Holder<MobEffect> effect, boolean tracked, String key) {
        if (!tracked) {
            seen.remove(effect);
            warned.remove(effect);
            return;
        }
        MobEffectInstance active = player.getEffect(effect);
        if (active == null) {
            if (seen.remove(effect) && notifyOnExpire.get()) {
                Notifier.warning(I18n.get("notification.rebuffnotifier.expired", I18n.get("module.rebuffnotifier.effect." + key)));
                playAlert();
            }
            warned.remove(effect);
            return;
        }
        seen.add(effect);
        if (active.isInfiniteDuration() || active.getDuration() > thresholdSeconds.get() * 20) {
            warned.remove(effect);
            return;
        }
        if (!warned.add(effect)) return;
        int seconds = Math.max(1, (active.getDuration() + 19) / 20);
        Notifier.warning(I18n.get("notification.rebuffnotifier.expiring", I18n.get("module.rebuffnotifier.effect." + key), seconds));
        playAlert();
    }

    private void playAlert() {
        if (sound.get()) MiscAlertSound.WARNING.play();
    }

    private void reset() {
        seen.clear();
        warned.clear();
    }
}
