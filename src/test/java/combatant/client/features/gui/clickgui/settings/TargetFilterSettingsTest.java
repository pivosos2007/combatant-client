/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.SettingDef;
import combatant.client.config.values.TargetFilterValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static combatant.client.config.common.impl.TargetFilters.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetFilterSettingsTest {
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
}
