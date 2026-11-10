/* Copyright (c) 2026 pivosos2007. GPL-3.0-only. */
package combatant.client.render.engine.deferred;

import combatant.client.config.SettingDef;
import combatant.client.config.subsystem.ConfigSubsystem;
import combatant.client.config.subsystem.SubsystemConfig;
import combatant.client.config.values.BooleanValue;

import java.util.List;

@ConfigSubsystem(value = "dev/deferred", settingOwner = "deferred")
public final class DevDeferredSettings extends SubsystemConfig {
    private static final DevDeferredSettings INSTANCE = new DevDeferredSettings();

    private final BooleanValue enabled = bool("enabled", Boolean.getBoolean("combatant.render.deferred"));
    private final BooleanValue gtao = bool("gtao", DeferredRuntimeConfig.current().ambientOcclusionEnabled());
    private final BooleanValue indirectLight = bool("indirect_light", DeferredRuntimeConfig.current().indirectLightEnabled());
    private final BooleanValue reflections = bool("reflections", DeferredRuntimeConfig.current().reflectionsEnabled());
    private final BooleanValue coloredLight = bool("colored_light", DeferredRuntimeConfig.current().coloredBlockLightEnabled());
    private final BooleanValue dynamicLights = bool("dynamic_lights", DeferredRuntimeConfig.current().dynamicLightsEnabled());
    private final BooleanValue debugGbuffer = bool("debug_gbuffer", Boolean.getBoolean("combatant.render.deferred.debugGbuffer"));
    private final List<SettingDef> definitions = settings(
            SettingDef.bool(enabled), SettingDef.bool(gtao), SettingDef.bool(indirectLight),
            SettingDef.bool(reflections), SettingDef.bool(coloredLight), SettingDef.bool(dynamicLights),
            SettingDef.bool(debugGbuffer));

    private DevDeferredSettings() {
        loadConfig();
    }

    public static DevDeferredSettings get() { return INSTANCE; }

    @Override
    public List<SettingDef> getSettingDefs() { return definitions; }

    public boolean enabled() { return enabled.get(); }
    public boolean debugGbuffer() { return debugGbuffer.get(); }

    public void publish() {
        DeferredRuntimeConfig.update(builder -> builder
                .ambientOcclusionEnabled(gtao.get())
                .indirectLightEnabled(indirectLight.get())
                .reflectionsEnabled(reflections.get())
                .coloredBlockLightEnabled(coloredLight.get())
                .dynamicLightsEnabled(dynamicLights.get()));
    }
}
