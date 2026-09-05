/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.*;
import combatant.client.features.relations.PlayerRelations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import combatant.client.config.SettingDef;
import combatant.client.config.values.*;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.pvp.ItemCooldownSnapshot;
import combatant.client.util.pvp.PvpChatParser;
import combatant.client.util.pvp.PvpOverlayParser;
import combatant.client.util.pvp.PvpState;
import combatant.client.util.pvp.client.CooldownsState;

import java.util.*;

/**
 * Handles PvP cooldown rendering and optional local rule synthesis.
 */
//todo Description
@ModuleInfo(
        id = "pvpcooldowns",
        displayName = "PvpCooldowns",
        category = ModuleCategory.MISC
)
public class PvpCooldowns extends Module {

    private static final long LOCAL_RULE_GRACE_MS = 10_000L;

    private final BooleanValue renderSlots = bool("pvpcd_render_slots", "render_slots", true);
    private final BooleanValue renderWidget = bool("pvpcd_widget", "show_in_hud", true);
    private final NumberValue<Integer> targetGlowTtlMs =
            num("pvpcd_target_glow_ttl_ms", "target_glow_ttl_ms", 1500, 0, 10000);
    private final BooleanValue hideTargetGlow = bool("pvpcd_hide_target_glow", "hide_target_glow", false);
    private final BooleanValue dynamicEnemyEnabled = bool("pvpcd_dynamic_enemy", "dynamic_enemy", false);
    private final NumberValue<Integer> dynamicEnemyTime = visibleWhen(
            num("pvpcd_dynamic_enemy_time", "dynamic_enemy_time", 60, 0, 600),
            dynamicEnemyEnabled::get
    );
    private final BooleanValue blockDisconnect = bool("pvpcd_block_disconnect", "block_disconnect", true);

    private final BooleanMapValue stateSources = group(
            "pvpcd_state_sources",
            "state_sources",
            defaultStateSources()
    );

    private final SetValue chatGivePatterns = visibleWhen(textList(
            "pvpcd_chat_give_patterns",
            "chat_give_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpChatParser.defaultGivePatterns()
    ), () -> sourceEnabled("chat") || sourceEnabled("game_message"));

    private final SetValue chatReceivePatterns = visibleWhen(textList(
            "pvpcd_chat_receive_patterns",
            "chat_receive_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpChatParser.defaultReceivePatterns()
    ), () -> sourceEnabled("chat") || sourceEnabled("game_message"));

    private final SetValue chatActivePatterns = visibleWhen(textList(
            "pvpcd_chat_active_patterns",
            "chat_active_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpChatParser.defaultActivePatterns()
    ), () -> sourceEnabled("chat") || sourceEnabled("game_message"));

    private final SetValue chatExitPatterns = visibleWhen(textList(
            "pvpcd_chat_exit_patterns",
            "chat_exit_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpChatParser.defaultExitPatterns()
    ), () -> sourceEnabled("chat") || sourceEnabled("game_message"));

    private final SetValue overlayActivePatterns = visibleWhen(textList(
            "pvpcd_overlay_active_patterns",
            "overlay_active_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpOverlayParser.defaultActivePatterns()
    ), () -> sourceEnabled("overlay"));

    private final SetValue overlayExitPatterns = visibleWhen(textList(
            "pvpcd_overlay_exit_patterns",
            "overlay_exit_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpOverlayParser.defaultExitPatterns()
    ), () -> sourceEnabled("overlay"));

    private final SetValue tabActivePatterns = visibleWhen(textList(
            "pvpcd_tab_active_patterns",
            "tab_active_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpOverlayParser.defaultActivePatterns()
    ), () -> sourceEnabled("tab"));

    private final SetValue tabExitPatterns = visibleWhen(textList(
            "pvpcd_tab_exit_patterns",
            "tab_exit_patterns",
            TextListSetting.PickerMode.TEXT,
            PvpOverlayParser.defaultExitPatterns()
    ), () -> sourceEnabled("tab"));

    /**
     * Off by default. Server-provided vanilla item cooldown packets must remain the primary flow.
     * Enable only for servers that lock items without sending cooldown data.
     */
    private final BooleanValue localRulesEnabled = bool("pvpcd_local_rules", "local_rules", false);

