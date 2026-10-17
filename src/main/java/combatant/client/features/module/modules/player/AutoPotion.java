/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.SetValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputScope;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.sound.MiscAlertSound;
import combatant.client.util.target.TargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@ModuleInfo(
        id = "autopotion",
        displayName = "AutoPotion",
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.autopotion.description")
public final class AutoPotion extends Module {

    private static final int ROTATION_PRIORITY = 50;
    private static final int MIN_PLAYER_AGE = 100;
    private static final int AUTO_EVALUATION_INTERVAL = 3;
    private static final int WARNING_EVALUATION_INTERVAL = 4;
    private static final int MANUAL_REQUEST_TIMEOUT = 20;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumMode("autoPotionMode", Mode.AUTO, Mode.values());
    private final SetValue potions = textList(
            "autoPotionPotions",
            "potions",
            TextListSetting.PickerMode.POTIONS,
            defaultPotions()
    );
    private final KeyBindValue semiKey = visibleWhen(
            bind("autoPotionSemiKey", "semi_key", "NONE", BindMode.PRESS),
            () -> mode.get() == Mode.SEMI_AUTO
    );
    private final NumberValue<Integer> refreshSeconds = visibleWhen(
            num("autoPotionRefreshSeconds", "refresh_seconds", 10, 1, 120),
            () -> mode.get() == Mode.AUTO
    );
    private final EnumValue<AutoTiming> autoTiming = visibleWhen(
            enumSetting("autoPotionTiming", "timing", AutoTiming.SMART, AutoTiming.values()),
            () -> mode.get() == Mode.AUTO
    );
    private final EnumValue<Preference> preference =
            enumSetting("autoPotionPreference", "preference", Preference.BALANCED, Preference.values());
    private final NumberValue<Float> healthThreshold = visibleWhen(
            num("autoPotionHealthThreshold", "health_threshold", 12.0F, 2.0F, 20.0F),
            this::hasHealingPotionSelected
    );
    private final BooleanValue warnings = bool("autoPotionWarnings", "warnings", true);
    private final NumberValue<Integer> warningSeconds = visibleWhen(
            num("autoPotionWarningSeconds", "warning_seconds", 15, 1, 120),
            warnings::get
    );
    private final NumberValue<Integer> cooldownTicks =
            num("autoPotionCooldownTicks", "cooldown_ticks", 12, 1, 40);

    private final List<PotionCandidate> pendingPotions = new ArrayList<>(6);
    private final Set<String> warnedPotions = new HashSet<>();
    private int cooldown;
    private int manualRequestTicks;
    private boolean pendingRotation;
    private String lastSemiCombo = "NONE";

    @Override
    public void onEnable() {
        normalizePotionSelection();
        resetState(false);
        ensureSemiBind();
    }

    @Override
    public void onDisable() {
        resetState(true);
        CombatantInput.bindings().unbind(semiBindingName());
        lastSemiCombo = "NONE";
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        ensureSemiBind();

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            resetState(true);
            return;
        }

        if (warnings.get() && player.tickCount % WARNING_EVALUATION_INTERVAL == 0) {
            updateWarnings(player);
        }

        if (mode.get() == Mode.SEMI_AUTO && CombatantInput.bindings().consumePress(semiBindingName(), InputScope.GAME)) {
            manualRequestTicks = MANUAL_REQUEST_TIMEOUT;
        }

        if (cooldown > 0) cooldown--;
        if (manualRequestTicks > 0) manualRequestTicks--;
        if (cooldown > 0 || pendingRotation) return;

        boolean manual = mode.get() == Mode.SEMI_AUTO && manualRequestTicks > 0;
        if (!manual && mode.get() == Mode.SEMI_AUTO) return;
        if (!manual && player.tickCount % AUTO_EVALUATION_INTERVAL != 0) return;
        if (!canStartSequence(player)) return;

        pendingPotions.clear();
        collectPendingPotions(player, pendingPotions, manual);
        if (pendingPotions.isEmpty()) return;

        if (!manual && autoTiming.get() == AutoTiming.SMART && !isSmartWindow(player, pendingPotions)) {
            pendingPotions.clear();
            return;
        }

        if (requiresInventoryClick(pendingPotions) && isMovingForInventory(player)) {
            pendingPotions.clear();
            return;
        }

        if (manual) manualRequestTicks = 0;
        pendingRotation = true;
    }

    @EventHandler(priority = 20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled() || !pendingRotation) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || pendingPotions.isEmpty()) {
            pendingRotation = false;
            pendingPotions.clear();
            return;
        }

        RotationTarget target = new RotationTarget(
                new Rotation(player.getYRot(), 90.0F),
                player,
                List.of(),
                1,
                4.0F,
                false,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(this::throwPendingPotions)
        );
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private boolean canStartSequence(LocalPlayer player) {
        if (player.isUsingItem() || !player.onGround() || player.tickCount <= MIN_PLAYER_AGE) return false;
        if (RotationManager.INSTANCE.getActiveRotationTarget() != null) return false;
        return !InventorySwap.INSTANCE.isHotbarLeased();
    }

    private boolean isSmartWindow(LocalPlayer player, List<PotionCandidate> pending) {
        if (containsUrgentHealing(player, pending)) return true;

        LivingEntity target = TargetManager.getTarget(false);
        if (target == null || !target.isAlive()) return true;

        double distance = RotationManager.boxedDistanceToPlayer(target);
        if (distance > 4.5D) return true;
        if (requiresInventoryClick(pending)) return false;
        if (target.hurtTime >= 4) return true;
        return player.hurtTime < 6;
    }

    private boolean containsUrgentHealing(LocalPlayer player, List<PotionCandidate> pending) {
        if (player.getHealth() + player.getAbsorptionAmount() > healthThreshold.get()) return false;
        for (PotionCandidate candidate : pending) {
            Holder<Potion> potion = resolveFamilyPotion(candidate.familyId);
            if (potion != null && containsEffect(potion, MobEffects.INSTANT_HEALTH)) return true;
        }
        return false;
    }

    private void collectPendingPotions(LocalPlayer player, List<PotionCandidate> out, boolean forceRefresh) {
        Set<String> selected = normalizedSelectedPotions();
        if (selected.isEmpty()) return;

        Set<String> requested = new LinkedHashSet<>();
        for (String familyId : selected) {
            Holder<Potion> potion = resolveFamilyPotion(familyId);
            if (potion != null && shouldRefresh(player, potion, forceRefresh)) {
                requested.add(familyId);
            }
        }

        if (requested.isEmpty()) return;
        Map<String, PotionCandidate> available = findBestPotions(player, requested);
        for (String familyId : requested) {
            PotionCandidate candidate = available.get(familyId);
            if (candidate != null) out.add(candidate);
        }
        out.sort(Comparator.comparingDouble((PotionCandidate candidate) ->
                pendingScore(player, candidate, forceRefresh)).reversed());
    }

    private boolean shouldRefresh(LocalPlayer player, Holder<Potion> potion, boolean forceRefresh) {
        for (MobEffectInstance template : potion.value().getEffects()) {
            Holder<MobEffect> effect = template.getEffect();
            if (effect.value().isInstantaneous()) {
                if (effect.equals(MobEffects.INSTANT_HEALTH)) {
                    if (player.getHealth() + player.getAbsorptionAmount() <= healthThreshold.get()) return true;
                } else if (forceRefresh) {
                    return true;
                }
                continue;
            }

            if (forceRefresh) return true;
            MobEffectInstance active = player.getEffect(effect);
            if (active == null || active.getDuration() <= refreshSeconds.get() * 20) return true;
        }
        return false;
    }

    private double pendingScore(LocalPlayer player, PotionCandidate candidate, boolean forceRefresh) {
        Holder<Potion> potion = resolveFamilyPotion(candidate.familyId);
        if (potion == null) return candidate.preferenceScore;

        double urgency = 0.0D;
        int refreshTicks = Math.max(1, refreshSeconds.get() * 20);
        for (MobEffectInstance template : potion.value().getEffects()) {
            Holder<MobEffect> effect = template.getEffect();
            if (effect.value().isInstantaneous()) {
                if (effect.equals(MobEffects.INSTANT_HEALTH)) {
                    double health = player.getHealth() + player.getAbsorptionAmount();
                    double missing = Math.max(0.0D, healthThreshold.get() - health);
                    urgency = Math.max(urgency, 600.0D + missing * 60.0D);
                } else if (forceRefresh) {
                    urgency = Math.max(urgency, 120.0D);
                }
                continue;
            }

            MobEffectInstance active = player.getEffect(effect);
            if (active == null) {
                urgency = Math.max(urgency, 500.0D);
                continue;
            }

            int remaining = Math.max(0, active.getDuration());
            if (remaining <= refreshTicks) {
                double ratio = 1.0D - Math.min(1.0D, (double) remaining / (double) refreshTicks);
                urgency = Math.max(urgency, 250.0D + ratio * 200.0D);
            } else if (forceRefresh) {
                urgency = Math.max(urgency, 25.0D);
            }
        }
        return urgency + candidate.preferenceScore * 0.10D;
    }

    private Map<String, PotionCandidate> findBestPotions(LocalPlayer player, Set<String> requested) {
        Map<String, List<PotionCandidate>> candidates = new HashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty() || !stack.is(Items.SPLASH_POTION)) continue;

            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null || contents.potion().isEmpty()) continue;
            Holder<Potion> potion = contents.potion().get();
            String potionId = potion.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
            if (potionId == null) continue;

            String familyId = canonicalPotionId(potionId);
            if (!requested.contains(familyId)) continue;

            int amplifier = 0;
            int duration = 0;
            for (MobEffectInstance effect : potion.value().getEffects()) {
                amplifier = Math.max(amplifier, effect.getAmplifier());
                duration += Math.max(0, effect.getDuration());
            }

            List<PotionCandidate> family = candidates.computeIfAbsent(familyId, ignored -> new ArrayList<>(3));
            boolean duplicate = false;
            for (PotionCandidate existing : family) {
                if (existing.potionId.equals(potionId)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) family.add(new PotionCandidate(familyId, potionId, amplifier, duration, 0.0D));
        }

        Map<String, PotionCandidate> best = new HashMap<>();
        for (Map.Entry<String, List<PotionCandidate>> entry : candidates.entrySet()) {
            List<PotionCandidate> family = entry.getValue();
            int maxAmplifier = 0;
            int maxDuration = 0;
            for (PotionCandidate candidate : family) {
                maxAmplifier = Math.max(maxAmplifier, candidate.amplifier);
                maxDuration = Math.max(maxDuration, candidate.durationTicks);
            }

            PotionCandidate selected = null;
            double selectedScore = Double.NEGATIVE_INFINITY;
            for (PotionCandidate candidate : family) {
                double score = candidateScore(candidate, maxAmplifier, maxDuration);
                if (score > selectedScore) {
                    selectedScore = score;
                    selected = new PotionCandidate(
                            candidate.familyId,
                            candidate.potionId,
                            candidate.amplifier,
                            candidate.durationTicks,
                            score
                    );
                }
            }
            if (selected != null) best.put(entry.getKey(), selected);
        }
        return best;
    }

    private double candidateScore(PotionCandidate candidate, int maxAmplifier, int maxDuration) {
        double strengthScore = maxAmplifier <= 0
                ? 100.0D
                : ((candidate.amplifier + 1.0D) / (maxAmplifier + 1.0D)) * 100.0D;
        double durationScore = maxDuration <= 0
                ? 0.0D
                : ((double) candidate.durationTicks / (double) maxDuration) * 100.0D;

        return switch (preference.get()) {
            case DURATION -> durationScore * 0.80D + strengthScore * 0.20D;
            case STRENGTH -> strengthScore * 0.80D + durationScore * 0.20D;
            case BALANCED -> durationScore * 0.50D + strengthScore * 0.50D;
        };
    }

    private void throwPendingPotions() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null || pendingPotions.isEmpty()) {
            pendingRotation = false;
            pendingPotions.clear();
            return;
        }

        List<PotionCandidate> queued = List.copyOf(pendingPotions);
        pendingPotions.clear();
        pendingRotation = false;

        LinkedHashSet<String> replenished = new LinkedHashSet<>();
        for (PotionCandidate candidate : queued) {
            Predicate<ItemStack> predicate = stack -> isSplashPotion(stack, candidate.potionId);
            InventorySwapRequest request = InventorySwapRequest.builder(predicate, () -> usePotion(mc.player))
                    .scope(InventorySearchScope.FULL)
                    .visibility(InventorySwapVisibility.SILENT)
                    .restore(true)
                    .policy(InventorySwapPolicy.NONE)
                    .actionKind(InventoryActionKind.USE_ITEM)
                    .build();

            if (InventorySwap.INSTANCE.execute(request)) {
                replenished.add(candidate.familyId);
                warnedPotions.remove(candidate.familyId);
            }
        }

        if (!replenished.isEmpty()) {
            cooldown = cooldownTicks.get();
            Notifier.info(I18n.get("notification.autopotion.replenished", joinPotionNames(replenished)));
        }
    }

    private void updateWarnings(LocalPlayer player) {
        Set<String> selected = normalizedSelectedPotions();
        warnedPotions.retainAll(selected);
        int thresholdTicks = warningSeconds.get() * 20;

        for (String familyId : selected) {
            Holder<Potion> potion = resolveFamilyPotion(familyId);
            if (potion == null) {
                warnedPotions.remove(familyId);
                continue;
            }

            int remaining = remainingTicks(player, potion);
            if (remaining <= 0 || remaining > thresholdTicks) {
                warnedPotions.remove(familyId);
                continue;
            }

            if (!warnedPotions.add(familyId)) continue;
            int seconds = Math.max(1, (remaining + 19) / 20);
            Notifier.warning(I18n.get("notification.autopotion.expiring", potionName(potion), seconds));
            MiscAlertSound.WARNING.play();
        }
    }

    private int remainingTicks(LocalPlayer player, Holder<Potion> potion) {
        int minimum = Integer.MAX_VALUE;
        boolean timed = false;
        for (MobEffectInstance template : potion.value().getEffects()) {
            Holder<MobEffect> effect = template.getEffect();
            if (effect.value().isInstantaneous()) continue;
            timed = true;
            MobEffectInstance active = player.getEffect(effect);
            if (active == null) return 0;
            minimum = Math.min(minimum, active.getDuration());
        }
        return timed ? minimum : 0;
    }

    private boolean requiresInventoryClick(List<PotionCandidate> candidates) {
        for (PotionCandidate candidate : candidates) {
            Predicate<ItemStack> predicate = stack -> isSplashPotion(stack, candidate.potionId);
            if (!InventorySwap.INSTANCE.findHotbar(predicate).found()
                    && InventorySwap.INSTANCE.findInventory(predicate).found()) {
                return true;
            }
        }
        return false;
    }

    private boolean isMovingForInventory(LocalPlayer player) {
        if (player.isSprinting()) return true;
        if (player.input == null || player.input.keyPresses == null) return false;
        Input input = player.input.keyPresses;
        return input.forward() || input.backward() || input.left() || input.right();
    }

    private boolean hasHealingPotionSelected() {
        for (String familyId : normalizedSelectedPotions()) {
            Holder<Potion> potion = resolveFamilyPotion(familyId);
            if (potion != null && containsEffect(potion, MobEffects.INSTANT_HEALTH)) return true;
        }
        return false;
    }

    private boolean containsEffect(Holder<Potion> potion, Holder<MobEffect> wanted) {
        for (MobEffectInstance effect : potion.value().getEffects()) {
            if (effect.getEffect().equals(wanted)) return true;
        }
        return false;
    }

    private Set<String> normalizedSelectedPotions() {
        Set<String> selected = potions.get();
        return selected == null ? Set.of() : selected;
    }

    private void normalizePotionSelection() {
        Set<String> selected = potions.get();
        if (selected == null || selected.isEmpty()) return;

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        boolean changed = false;
        for (String raw : selected) {
            String familyId = normalizeSelectionId(raw);
            if (familyId == null) {
                changed = true;
                continue;
            }
            normalized.add(familyId);
            if (!familyId.equals(raw)) changed = true;
        }

        if (changed && !normalized.equals(selected)) potions.set(normalized);
    }

    private String normalizeSelectionId(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) return null;

        if (BuiltInRegistries.POTION.get(id).isPresent()) return canonicalPotionId(id.toString());

        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
        if (effect == null) return null;
        for (Potion potion : BuiltInRegistries.POTION) {
            Identifier potionId = BuiltInRegistries.POTION.getKey(potion);
            if (potionId == null || potion.getEffects().isEmpty()) continue;
            for (MobEffectInstance template : potion.getEffects()) {
                if (template.getEffect().equals(effect)) return canonicalPotionId(potionId.toString());
            }
        }
        return null;
    }

    private Holder<Potion> resolveFamilyPotion(String familyId) {
        Identifier id = Identifier.tryParse(familyId);
        if (id == null) return null;

        Holder<Potion> direct = BuiltInRegistries.POTION.get(id).orElse(null);
        if (direct != null && !direct.value().getEffects().isEmpty()) return direct;

        for (Potion potion : BuiltInRegistries.POTION) {
            Identifier potionId = BuiltInRegistries.POTION.getKey(potion);
            if (potionId == null || potion.getEffects().isEmpty()) continue;
            if (!canonicalPotionId(potionId.toString()).equals(familyId)) continue;
            return BuiltInRegistries.POTION.wrapAsHolder(potion);
        }
        return null;
    }

    private String potionName(Holder<Potion> potion) {
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, potion);
        return stack.getHoverName().getString();
    }

    private String joinPotionNames(Set<String> familyIds) {
        List<String> names = new ArrayList<>(familyIds.size());
        for (String id : familyIds) {
            Holder<Potion> potion = resolveFamilyPotion(id);
            names.add(potion != null ? potionName(potion) : id);
        }
        return String.join(", ", names);
    }

    private boolean isSplashPotion(ItemStack stack, String potionId) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.SPLASH_POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return false;
        return contents.potion().get().unwrapKey()
                .map(key -> key.identifier().toString().equals(potionId))
                .orElse(false);
    }

    private void usePotion(LocalPlayer player) {
        InteractionResult result = InventorySwap.INSTANCE.useItem(InteractionHand.MAIN_HAND);
        if (result != null && result.consumesAction()) player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
    }

    private String semiBindingName() {
        return name() + ":" + semiKey.getName();
    }

    private void ensureSemiBind() {
        String combo = mode.get() == Mode.SEMI_AUTO ? semiKey.get() : "NONE";
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(lastSemiCombo)) return;

        String name = semiBindingName();
        CombatantInput.bindings().unbind(name);
        if (!semiKey.isNone() && mode.get() == Mode.SEMI_AUTO) CombatantInput.bindings().bind(name, combo);
        lastSemiCombo = combo;
    }

    private static String canonicalPotionId(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) return raw;
        String path = id.getPath();
        if (path.startsWith("long_")) path = path.substring(5);
        if (path.startsWith("strong_")) path = path.substring(7);
        return id.getNamespace() + ":" + path;
    }

    private static Set<String> defaultPotions() {
        LinkedHashSet<String> defaults = new LinkedHashSet<>();
        defaults.add("minecraft:fire_resistance");
        defaults.add("minecraft:swiftness");
        defaults.add("minecraft:regeneration");
        return defaults;
    }

    private void resetState(boolean releaseRotation) {
        cooldown = 0;
        manualRequestTicks = 0;
        pendingRotation = false;
        pendingPotions.clear();
        warnedPotions.clear();
        if (releaseRotation) RotationManager.INSTANCE.release(this);
    }

    public enum Mode {
        AUTO,
        SEMI_AUTO
    }

    public enum AutoTiming {
        WHEN_POSSIBLE,
        SMART
    }

    public enum Preference {
        BALANCED,
        DURATION,
        STRENGTH
    }

    private record PotionCandidate(
            String familyId,
            String potionId,
            int amplifier,
            int durationTicks,
            double preferenceScore
    ) {
    }

}
