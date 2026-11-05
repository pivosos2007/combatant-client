/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.compat.seedcracker.SeedSnapshots;
import combatant.client.config.ConfigPaths;
import combatant.client.config.SettingDef;
import combatant.client.config.SettingDefProvider;
import combatant.client.config.SettingOwner;
import combatant.client.config.persist.AtomicFileStore;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.ModeValue;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Combatant settings for the optional SeedCrackerX integration.
 *
 * <p>Settings belong to a (server, dimension) context. Never serialize a server's
 * settings through the global subsystem serializer: that would make the last
 * visited server's settings the defaults for the next server. Values are held
 * in stable ConfigValue objects so the Main Settings GUI can retain bindings
 * while the active world/dimension changes.
 */
public final class SeedCrackerConfig implements SettingOwner, SettingDefProvider {
    private static final String MENU_SCOPE = "";
    private static final String FORMAT = "1";
    private static final SeedCrackerConfig INSTANCE = new SeedCrackerConfig();

    private final ConfigDsl dsl = new ConfigDsl();
    private final BooleanValue enabled = dsl.bool("enabled", false);
    private final BooleanValue offerOnDemand = dsl.bool("offer_on_demand", true);
    private final BooleanValue useSaved = dsl.bool("use_saved", true);
    private final BooleanValue saveRecovered = dsl.bool("save_recovered", true);
    private final BooleanValue hideNativeCommands = dsl.bool("hide_native_commands", true);
    private final BooleanValue showNativeOverlay = dsl.bool("show_native_overlay", false);
    private final BooleanValue ignoreServerHash = dsl.bool("ignore_server_hash", false);
    private final ModeValue worldgen = dsl.mode("worldgen", "auto", "auto", "26.3", "26.2", "26.1",
            "1.21.11", "1.21.4", "1.20.4", "1.18.2", "1.16.5");
    private final List<SettingDef> definitions = dsl.settings(
            SettingDef.bool(enabled), SettingDef.bool(offerOnDemand),
            SettingDef.bool(useSaved), SettingDef.bool(saveRecovered),
            SettingDef.bool(hideNativeCommands), SettingDef.bool(showNativeOverlay),
            SettingDef.bool(ignoreServerHash), SettingDef.mode(worldgen));

    private String activeScope;
    private String activeServer = "";
    private String activeDimension = "";

    private SeedCrackerConfig() { }

    public static SeedCrackerConfig get() {
        INSTANCE.refreshContext();
        return INSTANCE;
    }

    /** Only the main client thread should switch world contexts or edit GUI values. */
    public synchronized void refreshContext() {
        Minecraft mc = Minecraft.getInstance();
        String server = mc == null || mc.level == null ? "" : SeedSnapshots.server(mc);
        String dimension = mc == null || mc.level == null ? "" : SeedSnapshots.dimension(mc);
        String next = server.isBlank() || dimension.isBlank() ? MENU_SCOPE : scope(server, dimension);
        if (next.equals(activeScope)) return;

        activeScope = next;
        activeServer = server;
        activeDimension = dimension;
        restoreDefaults();
        readScope(defaultFile(), MENU_SCOPE);
        if (!activeScope.isEmpty()) readScope(file(), activeScope);
    }

    /** Useful for diagnostics and to make the GUI's active profile explicit. */
    public synchronized String contextDescription() {
        refreshContext();
        return activeScope.isEmpty() ? "defaults (no world)" : activeServer + " / " + activeDimension;
    }

