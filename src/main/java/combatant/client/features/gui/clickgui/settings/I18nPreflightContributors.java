/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.ConfigSerializer;
import combatant.client.config.MainConfig;
import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.I18nPreflightCollectEvent;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.DropESP;
import combatant.client.features.module.modules.visuals.NameTags;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.RarityColorConfig;
import combatant.client.util.item.TopEnchantUtil;

import java.util.ArrayList;
import java.util.List;

public enum I18nPreflightContributors {
    INSTANCE;

    private static final SettingOwner RELATIONS_OWNER =
            new StaticOwner("relations_color", () -> PlayerRelations.get().save());
    private static final SettingOwner ITEMS_OWNER =
            new StaticOwner("items_color", I18nPreflightContributors::saveItemColorOwners);

    @EventHandler(priority = 1000)
    private void onCollect(I18nPreflightCollectEvent event) {
        collectModules(event);
        collectHud(event);
        collectMainConfig(event);
        collectStaticSettings(event);
    }

    private static void collectModules(I18nPreflightCollectEvent event) {
        for (Module module : ModuleManager.getModules()) {
            if (module == null) continue;
            event.settings("modules/" + module.name(), module.getSettings());
        }
    }

    private static void collectHud(I18nPreflightCollectEvent event) {
        event.settingDefs("hud/global", HudGlobalConfig.get(), HudGlobalConfig.get().getSettingDefs());

        for (DraggableHudElement widget : DraggableHudElementRegistry.getWidgets()) {
            if (widget == null) continue;
            event.settingDefs("hud/draggable/" + widget.getId(), widget, widget.getSettingDefs());
        }

        for (AbstractHudElement element : StaticHudElementRegistry.getAll()) {
            if (element == null) continue;
            event.settingDefs("hud/static/" + element.getId(), element, element.getSettingDefs());
        }
    }

    private static void collectMainConfig(I18nPreflightCollectEvent event) {
        MainConfig config = MainConfig.get();
        event.settingDefs("main_config/image", config, config.getImageSettingDefs());
        event.settingDefs("main_config/misc", config, config.getMiscellaneousSettingDefs());
        event.settingDefs("main_config/security", config, config.getSecuritySettingDefs());
        event.settingDefs("main_config/utility", config, config.getUtilitySettingDefs());
    }

    private static void collectStaticSettings(I18nPreflightCollectEvent event) {
        event.settingDefs("settings/relations_color", RELATIONS_OWNER, relationColorDefs());
        event.settingDefs("settings/items_color", ITEMS_OWNER, itemColorDefs());
    }

    private static List<SettingDef> relationColorDefs() {
        PlayerRelations relations = PlayerRelations.get();
        List<SettingDef> defs = new ArrayList<>();
        defs.add(SettingDef.colorNoAlpha("default_color", relations.colorDefaultValue()));
        defs.add(SettingDef.colorNoAlpha("friend_color", relations.colorFriendValue()));
        defs.add(SettingDef.colorNoAlpha("enemy_color", relations.colorEnemyValue()));
        defs.add(SettingDef.colorNoAlpha("staff_color", relations.colorStaffValue()));
        defs.add(SettingDef.colorNoAlpha("bedwars_self_color", relations.colorBedwarsSelfValue()));
        defs.add(SettingDef.colorNoAlpha("bedwars_enemy_color", relations.colorBedwarsEnemyValue()));
        return defs;
    }

    private static List<SettingDef> itemColorDefs() {
        RarityColorConfig rarity = RarityColorConfig.INSTANCE;
        List<SettingDef> defs = new ArrayList<>();
        defs.add(SettingDef.colorNoAlpha("illegal_item_color", IllegalItemUtil.illegalColorValue()));
        defs.add(SettingDef.colorNoAlpha("top_enchant_color", TopEnchantUtil.topColorValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_common", rarity.commonValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_uncommon", rarity.uncommonValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_rare", rarity.rareValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_epic", rarity.epicValue()));
        return defs;
    }

    private static void saveItemColorOwners() {
        ConfigSerializer.requestSave(RarityColorConfig.INSTANCE);
        BetterChat chat = BetterChat.get();
        if (chat != null) {
            chat.saveConfig();
        }
        DropESP dropESP = Modules.get(DropESP.class);
        if (dropESP != null) {
            dropESP.saveConfig();
        }
        NameTags nameTags = Modules.get(NameTags.class);
        if (nameTags != null) {
            nameTags.saveConfig();
        }
    }

    private record StaticOwner(String name, Runnable saveAction) implements SettingOwner {
        @Override
        public void saveConfig() {
            if (saveAction != null) {
                saveAction.run();
            }
        }
    }
}
