/* Copyright (c) 2026 pivosos2007. GPL-3.0-only. */
package combatant.client.render.engine.deferred;

import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;
import combatant.client.features.gui.clickgui.layout.screen.settings.subsystem.MainSettingsContributor;

import java.util.List;

public final class DevDeferredSettingsContributor implements MainSettingsContributor {
    @Override public String id() { return "deferred"; }
    @Override public String titleKey() { return "clickgui.settings.main.deferred"; }
    @Override public String fallbackTitle() { return "Deferred (Dev)"; }
    @Override public String icon() { return "layers"; }
    @Override public int order() { return 110; }
    @Override public SettingOwner owner() { return DevDeferredSettings.get(); }
    @Override public List<SettingDef> settingDefs() { return DevDeferredSettings.get().getSettingDefs(); }
}
