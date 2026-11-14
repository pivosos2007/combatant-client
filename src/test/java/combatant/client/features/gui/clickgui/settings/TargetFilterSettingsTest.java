/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.SettingDef;
import combatant.client.config.values.TargetFilterValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.modules.combat.AutoAttack;
import combatant.client.features.module.modules.combat.KillAura;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static combatant.client.config.common.impl.TargetFilters.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetFilterSettingsTest {
    private static List<GroupSetting> groups(TargetFilterValue value) {
        return SettingFactory.fromDefs(List.of(SettingDef.group("targets", value))).stream()
                .map(GroupSetting.class::cast).toList();
    }

    @Test
    void sectionsSharePersistenceAndHonorModuleAndOptionVisibility() {
        TargetFilterValue value = new TargetFilterValue("autoattack_toggles", Map.of(PLAYERS_ONLY, false), false);
        AtomicBoolean enemyMode = new AtomicBoolean(true);
        List<Setting> settings = SettingFactory.fromDefs(List.of(
                SettingDef.group("targets", value).visibleWhen(enemyMode::get)));
        assertEquals(4, settings.size());
        assertEquals("targets", settings.getFirst().getId());
        for (Setting setting : settings) assertSame(value, setting.getConfigValue());
        GroupSetting players = (GroupSetting) settings.get(1);
        assertEquals(PLAYERS_ENABLED, value.getAll().keySet().stream().filter(players::isOptionVisible).findFirst().orElseThrow());
        assertTrue(players.isOptionVisible(IGNORE_STAFF));
        assertFalse(players.isOptionVisible(ONLY_HOSTILE));
        value.set(ONLY_STAFF, true);
        assertFalse(players.isOptionVisible(IGNORE_STAFF));
        assertTrue(players.isOptionVisible(ONLY_STAFF));
        assertTrue(settings.get(2).isVisible());
        value.set(PLAYERS_ONLY, true);
        assertFalse(settings.get(2).isVisible());
        enemyMode.set(false);
        for (Setting setting : settings) assertFalse(setting.isVisible());
        enemyMode.set(true);
        assertTrue(players.isVisible());
    }

    @Test
    void renderedOptionListsStayInsideTheirOwnGroups() {
        TargetFilterValue value = new TargetFilterValue("targets", Map.of(PLAYERS_ONLY, false), false);
        List<GroupSetting> groups = groups(value);
        for (int i = 0; i < Section.values().length; i++) {
            assertTrue(Section.values()[i].keys.containsAll(groups.get(i).visibleOptionIds()));
            for (int other = i + 1; other < groups.size(); other++) {
                assertTrue(java.util.Collections.disjoint(groups.get(i).visibleOptionIds(), groups.get(other).visibleOptionIds()));
            }
        }
    }

    @Test
    void playerExclusionsDisappearImmediatelyAndHiddenOptionsCannotBeClicked() {
        TargetFilterValue value = new TargetFilterValue("targets", Map.of(PLAYERS_ONLY, false), false);
        GroupSetting players = groups(value).get(1);
        assertFalse(players.visibleOptionIds().contains(ONLY_FRIENDS));
        assertFalse(players.toggleOption(ONLY_FRIENDS));
        assertTrue(players.toggleOption(IGNORE_FRIENDS));
        assertTrue(players.visibleOptionIds().contains(ONLY_FRIENDS));
        assertTrue(players.toggleOption(ONLY_FRIENDS));
        for (String key : List.of(IGNORE_FRIENDS, IGNORE_STAFF, IGNORE_ENEMIES, IGNORE_TEAMMATES, IGNORE_OTHER_PLAYERS)) {
            assertFalse(players.visibleOptionIds().contains(key));
            boolean previous = value.get(key);
            assertFalse(players.toggleOption(key));
            assertEquals(previous, value.get(key));
        }
        assertTrue(players.toggleOption(ONLY_STAFF));
        assertTrue(players.toggleOption(ONLY_ENEMIES));
        assertTrue(value.get(ONLY_FRIENDS) && value.get(ONLY_STAFF) && value.get(ONLY_ENEMIES));
        assertTrue(players.toggleOption(PLAYERS_ENABLED));
        assertEquals(List.of(PLAYERS_ENABLED), players.visibleOptionIds());
        assertFalse(players.toggleOption(ONLY_FRIENDS));
        assertTrue(players.toggleOption(PLAYERS_ENABLED));
        assertTrue(players.visibleOptionIds().contains(ONLY_FRIENDS));
        assertFalse(players.visibleOptionIds().contains(IGNORE_FRIENDS));
    }

    @Test
    void armorAndMarkedPairsHideInBothDirectionsWithoutLockingImportedConflicts() {
        TargetFilterValue value = new TargetFilterValue("targets", Map.of(), false);
        List<GroupSetting> groups = groups(value);
        for (String[] pair : List.of(new String[]{ONLY_NAKED, IGNORE_NAKED}, new String[]{ONLY_MARKED, IGNORE_MARKED})) {
            GroupSetting group = pair[0].equals(ONLY_NAKED) ? groups.get(1) : groups.get(3);
            assertTrue(group.toggleOption(pair[1]));
            assertFalse(group.visibleOptionIds().contains(pair[0]));
            assertFalse(group.toggleOption(pair[0]));
            assertTrue(group.toggleOption(pair[1]));
            assertTrue(group.toggleOption(pair[0]));
            assertFalse(group.visibleOptionIds().contains(pair[1]));
            assertFalse(group.toggleOption(pair[1]));
            value.set(pair[1], true);
            assertTrue(group.visibleOptionIds().contains(pair[0]));
            assertTrue(group.toggleOption(pair[0]));
            assertTrue(group.visibleOptionIds().contains(pair[1]));
            value.set(pair[1], false);
        }
    }

    @Test
    void mobSelectionsAndScopeChangesUpdateRenderedOptions() {
        TargetFilterValue value = new TargetFilterValue("targets", Map.of(PLAYERS_ONLY, false), false);
        List<GroupSetting> groups = groups(value);
        GroupSetting scope = groups.get(0);
        GroupSetting mobs = groups.get(2);
        assertTrue(mobs.toggleOption(IGNORE_FRIENDLY));
        assertFalse(mobs.visibleOptionIds().contains(ONLY_FRIENDLY));
        assertTrue(mobs.toggleOption(ONLY_HOSTILE));
        assertTrue(mobs.visibleOptionIds().contains(ONLY_FRIENDLY));
        assertFalse(mobs.visibleOptionIds().contains(IGNORE_HOSTILE));
        assertFalse(mobs.visibleOptionIds().contains(IGNORE_NEUTRAL));
        assertFalse(mobs.visibleOptionIds().contains(IGNORE_FRIENDLY));
        assertTrue(scope.toggleOption(PLAYERS_ONLY));
        assertFalse(scope.visibleOptionIds().contains(MOBS_ONLY));
        assertFalse(scope.toggleOption(MOBS_ONLY));
        assertFalse(mobs.isVisible());
        assertFalse(mobs.toggleOption(ONLY_HOSTILE));
        assertTrue(scope.toggleOption(PLAYERS_ONLY));
        assertTrue(mobs.isVisible());
        assertTrue(mobs.toggleOption(MOBS_ENABLED));
        assertEquals(List.of(MOBS_ENABLED), mobs.visibleOptionIds());
        GroupSetting marked = groups.get(3);
        assertTrue(marked.toggleOption(MARKED_ENABLED));
        assertEquals(List.of(MARKED_ENABLED), marked.visibleOptionIds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void killAuraAndTriggerBotDeclarationsCreateFourFilteredControls() throws Exception {
        var definitions = Module.class.getDeclaredField("declaredSettingDefs");
        definitions.setAccessible(true);
        for (Module module : List.of(new KillAura(), new AutoAttack())) {
            List<SettingDef> defs = (List<SettingDef>) definitions.get(module);
            List<Setting> settings = SettingFactory.fromDefs(defs);
            List<GroupSetting> targets = settings.stream()
                    .filter(setting -> setting.getConfigValue() instanceof TargetFilterValue)
                    .map(GroupSetting.class::cast).toList();
            assertEquals(4, targets.size(), module.name());
            assertEquals(List.of(PLAYERS_ONLY, MOBS_ONLY, VISIBLE_ONLY, IGNORE_INVISIBLE), targets.get(0).visibleOptionIds());
            assertFalse(targets.get(1).visibleOptionIds().contains(ONLY_FRIENDS));
            assertFalse(targets.get(1).visibleOptionIds().contains(ONLY_STAFF));
            assertEquals(List.of(MARKED_ENABLED, IGNORE_MARKED), targets.get(3).visibleOptionIds());
        }
    }
}