    private static String scope(String server, String dimension) {
        return server.trim().toLowerCase(Locale.ROOT) + "|" + dimension.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path defaultFile() {
        return ConfigPaths.subsystemsRoot().resolve("seedcracker/contexts/defaults.properties");
    }

    private Path file() {
        return activeScope.isEmpty() ? defaultFile() : ConfigPaths.subsystemsRoot()
                .resolve("seedcracker/contexts/").resolve(hash(activeScope) + ".properties");
    }

    private void restoreDefaults() {
        enabled.set(false);
        offerOnDemand.set(true);
        useSaved.set(true);
        saveRecovered.set(true);
        hideNativeCommands.set(true);
        showNativeOverlay.set(false);
        ignoreServerHash.set(false);
        worldgen.set("auto");
    }

    private void readScope(Path target, String expectedScope) {
        AtomicFileStore.ReadResult read = AtomicFileStore.readRecovering(
                target, bytes -> validFile(bytes, expectedScope));
        if (!read.found()) return;
        Properties properties = new Properties();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(read.bytes())) {
            properties.load(stream);
            for (ConfigValue<?> value : dsl.values()) {
                String raw = properties.getProperty(value.getName());
                if (raw == null) continue;
                if (value instanceof BooleanValue && (raw.equals("true") || raw.equals("false"))) {
                    value.fromJson(Boolean.parseBoolean(raw));
                } else if (value instanceof ModeValue mode) {
                    if (value == worldgen) mode.addOption(raw);
                    value.fromJson(raw);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            DebugLog.error("SeedCracker config read failed for " + activeScope, e);
        }
    }

    private boolean validFile(byte[] bytes, String expectedScope) {
        Properties p = new Properties();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
            p.load(stream);
            if (!FORMAT.equals(p.getProperty("format")) || !expectedScope.equals(p.getProperty("scope"))) return false;
            for (ConfigValue<?> value : dsl.values()) {
                String raw = p.getProperty(value.getName());
                // v1 files created before hash-guard existed remain readable.
                if (raw == null && value == ignoreServerHash) continue;
                if (raw == null) return false;
                if (value instanceof BooleanValue && !raw.equals("true") && !raw.equals("false")) return false;
                if (value instanceof ModeValue mode && (raw == null || raw.isBlank()
                        || (value != worldgen && !mode.getOptions().contains(raw)))) return false;
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override public String name() { return "seedcracker_config"; }
    @Override public List<SettingDef> getSettingDefs() { refreshContext(); return definitions; }

    /** Called both by GUI's SettingOwner and by the @seed command setters. */
    @Override public synchronized void saveConfig() {
        refreshContext();
        Properties props = new Properties();
        props.setProperty("format", FORMAT);
        props.setProperty("scope", activeScope);
        props.setProperty("server", activeServer);
        props.setProperty("dimension", activeDimension);
        for (ConfigValue<?> value : dsl.values()) props.setProperty(value.getName(), String.valueOf(value.toJson()));
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            props.store(stream, "Combatant SeedCrackerX per-server/dimension configuration");
            AtomicFileStore.write(file(), stream.toByteArray(), bytes -> validFile(bytes, activeScope));
        } catch (IOException e) {
            DebugLog.error("SeedCracker config write failed for " + activeScope, e);
        }
    }

    public boolean enabled() { refreshContext(); return enabled.get(); }
    public void setEnabled(boolean value) { refreshContext(); enabled.set(value); saveConfig(); }
    public boolean offerOnDemand() { refreshContext(); return offerOnDemand.get(); }
    public void setOfferOnDemand(boolean value) { refreshContext(); offerOnDemand.set(value); saveConfig(); }
    public boolean useSaved() { refreshContext(); return useSaved.get(); }
    public void setUseSaved(boolean value) { refreshContext(); useSaved.set(value); saveConfig(); }
    public boolean saveRecovered() { refreshContext(); return saveRecovered.get(); }
    public void setSaveRecovered(boolean value) { refreshContext(); saveRecovered.set(value); saveConfig(); }
    public boolean hideNativeCommands() { refreshContext(); return hideNativeCommands.get(); }
    public void setHideNativeCommands(boolean value) { refreshContext(); hideNativeCommands.set(value); saveConfig(); }
    public boolean showNativeOverlay() { refreshContext(); return showNativeOverlay.get(); }
    public void setShowNativeOverlay(boolean value) { refreshContext(); showNativeOverlay.set(value); saveConfig(); }
    public boolean ignoreServerHash() { refreshContext(); return ignoreServerHash.get(); }
    public void setIgnoreServerHash(boolean value) { refreshContext(); ignoreServerHash.set(value); saveConfig(); }
    public String worldgen() { refreshContext(); return worldgen.get(); }
    public void setWorldgen(String version) {
        refreshContext();
        // The upstream version command exposes the full mc_core version table,
        // not just the abbreviated versions offered by the settings GUI.
        worldgen.addOption(version);
        worldgen.fromJson(version);
        saveConfig();
    }
}
