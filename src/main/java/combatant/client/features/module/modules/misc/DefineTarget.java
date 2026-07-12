/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ModeValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.features.relations.EntityFilters;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.text.ChatNameUtil;

/**
 * Quick target classification under the crosshair:
 * - Players: friend/enemy/staff
 * - Non-players: toggle ignored entities (EntityFilters)
 */
//todo Description
@ModuleInfo(id = "definetarget", displayName = "DefineTarget", aliases = {"clickfriend"}, category = ModuleCategory.MISC)
public class DefineTarget extends Module {

    private static final String SETTING_MODE = "mode";
    private static final String SETTING_BLOCK_DAMAGE_TO = "block_damage_to";
    private static final String SETTING_BEDWARS_RELATIONS = "bedwars_relations";
    private static final String SETTING_BEDWARS_COLOR_MODE = "bedwars_color_mode";
    private static final String SETTING_BLOCK_DAMAGE_TO_BEDWARS = "block_damage_to_bedwars";
    private static final String SETTING_IGNORED_ENTITIES = "ignored_entities";
    private static final String ACTION_TOGGLE_TARGET = "toggle_target";
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue bedwarsRelations = bool("defineTargetBedwarsRelations", SETTING_BEDWARS_RELATIONS, false);
    private final EnumValue<BedwarsColorMode> bedwarsColorMode =
            visibleWhen(enumSetting("defineTargetBedwarsColorMode", SETTING_BEDWARS_COLOR_MODE, BedwarsColorMode.RELATION_COLORS, BedwarsColorMode.values()),
                    bedwarsRelations::get);
    private final ModeValue modeValue = modeSetting(
            "defineTargetMode",
            SETTING_MODE,
            "friend",
            "friend",
            "enemy",
            "staff"
    );
    private final BooleanMapValue blockCategories = group(
            "defineTargetBlockCategories",
            SETTING_BLOCK_DAMAGE_TO,
            new java.util.LinkedHashMap<>() {{
                put("friends", true);
                put("staff", true);
                put("enemies", false);
                put("entities", true);
            }}
    );
    private final BooleanMapValue bedwarsBlockCategories = visibleWhen(group(
            "defineTargetBedwarsBlockCategories",
            SETTING_BLOCK_DAMAGE_TO_BEDWARS,
            new java.util.LinkedHashMap<>() {{
                put("bedwars_self", true);
                put("bedwars_enemy", false);
            }}
    ), bedwarsRelations::get);

    private long lastBlockToast;

    {
        addAction(ACTION_TOGGLE_TARGET, "MOUSE_MIDDLE");
        addRelationSettings();

        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (!isEnabled()) return InteractionResult.PASS;
            if (target instanceof Player targetPlayer) {
                String name = targetPlayer.getGameProfile().name();
                CategoryType type = CategoryRules.determine(name);

                boolean block = switch (type) {
                    case FRIEND -> blockCategories.get("friends");
                    case STAFF -> blockCategories.get("staff");
                    case ENEMY -> blockCategories.get("enemies");
                    case BEDWARS_SELF -> bedwarsRelations.get() && bedwarsBlockCategories.get("bedwars_self");
                    case BEDWARS_ENEMY -> bedwarsRelations.get() && bedwarsBlockCategories.get("bedwars_enemy");
                    default -> false;
                };

                if (block) {
                    notifyBlock(target);
                    return InteractionResult.FAIL;
                }
            }
            boolean blockEntities = blockCategories.get("entities");
            if (blockEntities) {
                var id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
                if (id != null && EntityFilters.get().isIgnoredEntity(id.toString())) {
                    notifyBlock(target);
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.PASS;
        });
    }

    private void addRelationSettings() {
        EntityFilters filters = EntityFilters.get();

        settings.add(new TextListSetting(SETTING_IGNORED_ENTITIES, filters.getIgnoredEntitiesValue(), TextListSetting.PickerMode.ENTITIES) {
            @Override
            public void applyEditorText(String rawText) {
                super.applyEditorText(rawText);
                filters.save();
            }

            @Override
            public Object save() {
                return null;
            }

            @Override
            public void load(Object data) {
            }
        });
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        if (isActionPressedOnce(ACTION_TOGGLE_TARGET)) {
            handleToggle();
        }
    }