    /**
     * Object-shaped per-item local cooldown rules. Rendered by CooldownRulesSetting.
     */
    private final ItemCooldownRulesValue itemRules = new ItemCooldownRulesValue(
            "pvpcd_item_rules",
            defaultItemRules()
    );

    {
        setting(SettingDef.cooldownRules("cooldown_rules", itemRules).visibleWhen(localRulesEnabled::get));
    }

    private static Map<String, Boolean> defaultStateSources() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("chat", true);
        defaults.put("game_message", true);
        defaults.put("overlay", true);
        defaults.put("tab", true);
        return defaults;
    }

    private static Map<String, ItemCooldownRulesValue.Rule> defaultItemRules() {
        Map<String, ItemCooldownRulesValue.Rule> rules = new LinkedHashMap<>();
        rules.put("minecraft:golden_apple", new ItemCooldownRulesValue.Rule(
                false,
                30,
                2,
                10,
                ItemCooldownRulesValue.Scope.PVP_GRACE,
                ItemCooldownRulesValue.Trigger.CONSUME_FINISH,
                false
        ));
        rules.put("minecraft:enchanted_golden_apple", new ItemCooldownRulesValue.Rule(
                false,
                60,
                2,
                10,
                ItemCooldownRulesValue.Scope.PVP_GRACE,
                ItemCooldownRulesValue.Trigger.CONSUME_FINISH,
                false
        ));
        rules.put("minecraft:totem_of_undying", new ItemCooldownRulesValue.Rule(
                false,
                0,
                1,
                0,
                ItemCooldownRulesValue.Scope.PVP_GRACE,
                ItemCooldownRulesValue.Trigger.TOTEM_POP,
                false
        ));
        return rules;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            CooldownsState.MANAGER.clear();
        }
        syncDynamicEnemySettings();
    }

    @Override
    public void onDisable() {
        CooldownsState.MANAGER.clear();
        CooldownsState.PENDING.clear();
        syncDynamicEnemySettings();
    }

    @Override
    public void onEnable() {
        syncDynamicEnemySettings();
    }

    public boolean isSystemEnabled() {
        return isEnabled();
    }

    public boolean shouldRenderSlots() {
        return renderSlots.get();
    }

    /** Compatibility API for callers compiled against the previous signature. */
    public boolean shouldBlockUsage() {
        return false;
    }

    public boolean shouldRenderWidget() {
        return renderWidget.get();
    }

    public int getTargetGlowTtlMs() {
        return targetGlowTtlMs.get();
    }

    public boolean shouldHideTargetGlow() {
        return isEnabled() && hideTargetGlow.get();
    }

    public boolean isDynamicEnemyEnabled() {
        return dynamicEnemyEnabled.get();
    }

    public int getDynamicEnemySeconds() {
        return dynamicEnemyTime.get();
    }

    public boolean shouldBlockDisconnect() {
        return isEnabled() && blockDisconnect.get() && CooldownsState.MANAGER.isInPvp();
    }

    public long getDisconnectBlockRemainingMs() {
        if (!shouldBlockDisconnect()) {
            return 0L;
        }
        return PvpState.getPredictedRemainingMs();
    }

    public Component getDisconnectBlockTooltip() {
        long remainingMs = getDisconnectBlockRemainingMs();
        if (remainingMs <= 0L) {
            return Component.literal("PvP cooldown active");
        }
        double seconds = Math.ceil(remainingMs / 100.0) / 10.0;
        return Component.literal(String.format(Locale.ROOT, "PvP cooldown: %.1fs", seconds));
    }

    public boolean shouldBlockItemUse(Item item) {
        if (!isEnabled()) return false;
        if (item == null || item == Items.AIR) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return false;

        if (!localRulesEnabled.get()) return false;

        ItemCooldownRulesValue.Rule rule = itemRules.getRule(id.toString());
        if (rule == null || !rule.enabled() || !scopeAllows(rule.scope())) return false;

        ItemCooldownRulesValue.UseBlockMode blockMode = rule.blockMode() != null
                ? rule.blockMode()
                : ItemCooldownRulesValue.UseBlockMode.NONE;
        if (blockMode == ItemCooldownRulesValue.UseBlockMode.NONE) return false;

        ItemCooldownSnapshot snapshot = CooldownsState.MANAGER.snapshot(item);
        return switch (blockMode) {
            case NONE -> false;
            case COOLDOWN -> snapshot.cooling();
            case WINDOW -> snapshot.useWindowActive();
            case ANY -> snapshot.visible();
        };
    }

    public boolean shouldReadChatMessages() {
        return isEnabled() && sourceEnabled("chat");
    }

    public boolean shouldReadGameMessages() {
        return isEnabled() && sourceEnabled("game_message");
    }

    public boolean shouldReadOverlayMessages() {
        return isEnabled() && sourceEnabled("overlay");
    }

    public boolean shouldReadTabText() {
        return isEnabled() && sourceEnabled("tab");
    }

    public Set<String> getChatGivePatterns() {
        return chatGivePatterns.get();
    }

    public Set<String> getChatReceivePatterns() {
        return chatReceivePatterns.get();
    }

    public Set<String> getChatActivePatterns() {
        return chatActivePatterns.get();
    }

    public Set<String> getChatExitPatterns() {
        return chatExitPatterns.get();
    }

    public Set<String> getOverlayActivePatterns() {
        return overlayActivePatterns.get();
    }

    public Set<String> getOverlayExitPatterns() {
        return overlayExitPatterns.get();
    }

    public Set<String> getTabActivePatterns() {
        return tabActivePatterns.get();
    }

    public Set<String> getTabExitPatterns() {
        return tabExitPatterns.get();
    }

    public int getLocalCooldownSeconds(Item item) {
        ItemCooldownRulesValue.Rule rule = configuredRuleFor(item);
        return rule != null ? rule.seconds() : 0;
    }

    public int getLocalRuleUses(Item item) {
        ItemCooldownRulesValue.Rule rule = configuredRuleFor(item);
        return rule != null ? rule.uses() : 1;
    }

    public int getLocalRuleWindowSeconds(Item item) {
        ItemCooldownRulesValue.Rule rule = configuredRuleFor(item);
        return rule != null ? rule.windowSeconds() : 0;
    }

    public ItemCooldownRulesValue.Rule getLocalRule(Item item) {
        return configuredRuleFor(item);
    }

    public Set<Item> getLocalCooldownItems() {
        Set<Item> out = new LinkedHashSet<>();
        if (!isEnabled() || !localRulesEnabled.get()) return out;

        for (var entry : itemRules.getRules().entrySet()) {
            ItemCooldownRulesValue.Rule rule = entry.getValue();
            if (rule == null || !rule.enabled()) continue;
            if (rule.seconds() <= 0 && rule.uses() <= 1) continue;
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.getKey()));
                if (item != null && item != Items.AIR) {
                    out.add(item);
                }
            } catch (RuntimeException ignored) {
                // Invalid custom item id in config: ignore this rule instead of breaking the module.
            }
        }

        return out;
    }

    public boolean tryStartLocalCooldown(Item item, ItemCooldownRulesValue.Trigger trigger) {
        ItemCooldownRulesValue.Rule rule = configuredRuleFor(item);
        if (rule == null || rule.trigger() != trigger) return false;
        if (!scopeAllows(rule.scope())) return false;
        CooldownsState.MANAGER.recordRuleUse(item, rule);
        return true;
    }

    private ItemCooldownRulesValue.Rule configuredRuleFor(Item item) {
        if (!isEnabled() || !localRulesEnabled.get()) return null;
        if (item == null || item == Items.AIR) return null;

        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return null;

        ItemCooldownRulesValue.Rule rule = itemRules.getRule(id.toString());
        if (rule == null || !rule.enabled()) return null;
        if (rule.seconds() <= 0 && rule.uses() <= 1) return null;
        return rule;
    }

    private boolean scopeAllows(ItemCooldownRulesValue.Scope scope) {
        return switch (scope != null ? scope : ItemCooldownRulesValue.Scope.PVP_GRACE) {
            case ALWAYS -> true;
            case PVP_ONLY -> CooldownsState.MANAGER.isInPvp();
            case PVP_GRACE -> CooldownsState.MANAGER.isPvpGraceActive(LOCAL_RULE_GRACE_MS);
        };
    }

    private void syncDynamicEnemySettings() {
        PlayerRelations.setDynamicEnemyEnabled(
                isEnabled() && dynamicEnemyEnabled.get()
        );
        PlayerRelations.setDynamicEnemyDurationSeconds(
                dynamicEnemyTime.get()
        );
    }

    private boolean sourceEnabled(String key) {
        return stateSources.get(key);
    }
}
