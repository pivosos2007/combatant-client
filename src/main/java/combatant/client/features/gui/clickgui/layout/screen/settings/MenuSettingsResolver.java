/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings;

import combatant.client.config.ConfigSerializer;
import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.module.ModuleComponent;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingFactory;
import combatant.client.features.gui.clickgui.settings.SettingErrorView;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.DropESP;
import combatant.client.features.module.modules.visuals.NameTags;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.RarityColorConfig;
import combatant.client.util.item.TopEnchantUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum MenuSettingsResolver {
    ;
    private static final String UI_HOTBAR = "ui_hotbar";
    private static final String UI_HEALTH = "ui_health";
    private static final String UI_DYNAMIC_ISLAND = "ui_dynamic_island";
    private static final String UI_TAB_LIST = "ui_tab_list";
    private static final String UI_BAR = "ui_bar";
    private static final String UI_BUTTONS = "ui_buttons";
    private static final String UI_TOOLTIPS = "ui_tooltips";
    private static final String UI_SWAP_TOOLTIP = "ui_swap_tooltip";
    private static final String UI_RELATIONS_COLOR = "ui_relations_color";
    private static final String UI_ITEMS_COLOR = "ui_items_color";

    private static final List<StaticHudCard> STATIC_UI_CARDS = List.of(
            new StaticHudCard("vanilla_buttons", "Buttons"),
            new StaticHudCard("vanilla_tooltips", "Tooltips"),
            new StaticHudCard("swap_tooltip", "Swap Tooltip"),
            new StaticHudCard("vanilla_hotbar", "Hotbar"),
            new StaticHudCard("vanilla_bar", "Bar"),
            new StaticHudCard("vanilla_health", "Health"),
            new StaticHudCard("dynamic_island", "Dynamic Island"),
            new StaticHudCard("tab_list", "Tab List")
    );
    private static final SettingOwner RELATIONS_OWNER =
            new StaticOwner("relations_color", () -> PlayerRelations.get().save());
    private static final SettingOwner ITEMS_OWNER =
            new StaticOwner("items_color", MenuSettingsResolver::saveItemColorOwners);

    public static List<ModuleComponent.CardEntry> buildCards(MenuScreen.Category category) {
        List<ModuleComponent.CardEntry> out = new ArrayList<>();
        switch (category) {
            case HUD -> {
                for (DraggableHudElement widget : DraggableHudElementRegistry.getWidgets()) {
                    if (widget == null) continue;
                    boolean failed = ErrorHandler.failure(widget) != null;
                    out.add(new ModuleComponent.CardEntry(
                            widget.getId(),
                            widget.getTitle(),
                            "",
                            "",
                            !widget.getSettingDefs().isEmpty() || failed,
                            widget.isEnabled(),
                            !failed,
                            failed,
                            true
                    ));
                }
            }
            case UI -> {
                Set<String> included = new HashSet<>();
                for (StaticHudCard card : STATIC_UI_CARDS) {
                    ModuleComponent.CardEntry entry = uiStaticCard(card);
                    if (entry != null) {
                        out.add(entry);
                        included.add(entry.id());
                    }
                }
                for (AbstractHudElement element : StaticHudElementRegistry.getAll()) {
                    if (element == null || included.contains(element.getId())) continue;
                    ModuleComponent.CardEntry entry = uiStaticCard(
                            new StaticHudCard(element.getId(), element.getTitle()));
                    if (entry != null) out.add(entry);
                }
                out.add(uiReadOnlyCard(UI_RELATIONS_COLOR, "Relations Color"));
                out.add(uiReadOnlyCard(UI_ITEMS_COLOR, "Items Color"));
            }
        }
        return out;
    }

    public static void toggleEntry(String id) {
        if (id == null || id.isBlank()) return;


        String mappedId = mapUiAlias(id);
        if (!mappedId.equals(id)) {
            toggleEntry(mappedId);
            return;
        }

        DraggableHudElement draggable = DraggableHudElementRegistry.getById(id);
        if (draggable != null) {
            draggable.setEnabled(!draggable.isEnabled());
            return;
        }

        toggleStatic(id);
    }

    public static ResolvedSettings resolveSettings(String id) {
        if (id == null || id.isBlank()) return null;

        if (UI_RELATIONS_COLOR.equals(id)) {
            return resolveRelationsColorSettings();
        }
        if (UI_ITEMS_COLOR.equals(id)) {
            return resolveItemsColorSettings();
        }

        String mappedId = mapUiAlias(id);
        if (!mappedId.equals(id)) {
            return resolveSettings(mappedId);
        }

        DraggableHudElement draggable = DraggableHudElementRegistry.getById(id);
        if (draggable != null) {
            return new ResolvedSettings(id, draggable.getTitle(),
                    SettingErrorView.withDiagnostics(draggable, buildSettingsFromDefs(draggable)), true);
        }

        AbstractHudElement staticHud = StaticHudElementRegistry.getById(id);
        if (staticHud != null) {
            return new ResolvedSettings(id, staticHud.getTitle(),
                    SettingErrorView.withDiagnostics(staticHud, buildSettingsFromDefs(staticHud)), true);
        }

        return null;
    }

    private static ModuleComponent.CardEntry uiStaticCard(StaticHudCard card) {
        if (card == null) return null;
        AbstractHudElement staticHud = StaticHudElementRegistry.getById(card.staticId());
        if (staticHud == null) return null;

        String title = staticHud.getTitle() == null || staticHud.getTitle().isBlank()
                ? card.fallbackTitle()
                : staticHud.getTitle();
        boolean failed = ErrorHandler.failure(staticHud) != null;

        return new ModuleComponent.CardEntry(
                staticHud.getId(),
                title,
                "",
                "",
                !staticHud.getSettingDefs().isEmpty() || failed,
                staticHud.isEnabled(),
                !failed,
                failed,
                true
        );
    }

    private static ModuleComponent.CardEntry uiReadOnlyCard(String id, String title) {
        return new ModuleComponent.CardEntry(id, title, "", "", true, false, false);
    }

    private static String mapUiAlias(String id) {
        return switch (id) {
            case UI_HOTBAR -> "vanilla_hotbar";
            case UI_BAR -> "vanilla_bar";
            case UI_HEALTH -> "vanilla_health";
            case UI_DYNAMIC_ISLAND -> "dynamic_island";
            case UI_TAB_LIST -> "tab_list";
            case UI_BUTTONS -> "vanilla_buttons";
            case UI_TOOLTIPS -> "vanilla_tooltips";
            case UI_SWAP_TOOLTIP -> "swap_tooltip";
            default -> id;
        };
    }

    private static ResolvedSettings resolveRelationsColorSettings() {
        PlayerRelations relations = PlayerRelations.get();
        List<SettingDef> defs = new ArrayList<>();
        defs.add(SettingDef.colorNoAlpha("default_color", relations.colorDefaultValue()));
        defs.add(SettingDef.colorNoAlpha("friend_color", relations.colorFriendValue()));
        defs.add(SettingDef.colorNoAlpha("enemy_color", relations.colorEnemyValue()));
        defs.add(SettingDef.colorNoAlpha("staff_color", relations.colorStaffValue()));
        defs.add(SettingDef.colorNoAlpha("bedwars_self_color", relations.colorBedwarsSelfValue()));
        defs.add(SettingDef.colorNoAlpha("bedwars_enemy_color", relations.colorBedwarsEnemyValue()));

        List<Setting> out = SettingFactory.fromDefs(defs);
        for (Setting setting : out) {
            setting.setParent(RELATIONS_OWNER);
            setting.preflightI18n();
        }
        if (out.isEmpty()) return null;
        return new ResolvedSettings(UI_RELATIONS_COLOR, "Relations Color", out, false);
    }

    private static ResolvedSettings resolveItemsColorSettings() {
        RarityColorConfig rarity = RarityColorConfig.INSTANCE;
        List<SettingDef> defs = new ArrayList<>();
        defs.add(SettingDef.colorNoAlpha("illegal_item_color", IllegalItemUtil.illegalColorValue()));
        defs.add(SettingDef.colorNoAlpha("top_enchant_color", TopEnchantUtil.topColorValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_common", rarity.commonValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_uncommon", rarity.uncommonValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_rare", rarity.rareValue()));
        defs.add(SettingDef.colorNoAlpha("rarity_epic", rarity.epicValue()));

        List<Setting> out = SettingFactory.fromDefs(defs);
        for (Setting setting : out) {
            setting.setParent(ITEMS_OWNER);
            setting.preflightI18n();
        }
        if (out.isEmpty()) return null;
        return new ResolvedSettings(UI_ITEMS_COLOR, "Items Color", out, false);
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
        if (Modules.get(NameTags.class) != null) {
            Modules.get(NameTags.class).saveConfig();
        }
    }

    private static void toggleStatic(String staticId) {
        AbstractHudElement staticHud = StaticHudElementRegistry.getById(staticId);
        if (staticHud == null) return;
        staticHud.setEnabled(!staticHud.isEnabled());
    }

    private static List<Setting> buildSettingsFromDefs(AbstractHudElement hud) {
        List<Setting> out = SettingFactory.fromDefs(hud.getSettingDefs());
        for (Setting setting : out) {
            setting.setParent(hud);
            setting.preflightI18n();
        }
        return out;
    }

    private static List<Setting> buildSettingsFromDefs(DraggableHudElement hud) {
        List<Setting> out = SettingFactory.fromDefs(hud.getSettingDefs());
        for (Setting setting : out) {
            setting.setParent(hud);
            setting.preflightI18n();
        }
        return out;
    }

    private static String resolveTitle(String id) {
        return switch (id) {
            case UI_RELATIONS_COLOR -> "Relations Color";
            case UI_ITEMS_COLOR -> "Items Color";
            case "vanilla_buttons", UI_BUTTONS -> "Buttons";
            case "vanilla_tooltips", UI_TOOLTIPS -> "Tooltips";
            case "swap_tooltip", UI_SWAP_TOOLTIP -> "Swap Tooltip";
            case "vanilla_hotbar", UI_HOTBAR -> "Hotbar";
            case "vanilla_bar", UI_BAR -> "Bar";
            case "vanilla_health", UI_HEALTH -> "Health";
            case "dynamic_island", UI_DYNAMIC_ISLAND -> "Dynamic Island";
            case "tab_list", UI_TAB_LIST -> "Tab List";
            default -> id;
        };
    }

    private record StaticHudCard(String staticId, String fallbackTitle) {
    }

    private record StaticOwner(String name, Runnable saveAction) implements SettingOwner {

        @Override
        public void saveConfig() {
            if (saveAction != null) {
                saveAction.run();
            }
        }
    }

    public record ResolvedSettings(String id, String title, List<Setting> settings, boolean hudContext) {
        public String getId() {
            return id;
        }
    }
}