    private void handleToggle() {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof EntityHitResult ehr)) return;
        Entity ent = ehr.getEntity();
        if (ent == null) return;

        RelationTargetMode mode = RelationTargetMode.fromId(modeValue.get());

        if (ent instanceof Player p && mode != null) {
            String name = p.getGameProfile().name();
            if (name == null || name.isBlank()) return;
            boolean added = togglePlayerRelation(mode, name);
            notifyToggle(mode.fallbackLabel(), name, added);
            return;
        }

        // Non-player: always toggle ignore list in EntityFilters
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType());
        if (id == null) return;
        String key = id.toString().toLowerCase();
        EntityFilters filters = EntityFilters.get();
        if (filters.isIgnoredEntity(key)) {
            filters.removeIgnoredEntity(key);
            notifyToggle("Entity", key, false);
        } else {
            filters.addIgnoredEntity(key);
            notifyToggle("Entity", key, true);
        }
        filters.save();
    }


    public RelationTargetMode currentRelationTargetMode() {
        RelationTargetMode mode = RelationTargetMode.fromId(modeValue.get());
        return mode != null ? mode : RelationTargetMode.FRIEND;
    }

    public boolean hasBedwarsRelationsConfigured() {
        return bedwarsRelations.get();
    }

    public static boolean isPlayerInRelationMode(RelationTargetMode mode, String rawName) {
        if (mode == null) return false;
        String name = normalizePlayerName(rawName);
        if (name.isBlank()) return false;
        PlayerRelations rel = PlayerRelations.get();
        return switch (mode) {
            case FRIEND -> rel.isFriend(name);
            case ENEMY -> rel.isEnemyPersisted(name);
            case STAFF -> rel.isStaff(name);
        };
    }

    public static boolean assignPlayerRelation(RelationTargetMode mode, String rawName) {
        if (mode == null) return false;
        String name = normalizePlayerName(rawName);
        if (!ChatNameUtil.isNickLike(name)) return false;
        PlayerRelations rel = PlayerRelations.get();
        boolean changed = removeAllPersistentRelations(rel, name);
        changed |= addPersistentRelation(rel, mode, name);
        if (changed) rel.save();
        return changed;
    }

    public static boolean togglePlayerRelation(RelationTargetMode mode, String rawName) {
        if (mode == null) return false;
        String name = normalizePlayerName(rawName);
        if (!ChatNameUtil.isNickLike(name)) return false;
        PlayerRelations rel = PlayerRelations.get();
        boolean wasInMode = isPlayerInRelationMode(mode, name);
        boolean changed;
        if (wasInMode) {
            changed = removePersistentRelation(rel, mode, name);
            if (changed) rel.save();
            return false;
        }
        changed = removeAllPersistentRelations(rel, name);
        changed |= addPersistentRelation(rel, mode, name);
        if (changed) rel.save();
        return true;
    }

    private static String normalizePlayerName(String rawName) {
        String normalized = ChatNameUtil.normalizeNickCandidate(rawName);
        return normalized == null ? "" : normalized;
    }

    private static boolean addPersistentRelation(PlayerRelations rel, RelationTargetMode mode, String name) {
        if (rel == null || mode == null || name == null || name.isBlank()) return false;
        return switch (mode) {
            case FRIEND -> rel.addFriend(name);
            case ENEMY -> rel.addEnemy(name);
            case STAFF -> rel.addStaff(name);
        };
    }

    private static boolean removePersistentRelation(PlayerRelations rel, RelationTargetMode mode, String name) {
        if (rel == null || mode == null || name == null || name.isBlank()) return false;
        return switch (mode) {
            case FRIEND -> rel.removeFriend(name);
            case ENEMY -> rel.removeEnemy(name);
            case STAFF -> rel.removeStaff(name);
        };
    }

    private static boolean removeAllPersistentRelations(PlayerRelations rel, String name) {
        if (rel == null || name == null || name.isBlank()) return false;
        boolean changed = false;
        changed |= rel.removeFriend(name);
        changed |= rel.removeEnemy(name);
        changed |= rel.removeStaff(name);
        changed |= rel.removeBedwarsSelf(name);
        changed |= rel.removeBedwarsEnemy(name);
        return changed;
    }

    private void notifyToggle(String type, String target, boolean added) {
        Notifier.state(type + ": " + target, added);
    }

    private void notifyBlock(Entity target) {
        long now = System.currentTimeMillis();
        if (now - lastBlockToast < 600) return;
        lastBlockToast = now;
        String name = target.getName() != null ? target.getName().getString() : target.getType().toString();
        Notifier.info("Blocked damage to " + name);
    }

    public boolean isBedwarsRelationsActive() {
        return isEnabled() && bedwarsRelations.get();
    }

    public boolean useBedwarsTeamColors() {
        return isBedwarsRelationsActive() && bedwarsColorMode.get() == BedwarsColorMode.TEAM_COLORS;
    }

    public enum RelationTargetMode {
        FRIEND("friend", "Friend"),
        ENEMY("enemy", "Enemy"),
        STAFF("staff", "Staff");

        private final String id;
        private final String fallbackLabel;

        RelationTargetMode(String id, String fallbackLabel) {
            this.id = id;
            this.fallbackLabel = fallbackLabel;
        }

        public String id() {
            return id;
        }

        public String fallbackLabel() {
            return fallbackLabel;
        }

        public int color(PlayerRelations relations) {
            PlayerRelations rel = relations != null ? relations : PlayerRelations.get();
            return switch (this) {
                case FRIEND -> rel.colorFriend();
                case ENEMY -> rel.colorEnemy();
                case STAFF -> rel.colorStaff();
            };
        }

        public static RelationTargetMode fromId(String rawId) {
            if (rawId == null || rawId.isBlank()) return FRIEND;
            String id = rawId.toLowerCase(java.util.Locale.ROOT);
            if (id.equals("entities")) return FRIEND;
            for (RelationTargetMode mode : values()) {
                if (mode.id.equals(id)) return mode;
            }
            return FRIEND;
        }
    }

    public enum BedwarsColorMode {
        RELATION_COLORS,
        TEAM_COLORS
    }
}
