/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.map.duplex.DuplexRole;

import java.util.List;

@ConfigSubsystem(value = "map/duplex_irc", settingOwner = "duplex_irc")
public final class DuplexIrcConfig extends SubsystemConfig {
    public static final DuplexIrcConfig INSTANCE = new DuplexIrcConfig();

    private final BooleanValue enabled = bool("enabled", false);
    private final EnumValue<DuplexRole> role = enumValue("role", DuplexRole.PRIMARY, DuplexRole.class);
    private final StringValue host = value(new StringValue("host", ""));
    private final NumberValue<Integer> port = number("port", 6697, 1, 65535);
    private final BooleanValue tls = bool("tls", true);
    private final StringValue channel = value(new StringValue("channel", ""));
    private final StringValue nickname = value(new StringValue("nickname", ""));
    private final StringValue sessionToken = value(new StringValue("sessionToken", ""));
    private final NumberValue<Integer> heartbeatMs = number("heartbeatMs", 5000, 1000, 60000);
    private final NumberValue<Integer> samplePairToleranceMs = number("samplePairToleranceMs", 750, 25, 10000);
    private final NumberValue<Double> minCrossingAngleDegrees = number("minCrossingAngleDegrees", 5.0, 0.5, 45.0);
    private final NumberValue<Double> bearingNoiseDegrees = number("bearingNoiseDegrees", 0.25, 0.01, 5.0);

    private DuplexIrcConfig(){loadConfig();}
    public static DuplexIrcConfig get(){return INSTANCE;}
    public boolean enabled(){return enabled.get();}
    public DuplexRole role(){return role.get();}
    public String host(){return host.get().trim();}
    public int port(){return port.get().intValue();}
    public boolean tls(){return tls.get();}
    public String channel(){return channel.get().trim();}
    public String nickname(){return nickname.get().trim();}
    public String sessionToken(){return sessionToken.get();}
    public int heartbeatMs(){return heartbeatMs.get().intValue();}
    public int samplePairToleranceMs(){return samplePairToleranceMs.get().intValue();}
    public double minCrossingAngleRadians(){return Math.toRadians(minCrossingAngleDegrees.get().doubleValue());}
    public double bearingNoiseRadians(){return Math.toRadians(bearingNoiseDegrees.get().doubleValue());}
    @Override public List<SettingDef> getSettingDefs(){return List.of();}
}
